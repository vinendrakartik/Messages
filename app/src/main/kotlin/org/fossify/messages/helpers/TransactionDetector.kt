package org.fossify.messages.helpers

import android.util.Log
import java.text.DecimalFormat

data class TransactionInfo(
    val amount: String,
    val isDebit: Boolean,
    val source: String, // Bank or Card name
    val participant: String?, // The "XYZ" (merchant or sender)
    val ttsAmount: String, // Amount formatted for TTS with commas
    val isInterest: Boolean = false // New flag for interest-based credits
)

private val FALSE_POSITIVE_KEYWORDS = listOf("bill","ignore", "insurance", "prudential", "wi-fi", "placing order", "recharge successful", "recharge of", "dnd", "folio", "nav", "contribution", "failed", "unsuccessful", "requested", "premium", "cashback", "due on", "bill alert", "ignore if paid", "new bill alert")
private val FALSE_NEGATIVE_KEYWORDS = listOf("bharat bill payment system", "bbps")
private val OTP_KEYWORDS = listOf("otp", "verification", "verify", "code", "secret")
private val DEBIT_KEYWORDS = listOf("debited", "spent", "sent", "withdrawn", "deducted", "debit", "paid", "txn of", "transaction")
private val CREDIT_KEYWORDS = listOf("credited", "received", "topped up", "added to", "deposited", "earned")
private val INTEREST_KEYWORDS = listOf("interest", "int of")

private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr|₹)\s?([\d,]+(?:\.\d{1,2})?)""")
private val SOURCE_FALLBACK_REGEX = Regex("""(?:a/c|account|card)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""")

// Separate regex for specific directions
private val TO_AT_PREFIX_REGEX = Regex("""(?i)(?:at|to)\s+([a-z0-9\s@\.&/-]{3,35})(?:\s*\(|\s+on|\s+via|\s+vide|\s+ending|\s+xx|\s+a/c|\.|\-|$)""")
private val FROM_PREFIX_REGEX = Regex("""(?i)(?:from)\s+([a-z0-9\s@\.&/-]{3,35})(?:\s*\(|\s+on|\s+via|\s+vide|\s+ending|\s+xx|\s+a/c|\.|\-|$)""")
private val PARTICIPANT_SLASH_REGEX = Regex("""(?<!http|https):?/(?!/)([a-z]{3,20})(?:\s|$)""")

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
    "YESBNK" to "Yes Bank",
    "UBIN" to "Union Bank", "UBINBK" to "Union Bank",
    "IDIBNK" to "Indian Bank",
    "CBIN" to "Central Bank", "CBIND" to "Central Bank",
    "BKID" to "BOI", "BOISMS" to "BOI",
    "RBLBNK" to "RBL", "RBLBK" to "RBL",
    "AUBNK" to "AU Bank", "AUFIRA" to "AU Bank",
    "EQUTAS" to "Equitas", "UJJIVN" to "Ujjivan",
    "DBSSMS" to "DBS", "DBSBK" to "DBS",
    "SCBANK" to "Standard Chartered", "SCREDC" to "Standard Chartered", "STANCB" to "Standard Chartered",
    "CITIBK" to "Citi", "CITI" to "Citi",
    "HSBCBK" to "HSBC", "HSBC" to "HSBC",
    "BAJAJF" to "Bajaj", "BAJAJ" to "Bajaj",
    "AMEX" to "American Express", "AMEXIN" to "American Express",
    "TIDEPF" to "Tide"
)

fun String.extractTransactionInfo(sender: String? = null): TransactionInfo? {
    val body = this.lowercase()
    val senderUpper = sender?.uppercase() ?: ""

    // 1. Filter out non-transactions early
    if (OTP_KEYWORDS.any { body.contains(it) }) return null
    
    val isForcedTransaction = FALSE_NEGATIVE_KEYWORDS.any { body.contains(it) }
    if (!isForcedTransaction && FALSE_POSITIVE_KEYWORDS.any { body.contains(it) }) return null

    val isDebit = DEBIT_KEYWORDS.any { body.contains(it) }
    val isCredit = CREDIT_KEYWORDS.any { body.contains(it) }
    val isInterest = INTEREST_KEYWORDS.any { body.contains(it) }
    
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
            body.contains("idfc") -> "IDFC First"
            body.contains("bajaj") -> "Bajaj"
            body.contains("tide") -> "Tide"
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
    val amountVal = amountMatch.groupValues[1].replace(",", "")
    
    // Format display amount: if it ends with .00, remove the .00
    val displayAmount = if (amountVal.endsWith(".00")) {
        amountVal.substring(0, amountVal.length - 3)
    } else {
        amountVal
    }

    // Create TTS amount with commas for clarity (Indian formatting hint)
    val ttsAmount = try {
        val parsed = displayAmount.toDouble()
        val formatter = DecimalFormat("#,##,###.##")
        formatter.format(parsed)
    } catch (e: Exception) {
        displayAmount
    }

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
    // For debits, prioritize "at" or "to". For credits, prioritize "from".
    var participant: String? = null
    if (isDebit && !isInterest) {
        participant = TO_AT_PREFIX_REGEX.find(body)?.groupValues?.get(1)?.trim()?.uppercase()
    }
    
    if (participant == null) {
        participant = FROM_PREFIX_REGEX.find(body)?.groupValues?.get(1)?.trim()?.uppercase()
    }
    
    if (participant == null) {
        participant = PARTICIPANT_SLASH_REGEX.find(body)?.groupValues?.get(1)?.trim()?.uppercase()
    }

    // Validation: Filter out source name, numbers, instructions, and dates
    if (participant != null) {
        val sourceUpper = detectedSource.uppercase()
        val isMostlyDigits = participant.count { it.isDigit() } > participant.length / 2
        val isUrl = participant.contains("http") || participant.contains(".com") || participant.contains(".in")
        val isInstruction = listOf("BLOCK", "SMS", "CALL", "HELP", "REF", "UTR", "FRAUD", "DISPUTE", "REPORT", "BBPS").any { participant!!.contains(it) }
        val isDate = participant.contains(Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}"""))
        
        if ((sourceUpper.isNotEmpty() && participant.contains(sourceUpper)) || isMostlyDigits || isUrl || isInstruction || isDate || participant.length < 3) {
            participant = null
        }
    }

    Log.d("TransactionDetector", "Detected: Amount=$displayAmount, Source=$fullSource, Participant=$participant, isDebit=${isDebit || !isCredit}, isInterest=$isInterest")
    return TransactionInfo(displayAmount, isDebit && !isInterest, fullSource, participant, ttsAmount, isInterest)
}
