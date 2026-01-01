package org.fossify.messages.helpers

fun String.extractOTP(): String? {
    // 1. Check for common "trigger" words to ensure it's actually an OTP
    val otpKeywords = listOf("code", "otp", "verification", "verify", "secret", "one time", "pin", "password")
    val containsKeyword = otpKeywords.any { this.contains(it, ignoreCase = true) }

    if (!containsKeyword) return null
    
    // Avoid false positives for mutual fund statements, folio numbers, and PDF passwords
    val exclusionKeywords = listOf("folio", "nav", "statement", "pan as the password", "to open", "pdf", "units")
    if (exclusionKeywords.any { this.contains(it, ignoreCase = true) }) return null

    // 2. Extract 4 to 8 digit numbers
    // We try to avoid matching years or common dates by ensuring it's not part of a date-like structure
    val pattern = Regex("(?<!\\d-|/|:)\\b(\\d{4,8})\\b(?!-|/|:)")
    return pattern.find(this)?.groupValues?.get(1)
}
