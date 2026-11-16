package com.example.tts

enum class GeminiTtsModel(
    val modelId: String,
    val fallbackPreviewId: String,
    val displayName: String,
    val badgeTh: String,
    val descriptionTh: String
) {
    FLASH_3_8(
        modelId = "gemini-3.8-flash-tts",
        fallbackPreviewId = "gemini-3.1-flash-tts-preview",
        displayName = "Gemini 3.8 Flash TTS",
        badgeTh = "คุณภาพสูงสุด (Studio Quality)",
        descriptionTh = "เน้นความคมชัด วรรณยุกต์ไทยแม่นยำ และโทนเสียงมีมิติเหมือนมนุษย์"
    ),
    FLASH_LITE_3_8(
        modelId = "gemini-3.8-flash-lite-tts",
        fallbackPreviewId = "gemini-3.1-flash-tts-preview",
        displayName = "Gemini 3.8 Flash-Lite TTS",
        badgeTh = "ความหน่วงต่ำ (Ultra-Low Latency)",
        descriptionTh = "สังเคราะห์ไว ประหยัดโควตา เหมาะสำหรับคำสั่งสั้นและการนำทาง"
    )
}

/**
 * Gender / persona classification for Gemini Cloud TTS voice profiles.
 */
enum class VoiceProfileCategory(
    val id: String,
    val labelTh: String,
    val labelEn: String,
    val badgeTh: String
) {
    ALL("all", "ทั้งหมด (All)", "All", "ทุกโทนเสียง"),
    FEMALE("female", "ผู้หญิง (Female)", "Female", "เสียงผู้หญิง"),
    MALE("male", "ผู้ชาย (Male)", "Male", "เสียงผู้ชาย"),
    NEUTRAL("neutral", "เป็นกลาง (Neutral)", "Neutral", "โทนเป็นกลาง")
}

enum class GeminiVoice(
    val voiceId: String,
    val thaiTitle: String,
    val genderToneTh: String,
    val sampleDescriptionTh: String,
    val category: VoiceProfileCategory,
    val recommendedParticle: PolitenessParticle
) {
    KORE(
        voiceId = "Kore",
        thaiTitle = "Kore (คอร์)",
        genderToneTh = "หญิง · นุ่มนวลชัดเจน",
        sampleDescriptionTh = "เหมาะสำหรับผู้ช่วยอัจฉริยะและระบบนำทาง",
        category = VoiceProfileCategory.FEMALE,
        recommendedParticle = PolitenessParticle.KHA
    ),
    AOEDE(
        voiceId = "Aoede",
        thaiTitle = "Aoede (เออีดี)",
        genderToneTh = "หญิง · อบอุ่นมีชีวิตชีวา",
        sampleDescriptionTh = "เหมาะสำหรับอ่านบทความและเล่านิทาน",
        category = VoiceProfileCategory.FEMALE,
        recommendedParticle = PolitenessParticle.KHA
    ),
    LEDA(
        voiceId = "Leda",
        thaiTitle = "Leda (ลีดา)",
        genderToneTh = "หญิง · สุภาพทางการ",
        sampleDescriptionTh = "เหมาะสำหรับประกาศเสียงตามสายและข่าวสาร",
        category = VoiceProfileCategory.FEMALE,
        recommendedParticle = PolitenessParticle.KHA
    ),
    ZEPHYR(
        voiceId = "Zephyr",
        thaiTitle = "Zephyr (เซเฟอร์)",
        genderToneTh = "เป็นกลาง · สดใสรวดเร็ว",
        sampleDescriptionTh = "โทนเสียงกลางฟังสบาย เหมาะสำหรับผู้ช่วยดิจิทัลและแจ้งเตือนสั้นๆ",
        category = VoiceProfileCategory.NEUTRAL,
        recommendedParticle = PolitenessParticle.FRIENDLY_NEUTRAL
    ),
    PUCK(
        voiceId = "Puck",
        thaiTitle = "Puck (พัค)",
        genderToneTh = "ชาย · กระฉับกระเฉง",
        sampleDescriptionTh = "เหมาะสำหรับบทสนทนาและการโต้ตอบเร็ว",
        category = VoiceProfileCategory.MALE,
        recommendedParticle = PolitenessParticle.KRUB
    ),
    CHARON(
        voiceId = "Charon",
        thaiTitle = "Charon (คารอน)",
        genderToneTh = "ชาย · สุขุมทุ้มลึก",
        sampleDescriptionTh = "เหมาะสำหรับสารคดีและงานทางการ",
        category = VoiceProfileCategory.MALE,
        recommendedParticle = PolitenessParticle.KRUB
    ),
    FENRIR(
        voiceId = "Fenrir",
        thaiTitle = "Fenrir (เฟนริร์)",
        genderToneTh = "ชาย · หนักแน่นชัดเจน",
        sampleDescriptionTh = "เหมาะสำหรับคำสั่งนำทางในรถยนต์",
        category = VoiceProfileCategory.MALE,
        recommendedParticle = PolitenessParticle.KRUB
    ),
    ORUS(
        voiceId = "Orus",
        thaiTitle = "Orus (โอรัส)",
        genderToneTh = "เป็นกลาง · สุภาพเป็นกันเอง",
        sampleDescriptionTh = "โทนเสียงสมดุลเป็นกลาง เหมาะสำหรับอ่านข้อความทั่วไปและข้อมูลไร้เพศสภาพ",
        category = VoiceProfileCategory.NEUTRAL,
        recommendedParticle = PolitenessParticle.FRIENDLY_NEUTRAL
    )
}

/**
 * Curated Gemini Cloud TTS voice profiles covering Female, Male, and Neutral personas,
 * pairing prebuilt Gemini voices with appropriate Thai politeness particles, speaking styles,
 * and acoustic tuning.
 */
