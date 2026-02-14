package com.vk.messages.helpers

import android.content.Context

fun Context.logDebug(tag: String, message: String) {
    // This is a no-op for release builds
}

fun Context.logError(tag: String, message: String, throwable: Throwable? = null) {
    // This is a no-op for release builds, but you might want to log to a crash reporting service here
}
