package com.paydar.app

import android.content.Context
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LocalStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun createStore() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("paydar.db")
        store = LocalStore(context)
    }

    @After
    fun closeStore() {
        store.close()
        context.deleteDatabase("paydar.db")
    }

    @Test
    fun importedPaymentPreservesPausedStateAcrossRestart() {
        store.saveSubscription(null, "Coffee Club", "200", "2026-10-01")
        val id = store.subscriptions().single().id
        store.toggleSubscription(id, false)
        assertTrue(
            store.importMessage(
                ParsedSubscription("Coffee Club", BigDecimal("250"), "message-1"),
                LocalDate.of(2026, 10, 5),
            )
        )
        store.close()
        store = LocalStore(context)
        val subscription = store.subscriptions().single()
        assertFalse(subscription.active)
        assertEquals("250", subscription.amount)
        assertEquals("2026-11-05", subscription.nextDate)
    }

    @Test
    fun editingPreservesPausedStateAndNormalizesDate() {
        store.saveSubscription(null, "Coffee Club", "200", "2026-10-01")
        val id = store.subscriptions().single().id
        store.toggleSubscription(id, false)
        store.saveSubscription(id, "Coffee Club", "300", " 2026-11-02 ")
        assertFalse(store.subscriptions().single().active)
        assertEquals("2026-11-02", store.subscriptions().single().nextDate)
    }

    @Test
    fun importsAreIdempotentAndOlderPaymentsCannotOverwriteLatestAmount() {
        val latest = ParsedSubscription("Coffee Club", BigDecimal("300"), "new")
        assertTrue(store.importMessage(latest, LocalDate.of(2026, 10, 5)))
        assertFalse(store.importMessage(latest, LocalDate.of(2026, 10, 5)))
        assertTrue(
            store.importMessage(
                ParsedSubscription("Coffee Club", BigDecimal("200"), "old"),
                LocalDate.of(2026, 9, 5),
            )
        )
        assertEquals(1, store.subscriptions().size)
        assertEquals("300", store.subscriptions().single().amount)
    }

    @Test
    fun invalidRiskListPreservesExistingRecords() {
        store.importFeed(
            """{"source":"Local report","expires":"2026-12-01","records":[{"upi":"one@bank","reason":"Reported by account holder"}]}"""
        )
        try {
            store.importFeed(
                """{"source":"Replacement","expires":"2026-12-01","records":[{"upi":"two@bank","reason":"Reported"},{"upi":"invalid","reason":"Reported"}]}"""
            )
            fail("Invalid list must fail")
        } catch (_: IllegalArgumentException) {}
        assertEquals("one@bank", store.records().single().upi)
    }

    @Test
    fun duplicateNormalizedRiskIdsAreRejectedAtomically() {
        try {
            store.importFeed(
                """{"source":"Local report","expires":"2026-12-01","records":[{"upi":"One@bank","reason":"Reported"},{"upi":"one@bank","reason":"Reported"}]}"""
            )
            fail("Duplicate recipient must fail")
        } catch (_: IllegalArgumentException) {}
        assertTrue(store.records().isEmpty())
    }

    @Test
    fun deleteAllClearsEveryStoredCategoryAndDeduplication() {
        val parsed = ParsedSubscription("Coffee Club", BigDecimal("300"), "message")
        store.importMessage(parsed, LocalDate.of(2026, 10, 5))
        store.addCheck(Payment("one@bank"), Verdict("UNKNOWN", "Unknown", "No data"))
        store.saveReport("User draft")
        store.importFeed(
            """{"source":"Local report","expires":"2026-12-01","records":[{"upi":"one@bank","reason":"Reported"}]}"""
        )
        store.clearAll()
        assertTrue(store.subscriptions().isEmpty())
        assertTrue(store.history().isEmpty())
        assertTrue(store.reports().isEmpty())
        assertTrue(store.records().isEmpty())
        assertTrue(store.importMessage(parsed, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun invalidSubscriptionDoesNotWritePartialRecord() {
        for (date in listOf("2026-02-30", "not-a-date", "0001-01-01")) {
            try {
                store.saveSubscription(null, "Coffee Club", "300", date)
                fail("Invalid date must fail")
            } catch (_: IllegalArgumentException) {}
        }
        assertTrue(store.subscriptions().isEmpty())
    }
}
