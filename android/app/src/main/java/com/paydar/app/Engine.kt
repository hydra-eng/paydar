package com.paydar.app

import java.math.BigDecimal
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale

data class Payment(
    val upi: String,
    val name: String = "",
    val amount: BigDecimal? = null,
    val note: String = "",
)

data class Verdict(val level: String, val title: String, val detail: String)

data class FraudRecord(
    val upi: String,
    val reason: String,
    val source: String,
    val expires: LocalDate,
)

data class ParsedSubscription(
    val merchant: String,
    val amount: BigDecimal,
    val fingerprint: String,
)

object PayDarEngine {
    private val upiPattern = Regex("^[a-z0-9][a-z0-9._-]{1,127}@[a-z][a-z0-9.-]{1,63}$")

    fun normalize(raw: String) = raw.trim().lowercase(Locale.ROOT)

    fun validUpi(raw: String) = upiPattern.matches(normalize(raw))

    fun money(raw: String): BigDecimal {
        require(Regex("^[0-9]{1,9}(\\.[0-9]{1,2})?$").matches(raw.trim())) {
            "Enter a positive amount with up to two decimal places."
        }
        return raw.trim().toBigDecimal().also {
            require(it > BigDecimal.ZERO) { "Amount must be greater than zero." }
        }
    }

    fun parsePayment(raw: String): Payment {
        val input = raw.trim()
        require(input.length <= 4096) { "This QR contains too much data." }
        if (!input.startsWith("upi:", true)) {
            require(validUpi(input)) {
                "Enter a conventional UPI ID such as name@bank, or scan a UPI payment QR."
            }
            return Payment(normalize(input))
        }
        val uri =
            try {
                URI(input)
            } catch (_: Exception) {
                throw IllegalArgumentException("The QR contains an invalid URI.")
            }
        require(
            uri.scheme.equals("upi", true) &&
                uri.host.equals("pay", true) &&
                uri.userInfo == null &&
                uri.port == -1 &&
                uri.fragment == null &&
                (uri.path.isNullOrEmpty() || uri.path == "/")
        ) {
            "Only upi://pay payment codes are supported."
        }
        val params = linkedMapOf<String, String>()
        for (part in (uri.rawQuery ?: "").split('&').filter { it.isNotBlank() }) {
            val pair = part.split('=', limit = 2)
            val key = URLDecoder.decode(pair[0], "UTF-8").lowercase(Locale.ROOT)
            val value = URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
            require(key !in params) {
                "This QR has duplicate fields. Confirm the recipient directly."
            }
            require(value.none { it.isISOControl() }) {
                "This QR contains invalid control characters."
            }
            params[key] = value
        }
        val upi = normalize(params["pa"] ?: "")
        require(validUpi(upi)) { "No supported recipient UPI ID was found in this QR." }
        require(params["cu"].isNullOrBlank() || params["cu"].equals("INR", true)) {
            "Only INR payment codes are supported."
        }
        return Payment(
            upi,
            params["pn"].orEmpty().take(120),
            params["am"]?.takeIf { it.isNotBlank() }?.let(::money),
            params["tn"].orEmpty().take(240),
        )
    }

    fun check(
        payment: Payment,
        records: List<FraudRecord>,
        expected: String = "",
        today: LocalDate = LocalDate.now(),
    ): Verdict {
        if (expected.isNotBlank() && normalize(expected) != payment.upi)
            return Verdict(
                "HIGH",
                "Recipient mismatch",
                "The QR recipient does not match the UPI ID you expected. Stop and confirm the ID directly with the person or business.",
            )
        val found = records.firstOrNull { normalize(it.upi) == payment.upi }
        if (found != null) {
            val age = if (found.expires.isBefore(today)) "This record has expired. " else ""
            return Verdict(
                "CAUTION",
                "Reported in your imported list",
                "$age${found.reason}. Source label: ${found.source}. Imported records are not authenticated by PayDar. Independently verify this recipient; a list entry is not proof of fraud.",
            )
        }
        return Verdict(
            "UNKNOWN",
            "Unknown. Verify before paying.",
            "No match in your local list. That does not mean safe. Confirm the recipient name and ID independently in your payment app. The beta is not connected to an authoritative fraud database.",
        )
    }

