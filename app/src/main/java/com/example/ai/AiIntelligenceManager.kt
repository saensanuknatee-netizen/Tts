package com.example.ai

import android.util.Log
import com.example.audio.WavAudioUtils
import com.example.tts.ResponseStyle
import com.example.tts.SynthesisConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveGenerationConfig
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

private const val TAG = "AiIntelligenceMgr"

enum class AssistantModelMode(
    val modelId: String,
    val titleTh: String,
    val subtitleTh: String
) {
    ARGON_HIGH_THINKING(
        modelId = "gemini-4-argon",
        titleTh = "Gemini 4 Argon (High Thinking)",
        subtitleTh = "วิเคราะห์เชิงลึกด้วย ThinkingLevel.HIGH สำหรับบุคลิกเสียง คำพ้องรูป และการออกแบบจังหวะการพูด"
    ),
    FLASH_SEARCH_MAPS(
        modelId = "gemini-3.8-flash",
        titleTh = "Gemini 3.8 Flash (Search + Maps Grounding)",
        subtitleTh = "เชื่อมต่อข้อมูลจริงจาก Google Search และ Google Maps สำหรับสร้างคำสั่งนำทางและข่าวสาร"
    ),
    FLASH_LITE_FAST(
        modelId = "gemini-3.5-flash-lite",
        titleTh = "Gemini 3.5 Flash-Lite (Ultra-Low Latency)",
        subtitleTh = "ตอบกลับความเร็วสูงพิเศษสำหรับเกลาคำพูดสั้นๆ ทันใจ (Verified Firebase AI SDK Model ID)"
    )
}

enum class LyriaModelOption(
    val modelId: String,
    val titleTh: String,
    val subtitleTh: String
) {
    CLIP_PREVIEW(
        modelId = "lyria-3-clip-preview",
        titleTh = "Lyria 3 Clip Preview (≤ 30 วินาที)",
        subtitleTh = "สร้างเสียงดนตรีประกอบสั้นๆ หรือจิงเกิลเปิดรายการพอดแคสต์"
    ),
    PRO_PREVIEW(
        modelId = "lyria-3-pro-preview",
        titleTh = "Lyria 3 Pro Preview (Full Track)",
        subtitleTh = "สร้างแทร็กดนตรีบรรเลงความยาวเต็มรูปแบบสำหรับประกอบเสียงบรรยาย"
    )
}