enum class GeminiVoiceProfile(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val category: VoiceProfileCategory,
    val voice: GeminiVoice,
    val recommendedStyle: VoiceStylePreset,
    val recommendedParticle: PolitenessParticle,
    val defaultPitch: Float,
    val defaultWarmth: Int,
    val descriptionTh: String
) {
    FEMALE_KORE_ASSISTANT(
        id = "female_kore_assistant",
        titleTh = "ผู้หญิง · นุ่มนวลชัดเจน (Kore)",
        titleEn = "Female · Clear Assistant (Kore)",
        category = VoiceProfileCategory.FEMALE,
        voice = GeminiVoice.KORE,
        recommendedStyle = VoiceStylePreset.NATURAL,
        recommendedParticle = PolitenessParticle.KHA,
        defaultPitch = 1.0f,
        defaultWarmth = 68,
        descriptionTh = "โทนเสียงผู้หญิงชัดเจน ฟังสบาย ลงท้ายด้วย 'ค่ะ/นะคะ' เหมาะกับผู้ช่วยประจำวันและนำทาง"
    ),
    FEMALE_AOEDE_WARM(
        id = "female_aoede_warm",
        titleTh = "ผู้หญิง · อบอุ่นมีชีวิตชีวา (Aoede)",
        titleEn = "Female · Warm & Expressive (Aoede)",
        category = VoiceProfileCategory.FEMALE,
        voice = GeminiVoice.AOEDE,
        recommendedStyle = VoiceStylePreset.FRIENDLY,
        recommendedParticle = PolitenessParticle.KHA,
        defaultPitch = 1.02f,
        defaultWarmth = 82,
        descriptionTh = "โทนเสียงผู้หญิงนุ่มละมุน มีมิติอารมณ์สูง เหมาะกับการสนทนาที่เป็นกันเองและเล่าเรื่อง"
    ),
    FEMALE_LEDA_EXECUTIVE(
        id = "female_leda_executive",
        titleTh = "ผู้หญิง · สุภาพทางการ (Leda)",
        titleEn = "Female · Professional & Formal (Leda)",
        category = VoiceProfileCategory.FEMALE,
        voice = GeminiVoice.LEDA,
        recommendedStyle = VoiceStylePreset.PROFESSIONAL,
        recommendedParticle = PolitenessParticle.KHA,
        defaultPitch = 0.98f,
        defaultWarmth = 58,
        descriptionTh = "โทนเสียงผู้หญิงสุภาพ น่าเชื่อถือ ออกเสียงควบกล้ำชัดเจน เหมาะกับข่าวสารและงานองค์กร"
    ),
    MALE_PUCK_DYNAMIC(
        id = "male_puck_dynamic",
        titleTh = "ผู้ชาย · กระฉับกระเฉงชัดเจน (Puck)",
        titleEn = "Male · Dynamic & Conversational (Puck)",
        category = VoiceProfileCategory.MALE,
        voice = GeminiVoice.PUCK,
        recommendedStyle = VoiceStylePreset.NATURAL,
        recommendedParticle = PolitenessParticle.KRUB,
        defaultPitch = 1.0f,
        defaultWarmth = 62,
        descriptionTh = "โทนเสียงผู้ชายคล่องแคล่ว ลงท้ายด้วย 'ครับ/นะครับ' เหมาะกับการโต้ตอบรวดเร็วและผู้ช่วยส่วนตัว"
    ),
    MALE_CHARON_DEEP(
        id = "male_charon_deep",
        titleTh = "ผู้ชาย · ทุ้มลึกสุขุมน่าเชื่อถือ (Charon)",
        titleEn = "Male · Deep & Authoritative (Charon)",
        category = VoiceProfileCategory.MALE,
        voice = GeminiVoice.CHARON,
        recommendedStyle = VoiceStylePreset.PROFESSIONAL,
        recommendedParticle = PolitenessParticle.KRUB,
        defaultPitch = 0.94f,
        defaultWarmth = 66,
        descriptionTh = "โทนเสียงผู้ชายทุ้มนุ่ม สุขุม มีน้ำหนัก เหมาะสำหรับอ่านบทความยาว สารคดี และรายงานทางการ"
    ),
    MALE_FENRIR_NAVIGATION(
        id = "male_fenrir_navigation",
        titleTh = "ผู้ชาย · หนักแน่นคมชัดนำทาง (Fenrir)",
        titleEn = "Male · Crisp Navigation (Fenrir)",
        category = VoiceProfileCategory.MALE,
        voice = GeminiVoice.FENRIR,
        recommendedStyle = VoiceStylePreset.NAVIGATION,
        recommendedParticle = PolitenessParticle.KRUB,
        defaultPitch = 0.98f,
        defaultWarmth = 50,
        descriptionTh = "โทนเสียงผู้ชายคมชัด ตัดเสียงรบกวนในห้องโดยสารได้ดี เหมาะกับคำสั่งนำทางขณะขับขี่"
    ),
    NEUTRAL_ZEPHYR_CLEAR(
        id = "neutral_zephyr_clear",
        titleTh = "เป็นกลาง · สดใสไร้เพศสภาพ (Zephyr)",
        titleEn = "Neutral · Bright & Balanced (Zephyr)",
        category = VoiceProfileCategory.NEUTRAL,
        voice = GeminiVoice.ZEPHYR,
        recommendedStyle = VoiceStylePreset.NATURAL,
        recommendedParticle = PolitenessParticle.FRIENDLY_NEUTRAL,
        defaultPitch = 1.0f,
        defaultWarmth = 65,
        descriptionTh = "โปรไฟล์เสียงเป็นกลาง ไม่เน้นเพศสภาพ ใช้คำลงท้ายแบบกลาง/กันเอง เหมาะกับอินเทอร์เฟซยุคใหม่"
    ),
    NEUTRAL_ORUS_CALM(
        id = "neutral_orus_calm",
        titleTh = "เป็นกลาง · ราบเรียบสบายหู (Orus)",
        titleEn = "Neutral · Calm & Ambient (Orus)",
        category = VoiceProfileCategory.NEUTRAL,
        voice = GeminiVoice.ORUS,
        recommendedStyle = VoiceStylePreset.CALM,
        recommendedParticle = PolitenessParticle.FRIENDLY_NEUTRAL,
        defaultPitch = 1.0f,
        defaultWarmth = 72,
        descriptionTh = "โทนเสียงกลางนุ่มสบายหู จังหวะสม่ำเสมอ ไม่ระบุครับ/ค่ะ เหมาะกับการอ่านข้อมูลต่อเนื่อง"
    );

    companion object {
        fun forVoice(voice: GeminiVoice): GeminiVoiceProfile =
            entries.firstOrNull { it.voice == voice } ?: FEMALE_KORE_ASSISTANT

        fun filteredBy(category: VoiceProfileCategory): List<GeminiVoiceProfile> =
            if (category == VoiceProfileCategory.ALL) {
                entries
            } else {
                entries.filter { it.category == category }
            }
    }
}


