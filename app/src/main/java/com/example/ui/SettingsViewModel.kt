package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VoiceProfileEntity
import com.example.tts.F5SynthesisParams
import com.example.tts.F5TokenizerMode
import com.example.tts.GeminiTtsModel
import com.example.tts.GeminiVoice
import com.example.tts.GeminiVoiceProfile
import com.example.tts.HybridTtsRepository
import com.example.tts.LocalTtsEngineStatus
import com.example.tts.OfflineVoiceProfile
import com.example.tts.PolitenessParticle
import com.example.tts.ProviderCapabilityProfile
import com.example.tts.RoutingPolicy
import com.example.tts.SynthesisConfig
import com.example.tts.VoiceProfileCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * UI State for [SettingsViewModel] managing the selection and hybrid fallback behavior
 * between **Gemini Cloud TTS** (`gemini-3.8-flash-tts` / `gemini-3.8-flash-lite-tts`),
 * curated **Gemini Voice Profiles** (Female, Male, Neutral), and the
 * **F5-TTS-THAI / ONNX Neural TTS & Android System TTS** fallback engines.
 */
data class SettingsUiState(
    val useLocalFallbackOnly: Boolean = false,
    val autoFallbackEnabled: Boolean = true,
    val prioritizeLocalForShortPhrases: Boolean = false,
    val routingPolicy: RoutingPolicy = RoutingPolicy.AUTO_HYBRID,
    val selectedGeminiModel: GeminiTtsModel = GeminiTtsModel.FLASH_3_8,
    val selectedGeminiVoice: GeminiVoice = GeminiVoice.KORE,
    val selectedVoiceCategory: VoiceProfileCategory = VoiceProfileCategory.ALL,
    val selectedVoiceProfile: GeminiVoiceProfile = GeminiVoiceProfile.FEMALE_KORE_ASSISTANT,
    val availableVoiceProfiles: List<GeminiVoiceProfile> = GeminiVoiceProfile.entries,
    val availableGeminiVoices: List<GeminiVoice> = GeminiVoice.entries,
    val selectedOfflineVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
    val availableOfflineVoiceProfiles: List<OfflineVoiceProfile> = OfflineVoiceProfile.entries,
    val selectedF5TokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
    val f5NfeSteps: Int = F5SynthesisParams.DEFAULT_NFE_STEPS,
    val cloudTimeoutMs: Long = SynthesisConfig.DEFAULT_CLOUD_TIMEOUT_MS,
    val useArgonReasoning: Boolean = false,
    val fallbackPriorityChain: String = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
    val politenessParticle: PolitenessParticle = PolitenessParticle.KHA,
    val savedUserProfiles: List<VoiceProfileEntity> = emptyList(),
    val dailyCloudQuotaLimit: Int = 40,
    val dailyCloudUsageCount: Int = 0,
    val localEngineStatus: LocalTtsEngineStatus = LocalTtsEngineStatus(),
    val capabilityProfile: ProviderCapabilityProfile =
        ProviderCapabilityProfile.forRoutingPolicy(RoutingPolicy.AUTO_HYBRID),
    val disableRewritingForNavigation: Boolean = false,
    val isF5Downloaded: Boolean = false,
    val isDownloadingF5: Boolean = false,
    val installedF5Bytes: Long = 0L,
    val freeDiskBytes: Long = 0L,
    val isDarkTheme: Boolean = true,
    val statusMessageTh: String? = null
) {
    val activeEngineSummaryTh: String
        get() = when (routingPolicy) {
            RoutingPolicy.OFFLINE_ONLY ->
                "โหมดออฟไลน์ 100%: ใช้ ${selectedOfflineVoiceProfile.titleTh} (F5-TTS-THAI / ONNX VITS) ในเครื่องเท่านั้น"
            RoutingPolicy.CLOUD_ONLY ->
                "โหมด Gemini Cloud เท่านั้น: ใช้ ${selectedGeminiModel.displayName} (${selectedVoiceProfile.titleTh}) โดยไม่สลับลงเครื่อง"
            RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST ->
                "โหมดไฮบริดวลีสั้น: วลีสั้น ≤ 35 ตัวอักษรใช้ ${selectedOfflineVoiceProfile.titleTh}/Cache ทันที · ข้อความยาวใช้ ${selectedGeminiModel.displayName}"
            RoutingPolicy.AUTO_HYBRID ->
                "โหมดไฮบริดอัตโนมัติ (Fallback: $fallbackPriorityChain · Cloud Timeout ${cloudTimeoutMs / 1000}s): ใช้ ${selectedGeminiModel.displayName} เป็นหลัก"
        }
}

