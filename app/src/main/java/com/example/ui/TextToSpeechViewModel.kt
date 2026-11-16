package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppCheckDiagnosticState
import com.example.AppCheckStatusHolder
import com.example.ai.AiIntelligenceManager
import com.example.ai.AssistantModelMode
import com.example.ai.ChatMessageItem
import com.example.ai.LiveVoiceState
import com.example.ai.LyriaModelOption
import com.example.ai.MusicGenState
import com.example.audio.AudioPlayerManager
import com.example.audio.PlaybackState
import com.example.data.local.CachedAudioEntity
import com.example.data.local.DiagnosticLogEntity
import com.example.data.local.PronunciationRuleEntity
import com.example.data.local.VoiceProfileEntity
import com.example.tts.DEFAULT_THAI_NAVIGATION_PHRASES
import com.example.tts.F5SynthesisParams
import com.example.tts.F5TokenizerMode
import com.example.tts.GeminiTtsModel
import com.example.tts.GeminiVoice
import com.example.tts.HybridTtsRepository
import com.example.tts.LocalTtsEngineStatus
import com.example.tts.OfflineVoiceProfile
import com.example.tts.PolitenessParticle
import com.example.tts.QueueTicket
import com.example.tts.ResponseStyle
import com.example.tts.RoutingPolicy
import com.example.tts.SpeechAdaptationLevel
import com.example.tts.SynthesisConfig
import com.example.tts.SynthesisOutcome
import com.example.tts.ThaiPoliteEnding
import com.example.tts.ThaiTextOptimizer
import com.example.tts.ThaiVoicePersonality
import com.example.tts.VoiceStylePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface SynthesisUiState {
    data object Idle : SynthesisUiState
    data class Synthesizing(val progressLabelTh: String) : SynthesisUiState
    data class Success(val outcome: SynthesisOutcome, val statusBannerTh: String) : SynthesisUiState
    data class Error(val errorMessageTh: String) : SynthesisUiState
}

/**
 * Consolidated UI state for [TextToSpeechViewModel] covering text input,
 * 2-level voice/phrasing adaptation, voice style presets, response styles,
 * and speaking parameters (speed, expressiveness, warmth, etc.).
 */
data class TextToSpeechUiState(
    val inputText: String = "",
    val level1VocalTonePreview: String = "",
    val level2RewrittenPreview: String = "",
    val effectiveSpokenText: String = "",
    val voiceStylePreset: VoiceStylePreset = VoiceStylePreset.NATURAL,
    val responseStyle: ResponseStyle = ResponseStyle.CONVERSATIONAL,
    val adaptationLevel: SpeechAdaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
    val politenessParticle: PolitenessParticle = PolitenessParticle.KRUB,
    val thaiVoicePersonality: ThaiVoicePersonality = ThaiVoicePersonality.FRIENDLY_COMPANION,
    val thaiPoliteEnding: ThaiPoliteEnding = ThaiPoliteEnding.AUTO_NATURAL,
    val disableRewritingForNavigation: Boolean = false,
    val speed: Float = 1.00f,
    val pitch: Float = 1.00f,
    val volume: Float = 1.00f,
    val emotionalIntensity: Int = 55,
    val expressiveness: Int = 65,
    val naturalPauses: Int = 55,
    val pronunciationClarity: Int = 75,
    val voiceWarmth: Int = 65,
    val formality: Int = 50,
    val speechStability: Int = 80,
    val customStyleInstruction: String = "",
    val isRewritingScriptWithAi: Boolean = false,
    val aiRewrittenSuggestion: String? = null
)