/**
 * 9 Selectable Voice Style Presets with tuned default acoustic parameters.
 * Kept aliased via `typealias SpeakingStyle = VoiceStylePreset` for full compatibility.
 */
enum class VoiceStylePreset(
    val id: String,
    val titleEn: String,
    val titleTh: String,
    val subtitleTh: String,
    val promptDirectiveTh: String,
    val defaultSpeed: Float,
    val defaultPitch: Float,
    val defaultEmotionalIntensity: Int,
    val defaultExpressiveness: Int,
    val defaultNaturalPauses: Int,
    val defaultClarity: Int,
    val defaultWarmth: Int,
    val defaultFormality: Int,
    val defaultStability: Int
) {
    NATURAL(
        id = "NATURAL",
        titleEn = "Natural",
        titleTh = "ธรรมชาติ (Natural)",
        subtitleTh = "เสียงพูดเป็นธรรมชาติ ลื่นไหล เหมือนมนุษย์สนทนาจริง",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงเป็นธรรมชาติ ลื่นไหลเหมือนมนุษย์สนทนาจริง วรรณยุกต์แม่นยำ",
        defaultSpeed = 1.00f,
        defaultPitch = 1.00f,
        defaultEmotionalIntensity = 55,
        defaultExpressiveness = 65,
        defaultNaturalPauses = 55,
        defaultClarity = 75,
        defaultWarmth = 65,
        defaultFormality = 50,
        defaultStability = 80
    ),
    PROFESSIONAL(
        id = "PROFESSIONAL",
        titleEn = "Professional",
        titleTh = "มืออาชีพ (Professional)",
        subtitleTh = "ชัดเจน สุภาพ น่าเชื่อถือ และมั่นใจ",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงมืออาชีพ ชัดถ้อยชัดคำ สุภาพ น่าเชื่อถือ และมั่นใจ",
        defaultSpeed = 0.98f,
        defaultPitch = 0.98f,
        defaultEmotionalIntensity = 40,
        defaultExpressiveness = 50,
        defaultNaturalPauses = 60,
        defaultClarity = 90,
        defaultWarmth = 55,
        defaultFormality = 88,
        defaultStability = 92
    ),
    FRIENDLY(
        id = "FRIENDLY",
        titleEn = "Friendly",
        titleTh = "เป็นกันเอง (Friendly)",
        subtitleTh = "อบอุ่น เข้าถึงง่าย และมีชีวิตชีวา",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงอบอุ่น เป็นกันเอง ยิ้มแย้ม และเข้าถึงง่าย",
        defaultSpeed = 1.02f,
        defaultPitch = 1.04f,
        defaultEmotionalIntensity = 70,
        defaultExpressiveness = 78,
        defaultNaturalPauses = 50,
        defaultClarity = 75,
        defaultWarmth = 85,
        defaultFormality = 35,
        defaultStability = 75
    ),
    CALM(
        id = "CALM",
        titleEn = "Calm",
        titleTh = "สงบผ่อนคลาย (Calm)",
        subtitleTh = "นุ่มนวล ผ่อนคลาย จังหวะราบเรียบสบายหู",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงนุ่มนวล สงบ ผ่อนคลาย จังหวะช้าลงเล็กน้อยอย่างราบรื่น",
        defaultSpeed = 0.92f,
        defaultPitch = 0.96f,
        defaultEmotionalIntensity = 35,
        defaultExpressiveness = 45,
        defaultNaturalPauses = 75,
        defaultClarity = 78,
        defaultWarmth = 82,
        defaultFormality = 60,
        defaultStability = 90
    ),
    ENERGETIC(
        id = "ENERGETIC",
        titleEn = "Energetic",
        titleTh = "กระตือรือร้น (Energetic)",
        subtitleTh = "สดใส มีพลัง ชวนติดตาม",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงสดใส กระตือรือร้น มีพลัง และตื่นตัว",
        defaultSpeed = 1.10f,
        defaultPitch = 1.06f,
        defaultEmotionalIntensity = 85,
        defaultExpressiveness = 88,
        defaultNaturalPauses = 40,
        defaultClarity = 80,
        defaultWarmth = 60,
        defaultFormality = 40,
        defaultStability = 70
    ),
    STORYTELLING(
        id = "STORYTELLING",
        titleEn = "Storytelling",
        titleTh = "เล่านิทาน/บรรยาย (Storytelling)",
        subtitleTh = "ถ่ายทอดอารมณ์ลึกซึ้งพร้อมจังหวะเว้นวรรคธรรมชาติ",
        promptDirectiveTh = "พูดภาษาไทยสไตล์ผู้บรรยายเรื่องราว ถ่ายทอดอารมณ์มีมิติ และเว้นจังหวะหายใจอย่างเป็นธรรมชาติ",
        defaultSpeed = 0.95f,
        defaultPitch = 1.00f,
        defaultEmotionalIntensity = 80,
        defaultExpressiveness = 85,
        defaultNaturalPauses = 85,
        defaultClarity = 82,
        defaultWarmth = 78,
        defaultFormality = 55,
        defaultStability = 72
    ),
    NAVIGATION(
        id = "NAVIGATION",
        titleEn = "Navigation",
        titleTh = "นำทาง (Navigation)",
        subtitleTh = "กระชับ ชัดเจน รวดเร็ว สำหรับการขับขี่",
        promptDirectiveTh = "พูดคำสั่งนำทางภาษาไทยด้วยน้ำเสียงกระชับ ชัดเจน ตรงประเด็น ฟังเข้าใจทันทีขณะขับรถ",
        defaultSpeed = 1.08f,
        defaultPitch = 1.00f,
        defaultEmotionalIntensity = 30,
        defaultExpressiveness = 40,
        defaultNaturalPauses = 35,
        defaultClarity = 95,
        defaultWarmth = 50,
        defaultFormality = 70,
        defaultStability = 95
    ),
    LUXURY(
        id = "LUXURY",
        titleEn = "Luxury",
        titleTh = "หรูหราพรีเมียม (Luxury)",
        subtitleTh = "สง่างาม นุ่มลึก สุภาพระดับผู้ช่วยส่วนตัวโรงแรมห้าดาว",
        promptDirectiveTh = "พูดภาษาไทยด้วยน้ำเสียงสง่างาม หรูหรา นุ่มลึก สุภาพประณีตระดับพรีเมียม",
        defaultSpeed = 0.94f,
        defaultPitch = 0.95f,
        defaultEmotionalIntensity = 50,
        defaultExpressiveness = 60,
        defaultNaturalPauses = 70,
        defaultClarity = 88,
        defaultWarmth = 90,
        defaultFormality = 92,
        defaultStability = 88
    ),
    CUSTOM(
        id = "CUSTOM",
        titleEn = "Custom",
        titleTh = "กำหนดเอง (Custom)",
        subtitleTh = "ปรับแต่งบุคลิกเสียงและพารามิเตอร์ทั้งหมดได้อย่างอิสระ",
        promptDirectiveTh = "พูดภาษาไทยตามพารามิเตอร์และคำสั่งสไตล์ที่ผู้ใช้กำหนดอย่างแม่นยำ",
        defaultSpeed = 1.00f,
        defaultPitch = 1.00f,
        defaultEmotionalIntensity = 60,
        defaultExpressiveness = 65,
        defaultNaturalPauses = 55,
        defaultClarity = 80,
        defaultWarmth = 65,
        defaultFormality = 55,
        defaultStability = 80
    );

    val promptPrefixTh: String
        get() = "$promptDirectiveTh: "
}

