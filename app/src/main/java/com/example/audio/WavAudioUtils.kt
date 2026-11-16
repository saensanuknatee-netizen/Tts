package com.example.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object WavAudioUtils {
    const val DEFAULT_GEMINI_SAMPLE_RATE = 24000
    const val WAV_HEADER_SIZE = 44

    /**
     * Computes a deterministic SHA-256 cache key for a given text + voice personality + prosody settings.
     */
    fun computeCacheKey(
        normalizedText: String,
        modelId: String,
        voiceName: String,
        styleName: String,
        speechRate: Float,
        pitch: Float,
        volume: Float = 1.0f,
        voiceWarmth: Int = 65,
        expressiveness: Int = 65,
        responseStyleId: String = "CONVERSATIONAL"
    ): String {
        val raw = buildString {
            append(normalizedText.trim())
            append('|').append(modelId)
            append('|').append(voiceName)
            append('|').append(styleName)
            append('|').append(responseStyleId)
            append('|').append("%.2f".format(speechRate))
            append('|').append("%.2f".format(pitch))
            append('|').append("%.2f".format(volume))
            append('|').append(voiceWarmth)
            append('|').append(expressiveness)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Checks whether a byte array already begins with a RIFF/WAVE header.
     */
    fun isRiffWav(bytes: ByteArray): Boolean {
        if (bytes.size < WAV_HEADER_SIZE) return false
        return bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'A'.code.toByte() &&
            bytes[10] == 'V'.code.toByte() &&
            bytes[11] == 'E'.code.toByte()
    }

    /**
     * Parses the sample rate from a valid RIFF WAV header (bytes 24..27, little-endian).
     */
    fun extractSampleRateFromWav(wavBytes: ByteArray): Int {
        if (!isRiffWav(wavBytes)) return DEFAULT_GEMINI_SAMPLE_RATE
        return ByteBuffer.wrap(wavBytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 96000)
    }

    /**
     * Extracts raw 16-bit PCM payload from a WAV file, or returns the array as-is if already raw PCM.
     */
    fun extractPcmFromWav(bytes: ByteArray): ByteArray {
        if (!isRiffWav(bytes)) return bytes
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId == "data") {
                val start = offset + 8
                val end = min(bytes.size, start + max(0, chunkSize))
                return bytes.copyOfRange(start, end)
            }
            offset += 8 + max(0, chunkSize)
        }
        return if (bytes.size > WAV_HEADER_SIZE) bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size) else ByteArray(0)
    }

    /**
     * Real 16-bit PCM DSP Audio Post-Processor for Volume Gain, Voice Warmth (Low-Shelf / Warmth Filter),
     * and Speed / Rate adjustment.
     */
    fun applyAudioPostProcessing(
        wavOrPcmBytes: ByteArray,
        sampleRateHz: Int = DEFAULT_GEMINI_SAMPLE_RATE,
        volume: Float = 1.0f,
        voiceWarmth: Int = 65,
        speedRatio: Float = 1.0f
    ): ByteArray {
        val rawPcm = extractPcmFromWav(wavOrPcmBytes)
        val inSampleCount = rawPcm.size / 2
        if (inSampleCount <= 4) {
            return ensureWavContainer(rawPcm, sampleRateHz)
        }

        val inShorts = ShortArray(inSampleCount)
        ByteBuffer.wrap(rawPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inShorts)

        val safeSpeed = speedRatio.coerceIn(0.55f, 1.85f)
        val outSampleCount = (inSampleCount / safeSpeed).toInt().coerceAtLeast(1)
        val outBytes = ByteArray(outSampleCount * 2)
        val outBuf = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val gain = volume.coerceIn(0.10f, 1.50f)
        // Warmth smoothing coefficient (0.0 = bright/crisp, 0.38 = warm/velvety)
        val warmthAlpha = ((voiceWarmth.coerceIn(0, 100) - 40) / 160f).coerceIn(0f, 0.38f)
        var prevFiltered = 0f

        for (i in 0 until outSampleCount) {
            val srcPos = i * safeSpeed
            val idx0 = srcPos.toInt().coerceIn(0, inSampleCount - 1)
            val idx1 = (idx0 + 1).coerceIn(0, inSampleCount - 1)
            val frac = srcPos - idx0

            val s0 = inShorts[idx0].toFloat() / 32768f
            val s1 = inShorts[idx1].toFloat() / 32768f
            val interpolated = s0 + (s1 - s0) * frac

            // Apply subtle warmth filter
            val warmed = if (warmthAlpha > 0.01f) {
                val filtered = (1f - warmthAlpha) * interpolated + warmthAlpha * prevFiltered
                prevFiltered = filtered
                filtered
            } else {
                interpolated
            }

            // Apply volume gain with soft limiting
            val gained = (warmed * gain).coerceIn(-0.98f, 0.98f)
            outBuf.put(i, (gained * 32767f).toInt().toShort())
        }

        return ensureWavContainer(outBytes, sampleRateHz = sampleRateHz)
    }

    /**
     * Wraps raw 16-bit mono little-endian PCM bytes into a standard 44-byte RIFF/WAVE container.
     * If the input already has a RIFF header, returns it unchanged.
     */
    fun ensureWavContainer(
        audioBytes: ByteArray,
        sampleRateHz: Int = DEFAULT_GEMINI_SAMPLE_RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        if (isRiffWav(audioBytes)) return audioBytes

        val pcmDataSize = audioBytes.size
        val totalFileSizeMinus8 = pcmDataSize + 36
        val byteRate = sampleRateHz * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()

        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put('R'.code.toByte())
        header.put('I'.code.toByte())
        header.put('F'.code.toByte())
        header.put('F'.code.toByte())
        header.putInt(totalFileSizeMinus8)
        header.put('W'.code.toByte())
        header.put('A'.code.toByte())
        header.put('V'.code.toByte())
        header.put('E'.code.toByte())
        header.put('f'.code.toByte())
        header.put('m'.code.toByte())
        header.put('t'.code.toByte())
        header.put(' '.code.toByte())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRateHz)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put('d'.code.toByte())
        header.put('a'.code.toByte())
        header.put('t'.code.toByte())
        header.put('a'.code.toByte())
        header.putInt(pcmDataSize)

        val out = ByteArrayOutputStream(WAV_HEADER_SIZE + pcmDataSize)
        out.write(header.array())
        out.write(audioBytes)
        return out.toByteArray()
    }

    /**
     * Estimates duration in milliseconds from a 16-bit mono WAV or PCM byte array.
     */
    fun calculateDurationMs(wavOrPcmBytes: ByteArray, defaultSampleRate: Int = DEFAULT_GEMINI_SAMPLE_RATE): Long {
        val sampleRate = if (isRiffWav(wavOrPcmBytes)) extractSampleRateFromWav(wavOrPcmBytes) else defaultSampleRate
        val pcmBytes = extractPcmFromWav(wavOrPcmBytes)
        val totalSamples = pcmBytes.size / 2
        return ((totalSamples.toDouble() / sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(50L)
    }

    /**
     * Computes normalized RMS amplitude bars (0.08f..1.0f) from a WAV or PCM byte array for the UI waveform visualizer.
     */
    fun computeWaveformAmplitudes(wavOrPcmBytes: ByteArray, bucketCount: Int = 40): List<Float> {
        val pcm = extractPcmFromWav(wavOrPcmBytes)
        val sampleCount = pcm.size / 2
        if (sampleCount < bucketCount * 2) {
            return List(bucketCount) { 0.15f }
        }
        val samplesPerBucket = sampleCount / bucketCount
        val rmsList = FloatArray(bucketCount)
        var maxRms = 0.01f

        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (b in 0 until bucketCount) {
            val start = b * samplesPerBucket
            val end = min(sampleCount, start + samplesPerBucket)
            var sumSq = 0.0
            var count = 0
            var i = start
            while (i < end) {
                val sample = buffer.get(i).toFloat() / 32768f
                sumSq += sample * sample
                count++
                i += max(1, (end - start) / 64)
            }
            val rms = if (count > 0) sqrt(sumSq / count).toFloat() else 0f
            rmsList[b] = rms
            if (rms > maxRms) maxRms = rms
        }

        return rmsList.map { (it / maxRms).coerceIn(0.08f, 1.0f) }
    }

    /**
     * On-device deterministic Thai 5-Tone Formant/Harmonic Acoustic Synthesizer.
     * Synthesizes syllable-segmented harmonic formant speech envelopes following Thai tonal contours:
     * - Mid (สามัญ), Low (เอก ่), Falling (โท ้), High (ตรี ๊), Rising (จัตวา ๋)
     */
    fun synthesizeOfflineThaiToneAcousticWav(
        text: String,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 1.0f,
        voiceWarmth: Int = 65,
        sampleRate: Int = DEFAULT_GEMINI_SAMPLE_RATE
    ): ByteArray {
        val cleanText = text.trim().ifEmpty { "สวัสดี" }
        val clusters = segmentThaiSyllableClusters(cleanText)
        val syllableDurationSec = (0.145 / speechRate.coerceIn(0.5f, 2.0f)).coerceIn(0.06, 0.28)
        val samplesPerSyllable = (syllableDurationSec * sampleRate).toInt()
        val totalSamples = (clusters.size * samplesPerSyllable).coerceIn(sampleRate / 4, sampleRate * 25)
        val pcmBytes = ByteArray(totalSamples * 2)
        val shortBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        val baseF0 = 175.0 * pitch.coerceIn(0.6f, 1.6f)
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0

        clusters.forEachIndexed { idx, cluster ->
            if (cluster.isBlank() || cluster.all { it == ' ' || it == ',' || it == '.' }) {
                val startSample = idx * samplesPerSyllable
                for (s in 0 until samplesPerSyllable) {
                    if (startSample + s < totalSamples) {
                        shortBuf.put(startSample + s, 0)
                    }
                }
            } else {
                val toneType = detectThaiToneContour(cluster)
                val charHash = cluster.fold(0) { acc, c -> acc * 31 + c.code }
                val f1 = 420.0 + abs(charHash % 320)
                val f2 = 1150.0 + abs((charHash / 7) % 950)

                val startSample = idx * samplesPerSyllable
                for (s in 0 until samplesPerSyllable) {
                    val globalSample = startSample + s
                    if (globalSample >= totalSamples) break
                    val normT = s.toDouble() / samplesPerSyllable.toDouble()

                    val toneMultiplier = when (toneType) {
                        1 -> 0.92 - 0.10 * normT
                        2 -> 1.08 + 0.10 * sin(normT * PI) - 0.24 * normT * normT
                        3 -> 1.06 + 0.18 * normT
                        4 -> 0.90 - 0.08 * sin(normT * PI) + 0.25 * normT * normT
                        else -> 1.0 - 0.03 * normT
                    }

                    val f0 = baseF0 * toneMultiplier
                    phase1 += (2.0 * PI * f0) / sampleRate
                    phase2 += (2.0 * PI * f1) / sampleRate
                    phase3 += (2.0 * PI * f2) / sampleRate

                    val attack = (normT / 0.12).coerceAtMost(1.0)
                    val release = ((1.0 - normT) / 0.18).coerceAtMost(1.0)
                    val envelope = attack * release * exp(-0.4 * normT)

                    val glottal = 0.55 * sin(phase1) + 0.25 * sin(2.0 * phase1) + 0.12 * sin(3.0 * phase1)
                    val formantResonance = 0.22 * sin(phase2) + 0.10 * sin(phase3)
                    val sampleVal = ((glottal + formantResonance) * envelope * 0.65).coerceIn(-0.98, 0.98)
                    shortBuf.put(globalSample, (sampleVal * 32767.0).toInt().toShort())
                }
            }
        }

        return applyAudioPostProcessing(
            wavOrPcmBytes = pcmBytes,
            sampleRateHz = sampleRate,
            volume = volume,
            voiceWarmth = voiceWarmth,
            speedRatio = 1.0f
        )
    }

    private fun detectThaiToneContour(cluster: String): Int {
        return when {
            cluster.contains('\u0E48') -> 1 // ไม้เอก
            cluster.contains('\u0E49') -> 2 // ไม้โท
            cluster.contains('\u0E4A') -> 3 // ไม้ตรี
            cluster.contains('\u0E4B') -> 4 // ไม้จัตวา
            else -> 0 // สามัญ
        }
    }

    fun segmentThaiSyllableClusters(text: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text) {
            val isCombiningThai = ch.code in 0x0E31..0x0E3A || ch.code in 0x0E47..0x0E4E
            if (ch.isWhitespace()) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                result.add(" ")
            } else if (isCombiningThai) {
                current.append(ch)
            } else {
                if (current.length >= 3) {
                    result.add(current.toString())
                    current.clear()
                }
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result.ifEmpty { listOf("สวัสดี") }
    }
}
