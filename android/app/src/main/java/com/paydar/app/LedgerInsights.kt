package com.paydar.app

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SpendingDay(val date: LocalDate, val sent: BigDecimal)
data class LedgerSummary(
    val sent: BigDecimal, val received: BigDecimal, val previousSent: BigDecimal,
    val days: List<SpendingDay>, val apps: List<Pair<String, BigDecimal>>,
    val repeated: List<Pair<String, List<TransactionRecord>>>,
)

object LedgerInsights {
    fun summarize(records: List<TransactionRecord>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): LedgerSummary {
        fun date(r: TransactionRecord) = Instant.ofEpochMilli(r.occurred).atZone(zone).toLocalDate()
        fun total(rows: List<TransactionRecord>) = rows.fold(BigDecimal.ZERO) { sum, r -> sum + r.amount.toBigDecimal() }
        val valid = records.filter { it.status == "SUCCEEDED" && !date(it).isAfter(today) }
        val start = today.withDayOfMonth(1)
        val month = valid.filter { !date(it).isBefore(start) }
        val debits = month.filter { it.direction == "DEBIT" }
        val previous = valid.filter { it.direction == "DEBIT" && date(it) >= start.minusMonths(1) && date(it) < start }
        val repeated = valid.filter { it.direction == "DEBIT" && date(it) >= today.minusDays(90) }
            .groupBy { (it.upi.ifBlank { it.party }).trim().lowercase(java.util.Locale.ROOT) + "|" + it.amount.toBigDecimal().stripTrailingZeros() }
            .values.filter { rows -> rows.map { date(it) }.distinct().size >= 2 }
            .map { it.first().party to it.sortedByDescending { row -> row.occurred } }
            .sortedByDescending { it.second.first().occurred }
        return LedgerSummary(total(debits), total(month.filter { it.direction == "CREDIT" }), total(previous),
            (6L downTo 0L).map { offset -> val d = today.minusDays(offset); SpendingDay(d, total(valid.filter { it.direction == "DEBIT" && date(it) == d })) },
            debits.groupBy { it.app }.map { it.key to total(it.value) }.sortedByDescending { it.second }, repeated)
    }
}