typealias SpeakingStyle = VoiceStylePreset

/**
 * 6 Independent Response Style Intelligence behaviors.
 * Users can combine any ResponseStyle with any VoiceStylePreset independently.
 */
enum class ResponseStyle(
    val id: String,
    val titleEn: String,
    val titleTh: String,
    val subtitleTh: String,
    val aiSystemInstructionTh: String
) {
    CONCISE(
        id = "CONCISE",
        titleEn = "Concise",
        titleTh = "กระชับ (Concise)",
        subtitleTh = "สั้น ตรงประเด็น ไม่เยิ่นเย้อ",
        aiSystemInstructionTh = "ตอบสั้น กระชับ ตรงประเด็นที่สุด ไม่เกิน 1-2 ประโยค"
    ),
    BALANCED(
        id = "BALANCED",
        titleEn = "Balanced",
        titleTh = "สมดุล (Balanced)",
        subtitleTh = "เป็นธรรมชาติและให้ข้อมูลครบถ้วนพอดี",
        aiSystemInstructionTh = "ตอบอย่างสมดุล เป็นธรรมชาติ มีข้อมูลชัดเจนและอ่านเข้าใจง่าย"
    ),
    DETAILED(
        id = "DETAILED",
        titleEn = "Detailed",
        titleTh = "ละเอียด (Detailed)",
        subtitleTh = "อธิบายครบถ้วนทุกแง่มุมอย่างชัดเจน",
        aiSystemInstructionTh = "อธิบายอย่างละเอียด ครบถ้วน มีตัวอย่างและลำดับขั้นตอนชัดเจน"
    ),
    CONVERSATIONAL(
        id = "CONVERSATIONAL",
        titleEn = "Conversational",
        titleTh = "สนทนา (Conversational)",
        subtitleTh = "โต้ตอบผ่อนคลายเหมือนคุยกับมนุษย์จริง",
        aiSystemInstructionTh = "ตอบด้วยภาษาพูดที่เป็นธรรมชาติ ผ่อนคลาย เหมือนมนุษย์สนทนากันอย่างเป็นมิตร"
    ),
    ASSISTANT(
        id = "ASSISTANT",
        titleEn = "Assistant",
        titleTh = "ผู้ช่วยส่วนตัว (Assistant)",
        subtitleTh = "สุภาพ กระตือรือร้น และแนะนำขั้นตอนถัดไป",
        aiSystemInstructionTh = "ตอบในฐานะผู้ช่วยอัจฉริยะที่สุภาพ รอบคอบ และพร้อมช่วยเหลือเชิงรุก"
    ),
    NAVIGATION(
        id = "NAVIGATION",
        titleEn = "Navigation",
        titleTh = "นำทาง (Navigation)",
        subtitleTh = "คำสั่งสั้น ชัดเจน ไม่รบกวนสมาธิขณะขับขี่",
        aiSystemInstructionTh = "ตอบเป็นคำสั่งนำทางสั้นๆ ชัดเจน ไม่รบกวนสมาธิผู้ขับขี่ ระบุระยะทางและทิศทางตรงไปตรงมา"
    )
}

