package com.example

import com.example.ai.AssistantModelMode
import com.example.audio.WavAudioUtils
import com.example.tts.GeminiTtsClient
import com.example.tts.GeminiTtsModel
import com.example.tts.GeminiVoice
import com.example.tts.HybridTtsRepository
import com.example.tts.OfflineTtsEngine
import com.example.tts.PolitenessParticle
import com.example.tts.ProviderCapabilityProfile
import com.example.tts.ResponseStyle
import com.example.tts.RoutingPolicy
import com.example.tts.SpeakingStyle
import com.example.tts.SpeechAdaptationLevel
import com.example.tts.SynthesisConfig
import com.example.tts.ThaiOfflineNeuralTtsEngine
import com.example.tts.ThaiTextOptimizer
import com.example.tts.VoiceStylePreset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun priority1_filterOutSelfTtsEngines_excludesSiamVoiceAndSelectsCompatibleExternalProvider() {
        val selfPkg = "com.aistudio.siamvoice.vxtqmr"

        // Case 1: Multiple engines installed including SiamVoice and Google TTS
        val candidates = listOf(
            "com.aistudio.siamvoice.vxtqmr",
            "com.example",
            "com.google.android.tts",
            "com.samsung.SMT"
        )
        val filtered = HybridTtsRepository.filterOutSelfTtsEngines(candidates, selfPkg)
        assertEquals(listOf("com.google.android.tts", "com.samsung.SMT"), filtered)
        assertFalse(filtered.contains(selfPkg))

        // Case 2: SiamVoice is the ONLY installed engine -> returns empty list so TextToSpeech never binds to self
        val onlySelf = listOf("com.aistudio.siamvoice.vxtqmr")
        val filteredEmpty = HybridTtsRepository.filterOutSelfTtsEngines(onlySelf, selfPkg)
        assertTrue(filteredEmpty.isEmpty())
    }

    @Test
    fun priority2_offlineTtsEngine_tokenizesThaiAndConvertsNeuralFloatWaveformToPcm16() {
        val defaultVocab = mapOf(
            "<pad>" to 0L,
            " " to 1L,
            "ก" to 2L,
            "ข" to 3L,
            "า" to 4L
        )
        val tokens = OfflineTtsEngine.tokenizeThaiForVits("กขา", defaultVocab)
        // VITS blank-interspersed format: [0, id(ก), 0, id(ข), 0, id(า), 0]
        assertArrayEquals(longArrayOf(0L, 2L, 0L, 3L, 0L, 4L, 0L), tokens)
        assertArrayEquals(tokens, ThaiOfflineNeuralTtsEngine.tokenizeThaiForVits("กขา", defaultVocab))

        // Verify 3D ONNX output tensor extraction [1, 1, N] -> FloatArray
        val nestedTensorOutput = arrayOf(arrayOf(floatArrayOf(0.0f, 0.5f, -0.5f)))
        val extracted = OfflineTtsEngine.extractFloatWaveform(nestedTensorOutput)
        assertEquals(3, extracted.size)

        // Verify FloatArray [-1.0, 1.0] conversion to 16-bit Little-Endian PCM
        val pcmBytes = OfflineTtsEngine.convertFloatWaveformToPcm16Le(extracted)
        assertEquals(6, pcmBytes.size)
        val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals(0.toShort(), shortBuf.get(0))
        assertEquals(16383.toShort(), shortBuf.get(1))
        assertEquals((-16383).toShort(), shortBuf.get(2))

        // Verify peak normalization prevents hard clipping when neural output exceeds [-0.98, 0.98]
        val overdriven = OfflineTtsEngine.convertFloatWaveformToPcm16Le(floatArrayOf(1.2f, -1.2f))
        val overdrivenBuf = ByteBuffer.wrap(overdriven).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals((0.98f * 32767f).toInt().toShort(), overdrivenBuf.get(0))
        assertEquals((-0.98f * 32767f).toInt().toShort(), overdrivenBuf.get(1))
    }

    @Test
    fun priority3_geminiModelsAndStylePrompt_useOfficialFormatWithoutInventedFields() {
        assertEquals("gemini-3.8-flash-tts", GeminiTtsModel.FLASH_3_8.modelId)
        assertEquals("gemini-3.8-flash-lite-tts", GeminiTtsModel.FLASH_LITE_3_8.modelId)
        assertEquals("gemini-3.1-flash-tts-preview", GeminiTtsModel.FLASH_3_8.fallbackPreviewId)
        assertEquals("gemini-4-argon", AssistantModelMode.ARGON_HIGH_THINKING.modelId)
        assertEquals("gemini-3.5-flash-lite", AssistantModelMode.FLASH_LITE_FAST.modelId)

        val config = SynthesisConfig(
            model = GeminiTtsModel.FLASH_3_8,
            voice = GeminiVoice.KORE,
            style = VoiceStylePreset.FRIENDLY,
            responseStyle = ResponseStyle.NAVIGATION
        )
        val prompt = config.buildGeminiStylePrompt("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ")
        assertTrue(prompt.endsWith(": อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ"))
        assertFalse(prompt.startsWith("["))

        // Verify GeminiTtsClient MIME rate parsing and audio stream decoding (PCM16 LE, L16 BE, and RIFF WAV)
        assertEquals(24000, GeminiTtsClient.parseSampleRateFromMimeType("audio/pcm;rate=24000"))
        assertEquals(16000, GeminiTtsClient.parseSampleRateFromMimeType("audio/L16;rate=16000;channels=1"))
        assertEquals(24000, GeminiTtsClient.parseSampleRateFromMimeType(null))

        // 1. Little-endian audio/pcm with odd trailing byte -> trims to 2-byte sample alignment
        val oddPcmInput = byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x7F)
        val decodedLe = GeminiTtsClient.decodeInlineAudioPartToPcm16Le(oddPcmInput, "audio/pcm;rate=24000")
        assertEquals(24000, decodedLe.sampleRateHz)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0x78, 0x56), decodedLe.pcm16LeBytes)

        // 2. Big-endian RFC 2586 audio/L16 -> byte-swaps pairs into little-endian 16-bit PCM
        val bigEndianL16 = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val decodedBe = GeminiTtsClient.decodeInlineAudioPartToPcm16Le(bigEndianL16, "audio/L16;rate=22050")
        assertEquals(22050, decodedBe.sampleRateHz)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0x78, 0x56), decodedBe.pcm16LeBytes)

        // 3. RIFF WAV container in InlineDataPart -> extracts sample rate and strips 44-byte WAV header
        val wavWrapped = WavAudioUtils.ensureWavContainer(byteArrayOf(0x10, 0x20, 0x30, 0x40), sampleRateHz = 24000)
        val decodedWav = GeminiTtsClient.decodeInlineAudioPartToPcm16Le(wavWrapped, "audio/wav")
        assertEquals(24000, decodedWav.sampleRateHz)
        assertArrayEquals(byteArrayOf(0x10, 0x20, 0x30, 0x40), decodedWav.pcm16LeBytes)

        // 4. Quota message classification
        assertTrue(GeminiTtsClient.isQuotaMessage("HTTP 429 RESOURCE_EXHAUSTED: Quota exceeded"))
        assertFalse(GeminiTtsClient.isQuotaMessage("SocketTimeoutException: Read timed out"))
    }

    @Test
    fun wavContainer_wrapsRawPcmAndExtractsCorrectly() {
        val rawPcm = ByteArray(24000 * 2) { (it % 120).toByte() } // 1.0 second at 24kHz 16-bit mono
        assertFalse(WavAudioUtils.isRiffWav(rawPcm))

        val wavBytes = WavAudioUtils.ensureWavContainer(rawPcm, sampleRateHz = 24000)
        assertTrue(WavAudioUtils.isRiffWav(wavBytes))
        assertEquals(44 + rawPcm.size, wavBytes.size)
        assertEquals(24000, WavAudioUtils.extractSampleRateFromWav(wavBytes))

        val extractedPcm = WavAudioUtils.extractPcmFromWav(wavBytes)
        assertArrayEquals(rawPcm, extractedPcm)

        val durationMs = WavAudioUtils.calculateDurationMs(wavBytes)
        assertEquals(1000L, durationMs)
    }

    @Test
    fun cacheKey_isDeterministicAndSensitiveToParameters() {
        val key1 = WavAudioUtils.computeCacheKey(
            normalizedText = "เลี้ยวซ้ายข้างหน้า",
            modelId = GeminiTtsModel.FLASH_3_8.modelId,
            voiceName = GeminiVoice.KORE.voiceId,
            styleName = SpeakingStyle.NAVIGATION.id,
            speechRate = 1.0f,
            pitch = 1.0f,
            responseStyleId = ResponseStyle.NAVIGATION.id,
            expressiveness = 65
        )
        val key2 = WavAudioUtils.computeCacheKey(
            normalizedText = "เลี้ยวซ้ายข้างหน้า",
            modelId = GeminiTtsModel.FLASH_3_8.modelId,
            voiceName = GeminiVoice.KORE.voiceId,
            styleName = SpeakingStyle.NAVIGATION.id,
            speechRate = 1.0f,
            pitch = 1.0f,
            responseStyleId = ResponseStyle.NAVIGATION.id,
            expressiveness = 65
        )
        val keyLuxury = WavAudioUtils.computeCacheKey(
            normalizedText = "เลี้ยวซ้ายข้างหน้า",
            modelId = GeminiTtsModel.FLASH_3_8.modelId,
            voiceName = GeminiVoice.KORE.voiceId,
            styleName = VoiceStylePreset.LUXURY.id,
            speechRate = 1.0f,
            pitch = 1.0f,
            responseStyleId = ResponseStyle.CONVERSATIONAL.id,
            expressiveness = 70
        )

        assertEquals(key1, key2)
        assertNotEquals(key1, keyLuxury)
    }

    @Test
    fun thaiTextOptimizer_normalizesNumbersUnitsTimeRoadsAndMixedEnglish() {
        val optimized = ThaiTextOptimizer.optimizeThaiText(
            rawText = "เวลา 14:30 น. อีก 200 ม. เลี้ยวซ้ายเข้าสู่ ถ.สุขุมวิท ระยะทาง 1.5 กม. ตาม GPS",
            customRules = emptyList(),
            pronunciationClarity = 80,
            naturalPauses = 60
        )
        assertTrue("Expected Thai time reading, got: $optimized", optimized.contains("สิบสี่นาฬิกา สามสิบนาที"))
        assertTrue("Expected 200m reading, got: $optimized", optimized.contains("สองร้อยเมตร"))
        assertTrue("Expected Sukhumvit road expansion, got: $optimized", optimized.contains("ถนนสุขุมวิท"))
        assertTrue("Expected 1.5km reading, got: $optimized", optimized.contains("หนึ่งจุดห้ากิโลเมตร"))
        assertTrue("Expected GPS acronym reading, got: $optimized", optimized.contains("จีพีเอส"))
    }

    @Test
    fun twoLevelAdaptation_level1KeepsWordsAndLevel2RewritesPhrasing() {
        val rawInput = "อีกสามร้อยเมตร เลี้ยวซ้าย"

        // Level 1: ดัดน้ำเสียง/อารมณ์ให้ดูเป็นกันเองขึ้น (คงคำพูดเดิม 100%)
        val level1Output = ThaiTextOptimizer.optimizeThaiText(
            rawText = rawInput,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
            politenessParticle = PolitenessParticle.KRUB,
            voiceStyle = VoiceStylePreset.FRIENDLY
        )
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้าย", level1Output)

        // Level 2: สั่งให้ดัดแปลงคำพูดใหม่ (เช่น เปลี่ยนจาก "อีกสามร้อยเมตร เลี้ยวซ้าย" เป็น "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ")
        val level2OutputKrub = ThaiTextOptimizer.optimizeThaiText(
            rawText = rawInput,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = PolitenessParticle.KRUB,
            voiceStyle = VoiceStylePreset.FRIENDLY
        )
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", level2OutputKrub)

        // Level 2 with numeric "อีก 300 ม. เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ"
        val level2Numeric = ThaiTextOptimizer.optimizeThaiText(
            rawText = "อีก 300 ม. เลี้ยวซ้าย",
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = PolitenessParticle.KRUB,
            voiceStyle = VoiceStylePreset.NATURAL
        )
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ", level2Numeric)

        // Level 2 with KHA particle -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะคะ"
        val level2OutputKha = ThaiTextOptimizer.optimizeThaiText(
            rawText = rawInput,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = PolitenessParticle.KHA,
            voiceStyle = VoiceStylePreset.FRIENDLY
        )
        assertEquals("อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะคะ", level2OutputKha)
    }

    @Test
    fun thaiNumberToWords_handlesEdgeCasesAccurately() {
        assertEquals("ศูนย์", ThaiTextOptimizer.numberToThaiWords(0))
        assertEquals("สิบเอ็ด", ThaiTextOptimizer.numberToThaiWords(11))
        assertEquals("ยี่สิบเอ็ด", ThaiTextOptimizer.numberToThaiWords(21))
        assertEquals("หนึ่งร้อยเอ็ด", ThaiTextOptimizer.numberToThaiWords(101))
        assertEquals("สองร้อยห้าสิบ", ThaiTextOptimizer.numberToThaiWords(250))
    }

    @Test
    fun providerCapabilityProfile_disablesUnsupportedControlsForOfflineMode() {
        val cloudProfile = ProviderCapabilityProfile.forRoutingPolicy(RoutingPolicy.AUTO_HYBRID)
        val offlineProfile = ProviderCapabilityProfile.forRoutingPolicy(RoutingPolicy.OFFLINE_ONLY)

        assertTrue(cloudProfile.speedSupport.isSupported)
        assertTrue(cloudProfile.emotionalIntensitySupport.isSupported)
        assertTrue(cloudProfile.expressivenessSupport.isSupported)
        assertTrue(cloudProfile.speechStabilitySupport.isSupported)

        assertTrue(offlineProfile.speedSupport.isSupported)
        assertTrue(offlineProfile.pitchSupport.isSupported)
        assertTrue(offlineProfile.volumeSupport.isSupported)
        assertFalse(offlineProfile.emotionalIntensitySupport.isSupported)
        assertFalse(offlineProfile.expressivenessSupport.isSupported)
        assertFalse(offlineProfile.formalitySupport.isSupported)
        assertFalse(offlineProfile.speechStabilitySupport.isSupported)
    }

    @Test
    fun defaultSynthesisConfig_matchesRequiredDefaults() {
        val defaultConfig = SynthesisConfig()
        assertEquals(RoutingPolicy.AUTO_HYBRID, defaultConfig.routingPolicy)
        assertEquals("AUTO_HYBRID", defaultConfig.routingPolicy.id)
        assertEquals(GeminiTtsModel.FLASH_3_8, defaultConfig.model)
        assertEquals("gemini-3.8-flash-tts", defaultConfig.model.modelId)
        assertFalse(defaultConfig.useArgonReasoning)
        assertEquals(3_000L, defaultConfig.cloudTimeoutMs)
        assertEquals(3_000L, GeminiTtsClient.DEFAULT_TIMEOUT_MS)
        assertEquals("Gemini Cloud > F5-TTS-THAI > Android System TTS", SynthesisConfig.FALLBACK_PRIORITY_CHAIN)
        assertEquals(VoiceStylePreset.NATURAL, defaultConfig.style)
        assertEquals(ResponseStyle.CONVERSATIONAL, defaultConfig.responseStyle)
        assertEquals(SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION, defaultConfig.adaptationLevel)
        assertEquals(1.00f, defaultConfig.speechRate, 0.001f)
        assertEquals(65, defaultConfig.expressiveness)
    }

    @Test
    fun f5TtsThai_phonemizerMaxDurationClauseSegmentationAndCrossfade_workAccurately() {
        // 1. Thai-to-IPA phonemization for F5-TTS-TH-V2 / F5-TH-ONNX (79-token VachanaTTS IPA vocabulary)
        val ipaSymbols = com.example.tts.F5TtsThaiEngine.thaiToIpaPhonemes("อีกสามร้อยเมตร เลี้ยวซ้าย")
        assertTrue("Expected non-empty IPA symbols", ipaSymbols.length > 10)
        assertTrue(
            "Expected combining tone marks in IPA output",
            ipaSymbols.any { it in setOf('\u0300', '\u0301', '\u0302', '\u030C') }
        )

        // 2. F5 max_duration mel frame calculation (24,000 Hz / hop_length 256)
        val maxFrames = com.example.tts.F5TtsThaiEngine.computeF5MaxDurationFrames(
            refAudioSamples24k = 24000 * 4, // 4.0 seconds reference audio = 375 mel frames
            refTextLength = 80,
            genTextLength = 40,
            speed = 1.0f
        )
        assertTrue("Expected maxFrames > 375 (ref_audio_len + gen_frames)", maxFrames > 375L)

        // 3. Thai clause segmentation for low-latency F5 synthesis
        val clauses = com.example.tts.F5TtsThaiEngine.segmentThaiClausesForF5(
            rawText = "ในอีก 300 ม. เลี้ยวซ้ายเข้าสู่ ถ.สุขุมวิท แล้วตรงไปอีก 2 กม. จนถึงสี่แยกอโศก",
            maxClauseChars = 45
        )
        assertTrue("Expected long Thai navigation text to be segmented into >= 2 clauses", clauses.size >= 2)
        assertTrue(clauses.first().contains("สามร้อยเมตร"))

        // 4. 24kHz PCM16 linear crossfade (0.15s)
        val chunkA = ShortArray(24000) { 4000 }
        val chunkB = ShortArray(24000) { -4000 }
        val crossfaded = com.example.tts.F5TtsThaiEngine.crossfadePcm16Chunks(
            chunks = listOf(chunkA, chunkB),
            crossFadeDurationSec = 0.15f,
            sampleRateHz = 24000
        )
        val expectedFadeSamples = (24000 * 0.15f).toInt()
        assertEquals(chunkA.size + chunkB.size - expectedFadeSamples, crossfaded.size)
    }

    @Test
    fun dspAudioPostProcessing_appliesVolumeWarmthAndSpeedToPcmBuffer() {
        val rawPcm = ByteArray(24000 * 2) { ((it * 7) % 100).toByte() }
        val baseWav = WavAudioUtils.ensureWavContainer(rawPcm, sampleRateHz = 24000)
        val processedWav = WavAudioUtils.applyAudioPostProcessing(
            wavOrPcmBytes = baseWav,
            volume = 0.85f,
            voiceWarmth = 75,
            speedRatio = 1.15f
        )
        assertTrue(WavAudioUtils.isRiffWav(processedWav))
        assertTrue(processedWav.size < baseWav.size)
    }

    @Test
    fun thaiVoicePersonalityAndPoliteEndingSystem_preservesRulesAndAccuracy() {
        // 1. Verify Thai Voice Personality Presets
        val friendly = com.example.tts.ThaiVoicePersonality.FRIENDLY_COMPANION
        assertEquals(GeminiVoice.PUCK, friendly.recommendedVoice)
        assertTrue(friendly.thaiVoiceStyleInstruction.contains("เหมือนเพื่อนสนิทนั่งข้างคนขับ"))
        assertTrue(friendly.thaiVoiceStyleInstruction.contains("พูดด้วยน้ำเสียงอารมณ์ดี"))

        val assistant = com.example.tts.ThaiVoicePersonality.PERSONAL_ASSISTANT
        assertEquals(GeminiVoice.ORUS, assistant.recommendedVoice)
        assertTrue(assistant.thaiVoiceStyleInstruction.contains("เหมือนเลขาส่วนตัวที่คอยช่วยเหลือ"))
        assertTrue(assistant.thaiVoiceStyleInstruction.contains("พูดด้วยน้ำเสียงนุ่มทุ้ม สุภาพ ใจเย็น"))

        // 2. Verify Thai Polite Sentence Ending System contextual rules
        val navTurn = "อีก 300 เมตร เลี้ยวซ้าย"
        val navTurnProcessed = ThaiTextOptimizer.applyPoliteEnding(
            text = navTurn,
            ending = com.example.tts.ThaiPoliteEnding.AUTO_NATURAL,
            disableRewritingForNavigation = false
        )
        assertEquals("อีก 300 เมตร เลี้ยวซ้ายนะครับ", navTurnProcessed)

        val routeFound = "พบเส้นทางที่เร็วกว่า"
        val routeFoundProcessed = ThaiTextOptimizer.applyPoliteEnding(
            text = routeFound,
            ending = com.example.tts.ThaiPoliteEnding.AUTO_NATURAL,
            disableRewritingForNavigation = false
        )
        assertEquals("พบเส้นทางที่เร็วกว่าแล้วครับผม", routeFoundProcessed)

        val arrived = "ถึงจุดหมายแล้ว"
        val arrivedProcessed = ThaiTextOptimizer.applyPoliteEnding(
            text = arrived,
            ending = com.example.tts.ThaiPoliteEnding.AUTO_NATURAL,
            disableRewritingForNavigation = false
        )
        assertEquals("ถึงจุดหมายแล้วนะครับ", arrivedProcessed)

        // 3. Verify duplicate prevention
        val alreadyHasKrub = "เดินทางปลอดภัยนะครับ"
        val deduped = ThaiTextOptimizer.applyPoliteEnding(
            text = alreadyHasKrub,
            ending = com.example.tts.ThaiPoliteEnding.KRUB,
            disableRewritingForNavigation = false
        )
        assertEquals("เดินทางปลอดภัยนะครับ", deduped)

        // 4. Verify urgent safety instructions are preserved intact
        val urgent = "ระวังอันตราย รถชะลอตัวกะทันหัน"
        val urgentProcessed = ThaiTextOptimizer.applyPoliteEnding(
            text = urgent,
            ending = com.example.tts.ThaiPoliteEnding.AUTO_NATURAL,
            disableRewritingForNavigation = false
        )
        assertEquals("ระวังอันตราย รถชะลอตัวกะทันหัน", urgentProcessed)

        // 5. Verify disable rewriting for navigation flag
        val navDisabled = ThaiTextOptimizer.applyPoliteEnding(
            text = "อีก 500 เมตร เลี้ยวขวา",
            ending = com.example.tts.ThaiPoliteEnding.AUTO_NATURAL,
            disableRewritingForNavigation = true
        )
        assertEquals("อีก 500 เมตร เลี้ยวขวา", navDisabled)

        // 6. Verify specific endings
        val krubEnding = ThaiTextOptimizer.applyPoliteEnding(
            text = "ยินดีต้อนรับ",
            ending = com.example.tts.ThaiPoliteEnding.KRUB,
            disableRewritingForNavigation = false
        )
        assertEquals("ยินดีต้อนรับครับ", krubEnding)

        val naKrubEnding = ThaiTextOptimizer.applyPoliteEnding(
            text = "ยินดีต้อนรับ",
            ending = com.example.tts.ThaiPoliteEnding.NA_KRUB,
            disableRewritingForNavigation = false
        )
        assertEquals("ยินดีต้อนรับนะครับ", naKrubEnding)

        val noEnding = ThaiTextOptimizer.applyPoliteEnding(
            text = "ยินดีต้อนรับ",
            ending = com.example.tts.ThaiPoliteEnding.NO_ENDING,
            disableRewritingForNavigation = false
        )
        assertEquals("ยินดีต้อนรับ", noEnding)
    }
}