data class ChatMessageItem(
    val id: String,
    val role: String, // "user" | "model" | "error"
    val text: String,
    val modelBadge: String,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface LiveVoiceState {
    data object Disconnected : LiveVoiceState
    data object Connecting : LiveVoiceState
    data class Connected(val isAudioActive: Boolean, val statusTextTh: String) : LiveVoiceState
    data class Error(val message: String) : LiveVoiceState
}

sealed interface MusicGenState {
    data object Idle : MusicGenState
    data class Loading(val modelId: String) : MusicGenState
    data class Success(val wavBytes: ByteArray, val description: String, val modelId: String) : MusicGenState
    data class Error(val message: String) : MusicGenState
}

@OptIn(PublicPreviewAPI::class)
class AiIntelligenceManager {

    private val systemPrompt = content("system") {
        text(
            "คุณคือผู้เชี่ยวชาญด้านภาษาศาสตร์ภาษาไทย การสังเคราะห์เสียงพูด (TTS) ระบบบุคลิกเสียง (Voice Personality) " +
                "และการออกแบบข้อความตามสไตล์การตอบ (Response Style) สำหรับแอป SiamVoice Hybrid TTS " +
                "ห้ามเปลี่ยนความหมายเดิมของข้อความ และให้คำแนะนำที่เป็นธรรมชาติชัดเจน"
        )
    }

    // 1. Gemini 4 Argon with ThinkingLevel.HIGH (Do NOT set maxOutputTokens per requirements)
    private val argonHighThinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            },
            systemInstruction = systemPrompt
        )
    }

    // 2. Gemini 3.8 Flash with Google Search and Google Maps grounding tools
    private val flashGroundedModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(
                Tool.googleSearch(),
                Tool.googleMaps()
            ),
            systemInstruction = systemPrompt
        )
    }

    // 3. Gemini 3.5 Flash-Lite for ultra-low-latency responses (official Firebase AI Logic model ID)
    private val flashLiteFastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = systemPrompt
        )
    }

    private var activeChatMode: AssistantModelMode? = null
    private var activeChatSession: Chat? = null

    private val _liveState = MutableStateFlow<LiveVoiceState>(LiveVoiceState.Disconnected)
    val liveState: StateFlow<LiveVoiceState> = _liveState.asStateFlow()

    private var activeLiveSession: LiveSession? = null

    /**
     * Sends a message in the multi-turn chatbot respecting the active ResponseStyle
     * (Concise, Balanced, Detailed, Conversational, Assistant, Navigation).
     */
    suspend fun sendChatMessage(
        userPrompt: String,
        mode: AssistantModelMode,
        responseStyle: ResponseStyle,
        existingHistory: List<ChatMessageItem>
    ): Result<String> {
        return try {
            val targetModel = when (mode) {
                AssistantModelMode.ARGON_HIGH_THINKING -> argonHighThinkingModel
                AssistantModelMode.FLASH_SEARCH_MAPS -> flashGroundedModel
                AssistantModelMode.FLASH_LITE_FAST -> flashLiteFastModel
            }

            if (activeChatSession == null || activeChatMode != mode) {
                val mappedHistory = existingHistory
                    .filter { it.role == "user" || it.role == "model" }
                    .map { item ->
                        content(item.role) { text(item.text) }
                    }
                activeChatSession = targetModel.startChat(history = mappedHistory)
                activeChatMode = mode
            }

            val styledUserPrompt = "[รูปแบบการตอบ: ${responseStyle.titleTh} — ${responseStyle.aiSystemInstructionTh}]\n$userPrompt"
            val chat = activeChatSession ?: targetModel.startChat()
            val response = withTimeout(60_000L) {
                chat.sendMessage(styledUserPrompt)
            }
            val text = response.text?.trim()
            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(IllegalStateException("โมเดล ${mode.modelId} ส่งคำตอบว่างกลับมา"))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat timed out on ${mode.modelId}", e)
            Result.failure(IllegalStateException("หมดเวลาการเชื่อมต่อ 60 วินาทีสำหรับ ${mode.modelId}"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chat failed on ${mode.modelId}", e)
            Result.failure(e)
        }
    }

    /**
     * Uses Gemini 4 Argon (with ThinkingLevel.HIGH) to analyze the user's selected Voice Style,
     * Response Style, and speaking parameters alongside the Thai input text, returning:
     * 1. Phonetic / homograph disambiguation & Thai-English mixed rhythm advice
     * 2. Optimized style instruction for the selected TTS model
     * 3. Natural pause & sentence boundary recommendations without altering meaning
     */
    suspend fun analyzeThaiPronunciationWithArgon(
        rawThaiText: String,
        config: SynthesisConfig
    ): Result<String> {
        return try {
            val prompt = """
                วิเคราะห์ข้อความภาษาไทยและการตั้งค่าบุคลิกเสียงต่อไปนี้ด้วยเหตุผลเชิงลึก (Gemini 4 Argon):
                - ข้อความต้นฉบับ: "$rawThaiText"
                - โมเดลเป้าหมาย: ${config.model.displayName} (${config.model.modelId})
                - เสียงพูด (Voice): ${config.voice.voiceId}
                - สไตล์เสียง (Voice Style): ${config.style.titleTh}
                - รูปแบบการสื่อสาร (Response Style): ${config.responseStyle.titleTh}
                - พารามิเตอร์: ความเร็ว ${"%.2f".format(config.speechRate)}x, Pitch ${"%.2f".format(config.pitch)}x, Expressiveness ${config.expressiveness}%, Emotional Intensity ${config.emotionalIntensity}%, Warmth ${config.voiceWarmth}%, Clarity ${config.pronunciationClarity}%
                
                สิ่งที่ต้องทำ (โดยห้ามเปลี่ยนความหมายของข้อความต้นฉบับเด็ดขาด):
                1. วิเคราะห์คำพ้องรูป คำย่อ ตัวเลข เวลา หน่วยวัด หรือคำทับศัพท์ภาษาอังกฤษในประโยค แล้วระบุคำอ่านภาษาไทยที่ถูกต้อง
                2. แนะนำจุดเว้นจังหวะหายใจ (Natural Pauses) และการแบ่งขอบเขตประโยคให้เข้ากับสไตล์ ${config.style.titleEn}
                3. สร้างคำสั่งกำกับสไตล์เสียง (Optimized Style Instruction) ที่เหมาะที่สุดสำหรับโมเดล ${config.model.modelId}
            """.trimIndent()

            val response = withTimeout(60_000L) {
                argonHighThinkingModel.generateContent(prompt)
            }
            val output = response.text?.trim()
            if (!output.isNullOrBlank()) {
                Result.success(output)
            } else {
                Result.failure(IllegalStateException("Gemini 4 Argon ไม่ได้ส่งผลการวิเคราะห์กลับมา"))
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Result.failure(IllegalStateException("การวิเคราะห์ด้วย Gemini 4 Argon หมดเวลา (60s)"))
        } catch (e: Exception) {
            Log.e(TAG, "Argon phonetic analysis failed", e)
            Result.failure(e)
        }
    }

    /**
     * Level 2 AI Conversational Script Rewriter powered by Gemini 4 Argon (with ThinkingLevel.HIGH).
     * Rewrites the input Thai sentence to sound natural, friendly, and tailored to the active
     * VoiceStylePreset, ResponseStyle, and PolitenessParticle while keeping all facts intact.
     * Example: "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ"
     */
    suspend fun rewriteScriptWithArgon(
        rawThaiText: String,
        config: SynthesisConfig
    ): Result<String> {
        return try {
            val prompt = """
                คุณคือระบบดัดแปลงบทพูดภาษาไทยระดับที่ 2 (Level 2 Conversational Script Rewriter) ของแอป SiamVoice TTS
                จงดัดแปลงประโยคต้นฉบับต่อไปนี้ให้ฟังดูเป็นธรรมชาติ เป็นกันเอง และเข้ากับบุคลิกเสียงที่เลือก โดยห้ามเปลี่ยนข้อเท็จจริง ตัวเลข ระยะทาง ทิศทาง หรือความหมายหลักเด็ดขาด:
                - ประโยคต้นฉบับ: "$rawThaiText"
                - สไตล์เสียงพูด (Voice Style): ${config.style.titleTh} (${config.style.subtitleTh})
                - รูปแบบการตอบ (Response Style): ${config.responseStyle.titleTh} (${config.responseStyle.subtitleTh})
                - คำลงท้ายที่ต้องการ: ${config.politenessParticle.labelTh} (เช่น "${config.politenessParticle.actionEndingTh}" หรือ "${config.politenessParticle.statementEndingTh}")
                - ความอบอุ่นของเสียง (Warmth): ${config.voiceWarmth}%, ความเป็นทางการ (Formality): ${config.formality}%
                - ตัวอย่างการดัดแปลง: "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ"
                
                ตอบกลับเฉพาะประโยคภาษาไทยที่ดัดแปลงเสร็จแล้วเพียง 1 ประโยคเท่านั้น ห้ามใส่เครื่องหมายคำพูดและห้ามอธิบายเพิ่มเติม
            """.trimIndent()

            val response = withTimeout(45_000L) {
                argonHighThinkingModel.generateContent(prompt)
            }
            val rewritten = response.text?.trim()?.removeSurrounding("\"")?.trim()
            if (!rewritten.isNullOrBlank()) {
                Result.success(rewritten)
            } else {
                Result.failure(IllegalStateException("Gemini 4 Argon ไม่ได้ส่งประโยคที่ดัดแปลงกลับมา"))
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            Result.failure(IllegalStateException("การดัดแปลงคำพูดด้วย Gemini 4 Argon หมดเวลา"))
        } catch (e: Exception) {
            Log.e(TAG, "Argon script rewrite failed", e)
            Result.failure(e)
        }
    }

    /**
     * Connects to Gemini Live API (gemini-3.8-live) for real-time bidirectional voice conversations.
     */
    suspend fun connectLiveVoiceSession(enableMicAudio: Boolean) {
        _liveState.value = LiveVoiceState.Connecting
        try {
            val liveConfig = LiveGenerationConfig.Builder()
                .setResponseModality(ResponseModality.AUDIO)
                .setSpeechConfig(SpeechConfig(Voice("Kore"), "th-TH"))
                .build()

            val liveModel = Firebase.ai.liveModel(
                modelName = "gemini-3.8-live",
                generationConfig = liveConfig,
                systemInstruction = systemPrompt
            )

            val session = withTimeout(30_000L) {
                liveModel.connect()
            }
            activeLiveSession = session

            if (enableMicAudio) {
                session.startAudioConversation()
                _liveState.value = LiveVoiceState.Connected(
                    isAudioActive = true,
                    statusTextTh = "เชื่อมต่อ gemini-3.8-live สำเร็จ · กำลังสนทนาด้วยเสียงแบบเรียลไทม์"
                )
            } else {
                _liveState.value = LiveVoiceState.Connected(
                    isAudioActive = false,
                    statusTextTh = "เชื่อมต่อเซสชัน gemini-3.8-live สำเร็จ (โหมดส่งข้อความ/รอเปิดไมโครโฟน)"
                )
            }
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            _liveState.value = LiveVoiceState.Error("หมดเวลาการเชื่อมต่อ gemini-3.8-live (30 วินาที)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect gemini-3.8-live session", e)
            _liveState.value = LiveVoiceState.Error(
                "เชื่อมต่อ gemini-3.8-live ไม่สำเร็จ: ${e.localizedMessage ?: "เกิดข้อผิดพลาด"}"
            )
        }
    }

    suspend fun sendRealtimePromptToLiveSession(prompt: String) {
        val session = activeLiveSession ?: return
        try {
            session.send(prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending prompt to live session", e)
            _liveState.value = LiveVoiceState.Error("ส่งข้อมูลไปยัง Live API ล้มเหลว: ${e.localizedMessage}")
        }
    }

    suspend fun disconnectLiveVoiceSession() {
        try {
            activeLiveSession?.stopAudioConversation()
            activeLiveSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiveSession", e)
        } finally {
            activeLiveSession = null
            _liveState.value = LiveVoiceState.Disconnected
        }
    }

    /**
     * Generates music or audio backing clips using Lyria 3 (lyria-3-clip-preview or lyria-3-pro-preview).
     */
    suspend fun generateLyriaMusic(
        prompt: String,
        option: LyriaModelOption
    ): MusicGenState {
        return try {
            val model = Firebase.ai.generativeModel(
                modelName = option.modelId,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                }
            )
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val inlineAudio = response.candidates.firstOrNull()
                ?.content?.parts
                ?.filterIsInstance<InlineDataPart>()
                ?.firstOrNull { it.inlineData.isNotEmpty() }

            if (inlineAudio != null) {
                val wavBytes = WavAudioUtils.ensureWavContainer(inlineAudio.inlineData)
                MusicGenState.Success(
                    wavBytes = wavBytes,
                    description = "สร้างเสียงดนตรีด้วย ${option.modelId} สำเร็จ (${wavBytes.size / 1024} KB)",
                    modelId = option.modelId
                )
            } else {
                val textFallback = response.text
                MusicGenState.Error(
                    textFallback ?: "โมเดล ${option.modelId} ไม่ได้ส่งข้อมูลเสียง AUDIO กลับมา"
                )
            }
        } catch (e: TimeoutCancellationException) {
            MusicGenState.Error("การสร้างดนตรีด้วย ${option.modelId} หมดเวลาหลัง 60 วินาที")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Lyria music generation failed", e)
            MusicGenState.Error("ไม่สามารถสร้างดนตรีด้วย ${option.modelId}: ${e.localizedMessage ?: "เกิดข้อผิดพลาด"}")
        }
    }
}