enum class RoutingPolicy(
    val id: String,
    val titleTh: String,
    val subtitleTh: String
) {
    AUTO_HYBRID(
        id = "AUTO_HYBRID",
        titleTh = "ไฮบริดอัตโนมัติ (Auto Hybrid)",
        subtitleTh = "ใช้ Gemini Cloud เป็นหลัก และสลับไปใช้เสียง Local ทันทีเมื่อโควตาถึงเกณฑ์หรือไม่มีเน็ต"
    ),
    SHORT_PHRASE_LOCAL_FIRST(
        id = "SHORT_PHRASE_LOCAL_FIRST",
        titleTh = "เน้นความเร็ววลีสั้น (Short-Phrase Fast)",
        subtitleTh = "ข้อความสั้น ≤ 35 ตัวอักษรใช้แคช/Local ทันที ส่วนข้อความยาวใช้ Gemini Cloud"
    ),
    CLOUD_ONLY(
        id = "CLOUD_ONLY",
        titleTh = "เฉพาะ Gemini Cloud (Cloud Only)",
        subtitleTh = "บังคับสังเคราะห์ผ่าน Gemini 3.8 TTS เสมอ (หากโควตาเต็มจะแจ้งเตือนโดยไม่ข้ามโควตา)"
    ),
    OFFLINE_ONLY(
        id = "OFFLINE_ONLY",
        titleTh = "ออฟไลน์ในเครื่อง 100% (Offline Local)",
        subtitleTh = "สังเคราะห์ในอุปกรณ์โดยไม่เรียกใช้เครือข่ายหรือโควตา Cloud"
    )
}

/**
 * Explicitly classifies how each voice control is supported by the active TTS provider.
 * Never claims an unsupported control is working natively.
 */
enum class ControlSupportMechanism(
    val badgeTh: String,
    val isSupported: Boolean
) {
    NATIVE_ENGINE("รองรับระดับ Engine (Native)", true),
    PROMPT_DIRECTIVE("สั่งการผ่าน Gemini Prompt", true),
    DSP_POST_PROCESS("ประมวลผลสัญญาณเสียง (DSP Audio)", true),
    UNSUPPORTED_DISABLED("ไม่รองรับในโหมดออฟไลน์", false)
}

data class ProviderCapabilityProfile(
    val providerNameTh: String,
    val speedSupport: ControlSupportMechanism,
    val pitchSupport: ControlSupportMechanism,
    val volumeSupport: ControlSupportMechanism,
    val emotionalIntensitySupport: ControlSupportMechanism,
    val expressivenessSupport: ControlSupportMechanism,
    val naturalPausesSupport: ControlSupportMechanism,
    val pronunciationClaritySupport: ControlSupportMechanism,
    val voiceWarmthSupport: ControlSupportMechanism,
    val formalitySupport: ControlSupportMechanism,
    val speechStabilitySupport: ControlSupportMechanism
) {
    companion object {
        fun forRoutingPolicy(policy: RoutingPolicy): ProviderCapabilityProfile {
            return if (policy == RoutingPolicy.OFFLINE_ONLY) {
                ProviderCapabilityProfile(
                    providerNameTh = "Local Offline Neural TTS (F5-TTS-THAI / VITS ONNX + DSP)",
                    speedSupport = ControlSupportMechanism.NATIVE_ENGINE,
                    pitchSupport = ControlSupportMechanism.NATIVE_ENGINE,
                    volumeSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    emotionalIntensitySupport = ControlSupportMechanism.UNSUPPORTED_DISABLED,
                    expressivenessSupport = ControlSupportMechanism.UNSUPPORTED_DISABLED,
                    naturalPausesSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    pronunciationClaritySupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    voiceWarmthSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    formalitySupport = ControlSupportMechanism.UNSUPPORTED_DISABLED,
                    speechStabilitySupport = ControlSupportMechanism.UNSUPPORTED_DISABLED
                )
            } else {
                ProviderCapabilityProfile(
                    providerNameTh = "Gemini 3.8 Cloud TTS + DSP Post-Processor",
                    speedSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    pitchSupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    volumeSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    emotionalIntensitySupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    expressivenessSupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    naturalPausesSupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    pronunciationClaritySupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    voiceWarmthSupport = ControlSupportMechanism.DSP_POST_PROCESS,
                    formalitySupport = ControlSupportMechanism.PROMPT_DIRECTIVE,
                    speechStabilitySupport = ControlSupportMechanism.NATIVE_ENGINE
                )
            }
        }
    }
}

/**
 * Two-Level Speech & Voice Adaptation System:
 * - Level 1: Directs the TTS voice/emotion to sound warmer, friendlier, or styled while keeping the original wording intact.
 * - Level 2: Both adapts the vocal tone/emotion AND rewrites/adapts the phrasing to sound natural and conversational
 *   (e.g., transforming "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ").
 */
enum class SpeechAdaptationLevel(
    val id: String,
    val levelNumber: Int,
    val titleTh: String,
    val shortBadgeTh: String,
    val subtitleTh: String,
    val exampleBeforeAfterTh: String
) {
    LEVEL_1_TONE_AND_EMOTION(
        id = "LEVEL_1_TONE_AND_EMOTION",
        levelNumber = 1,
        titleTh = "ระดับที่ 1: ดัดน้ำเสียง/อารมณ์ให้ดูเป็นกันเองขึ้น",
        shortBadgeTh = "ระดับ 1: ดัดน้ำเสียง (คงคำเดิม)",
        subtitleTh = "ปรับโทนเสียง จังหวะ และอารมณ์ให้เป็นกันเอง นุ่มนวล หรือเร้าใจตามสไตล์ โดยคงคำพูดต้นฉบับไว้ 100%",
        exampleBeforeAfterTh = "\"อีกสามร้อยเมตร เลี้ยวซ้าย\" → ออกเสียงด้วยน้ำเสียงอบอุ่นเป็นกันเองโดยไม่เติมคำใหม่"
    ),
    LEVEL_2_REWRITE_SPEECH(
        id = "LEVEL_2_REWRITE_SPEECH",
        levelNumber = 2,
        titleTh = "ระดับที่ 2: สั่งดัดแปลงคำพูดใหม่ให้เป็นกันเอง",
        shortBadgeTh = "ระดับ 2: ดัดน้ำเสียง + เกลาคำพูดใหม่",
        subtitleTh = "ดัดแปลงรูปประโยคและเติมคำลงท้ายที่เป็นธรรมชาติเข้ากับสไตล์เสียงโดยรักษาใจความสำคัญเดิมครบถ้วน",
        exampleBeforeAfterTh = "\"อีกสามร้อยเมตร เลี้ยวซ้าย\" → \"อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ\""
    )
}

