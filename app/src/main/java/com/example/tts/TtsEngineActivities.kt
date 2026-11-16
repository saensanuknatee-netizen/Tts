package com.example.tts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.R
import com.example.ui.theme.MyApplicationTheme
import java.util.ArrayList

/**
 * Responds to TextToSpeech.Engine.ACTION_CHECK_TTS_DATA so Android OS Settings recognizes
 * Thai (tha-THA) and English (eng-USA) voices as available without missing data.
 */
class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val returnIntent = Intent()
        val available = ArrayList<String>().apply {
            add("tha-THA")
            add("tha")
            add("th-TH")
            add("th")
            add("eng-USA")
            add("eng")
        }
        val unavailable = ArrayList<String>()
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, available)
        returnIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, unavailable)
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnIntent)
        finish()
    }
}

/**
 * Responds to TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT when the user taps "Listen to an example"
 * in Android System Text-to-Speech settings.
 */
class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent?.getStringExtra("language").orEmpty()
        val sample = if (lang.startsWith("en", ignoreCase = true)) {
            getString(R.string.tts_sample_text_en)
        } else {
            getString(R.string.tts_sample_text_th)
        }
        val resultIntent = Intent().apply {
            putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}

/**
 * Handles TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA.
 */
class InstallVoiceDataActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ชุดข้อมูลเสียงภาษาไทยพร้อมใช้งานแล้ว",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SiamVoice Hybrid TTS มีทั้งระบบสังเคราะห์เสียง Gemini 3.8 TTS และชุดสังเคราะห์เสียงภาษาไทยแบบออฟไลน์ภายในแอปพร้อมใช้งานทันที",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                setResult(RESULT_OK)
                                finish()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("เสร็จสิ้น (Done)")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Settings activity invoked when the user taps the gear icon next to SiamVoice Hybrid TTS
 * in Android System TTS Settings.
 */
class TtsEngineSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        com.example.ui.SettingsScreen(
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                startActivity(Intent(this@TtsEngineSettingsActivity, MainActivity::class.java))
                                finish()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("เปิดแอป SiamVoice TTS Studio เต็มรูปแบบ")
                        }
                    }
                }
            }
        }
    }
}

