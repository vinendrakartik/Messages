package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

data class TransactionInfo(
    val amount: Double,
    val ttsAmount: Double,
    val source: String,
    val isDebit: Boolean,
    val participant: String? = null,
    val isInterest: Boolean = false,
    val isStatement: Boolean = false,
    val isCreditCard: Boolean = false,
    val confidence: Double = 0.0
)

private const val MIN_CONFIDENCE = 0.6

// --- Regex lists and helpers ---

private val NON_TRANSACTION_PHRASES = listOf(
    "\\byou have successfully set the upi pin\\b",
    "\\bset the upi pin\\b",
    "\\bsuccessfully set the upi pin\\b",
    "\\bmodified card usage limits\\b",
    "\\bmodified card usage\\b",
    "\\bmodified card usage limits for your\\b",
    "\\byou've successfully modified card usage limits\\b",
    "\\bupdate! you've successfully modified card usage limits\\b",
    "\\byour package .* delivered\\b",
    "\\border .* delivered\\b",
    "\\bpackage .* delivered\\b",
    "\\bitems was successfully delivered\\b",
    "\\bvia mycards app\\b",
    "\\bpolicy issued\\b",
    "\\bbeta test\\b",
    "\\bunit allotment\\b",
    "\\bprocessed at applicable nav\\b",
    "\\bnav of\\b",
    "\\bkyc is mandatory\\b",
    "\\brequest for purchase\\b",
    "\\bdeclined\\b",
    "\\bfailed\\b",
    "\\binsufficient limit\\b",
    "\\bcould not be processed\\b",
    "\\bnot processed\\b",
    "\\btransaction .* failed\\b",
    "\\bpayment .* failed\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val FALSE_POSITIVE_REGEX = listOf(
    "\\bignore\\b", "\\bverification\\b", "\\bcode\\b", "\\botp\\b", "\\bsecret\\b",
    "\\blogin\\b", "\\bpassword\\b", "\\bwi[- ]?fi\\b", "\\bplacing order\\b", "\\brecharge of\\b",
    "\\bdnd\\b", "\\bfolio\\b", "\\bnav\\b", "\\bcontribution\\b",
    "\\bpremium\\b", "\\bunsuccessful\\b", "\\brequested\\b", "\\bpdf\\b", "\\bto open\\b",
    "\\bresponse[:]?\\b", "\\balert system\\b", "\\bplease response\\b", "\\bplease respond\\b",
    "\\bthank you and have a nice day\\b", "\\bthank you for using\\b", "\\bthis message was sent to you\\b",
    "\\bloan approved\\b", "\\bcall now\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val STATEMENT_REGEX = listOf(
    "\\bstatement is sent\\b", "\\bstatement has been sent\\b", "\\be-?statement\\b",
    "\\bbill generated\\b", "\\bbill is generated\\b", "\\btotal of\\b",
    "\\bminimum of\\b", "\\bminimum due\\b", "\\btotal amount due\\b",
    "\\bminimum amount due\\b", "\\bpayment due\\b", "\\bamount due\\b",
    "\\boutstanding balance\\b", "\\bbill ready\\b", "\\bpayment reminder\\b",
    "\\bstatement for\\b", "\\bstatement available\\b",
    "\\bpay now\\b", "\\bbill alert\\b", "\\bnew bill\\b", "\\bdue on\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val FALSE_NEGATIVE_REGEX = listOf(
    "\\bbharat bill payment system\\b", "\\bbbps\\b", "\\bpayment successful\\b",
    "\\bpayment received\\b", "\\btransaction successful\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val DEBIT_REGEX = listOf(
    "\\bdebited\\b", "\\bspent\\b", "\\bwithdrawn\\b", "\\bdeducted\\b",
    "\\btxn of\\b", "\\btransaction\\b", "\\bpurchase\\b", "\\bpaid to\\b", "\\bpaid from\\b",
    "\\bspent via\\b", "\\bsent via\\b", "\\bsent to\\b", "\\bwithdrawal\\b", "\\batm cash withdrawal\\b",
    "\\bdebited with\\b", "\\bdebited by\\b", "\\bdebited for\\b", "\\bsent from\\b", "\\bsent to\\b",
    "\\bamt deducted\\b", "\\bamt deducted!\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val CREDIT_REGEX = listOf(
    "\\bcredited\\b", "\\bpayment received\\b", "\\bpayment of\\b", "\\badded to\\b", "\\bdeposited\\b",
    "\\bcredited to your\\b", "\\bcredited by\\b", "\\bhas been credited\\b", "\\breceived to your\\b",
    "\\breceived in your\\b", "\\breceived by\\b", "\\bpayment towards\\b", "\\brefund\\b", "\\breceived a payment\\b",
    "\\bwe have received a payment\\b", "\\bpayment of rs\\b", "\\breceived rs\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val INTEREST_REGEX = listOf("\\binterest\\b", "\\bint of\\b", "\\binterest credited\\b").map { Regex(it, RegexOption.IGNORE_CASE) }

private val ACRONYMS_TO_SPACE = setOf(
    "HDFC", "SBI", "ICICI", "IDFC", "PNB", "BOB", "DBS", "HSBC", "RBL", "UCO",
    "IOB", "KVI", "UPI", "IMPS", "NEFT", "RTGS", "CSB"
)

// Primary amount regex: currency symbol before number.
// Fix: Apply \b only to text codes (Rs, INR). Do NOT apply \b to symbols (₹, $) as they are non-word characters.
private val AMOUNT_REGEX = Regex(
    """(?i)(?:(?:\b(?:rs\.?|inr))|₹|\u20b9|\p{Sc})\s?(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\b""",
    RegexOption.IGNORE_CASE
)

// Fallback amount regex: number followed by currency word
private val AMOUNT_FALLBACK_REGEX = Regex(
    """(?i)\b(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s?(?:inr|rupees|rs|₹|\p{Sc})\b""",
    RegexOption.IGNORE_CASE
)

private val OTP_PROXIMITY_REGEX = Regex(
    """(?i)\b(?:otp|code|pin|one[\s-]?time|verification|secret|use)\b(?=.{0,30}\b(\d{3,8})\b)""",
    RegexOption.IGNORE_CASE
)

private val SOURCE_FALLBACK_REGEX = Regex(
    """(?i)(?<!from\s|on\s|to\s|via\s|ending\swith\s)\b(?:a/c|account|acct|card)\.?\s*(?:no\.?\s*)?([A-Za-z0-9*]{3,16})""",
    RegexOption.IGNORE_CASE
)

private val CARD_PHRASE_REGEX = Regex(
    """(?i)\b(?:credit card|cr card|cc\b|card ending|card no|card number|card\s*:\s*|card\s*ending)\b"""
)

private val MASKED_CARD_REGEX = Regex(
    """\b(?:\d{4}[-\s]?){3}\d{4}\b|\b(?:\*{2,}|\*{4})\d{2,4}\b|\bxxxx[-\s]?\d{4}\b""",
    RegexOption.IGNORE_CASE
)

private val CREDIT_SENDER_INDICATORS = listOf("CRD", "CREDIT", "CARD", "AMEX", "AMEXIN", "SBICRD", "ONECRD")

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
    "ONECRD" to "One Card", "SLICE" to "Slice Card", "SLCE" to "Slice Card", "SLCEIT" to "Slice Account",
    "PAYTM" to "Paytm", "PYTM" to "Paytm",
    "BARODA" to "BOB", "BOB" to "BOB",
    "PUNBNB" to "PNB", "PNBSMS" to "PNB",
    "CANBK" to "Canara Bank", "YESBNK" to "Yes Bank",
    "UBIN" to "Union Bank", "UBINBK" to "Union Bank",
    "IDIBNK" to "Indian Bank", "CBIN" to "Central Bank", "CBIND" to "Central Bank",
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
).mapKeys { it.key.uppercase() }

