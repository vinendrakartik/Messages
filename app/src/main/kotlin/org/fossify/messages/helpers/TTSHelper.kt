package org.fossify.messages.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSHelper private constructor(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingMessages = mutableListOf<String>()

    companion object {
        @Volatile
        private var INSTANCE: TTSHelper? = null

        fun getInstance(context: Context): TTSHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        // 1. Get the current default engine from the system
        val defaultEngine = TextToSpeech(context, null).defaultEngine

        // 2. Re-initialize with that specific engine to bypass any auto-selection
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTSHelper", "Language not supported or missing data")
                } else {
                    isInitialized = true
                    pendingMessages.forEach { speak(it) }
                    pendingMessages.clear()
                }
            } else {
                Log.e("TTSHelper", "Initialization failed")
            }
        }, defaultEngine) // <--- This forces the system-set engine
    }

    fun speak(text: String) {
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            pendingMessages.add(text)
        }
    }

    fun stop() {
        tts?.stop()
    }

    // Call this only when the app is actually closing to free resources
    fun shutdown() {
        tts?.shutdown()
        INSTANCE = null
    }
}
