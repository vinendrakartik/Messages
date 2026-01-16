package com.vk.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import com.vk.messages.extensions.deleteMessage
import com.vk.messages.extensions.updateLastConversationMessage
import com.vk.messages.helpers.IS_MMS
import com.vk.messages.helpers.MESSAGE_ID
import com.vk.messages.helpers.THREAD_ID
import com.vk.messages.helpers.refreshConversations
import com.vk.messages.helpers.refreshMessages

class DeleteSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(THREAD_ID, 0L)
        val messageId = intent.getLongExtra(MESSAGE_ID, 0L)
        val isMms = intent.getBooleanExtra(IS_MMS, false)
        
        val otp = intent.getStringExtra("otp")
        val isTransaction = intent.getBooleanExtra("is_transaction", false)

        val notificationId = when {
            otp != null -> otp.hashCode()
            isTransaction -> intent.getIntExtra("transaction_hash", 0)
            else -> threadId.hashCode()
        }

        if (notificationId != 0) {
            context.notificationManager.cancel(notificationId)
        }

        ensureBackgroundThread {
            context.deleteMessage(messageId, isMms)
            context.updateLastConversationMessage(threadId)
            refreshMessages()
            refreshConversations()
        }
    }
}
