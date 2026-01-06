package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.activities.ThreadActivity
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.shortcutHelper
import org.fossify.messages.messaging.isShortCodeWithLetters
import org.fossify.messages.receivers.DeleteSmsReceiver
import org.fossify.messages.receivers.DirectReplyReceiver
import org.fossify.messages.receivers.MarkAsReadReceiver
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val ttsHelper = TTSHelper.getInstance(context)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    private val otpChannelId = "otp_channel"
    private val transactionChannelId = "transaction_channel"
    private val defaultChannelId = NOTIFICATION_CHANNEL_ID

    private fun getSoundUri(isOtp: Boolean, isTransaction: Boolean): Uri? {
        // Suppress sound ONLY if it's a transaction AND TTS is enabled
        if (isTransaction && context.config.useNaturalVoices) return null

        val soundName = if (isOtp) "otp" else "message"
        val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)
        return if (resId != 0) {
            Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")
        } else {
            @Suppress("DEPRECATION")
            android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
        }
    }

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {
        if (context.config.mutedThreads.contains(threadId.toString())) {
            return
        }

        val otp = body.extractOTP()
        val isOtp = otp != null

        // Pass the address (header) to extractTransactionInfo for better bank detection
        val transaction = if (!isOtp) body.extractTransactionInfo(context, address) else null
        // Treat statement messages as non-transactional notifications
        val isTransaction = transaction != null && !transaction.isStatement

        if (otp != null) {
            copyToClipboard(otp)
        } else if (transaction != null && !transaction.isStatement) {
            handleTransactionTTS(transaction, body)
        }

        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())

        val notificationChannelId = when {
            isOtp -> otpChannelId
            isTransaction && context.config.useNaturalVoices -> transactionChannelId
            hasCustomNotifications -> threadId.toString()
            else -> defaultChannelId
        }

        when {
            isOtp -> createChannel(otpChannelId, context.getString(R.string.otp_notifications), true, true)
            isTransaction && context.config.useNaturalVoices -> createChannel(transactionChannelId, context.getString(R.string.transaction_notifications), false, false)
            !hasCustomNotifications -> createChannel(defaultChannelId, context.getString(R.string.channel_received_sms), false, false)
        }

        val notificationId = when {
            otp != null -> otp.hashCode()
            transaction != null && !transaction.isStatement -> transaction.hashCode()
            else -> threadId.hashCode()
        }

        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
            if (otp != null) {
                putExtra("otp", otp)
            }
            if (transaction != null && !transaction.isStatement) {
                putExtra("is_transaction", true)
                putExtra("transaction_hash", transaction.hashCode())
            }
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                markAsReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
            if (otp != null) {
                putExtra("otp", otp)
            }
            if (transaction != null && !transaction.isStatement) {
                putExtra("is_transaction", true)
                putExtra("transaction_hash", transaction.hashCode())
            }
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                deleteSmsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (!isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_send_vector,
                replyLabel,
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, notificationChannelId).apply {
            val contentBody = if (otp != null) context.getString(R.string.otp_message, otp, body) else body
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, contentBody, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(
                        NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(contentBody)
                    )
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messenger)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)

            // Suppress sound ONLY if TTS is enabled for this transaction
            if (isTransaction && context.config.useNaturalVoices) {
                setSound(null)
            } else {
                setSound(getSoundUri(isOtp, false), AudioManager.STREAM_NOTIFICATION)
            }
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(
            org.fossify.commons.R.drawable.ic_check_vector,
            context.getString(R.string.mark_as_read),
            markAsReadPendingIntent
        )
            .setChannelId(notificationChannelId)

        // Use the custom delete intent for OTP/Transactions too
        builder.addAction(
            org.fossify.commons.R.drawable.ic_delete_vector,
            context.getString(org.fossify.commons.R.string.delete),
            deleteSmsPendingIntent
        ).setChannelId(notificationChannelId)

        var shortcut = context.shortcutHelper.getShortcut(threadId)
        if (shortcut == null) {
            ensureBackgroundThread {
                shortcut = context.shortcutHelper.createOrUpdateShortcut(threadId)
                builder.setShortcutInfo(shortcut)
                notificationManager.notify(notificationId, builder.build())
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        } else {
            builder.setShortcutInfo(shortcut)
            notificationManager.notify(notificationId, builder.build())
            ensureBackgroundThread {
                context.shortcutHelper.reportReceiveMessageUsage(threadId)
            }
        }

        // Log the notification posting
        context.logDebug("NotificationHelper", "Posted notification id=$notificationId address=$address isOtp=$isOtp isTransaction=$isTransaction")
    }

    private fun handleTransactionTTS(transaction: TransactionInfo, originalBody: String) {
        if (!context.config.useNaturalVoices) return

        val amount = transaction.ttsAmount
        val source = transaction.source
        val participant = transaction.participant

        val ssmlText = when {
            transaction.isInterest -> {
                context.getString(R.string.ssml_interest_received, amount.toString(), source)
            }
            transaction.isDebit -> {
                if (participant != null) {
                    context.getString(R.string.ssml_amount_paid_to, amount.toString(), participant, source)
                } else {
                    context.getString(R.string.ssml_amount_paid, amount.toString(), source)
                }
            }
            else -> {
                if (participant != null) {
                    context.getString(R.string.ssml_amount_received_from, amount.toString(), participant, source)
                } else {
                    context.getString(R.string.ssml_amount_received, amount.toString(), source)
                }
            }
        }
        context.logDebug("NotificationHelper", "Message Body: $originalBody")
        context.logDebug("NotificationHelper", "SSML Text: $ssmlText")
        ttsHelper.speak(ssmlText)
    }

    private fun copyToClipboard(otp: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(context.getString(R.string.otp), otp)
            clipboard.setPrimaryClip(clip)
            context.logDebug("NotificationHelper", "Copied OTP to clipboard")
        } catch (t: Throwable) {
            context.logDebug("NotificationHelper", "Failed to copy OTP: ${t.message}")
        }
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications =
            context.config.customNotifications.contains(threadId.toString())
        val notificationChannelId =
            if (hasCustomNotifications) threadId.toString() else defaultChannelId
        if (!hasCustomNotifications) {
            createChannel(notificationChannelId, context.getString(R.string.message_not_sent_short), false, false)
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val summaryText =
            String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannelId)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createChannel(id: String, name: String, isOtp: Boolean, isTransaction: Boolean) {
        try {
            val soundUri = getSoundUri(isOtp, isTransaction)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val importance = IMPORTANCE_HIGH
            NotificationChannel(id, name, importance).apply {
                setBypassDnd(false)
                enableLights(true)
                if (isTransaction && context.config.useNaturalVoices) {
                    setSound(null, null)
                } else {
                    setSound(soundUri, audioAttributes)
                }
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        } catch (t: Throwable) {
            context.logDebug("NotificationHelper", "createChannel failed: ${t.message}")
        }
    }

    private fun getMessagesStyle(
        address: String,
        body: String,
        notificationId: Int,
        name: String?
    ): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage =
                NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        val currentNotification =
            notificationManager.activeNotifications.find { it.id == notificationId }
        val messagingStyle = currentNotification?.notification?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
        return messagingStyle?.messages ?: emptyList()
    }

    // Utility used by notifications
    private fun generateRandomId(): Int {
        return ((System.currentTimeMillis() % Int.MAX_VALUE).toInt() xor (Math.random() * Int.MAX_VALUE).toInt())
    }
}
