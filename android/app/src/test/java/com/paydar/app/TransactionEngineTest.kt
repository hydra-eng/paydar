package com.paydar.app

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TransactionEngineTest {
    private val date = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()

    private fun parse(text: String) = TransactionEngine.parseReceipt(text, date, "SMS")

    @Test
    fun debitHasActualAmountUpiAndReference() {
        val r =
            parse(
                "INR 1,250.50 debited from account via UPI to coffee@bank on 05-Oct. UPI Ref No 123456789012. Avl bal INR 9,000."
            )!!
        assertEquals("1250.50", r.amount)
        assertEquals("DEBIT", r.direction)
        assertEquals("coffee@bank", r.upi)
        assertEquals("123456789012", r.reference)
        assertEquals("coffee@bank", r.party)
    }

    @Test
    fun creditAndRefundAreNotExpenses() {
        assertEquals(
            "CREDIT",
            parse("Received Rs. 500 from Alice via UPI. UTR 123456789012")!!.direction,
        )
        assertEquals(
            "CREDIT",
            parse("Refund INR 300 credited to your account via UPI. Ref 123456789013")!!.direction,
        )
    }

    @Test
    fun requestsOtpFutureDebitsAndPromotionsAreIgnored() {
        for (text in
            listOf(
                "OTP 123456 to pay INR 500 via UPI",
                "Payment request: pay now INR 500 to Alice",
                "INR 500 will be debited for autopay",
                "Cashback offer: received INR 500 reward points",
            )) assertNull(text, parse(text))
    }

    @Test
    fun balanceWithoutPaymentIsIgnored() {
        assertNull(parse("Available balance INR 1500 after UPI activity"))
        assertNull(parse("Balance INR 2000. Payment INR 300 sent to Alice via UPI"))
    }

    @Test
    fun statusesDoNotTurnFailedOrPendingAttemptsIntoSuccess() {
        assertEquals(
            "FAILED",
            parse("Payment of INR 500 to Alice failed via UPI. Ref 123456789012")!!.status,
        )
        assertEquals(
            "PENDING",
            parse("INR 500 sent to Alice via UPI, pending. Ref 123456789012")!!.status,
        )
    }

    @Test
    fun bothDirectionsAreAmbiguousAndIgnored() {
        assertNull(parse("INR 500 debited and INR 500 credited via UPI"))
    }

    @Test
    fun referenceMatchesAcrossSourcesButOppositeDirectionsRemainDistinct() {
        val sms = parse("INR 500 paid to Alice via UPI. Ref 123456789012")!!
        val app =
            TransactionEngine.parseReceipt(
                "Sent ₹500 to Alice. UTR 123456789012",
                date + 3000,
                "Notification: PhonePe",
                "PhonePe",
            )!!
        assertEquals(sms.fingerprint, app.fingerprint)
        assertNotEquals(
            sms.fingerprint,
            parse("INR 500 received from Alice via UPI. Ref 123456789012")!!.fingerprint,
        )
    }

    @Test
    fun sameAmountDifferentReferenceIsNotMerged() {
        assertNotEquals(
            parse("INR 500 paid to Alice. Ref 123456789012")!!.fingerprint,
            parse("INR 500 paid to Alice. Ref 123456789013")!!.fingerprint,
        )
    }

    @Test
    fun csvRoundTripPreservesMoneyQuotesAndTime() {
        val r =
            TransactionEngine.validate(
                TransactionRecord(
                    amount = "500",
                    direction = "DEBIT",
                    status = "SUCCEEDED",
                    party = "Alice, \"Cafe\"",
                    occurred = date,
                    sources = "Manual",
                    fingerprint = "",
                    app = "PhonePe",
                    reference = "123456789012",
                )
            )
        val parsed = TransactionEngine.readCsv(TransactionEngine.exportCsv(listOf(r)))
        assertEquals(0, parsed.skipped)
        assertEquals(r.party, parsed.records.single().party)
        assertEquals(r.amount, parsed.records.single().amount)
        assertEquals(date, parsed.records.single().occurred)
    }

    @Test
    fun csvInvalidRowsAreReportedAndNotInserted() {
        val csv =
            "date,amount,direction,status,party,upi,reference,app,source\n2026-10-05,0,DEBIT,SUCCEEDED,Alice,,,PhonePe,Manual\n2026-10-05,200,CREDIT,SUCCEEDED,Bob,,,Google Pay,Manual\n"
        val result = TransactionEngine.readCsv(csv)
        assertEquals(1, result.skipped)
        assertEquals(1, result.records.size)
        assertEquals("CSV", result.records.single().sources)
    }

    @Test
    fun unsupportedStatementFormatRequiresExplicitMapping() {
        try {
            TransactionEngine.readCsv("amount,date\n500,2026-10-05")
            fail()
        } catch (_: IllegalArgumentException) {}
        try {
            TransactionEngine.readCsv(
                "date,amount,direction,status,party,upi,reference,app,source\n\"unclosed"
            )
            fail()
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun formulaFieldsAreNeutralizedOnExport() {
        val r =
            TransactionRecord(
                amount = "500",
                direction = "DEBIT",
                status = "SUCCEEDED",
                party = "=HYPERLINK()",
                occurred = date,
                sources = "Manual",
                fingerprint = "",
            )
        assertTrue(TransactionEngine.exportCsv(listOf(r)).contains("'=HYPERLINK()"))
    }
}
