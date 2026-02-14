package com.vk.messages.helpers

import android.content.Context
import com.vk.messages.R
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Data class representing extracted transaction details.
 */
data class TransactionInfo(
    val amount: Double,
    val ttsAmount: Double,
    val source: String,
    val isDebit: Boolean,
    val participant: String? = null,
    val isInterest: Boolean = false,
    val isStatement: Boolean = false,
    val isCreditCard: Boolean = false,
    val isRecurring: Boolean = false,
    val confidence: Double = 0.0
)

private const val TAG = "TransactionDetector"
private const val MIN_CONFIDENCE = 0.6

// ==========================================
// 1. ML ENGINE (Restored)
// ==========================================

private object TransactionML {
    private const val MODEL_FILE = "transaction_model.tflite"
    private const val VOCAB_FILE = "vocab.json"
    private const val MAX_LEN = 80
    private const val VOCAB_SIZE = 6000

    private var interpreter: Interpreter? = null
    private var vocab: Map<String, Int>? = null
    private var isModelBroken = false

    private val classMap = mapOf(
        0 to "NORMAL", 1 to "TRANSACTION_DEBIT", 2 to "TRANSACTION_CREDIT",
        3 to "OTP", 4 to "STATEMENT", 5 to "DELIVERY", 6 to "INVESTMENT"
    )

    private val tagMap = mapOf(
        0 to "O", 1 to "B-AMT", 2 to "I-AMT",
        3 to "B-MERCH", 4 to "I-MERCH",
        5 to "B-SRC", 6 to "I-SRC"
    )

