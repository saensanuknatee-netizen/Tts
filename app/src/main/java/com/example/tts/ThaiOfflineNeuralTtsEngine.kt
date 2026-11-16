package com.example.tts

import android.content.Context
import java.io.Closeable
import java.io.File

/**
 * Metadata and runtime status for the ONNX Runtime Thai Neural Offline TTS engine
 * (supporting both **VIZINTZOR/F5-TTS-THAI** 24kHz Flow-Matching + Vocos and
 * **Meta MMS-TTS Thai VITS** 16kHz low-latency navigation synthesis).
 */
data class OfflineNeuralEngineInfo(
    val ortAvailable: Boolean,
    val ortVersion: String,
    val supportedAbis: List<String>,
    val isArm64Compatible: Boolean,
    val modelDirectoryPath: String,
    val modelFilePresent: Boolean,
    val modelFileSizeBytes: Long,
    val tokensFilePresent: Boolean,
    val f5ConditioningAssetsReady: Boolean = false,
    val f5SubgraphsInstalled: Boolean = false,
    val f5ModelDirectoryPath: String = "",
    val verifiedChecksumCount: Int = 0,
    val totalOfflineStorageBytes: Long = 0L,
    val modelArchitectureSpec: String =
        "F5-TTS-THAI (Diffusion Transformer dim=1024, depth=22, heads=16 + Vocos 24kHz) & VITS Low-Latency Thai (16kHz)",
    val licensingNotesTh: String =
        "VIZINTZOR/F5-TTS-THAI (CC-BY-4.0) · SWivid/F5-TTS & Vocos (MIT) · Meta MMS-TTS Thai VITS (CC-BY-NC-4.0)",
    val memoryLatencyProfileTh: String =
        "Thai VITS ONNX: 34.7 MB (RAM ~110 MB, Latency ~25-180 ms) · F5-TH-ONNX 3 Subgraphs: 810.5 MB (Preprocess 85.3 MB + Transformer 662.6 MB + Vocoder 62.5 MB)"
) {
    val runtimeAvailable: Boolean get() = ortAvailable
    val runtimeVersion: String get() = ortVersion
    val modelFilesInstalled: Boolean get() = modelFilePresent || f5SubgraphsInstalled
    val modelFilePath: String get() = "$modelDirectoryPath/model.onnx"
    val licensingSummaryTh: String get() = licensingNotesTh
    val memoryAndLatencySpecTh: String get() = memoryLatencyProfileTh
}

/**
 * Single benchmark item for a real Thai sentence or short navigation command.
 */
data class OfflineBenchmarkSampleMetrics(
    val phraseTh: String,
    val isNavigationCommand: Boolean,
    val tokenCount: Int,
    val f5IpaTokenCount: Int,
    val f5MaxDurationFrames: Long,
    val inferenceLatencyMs: Long,
    val audioDurationMs: Long,
    val sampleRateHz: Int,
    val pcmByteSize: Int,
    val realTimeFactor: Double,
    val engineUsed: String
)

/**
 * Aggregated benchmark report for real Thai offline neural speech synthesis.
 */
data class OfflineBenchmarkReport(
    val coldLoadLatencyMs: Long,
    val warmLoadLatencyMs: Long,
    val referenceAudioConditioningMs: Long,
    val referenceAudioFrames24k: Long,
    val samples: List<OfflineBenchmarkSampleMetrics>,
    val averageNavigationLatencyMs: Long,
    val averageSentenceLatencyMs: Long,
    val averageRealTimeFactor: Double
)

/**
 * Adapter class delegating to [OfflineTtsEngine] for backward compatibility across the codebase.
 */
class ThaiOfflineNeuralTtsEngine(context: Context) : Closeable {

    val offlineTtsEngine = OfflineTtsEngine(context.applicationContext)

    val modelFile: File
        get() = offlineTtsEngine.modelFile

    val tokensFile: File
        get() = offlineTtsEngine.tokensFile

    val f5TtsEngine: F5TtsThaiEngine
        get() = offlineTtsEngine.f5TtsEngine

    val storageManager: OfflineModelStorageManager
        get() = offlineTtsEngine.storageManager

    fun inspectRuntimeStatus(): OfflineNeuralEngineInfo = offlineTtsEngine.inspectRuntimeStatus()

    fun isNeuralModelReady(): Boolean = offlineTtsEngine.isNeuralModelReady()

    suspend fun loadModel(customModelFile: File = modelFile, customTokensFile: File? = null) =
        offlineTtsEngine.loadModel(customModelFile, customTokensFile)

    fun tokenizeThaiForVits(normalizedThaiText: String): LongArray =
        offlineTtsEngine.tokenizeThaiForVits(normalizedThaiText)

    suspend fun synthesizeThaiNeuralWav(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        voiceWarmth: Int = 50,
        sampleRateHz: Int = OfflineTtsEngine.DEFAULT_VITS_SAMPLE_RATE_HZ,
        offlineVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
        f5TokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
        f5NfeSteps: Int = F5SynthesisParams.DEFAULT_NFE_STEPS
    ): Result<ByteArray> = offlineTtsEngine.synthesizeThaiNeuralWav(
        text = text,
        speechRate = speechRate,
        pitch = pitch,
        volume = volume,
        voiceWarmth = voiceWarmth,
        sampleRateHz = sampleRateHz,
        offlineVoiceProfile = offlineVoiceProfile,
        f5TokenizerMode = f5TokenizerMode,
        f5NfeSteps = f5NfeSteps
    )

    override fun close() {
        offlineTtsEngine.close()
    }

    companion object {
        fun tokenizeThaiForVits(
            normalizedThaiText: String,
            vocab: Map<String, Long>
        ): LongArray = OfflineTtsEngine.tokenizeThaiForVits(normalizedThaiText, vocab)
    }
}