open class TextToSpeechViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("siamvoice_user_prefs", Context.MODE_PRIVATE)
    private val repository = HybridTtsRepository.getInstance(application)
    private val playerManager = AudioPlayerManager(application, viewModelScope)
    private val aiManager = AiIntelligenceManager()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _inputText = MutableStateFlow(
        "อีกสามร้อยเมตร เลี้ยวซ้าย"
    )
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _previewSampleText = MutableStateFlow(
        "อีกสามร้อยเมตร เลี้ยวซ้าย"
    )
    val previewSampleText: StateFlow<String> = _previewSampleText.asStateFlow()

    private val _config = MutableStateFlow(loadPersistedConfig())
    val config: StateFlow<SynthesisConfig> = _config.asStateFlow()

    private val _synthesisState = MutableStateFlow<SynthesisUiState>(SynthesisUiState.Idle)
    val synthesisState: StateFlow<SynthesisUiState> = _synthesisState.asStateFlow()

    private val _lastOutcome = MutableStateFlow<SynthesisOutcome?>(null)
    val lastOutcome: StateFlow<SynthesisOutcome?> = _lastOutcome.asStateFlow()

    private val _exportBanner = MutableStateFlow<String?>(null)
    val exportBanner: StateFlow<String?> = _exportBanner.asStateFlow()

    private val _isPreCachingNav = MutableStateFlow(false)
    val isPreCachingNav: StateFlow<Boolean> = _isPreCachingNav.asStateFlow()

    private val _enableArgonOnNav = MutableStateFlow(false)
    val enableArgonOnNav: StateFlow<Boolean> = _enableArgonOnNav.asStateFlow()

    // Level 2 AI Script Rewriter states
    private val _isRewritingScriptWithAi = MutableStateFlow(false)
    val isRewritingScriptWithAi: StateFlow<Boolean> = _isRewritingScriptWithAi.asStateFlow()

    private val _aiRewrittenSuggestion = MutableStateFlow<String?>(null)
    val aiRewrittenSuggestion: StateFlow<String?> = _aiRewrittenSuggestion.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val queueTickets: StateFlow<List<QueueTicket>> = repository.queueTickets
    val localEngineStatus: StateFlow<LocalTtsEngineStatus> = repository.localEngineStatus
    val dailyCloudCount: StateFlow<Int> = repository.dailyCloudCount
    val appCheckState: StateFlow<AppCheckDiagnosticState> = AppCheckStatusHolder.state

    val cachedAudioList: StateFlow<List<CachedAudioEntity>> = repository.observeCachedAudio()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticLogs: StateFlow<List<DiagnosticLogEntity>> = repository.observeDiagnosticLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pronunciationRules: StateFlow<List<PronunciationRuleEntity>> = repository.observePronunciationRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val voiceProfiles: StateFlow<List<VoiceProfileEntity>> = repository.observeVoiceProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(buildSnapshotUiState(_inputText.value, _config.value))
    val uiState: StateFlow<TextToSpeechUiState> = _uiState.asStateFlow()

    private val prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "dark_theme") {
            _isDarkTheme.value = prefs.getBoolean("dark_theme", true)
        } else {
            _config.value = loadPersistedConfig()
            syncUiState()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        viewModelScope.launch {
            pronunciationRules.collect {
                syncUiState()
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onCleared()
    }

    private fun syncUiState() {
        _uiState.value = buildSnapshotUiState(
            rawText = _inputText.value,
            cfg = _config.value,
            rules = pronunciationRules.value,
            isRewriting = _isRewritingScriptWithAi.value,
            aiSuggestion = _aiRewrittenSuggestion.value
        )
    }

    // AI Assistant / Chatbot / Argon / Live API / Lyria states
    private val _selectedAssistantMode = MutableStateFlow(AssistantModelMode.ARGON_HIGH_THINKING)
    val selectedAssistantMode: StateFlow<AssistantModelMode> = _selectedAssistantMode.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessageItem>>(
        listOf(
            ChatMessageItem(
                id = "welcome_1",
                role = "model",
                text = "สวัสดีครับ! ผมคือผู้ช่วย AI ด้านภาษาศาสตร์และระบบบุคลิกเสียงของ SiamVoice TTS\n" +
                    "• รองรับการปรับเสียง 2 ระดับ: ระดับที่ 1 ดัดน้ำเสียง/อารมณ์ให้ดูเป็นกันเองขึ้น และ ระดับที่ 2 ดัดแปลงคำพูดใหม่ (เช่น 'อีกสามร้อยเมตร เลี้ยวซ้าย' → 'อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ')\n" +
                    "• ปัจจุบันตั้งค่าเริ่มต้นเป็น Voice Style: Natural · Response Style: Conversational · Speed: 1.00x · Expressiveness: 65%",
                modelBadge = "System"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessageItem>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _argonAnalysisResult = MutableStateFlow<String?>(null)
    val argonAnalysisResult: StateFlow<String?> = _argonAnalysisResult.asStateFlow()

    private val _isArgonAnalyzing = MutableStateFlow(false)
    val isArgonAnalyzing: StateFlow<Boolean> = _isArgonAnalyzing.asStateFlow()

    val liveVoiceState: StateFlow<LiveVoiceState> = aiManager.liveState

    private val _musicGenState = MutableStateFlow<MusicGenState>(MusicGenState.Idle)
    val musicGenState: StateFlow<MusicGenState> = _musicGenState.asStateFlow()

    private fun buildSnapshotUiState(
        rawText: String,
        cfg: SynthesisConfig,
        rules: List<PronunciationRuleEntity> = emptyList(),
        isRewriting: Boolean = false,
        aiSuggestion: String? = null
    ): TextToSpeechUiState {
        val l1 = ThaiTextOptimizer.optimizeThaiText(
            rawText = rawText,
            customRules = rules,
            pronunciationClarity = cfg.pronunciationClarity,
            naturalPauses = cfg.naturalPauses,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
            politenessParticle = cfg.politenessParticle,
            voiceStyle = cfg.style,
            responseStyle = cfg.responseStyle
        )
        val l2 = ThaiTextOptimizer.optimizeThaiText(
            rawText = rawText,
            customRules = rules,
            pronunciationClarity = cfg.pronunciationClarity,
            naturalPauses = cfg.naturalPauses,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = cfg.politenessParticle,
            voiceStyle = cfg.style,
            responseStyle = cfg.responseStyle
        )
        val finalEffective = when (cfg.adaptationLevel) {
            SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION -> l1
            SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH -> l2
            else -> ThaiTextOptimizer.applyPoliteEnding(
                text = rawText,
                ending = cfg.thaiPoliteEnding,
                disableRewritingForNavigation = cfg.disableRewritingForNavigation
            )
        }
        return TextToSpeechUiState(
            inputText = rawText,
            level1VocalTonePreview = l1,
            level2RewrittenPreview = l2,
            effectiveSpokenText = finalEffective,
            voiceStylePreset = cfg.style,
            responseStyle = cfg.responseStyle,
            adaptationLevel = cfg.adaptationLevel,
            politenessParticle = cfg.politenessParticle,
            thaiVoicePersonality = cfg.thaiVoicePersonality,
            thaiPoliteEnding = cfg.thaiPoliteEnding,
            disableRewritingForNavigation = cfg.disableRewritingForNavigation,
            speed = cfg.speechRate,
            pitch = cfg.pitch,
            volume = cfg.volume,
            emotionalIntensity = cfg.emotionalIntensity,
            expressiveness = cfg.expressiveness,
            naturalPauses = cfg.naturalPauses,
            pronunciationClarity = cfg.pronunciationClarity,
            voiceWarmth = cfg.voiceWarmth,
            formality = cfg.formality,
            speechStability = cfg.speechStability,
            customStyleInstruction = cfg.customStyleInstruction,
            isRewritingScriptWithAi = isRewriting,
            aiRewrittenSuggestion = aiSuggestion
        )
    }

    private fun loadPersistedConfig(): SynthesisConfig {
        val styleId = prefs.getString("voice_style", VoiceStylePreset.NATURAL.id) ?: VoiceStylePreset.NATURAL.id
        val respId = prefs.getString("response_style", ResponseStyle.CONVERSATIONAL.id) ?: ResponseStyle.CONVERSATIONAL.id
        val adaptId = prefs.getString("adaptation_level", SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION.id)
            ?: SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION.id
        val particleId = prefs.getString("politeness_particle", PolitenessParticle.KRUB.id)
            ?: PolitenessParticle.KRUB.id
        val personalityId = prefs.getString("thai_voice_personality", ThaiVoicePersonality.FRIENDLY_COMPANION.id)
        val politeEndingId = prefs.getString("thai_polite_ending", ThaiPoliteEnding.AUTO_NATURAL.id)
        val disableNavRewrite = prefs.getBoolean("disable_rewriting_for_navigation", false)
        val modelId = prefs.getString("model_id", GeminiTtsModel.FLASH_3_8.modelId) ?: GeminiTtsModel.FLASH_3_8.modelId
        val voiceId = prefs.getString("voice_id", GeminiVoice.KORE.voiceId) ?: GeminiVoice.KORE.voiceId
        val policyId = prefs.getString("routing_policy", RoutingPolicy.AUTO_HYBRID.id) ?: RoutingPolicy.AUTO_HYBRID.id
        val offlineProfileId = prefs.getString("offline_voice_profile", OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR.id)
            ?: OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR.id
        val tokModeId = prefs.getString("f5_tokenizer_mode", F5TokenizerMode.V2_IPA_PHONEME.id)
            ?: F5TokenizerMode.V2_IPA_PHONEME.id

        return SynthesisConfig(
            model = GeminiTtsModel.entries.firstOrNull { it.modelId == modelId } ?: GeminiTtsModel.FLASH_3_8,
            voice = GeminiVoice.entries.firstOrNull { it.voiceId == voiceId } ?: GeminiVoice.KORE,
            style = VoiceStylePreset.entries.firstOrNull { it.id == styleId } ?: VoiceStylePreset.NATURAL,
            responseStyle = ResponseStyle.entries.firstOrNull { it.id == respId } ?: ResponseStyle.CONVERSATIONAL,
            adaptationLevel = SpeechAdaptationLevel.entries.firstOrNull { it.id == adaptId }
                ?: SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
            politenessParticle = PolitenessParticle.entries.firstOrNull { it.id == particleId }
                ?: PolitenessParticle.KRUB,
            thaiVoicePersonality = ThaiVoicePersonality.fromId(personalityId),
            thaiPoliteEnding = ThaiPoliteEnding.fromId(politeEndingId),
            disableRewritingForNavigation = disableNavRewrite,
            routingPolicy = RoutingPolicy.entries.firstOrNull { it.id == policyId } ?: RoutingPolicy.AUTO_HYBRID,
            speechRate = prefs.getFloat("speech_rate", 1.00f),
            pitch = prefs.getFloat("pitch", 1.00f),
            volume = prefs.getFloat("volume", 1.00f),
            emotionalIntensity = prefs.getInt("emotional_intensity", 55),
            expressiveness = prefs.getInt("expressiveness", 65),
            naturalPauses = prefs.getInt("natural_pauses", 55),
            pronunciationClarity = prefs.getInt("pronunciation_clarity", 75),
            voiceWarmth = prefs.getInt("voice_warmth", 65),
            formality = prefs.getInt("formality", 50),
            speechStability = prefs.getInt("speech_stability", 80),
            customStyleInstruction = prefs.getString("custom_instruction", "").orEmpty(),
            preferredLanguage = prefs.getString("preferred_language", "th-TH") ?: "th-TH",
            dailyCloudQuotaLimit = prefs.getInt("daily_quota_limit", 40),
            offlineVoiceProfile = OfflineVoiceProfile.entries.firstOrNull { it.id == offlineProfileId }
                ?: OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
            f5TokenizerMode = F5TokenizerMode.entries.firstOrNull { it.id == tokModeId }
                ?: F5TokenizerMode.V2_IPA_PHONEME,
            f5NfeSteps = prefs.getInt("f5_nfe_steps", F5SynthesisParams.DEFAULT_NFE_STEPS),
            useArgonReasoning = prefs.getBoolean("use_argon_reasoning", false),
            cloudTimeoutMs = prefs.getLong("cloud_timeout_ms", SynthesisConfig.DEFAULT_CLOUD_TIMEOUT_MS)
        )
    }

    private fun persistConfig(cfg: SynthesisConfig) {
        prefs.edit()
            .putString("voice_style", cfg.style.id)
            .putString("response_style", cfg.responseStyle.id)
            .putString("adaptation_level", cfg.adaptationLevel.id)
            .putString("politeness_particle", cfg.politenessParticle.id)
            .putString("thai_voice_personality", cfg.thaiVoicePersonality.id)
            .putString("thai_polite_ending", cfg.thaiPoliteEnding.id)
            .putBoolean("disable_rewriting_for_navigation", cfg.disableRewritingForNavigation)
            .putString("model_id", cfg.model.modelId)
            .putString("voice_id", cfg.voice.voiceId)
            .putString("routing_policy", cfg.routingPolicy.id)
            .putFloat("speech_rate", cfg.speechRate)
            .putFloat("pitch", cfg.pitch)
            .putFloat("volume", cfg.volume)
            .putInt("emotional_intensity", cfg.emotionalIntensity)
            .putInt("expressiveness", cfg.expressiveness)
            .putInt("natural_pauses", cfg.naturalPauses)
            .putInt("pronunciation_clarity", cfg.pronunciationClarity)
            .putInt("voice_warmth", cfg.voiceWarmth)
            .putInt("formality", cfg.formality)
            .putInt("speech_stability", cfg.speechStability)
            .putString("custom_instruction", cfg.customStyleInstruction)
            .putString("preferred_language", cfg.preferredLanguage)
            .putInt("daily_quota_limit", cfg.dailyCloudQuotaLimit)
            .putString("offline_voice_profile", cfg.offlineVoiceProfile.id)
            .putString("f5_tokenizer_mode", cfg.f5TokenizerMode.id)
            .putInt("f5_nfe_steps", cfg.f5NfeSteps)
            .putBoolean("use_argon_reasoning", cfg.useArgonReasoning)
            .putLong("cloud_timeout_ms", cfg.cloudTimeoutMs)
            .apply()
    }

    private fun updateAndPersistConfig(transform: (SynthesisConfig) -> SynthesisConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        persistConfig(updated)
        syncUiState()
    }

    fun toggleDarkTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        prefs.edit().putBoolean("dark_theme", next).apply()
    }

    fun updateInputText(text: String) {
        _inputText.value = text
        syncUiState()
    }

    fun updatePreviewSampleText(text: String) {
        _previewSampleText.value = text
    }

    fun selectModel(model: GeminiTtsModel) {
        updateAndPersistConfig { it.copy(model = model) }
    }

    fun selectVoice(voice: GeminiVoice) {
        updateAndPersistConfig { it.copy(voice = voice) }
    }

    /**
     * Applies one of the 9 Voice Style Presets along with its calibrated default speaking parameters.
     */
    fun selectStyle(style: VoiceStylePreset) {
        updateAndPersistConfig { current ->
            if (style == VoiceStylePreset.CUSTOM) {
                current.copy(style = style)
            } else {
                current.copy(
                    style = style,
                    speechRate = style.defaultSpeed,
                    pitch = style.defaultPitch,
                    emotionalIntensity = style.defaultEmotionalIntensity,
                    expressiveness = style.defaultExpressiveness,
                    naturalPauses = style.defaultNaturalPauses,
                    pronunciationClarity = style.defaultClarity,
                    voiceWarmth = style.defaultWarmth,
                    formality = style.defaultFormality,
                    speechStability = style.defaultStability
                )
            }
        }
    }

    fun selectResponseStyle(responseStyle: ResponseStyle) {
        updateAndPersistConfig { it.copy(responseStyle = responseStyle) }
    }

    /**
     * Switches between:
     * - Level 1: ดัดน้ำเสียง/อารมณ์ให้ดูเป็นกันเองขึ้น (คงคำพูดเดิม)
     * - Level 2: สั่งให้ดัดแปลงคำพูดใหม่ (เช่น "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ")
     */
    fun selectAdaptationLevel(level: SpeechAdaptationLevel) {
        updateAndPersistConfig { it.copy(adaptationLevel = level) }
    }

    fun selectPolitenessParticle(particle: PolitenessParticle) {
        updateAndPersistConfig { it.copy(politenessParticle = particle) }
    }

    fun selectThaiVoicePersonality(personality: ThaiVoicePersonality) {
        updateAndPersistConfig { current ->
            if (personality == ThaiVoicePersonality.CUSTOM) {
                current.copy(thaiVoicePersonality = personality)
            } else {
                current.copy(
                    thaiVoicePersonality = personality,
                    voice = personality.recommendedVoice,
                    speechRate = personality.defaultSpeed,
                    expressiveness = personality.defaultExpressiveness,
                    customStyleInstruction = personality.thaiVoiceStyleInstruction
                )
            }
        }
    }

    fun selectThaiPoliteEnding(ending: ThaiPoliteEnding) {
        updateAndPersistConfig { current ->
            val matchingParticle = when (ending) {
                ThaiPoliteEnding.AUTO_NATURAL -> PolitenessParticle.AUTO_NATURAL
                ThaiPoliteEnding.KRUB -> PolitenessParticle.KRUB
                ThaiPoliteEnding.NA_KRUB -> PolitenessParticle.NA_KRUB
                ThaiPoliteEnding.KRUB_PHOM -> PolitenessParticle.KRUB_PHOM
                ThaiPoliteEnding.NO_ENDING -> PolitenessParticle.NO_ENDING
            }
            current.copy(
                thaiPoliteEnding = ending,
                politenessParticle = matchingParticle
            )
        }
    }

    fun setDisableRewritingForNavigation(disabled: Boolean) {
        updateAndPersistConfig { it.copy(disableRewritingForNavigation = disabled) }
    }

    /**
     * Replaces the current input text with the Level 2 conversational rewritten phrasing.
     */
    fun applyLevel2RewriteToInput() {
        val rewritten = ThaiTextOptimizer.optimizeThaiText(
            rawText = _inputText.value,
            customRules = pronunciationRules.value,
            pronunciationClarity = _config.value.pronunciationClarity,
            naturalPauses = _config.value.naturalPauses,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = _config.value.politenessParticle,
            voiceStyle = _config.value.style,
            responseStyle = _config.value.responseStyle
        )
        if (rewritten.isNotBlank()) {
            _inputText.value = rewritten
            syncUiState()
        }
    }

    /**
     * Invokes Gemini 4 Argon (ThinkingLevel.HIGH) to dynamically rewrite the input text into natural,
     * friendly Thai matching the active VoiceStylePreset, ResponseStyle, and PolitenessParticle.
     */
    fun rewriteScriptWithArgonLevel2(applyImmediatelyToInput: Boolean = false) {
        val raw = _inputText.value.trim()
        if (raw.isEmpty() || _isRewritingScriptWithAi.value) return
        _isRewritingScriptWithAi.value = true
        _aiRewrittenSuggestion.value = null
        syncUiState()

        viewModelScope.launch {
            val res = aiManager.rewriteScriptWithArgon(raw, _config.value)
            _isRewritingScriptWithAi.value = false
            res.fold(
                onSuccess = { rewritten ->
                    _aiRewrittenSuggestion.value = rewritten
                    if (applyImmediatelyToInput) {
                        _inputText.value = rewritten
                    }
                    syncUiState()
                },
                onFailure = {
                    // Fallback to deterministic Level 2 conversational phrasing adapter if offline or quota reached
                    val fallbackRewritten = ThaiTextOptimizer.optimizeThaiText(
                        rawText = raw,
                        customRules = pronunciationRules.value,
                        pronunciationClarity = _config.value.pronunciationClarity,
                        naturalPauses = _config.value.naturalPauses,
                        adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
                        politenessParticle = _config.value.politenessParticle,
                        voiceStyle = _config.value.style,
                        responseStyle = _config.value.responseStyle
                    )
                    _aiRewrittenSuggestion.value = fallbackRewritten
                    if (applyImmediatelyToInput) {
                        _inputText.value = fallbackRewritten
                    }
                    syncUiState()
                }
            )
        }
    }

    fun applyAiRewrittenSuggestion() {
        val suggestion = _aiRewrittenSuggestion.value
        if (!suggestion.isNullOrBlank()) {
            _inputText.value = suggestion
            _aiRewrittenSuggestion.value = null
            syncUiState()
        }
    }

    fun dismissAiRewrittenSuggestion() {
        _aiRewrittenSuggestion.value = null
        syncUiState()
    }

    fun selectRoutingPolicy(policy: RoutingPolicy) {
        updateAndPersistConfig { it.copy(routingPolicy = policy) }
    }

    fun updateSpeechRate(rate: Float) {
        updateAndPersistConfig { it.copy(speechRate = rate.coerceIn(0.5f, 2.0f)) }
    }

    fun updatePitch(pitch: Float) {
        updateAndPersistConfig { it.copy(pitch = pitch.coerceIn(0.5f, 2.0f)) }
    }

    fun updateVolume(volume: Float) {
        updateAndPersistConfig { it.copy(volume = volume.coerceIn(0.1f, 1.5f)) }
    }

    fun updateEmotionalIntensity(value: Int) {
        updateAndPersistConfig { it.copy(emotionalIntensity = value.coerceIn(0, 100)) }
    }

    fun updateExpressiveness(value: Int) {
        updateAndPersistConfig { it.copy(expressiveness = value.coerceIn(0, 100)) }
    }

    fun updateNaturalPauses(value: Int) {
        updateAndPersistConfig { it.copy(naturalPauses = value.coerceIn(0, 100)) }
    }

    fun updatePronunciationClarity(value: Int) {
        updateAndPersistConfig { it.copy(pronunciationClarity = value.coerceIn(0, 100)) }
    }

    fun updateVoiceWarmth(value: Int) {
        updateAndPersistConfig { it.copy(voiceWarmth = value.coerceIn(0, 100)) }
    }

    fun updateFormality(value: Int) {
        updateAndPersistConfig { it.copy(formality = value.coerceIn(0, 100)) }
    }

    fun updateSpeechStability(value: Int) {
        updateAndPersistConfig { it.copy(speechStability = value.coerceIn(0, 100)) }
    }

    fun updateCustomStyleInstruction(instruction: String) {
        updateAndPersistConfig { it.copy(customStyleInstruction = instruction) }
    }

    fun updatePreferredLanguage(lang: String) {
        updateAndPersistConfig { it.copy(preferredLanguage = lang) }
    }

    fun updateDailyQuotaThreshold(limit: Int) {
        updateAndPersistConfig { it.copy(dailyCloudQuotaLimit = limit.coerceIn(1, 500)) }
    }

    fun setEnableArgonOnNav(enabled: Boolean) {
        _enableArgonOnNav.value = enabled
    }

    fun computeLiveNormalizedPreview(rawText: String, rules: List<PronunciationRuleEntity>): String {
        val cfg = _config.value
        return ThaiTextOptimizer.optimizeThaiText(
            rawText = rawText,
            customRules = rules,
            pronunciationClarity = cfg.pronunciationClarity,
            naturalPauses = cfg.naturalPauses,
            adaptationLevel = cfg.adaptationLevel,
            politenessParticle = cfg.politenessParticle,
            voiceStyle = cfg.style,
            responseStyle = cfg.responseStyle
        )
    }

    /**
     * Live Preview: Synthesizes (or retrieves from local WAV cache) a specific VoiceStylePreset
     * using the shared preview Thai text so users can compare styles with minimal API calls.
     */
    fun previewVoiceStylePreset(preset: VoiceStylePreset) {
        selectStyle(preset)
        val sampleText = _previewSampleText.value.ifBlank { _inputText.value }
        synthesizeSpecificText(
            text = sampleText,
            isNavPhrase = preset == VoiceStylePreset.NAVIGATION,
            bypassCache = false,
            autoPlay = true,
            updateMainInput = false
        )
    }

    fun saveCurrentVoiceProfile(profileName: String) {
        if (profileName.isBlank()) return
        viewModelScope.launch {
            repository.saveVoiceProfile(profileName, _config.value)
        }
    }

    fun applyVoiceProfile(entity: VoiceProfileEntity) {
        updateAndPersistConfig { current ->
            current.copy(
                routingPolicy = RoutingPolicy.entries.firstOrNull { it.id == entity.routingPolicyId } ?: current.routingPolicy,
                model = GeminiTtsModel.entries.firstOrNull { it.modelId == entity.modelId } ?: current.model,
                voice = GeminiVoice.entries.firstOrNull { it.voiceId == entity.voiceId } ?: current.voice,
                responseStyle = ResponseStyle.entries.firstOrNull { it.id == entity.responseStyleId } ?: current.responseStyle,
                style = VoiceStylePreset.entries.firstOrNull { it.id == entity.voiceStyleId } ?: current.style,
                speechRate = entity.speechRate,
                pitch = entity.pitch,
                volume = entity.volume,
                emotionalIntensity = entity.emotionalIntensity,
                expressiveness = entity.expressiveness,
                naturalPauses = entity.naturalPauses,
                pronunciationClarity = entity.pronunciationClarity,
                voiceWarmth = entity.voiceWarmth,
                formality = entity.formality,
                speechStability = entity.speechStability,
                customStyleInstruction = entity.customStyleInstruction,
                preferredLanguage = entity.preferredLanguage
            )
        }
    }

    fun deleteVoiceProfile(id: Long) {
        viewModelScope.launch {
            repository.deleteVoiceProfile(id)
        }
    }

    fun synthesizeCurrentText(isNavPhrase: Boolean = false, bypassCache: Boolean = false) {
        val text = _inputText.value
        synthesizeSpecificText(
            text = text,
            isNavPhrase = isNavPhrase,
            bypassCache = bypassCache,
            autoPlay = true,
            updateMainInput = true
        )
    }

    fun synthesizeSpecificText(
        text: String,
        isNavPhrase: Boolean = false,
        bypassCache: Boolean = false,
        autoPlay: Boolean = true,
        updateMainInput: Boolean = true
    ) {
        viewModelScope.launch {
            if (updateMainInput) {
                _inputText.value = text
            }
            val currentCfg = _config.value
            _synthesisState.value = SynthesisUiState.Synthesizing(
                progressLabelTh = "กำลังสังเคราะห์ [${currentCfg.style.titleEn} · ${currentCfg.adaptationLevel.shortBadgeTh}]..."
            )

            // Only call Gemini 4 Argon reasoning on navigation phrases if explicitly enabled by the user
            if (isNavPhrase && _enableArgonOnNav.value) {
                aiManager.analyzeThaiPronunciationWithArgon(text, currentCfg)
                    .onSuccess { _argonAnalysisResult.value = it }
            }

            val result = repository.synthesizeSpeech(
                rawText = text,
                config = currentCfg,
                isNavigationPhrase = isNavPhrase,
                bypassCache = bypassCache
            )
            result.fold(
                onSuccess = { outcome ->
                    _lastOutcome.value = outcome
                    val banner = if (outcome.fallbackTriggered) {
                        "สลับใช้ ${outcome.providerUsed} อัตโนมัติ (${outcome.latencyMs} ms): ${outcome.fallbackReasonTh.orEmpty()}"
                    } else {
                        "สำเร็จผ่าน ${outcome.providerUsed} · ${currentCfg.style.titleEn} · ${currentCfg.adaptationLevel.shortBadgeTh} (${outcome.latencyMs} ms)"
                    }
                    _synthesisState.value = SynthesisUiState.Success(outcome, banner)
                    if (autoPlay) {
                        playerManager.playWavBytes(outcome.wavBytes, outcome.wavFilePath)
                    }
                },
                onFailure = { err ->
                    _synthesisState.value = SynthesisUiState.Error(
                        err.localizedMessage ?: "เกิดข้อผิดพลาดในการสังเคราะห์เสียง"
                    )
                }
            )
        }
    }

    fun preCacheNavigationPack() {
        if (_isPreCachingNav.value) return
        viewModelScope.launch {
            _isPreCachingNav.value = true
            try {
                val navConfig = _config.value.copy(
                    style = VoiceStylePreset.NAVIGATION,
                    responseStyle = ResponseStyle.NAVIGATION,
                    routingPolicy = RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST
                )
                for (preset in DEFAULT_THAI_NAVIGATION_PHRASES) {
                    repository.synthesizeSpeech(
                        rawText = preset.thaiText,
                        config = navConfig,
                        isNavigationPhrase = true,
                        bypassCache = false
                    )
                }
            } finally {
                _isPreCachingNav.value = false
            }
        }
    }

    fun playCurrentOrCachedFile(filePath: String) {
        playerManager.playWavFile(filePath)
    }

    fun playWavBytes(bytes: ByteArray, label: String = "audio.wav") {
        playerManager.playWavBytes(bytes, label)
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    fun exportWavFile(filePath: String, titleSnippet: String) {
        viewModelScope.launch {
            val res = playerManager.exportWavToDevice(filePath, titleSnippet)
            res.fold(
                onSuccess = { savedPath ->
                    _exportBanner.value = "บันทึกไฟล์ WAV สำเร็จที่: $savedPath"
                },
                onFailure = { err ->
                    _exportBanner.value = "บันทึกไฟล์ WAV ไม่สำเร็จ: ${err.localizedMessage}"
                }
            )
        }
    }

    fun dismissExportBanner() {
        _exportBanner.value = null
    }

    fun addPronunciationRule(pattern: String, replacement: String, note: String) {
        viewModelScope.launch {
            repository.addPronunciationRule(pattern, replacement, note)
        }
    }

    fun deletePronunciationRule(id: Long) {
        viewModelScope.launch {
            repository.deletePronunciationRule(id)
        }
    }

    fun clearAllCache() {
        viewModelScope.launch {
            playerManager.stop()
            repository.clearAudioCache()
            _lastOutcome.value = null
            _synthesisState.value = SynthesisUiState.Idle
        }
    }

    fun clearDiagnosticLogs() {
        viewModelScope.launch {
            repository.clearDiagnostics()
        }
    }

    fun refreshLocalTtsEngine() {
        repository.initAndroidSystemTts()
    }

    // AI Studio Features
    fun selectAssistantMode(mode: AssistantModelMode) {
        _selectedAssistantMode.value = mode
    }

    fun sendAssistantMessage(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _isChatLoading.value) return

        val mode = _selectedAssistantMode.value
        val activeRespStyle = _config.value.responseStyle
        val currentList = _chatMessages.value
        val userMsg = ChatMessageItem(
            id = UUID.randomUUID().toString(),
            role = "user",
            text = trimmed,
            modelBadge = "คุณ (${activeRespStyle.titleEn})"
        )
        _chatMessages.value = currentList + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            val response = aiManager.sendChatMessage(
                userPrompt = trimmed,
                mode = mode,
                responseStyle = activeRespStyle,
                existingHistory = currentList
            )
            _isChatLoading.value = false
            response.fold(
                onSuccess = { reply ->
                    _chatMessages.value = _chatMessages.value + ChatMessageItem(
                        id = UUID.randomUUID().toString(),
                        role = "model",
                        text = reply,
                        modelBadge = "${mode.modelId} · ${activeRespStyle.titleEn}"
                    )
                },
                onFailure = { err ->
                    val errMsg = err.localizedMessage ?: "การเรียกใช้โมเดลล้มเหลว"
                    _chatMessages.value = _chatMessages.value + ChatMessageItem(
                        id = UUID.randomUUID().toString(),
                        role = "error",
                        text = "ข้อผิดพลาด (${mode.modelId}): $errMsg",
                        modelBadge = "Error"
                    )
                    repository.logDiagnostic(
                        level = "ERROR",
                        category = "AI_LOGIC",
                        provider = mode.modelId,
                        message = errMsg
                    )
                }
            )
        }
    }

    fun runArgonPhoneticAnalysis() {
        val text = _inputText.value.trim()
        if (text.isEmpty() || _isArgonAnalyzing.value) return
        _isArgonAnalyzing.value = true
        _argonAnalysisResult.value = null

        viewModelScope.launch {
            val res = aiManager.analyzeThaiPronunciationWithArgon(text, _config.value)
            _isArgonAnalyzing.value = false
            res.fold(
                onSuccess = { analysis ->
                    _argonAnalysisResult.value = analysis
                },
                onFailure = { err ->
                    _argonAnalysisResult.value = "ไม่สามารถวิเคราะห์ด้วย Gemini 4 Argon: ${err.localizedMessage}"
                }
            )
        }
    }

    fun dismissArgonAnalysis() {
        _argonAnalysisResult.value = null
    }

    fun toggleLiveVoiceSession(hasMicPermission: Boolean) {
        viewModelScope.launch {
            if (liveVoiceState.value is LiveVoiceState.Connected) {
                aiManager.disconnectLiveVoiceSession()
            } else {
                aiManager.connectLiveVoiceSession(enableMicAudio = hasMicPermission)
            }
        }
    }

    fun sendLivePrompt(prompt: String) {
        viewModelScope.launch {
            aiManager.sendRealtimePromptToLiveSession(prompt)
        }
    }

    fun generateMusicTrack(prompt: String, option: LyriaModelOption) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        viewModelScope.launch {
            _musicGenState.value = MusicGenState.Loading(option.modelId)
            val result = aiManager.generateLyriaMusic(cleanPrompt, option)
            _musicGenState.value = result
            if (result is MusicGenState.Success) {
                playerManager.playWavBytes(result.wavBytes, "lyria_${option.modelId}.wav")
            }
        }
    }
}
