package com.example.tts

import com.example.data.local.PronunciationRuleEntity

/**
 * Deterministic, zero-latency Thai Language Optimizer & Two-Level Speech Adapter for natural Thai TTS synthesis.
 * Handles:
 * 1. Thai numbers, times (HH:mm น.), distances, speeds, percentages, and units without changing meaning.
 * 2. Accurate Thai road/location abbreviations and homographs.
 * 3. Intelligent Thai-English mixed-sentence boundary spacing and acronym pronunciation.
 * 4. Natural Thai clause segmentation and breath pauses scaled by the `naturalPauses` parameter.
 * 5. Level 2 Conversational Phrasing Adaptation (`SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH`):
 *    Transforms stiff or terse phrases into natural, friendly spoken Thai
 *    (e.g., "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ").
 */
object ThaiTextOptimizer {

    private val thaiDigits = arrayOf("ศูนย์", "หนึ่ง", "สอง", "สาม", "สี่", "ห้า", "หก", "เจ็ด", "แปด", "เก้า")

    private val builtInLocationAndAbbrevMap = linkedMapOf(
        "กม./ชม." to "กิโลเมตรต่อชั่วโมง",
        "ตร.ม." to "ตารางเมตร",
        "ตร.วา" to "ตารางวา",
        "กทม." to "กรุงเทพมหานคร",
        "รพ." to "โรงพยาบาล",
        "ถ.สุขุมวิท" to "ถนนสุขุมวิท",
        "ถ.พหลโยธิน" to "ถนนพหลโยธิน",
        "ถ.พระราม" to "ถนนพระราม",
        "ถ.เพชรบุรี" to "ถนนเพชรบุรี",
        "ถ.รัชดาภิเษก" to "ถนนรัชดาภิเษก",
        "ถ.วิภาวดีรังสิต" to "ถนนวิภาวดีรังสิต",
        "ถ." to "ถนน",
        "ซ." to "ซอย",
        "ม.เกษตร" to "มหาวิทยาลัยเกษตรศาสตร์",
        "มศว" to "มะ-สะ-วอ",
        "ชม." to "ชั่วโมง"
    )

    private val mixedAcronymMap = linkedMapOf(
        "GPS" to "จีพีเอส",
        "BTS" to "บีทีเอส",
        "MRT" to "เอ็มอาร์ที",
        "ARL" to "แอร์พอร์ตเรลลิงก์",
        "EV" to "อีวี",
        "NGV" to "เอ็นจีวี",
        "LPG" to "แอลพีจี",
        "USB" to "ยูเอสบี",
        "Wi-Fi" to "วายฟาย",
        "SOS" to "เอสโอเอส"
    )

    private val clauseBreakConnectors = listOf(
        "อย่างไรก็ตาม",
        "เพราะฉะนั้น",
        "นอกจากนี้",
        "ในขณะที่",
        "เพื่อเข้าสู่",
        "หลังจากนั้น",
        "จากนั้น"
    )

    private val actionableNavigationPhrases = listOf(
        "เลี้ยวซ้าย",
        "เลี้ยวขวา",
        "กลับรถ",
        "เบี่ยงซ้าย",
        "เบี่ยงขวา",
        "ชิดซ้าย",
        "ชิดขวา",
        "ตรงไป",
        "เข้าสู่ทางด่วน",
        "ออกทางคู่ขนาน",
        "ใช้ทางออก"
    )

    private val existingPoliteEndings = listOf(
        "นะครับ",
        "นะคะ",
        "ครับผม",
        "ค่ะ",
        "ครับ",
        "นะจ๊ะ",
        "จ้ะ",
        "ได้เลย"
    )

    fun optimizeThaiText(
        rawText: String,
        customRules: List<PronunciationRuleEntity> = emptyList(),
        pronunciationClarity: Int = 75,
        naturalPauses: Int = 50,
        adaptationLevel: SpeechAdaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
        politenessParticle: PolitenessParticle = PolitenessParticle.KRUB,
        voiceStyle: VoiceStylePreset = VoiceStylePreset.NATURAL,
        responseStyle: ResponseStyle = ResponseStyle.CONVERSATIONAL,
        thaiPoliteEnding: ThaiPoliteEnding? = null,
        disableRewritingForNavigation: Boolean = false
    ): String {
        if (rawText.isBlank()) return ""
        var text = rawText.trim().replace(Regex("[ \\t]+"), " ")

        // 1. Apply user custom pronunciation dictionary rules first (longest pattern first)
        val sortedCustom = customRules.filter { it.enabled && it.thaiPattern.isNotBlank() }
            .sortedByDescending { it.thaiPattern.length }
        for (rule in sortedCustom) {
            if (text.contains(rule.thaiPattern)) {
                text = text.replace(rule.thaiPattern, rule.spokenReplacement)
            }
        }

        // 2. Expand Thai road, location, and standard abbreviations
        for ((abbr, full) in builtInLocationAndAbbrevMap) {
            if (text.contains(abbr)) {
                text = text.replace(abbr, full)
            }
        }

        // 3. Convert time expressions e.g. "14:30 น." or "08:00 น."
        text = Regex("(\\d{1,2}):(\\d{2})\\s*น\\.?").replace(text) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: 0
            val min = match.groupValues[2].toIntOrNull() ?: 0
            val hourWords = numberToThaiWords(hour.toLong())
            if (min == 0) {
                "${hourWords}นาฬิกาตรง"
            } else {
                "${hourWords}นาฬิกา ${numberToThaiWords(min.toLong())}นาที"
            }
        }