/**
 * Thai conversational politeness particle used when Level 2 (Phrasing Rewrite) is active.
 */
enum class PolitenessParticle(
    val id: String,
    val labelTh: String,
    val actionEndingTh: String,
    val statementEndingTh: String
) {
    KRUB(
        id = "KRUB",
        labelTh = "ครับ",
        actionEndingTh = "ได้เลยนะครับ",
        statementEndingTh = "นะครับ"
    ),
    NA_KRUB(
        id = "NA_KRUB",
        labelTh = "นะครับ",
        actionEndingTh = "นะครับ",
        statementEndingTh = "นะครับ"
    ),
    KRUB_PHOM(
        id = "KRUB_PHOM",
        labelTh = "ครับผม",
        actionEndingTh = "ครับผม",
        statementEndingTh = "ครับผม"
    ),
    AUTO_NATURAL(
        id = "AUTO_NATURAL",
        labelTh = "Auto Natural (อัตโนมัติ)",
        actionEndingTh = "นะครับ",
        statementEndingTh = "นะครับ"
    ),
    NO_ENDING(
        id = "NO_ENDING",
        labelTh = "No Ending (ไม่ใส่คำลงท้าย)",
        actionEndingTh = "",
        statementEndingTh = ""
    ),
    KHA(
        id = "KHA",
        labelTh = "ค่ะ / ได้เลยนะคะ",
        actionEndingTh = "ได้เลยนะคะ",
        statementEndingTh = "นะคะ"
    ),
    FRIENDLY_NEUTRAL(
        id = "FRIENDLY_NEUTRAL",
        labelTh = "กันเองทั่วไป (ได้เลย / นะ)",
        actionEndingTh = "ได้เลย",
        statementEndingTh = "นะ"
    )
}

/**
 * Redesigned Thai Voice Personality presets per user specifications:
 * - FRIENDLY_COMPANION: Puck (ผู้ชายเป็นมิตร กระตือรือร้น เหมือนเพื่อนสนิทนั่งข้างคนขับ)
 * - PERSONAL_ASSISTANT: Orus (นุ่มทุ้ม สุภาพ ใจเย็น อบอุ่น มั่นใจ เหมือนเลขาส่วนตัว)
 * - NATURAL: Kore (ธรรมชาติ ลื่นไหล วรรณยุกต์แม่นยำ)
 * - CUSTOM: คำสั่งสไตล์ที่ผู้ใช้กำหนดเอง
 */
enum class ThaiVoicePersonality(
    val id: String,
    val titleTh: String,
    val titleEn: String,
    val subtitleTh: String,
    val recommendedVoice: GeminiVoice,
    val thaiVoiceStyleInstruction: String,
    val defaultSpeed: Float,
    val defaultExpressiveness: Int
) {
    FRIENDLY_COMPANION(
        id = "FRIENDLY_COMPANION",
        titleTh = "เพื่อนร่วมทาง (Friendly Companion)",
        titleEn = "Friendly Companion",
        subtitleTh = "อารมณ์ดี กระตือรือร้น เป็นธรรมชาติ เหมือนเพื่อนสนิทนั่งข้างคนขับ",
        recommendedVoice = GeminiVoice.PUCK,
        thaiVoiceStyleInstruction = "พูดด้วยน้ำเสียงอารมณ์ดี กระตือรือร้น เป็นธรรมชาติ เหมือนเพื่อนสนิทนั่งข้างคนขับ พูดอย่างอบอุ่น มีชีวิตชีวา และฟังสบาย ไม่พูดแข็งหรือเป็นหุ่นยนต์",
        defaultSpeed = 1.05f,
        defaultExpressiveness = 80
    ),
    PERSONAL_ASSISTANT(
        id = "PERSONAL_ASSISTANT",
        titleTh = "ผู้ช่วยส่วนตัว (Personal Assistant)",
        titleEn = "Personal Assistant",
        subtitleTh = "นุ่มทุ้ม สุภาพ ใจเย็น อบอุ่นและมั่นใจ เหมือนเลขาส่วนตัวที่คอยช่วยเหลือ",
        recommendedVoice = GeminiVoice.ORUS,
        thaiVoiceStyleInstruction = "พูดด้วยน้ำเสียงนุ่มทุ้ม สุภาพ ใจเย็น อบอุ่นและมั่นใจ เหมือนเลขาส่วนตัวที่คอยช่วยเหลือ พูดชัดเจน จังหวะนุ่มนวล มีความเป็นธรรมชาติ",
        defaultSpeed = 0.98f,
        defaultExpressiveness = 60
    ),
    NATURAL(
        id = "NATURAL",
        titleTh = "ธรรมชาติ (Natural)",
        titleEn = "Natural",
        subtitleTh = "เสียงพูดเป็นธรรมชาติ ลื่นไหลเหมือนมนุษย์สนทนาจริง วรรณยุกต์แม่นยำ",
        recommendedVoice = GeminiVoice.KORE,
        thaiVoiceStyleInstruction = "พูดภาษาไทยด้วยน้ำเสียงเป็นธรรมชาติ ลื่นไหลเหมือนมนุษย์สนทนาจริง วรรณยุกต์แม่นยำ ฟังสบาย",
        defaultSpeed = 1.00f,
        defaultExpressiveness = 65
    ),
    CUSTOM(
        id = "CUSTOM",
        titleTh = "กำหนดเอง (Custom)",
        titleEn = "Custom",
        subtitleTh = "ปรับแต่งคำสั่งสไตล์เสียงและพารามิเตอร์ได้อย่างอิสระ",
        recommendedVoice = GeminiVoice.KORE,
        thaiVoiceStyleInstruction = "",
        defaultSpeed = 1.00f,
        defaultExpressiveness = 65
    );

    companion object {
        fun fromId(id: String?): ThaiVoicePersonality =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FRIENDLY_COMPANION
    }
}

