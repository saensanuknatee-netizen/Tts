package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_audio")
data class CachedAudioEntity(
    @PrimaryKey val cacheKey: String,
    val originalText: String,
    val normalizedText: String,
    val providerUsed: String, // "GEMINI_CLOUD" | "ANDROID_LOCAL_TTS" | "DSP_THAI_OFFLINE"
    val modelId: String,
    val voiceName: String,
    val styleName: String,
    val speechRate: Float,
    val pitch: Float,
    val wavFilePath: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val fileSizeBytes: Long,
    val hitCount: Int = 1,
    val synthesisLatencyMs: Long = 0L,
    val isNavigationPhrase: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // "INFO" | "WARN" | "ERROR"
    val category: String, // "CLOUD_TTS" | "LOCAL_TTS" | "QUOTA" | "NETWORK" | "CACHE" | "TTS_SERVICE" | "AI_LOGIC"
    val provider: String,
    val errorCode: String? = null,
    val message: String,
    val isQuotaError: Boolean = false,
    val isNetworkError: Boolean = false,
    val fallbackTriggered: Boolean = false
)

@Entity(tableName = "pronunciation_rules")
data class PronunciationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thaiPattern: String,
    val spokenReplacement: String,
    val note: String,
    val enabled: Boolean = true
)

@Entity(tableName = "voice_profiles")
data class VoiceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String,
    val routingPolicyId: String,
    val modelId: String,
    val voiceId: String,
    val responseStyleId: String,
    val voiceStyleId: String,
    val speechRate: Float,
    val pitch: Float,
    val volume: Float,
    val emotionalIntensity: Int,
    val expressiveness: Int,
    val naturalPauses: Int,
    val pronunciationClarity: Int,
    val voiceWarmth: Int,
    val formality: Int,
    val speechStability: Int,
    val customStyleInstruction: String,
    val preferredLanguage: String,
    val createdAt: Long = System.currentTimeMillis()
)