    val muleQuestions =
        listOf(
            "Has someone asked you to receive money and send it to another account?",
            "Are you being offered a commission or reward for moving that money?",
            "Are they asking you to act urgently or keep the transfer secret?",
        )

    fun mule(answers: List<Boolean?>): Verdict {
        require(answers.size == 3 && answers.none { it == null }) {
            "Answer all three questions first."
        }
        val yes = answers.count { it == true }
        return when {
            answers[0] == true ->
                Verdict(
                    "HIGH",
                    "Pause the transfer",
                    "Receiving and forwarding someone else's money can expose you to a money-mule scheme. Do not move it on their instructions. Contact your bank through a channel you independently verify.",
                )
            yes > 0 ->
                Verdict(
                    "CAUTION",
                    "Something deserves a closer look",
                    "Rewards, pressure and secrecy are warning signals. Independently verify the request. Never share your PIN, OTP or account access.",
                )
            else ->
                Verdict(
                    "UNKNOWN",
                    "No selected warning signals",
                    "Your answers did not trigger these three rules. This is not a safety verdict. Other scams may use different patterns.",
                )
        }
    }

    fun parseSms(raw: String): ParsedSubscription? {
        if (raw.length !in 8..5000) return null
        if (
            Regex(
                    "\\b(otp|one.time.password|verification.code|credited|refund|failed|declined|reversed)\\b",
                    RegexOption.IGNORE_CASE,
                )
                .containsMatchIn(raw)
        )
            return null
        if (
            !Regex(
                    "\\b(debited|paid|charged|payment|subscription|renewal|autopay)\\b",
                    RegexOption.IGNORE_CASE,
                )
                .containsMatchIn(raw)
        )
            return null
        val amountText =
            Regex(
                    "(?:INR|Rs\\.?|₹)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)(?![0-9.])",
                    RegexOption.IGNORE_CASE,
                )
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.replace(",", "") ?: return null
        val amount = runCatching { money(amountText) }.getOrNull() ?: return null
        val names =
            listOf(
                "Netflix",
                "Spotify",
                "YouTube",
                "Amazon Prime",
                "Apple",
                "Hotstar",
                "JioCinema",
                "LinkedIn",
                "Kuku FM",
                "Google One",
                "Zee5",
                "SonyLiv",
                "Adobe",
                "Notion",
            )
        val merchant =
            names.firstOrNull {
                Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(raw)
            }
                ?: Regex(
                        "(?:paid to|payment to|at|towards|to)\\s+([A-Za-z][A-Za-z0-9 &.-]{1,45}?)(?=\\s+(?:on|via|using|ref|txn|for|from)|[.;\\n]|$)",
                        RegexOption.IGNORE_CASE,
                    )
                    .find(raw)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                ?: return null
        val fingerprint =
            MessageDigest.getInstance("SHA-256").digest(raw.trim().toByteArray()).joinToString("") {
                "%02x".format(it)
            }
        return ParsedSubscription(merchant, amount, fingerprint)
    }

    fun report(upi: String, amount: String, utr: String, date: String, notes: String): String {
        require(validUpi(upi)) { "Enter the recipient's UPI ID." }
        val value = money(amount)
        require(Regex("^[A-Za-z0-9-]{6,40}$").matches(utr.trim())) {
            "Enter the transaction reference from your payment app (6–40 letters/digits)."
        }
        val parsedDate =
            runCatching { LocalDate.parse(date) }
                .getOrElse {
                    throw IllegalArgumentException("Use a valid date in YYYY-MM-DD format.")
                }
        require(!parsedDate.isAfter(LocalDate.now())) {
            "The transaction date cannot be in the future."
        }
        return """PAYDAR — TRANSACTION SUPPORT DRAFT
            |Recipient UPI ID: ${normalize(upi)}
            |Amount: INR ${value.setScale(2)}
            |Transaction reference: ${utr.trim()}
            |Transaction date: $parsedDate
            |
            |Description supplied by the account holder:
            |${notes.trim().take(2000).ifBlank { "Payment sent to an unintended recipient. Please investigate the transaction and advise on the available resolution process." }}
            |
            |I request that my payment provider/bank investigate this transaction. I can provide the original transaction receipt through the bank's verified secure channel.
            |
            |This is a locally prepared draft, not a submitted complaint. No refund or recovery is guaranteed. Never include your UPI PIN, OTP, password or full card details.
        """
            .trimMargin()
    }
}
