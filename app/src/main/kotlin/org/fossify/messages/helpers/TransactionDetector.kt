package org.fossify.messages.helpers

data class TransactionInfo(
    val amount: Double,
    val ttsAmount: Double,
    val source: String,
    val isDebit: Boolean,
    val participant: String? = null,
    val isInterest: Boolean = false,
    val isStatement: Boolean = false
)

private val FALSE_POSITIVE_KEYWORDS = listOf(
    "ignore", "verification", "code", "otp", "secret", "login", "password",
    "wi-fi", "placing order", "recharge of", "dnd",
    "folio", "nav", "contribution", "failed", "premium","unsuccessful", "requested"
)

private val STATEMENT_KEYWORDS = listOf(
    "statement is sent", "total amount due", "min amt due", "minimum due",
    "total due", "payment due", "amount due", "bill is generated", "e-statement"
)

private val FALSE_NEGATIVE_KEYWORDS = listOf("bharat bill payment system", "bbps", "payment successful")
private val OTP_KEYWORDS = listOf("otp", "verification", "verify", "code", "secret")
private val DEBIT_KEYWORDS = listOf("debited", "spent", "sent", "withdrawn", "deducted", "debit", "paid", "txn of", "transaction", "purchase")
private val CREDIT_KEYWORDS = listOf("credited", "received", "topped up", "added to", "deposited", "earned", "refund")
private val INTEREST_KEYWORDS = listOf("interest", "int of")

private val ACRONYMS_TO_SPACE = setOf(
    "HDFC", "SBI", "ICICI", "IDFC", "PNB", "BOB", "DBS", "HSBC", "RBL", "UCO", "IOB", "KVI", "UPI", "IMPS", "NEFT", "RTGS", "CSB"
)

private val AMOUNT_REGEX = Regex("""(?i)(?:rs\.?|inr|₹|\u20b9)\s?([\d,]+(?:\.\d{1,2})?)""")
private val SOURCE_FALLBACK_REGEX = Regex("""(?i)(?<!from |on |to |via )(?:a/c|account|card)\s+(?:no\.?\s+)?([a-z0-9*]{3,15})""")

private val SOURCE_MAP = mapOf(
    "INDUSB" to "IndusInd Bank", "INDUSI" to "IndusInd Bank",
    "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank",
    "AXISBK" to "Axis Bank", "UTIBNK" to "Axis Bank", "AXISBN" to "Axis Bank",
    "ICICIB" to "ICICI Bank", "ICICIP" to "ICICI Bank", "ICICIT" to "ICICI Bank", "ICIC" to "ICICI Bank",
    "SBIN" to "SBI", "SBIINB" to "SBI", "SBIPS" to "SBI", "SBICRD" to "SBI Credit Card",
    "JTEDGE" to "Jupiter Bank", "JUPITR" to "Jupiter Bank",
    "KOTAKB" to "Kotak Bank", "KOTAKM" to "Kotak Bank",
    "FDRL" to "Federal Bank", "FEDBNK" to "Federal Bank",
    "IDFCBK" to "IDFC FIRST Bank", "IDFB" to "IDFC FIRST Bank", "IDFCFB" to "IDFC FIRST Bank",
    "ONECRD" to "One Card",
    "SLICEC" to "Slice Card", "SLCE" to "Slice Card", "SLCEIT" to "Slice Account", "SLICE" to "Slice",
    "PAYTM" to "Paytm", "PYTM" to "Paytm",
    "BARODA" to "BOB", "BOB" to "BOB",
    "PUNBNB" to "PNB", "PNBSMS" to "PNB",
    "CANBK" to "Canara Bank",
    "YESBNK" to "Yes Bank",
    "UBIN" to "Union Bank", "UBINBK" to "Union Bank",
    "IDIBNK" to "Indian Bank",
    "CBIN" to "Central Bank", "CBIND" to "Central Bank",
    "BKID" to "BOI", "BOISMS" to "BOI",
    "RBLBNK" to "RBL Bank", "RBLBK" to "RBL Bank",
    "AUBNK" to "AU Bank", "AUFIRA" to "AU Bank",
    "EQUTAS" to "Equitas Bank", "UJJIVN" to "Ujjivan Bank",
    "DBSSMS" to "DBS Bank", "DBSBK" to "DBS Bank",
    "SCBANK" to "Standard Chartered", "SCREDC" to "Standard Chartered", "STANCB" to "Standard Chartered",
    "CITIBK" to "Citi Bank", "CITI" to "Citi Bank",
    "HSBCBK" to "HSBC Bank", "HSBC" to "HSBC Bank", "HSBCIM" to "HSBC Bank",
    "BAJAJF" to "Bajaj Finance", "BAJAJ" to "Bajaj Finance",
    "AMEX" to "American Express", "AMEXIN" to "American Express",
    "TIDEPF" to "Tide Bank"
)

