package com.vk.messages.data

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vk.messages.helpers.TransactionInfo
import com.vk.messages.helpers.extractTransactionInfo
import java.util.regex.Pattern

@Keep
enum class SmsTag {
    UNREAD,
    PERSONAL,
    TRANSACTIONAL,
    BANKING,
    MARKETING,
    INVESTMENTS,
    GOVT,
    ORDERS,
    SERVICE,
    SPAM,
    ALL
}

@Keep
data class Sender(val address: String, val tags: MutableSet<SmsTag> = mutableSetOf())

@Keep
object SmsRepository {

    private val senders = mutableMapOf<String, Sender>()

    // TRAI Suffixes (Case Insensitive): Matches addresses ending in -T, -S, -P, -G
    private val traiSuffixPattern = Pattern.compile(".*-([TSPG])$", Pattern.CASE_INSENSITIVE)

    private val investmentsPattern = Pattern.compile("(?i)(invest|stock|mutual fund|sip|portfolio|demat|nse|bse|equity|shares|nav of)")
    private val ordersPattern = Pattern.compile("(?i)(order|delivered|shipped|delivery|out for delivery|arriving|track|awb|zomato|swiggy|flipkart|amazon|myntra|bluedart|delhivery)")
    private val spamPattern = Pattern.compile("(?i)(congratulations|won|prize|lottery|betting|spin.*win|jackpot|loan.*approved|click.*link|bit\\.ly|call.*now|limited.*offer|urgent|dream11|rummy)")

    // Pre-defined headers
    private val knownHeaders = mapOf(
        "ZRODHA" to SmsTag.INVESTMENTS,
        "ZERODHA" to SmsTag.INVESTMENTS,
        "GROWW" to SmsTag.INVESTMENTS,
        "UPSTOX" to SmsTag.INVESTMENTS,
        "INDMON" to SmsTag.INVESTMENTS,
        "AMAZON" to SmsTag.ORDERS,
        "FLPKRT" to SmsTag.ORDERS,
        "MYNTRA" to SmsTag.ORDERS,
        "SWIGGY" to SmsTag.ORDERS,
        "ZOMATO" to SmsTag.ORDERS,
        "HDFCBK" to SmsTag.BANKING,
        "ICICIB" to SmsTag.BANKING,
        "SBIN" to SmsTag.BANKING,
        "AXISBK" to SmsTag.BANKING
    )

    suspend fun loadAndTagSenders(context: Context) = withContext(Dispatchers.IO) {
        senders.clear()
        val contacts = getContacts(context)

        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.READ)
        val cursor: Cursor? = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val readIdx = it.getColumnIndexOrThrow(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: ""
                val isRead = it.getInt(readIdx) == 1

                val sender = senders.getOrPut(address) { Sender(address) }

                if (!isRead) {
                    sender.tags.add(SmsTag.UNREAD)
                }

                sender.tags.addAll(generateTags(context, address, body, contacts))
            }
        }
    }

    fun onNewSmsReceived(context: Context, address: String, body: String) {
        val newTags = generateTags(context, address, body, getContacts(context))
        newTags.add(SmsTag.UNREAD)
        val sender = senders.getOrPut(address) { Sender(address) }
        sender.tags.addAll(newTags)
    }

    fun getSendersForTag(tag: SmsTag): List<String> {
        return senders.values.filter { it.tags.contains(tag) }.map { it.address }
    }

    fun getConversationsWithAddresses(addresses: List<String>): List<Sender> {
        return senders.values.filter { addresses.contains(it.address) }
    }

    fun getSenderTags(address: String): Set<SmsTag> {
        return senders[address]?.tags ?: emptySet()
    }

    private fun generateTags(context: Context, address: String, body: String, contacts: Set<String>): MutableSet<SmsTag> {
        val detectedTags = mutableSetOf<SmsTag>()

        // 1. Check Personal (Contacts)
        if (contacts.any { PhoneNumberUtils.compare(it, address) }) {
            detectedTags.add(SmsTag.PERSONAL)
        }

        // 2. Check TRAI Suffixes
        val suffixMatcher = traiSuffixPattern.matcher(address)
        if (suffixMatcher.find()) {
            when (suffixMatcher.group(1)?.uppercase()) {
                "T" -> detectedTags.add(SmsTag.TRANSACTIONAL)
                "S" -> detectedTags.add(SmsTag.SERVICE)
                "P" -> detectedTags.add(SmsTag.MARKETING)
                "G" -> detectedTags.add(SmsTag.GOVT)
            }
        }

        // 3. Content & Header Analysis
        for ((key, tag) in knownHeaders) {
            if (address.contains(key, ignoreCase = true)) {
                detectedTags.add(tag)
            }
        }

        // Transaction Detector
        if (body.isNotBlank()) {
            val transactionInfo: TransactionInfo? = body.extractTransactionInfo(context, address)
            if (transactionInfo != null) {
                detectedTags.add(SmsTag.TRANSACTIONAL)
                if (transactionInfo.isCreditCard ||
                    transactionInfo.source.contains("bank", true) ||
                    transactionInfo.source.contains("card", true) ||
                    transactionInfo.amount > 0
                ) {
                    detectedTags.add(SmsTag.BANKING)
                }
            }
        }

        // Regex Categorization
        if (investmentsPattern.matcher(body).find()) detectedTags.add(SmsTag.INVESTMENTS)
        if (ordersPattern.matcher(body).find()) detectedTags.add(SmsTag.ORDERS)
        if (spamPattern.matcher(body).find()) detectedTags.add(SmsTag.SPAM)

        // Heuristic: Alphanumeric sender with no other tags likely Service
        if (detectedTags.isEmpty() && !address.any { it.isDigit() } && address.length >= 6) {
            detectedTags.add(SmsTag.SERVICE)
        }

        return detectedTags
    }

    private fun getContacts(context: Context): Set<String> {
        val contacts = mutableSetOf<String>()
        val cursor: Cursor? = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER),
            null,
            null,
            null
        )

        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
            if (numberIndex != -1) {
                while (it.moveToNext()) {
                    val number = it.getString(numberIndex)
                    if (number != null) {
                        contacts.add(number)
                    }
                }
            }
        }
        return contacts
    }
}
