package com.paydar.app

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    @Test
    fun parsesEncodedUpiAndAmount() {
        val p =
            PayDarEngine.parsePayment(
                "upi://pay?pa=Coffee%40bank&pn=Corner%20Coffee&am=180.50&cu=INR"
            )
        assertEquals("coffee@bank", p.upi)
        assertEquals("Corner Coffee", p.name)
        assertEquals("180.50", p.amount.toString())
    }

    @Test
    fun unknownIsNeverSafe() {
        assertEquals("UNKNOWN", PayDarEngine.check(Payment("coffee@bank"), emptyList()).level)
    }

    @Test
    fun normalizesCaseWithoutChangingSemantics() {
        assertEquals("coffee@bank", PayDarEngine.parsePayment(" COFFEE@BANK ").upi)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateRecipient() {
        PayDarEngine.parsePayment("upi://pay?pa=one@bank&pa=two@bank")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongScheme() {
        PayDarEngine.parsePayment("https://pay.example/?pa=one@bank")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsForeignCurrency() {
        PayDarEngine.parsePayment("upi://pay?pa=one@bank&cu=USD")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeAmount() {
        PayDarEngine.parsePayment("upi://pay?pa=one@bank&am=-10")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHiddenControlCharacters() {
        PayDarEngine.parsePayment("upi://pay?pa=one@bank&pn=abc%0Adef")
    }

    @Test
    fun detectsRecipientMismatch() {
        assertEquals(
            "HIGH",
            PayDarEngine.check(Payment("other@bank"), emptyList(), "coffee@bank").level,
        )
    }

    @Test
    fun expiredRecordIsQualified() {
        val record = FraudRecord("one@bank", "User report", "test", LocalDate.of(2020, 1, 1))
        assertTrue(
            PayDarEngine.check(Payment("one@bank"), listOf(record)).detail.contains("expired")
        )
    }

    @Test
    fun noHardcodedRecipientVerdicts() {
        assertEquals(
            "UNKNOWN",
            PayDarEngine.check(Payment("demo.flagged@paydar"), emptyList()).level,
        )
    }

    @Test
    fun muleForwardingTriggersHighRisk() {
        assertEquals("HIGH", PayDarEngine.mule(listOf(true, false, false)).level)
    }

    @Test
    fun allNoStillUnknown() {
        assertEquals("UNKNOWN", PayDarEngine.mule(listOf(false, false, false)).level)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteMuleCannotProduceVerdict() {
        PayDarEngine.mule(listOf(false, null, false))
    }

    @Test
    fun parsesKnownSubscription() {
        val s = PayDarEngine.parseSms("INR 499.00 debited for Netflix on 2026-10-01")
        assertEquals("Netflix", s?.merchant)
        assertEquals("499.00", s?.amount.toString())
    }

    @Test
    fun rejectsOtpAndCreditAndFailures() {
        for (s in
            listOf(
                "OTP 499999 for INR 499 Netflix payment",
                "INR 499 credited by Netflix",
                "Netflix payment Rs 499 failed",
            )) assertNull(PayDarEngine.parseSms(s))
    }

    @Test
    fun identicalMessagesHaveSameFingerprint() {
        val s = "Rs.119 paid to Spotify on 2026-10-01"
        assertEquals(PayDarEngine.parseSms(s)?.fingerprint, PayDarEngine.parseSms(s)?.fingerprint)
    }

    @Test
    fun reportIsDraftWithoutSecretFields() {
        val r =
            PayDarEngine.report(
                "coffee@bank",
                "180",
                "123456789012",
                "2026-01-01",
                "Wrong recipient",
            )
        assertTrue(r.contains("not a submitted complaint"))
        assertTrue(r.contains("180.00"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFutureReportDate() {
        PayDarEngine.report(
            "coffee@bank",
            "180",
            "123456789012",
            LocalDate.now().plusDays(1).toString(),
            "",
        )
    }
}
