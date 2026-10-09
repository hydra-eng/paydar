package com.paydar.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

object TransactionUpdates {
    private val counter = MutableStateFlow(0L)
    val version = counter.asStateFlow()

    fun changed() {
        counter.update { it + 1 }
    }
}

data class Subscription(
    val id: Long,
    val merchant: String,
    val amount: String,
    val nextDate: String,
    val active: Boolean,
)

data class CheckEntry(
    val id: Long,
    val upi: String,
    val level: String,
    val title: String,
    val created: Long,
)

data class RecoveryDraft(val id: Long, val body: String, val created: Long)

class LocalStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "paydar.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE checks(id INTEGER PRIMARY KEY, upi TEXT NOT NULL, level TEXT NOT NULL, title TEXT NOT NULL, created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE subscriptions(id INTEGER PRIMARY KEY, merchant TEXT NOT NULL COLLATE NOCASE UNIQUE, amount TEXT NOT NULL, next_date TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL("CREATE TABLE messages(fingerprint TEXT PRIMARY KEY)")
        db.execSQL(
            "CREATE TABLE reports(id INTEGER PRIMARY KEY, body TEXT NOT NULL, created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE fraud(upi TEXT PRIMARY KEY, reason TEXT NOT NULL, source TEXT NOT NULL, expires TEXT NOT NULL)"
        )
        createTransactions(db)
    }

    private fun createTransactions(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS transactions(id INTEGER PRIMARY KEY, amount TEXT NOT NULL, direction TEXT NOT NULL, status TEXT NOT NULL, party TEXT NOT NULL, upi TEXT NOT NULL, reference TEXT NOT NULL, app TEXT NOT NULL, occurred INTEGER NOT NULL, sources TEXT NOT NULL, fingerprint TEXT NOT NULL UNIQUE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS transactions_date ON transactions(occurred DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) createTransactions(db)
    }

    fun transactions(): List<TransactionRecord> = buildList {
        readableDatabase
            .rawQuery(
                "SELECT id,amount,direction,status,party,upi,reference,app,occurred,sources,fingerprint FROM transactions ORDER BY occurred DESC,id DESC",
                null,
            )
            .use { c ->
                while (c.moveToNext()) add(
                    TransactionRecord(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getString(4),
                        c.getString(5),
                        c.getString(6),
                        c.getString(7),
                        c.getLong(8),
                        c.getString(9),
                        c.getString(10),
                    )
                )
            }
    }

    /** Reference-based matches enrich existing receipts, never add twice to the totals. */
    fun importTransactions(records: List<TransactionRecord>): Pair<Int, Int> {
        val validated = records.map(TransactionEngine::validate)
        val db = writableDatabase
        var added = 0
        var merged = 0
        db.beginTransaction()
        try {
            for (r in validated) {
                val existing =
                    db.rawQuery(
                            "SELECT id,status,party,upi,app,sources FROM transactions WHERE fingerprint=?",
                            arrayOf(r.fingerprint),
                        )
                        .use { c ->
                            if (c.moveToFirst())
                                listOf(
                                    c.getLong(0).toString(),
                                    c.getString(1),
                                    c.getString(2),
                                    c.getString(3),
                                    c.getString(4),
                                    c.getString(5),
                                )
                            else null
                        }
                val values = transactionValues(r)
                if (existing == null) {
                    db.insertOrThrow("transactions", null, values)
                    added++
                } else {
                    // Late pending alerts must not replace a terminal status. Otherwise keep the
                    // latest source status.
                    values.put(
                        "status",
                        if (r.status == "PENDING" && existing[1] != "PENDING") existing[1]
                        else r.status,
                    )
                    if (r.party in listOf("Sender not provided", "Recipient not provided"))
                        values.put("party", existing[2])
                    if (r.upi.isBlank()) values.put("upi", existing[3])
                    if (r.app == "Not identified") values.put("app", existing[4])
                    values.put(
                        "sources",
                        (existing[5].split('\n') + r.sources.split('\n'))
                            .distinct()
                            .joinToString("\n"),
                    )
                    db.update("transactions", values, "id=?", arrayOf(existing[0]))
                    merged++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        TransactionUpdates.changed()
        return added to merged
    }

    private fun transactionValues(r: TransactionRecord) =
        ContentValues().apply {
            put("amount", r.amount)
            put("direction", r.direction)
            put("status", r.status)
            put("party", r.party)
            put("upi", r.upi)
            put("reference", r.reference)
            put("app", r.app)
            put("occurred", r.occurred)
            put("sources", r.sources)
            put("fingerprint", r.fingerprint)
        }

    fun saveTransaction(record: TransactionRecord) {
        val r = TransactionEngine.validate(record)
        if (r.id == 0L) importTransactions(listOf(r))
        else {
            val values = transactionValues(r)
            require(
                writableDatabase.update("transactions", values, "id=?", arrayOf(r.id.toString())) ==
                    1
            ) {
                "This transaction was deleted. Add it again."
            }
            TransactionUpdates.changed()
        }
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete("transactions", "id=?", arrayOf(id.toString()))
        TransactionUpdates.changed()
    }

    fun addCheck(payment: Payment, verdict: Verdict) {
        writableDatabase.insertOrThrow(
            "checks",
            null,
            ContentValues().apply {
                put("upi", payment.upi)
                put("level", verdict.level)
                put("title", verdict.title)
                put("created", System.currentTimeMillis())
            },
        )
    }

    fun history(): List<CheckEntry> = buildList {
        readableDatabase
            .rawQuery(
                "SELECT id,upi,level,title,created FROM checks ORDER BY id DESC LIMIT 100",
                null,
            )
            .use { c ->
                while (c.moveToNext()) add(
                    CheckEntry(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getLong(4),
                    )
                )
            }
    }

    fun subscriptions(): List<Subscription> = buildList {
        readableDatabase
            .rawQuery(
                "SELECT id,merchant,amount,next_date,active FROM subscriptions ORDER BY active DESC,next_date",
                null,
            )
            .use { c ->
                while (c.moveToNext()) add(
                    Subscription(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        c.getString(3),
                        c.getInt(4) == 1,
                    )
                )
            }
    }

    fun importMessage(parsed: ParsedSubscription, date: LocalDate): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (
                db.insertWithOnConflict(
                    "messages",
                    null,
                    ContentValues().apply { put("fingerprint", parsed.fingerprint) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                ) == -1L
            ) {
                db.setTransactionSuccessful()
                return false
            }
            val existing =
                db.rawQuery(
                        "SELECT id,next_date FROM subscriptions WHERE merchant=?",
                        arrayOf(parsed.merchant),
                    )
                    .use { c -> if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null }
            val proposed = date.plusMonths(1).toString()
            val values =
                ContentValues().apply {
                    put("merchant", parsed.merchant)
                    put("amount", parsed.amount.toPlainString())
                    put("next_date", proposed)
                }
            if (existing == null) db.insertOrThrow("subscriptions", null, values)
            else if (proposed >= existing.second)
                db.update("subscriptions", values, "id=?", arrayOf(existing.first.toString()))
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun saveSubscription(id: Long?, merchant: String, amount: String, next: String) {
        require(merchant.trim().length in 2..80) { "Enter a merchant name (2–80 characters)." }
        require(merchant.none { it.isISOControl() }) {
            "Enter a merchant name without line breaks or control characters."
        }
        val value = PayDarEngine.money(amount)
        val date =
            runCatching { LocalDate.parse(next.trim()) }
                .getOrElse {
                    throw IllegalArgumentException(
                        "Use a valid next charge date in YYYY-MM-DD format."
                    )
                }
        require(date.year in 2000..9999) { "Enter a next charge date from the year 2000 onward." }
        val values =
            ContentValues().apply {
                put("merchant", merchant.trim())
                put("amount", value.toPlainString())
                put("next_date", date.toString())
            }
        if (id == null) writableDatabase.insertOrThrow("subscriptions", null, values)
        else
            require(
                writableDatabase.update("subscriptions", values, "id=?", arrayOf(id.toString())) ==
                    1
            ) {
                "This subscription no longer exists. Add it again."
            }
    }

    fun toggleSubscription(id: Long, active: Boolean) {
        writableDatabase.update(
            "subscriptions",
            ContentValues().apply { put("active", if (active) 1 else 0) },
            "id=?",
            arrayOf(id.toString()),
        )
    }

    fun deleteSubscription(id: Long) {
        writableDatabase.delete("subscriptions", "id=?", arrayOf(id.toString()))
    }

    fun saveReport(body: String) {
        writableDatabase.insertOrThrow(
            "reports",
            null,
            ContentValues().apply {
                put("body", body)
                put("created", System.currentTimeMillis())
            },
        )
    }

    fun reports(): List<RecoveryDraft> = buildList {
        readableDatabase
            .rawQuery("SELECT id,body,created FROM reports ORDER BY id DESC LIMIT 30", null)
            .use { c ->
                while (c.moveToNext()) add(
                    RecoveryDraft(c.getLong(0), c.getString(1), c.getLong(2))
                )
            }
    }

    fun records(): List<FraudRecord> = buildList {
        readableDatabase.rawQuery("SELECT upi,reason,source,expires FROM fraud", null).use { c ->
            while (c.moveToNext()) add(
                FraudRecord(
                    c.getString(0),
                    c.getString(1),
                    c.getString(2),
                    LocalDate.parse(c.getString(3)),
                )
            )
        }
    }

    fun importFeed(json: String): Int {
        require(json.length <= 1_000_000) { "Import a list smaller than 1 MB." }
        val root = JSONObject(json)
        val source = root.getString("source").trim()
        require(source.length in 2..120) { "The source label is required." }
        val expires = LocalDate.parse(root.getString("expires"))
        val entries = root.getJSONArray("records")
        require(entries.length() <= 5000) { "Maximum 5,000 records per list." }
        val validated =
            (0 until entries.length()).map { i ->
                val r = entries.getJSONObject(i)
                val upi = PayDarEngine.normalize(r.getString("upi"))
                require(PayDarEngine.validUpi(upi)) { "Record ${i+1} has an invalid UPI ID." }
                val reason = r.getString("reason").trim()
                require(reason.length in 3..300) {
                    "Each record needs a reason (3–300 characters)."
                }
                FraudRecord(upi, reason, source, expires)
            }
        require(validated.map { it.upi }.toSet().size == validated.size) {
            "The list contains duplicate UPI IDs."
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("fraud", null, null)
            validated.forEach { r ->
                db.insertOrThrow(
                    "fraud",
                    null,
                    ContentValues().apply {
                        put("upi", r.upi)
                        put("reason", r.reason)
                        put("source", r.source)
                        put("expires", r.expires.toString())
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return validated.size
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (table in
                listOf(
                    "checks",
                    "subscriptions",
                    "messages",
                    "reports",
                    "fraud",
                    "transactions",
                )) db.delete(table, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        db.execSQL("VACUUM")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.rawQuery("PRAGMA secure_delete=ON", null).use { it.moveToFirst() }
    }
}
