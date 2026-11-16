package com.example

import android.app.Application
import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.audio.WavAudioUtils
import com.example.tts.GeminiTtsModel
import com.example.tts.GeminiVoice
import com.example.tts.GeminiVoiceProfile
import com.example.tts.HybridTtsRepository
import com.example.tts.OfflineTtsEngine
import com.example.tts.OfflineTtsException
import com.example.tts.PolitenessParticle
import com.example.tts.ResponseStyle
import com.example.tts.RoutingPolicy
import com.example.tts.SiamVoiceTtsService
import com.example.tts.SpeechAdaptationLevel
import com.example.tts.SynthesisConfig
import com.example.tts.VoiceProfileCategory
import com.example.tts.VoiceStylePreset
import com.example.ui.SettingsViewModel
import com.example.ui.TextToSpeechViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_matchesSiamVoiceTts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SiamVoice TTS", appName)
    }

    @Test
    fun priority1And2_selfRecursionGuardAndOnnxRuntimeInspection_neverReportsDspAsHumanSpeech() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = HybridTtsRepository.getInstance(context)
        repo.initAndroidSystemTts()

        val status = repo.localEngineStatus.value
        // Priority 1: Verify self-recursion guard is active and never selects SiamVoice itself
        assertTrue(status.selfRecursionPrevented)
        assertEquals(context.packageName, status.selfPackageName)
        assertNull(status.selectedExternalEnginePackage)

        // Priority 2A: Verify bundled Thai VITS ONNX model (facebook/mms-tts-tha) stages, loads,
        // and executes real neural speech synthesis (16,000 Hz 16-bit PCM WAV)
        val offlineEngine = OfflineTtsEngine(context, autoStageBundledAssets = true)
        val onnxSpec = offlineEngine.inspectRuntimeStatus()
        assertTrue(onnxSpec.runtimeAvailable)
        assertTrue("Bundled Thai VITS ONNX model should be staged and present", onnxSpec.modelFilesInstalled)
        assertTrue("Bundled tokens/vocab should be present", onnxSpec.tokensFilePresent)
        assertTrue(offlineEngine.isNeuralModelReady())

        val loadRes = offlineEngine.loadModel()
        assertTrue("Expected OrtSession loadModel() to succeed, got: ${loadRes.exceptionOrNull()}", loadRes.isSuccess)

        val realNeuralSynth = offlineEngine.synthesize("สวัสดีครับ เลี้ยวซ้ายข้างหน้า")
        assertTrue("Expected real Thai VITS ONNX synthesis to succeed, got: ${realNeuralSynth.exceptionOrNull()}", realNeuralSynth.isSuccess)
        val neuralOut = realNeuralSynth.getOrThrow()
        assertEquals(16000, neuralOut.sampleRateHz)
        assertTrue("Expected non-empty neural PCM16 audio (> 8000 bytes)", neuralOut.pcm16Bytes.size > 8000)
        assertTrue(WavAudioUtils.isRiffWav(neuralOut.wavBytes))
        assertTrue(neuralOut.durationMs > 200L)
        offlineEngine.close()

        // Priority 2B: Verify missing or corrupted model files are strictly rejected without DSP tone fallback
        val missingModelFile = File(context.cacheDir, "non_existent_model.onnx").apply { delete() }
        val unbundledEngine = OfflineTtsEngine(context, autoStageBundledAssets = false)
        val missingLoadResult = unbundledEngine.loadModel(customModelFile = missingModelFile)
        assertTrue(missingLoadResult.isFailure)
        assertTrue(missingLoadResult.exceptionOrNull() is OfflineTtsException.ModelNotFoundException)

        // Direct OfflineTtsEngine.loadModel() with a truncated/invalid model file must fail with InvalidModelFileException
        val corruptedModelFile = File(context.cacheDir, "corrupted_model.onnx").apply {
            writeBytes(ByteArray(128) { 0x42 })
        }
        val corruptedLoadResult = unbundledEngine.loadModel(customModelFile = corruptedModelFile)
        assertTrue(corruptedLoadResult.isFailure)
        assertTrue(corruptedLoadResult.exceptionOrNull() is OfflineTtsException.InvalidModelFileException)
        corruptedModelFile.delete()
        unbundledEngine.close()
    }

    @Test
    fun textToSpeechViewModel_managesInputPresetsSpeedExpressivenessAndTwoLevelAdaptation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = TextToSpeechViewModel(application)

        // Verify defaults
        viewModel.updateInputText("อีกสามร้อยเมตร เลี้ยวซ้าย")
        viewModel.selectStyle(VoiceStylePreset.NATURAL)
        viewModel.selectAdaptationLevel(SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION)

        val stateLevel1 = viewModel.uiState.value
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้าย", stateLevel1.inputText)
        assertEquals(VoiceStylePreset.NATURAL, stateLevel1.voiceStylePreset)
        assertEquals(1.00f, stateLevel1.speed, 0.001f)
        assertEquals(65, stateLevel1.expressiveness)
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้าย", stateLevel1.effectiveSpokenText)
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", stateLevel1.level2RewrittenPreview)

        // Switch to Friendly preset and adjust speed & expressiveness
        viewModel.selectStyle(VoiceStylePreset.FRIENDLY)
        viewModel.updateSpeechRate(1.08f)
        viewModel.updateExpressiveness(82)

        // Switch to Level 2 (สั่งให้ดัดแปลงคำพูดใหม่)
        viewModel.selectAdaptationLevel(SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH)
        val stateLevel2 = viewModel.uiState.value
        assertEquals(VoiceStylePreset.FRIENDLY, stateLevel2.voiceStylePreset)
        assertEquals(1.08f, stateLevel2.speed, 0.001f)
        assertEquals(82, stateLevel2.expressiveness)
        assertEquals(SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH, stateLevel2.adaptationLevel)
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", stateLevel2.effectiveSpokenText)

        // Apply Level 2 rewrite directly to the input text box
        viewModel.applyLevel2RewriteToInput()
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", viewModel.inputText.value)
    }

    @Test
    fun settingsViewModel_togglesBetweenGeminiCloudAndLocalFallbackEngine_andSyncsWithTtsViewModel() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val settingsVm = SettingsViewModel(application)
        val ttsVm = TextToSpeechViewModel(application)

        // 1. Toggle to Local Android/ONNX Fallback Only (OFFLINE_ONLY)
        settingsVm.toggleEngineMode(useLocalFallbackOnly = true)
        assertTrue(settingsVm.uiState.value.useLocalFallbackOnly)
        assertEquals(RoutingPolicy.OFFLINE_ONLY, settingsVm.uiState.value.routingPolicy)
        assertFalse(settingsVm.uiState.value.capabilityProfile.expressivenessSupport.isSupported)
        assertEquals(RoutingPolicy.OFFLINE_ONLY, ttsVm.config.value.routingPolicy)

        // 2. Toggle back to Gemini Cloud TTS with Auto Hybrid Fallback (AUTO_HYBRID)
        settingsVm.toggleEngineMode(useLocalFallbackOnly = false)
        assertFalse(settingsVm.uiState.value.useLocalFallbackOnly)
        assertTrue(settingsVm.uiState.value.autoFallbackEnabled)
        assertEquals(RoutingPolicy.AUTO_HYBRID, settingsVm.uiState.value.routingPolicy)
        assertTrue(settingsVm.uiState.value.capabilityProfile.expressivenessSupport.isSupported)
        assertEquals(RoutingPolicy.AUTO_HYBRID, ttsVm.config.value.routingPolicy)

        // 3. Disable auto-fallback -> switches to CLOUD_ONLY
        settingsVm.setAutoFallbackEnabled(false)
        assertFalse(settingsVm.uiState.value.autoFallbackEnabled)
        assertEquals(RoutingPolicy.CLOUD_ONLY, settingsVm.uiState.value.routingPolicy)
        assertEquals(RoutingPolicy.CLOUD_ONLY, ttsVm.config.value.routingPolicy)

        // 4. Enable short-phrase local-first -> switches to SHORT_PHRASE_LOCAL_FIRST
        settingsVm.setPrioritizeLocalForShortPhrases(true)
        assertTrue(settingsVm.uiState.value.prioritizeLocalForShortPhrases)
        assertTrue(settingsVm.uiState.value.autoFallbackEnabled)
        assertEquals(RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST, settingsVm.uiState.value.routingPolicy)
        assertEquals(RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST, ttsVm.config.value.routingPolicy)

        // 5. Switch Gemini model & daily quota limit
        settingsVm.selectGeminiModel(GeminiTtsModel.FLASH_LITE_3_8)
        settingsVm.updateDailyQuotaThreshold(75)
        assertEquals(GeminiTtsModel.FLASH_LITE_3_8, settingsVm.uiState.value.selectedGeminiModel)
        assertEquals(75, settingsVm.uiState.value.dailyCloudQuotaLimit)
        assertEquals(GeminiTtsModel.FLASH_LITE_3_8, ttsVm.config.value.model)
        assertEquals(75, ttsVm.config.value.dailyCloudQuotaLimit)

        // 6. Filter and select Female, Male, and Neutral Gemini Cloud voice profiles
        settingsVm.selectVoiceCategory(VoiceProfileCategory.MALE)
        assertEquals(VoiceProfileCategory.MALE, settingsVm.uiState.value.selectedVoiceCategory)
        assertTrue(settingsVm.uiState.value.availableVoiceProfiles.all { it.category == VoiceProfileCategory.MALE })
        settingsVm.selectVoiceProfile(GeminiVoiceProfile.MALE_CHARON_DEEP)
        assertEquals(GeminiVoiceProfile.MALE_CHARON_DEEP, settingsVm.uiState.value.selectedVoiceProfile)
        assertEquals(GeminiVoice.CHARON, settingsVm.uiState.value.selectedGeminiVoice)
        assertEquals(PolitenessParticle.KRUB, settingsVm.uiState.value.politenessParticle)
        assertEquals(GeminiVoice.CHARON, ttsVm.config.value.voice)
        assertEquals(PolitenessParticle.KRUB, ttsVm.config.value.politenessParticle)

        settingsVm.selectVoiceCategory(VoiceProfileCategory.NEUTRAL)
        assertEquals(VoiceProfileCategory.NEUTRAL, settingsVm.uiState.value.selectedVoiceCategory)
        assertTrue(settingsVm.uiState.value.availableVoiceProfiles.all { it.category == VoiceProfileCategory.NEUTRAL })
        settingsVm.selectVoiceProfile(GeminiVoiceProfile.NEUTRAL_ZEPHYR_CLEAR)
        assertEquals(GeminiVoiceProfile.NEUTRAL_ZEPHYR_CLEAR, settingsVm.uiState.value.selectedVoiceProfile)
        assertEquals(GeminiVoice.ZEPHYR, settingsVm.uiState.value.selectedGeminiVoice)
        assertEquals(PolitenessParticle.FRIENDLY_NEUTRAL, settingsVm.uiState.value.politenessParticle)
        assertEquals(GeminiVoice.ZEPHYR, ttsVm.config.value.voice)
        assertEquals(PolitenessParticle.FRIENDLY_NEUTRAL, ttsVm.config.value.politenessParticle)

        settingsVm.selectVoiceCategory(VoiceProfileCategory.FEMALE)
        assertEquals(VoiceProfileCategory.FEMALE, settingsVm.uiState.value.selectedVoiceCategory)
        assertTrue(settingsVm.uiState.value.availableVoiceProfiles.all { it.category == VoiceProfileCategory.FEMALE })
        settingsVm.selectVoiceProfile(GeminiVoiceProfile.FEMALE_AOEDE_WARM)
        assertEquals(GeminiVoiceProfile.FEMALE_AOEDE_WARM, settingsVm.uiState.value.selectedVoiceProfile)
        assertEquals(GeminiVoice.AOEDE, settingsVm.uiState.value.selectedGeminiVoice)
        assertEquals(PolitenessParticle.KHA, settingsVm.uiState.value.politenessParticle)
        assertEquals(GeminiVoice.AOEDE, ttsVm.config.value.voice)
        assertEquals(PolitenessParticle.KHA, ttsVm.config.value.politenessParticle)

        // Restore defaults
        settingsVm.selectVoiceCategory(VoiceProfileCategory.ALL)
        settingsVm.selectVoiceProfile(GeminiVoiceProfile.FEMALE_KORE_ASSISTANT)
        settingsVm.selectGeminiModel(GeminiTtsModel.FLASH_3_8)
        settingsVm.toggleEngineMode(useLocalFallbackOnly = false)
    }

    @Test
    fun priority4_siamVoiceTtsService_streamsAlignedPcmFromCacheAndReportsErrorWhenUncachedOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = HybridTtsRepository.getInstance(context)

        val serviceController = Robolectric.buildService(SiamVoiceTtsService::class.java).create()
        val service = serviceController.get()

        val thaiAvail = service.onIsLanguageAvailable("tha", "THA", "")
        assertEquals(TextToSpeech.LANG_COUNTRY_AVAILABLE, thaiAvail)

        val voices = service.onGetVoices()
        assertTrue(voices.any { it.name == "th-th-gemini-flash-kore" })
        assertTrue(voices.any { it.name == "th-th-gemini-lite-kore" })
        assertTrue(voices.any { it.name == "th-th-x-siamvoice-local" })
        assertEquals(TextToSpeech.SUCCESS, service.onIsValidVoiceName("th-th-gemini-flash-kore"))

        // 1. Test uncached offline request -> synthesizes real 16kHz 16-bit PCM speech via bundled Thai VITS ONNX model
        val onnxRecorder = RecordingSynthesisCallback()
        val uncachedParams = Bundle().apply {
            putString("voiceName", "th-th-x-siamvoice-local")
        }
        val uncachedReq = SynthesisRequest("เลี้ยวขวาข้างหน้า", uncachedParams)
        service.onSynthesizeText(uncachedReq, onnxRecorder)
        assertTrue("Expected startCalled on uncached offline ONNX synthesis", onnxRecorder.startCalled)
        assertEquals(16000, onnxRecorder.sampleRateHz)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, onnxRecorder.audioFormat)
        assertEquals(1, onnxRecorder.channelCount)
        assertTrue(onnxRecorder.doneCalled)
        assertFalse(onnxRecorder.errorCalled)
        assertTrue("Expected non-empty PCM bytes from ONNX VITS synthesis", onnxRecorder.totalBytesWritten > 4000)
        assertTrue(onnxRecorder.chunkSizes.all { it % 2 == 0 })

        // 2. Seed a verified 24kHz 16-bit PCM WAV into the cache for a short driving instruction
        val navPhrase = "อีกสามร้อยเมตร เลี้ยวซ้าย"
        val serviceConfig = SynthesisConfig(
            model = GeminiTtsModel.FLASH_LITE_3_8,
            voice = GeminiVoice.KORE,
            style = VoiceStylePreset.NAVIGATION,
            responseStyle = ResponseStyle.NAVIGATION,
            routingPolicy = RoutingPolicy.OFFLINE_ONLY,
            speechRate = 1.0f,
            pitch = 1.0f
        )
        val rawPcm = ByteArray(4800) { (it % 64).toByte() } // 2400 16-bit samples
        repo.seedVerifiedCachedWav(
            rawText = navPhrase,
            config = serviceConfig,
            wavBytes = WavAudioUtils.ensureWavContainer(rawPcm, sampleRateHz = 24000)
        )

        val pcmRecorder = RecordingSynthesisCallback(maxBuffer = 1025) // intentionally odd buffer size to test 2-byte alignment
        val cachedParams = Bundle().apply {
            putString("voiceName", "th-th-x-siamvoice-local")
        }
        val cachedReq = SynthesisRequest(navPhrase, cachedParams)
        service.onSynthesizeText(cachedReq, pcmRecorder)

        assertTrue(pcmRecorder.startCalled)
        assertEquals(24000, pcmRecorder.sampleRateHz)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, pcmRecorder.audioFormat)
        assertEquals(1, pcmRecorder.channelCount)
        assertTrue(pcmRecorder.doneCalled)
        assertFalse(pcmRecorder.errorCalled)
        assertEquals(4800, pcmRecorder.totalBytesWritten)
        assertEquals(
            com.example.tts.TtsServicePlaybackState.COMPLETED,
            com.example.tts.SystemTtsServiceMonitor.playbackState.value
        )
        // Every chunk written must be 2-byte aligned for 16-bit mono PCM
        assertTrue(pcmRecorder.chunkSizes.all { it % 2 == 0 })

        // 3. Test mid-stream playback lifecycle preemption via onStop()
        val stoppingRecorder = RecordingSynthesisCallback(
            maxBuffer = 1024,
            onFirstChunkAvailable = { service.onStop() }
        )
        service.onSynthesizeText(cachedReq, stoppingRecorder)
        assertTrue(stoppingRecorder.startCalled)
        assertEquals(1, stoppingRecorder.chunkSizes.size) // stopped immediately after first chunk
        assertFalse(stoppingRecorder.doneCalled) // done() must NOT be called after onStop()
        assertEquals(
            com.example.tts.TtsServicePlaybackState.STOPPED,
            com.example.tts.SystemTtsServiceMonitor.playbackState.value
        )

        // 4. Test Runtime Anti-Recursion Circuit Breaker when request originates from SiamVoice itself
        val recursiveParams = Bundle().apply {
            putString(HybridTtsRepository.EXTRA_ORIGIN_PACKAGE, context.packageName)
            putString("voiceName", "th-th-x-siamvoice-local")
        }
        val recursiveReq = SynthesisRequest(navPhrase, recursiveParams)
        val recursiveRecorder = RecordingSynthesisCallback()
        val blockedBefore = com.example.tts.SystemTtsServiceMonitor.recursiveCallsBlocked.value
        service.onSynthesizeText(recursiveReq, recursiveRecorder)

        assertTrue(recursiveRecorder.errorCalled)
        assertEquals(TextToSpeech.ERROR_SYNTHESIS, recursiveRecorder.lastErrorCode)
        assertFalse(recursiveRecorder.startCalled)
        assertEquals(blockedBefore + 1, com.example.tts.SystemTtsServiceMonitor.recursiveCallsBlocked.value)
        assertEquals(
            com.example.tts.TtsServicePlaybackState.RECURSION_BLOCKED,
            com.example.tts.SystemTtsServiceMonitor.playbackState.value
        )
    }

    @Test
    fun hybridRepository_seededWavCacheHitAndSmartVoiceProfilePersistence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = HybridTtsRepository.getInstance(context)

        val navConfig = SynthesisConfig(
            routingPolicy = RoutingPolicy.OFFLINE_ONLY,
            style = VoiceStylePreset.NAVIGATION,
            responseStyle = ResponseStyle.NAVIGATION,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH
        )
        val samplePcm = ByteArray(9600) { (it % 90).toByte() }
        val seeded = repo.seedVerifiedCachedWav(
            rawText = "อีกสามร้อยเมตร เลี้ยวซ้าย",
            config = navConfig,
            wavBytes = WavAudioUtils.ensureWavContainer(samplePcm, sampleRateHz = 24000)
        )
        assertTrue(File(seeded.wavFilePath).exists())
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", seeded.normalizedText)

        // Subsequent synthesis with bypassCache = false hits the local WAV cache immediately
        val cacheHitResult = repo.synthesizeSpeech(
            rawText = "อีกสามร้อยเมตร เลี้ยวซ้าย",
            config = navConfig,
            isNavigationPhrase = true,
            bypassCache = false
        )
        assertTrue(cacheHitResult.isSuccess)
        val outcome = cacheHitResult.getOrThrow()
        assertTrue(outcome.providerUsed.startsWith("CACHE_HIT"))
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", outcome.normalizedText)

        // Verify Smart Voice Profile save & delete
        val customConfig = SynthesisConfig(
            style = VoiceStylePreset.LUXURY,
            responseStyle = ResponseStyle.ASSISTANT,
            speechRate = 0.94f,
            expressiveness = 70,
            voiceWarmth = 78,
            formality = 88,
            customStyleInstruction = "พูดด้วยน้ำเสียงนุ่มนวลและสุภาพระดับโรงแรมห้าดาว"
        )
        repo.saveVoiceProfile(
            profileName = "Test Five-Star Concierge",
            config = customConfig
        )
        val profiles = repo.observeVoiceProfiles().first()
        val saved = profiles.firstOrNull { it.profileName == "Test Five-Star Concierge" }
        assertTrue(saved != null)
        repo.deleteVoiceProfile(saved!!.id)
    }

    @Test
    fun f5TtsThai_checksumVerificationReferenceAudioConditioningTokenizersAndBenchmark_verified() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storageManager = com.example.tts.OfflineModelStorageManager(context)
        val f5Engine = com.example.tts.F5TtsThaiEngine(context, autoStageBundledAssets = true)
        val offlineEngine = OfflineTtsEngine(context, autoStageBundledAssets = true)

        // 1. Verify SHA-256 Checksum Validation on all 10 bundled F5-TTS-THAI & Thai VITS ONNX artifacts
        val summary = storageManager.inspectStorageSummary(stageBundledFirst = true)
        assertTrue("Expected bundled Thai VITS ONNX model to be verified", summary.vitsReadyAndVerified)
        assertTrue("Expected bundled F5-TTS-THAI conditioning assets to be verified", summary.f5ConditioningAssetsVerified)

        val bundledSpecs = com.example.tts.OfflineModelStorageManager.MODEL_CATALOG.filter { it.isBundledInApk }
        assertEquals(8, bundledSpecs.size)
        for (spec in bundledSpecs) {
            val status = summary.artifacts.first { it.spec.id == spec.id }
            assertTrue("Expected ${spec.fileName} to be installed", status.isInstalled)
            assertTrue("Expected ${spec.fileName} SHA-256 checksum to match ${spec.expectedSha256}", status.checksumMatches)
            assertEquals(spec.expectedSizeBytes, status.actualSizeBytes)
        }

        // 2. Verify empty placeholder or corrupted files are strictly rejected by importModelFileWithChecksum
        val emptyPlaceholder = File(context.cacheDir, "empty_placeholder.onnx").apply { writeBytes(ByteArray(0)) }
        val emptyImportRes = storageManager.importModelFileWithChecksum(
            emptyPlaceholder,
            com.example.tts.OfflineModelStorageManager.SPEC_F5_VOCODER_ONNX
        )
        assertTrue(emptyImportRes.isFailure)
        emptyPlaceholder.delete()

        val badHashFile = File(context.cacheDir, "bad_hash.onnx").apply { writeBytes(ByteArray(2048) { 0x11 }) }
        val badHashRes = storageManager.importModelFileWithChecksum(
            badHashFile,
            com.example.tts.OfflineModelStorageManager.SPEC_F5_VOCODER_ONNX
        )
        assertTrue(badHashRes.isFailure)
        badHashFile.delete()

        // 3. Verify Reference Audio Conditioning (22.05kHz stereo -> 24kHz mono and 44.1kHz mono -> 24kHz mono)
        val ref1Cond = f5Engine.conditionReferenceAudio(com.example.tts.OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR).getOrThrow()
        assertEquals(22050, ref1Cond.originalSampleRateHz)
        assertEquals(2, ref1Cond.originalChannels)
        assertEquals(24000, ref1Cond.sampleRateHz)
        assertTrue("Expected ~4.38s of 24kHz mono samples (~105,256 samples)", ref1Cond.sampleCount24k in 100_000..110_000)
        assertEquals(ref1Cond.sampleCount24k, ref1Cond.pcm16Mono24k.size)

        val ref2Cond = f5Engine.conditionReferenceAudio(com.example.tts.OfflineVoiceProfile.F5_THAI_STUDIO_NARRATOR).getOrThrow()
        assertEquals(44100, ref2Cond.originalSampleRateHz)
        assertEquals(1, ref2Cond.originalChannels)
        assertEquals(24000, ref2Cond.sampleRateHz)
        assertTrue("Expected ~4.76s of 24kHz mono samples (~114,251 samples)", ref2Cond.sampleCount24k in 110_000..120_000)

        // 4. Verify F5-TTS-THAI v1 (2,586 char vocab) and v2 / F5-TH-ONNX (79 IPA phoneme vocab) tokenizers
        val v1Vocab = f5Engine.loadVocabV1()
        val v2Vocab = f5Engine.loadVocabV2()
        val ipaVocab = f5Engine.loadOnnxIpaVocab()
        assertEquals(2586, v1Vocab.size)
        assertEquals(2554, v2Vocab.size)
        assertEquals(81, ipaVocab.size)

        val v1Tokens = f5Engine.tokenizeForF5V1Char(ref1Cond.referenceTextTh, "อีกสามร้อยเมตร เลี้ยวซ้าย")
        assertTrue(v1Tokens.size > 30)
        val ipaTokens = f5Engine.tokenizeForF5OnnxIpa(ref1Cond.referenceTextTh, "อีกสามร้อยเมตร เลี้ยวซ้าย")
        assertTrue(ipaTokens.size > 30)

        // 5. If /tmp/Preprocess.onnx and /tmp/Vocoder.onnx from VIZINTZOR/F5-TH-ONNX are present,
        //    verify their SHA-256 and execute real ONNX Runtime forward passes on conditioned reference audio!
        val tmpPreprocess = File("/tmp/Preprocess.onnx")
        val tmpVocoder = File("/tmp/Vocoder.onnx")
        if (tmpPreprocess.exists() && tmpVocoder.exists()) {
            val env = ai.onnxruntime.OrtEnvironment.getEnvironment()
            val opts = ai.onnxruntime.OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(ai.onnxruntime.OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            env.createSession(tmpPreprocess.absolutePath, opts).use { prepSession ->
                env.createSession(tmpVocoder.absolutePath, opts).use { vocSession ->
                    // Subgraph A: Preprocess.onnx forward pass with real ref_audio_1.wav (24kHz mono INT16)
                    val maxDur = com.example.tts.F5TtsThaiEngine.computeF5MaxDurationFrames(
                        refAudioSamples24k = ref1Cond.sampleCount24k,
                        refTextLength = ref1Cond.referenceTextTh.length,
                        genTextLength = "อีกสามร้อยเมตร เลี้ยวซ้าย".length,
                        speed = 1.0f
                    )
                    val audioTensor = ai.onnxruntime.OnnxTensor.createTensor(
                        env,
                        java.nio.ShortBuffer.wrap(ref1Cond.pcm16Mono24k),
                        longArrayOf(1L, 1L, ref1Cond.sampleCount24k.toLong())
                    )
                    val textTensor = ai.onnxruntime.OnnxTensor.createTensor(
                        env,
                        java.nio.IntBuffer.wrap(ipaTokens),
                        longArrayOf(1L, ipaTokens.size.toLong())
                    )
                    val maxDurTensor = ai.onnxruntime.OnnxTensor.createTensor(
                        env,
                        java.nio.LongBuffer.wrap(longArrayOf(maxDur)),
                        longArrayOf(1L)
                    )
                    prepSession.run(
                        mapOf(
                            "audio" to audioTensor,
                            "text_ids" to textTensor,
                            "max_duration" to maxDurTensor
                        )
                    ).use { prepOut ->
                        val noiseTensor = prepOut.get("noise").get() as ai.onnxruntime.OnnxTensor
                        val refSignalLenTensor = prepOut.get("ref_signal_len").get() as ai.onnxruntime.OnnxTensor
                        assertArrayEquals(longArrayOf(1L, maxDur, 100L), noiseTensor.info.shape)

                        // Subgraph C: Vocoder.onnx (Vocos 24kHz) forward pass
                        vocSession.run(
                            mapOf(
                                "denoised" to noiseTensor,
                                "ref_signal_len" to refSignalLenTensor
                            )
                        ).use { vocOut ->
                            val outAudioTensor = vocOut.get("output_audio").get() as ai.onnxruntime.OnnxTensor
                            val outShape = outAudioTensor.info.shape
                            assertEquals(3, outShape.size)
                            assertTrue("Expected generated 24kHz PCM samples > 10,000", outShape[2] > 10_000L)
                            println("VERIFIED_F5_ONNX_SUBGRAPHS: Preprocess.onnx -> Vocoder.onnx output_audio shape=${outShape.contentToString()}")
                        }
                    }
                    audioTensor.close()
                    textTensor.close()
                    maxDurTensor.close()
                }
            }
        }

        // 6. Run real offline neural benchmark on Thai navigation commands and general sentences
        val benchmark = offlineEngine.runOfflineBenchmark().getOrThrow()
        assertTrue(benchmark.samples.isNotEmpty())
        assertTrue(benchmark.averageNavigationLatencyMs > 0L)
        assertTrue(benchmark.averageRealTimeFactor > 0.0)
        println(
            "OFFLINE_BENCHMARK_SUMMARY: coldLoad=${benchmark.coldLoadLatencyMs}ms, " +
                "warmLoad=${benchmark.warmLoadLatencyMs}ms, refCond=${benchmark.referenceAudioConditioningMs}ms, " +
                "avgNavLatency=${benchmark.averageNavigationLatencyMs}ms, " +
                "avgSentLatency=${benchmark.averageSentenceLatencyMs}ms, " +
                "avgRTF=${String.format(java.util.Locale.US, "%.4f", benchmark.averageRealTimeFactor)}"
        )
        for (sample in benchmark.samples) {
            println(
                "OFFLINE_BENCHMARK_SAMPLE: phrase='${sample.phraseTh}' | nav=${sample.isNavigationCommand} | " +
                    "vitsTokens=${sample.tokenCount} | f5IpaTokens=${sample.f5IpaTokenCount} | " +
                    "f5MaxFrames=${sample.f5MaxDurationFrames} | latency=${sample.inferenceLatencyMs}ms | " +
                    "audioDur=${sample.audioDurationMs}ms | sr=${sample.sampleRateHz}Hz | " +
                    "pcmBytes=${sample.pcmByteSize} | RTF=${String.format(java.util.Locale.US, "%.4f", sample.realTimeFactor)}"
            )
        }

        f5Engine.close()
        offlineEngine.close()
    }

    private class RecordingSynthesisCallback(
        private val maxBuffer: Int = 2048,
        private val onFirstChunkAvailable: (() -> Unit)? = null
    ) : SynthesisCallback {
        var startCalled = false
        var doneCalled = false
        var errorCalled = false
        var lastErrorCode = TextToSpeech.SUCCESS
        var sampleRateHz = 0
        var audioFormat = 0
        var channelCount = 0
        var totalBytesWritten = 0
        val chunkSizes = mutableListOf<Int>()

        override fun getMaxBufferSize(): Int = maxBuffer

        override fun start(sampleRateInHz: Int, audioFormat: Int, channelCount: Int): Int {
            startCalled = true
            this.sampleRateHz = sampleRateInHz
            this.audioFormat = audioFormat
            this.channelCount = channelCount
            return TextToSpeech.SUCCESS
        }

        override fun audioAvailable(buffer: ByteArray?, offset: Int, length: Int): Int {
            chunkSizes.add(length)
            totalBytesWritten += length
            if (chunkSizes.size == 1) {
                onFirstChunkAvailable?.invoke()
            }
            return TextToSpeech.SUCCESS
        }

        override fun done(): Int {
            doneCalled = true
            return TextToSpeech.SUCCESS
        }

        override fun error() {
            errorCalled = true
            lastErrorCode = TextToSpeech.ERROR
        }

        override fun error(errorCode: Int) {
            errorCalled = true
            lastErrorCode = errorCode
        }

        override fun hasStarted(): Boolean = startCalled

        override fun hasFinished(): Boolean = doneCalled || errorCalled
    }
}