fun String.extractTransactionInfo(address: String): TransactionInfo? {
    val body = this
    val lowerBody = body.lowercase()

    if (OTP_KEYWORDS.any { lowerBody.contains(it) }) return null

    val isStatement = STATEMENT_KEYWORDS.any { lowerBody.contains(it) } ||
        (lowerBody.contains("minimum") && lowerBody.contains("due"))

    if (isStatement) return null

    if (FALSE_POSITIVE_KEYWORDS.any { lowerBody.contains(it) } &&
        FALSE_NEGATIVE_KEYWORDS.none { lowerBody.contains(it) }) return null

    val isDebit = DEBIT_KEYWORDS.any { lowerBody.contains(it) } || lowerBody.contains("deducted")
    val isInterest = INTEREST_KEYWORDS.any { lowerBody.contains(it) }

    val isReceivedPayment = lowerBody.contains("received payment") || 
                           lowerBody.contains("payment received") ||
                           (lowerBody.contains("payment") && lowerBody.contains("received"))
    
    val isCredit = isReceivedPayment || CREDIT_KEYWORDS.any { lowerBody.contains(it) }

    if (!isDebit && !isCredit && !isInterest) return null

    val matcher = AMOUNT_REGEX.find(body) ?: return null
    val amountStr = matcher.groupValues[1].replace(",", "")
    val amount = amountStr.toDoubleOrNull() ?: return null

    val ttsAmount = amount
    val source = identifySource(address, body)
    val participant = extractParticipant(body, isDebit)

    return TransactionInfo(amount, ttsAmount, source, isDebit, participant, isInterest, isStatement)
}

private fun identifySource(address: String, body: String): String {
    val upperAddress = address.uppercase()
    val lowerBody = body.lowercase()

    fun formatSourceName(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            if (ACRONYMS_TO_SPACE.contains(word.uppercase())) {
                word.uppercase().toCharArray().joinToString(" ")
            } else {
                word
            }
        }
    }

    val isCreditCard = lowerBody.contains("credit card") || lowerBody.contains("card ending")
    val isDebitCard = lowerBody.contains("debit card")
    val isAccount = lowerBody.contains("a/c") || lowerBody.contains("account")
    
    val typeSuffix = when {
        isCreditCard -> "Credit Card"
        isDebitCard -> "Debit Card"
        isAccount -> "Account"
        else -> ""
    }

    var baseName = "Bank"
    var foundBase = false

    if (upperAddress.contains("TIDE") || lowerBody.contains("tide card")) {
        baseName = if (lowerBody.contains("ncmc travel")) "Tide NCMC Travel Card" else "Tide Card"
        foundBase = true
    }

    if (!foundBase) {
        for ((key, value) in SOURCE_MAP) {
            if (upperAddress.contains(key)) {
                baseName = value
                foundBase = true
                break
            }
        }
    }

    if (!foundBase) {
        for ((key, value) in SOURCE_MAP) {
            if (lowerBody.contains(key.lowercase())) {
                baseName = value
                foundBase = true
                break
            }
        }
    }

    if (!foundBase) {
        val bodySourceRegex = Regex("""(?i)(?<!on |from |to )([a-z]{3,15})\s+(?:bank|credit card|debit card|card)""")
        bodySourceRegex.find(body)?.let {
            var found = it.groupValues[1].trim()
            baseName = found
            foundBase = true
        }
    }

    if (!foundBase) {
        SOURCE_FALLBACK_REGEX.find(body)?.let {
            val type = if (it.groupValues[0].contains("card", ignoreCase = true)) "Card" else "Account"
            return "$type ending ${it.groupValues[1].takeLast(4)}"
        }
    }

    var finalName = formatSourceName(baseName)
    if (typeSuffix.isNotEmpty() && !finalName.contains(typeSuffix, ignoreCase = true)) {
        finalName += " $typeSuffix"
    }

    return finalName
}

