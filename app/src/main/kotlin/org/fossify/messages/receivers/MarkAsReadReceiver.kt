package org.fossify.messages.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.extensions.markThreadMessagesRead
import org.fossify.messages.helpers.MARK_AS_READ
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.refreshConversations

class MarkAsReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MARK_AS_READ) {
            val threadId = intent.getLongExtra(THREAD_ID, 0L)
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
                context.markThreadMessagesRead(threadId)
                context.conversationsDB.markRead(threadId)
                refreshConversations()
            }
        }
    }
}
