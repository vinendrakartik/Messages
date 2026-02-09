package org.fossify.messages.helpers

import android.content.Context
import android.util.Log
import org.fossify.messages.extensions.config

fun Context.logDebug(tag: String, message: String) {
    if (config.enableDebugLogs) {
        Log.d(tag, message)
    }
}

fun Context.logError(tag: String, message: String, throwable: Throwable? = null) {
    if (config.enableDebugLogs) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
}
