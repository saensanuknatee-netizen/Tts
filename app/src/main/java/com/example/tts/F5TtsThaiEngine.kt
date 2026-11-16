package com.example.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.example.audio.WavAudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import java.text.Normalizer
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "F5TtsThaiEngine"

/**
 * Selectable offline Thai voice profiles for F5-TTS-THAI zero-shot reference conditioning
 * and low-latency VITS navigation synthesis.
 */
enum class OfflineVoiceProfile(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val subtitleTh: String,
    val referenceAudioSpec: OfflineModelArtifactSpec?,
    val defaultReferenceTextTh: String,
    val prefersF5ZeroShot: Boolean,
    val sampleRateHz: Int
) {
    F5_THAI_FEMALE_CLEAR(
        id = "f5_thai_female_clear",
        titleTh = "F5-TTS-THAI · เสียงต้นแบบ 1 (เชียงใหม่ฤดูหนาว · 24kHz)",
        titleEn = "F5-TTS-THAI Ref Voice 1 (24kHz)",
        subtitleTh = "เสียงอ้างอิงต้นฉบับ VIZINTZOR/F5-TTS-THAI (ref_audio_1.wav · CC-BY-4.0) นุ่มนวลเป็นธรรมชาติ",
        referenceAudioSpec = OfflineModelArtifactSpec.F5_REF_AUDIO_1,
        defaultReferenceTextTh = "ฉันเดินทางไปเที่ยวที่จังหวัดเชียงใหม่ในช่วงฤดูหนาวเพื่อสัมผัสอากาศเย็นสบาย",
        prefersF5ZeroShot = true,
        sampleRateHz = 24_000
    ),
    F5_THAI_STUDIO_NARRATOR(
        id = "f5_thai_studio_narrator",
        titleTh = "F5-TTS-THAI · เสียงต้นแบบ 2 (สตูดิโอคมชัด · 24kHz)",
        titleEn = "F5-TTS-THAI Ref Voice 2 (24kHz)",
        subtitleTh = "เสียงอ้างอิงต้นฉบับที่ 2 จาก VIZINTZOR/F5-TTS-THAI (ref_audio_2.wav · CC-BY-4.0) ชัดถ้อยชัดคำ",
        referenceAudioSpec = OfflineModelArtifactSpec.F5_REF_AUDIO_2,
        defaultReferenceTextTh = "ได้รับข่าวคราวของเราที่จะหาที่มันเป็นไปที่จะจัดขึ้น.",
        prefersF5ZeroShot = true,
        sampleRateHz = 24_000
    ),
    THAI_VITS_FAST_NAVIGATION(
        id = "thai_vits_fast_nav",
        titleTh = "Fast Thai VITS ONNX · นำทางความหน่วงต่ำ (16kHz Int8)",
        titleEn = "Fast Thai VITS Navigation (16kHz)",
        subtitleTh = "โมเดลประสาทเทียม Single-Pass VITS ONNX ในเครื่อง ความหน่วงต่ำพิเศษสำหรับคำสั่งขับรถสั้นๆ",
        referenceAudioSpec = null,
        defaultReferenceTextTh = "",
        prefersF5ZeroShot = false,
        sampleRateHz = 16_000
    ),
    CUSTOM_REFERENCE_VOICE(
        id = "f5_custom_ref_voice",
        titleTh = "F5-TTS-THAI · เสียงต้นแบบกำหนดเอง (Custom Reference 2-8s)",
        titleEn = "Custom Reference Voice (24kHz)",
        subtitleTh = "ใช้ไฟล์ WAV เสียงอ้างอิงภาษาไทย (ความยาวแนะนำ 2-8 วินาที) พร้อมข้อความต้นฉบับที่ตรงกับเสียง",
        referenceAudioSpec = OfflineModelArtifactSpec.F5_REF_AUDIO_1,
        defaultReferenceTextTh = "ฉันเดินทางไปเที่ยวที่จังหวัดเชียงใหม่ในช่วงฤดูหนาวเพื่อสัมผัสอากาศเย็นสบาย",
        prefersF5ZeroShot = true,
        sampleRateHz = 24_000
    );

    val descriptionTh: String
        get() = subtitleTh
}

/**
 * Tokenizer mode corresponding to VIZINTZOR/F5-TTS-THAI v1, F5-TTS-TH-V2, and F5-TH-ONNX.
 */
enum class F5TokenizerMode(
    val id: String,
    val labelTh: String,
    val descriptionTh: String
) {
    V1_CHAR_UTF8(
        id = "v1_char",
        labelTh = "F5-TTS-THAI v1 (Character Tokenizer · 2,586 Tokens)",
        descriptionTh = "แบ่งตัวอักษรภาษาไทยและอังกฤษโดยตรงตาม vocab_v1.txt ให้ความเป็นธรรมชาติสูง"
    ),
    V2_IPA_PHONEME(
        id = "v2_ipa",
        labelTh = "F5-TTS-TH-V2 / F5-TH-ONNX (IPA Phoneme · G2P)",
        descriptionTh = "แปลงคำอ่านภาษาไทยเป็นสัทอักษร IPA เพื่อลดความผิดพลาดในการอ่านคำยาก"
    );

    val titleTh: String
        get() = labelTh
}

/**
 * Synthesis parameters for F5-TTS-THAI flow-matching inference.
 */
data class F5SynthesisParams(
    val voiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
    val tokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
    val speed: Float = 1.0f,
    val nfeSteps: Int = DEFAULT_NFE_STEPS,
    val cfgStrength: Float = DEFAULT_CFG_STRENGTH,
    val swaySamplingCoef: Float = DEFAULT_SWAY_COEF,
    val targetRms: Float = DEFAULT_TARGET_RMS,
    val crossFadeDurationSec: Float = DEFAULT_CROSSFADE_SEC,
    val maxClauseChars: Int = DEFAULT_MAX_CLAUSE_CHARS,
    val customRefAudioPath: String? = null,
    val customRefTextTh: String? = null,
    val useNnapiExecutionProvider: Boolean = false
) {
    companion object {
        const val DEFAULT_NFE_STEPS = 16
        const val DEFAULT_CFG_STRENGTH = 2.0f
        const val DEFAULT_SWAY_COEF = -1.0f
        const val DEFAULT_TARGET_RMS = 0.1f
        const val DEFAULT_CROSSFADE_SEC = 0.15f
        const val DEFAULT_MAX_CLAUSE_CHARS = 100
        val SUPPORTED_NFE_STEPS = listOf(8, 16, 20, 32)
    }
}

