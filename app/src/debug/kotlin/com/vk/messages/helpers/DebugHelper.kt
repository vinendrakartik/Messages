package com.vk.messages.helpers

import android.content.Context
import android.util.Log
import com.vk.messages.extensions.config

/**
 * A helper object for logging debug messages.
 * The debug receiver can be triggered via: adb shell am broadcast -a ${applicationId}.TEST_SMS --es address "SENDER" --es body "Message Content"
 */
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
