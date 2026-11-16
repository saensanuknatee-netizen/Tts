package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackState
import com.example.data.local.VoiceProfileEntity
import com.example.tts.GeminiVoice
import com.example.tts.ResponseStyle
import com.example.tts.SynthesisConfig
import com.example.tts.VoiceStylePreset

@Composable
fun VoiceStudioScreen(
    config: SynthesisConfig,
    synthesisState: SynthesisUiState,
    playbackState: PlaybackState,
    voiceProfiles: List<VoiceProfileEntity>,
    previewSampleText: String,
    onUpdatePreviewSampleText: (String) -> Unit,
    onPreviewSpeech: (String) -> Unit,
    onStopPlayback: () -> Unit,
    onCustomInstructionChange: (String) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onExpressivenessChange: (Int) -> Unit,
    onPitchChange: (Float) -> Unit,
    onNaturalPausesChange: (Int) -> Unit,
    onSelectResponseStyle: (ResponseStyle) -> Unit,
    onSaveProfile: (String) -> Unit,
    onApplyProfile: (VoiceProfileEntity) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }

    val isPlaying = playbackState.isPlaying
    val isSynthesizing = synthesisState is SynthesisUiState.Synthesizing

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("บันทึกพรีเซ็ตเสียง (Save Custom Preset)") },
            text = {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("ชื่อพรีเซ็ต (เช่น เพื่อนนำทางเสียงหวาน)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            onSaveProfile(newProfileName.trim())
                            newProfileName = ""
                            showSaveDialog = false
                        }
                    },
                    enabled = newProfileName.isNotBlank(),
                    modifier = Modifier.testTag("confirm_save_preset_button")
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Studio Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "สตูดิโอปรับแต่งเสียง (Voice Studio)",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
            Text(
                text = "ปรับแต่งสไตล์เสียง คำสั่งการออกเสียง ความเร็ว ความมีชีวิตชีวา และบันทึกพรีเซ็ต",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Section 1: Voice Preview Player
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Hearing,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ทดลองฟังเสียงพรีวิว (Voice Preview)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${config.voice.voiceId} · ${config.thaiVoicePersonality.titleTh.substringBefore(" (")}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = previewSampleText,
                    onValueChange = onUpdatePreviewSampleText,
                    label = { Text("ข้อความสำหรับทดลองฟังเสียง") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preview_sample_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2,
                    maxLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (isPlaying) {
                                onStopPlayback()
                            } else {
                                onPreviewSpeech(previewSampleText)
                            }
                        },
                        enabled = previewSampleText.isNotBlank() && !isSynthesizing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("preview_listen_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPlaying) "หยุด" else "ทดลองฟังเสียงนี้")
                    }
                }
            }
        }

        // Section 2: Custom Voice Style Instructions
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "คำสั่งสไตล์เสียงที่กำหนดเอง (Voice Style Instructions)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Gemini TTS รองรับ speech_metadata.style นำคำสั่งภาษาไทยนี้ไปกำกับอารมณ์และบุคลิกเสียงโดยตรง",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = config.customStyleInstruction,
                    onValueChange = onCustomInstructionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_style_instruction_field"),
                    label = { Text("คำสั่งกำกับเสียง (Prompt Instruction)") },
                    placeholder = { Text("เช่น พูดด้วยน้ำเสียงอารมณ์ดี กระตือรือร้น เป็นธรรมชาติ...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick instruction chips
                Text(
                    text = "เทมเพลตคำสั่งสไตล์ด่วน:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickPrompts = listOf(
                        "เพื่อนสนิท" to "พูดด้วยน้ำเสียงอารมณ์ดี กระตือรือร้น เป็นธรรมชาติ เหมือนเพื่อนสนิทนั่งข้างคนขับ พูดอย่างอบอุ่น มีชีวิตชีวา และฟังสบาย ไม่พูดแข็งหรือเป็นหุ่นยนต์",
                        "เลขาผู้ช่วย" to "พูดด้วยน้ำเสียงนุ่มทุ้ม สุภาพ ใจเย็น อบอุ่นและมั่นใจ เหมือนเลขาส่วนตัวที่คอยช่วยเหลือ พูดชัดเจน จังหวะนุ่มนวล มีความเป็นธรรมชาติ",
                        "สารคดีนุ่มลึก" to "พูดภาษาไทยด้วยน้ำเสียงสุขุม ลุ่มลึก น่าเชื่อถือ จังหวะสม่ำเสมอ เหมือนผู้บรรยายสารคดีมืออาชีพ",
                        "ร่าเริงสดใส" to "พูดด้วยน้ำเสียงร่าเริง สดใส มีพลังบวก พูดเร็วคล่องแคล่วและเป็นกันเอง"
                    )
                    quickPrompts.forEach { (label, prompt) ->
                        Surface(
                            onClick = { onCustomInstructionChange(prompt) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Speed, Expressiveness & Acoustic Sliders
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "การควบคุมความเร็วและมิติเสียง (Acoustic Controls)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                // Speed Slider (0.75x - 1.50x)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ความเร็วในการพูด (Speed)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.2fx", config.speechRate),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.speechRate,
                        onValueChange = onSpeechRateChange,
                        valueRange = 0.75f..1.50f,
                        steps = 15,
                        modifier = Modifier.testTag("speed_slider")
                    )
                }

                // Expressiveness Slider (0% - 100%)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ความมีชีวิตชีวา / อารมณ์ (Expressiveness)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${config.expressiveness}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.expressiveness.toFloat(),
                        onValueChange = { onExpressivenessChange(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("expressiveness_slider")
                    )
                }

                // Pitch Slider (0.80x - 1.20x)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ระดับเสียงทุ้ม/แหลม (Pitch)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format("%.2fx", config.pitch),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.pitch,
                        onValueChange = onPitchChange,
                        valueRange = 0.80f..1.20f,
                        steps = 8,
                        modifier = Modifier.testTag("pitch_slider")
                    )
                }

                // Natural Pauses (0% - 100%)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "จังหวะหยุดพักหายใจ (Natural Pauses)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${config.naturalPauses}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = config.naturalPauses.toFloat(),
                        onValueChange = { onNaturalPausesChange(it.toInt()) },
                        valueRange = 0f..100f,
                        modifier = Modifier.testTag("natural_pauses_slider")
                    )
                }
            }
        }

        // Section 4: Response Style (What is said vs How it sounds)
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "สไตล์การตอบคำถาม (Response Style)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "ควบคุมความยาวและรูปแบบเนื้อหา (สิ่งที่พูด) แยกอิสระจากน้ำเสียง (วิธีพูด)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResponseStyle.entries.forEach { style ->
                        val isSelected = config.responseStyle == style
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectResponseStyle(style) },
                            label = { Text(style.titleTh.substringBefore(" (")) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // Section 5: Save & Manage Custom Presets
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
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
                        text = "พรีเซ็ตเสียงที่บันทึกไว้ (Saved Presets)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_preset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("บันทึกพรีเซ็ตนี้")
                    }
                }

                if (voiceProfiles.isEmpty()) {
                    Text(
                        text = "ยังไม่มีพรีเซ็ตที่บันทึกไว้ ปรับแต่งค่าด้านบนแล้วกด 'บันทึกพรีเซ็ตนี้'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        voiceProfiles.forEach { profile ->
                            OutlinedCard(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = profile.profileName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "เสียง: ${profile.voiceId} · เร็ว: ${profile.speechRate}x · มิติ: ${profile.expressiveness}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = { onApplyProfile(profile) },
                                            modifier = Modifier.testTag("apply_preset_${profile.id}")
                                        ) {
                                            Text("ใช้งาน")
                                        }
                                        IconButton(
                                            onClick = { onDeleteProfile(profile.id) },
                                            modifier = Modifier.testTag("delete_preset_${profile.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "ลบพรีเซ็ต",
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

        Spacer(modifier = Modifier.height(16.dp))
    }
}
