package com.example.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.audio.WavAudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "OfflineTtsEngine"

/**
 * Domain exceptions thrown by [OfflineTtsEngine] to ensure transparent failure reporting
 * without ever substituting fake DSP tones for neural speech synthesis.
 */
sealed class OfflineTtsException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause) {
    class ModelNotFoundException(val expectedModelPath: String) : OfflineTtsException(
        "ยังไม่ได้ติดตั้งไฟล์โมเดลเสียงภาษาไทยออฟไลน์ (ONNX: $expectedModelPath) " +
            "และระบบจะไม่ใช้เสียงสังเคราะห์คลื่นความถี่ (DSP Tone) แทนเสียงพูดมนุษย์"
    )

    class InvalidModelFileException(val modelPath: String, reason: String) : OfflineTtsException(
        "ไฟล์โมเดล ONNX ไม่ถูกต้องหรือเสียหาย ($modelPath): $reason"
    )

    class RuntimeInitializationException(reason: String, cause: Throwable? = null) : OfflineTtsException(
        "ไม่สามารถเริ่มต้นรันไทม์ประสาทเทียม ONNX Runtime ได้: $reason",
        cause
    )

    class InferenceException(reason: String, cause: Throwable? = null) : OfflineTtsException(
        "การรันโมเดลประสาทเทียม ONNX Runtime ล้มเหลว: $reason",
        cause
    )
}

/**
 * Result of a real neural synthesis pass from [OfflineTtsEngine].
 */
data class OfflineNeuralSynthesisResult(
    val wavBytes: ByteArray,
    val pcm16Bytes: ByteArray,
    val sampleRateHz: Int,
    val durationMs: Long,
    val tokenCount: Int,
    val inferenceLatencyMs: Long,
    val modelPathUsed: String,
    val engineBackendUsed: String = "ONNX_VITS_NEURAL_TH",
    val voiceProfileUsed: String = "th-TH-vits-onnx"
)

/**
 * Production On-Device Thai Neural TTS Engine powered by Microsoft ONNX Runtime
 * (`com.microsoft.onnxruntime:onnxruntime-android`) supporting:
 * 1. **VIZINTZOR/F5-TTS-THAI (`VIZINTZOR/F5-TH-ONNX`)** 3-subgraph Flow-Matching Diffusion Transformer
 *    + `charactr/vocos-mel-24khz` 24,000 Hz zero-shot voice cloning engine via [f5TtsEngine].
 * 2. **Meta MMS-TTS Thai VITS (`facebook/mms-tts-tha`)** 16,000 Hz quantized ONNX low-latency engine
 *    (`assets/models/thai_vits_onnx/model.onnx`) for instant offline Thai navigation and fallback synthesis.
 * 3. **Checksum-Verified Model & Asset Management** via [storageManager] (`SHA-256` validation, atomic staging,
 *    and strict rejection of 0-byte or corrupted placeholder files).
 *
 * Strictly prohibits any non-neural DSP sine-wave tone fallback.
 */
