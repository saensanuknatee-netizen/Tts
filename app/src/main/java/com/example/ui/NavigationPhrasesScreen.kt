package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CachedAudioEntity
import com.example.tts.DEFAULT_THAI_NAVIGATION_PHRASES

@Composable
fun NavigationPhrasesScreen(
    cachedAudioList: List<CachedAudioEntity>,
    isPreCachingNav: Boolean,
    onSpeakNavPhrase: (String) -> Unit,
    onPreCacheAllNav: () -> Unit,
    onPlayCachedFile: (String) -> Unit,
    onExportCachedFile: (String, String) -> Unit,
    onClearAllCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCacheBytes = cachedAudioList.sumOf { it.fileSizeBytes }
    val totalCacheHits = cachedAudioList.sumOf { it.hitCount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
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
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = "Low-Latency Navigation",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "วลีนำทางความหน่วงต่ำ (Ultra-Low Latency Thai Navigation)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = "ออกแบบมาสำหรับคำสั่งนำทางสั้นๆ โดยให้ความสำคัญกับการสังเคราะห์ในเครื่อง (Local Synthesis) และการดึงไฟล์ WAV จากแคชความเร็วสูง (< 10 ms) เพื่อการเล่นเสียงทันที",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )

                    Button(
                        onClick = onPreCacheAllNav,
                        enabled = !isPreCachingNav,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("precache_nav_pack_button")
                    ) {
                        if (isPreCachingNav) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("กำลังเตรียมแคชวลีนำทางทั้งหมด...")
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Pre-cache Navigation Pack"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("เตรียมแคชวลีนำทางทั้งหมดล่วงหน้า (Pre-Cache 8 วลี)")
                        }
                    }
                }
            }
        }

        items(DEFAULT_THAI_NAVIGATION_PHRASES, key = { it.id }) { preset ->
            val cachedMatch = cachedAudioList.firstOrNull {
                it.originalText.trim() == preset.thaiText.trim() ||
                    it.normalizedText.trim() == preset.thaiText.trim()
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nav_phrase_card_${preset.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = preset.distanceBadge,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = preset.categoryTh,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (cachedMatch != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "พร้อมในแคช · ${cachedMatch.synthesisLatencyMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = preset.thaiText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onSpeakNavPhrase(preset.thaiText) },
                        modifier = Modifier.testTag("speak_nav_${preset.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "พูดวลีนำทาง"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (cachedMatch != null) "เล่นทันที" else "สังเคราะห์")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
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
                                imageVector = Icons.Filled.Storage,
                                contentDescription = "Audio Cache",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "คลังแคชเสียง WAV ในเครื่อง (${cachedAudioList.size} รายการ)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (cachedAudioList.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearAllCache,
                                modifier = Modifier.testTag("clear_cache_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteSweep,
                                    contentDescription = "ล้างแคช",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ล้างแคช")
                            }
                        }
                    }

                    Text(
                        text = "พื้นที่ใช้งานรวม: ${totalCacheBytes / 1024} KB · จำนวนการดึงแคชสะสม: $totalCacheHits ครั้ง",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (cachedAudioList.isEmpty()) {
                        Text(
                            text = "ยังไม่มีไฟล์เสียงในแคช กดสังเคราะห์เสียงหรือกดปุ่ม 'เตรียมแคชวลีนำทางทั้งหมดล่วงหน้า' ด้านบน",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider()
                        cachedAudioList.take(20).forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.originalText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.providerUsed} · ${entry.modelId} · ${entry.durationMs} ms · Hit ${entry.hitCount}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = { onPlayCachedFile(entry.wavFilePath) }) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = "เล่นไฟล์แคช",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onExportCachedFile(entry.wavFilePath, entry.originalText) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = "ส่งออกไฟล์ WAV",
                                            tint = MaterialTheme.colorScheme.primary
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