/**
 * Dedicated ViewModel that allows users to toggle between **Gemini Cloud TTS** and the
 * **F5-TTS-THAI / Local ONNX Fallback Engine**, filter and select **Gemini Voice Profiles**
 * across Female (`FEMALE`), Male (`MALE`), and Neutral (`NEUTRAL`) personas, select
 * **Offline Voice Profiles** (`F5-TTS-THAI` ref 1/2 or `Thai VITS`), configure F5 NFE steps
 * and tokenizer modes, adjust daily cloud quota thresholds, and inspect/reload local TTS runtimes.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val repository: HybridTtsRepository = HybridTtsRepository.getInstance(application)

    private val _routingPolicy = MutableStateFlow(loadPersistedRoutingPolicy())
    private val _selectedModel = MutableStateFlow(loadPersistedGeminiModel())
    private val _selectedVoice = MutableStateFlow(loadPersistedGeminiVoice())
    private val _selectedVoiceCategory = MutableStateFlow(loadPersistedVoiceCategory())
    private val _selectedVoiceProfile = MutableStateFlow(loadPersistedVoiceProfile(_selectedVoice.value))
    private val _selectedOfflineVoiceProfile = MutableStateFlow(loadPersistedOfflineVoiceProfile())
    private val _selectedF5TokenizerMode = MutableStateFlow(loadPersistedF5TokenizerMode())
    private val _f5NfeSteps = MutableStateFlow(prefs.getInt(KEY_F5_NFE_STEPS, F5SynthesisParams.DEFAULT_NFE_STEPS))
    private val _cloudTimeoutMs = MutableStateFlow(prefs.getLong(KEY_CLOUD_TIMEOUT_MS, SynthesisConfig.DEFAULT_CLOUD_TIMEOUT_MS))
    private val _useArgonReasoning = MutableStateFlow(prefs.getBoolean(KEY_USE_ARGON_REASONING, false))
    private val _politenessParticle = MutableStateFlow(loadPersistedPolitenessParticle(_selectedVoice.value))
    private val _savedUserProfiles = MutableStateFlow<List<VoiceProfileEntity>>(emptyList())
    private val _dailyQuotaLimit = MutableStateFlow(prefs.getInt(KEY_DAILY_QUOTA_LIMIT, 40))
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK_THEME, true))
    private val storageManager by lazy {
        com.example.tts.OfflineModelStorageManager(application)
    }
    private val _statusMessageTh = MutableStateFlow<String?>(null)
    private val _disableRewritingForNavigation = MutableStateFlow(prefs.getBoolean("disable_rewriting_for_navigation", false))
    private val _isDownloadingF5 = MutableStateFlow(false)
    private val _storageSummary = MutableStateFlow(storageManager.inspectStorageSummary())

    private val _uiState = MutableStateFlow(buildCurrentSnapshot())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_ROUTING_POLICY -> _routingPolicy.value = loadPersistedRoutingPolicy()
            KEY_MODEL_ID -> _selectedModel.value = loadPersistedGeminiModel()
            KEY_VOICE_ID -> {
                val voice = loadPersistedGeminiVoice()
                _selectedVoice.value = voice
                _selectedVoiceProfile.value = loadPersistedVoiceProfile(voice)
            }
            KEY_VOICE_PROFILE_ID -> {
                _selectedVoiceProfile.value = loadPersistedVoiceProfile(_selectedVoice.value)
            }
            KEY_VOICE_CATEGORY -> {
                _selectedVoiceCategory.value = loadPersistedVoiceCategory()
            }
            KEY_OFFLINE_VOICE_PROFILE -> {
                _selectedOfflineVoiceProfile.value = loadPersistedOfflineVoiceProfile()
            }
            KEY_F5_TOKENIZER_MODE -> {
                _selectedF5TokenizerMode.value = loadPersistedF5TokenizerMode()
            }
            KEY_F5_NFE_STEPS -> {
                _f5NfeSteps.value = prefs.getInt(KEY_F5_NFE_STEPS, F5SynthesisParams.DEFAULT_NFE_STEPS)
            }
            KEY_CLOUD_TIMEOUT_MS -> {
                _cloudTimeoutMs.value = prefs.getLong(KEY_CLOUD_TIMEOUT_MS, SynthesisConfig.DEFAULT_CLOUD_TIMEOUT_MS)
            }
            KEY_USE_ARGON_REASONING -> {
                _useArgonReasoning.value = prefs.getBoolean(KEY_USE_ARGON_REASONING, false)
            }
            KEY_POLITENESS_PARTICLE -> {
                _politenessParticle.value = loadPersistedPolitenessParticle(_selectedVoice.value)
            }
            KEY_DAILY_QUOTA_LIMIT -> _dailyQuotaLimit.value = prefs.getInt(KEY_DAILY_QUOTA_LIMIT, 40)
            KEY_DARK_THEME -> _isDarkTheme.value = prefs.getBoolean(KEY_DARK_THEME, true)
        }
        _uiState.value = buildCurrentSnapshot()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        viewModelScope.launch {
            repository.observeVoiceProfiles().collect { profiles ->
                _savedUserProfiles.value = profiles
                _uiState.value = buildCurrentSnapshot()
            }
        }
        viewModelScope.launch {
            repository.dailyCloudCount.collect {
                _uiState.value = buildCurrentSnapshot()
            }
        }
        viewModelScope.launch {
            repository.localEngineStatus.collect {
                _uiState.value = buildCurrentSnapshot()
            }
        }
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onCleared()
    }

    /**
     * Primary toggle between **Gemini Cloud TTS** (`useLocalFallbackOnly = false`) and
     * **Local Android / F5-TTS-THAI / ONNX Fallback Engine** (`useLocalFallbackOnly = true`).
     */
    fun toggleEngineMode(useLocalFallbackOnly: Boolean) {
        val nextPolicy = if (useLocalFallbackOnly) {
            RoutingPolicy.OFFLINE_ONLY
        } else {
            RoutingPolicy.AUTO_HYBRID
        }
        applyRoutingPolicy(
            policy = nextPolicy,
            feedbackTh = if (useLocalFallbackOnly) {
                "สลับเป็นโหมด Local F5-TTS-THAI / ONNX Fallback Engine (ออฟไลน์ 100%)"
            } else {
                "สลับเป็นโหมด Gemini Cloud TTS พร้อมระบบสำรอง (${SynthesisConfig.FALLBACK_PRIORITY_CHAIN})"
            }
        )
    }

    /**
     * Toggles whether Gemini Cloud TTS automatically falls back to the local F5-TTS-THAI/ONNX
     * engine on quota exhaustion or network loss (`AUTO_HYBRID` vs `CLOUD_ONLY`).
     */
    fun setAutoFallbackEnabled(enabled: Boolean) {
        val current = _routingPolicy.value
        val nextPolicy = if (enabled) {
            if (current == RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST) {
                RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST
            } else {
                RoutingPolicy.AUTO_HYBRID
            }
        } else {
            RoutingPolicy.CLOUD_ONLY
        }
        applyRoutingPolicy(
            policy = nextPolicy,
            feedbackTh = if (enabled) {
                "เปิดระบบสลับสำรองอัตโนมัติ (${SynthesisConfig.FALLBACK_PRIORITY_CHAIN})"
            } else {
                "ตั้งค่าเป็น Cloud Only (ใช้เฉพาะ Gemini Cloud TTS โดยไม่สลับลงเครื่อง)"
            }
        )
    }

    /**
     * Toggles whether short phrases (≤ 35 characters, e.g. driving directions) prioritize
     * local synthesis and cache before calling Gemini Cloud TTS.
     */
    fun setPrioritizeLocalForShortPhrases(enabled: Boolean) {
        val nextPolicy = if (enabled) {
            RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST
        } else {
            RoutingPolicy.AUTO_HYBRID
        }
        applyRoutingPolicy(
            policy = nextPolicy,
            feedbackTh = if (enabled) {
                "เปิดโหมดเน้นความเร็ววลีสั้น (Short-Phrase Local First)"
            } else {
                "เปลี่ยนกลับเป็นโหมดไฮบริดอัตโนมัติมาตรฐาน (Auto Hybrid)"
            }
        )
    }

    /**
     * Directly selects one of the 4 [RoutingPolicy] modes.
     */
    fun selectRoutingPolicy(policy: RoutingPolicy) {
        applyRoutingPolicy(
            policy = policy,
            feedbackTh = "เปลี่ยนนโยบายการเลือกเอนจินเป็น: ${policy.titleTh}"
        )
    }

    /**
     * Selects the active Gemini Cloud TTS model (`gemini-3.8-flash-tts` or `gemini-3.8-flash-lite-tts`).
     */
    fun selectGeminiModel(model: GeminiTtsModel) {
        _selectedModel.value = model
        prefs.edit().putString(KEY_MODEL_ID, model.modelId).apply()
        _statusMessageTh.value = "เลือกโมเดล ${model.displayName} เรียบร้อยแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Selects the offline Thai neural voice profile (`F5_THAI_FEMALE_CLEAR`, `F5_THAI_STUDIO_NARRATOR`,
     * `THAI_VITS_FAST_NAVIGATION`, or `CUSTOM_REFERENCE_VOICE`).
     */
    fun selectOfflineVoiceProfile(profile: OfflineVoiceProfile) {
        _selectedOfflineVoiceProfile.value = profile
        prefs.edit().putString(KEY_OFFLINE_VOICE_PROFILE, profile.id).apply()
        _statusMessageTh.value = "เลือกโปรไฟล์เสียงออฟไลน์: ${profile.titleTh} เรียบร้อยแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Selects the F5-TTS-THAI tokenizer mode (`V2_IPA_PHONEME` vs `V1_CHAR_UTF8`).
     */
    fun selectF5TokenizerMode(mode: F5TokenizerMode) {
        _selectedF5TokenizerMode.value = mode
        prefs.edit().putString(KEY_F5_TOKENIZER_MODE, mode.id).apply()
        _statusMessageTh.value = "เลือกโหมดตัวตัดคำ F5-TTS-THAI: ${mode.titleTh}"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Updates the F5-TTS-THAI flow-matching inference step count (`nfeSteps`, typically 8, 16, 20, or 32).
     */
    fun updateF5NfeSteps(steps: Int) {
        val coerced = steps.coerceIn(4, 64)
        _f5NfeSteps.value = coerced
        prefs.edit().putInt(KEY_F5_NFE_STEPS, coerced).apply()
        _statusMessageTh.value = "ตั้งค่าจำนวนรอบการสังเคราะห์ F5-TTS-THAI (NFE Steps) เป็น $coerced รอบ"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Filters the displayed Gemini Cloud TTS voice profiles by gender/persona category.
     */
    fun selectVoiceCategory(category: VoiceProfileCategory) {
        _selectedVoiceCategory.value = category
        prefs.edit().putString(KEY_VOICE_CATEGORY, category.id).apply()
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Selects a curated [GeminiVoiceProfile] (Female, Male, or Neutral) for the Gemini Cloud TTS engine.
     */
    fun selectVoiceProfile(profile: GeminiVoiceProfile) {
        _selectedVoiceProfile.value = profile
        _selectedVoice.value = profile.voice
        _politenessParticle.value = profile.recommendedParticle
        prefs.edit()
            .putString(KEY_VOICE_PROFILE_ID, profile.id)
            .putString(KEY_VOICE_ID, profile.voice.voiceId)
            .putString(KEY_POLITENESS_PARTICLE, profile.recommendedParticle.id)
            .putString(KEY_VOICE_STYLE, profile.recommendedStyle.id)
            .putFloat(KEY_PITCH, profile.defaultPitch)
            .putInt(KEY_VOICE_WARMTH, profile.defaultWarmth)
            .apply()
        _statusMessageTh.value =
            "เลือกโปรไฟล์เสียง ${profile.titleTh} (${profile.recommendedParticle.labelTh}) เรียบร้อยแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Selects the default Gemini voice directly and syncs its matching [GeminiVoiceProfile]
     * and recommended Thai politeness particle.
     */
    fun selectGeminiVoice(voice: GeminiVoice) {
        val matchingProfile = GeminiVoiceProfile.forVoice(voice)
        _selectedVoice.value = voice
        _selectedVoiceProfile.value = matchingProfile
        _politenessParticle.value = voice.recommendedParticle
        prefs.edit()
            .putString(KEY_VOICE_ID, voice.voiceId)
            .putString(KEY_VOICE_PROFILE_ID, matchingProfile.id)
            .putString(KEY_POLITENESS_PARTICLE, voice.recommendedParticle.id)
            .apply()
        _statusMessageTh.value =
            "เลือกเสียงพูด ${voice.thaiTitle} (${voice.genderToneTh}) เรียบร้อยแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Selects the Thai politeness particle (`ครับ`, `ค่ะ`, or `กันเอง/ไม่เติมคำลงท้าย`) for Gemini Cloud speech.
     */
    fun selectPolitenessParticle(particle: PolitenessParticle) {
        _politenessParticle.value = particle
        prefs.edit().putString(KEY_POLITENESS_PARTICLE, particle.id).apply()
        _statusMessageTh.value = "ตั้งค่าคำลงท้ายเป็น: ${particle.labelTh}"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Applies a user-saved custom [VoiceProfileEntity] from Room database to the active Gemini Cloud TTS configuration.
     */
    fun applySavedUserProfile(entity: VoiceProfileEntity) {
        val model = GeminiTtsModel.entries.firstOrNull { it.modelId == entity.modelId }
            ?: GeminiTtsModel.FLASH_3_8
        val voice = GeminiVoice.entries.firstOrNull { it.voiceId == entity.voiceId }
            ?: GeminiVoice.KORE
        val policy = RoutingPolicy.entries.firstOrNull { it.id == entity.routingPolicyId }
            ?: RoutingPolicy.AUTO_HYBRID
        val matchingProfile = GeminiVoiceProfile.forVoice(voice)

        _selectedModel.value = model
        _selectedVoice.value = voice
        _selectedVoiceProfile.value = matchingProfile
        _politenessParticle.value = voice.recommendedParticle
        _routingPolicy.value = policy

        prefs.edit()
            .putString(KEY_MODEL_ID, model.modelId)
            .putString(KEY_VOICE_ID, voice.voiceId)
            .putString(KEY_VOICE_PROFILE_ID, matchingProfile.id)
            .putString(KEY_POLITENESS_PARTICLE, voice.recommendedParticle.id)
            .putString(KEY_ROUTING_POLICY, policy.id)
            .putString(KEY_VOICE_STYLE, entity.voiceStyleId)
            .putString(KEY_RESPONSE_STYLE, entity.responseStyleId)
            .putFloat(KEY_SPEECH_RATE, entity.speechRate)
            .putFloat(KEY_PITCH, entity.pitch)
            .putFloat(KEY_VOLUME, entity.volume)
            .putInt(KEY_VOICE_WARMTH, entity.voiceWarmth)
            .apply()

        _statusMessageTh.value = "เรียกใช้โปรไฟล์ที่บันทึกไว้: \"${entity.profileName}\" (${voice.thaiTitle}) เรียบร้อยแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Updates the daily Gemini Cloud request threshold before automatic fallback to Local TTS triggers.
     */
    fun updateDailyQuotaThreshold(limit: Int) {
        val coerced = limit.coerceIn(5, 200)
        _dailyQuotaLimit.value = coerced
        prefs.edit().putInt(KEY_DAILY_QUOTA_LIMIT, coerced).apply()
        _uiState.value = buildCurrentSnapshot()
    }

    /**
     * Re-scans external Android TTS engines (enforcing the self-recursion guard), verifies SHA-256
     * checksums of F5-TTS-THAI and Thai VITS ONNX assets, and loads the local ONNX neural model.
     */
    fun refreshLocalFallbackEngines() {
        viewModelScope.launch {
            repository.initAndroidSystemTts()
            val onnxLoadResult = repository.offlineTtsEngine.loadModel()
            val localStatus = repository.localEngineStatus.value
            _statusMessageTh.value = if (onnxLoadResult.isSuccess) {
                "โหลดโมเดลประสาทเทียมออฟไลน์สำเร็จ (ตรวจสอบ SHA-256 ผ่าน ${localStatus.verifiedChecksumCount} รายการ · F5 Conditioning Ready: ${localStatus.f5ConditioningAssetsReady})"
            } else if (localStatus.selectedExternalEnginePackage != null) {
                "เชื่อมต่อเอนจินภายนอก (${localStatus.selectedExternalEnginePackage}) พร้อมใช้งานแล้ว (ยังไม่พบไฟล์ model.onnx)"
            } else {
                "ตรวจสอบ Local TTS แล้ว: รันไทม์ ONNX (${localStatus.onnxRuntimeVersion}) พร้อมใช้งาน แต่ยังไม่พบไฟล์ model.onnx หรือเอนจินภายนอก"
            }
            _uiState.value = buildCurrentSnapshot()
        }
    }

    fun toggleDarkTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        prefs.edit().putBoolean(KEY_DARK_THEME, next).apply()
        _uiState.value = buildCurrentSnapshot()
    }

    fun clearAudioCache() {
        viewModelScope.launch {
            repository.clearAudioCache()
            _statusMessageTh.value = "ล้างไฟล์แคชเสียง WAV ในเครื่องเรียบร้อยแล้ว"
            _uiState.value = buildCurrentSnapshot()
        }
    }

    fun dismissStatusMessage() {
        _statusMessageTh.value = null
        _uiState.value = buildCurrentSnapshot()
    }

    fun setDisableRewritingForNavigation(disabled: Boolean) {
        _disableRewritingForNavigation.value = disabled
        prefs.edit().putBoolean("disable_rewriting_for_navigation", disabled).apply()
        _statusMessageTh.value = if (disabled) "ปิดการดัดแปลงคำลงท้ายสำหรับคำสั่งนำทางแล้ว" else "เปิดการดัดแปลงคำลงท้ายสำหรับคำสั่งนำทางแล้ว"
        _uiState.value = buildCurrentSnapshot()
    }

    fun refreshStorageStatus() {
        viewModelScope.launch {
            _storageSummary.value = storageManager.inspectStorageSummary(verifyLargeFileHashes = false)
            _uiState.value = buildCurrentSnapshot()
        }
    }

    fun uninstallF5Model() {
        viewModelScope.launch {
            val freed = storageManager.deleteDownloadedF5Subgraphs()
            val freedMb = freed / (1024 * 1024)
            _statusMessageTh.value = "ถอนการติดตั้งโมเดล F5 เรียบร้อยแล้ว (คืนพื้นที่ $freedMb MB)"
            _storageSummary.value = storageManager.inspectStorageSummary()
            _uiState.value = buildCurrentSnapshot()
        }
    }

    fun downloadF5Model() {
        if (_isDownloadingF5.value) return
        _isDownloadingF5.value = true
        _statusMessageTh.value = "กำลังดาวน์โหลดโมเดล F5-TTS-THAI จาก HuggingFace..."
        _uiState.value = buildCurrentSnapshot()

        viewModelScope.launch {
            val res1 = storageManager.downloadArtifactWithChecksum(com.example.tts.OfflineModelArtifactSpec.F5_PREPROCESS_ONNX)
            val res2 = if (res1.isSuccess) {
                storageManager.downloadArtifactWithChecksum(com.example.tts.OfflineModelArtifactSpec.F5_VOCODER_ONNX)
            } else res1
            val res3 = if (res2.isSuccess) {
                storageManager.downloadArtifactWithChecksum(com.example.tts.OfflineModelArtifactSpec.F5_TRANSFORMER_ONNX)
            } else res2

            _isDownloadingF5.value = false
            if (res3.isSuccess) {
                _statusMessageTh.value = "ดาวน์โหลดและตรวจสอบ SHA-256 โมเดล F5-TTS-THAI สำเร็จครบถ้วนแล้ว"
            } else {
                _statusMessageTh.value = "ดาวน์โหลดโมเดลไม่สำเร็จ: ${res3.exceptionOrNull()?.localizedMessage ?: "ข้อผิดพลาดเครือข่าย"}"
            }
            _storageSummary.value = storageManager.inspectStorageSummary()
            _uiState.value = buildCurrentSnapshot()
        }
    }

    private fun applyRoutingPolicy(policy: RoutingPolicy, feedbackTh: String) {
        _routingPolicy.value = policy
        prefs.edit().putString(KEY_ROUTING_POLICY, policy.id).apply()
        _statusMessageTh.value = feedbackTh
        _uiState.value = buildCurrentSnapshot()
    }

    private fun loadPersistedRoutingPolicy(): RoutingPolicy {
        val id = prefs.getString(KEY_ROUTING_POLICY, RoutingPolicy.AUTO_HYBRID.id)
            ?: RoutingPolicy.AUTO_HYBRID.id
        return RoutingPolicy.entries.firstOrNull { it.id == id } ?: RoutingPolicy.AUTO_HYBRID
    }

    private fun loadPersistedGeminiModel(): GeminiTtsModel {
        val id = prefs.getString(KEY_MODEL_ID, GeminiTtsModel.FLASH_3_8.modelId)
            ?: GeminiTtsModel.FLASH_3_8.modelId
        return GeminiTtsModel.entries.firstOrNull { it.modelId == id } ?: GeminiTtsModel.FLASH_3_8
    }

    private fun loadPersistedGeminiVoice(): GeminiVoice {
        val id = prefs.getString(KEY_VOICE_ID, GeminiVoice.KORE.voiceId)
            ?: GeminiVoice.KORE.voiceId
        return GeminiVoice.entries.firstOrNull { it.voiceId == id } ?: GeminiVoice.KORE
    }

    private fun loadPersistedVoiceCategory(): VoiceProfileCategory {
        val id = prefs.getString(KEY_VOICE_CATEGORY, VoiceProfileCategory.ALL.id)
            ?: VoiceProfileCategory.ALL.id
        return VoiceProfileCategory.entries.firstOrNull { it.id == id } ?: VoiceProfileCategory.ALL
    }

    private fun loadPersistedVoiceProfile(currentVoice: GeminiVoice): GeminiVoiceProfile {
        val id = prefs.getString(KEY_VOICE_PROFILE_ID, null)
        return GeminiVoiceProfile.entries.firstOrNull { it.id == id }
            ?: GeminiVoiceProfile.forVoice(currentVoice)
    }

    private fun loadPersistedOfflineVoiceProfile(): OfflineVoiceProfile {
        val id = prefs.getString(KEY_OFFLINE_VOICE_PROFILE, OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR.id)
            ?: OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR.id
        return OfflineVoiceProfile.entries.firstOrNull { it.id == id }
            ?: OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR
    }

    private fun loadPersistedF5TokenizerMode(): F5TokenizerMode {
        val id = prefs.getString(KEY_F5_TOKENIZER_MODE, F5TokenizerMode.V2_IPA_PHONEME.id)
            ?: F5TokenizerMode.V2_IPA_PHONEME.id
        return F5TokenizerMode.entries.firstOrNull { it.id == id }
            ?: F5TokenizerMode.V2_IPA_PHONEME
    }

    private fun loadPersistedPolitenessParticle(currentVoice: GeminiVoice): PolitenessParticle {
        val id = prefs.getString(KEY_POLITENESS_PARTICLE, null)
        return PolitenessParticle.entries.firstOrNull { it.id == id }
            ?: currentVoice.recommendedParticle
    }

    private fun buildCurrentSnapshot(): SettingsUiState {
        val policy = _routingPolicy.value
        val category = _selectedVoiceCategory.value
        val filteredProfiles = GeminiVoiceProfile.filteredBy(category)
        val filteredVoices = if (category == VoiceProfileCategory.ALL) {
            GeminiVoice.entries
        } else {
            GeminiVoice.entries.filter { it.category == category }
        }
        val summary = _storageSummary.value
        return SettingsUiState(
            useLocalFallbackOnly = policy == RoutingPolicy.OFFLINE_ONLY,
            autoFallbackEnabled = policy != RoutingPolicy.CLOUD_ONLY,
            prioritizeLocalForShortPhrases = policy == RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST,
            routingPolicy = policy,
            selectedGeminiModel = _selectedModel.value,
            selectedGeminiVoice = _selectedVoice.value,
            selectedVoiceCategory = category,
            selectedVoiceProfile = _selectedVoiceProfile.value,
            availableVoiceProfiles = filteredProfiles,
            availableGeminiVoices = filteredVoices,
            selectedOfflineVoiceProfile = _selectedOfflineVoiceProfile.value,
            availableOfflineVoiceProfiles = OfflineVoiceProfile.entries,
            selectedF5TokenizerMode = _selectedF5TokenizerMode.value,
            f5NfeSteps = _f5NfeSteps.value,
            cloudTimeoutMs = _cloudTimeoutMs.value,
            useArgonReasoning = _useArgonReasoning.value,
            politenessParticle = _politenessParticle.value,
            savedUserProfiles = _savedUserProfiles.value,
            dailyCloudQuotaLimit = _dailyQuotaLimit.value,
            dailyCloudUsageCount = repository.dailyCloudCount.value,
            localEngineStatus = repository.localEngineStatus.value,
            capabilityProfile = ProviderCapabilityProfile.forRoutingPolicy(policy),
            disableRewritingForNavigation = _disableRewritingForNavigation.value,
            isF5Downloaded = summary.f5SubgraphsComplete,
            isDownloadingF5 = _isDownloadingF5.value,
            installedF5Bytes = summary.totalInstalledModelBytes,
            freeDiskBytes = summary.freeDeviceBytes,
            isDarkTheme = _isDarkTheme.value,
            statusMessageTh = _statusMessageTh.value
        )
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    private data class VoiceSelectionBundle(
        val category: VoiceProfileCategory,
        val profile: GeminiVoiceProfile,
        val particle: PolitenessParticle,
        val userProfiles: List<VoiceProfileEntity>,
        val offlineProfile: OfflineVoiceProfile
    )

    private data class F5AndCloudParamsBundle(
        val tokenizerMode: F5TokenizerMode,
        val nfeSteps: Int,
        val cloudTimeoutMs: Long,
        val useArgonReasoning: Boolean
    )

    companion object {
        const val PREFS_NAME = "siamvoice_user_prefs"
        const val KEY_ROUTING_POLICY = "routing_policy"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_VOICE_ID = "voice_id"
        const val KEY_VOICE_PROFILE_ID = "voice_profile_id"
        const val KEY_VOICE_CATEGORY = "voice_category"
        const val KEY_OFFLINE_VOICE_PROFILE = "offline_voice_profile"
        const val KEY_F5_TOKENIZER_MODE = "f5_tokenizer_mode"
        const val KEY_F5_NFE_STEPS = "f5_nfe_steps"
        const val KEY_CLOUD_TIMEOUT_MS = "cloud_timeout_ms"
        const val KEY_USE_ARGON_REASONING = "use_argon_reasoning"
        const val KEY_POLITENESS_PARTICLE = "politeness_particle"
        const val KEY_VOICE_STYLE = "voice_style"
        const val KEY_RESPONSE_STYLE = "response_style"
        const val KEY_SPEECH_RATE = "speech_rate"
        const val KEY_PITCH = "pitch"
        const val KEY_VOLUME = "volume"
        const val KEY_VOICE_WARMTH = "voice_warmth"
        const val KEY_DAILY_QUOTA_LIMIT = "daily_quota_limit"
        const val KEY_DARK_THEME = "dark_theme"
    }
}
