package com.example.tts

import android.util.Log
import com.example.audio.WavAudioUtils
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.random.Random

/**
 * Structured error hierarchy for Gemini Cloud TTS operations via Firebase AI Logic.
 */
sealed class GeminiTtsException(
    message: String,
    cause: Throwable? = null,
    val isQuotaError: Boolean = false
) : Exception(message, cause) {

    class QuotaExceeded(
        val modelId: String,
        message: String = "โควตาการใช้งาน Gemini Cloud TTS ($modelId) เต็มชั่วคราว (HTTP 429 / Quota Exhausted)",
        cause: Throwable? = null
    ) : GeminiTtsException(message, cause, isQuotaError = true)

    class Timeout(
        val modelId: String,
        val timeoutMs: Long,
        message: String = "การเชื่อมต่อโมเดล $modelId หมดเวลาหลังจาก ${timeoutMs / 1000} วินาที",
        cause: Throwable? = null
    ) : GeminiTtsException(message, cause, isQuotaError = false)

    class EmptyAudioStream(
        val modelId: String,
        message: String = "โมเดล $modelId ไม่ได้ส่งข้อมูลกระแสเสียง InlineDataPart (AUDIO) กลับมา"
    ) : GeminiTtsException(message, null, isQuotaError = false)

    class NetworkOrApiError(
        val modelId: String,
        message: String,
        cause: Throwable? = null,
        isQuota: Boolean = false
    ) : GeminiTtsException(message, cause, isQuotaError = isQuota)
}

/**
 * Represents a decoded 16-bit Little-Endian Mono PCM audio chunk extracted from a Gemini response part.
 */
data class DecodedAudioChunk(
    val pcm16LeBytes: ByteArray,
    val sampleRateHz: Int,
    val mimeType: String
)

/**
 * Streaming events emitted by [GeminiTtsClient.streamSpeechPcm] while fetching and decoding
 * audio chunks from the Gemini 3.8 Flash TTS model.
 */
sealed interface GeminiAudioStreamEvent {
    data class Started(
        val modelId: String,
        val voiceId: String,
        val styledPrompt: String
    ) : GeminiAudioStreamEvent

    data class PcmChunk(
        val chunkIndex: Int,
        val pcm16LeBytes: ByteArray,
        val sampleRateHz: Int,
        val mimeType: String
    ) : GeminiAudioStreamEvent

    data class Completed(
        val result: GeminiTtsSynthesisResult
    ) : GeminiAudioStreamEvent
}

/**
 * Complete decoded audio result from [GeminiTtsClient].
 */
data class GeminiTtsSynthesisResult(
    val wavBytes: ByteArray,
    val rawPcmBytes: ByteArray,
    val sampleRateHz: Int,
    val durationMs: Long,
    val modelUsed: String,
    val voiceUsed: String,
    val chunkCount: Int,
    val mimeType: String
)

/**
 * Production-grade client using the Firebase AI Logic SDK (`Firebase.ai`) to fetch and decode
 * synthetic speech audio streams from **Gemini 3.8 Flash TTS** (`gemini-3.8-flash-tts`) and
 * **Gemini 3.8 Flash-Lite TTS** (`gemini-3.8-flash-lite-tts`), with automatic model fallback
 * to `gemini-3.1-flash-tts-preview`.
 *
 * Responsibilities:
 * 1. Configures official `generationConfig` with `ResponseModality.AUDIO` and `SpeechConfig(Voice, languageCode)`.
 * 2. Streams and decodes `InlineDataPart` audio responses (`generateContentStream` with unary fallback)
 *    into 2-byte-aligned 16-bit signed Little-Endian Mono PCM (`24,000 Hz` default).
 * 3. Applies optional post-processing (`volume`, `voiceWarmth`, `speechRate`) and wraps the decoded
 *    PCM stream into a standard 44-byte RIFF/WAVE container.
 * 4. Preserves cooperative coroutine cancellation and classifies quota (`QuotaExceededException` / HTTP 429)
 *    vs transient network failures for exponential backoff and hybrid local TTS fallback.
 */