    fun init(context: Context) {
        if (interpreter != null || isModelBroken) return
        try {
            interpreter = Interpreter(loadModelFile(context))
            val jsonString = context.assets.open(VOCAB_FILE).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val map = mutableMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getInt(key)
            }
            vocab = map
            context.logDebug(TAG, "ML Model loaded successfully.")
        } catch (e: Exception) {
            context.logDebug(TAG, "CRITICAL: ML Failed. Switching to Regex-only mode.")
            isModelBroken = true
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    data class MLResult(val label: String, val confidence: Double, val nerMerchant: String?, val nerBank: String?)

    fun predict(context: Context, rawText: String): MLResult? {
        if (isModelBroken || interpreter == null || vocab == null) return null

        val cleanedText = cleanTextForML(rawText)
        val tokens = tokenize(cleanedText)
        val rawWords = cleanedText.split(" ").filter { it.isNotBlank() }

        val input = Array(1) { FloatArray(MAX_LEN) }
        for (i in 0 until MAX_LEN) {
            input[0][i] = if (i < tokens.size) tokens[i].toFloat() else 0.0f
        }

        val outputNer = Array(1) { Array(MAX_LEN) { FloatArray(7) } }
        val outputClass = Array(1) { FloatArray(7) }
        val outputs = mapOf(0 to outputNer, 1 to outputClass)

        try {
            interpreter?.runForMultipleInputsOutputs(arrayOf(input), outputs)
        } catch (e: Exception) {
            return null
        }


        val classProbs = outputClass[0]
        val maxClassIdx = classProbs.indices.maxByOrNull { classProbs[it] } ?: 0
        val label = classMap[maxClassIdx] ?: "NORMAL"
        val confidence = classProbs[maxClassIdx].toDouble()

        var extractedMerchant = ""
        var extractedBank = ""
        for (i in 0 until minOf(rawWords.size, MAX_LEN)) {
            val wordProbs = outputNer[0][i]
            val maxTagIdx = wordProbs.indices.maxByOrNull { wordProbs[it] } ?: 0
            val tag = tagMap[maxTagIdx] ?: "O"
            if (tag == "B-MERCH" || tag == "I-MERCH") extractedMerchant += "${rawWords[i]} "
            if (tag == "B-SRC" || tag == "I-SRC") extractedBank += "${rawWords[i]} "
        }

        return MLResult(
            label,
            confidence,
            nerMerchant = if (extractedMerchant.isBlank()) null else extractedMerchant.trim(),
            nerBank = if (extractedBank.isBlank()) null else extractedBank.trim()
        )
    }

    private fun cleanTextForML(text: String): String {
        var t = text.replace("\n", " ").lowercase()
        t = t.replace(Regex("(rs\\.?|inr|₹|usd)\\s*[\\d,]+(\\.\\d+)?"), " <amt> ")
        t = t.replace(Regex("\\b\\d{4,12}\\b"), " <num> ")
        return t
    }

    private fun tokenize(text: String): List<Int> {
        val filters = "[!\"#$%&()*+,-./:;<=>?@\\[\\]^_`{|}~\\t\\n]"
        val clean = text.replace(Regex(filters), "")
        return clean.split(" ").filter { it.isNotBlank() }.map { token ->
            val id = vocab?.get(token) ?: 1
            if (id >= VOCAB_SIZE) 1 else id
        }
    }
}

// ==========================================
// 2. REGEX PATTERNS (Updated from V2)
// ==========================================

private val NON_TRANSACTION_PHRASES = listOf(
    "\\byou have successfully set the upi pin\\b", "\\bset the upi pin\\b", "\\bsuccessfully set the upi pin\\b",
    "\\bmodified card usage limits\\b", "\\bmodified card usage\\b", "\\bmodified card usage limits for your\\b",
    "\\byou've successfully modified card usage limits\\b", "\\bupdate! you've successfully modified card usage limits\\b",
    "\\byour package .* delivered\\b", "\\border .* delivered\\b", "\\bpackage .* delivered\\b",
    "\\bitems was successfully delivered\\b", "\\bvia mycards app\\b", "\\bpolicy issued\\b", "\\bbeta test\\b",
    "\\bunit allotment\\b", "\\bprocessed at applicable nav\\b", "\\bnav of\\b", "\\bkyc is mandatory\\b",
    "\\brequest for purchase\\b", "\\bdeclined\\b", "\\bfailed\\b", "\\binsufficient limit\\b",
    "\\bcould not be processed\\b", "\\bnot processed\\b", "\\btransaction .* failed\\b", "\\bpayment .* failed\\b",
    "\\brequest to pay\\b"
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
    "\\bstatement for\\b", "\\bstatement available\\b", "\\bpay now\\b", "\\bbill alert\\b", "\\bnew bill\\b", "\\bdue on\\b"
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
    "\\bamt deducted\\b", "\\bamt deducted!\\b", "\\bsent\\b", "\\bsent rs\\b", "\\bused for\\b", "\\busing\\b", "\\bnach debit\\b",
    "\\bsuccessfully processed\\b",
    "\\bfunds blocked\\b",
    "\\bmandate created\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val CREDIT_REGEX = listOf(
    "\\bcredited\\b", "\\bpayment received\\b", "\\bpayment of\\b", "\\badded to\\b", "\\bdeposited\\b",
    "\\bcredited to your\\b", "\\bcredited by\\b", "\\bhas been credited\\b", "\\breceived to your\\b",
    "\\breceived in your\\b", "\\breceived by\\b", "\\bpayment towards\\b", "\\brefund\\b", "\\breceived a payment\\b",
    "\\bwe have received a payment\\b", "\\bpayment of rs\\b", "\\breceived rs\\b",
    "\\breceived in a/c\\b", "\\breceived in account\\b", "\\breceived in (?:[a-z]+ )*(?:account|a/c)\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val RECURRING_REGEX = listOf(
    "\\bemi\\b", "\\bstanding instruction\\b", "\\bauto-?debit\\b", "\\bsubscription\\b",
    "\\binstallment\\b", "\\bplan payment\\b", "\\brecurring\\b"
).map { Regex(it, RegexOption.IGNORE_CASE) }

private val INTEREST_REGEX = listOf("\\binterest\\b", "\\bint of\\b", "\\binterest credited\\b").map { Regex(it, RegexOption.IGNORE_CASE) }

private val ACRONYMS_TO_SPACE = setOf(
    "HDFC", "SBI", "ICICI", "IDFC", "PNB", "BOB", "DBS", "HSBC", "RBL", "UCO",
    "IOB", "KVI", "UPI", "IMPS", "NEFT", "RTGS", "CSB"
)

private val AMOUNT_REGEX = Regex(
    """(?i)(?:(?:\b(?:rs\.?|inr))|₹|\u20b9|\p{Sc})\s?(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\b""",
    RegexOption.IGNORE_CASE
)

private val AMOUNT_FALLBACK_REGEX = Regex(
    """(?i)\b(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s?(?:inr|rupees|rs|₹|\p{Sc})\b""",
    RegexOption.IGNORE_CASE
)

private val OTP_PROXIMITY_REGEX = Regex(
    """(?i)\b(?:otp|code|pin|pw|password)\b[:\s-]*(?<!\d)(?![1-9]{1}800)\d{4,8}(?!\d)""",
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

private val CREDIT_CARD_REGEX = Regex("\\bcredit card\\b", RegexOption.IGNORE_CASE)

private val CREDIT_SENDER_INDICATORS = listOf("CRD", "CREDIT", "CARD", "AMEX", "AMEXIN", "SBICRD", "ONECRD")

private val SOURCE_MAP = mapOf(
    "INDUSB" to "IndusInd Bank", "INDUSI" to "IndusInd Bank",
    "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank",
    "AXISBK" to "Axis Bank", "UTIBNK" to "Axis Bank", "AXISBN" to "Axis Bank", "AXIS" to "Axis Bank", "AXISBN" to "Axis Bank",
    "ICICIB" to "ICICI Bank", "ICICIP" to "ICICI Bank", "ICICIT" to "ICICI Bank", "ICIC" to "ICICI Bank",
    "SBIN" to "SBI", "SBIINB" to "SBI", "SBIPS" to "SBI", "SBICRD" to "SBI Credit Card", "SBIC" to "SBI",
    "JTEDGE" to "Jupiter Bank", "JUPITR" to "Jupiter Bank",
    "KOTAKB" to "Kotak Bank", "KOTAKM" to "Kotak Bank", "KKBK" to "Kotak Bank",
    "FDRL" to "Federal Bank", "FEDBNK" to "Federal Bank",
    "IDFCBK" to "IDFC FIRST Bank", "IDFB" to "IDFC FIRST Bank", "IDFCFB" to "IDFC FIRST Bank",
    "ONECRD" to "One Card", "SLICE" to "Slice Card", "SLCE" to "Slice Card", "SLCEIT" to "Slice Account",
    "PAYTM" to "Paytm", "PYTM" to "Paytm", "IPAYTM" to "Paytm Bank",
    "BARODA" to "BOB", "BOB" to "BOB", "BOBTXN" to "BOB", "BOBSMS" to "BOB",
    "PUNBNB" to "PNB", "PNBSMS" to "PNB", "PNBBK" to "PNB",
    "CANBK" to "Canara Bank", "CNRB" to "Canara Bank",
    "YESBNK" to "Yes Bank", "YESB" to "Yes Bank",
    "UBIN" to "Union Bank", "UBINBK" to "Union Bank", "UNBK" to "Union Bank",
    "IDIBNK" to "Indian Bank", "INDIAN" to "Indian Bank",
    "CBIN" to "Central Bank", "CBIND" to "Central Bank", "CBI" to "Central Bank",
    "BKID" to "BOI", "BOISMS" to "BOI", "BOI" to "BOI",
    "RBLBNK" to "RBL Bank", "RBLBK" to "RBL Bank", "RBL" to "RBL Bank",
    "AUBNK" to "AU Bank", "AUFIRA" to "AU Bank",
    "EQUTAS" to "Equitas Bank", "UJJIVN" to "Ujjivan Bank",
    "DBSSMS" to "DBS Bank", "DBSBK" to "DBS Bank", "DBSBNK" to "DBS Bank",
    "SCBANK" to "Standard Chartered", "SCREDC" to "Standard Chartered", "STANCB" to "Standard Chartered",
    "CITIBK" to "Citi Bank", "CITI" to "Citi Bank",
    "HSBCBK" to "HSBC Bank", "HSBC" to "HSBC Bank", "HSBCIM" to "HSBC Bank",
    "BAJAJF" to "Bajaj Finance", "BAJAJ" to "Bajaj Finance",
    "AMEX" to "American Express", "AMEXIN" to "American Express",
    "TIDEPF" to "Tide Card", "TIDE" to "Tide Card",
    "JKBANK" to "J&K Bank", "JKGRAM" to "J&K Grameen Bank",
    "SIB" to "South Indian Bank", "SIBL" to "South Indian Bank",
    "KARB" to "Karnataka Bank", "KTKBNK" to "Karnataka Bank",
    "MAHAB" to "Bank of Maharashtra", "MAHBNK" to "Bank of Maharashtra",
    "UCOBNK" to "UCO Bank", "UCO" to "UCO Bank",
    "IOB" to "IOB", "IOBA" to "IOB",
    "AIRBNK" to "Airtel Payments Bank", "AIRTEL" to "Airtel Payments Bank",
    "JIOPAY" to "Jio Payments Bank", "JIOPBL" to "Jio Payments Bank",
    "BANDHAN" to "Bandhan Bank", "BDBN" to "Bandhan Bank",
    "IDBIBK" to "IDBI Bank", "IDBI" to "IDBI Bank",
    "PSBANK" to "Punjab & Sind Bank", "PSB" to "Punjab & Sind Bank",
    "ANDBNK" to "Andhra Bank",
    "SYNBNK" to "Syndicate Bank", "SYNB" to "Syndicate Bank",
    "IPPB" to "India Post Payments Bank",
    "FINO" to "Fino Payments Bank",
    "KVB" to "Karur Vysya Bank", "KARUR" to "Karur Vysya Bank",
    "TMB" to "Tamilnad Mercantile Bank",
    "DHAN" to "Dhanlaxmi Bank"
).mapKeys { it.key.uppercase() }

private val PARTICIPANT_EXCLUSIONS = listOf(
    "Info:", "Ref:", "RTGS", "NEFT", "UPI", "via", "using", "vide",
    "on", "at", "Not you", "Call", "to report", "towards",
    "Bharat Bill", "BBPS", "Team", "Help", "Helpline", "NetBanking",
    "Block", "SMS", "Urgent", "Click", "Link", "Touch",
    "Rs", "INR", "₹", "\u20b9", "invoice", "dispute", "clearing", "update", "report", "issue", "report issue",
    "Dear Customer", "Dear Customer,", "Dear", "Pay Now", "Realization",
    "Available Bal", "Avl Bal", "An A", "An Account", "A/C", "Declined",
    "Cardmember", "Bank Cardmember", "Thank", "Thank you", "Thanks", "Congratulations",
    "Cash Deposit", "Cash Withdrawal", "IMPS", "RTGS", "UPI"
)

private val VPA_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+""")

// ==========================================
// 3. MAIN EXTRACTOR FUNCTION (Hybrid)
// ==========================================

fun String.extractTransactionInfo(context: Context, address: String): TransactionInfo? {
    val raw = this.trim()
    if (raw.isEmpty()) return null
    val body = normalizeWhitespace(raw)
    val lowerBody = body.lowercase(Locale.getDefault())
    val upperAddress = address.uppercase(Locale.getDefault())

    // 1. Initialize & Run ML
    TransactionML.init(context)
    val ml = TransactionML.predict(context, body)

    // 2. Initial Regex Filters
    if (NON_TRANSACTION_PHRASES.any { it.containsMatchIn(body) }) return null

    val isStatement = STATEMENT_REGEX.any { it.containsMatchIn(body) } || (lowerBody.contains("minimum") && lowerBody.contains("due"))
    if (isStatement) {
        return TransactionInfo(
            amount = 0.0, ttsAmount = 0.0, source = context.getString(R.string.bank),
            isDebit = false, participant = null, isInterest = false, isStatement = true,
            isCreditCard = false, isRecurring = false, confidence = 1.0
        )
    }

    // 3. Regex Signal Extraction
    val debitSignal = DEBIT_REGEX.any { it.containsMatchIn(body) }
    val rawCreditSignal = CREDIT_REGEX.any { it.containsMatchIn(body) }
    val interestSignal = INTEREST_REGEX.any { it.containsMatchIn(body) }
    val recurringSignal = RECURRING_REGEX.any { it.containsMatchIn(body) }
    val amount = extractAmount(body)

    // 4. ML Validation & Enhancement
    val mlDebit = ml?.label == "TRANSACTION_DEBIT" && ml.confidence > 0.8
    val mlCredit = ml?.label == "TRANSACTION_CREDIT" && ml.confidence > 0.8
    val mlValid = ml?.label != "OTP" && ml?.label != "NORMAL" && ml?.label != "DELIVERY" && ml?.confidence ?: 0.0 > 0.7

    val hasFalsePositive = FALSE_POSITIVE_REGEX.any { it.containsMatchIn(body) }
    val hasFalseNegative = FALSE_NEGATIVE_REGEX.any { it.containsMatchIn(body) }
    val isStrongTransaction = amount != null && (debitSignal || rawCreditSignal || mlDebit || mlCredit)

    // OTP/Spam Filter Logic
    if (OTP_PROXIMITY_REGEX.containsMatchIn(body)) {
        if (!isStrongTransaction && !hasFalseNegative) return null
    }

    if (hasFalsePositive && !hasFalseNegative) {
        // If it looks like spam but ML is super confident it's a transaction, give ML the benefit of the doubt
        if (!isStrongTransaction && !mlValid) return null
    }

    // Must have amount, or strictly defined interest/credit/debit signal
    if (!debitSignal && !rawCreditSignal && !interestSignal && !mlDebit && !mlCredit && amount == null) return null
    if (amount == null) return null

    // 5. Source & Credit/Debit Logic
    val (sourceRaw, detectedCard) = identifySource(context, upperAddress, body)

    // Final Bank Name Fallback (Regex Source -> ML Source -> Default)
    val finalSource = if (sourceRaw.isBlank() && ml?.nerBank != null) {
        ml.nerBank
    } else {
        sourceRaw.ifBlank { context.getString(R.string.bank) }
    }

    val isRefund = lowerBody.contains("refund") || lowerBody.contains("reversal")
    val creditedToSelfRegex = Regex("""(?i)credited\s+to\s+(?:your|a/c|acct|account|card|my)""")
    val isSelfCredit = rawCreditSignal && (creditedToSelfRegex.containsMatchIn(body) || !body.contains("credited to", ignoreCase = true))

    var isCredit = isRefund || (isSelfCredit && !debitSignal) || mlCredit
    var isDebit = debitSignal || (rawCreditSignal && !isCredit) || mlDebit

    // If source is a known bank and amount is present, force detection
    val knownBank = sourceRaw.isNotBlank() // identified from SOURCE_MAP
    if (knownBank && amount != null && !isStatement) {
        if (!isDebit && !isCredit) {
            // Fallback keywords
            if (lowerBody.contains("debited") || lowerBody.contains("dr") || lowerBody.contains("spent") || lowerBody.contains("paid") || lowerBody.contains("sent") || lowerBody.contains(
                    "withdrawn"
                ) || lowerBody.contains("used")
            ) {
                isDebit = true
            } else if (lowerBody.contains("credited") || lowerBody.contains("cr") || lowerBody.contains("received") || lowerBody.contains("deposited") || lowerBody.contains(
                    "added"
                )
            ) {
                isCredit = true
            }
        }
    }

    if (isDebit && isCredit) {
        if (lowerBody.contains("spent") || lowerBody.contains("paid") || lowerBody.contains("debited") || lowerBody.contains("sent") || lowerBody.contains("used")) {
            isCredit = false
        } else {
            isDebit = false
        }
    }

    if (!isDebit && !isCredit && !interestSignal) return null

    // 6. Participant Extraction
    // Try Regex first (more accurate for specific patterns)
    var participant = extractParticipant(body, isDebit, finalSource)

    // If Regex failed, ask ML NER
    if (participant == null && ml?.nerMerchant != null) {
        val mlMerchant = cleanParticipantName(ml.nerMerchant)
        if (!mlMerchant.isNullOrBlank()) {
            participant = mlMerchant
        }
    }

    val confidence = computeConfidence(
        debitSignal = isDebit,
        creditSignal = isCredit,
        hasSource = finalSource.isNotBlank(),
        hasParticipant = !participant.isNullOrBlank(),
        interestSignal = interestSignal,
        body = body
    )

    // Boost confidence if ML agrees
    var finalConfidence = confidence
    if (ml != null && ml.confidence > 0.8 && (ml.label.contains("TRANSACTION"))) {
        finalConfidence += 0.15
    }

    if (finalConfidence < MIN_CONFIDENCE) return null

    val ttsAmount = formatForTts(amount)

    return TransactionInfo(
        amount = amount,
        ttsAmount = ttsAmount,
        source = finalSource,
        isDebit = isDebit,
        participant = participant,
        isInterest = interestSignal,
        isStatement = false,
        isCreditCard = detectedCard,
        isRecurring = recurringSignal,
        confidence = (finalConfidence * 100.0).roundToLong() / 100.0
    )
}

// ==========================================
// 4. HELPER FUNCTIONS (Updated from V2)
// ==========================================

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
    debitSignal: Boolean, creditSignal: Boolean, hasSource: Boolean,
    hasParticipant: Boolean, interestSignal: Boolean, body: String
): Double {
    var score = 0.0
    score += 0.4
    if (debitSignal || creditSignal) score += 0.3
    if (hasSource) score += 0.15
    if (hasParticipant) score += 0.1
    if (interestSignal) score += 0.05
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
            if (ACRONYMS_TO_SPACE.contains(word.uppercase())) word.uppercase().toCharArray().joinToString(" ") else word
        }
    }

    val lowerBody = body.lowercase(Locale.getDefault())
    val headerSuggestsCard = CREDIT_SENDER_INDICATORS.any { upperAddress.contains(it) }
    val explicitDebitCard = lowerBody.contains("debit card")
    val isCreditCard = CREDIT_CARD_REGEX.containsMatchIn(body) && !explicitDebitCard

    fun formatDigits(token: String): String {
        val lastFour = token.filter { it.isDigit() }.takeLast(4)
        return lastFour.map { "$it " }.joinToString("").trim()
    }

    fun combineParts(base: String, digits: String, isCard: Boolean): String {
        val suffix = if (isCard) context.getString(R.string.credit_card) else context.getString(R.string.account)
        val spacedDigits = if (digits.isNotEmpty()) digits else ""
        if (base.contains(suffix, ignoreCase = true)) return "$base $spacedDigits".trim()
        return "$base $suffix $spacedDigits".trim()
    }

    for ((key, value) in SOURCE_MAP) {
        if (upperAddress.contains(key)) {
            val base = formatSourceName(value)
            val shouldBeCard = headerSuggestsCard || key.contains("CRD", ignoreCase = true) || value.contains("Credit", ignoreCase = true) || isCreditCard
            val digits = SOURCE_FALLBACK_REGEX.find(body)?.let { formatDigits(it.groupValues[1]) } ?: ""
            val finalSource = combineParts(base, digits, shouldBeCard)
            val isCreditCardBool = (shouldBeCard || finalSource.contains(context.getString(R.string.credit_card), ignoreCase = true)) && !explicitDebitCard
            return Pair(finalSource, isCreditCardBool)
        }
    }

    for ((key, value) in SOURCE_MAP) {
        if (lowerBody.contains(key.lowercase())) {
            val base = formatSourceName(value)
            val shouldBeCard = headerSuggestsCard || key.contains("CRD", ignoreCase = true) || value.contains("Credit", ignoreCase = true) || isCreditCard
            val digits = SOURCE_FALLBACK_REGEX.find(body)?.let { formatDigits(it.groupValues[1]) } ?: ""
            val finalSource = combineParts(base, digits, shouldBeCard)
            val isCreditCardBool = (shouldBeCard || finalSource.contains(context.getString(R.string.credit_card), ignoreCase = true)) && !explicitDebitCard
            return Pair(finalSource, isCreditCardBool)
        }
    }

    val bodySourceRegex = Regex("""(?i)\b([A-Za-z]{3,20})\s+(?:bank|credit card|debit card|card)\b""")
    bodySourceRegex.find(body)?.let {
        val found = it.groupValues[1].trim()
        val base = formatSourceName(found)
        val explicitCard = it.value.contains("credit", ignoreCase = true) || CARD_PHRASE_REGEX.containsMatchIn(body) || isCreditCard
        val digits = SOURCE_FALLBACK_REGEX.find(body)?.let { formatDigits(it.groupValues[1]) } ?: ""
        val finalSource = combineParts(base, digits, explicitCard)
        val isCreditCardBool = (explicitCard || finalSource.contains(context.getString(R.string.credit_card), ignoreCase = true)) && !explicitDebitCard
        return Pair(finalSource, isCreditCardBool)
    }

    SOURCE_FALLBACK_REGEX.find(body)?.let {
        val digits = formatDigits(it.groupValues[1])
        val isCard = it.value.contains(
            "card",
            ignoreCase = true
        ) || CARD_PHRASE_REGEX.containsMatchIn(body) || MASKED_CARD_REGEX.containsMatchIn(body) || headerSuggestsCard || isCreditCard
        val suffix = if (isCard) context.getString(R.string.credit_card) else context.getString(R.string.account)
        val finalSource = "$suffix $digits".trim()
        val isCreditCardBool = isCard && !explicitDebitCard
        return Pair(finalSource, isCreditCardBool)
    }

    if (MASKED_CARD_REGEX.containsMatchIn(body) || CARD_PHRASE_REGEX.containsMatchIn(body) || headerSuggestsCard || isCreditCard) {
        val generic = context.getString(R.string.credit_card)
        return Pair(generic, true)
    }

    return Pair("", false)
}

private fun extractParticipant(body: String, isDebit: Boolean, bankName: String): String? {
    val normalized = normalizeWhitespace(body)
    if (normalized.contains("linked to your", ignoreCase = true)) return null

    fun isInvalidParticipant(name: String?): Boolean {
        if (name == null) return true
        val lower = name.lowercase()
        if (bankName.lowercase().contains(lower) || lower.contains(bankName.lowercase())) return true
        return false
    }

    val spentAtRegex = Regex("""(?i)\bspent\s+.*?\s+at\s+([A-Za-z0-9&.\- ]{2,80})""")
    spentAtRegex.find(normalized)?.let {
        val cleaned = cleanParticipantName(it.groupValues[1])
        if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
    }

    val spentRegex = Regex("""(?i)\bspent\s?(?!on\b)@?\s?([A-Za-z0-9\s&.\-]{3,60})""")
    spentRegex.find(normalized)?.let {
        val cleaned = cleanParticipantName(it.groupValues[1])
        if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
    }

    val paidRegex = Regex("""(?i)\bpaid\s+(?:to|from)\s+([A-Za-z0-9@._&.\- ]{3,80})""")
    paidRegex.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
    }

    val debitSearch = Regex("""(?i)(?:to|at|towards|via|@)\s+([A-Za-z0-9@._&.\- ]{2,80})""")
    val creditSearch = Regex("""(?i)(?:from|by)\s+([A-Za-z0-9@._&.\- ]{2,80})""")

    val primary = if (isDebit) debitSearch else creditSearch
    primary.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
    }

    val secondary = if (isDebit) creditSearch else debitSearch
    secondary.findAll(normalized).forEach { match ->
        val cleaned = cleanParticipantName(match.groupValues[1])
        if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
    }

    if (normalized.contains(" to ", ignoreCase = true)) {
        val fallbackTo = Regex("""(?i)\bto\s+([A-Za-z0-9&.\- ]{2,50})""")
        fallbackTo.findAll(normalized).forEach { match ->
            val cleaned = cleanParticipantName(match.groupValues[1])
            if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
        }
    }
    if (normalized.contains(" from ", ignoreCase = true)) {
        val fallbackFrom = Regex("""(?i)\bfrom\s+([A-Za-z0-9&.\- ]{2,50})""")
        fallbackFrom.findAll(normalized).forEach { match ->
            val cleaned = cleanParticipantName(match.groupValues[1])
            if (!cleaned.isNullOrBlank() && !isInvalidParticipant(cleaned)) return cleaned
        }
    }

    val trailingRegex = Regex("""(?i)(?:on\s+[\d-]{5,})?\.?\s*\b(ATM\s+Cash|Cash\s+Withdrawal|POS|E-COM)\b$""")
    trailingRegex.find(normalized)?.let { return it.groupValues[1] }

    val infoRegex = Regex("""(?i)\b(?:Info|Ref|Narration)\s?[-:]\s?([A-Za-z0-9\s/.\-]+)""")
    infoRegex.find(normalized)?.let {
        val rawInfo = it.groupValues[1]
        val tokens = rawInfo.split("/")
        for (token in tokens.reversed()) {
            val cleaned = cleanParticipantName(token)
            if (!cleaned.isNullOrBlank() && !cleaned.equals("NEFT", true) && !cleaned.equals("IMPS", true) && !cleaned.equals(
                    "UPI",
                    true
                ) && !isInvalidParticipant(cleaned)
            ) {
                return cleaned
            }
        }
    }

    if (normalized.contains("UPI", ignoreCase = true)) return "UPI Transfer"
    if (normalized.contains("IMPS", ignoreCase = true)) return "IMPS Transfer"
    if (normalized.contains("NEFT", ignoreCase = true)) return "NEFT Transfer"

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

    name = name.trim { !it.isLetterOrDigit() }
    if (name.isEmpty()) return null
    if (VPA_REGEX.containsMatchIn(name)) return "UPI"
    if (name.contains("http", ignoreCase = true) || name.contains("www", ignoreCase = true)) return null

    val lower = name.lowercase(Locale.getDefault())
    val genericTokens = listOf("your", "payment", "received", "sent", "avl", "limit", "not you", "not you?", "total", "outstanding", "transaction", "thank")
    if (genericTokens.any { lower.startsWith(it) || lower.contains(" $it") }) return null
    if (lower.startsWith("spent") || lower.startsWith("inr") || lower.startsWith("rs") || lower.startsWith("avl")) return null
    if (lower.startsWith("not you") || lower.startsWith("call") || lower.startsWith("ref")) return null
    if (lower.contains("thank you") || lower.contains("dear customer") || lower.contains("this message")) return null
    if (lower.startsWith("congratulations") || lower.contains("cashback")) return null
    if (lower.contains("mobile") && (name.contains("X", ignoreCase = true) || name.any { it.isDigit() })) return null
    if (name.contains("XXXX", ignoreCase = true) || (name.count { it == '*' } > 2)) return null
    if (lower.contains("a/c") || lower.contains("account")) return null

    if (PARTICIPANT_EXCLUSIONS.any { name.equals(it, ignoreCase = true) || name.startsWith(it, ignoreCase = true) }) return null
    if (name.count { it.isDigit() } > 3) return null
    if (name.length < 2) return null

    val noiseWords = listOf("pvt", "ltd", "private", "limited", "solutions", "services")
    var cleanNameParts = name.split(" ").filter { !noiseWords.contains(it.lowercase(Locale.getDefault()).replace(".", "")) }
    cleanNameParts = cleanNameParts.filterIndexed { index, s -> index == 0 || !s.equals(cleanNameParts[index - 1], ignoreCase = true) }

    name = cleanNameParts.joinToString(" ")
    name = name.trim().trimEnd('.', ',', ';', ':')

    val words = name.split("\\s+".toRegex())
    val uppercaseWords = words.count { it.length > 1 && it == it.uppercase(Locale.getDefault()) }
    if (uppercaseWords == words.size) {
        if (name.contains("INR", ignoreCase = true) || name.contains("RS", ignoreCase = true) || name.contains("AVL", ignoreCase = true)) return null
    }

    val isAllUppercase = name.all { !it.isLetter() || it.isUpperCase() }
    if (isAllUppercase && name.any { it.isLetter() }) {
        return name.lowercase(Locale.getDefault()).split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
    return name
}
