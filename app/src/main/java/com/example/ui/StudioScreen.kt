package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.PlaybackState
import com.example.data.local.PronunciationRuleEntity
import com.example.data.local.VoiceProfileEntity
import com.example.tts.GeminiTtsModel
import com.example.tts.GeminiVoice
import com.example.tts.PolitenessParticle
import com.example.tts.ResponseStyle
import com.example.tts.RoutingPolicy
import com.example.tts.SpeechAdaptationLevel
import com.example.tts.SynthesisConfig
import com.example.tts.SynthesisOutcome
import com.example.tts.ThaiTextOptimizer
import com.example.tts.VoiceStylePreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioScreen(
    inputText: String,
    previewSampleText: String,
    config: SynthesisConfig,
    synthesisState: SynthesisUiState,
    lastOutcome: SynthesisOutcome?,
    playbackState: PlaybackState,
    exportBanner: String?,
    pronunciationRules: List<PronunciationRuleEntity>,
    voiceProfiles: List<VoiceProfileEntity>,
    argonAnalysis: String?,
    isArgonAnalyzing: Boolean,
    isRewritingScriptWithAi: Boolean = false,
    aiRewrittenSuggestion: String? = null,
    dailyCloudCount: Int,
    isDarkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onTextChange: (String) -> Unit,
    onUpdatePreviewSampleText: (String) -> Unit,
    onSelectModel: (GeminiTtsModel) -> Unit,
    onSelectVoice: (GeminiVoice) -> Unit,
    onSelectStyle: (VoiceStylePreset) -> Unit,
    onSelectResponseStyle: (ResponseStyle) -> Unit,
    onSelectAdaptationLevel: (SpeechAdaptationLevel) -> Unit = {},
    onSelectPolitenessParticle: (PolitenessParticle) -> Unit = {},
    onApplyLevel2RewriteToInput: () -> Unit = {},
    onRewriteScriptWithArgonLevel2: () -> Unit = {},
    onApplyAiRewrittenSuggestion: () -> Unit = {},
    onDismissAiRewrittenSuggestion: () -> Unit = {},
    onPreviewStylePreset: (VoiceStylePreset) -> Unit,
    onSelectRoutingPolicy: (RoutingPolicy) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onEmotionalIntensityChange: (Int) -> Unit,
    onExpressivenessChange: (Int) -> Unit,
    onNaturalPausesChange: (Int) -> Unit,
    onPronunciationClarityChange: (Int) -> Unit,
    onVoiceWarmthChange: (Int) -> Unit,
    onFormalityChange: (Int) -> Unit,
    onSpeechStabilityChange: (Int) -> Unit,
    onCustomInstructionChange: (String) -> Unit,
    onPreferredLanguageChange: (String) -> Unit,
    onSaveVoiceProfile: (String) -> Unit,
    onApplyVoiceProfile: (VoiceProfileEntity) -> Unit,
    onDeleteVoiceProfile: (Long) -> Unit,
    onSynthesize: (Boolean) -> Unit,
    onPlayFile: (String) -> Unit,
    onStopPlayback: () -> Unit,
    onExportWav: (String, String) -> Unit,
    onDismissExportBanner: () -> Unit,
    onAddPronunciationRule: (String, String, String) -> Unit,
    onDeletePronunciationRule: (Long) -> Unit,
    onRunArgonAnalysis: () -> Unit,
    onDismissArgonAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPronunciationSheet by remember { mutableStateOf(false) }
    var newPattern by remember { mutableStateOf("") }
    var newReplacement by remember { mutableStateOf("") }
    var newNote by remember { mutableStateOf("") }
    var showOnlySelectedVoice by remember { mutableStateOf(true) }

    val level1NormalizedPreview = remember(
        inputText,
        pronunciationRules,
        config.pronunciationClarity,
        config.naturalPauses
    ) {
        ThaiTextOptimizer.optimizeThaiText(
            rawText = inputText,
            customRules = pronunciationRules,
            pronunciationClarity = config.pronunciationClarity,
            naturalPauses = config.naturalPauses,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_1_TONE_AND_EMOTION,
            politenessParticle = config.politenessParticle,
            voiceStyle = config.style,
            responseStyle = config.responseStyle
        )
    }

    val level2RewrittenPreview = remember(
        inputText,
        pronunciationRules,
        config.pronunciationClarity,
        config.naturalPauses,
        config.politenessParticle,
        config.style,
        config.responseStyle
    ) {
        ThaiTextOptimizer.optimizeThaiText(
            rawText = inputText,
            customRules = pronunciationRules,
            pronunciationClarity = config.pronunciationClarity,
            naturalPauses = config.naturalPauses,
            adaptationLevel = SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH,
            politenessParticle = config.politenessParticle,
            voiceStyle = config.style,
            responseStyle = config.responseStyle
        )
    }

    val liveNormalizedPreview = if (config.adaptationLevel == SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH) {
        level2RewrittenPreview
    } else {
        level1NormalizedPreview
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Studio Hero Header Card with Active Voice Personality Summary & Theme Toggle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "SiamVoice Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "SiamVoice Hybrid TTS",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "โควตา $dailyCloudCount/${config.dailyCloudQuotaLimit}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleDarkTheme,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "สลับโหมดมืด/สว่าง",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = "Voice Style: ${config.style.titleEn} · Response Style: ${config.responseStyle.titleEn} · Speed: ${"%.2f".format(config.speechRate)}x · Expressiveness: ${config.expressiveness}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "โหมดดัดเสียงพูด: ${config.adaptationLevel.shortBadgeTh} (${config.politenessParticle.labelTh})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // 1. Thai Text Input & 2-Level Voice / Phrasing Adaptation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ข้อความภาษาไทย (Thai Script Input)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = onRunArgonAnalysis,
                            enabled = !isArgonAnalyzing && inputText.isNotBlank(),
                            modifier = Modifier.testTag("argon_analyze_button")
                        ) {
                            if (isArgonAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = "Analyze with Gemini 4 Argon",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("วิเคราะห์ด้วย Argon")
                        }
                        IconButton(
                            onClick = { onTextChange("") },
                            modifier = Modifier.testTag("clear_text_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "ล้างข้อความ"
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tts_text_input"),
                    minLines = 2,
                    maxLines = 5,
                    placeholder = {
                        Text("เช่น อีกสามร้อยเมตร เลี้ยวซ้าย หรือ เลี้ยวซ้ายเข้า ถ.สุขุมวิท อีก 200 ม. ...")
                    }
                )

                // Quick sample phrase chips (including the exact "อีกสามร้อยเมตร เลี้ยวซ้าย" example)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val samples = listOf(
                        "อีกสามร้อยเมตร เลี้ยวซ้าย",
                        "เลี้ยวซ้ายเข้า ถ.สุขุมวิท ในอีก 200 ม. เวลา 14:30 น.",
                        "ระวังการจราจรติดขัดข้างหน้า",
                        "ในเพลาเช้าตรู่ รถวิ่งผ่าน รพ. มุ่งหน้าสู่ กทม. ด้วยความเร็ว 80 กม./ชม.",
                        "ยินดีต้อนรับสู่บริการผู้ช่วยส่วนตัวระดับพรีเมียม"
                    )
                    samples.forEachIndexed { index, sample ->
                        FilterChip(
                            selected = inputText == sample,
                            onClick = { onTextChange(sample) },
                            label = {
                                Text(
                                    text = sample,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("sample_chip_$index")
                        )
                    }
                }

                HorizontalDivider()

                // 2-Level Voice & Conversational Phrasing Adaptation Selector
                Text(
                    text = "เลือกระดับการปรับความเป็นกันเอง (2-Level Speech & Voice Adaptation)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                SpeechAdaptationLevel.entries.forEach { level ->
                    val isSelected = config.adaptationLevel == level
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectAdaptationLevel(level) }
                            .padding(12.dp)
                            .testTag("adaptation_level_${level.levelNumber}")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = level.titleTh,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected Level",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                text = level.subtitleTh,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ตัวอย่าง: ${level.exampleBeforeAfterTh}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Politeness Particle selector & Level 2 Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "หางเสียง (ระดับ 2):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PolitenessParticle.entries.forEach { particle ->
                        FilterChip(
                            selected = config.politenessParticle == particle,
                            onClick = { onSelectPolitenessParticle(particle) },
                            label = { Text(particle.labelTh) },
                            modifier = Modifier.testTag("particle_chip_${particle.id.lowercase()}")
                        )
                    }
                }

                // Live Preview Comparison of Level 1 vs Level 2 Phrasing for current input
                if (inputText.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_adaptation_preview_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (config.adaptationLevel == SpeechAdaptationLevel.LEVEL_2_REWRITE_SPEECH) {
                                    "ประโยคที่จะใช้พูดจริง (ระดับที่ 2: ดัดน้ำเสียง + ดัดแปลงคำพูดใหม่):"
                                } else {
                                    "ประโยคที่จะใช้พูดจริง (ระดับที่ 1: ดัดน้ำเสียง/อารมณ์ คงคำเดิม):"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "\"$liveNormalizedPreview\"",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("effective_spoken_text_preview")
                            )

                            if (level1NormalizedPreview != level2RewrittenPreview) {
                                Text(
                                    text = "เปรียบเทียบ ระดับ 1: \"$level1NormalizedPreview\"  →  ระดับ 2: \"$level2RewrittenPreview\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onApplyLevel2RewriteToInput,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("apply_level2_rewrite_button")
                                ) {
                                    Text("แทนที่ด้วยคำพูดระดับ 2")
                                }
                                OutlinedButton(
                                    onClick = onRewriteScriptWithArgonLevel2,
                                    enabled = !isRewritingScriptWithAi,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("argon_level2_rewrite_button")
                                ) {
                                    if (isRewritingScriptWithAi) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Argon กำลังเกลา...")
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = "Argon Rewrite",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("เกลาคำพูดด้วย Argon")
                                    }
                                }
                            }
                        }
                    }
                }

                // Gemini 4 Argon AI Script Rewrite Suggestion Banner
                AnimatedVisibility(visible = !aiRewrittenSuggestion.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "ประโยคที่ดัดแปลงใหม่โดย Gemini 4 Argon (ระดับที่ 2):",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "\"${aiRewrittenSuggestion.orEmpty()}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onApplyAiRewrittenSuggestion,
                                    modifier = Modifier.testTag("use_ai_rewritten_script_btn")
                                ) {
                                    Text("นำประโยคนี้ไปใช้")
                                }
                                TextButton(onClick = onDismissAiRewrittenSuggestion) {
                                    Text("ปิด")
                                }
                            }
                        }
                    }
                }

                // Gemini 4 Argon High-Thinking Phonetic & Style Analysis Result
                AnimatedVisibility(visible = argonAnalysis != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "ผลวิเคราะห์โดย Gemini 4 Argon (ThinkingLevel.HIGH)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                TextButton(onClick = onDismissArgonAnalysis) {
                                    Text("ปิด")
                                }
                            }
                            Text(
                                text = argonAnalysis.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Primary Synthesize Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onSynthesize(false) },
                        enabled = synthesisState !is SynthesisUiState.Synthesizing && inputText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("synthesize_button")
                    ) {
                        if (synthesisState is SynthesisUiState.Synthesizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังสังเคราะห์...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = "สังเคราะห์เสียงพูด"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "สังเคราะห์เสียงพูด (${config.style.titleEn})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { onSynthesize(true) },
                        enabled = synthesisState !is SynthesisUiState.Synthesizing && inputText.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("force_resynthesize_button")
                    ) {
                        Text("สร้างใหม่")
                    }
                }
            }
        }

        // 2. Active Audio Waveform & Playback / Export WAV Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("waveform_playback_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "เครื่องเล่นเสียง & บันทึก WAV (24kHz 16-bit PCM)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (lastOutcome != null) {
                        Surface(
                            color = if (lastOutcome.fallbackTriggered) {
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${lastOutcome.providerUsed} · ${lastOutcome.latencyMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (lastOutcome.fallbackTriggered) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                when (synthesisState) {
                    is SynthesisUiState.Synthesizing -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = synthesisState.progressLabelTh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is SynthesisUiState.Success -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = synthesisState.statusBannerTh,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is SynthesisUiState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = synthesisState.errorMessageTh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    SynthesisUiState.Idle -> {
                        Text(
                            text = "กดปุ่ม 'สังเคราะห์เสียงพูด' หรือกดปุ่ม 'ฟัง' ในแถบเปรียบเทียบสไตล์เสียงด้านล่าง",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val bars = lastOutcome?.waveformBars ?: List(40) { 0.14f }
                val activeProgress = if (playbackState.isPlaying) playbackState.progressFraction else 0f
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val inactiveColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val count = bars.size
                        val spacing = 4.dp.toPx()
                        val totalSpacing = spacing * (count - 1)
                        val barWidth = ((size.width - totalSpacing) / count).coerceAtLeast(2f)

                        bars.forEachIndexed { idx, amp ->
                            val barFraction = idx.toFloat() / count.toFloat()
                            val isPlayed = playbackState.isPlaying && barFraction <= activeProgress
                            val barHeight = (amp * size.height).coerceIn(6.dp.toPx(), size.height)
                            val topY = (size.height - barHeight) / 2f
                            val x = idx * (barWidth + spacing)

                            drawRoundRect(
                                color = when {
                                    isPlayed -> primaryColor
                                    lastOutcome != null -> secondaryColor.copy(alpha = 0.8f)
                                    else -> inactiveColor
                                },
                                topLeft = Offset(x, topY),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (playbackState.isPlaying) {
                                onStopPlayback()
                            } else if (lastOutcome != null) {
                                onPlayFile(lastOutcome.wavFilePath)
                            }
                        },
                        enabled = lastOutcome != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (playbackState.isPlaying) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("play_stop_audio_button")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "หยุดเล่น" else "เล่นเสียง"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (playbackState.isPlaying) {
                                "หยุดเล่น (${playbackState.currentPositionMs} ms)"
                            } else {
                                "เล่นเสียง (${lastOutcome?.durationMs ?: 0} ms)"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (lastOutcome != null) {
                                onExportWav(lastOutcome.wavFilePath, lastOutcome.normalizedText)
                            }
                        },
                        enabled = lastOutcome != null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_wav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "บันทึก WAV"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("บันทึก WAV")
                    }
                }

                if (exportBanner != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exportBanner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissExportBanner) {
                                Text("รับทราบ")
                            }
                        }
                    }
                }
            }
        }

        // 3. Complete Voice Personality, Response Style, Live Preview, 10 Advanced Sliders & Smart Profiles
        VoicePersonalityAndStyleSection(
            config = config,
            previewSampleText = previewSampleText,
            voiceProfiles = voiceProfiles,
            onSelectVoiceStyle = onSelectStyle,
            onSelectResponseStyle = onSelectResponseStyle,
            onPreviewStylePreset = onPreviewStylePreset,
            onUpdatePreviewSampleText = onUpdatePreviewSampleText,
            onSpeechRateChange = onSpeechRateChange,
            onPitchChange = onPitchChange,
            onVolumeChange = onVolumeChange,
            onEmotionalIntensityChange = onEmotionalIntensityChange,
            onExpressivenessChange = onExpressivenessChange,
            onNaturalPausesChange = onNaturalPausesChange,
            onPronunciationClarityChange = onPronunciationClarityChange,
            onVoiceWarmthChange = onVoiceWarmthChange,
            onFormalityChange = onFormalityChange,
            onSpeechStabilityChange = onSpeechStabilityChange,
            onCustomInstructionChange = onCustomInstructionChange,
            onPreferredLanguageChange = onPreferredLanguageChange,
            onSaveVoiceProfile = onSaveVoiceProfile,
            onApplyVoiceProfile = onApplyVoiceProfile,
            onDeleteVoiceProfile = onDeleteVoiceProfile
        )

        // 4. Gemini 3.8 Model, Voice Persona & Hybrid Routing Policy Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = "Gemini Model",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "โมเดล Gemini 3.8 TTS · เสียงพูด · โหมดไฮบริด",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                GeminiTtsModel.entries.forEach { modelOption ->
                    val isSelected = config.model == modelOption
                    Surface(
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectModel(modelOption) }
                            .padding(12.dp)
                            .testTag("model_card_${modelOption.name.lowercase()}")
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = modelOption.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = modelOption.modelId,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "${modelOption.badgeTh} — ${modelOption.descriptionTh}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "เสียงพูดที่ใช้งาน (เสียงแค่นี้เท่านั้น)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (showOnlySelectedVoice) "ใช้เฉพาะเสียงหลักนี้ตามที่ต้องการ (ไม่เอาเสียงอื่น)" else "เลือกเสียงพูดทั้งหมด (Gemini Voice Persona)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = { showOnlySelectedVoice = !showOnlySelectedVoice },
                        modifier = Modifier.testTag("toggle_voice_view_mode_button")
                    ) {
                        Text(if (showOnlySelectedVoice) "ดูเสียงอื่นเพิ่มเติม" else "ใช้เฉพาะเสียงนี้ (ไม่เอาเสียงอื่น)")
                    }
                }

                if (showOnlySelectedVoice) {
                    val activeVoice = config.voice
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectVoice(activeVoice) }
                            .padding(16.dp)
                            .testTag("voice_card_${activeVoice.voiceId.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = activeVoice.thaiTitle,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "เสียงแค่นี้เท่านั้น",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = activeVoice.genderToneTh,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${activeVoice.sampleDescriptionTh} — ระบบตั้งค่าให้ใช้เฉพาะเสียงนี้โดยตัดเสียงอื่นที่ไม่ต้องการออกแล้ว",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Active Voice",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GeminiVoice.entries.forEach { voice ->
                            val selected = config.voice == voice
                            Surface(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .width(175.dp)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onSelectVoice(voice) }
                                    .padding(12.dp)
                                    .testTag("voice_card_${voice.voiceId.lowercase()}")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = voice.thaiTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = voice.genderToneTh,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = voice.sampleDescriptionTh,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Routing Policy",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "โหมดการทำงานไฮบริด (Provider & Fallback Policy)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoutingPolicy.entries.forEach { policy ->
                        FilterChip(
                            selected = config.routingPolicy == policy,
                            onClick = { onSelectRoutingPolicy(policy) },
                            label = { Text(policy.titleTh) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("policy_chip_${policy.id.lowercase()}")
                        )
                    }
                }
            }
        }

        // 5. Custom Thai Pronunciation Dictionary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Translate,
                            contentDescription = "Thai Pronunciation Dictionary",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "พจนานุกรมคำอ่านภาษาไทยเฉพาะ (${pronunciationRules.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    TextButton(
                        onClick = { showPronunciationSheet = !showPronunciationSheet },
                        modifier = Modifier.testTag("toggle_pronunciation_rules_button")
                    ) {
                        Text(if (showPronunciationSheet) "ซ่อนรายการ" else "จัดการคำอ่าน")
                    }
                }

                AnimatedVisibility(visible = showPronunciationSheet) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newPattern,
                                onValueChange = { newPattern = it },
                                label = { Text("คำศัพท์/ชื่อเฉพาะ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = newReplacement,
                                onValueChange = { newReplacement = it },
                                label = { Text("คำอ่านที่ต้องการ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newNote,
                                onValueChange = { newNote = it },
                                label = { Text("หมายเหตุบริบท") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    onAddPronunciationRule(newPattern, newReplacement, newNote)
                                    newPattern = ""
                                    newReplacement = ""
                                    newNote = ""
                                },
                                enabled = newPattern.isNotBlank() && newReplacement.isNotBlank()
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "เพิ่มคำอ่าน")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("เพิ่ม")
                            }
                        }

                        pronunciationRules.forEach { rule ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${rule.thaiPattern}  →  ${rule.spokenReplacement}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = rule.note,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeletePronunciationRule(rule.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "ลบกฎคำอ่าน",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
