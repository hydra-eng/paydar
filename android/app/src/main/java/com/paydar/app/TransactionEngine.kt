package com.paydar.app

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class TransactionRecord(
    val id: Long = 0,
    val amount: String,
    val direction: String,
    val status: String,
    val party: String,
    val upi: String = "",
    val reference: String = "",
    val app: String = "Not identified",
    val occurred: Long,
    val sources: String,
    val fingerprint: String,
)

data class TransactionImport(val records: List<TransactionRecord>, val skipped: Int)

object TransactionEngine {
    val paymentApps =
        linkedMapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "com.phonepe.app" to "PhonePe",
            "net.one97.paytm" to "Paytm",
            "in.org.npci.upiapp" to "BHIM",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
        )
    private val options = setOf(RegexOption.IGNORE_CASE)

    private fun hash(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(
            ""
        ) {
            "%02x".format(it)
        }

    fun validate(record: TransactionRecord): TransactionRecord {
        val amount = PayDarEngine.money(record.amount).setScale(2).toPlainString()
        require(record.direction in setOf("DEBIT", "CREDIT")) { "Choose money sent or received." }
        require(record.status in setOf("SUCCEEDED", "PENDING", "FAILED")) {
            "Choose a receipt status."
        }
        require(record.occurred in 946684800000L..System.currentTimeMillis() + 300000L) {
            "Use a transaction date from 2000 through today."
        }
        fun clean(value: String, maximum: Int): String {
            require(value.length <= maximum && value.none { it.isISOControl() }) {
                "A transaction field is too long or contains unsupported characters."
            }
            return value.trim()
        }
        val party = clean(record.party, 120)
        require(party.isNotBlank()) { "Enter the recipient or sender." }
        val reference = clean(record.reference, 80).uppercase(Locale.ROOT)
        require(reference.isEmpty() || Regex("[A-Z0-9_-]{6,80}").matches(reference)) {
            "Use the reference printed on your receipt (6–80 letters/digits)."
        }
        val upi = clean(record.upi, 192).lowercase(Locale.ROOT)
        require(upi.isEmpty() || PayDarEngine.validUpi(upi)) {
            "Enter a valid UPI ID, or leave it empty."
        }
        val app = clean(record.app, 80).ifBlank { "Not identified" }
        val sources =
            record.sources
                .split('\n')
                .map { clean(it, 160) }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")
        require(sources.isNotBlank()) { "The transaction source is required." }
        val fingerprint =
            if (reference.isNotEmpty()) hash("ref|$reference|${record.direction}|$amount")
            else
                record.fingerprint.takeIf { Regex("[a-f0-9]{64}").matches(it) }
                    ?: hash(
                        "manual|$sources|${record.occurred}|$amount|${record.direction}|$party|$upi"
                    )
        return record.copy(
            amount = amount,
            party = party,
            reference = reference,
            upi = upi,
            app = app,
            sources = sources,
            fingerprint = fingerprint,
        )
    }

    /**
     * Receipt parsing is conservative. Requests, OTPs, balances and promotions are not payments.
     */
    fun parseReceipt(
        raw: String,
        occurred: Long,
        source: String,
        app: String = "Not identified",
        eventKey: String = "",
    ): TransactionRecord? {
        if (raw.length !in 8..5000) return null
        if (
            Regex(
                    "\\b(otp|one[ -]time password|verification code|payment request|collect request|request(?:ed)? money|pay now|due on|scheduled|will be debited|cashback offer|reward points)\\b",
                    options,
                )
                .containsMatchIn(raw)
        )
            return null
        val incoming =
            Regex("\\b(credited|received|refund(?:ed)?|reversed)\\b", options).containsMatchIn(raw)
        val outgoing =
            Regex(
                    "\\b(debited|paid|sent|transferred|payment (?:of|to|for)|payment.*(?:successful|failed|pending)|transaction.*(?:successful|failed|pending))\\b",
                    options,
                )
                .containsMatchIn(raw)
        // A failed or pending generic payment has no known direction; don't guess.
        if (incoming == outgoing) return null
        if (
            Regex("\\b(balance|available|avl|limit)\\b", options).find(raw)?.range?.first?.let {
                first ->
                val amountStart =
                    Regex("(?:INR|Rs\\.?|₹)\\s*[0-9]", options).find(raw)?.range?.first
                        ?: Int.MAX_VALUE
                first < amountStart
            } == true
        )
            return null
        val amount =
            Regex("(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9.])", options)
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.replace(",", "") ?: return null
        val value =
            runCatching { PayDarEngine.money(amount).setScale(2).toPlainString() }.getOrNull()
                ?: return null
        val upi =
            Regex(
                    "(?<![A-Za-z0-9._-])([A-Za-z0-9][A-Za-z0-9._-]{1,127}@[A-Za-z][A-Za-z0-9.-]{1,63})",
                    options,
                )
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.trimEnd('.')
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        val reference =
            Regex(
                    "(?:UPI\\s*(?:ref(?:erence)?(?:\\s*(?:no|number))?|txn(?:\\s*id)?)|UTR|RRN|Ref(?:erence)?(?:\\s*(?:no|number|id))?|Txn(?:\\s*id)?|Transaction\\s*id)\\s*[:.#-]?\\s*([A-Za-z0-9_-]{6,80})",
                    options,
                )
                .find(raw)
                ?.groupValues
                ?.get(1)
                .orEmpty()
        val partyStart =
            if (incoming) "(?:received from|\\bfrom)"
            else "(?:paid to|sent to|transferred to|\\bto|\\bat)"
        val party =
            Regex(
                    "$partyStart\\s+([A-Za-z][A-Za-z0-9 &@._-]{1,119}?)(?=\\s+(?:on|via|using|ref|txn|UPI|for|a/c|account|has)|[;\\n]|$)",
                    options,
                )
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.trimEnd('.')
                ?: upi.ifBlank { if (incoming) "Sender not provided" else "Recipient not provided" }
        val state =
            when {
                Regex("\\b(failed|declined|unsuccessful)\\b", options).containsMatchIn(raw) ->
                    "FAILED"
                Regex("\\b(pending|processing|in progress)\\b", options).containsMatchIn(raw) ->
                    "PENDING"
                else -> "SUCCEEDED"
            }
        return runCatching {
                validate(
                    TransactionRecord(
                        amount = value,
                        direction = if (incoming) "CREDIT" else "DEBIT",
                        status = state,
                        party = party,
                        upi = upi,
                        reference = reference,
                        app = app,
                        occurred = occurred,
                        sources = source,
                        fingerprint =
                            hash(
                                "receipt|$source|${eventKey.ifBlank { raw.trim() }}|$occurred|$value|${if(incoming) "CREDIT" else "DEBIT"}"
                            ),
                    )
                )
            }
            .getOrNull()
    }

    private val headers =
        listOf(
            "date",
            "amount",
            "direction",
            "status",
            "party",
            "upi",
            "reference",
            "app",
            "source",
        )

    // Quoted CSV fields and CRLF are supported. The schema is explicit rather than guessed.
    fun readCsv(csv: String): TransactionImport {
        require(csv.length <= 2_000_000) { "Choose a CSV file smaller than 2 MB." }
        val rows = mutableListOf<List<String>>()
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var afterQuote = false
        var i = 0
        fun finishField() {
            require(field.length <= 5000) { "A CSV field is too long." }
            fields.add(field.toString())
            field.clear()
            afterQuote = false
        }
        fun finishRow() {
            finishField()
            if (fields.any { it.isNotBlank() }) rows.add(fields.toList())
            fields.clear()
            require(rows.size <= 10001) { "Import at most 10,000 transactions." }
        }
        while (i < csv.length) {
            val c = csv[i]
            when {
                quoted && c == '"' && csv.getOrNull(i + 1) == '"' -> {
                    field.append('"')
                    i++
                }
                quoted && c == '"' -> {
                    quoted = false
                    afterQuote = true
                }
                quoted -> field.append(c)
                c == '"' && field.isEmpty() && !afterQuote -> quoted = true
                c == ',' -> finishField()
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && csv.getOrNull(i + 1) == '\n') i++
                    finishRow()
                }
                afterQuote && c.isWhitespace() -> Unit
                else -> {
                    require(!afterQuote && c != '"') { "Malformed CSV quoting." }
                    field.append(c)
                }
            }
            i++
        }
        require(!quoted) { "A quoted CSV field is not closed." }
        if (field.isNotEmpty() || fields.isNotEmpty() || afterQuote) finishRow()
        require(rows.isNotEmpty()) { "This CSV is empty." }
        val header =
            rows.removeAt(0).map { it.trim().removePrefix("\uFEFF").lowercase(Locale.ROOT) }
        require(header == headers) {
            "Use the PayDar CSV columns: ${headers.joinToString(",")}. Export a template from Transactions."
        }
        var skipped = 0
        val records =
            rows.mapNotNull { row ->
                runCatching {
                        require(row.size == headers.size)
                        val date =
                            runCatching { Instant.parse(row[0].trim()).toEpochMilli() }
                                .getOrElse {
                                    LocalDate.parse(row[0].trim())
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli()
                                }
                        validate(
                            TransactionRecord(
                                amount = row[1].trim(),
                                direction = row[2].trim().uppercase(Locale.ROOT),
                                status = row[3].trim().uppercase(Locale.ROOT),
                                party = row[4],
                                upi = row[5],
                                reference = row[6],
                                app = row[7],
                                occurred = date,
                                sources = "CSV",
                                fingerprint = "",
                            )
                        )
                    }
                    .getOrElse {
                        skipped++
                        null
                    }
            }
        return TransactionImport(records, skipped)
    }

    fun exportCsv(records: List<TransactionRecord>): String {
        fun quote(s: String): String {
            // Neutralize spreadsheet formulas in fields supplied by other apps/users.
            val safe = if (s.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$s" else s
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        return headers.joinToString(",") +
            "\r\n" +
            records.joinToString("\r\n") { r ->
                listOf(
                        Instant.ofEpochMilli(r.occurred).toString(),
                        r.amount,
                        r.direction,
                        r.status,
                        r.party,
                        r.upi,
                        r.reference,
                        r.app,
                        r.sources.replace('\n', ';'),
                    )
                    .joinToString(",", transform = ::quote)
            } +
            if (records.isEmpty()) "" else "\r\n"
    }
}
