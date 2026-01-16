package com.vk.messages.helpers

import android.content.Context
import android.util.Log
import com.vk.messages.extensions.config

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
