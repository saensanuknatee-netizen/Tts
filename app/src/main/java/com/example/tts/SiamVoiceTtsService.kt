package com.example.tts

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.example.audio.WavAudioUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

private const val TAG = "SiamVoiceTtsService"

/**
 * Explicit playback & synthesis lifecycle states for the Android TextToSpeechService.
 */
enum class TtsServicePlaybackState(val labelTh: String) {
    IDLE("พร้อมรับคำสั่ง (Idle)"),
    PREPARING("กำลังเตรียมสังเคราะห์เสียง (Preparing)"),
    STREAMING_PCM("กำลังส่งสตรีมเสียง PCM 16-bit (Streaming Audio)"),
    STOPPED("หยุดเล่นตามคำสั่งระบบ (Stopped/Preempted)"),
    COMPLETED("ส่งข้อมูลเสียงเสร็จสมบูรณ์ (Completed)"),
    ERROR("เกิดข้อผิดพลาด (Error)"),
    RECURSION_BLOCKED("บล็อกการเรียกซ้ำตัวเอง (Recursive Call Blocked)")
}

object SystemTtsServiceMonitor {
    private val _serviceActive = MutableStateFlow(false)
    val serviceActive: StateFlow<Boolean> = _serviceActive.asStateFlow()

    private val _playbackState = MutableStateFlow(TtsServicePlaybackState.IDLE)
    val playbackState: StateFlow<TtsServicePlaybackState> = _playbackState.asStateFlow()

    private val _lastBoundRequest = MutableStateFlow("ยังไม่มีคำขอจากระบบ Android TTS")
    val lastBoundRequest: StateFlow<String> = _lastBoundRequest.asStateFlow()

    private val _totalServiceRequests = MutableStateFlow(0)
    val totalServiceRequests: StateFlow<Int> = _totalServiceRequests.asStateFlow()

    private val _recursiveCallsBlocked = MutableStateFlow(0)
    val recursiveCallsBlocked: StateFlow<Int> = _recursiveCallsBlocked.asStateFlow()

    fun markActive(active: Boolean) {
        _serviceActive.value = active
        if (!active) {
            _playbackState.value = TtsServicePlaybackState.IDLE
        }
    }

    fun updatePlaybackState(state: TtsServicePlaybackState) {
        _playbackState.value = state
    }

    fun recordRequest(summary: String) {
        _totalServiceRequests.value += 1
        _lastBoundRequest.value = summary
    }

    fun recordRecursionBlocked(reason: String) {
        _recursiveCallsBlocked.value += 1
        _playbackState.value = TtsServicePlaybackState.RECURSION_BLOCKED
        _lastBoundRequest.value = "BLOCKED RECURSION: $reason"
    }
}

/**
 * Production Android TextToSpeechService allowing SiamVoice Hybrid TTS to be selected
 * as a system-wide Android TTS engine in Settings -> Accessibility -> Text-to-speech output.
 *
 * Key guarantees:
 * 1. Playback & Synthesis Lifecycle Management:
 *    - Uses an atomic generation counter (`activeGenerationToken`) + `@Volatile isStopped` flag
 *      and `activeSynthesisJob` reference so `onStop()`, `onDestroy()`, or a newer `onSynthesizeText()`
 *      request immediately preempts any in-flight synthesis or PCM streaming loop.
 *    - Strictly tracks `SynthesisCallback` lifecycle (`start` -> 2-byte aligned `audioAvailable` chunks -> `done` or `error`).
 * 2. Two-Layer Non-Recursive Execution:
 *    - Layer 1: `HybridTtsRepository` enumerates installed engines via `PackageManager` and excludes
 *      SiamVoice's own package (`applicationContext.packageName`) before instantiating any fallback `TextToSpeech`.
 *    - Layer 2: `isRecursiveEngineCall(request)` inspects `HybridTtsRepository.EXTRA_ORIGIN_PACKAGE` and
 *      `repository.isExternalFallbackInFlight` at runtime. If a fallback call ever routes back into
 *      `SiamVoiceTtsService`, it is immediately rejected with `TextToSpeech.ERROR_SYNTHESIS`.
 */
open class SiamVoiceTtsService : TextToSpeechService() {

    private val repository by lazy { HybridTtsRepository.getInstance(applicationContext) }

    @Volatile
    private var isStopped = false

    private val activeGenerationToken = AtomicLong(0L)

    @Volatile
    private var activeSynthesisJob: Job? = null

    private val currentLanguage = arrayOf("tha", "THA", "")

