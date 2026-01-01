package org.fossify.messages.helpers

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import org.fossify.messages.extensions.config
import java.util.Locale

class TTSHelper private constructor(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingMessages = mutableListOf<String>()

    companion object {
        private const val TAG = "TTSHelper"

        @Volatile
        private var INSTANCE: TTSHelper? = null

        fun getInstance(context: Context): TTSHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSHelper(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        val defaultEngine = TextToSpeech(context, null).defaultEngine
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                setupVoice()
                pendingMessages.forEach { speak(it) }
                pendingMessages.clear()
            } else {
                Log.e(TAG, "Initialization failed")
            }
        }, defaultEngine)
    }

    fun setupVoice() {
        val tts = tts ?: return
        val currentLocale = Locale.getDefault()

        // Apply speed and pitch from settings
        tts.setSpeechRate(context.config.ttsSpeed)
        tts.setPitch(context.config.ttsPitch)

        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val selectedVoice = findBestVoice(voices, currentLocale)

                if (selectedVoice != null) {
                    tts.voice = selectedVoice
                    logVoiceDetails(selectedVoice)
                } else {
                    tts.language = currentLocale
                    Log.d(TAG, "Using default system voice selection for $currentLocale")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting voice", e)
            tts.language = currentLocale
        }
    }

    private fun findBestVoice(voices: Set<Voice>, currentLocale: Locale): Voice? {
        // PRIORITY 1: Indian Female Network/Neural Voice (Expressive)
        voices.find { voice ->
            val isIndian = voice.locale.language == "en" && voice.locale.country == "IN"
            val isFemale = voice.name.contains("female", ignoreCase = true) ||
                voice.name.contains("en-in-x-end", ignoreCase = true) ||
                voice.name.contains("en-in-x-ena", ignoreCase = true) ||
                voice.name.contains("Neerja", ignoreCase = true)

            val isHighQuality = voice.name.contains("network", ignoreCase = true) ||
                voice.name.contains("neural", ignoreCase = true)

            isIndian && isFemale && isHighQuality
        }?.let { return it }

        // PRIORITY 2: Any Indian Female Voice (even local)
        voices.find { voice ->
            val isIndian = voice.locale.language == "en" && voice.locale.country == "IN"
            val isFemale = voice.name.contains("female", ignoreCase = true) ||
                voice.name.contains("-x-end") ||
                voice.name.contains("-x-ena")
            isIndian && isFemale
        }?.let { return it }

        // PRIORITY 3: Best available Local/Network voice for current system language
        if (context.config.useNaturalVoices) {
            voices.find { voice ->
                voice.locale.language == currentLocale.language &&
                    (voice.name.contains("network", ignoreCase = true) ||
                        voice.name.contains("neural", ignoreCase = true))
            }?.let { return it }
        }

        // PRIORITY 4: Fallback to best local (offline) voice
        return voices.find {
            it.locale.language == currentLocale.language && !it.isNetworkConnectionRequired
        }
    }

    private fun logVoiceDetails(voice: Voice) {
        val quality = if (voice.name.contains("network", true)) "Neural/Network" else "Standard/Local"
        val latency = if (voice.isNetworkConnectionRequired) "Internet Required" else "Offline Ready"

        Log.d(TAG, """
            --- TTS VOICE ACTIVATED ---
            Name: ${voice.name}
            Locale: ${voice.locale}
            Quality Type: $quality
            Connectivity: $latency
            Features: ${voice.features}
            ---------------------------
        """.trimIndent())
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

    fun shutdown() {
        tts?.shutdown()
        INSTANCE = null
    }
}