/**
 * Thai Polite Sentence Ending System:
 * - Auto Natural (อัตโนมัติเป็นธรรมชาติ ตามความหมายและบริบทประโยค)
 * - ครับ
 * - นะครับ
 * - ครับผม
 * - No Ending (ไม่ใส่คำลงท้าย)
 */
enum class ThaiPoliteEnding(
    val id: String,
    val labelTh: String,
    val suffixText: String
) {
    AUTO_NATURAL(
        id = "AUTO_NATURAL",
        labelTh = "Auto Natural",
        suffixText = ""
    ),
    KRUB(
        id = "KRUB",
        labelTh = "ครับ",
        suffixText = "ครับ"
    ),
    NA_KRUB(
        id = "NA_KRUB",
        labelTh = "นะครับ",
        suffixText = "นะครับ"
    ),
    KRUB_PHOM(
        id = "KRUB_PHOM",
        labelTh = "ครับผม",
        suffixText = "ครับผม"
    ),
    NO_ENDING(
        id = "NO_ENDING",
        labelTh = "No Ending",
        suffixText = ""
    );

    companion object {
        fun fromId(id: String?): ThaiPoliteEnding =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO_NATURAL
    }
}

/**
 * Full voice personality, response style, and hybrid engine configuration.
 * Engine Priority: Gemini Flash TTS → Gemini Flash-Lite TTS → F5-TTS-THAI → External Android TTS.
 */
data class SynthesisConfig(
    val model: GeminiTtsModel = GeminiTtsModel.FLASH_3_8,
    val voice: GeminiVoice = GeminiVoice.KORE,
    val style: VoiceStylePreset = VoiceStylePreset.NATURAL,
    val responseStyle: ResponseStyle = ResponseStyle.CONVERSATIONAL,
    val adaptationLevel: SpeechAdaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
    val politenessParticle: PolitenessParticle = PolitenessParticle.KRUB,
    val thaiVoicePersonality: ThaiVoicePersonality = ThaiVoicePersonality.FRIENDLY_COMPANION,
    val thaiPoliteEnding: ThaiPoliteEnding = ThaiPoliteEnding.AUTO_NATURAL,
    val disableRewritingForNavigation: Boolean = false,
    val routingPolicy: RoutingPolicy = RoutingPolicy.AUTO_HYBRID,
    val speechRate: Float = 1.00f,
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
    val preferredLanguage: String = "th-TH",
    val dailyCloudQuotaLimit: Int = 40,
    val rpmLimit: Int = 10,
    val shortPhraseThresholdChars: Int = 35,
    val useArgonReasoning: Boolean = false,
    val cloudTimeoutMs: Long = DEFAULT_CLOUD_TIMEOUT_MS,
    val offlineVoiceProfile: OfflineVoiceProfile = OfflineVoiceProfile.F5_THAI_FEMALE_CLEAR,
    val f5TokenizerMode: F5TokenizerMode = F5TokenizerMode.V2_IPA_PHONEME,
    val f5NfeSteps: Int = F5SynthesisParams.DEFAULT_NFE_STEPS,
    val f5CfgStrength: Float = F5SynthesisParams.DEFAULT_CFG_STRENGTH
) {
    /**
     * Builds the rich Gemini 3.8 TTS acoustic style & personality directive from active controls.
     */
    fun buildGeminiStylePrompt(optimizedThaiText: String): String {
        val descriptors = mutableListOf<String>()

        val personalityDirective = when (thaiVoicePersonality) {
            ThaiVoicePersonality.CUSTOM -> customStyleInstruction.ifBlank { style.promptDirectiveTh }
            else -> thaiVoicePersonality.thaiVoiceStyleInstruction.ifBlank { style.promptDirectiveTh }
        }
        descriptors.add(personalityDirective)
        descriptors.add("รูปแบบการสื่อสารแบบ${responseStyle.titleTh}")

        if (adaptationLevel == SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION) {
            descriptors.add("ดัดน้ำเสียงและอารมณ์ให้ดูเป็นกันเองและมีชีวิตชีวาขึ้นตามบุคลิกเสียงโดยอ่านตามถ้อยคำที่กำหนด")
        } else {
            descriptors.add("ดัดน้ำเสียงและพูดด้วยถ้อยคำที่เป็นกันเองเป็นธรรมชาติเสมือนผู้ช่วยที่กำลังคุยกับผู้ฟังโดยตรง")
        }

        if (speechRate <= 0.88f) descriptors.add("จังหวะการพูดค่อนข้างช้าและชัด")
        else if (speechRate >= 1.12f) descriptors.add("จังหวะการพูดกระฉับกระเฉงรวดเร็ว")

        if (pitch <= 0.90f) descriptors.add("โทนเสียงทุ้มต่ำกว่าปกติเล็กน้อย")
        else if (pitch >= 1.10f) descriptors.add("โทนเสียงสูงสดใสขึ้นเล็กน้อย")

        if (emotionalIntensity >= 70) descriptors.add("ใส่อารมณ์ความรู้สึกลึกซึ้งชัดเจน ($emotionalIntensity%)")
        else if (emotionalIntensity <= 35) descriptors.add("คุมอารมณ์เสียงให้นิ่งและเป็นกลาง")

        if (expressiveness >= 70) descriptors.add("เน้นน้ำหนักคำและทำนองเสียงมีชีวิตชีวา ($expressiveness%)")
        else if (expressiveness <= 40) descriptors.add("น้ำเสียงเรียบสม่ำเสมอ")

        if (naturalPauses >= 70) descriptors.add("เว้นจังหวะหายใจระหว่างวลีอย่างเป็นธรรมชาติ")
        if (pronunciationClarity >= 80) descriptors.add("ออกเสียงพยัญชนะ ร ล คำควบกล้ำ และวรรณยุกต์ไทยชัดเจนพิเศษ")
        if (voiceWarmth >= 70) descriptors.add("เนื้อเสียงอบอุ่นนุ่มนวลสบายหู")

        if (formality >= 75) descriptors.add("ระดับภาษาสุภาพทางการ")
        else if (formality <= 35) descriptors.add("ระดับภาษาเป็นกันเองผ่อนคลาย")

        if (customStyleInstruction.isNotBlank()) {
            descriptors.add("คำสั่งเพิ่มเติม: ${customStyleInstruction.trim()}")
        }

        val joinedDirective = descriptors.joinToString(", ")
        return "$joinedDirective: $optimizedThaiText"
    }

    /**
     * Maps `speechStability` (0..100%) to Gemini `GenerationConfig.temperature` (0.20f..0.95f).
     */
    fun computeGenerationTemperature(): Float {
        val normStability = (speechStability.coerceIn(0, 100)) / 100f
        return (0.90f - (normStability * 0.65f)).coerceIn(0.20f, 0.95f)
    }

    companion object {
        const val DEFAULT_CLOUD_TIMEOUT_MS = 3_000L
        const val FALLBACK_PRIORITY_CHAIN = "Gemini Cloud > F5-TTS-THAI > Android System TTS"
        const val DETAILED_ENGINE_PRIORITY = "Gemini Flash TTS → Gemini Flash-Lite TTS → F5-TTS-THAI → External Android TTS"
    }
}

