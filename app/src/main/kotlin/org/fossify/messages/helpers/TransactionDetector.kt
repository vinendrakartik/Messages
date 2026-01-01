package org.fossify.messages.helpers

import java.util.regex.Pattern

data class TransactionInfo(
    val amount: Double,
    val ttsAmount: String,
    val source: String,
    val isDebit: Boolean,
    val participant: String? = null,
    val isInterest: Boolean = false
)

private val FALSE_POSITIVE_KEYWORDS = listOf("bill", "ignore", "insurance", "prudential", "wi-fi", "placing order", "recharge successful", "recharge of", "dnd", "folio", "nav", "contribution", "failed", "unsuccessful", "requested", "premium", "cashback", "due on", "bill alert", "ignore if paid", "new bill alert")
private val FALSE_NEGATIVE_KEYWORDS = listOf("bharat bill payment system", "bbps")
private val OTP_KEYWORDS = listOf("otp", "verification", "verify", "code", "secret")
private val DEBIT_KEYWORDS = listOf("debited", "spent", "sent", "withdrawn", "deducted", "debit", "paid", "txn of", "transaction")
private val CREDIT_KEYWORDS = listOf("credited", "received", "topped up", "added to", "deposited", "earned")
private val INTEREST_KEYWORDS = listOf("interest", "int of")