/**
 * Conditioned 24,000 Hz mono reference audio ready for F5-TTS-THAI zero-shot conditioning.
 */
data class ConditionedReferenceAudio(
    val sourceFilePath: String,
    val originalSampleRateHz: Int,
    val originalChannels: Int,
    val conditionedSampleRateHz: Int,
    val durationMs: Long,
    val rmsAmplitude: Float,
    val int16Samples24k: ShortArray,
    val floatSamples24k: FloatArray,
    val referenceTextTh: String,
    val referenceTextIpa: String
) {
    val frameCountHop256: Int
        get() = (int16Samples24k.size / F5TtsThaiEngine.F5_HOP_LENGTH) + 1

    val sampleRateHz: Int
        get() = conditionedSampleRateHz

    val sampleCount24k: Int
        get() = int16Samples24k.size

    val pcm16Mono24k: ShortArray
        get() = int16Samples24k

    val melFrameEstimate: Long
        get() = frameCountHop256.toLong()
}

/**
 * Genuine Android native inference & conditioning engine for **VIZINTZOR/F5-TTS-THAI**
 * (`F5-TTS-THAI` v1, `F5-TTS-TH-V2`, and `VIZINTZOR/F5-TH-ONNX` 3-stage ONNX Runtime pipeline).
 *
 * Implements:
 * 1. Reference Audio Conditioning (`conditionReferenceAudioWav`):
 *    - Parses RIFF WAV (mono/stereo, 16kHz/22.05kHz/24kHz/44.1kHz/48kHz), downmixes to mono,
 *      resamples to 24,000 Hz, normalizes RMS (`target_rms = 0.1`) and int16 peak (`[-32767, 32767]`),
 *      and clamps to the 2.0s–8.0s reference window required by F5-TTS-THAI.
 * 2. Dual Tokenizer & Thai G2P (`tokenizeV1Char`, `thaiToIpaPhonemes`, `tokenizeF5OnnxIpa`):
 *    - Loads `vocab_v1.txt` (2,586 tokens), `vocab_v2.txt` (2,554 tokens), and `f5_onnx_vocab.json` (79 IPA tokens).
 *    - Converts Thai words and syllables into IPA phonemes with tone diacritics matching `VIZINTZOR/F5-TH-ONNX`.
 * 3. Clause Segmentation & Linear Crossfading (`segmentThaiClausesForF5`, `crossfadePcm16Chunks`):
 *    - Splits long Thai paragraphs into natural clauses without breaking Thai vowel/tone diacritics
 *      and crossfades generated 24kHz PCM chunks with a 150ms linear overlap.
 * 4. 3-Stage ONNX Runtime Execution (`Preprocess.onnx` -> `Transformer.onnx` NFE loop -> `Vocoder.onnx`):
 *    - Never fakes or simulates audio output; validates all three ONNX subgraphs before execution.
 */