enum class QueueTicketStatus(val labelTh: String) {
    QUEUED("อยู่ในคิว"),
    CACHE_HIT("ดึงจากแคชสำเร็จ"),
    SYNTHESIZING_CLOUD("กำลังสังเคราะห์ Gemini Cloud"),
    RETRY_BACKOFF("กำลังลองใหม่ (Exponential Backoff)"),
    FALLBACK_LOCAL("สลับไปใช้ Local TTS อัตโนมัติ"),
    COMPLETED("เสร็จสมบูรณ์"),
    FAILED("ล้มเหลว")
}

data class QueueTicket(
    val id: String,
    val textSnippet: String,
    val status: QueueTicketStatus,
    val providerUsed: String,
    val attempt: Int = 1,
    val latencyMs: Long = 0L,
    val messageTh: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalTtsEngineStatus(
    val isInitialized: Boolean = false,
    val selfPackageName: String = "",
    val defaultEnginePackage: String = "กำลังตรวจสอบ...",
    val selectedExternalEnginePackage: String? = null,
    val selfRecursionPrevented: Boolean = true,
    val installedEngines: List<String> = emptyList(),
    val thaiLanguageAvailable: Boolean = false,
    val thaiOfflineVoices: List<String> = emptyList(),
    val onnxRuntimeAvailable: Boolean = false,
    val onnxRuntimeVersion: String = "กำลังตรวจสอบ...",
    val onnxModelInstalled: Boolean = false,
    val onnxModelPath: String = "",
    val f5ConditioningAssetsReady: Boolean = false,
    val f5SubgraphsInstalled: Boolean = false,
    val f5ModelDirPath: String = "",
    val verifiedChecksumCount: Int = 0,
    val totalOfflineStorageBytes: Long = 0L,
    val fallbackPriorityChain: String = SynthesisConfig.FALLBACK_PRIORITY_CHAIN,
    val onnxLicensingAndSpecsTh: String = "VIZINTZOR/F5-TTS-THAI (CC-BY-4.0 · SWivid/F5-TTS MIT · Vocos 24kHz MIT) + Fast Thai VITS ONNX (facebook/mms-tts-tha · CC-BY-NC-4.0)",
    val activeOfflineRuntime: String = "กำลังตรวจสอบ...",
    val statusDetailTh: String = "กำลังเชื่อมต่อระบบ Android TextToSpeech..."
)

data class SynthesisOutcome(
    val wavFilePath: String,
    val wavBytes: ByteArray,
    val waveformBars: List<Float>,
    val durationMs: Long,
    val sampleRateHz: Int,
    val providerUsed: String,
    val modelUsed: String,
    val voiceUsed: String,
    val latencyMs: Long,
    val fallbackTriggered: Boolean,
    val fallbackReasonTh: String? = null,
    val normalizedText: String
)

data class NavigationPhrasePreset(
    val id: String,
    val thaiText: String,
    val categoryTh: String,
    val distanceBadge: String
)

val DEFAULT_THAI_NAVIGATION_PHRASES = listOf(
    NavigationPhrasePreset("nav_1", "เลี้ยวซ้ายข้างหน้า", "คำสั่งเลี้ยว", "50 ม."),
    NavigationPhrasePreset("nav_2", "เลี้ยวขวาในอีก 200 ม.", "คำสั่งเลี้ยว", "200 ม."),
    NavigationPhrasePreset("nav_3", "เบี่ยงซ้ายเพื่อเข้าสู่ทางด่วน", "ทางพิเศษ", "300 ม."),
    NavigationPhrasePreset("nav_4", "ขับตรงไปตาม ถ.สุขุมวิท อีก 1.5 กม.", "เส้นทางหลัก", "1.5 กม."),
    NavigationPhrasePreset("nav_5", "กลับรถใต้สะพานข้างหน้า", "กลับรถ", "150 ม."),
    NavigationPhrasePreset("nav_6", "ระวังการจราจรติดขัดข้างหน้า", "แจ้งเตือนจราจร", "เตือนภัย"),
    NavigationPhrasePreset("nav_7", "กำลังคำนวณเส้นทางใหม่ด้วย GPS", "ระบบนำทาง", "อัตโนมัติ"),
    NavigationPhrasePreset("nav_8", "คุณมาถึงจุดหมายปลายทางแล้วในเวลา 14:30 น.", "สิ้นสุดการเดินทาง", "0 ม.")
)
