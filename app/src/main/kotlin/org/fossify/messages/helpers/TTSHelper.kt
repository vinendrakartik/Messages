package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.fossify.messages.extensions.config
import java.util.Locale

// 1. Change constructor: Take 'context' as a parameter, but don't make it a 'val' in the constructor signature
class TTSHelper private constructor(context: Context) {

    // 2. Force it to be ApplicationContext here.
    // This guarantees we never hold an Activity reference, satisfying the Linter/Memory safety.
    private val context = context.applicationContext

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingMessages = mutableListOf<String>()

    companion object {
        private const val TAG = "TTSHelper"

        @SuppressLint("StaticFieldLeak") // <--- ADD THIS LINE
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
                // Process queue
                pendingMessages.forEach { speak(it) }
                pendingMessages.clear()
            } else {
                context.logError(TAG, "Initialization failed")
            }
        }, defaultEngine)
    }

    fun getAvailableVoices(): List<Voice>? {
        return tts?.voices?.toList()?.sortedBy { it.name }
    }

    fun setVoice(voiceName: String) {
        val targetVoice = tts?.voices?.find { it.name == voiceName }
        if (targetVoice != null) {
            tts?.voice = targetVoice
        } else {
            tts?.voice = tts?.defaultVoice
        }
    }

    fun setupVoice() {
        val tts = tts ?: return
        val currentLocale = Locale.getDefault()

        val speed = context.config.ttsSpeed
        tts.setSpeechRate(speed)

        // Only log speed if needed, otherwise it spams logs
        context.logDebug(TAG, "TTS Speed: Applied=$speed")

        tts.setPitch(context.config.ttsPitch)

        try {
            val voices = tts.voices
            if (!voices.isNullOrEmpty()) {
                val selectedVoiceName = context.config.selectedTtsVoice
                val selectedVoice = if (selectedVoiceName.isNotEmpty()) {
                    voices.find { it.name == selectedVoiceName }
                } else {
                    findBestVoice(voices, currentLocale)
                }

                if (selectedVoice != null) {
                    tts.voice = selectedVoice
                    logVoiceDetails(selectedVoice) // Optional logging
                } else {
                    tts.language = currentLocale
                }
            }
        } catch (e: Exception) {
            context.logError(TAG, "Error setting voice", e)
            tts.language = currentLocale
        }
    }

    private fun findBestVoice(voices: Set<Voice>, currentLocale: Locale): Voice? {
        // 1. Try Indian Female High Quality (Network/Neural)
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

        // 2. Try Standard Indian Female
        voices.find { voice ->
            val isIndian = voice.locale.language == "en" && voice.locale.country == "IN"
            val isFemale = voice.name.contains("female", ignoreCase = true) ||
                voice.name.contains("-x-end") ||
                voice.name.contains("-x-ena")
            isIndian && isFemale
        }?.let { return it }

        // 3. Try any Neural/Network voice for the current language
        if (context.config.useNaturalVoices) {
            voices.find { voice ->
                voice.locale.language == currentLocale.language &&
                    (voice.name.contains("network", ignoreCase = true) ||
                        voice.name.contains("neural", ignoreCase = true))
            }?.let { return it }
        }

        // 4. Fallback to offline voice
        return voices.find {
            it.locale.language == currentLocale.language && !it.isNetworkConnectionRequired
        }
    }

    private fun logVoiceDetails(voice: Voice) {
        val quality = if (voice.name.contains("network", true)) "Neural/Network" else "Standard/Local"
        val latency = if (voice.isNetworkConnectionRequired) "Internet Required" else "Offline Ready"
        context.logDebug(TAG, "TTS Voice: ${voice.name}, $quality, $latency")
    }

    fun speak(text: String) {
        // MOVED CHECK HERE: Check logic BEFORE adding to queue
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode

        if (ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            context.logDebug(TAG, "TTS Skipped: Device in Silent/Vibrate mode")
            return
        }

        if (isInitialized) {
            // Ensure voice settings are fresh (in case config changed)
            setupVoice()
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            // Only add to pending if we are NOT in silent mode
            pendingMessages.add(text)
        }
    }

    fun stop() {
        tts?.stop()
    }


}
