package org.fossify.messages.helpers

import android.util.Log

data class TransactionInfo(
    val amount: String,
    val isDebit: Boolean,
    val source: String, // Bank or Card name
    val participant: String? // The "XYZ" (merchant or sender)
)

private val FALSE_POSITIVE_KEYWORDS = listOf("insurance", "prudential", "bill payment", "recharge successful","folio","NAV")
private val OTP_KEYWORDS = listOf("otp", "verification", "verify", "code", "secret")
private val DEBIT_KEYWORDS = listOf("debited", "spent", "sent", "withdrawn", "deducted", "debit", "paid", "txn of")
private val CREDIT_KEYWORDS = listOf("credited", "received", "topped up", "credit", "added to", "deposited")

private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr|₹)\s?([\d,]+(?:\.\d{1,2})?)""")
private val SOURCE_FALLBACK_REGEX = Regex("""(?:a/c|account|card)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""")
private val PARTICIPANT_REGEX = Regex("""(?i)(?:at|to|from)\s+([a-z0-9\s@\.]{3,30})(?:\s+on|\s+via|\s+vide|\s+ending|\s+xx|\s+a/c|\.|$)""")

private val SOURCE_MAP = mapOf(
    "INDUSB" to "IndusInd", "INDUSI" to "IndusInd",
    "HDFCBK" to "HDFC", "HDFCBN" to "HDFC",
    "AXISBK" to "Axis", "UTIBNK" to "Axis", "AXISBN" to "Axis",
    "ICICIB" to "ICICI", "ICICIP" to "ICICI", "ICICIT" to "ICICI",
    "SBIN" to "SBI", "SBIINB" to "SBI", "SBIPS" to "SBI", "SBICRD" to "SBI Card",
    "JTEDGE" to "Jupiter", "JUPITR" to "Jupiter",
    "KOTAKB" to "Kotak", "KOTAKM" to "Kotak",
    "FDRL" to "Federal", "FEDBNK" to "Federal",
    "IDFCBK" to "IDFC First", "IDFB" to "IDFC First", "IDFCFB" to "IDFC First",
    "ONECRD" to "OneCard", "SLICEC" to "Slice", "SLCE" to "Slice", "SLCEIT" to "Slice",
    "PAYTM" to "Paytm", "PYTM" to "Paytm",
    "BARODA" to "BOB", "BOB" to "BOB",
    "PUNBNB" to "PNB", "PNBSMS" to "PNB",
    "CANBK" to "Canara",
    "YESBNK" to "Yes",
    "UBIN" to "Union", "UBINBK" to "Union",
    "IDIBNK" to "Indian",
    "CBIN" to "Central", "CBIND" to "Central",
    "BKID" to "BOI", "BOISMS" to "BOI",
    "RBLBNK" to "RBL", "RBLBK" to "RBL",
    "AUBNK" to "AU", "AUFIRA" to "AU",
    "EQUTAS" to "Equitas", "UJJIVN" to "Ujjivan",
    "DBSSMS" to "DBS", "DBSBK" to "DBS",
    "SCBANK" to "Standard Chartered", "SCREDC" to "Standard Chartered", "STANCB" to "Standard Chartered",
    "CITIBK" to "Citi", "CITI" to "Citi",
    "HSBCBK" to "HSBC", "HSBC" to "HSBC",
    "AMEX" to "American Express", "AMEXIN" to "American Express"
)

fun String.extractTransactionInfo(sender: String? = null): TransactionInfo? {
    val body = this.lowercase()
    val senderUpper = sender?.uppercase() ?: ""

    // 1. Filter out non-transactions early
    if (OTP_KEYWORDS.any { body.contains(it) }) return null
    if (FALSE_POSITIVE_KEYWORDS.any { body.contains(it) }) return null

    val isDebit = DEBIT_KEYWORDS.any { body.contains(it) }
    val isCredit = CREDIT_KEYWORDS.any { body.contains(it) }
    
    // 2. Identify Source from Header
    var detectedSource = SOURCE_MAP.entries.find { senderUpper.contains(it.key) }?.value ?: ""
    
    // Fallback to body keywords
    if (detectedSource.isEmpty()) {
        detectedSource = when {
            body.contains("indusind") -> "IndusInd"
            body.contains("jupiter") -> "Jupiter"
            body.contains("onecard") -> "OneCard"
            body.contains("slice") -> "Slice"
            body.contains("hdfc") -> "HDFC"
            body.contains("axis") -> "Axis"
            body.contains("icici") -> "ICICI"
            body.contains("sbi") -> "SBI"
            else -> ""
        }
    }

    val hasAmountIndicator = body.contains("rs") || body.contains("inr") || body.contains("₹")
    
    // Reject if no clear transaction indicator
    if (!isDebit && !isCredit && (detectedSource.isEmpty() || !hasAmountIndicator)) {
        return null
    }

    // 3. Extract Amount
    val amountMatch = AMOUNT_REGEX.find(body) ?: return null
    val amount = amountMatch.groupValues[1].replace(",", "")

    // 4. Refine Source Name if still unknown
    if (detectedSource.isEmpty()) {
        detectedSource = SOURCE_FALLBACK_REGEX.find(body)?.groupValues?.get(1)?.uppercase() ?: "YOUR"
    }
    
    val isCard = body.contains("card") || detectedSource.contains("Card", ignoreCase = true)
    val sourceSuffix = if (isCard) "Card" else "Bank"
    val fullSource = if (detectedSource.contains(sourceSuffix, ignoreCase = true)) {
        detectedSource
    } else {
        "$detectedSource $sourceSuffix"
    }

    // 5. Extract Participant
    var participant = PARTICIPANT_REGEX.find(body)?.groupValues?.get(1)?.trim()?.uppercase()
    if (participant != null) {
        val sourceUpper = detectedSource.uppercase()
        if (sourceUpper.isNotEmpty() && participant.contains(sourceUpper) || participant.length < 3) {
            participant = null
        }
    }

    Log.d("TransactionDetector", "Detected: Amount=$amount, Source=$fullSource, Participant=$participant")
    return TransactionInfo(amount, isDebit || !isCredit, fullSource, participant)
}