class OfflineTtsEngine(
    private val context: Context,
    private val autoStageBundledAssets: Boolean = true
) : Closeable {

    private val sessionMutex = Mutex()

    val f5TtsEngine: F5TtsThaiEngine by lazy {
        F5TtsThaiEngine(context.applicationContext, autoStageBundledAssets)
    }

    val storageManager: OfflineModelStorageManager by lazy {
        OfflineModelStorageManager(context.applicationContext)
    }

    val modelDir: File by lazy {
        File(context.filesDir, DEFAULT_MODEL_SUBDIR).apply { mkdirs() }
    }

    val modelFile: File
        get() = File(modelDir, DEFAULT_MODEL_FILENAME)

    val vocabJsonFile: File
        get() = File(modelDir, DEFAULT_VOCAB_JSON_FILENAME)

    val tokensFile: File
        get() = File(modelDir, DEFAULT_TOKENS_FILENAME)

    val configFile: File
        get() = File(modelDir, DEFAULT_CONFIG_FILENAME)

    val licenseFile: File
        get() = File(modelDir, DEFAULT_LICENSE_FILENAME)

    @Volatile
    private var activeSession: OrtSession? = null

    @Volatile
    private var loadedModelPath: String? = null

    @Volatile
    private var loadedModelLastModified: Long = -1L

    @Volatile
    private var modelSampleRateHz: Int = DEFAULT_VITS_SAMPLE_RATE_HZ

    @Volatile
    private var defaultNoiseScale: Float = 0.667f

    @Volatile
    private var defaultNoiseScaleW: Float = 0.8f

    @Volatile
    private var cachedStorageSummary: OfflineStorageSummary? = null

    @Volatile
    private var cachedStorageTimestampMs: Long = 0L

    /**
     * Exact 71-entry Thai VITS character vocabulary from `facebook/mms-tts-tha` (`vocab.json` / `tokens.txt`),
     * used as a built-in fallback if external `vocab.json` / `tokens.txt` files are not yet staged.
     * Note: In `facebook/mms-tts-tha`, token ID `0` (`"า"`) is also the `pad_token` (`blankId = 0L`),
     * and token ID `70` is `' '` (space).
     */
    private val mmsThaiVitsVocab: Map<Char, Long> by lazy {
        mapOf(
            'า' to 0L, 'น' to 1L, '่' to 2L, 'ร' to 3L, 'เ' to 4L,
            '้' to 5L, 'อ' to 6L, 'ง' to 7L, 'ก' to 8L, 'ว' to 9L,
            'ะ' to 10L, 'ั' to 11L, 'ม' to 12L, 'ท' to 13L, 'พ' to 14L,
            'ย' to 15L, 'ล' to 16L, 'จ' to 17L, 'ี' to 18L, 'ค' to 19L,
            'ต' to 20L, 'ด' to 21L, 'ห' to 22L, 'ข' to 23L, 'ิ' to 24L,
            'แ' to 25L, 'ส' to 26L, 'บ' to 27L, 'ป' to 28L, 'ไ' to 29L,
            'ู' to 30L, 'ใ' to 31L, '็' to 32L, 'ื' to 33L, '์' to 34L,
            'ช' to 35L, 'ุ' to 36L, 'ึ' to 37L, 'ํ' to 38L, 'โ' to 39L,
            'ผ' to 40L, 'ถ' to 41L, 'ญ' to 42L, 'ซ' to 43L, 'ธ' to 44L,
            'ศ' to 45L, 'ณ' to 46L, 'ษ' to 47L, 'ฟ' to 48L, 'ภ' to 49L,
            'ฉ' to 50L, 'ฝ' to 51L, 'ฐ' to 52L, 'ฤ' to 53L, 'ฏ' to 54L,
            'ฮ' to 55L, 'ฆ' to 56L, '๋' to 57L, 'ฎ' to 58L, '\'' to 59L,
            '0' to 60L, '๊' to 61L, 'ฑ' to 62L, '1' to 63L, '4' to 64L,
            '2' to 65L, '-' to 66L, 'ฬ' to 67L, 'ฒ' to 68L, 'ฌ' to 69L,
            ' ' to 70L
        )
    }

    /**
     * Inspects the current ONNX Runtime environment and on-device model file state
     * (including both F5-TTS-THAI and Thai VITS ONNX assets).
     */
    fun inspectRuntimeStatus(): OfflineNeuralEngineInfo {
        if (autoStageBundledAssets) {
            stageBundledAssetModelIfAvailable()
            f5TtsEngine.stageBundledF5AssetsIfAvailable()
        }
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val isArm64 = abis.any {
            it.contains("arm64", ignoreCase = true) || it.contains("x86_64", ignoreCase = true)
        }
        var ortOk = false
        var ortVer = "1.20.0"
        try {
            val env = OrtEnvironment.getEnvironment()
            if (env != null) {
                ortOk = true
                ortVer = env.version
            }
        } catch (e: Throwable) {
            ortOk = runCatching {
                Class.forName(
                    "ai.onnxruntime.OrtEnvironment",
                    false,
                    OfflineTtsEngine::class.java.classLoader
                ) != null
            }.getOrDefault(false)
            Log.w(TAG, "ONNX Runtime native environment note: ${e.message}")
        }

        val exists = isNeuralModelReady()
        val f5AssetsReady = f5TtsEngine.areConditioningAssetsInstalled()
        val f5GraphsReady = f5TtsEngine.areF5OnnxSubgraphsInstalled()

        val now = System.currentTimeMillis()
        val summary = cachedStorageSummary?.takeIf { now - cachedStorageTimestampMs < 30_000L }
            ?: runCatching {
                storageManager.inspectStorageSummary(stageBundledFirst = false).also {
                    cachedStorageSummary = it
                    cachedStorageTimestampMs = now
                }
            }.getOrNull()

        val verifiedCount = summary?.artifacts?.count { it.checksumMatches } ?: 0
        val totalBytes = summary?.totalInstalledBytes ?: if (modelFile.exists()) modelFile.length() else 0L

        return OfflineNeuralEngineInfo(
            ortAvailable = ortOk,
            ortVersion = ortVer,
            supportedAbis = abis,
            isArm64Compatible = isArm64,
            modelDirectoryPath = modelDir.absolutePath,
            modelFilePresent = exists,
            modelFileSizeBytes = if (modelFile.exists()) modelFile.length() else 0L,
            tokensFilePresent = (vocabJsonFile.exists() && vocabJsonFile.length() > 0L) ||
                (tokensFile.exists() && tokensFile.length() > 0L),
            f5ConditioningAssetsReady = f5AssetsReady,
            f5SubgraphsInstalled = f5GraphsReady,
            f5ModelDirectoryPath = f5TtsEngine.modelDir.absolutePath,
            verifiedChecksumCount = verifiedCount,
            totalOfflineStorageBytes = totalBytes
        )
    }

    /**
     * Returns true when a valid non-empty `model.onnx` file or full F5-TH-ONNX subgraph set is present on disk.
     */
    fun isNeuralModelReady(candidateFile: File = modelFile): Boolean {
        if (autoStageBundledAssets && candidateFile.absolutePath == modelFile.absolutePath) {
            stageBundledAssetModelIfAvailable()
            f5TtsEngine.stageBundledF5AssetsIfAvailable()
        }
        val vitsReady = candidateFile.exists() && candidateFile.isFile && candidateFile.length() > MIN_VALID_ONNX_BYTES
        val f5Ready = f5TtsEngine.areF5OnnxSubgraphsInstalled()
        return vitsReady || f5Ready
    }

    /**
     * Returns true if an [OrtSession] is currently loaded and ready in memory.
     */
    fun isSessionLoaded(): Boolean = activeSession != null

    /**
     * Stages the bundled Thai VITS ONNX model and companion tokenizer/config/license files from APK
     * `assets/models/thai_vits_onnx/` into `filesDir/models/thai_vits_onnx/` if not yet copied.
     */
    fun stageBundledAssetModelIfAvailable() {
        val companionFiles = listOf(
            DEFAULT_MODEL_FILENAME,
            DEFAULT_VOCAB_JSON_FILENAME,
            DEFAULT_TOKENS_FILENAME,
            DEFAULT_TOKENIZER_CONFIG_FILENAME,
            DEFAULT_TOKENIZER_JSON_FILENAME,
            DEFAULT_CONFIG_FILENAME,
            DEFAULT_LICENSE_FILENAME
        )

        runCatching {
            val assetManager = context.assets
            if (assetManager != null) {
                val assetEntries = assetManager.list(DEFAULT_MODEL_SUBDIR)?.toList().orEmpty()
                for (fileName in companionFiles) {
                    if (fileName in assetEntries) {
                        val destFile = File(modelDir, fileName)
                        val minLen = if (fileName == DEFAULT_MODEL_FILENAME) MIN_VALID_ONNX_BYTES else 1L
                        if (!destFile.exists() || destFile.length() <= minLen) {
                            assetManager.open("$DEFAULT_MODEL_SUBDIR/$fileName").use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            }
        }

        // Fallback for local JVM / Robolectric runs if AssetManager did not resolve the source tree
        if (!modelFile.exists() || modelFile.length() <= MIN_VALID_ONNX_BYTES) {
            val candidateDirs = listOf(
                File("app/src/main/assets/$DEFAULT_MODEL_SUBDIR"),
                File("src/main/assets/$DEFAULT_MODEL_SUBDIR"),
                File("../app/src/main/assets/$DEFAULT_MODEL_SUBDIR")
            )
            val sourceDir = candidateDirs.firstOrNull {
                File(it, DEFAULT_MODEL_FILENAME).let { f -> f.exists() && f.length() > MIN_VALID_ONNX_BYTES }
            }
            if (sourceDir != null) {
                for (fileName in companionFiles) {
                    val srcFile = File(sourceDir, fileName)
                    val destFile = File(modelDir, fileName)
                    if (srcFile.exists() && (!destFile.exists() || destFile.length() == 0L)) {
                        runCatching { srcFile.copyTo(destFile, overwrite = true) }
                    }
                }
            }
        }
    }

    /**
     * Explicitly loads and validates the ONNX neural TTS model into an [OrtSession].
     * Fails with [OfflineTtsException] if the model file is missing or invalid.
     */
    suspend fun loadModel(
        customModelFile: File = modelFile,
        customTokensFile: File? = null
    ): Result<OrtSession> = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            if (autoStageBundledAssets && customModelFile.absolutePath == modelFile.absolutePath) {
                stageBundledAssetModelIfAvailable()
                f5TtsEngine.stageBundledF5AssetsIfAvailable()
            }

            if (!customModelFile.exists() || !customModelFile.isFile) {
                return@withLock Result.failure(
                    OfflineTtsException.ModelNotFoundException(customModelFile.absolutePath)
                )
            }

            if (customModelFile.length() <= MIN_VALID_ONNX_BYTES) {
                return@withLock Result.failure(
                    OfflineTtsException.InvalidModelFileException(
                        customModelFile.absolutePath,
                        "ขนาดไฟล์เล็กกว่าเกณฑ์ขั้นต่ำของโมเดลประสาทเทียม ONNX (${customModelFile.length()} bytes)"
                    )
                )
            }

            if (customTokensFile != null && customTokensFile.exists() && customTokensFile.absolutePath != tokensFile.absolutePath) {
                runCatching { customTokensFile.copyTo(tokensFile, overwrite = true) }
            }

            parseModelConfigIfPresent()

            val lastMod = customModelFile.lastModified()
            val currentSession = activeSession
            if (currentSession != null &&
                loadedModelPath == customModelFile.absolutePath &&
                loadedModelLastModified == lastMod
            ) {
                return@withLock Result.success(currentSession)
            }

            return@withLock try {
                val env = OrtEnvironment.getEnvironment()
                    ?: return@withLock Result.failure(
                        OfflineTtsException.RuntimeInitializationException("OrtEnvironment.getEnvironment() returned null")
                    )

                currentSession?.close()

                val sessionOptions = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(true)
                }

                val newSession = env.createSession(customModelFile.absolutePath, sessionOptions)
                if (newSession.numInputs <= 0L || newSession.numOutputs <= 0L) {
                    newSession.close()
                    return@withLock Result.failure(
                        OfflineTtsException.InvalidModelFileException(
                            customModelFile.absolutePath,
                            "กราฟ ONNX ไม่มีโหนด Input หรือ Output ที่ใช้งานได้"
                        )
                    )
                }

                activeSession = newSession
                loadedModelPath = customModelFile.absolutePath
                loadedModelLastModified = lastMod
                Log.i(
                    TAG,
                    "Loaded ONNX Thai TTS model: ${customModelFile.absolutePath} " +
                        "(inputs=${newSession.inputNames}, outputs=${newSession.outputNames})"
                )
                Result.success(newSession)
            } catch (t: Throwable) {
                Result.failure(
                    OfflineTtsException.RuntimeInitializationException(
                        t.localizedMessage ?: t.javaClass.simpleName,
                        t
                    )
                )
            }
        }
    }

    private fun parseModelConfigIfPresent() {
        if (!configFile.exists() || configFile.length() == 0L) return
        runCatching {
            val root = JSONObject(configFile.readText(Charsets.UTF_8))
            val audioObj = root.optJSONObject("audio")
            val dataObj = root.optJSONObject("data")
            val sr = audioObj?.optInt("sample_rate", 0)?.takeIf { it in 8000..48000 }
                ?: dataObj?.optInt("sampling_rate", 0)?.takeIf { it in 8000..48000 }
                ?: root.optInt("sampling_rate", 0).takeIf { it in 8000..48000 }
                ?: root.optInt("sample_rate", DEFAULT_VITS_SAMPLE_RATE_HZ).takeIf { it in 8000..48000 }
                ?: DEFAULT_VITS_SAMPLE_RATE_HZ
            modelSampleRateHz = sr

            if (root.has("noise_scale")) {
                defaultNoiseScale = root.optDouble("noise_scale", 0.667).toFloat().coerceIn(0.1f, 1.5f)
            }
            if (root.has("noise_scale_duration")) {
                defaultNoiseScaleW = root.optDouble("noise_scale_duration", 0.8).toFloat().coerceIn(0.1f, 1.5f)
            }
            val inferenceObj = root.optJSONObject("inference")
            if (inferenceObj != null) {
                defaultNoiseScale = inferenceObj.optDouble("noise_scale", defaultNoiseScale.toDouble()).toFloat().coerceIn(0.1f, 1.5f)
                defaultNoiseScaleW = inferenceObj.optDouble("noise_w", defaultNoiseScaleW.toDouble()).toFloat().coerceIn(0.1f, 1.5f)
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to parse optional config.json: ${e.message}")
        }
    }

    /**
     * Converts Thai text into VITS blank-interspersed token IDs `[0, id_1, 0, id_2, ..., 0]`.
     * Normalizes numbers and Thai digits via [ThaiTextOptimizer] so every spoken word maps
     * cleanly to the 71-token `facebook/mms-tts-tha` vocabulary.
     */
    fun tokenizeThaiForVits(normalizedThaiText: String): LongArray {
        val preprocessed = ThaiTextOptimizer.optimizeThaiText(normalizedThaiText)
        val cleaned = Normalizer.normalize(preprocessed.trim(), Normalizer.Form.NFC)
        val vocab = loadCustomVocabIfPresent() ?: mmsThaiVitsVocab
        val stringVocab = vocab.entries.associate { (k, v) -> k.toString() to v }
        return tokenizeThaiForVits(cleaned, stringVocab)
    }

    private fun loadCustomVocabIfPresent(): Map<Char, Long>? {
        if (vocabJsonFile.exists() && vocabJsonFile.length() > 0L) {
            runCatching {
                val json = JSONObject(vocabJsonFile.readText(Charsets.UTF_8))
                val map = mutableMapOf<Char, Long>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = json.optLong(key, -1L)
                    if (id >= 0L && key.length == 1) {
                        map[key[0]] = id
                    }
                }
                if (map.isNotEmpty()) return map
            }.onFailure { e ->
                Log.w(TAG, "Failed to parse vocab.json: ${e.message}")
            }
        }

        if (!tokensFile.exists() || tokensFile.length() == 0L) return null
        return try {
            val map = mutableMapOf<Char, Long>()
            tokensFile.readLines(Charsets.UTF_8).forEachIndexed { idx, rawLine ->
                val line = rawLine.trimEnd('\r', '\n')
                if (line.isBlank() && !line.startsWith(" ")) return@forEachIndexed
                val lastSpace = line.lastIndexOf(' ')
                if (lastSpace > 0) {
                    val sym = line.substring(0, lastSpace)
                    val idStr = line.substring(lastSpace + 1).trim()
                    val id = idStr.toLongOrNull() ?: idx.toLong()
                    when {
                        sym == "<pad>" || sym == "_" || sym == "<blank>" -> map['_'] = id
                        sym == "<space>" || sym == "▁" -> map[' '] = id
                        sym.length == 1 -> map[sym[0]] = id
                    }
                } else if (line.isNotEmpty()) {
                    map[line[0]] = idx.toLong()
                }
            }
            if (map.isNotEmpty()) map else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse custom tokens.txt, using built-in MMS Thai VITS vocab", e)
            null
        }
    }

    /**
     * Executes real ONNX Runtime neural speech synthesis:
     * - First attempts **F5-TTS-THAI (`VIZINTZOR/F5-TH-ONNX`)** 3-subgraph inference (`Preprocess.onnx` ->
     *   `Transformer.onnx` -> `Vocoder.onnx` at 24,000 Hz) if the F5 ONNX subgraphs are installed and
     *   [offlineVoiceProfile] prefers F5 zero-shot voice conditioning.
     * - Otherwise executes the bundled low-latency **Thai VITS ONNX (`facebook/mms-tts-tha`)** neural model
     *   at 16,000 Hz (while still validating F5-TTS-THAI reference voice conditioning and clause segmentation).
     *
     * Never falls back to synthetic DSP sine waves or non-neural audio generators.
     */
    suspend fun synthesize(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        voiceWarmth: Int = 50,
        speakerId: Long = 0L,
        sampleRateOverrideHz: Int? = null,
        offlineVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
        f5TokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
        f5NfeSteps: Int = F5SynthesisParams.DEFAULT_NFE_STEPS,
        customRefAudioFile: File? = null,
        customRefText: String? = null
    ): Result<OfflineNeuralSynthesisResult> = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()

        if (autoStageBundledAssets) {
            stageBundledAssetModelIfAvailable()
            f5TtsEngine.stageBundledF5AssetsIfAvailable()
        }

        // 1. If all 3 F5-TH-ONNX subgraphs are installed on disk and the selected voice profile
        //    targets F5-TTS-THAI zero-shot synthesis, run the 3-stage F5-TTS-THAI ONNX pipeline first.
        if (offlineVoiceProfile.prefersF5ZeroShot && f5TtsEngine.areF5OnnxSubgraphsInstalled()) {
            val f5Params = F5SynthesisParams(
                speed = speechRate.coerceIn(0.5f, 2.0f),
                nfeSteps = f5NfeSteps.coerceIn(4, 64),
                tokenizerMode = f5TokenizerMode
            )
            val f5Res = f5TtsEngine.synthesizeWithF5Onnx(
                targetText = text,
                voiceProfile = offlineVoiceProfile,
                params = f5Params,
                customRefAudioFile = customRefAudioFile,
                customRefText = customRefText
            )
            if (f5Res.isSuccess) {
                val f5Out = f5Res.getOrThrow()
                val pitchFactor = if (abs(pitch - 1.0f) > 0.03f) pitch.coerceIn(0.75f, 1.35f) else 1.0f
                val finalWavBytes = if (
                    abs(volume - 1.0f) > 0.02f ||
                    voiceWarmth != 50 ||
                    abs(pitchFactor - 1.0f) > 0.02f
                ) {
                    WavAudioUtils.applyAudioPostProcessing(
                        wavOrPcmBytes = f5Out.wavBytes,
                        sampleRateHz = f5Out.sampleRateHz,
                        volume = volume,
                        voiceWarmth = voiceWarmth,
                        speedRatio = pitchFactor
                    )
                } else {
                    f5Out.wavBytes
                }
                val finalPcm = WavAudioUtils.extractPcmFromWav(finalWavBytes)
                val durationMs = WavAudioUtils.calculateDurationMs(finalWavBytes, f5Out.sampleRateHz)
                return@withContext Result.success(
                    OfflineNeuralSynthesisResult(
                        wavBytes = finalWavBytes,
                        pcm16Bytes = finalPcm,
                        sampleRateHz = f5Out.sampleRateHz,
                        durationMs = durationMs,
                        tokenCount = f5Out.tokenCount,
                        inferenceLatencyMs = f5Out.inferenceLatencyMs,
                        modelPathUsed = f5Out.modelPathUsed,
                        engineBackendUsed = "F5_TTS_THAI_ONNX_24KHZ",
                        voiceProfileUsed = offlineVoiceProfile.id
                    )
                )
            } else {
                Log.w(
                    TAG,
                    "F5-TH-ONNX subgraph inference returned error (${f5Res.exceptionOrNull()?.message}); " +
                        "falling back to bundled Thai VITS ONNX neural model"
                )
            }
        }

        // 2. Execute bundled low-latency Thai VITS ONNX model (`facebook/mms-tts-tha`)
        val sessionResult = loadModel(modelFile)
        if (sessionResult.isFailure) {
            return@withContext Result.failure(
                sessionResult.exceptionOrNull()
                    ?: OfflineTtsException.ModelNotFoundException(modelFile.absolutePath)
            )
        }

        val session = sessionResult.getOrThrow()
        val tokenIds = tokenizeThaiForVits(text)
        val seqLen = tokenIds.size.toLong()
        val lengthScale = (1.0f / speechRate.coerceIn(0.5f, 2.0f)).coerceIn(0.5f, 2.0f)

        val allocatedTensors = mutableListOf<OnnxTensor>()

        try {
            val env = OrtEnvironment.getEnvironment()
            val inputNames = session.inputNames.toList()
            if (inputNames.isEmpty()) {
                return@withContext Result.failure(
                    OfflineTtsException.InferenceException("โมเดล ONNX ไม่มี Input Tensor")
                )
            }

            val inputs = linkedMapOf<String, OnnxTensor>()

            // 1. Primary token IDs input ("input_ids", "x", "input", or "text")
            val idsName = inputNames.firstOrNull {
                it.equals("input_ids", ignoreCase = true) ||
                    it.equals("input", ignoreCase = true) ||
                    it.equals("x", ignoreCase = true) ||
                    it.equals("text", ignoreCase = true)
            } ?: inputNames.first()

            val idsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokenIds),
                longArrayOf(1L, seqLen)
            ).also { allocatedTensors.add(it) }
            inputs[idsName] = idsTensor

            // 2. HuggingFace / Optimum VitsModel signature: "attention_mask" (int64[1, seqLen])
            val attentionMaskName = inputNames.firstOrNull {
                it.equals("attention_mask", ignoreCase = true)
            }
            if (attentionMaskName != null) {
                val maskArray = LongArray(tokenIds.size) { 1L }
                val maskTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(maskArray),
                    longArrayOf(1L, seqLen)
                ).also { allocatedTensors.add(it) }
                inputs[attentionMaskName] = maskTensor
            }

            // 3. Sequence length input ("input_lengths", "x_length", "x_lengths", "text_lengths")
            val lengthsName = inputNames.firstOrNull {
                it.equals("input_lengths", ignoreCase = true) ||
                    it.equals("x_length", ignoreCase = true) ||
                    it.equals("x_lengths", ignoreCase = true) ||
                    it.equals("text_lengths", ignoreCase = true)
            }
            if (lengthsName != null) {
                val lengthsTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(seqLen)),
                    longArrayOf(1L)
                ).also { allocatedTensors.add(it) }
                inputs[lengthsName] = lengthsTensor
            }

            // 4. Piper VITS combined "scales" input (float32[3])
            val scalesName = inputNames.firstOrNull {
                it.equals("scales", ignoreCase = true)
            }
            var graphConsumedSpeechRate = false
            if (scalesName != null) {
                val scalesTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatArrayOf(defaultNoiseScale, lengthScale, defaultNoiseScaleW)),
                    longArrayOf(3L)
                ).also { allocatedTensors.add(it) }
                inputs[scalesName] = scalesTensor
                graphConsumedSpeechRate = true
            }

            // 5. Sherpa-ONNX MMS VITS separate scale inputs ("noise_scale", "length_scale", "noise_scale_w")
            inputNames.firstOrNull { it.equals("noise_scale", ignoreCase = true) }?.let { name ->
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatArrayOf(defaultNoiseScale)),
                    longArrayOf(1L)
                ).also { allocatedTensors.add(it) }
                inputs[name] = tensor
            }
            inputNames.firstOrNull { it.equals("length_scale", ignoreCase = true) }?.let { name ->
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatArrayOf(lengthScale)),
                    longArrayOf(1L)
                ).also { allocatedTensors.add(it) }
                inputs[name] = tensor
                graphConsumedSpeechRate = true
            }
            inputNames.firstOrNull {
                it.equals("noise_scale_w", ignoreCase = true) || it.equals("noise_w", ignoreCase = true)
            }?.let { name ->
                val tensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatArrayOf(defaultNoiseScaleW)),
                    longArrayOf(1L)
                ).also { allocatedTensors.add(it) }
                inputs[name] = tensor
            }

            // 6. Optional multi-speaker ID ("sid" or "speaker_id")
            inputNames.firstOrNull {
                it.equals("sid", ignoreCase = true) || it.equals("speaker_id", ignoreCase = true)
            }?.let { sidName ->
                val sidTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(speakerId)),
                    longArrayOf(1L)
                ).also { allocatedTensors.add(it) }
                inputs[sidName] = sidTensor
            }

            val floatSamples: FloatArray = sessionMutex.withLock {
                session.run(inputs).use { results ->
                    if (results.size() == 0) {
                        FloatArray(0)
                    } else {
                        val waveformVal = results.get("waveform")?.orElse(null)?.value
                            ?: results[0].value
                        extractFloatWaveform(waveformVal)
                    }
                }
            }

            if (floatSamples.isEmpty()) {
                return@withContext Result.failure(
                    OfflineTtsException.InferenceException("โมเดลประสาทเทียม VITS ONNX คืนค่าคลื่นเสียงว่างเปล่า")
                )
            }

            val rawPcmBytes = convertFloatWaveformToPcm16Le(floatSamples)
            val effectiveSampleRate = sampleRateOverrideHz ?: modelSampleRateHz

            val baseWavBytes = WavAudioUtils.ensureWavContainer(
                audioBytes = rawPcmBytes,
                sampleRateHz = effectiveSampleRate
            )

            // Apply post-processing for volume, warmth, pitch, and (if not consumed by ONNX graph) speechRate
            val rateFactor = if (!graphConsumedSpeechRate) speechRate.coerceIn(0.5f, 2.0f) else 1.0f
            val pitchFactor = if (abs(pitch - 1.0f) > 0.03f) pitch.coerceIn(0.75f, 1.35f) else 1.0f
            val combinedSpeedRatio = (rateFactor * pitchFactor).coerceIn(0.5f, 2.0f)

            val finalWavBytes = if (
                abs(volume - 1.0f) > 0.02f ||
                voiceWarmth != 50 ||
                abs(combinedSpeedRatio - 1.0f) > 0.02f
            ) {
                WavAudioUtils.applyAudioPostProcessing(
                    wavOrPcmBytes = baseWavBytes,
                    sampleRateHz = effectiveSampleRate,
                    volume = volume,
                    voiceWarmth = voiceWarmth,
                    speedRatio = combinedSpeedRatio
                )
            } else {
                baseWavBytes
            }

            val finalPcmBytes = WavAudioUtils.extractPcmFromWav(finalWavBytes)
            val durationMs = WavAudioUtils.calculateDurationMs(finalWavBytes, effectiveSampleRate)
            val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            Result.success(
                OfflineNeuralSynthesisResult(
                    wavBytes = finalWavBytes,
                    pcm16Bytes = finalPcmBytes,
                    sampleRateHz = effectiveSampleRate,
                    durationMs = durationMs,
                    tokenCount = tokenIds.size,
                    inferenceLatencyMs = latencyMs,
                    modelPathUsed = modelFile.absolutePath,
                    engineBackendUsed = "ONNX_VITS_NEURAL_TH",
                    voiceProfileUsed = offlineVoiceProfile.id
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "ONNX VITS Thai neural inference failed", t)
            Result.failure(
                OfflineTtsException.InferenceException(
                    t.localizedMessage ?: t.javaClass.simpleName,
                    t
                )
            )
        } finally {
            for (tensor in allocatedTensors) {
                runCatching { tensor.close() }
            }
        }
    }

    /**
     * Convenience wrapper returning the RIFF WAV byte array from real ONNX neural inference.
     */
    suspend fun synthesizeThaiNeuralWav(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        voiceWarmth: Int = 50,
        sampleRateHz: Int = modelSampleRateHz,
        offlineVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
        f5TokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
        f5NfeSteps: Int = F5SynthesisParams.DEFAULT_NFE_STEPS
    ): Result<ByteArray> {
        return synthesize(
            text = text,
            speechRate = speechRate,
            pitch = pitch,
            volume = volume,
            voiceWarmth = voiceWarmth,
            sampleRateOverrideHz = sampleRateHz,
            offlineVoiceProfile = offlineVoiceProfile,
            f5TokenizerMode = f5TokenizerMode,
            f5NfeSteps = f5NfeSteps
        ).map { it.wavBytes }
    }

    /**
     * Benchmarks real Thai sentences and short navigation commands on the active offline neural engine,
     * measuring cold/warm session load time, F5-TTS-THAI reference audio conditioning time,
     * tokenization, inference latency, audio duration, and Real-Time Factor (RTF).
     */
    suspend fun runOfflineBenchmark(
        navigationPhrases: List<String> = listOf(
            "อีกสามร้อยเมตร เลี้ยวซ้าย",
            "เลี้ยวขวาในอีก 200 ม."
        ),
        generalSentences: List<String> = listOf(
            "ฉันเดินทางไปเที่ยวที่จังหวัดเชียงใหม่ในช่วงฤดูหนาวเพื่อสัมผัสอากาศเย็นสบาย"
        )
    ): Result<OfflineBenchmarkReport> = withContext(Dispatchers.Default) {
        try {
            // Measure cold load (or initial load)
            val tCold0 = System.currentTimeMillis()
            val loadRes = loadModel()
            if (loadRes.isFailure) {
                return@withContext Result.failure(
                    loadRes.exceptionOrNull() ?: IllegalStateException("Failed to load offline neural model")
                )
            }
            val coldLoadMs = (System.currentTimeMillis() - tCold0).coerceAtLeast(1L)

            // Measure warm session reuse
            val tWarm0 = System.currentTimeMillis()
            loadModel().getOrThrow()
            val warmLoadMs = (System.currentTimeMillis() - tWarm0).coerceAtLeast(0L)

            // Measure F5-TTS-THAI reference audio conditioning (24,000 Hz mono resampling + RMS normalization)
            val tCond0 = System.currentTimeMillis()
            val condAudio = f5TtsEngine.conditionReferenceAudio(OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR).getOrThrow()
            val condMs = (System.currentTimeMillis() - tCond0).coerceAtLeast(1L)

            val metrics = mutableListOf<OfflineBenchmarkSampleMetrics>()

            for (navPhrase in navigationPhrases) {
                val syn = synthesize(
                    text = navPhrase,
                    speechRate = 1.05f,
                    offlineVoiceProfile = OfflineVoiceProfile.THAI_VITS_FAST_NAVIGATION
                ).getOrThrow()
                val f5Tokens = f5TtsEngine.tokenizeForF5OnnxIpa(condAudio.referenceTextTh, navPhrase)
                val maxDur = F5TtsThaiEngine.computeF5MaxDurationFrames(
                    refAudioSamples24k = condAudio.sampleCount24k,
                    refTextLength = condAudio.referenceTextTh.length,
                    genTextLength = navPhrase.length,
                    speed = 1.05f
                )
                val rtf = syn.inferenceLatencyMs.toDouble() / syn.durationMs.coerceAtLeast(1L).toDouble()
                metrics.add(
                    OfflineBenchmarkSampleMetrics(
                        phraseTh = navPhrase,
                        isNavigationCommand = true,
                        tokenCount = syn.tokenCount,
                        f5IpaTokenCount = f5Tokens.size,
                        f5MaxDurationFrames = maxDur,
                        inferenceLatencyMs = syn.inferenceLatencyMs,
                        audioDurationMs = syn.durationMs,
                        sampleRateHz = syn.sampleRateHz,
                        pcmByteSize = syn.pcm16Bytes.size,
                        realTimeFactor = rtf,
                        engineUsed = syn.engineBackendUsed
                    )
                )
            }

            for (sentence in generalSentences) {
                val syn = synthesize(
                    text = sentence,
                    speechRate = 1.0f,
                    offlineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR
                ).getOrThrow()
                val f5Tokens = f5TtsEngine.tokenizeForF5OnnxIpa(condAudio.referenceTextTh, sentence)
                val maxDur = F5TtsThaiEngine.computeF5MaxDurationFrames(
                    refAudioSamples24k = condAudio.sampleCount24k,
                    refTextLength = condAudio.referenceTextTh.length,
                    genTextLength = sentence.length,
                    speed = 1.0f
                )
                val rtf = syn.inferenceLatencyMs.toDouble() / syn.durationMs.coerceAtLeast(1L).toDouble()
                metrics.add(
                    OfflineBenchmarkSampleMetrics(
                        phraseTh = sentence,
                        isNavigationCommand = false,
                        tokenCount = syn.tokenCount,
                        f5IpaTokenCount = f5Tokens.size,
                        f5MaxDurationFrames = maxDur,
                        inferenceLatencyMs = syn.inferenceLatencyMs,
                        audioDurationMs = syn.durationMs,
                        sampleRateHz = syn.sampleRateHz,
                        pcmByteSize = syn.pcm16Bytes.size,
                        realTimeFactor = rtf,
                        engineUsed = syn.engineBackendUsed
                    )
                )
            }

            val navLatencies = metrics.filter { it.isNavigationCommand }.map { it.inferenceLatencyMs }
            val sentLatencies = metrics.filter { !it.isNavigationCommand }.map { it.inferenceLatencyMs }
            val avgRtf = metrics.map { it.realTimeFactor }.average()

            Result.success(
                OfflineBenchmarkReport(
                    coldLoadLatencyMs = coldLoadMs,
                    warmLoadLatencyMs = warmLoadMs,
                    referenceAudioConditioningMs = condMs,
                    referenceAudioFrames24k = condAudio.melFrameEstimate,
                    samples = metrics,
                    averageNavigationLatencyMs = if (navLatencies.isNotEmpty()) navLatencies.average().toLong() else 0L,
                    averageSentenceLatencyMs = if (sentLatencies.isNotEmpty()) sentLatencies.average().toLong() else 0L,
                    averageRealTimeFactor = avgRtf
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    override fun close() {
        runCatching {
            activeSession?.close()
        }
        runCatching {
            f5TtsEngine.close()
        }
        activeSession = null
        loadedModelPath = null
        loadedModelLastModified = -1L
    }

    companion object {
        const val DEFAULT_MODEL_SUBDIR = "models/thai_vits_onnx"
        const val DEFAULT_MODEL_FILENAME = "model.onnx"
        const val DEFAULT_VOCAB_JSON_FILENAME = "vocab.json"
        const val DEFAULT_TOKENS_FILENAME = "tokens.txt"
        const val DEFAULT_TOKENIZER_CONFIG_FILENAME = "tokenizer_config.json"
        const val DEFAULT_TOKENIZER_JSON_FILENAME = "tokenizer.json"
        const val DEFAULT_CONFIG_FILENAME = "config.json"
        const val DEFAULT_LICENSE_FILENAME = "LICENSE.txt"
        const val DEFAULT_VITS_SAMPLE_RATE_HZ = 16000
        const val MIN_VALID_ONNX_BYTES = 1024L

        /**
         * Pure static VITS blank-interspersed tokenizer (`[0, id_1, 0, id_2, ..., 0]`).
         * Uses token ID `0` (`<pad>` / `"า"` in `facebook/mms-tts-tha`) as the VITS Monotonic Alignment
         * Search blank token and maps whitespace/punctuation to the vocabulary space token (`" "` = `70`).
         */
        fun tokenizeThaiForVits(
            normalizedThaiText: String,
            vocab: Map<String, Long>
        ): LongArray {
            val blankId = vocab["<pad>"] ?: vocab["_"] ?: 0L
            val spaceId = vocab[" "] ?: 70L
            val rawIds = mutableListOf<Long>()
            var lastWasSpace = false

            for (ch in normalizedThaiText) {
                val directId = vocab[ch.toString()]
                if (directId != null) {
                    if (ch == ' ') {
                        if (!lastWasSpace && rawIds.isNotEmpty()) {
                            rawIds.add(directId)
                            lastWasSpace = true
                        }
                    } else {
                        rawIds.add(directId)
                        lastWasSpace = false
                    }
                } else if (ch.isWhitespace() || ch == ',' || ch == '.' || ch == ';' || ch == ':') {
                    if (!lastWasSpace && rawIds.isNotEmpty()) {
                        rawIds.add(spaceId)
                        lastWasSpace = true
                    }
                }
            }

            if (rawIds.isEmpty()) {
                return longArrayOf(blankId, spaceId, blankId)
            }

            val interspersed = LongArray(rawIds.size * 2 + 1)
            var ptr = 0
            interspersed[ptr++] = blankId
            for (token in rawIds) {
                interspersed[ptr++] = token
                interspersed[ptr++] = blankId
            }
            return interspersed
        }

        /**
         * Recursively flattens a multi-dimensional ONNX float output (`float[]`, `float[][]`, `float[][][]`)
         * into a 1D mono `FloatArray`.
         */
        fun extractFloatWaveform(rawOutput: Any?): FloatArray {
            return when (rawOutput) {
                is FloatArray -> rawOutput
                is Array<*> -> {
                    val first = rawOutput.firstOrNull()
                    extractFloatWaveform(first)
                }
                else -> FloatArray(0)
            }
        }

        /**
         * Peak-normalizes (when clipping would occur) and encodes a neural `FloatArray` waveform
         * into 16-bit signed little-endian mono PCM bytes (`AudioFormat.ENCODING_PCM_16BIT`).
         */
        fun convertFloatWaveformToPcm16Le(floatSamples: FloatArray): ByteArray {
            if (floatSamples.isEmpty()) return ByteArray(0)
            var peak = 0f
            for (sample in floatSamples) {
                val a = abs(sample)
                if (!a.isNaN() && !a.isInfinite() && a > peak) {
                    peak = a
                }
            }
            val normScale = when {
                peak > 0.98f -> 0.98f / max(peak, 1e-6f)
                peak in 0.005f..0.22f -> (0.65f / peak).coerceAtMost(6.0f)
                else -> 1.0f
            }

            val pcmBytes = ByteArray(floatSamples.size * 2)
            val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            for (i in floatSamples.indices) {
                val raw = floatSamples[i]
                val safeSample = if (raw.isNaN() || raw.isInfinite()) 0f else (raw * normScale)
                val clamped = safeSample.coerceIn(-0.98f, 0.98f)
                shortBuf.put(i, (clamped * 32767f).toInt().toShort())
            }
            return pcmBytes
        }
    }
}
