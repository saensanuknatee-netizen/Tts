package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CachedAudioEntity::class,
        DiagnosticLogEntity::class,
        PronunciationRuleEntity::class,
        VoiceProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class TtsDatabase : RoomDatabase() {
    abstract fun ttsDao(): TtsDao

    companion object {
        @Volatile
        private var INSTANCE: TtsDatabase? = null

        fun getInstance(context: Context): TtsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TtsDatabase::class.java,
                    "siamvoice_tts_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    seedDefaultPronunciationRules(instance.ttsDao())
                    seedDefaultVoiceProfiles(instance.ttsDao())
                }
                instance
            }
        }

        suspend fun seedDefaultPronunciationRules(dao: TtsDao) {
            if (dao.countPronunciationRules() == 0) {
                val defaults = listOf(
                    PronunciationRuleEntity(
                        thaiPattern = "กทม.",
                        spokenReplacement = "กรุงเทพมหานคร",
                        note = "คำย่อชื่อจังหวัดหลวง",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "รพ.",
                        spokenReplacement = "โรงพยาบาล",
                        note = "คำย่อสถานที่สำคัญสำหรับการนำทาง",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "ชม.",
                        spokenReplacement = "ชั่วโมง",
                        note = "หน่วยเวลาในการเดินทาง",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "กม./ชม.",
                        spokenReplacement = "กิโลเมตรต่อชั่วโมง",
                        note = "หน่วยความเร็วการจราจร",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "เพลาเช้า",
                        spokenReplacement = "เพ-ลา-เช้า",
                        note = "คำพ้องรูป 'เพลา' (เวลา)",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "เพลารถ",
                        spokenReplacement = "พะ-เลา-รด",
                        note = "คำพ้องรูป 'เพลา' (แกนล้อรถ)",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "หวงแหน",
                        spokenReplacement = "หวง-แห-น",
                        note = "คำพ้องรูป 'แหน'",
                        enabled = true
                    ),
                    PronunciationRuleEntity(
                        thaiPattern = "สวัสดี",
                        spokenReplacement = "สวัสดี",
                        note = "คำทักทายมาตรฐานภาษาไทย",
                        enabled = true
                    )
                )
                defaults.forEach { dao.insertPronunciationRule(it) }
            }
        }

        suspend fun seedDefaultVoiceProfiles(dao: TtsDao) {
            if (dao.countVoiceProfiles() == 0) {
                val presets = listOf(
                    VoiceProfileEntity(
                        profileName = "Natural Conversational (ค่าเริ่มต้น)",
                        routingPolicyId = "AUTO_HYBRID",
                        modelId = "gemini-3.8-flash-tts",
                        voiceId = "Kore",
                        responseStyleId = "CONVERSATIONAL",
                        voiceStyleId = "NATURAL",
                        speechRate = 1.00f,
                        pitch = 1.00f,
                        volume = 1.00f,
                        emotionalIntensity = 55,
                        expressiveness = 65,
                        naturalPauses = 55,
                        pronunciationClarity = 75,
                        voiceWarmth = 65,
                        formality = 50,
                        speechStability = 80,
                        customStyleInstruction = "พูดเป็นธรรมชาติ ลื่นไหลเหมือนเพื่อนสนทนา",
                        preferredLanguage = "th-TH"
                    ),
                    VoiceProfileEntity(
                        profileName = "Siam Luxury Concierge (หรูหรานุ่มลึก)",
                        routingPolicyId = "AUTO_HYBRID",
                        modelId = "gemini-3.8-flash-tts",
                        voiceId = "Aoede",
                        responseStyleId = "ASSISTANT",
                        voiceStyleId = "LUXURY",
                        speechRate = 0.94f,
                        pitch = 0.95f,
                        volume = 1.00f,
                        emotionalIntensity = 50,
                        expressiveness = 60,
                        naturalPauses = 70,
                        pronunciationClarity = 88,
                        voiceWarmth = 90,
                        formality = 92,
                        speechStability = 88,
                        customStyleInstruction = "น้ำเสียงผู้ช่วยส่วนตัวระดับโรงแรมห้าดาว สุภาพประณีต",
                        preferredLanguage = "th-TH"
                    ),
                    VoiceProfileEntity(
                        profileName = "Bangkok Fast Navigator (นำทางด่วน)",
                        routingPolicyId = "SHORT_PHRASE_LOCAL_FIRST",
                        modelId = "gemini-3.8-flash-lite-tts",
                        voiceId = "Fenrir",
                        responseStyleId = "NAVIGATION",
                        voiceStyleId = "NAVIGATION",
                        speechRate = 1.08f,
                        pitch = 1.00f,
                        volume = 1.15f,
                        emotionalIntensity = 30,
                        expressiveness = 40,
                        naturalPauses = 35,
                        pronunciationClarity = 95,
                        voiceWarmth = 50,
                        formality = 70,
                        speechStability = 95,
                        customStyleInstruction = "กระชับ ชัดเจน สำหรับฟังขณะขับรถในกรุงเทพฯ",
                        preferredLanguage = "th-TH"
                    ),
                    VoiceProfileEntity(
                        profileName = "Thai Warm Storyteller (เล่านิทานอบอุ่น)",
                        routingPolicyId = "AUTO_HYBRID",
                        modelId = "gemini-3.8-flash-tts",
                        voiceId = "Charon",
                        responseStyleId = "DETAILED",
                        voiceStyleId = "STORYTELLING",
                        speechRate = 0.95f,
                        pitch = 0.98f,
                        volume = 1.00f,
                        emotionalIntensity = 80,
                        expressiveness = 85,
                        naturalPauses = 85,
                        pronunciationClarity = 82,
                        voiceWarmth = 85,
                        formality = 55,
                        speechStability = 72,
                        customStyleInstruction = "เว้นจังหวะน่าติดตาม น้ำเสียงอบอุ่นมีมิติ",
                        preferredLanguage = "th-TH"
                    )
                )
                presets.forEach { dao.insertVoiceProfile(it) }
            }
        }
    }
}
