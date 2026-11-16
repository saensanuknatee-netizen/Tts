package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.HomeScreen
import com.example.ui.SettingsScreen
import com.example.ui.SettingsViewModel
import com.example.ui.TextToSpeechViewModel
import com.example.ui.VoiceStudioScreen
import com.example.ui.theme.MyApplicationTheme

enum class MainDestination(
    val route: String,
    val labelTh: String,
    val icon: ImageVector,
    val testTag: String
) {
    HOME("home", "หน้าหลัก", Icons.Filled.RecordVoiceOver, "nav_tab_home"),
    VOICE_STUDIO("voice_studio", "สตูดิโอเสียง", Icons.Filled.Tune, "nav_tab_voice_studio"),
    SETTINGS("settings", "ตั้งค่า", Icons.Filled.Settings, "nav_tab_settings");

    companion object {
        val STUDIO get() = HOME
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val vm: TextToSpeechViewModel = viewModel()
            val isDarkTheme by vm.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                SiamVoiceApp(viewModel = vm)
            }
        }
    }
}

@Composable
fun SiamVoiceApp(
    viewModel: TextToSpeechViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var currentTab by rememberSaveable { mutableStateOf(MainDestination.HOME) }

    if (currentTab != MainDestination.HOME) {
        BackHandler {
            currentTab = MainDestination.HOME
        }
    }

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val inputText by viewModel.inputText.collectAsStateWithLifecycle()
    val previewSampleText by viewModel.previewSampleText.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val synthesisState by viewModel.synthesisState.collectAsStateWithLifecycle()
    val lastOutcome by viewModel.lastOutcome.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val voiceProfiles by viewModel.voiceProfiles.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar {
                        MainDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.labelTh
                                    )
                                },
                                label = { Text(dest.labelTh) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail {
                        MainDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.labelTh
                                    )
                                },
                                label = { Text(dest.labelTh) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                when (currentTab) {
                    MainDestination.HOME -> {
                        HomeScreen(
                            inputText = inputText,
                            config = config,
                            synthesisState = synthesisState,
                            lastOutcome = lastOutcome,
                            playbackState = playbackState,
                            onTextChange = viewModel::updateInputText,
                            onSelectVoice = viewModel::selectVoice,
                            onSelectPersonality = viewModel::selectThaiVoicePersonality,
                            onSelectPoliteEnding = viewModel::selectThaiPoliteEnding,
                            onSynthesizeAndPlay = {
                                val spokenText = if (inputText.isNotBlank()) {
                                    com.example.tts.ThaiTextOptimizer.applyPoliteEnding(
                                        text = inputText,
                                        ending = config.thaiPoliteEnding,
                                        disableRewritingForNavigation = config.disableRewritingForNavigation
                                    )
                                } else inputText

                                viewModel.synthesizeSpecificText(
                                    text = spokenText,
                                    isNavPhrase = false,
                                    bypassCache = false,
                                    autoPlay = true,
                                    updateMainInput = false
                                )
                            },
                            onStopPlayback = viewModel::stopPlayback,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    MainDestination.VOICE_STUDIO -> {
                        VoiceStudioScreen(
                            config = config,
                            synthesisState = synthesisState,
                            playbackState = playbackState,
                            voiceProfiles = voiceProfiles,
                            previewSampleText = previewSampleText,
                            onUpdatePreviewSampleText = viewModel::updatePreviewSampleText,
                            onPreviewSpeech = { text ->
                                viewModel.synthesizeSpecificText(
                                    text = text,
                                    isNavPhrase = false,
                                    bypassCache = false,
                                    autoPlay = true,
                                    updateMainInput = false
                                )
                            },
                            onStopPlayback = viewModel::stopPlayback,
                            onCustomInstructionChange = viewModel::updateCustomStyleInstruction,
                            onSpeechRateChange = viewModel::updateSpeechRate,
                            onExpressivenessChange = viewModel::updateExpressiveness,
                            onPitchChange = viewModel::updatePitch,
                            onNaturalPausesChange = viewModel::updateNaturalPauses,
                            onSelectResponseStyle = viewModel::selectResponseStyle,
                            onSaveProfile = viewModel::saveCurrentVoiceProfile,
                            onApplyProfile = viewModel::applyVoiceProfile,
                            onDeleteProfile = viewModel::deleteVoiceProfile,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    MainDestination.SETTINGS -> {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
