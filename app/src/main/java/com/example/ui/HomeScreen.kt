package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.PlaybackState
import com.example.tts.GeminiVoice
import com.example.tts.SynthesisConfig
import com.example.tts.SynthesisOutcome
import com.example.tts.ThaiPoliteEnding
import com.example.tts.ThaiTextOptimizer
import com.example.tts.ThaiVoicePersonality

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    inputText: String,
    config: SynthesisConfig,
    synthesisState: SynthesisUiState,
    lastOutcome: SynthesisOutcome?,
    playbackState: PlaybackState,
    onTextChange: (String) -> Unit,
    onSelectVoice: (GeminiVoice) -> Unit,
    onSelectPersonality: (ThaiVoicePersonality) -> Unit,
    onSelectPoliteEnding: (ThaiPoliteEnding) -> Unit,
    onSynthesizeAndPlay: () -> Unit,
    onStopPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Real-time preview of the spoken sentence after Thai Polite Ending processing
    val processedPreviewText by remember(inputText, config.thaiPoliteEnding, config.disableRewritingForNavigation) {
        derivedStateOf {
            if (inputText.isBlank()) {
                ""
            } else {
                ThaiTextOptimizer.applyPoliteEnding(
                    text = inputText,
                    ending = config.thaiPoliteEnding,
                    disableRewritingForNavigation = config.disableRewritingForNavigation
                )
            }
        }
    }

    val isSynthesizing = synthesisState is SynthesisUiState.Synthesizing
    val isPlaying = playbackState.isPlaying

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Header & Engine Priority Banner
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "SiamVoice TTS",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                )
            }
            Text(
                text = "ระบบสังเคราะห์เสียงพูดภาษาไทยอัจฉริยะ ลื่นไหลเป็นธรรมชาติ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Minimal Engine Priority Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ลำดับเอนจิน: Gemini Flash → Flash-Lite → F5-TTS-THAI → Android TTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Thai Voice Personality Presets (Primary feature)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "บุคลิกเสียงภาษาไทย (Voice Personality)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThaiVoicePersonality.entries.forEach { personality ->
                    val isSelected = config.thaiVoicePersonality == personality
                    ElevatedCard(
                        onClick = { onSelectPersonality(personality) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personality_${personality.id.lowercase()}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = if (isSelected) 3.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (personality == ThaiVoicePersonality.FRIENDLY_COMPANION) {
                                            Icons.Filled.Hearing
                                        } else if (personality == ThaiVoicePersonality.PERSONAL_ASSISTANT) {
                                            Icons.Filled.Person
                                        } else {
                                            Icons.Filled.RecordVoiceOver
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = personality.titleTh,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = personality.recommendedVoice.voiceId,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = personality.subtitleTh,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "เลือกอยู่",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Voice Selection Chips
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "เลือกเสียงพูด (Voice Model)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GeminiVoice.entries.forEach { voice ->
                    val isVoiceSelected = config.voice == voice
                    FilterChip(
                        selected = isVoiceSelected,
                        onClick = { onSelectVoice(voice) },
                        label = {
                            Text(
                                text = "${voice.voiceId} (${voice.genderToneTh.substringBefore(" ·")})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = if (isVoiceSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("voice_chip_${voice.voiceId.lowercase()}")
                    )
                }
            }
        }

        // Section 3: Thai Polite Sentence Ending System
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "คำลงท้ายประโยคสุภาพ (Polite Ending)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThaiPoliteEnding.entries.forEach { ending ->
                    val isEndingSelected = config.thaiPoliteEnding == ending
                    FilterChip(
                        selected = isEndingSelected,
                        onClick = { onSelectPoliteEnding(ending) },
                        label = {
                            Text(
                                text = ending.labelTh,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = if (isEndingSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        modifier = Modifier.testTag("ending_chip_${ending.id.lowercase()}")
                    )
                }
            }
        }

        // Quick sample navigation phrase chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "ประโยคตัวอย่างสำหรับทดสอบ:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val samples = listOf(
                    "อีก 300 เมตร เลี้ยวซ้าย",
                    "พบเส้นทางที่เร็กว่า",
                    "ถึงจุดหมายแล้ว",
                    "ข้างหน้ามีกล้องตรวจจับความเร็ว ขับขี่ด้วยความระมัดระวัง"
                )
                samples.forEach { sample ->
                    Surface(
                        onClick = { onTextChange(sample) },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("sample_chip_${sample.take(6)}")
                    ) {
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Section 4: Clean Text Input
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_text_field"),
                label = { Text("ข้อความที่ต้องการให้อ่าน (Thai Text)") },
                placeholder = { Text("พิมพ์ข้อความภาษาไทย หรือเลือกประโยคตัวอย่างด้านบน...") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        IconButton(
                            onClick = { onTextChange("") },
                            modifier = Modifier.testTag("clear_text_button")
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "ล้างข้อความ")
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${inputText.length} ตัวอักษร",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Live Spoken Text Preview Card (Showing exact sentence that will be uttered)
        AnimatedVisibility(
            visible = processedPreviewText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            OutlinedCard(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ข้อความที่จะพูดจริง (Final Spoken Text):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = processedPreviewText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Section 5: Prominent Listen/Stop Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isPlaying) {
                        onStopPlayback()
                    } else {
                        onSynthesizeAndPlay()
                    }
                },
                enabled = inputText.isNotBlank() && !isSynthesizing,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("listen_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "กำลังสร้างเสียง...",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else if (isPlaying) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "หยุดเล่น",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "หยุดเล่นเสียง",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "ฟังเสียง",
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ฟังเสียง (Listen)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        // Synthesis Outcome feedback (Engine used, latency, sample rate)
        lastOutcome?.let { outcome ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "สังเคราะห์สำเร็จโดย: ${outcome.providerUsed}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${outcome.latencyMs} ms · ${outcome.sampleRateHz} Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
