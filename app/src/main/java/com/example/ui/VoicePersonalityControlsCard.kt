package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PsychologyAlt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.VoiceProfileEntity
import com.example.tts.ControlSupportMechanism
import com.example.tts.ProviderCapabilityProfile
import com.example.tts.ResponseStyle
import com.example.tts.SynthesisConfig
import com.example.tts.VoiceStylePreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoicePersonalityAndStyleSection(
    config: SynthesisConfig,
    previewSampleText: String,
    voiceProfiles: List<VoiceProfileEntity>,
    onSelectVoiceStyle: (VoiceStylePreset) -> Unit,
    onSelectResponseStyle: (ResponseStyle) -> Unit,
    onPreviewStylePreset: (VoiceStylePreset) -> Unit,
    onUpdatePreviewSampleText: (String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val capability = remember(config.routingPolicy) {
        ProviderCapabilityProfile.forRoutingPolicy(config.routingPolicy)
    }
    var newProfileName by remember { mutableStateOf("") }
    var expandAdvancedSliders by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Voice Style Presets (9 Presets) + Live Preview Comparison
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = "Voice Style Presets",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "บุคลิกเสียงพูด (9 Voice Style Presets)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = config.style.titleEn,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceStylePreset.entries.forEach { preset ->
                        val selected = config.style == preset
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectVoiceStyle(preset) },
                            label = { Text(preset.titleTh) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("style_chip_${preset.id.lowercase()}")
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${config.style.titleTh} — ${config.style.subtitleTh}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "คำสั่งเสียง: \"${config.style.promptDirectiveTh}\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Custom Style Instruction input (always available, highlighted in Custom mode)
                OutlinedTextField(
                    value = config.customStyleInstruction,
                    onValueChange = onCustomInstructionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_style_instruction_input"),
                    label = { Text("คำสั่งบุคลิกเสียงเพิ่มเติม (Custom Style Instructions)") },
                    placeholder = { Text("เช่น ออกเสียงนุ่มลึกเหมือนผู้บรรยายสารคดี เว้นจังหวะท้ายประโยค...") },
                    singleLine = true
                )

                HorizontalDivider()

                // Live Preview & Style Comparison Bar
                Text(
                    text = "ฟังเปรียบเทียบสไตล์เสียงสด (Live Style Preview · แคชอัตโนมัติเพื่อประหยัดโควตา)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedTextField(
                    value = previewSampleText,
                    onValueChange = onUpdatePreviewSampleText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_preview_sample_input"),
                    label = { Text("ประโยคภาษาไทยสำหรับทดสอบเปรียบเทียบสไตล์เสียง") },
                    singleLine = true
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceStylePreset.entries.forEach { preset ->
                        OutlinedButton(
                            onClick = { onPreviewStylePreset(preset) },
                            modifier = Modifier.testTag("live_preview_btn_${preset.id.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "ฟังตัวอย่าง ${preset.titleEn}",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ฟัง ${preset.titleEn}")
                        }
                    }
                }
            }
        }

        // 2. Response Style Intelligence (6 Independent Response Styles)
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
                            imageVector = Icons.Filled.PsychologyAlt,
                            contentDescription = "Response Style Intelligence",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "รูปแบบการตอบกลับอัจฉริยะ (Response Style Intelligence)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = "${config.responseStyle.titleEn} + ${config.style.titleEn}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "ผสานรวม Response Style และ Voice Style ได้อย่างอิสระ:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResponseStyle.entries.forEach { rStyle ->
                        FilterChip(
                            selected = config.responseStyle == rStyle,
                            onClick = { onSelectResponseStyle(rStyle) },
                            label = { Text(rStyle.titleTh) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("response_style_chip_${rStyle.id.lowercase()}")
                        )
                    }
                }

                Text(
                    text = "${config.responseStyle.subtitleTh} (${config.responseStyle.aiSystemInstructionTh})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 3. Advanced Voice Controls (10 Sliders with Provider Capability Transparency)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Advanced Voice Controls",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "การควบคุมเสียงขั้นสูง (10 Advanced Voice Controls)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "ความเข้ากันได้: ${capability.providerNameTh}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { expandAdvancedSliders = !expandAdvancedSliders },
                        modifier = Modifier.testTag("toggle_advanced_controls_button")
                    ) {
                        Text(if (expandAdvancedSliders) "ย่อ" else "ขยาย")
                    }
                }

                // Always show Speed & Pitch & Expressiveness at top, plus remaining 7 when expanded
                CapabilityFloatSliderRow(
                    labelTh = "1. ความเร็วการพูด (Speaking Speed)",
                    valueText = "${"%.2f".format(config.speechRate)}x",
                    value = config.speechRate,
                    valueRange = 0.50f..1.80f,
                    mechanism = capability.speedSupport,
                    testTag = "speech_rate_slider",
                    onValueChange = onSpeechRateChange
                )

                CapabilityFloatSliderRow(
                    labelTh = "2. ระดับเสียงสูง-ต่ำ (Pitch)",
                    valueText = "${"%.2f".format(config.pitch)}x",
                    value = config.pitch,
                    valueRange = 0.65f..1.45f,
                    mechanism = capability.pitchSupport,
                    testTag = "pitch_slider",
                    onValueChange = onPitchChange
                )

                CapabilityFloatSliderRow(
                    labelTh = "3. ความดังเสียง (Volume Gain)",
                    valueText = "${(config.volume * 100).toInt()}%",
                    value = config.volume,
                    valueRange = 0.25f..1.50f,
                    mechanism = capability.volumeSupport,
                    testTag = "volume_slider",
                    onValueChange = onVolumeChange
                )

                CapabilityIntSliderRow(
                    labelTh = "4. ความเข้มข้นของอารมณ์ (Emotional Intensity)",
                    value = config.emotionalIntensity,
                    mechanism = capability.emotionalIntensitySupport,
                    testTag = "emotional_intensity_slider",
                    onValueChange = onEmotionalIntensityChange
                )

                CapabilityIntSliderRow(
                    labelTh = "5. การสื่ออารมณ์และทำนองเสียง (Expressiveness)",
                    value = config.expressiveness,
                    mechanism = capability.expressivenessSupport,
                    testTag = "expressiveness_slider",
                    onValueChange = onExpressivenessChange
                )

                AnimatedVisibility(visible = expandAdvancedSliders) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CapabilityIntSliderRow(
                            labelTh = "6. จังหวะเว้นวรรคธรรมชาติ (Natural Pauses)",
                            value = config.naturalPauses,
                            mechanism = capability.naturalPausesSupport,
                            testTag = "natural_pauses_slider",
                            onValueChange = onNaturalPausesChange
                        )

                        CapabilityIntSliderRow(
                            labelTh = "7. ความชัดเจนของคำอ่านและวรรณยุกต์ (Pronunciation Clarity)",
                            value = config.pronunciationClarity,
                            mechanism = capability.pronunciationClaritySupport,
                            testTag = "pronunciation_clarity_slider",
                            onValueChange = onPronunciationClarityChange
                        )

                        CapabilityIntSliderRow(
                            labelTh = "8. ความอบอุ่นนุ่มนวลของเนื้อเสียง (Voice Warmth)",
                            value = config.voiceWarmth,
                            mechanism = capability.voiceWarmthSupport,
                            testTag = "voice_warmth_slider",
                            onValueChange = onVoiceWarmthChange
                        )

                        CapabilityIntSliderRow(
                            labelTh = "9. ระดับความเป็นทางการ (Formality)",
                            value = config.formality,
                            mechanism = capability.formalitySupport,
                            testTag = "formality_slider",
                            onValueChange = onFormalityChange
                        )

                        CapabilityIntSliderRow(
                            labelTh = "10. ความเสถียรของเสียงพูด (Speech Stability)",
                            value = config.speechStability,
                            mechanism = capability.speechStabilitySupport,
                            testTag = "speech_stability_slider",
                            onValueChange = onSpeechStabilityChange
                        )

                        // Preferred Language selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ภาษาหลัก (Preferred Language):", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("th-TH" to "ไทย (th-TH)", "en-US" to "ไทย-อังกฤษ").forEach { (code, label) ->
                                    FilterChip(
                                        selected = config.preferredLanguage == code,
                                        onClick = { onPreferredLanguageChange(code) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Smart Voice Profiles (Save & Load Custom Presets)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmarks,
                        contentDescription = "Smart Voice Profiles",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "โปรไฟล์เสียงอัจฉริยะ (Smart Voice Profiles · ${voiceProfiles.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_profile_name_input"),
                        placeholder = { Text("ตั้งชื่อโปรไฟล์เสียงใหม่...") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            onSaveVoiceProfile(newProfileName)
                            newProfileName = ""
                        },
                        enabled = newProfileName.isNotBlank(),
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "บันทึกโปรไฟล์")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("บันทึก")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    voiceProfiles.forEach { profile ->
                        val isCurrentMatch = profile.voiceStyleId == config.style.id &&
                            profile.responseStyleId == config.responseStyle.id &&
                            profile.voiceId == config.voice.voiceId
                        Surface(
                            color = if (isCurrentMatch) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .width(235.dp)
                                .border(
                                    width = if (isCurrentMatch) 2.dp else 1.dp,
                                    color = if (isCurrentMatch) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onApplyVoiceProfile(profile) }
                                .padding(12.dp)
                                .testTag("voice_profile_card_${profile.id}")
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = profile.profileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { onDeleteVoiceProfile(profile.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "ลบโปรไฟล์",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${profile.voiceId} · ${profile.voiceStyleId} · ${profile.responseStyleId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Speed ${"%.2f".format(profile.speechRate)}x · Expr ${profile.expressiveness}% · ${profile.routingPolicyId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityFloatSliderRow(
    labelTh: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    mechanism: ControlSupportMechanism,
    testTag: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelTh,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mechanism.isSupported) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (mechanism.isSupported) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = mechanism.badgeTh,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mechanism.isSupported) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(valueText, style = MaterialTheme.typography.labelMedium)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = mechanism.isSupported,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun CapabilityIntSliderRow(
    labelTh: String,
    value: Int,
    mechanism: ControlSupportMechanism,
    testTag: String,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelTh,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mechanism.isSupported) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (mechanism.isSupported) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = mechanism.badgeTh,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (mechanism.isSupported) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text("$value%", style = MaterialTheme.typography.labelMedium)
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            enabled = mechanism.isSupported,
            modifier = Modifier.testTag(testTag)
        )
    }
}
