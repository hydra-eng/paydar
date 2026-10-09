package com.paydar.app

import android.content.Context
import java.time.Instant
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
class TransactionStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private val date = Instant.parse("2026-10-05T12:00:00Z").toEpochMilli()

    private fun receipt(
        ref: String = "123456789012",
        source: String = "SMS",
        app: String = "Not identified",
        status: String = "SUCCEEDED",
    ) =
        TransactionEngine.validate(
            TransactionRecord(
                amount = "500",
                direction = "DEBIT",
                status = status,
                party = "Alice",
                upi = "alice@bank",
                reference = ref,
                app = app,
                occurred = date,
                sources = source,
                fingerprint = "",
            )
        )

    @Before
    fun before() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("paydar.db")
        PaymentCapture.reset(context)
        store = LocalStore(context)
    }

    @After
    fun after() {
        store.close()
        context.deleteDatabase("paydar.db")
        PaymentCapture.reset(context)
    }

    @Test
    fun referenceDedupEnrichesAppAndSurvivesRestart() {
        assertEquals(1, store.importTransactions(listOf(receipt())).first)
        assertEquals(
            1,
            store
                .importTransactions(
                    listOf(receipt(source = "Notification: PhonePe", app = "PhonePe"))
                )
                .second,
        )
        store.close()
        store = LocalStore(context)
        val r = store.transactions().single()
        assertEquals("PhonePe", r.app)
        assertEquals("500.00", r.amount)
        assertEquals(setOf("SMS", "Notification: PhonePe"), r.sources.split('\n').toSet())
    }

    @Test
    fun pendingCannotOverwriteACompletedReceipt() {
        store.importTransactions(listOf(receipt(status = "PENDING")))
        store.importTransactions(listOf(receipt()))
        store.importTransactions(listOf(receipt(status = "PENDING")))
        assertEquals("SUCCEEDED", store.transactions().single().status)
    }

    @Test
    fun separateReferencesRemainSeparateAndDeletesPersist() {
        store.importTransactions(listOf(receipt(), receipt("123456789013")))
        assertEquals(2, store.transactions().size)
        store.deleteTransaction(store.transactions().first().id)
        store.close()
        store = LocalStore(context)
        assertEquals(1, store.transactions().size)
    }

    @Test
    fun invalidBatchDoesNotPartiallyWrite() {
        try {
            store.importTransactions(listOf(receipt(), receipt("bad")))
            fail()
        } catch (_: IllegalArgumentException) {}
        assertTrue(store.transactions().isEmpty())
    }

    @Test
    fun correctionsAndClearAllIncludeTheLedger() {
        store.importTransactions(listOf(receipt()))
        val row = store.transactions().single()
        store.saveTransaction(row.copy(party = "Alice Cafe", amount = "650"))
        assertEquals("650.00", store.transactions().single().amount)
        store.clearAll()
        assertTrue(store.transactions().isEmpty())
    }

    @Test
    fun schemaUpgradePreservesTheExistingCheckHistory() {
        store.addCheck(Payment("alice@bank"), Verdict("UNKNOWN", "Unknown", "No data"))
        store.writableDatabase.execSQL("DROP TABLE transactions")
        store.writableDatabase.version = 1
        store.close()
        store = LocalStore(context)
        assertEquals(1, store.history().size)
        assertTrue(store.transactions().isEmpty())
        store.importTransactions(listOf(receipt()))
        assertEquals(1, store.transactions().size)
    }

    @Test
    fun captureStartsDisabledAndSelectionIsExplicit() {
        assertFalse(PaymentCapture.enabled(context))
        assertTrue(PaymentCapture.selected(context).isEmpty())
        PaymentCapture.select(context, "com.phonepe.app", true)
        assertEquals(setOf("com.phonepe.app"), PaymentCapture.selected(context))
        assertFalse(PaymentCapture.enabled(context))
        PaymentCapture.setEnabled(context, true)
        PaymentCapture.reset(context)
        assertFalse(PaymentCapture.enabled(context))
        assertTrue(PaymentCapture.selected(context).isEmpty())
    }
}