        // 4. Convert decimal numbers with units e.g. "1.5 กม.", "2.5 ม."
        text = Regex("(\\d+)\\.(\\d+)\\s*(กม\\.|ม\\.|ซม\\.|กก\\.|%)").replace(text) { match ->
            val intPart = match.groupValues[1].toLongOrNull() ?: 0L
            val decPart = match.groupValues[2]
            val unitRaw = match.groupValues[3]
            val decWords = decPart.mapNotNull { it.digitToIntOrNull()?.let { d -> thaiDigits[d] } }.joinToString("")
            val unitWord = expandUnitSuffix(unitRaw)
            "${numberToThaiWords(intPart)}จุด${decWords}${unitWord}"
        }

        // 5. Convert integer numbers followed by Thai units e.g. "200 ม.", "300 ม.", "50 กม.", "80%"
        text = Regex("(\\d{1,6})\\s*(กม\\.|ม\\.|ซม\\.|กก\\.|%|องศาเซลเซียส|°C)").replace(text) { match ->
            val num = match.groupValues[1].toLongOrNull() ?: return@replace match.value
            val unitWord = expandUnitSuffix(match.groupValues[2])
            "${numberToThaiWords(num)}${unitWord}"
        }

        // Collapse "อีก สามร้อยเมตร" -> "อีกสามร้อยเมตร" when "อีก" precedes a converted Thai number
        text = Regex("อีก\\s+(ศูนย์|หนึ่ง|สอง|สาม|สี่|ห้า|หก|เจ็ด|แปด|เก้า|สิบ|ยี่สิบ)").replace(text, "อีก$1")

        // 6. Handle Thai-English mixed sentences
        text = Regex("([ก-๙])([A-Za-z])").replace(text, "$1 $2")
        text = Regex("([A-Za-z])([ก-๙])").replace(text, "$1 $2")

        if (pronunciationClarity >= 60) {
            for ((acronym, thaiPhonetic) in mixedAcronymMap) {
                text = Regex("\\b${Regex.escape(acronym)}\\b").replace(text, thaiPhonetic)
            }
        }

        // 7. Natural sentence segmentation & breath pauses
        if (naturalPauses >= 45) {
            val pauseToken = if (naturalPauses >= 75) " , " else " "
            for (connector in clauseBreakConnectors) {
                if (text.contains(connector) && !text.startsWith(connector)) {
                    text = text.replace(" $connector", "$pauseToken$connector")
                }
            }
        }

        text = text.replace(Regex("\\s+"), " ").trim()

