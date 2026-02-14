package com.vk.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.vk.messages.BuildConfig
import com.vk.messages.extensions.config
import com.vk.messages.extensions.getNotificationBitmap
import com.vk.messages.helpers.NotificationHelper
import com.vk.messages.helpers.logDebug

/**
 * A receiver used ONLY for testing purposes to simulate incoming SMS messages.
 * Trigger via: adb shell am broadcast -a ${applicationId}.TEST_SMS --es address "SENDER" --es body "Message Content"
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = "${BuildConfig.APPLICATION_ID}.TEST_SMS"
        if (intent.action == action) {
            // SECURITY CHECK: Only allow test broadcasts if the user enabled Debug Logs in Settings
            if (context.config.enableDebugLogs) {
                val address = intent.getStringExtra("address") ?: "DEBUG"
                val body = intent.getStringExtra("body") ?: "No content"

                // Standard Android contact lookup to fix previous errors
                var senderName = address
                var photoUri: Uri? = null
                try {
                    val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
                    val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                            val photoIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)

                            if (nameIndex > -1) {
                                senderName = cursor.getString(nameIndex)
                            }
                            if (photoIndex > -1) {
                                val photoUriStr = cursor.getString(photoIndex)
                                if (photoUriStr != null) {
                                    photoUri = Uri.parse(photoUriStr)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    context.logDebug("DebugReceiver", "Contact lookup failed: ${e.message}")
                }

                val bitmap = context.getNotificationBitmap(photoUri?.toString() ?: "")

                // This triggers the detection logic, TTS, and Notification
                NotificationHelper(context).showMessageNotification(
                    messageId = System.currentTimeMillis(),
                    address = address,
                    body = body,
                    threadId = Math.abs(address.hashCode()).toLong(),
                    bitmap = bitmap,
                    sender = senderName
                )
            } else {
                context.logDebug("DebugReceiver", "Blocked TEST_SMS broadcast because Debug Logs are disabled.")
            }
        }
    }
}
