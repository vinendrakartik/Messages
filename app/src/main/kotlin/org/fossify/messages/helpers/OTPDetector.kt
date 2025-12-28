package org.fossify.messages.helpers

fun String.extractOTP(): String? {
    // 1. Check for common "trigger" words to ensure it's actually an OTP
    val otpKeywords = listOf("code", "otp", "verification", "verify", "secret", "one time")
    val containsKeyword = otpKeywords.any { this.contains(it, ignoreCase = true) }

    if (!containsKeyword) return null

    // 2. Extract 4 to 8 digit numbers
    val pattern = Regex("(\\b\\d{4,8}\\b)")
    return pattern.find(this)?.value
}
