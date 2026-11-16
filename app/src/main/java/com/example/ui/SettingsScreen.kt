package com.example.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.tts.GeminiTtsModel
import com.example.tts.RoutingPolicy
import com.example.tts.SynthesisConfig

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreenContent(
        uiState = uiState,
        onSelectRoutingPolicy = settingsViewModel::selectRoutingPolicy,
        onSetAutoFallbackEnabled = settingsViewModel::setAutoFallbackEnabled,
        onSetDisableRewritingForNav = settingsViewModel::setDisableRewritingForNavigation,
        onSelectGeminiModel = settingsViewModel::selectGeminiModel,
        onUpdateDailyQuotaLimit = settingsViewModel::updateDailyQuotaThreshold,
        onDownloadF5Model = settingsViewModel::downloadF5Model,
        onUninstallF5Model = settingsViewModel::uninstallF5Model,
        onRefreshLocalEngines = settingsViewModel::refreshLocalFallbackEngines,
        onClearAudioCache = settingsViewModel::clearAudioCache,
        onToggleDarkTheme = settingsViewModel::toggleDarkTheme,
        onDismissStatusMessage = settingsViewModel::dismissStatusMessage,
        modifier = modifier
    )
}

@Composable
fun SettingsScreenContent(
    uiState: SettingsUiState,
    onSelectRoutingPolicy: (RoutingPolicy) -> Unit,
    onSetAutoFallbackEnabled: (Boolean) -> Unit,
    onSetDisableRewritingForNav: (Boolean) -> Unit,
    onSelectGeminiModel: (GeminiTtsModel) -> Unit,
    onUpdateDailyQuotaLimit: (Int) -> Unit,
    onDownloadF5Model: () -> Unit,
    onUninstallF5Model: () -> Unit,
    onRefreshLocalEngines: () -> Unit,
    onClearAudioCache: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onDismissStatusMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Settings Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "การตั้งค่า (Settings)",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                IconButton(
                    onClick = onToggleDarkTheme,
                    modifier = Modifier.testTag("toggle_dark_theme_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "สลับธีม"
                    )
                }
            }
            Text(
                text = "จัดการเอนจินสังเคราะห์เสียง ดาวน์โหลดโมเดลออฟไลน์ และระบบสำรอง",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Status Toast / Message Bar
        AnimatedVisibility(visible = uiState.statusMessageTh != null) {
            uiState.statusMessageTh?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissStatusMessage) {
                            Text("ปิด")
                        }
                    }
                }
            }
        }

        // Card 1: Automatic Engine Priority & Fallback
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "ลำดับความสำคัญของเอนจิน (Engine Priority)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Explicit Priority Chain Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ลำดับการสลับเอนจินอัตโนมัติ (Fallback Priority):",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = SynthesisConfig.DETAILED_ENGINE_PRIORITY,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Routing Policy Selection
                Text(
                    text = "โหมดการทำงานของระบบ (Routing Policy):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val policies = listOf(
                        RoutingPolicy.AUTO_HYBRID to "ไฮบริดอัตโนมัติ (Auto)",
                        RoutingPolicy.OFFLINE_ONLY to "ออฟไลน์ 100% (Offline)",
                        RoutingPolicy.CLOUD_ONLY to "Cloud เท่านั้น (Gemini)"
                    )
                    policies.forEach { (policy, label) ->
                        val isSelected = uiState.routingPolicy == policy
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectRoutingPolicy(policy) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("policy_chip_${policy.id.lowercase()}")
                        )
                    }
                }

                HorizontalDivider()

                // Navigation Rewrite Disable Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ปิดการดัดแปลงคำลงท้ายสำหรับคำสั่งนำทาง",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "คงรูปประโยคนำทางสั้นกระชับ ไม่เติมคำลงท้าย เพื่อความรวดเร็วและปลอดภัย",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.disableRewritingForNavigation,
                        onCheckedChange = onSetDisableRewritingForNav,
                        modifier = Modifier.testTag("disable_nav_rewrite_switch")
                    )
                }
            }
        }

        // Card 2: Gemini Cloud Configuration
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "การตั้งค่า Gemini Cloud TTS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Model Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "โมเดล Gemini Cloud TTS:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GeminiTtsModel.entries.forEach { model ->
                            val isSelected = uiState.selectedGeminiModel == model
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectGeminiModel(model) },
                                label = { Text(model.displayName) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gemini_model_${model.modelId}")
                            )
                        }
                    }
                }

                // Daily Cloud Quota Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "จำกัดโควตา Cloud ประจำวัน (Daily Quota)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${uiState.dailyCloudUsageCount} / ${uiState.dailyCloudQuotaLimit} ครั้ง",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiState.dailyCloudQuotaLimit.toFloat(),
                        onValueChange = { onUpdateDailyQuotaLimit(it.toInt()) },
                        valueRange = 10f..200f,
                        steps = 19,
                        modifier = Modifier.testTag("daily_quota_slider")
                    )
                    Text(
                        text = "หากใช้เกินโควตาประจำวัน ระบบจะสลับไปใช้ F5-TTS-THAI ออฟไลน์โดยอัตโนมัติ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Card 3: F5-TTS-THAI Model Download Manager
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "ตัวจัดการโมเดล F5-TTS-THAI ออฟไลน์",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.isF5Downloaded) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (uiState.isF5Downloaded) "ติดตั้งแล้ว" else "ยังไม่ได้ดาวน์โหลด",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isF5Downloaded) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Text(
                    text = "โมเดล F5-TH-ONNX (~810 MB) ไม่ได้ฝังรวมใน APK เพื่อประหยัดขนาดแอป สามารถเลือกดาวน์โหลดหรือลบเพื่อคืนพื้นที่ได้ทุกเมื่อ พร้อมตรวจสอบ SHA-256",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Storage usage row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = "พื้นที่ที่ใช้: ${uiState.installedF5Bytes / (1024 * 1024)} MB",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "พื้นที่ว่าง: ${uiState.freeDiskBytes / (1024 * 1024 * 1024)} GB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Download Progress
                if (uiState.isDownloadingF5) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "กำลังดาวน์โหลดและตรวจสอบ SHA-256...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Actions: Download or Uninstall
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!uiState.isF5Downloaded) {
                        Button(
                            onClick = onDownloadF5Model,
                            enabled = !uiState.isDownloadingF5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_f5_model_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ดาวน์โหลดโมเดล (~810 MB)")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onUninstallF5Model,
                            enabled = !uiState.isDownloadingF5,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("uninstall_f5_model_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ถอนการติดตั้งโมเดล (คืนพื้นที่)")
                        }
                    }
                }
            }
        }

        // Card 4: Android Fallback Engine & Diagnostics
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "เอนจินสำรองของระบบ (System TTS) & การวินิจฉัย",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Anti-Recursion Safety Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ระบบป้องกันการเรียกวนซ้ำตนเอง (Anti-Recursion) เปิดทำงานอยู่",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Installed System Engine Info
                Text(
                    text = "เอนจินภายนอกที่ตรวจพบ: ${uiState.localEngineStatus.selectedExternalEnginePackage ?: "ไม่พบ (ใช้ F5-TTS / Thai VITS เป็นหลัก)"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefreshLocalEngines,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("refresh_engines_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ตรวจสอบเอนจิน")
                    }

                    OutlinedButton(
                        onClick = onClearAudioCache,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_cache_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ล้างแคชเสียง")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