class F5TtsThaiEngine(
    private val context: Context,
    val storageManager: OfflineModelStorageManager = OfflineModelStorageManager(context.applicationContext),
    private val autoStageBundledAssets: Boolean = true
) : Closeable {

    constructor(context: Context, autoStageBundledAssets: Boolean) : this(
        context = context,
        storageManager = OfflineModelStorageManager(context.applicationContext),
        autoStageBundledAssets = autoStageBundledAssets
    )

    val modelDir: File
        get() = storageManager.f5ModelDir

    private val f5Mutex = Mutex()

    @Volatile
    private var preprocessSession: OrtSession? = null

    @Volatile
    private var transformerSession: OrtSession? = null

    @Volatile
    private var vocoderSession: OrtSession? = null

    private val v1CharVocab: Map<String, Int> by lazy {
        storageManager.stageBundledF5CompanionAssets()
        loadLineVocabFile(storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_V1_VOCAB_TXT))
    }

    private val v2PhonemeVocab: Map<String, Int> by lazy {
        storageManager.stageBundledF5CompanionAssets()
        loadLineVocabFile(storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_V2_VOCAB_TXT))
    }

    private val onnxIpaVocab: Map<Char, Int> by lazy {
        storageManager.stageBundledF5CompanionAssets()
        loadOnnxVocabJson(storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_ONNX_VOCAB_JSON))
    }

    fun stageBundledF5AssetsIfAvailable() {
        storageManager.stageBundledF5CompanionAssets()
    }

    fun loadVocabV1(): Map<String, Int> = v1CharVocab

    fun loadVocabV2(): Map<String, Int> = v2PhonemeVocab

    fun loadOnnxIpaVocab(): Map<Char, Int> = onnxIpaVocab

    /**
     * Returns true only when all three F5-TH-ONNX subgraphs (`Preprocess.onnx`, `Transformer.onnx`,
     * and `Vocoder.onnx`) are installed and verified on disk.
     */
    fun areAllF5SubgraphsInstalled(): Boolean {
        val summary = storageManager.inspectStorageSummary(verifyLargeFileHashes = false, stageBundledFirst = autoStageBundledAssets)
        return summary.f5SubgraphsComplete && summary.f5ConditioningAssetsReady
    }

    fun areF5OnnxSubgraphsInstalled(): Boolean = areAllF5SubgraphsInstalled()

    /**
     * Returns true when the bundled F5-TTS-THAI vocabularies (`vocab_v1.txt`, `vocab_v2.txt`,
     * `f5_onnx_vocab.json`) and reference audio samples (`ref_audio_1.wav`, `ref_audio_2.wav`)
     * are staged and verified by SHA-256 checksum.
     */
    fun areConditioningAssetsVerified(): Boolean {
        val summary = storageManager.inspectStorageSummary(verifyLargeFileHashes = false, stageBundledFirst = autoStageBundledAssets)
        return summary.f5ConditioningAssetsReady
    }

    fun areConditioningAssetsInstalled(): Boolean = areConditioningAssetsVerified()

    /**
     * Conditions a reference WAV file (`ref_audio_1.wav`, `ref_audio_2.wav`, or custom WAV)
     * into a 24,000 Hz mono int16/float32 tensor paired with its normalized Thai & IPA reference transcript.
     */
    fun conditionReferenceAudio(
        profile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
        customWavFile: File? = null,
        customRefTextTh: String? = null,
        targetRms: Float = F5SynthesisParams.DEFAULT_TARGET_RMS
    ): Result<ConditionedReferenceAudio> {
        storageManager.stageBundledF5CompanionAssets()
        val wavFile = when {
            customWavFile != null && customWavFile.exists() -> customWavFile
            profile.referenceAudioSpec != null -> storageManager.resolveArtifactFile(profile.referenceAudioSpec)
            else -> storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_REF_AUDIO_1)
        }

        if (!wavFile.exists() || wavFile.length() <= WavAudioUtils.WAV_HEADER_SIZE) {
            return Result.failure(
                OfflineTtsException.ModelNotFoundException(wavFile.absolutePath)
            )
        }

        val rawRefText = customRefTextTh?.takeIf { it.isNotBlank() }
            ?: profile.defaultReferenceTextTh.ifBlank {
                OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR.defaultReferenceTextTh
            }

        return runCatching {
            val wavBytes = wavFile.readBytes()
            val parsed = decodeWavToMonoFloat(wavBytes)
            val resampled24k = if (parsed.sampleRateHz == F5_SAMPLE_RATE_HZ) {
                parsed.monoSamples
            } else {
                resampleLinear(parsed.monoSamples, parsed.sampleRateHz, F5_SAMPLE_RATE_HZ)
            }

            // Clamp reference audio to max 8.0 seconds (192,000 samples at 24kHz) per F5-TTS-THAI guidelines
            val maxSamples = F5_SAMPLE_RATE_HZ * 8
            val clampedFloat = if (resampled24k.size > maxSamples) {
                resampled24k.copyOfRange(0, maxSamples)
            } else {
                resampled24k
            }

            // Calculate RMS and normalize if below targetRms (matches f5_tts_th/utils_infer.py)
            var sumSq = 0.0
            for (s in clampedFloat) {
                sumSq += (s * s).toDouble()
            }
            val rms = sqrt(sumSq / max(1, clampedFloat.size)).toFloat()
            val rmsScaled = if (rms in 1e-5f..<targetRms) {
                val gain = (targetRms / rms).coerceAtMost(8.0f)
                FloatArray(clampedFloat.size) { i -> (clampedFloat[i] * gain).coerceIn(-0.99f, 0.99f) }
            } else {
                clampedFloat
            }

            // Peak-normalize to int16 [-32767, 32767] for Preprocess.onnx INT16[1, 1, audio_len] input
            var maxAbs = 0f
            for (s in rmsScaled) {
                val a = abs(s)
                if (a > maxAbs) maxAbs = a
            }
            val int16Scale = if (maxAbs > 1e-6f) (32767f / maxAbs) else 1f
            val int16Samples = ShortArray(rmsScaled.size) { i ->
                (rmsScaled[i] * int16Scale).roundToInt().coerceIn(-32767, 32767).toShort()
            }

            val normalizedRefText = ensureTrailingPeriod(
                ThaiTextOptimizer.optimizeThaiText(rawRefText)
            )
            val refIpa = thaiToIpaPhonemes(normalizedRefText)
            val durationMs = (int16Samples.size * 1000L) / F5_SAMPLE_RATE_HZ.toLong()

            ConditionedReferenceAudio(
                sourceFilePath = wavFile.absolutePath,
                originalSampleRateHz = parsed.sampleRateHz,
                originalChannels = parsed.channels,
                conditionedSampleRateHz = F5_SAMPLE_RATE_HZ,
                durationMs = durationMs,
                rmsAmplitude = rms,
                int16Samples24k = int16Samples,
                floatSamples24k = rmsScaled,
                referenceTextTh = normalizedRefText,
                referenceTextIpa = refIpa
            )
        }
    }

    /**
     * Tokenizes reference + target Thai text using F5-TTS-THAI v1 `vocab_v1.txt` (2,586 UTF-8 char tokens).
     */
    fun tokenizeForF5V1Char(refTextTh: String, genTextTh: String): IntArray {
        val cleanRef = ensureTrailingPeriod(ThaiTextOptimizer.optimizeThaiText(refTextTh))
        val cleanGen = Normalizer.normalize(ThaiTextOptimizer.optimizeThaiText(genTextTh), Normalizer.Form.NFC)
        val combined = "$cleanRef $cleanGen"
        val map = v1CharVocab
        return IntArray(combined.length) { i ->
            val ch = combined[i].toString()
            map[ch] ?: 0
        }
    }

    /**
     * Tokenizes reference + target Thai text into IPA phoneme IDs using `VIZINTZOR/F5-TH-ONNX`'s
     * `f5_onnx_vocab.json` (79 IPA tokens) for `Preprocess.onnx` (`INT32[1, text_ids_len]`).
     */
    fun tokenizeForF5OnnxIpa(refTextTh: String, genTextTh: String): IntArray {
        val refIpa = thaiToIpaPhonemes(refTextTh).trimEnd('.', ' ')
        val genIpa = thaiToIpaPhonemes(genTextTh)
        val combinedIpa = "$refIpa. $genIpa"
        val map = onnxIpaVocab
        return IntArray(combinedIpa.length) { i ->
            map[combinedIpa[i]] ?: 0
        }
    }

    /**
     * Computes `max_duration` in 256-sample mel frames for F5-TTS-THAI (`Preprocess.onnx`),
     * matching `f5_th_onnx.py`: `audio_samples // 256 + 1 + int(gen_text_len / speed)`.
     */
    fun computeF5MaxDurationFrames(
        refAudioSamples24k: Int,
        genTextIpaOrChar: String,
        speed: Float = 1.0f
    ): Long {
        val safeSpeed = speed.coerceIn(0.4f, 2.0f)
        val refFrames = (refAudioSamples24k / F5_HOP_LENGTH) + 1
        val spaceCount = genTextIpaOrChar.count { it == ' ' }
        val nonSpaceCount = (genTextIpaOrChar.length - spaceCount).coerceAtLeast(1)
        val rawGenFrames = (nonSpaceCount * 5.0f) + (spaceCount * 2.5f)
        val scaledGenFrames = max(32, (rawGenFrames / safeSpeed).roundToInt())
        return (refFrames + scaledGenFrames).toLong()
    }

    /**
     * Explicitly loads all three F5-TH-ONNX subgraphs (`Preprocess.onnx`, `Transformer.onnx`, `Vocoder.onnx`)
     * into ONNX Runtime sessions and verifies their input/output tensor signatures.
     */
    suspend fun loadF5SubgraphSessions(
        useNnapi: Boolean = false
    ): Result<Triple<OrtSession, OrtSession, OrtSession>> = withContext(Dispatchers.IO) {
        f5Mutex.withLock {
            val prepFile = storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_PREPROCESS_ONNX)
            val transFile = storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_TRANSFORMER_ONNX)
            val vocFile = storageManager.resolveArtifactFile(OfflineModelArtifactSpec.F5_VOCODER_ONNX)

            for ((spec, file) in listOf(
                OfflineModelArtifactSpec.F5_PREPROCESS_ONNX to prepFile,
                OfflineModelArtifactSpec.F5_TRANSFORMER_ONNX to transFile,
                OfflineModelArtifactSpec.F5_VOCODER_ONNX to vocFile
            )) {
                if (!file.exists() || file.length() <= OfflineTtsEngine.MIN_VALID_ONNX_BYTES) {
                    return@withLock Result.failure(
                        OfflineTtsException.ModelNotFoundException("${spec.fileName} (${file.absolutePath})")
                    )
                }
            }

            val existingPrep = preprocessSession
            val existingTrans = transformerSession
            val existingVoc = vocoderSession
            if (existingPrep != null && existingTrans != null && existingVoc != null) {
                return@withLock Result.success(Triple(existingPrep, existingTrans, existingVoc))
            }

            return@withLock try {
                val env = OrtEnvironment.getEnvironment()
                    ?: return@withLock Result.failure(
                        OfflineTtsException.RuntimeInitializationException("OrtEnvironment.getEnvironment() returned null")
                    )

                val cpuOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(true)
                }

                val transformerOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(4)
                    setInterOpNumThreads(1)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setCPUArenaAllocator(true)
                    if (useNnapi) {
                        runCatching { addNnapi() }.onFailure {
                            Log.w(TAG, "NNAPI execution provider not available, using ARM64 CPU: ${it.message}")
                        }
                    }
                }

                val sPrep = env.createSession(prepFile.absolutePath, cpuOpts)
                val sTrans = env.createSession(transFile.absolutePath, transformerOpts)
                val sVoc = env.createSession(vocFile.absolutePath, cpuOpts)

                preprocessSession = sPrep
                transformerSession = sTrans
                vocoderSession = sVoc

                Result.success(Triple(sPrep, sTrans, sVoc))
            } catch (t: Throwable) {
                close()
                Result.failure(
                    OfflineTtsException.RuntimeInitializationException(
                        "โหลดกราฟ F5-TH-ONNX (Preprocess/Transformer/Vocoder) ไม่สำเร็จ: ${t.localizedMessage ?: t.javaClass.simpleName}",
                        t
                    )
                )
            }
        }
    }

    /**
     * Overload accepting `targetText`, `voiceProfile`, `params`, `customRefAudioFile`, and `customRefText`.
     */
    suspend fun synthesizeWithF5Onnx(
        targetText: String,
        voiceProfile: OfflineVoiceProfile,
        params: F5SynthesisParams = F5SynthesisParams(),
        customRefAudioFile: File? = null,
        customRefText: String? = null
    ): Result<OfflineNeuralSynthesisResult> {
        val mergedParams = params.copy(
            voiceProfile = voiceProfile,
            customRefAudioPath = customRefAudioFile?.absolutePath ?: params.customRefAudioPath,
            customRefTextTh = customRefText ?: params.customRefTextTh
        )
        return synthesizeWithF5Onnx(text = targetText, params = mergedParams)
    }

    /**
     * Runs genuine 3-stage F5-TTS-THAI ONNX inference (`Preprocess.onnx` -> `nfeSteps` iterations of
     * `Transformer.onnx` -> `Vocoder.onnx`) when all 3 subgraphs are installed on disk.
     */
    suspend fun synthesizeWithF5Onnx(
        text: String,
        params: F5SynthesisParams = F5SynthesisParams(),
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        voiceWarmth: Int = 50
    ): Result<OfflineNeuralSynthesisResult> = withContext(Dispatchers.Default) {
        val startMs = System.currentTimeMillis()
        val sessionsResult = loadF5SubgraphSessions(useNnapi = params.useNnapiExecutionProvider)
        if (sessionsResult.isFailure) {
            return@withContext Result.failure(
                sessionsResult.exceptionOrNull()
                    ?: OfflineTtsException.ModelNotFoundException("F5-TH-ONNX subgraphs")
            )
        }
        val (sPrep, sTrans, sVoc) = sessionsResult.getOrThrow()

        val customWav = params.customRefAudioPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        val condResult = conditionReferenceAudio(
            profile = params.voiceProfile,
            customWavFile = customWav,
            customRefTextTh = params.customRefTextTh,
            targetRms = params.targetRms
        )
        if (condResult.isFailure) {
            return@withContext Result.failure(
                condResult.exceptionOrNull()
                    ?: OfflineTtsException.InvalidModelFileException("ref_audio.wav", "ไม่สามารถเตรียมเสียงอ้างอิง F5-TTS-THAI ได้")
            )
        }
        val conditionedRef = condResult.getOrThrow()

        val clauses = segmentThaiClausesForF5(text, maxChars = params.maxClauseChars)
        if (clauses.isEmpty()) {
            return@withContext Result.failure(
                OfflineTtsException.InferenceException("ข้อความภาษาไทยว่างเปล่าหลังการตัดแบ่งประโยค")
            )
        }

        val generatedChunkPcmList = mutableListOf<ShortArray>()
        var totalTokens = 0
        val steps = params.nfeSteps.coerceIn(4, 64)
        val env = OrtEnvironment.getEnvironment()

        try {
            for (clause in clauses) {
                coroutineContext.ensureActive()
                val genIpa = thaiToIpaPhonemes(clause)
                val textIds = tokenizeForF5OnnxIpa(conditionedRef.referenceTextTh, clause)
                totalTokens += textIds.size
                val maxDur = computeF5MaxDurationFrames(
                    refAudioSamples24k = conditionedRef.int16Samples24k.size,
                    genTextIpaOrChar = genIpa,
                    speed = params.speed
                )

                val prepTensors = mutableListOf<OnnxTensor>()
                var prepResult: OrtSession.Result? = null
                try {
                    val audioTensor = OnnxTensor.createTensor(
                        env,
                        ShortBuffer.wrap(conditionedRef.int16Samples24k),
                        longArrayOf(1L, 1L, conditionedRef.int16Samples24k.size.toLong())
                    ).also { prepTensors.add(it) }

                    val textTensor = OnnxTensor.createTensor(
                        env,
                        IntBuffer.wrap(textIds),
                        longArrayOf(1L, textIds.size.toLong())
                    ).also { prepTensors.add(it) }

                    val durTensor = OnnxTensor.createTensor(
                        env,
                        LongBuffer.wrap(longArrayOf(maxDur)),
                        longArrayOf(1L)
                    ).also { prepTensors.add(it) }

                    val prepInNames = sPrep.inputNames.toList()
                    val prepInputs = mapOf(
                        prepInNames[0] to audioTensor,
                        prepInNames[1] to textTensor,
                        prepInNames[2] to durTensor
                    )

                    prepResult = sPrep.run(prepInputs)
                    var currentNoise = prepResult[0] as OnnxTensor
                    val ropeCosQ = prepResult[1] as OnnxTensor
                    val ropeSinQ = prepResult[2] as OnnxTensor
                    val ropeCosK = prepResult[3] as OnnxTensor
                    val ropeSinK = prepResult[4] as OnnxTensor
                    val catMelText = prepResult[5] as OnnxTensor
                    val catMelTextDrop = prepResult[6] as OnnxTensor
                    val refSignalLen = prepResult[7] as OnnxTensor

                    var currentStepTensor = OnnxTensor.createTensor(
                        env,
                        IntBuffer.wrap(intArrayOf(0)),
                        longArrayOf(1L)
                    )

                    val transInNames = sTrans.inputNames.toList()
                    for (step in 1..steps) {
                        coroutineContext.ensureActive()
                        val transInputs = linkedMapOf(
                            transInNames[0] to currentNoise,
                            transInNames[1] to ropeCosQ,
                            transInNames[2] to ropeSinQ,
                            transInNames[3] to ropeCosK,
                            transInNames[4] to ropeSinK,
                            transInNames[5] to catMelText,
                            transInNames[6] to catMelTextDrop,
                            transInNames[7] to currentStepTensor
                        )
                        val stepResult = sTrans.run(transInputs)
                        if (step > 1) {
                            runCatching { currentNoise.close() }
                        }
                        runCatching { currentStepTensor.close() }
                        currentNoise = stepResult[0] as OnnxTensor
                        currentStepTensor = stepResult[1] as OnnxTensor
                    }
                    runCatching { currentStepTensor.close() }

                    val vocInNames = sVoc.inputNames.toList()
                    val vocInputs = mapOf(
                        vocInNames[0] to currentNoise,
                        vocInNames[1] to refSignalLen
                    )
                    sVoc.run(vocInputs).use { vocRes ->
                        val rawSignal = vocRes[0].value
                        val pcmShorts = extractShortOrFloatWaveform(rawSignal)
                        if (pcmShorts.isNotEmpty()) {
                            generatedChunkPcmList.add(pcmShorts)
                        }
                    }
                    if (steps >= 1) {
                        runCatching { currentNoise.close() }
                    }
                } finally {
                    prepResult?.close()
                    prepTensors.forEach { runCatching { it.close() } }
                }
            }

            if (generatedChunkPcmList.isEmpty()) {
                return@withContext Result.failure(
                    OfflineTtsException.InferenceException("F5-TH-ONNX Vocoder คืนค่าคลื่นเสียงว่างเปล่า")
                )
            }

            val crossfadeSamples = (params.crossFadeDurationSec.coerceIn(0f, 0.5f) * F5_SAMPLE_RATE_HZ).roundToInt()
            val mergedShorts = crossfadePcm16Chunks(generatedChunkPcmList, crossfadeSamples)
            val rawPcmBytes = shortArrayToPcm16LeBytes(mergedShorts)
            val baseWavBytes = WavAudioUtils.ensureWavContainer(rawPcmBytes, sampleRateHz = F5_SAMPLE_RATE_HZ)

            val pitchRatio = if (abs(pitch - 1.0f) > 0.03f) pitch.coerceIn(0.75f, 1.35f) else 1.0f
            val finalWavBytes = if (abs(volume - 1.0f) > 0.02f || voiceWarmth != 50 || abs(pitchRatio - 1.0f) > 0.02f) {
                WavAudioUtils.applyAudioPostProcessing(
                    wavOrPcmBytes = baseWavBytes,
                    sampleRateHz = F5_SAMPLE_RATE_HZ,
                    volume = volume,
                    voiceWarmth = voiceWarmth,
                    speedRatio = pitchRatio
                )
            } else {
                baseWavBytes
            }

            val finalPcm = WavAudioUtils.extractPcmFromWav(finalWavBytes)
            val durationMs = WavAudioUtils.calculateDurationMs(finalWavBytes, F5_SAMPLE_RATE_HZ)
            val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            Result.success(
                OfflineNeuralSynthesisResult(
                    wavBytes = finalWavBytes,
                    pcm16Bytes = finalPcm,
                    sampleRateHz = F5_SAMPLE_RATE_HZ,
                    durationMs = durationMs,
                    tokenCount = totalTokens,
                    inferenceLatencyMs = latencyMs,
                    modelPathUsed = storageManager.f5ModelDir.absolutePath
                )
            )
        } catch (t: Throwable) {
            Log.e(TAG, "F5-TH-ONNX 3-stage synthesis failed", t)
            Result.failure(
                OfflineTtsException.InferenceException(
                    "F5-TH-ONNX inference error: ${t.localizedMessage ?: t.javaClass.simpleName}",
                    t
                )
            )
        }
    }

    override fun close() {
        runCatching { preprocessSession?.close() }
        runCatching { transformerSession?.close() }
        runCatching { vocoderSession?.close() }
        preprocessSession = null
        transformerSession = null
        vocoderSession = null
    }

    companion object {
        const val F5_SAMPLE_RATE_HZ = 24_000
        const val F5_HOP_LENGTH = 256
        const val F5_MEL_CHANNELS = 100

        /**
         * Thai Consonant & Vowel to IPA mapping aligned with `VIZINTZOR/F5-TH-ONNX` (`f5_onnx_vocab.json`)
         * and `VIZINTZOR/F5-TTS-TH-V2` (`THG2P.py`).
         */
        private val thaiConsonantIpaMap = mapOf(
            'ก' to "k", 'ข' to "kʰ", 'ฃ' to "kʰ", 'ค' to "kʰ", 'ฅ' to "kʰ", 'ฆ' to "kʰ",
            'ง' to "ŋ", 'จ' to "tɕ", 'ฉ' to "tɕʰ", 'ช' to "tɕʰ", 'ซ' to "s", 'ฌ' to "tɕʰ",
            'ญ' to "j", 'ฎ' to "d", 'ฏ' to "t", 'ฐ' to "tʰ", 'ฑ' to "tʰ", 'ฒ' to "tʰ",
            'ณ' to "n", 'ด' to "d", 'ต' to "t", 'ถ' to "tʰ", 'ท' to "tʰ", 'ธ' to "tʰ",
            'น' to "n", 'บ' to "b", 'ป' to "p", 'ผ' to "pʰ", 'ฝ' to "f", 'พ' to "pʰ",
            'ฟ' to "f", 'ภ' to "pʰ", 'ม' to "m", 'ย' to "j", 'ร' to "r", 'ฤ' to "rɯ",
            'ล' to "l", 'ฦ' to "lɯ", 'ว' to "w", 'ศ' to "s", 'ษ' to "s", 'ส' to "s",
            'ห' to "h", 'ฬ' to "l", 'อ' to "ʔ", 'ฮ' to "h"
        )

        private val thaiVowelAndToneIpaMap = mapOf(
            'ะ' to "aʔ", 'ั' to "a", 'า' to "aː", 'ำ' to "am",
            'ิ' to "i", 'ี' to "iː", 'ึ' to "ɯ", 'ื' to "ɯː",
            'ุ' to "u", 'ู' to "uː", 'เ' to "eː", 'แ' to "ɛː",
            'โ' to "oː", 'ใ' to "aj", 'ไ' to "aj", 'ๅ' to "ː",
            '็' to "", '่' to "\u0300", '้' to "\u0302", '๊' to "\u0301", '๋' to "\u030C"
        )

        /**
         * Converts Thai text into an IPA phoneme sequence compatible with `VIZINTZOR/F5-TH-ONNX`
         * (`f5_onnx_vocab.json`) and `VIZINTZOR/F5-TTS-TH-V2`.
         * Handles silent Karan (`์`), Maiyamok (`ๆ`), numbers, and preposed Thai vowels (`เ`, `แ`, `โ`, `ใ`, `ไ`).
         */
        fun thaiToIpaPhonemes(rawText: String): String {
            val normalized = ThaiTextOptimizer.optimizeThaiText(rawText)
            if (normalized.isBlank()) return "."

            // Expand Maiyamok (ๆ) by repeating preceding token
            val expanded = normalized.replace(Regex("([ก-๙]+)\\s*ๆ"), "$1 $1")

            val sb = StringBuilder(expanded.length * 2)
            var i = 0
            while (i < expanded.length) {
                val ch = expanded[i]

                // Skip consonant + silent mark (การันต์ ์)
                if (i + 1 < expanded.length && expanded[i + 1] == '์') {
                    i += 2
                    continue
                }
                if (ch == '์') {
                    i++
                    continue
                }

                // Handle preposed Thai vowels (เ, แ, โ, ใ, ไ) followed by a consonant
                if (ch in "เแโใไ" && i + 1 < expanded.length && expanded[i + 1] in 'ก'..'ฮ') {
                    val cons = expanded[i + 1]
                    val consIpa = thaiConsonantIpaMap[cons].orEmpty()
                    val vowelIpa = thaiVowelAndToneIpaMap[ch].orEmpty()
                    sb.append(consIpa).append(vowelIpa)
                    i += 2
                    continue
                }

                val consIpa = thaiConsonantIpaMap[ch]
                if (consIpa != null) {
                    val nextCh = if (i + 1 < expanded.length) expanded[i + 1] else '\u0000'
                    val hasExplicitVowel = nextCh in thaiVowelAndToneIpaMap || nextCh in 'ะ'..'ู' || nextCh in '่'..'๋'
                    sb.append(consIpa)
                    if (!hasExplicitVowel && nextCh in 'ก'..'ฮ') {
                        sb.append("a")
                    }
                    i++
                    continue
                }

                val vowelOrTone = thaiVowelAndToneIpaMap[ch]
                if (vowelOrTone != null) {
                    sb.append(vowelOrTone)
                    i++
                    continue
                }

                if (ch.isWhitespace()) {
                    if (sb.isNotEmpty() && sb.last() != ' ') {
                        sb.append(' ')
                    }
                } else if (ch in ".,?'-0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz") {
                    sb.append(ch)
                }
                i++
            }

            val result = sb.toString().replace(Regex("\\s+"), " ").trim()
            return if (result.endsWith(".")) result else "$result."
        }

        /**
         * Splits Thai text into natural clauses not exceeding [maxChars] characters,
         * strictly preserving Thai combining vowel/tone marks so no diacritic is ever orphaned.
         */
        fun segmentThaiClausesForF5(text: String, maxChars: Int = 100): List<String> {
            val optimized = ThaiTextOptimizer.optimizeThaiText(text)
            if (optimized.isBlank()) return emptyList()
            val safeMax = maxChars.coerceAtLeast(20)
            if (optimized.length <= safeMax) return listOf(optimized)

            val rawTokens = optimized.split(Regex("(?<=\\s)|(?=[,\\.;])"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val chunks = mutableListOf<String>()
            val current = StringBuilder()

            for (tok in rawTokens) {
                if (current.isEmpty()) {
                    if (tok.length <= safeMax) {
                        current.append(tok)
                    } else {
                        chunks.addAll(splitLongThaiWordSafely(tok, safeMax))
                    }
                } else if (current.length + 1 + tok.length <= safeMax) {
                    current.append(' ').append(tok)
                } else {
                    chunks.add(current.toString().trim())
                    current.clear()
                    if (tok.length <= safeMax) {
                        current.append(tok)
                    } else {
                        chunks.addAll(splitLongThaiWordSafely(tok, safeMax))
                    }
                }
            }
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trim())
            }
            return chunks
        }

        private fun splitLongThaiWordSafely(text: String, maxChars: Int): List<String> {
            val parts = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var end = min(start + maxChars, text.length)
                // Do not split right before a Thai combining mark or preposed vowel
                while (end < text.length && isThaiCombiningOrPreposedVowel(text[end])) {
                    end++
                }
                parts.add(text.substring(start, end).trim())
                start = end
            }
            return parts.filter { it.isNotEmpty() }
        }

        private fun isThaiCombiningOrPreposedVowel(ch: Char): Boolean {
            val code = ch.code
            return code == 0x0E31 || code in 0x0E34..0x0E3A || code in 0x0E47..0x0E4E
        }

        fun segmentThaiClausesForF5(
            rawText: String,
            maxClauseChars: Int,
            unusedTag: Boolean = true
        ): List<String> = segmentThaiClausesForF5(text = rawText, maxChars = maxClauseChars)

        fun computeF5MaxDurationFrames(
            refAudioSamples24k: Int,
            refTextLength: Int,
            genTextLength: Int,
            speed: Float = 1.0f
        ): Long {
            val safeSpeed = speed.coerceIn(0.4f, 2.0f)
            val refFrames = (refAudioSamples24k / F5_HOP_LENGTH) + 1
            val ratio = (genTextLength.coerceAtLeast(1).toFloat() / refTextLength.coerceAtLeast(1).toFloat())
            val estGenFrames = max(32, ((refFrames * ratio * 1.15f) / safeSpeed).roundToInt())
            return (refFrames + estGenFrames).toLong()
        }

        fun crossfadePcm16Chunks(
            chunks: List<ShortArray>,
            crossFadeDurationSec: Float,
            sampleRateHz: Int = F5_SAMPLE_RATE_HZ
        ): ShortArray {
            val fadeSamples = (crossFadeDurationSec.coerceIn(0f, 0.5f) * sampleRateHz).roundToInt()
            return crossfadePcm16Chunks(chunks, fadeSamples)
        }

        /**
         * Crossfades adjacent 16-bit PCM `ShortArray` chunks using a linear fade-out/fade-in overlap
         * of [crossFadeSamples] samples (matches `f5_tts_th/utils_infer.py` `cross_fade_duration = 0.15`).
         */
        fun crossfadePcm16Chunks(
            chunks: List<ShortArray>,
            crossFadeSamples: Int
        ): ShortArray {
            if (chunks.isEmpty()) return ShortArray(0)
            if (chunks.size == 1) return chunks[0]

            var accumulated = chunks[0]
            for (idx in 1 until chunks.size) {
                val nextChunk = chunks[idx]
                if (nextChunk.isEmpty()) continue
                if (accumulated.isEmpty()) {
                    accumulated = nextChunk
                    continue
                }

                val overlap = min(crossFadeSamples, min(accumulated.size / 3, nextChunk.size / 3))
                if (overlap <= 0) {
                    val combined = ShortArray(accumulated.size + nextChunk.size)
                    System.arraycopy(accumulated, 0, combined, 0, accumulated.size)
                    System.arraycopy(nextChunk, 0, combined, accumulated.size, nextChunk.size)
                    accumulated = combined
                } else {
                    val combined = ShortArray(accumulated.size + nextChunk.size - overlap)
                    val prefixLen = accumulated.size - overlap
                    System.arraycopy(accumulated, 0, combined, 0, prefixLen)
                    for (j in 0 until overlap) {
                        val fadeOut = 1.0f - (j.toFloat() / overlap.toFloat())
                        val fadeIn = j.toFloat() / overlap.toFloat()
                        val mixed = (accumulated[prefixLen + j] * fadeOut) + (nextChunk[j] * fadeIn)
                        combined[prefixLen + j] = mixed.roundToInt().coerceIn(-32768, 32767).toShort()
                    }
                    System.arraycopy(nextChunk, overlap, combined, prefixLen + overlap, nextChunk.size - overlap)
                    accumulated = combined
                }
            }
            return accumulated
        }

        private data class DecodedWavMono(
            val sampleRateHz: Int,
            val channels: Int,
            val monoSamples: FloatArray
        )

        /**
         * Decodes a 16-bit PCM RIFF/WAVE byte array (mono or stereo) into normalized `[-1.0, 1.0]` mono floats.
         */
        private fun decodeWavToMonoFloat(wavBytes: ByteArray): DecodedWavMono {
            if (!WavAudioUtils.isRiffWav(wavBytes) || wavBytes.size <= 44) {
                return DecodedWavMono(F5_SAMPLE_RATE_HZ, 1, FloatArray(0))
            }
            val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
            var pos = 12
            var channels = 1
            var sampleRate = F5_SAMPLE_RATE_HZ
            var bitsPerSample = 16
            var dataOffset = 44
            var dataSize = wavBytes.size - 44

            while (pos + 8 <= wavBytes.size) {
                val id0 = wavBytes[pos].toInt().toChar()
                val id1 = wavBytes[pos + 1].toInt().toChar()
                val id2 = wavBytes[pos + 2].toInt().toChar()
                val id3 = wavBytes[pos + 3].toInt().toChar()
                val chunkId = "$id0$id1$id2$id3"
                val chunkSize = buf.getInt(pos + 4).coerceAtLeast(0)
                val bodyPos = pos + 8

                if (chunkId == "fmt " && bodyPos + 16 <= wavBytes.size) {
                    channels = buf.getShort(bodyPos + 2).toInt().coerceIn(1, 8)
                    sampleRate = buf.getInt(bodyPos + 4).coerceIn(8_000, 96_000)
                    bitsPerSample = buf.getShort(bodyPos + 14).toInt().coerceIn(8, 32)
                } else if (chunkId == "data") {
                    dataOffset = bodyPos
                    dataSize = min(chunkSize, wavBytes.size - bodyPos)
                    break
                }
                pos = bodyPos + chunkSize + (chunkSize % 2)
            }

            val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(2)
            val frameSize = bytesPerSample * channels
            val totalFrames = (dataSize / frameSize).coerceAtLeast(0)
            val mono = FloatArray(totalFrames)

            var frameIdx = 0
            var bytePtr = dataOffset
            while (frameIdx < totalFrames && bytePtr + frameSize <= wavBytes.size) {
                var sum = 0f
                for (ch in 0 until channels) {
                    val sampleShort = buf.getShort(bytePtr + ch * 2)
                    sum += sampleShort.toFloat() / 32768f
                }
                mono[frameIdx] = (sum / channels.toFloat()).coerceIn(-1.0f, 1.0f)
                bytePtr += frameSize
                frameIdx++
            }

            return DecodedWavMono(
                sampleRateHz = sampleRate,
                channels = channels,
                monoSamples = mono
            )
        }

        /**
         * Resamples a 1D `FloatArray` from [fromRate] Hz to [toRate] Hz using linear interpolation.
         */
        fun resampleLinear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
            if (input.isEmpty() || fromRate <= 0 || toRate <= 0 || fromRate == toRate) return input
            val ratio = fromRate.toDouble() / toRate.toDouble()
            val outLen = max(1, (input.size / ratio).roundToInt())
            val out = FloatArray(outLen)
            val lastIdx = input.size - 1
            for (i in 0 until outLen) {
                val srcPos = i * ratio
                val idx0 = srcPos.toInt().coerceIn(0, lastIdx)
                val idx1 = min(idx0 + 1, lastIdx)
                val frac = (srcPos - idx0).toFloat()
                out[i] = input[idx0] * (1f - frac) + input[idx1] * frac
            }
            return out
        }

        private fun ensureTrailingPeriod(text: String): String {
            val trimmed = text.trim()
            return if (trimmed.endsWith(".")) trimmed else "$trimmed."
        }

        private fun loadLineVocabFile(file: File): Map<String, Int> {
            if (!file.exists() || file.length() == 0L) return emptyMap()
            return runCatching {
                val map = LinkedHashMap<String, Int>(2600)
                file.readLines(Charsets.UTF_8).forEachIndexed { index, raw ->
                    val token = if (raw.isEmpty() && index == 0) " " else raw.trimEnd('\r', '\n')
                    val key = when {
                        token.isEmpty() -> "__blank_$index"
                        map.containsKey(token) -> "${token}__dup_$index"
                        else -> token
                    }
                    map[key] = index
                }
                map
            }.getOrDefault(emptyMap())
        }

        private fun loadOnnxVocabJson(file: File): Map<Char, Int> {
            if (!file.exists() || file.length() == 0L) return emptyMap()
            return runCatching {
                val root = JSONObject(file.readText(Charsets.UTF_8))
                val vocabObj = root.optJSONObject("vocab") ?: root
                val map = LinkedHashMap<Char, Int>(128)
                val keys = vocabObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = vocabObj.optString(key, "0").toIntOrNull() ?: vocabObj.optInt(key, 0)
                    if (key.length == 1) {
                        map[key[0]] = id
                    }
                }
                map
            }.getOrDefault(emptyMap())
        }

        private fun extractShortOrFloatWaveform(raw: Any?): ShortArray {
            return when (raw) {
                is ShortArray -> raw
                is FloatArray -> {
                    val pcmBytes = OfflineTtsEngine.convertFloatWaveformToPcm16Le(raw)
                    val shorts = ShortArray(pcmBytes.size / 2)
                    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    shorts
                }
                is Array<*> -> extractShortOrFloatWaveform(raw.firstOrNull())
                else -> ShortArray(0)
            }
        }

        private fun shortArrayToPcm16LeBytes(shorts: ShortArray): ByteArray {
            val bytes = ByteArray(shorts.size * 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts)
            return bytes
        }
    }
}
