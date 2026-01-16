package org.fossify.messages.activities

import android.os.Bundle
import android.speech.tts.Voice
import android.widget.SeekBar
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityCustomizationNotificationsBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.TTSHelper

class CustomizationNotificationsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityCustomizationNotificationsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupTopAppBar(binding.customizationAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.customizationHolder)

        binding.settingsNotificationsLabel.setTextColor(getProperPrimaryColor())

        setupSystemNotificationSettings()
        setupUseNaturalVoices()
        setupTtsVoice()
        setupTTSSettings()
    }

    private fun setupSystemNotificationSettings() = binding.apply {
        settingsSystemNotificationSettingsHolder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupUseNaturalVoices() = binding.apply {
        settingsUseNaturalVoices.isChecked = config.useNaturalVoices
        updateTTSSlidersState(config.useNaturalVoices)

        settingsUseNaturalVoicesHolder.setOnClickListener {
            settingsUseNaturalVoices.toggle()
            val isChecked = settingsUseNaturalVoices.isChecked
            config.useNaturalVoices = isChecked
            updateTTSSlidersState(isChecked)

            if (!isChecked) {
                TTSHelper.getInstance(this@CustomizationNotificationsActivity).stop()
            }
        }
    }

    private fun updateTTSSlidersState(isEnabled: Boolean) = binding.apply {
        settingsTtsSpeedSeekbar.isEnabled = isEnabled
        settingsTtsPitchSeekbar.isEnabled = isEnabled
        settingsTestTtsVoiceButton.isEnabled = isEnabled // 1. Disable Test Button

        val alpha = if (isEnabled) 1.0f else 0.5f
        settingsTtsSpeedHolder.alpha = alpha
        settingsTtsPitchHolder.alpha = alpha
        settingsTtsVoiceHolder.alpha = alpha
    }

    private fun setupTtsVoice() = binding.apply {
        val ttsHelper = TTSHelper.getInstance(this@CustomizationNotificationsActivity)
        val voices = ttsHelper.getAvailableVoices() ?: emptyList()
        val friendlyVoices = getFriendlyVoiceList(voices)

        settingsTtsVoice.text = friendlyVoices.find { it.second == config.selectedTtsVoice }?.first ?: getString(R.string.default_text)
        settingsTtsVoiceHolder.setOnClickListener {
            val items = ArrayList(friendlyVoices.mapIndexed { index, pair -> RadioItem(index, pair.first, pair.second) })
            val selectedIndex = items.indexOfFirst { it.value == config.selectedTtsVoice }

            RadioGroupDialog(this@CustomizationNotificationsActivity, items, selectedIndex) {
                config.selectedTtsVoice = it as String
                settingsTtsVoice.text = friendlyVoices.find { it.second == config.selectedTtsVoice }?.first
                ttsHelper.setupVoice()
            }
        }

        settingsTestTtsVoiceButton.setOnClickListener {
            ttsHelper.speak(getString(R.string.tts_test_message))
        }
    }

    private fun getFriendlyVoiceList(voices: List<Voice>): List<Pair<String, String>> {
        return voices.filter { it.locale.language == "en" }.map { voice ->
            val quality = if (voice.isNetworkConnectionRequired) "Natural" else "Standard"
            val gender = when {
                voice.name.contains("female", ignoreCase = true) -> "Female"
                voice.name.contains("male", ignoreCase = true) -> "Male"
                else -> "Voice"
            }

            val displayName = "${voice.locale.displayCountry} - $gender ($quality)"
            Pair(displayName, voice.name)
        }.toMutableList().apply {
            add(0, Pair(getString(R.string.default_text), ""))
        }
    }

    private fun setupTTSSettings() = binding.apply {
        // 2. Helper to format the float value to string (e.g. "1.0x")
        fun formatValue(value: Float): String = String.format("%.1fx", value)

        // --- SPEED SETTINGS ---
        val currentSpeed = config.ttsSpeed
        settingsTtsSpeedSeekbar.progress = ((currentSpeed - 0.5f) * 10).toInt()
        settingsTtsSpeedValue.text = formatValue(currentSpeed) // Set initial text

        settingsTtsSpeedSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val value = (p / 10.0f) + 0.5f
                settingsTtsSpeedValue.text = formatValue(value) // Update text dynamically
                if (fromUser) config.ttsSpeed = value
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                TTSHelper.getInstance(this@CustomizationNotificationsActivity).setupVoice()
            }
        })

        // --- PITCH SETTINGS ---
        val currentPitch = config.ttsPitch
        settingsTtsPitchSeekbar.progress = ((currentPitch - 0.5f) * 10).toInt()
        settingsTtsPitchValue.text = formatValue(currentPitch) // Set initial text

        settingsTtsPitchSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val value = (p / 10.0f) + 0.5f
                settingsTtsPitchValue.text = formatValue(value) // Update text dynamically
                if (fromUser) config.ttsPitch = value
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                TTSHelper.getInstance(this@CustomizationNotificationsActivity).setupVoice()
            }
        })
    }
}
