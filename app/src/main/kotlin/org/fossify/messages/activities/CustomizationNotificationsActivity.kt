package org.fossify.messages.activities

import android.os.Bundle
import android.widget.SeekBar
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
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

        val alpha = if (isEnabled) 1.0f else 0.5f
        settingsTtsSpeedHolder.alpha = alpha
        settingsTtsPitchHolder.alpha = alpha
    }

    private fun setupTTSSettings() = binding.apply {
        settingsTtsSpeedSeekbar.apply {
            progress = ((config.ttsSpeed - 0.5f) * 10).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) config.ttsSpeed = (p / 10.0f) + 0.5f
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    TTSHelper.getInstance(this@CustomizationNotificationsActivity).setupVoice()
                }
            })
        }

        settingsTtsPitchSeekbar.apply {
            progress = ((config.ttsPitch - 0.5f) * 10).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) config.ttsPitch = (p / 10.0f) + 0.5f
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {
                    TTSHelper.getInstance(this@CustomizationNotificationsActivity).setupVoice()
                }
            })
        }
    }
}
