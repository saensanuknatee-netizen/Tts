package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

private const val TAG = "AudioPlayerManager"

data class PlaybackState(
    val isPlaying: Boolean = false,
    val activeFilePath: String? = null,
    val progressFraction: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastExportedPath: String? = null
)

class AudioPlayerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var playbackJob: Job? = null
    @Volatile
    private var currentAudioTrack: AudioTrack? = null

    fun playWavFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "WAV file does not exist: $filePath")
            return
        }
        val bytes = file.readBytes()
        playWavBytes(bytes, filePath)
    }

    fun playWavBytes(wavBytes: ByteArray, sourceTag: String = "in_memory.wav") {
        scope.launch {
            stopInternal()
            playbackJob = launch(Dispatchers.IO) {
                try {
                    val sampleRate = WavAudioUtils.extractSampleRateFromWav(wavBytes)
                    val pcmBytes = WavAudioUtils.extractPcmFromWav(wavBytes)
                    if (pcmBytes.isEmpty()) return@launch

                    val totalSamples = pcmBytes.size / 2
                    val totalDurationMs = ((totalSamples.toDouble() / sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(50L)

                    val minBufSize = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(4096)

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBufSize)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()

                    currentAudioTrack = track
                    track.play()

                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = true,
                        activeFilePath = sourceTag,
                        progressFraction = 0f,
                        currentPositionMs = 0L,
                        durationMs = totalDurationMs
                    )

                    val chunkSize = 4096
                    var offset = 0
                    while (isActive && offset < pcmBytes.size) {
                        val toWrite = min(chunkSize, pcmBytes.size - offset)
                        val written = track.write(pcmBytes, offset, toWrite)
                        if (written <= 0) break
                        offset += written
                        val progress = (offset.toFloat() / pcmBytes.size.toFloat()).coerceIn(0f, 1f)
                        val posMs = (progress * totalDurationMs).toLong()
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = true,
                            progressFraction = progress,
                            currentPositionMs = posMs
                        )
                    }

                    // Allow buffer tail to finish playing
                    delay(80L)
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    Log.e(TAG, "AudioTrack playback error", e)
                } finally {
                    currentAudioTrack = null
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        progressFraction = 1f,
                        currentPositionMs = _playbackState.value.durationMs
                    )
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            stopInternal()
        }
    }

    private suspend fun stopInternal() {
        try {
            currentAudioTrack?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
                runCatching { track.release() }
            }
            currentAudioTrack = null
            playbackJob?.cancelAndJoin()
        } catch (_: Exception) {
        } finally {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                progressFraction = 0f,
                currentPositionMs = 0L
            )
        }
    }

    /**
     * Exports a cached WAV file to the public Music/SiamVoice directory via MediaStore
     * (or app external storage on older SDKs) without requiring broad storage permissions.
     */
    suspend fun exportWavToDevice(sourceWavPath: String, customDisplayName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(sourceWavPath)
                require(srcFile.exists()) { "ไม่พบไฟล์ต้นฉบับ WAV ($sourceWavPath)" }
                val safeName = customDisplayName
                    .replace(Regex("[^a-zA-Z0-9ก-๙_\\-]"), "_")
                    .take(40)
                    .ifBlank { "siamvoice_tts" }
                val fileName = "${safeName}_${System.currentTimeMillis() % 100000}.wav"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/SiamVoiceTTS")
                        put(MediaStore.Audio.Media.IS_MUSIC, 0)
                        put(MediaStore.Audio.Media.IS_NOTIFICATION, 0)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            srcFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        val exportedDesc = "Music/SiamVoiceTTS/$fileName"
                        _playbackState.value = _playbackState.value.copy(lastExportedPath = exportedDesc)
                        return@runCatching exportedDesc
                    }
                }

                // Fallback to app-specific external files dir
                val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SiamVoiceTTS").apply {
                    mkdirs()
                }
                val destFile = File(exportDir, fileName)
                FileOutputStream(destFile).use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
                _playbackState.value = _playbackState.value.copy(lastExportedPath = destFile.absolutePath)
                destFile.absolutePath
            }
        }
}