private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr|₹)\s?([\d,]+(?:\.\d{1,2})?)""")
private val SOURCE_FALLBACK_REGEX = Regex("""(?:a/c|account|card)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""", RegexOption.IGNORE_CASE)

private val SOURCE_MAP = mapOf(
    "INDUSB" to "IndusInd Bank", "INDUSI" to "IndusInd Bank",
    "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank",
    "AXISBK" to "Axis Bank", "UTIBNK" to "Axis Bank", "AXISBN" to "Axis Bank",
    "ICICIB" to "ICICI Bank", "ICICIP" to "ICICI Bank", "ICICIT" to "ICICI Bank",
    "SBIN" to "SBI", "SBIINB" to "SBI", "SBIPS" to "SBI", "SBICRD" to "SBI Card",
    "JTEDGE" to "Jupiter Bank", "JUPITR" to "Jupiter Bank",
    "KOTAKB" to "Kotak Bank", "KOTAKM" to "Kotak Bank",
    "FDRL" to "Federal Bank", "FEDBNK" to "Federal Bank",
    "IDFCBK" to "IDFC First Bank", "IDFB" to "IDFC First Bank", "IDFCFB" to "IDFC First Bank",
    "ONECRD" to "One Card", "SLICEC" to "Slice Card", "SLCE" to "Slice Card", "SLCEIT" to "Slice Account",
    "PAYTM" to "Paytm", "PYTM" to "Paytm",
    "BARODA" to "BOB Bank", "BOB" to "BOB Bank",
    "PUNBNB" to "PNB Bank", "PNBSMS" to "PNB Bank",
    "CANBK" to "Canara Bank",
    "YESBNK" to "Yes Bank",
    "UBIN" to "Union Bank", "UBINBK" to "Union Bank",
    "IDIBNK" to "Indian Bank",
    "CBIN" to "Central Bank", "CBIND" to "Central Bank",
    "BKID" to "BOI Bank", "BOISMS" to "BOI Bank",
    "RBLBNK" to "RBL Bank", "RBLBK" to "RBL Bank",
    "AUBNK" to "AU Bank", "AUFIRA" to "AU Bank",
    "EQUTAS" to "Equitas Bank", "UJJIVN" to "Ujjivan Bank",
    "DBSSMS" to "DBS Bank", "DBSBK" to "DBS Bank",
    "SCBANK" to "Standard Chartered Bank", "SCREDC" to "Standard Chartered Bank", "STANCB" to "Standard Chartered Bank",
    "CITIBK" to "Citi Bank", "CITI" to "Citi Bank",
    "HSBCBK" to "HSBC Bank", "HSBC" to "HSBC Bank",
    "BAJAJF" to "Bajaj Finance", "BAJAJ" to "Bajaj Finance",
    "AMEX" to "American Express Card", "AMEXIN" to "American Express Card",
    "TIDEPF" to "Tide Bank"
)

fun String.extractTransactionInfo(address: String): TransactionInfo? {
    val body = this
    val lowerBody = body.lowercase()

    // Filter out statement generated messages
    if (lowerBody.contains("statement is sent") || lowerBody.contains("is due by") || (lowerBody.contains("minimum of") && lowerBody.contains("is due"))) return null

    // Check for OTPs
    if (OTP_KEYWORDS.any { lowerBody.contains(it) }) return null

    // Check for false positives
    if (FALSE_POSITIVE_KEYWORDS.any { lowerBody.contains(it) } &&
        FALSE_NEGATIVE_KEYWORDS.none { lowerBody.contains(it) }) return null

    val isDebit = DEBIT_KEYWORDS.any { lowerBody.contains(it) } || lowerBody.contains("deducted")
    val isCredit = CREDIT_KEYWORDS.any { lowerBody.contains(it) } || (lowerBody.contains("received") && !lowerBody.contains("received payment") && !lowerBody.contains("received towards"))
    val isInterest = INTEREST_KEYWORDS.any { lowerBody.contains(it) }

    // Special case for "Payment of ... has been received" which is actually a credit
    val isReceivedPayment = lowerBody.contains("received payment") || (lowerBody.contains("payment") && lowerBody.contains("received"))
    
    if (!isDebit && !isCredit && !isInterest && !isReceivedPayment) return null

    // Extract amount
    val matcher = AMOUNT_REGEX.find(body) ?: return null
    val amountStr = matcher.groupValues[1].replace(",", "")
    val amount = amountStr.toDoubleOrNull() ?: return null

    // Human-friendly amount for TTS
    val ttsAmount = formatAmountForTTS(amount)

    // Identify source (Bank/Wallet)
    val source = identifySource(address, body)

    // Identify participant
    val participant = extractParticipant(body, isDebit)

    return TransactionInfo(amount, ttsAmount, source, isDebit, participant, isInterest)
}

private fun formatAmountForTTS(amount: Double): String {
    val integerPart = amount.toLong()
    val decimalPart = Math.round((amount - integerPart) * 100).toInt()

    if (integerPart == 0L && decimalPart == 0) return "zero"

    val crores = integerPart / 10000000
    val lakhs = (integerPart % 10000000) / 100000
    val thousands = (integerPart % 100000) / 1000
    val remainder = integerPart % 1000

    val parts = mutableListOf<String>()

    if (crores > 0) {
        val label = if (crores == 1L) "crore" else "crores"
        parts.add("$crores $label")
    }
    if (lakhs > 0) {
        val label = if (lakhs == 1L) "lakh" else "lakhs"
        parts.add("$lakhs $label")
    }
    if (thousands > 0) {
        val label = if (thousands == 1L) "thousand" else "thousands"
        parts.add("$thousands $label")
    }
    if (remainder > 0 || parts.isEmpty()) {
        parts.add(remainder.toString())
    }

    var result = parts.joinToString(" ")
    // We don't append "rupee/rupees" here anymore because it's handled in handleTransactionTTS

    if (decimalPart > 0) {
        val paiseLabel = if (decimalPart == 1) "paisa" else "paise"
        result += " and $decimalPart $paiseLabel"
    }

    return result
}

private fun identifySource(address: String, body: String): String {
    val upperAddress = address.uppercase()
    val lowerBody = body.lowercase()

    // Priority 1: Specific "Card" or "Bank" mentions in body for full context
    val bodySourceRegex = Regex("""(?i)([a-z0-9\s]{3,20})\s+(?:bank|card|wallet|credit card|debit card)""")
    bodySourceRegex.find(body)?.let {
        var found = it.groupValues[0].trim()
        // Format acronyms for better TTS (e.g. ICICI -> I C I C I)
        found = found.replace(Regex("""\b([A-Z]{2,5})\b""")) { match ->
            match.value.toCharArray().joinToString(" ")
        }
        return found
    }

    // Specific logic for Tide
    if (upperAddress.contains("TIDE") || lowerBody.contains("tide card")) {
        return if (lowerBody.contains("ncmc travel")) "Tide NCMC Travel Card" else "Tide Card"
    }

    for ((key, value) in SOURCE_MAP) {
        if (upperAddress.contains(key)) {
            val processedValue = if (value.all { it.isUpperCase() }) value.toCharArray().joinToString(" ") else value
            return processedValue
        }
    }

    for ((key, value) in SOURCE_MAP) {
        if (lowerBody.contains(key.lowercase())) {
            val processedValue = if (value.all { it.isUpperCase() }) value.toCharArray().joinToString(" ") else value
            return processedValue
        }
    }

    // Priority 4: Fallback regex for account/card ending numbers
    SOURCE_FALLBACK_REGEX.find(body)?.let {
        val matchedText = it.groupValues[0].lowercase()
        val type = if (matchedText.contains("card")) "Card" else "Account"
        return "your $type ending in ${it.groupValues[1].takeLast(4)}"
    }

    return "your bank"
}

private fun extractParticipant(body: String, isDebit: Boolean): String? {
    if (body.contains("linked to your", ignoreCase = true)) return null
    
    val ignoreKeywords = listOf("Info:", "Ref:", "RTGS", "NEFT", "UPI", "via", "using", "vide", "on", "at", "Not you?", "Call ", "to report issue")
    
    // Check Axis-specific multiline format
    val lines = body.split("\n")
    if (lines.size > 3 && isDebit) {
        val potentialName = lines[3].trim()
        if (potentialName.isNotEmpty() && potentialName.length > 3 && potentialName.all { it.isLetter() || it.isWhitespace() } && potentialName.none { it.isDigit() }) {
            return potentialName.take(20)
        }
    }

    val searchStr = if (isDebit) "(?:to|at|towards)\\s+" else "(?:from)\\s+"
    val regex = Regex(searchStr + """([a-zA-Z0-9\s@\.&/-]{3,35})""", RegexOption.IGNORE_CASE)
    
    regex.findAll(body).forEach { match ->
        var name = match.groupValues[1].trim()
        
        // Skip phone numbers or technical strings
        if (name.any { it.isDigit() && name.length > 8 } || name.contains("BLOCK", true) || name.contains("report issue", true)) {
            return@forEach
        }

        // Apply terminators
        val terminators = listOf(".", "-", "(", "on ", "via ", "ending ", "\n", " Ref")
        terminators.forEach {
            val idx = name.indexOf(it, ignoreCase = true)
            if (idx != -1) name = name.substring(0, idx).trim()
        }

        // Clean noise keywords
        ignoreKeywords.forEach { 
            val idx = name.indexOf(it, ignoreCase = true)
            if (idx != -1) name = name.substring(0, idx).trim()
        }
        
        if (name.length > 2) {
            val words = name.split(" ").filter { it.isNotBlank() }
            if (words.isNotEmpty()) return words.take(2).joinToString(" ")
        }
    }
    return null
}
