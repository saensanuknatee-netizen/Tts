package com.example.tts

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.audio.WavAudioUtils
import com.example.data.local.CachedAudioEntity
import com.example.data.local.DiagnosticLogEntity
import com.example.data.local.TtsDao
import com.example.data.local.TtsDatabase
import com.example.data.local.VoiceProfileEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.random.Random

private const val TAG = "HybridTtsRepo"

@OptIn(PublicPreviewAPI::class)
class HybridTtsRepository private constructor(
    private val context: Context,
    private val dao: TtsDao
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val requestTimestampsMs = ConcurrentLinkedDeque<Long>()

    private val cacheDir: File by lazy {
        File(context.filesDir, "tts_wav_cache").apply { mkdirs() }
    }

    val geminiTtsClient: GeminiTtsClient by lazy {
        GeminiTtsClient()
    }

    val offlineTtsEngine: OfflineTtsEngine by lazy {
        OfflineTtsEngine(context.applicationContext)
    }

    val thaiOfflineNeuralEngine: ThaiOfflineNeuralTtsEngine by lazy {
        ThaiOfflineNeuralTtsEngine(context.applicationContext)
    }

    private val _queueTickets = MutableStateFlow<List<QueueTicket>>(emptyList())
    val queueTickets: StateFlow<List<QueueTicket>> = _queueTickets.asStateFlow()

    private val _localEngineStatus = MutableStateFlow(LocalTtsEngineStatus())
    val localEngineStatus: StateFlow<LocalTtsEngineStatus> = _localEngineStatus.asStateFlow()

    private val _dailyCloudCount = MutableStateFlow(0)
    val dailyCloudCount: StateFlow<Int> = _dailyCloudCount.asStateFlow()

    @Volatile
    private var androidTts: TextToSpeech? = null
    @Volatile
    private var selectedExternalEnginePackage: String? = null
    @Volatile
    private var isAndroidTtsReady: Boolean = false
    @Volatile
    private var isThaiSupportedOnSystemTts: Boolean = false

    val isExternalFallbackInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        initAndroidSystemTts()
        repoScope.launch {
            refreshDailyCloudUsage()
        }
    }

    fun observeCachedAudio(): Flow<List<CachedAudioEntity>> = dao.observeAllCachedAudio()
    fun observeDiagnosticLogs(): Flow<List<DiagnosticLogEntity>> = dao.observeDiagnosticLogs()
    fun observePronunciationRules() = dao.observePronunciationRules()
    fun observeVoiceProfiles(): Flow<List<VoiceProfileEntity>> = dao.observeVoiceProfiles()

    /**
     * PRIORITY 1 — Self-Recursion Guard:
     * Enumerates installed TTS engine packages via PackageManager BEFORE instantiating TextToSpeech,
     * explicitly filtering out SiamVoice's own package name so fallback synthesis NEVER binds
     * to SiamVoiceTtsService recursively when SiamVoice is set as the system default TTS engine.
     */
    fun discoverInstalledTtsEnginesExcludingSelf(): Pair<List<String>, List<String>> {
        val selfPkg = context.packageName
        val pm = context.packageManager
        val discoveredPackages = linkedSetOf<String>()

        runCatching {
            val ttsServiceIntent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
            val activities = pm.queryIntentActivities(ttsServiceIntent, 0)
            activities.forEach { info ->
                info.activityInfo?.packageName?.let { discoveredPackages.add(it) }
            }
        }
        runCatching {
            val serviceIntent = Intent("android.intent.action.TTS_SERVICE")
            val services = pm.queryIntentServices(serviceIntent, 0)
            services.forEach { info ->
                info.serviceInfo?.packageName?.let { discoveredPackages.add(it) }
            }
        }

        val allPackages = discoveredPackages.toList()
        val externalCompatible = filterOutSelfTtsEngines(allPackages, selfPkg)
        return allPackages to externalCompatible
    }

    fun initAndroidSystemTts() {
        val selfPkg = context.packageName
        val onnxSpec = thaiOfflineNeuralEngine.inspectRuntimeStatus()
        val builtInNeuralVoices = buildList {
            if (onnxSpec.f5ConditioningAssetsReady) {
                add("f5-tts-thai-ref1 [24kHz Conditioning Ready]")
                add("f5-tts-thai-ref2 [24kHz Conditioning Ready]")
            }
            if (onnxSpec.f5SubgraphsInstalled) {
                add("f5-tts-thai-onnx [24kHz Zero-Shot Neural]")
            }
            if (onnxSpec.modelFilePresent) {
                add("onnx-vits-thai-local [16kHz Offline Neural]")
            }
        }
        try {
            androidTts?.shutdown()
            androidTts = null
            isAndroidTtsReady = false
            isThaiSupportedOnSystemTts = false
            selectedExternalEnginePackage = null

            val (allDiscovered, externalEngines) = discoverInstalledTtsEnginesExcludingSelf()
            val systemDefaultEngine = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
            }.getOrNull().orEmpty().ifBlank { "ไม่ระบุ (System Default)" }

            // Prioritize Google TTS if present among external engines, otherwise first non-self engine
            val chosenExternalPkg = externalEngines.firstOrNull {
                it.equals("com.google.android.tts", ignoreCase = true)
            } ?: externalEngines.firstOrNull()

            if (chosenExternalPkg == null) {
                // CRITICAL PRIORITY 1: Do NOT call TextToSpeech(context, listener) when no external engine
                // exists, because the 2-arg constructor connects to the default engine (which may be SiamVoice itself!).
                val activeRuntime = when {
                    onnxSpec.f5SubgraphsInstalled && onnxSpec.modelFilePresent ->
                        "F5-TTS-THAI ONNX (24kHz) + Thai VITS ONNX (16kHz) · ORT ${onnxSpec.runtimeVersion}"
                    onnxSpec.modelFilesInstalled ->
                        "ONNX Runtime Neural (${onnxSpec.runtimeVersion} · F5 Conditioning + VITS Ready)"
                    else ->
                        "ONNX Runtime ${onnxSpec.runtimeVersion} (รอไฟล์โมเดล ONNX · ไม่มีเอนจิน TTS ภายนอกอื่นในระบบ)"
                }
                _localEngineStatus.value = LocalTtsEngineStatus(
                    isInitialized = onnxSpec.modelFilesInstalled,
                    selfPackageName = selfPkg,
                    defaultEnginePackage = systemDefaultEngine,
                    selectedExternalEnginePackage = null,
                    selfRecursionPrevented = true,
                    installedEngines = allDiscovered.map { pkg ->
                        if (pkg == selfPkg) "$pkg (SiamVoice - ข้ามเพื่อป้องกันการเรียกซ้ำตัวเอง)" else pkg
                    },
                    thaiLanguageAvailable = onnxSpec.modelFilesInstalled,
                    thaiOfflineVoices = builtInNeuralVoices,
                    onnxRuntimeAvailable = onnxSpec.runtimeAvailable,
                    onnxRuntimeVersion = onnxSpec.runtimeVersion,
                    onnxModelInstalled = onnxSpec.modelFilesInstalled,
                    onnxModelPath = onnxSpec.modelFilePath,
                    f5ConditioningAssetsReady = onnxSpec.f5ConditioningAssetsReady,
                    f5SubgraphsInstalled = onnxSpec.f5SubgraphsInstalled,
                    f5ModelDirPath = onnxSpec.f5ModelDirectoryPath,
                    verifiedChecksumCount = onnxSpec.verifiedChecksumCount,
                    totalOfflineStorageBytes = onnxSpec.totalOfflineStorageBytes,
                    fallbackPriorityChain = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
                    onnxLicensingAndSpecsTh = "${onnxSpec.licensingSummaryTh} | ${onnxSpec.memoryAndLatencySpecTh}",
                    activeOfflineRuntime = activeRuntime,
                    statusDetailTh = if (onnxSpec.modelFilesInstalled) {
                        "พร้อมใช้งานระบบประสาทเทียมภาษาไทยออฟไลน์ (F5-TTS-THAI Conditioning + VITS ONNX · ตรวจสอบ SHA-256 ${onnxSpec.verifiedChecksumCount} รายการ · ป้องกัน Recursive TTS สำเร็จ)"
                    } else {
                        "ป้องกัน Recursive TTS สำเร็จ (ไม่ผูกกลับหา $selfPkg) · ONNX Runtime ${onnxSpec.runtimeVersion} พร้อมทำงาน แต่ยังไม่ได้ติดตั้งไฟล์ model.onnx ที่ ${onnxSpec.modelFilePath}"
                    }
                )
                return
            }

            selectedExternalEnginePackage = chosenExternalPkg
            // Explicitly pass chosenExternalPkg (3-arg constructor) so Android NEVER binds to SiamVoice itself
            androidTts = TextToSpeech(
                context.applicationContext,
                { status ->
                    val ttsInstance = androidTts
                    if (status == TextToSpeech.SUCCESS && ttsInstance != null && chosenExternalPkg != selfPkg) {
                        isAndroidTtsReady = true
                        val thaiLocale = Locale.forLanguageTag("th-TH")
                        val langResult = ttsInstance.isLanguageAvailable(thaiLocale)
                        val thaiSupported = langResult >= TextToSpeech.LANG_AVAILABLE
                        if (thaiSupported) {
                            ttsInstance.language = thaiLocale
                        }
                        isThaiSupportedOnSystemTts = thaiSupported

                        val thaiVoices = runCatching {
                            ttsInstance.voices
                                ?.filter { it.locale.language.equals("th", ignoreCase = true) }
                                ?.map { v ->
                                    val mode = if (v.isNetworkConnectionRequired) "Online" else "Offline"
                                    "${v.name} [$mode]"
                                }
                                .orEmpty()
                        }.getOrDefault(emptyList())

                        val runtimeLabel = when {
                            onnxSpec.f5SubgraphsInstalled ->
                                "F5-TTS-THAI ONNX (24kHz) + Thai VITS + External ($chosenExternalPkg)"
                            onnxSpec.modelFilesInstalled ->
                                "ONNX Runtime Neural (${onnxSpec.runtimeVersion}) + F5 Conditioning + External ($chosenExternalPkg)"
                            thaiSupported ->
                                "External Android System TTS ($chosenExternalPkg · th-TH)"
                            else ->
                                "ONNX Runtime ${onnxSpec.runtimeVersion} (รอไฟล์ model.onnx · เอนจินภายนอก $chosenExternalPkg ไม่มีชุดเสียง th-TH)"
                        }

                        _localEngineStatus.value = LocalTtsEngineStatus(
                            isInitialized = onnxSpec.modelFilesInstalled || thaiSupported,
                            selfPackageName = selfPkg,
                            defaultEnginePackage = systemDefaultEngine,
                            selectedExternalEnginePackage = chosenExternalPkg,
                            selfRecursionPrevented = true,
                            installedEngines = allDiscovered.map { pkg ->
                                if (pkg == selfPkg) "$pkg (SiamVoice - Excluded)" else pkg
                            },
                            thaiLanguageAvailable = onnxSpec.modelFilesInstalled || thaiSupported,
                            thaiOfflineVoices = (builtInNeuralVoices + thaiVoices).distinct(),
                            onnxRuntimeAvailable = onnxSpec.runtimeAvailable,
                            onnxRuntimeVersion = onnxSpec.runtimeVersion,
                            onnxModelInstalled = onnxSpec.modelFilesInstalled,
                            onnxModelPath = onnxSpec.modelFilePath,
                            f5ConditioningAssetsReady = onnxSpec.f5ConditioningAssetsReady,
                            f5SubgraphsInstalled = onnxSpec.f5SubgraphsInstalled,
                            f5ModelDirPath = onnxSpec.f5ModelDirectoryPath,
                            verifiedChecksumCount = onnxSpec.verifiedChecksumCount,
                            totalOfflineStorageBytes = onnxSpec.totalOfflineStorageBytes,
                            fallbackPriorityChain = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
                            onnxLicensingAndSpecsTh = "${onnxSpec.licensingSummaryTh} | ${onnxSpec.memoryAndLatencySpecTh}",
                            activeOfflineRuntime = runtimeLabel,
                            statusDetailTh = when {
                                onnxSpec.modelFilesInstalled ->
                                    "พร้อมใช้งานโมเดลเสียงภาษาไทยออฟไลน์ (F5-TTS-THAI + VITS ONNX · SHA-256 ${onnxSpec.verifiedChecksumCount} รายการ) และเอนจินภายนอก $chosenExternalPkg"
                                thaiSupported ->
                                    "ผูกเอนจินภายนอก $chosenExternalPkg สำเร็จ (ไม่เรียกซ้ำ $selfPkg) · พบเสียงไทย ${thaiVoices.size} เสียง"
                                else ->
                                    "เอนจินภายนอก $chosenExternalPkg ไม่รองรับภาษาไทย และยังไม่มีไฟล์ ONNX VITS model.onnx ในเครื่อง"
                            }
                        )
                    } else {
                        isAndroidTtsReady = false
                        isThaiSupportedOnSystemTts = false
                        _localEngineStatus.value = LocalTtsEngineStatus(
                            isInitialized = onnxSpec.modelFilesInstalled,
                            selfPackageName = selfPkg,
                            defaultEnginePackage = systemDefaultEngine,
                            selectedExternalEnginePackage = null,
                            selfRecursionPrevented = true,
                            installedEngines = allDiscovered,
                            thaiLanguageAvailable = onnxSpec.modelFilesInstalled,
                            thaiOfflineVoices = builtInNeuralVoices,
                            onnxRuntimeAvailable = onnxSpec.runtimeAvailable,
                            onnxRuntimeVersion = onnxSpec.runtimeVersion,
                            onnxModelInstalled = onnxSpec.modelFilesInstalled,
                            onnxModelPath = onnxSpec.modelFilePath,
                            f5ConditioningAssetsReady = onnxSpec.f5ConditioningAssetsReady,
                            f5SubgraphsInstalled = onnxSpec.f5SubgraphsInstalled,
                            f5ModelDirPath = onnxSpec.f5ModelDirectoryPath,
                            verifiedChecksumCount = onnxSpec.verifiedChecksumCount,
                            totalOfflineStorageBytes = onnxSpec.totalOfflineStorageBytes,
                            fallbackPriorityChain = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
                            onnxLicensingAndSpecsTh = "${onnxSpec.licensingSummaryTh} | ${onnxSpec.memoryAndLatencySpecTh}",
                            activeOfflineRuntime = if (onnxSpec.modelFilesInstalled) {
                                "ONNX Runtime Neural (${onnxSpec.runtimeVersion})"
                            } else {
                                "ไม่มีเอนจินเสียงภาษาไทยออฟไลน์ที่พร้อมใช้งาน (รอติดตั้ง model.onnx)"
                            },
                            statusDetailTh = "เชื่อมโยงเอนจินภายนอก $chosenExternalPkg ไม่สำเร็จ"
                        )
                    }
                },
                chosenExternalPkg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize external Android TextToSpeech", e)
            _localEngineStatus.value = LocalTtsEngineStatus(
                isInitialized = onnxSpec.modelFilesInstalled,
                selfPackageName = selfPkg,
                defaultEnginePackage = "Error",
                selectedExternalEnginePackage = null,
                selfRecursionPrevented = true,
                thaiOfflineVoices = builtInNeuralVoices,
                onnxRuntimeAvailable = onnxSpec.runtimeAvailable,
                onnxRuntimeVersion = onnxSpec.runtimeVersion,
                onnxModelInstalled = onnxSpec.modelFilesInstalled,
                onnxModelPath = onnxSpec.modelFilePath,
                f5ConditioningAssetsReady = onnxSpec.f5ConditioningAssetsReady,
                f5SubgraphsInstalled = onnxSpec.f5SubgraphsInstalled,
                f5ModelDirPath = onnxSpec.f5ModelDirectoryPath,
                verifiedChecksumCount = onnxSpec.verifiedChecksumCount,
                totalOfflineStorageBytes = onnxSpec.totalOfflineStorageBytes,
                fallbackPriorityChain = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
                onnxLicensingAndSpecsTh = "${onnxSpec.licensingSummaryTh} | ${onnxSpec.memoryAndLatencySpecTh}",
                activeOfflineRuntime = "ONNX Runtime ${onnxSpec.runtimeVersion} (Error initializing external TTS)",
                statusDetailTh = "เกิดข้อผิดพลาดในการเชื่อมโยงระบบ TTS ภายนอก: ${e.localizedMessage}"
            )
        }
    }

    suspend fun refreshDailyCloudUsage() {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        _dailyCloudCount.value = dao.countCloudRequestsSince(calendar.timeInMillis)
    }

    /**
     * Normalizes Thai text using ThaiTextOptimizer (numbers, times, units, roads, mixed Thai-English,
     * clause pauses, and custom pronunciation rules) without changing the meaning of the input text.
     */
    suspend fun normalizeAndApplyPronunciation(
        rawText: String,
        pronunciationClarity: Int = 75,
        naturalPauses: Int = 55,
        adaptationLevel: SpeechAdaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
        politenessParticle: PolitenessParticle = PolitenessParticle.KRUB,
        voiceStyle: VoiceStylePreset = VoiceStylePreset.NATURAL,
        responseStyle: ResponseStyle = ResponseStyle.CONVERSATIONAL,
        thaiPoliteEnding: ThaiPoliteEnding? = null,
        disableRewritingForNavigation: Boolean = false
    ): String {
        var activeRules = dao.getActivePronunciationRules()
        if (activeRules.isEmpty() && dao.countPronunciationRules() == 0) {
            TtsDatabase.seedDefaultPronunciationRules(dao)
            activeRules = dao.getActivePronunciationRules()
        }
        return ThaiTextOptimizer.optimizeThaiText(
            rawText = rawText,
            customRules = activeRules,
            pronunciationClarity = pronunciationClarity,
            naturalPauses = naturalPauses,
            adaptationLevel = adaptationLevel,
            politenessParticle = politenessParticle,
            voiceStyle = voiceStyle,
            responseStyle = responseStyle,
            thaiPoliteEnding = thaiPoliteEnding,
            disableRewritingForNavigation = disableRewritingForNavigation
        )
    }

    /**
     * Primary entry point for Hybrid TTS synthesis with full Voice Personality & Response Style support.
     */
    suspend fun synthesizeSpeech(
        rawText: String,
        config: SynthesisConfig,
        isNavigationPhrase: Boolean = false,
        bypassCache: Boolean = false,
        isCalledFromSystemTtsService: Boolean = false
    ): Result<SynthesisOutcome> = withContext(Dispatchers.IO) {
        val startTimeMs = System.currentTimeMillis()
        val ticketId = UUID.randomUUID().toString().take(8)
        val normalizedText = normalizeAndApplyPronunciation(
            rawText = rawText,
            pronunciationClarity = config.pronunciationClarity,
            naturalPauses = config.naturalPauses,
            adaptationLevel = config.adaptationLevel,
            politenessParticle = config.politenessParticle,
            voiceStyle = config.style,
            responseStyle = config.responseStyle,
            thaiPoliteEnding = config.thaiPoliteEnding,
            disableRewritingForNavigation = config.disableRewritingForNavigation
        )

        if (normalizedText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("กรุณาป้อนข้อความที่ต้องการสังเคราะห์เสียง"))
        }

        val cacheKey = WavAudioUtils.computeCacheKey(
            normalizedText = normalizedText,
            modelId = config.model.modelId,
            voiceName = config.voice.voiceId,
            styleName = config.style.id,
            speechRate = config.speechRate,
            pitch = config.pitch,
            volume = config.volume,
            voiceWarmth = config.voiceWarmth,
            expressiveness = config.expressiveness,
            responseStyleId = config.responseStyle.id
        )

        enqueueTicket(
            QueueTicket(
                id = ticketId,
                textSnippet = normalizedText.take(36),
                status = QueueTicketStatus.QUEUED,
                providerUsed = "${config.routingPolicy.id} · ${config.style.titleEn}"
            )
        )

        // 1. Check Local WAV Disk Cache first for ultra-low latency
        if (!bypassCache) {
            val cached = dao.getCachedAudioByKey(cacheKey)
            if (cached != null) {
                val wavFile = File(cached.wavFilePath)
                if (wavFile.exists() && wavFile.length() > WavAudioUtils.WAV_HEADER_SIZE) {
                    dao.incrementCacheHit(cacheKey)
                    val bytes = wavFile.readBytes()
                    val latency = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                    updateTicket(
                        ticketId,
                        QueueTicketStatus.CACHE_HIT,
                        provider = "LOCAL_CACHE (${cached.providerUsed})",
                        latencyMs = latency,
                        messageTh = "ดึงไฟล์เสียง WAV จากแคชสำเร็จ (${latency} ms · สไตล์ ${config.style.titleEn})"
                    )
                    logDiagnostic(
                        level = "INFO",
                        category = "CACHE",
                        provider = "LOCAL_CACHE",
                        message = "Cache HIT [${config.style.titleEn}/${config.responseStyle.titleEn}] '${normalizedText.take(28)}' (${latency} ms)"
                    )
                    return@withContext Result.success(
                        SynthesisOutcome(
                            wavFilePath = wavFile.absolutePath,
                            wavBytes = bytes,
                            waveformBars = WavAudioUtils.computeWaveformAmplitudes(bytes),
                            durationMs = cached.durationMs,
                            sampleRateHz = cached.sampleRateHz,
                            providerUsed = "CACHE_HIT (${cached.providerUsed})",
                            modelUsed = cached.modelId,
                            voiceUsed = cached.voiceName,
                            latencyMs = latency,
                            fallbackTriggered = false,
                            fallbackReasonTh = null,
                            normalizedText = normalizedText
                        )
                    )
                }
            }
        }

        queueMutex.withLock {
            refreshDailyCloudUsage()
            val currentDailyCount = _dailyCloudCount.value
            val isNetworkAvailable = isOnline()
            val isShortNav = isNavigationPhrase ||
                config.style == VoiceStylePreset.NAVIGATION ||
                config.responseStyle == ResponseStyle.NAVIGATION ||
                normalizedText.length <= config.shortPhraseThresholdChars

            when (config.routingPolicy) {
                RoutingPolicy.OFFLINE_ONLY -> {
                    return@withLock executeLocalSynthesisAndCache(
                        ticketId = ticketId,
                        cacheKey = cacheKey,
                        rawText = rawText,
                        normalizedText = normalizedText,
                        config = config,
                        startTimeMs = startTimeMs,
                        isNavigationPhrase = isNavigationPhrase,
                        fallbackTriggered = false,
                        fallbackReasonTh = "ตั้งค่าโหมดออฟไลน์ 100% (Offline Local)"
                    )
                }

                RoutingPolicy.SHORT_PHRASE_LOCAL_FIRST -> {
                    if (isShortNav) {
                        return@withLock executeLocalSynthesisAndCache(
                            ticketId = ticketId,
                            cacheKey = cacheKey,
                            rawText = rawText,
                            normalizedText = normalizedText,
                            config = config,
                            startTimeMs = startTimeMs,
                            isNavigationPhrase = isNavigationPhrase,
                            fallbackTriggered = false,
                            fallbackReasonTh = "วลีนำทาง/ข้อความสั้น ใช้การสังเคราะห์ในเครื่องทันทีเพื่อลดความหน่วง"
                        )
                    }
                }

                RoutingPolicy.AUTO_HYBRID, RoutingPolicy.CLOUD_ONLY -> {
                    // Proceed to check network & quota below
                }
            }

            // Check Quota Threshold before calling Cloud (Never bypass quotas)
            if (currentDailyCount >= config.dailyCloudQuotaLimit) {
                val quotaMsg = "จำนวนการใช้งาน Cloud วันนี้ถึงเกณฑ์ที่กำหนดแล้ว ($currentDailyCount/${config.dailyCloudQuotaLimit} ครั้ง)"
                logDiagnostic(
                    level = "WARN",
                    category = "QUOTA",
                    provider = config.model.modelId,
                    errorCode = "THRESHOLD_EXCEEDED",
                    message = quotaMsg,
                    isQuotaError = true,
                    fallbackTriggered = config.routingPolicy != RoutingPolicy.CLOUD_ONLY
                )
                if (config.routingPolicy == RoutingPolicy.CLOUD_ONLY) {
                    updateTicket(
                        ticketId,
                        QueueTicketStatus.FAILED,
                        provider = config.model.modelId,
                        latencyMs = System.currentTimeMillis() - startTimeMs,
                        messageTh = quotaMsg
                    )
                    return@withLock Result.failure(IllegalStateException("$quotaMsg (โหมด Cloud Only จึงไม่สลับไป Local)"))
                }
                return@withLock executeLocalSynthesisAndCache(
                    ticketId = ticketId,
                    cacheKey = cacheKey,
                    rawText = rawText,
                    normalizedText = normalizedText,
                    config = config,
                    startTimeMs = startTimeMs,
                    isNavigationPhrase = isNavigationPhrase,
                    fallbackTriggered = true,
                    fallbackReasonTh = "$quotaMsg · สลับมาใช้ Local TTS อัตโนมัติ"
                )
            }

            // Check Rate Limit (Requests Per Minute)
            if (!checkAndRecordRateLimit(config.rpmLimit)) {
                val rateMsg = "เกินอัตราการเรียก API ต่อนาที (${config.rpmLimit} RPM)"
                logDiagnostic(
                    level = "WARN",
                    category = "QUOTA",
                    provider = config.model.modelId,
                    errorCode = "RATE_LIMIT_RPM",
                    message = rateMsg,
                    isQuotaError = true,
                    fallbackTriggered = config.routingPolicy != RoutingPolicy.CLOUD_ONLY
                )
                if (config.routingPolicy == RoutingPolicy.CLOUD_ONLY) {
                    updateTicket(
                        ticketId,
                        QueueTicketStatus.FAILED,
                        provider = config.model.modelId,
                        latencyMs = System.currentTimeMillis() - startTimeMs,
                        messageTh = rateMsg
                    )
                    return@withLock Result.failure(IllegalStateException(rateMsg))
                }
                return@withLock executeLocalSynthesisAndCache(
                    ticketId = ticketId,
                    cacheKey = cacheKey,
                    rawText = rawText,
                    normalizedText = normalizedText,
                    config = config,
                    startTimeMs = startTimeMs,
                    isNavigationPhrase = isNavigationPhrase,
                    fallbackTriggered = true,
                    fallbackReasonTh = "$rateMsg · สลับมาใช้ Local TTS อัตโนมัติ"
                )
            }

            // Check Network Connectivity
            if (!isNetworkAvailable) {
                val netMsg = "ไม่พบการเชื่อมต่ออินเทอร์เน็ต"
                logDiagnostic(
                    level = "WARN",
                    category = "NETWORK",
                    provider = config.model.modelId,
                    errorCode = "OFFLINE",
                    message = netMsg,
                    isNetworkError = true,
                    fallbackTriggered = config.routingPolicy != RoutingPolicy.CLOUD_ONLY
                )
                if (config.routingPolicy == RoutingPolicy.CLOUD_ONLY) {
                    updateTicket(
                        ticketId,
                        QueueTicketStatus.FAILED,
                        provider = config.model.modelId,
                        latencyMs = System.currentTimeMillis() - startTimeMs,
                        messageTh = netMsg
                    )
                    return@withLock Result.failure(IllegalStateException("$netMsg ไม่สามารถเรียกใช้ Gemini Cloud ได้"))
                }
                return@withLock executeLocalSynthesisAndCache(
                    ticketId = ticketId,
                    cacheKey = cacheKey,
                    rawText = rawText,
                    normalizedText = normalizedText,
                    config = config,
                    startTimeMs = startTimeMs,
                    isNavigationPhrase = isNavigationPhrase,
                    fallbackTriggered = true,
                    fallbackReasonTh = "$netMsg · สลับมาใช้ Local TTS อัตโนมัติ"
                )
            }

            // Try Gemini Cloud TTS with Exponential Backoff
            val cloudResult = synthesizeWithGeminiCloudBackoff(
                ticketId = ticketId,
                normalizedText = normalizedText,
                config = config
            )

            if (cloudResult.isSuccess) {
                val (wavBytes, activeModelUsed) = cloudResult.getOrThrow()
                val wavFile = File(cacheDir, "$cacheKey.wav")
                wavFile.writeBytes(wavBytes)

                val durationMs = WavAudioUtils.calculateDurationMs(wavBytes)
                val sampleRate = WavAudioUtils.extractSampleRateFromWav(wavBytes)
                val latency = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)

                dao.insertCachedAudio(
                    CachedAudioEntity(
                        cacheKey = cacheKey,
                        originalText = rawText,
                        normalizedText = normalizedText,
                        providerUsed = "GEMINI_CLOUD",
                        modelId = activeModelUsed,
                        voiceName = config.voice.voiceId,
                        styleName = "${config.style.id}/${config.responseStyle.id}",
                        speechRate = config.speechRate,
                        pitch = config.pitch,
                        wavFilePath = wavFile.absolutePath,
                        durationMs = durationMs,
                        sampleRateHz = sampleRate,
                        fileSizeBytes = wavBytes.size.toLong(),
                        hitCount = 1,
                        synthesisLatencyMs = latency,
                        isNavigationPhrase = isNavigationPhrase
                    )
                )
                refreshDailyCloudUsage()

                updateTicket(
                    ticketId,
                    QueueTicketStatus.COMPLETED,
                    provider = "GEMINI_CLOUD ($activeModelUsed)",
                    latencyMs = latency,
                    messageTh = "สังเคราะห์ผ่าน $activeModelUsed [${config.style.titleEn}] สำเร็จ (${latency} ms)"
                )
                logDiagnostic(
                    level = "INFO",
                    category = "CLOUD_TTS",
                    provider = activeModelUsed,
                    message = "สังเคราะห์เสียงสำเร็จ (${wavBytes.size / 1024} KB, ${latency} ms, เสียง ${config.voice.voiceId}, สไตล์ ${config.style.titleEn})"
                )

                return@withLock Result.success(
                    SynthesisOutcome(
                        wavFilePath = wavFile.absolutePath,
                        wavBytes = wavBytes,
                        waveformBars = WavAudioUtils.computeWaveformAmplitudes(wavBytes),
                        durationMs = durationMs,
                        sampleRateHz = sampleRate,
                        providerUsed = "GEMINI_CLOUD",
                        modelUsed = activeModelUsed,
                        voiceUsed = config.voice.voiceId,
                        latencyMs = latency,
                        fallbackTriggered = false,
                        fallbackReasonTh = null,
                        normalizedText = normalizedText
                    )
                )
            } else {
                val ex = cloudResult.exceptionOrNull()
                val errMsg = ex?.localizedMessage ?: "Gemini Cloud TTS error"
                val isQuota = (ex as? GeminiTtsException)?.isQuotaError == true ||
                    ex is QuotaExceededException ||
                    GeminiTtsClient.isQuotaMessage(errMsg)

                logDiagnostic(
                    level = "ERROR",
                    category = if (isQuota) "QUOTA" else "CLOUD_TTS",
                    provider = config.model.modelId,
                    errorCode = if (isQuota) "HTTP_429_QUOTA" else "CLOUD_SYNTH_ERR",
                    message = "Gemini TTS ขัดข้อง: $errMsg",
                    isQuotaError = isQuota,
                    isNetworkError = !isQuota,
                    fallbackTriggered = config.routingPolicy != RoutingPolicy.CLOUD_ONLY
                )

                if (config.routingPolicy == RoutingPolicy.CLOUD_ONLY) {
                    updateTicket(
                        ticketId,
                        QueueTicketStatus.FAILED,
                        provider = config.model.modelId,
                        latencyMs = System.currentTimeMillis() - startTimeMs,
                        messageTh = "ล้มเหลว: $errMsg"
                    )
                    return@withLock Result.failure(ex ?: IllegalStateException(errMsg))
                }

                val reason = if (isQuota) {
                    "โควตา Gemini Cloud เต็ม (Quota Exhausted) · สลับมาใช้ Local TTS อัตโนมัติ"
                } else {
                    "เชื่อมต่อ Gemini Cloud ไม่สำเร็จ ($errMsg) · สลับมาใช้ Local TTS อัตโนมัติ"
                }

                return@withLock executeLocalSynthesisAndCache(
                    ticketId = ticketId,
                    cacheKey = cacheKey,
                    rawText = rawText,
                    normalizedText = normalizedText,
                    config = config,
                    startTimeMs = startTimeMs,
                    isNavigationPhrase = isNavigationPhrase,
                    fallbackTriggered = true,
                    fallbackReasonTh = reason
                )
            }
        }
    }

    private suspend fun synthesizeWithGeminiCloudBackoff(
        ticketId: String,
        normalizedText: String,
        config: SynthesisConfig
    ): Result<Pair<ByteArray, String>> {
        val result = geminiTtsClient.synthesizeWithBackoff(
            normalizedText = normalizedText,
            config = config,
            onAttemptStart = { attemptNumber, modelName ->
                updateTicket(
                    ticketId = ticketId,
                    status = if (attemptNumber == 1) QueueTicketStatus.SYNTHESIZING_CLOUD else QueueTicketStatus.RETRY_BACKOFF,
                    provider = modelName,
                    attempt = attemptNumber,
                    messageTh = if (attemptNumber == 1) {
                        "กำลังสตรีมและถอดรหัสเสียงจาก $modelName (${config.voice.voiceId} · ${config.style.titleEn})..."
                    } else {
                        "กำลังลองใหม่ครั้งที่ $attemptNumber ด้วยโมเดล $modelName..."
                    }
                )
            }
        )
        return result.map { it.wavBytes to it.modelUsed }
    }

    private suspend fun executeLocalSynthesisAndCache(
        ticketId: String,
        cacheKey: String,
        rawText: String,
        normalizedText: String,
        config: SynthesisConfig,
        startTimeMs: Long,
        isNavigationPhrase: Boolean,
        fallbackTriggered: Boolean,
        fallbackReasonTh: String?
    ): Result<SynthesisOutcome> {
        updateTicket(
            ticketId = ticketId,
            status = if (fallbackTriggered) QueueTicketStatus.FALLBACK_LOCAL else QueueTicketStatus.SYNTHESIZING_CLOUD,
            provider = "LOCAL_THAI_TTS",
            messageTh = fallbackReasonTh ?: "กำลังสังเคราะห์เสียงภาษาไทยในเครื่อง..."
        )

        val wavFile = File(cacheDir, "$cacheKey.wav")
        var providerTag = "ONNX_VITS_NEURAL_TH"
        var modelUsedLabel = "onnx-vits-thai"
        var voiceUsedLabel = config.offlineVoiceProfile.id
        var localSuccess = false
        var failureDetailTh: String? = null

        // 1. PRIORITY 2: Attempt On-Device Thai Neural TTS via OfflineTtsEngine
        //    (F5-TTS-THAI 3-subgraph 24kHz ONNX if installed, otherwise bundled Thai VITS 16kHz ONNX)
        if (offlineTtsEngine.isNeuralModelReady()) {
            val effectiveOfflineProfile = if (isNavigationPhrase && !offlineTtsEngine.f5TtsEngine.areF5OnnxSubgraphsInstalled()) {
                OfflineVoiceProfile.THAI_VITS_FAST_NAVIGATION
            } else {
                config.offlineVoiceProfile
            }
            val neuralResult = offlineTtsEngine.synthesize(
                text = normalizedText,
                speechRate = config.speechRate,
                pitch = config.pitch,
                volume = config.volume,
                voiceWarmth = config.voiceWarmth,
                offlineVoiceProfile = effectiveOfflineProfile,
                f5TokenizerMode = config.f5TokenizerMode,
                f5NfeSteps = config.f5NfeSteps
            )
            if (neuralResult.isSuccess) {
                val synOut = neuralResult.getOrThrow()
                if (synOut.wavBytes.size > WavAudioUtils.WAV_HEADER_SIZE) {
                    wavFile.writeBytes(synOut.wavBytes)
                    providerTag = synOut.engineBackendUsed
                    modelUsedLabel = if (synOut.engineBackendUsed == "F5_TTS_THAI_ONNX_24KHZ") {
                        "F5-TTS-THAI ONNX (24kHz)"
                    } else {
                        "ONNX VITS Thai Neural TTS (16kHz)"
                    }
                    voiceUsedLabel = synOut.voiceProfileUsed
                    localSuccess = true
                }
            } else {
                failureDetailTh = neuralResult.exceptionOrNull()?.localizedMessage
            }
        }

        // 2. PRIORITY 1 & 2: If ONNX neural synthesis is unavailable or failed, fall back to a verified EXTERNAL
        //    Android System TTS engine (strictly excluding SiamVoice's own package name to prevent recursion)
        val externalPkg = selectedExternalEnginePackage
        val tts = androidTts
        if (!localSuccess &&
            externalPkg != null &&
            !externalPkg.equals(context.packageName, ignoreCase = true) &&
            isAndroidTtsReady &&
            isThaiSupportedOnSystemTts &&
            tts != null
        ) {
            localSuccess = synthesizeViaAndroidSystemTts(
                tts = tts,
                text = normalizedText,
                speechRate = config.speechRate,
                pitch = config.pitch,
                destFile = wavFile
            )
            if (localSuccess && wavFile.exists() && wavFile.length() > WavAudioUtils.WAV_HEADER_SIZE) {
                providerTag = "EXTERNAL_ANDROID_TTS ($externalPkg)"
                modelUsedLabel = "External System TTS ($externalPkg)"
                voiceUsedLabel = "th-TH-external"
                if (abs(config.volume - 1.0f) > 0.02f || config.voiceWarmth != 50) {
                    val rawWav = wavFile.readBytes()
                    val sr = WavAudioUtils.extractSampleRateFromWav(rawWav)
                    val postWav = WavAudioUtils.applyAudioPostProcessing(
                        wavOrPcmBytes = rawWav,
                        sampleRateHz = sr,
                        volume = config.volume,
                        voiceWarmth = config.voiceWarmth,
                        speedRatio = 1.0f
                    )
                    wavFile.writeBytes(postWav)
                }
            }
        }

        // 3. STRICT PRIORITY 2 COMPLIANCE: Never synthesize or report DSP tones as human speech!
        if (!localSuccess || !wavFile.exists() || wavFile.length() <= WavAudioUtils.WAV_HEADER_SIZE) {
            val onnxStatus = thaiOfflineNeuralEngine.inspectRuntimeStatus()
            val honestErrorMsg = failureDetailTh ?: buildString {
                append("ไม่สามารถสังเคราะห์เสียงพูดภาษาไทยแบบออฟไลน์ได้: ")
                append("1) รันไทม์ประสาทเทียม ONNX Runtime (${onnxStatus.runtimeVersion}) พร้อมทำงาน แต่ยังไม่ได้ติดตั้งไฟล์โมเดล ONNX ที่ ${onnxStatus.modelFilePath} ")
                append("2) ไม่พบเอนจิน Android TTS ภาษาไทยภายนอกอื่นที่ต่างจาก ${context.packageName} ")
                append("(ระบบไม่ส่งสัญญาณเสียงสังเคราะห์ DSP แทนเสียงพูดมนุษย์ตามมาตรฐานความถูกต้อง)")
            }
            val latency = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
            updateTicket(
                ticketId = ticketId,
                status = QueueTicketStatus.FAILED,
                provider = "OFFLINE_UNAVAILABLE",
                latencyMs = latency,
                messageTh = honestErrorMsg
            )
            logDiagnostic(
                level = "ERROR",
                category = "LOCAL_TTS",
                provider = "ONNX_VITS_NEURAL_TH",
                errorCode = "OFFLINE_MODEL_NOT_INSTALLED",
                message = honestErrorMsg,
                fallbackTriggered = fallbackTriggered
            )
            return Result.failure(IllegalStateException(honestErrorMsg))
        }

        val wavBytes = wavFile.readBytes()
        val durationMs = WavAudioUtils.calculateDurationMs(wavBytes)
        val sampleRate = WavAudioUtils.extractSampleRateFromWav(wavBytes)
        val latency = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)

        dao.insertCachedAudio(
            CachedAudioEntity(
                cacheKey = cacheKey,
                originalText = rawText,
                normalizedText = normalizedText,
                providerUsed = providerTag,
                modelId = modelUsedLabel,
                voiceName = voiceUsedLabel,
                styleName = "${config.style.id}/${config.responseStyle.id}",
                speechRate = config.speechRate,
                pitch = config.pitch,
                wavFilePath = wavFile.absolutePath,
                durationMs = durationMs,
                sampleRateHz = sampleRate,
                fileSizeBytes = wavBytes.size.toLong(),
                hitCount = 1,
                synthesisLatencyMs = latency,
                isNavigationPhrase = isNavigationPhrase
            )
        )

        updateTicket(
            ticketId = ticketId,
            status = QueueTicketStatus.COMPLETED,
            provider = providerTag,
            latencyMs = latency,
            messageTh = if (fallbackTriggered) {
                "สลับใช้ $providerTag สำเร็จ (${latency} ms)"
            } else {
                "สังเคราะห์ในเครื่อง ($providerTag) สำเร็จ (${latency} ms)"
            }
        )

        logDiagnostic(
            level = if (fallbackTriggered) "WARN" else "INFO",
            category = "LOCAL_TTS",
            provider = providerTag,
            message = (fallbackReasonTh ?: "สังเคราะห์เสียงในเครื่องสำเร็จ") + " [${latency} ms]",
            fallbackTriggered = fallbackTriggered
        )

        return Result.success(
            SynthesisOutcome(
                wavFilePath = wavFile.absolutePath,
                wavBytes = wavBytes,
                waveformBars = WavAudioUtils.computeWaveformAmplitudes(wavBytes),
                durationMs = durationMs,
                sampleRateHz = sampleRate,
                providerUsed = providerTag,
                modelUsed = modelUsedLabel,
                voiceUsed = voiceUsedLabel,
                latencyMs = latency,
                fallbackTriggered = fallbackTriggered,
                fallbackReasonTh = fallbackReasonTh,
                normalizedText = normalizedText
            )
        )
    }

    /**
     * Pre-caches a verified WAV audio payload for a phrase so offline navigation or tests
     * can verify immediate local WAV cache lookup without invoking cloud APIs or fake DSP tones.
     */
    suspend fun seedVerifiedCachedWav(
        rawText: String,
        config: SynthesisConfig,
        wavBytes: ByteArray,
        providerLabel: String = "PRE_CACHED_WAV",
        isNavigationPhrase: Boolean = true
    ): SynthesisOutcome = withContext(Dispatchers.IO) {
        val normalizedText = normalizeAndApplyPronunciation(
            rawText = rawText,
            pronunciationClarity = config.pronunciationClarity,
            naturalPauses = config.naturalPauses,
            adaptationLevel = config.adaptationLevel,
            politenessParticle = config.politenessParticle,
            voiceStyle = config.style,
            responseStyle = config.responseStyle
        )
        val cacheKey = WavAudioUtils.computeCacheKey(
            normalizedText = normalizedText,
            modelId = config.model.modelId,
            voiceName = config.voice.voiceId,
            styleName = config.style.id,
            speechRate = config.speechRate,
            pitch = config.pitch,
            volume = config.volume,
            voiceWarmth = config.voiceWarmth,
            expressiveness = config.expressiveness,
            responseStyleId = config.responseStyle.id
        )
        val validWav = WavAudioUtils.ensureWavContainer(wavBytes)
        val wavFile = File(cacheDir, "$cacheKey.wav")
        wavFile.writeBytes(validWav)
        val durationMs = WavAudioUtils.calculateDurationMs(validWav)
        val sampleRate = WavAudioUtils.extractSampleRateFromWav(validWav)

        dao.insertCachedAudio(
            CachedAudioEntity(
                cacheKey = cacheKey,
                originalText = rawText,
                normalizedText = normalizedText,
                providerUsed = providerLabel,
                modelId = config.model.modelId,
                voiceName = config.voice.voiceId,
                styleName = "${config.style.id}/${config.responseStyle.id}",
                speechRate = config.speechRate,
                pitch = config.pitch,
                wavFilePath = wavFile.absolutePath,
                durationMs = durationMs,
                sampleRateHz = sampleRate,
                fileSizeBytes = validWav.size.toLong(),
                hitCount = 1,
                synthesisLatencyMs = 1L,
                isNavigationPhrase = isNavigationPhrase
            )
        )

        SynthesisOutcome(
            wavFilePath = wavFile.absolutePath,
            wavBytes = validWav,
            waveformBars = WavAudioUtils.computeWaveformAmplitudes(validWav),
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            providerUsed = providerLabel,
            modelUsed = config.model.modelId,
            voiceUsed = config.voice.voiceId,
            latencyMs = 1L,
            fallbackTriggered = false,
            fallbackReasonTh = null,
            normalizedText = normalizedText
        )
    }

    private suspend fun synthesizeViaAndroidSystemTts(
        tts: TextToSpeech,
        text: String,
        speechRate: Float,
        pitch: Float,
        destFile: File
    ): Boolean {
        isExternalFallbackInFlight.set(true)
        return try {
            withTimeoutOrNullSafe(8_000L) {
                suspendCancellableCoroutine { cont ->
                    val utteranceId = "utt_${UUID.randomUUID()}"
                    tts.setSpeechRate(speechRate.coerceIn(0.5f, 2.0f))
                    tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(destFile.exists() && destFile.length() > WavAudioUtils.WAV_HEADER_SIZE)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(id: String?) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(false)
                            }
                        }

                        override fun onError(id: String?, errorCode: Int) {
                            if (id == utteranceId && cont.isActive) {
                                cont.resume(false)
                            }
                        }
                    })

                    val params = Bundle().apply {
                        putString(EXTRA_ORIGIN_PACKAGE, context.packageName)
                    }
                    val res = tts.synthesizeToFile(text, params, destFile, utteranceId)
                    if (res != TextToSpeech.SUCCESS && cont.isActive) {
                        cont.resume(false)
                    }
                }
            } ?: false
        } finally {
            isExternalFallbackInFlight.set(false)
        }
    }

    private suspend fun <T> withTimeoutOrNullSafe(timeMillis: Long, block: suspend () -> T): T? {
        return try {
            withTimeout(timeMillis) { block() }
        } catch (_: Exception) {
            null
        }
    }

    private fun checkAndRecordRateLimit(rpmLimit: Int): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - 60_000L
        while (true) {
            val head = requestTimestampsMs.peekFirst() ?: break
            if (head < windowStart) {
                requestTimestampsMs.pollFirst()
            } else {
                break
            }
        }
        if (requestTimestampsMs.size >= rpmLimit) {
            return false
        }
        requestTimestampsMs.addLast(now)
        return true
    }

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun enqueueTicket(ticket: QueueTicket) {
        _queueTickets.value = (listOf(ticket) + _queueTickets.value).take(25)
    }

    private fun updateTicket(
        ticketId: String,
        status: QueueTicketStatus,
        provider: String,
        attempt: Int = 1,
        latencyMs: Long = 0L,
        messageTh: String = ""
    ) {
        _queueTickets.value = _queueTickets.value.map {
            if (it.id == ticketId) {
                it.copy(
                    status = status,
                    providerUsed = provider,
                    attempt = attempt,
                    latencyMs = latencyMs,
                    messageTh = messageTh
                )
            } else {
                it
            }
        }
    }

    suspend fun logDiagnostic(
        level: String,
        category: String,
        provider: String,
        errorCode: String? = null,
        message: String,
        isQuotaError: Boolean = false,
        isNetworkError: Boolean = false,
        fallbackTriggered: Boolean = false
    ) {
        dao.insertDiagnosticLog(
            DiagnosticLogEntity(
                level = level,
                category = category,
                provider = provider,
                errorCode = errorCode,
                message = message,
                isQuotaError = isQuotaError,
                isNetworkError = isNetworkError,
                fallbackTriggered = fallbackTriggered
            )
        )
    }

    suspend fun clearAudioCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { runCatching { it.delete() } }
        dao.clearAllCachedAudio()
        logDiagnostic(
            level = "INFO",
            category = "CACHE",
            provider = "LOCAL_CACHE",
            message = "ล้างไฟล์แคชเสียง WAV ทั้งหมดเรียบร้อยแล้ว"
        )
    }

    suspend fun clearDiagnostics() = withContext(Dispatchers.IO) {
        dao.clearDiagnosticLogs()
    }

    suspend fun addPronunciationRule(pattern: String, replacement: String, note: String) {
        if (pattern.isBlank() || replacement.isBlank()) return
        dao.insertPronunciationRule(
            com.example.data.local.PronunciationRuleEntity(
                thaiPattern = pattern.trim(),
                spokenReplacement = replacement.trim(),
                note = note.trim().ifEmpty { "กำหนดโดยผู้ใช้" },
                enabled = true
            )
        )
    }

    suspend fun deletePronunciationRule(id: Long) {
        dao.deletePronunciationRule(id)
    }

    suspend fun saveVoiceProfile(profileName: String, config: SynthesisConfig) {
        if (profileName.isBlank()) return
        if (dao.countVoiceProfiles() == 0) {
            TtsDatabase.seedDefaultVoiceProfiles(dao)
        }
        dao.insertVoiceProfile(
            VoiceProfileEntity(
                profileName = profileName.trim(),
                routingPolicyId = config.routingPolicy.id,
                modelId = config.model.modelId,
                voiceId = config.voice.voiceId,
                responseStyleId = config.responseStyle.id,
                voiceStyleId = config.style.id,
                speechRate = config.speechRate,
                pitch = config.pitch,
                volume = config.volume,
                emotionalIntensity = config.emotionalIntensity,
                expressiveness = config.expressiveness,
                naturalPauses = config.naturalPauses,
                pronunciationClarity = config.pronunciationClarity,
                voiceWarmth = config.voiceWarmth,
                formality = config.formality,
                speechStability = config.speechStability,
                customStyleInstruction = config.customStyleInstruction,
                preferredLanguage = config.preferredLanguage
            )
        )
    }

    suspend fun deleteVoiceProfile(id: Long) {
        dao.deleteVoiceProfile(id)
    }

    companion object {
        const val EXTRA_ORIGIN_PACKAGE = "com.siamvoice.tts.ORIGIN_PACKAGE"

        @Volatile
        private var INSTANCE: HybridTtsRepository? = null

        /**
         * Pure helper for Priority 1 verification: filters a list of discovered TTS engine package names
         * so that SiamVoice's own package name (`selfPackageName`), namespace (`com.example`), or any
         * package containing `siamvoice` is strictly excluded from fallback candidates.
         */
        fun filterOutSelfTtsEngines(
            candidateEnginePackages: List<String>,
            selfPackageName: String
        ): List<String> {
            return candidateEnginePackages
                .map { it.trim() }
                .filter { pkg ->
                    pkg.isNotEmpty() &&
                        !pkg.equals(selfPackageName, ignoreCase = true) &&
                        !pkg.equals("com.example", ignoreCase = true) &&
                        !pkg.contains("siamvoice", ignoreCase = true)
                }
                .distinct()
        }

        fun getInstance(context: Context): HybridTtsRepository {
            return INSTANCE ?: synchronized(this) {
                val db = TtsDatabase.getInstance(context.applicationContext)
                val repo = HybridTtsRepository(context.applicationContext, db.ttsDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
