package com.paydar.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerInsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)
    private fun row(date: String, amount: String = "100", status: String = "SUCCEEDED", direction: String = "DEBIT") = TransactionRecord(amount=amount, direction=direction, status=status, party="Merchant", occurred=LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(), sources="Manual", fingerprint=date)
    @Test fun calendarTotalsExcludeFailedPendingAndFuture() {
        val s = LedgerInsights.summarize(listOf(row("2026-10-01"), row("2026-09-30", "75"), row("2026-10-10", "20", direction="CREDIT"), row("2026-10-09", status="FAILED"), row("2026-10-08", status="PENDING"), row("2026-10-11")), today, zone)
        assertEquals(100, s.sent.toInt()); assertEquals(20, s.received.toInt()); assertEquals(75, s.previousSent.toInt())
        assertEquals(7, s.days.size); assertEquals(today, s.days.last().date)
    }
    @Test fun repeatedChargesRequireDifferentDaysAndSameAmount() {
        val s = LedgerInsights.summarize(listOf(row("2026-10-10"),row("2026-10-10"),row("2026-10-09", "200")), today, zone)
        assertTrue(s.repeated.isEmpty())
        assertEquals(1, LedgerInsights.summarize(listOf(row("2026-10-10"),row("2026-09-10", "100.00")), today, zone).repeated.size)
    }
    @Test fun emptyLedgerHasZeroTotalsAndSevenDays() {
        val s = LedgerInsights.summarize(emptyList(), today, zone)
        assertEquals(0, s.sent.signum()); assertTrue(s.apps.isEmpty()); assertEquals(7, s.days.size)
    }
}
