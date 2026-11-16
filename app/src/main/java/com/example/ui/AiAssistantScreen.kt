package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AssistantModelMode
import com.example.ai.ChatMessageItem
import com.example.ai.LiveVoiceState
import com.example.ai.LyriaModelOption
import com.example.ai.MusicGenState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    selectedMode: AssistantModelMode,
    chatMessages: List<ChatMessageItem>,
    isChatLoading: Boolean,
    liveVoiceState: LiveVoiceState,
    musicGenState: MusicGenState,
    onSelectMode: (AssistantModelMode) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onSynthesizeChatReply: (String) -> Unit,
    onToggleLiveSession: (Boolean) -> Unit,
    onSendLivePrompt: (String) -> Unit,
    onGenerateMusic: (String, LyriaModelOption) -> Unit,
    onPlayMusicBytes: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var chatInput by remember { mutableStateOf("") }
    var livePromptInput by remember { mutableStateOf("สวัสดีครับ ช่วยแนะนำการออกเสียงภาษาไทยสำหรับระบบนำทางหน่อยครับ") }
    var musicPrompt by remember {
        mutableStateOf("Ambient modern Thai traditional ranat and warm synth pad background track for voiceover")
    }
    var selectedLyriaModel by remember { mutableStateOf(LyriaModelOption.CLIP_PREVIEW) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onToggleLiveSession(granted)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Multi-Turn Gemini Chatbot (Argon High Thinking / Flash Search+Maps / Flash-Lite Fast)
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
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = "Gemini Intelligence",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ผู้ช่วย AI อัจฉริยะ (Argon High Thinking · Search/Maps · Flash-Lite)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantModelMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { onSelectMode(mode) },
                            label = { Text(mode.titleTh) },
                            modifier = Modifier.testTag("assistant_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = selectedMode.subtitleTh,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // Scrollable Chat Thread
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chatMessages.takeLast(8).forEach { msg ->
                        val isUser = msg.role == "user"
                        val isError = msg.role == "error"
                        Surface(
                            color = when {
                                isError -> MaterialTheme.colorScheme.errorContainer
                                isUser -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.modelBadge,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!isUser && !isError) {
                                        TextButton(
                                            onClick = { onSynthesizeChatReply(msg.text) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.RecordVoiceOver,
                                                contentDescription = "อ่านออกเสียงข้อความนี้",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ส่งไปสังเคราะห์เสียง TTS")
                                        }
                                    }
                                }
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Quick Prompt Suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            chatInput = "ช่วยแต่งคำสั่งนำทางจากสนามบินสุวรรณภูมิไปสยามพารากอนพร้อมคำอ่านที่ชัดเจน"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("สร้างบทนำทาง Maps", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            chatInput = "อธิบายความแตกต่างของการออกเสียงคำพ้องรูป เพลา และ แหน ในภาษาไทย"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("วิเคราะห์คำพ้องรูป", maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_chat_input"),
                        placeholder = { Text("ถาม Gemini 4 Argon หรือค้นหาข้อมูลด้วย Search/Maps...") },
                        maxLines = 3
                    )
                    Button(
                        onClick = {
                            onSendChatMessage(chatInput)
                            chatInput = ""
                        },
                        enabled = !isChatLoading && chatInput.isNotBlank(),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("assistant_send_button")
                    ) {
                        if (isChatLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "ส่งข้อความ"
                            )
                        }
                    }
                }
            }
        }

        // 2. Gemini 3.8 Live API Real-Time Voice Conversation Card
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
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Gemini 3.8 Live API",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "สนทนาเสียงเรียลไทม์ (Gemini 3.8 Live API · gemini-3.8-live)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Text(
                    text = "เชื่อมต่อแบบสองทิศทางความหน่วงต่ำผ่าน Firebase AI Logic LiveSession เพื่อทดสอบการสนทนาภาษาไทยแบบโต้ตอบทันที",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (liveVoiceState) {
                    is LiveVoiceState.Disconnected -> {
                        Text(
                            text = "สถานะ: ยังไม่ได้เชื่อมต่อเซสชัน Live API",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is LiveVoiceState.Connecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("กำลังเชื่อมต่อ gemini-3.8-live...")
                        }
                    }
                    is LiveVoiceState.Connected -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = liveVoiceState.statusTextTh,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    is LiveVoiceState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = liveVoiceState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                val isConnected = liveVoiceState is LiveVoiceState.Connected
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isConnected) {
                                onToggleLiveSession(false)
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    onToggleLiveSession(true)
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("live_api_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = "Live Voice Session"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isConnected) {
                                "วางสายเซสชัน gemini-3.8-live"
                            } else {
                                "เริ่มการสนทนาเสียงสด (gemini-3.8-live)"
                            }
                        )
                    }
                }

                if (isConnected) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = livePromptInput,
                            onValueChange = { livePromptInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("ส่งข้อความกระตุ้นไปยังเซสชันเสียงสด") }
                        )
                        OutlinedButton(onClick = { onSendLivePrompt(livePromptInput) }) {
                            Text("ส่ง")
                        }
                    }
                }
            }
        }

        // 3. Lyria 3 Music / Backing Audio Generator Card
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
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = "Lyria 3 Music Generation",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "สร้างดนตรีประกอบเสียงพูด (Lyria 3 Music Generation)",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LyriaModelOption.entries.forEach { option ->
                        FilterChip(
                            selected = selectedLyriaModel == option,
                            onClick = { selectedLyriaModel = option },
                            label = { Text(option.titleTh) },
                            modifier = Modifier.testTag("lyria_chip_${option.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = musicPrompt,
                    onValueChange = { musicPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyria_prompt_input"),
                    label = { Text("คำอธิบายดนตรีประกอบ (Music Prompt)") },
                    maxLines = 2
                )

                Button(
                    onClick = { onGenerateMusic(musicPrompt, selectedLyriaModel) },
                    enabled = musicGenState !is MusicGenState.Loading && musicPrompt.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_music_button")
                ) {
                    if (musicGenState is MusicGenState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("กำลังสร้างดนตรีด้วย ${musicGenState.modelId}...")
                    } else {
                        Icon(imageVector = Icons.Filled.MusicNote, contentDescription = "สร้างดนตรี")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("สร้างดนตรีประกอบด้วย ${selectedLyriaModel.modelId}")
                    }
                }

                when (musicGenState) {
                    is MusicGenState.Success -> {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = musicGenState.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = { onPlayMusicBytes(musicGenState.wavBytes) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "เล่นดนตรี")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("เล่น")
                                }
                            }
                        }
                    }
                    is MusicGenState.Error -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = musicGenState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
