package com.vk.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vk.messages.extensions.config
import com.vk.messages.helpers.NotificationHelper
import com.vk.messages.helpers.logDebug

/**
 * A receiver used ONLY for testing purposes to simulate incoming SMS messages.
 * Trigger via: adb shell am broadcast -a com.vk.messages.TEST_SMS --es address "SENDER" --es body "Message Content"
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.vk.messages.TEST_SMS") {
            // SECURITY CHECK: Only allow test broadcasts if the user enabled Debug Logs in Settings
            if (context.config.enableDebugLogs) {
                val address = intent.getStringExtra("address") ?: "DEBUG"
                val body = intent.getStringExtra("body") ?: "No content"

                // This triggers the detection logic, TTS, and Notification
                NotificationHelper(context).showMessageNotification(
                    messageId = System.currentTimeMillis(),
                    address = address,
                    body = body,
                    threadId = Math.abs(address.hashCode()).toLong(),
                    bitmap = null,
                    sender = address
                )
            } else {
                context.logDebug("DebugReceiver", "Blocked TEST_SMS broadcast because Debug Logs are disabled.")
            }
        }
    }
}