@OptIn(PublicPreviewAPI::class)
class GeminiTtsClient(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxAttemptsPerModel: Int = 1,
    private val baseBackoffDelayMs: Long = 650L
) {

    /**
     * Lazily caches default `GenerativeModel` instances keyed by `(modelName, voiceId, languageCode, temperatureBucket)`
     * so `Firebase.ai` is never evaluated eagerly at class construction time.
     */
    private val defaultFlashModel: GenerativeModel by lazy {
        createAudioGenerativeModel(
            modelName = GeminiTtsModel.FLASH_3_8.modelId,
            voiceId = GeminiVoice.KORE.voiceId,
            languageCode = "th-TH",
            temperature = 0.65f
        )
    }

    /**
     * Creates a configured Firebase AI `GenerativeModel` for speech synthesis using the official
     * `ResponseModality.AUDIO` and `SpeechConfig` schema.
     */
    fun createAudioGenerativeModel(
        modelName: String = GeminiTtsModel.FLASH_3_8.modelId,
        voiceId: String = GeminiVoice.KORE.voiceId,
        languageCode: String = "th-TH",
        temperature: Float = 0.65f
    ): GenerativeModel {
        val genConfig = generationConfig {
            responseModalities = listOf(ResponseModality.AUDIO)
            this.temperature = temperature.coerceIn(0.1f, 1.2f)
            speechConfig = SpeechConfig(
                voice = Voice(voiceId),
                languageCode = languageCode.ifBlank { "th-TH" }
            )
        }
        return Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = genConfig
        )
    }

    /**
     * Streams and decodes audio from Gemini 3.8 Flash TTS (or the model specified in [config])
     * as a cold [Flow] of [GeminiAudioStreamEvent].
     */
    fun streamSpeechPcm(
        normalizedText: String,
        config: SynthesisConfig = SynthesisConfig(),
        modelNameOverride: String? = null
    ): Flow<GeminiAudioStreamEvent> = flow {
        val targetModel = modelNameOverride ?: config.model.modelId
        val styledPrompt = config.buildGeminiStylePrompt(normalizedText)
        emit(
            GeminiAudioStreamEvent.Started(
                modelId = targetModel,
                voiceId = config.voice.voiceId,
                styledPrompt = styledPrompt
            )
        )

        val pcmBuffer = ByteArrayOutputStream(48_000)
        var detectedSampleRate = WavAudioUtils.DEFAULT_GEMINI_SAMPLE_RATE
        var detectedMimeType = "audio/pcm;rate=24000"
        var chunkIdx = 0

        val result = fetchAndDecodeSingleModel(
            modelName = targetModel,
            styledPrompt = styledPrompt,
            config = config,
            onDecodedChunk = { decodedChunk ->
                detectedSampleRate = decodedChunk.sampleRateHz
                detectedMimeType = decodedChunk.mimeType
                pcmBuffer.write(decodedChunk.pcm16LeBytes)
                emit(
                    GeminiAudioStreamEvent.PcmChunk(
                        chunkIndex = chunkIdx++,
                        pcm16LeBytes = decodedChunk.pcm16LeBytes,
                        sampleRateHz = decodedChunk.sampleRateHz,
                        mimeType = decodedChunk.mimeType
                    )
                )
            }
        )

        emit(GeminiAudioStreamEvent.Completed(result))
    }.flowOn(Dispatchers.IO)

    /**
     * Synthesizes speech using a single target model (defaults to `gemini-3.8-flash-tts`),
     * optionally invoking [onPcmChunk] as each decoded 16-bit PCM chunk arrives.
     */
    suspend fun synthesizeSpeech(
        normalizedText: String,
        config: SynthesisConfig = SynthesisConfig(),
        modelNameOverride: String? = null,
        onPcmChunk: (suspend (pcmChunk: ByteArray, sampleRateHz: Int) -> Unit)? = null
    ): Result<GeminiTtsSynthesisResult> = withContext(Dispatchers.IO) {
        val targetModel = modelNameOverride ?: config.model.modelId
        val styledPrompt = config.buildGeminiStylePrompt(normalizedText)
        try {
            val synthesisResult = fetchAndDecodeSingleModel(
                modelName = targetModel,
                styledPrompt = styledPrompt,
                config = config,
                onDecodedChunk = { chunk ->
                    onPcmChunk?.invoke(chunk.pcm16LeBytes, chunk.sampleRateHz)
                }
            )
            Result.success(synthesisResult)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
                val effectiveTimeout = if (timeoutMs == DEFAULT_TIMEOUT_MS) config.cloudTimeoutMs.coerceIn(1_000L, 60_000L) else timeoutMs
                val timeoutEx = GeminiTtsException.Timeout(
                    modelId = targetModel,
                    timeoutMs = effectiveTimeout,
                    cause = e
                )
                Log.e(TAG, "Timeout calling $targetModel", timeoutEx)
                Result.failure(timeoutEx)
            } catch (e: GeminiTtsException) {
                Log.e(TAG, "GeminiTtsClient error on $targetModel: ${e.message}", e)
                Result.failure(e)
            } catch (e: Exception) {
                val wrapped = classifyException(targetModel, e)
                Log.e(TAG, "Unexpected exception on $targetModel: ${wrapped.message}", wrapped)
                Result.failure(wrapped)
            }
        }

    /**
     * Synthesizes speech with candidate model fallback (`gemini-3.8-flash-tts` / `gemini-3.8-flash-lite-tts`
     * -> `gemini-3.1-flash-tts-preview`) and exponential backoff for transient errors.
     *
     * Quota exhaustion errors (`HTTP 429` / [QuotaExceededException]) fail fast on the affected model
     * without wasteful retry loops.
     */
    suspend fun synthesizeWithBackoff(
        normalizedText: String,
        config: SynthesisConfig,
        onAttemptStart: (suspend (attemptNumber: Int, modelName: String) -> Unit)? = null,
        onPcmChunk: (suspend (pcmChunk: ByteArray, sampleRateHz: Int) -> Unit)? = null
    ): Result<GeminiTtsSynthesisResult> = withContext(Dispatchers.IO) {
        val candidateModels = listOf(config.model.modelId, config.model.fallbackPreviewId).distinct()
        val styledPrompt = config.buildGeminiStylePrompt(normalizedText)
        val effectiveTimeout = if (timeoutMs == DEFAULT_TIMEOUT_MS) config.cloudTimeoutMs.coerceIn(1_000L, 60_000L) else timeoutMs
        var lastError: Throwable? = null
        var globalAttempt = 0

        for (modelName in candidateModels) {
            val retriesForModel = maxAttemptsPerModel.coerceAtLeast(1)
            for (modelAttempt in 1..retriesForModel) {
                globalAttempt++
                onAttemptStart?.invoke(globalAttempt, modelName)

                try {
                    val result = fetchAndDecodeSingleModel(
                        modelName = modelName,
                        styledPrompt = styledPrompt,
                        config = config,
                        onDecodedChunk = { chunk ->
                            onPcmChunk?.invoke(chunk.pcm16LeBytes, chunk.sampleRateHz)
                        }
                    )
                    return@withContext Result.success(result)
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                    val timeoutEx = GeminiTtsException.Timeout(
                        modelId = modelName,
                        timeoutMs = effectiveTimeout,
                        cause = e
                    )
                    lastError = timeoutEx
                    Log.e(TAG, "Timeout calling $modelName (attempt $globalAttempt)", timeoutEx)
                } catch (e: QuotaExceededException) {
                    val quotaEx = GeminiTtsException.QuotaExceeded(modelId = modelName, cause = e)
                    Log.w(TAG, "Quota exceeded on $modelName", quotaEx)
                    return@withContext Result.failure(quotaEx)
                } catch (e: GeminiTtsException) {
                    lastError = e
                    Log.e(TAG, "Attempt $globalAttempt failed on $modelName: ${e.message}", e)
                    if (e.isQuotaError) {
                        return@withContext Result.failure(e)
                    }
                } catch (e: Exception) {
                    val classified = classifyException(modelName, e)
                    lastError = classified
                    Log.e(TAG, "Attempt $globalAttempt failed on $modelName: ${classified.message}", classified)
                    if (classified.isQuotaError) {
                        return@withContext Result.failure(classified)
                    }
                }

                if (modelAttempt < retriesForModel) {
                    val backoffMs = (baseBackoffDelayMs * (1L shl (modelAttempt - 1))) +
                        Random.nextLong(80, 250)
                    delay(backoffMs)
                }
            }
        }

        Result.failure(
            lastError ?: GeminiTtsException.NetworkOrApiError(
                modelId = config.model.modelId,
                message = "ไม่สามารถสังเคราะห์เสียงผ่าน Gemini Cloud TTS ได้"
            )
        )
    }

    /**
     * Internal worker that invokes Firebase AI Logic (`generateContentStream` with unary `generateContent`
     * fallback), extracts and decodes all `InlineDataPart` audio chunks into 16-bit PCM, and builds
     * the final WAV container.
     */
    private suspend fun fetchAndDecodeSingleModel(
        modelName: String,
        styledPrompt: String,
        config: SynthesisConfig,
        onDecodedChunk: suspend (DecodedAudioChunk) -> Unit
    ): GeminiTtsSynthesisResult {
        val isDefaultFlashConfig = modelName == GeminiTtsModel.FLASH_3_8.modelId &&
            config.voice == GeminiVoice.KORE &&
            config.preferredLanguage.ifBlank { "th-TH" } == "th-TH" &&
            abs(config.computeGenerationTemperature() - 0.65f) < 0.01f

        val model = if (isDefaultFlashConfig) {
            defaultFlashModel
        } else {
            createAudioGenerativeModel(
                modelName = modelName,
                voiceId = config.voice.voiceId,
                languageCode = config.preferredLanguage.ifBlank { "th-TH" },
                temperature = config.computeGenerationTemperature()
            )
        }

        val accumulatedPcm = ByteArrayOutputStream(64_000)
        var sampleRateHz = WavAudioUtils.DEFAULT_GEMINI_SAMPLE_RATE
        var lastMimeType = "audio/pcm;rate=24000"
        var chunkCount = 0
        val effectiveTimeout = if (timeoutMs == DEFAULT_TIMEOUT_MS) config.cloudTimeoutMs.coerceIn(1_000L, 60_000L) else timeoutMs

        withTimeout(effectiveTimeout) {
            // 1. Attempt streaming audio generation first for low Time-to-First-Audio-Chunk
            var streamSucceeded = false
            try {
                model.generateContentStream(styledPrompt).collect { chunkResponse ->
                    val decodedChunks = extractAndDecodeAudioParts(chunkResponse)
                    for (decoded in decodedChunks) {
                        if (decoded.pcm16LeBytes.isNotEmpty()) {
                            sampleRateHz = decoded.sampleRateHz
                            lastMimeType = decoded.mimeType
                            accumulatedPcm.write(decoded.pcm16LeBytes)
                            chunkCount++
                            onDecodedChunk(decoded)
                        }
                    }
                }
                if (accumulatedPcm.size() > 0) {
                    streamSucceeded = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuotaExceededException) {
                throw GeminiTtsException.QuotaExceeded(modelId = modelName, cause = e)
            } catch (streamEx: Exception) {
                val msg = streamEx.localizedMessage.orEmpty()
                if (isQuotaMessage(msg)) {
                    throw GeminiTtsException.QuotaExceeded(modelId = modelName, cause = streamEx)
                }
                // If no bytes were emitted yet, fall back cleanly to unary generateContent()
                if (accumulatedPcm.size() == 0) {
                    Log.d(TAG, "Stream fallback to unary generateContent on $modelName: $msg")
                } else {
                    throw classifyException(modelName, streamEx)
                }
            }

            // 2. Fallback to unary generateContent() if streaming returned no audio parts
            if (!streamSucceeded && accumulatedPcm.size() == 0) {
                val unaryResponse = model.generateContent(styledPrompt)
                val decodedChunks = extractAndDecodeAudioParts(unaryResponse)
                for (decoded in decodedChunks) {
                    if (decoded.pcm16LeBytes.isNotEmpty()) {
                        sampleRateHz = decoded.sampleRateHz
                        lastMimeType = decoded.mimeType
                        accumulatedPcm.write(decoded.pcm16LeBytes)
                        chunkCount++
                        onDecodedChunk(decoded)
                    }
                }
            }
        }

        val rawPcmBytes = accumulatedPcm.toByteArray()
        if (rawPcmBytes.isEmpty()) {
            throw GeminiTtsException.EmptyAudioStream(modelId = modelName)
        }

        val needsPostProcess = abs(config.volume - 1.0f) > 0.02f ||
            abs(config.speechRate - 1.0f) > 0.02f ||
            config.voiceWarmth != 50

        val finalWavBytes = if (needsPostProcess) {
            WavAudioUtils.applyAudioPostProcessing(
                wavOrPcmBytes = rawPcmBytes,
                sampleRateHz = sampleRateHz,
                volume = config.volume,
                voiceWarmth = config.voiceWarmth,
                speedRatio = config.speechRate
            )
        } else {
            WavAudioUtils.ensureWavContainer(
                audioBytes = rawPcmBytes,
                sampleRateHz = sampleRateHz
            )
        }

        val finalPcmBytes = WavAudioUtils.extractPcmFromWav(finalWavBytes)
        val durationMs = WavAudioUtils.calculateDurationMs(finalWavBytes, sampleRateHz)

        return GeminiTtsSynthesisResult(
            wavBytes = finalWavBytes,
            rawPcmBytes = finalPcmBytes,
            sampleRateHz = sampleRateHz,
            durationMs = durationMs,
            modelUsed = modelName,
            voiceUsed = config.voice.voiceId,
            chunkCount = chunkCount,
            mimeType = lastMimeType
        )
    }

    private fun extractAndDecodeAudioParts(response: GenerateContentResponse): List<DecodedAudioChunk> {
        val inlineParts = response.candidates
            .asSequence()
            .mapNotNull { it.content }
            .flatMap { it.parts.asSequence() }
            .filterIsInstance<InlineDataPart>()
            .filter { it.inlineData.isNotEmpty() }
            .toList()

        return inlineParts.map { part ->
            decodeInlineAudioPartToPcm16Le(
                inlineBytes = part.inlineData,
                mimeType = part.mimeType
            )
        }
    }

    private fun classifyException(modelName: String, error: Throwable): GeminiTtsException {
        if (error is GeminiTtsException) return error
        if (error is QuotaExceededException) {
            return GeminiTtsException.QuotaExceeded(modelId = modelName, cause = error)
        }
        val msg = error.localizedMessage ?: error.message ?: "Gemini Cloud TTS error"
        if (isQuotaMessage(msg)) {
            return GeminiTtsException.QuotaExceeded(
                modelId = modelName,
                message = msg,
                cause = error
            )
        }
        return GeminiTtsException.NetworkOrApiError(
            modelId = modelName,
            message = msg,
            cause = error,
            isQuota = false
        )
    }

    companion object {
        private const val TAG = "GeminiTtsClient"
        const val DEFAULT_TIMEOUT_MS = 3_000L

        private val SAMPLE_RATE_REGEX = Regex("(?:rate|samplerate)=(\\d+)", RegexOption.IGNORE_CASE)

        /**
         * Checks whether an error message indicates Gemini API quota / rate-limit exhaustion.
         */
        fun isQuotaMessage(message: String?): Boolean {
            if (message.isNullOrBlank()) return false
            return message.contains("429") ||
                message.contains("quota", ignoreCase = true) ||
                message.contains("resource_exhausted", ignoreCase = true) ||
                message.contains("rate limit", ignoreCase = true)
        }

        /**
         * Parses the audio sample rate in Hz from a Gemini `InlineDataPart.mimeType`
         * (e.g., `"audio/pcm;rate=24000"` or `"audio/L16;rate=24000;channels=1"`).
         */
        fun parseSampleRateFromMimeType(
            mimeType: String?,
            defaultRate: Int = WavAudioUtils.DEFAULT_GEMINI_SAMPLE_RATE
        ): Int {
            if (mimeType.isNullOrBlank()) return defaultRate
            val match = SAMPLE_RATE_REGEX.find(mimeType)
            val parsed = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return defaultRate
            return parsed.coerceIn(8_000, 96_000)
        }

        /**
         * Determines whether the MIME type specifies big-endian RFC 2586 `audio/L16` without
         * an explicit `endianness=little-endian` parameter.
         * Note: Gemini's `audio/pcm;rate=24000` is already 16-bit signed little-endian PCM.
         */
        fun isBigEndianL16MimeType(mimeType: String?): Boolean {
            if (mimeType.isNullOrBlank()) return false
            val lower = mimeType.lowercase()
            if (!lower.contains("audio/l16")) return false
            return !lower.contains("endianness=little")
        }

        /**
         * Decodes raw bytes from a Gemini `InlineDataPart` into 2-byte-aligned 16-bit Little-Endian
         * Mono PCM (`AudioFormat.ENCODING_PCM_16BIT`) along with its detected sample rate.
         *
         * Handles:
         * - RIFF/WAVE containers (`audio/wav`, `audio/x-wav`, or RIFF magic header)
         * - Raw 16-bit Little-Endian PCM (`audio/pcm;rate=24000`)
         * - Raw 16-bit Big-Endian L16 PCM (`audio/L16;rate=24000`)
         */
        fun decodeInlineAudioPartToPcm16Le(
            inlineBytes: ByteArray,
            mimeType: String?
        ): DecodedAudioChunk {
            val effectiveMime = mimeType?.takeIf { it.isNotBlank() } ?: "audio/pcm;rate=24000"
            if (inlineBytes.isEmpty()) {
                return DecodedAudioChunk(
                    pcm16LeBytes = ByteArray(0),
                    sampleRateHz = parseSampleRateFromMimeType(effectiveMime),
                    mimeType = effectiveMime
                )
            }

            // 1. Check if payload is already wrapped in a RIFF/WAVE container
            if (WavAudioUtils.isRiffWav(inlineBytes)) {
                val wavRate = WavAudioUtils.extractSampleRateFromWav(inlineBytes)
                val rawPcm = WavAudioUtils.extractPcmFromWav(inlineBytes)
                val alignedSize = rawPcm.size - (rawPcm.size % 2)
                val alignedPcm = if (alignedSize == rawPcm.size) {
                    rawPcm
                } else {
                    rawPcm.copyOfRange(0, alignedSize)
                }
                return DecodedAudioChunk(
                    pcm16LeBytes = alignedPcm,
                    sampleRateHz = wavRate,
                    mimeType = effectiveMime
                )
            }

            // 2. Raw 16-bit PCM stream: ensure 2-byte alignment
            val sampleRate = parseSampleRateFromMimeType(effectiveMime)
            val alignedLength = inlineBytes.size - (inlineBytes.size % 2)
            if (alignedLength <= 0) {
                return DecodedAudioChunk(
                    pcm16LeBytes = ByteArray(0),
                    sampleRateHz = sampleRate,
                    mimeType = effectiveMime
                )
            }

            // 3. Convert big-endian L16 to little-endian PCM16 if needed
            val pcm16Le = if (isBigEndianL16MimeType(effectiveMime)) {
                val swapped = ByteArray(alignedLength)
                var i = 0
                while (i < alignedLength) {
                    swapped[i] = inlineBytes[i + 1]
                    swapped[i + 1] = inlineBytes[i]
                    i += 2
                }
                swapped
            } else if (alignedLength == inlineBytes.size) {
                inlineBytes
            } else {
                inlineBytes.copyOfRange(0, alignedLength)
            }

            return DecodedAudioChunk(
                pcm16LeBytes = pcm16Le,
                sampleRateHz = sampleRate,
                mimeType = effectiveMime
            )
        }
    }
}
