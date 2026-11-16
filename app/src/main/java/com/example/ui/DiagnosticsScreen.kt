package com.example.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppCheckDiagnosticState
import com.example.data.local.DiagnosticLogEntity
import com.example.tts.GeminiVoice
import com.example.tts.LocalTtsEngineStatus
import com.example.tts.QueueTicket
import com.example.tts.SynthesisConfig
import com.example.tts.SystemTtsServiceMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    appCheckState: AppCheckDiagnosticState,
    localEngineStatus: LocalTtsEngineStatus,
    config: SynthesisConfig,
    dailyCloudCount: Int,
    queueTickets: List<QueueTicket>,
    diagnosticLogs: List<DiagnosticLogEntity>,
    onUpdateDailyQuotaLimit: (Int) -> Unit,
    onRefreshLocalTts: () -> Unit,
    onClearDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serviceActive by SystemTtsServiceMonitor.serviceActive.collectAsState()
    val playbackState by SystemTtsServiceMonitor.playbackState.collectAsState()
    val lastBoundReq by SystemTtsServiceMonitor.lastBoundRequest.collectAsState()
    val totalServiceReqs by SystemTtsServiceMonitor.totalServiceRequests.collectAsState()
    val recursiveBlocked by SystemTtsServiceMonitor.recursiveCallsBlocked.collectAsState()

    val quotaErrorsCount = diagnosticLogs.count { it.isQuotaError }
    val networkErrorsCount = diagnosticLogs.count { it.isNetworkError }
    val fallbackCount = diagnosticLogs.count { it.fallbackTriggered }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. System-Wide Android TextToSpeechService Status & Installation Guide
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tts_service_status_card"),
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
                            imageVector = Icons.Filled.SettingsVoice,
                            contentDescription = "System TTS Service",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "สถานะบริการ Android TextToSpeechService",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SiamVoiceTtsService ลงทะเบียนใน AndroidManifest.xml พร้อมใช้งานแล้ว",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "สถานะการผูกบริการจากระบบ (Bound): ${if (serviceActive) "กำลังเชื่อมต่อ (Active)" else "พร้อมรับคำสั่ง (Standby)"} · คำขอสะสม: $totalServiceReqs ครั้ง",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "สถานะวงจรการเล่นเสียง (Playback Lifecycle): ${playbackState.labelTh} · บล็อกการเรียกซ้ำตัวเอง: $recursiveBlocked ครั้ง",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "คำขอล่าสุด: $lastBoundReq",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "คู่มือการเปิดใช้งานเป็นเครื่องยนต์หลักของระบบ Android (Installation Instructions):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "1. กดปุ่ม 'เปิดตั้งค่า Text-to-Speech ของระบบ Android' ด้านล่าง\n" +
                            "2. ในเมนู 'เครื่องยนต์ที่ต้องการ (Preferred engine)' ให้เลือก 'SiamVoice Hybrid Thai TTS'\n" +
                            "3. เลือกภาษาเป็น 'ไทย (ประเทศไทย)' เพื่อใช้เสียงสังเคราะห์ไฮบริดในแอปนำทางหรือ TalkBack ทั้งระบบ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            runCatching {
                                val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_system_tts_settings_button")
                    ) {
                        Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = "ตั้งค่า TTS ระบบ")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("เปิดตั้งค่า Text-to-Speech ของระบบ Android")
                    }
                }
            }
        }

        // 2. Security, App Check & Quota Threshold Controls
        item {
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
                            imageVector = Icons.Filled.Security,
                            contentDescription = "App Check & Quota",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "ความปลอดภัย Firebase App Check & การจัดการโควตา",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (appCheckState.initialized) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = "App Check Status",
                            tint = if (appCheckState.initialized) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Column {
                            Text(
                                text = "โหมดรับรองความปลอดภัย: ${appCheckState.mode}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = appCheckState.statusMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "การใช้งาน Gemini Cloud วันนี้",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "$dailyCloudCount / ${config.dailyCloudQuotaLimit} ครั้ง",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    val quotaProgress = (dailyCloudCount.toFloat() / config.dailyCloudQuotaLimit.coerceAtLeast(1).toFloat())
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { quotaProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    Text(
                        text = "ปรับเกณฑ์สลับไปใช้เสียง Local อัตโนมัติ (Usage Threshold): ${config.dailyCloudQuotaLimit} ครั้ง/วัน",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = config.dailyCloudQuotaLimit.toFloat(),
                        onValueChange = { onUpdateDailyQuotaLimit(it.toInt()) },
                        valueRange = 1f..150f,
                        modifier = Modifier.testTag("quota_threshold_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ข้อผิดพลาดโควตา: $quotaErrorsCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (quotaErrorsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ข้อผิดพลาดเครือข่าย: $networkErrorsCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (networkErrorsCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "สลับ Fallback สำเร็จ: $fallbackCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // 3. Available Cloud & Local Offline Voices Inventory
        item {
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
                                imageVector = Icons.Filled.Build,
                                contentDescription = "Voices Inventory",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "สถานะเอนจินออฟไลน์ & รายชื่อเสียงที่พร้อมใช้งาน",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        OutlinedButton(
                            onClick = onRefreshLocalTts,
                            modifier = Modifier.testTag("refresh_local_tts_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "ตรวจสอบอีกครั้ง")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ตรวจซ้ำ")
                        }
                    }

                    Text(
                        text = "รันไทม์ออฟไลน์ที่ใช้งานจริง: ${localEngineStatus.activeOfflineRuntime}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = localEngineStatus.statusDetailTh,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "ป้องกัน Recursive TTS (Self-Recursion Guard): " +
                                    if (localEngineStatus.selfRecursionPrevented) {
                                        "เปิดทำงาน (ข้ามแพ็กเกจตัวเอง ${localEngineStatus.selfPackageName} เสมอ)"
                                    } else {
                                        "ไม่ได้เปิด"
                                    },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "เอนจินภายนอกที่เลือกใช้เป็น Fallback: ${localEngineStatus.selectedExternalEnginePackage ?: "ไม่มี (ป้องกันการผูกกลับหาตัวเอง)"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "ONNX Runtime Neural TTS: ${if (localEngineStatus.onnxRuntimeAvailable) "พร้อมใช้งาน (v${localEngineStatus.onnxRuntimeVersion})" else "ไม่พร้อม"} · ไฟล์โมเดล VITS: ${if (localEngineStatus.onnxModelInstalled) "ติดตั้งแล้ว" else "ยังไม่พบ model.onnx"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "พาธไฟล์โมเดลออฟไลน์: ${localEngineStatus.onnxModelPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (localEngineStatus.onnxLicensingAndSpecsTh.isNotBlank()) {
                                Text(
                                    text = localEngineStatus.onnxLicensingAndSpecsTh,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = "เอนจินระบบที่ตรวจพบ (${localEngineStatus.installedEngines.size}): " +
                            localEngineStatus.installedEngines.joinToString(", ").ifEmpty { "ไม่พบเอนจินภายนอกในคอนเทนเนอร์นี้" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "เสียง Gemini 3.8 TTS Cloud ที่รองรับ (${GeminiVoice.entries.size} เสียง): " +
                            GeminiVoice.entries.joinToString(", ") { it.voiceId },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. Request Queue Monitor
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "คิวคำขอสังเคราะห์เสียงล่าสุด (Request Queue & Backoff Monitor)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (queueTickets.isEmpty()) {
                        Text(
                            text = "ยังไม่มีรายการในคิว",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        queueTickets.take(8).forEach { ticket ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "[#${ticket.id}] ${ticket.textSnippet}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = ticket.status.labelTh,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "ผู้ให้บริการ: ${ticket.providerUsed} · ครั้งที่ ${ticket.attempt} · ${ticket.messageTh}",
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

        // 5. Diagnostic Logs Header & Items
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "บันทึกเหตุการณ์ระบบ โควตา และการเชื่อมต่อ (${diagnosticLogs.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (diagnosticLogs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearDiagnostics,
                        modifier = Modifier.testTag("clear_diagnostics_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "ล้างประวัติ")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ล้างบันทึก")
                    }
                }
            }
        }

        items(diagnosticLogs, key = { it.id }) { log ->
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeStr = fmt.format(Date(log.timestamp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (log.level) {
                        "ERROR" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                        "WARN" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "[$timeStr] ${log.level} · ${log.category} (${log.provider})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (log.fallbackTriggered) {
                            Text(
                                text = "AUTO-FALLBACK",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        text = log.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