private val PARTICIPANT_EXCLUSIONS = listOf(
    "Info:", "Ref:", "RTGS", "NEFT", "UPI", "via", "using", "vide",
    "on", "at", "Not you", "Call", "to report", "towards",
    "Bharat Bill", "BBPS", "Team", "Help", "Helpline", "NetBanking",
    "Block", "SMS", "Urgent", "Click", "Link", "Touch",
    "Rs", "INR", "₹", "\u20b9", "invoice", "emi",
    "Dear Customer", "Dear Customer,", "Dear", "Pay Now", "Realization",
    "Available Bal", "Avl Bal", "An A", "An Account", "A/C", "Declined",
    "Cardmember", "Bank Cardmember"
)

private val VPA_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+""")

fun String.extractTransactionInfo(context: Context, address: String): TransactionInfo? {
    val raw = this.trim()
    if (raw.isEmpty()) return null
    val body = normalizeWhitespace(raw)
    val lowerBody = body.lowercase(Locale.getDefault())
    val upperAddress = address.uppercase(Locale.getDefault())

    // 0. Non-transaction explicit phrases (Includes "Declined", "Failed")
    if (NON_TRANSACTION_PHRASES.any { it.containsMatchIn(body) }) return null

    // 1. Short-circuit: OTP detection
    if (OTP_PROXIMITY_REGEX.containsMatchIn(body)) return null

    // 2. Statement detection
    val isStatement = STATEMENT_REGEX.any { it.containsMatchIn(body) } ||
        (lowerBody.contains("minimum") && lowerBody.contains("due"))

    if (isStatement) {
        return TransactionInfo(
            amount = 0.0,
            ttsAmount = 0.0,
            source = context.getString(R.string.bank),
            isDebit = false,
            participant = null,
            isInterest = false,
            isStatement = true,
            isCreditCard = false,
            confidence = 1.0
        )
    }

    // 3. Signals
    val debitSignal = DEBIT_REGEX.any { it.containsMatchIn(body) }
    val creditSignal = CREDIT_REGEX.any { it.containsMatchIn(body) }
    val interestSignal = INTEREST_REGEX.any { it.containsMatchIn(body) }
    val amount = extractAmount(body)

    // 4. False positive heuristics
    val hasFalsePositive = FALSE_POSITIVE_REGEX.any { it.containsMatchIn(body) }
    val hasFalseNegative = FALSE_NEGATIVE_REGEX.any { it.containsMatchIn(body) }

    // Fix: If false positive is detected (e.g., "Recharge of", "OTP") and NOT explicitly
    // whitelisted (e.g., "payment successful"), return null immediately.
    // This ignores the message even if an amount is found.
    if (hasFalsePositive && !hasFalseNegative) {
        return null
    }

    if (!debitSignal && !creditSignal && !interestSignal && amount == null) return null
    if (amount == null) return null

    // 5. Source & Participant
    val (sourceRaw, detectedCard) = identifySource(context, upperAddress, body)
    val participant = extractParticipant(body, debitSignal)

    // 6. Confidence Scoring
    val confidence = computeConfidence(
        debitSignal = debitSignal,
        creditSignal = creditSignal,
        hasSource = sourceRaw.isNotBlank(),
        hasParticipant = !participant.isNullOrBlank(),
        interestSignal = interestSignal,
        body = body
    )

    if (confidence < MIN_CONFIDENCE) return null

    val isDebit = debitSignal && !creditSignal
    val ttsAmount = formatForTts(amount)
    val source = sourceRaw.ifBlank { context.getString(R.string.bank) }

    return TransactionInfo(
        amount = amount,
        ttsAmount = ttsAmount,
        source = source,
        isDebit = isDebit,
        participant = participant,
        isInterest = interestSignal,
        isStatement = false,
        isCreditCard = detectedCard,
        confidence = (confidence * 100.0).roundToLong() / 100.0
    )
}

private fun normalizeWhitespace(s: String): String {
    return s.replace(Regex("\\s+"), " ").trim()
}

private fun extractAmount(body: String): Double? {
    AMOUNT_REGEX.find(body)?.let {
        val raw = it.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()
    }
    AMOUNT_FALLBACK_REGEX.find(body)?.let {
        val raw = it.groupValues[1].replace(",", "")
        return raw.toDoubleOrNull()
    }
    return null
}

private fun computeConfidence(
    debitSignal: Boolean,
    creditSignal: Boolean,
    hasSource: Boolean,
    hasParticipant: Boolean,
    interestSignal: Boolean,
    body: String
): Double {
    var score = 0.0
    score += 0.4
    if (debitSignal || creditSignal) score += 0.3
    if (hasSource) score += 0.15
    if (hasParticipant) score += 0.1
    if (interestSignal) score += 0.05

    // Explicitly penalize declined/failed messages if they leaked through
    if (body.contains("declined", ignoreCase = true) || body.contains("failed", ignoreCase = true)) {
        score -= 0.5
    }

    return score.coerceIn(0.0, 1.0)
}

private fun formatForTts(amount: Double): Double {
    return (NumberFormat.getNumberInstance(Locale.US).format(amount)).replace(",", "").toDoubleOrNull() ?: amount
}

private fun identifySource(context: Context, upperAddress: String, body: String): Pair<String, Boolean> {
    fun formatSourceName(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            if (ACRONYMS_TO_SPACE.contains(word.uppercase())) {
                word.uppercase().toCharArray().joinToString(" ")
            } else {
                word
            }
        }
    }

    val lowerBody = body.lowercase(Locale.getDefault())
    val headerSuggestsCard = CREDIT_SENDER_INDICATORS.any { upperAddress.contains(it) }

    for ((key, value) in SOURCE_MAP) {
        if (upperAddress.contains(key)) {
            val base = formatSourceName(value)
            val shouldBeCard = headerSuggestsCard || key.contains("CRD", ignoreCase = true) || value.contains("Credit", ignoreCase = true)
            val final = appendTypeSuffix(context, base, lowerBody, forceCredit = shouldBeCard)
            val isCard = shouldBeCard || final.contains(context.getString(R.string.credit_card), ignoreCase = true)
            return Pair(final, isCard)
        }
    }

    for ((key, value) in SOURCE_MAP) {
        if (lowerBody.contains(key.lowercase())) {
            val base = formatSourceName(value)
            val shouldBeCard = headerSuggestsCard || key.contains("CRD", ignoreCase = true) || value.contains("Credit", ignoreCase = true)
            val final = appendTypeSuffix(context, base, lowerBody, forceCredit = shouldBeCard)
            val isCard = shouldBeCard || final.contains(context.getString(R.string.credit_card), ignoreCase = true)
            return Pair(final, isCard)
        }
    }

    val bodySourceRegex = Regex("""(?i)\b([A-Za-z]{3,20})\s+(?:bank|credit card|debit card|card)\b""")
    bodySourceRegex.find(body)?.let {
        val found = it.groupValues[1].trim()
        val base = formatSourceName(found)
        val explicitCard = it.value.contains("credit", ignoreCase = true) || CARD_PHRASE_REGEX.containsMatchIn(body)
        val final = appendTypeSuffix(context, base, lowerBody, forceCredit = explicitCard)
        val isCard = explicitCard || final.contains(context.getString(R.string.credit_card), ignoreCase = true)
        return Pair(final, isCard)
    }

    SOURCE_FALLBACK_REGEX.find(body)?.let {
        val token = it.groupValues[1]
        val lastFour = token.filter { ch -> ch.isDigit() || ch == '*' }.takeLast(4)
        val isCard = it.value.contains("card", ignoreCase = true) ||
            CARD_PHRASE_REGEX.containsMatchIn(body) ||
            MASKED_CARD_REGEX.containsMatchIn(body) ||
            headerSuggestsCard
        val label = if (isCard) context.getString(R.string.card_ending, lastFour) else context.getString(R.string.account_ending, lastFour)
        return Pair(label, isCard)
    }

    if (MASKED_CARD_REGEX.containsMatchIn(body) || CARD_PHRASE_REGEX.containsMatchIn(body) || headerSuggestsCard) {
        val generic = context.getString(R.string.credit_card)
        return Pair(generic, true)
    }

    return Pair("", false)
}

private fun appendTypeSuffix(context: Context, baseName: String, lowerBody: String, forceCredit: Boolean = false): String {
    val isCreditCard = forceCredit || lowerBody.contains("credit card") || lowerBody.contains("card ending") ||
        lowerBody.contains("card no") || lowerBody.contains("card number") ||
        Regex("""\bcc\b""", RegexOption.IGNORE_CASE).containsMatchIn(lowerBody)

    val isDebitCard = lowerBody.contains("debit card")
    val isAccount = lowerBody.contains("a/c") || lowerBody.contains("account")

    val typeSuffix = when {
        isCreditCard -> context.getString(R.string.credit_card)
        isDebitCard -> context.getString(R.string.debit_card)
        isAccount -> context.getString(R.string.account)
        else -> ""
    }
    return if (typeSuffix.isNotEmpty() && !baseName.contains(typeSuffix, ignoreCase = true)) "$baseName $typeSuffix" else baseName
}

private fun extractParticipant(body: String, isDebit: Boolean): String? {
    val normalized = normalizeWhitespace(body)
    if (normalized.contains("linked to your", ignoreCase = true)) return null

    // 1. High Priority "Spent ... at" (Handles "Spent on Card at Merchant")
    // Fix: This regex scans specifically for "at [Merchant]" to avoid capturing the card name
    // when the pattern is "Spent on Axis Bank Credit Card at MCDONALDS"
    val spentAtRegex = Regex("""(?i)\bspent\s+.*?\s+at\s+([A-Za-z0-9&.\- ]{2,80})""")
    spentAtRegex.find(normalized)?.let {
        val cleaned = cleanParticipantName(it.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    // 2. Generic "Spent" patterns (Lower priority)
    // Added lookahead (?!on\b) to avoid capturing "on CardName" immediately
    val spentRegex = Regex("""(?i)\bspent\s?(?!on\b)@?\s?([A-Za-z0-9\s&.\-]{3,60})""")
    spentRegex.find(normalized)?.let {
        val cleaned = cleanParticipantName(it.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    // 3. Try specific "Paid to/from" patterns (Connected phrases)
    val paidRegex = Regex("""(?i)\bpaid\s+(?:to|from)\s+([A-Za-z0-9@._&.\- ]{3,80})""")
    paidRegex.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    // 4. Directional Search (General to/at/from/by)
    val debitSearch = Regex("""(?i)(?:to|at|towards|via|@)\s+([A-Za-z0-9@._&.\- ]{2,80})""")
    val creditSearch = Regex("""(?i)(?:from|by)\s+([A-Za-z0-9@._&.\- ]{2,80})""")

    val primary = if (isDebit) debitSearch else creditSearch
    primary.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    val secondary = if (isDebit) creditSearch else debitSearch
    secondary.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    // 5. Fallback: Explicit "To/From" Scan
    if (normalized.contains(" to ", ignoreCase = true)) {
        val fallbackTo = Regex("""(?i)\bto\s+([A-Za-z0-9&.\-]{2,50})""")
        fallbackTo.findAll(normalized).forEach { match ->
            val cleaned = cleanParticipantName(match.groupValues[1])
            if (!cleaned.isNullOrBlank()) return cleaned
        }
    }
    if (normalized.contains(" from ", ignoreCase = true)) {
        val fallbackFrom = Regex("""(?i)\bfrom\s+([A-Za-z0-9&.\-]{2,50})""")
        fallbackFrom.findAll(normalized).forEach { match ->
            val cleaned = cleanParticipantName(match.groupValues[1])
            if (!cleaned.isNullOrBlank()) return cleaned
        }
    }

    // 6. Line-based heuristic
    val lines = normalized.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size > 2) {
        for (i in 2 until lines.size) {
            val candidate = lines[i]
            if (candidate.length < 3 || candidate.length > 80) continue
            if (candidate.contains("avl", ignoreCase = true) || candidate.contains("limit", ignoreCase = true)) continue
            val cleaned = cleanParticipantName(candidate)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
    }

    // 7. Capitalized Sequence
    val capSeq = Regex("""([A-Z][a-zA-Z0-9&.\-]{2,30}(?:\s+[A-Z][a-zA-Z0-9&.\-]{2,30})*)""")
    capSeq.find(normalized)?.let {
        val cleaned = cleanParticipantName(it.groupValues[1])
        if (!cleaned.isNullOrBlank()) return cleaned
    }

    // 8. Info/Ref Parsing (For Axis/Bank headers)
    // Matches: "Info - NEFT/HSBC.../E-IN" or "Ref: UPI/..."
    val infoRegex = Regex("""(?i)\b(?:Info|Ref|Narration)\s?[-:]\s?([A-Za-z0-9\s/.\-]+)""")
    infoRegex.find(normalized)?.let {
        val rawInfo = it.groupValues[1]

        // Strategy: If it contains '/', it's likely a banking string (NEFT/Ref/Name).
        // We usually want the LAST component (the name), or sometimes the first if it's "Name/Ref".
        // For "NEFT/HSBC.../E-IN", splitting gives [NEFT, HSBC..., E-IN]. "E-IN" is the name.
        if (rawInfo.contains("/")) {
            val tokens = rawInfo.split("/")
            // Try tokens in reverse order to find a valid name
            for (token in tokens.reversed()) {
                val cleaned = cleanParticipantName(token)
                if (!cleaned.isNullOrBlank() && !cleaned.equals("NEFT", true) && !cleaned.equals("IMPS", true) && !cleaned.equals("UPI", true)) {
                    return cleaned
                }
            }
        } else {
            // No slashes, try the whole info string (e.g. "Info - AMAZON PAY")
            val cleaned = cleanParticipantName(rawInfo)
            if (!cleaned.isNullOrBlank()) return cleaned
        }
    }
    return null
}

private fun cleanParticipantName(raw: String): String? {
    var name = raw.trim()
    val terminators = listOf(".", "-", "(", " on ", " via ", " ending ", "\n", " Ref", " bal", " using", " - ")
    for (t in terminators) {
        val idx = name.indexOf(t, ignoreCase = true)
        if (idx != -1) {
            if (idx > 0) name = name.take(idx).trim()
            else if (idx == 0) name = name.substring(t.length).trim()
        }
    }

    // Fix: Trim non-alphanumeric chars from start/end (e.g. "..ECO" -> "ECO")
    name = name.trim { !it.isLetterOrDigit() }

    if (name.isEmpty()) return null
    if (VPA_REGEX.containsMatchIn(name)) return "UPI"
    if (name.contains("http", ignoreCase = true) || name.contains("www", ignoreCase = true)) return null

    val lower = name.lowercase(Locale.getDefault())
    val genericTokens = listOf("your", "payment", "received", "sent", "avl", "limit", "not you", "not you?", "total", "outstanding", "transaction")
    if (genericTokens.any { lower.startsWith(it) || lower.contains(" $it") }) return null
    if (lower.startsWith("spent") || lower.startsWith("inr") || lower.startsWith("rs") || lower.startsWith("avl")) return null
    if (lower.startsWith("not you") || lower.startsWith("call") || lower.startsWith("ref")) return null
    if (lower.contains("thank you") || lower.contains("dear customer") || lower.contains("this message")) return null

    // Fix: Explicitly block "on [Bank]" if regex leaks it
    if (lower.startsWith("on ") || lower.startsWith("via ")) return null

    if (PARTICIPANT_EXCLUSIONS.any { name.equals(it, ignoreCase = true) || name.startsWith(it, ignoreCase = true) }) return null
    if (lower.startsWith("an a") || lower.contains("linked to")) return null
    if (name.count { it.isDigit() } > 4) return null
    if (name.length < 2) return null

    // Fix: Block common 2-letter stopwords
    if (name.length == 2) {
        val shortStopWords = setOf("me", "my", "to", "at", "on", "an", "is", "of", "or", "by", "it", "us", "we", "up", "do", "go", "if", "in", "no")
        if (shortStopWords.contains(lower)) return null
    }

    name = name.trim().trimEnd('.', ',', ';', ':')

    val words = name.split("\\s+".toRegex())
    val uppercaseWords = words.count { it.length > 1 && it == it.uppercase(Locale.getDefault()) }
    if (uppercaseWords == words.size) {
        if (name.contains("INR", ignoreCase = true) || name.contains("RS", ignoreCase = true) || name.contains("AVL", ignoreCase = true)) return null
    }

    // Fix: Convert ALL CAPS participants to Title Case (e.g. "AMAZON INDIA" -> "Amazon India")
    val isAllUppercase = name.all { !it.isLetter() || it.isUpperCase() }
    if (isAllUppercase && name.any { it.isLetter() }) {
        return name.lowercase(Locale.getDefault())
            .split(" ")
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }

    return name
}
