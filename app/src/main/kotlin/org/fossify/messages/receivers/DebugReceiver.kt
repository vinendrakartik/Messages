package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.NotificationHelper
import org.fossify.messages.helpers.logDebug

/**
 * A receiver used ONLY for testing purposes to simulate incoming SMS messages.
 * Trigger via: adb shell am broadcast -a org.fossify.messages.TEST_SMS --es address "SENDER" --es body "Message Content"
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "org.fossify.messages.TEST_SMS") {
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