    override fun onCreate() {
        super.onCreate()
        SystemTtsServiceMonitor.markActive(true)
        SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.IDLE)
        Log.i(TAG, "SiamVoiceTtsService created and ready for system TTS bindings")
    }

    override fun onDestroy() {
        onStop()
        SystemTtsServiceMonitor.markActive(false)
        super.onDestroy()
    }

    public override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        if (lang == null) return TextToSpeech.LANG_NOT_SUPPORTED
        val normalized = lang.lowercase(Locale.ROOT)
        return when {
            normalized == "th" || normalized == "tha" -> {
                if (country.equals("TH", ignoreCase = true) || country.equals("THA", ignoreCase = true)) {
                    TextToSpeech.LANG_COUNTRY_AVAILABLE
                } else {
                    TextToSpeech.LANG_AVAILABLE
                }
            }
            normalized == "en" || normalized == "eng" -> TextToSpeech.LANG_AVAILABLE
            else -> TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    public override fun onGetLanguage(): Array<String> = currentLanguage

    public override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val availability = onIsLanguageAvailable(lang, country, variant)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            currentLanguage[0] = lang ?: "tha"
            currentLanguage[1] = country ?: "THA"
            currentLanguage[2] = variant ?: ""
        }
        return availability
    }

    public override fun onGetVoices(): MutableList<Voice> {
        val thaiLocale = Locale.forLanguageTag("th-TH")
        val voices = mutableListOf<Voice>()

        // 1. Offline Low-Latency Thai Neural Voices (F5-TTS-THAI 24kHz & Thai VITS ONNX 16kHz)
        voices.add(
            Voice(
                "th-th-x-siamvoice-local",
                thaiLocale,
                Voice.QUALITY_HIGH,
                Voice.LATENCY_VERY_LOW,
                false,
                setOf("local", "thai_navigation", "onnx_vits")
            )
        )
        voices.add(
            Voice(
                "th-th-f5-thai-ref1",
                thaiLocale,
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                setOf("local", "f5_tts_thai", "ref_audio_1", "24khz")
            )
        )
        voices.add(
            Voice(
                "th-th-f5-thai-ref2",
                thaiLocale,
                Voice.QUALITY_VERY_HIGH,
                Voice.LATENCY_LOW,
                false,
                setOf("local", "f5_tts_thai", "ref_audio_2", "24khz")
            )
        )

        // 2. Gemini 3.8 Flash TTS & Flash-Lite TTS Hybrid Voices
        GeminiVoice.entries.forEach { gv ->
            voices.add(
                Voice(
                    "th-th-gemini-flash-${gv.voiceId.lowercase(Locale.ROOT)}",
                    thaiLocale,
                    Voice.QUALITY_VERY_HIGH,
                    Voice.LATENCY_NORMAL,
                    false,
                    setOf("hybrid", "gemini_3_8_flash_tts", gv.voiceId)
                )
            )
            voices.add(
                Voice(
                    "th-th-gemini-lite-${gv.voiceId.lowercase(Locale.ROOT)}",
                    thaiLocale,
                    Voice.QUALITY_HIGH,
                    Voice.LATENCY_LOW,
                    false,
                    setOf("hybrid", "gemini_3_8_flash_lite_tts", gv.voiceId)
                )
            )
        }
        return voices
    }

    public override fun onGetDefaultVoiceNameFor(lang: String?, country: String?, variant: String?): String {
        return "th-th-gemini-flash-kore"
    }

    public override fun onIsValidVoiceName(voiceName: String?): Int {
        if (voiceName.isNullOrBlank()) return TextToSpeech.ERROR
        val exists = onGetVoices().any { it.name.equals(voiceName, ignoreCase = true) }
        return if (exists) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    public override fun onLoadVoice(voiceName: String?): Int {
        return onIsValidVoiceName(voiceName)
    }

    /**
     * Immediately stops any active synthesis and halts ongoing PCM audio streaming.
     */
    public override fun onStop() {
        isStopped = true
        activeGenerationToken.incrementAndGet()
        activeSynthesisJob?.cancel(CancellationException("SiamVoiceTtsService.onStop() invoked"))
        activeSynthesisJob = null
        SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STOPPED)
    }

    /**
     * Checks whether this synthesis request was triggered recursively by SiamVoice's own
     * internal fallback mechanism.
     */
    fun isRecursiveEngineCall(request: SynthesisRequest): Boolean {
        val selfPkg = applicationContext.packageName
        val originPkg = request.params?.getString(HybridTtsRepository.EXTRA_ORIGIN_PACKAGE).orEmpty()
        if (originPkg.isNotBlank() &&
            (originPkg.equals(selfPkg, ignoreCase = true) ||
                originPkg.equals("com.example", ignoreCase = true) ||
                originPkg.contains("siamvoice", ignoreCase = true))
        ) {
            return true
        }
        if (repository.isExternalFallbackInFlight.get()) {
            return true
        }
        return false
    }

    private fun isRequestCancelled(generationToken: Long): Boolean {
        return isStopped || activeGenerationToken.get() != generationToken
    }

    public override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return

        // 1. Runtime Circuit Breaker against Recursive TTS Calls
        if (isRecursiveEngineCall(request)) {
            val msg = "ตรวจพบและบล็อกการเรียก TTS ซ้ำเข้าหาตัวเอง (${applicationContext.packageName})"
            Log.e(TAG, msg)
            SystemTtsServiceMonitor.recordRecursionBlocked(msg)
            runBlocking {
                repository.logDiagnostic(
                    level = "ERROR",
                    category = "RECURSION_GUARD",
                    provider = "SiamVoiceTtsService",
                    errorCode = "RECURSIVE_TTS_CALL_BLOCKED",
                    message = msg
                )
            }
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
            return
        }

        // 2. Preempt any previous synthesis request and start a new lifecycle token
        activeSynthesisJob?.cancel(CancellationException("Preempted by newer onSynthesizeText request"))
        val myGenerationToken = activeGenerationToken.incrementAndGet()
        isStopped = false
        SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.PREPARING)

        val text = request.charSequenceText?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            val startRes = callback.start(
                WavAudioUtils.DEFAULT_GEMINI_SAMPLE_RATE,
                AudioFormat.ENCODING_PCM_16BIT,
                1
            )
            if (startRes != TextToSpeech.ERROR) {
                callback.done()
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.COMPLETED)
            } else {
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
            }
            return
        }

        val requestedVoiceName = request.voiceName?.takeIf { it.isNotBlank() }
            ?: request.params?.getString("voiceName").orEmpty()
        val rawRate = if (request.speechRate > 0) request.speechRate else 100
        val rawPitch = if (request.pitch > 0) request.pitch else 100
        val speechRate = (rawRate.toFloat() / 100f).coerceIn(0.5f, 2.0f)
        val pitch = (rawPitch.toFloat() / 100f).coerceIn(0.5f, 2.0f)
        val isShortDrivingInstruction = text.length <= 45

        val selectedModel = if (requestedVoiceName.contains("lite", ignoreCase = true) || isShortDrivingInstruction) {
            GeminiTtsModel.FLASH_LITE_3_8
        } else {
            GeminiTtsModel.FLASH_3_8
        }

        val selectedVoice = GeminiVoice.entries.firstOrNull {
            requestedVoiceName.endsWith(it.voiceId, ignoreCase = true)
        } ?: GeminiVoice.KORE

        val selectedOfflineVoiceProfile = when {
            requestedVoiceName.contains("ref2", ignoreCase = true) -> OfflineVoiceProfile.F5_THAI_STUDIO_NARRATOR
            requestedVoiceName.contains("siamvoice-local", ignoreCase = true) -> OfflineVoiceProfile.THAI_VITS_FAST_NAVIGATION
            else -> OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR
        }

        val localStatus = repository.localEngineStatus.value
        val hasReadyOfflineSpeech = localStatus.onnxModelInstalled ||
            (localStatus.selectedExternalEnginePackage != null && localStatus.thaiLanguageAvailable)

        val routingPolicy = when {
            requestedVoiceName.contains("local", ignoreCase = true) ||
                requestedVoiceName.contains("f5-thai", ignoreCase = true) -> RoutingPolicy.OFFLINE_ONLY
            isShortDrivingInstruction && hasReadyOfflineSpeech -> RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST
            else -> RoutingPolicy.AUTO_HYBRID
        }

        SystemTtsServiceMonitor.recordRequest(
            "Text: \"${text.take(30)}\" | Voice: ${requestedVoiceName.ifBlank { "default" }} | Policy: ${routingPolicy.id}"
        )

        try {
            var outcomeResult: Result<SynthesisOutcome>? = null
            runBlocking {
                coroutineScope {
                    val job = launch {
                        ensureActive()
                        outcomeResult = repository.synthesizeSpeech(
                            rawText = text,
                            config = SynthesisConfig(
                                model = selectedModel,
                                voice = selectedVoice,
                                style = if (isShortDrivingInstruction) VoiceStylePreset.NAVIGATION else VoiceStylePreset.NATURAL,
                                responseStyle = if (isShortDrivingInstruction) ResponseStyle.NAVIGATION else ResponseStyle.CONVERSATIONAL,
                                thaiVoicePersonality = if (selectedVoice == GeminiVoice.ORUS) ThaiVoicePersonality.PERSONAL_ASSISTANT else ThaiVoicePersonality.FRIENDLY_COMPANION,
                                thaiPoliteEnding = ThaiPoliteEnding.AUTO_NATURAL,
                                disableRewritingForNavigation = false,
                                routingPolicy = routingPolicy,
                                speechRate = speechRate,
                                pitch = pitch,
                                offlineVoiceProfile = selectedOfflineVoiceProfile,
                                useArgonReasoning = false,
                                cloudTimeoutMs = SynthesisConfig.DEFAULT_CLOUD_TIMEOUT_MS
                            ),
                            isNavigationPhrase = isShortDrivingInstruction,
                            isCalledFromSystemTtsService = true
                        )
                    }
                    activeSynthesisJob = job
                    job.join()
                }
            }

            if (isRequestCancelled(myGenerationToken)) {
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STOPPED)
                return
            }

            val result = outcomeResult
            if (result == null || result.isFailure) {
                val errEx = result?.exceptionOrNull()
                val errMsg = errEx?.localizedMessage.orEmpty()
                Log.w(TAG, "SiamVoiceTtsService synthesis failed honestly (no fake DSP fallback): $errMsg")
                val errorCode = when {
                    errMsg.contains("model.onnx", ignoreCase = true) ||
                        errMsg.contains("OFFLINE", ignoreCase = true) -> TextToSpeech.ERROR_NOT_INSTALLED_YET
                    errMsg.contains("อินเทอร์เน็ต", ignoreCase = true) ||
                        errMsg.contains("network", ignoreCase = true) -> TextToSpeech.ERROR_NETWORK
                    else -> TextToSpeech.ERROR_SYNTHESIS
                }
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
                callback.error(errorCode)
                return
            }

            val wavBytes = result.getOrThrow().wavBytes
            val sampleRate = WavAudioUtils.extractSampleRateFromWav(wavBytes)
            val pcmBytes = WavAudioUtils.extractPcmFromWav(wavBytes)

            // Ensure 16-bit mono PCM byte length is strictly even (2 bytes per sample)
            val alignedTotalBytes = pcmBytes.size and 1.inv()
            if (alignedTotalBytes <= 0) {
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
                return
            }

            if (isRequestCancelled(myGenerationToken)) {
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STOPPED)
                return
            }

            val startStatus = callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1)
            if (startStatus == TextToSpeech.ERROR) {
                Log.w(TAG, "SynthesisCallback.start returned ERROR")
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
                callback.error(TextToSpeech.ERROR_SYNTHESIS)
                return
            }

            SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STREAMING_PCM)

            val rawMaxBuffer = callback.maxBufferSize.coerceAtLeast(1024)
            val maxBufferSize = (rawMaxBuffer and 1.inv()).coerceAtLeast(2)

            var offset = 0
            while (offset < alignedTotalBytes && !isRequestCancelled(myGenerationToken)) {
                val remaining = alignedTotalBytes - offset
                val bytesToWrite = min(maxBufferSize, remaining) and 1.inv()
                if (bytesToWrite <= 0) break

                val status = callback.audioAvailable(pcmBytes, offset, bytesToWrite)
                if (status == TextToSpeech.ERROR) {
                    Log.w(TAG, "SynthesisCallback.audioAvailable returned ERROR")
                    SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
                    callback.error(TextToSpeech.ERROR_SYNTHESIS)
                    return
                }
                offset += bytesToWrite
            }

            if (!isRequestCancelled(myGenerationToken)) {
                callback.done()
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.COMPLETED)
            } else {
                SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STOPPED)
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "SiamVoiceTtsService synthesis cancelled via onStop()")
            SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.STOPPED)
        } catch (e: Exception) {
            Log.e(TAG, "Error inside SiamVoiceTtsService.onSynthesizeText", e)
            SystemTtsServiceMonitor.updatePlaybackState(TtsServicePlaybackState.ERROR)
            callback.error(TextToSpeech.ERROR_SYNTHESIS)
        } finally {
            activeSynthesisJob = null
        }
    }
}

/**
 * Alias subclass for callers referencing `AppTextToSpeechService`.
 */
class AppTextToSpeechService : SiamVoiceTtsService()