private fun extractParticipant(body: String, isDebit: Boolean): String? {
    if (body.contains("linked to your", ignoreCase = true)) return null

    val ignoreList = listOf(
        "Info:", "Ref:", "RTGS", "NEFT", "UPI", "via", "using", "vide",
        "on", "at", "Not you", "Call", "to report", "towards",
        "Bharat Bill", "BBPS", "Team", "Help", "Helpline", "NetBanking",
        "Block", "SMS", "Urgent", "Click", "Link", "Touch",
        "Rs", "INR", "₹", "\u20b9"
    )

    val lines = body.split("\n")
    if (lines.size > 2) {
        for (i in 2 until lines.size) {
            val potential = lines[i].trim()
            if (potential.length in 4..25 &&
                potential.all { it.isLetter() || it.isWhitespace() || it == '.' || it == '&' } &&
                !potential.contains("Limit", true)) {
                return potential
            }
        }
    }

    if (isDebit && body.contains("spent", ignoreCase = true)) {
        val spentRegex = Regex("""spent\s?@\s?([a-zA-Z0-9\s*]{3,20})""", RegexOption.IGNORE_CASE)
        spentRegex.find(body)?.let {
            val name = cleanParticipantName(it.groupValues[1], ignoreList)
            if (name != null) return name
        }
    }

    val searchStr = if (isDebit) "(?:to|at|towards|in|on|via)\\s+" else "(?:from|by)\\s+"
    val regex = Regex(searchStr + """([a-zA-Z0-9\s@.&*-]{3,30})""", RegexOption.IGNORE_CASE)

    val matches = regex.findAll(body).toList()
    
    // Prioritize 'from' for credits and 'to' for debits if possible
    if (!isDebit) {
        val fromMatch = matches.find { it.value.lowercase().startsWith("from") }
        if (fromMatch != null) {
            val cleaned = cleanParticipantName(fromMatch.groupValues[1], ignoreList)
            if (cleaned != null) return cleaned
        }
    }

    matches.forEach { match ->
        val rawName = match.groupValues[1].trim()
        val cleanedName = cleanParticipantName(rawName, ignoreList)
        if (cleanedName != null) return cleanedName
    }

    return null
}

private fun cleanParticipantName(raw: String, ignoreList: List<String>): String? {
    var name = raw

    val terminators = listOf(".", "-", "(", " on ", " via ", " ending ", "\n", " Ref", " bal", " using")
    terminators.forEach {
        val idx = name.indexOf(it, ignoreCase = true)
        if (idx != -1) name = name.substring(0, idx).trim()
    }

    if (ignoreList.any { name.startsWith(it, ignoreCase = true) || name.equals(it, ignoreCase = true) }) return null

    if (name.count { it.isDigit() } > 3) return null
    if (name.contains("http") || name.contains("www")) return null

    if (name.length > 2) return name.trim()
    return null
}
