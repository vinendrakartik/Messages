package org.fossify.messages.helpers

import android.util.Log

data class TransactionInfo(
    val amount: String,
    val isDebit: Boolean,
    val source: String, // Bank or Card name
    val participant: String? // The "XYZ" (merchant or sender)
)

fun String.extractTransactionInfo(sender: String? = null): TransactionInfo? {
    val body = this.lowercase()
    val senderUpper = sender?.uppercase() ?: ""
    Log.d("TransactionDetector", "Processing message from $senderUpper: $body")

    // 1. Filter out false positives (Insurance, generic bills, etc.)
    val falsePositiveKeywords = listOf("insurance", "prudential", "bill payment", "recharge successful")
    if (falsePositiveKeywords.any { body.contains(it) }) {
        Log.d("TransactionDetector", "Message rejected: False positive keyword found")
        return null
    }

    // 2. Identify if it's a transaction (ensure it's not an OTP/Verification message)
    val otpKeywords = listOf("otp", "verification", "verify", "code", "secret")
    if (otpKeywords.any { body.contains(it) }) {
        Log.d("TransactionDetector", "Message rejected: OTP keyword found")
        return null
    }

    val debitKeywords = listOf("debited", "spent", "sent", "withdrawn", "deducted", "debit", "paid", "txn of")
    val creditKeywords = listOf("credited", "received", "topped up", "credit", "added to")
    
    val isDebit = debitKeywords.any { body.contains(it) }
    val isCredit = creditKeywords.any { body.contains(it) }
    
    // 3. Identify Bank from Sender or Body
    val bankMap = mapOf(
        "HDFCBK" to "HDFC",
        "INDUSB" to "IndusInd",
        "ICICIB" to "ICICI",
        "AXISBK" to "Axis",
        "SBIN" to "SBI",
        "KOTAKB" to "Kotak",
        "JTEDGE" to "Jupiter",
        "FDRL" to "Federal",
        "CANBK" to "Canara",
        "BARODA" to "BOB"
    )

    var detectedBank = bankMap.entries.find { senderUpper.contains(it.key) }?.value ?: ""
    if (detectedBank.isEmpty()) {
        val banks = listOf("axis", "hdfc", "idfc", "kotak", "slice", "federal", "sbi", "icici", "jupiter", "canara", "bob", "indusind")
        detectedBank = banks.find { body.contains(it) }?.uppercase() ?: ""
    }

    val hasAmountIndicator = body.contains("rs") || body.contains("inr") || body.contains("₹")
    
    if (!isDebit && !isCredit && !(detectedBank.isNotEmpty() && hasAmountIndicator)) {
        Log.d("TransactionDetector", "Message rejected: Not a recognized transaction pattern")
        return null
    }

    // 4. Extract Amount (Supports Rs, INR, ₹, and comma formatting)
    val amountRegex = Regex("""(?i)(?:rs\.?|inr|₹)\s?([\d,]+(?:\.\d{1,2})?)""")
    val amountMatch = amountRegex.find(body)
    val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")
    if (amount == null) {
        Log.d("TransactionDetector", "Message rejected: Amount not found")
        return null
    }
    Log.d("TransactionDetector", "Extracted amount: $amount")

    // 5. Refine Source Name
    if (detectedBank.isEmpty()) {
        val sourceRegex = Regex("""(?:a/c|account)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""")
        val sourceMatch = sourceRegex.find(body)
        detectedBank = sourceMatch?.groupValues?.get(1)?.uppercase() ?: "YOUR"
    }
    
    val sourceSuffix = if (body.contains("card")) "card" else "bank"
    val fullSource = "$detectedBank $sourceSuffix"

    // 6. Extract Participant (XYZ)
    val participantRegex = Regex("""(?i)(?:at|to|from)\s+([a-z0-9\s]{3,30})(?:\s+on|\s+via|\s+vide|\s+ending|\s+xx|\s+a/c|\.|$)""")
    val participantMatches = participantRegex.findAll(body)
    
    var participant: String? = null
    for (match in participantMatches) {
        val candidate = match.groupValues[1].trim().uppercase()
        if (candidate.contains(detectedBank) || candidate.length < 3) continue
        
        val noise = listOf("inr", "rs", "bank", "card", "a/c", "account", "your")
        if (!noise.any { candidate.lowercase().contains(it) }) {
            participant = candidate
            break
        }
    }
    Log.d("TransactionDetector", "Extracted participant: $participant")

    return TransactionInfo(amount, isDebit || !isCredit, fullSource, participant)
}
