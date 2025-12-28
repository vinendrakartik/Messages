package org.fossify.messages.helpers

import android.util.Log

data class TransactionInfo(
    val amount: String,
    val isDebit: Boolean,
    val source: String, // Bank or Card name
    val participant: String? // The "XYZ" (merchant or sender)
)

fun String.extractTransactionInfo(): TransactionInfo? {
    val body = this.lowercase()
    Log.d("TransactionDetector", "Processing message: $body")

    // 1. Identify if it's a transaction (ensure it's not an OTP/Verification message)
    val otpKeywords = listOf("otp", "verification", "verify", "code", "secret")
    if (otpKeywords.any { body.contains(it) }) {
        Log.d("TransactionDetector", "Message rejected: OTP keyword found")
        return null
    }

    val debitKeywords = listOf("debited", "spent", "sent from", "withdrawn", "deducted", "paid from", "debit", "paid to", "sent to")
    val creditKeywords = listOf("credited", "received", "received in", "topped up", "credit")
    
    val isDebit = debitKeywords.any { body.contains(it) }
    val isCredit = creditKeywords.any { body.contains(it) }
    
    if (!isDebit && !isCredit) {
        Log.d("TransactionDetector", "Message rejected: Not a debit or credit transaction")
        return null
    }

    // 2. Extract Amount
    val amountRegex = Regex("""(?i)(?:rs\.?|inr)\s?([\d,]+(?:\.\d{1,2})?)""")
    val amountMatch = amountRegex.find(body)
    val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")
    if (amount == null) {
        Log.d("TransactionDetector", "Message rejected: Amount not found")
        return null
    }
    Log.d("TransactionDetector", "Extracted amount: $amount")

    // 3. Extract Source (Bank/Card)
    val banks = listOf("axis", "hdfc", "idfc", "kotak", "slice", "federal", "sbi", "icici", "jupiter", "canara", "bob")
    var sourceName = banks.find { body.contains(it) }?.uppercase() ?: ""
    
    if (sourceName.isEmpty()) {
        val sourceRegex = Regex("""(?:a/c|account)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""")
        val sourceMatch = sourceRegex.find(body)
        sourceName = sourceMatch?.groupValues?.get(1)?.uppercase() ?: "YOUR"
    }
    
    val sourceSuffix = if (body.contains("card")) "card" else "bank"
    val fullSource = "$sourceName $sourceSuffix"
    Log.d("TransactionDetector", "Extracted source: $fullSource")

    // 4. Extract Participant (XYZ)
    val participantRegex = Regex("""(?i)(?:at|to|from)\s+([a-z0-9\s]{3,30})(?:\s+on|\s+via|\s+vide|\s+ending|\s+xx|\s+a/c|\.|$)""")
    val participantMatches = participantRegex.findAll(body)
    
    var participant: String? = null
    for (match in participantMatches) {
        val candidate = match.groupValues[1].trim().uppercase()
        if (candidate.contains(sourceName) || candidate.length < 3) continue
        
        val noise = listOf("inr", "rs", "bank", "card", "a/c", "account", "your")
        if (!noise.any { candidate.lowercase().contains(it) }) {
            participant = candidate
            break
        }
    }
    Log.d("TransactionDetector", "Extracted participant: $participant")

    return TransactionInfo(amount, isDebit, fullSource, participant)
}
