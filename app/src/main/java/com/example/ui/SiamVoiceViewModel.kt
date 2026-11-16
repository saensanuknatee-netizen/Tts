package com.example.ui

import android.app.Application

/**
 * Compatibility subclass of [TextToSpeechViewModel] so both [TextToSpeechViewModel]
 * and [SiamVoiceViewModel] can be instantiated seamlessly by ViewModelProvider.
 */
class SiamVoiceViewModel(application: Application) : TextToSpeechViewModel(application)