        // 8. Polite Sentence Ending / Phrasing Adaptation
        if (adaptationLevel == SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH) {
            text = applyConversationalPhrasingAdaptation(
                normalizedText = text,
                politenessParticle = politenessParticle,
                voiceStyle = voiceStyle,
                responseStyle = responseStyle
            )
        } else if (thaiPoliteEnding != null && thaiPoliteEnding != ThaiPoliteEnding.NO_ENDING && adaptationLevel != SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION) {
            text = applyPoliteEnding(
                text = text,
                ending = thaiPoliteEnding,
                disableRewritingForNavigation = disableRewritingForNavigation
            )
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Configurable Thai Polite Sentence Ending System:
     * - Auto Natural: Selects appropriate endings based on meaning & context.
     *   "อีก 300 เมตร เลี้ยวซ้าย" -> "อีก 300 เมตร เลี้ยวซ้ายนะครับ"
     *   "พบเส้นทางที่เร็วกว่า" -> "พบเส้นทางที่เร็วกว่าแล้วครับผม"
     *   "ถึงจุดหมายแล้ว" -> "ถึงจุดหมายแล้วนะครับ"
     * - ครับ: Appends "ครับ" if not present
     * - นะครับ: Appends "นะครับ" if not present
     * - ครับผม: Appends "ครับผม" if not present
     * - No Ending: Preserves base wording without adding polite particles
     */
    fun applyPoliteEnding(
        text: String,
        ending: ThaiPoliteEnding = ThaiPoliteEnding.AUTO_NATURAL,
        disableRewritingForNavigation: Boolean = false
    ): String {
        if (text.isBlank()) return text
        val trimmed = text.trim().trimEnd('.', '!', '?')

        val existingEndings = listOf(
            "แล้วครับผม", "ครับผม", "นะครับ", "นะคะ", "ได้เลยนะครับ", "ได้เลยนะคะ",
            "ครับ", "ค่ะ", "นะจ๊ะ", "จ๊ะ", "จ้า", "จ้ะ"
        )

        if (ending == ThaiPoliteEnding.NO_ENDING) {
            for (suffix in existingEndings) {
                if (trimmed.endsWith(suffix)) {
                    return trimmed.removeSuffix(suffix).trimEnd()
                }
            }
            return trimmed
        }

        // Preserve urgent safety instructions without adding polite filler
        val urgentSafetyPatterns = listOf(
            "ระวังอันตราย", "เบรกกะทันหัน", "หยุดทันที", "ฉุกเฉิน", "อันตรายข้างหน้า",
            "ห้ามเลี้ยว", "เกิดอุบัติเหตุ", "ระวังชน", "หยุดรถ"
        )
        if (urgentSafetyPatterns.any { trimmed.contains(it) }) {
            return trimmed
        }

        // Check if this is a navigation command
        val isNavigationInstruction = listOf(
            "เลี้ยวซ้าย", "เลี้ยวขวา", "ตรงไป", "กลับรถ", "ชิดซ้าย", "ชิดขวา",
            "เบี่ยงซ้าย", "เบี่ยงขวา", "ทางออก", "ทางด่วน", "วงเวียน", "เข้าสู่", "ม.", "กม.", "เมตร", "กิโลเมตร"
        ).any { trimmed.contains(it) }

        if (disableRewritingForNavigation && isNavigationInstruction) {
            return trimmed
        }

        // Never duplicate existing polite endings
        if (existingEndings.any { trimmed.endsWith(it) }) {
            return trimmed
        }

        return when (ending) {
            ThaiPoliteEnding.KRUB -> "${trimmed}ครับ"
            ThaiPoliteEnding.NA_KRUB -> "${trimmed}นะครับ"
            ThaiPoliteEnding.KRUB_PHOM -> "${trimmed}ครับผม"
            ThaiPoliteEnding.NO_ENDING -> trimmed
            ThaiPoliteEnding.AUTO_NATURAL -> {
                when {
                    // Discovery/route calculation: "พบเส้นทางที่เร็วกว่า" -> "พบเส้นทางที่เร็วกว่าแล้วครับผม"
                    trimmed.contains("พบเส้นทาง") || trimmed.contains("คำนวณเส้นทาง") || trimmed.startsWith("พบ") -> {
                        if (trimmed.endsWith("แล้ว")) "${trimmed}ครับผม" else "${trimmed}แล้วครับผม"
                    }
                    // Arrival / completion: "ถึงจุดหมายแล้ว" -> "ถึงจุดหมายแล้วนะครับ"
                    trimmed.contains("ถึงจุดหมาย") || trimmed.contains("จุดหมายปลายทาง") || trimmed.contains("เสร็จสิ้น") -> {
                        "${trimmed}นะครับ"
                    }
                    // Nav command: "อีก 300 เมตร เลี้ยวซ้าย" -> "อีก 300 เมตร เลี้ยวซ้ายนะครับ"
                    isNavigationInstruction || listOf("เลี้ยว", "ตรงไป", "กลับรถ", "ชิด", "เบี่ยง").any { trimmed.contains(it) } -> {
                        "${trimmed}นะครับ"
                    }
                    // Traffic / general warnings (non-urgent)
                    trimmed.startsWith("ระวัง") || trimmed.startsWith("โปรดระวัง") -> {
                        "${trimmed}นะครับ"
                    }
                    // Questions
                    trimmed.contains("ไหม") || trimmed.contains("หรือไม่") || trimmed.contains("อะไร") -> {
                        "${trimmed}ครับ"
                    }
                    else -> "${trimmed}นะครับ"
                }
            }
        }
    }

    /**
     * Level 2 Phrasing Adapter:
     * Rewrites terse commands or plain statements into warm, friendly, conversational Thai without altering facts.
     * Example:
     * - "อีกสามร้อยเมตร เลี้ยวซ้าย" -> "อีกสามร้อยเมตร เลี้ยวซ้ายได้เลยนะครับ"
     * - "เลี้ยวซ้ายข้างหน้า" -> "เลี้ยวซ้ายข้างหน้าได้เลยนะครับ"
     * - "ระวังการจราจรติดขัดข้างหน้า" -> "ระวังการจราจรติดขัดข้างหน้าด้วยนะครับ"
     */
    fun applyConversationalPhrasingAdaptation(
        normalizedText: String,
        politenessParticle: PolitenessParticle = PolitenessParticle.KRUB,
        voiceStyle: VoiceStylePreset = VoiceStylePreset.NATURAL,
        responseStyle: ResponseStyle = ResponseStyle.CONVERSATIONAL
    ): String {
        val clean = normalizedText.trim().trimEnd('.', '!')
        if (clean.isEmpty()) return clean

        // If the sentence already ends with an explicit conversational particle like "ได้เลยนะครับ" or "นะครับ", keep it clean
        if (clean.endsWith(politenessParticle.actionEndingTh) || clean.endsWith(politenessParticle.statementEndingTh)) {
            return clean
        }

        // Strip bare "ครับ" or "ค่ะ" at the very end so we can upgrade it to a friendlier conversational ending
        val strippedBase = when {
            clean.endsWith("ครับ") && !clean.endsWith("นะครับ") -> clean.removeSuffix("ครับ").trimEnd()
            clean.endsWith("ค่ะ") && !clean.endsWith("นะคะ") -> clean.removeSuffix("ค่ะ").trimEnd()
            else -> clean
        }

        if (existingPoliteEndings.any { strippedBase.endsWith(it) }) {
            return strippedBase
        }

        // Check if this is a caution/warning sentence
        if (strippedBase.startsWith("ระวัง") || strippedBase.startsWith("โปรดระวัง")) {
            return "${strippedBase}ด้วย${politenessParticle.statementEndingTh}"
        }

        // Check if this contains an actionable driving/navigation instruction (e.g., "อีกสามร้อยเมตร เลี้ยวซ้าย")
        val isActionableDirective = actionableNavigationPhrases.any { strippedBase.contains(it) }
        if (isActionableDirective) {
            return when (voiceStyle) {
                VoiceStylePreset.LUXURY -> "ขออนุญาตแนะนำ ${strippedBase}${politenessParticle.actionEndingTh}"
                else -> "${strippedBase}${politenessParticle.actionEndingTh}"
            }
        }

        // General statement / conversational adaptation
        return when {
            responseStyle == ResponseStyle.CONCISE -> "${strippedBase}${politenessParticle.statementEndingTh}"
            voiceStyle == VoiceStylePreset.LUXURY || voiceStyle == VoiceStylePreset.PROFESSIONAL ->
                "${strippedBase}${politenessParticle.statementEndingTh}"
            else -> "${strippedBase}${politenessParticle.statementEndingTh}"
        }
    }

    private fun expandUnitSuffix(unitRaw: String): String {
        return when (unitRaw.trim()) {
            "กม." -> "กิโลเมตร"
            "ม." -> "เมตร"
            "ซม." -> "เซนติเมตร"
            "กก." -> "กิโลกรัม"
            "%" -> "เปอร์เซ็นต์"
            "°C", "องศาเซลเซียส" -> "องศาเซลเซียส"
            else -> unitRaw
        }
    }

    /**
     * Converts a non-negative integer (0..999,999) into natural spoken Thai words
     * respecting ยี่สิบ (20) and เอ็ด (trailing 1).
     */
    fun numberToThaiWords(number: Long): String {
        if (number == 0L) return "ศูนย์"
        if (number < 0L) return "ลบ" + numberToThaiWords(-number)
        if (number > 999_999L) return number.toString()

        val positions = arrayOf("", "สิบ", "ร้อย", "พัน", "หมื่น", "แสน")
        val digits = number.toString().map { it - '0' }
        val len = digits.size
        val sb = StringBuilder()

        for (i in 0 until len) {
            val d = digits[i]
            val pos = len - 1 - i
            if (d == 0) continue

            when {
                pos == 1 && d == 1 -> sb.append("สิบ")
                pos == 1 && d == 2 -> sb.append("ยี่สิบ")
                pos == 0 && d == 1 && len > 1 -> sb.append("เอ็ด")
                else -> {
                    sb.append(thaiDigits[d])
                    sb.append(positions[pos])
                }
            }
        }
        return sb.toString()
    }
}
