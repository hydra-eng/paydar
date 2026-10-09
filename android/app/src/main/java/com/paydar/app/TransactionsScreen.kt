@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paydar.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LedgerMuted = Color(0xFFA0A798)
private val LedgerGreen = Color(0xFF8CF7B1)
private val LedgerYellow = Color(0xFFE5FF00)
private val LedgerRed = Color(0xFFFF898C)

private fun ledgerMoney(value: BigDecimal) =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)

private fun ledgerDate(value: Long) =
    Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm"))

private suspend fun <T> ledgerWrite(block: () -> T): T {
    val job = currentCoroutineContext()
    return synchronized(PaymentCapture) {
        job.ensureActive()
        block()
    }
}

@Composable
fun TransactionsScreen(store: LocalStore, incomingReceipt: String = "", onCheck: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes by TransactionUpdates.version.collectAsState()
    var revision by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<TransactionRecord>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var direction by rememberSaveable { mutableStateOf("ALL") }
    var app by rememberSaveable { mutableStateOf("All apps") }
    var monthOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TransactionRecord?>(null) }
    var newEntry by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(incomingReceipt.isNotBlank()) }
    var smsConsent by remember { mutableStateOf(false) }
    var captureDialog by remember { mutableStateOf(false) }
    var deleteEntry by remember { mutableStateOf<TransactionRecord?>(null) }
    var exportData by remember { mutableStateOf("") }
    var captureEnabled by remember { mutableStateOf(PaymentCapture.enabled(context)) }
    var access by remember { mutableStateOf(false) }
    fun refreshAccess() {
        access =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        captureEnabled = PaymentCapture.enabled(context)
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val lifecycle = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAccess()
                revision++
            }
        }
        owner.lifecycle.addObserver(lifecycle)
        refreshAccess()
        onDispose { owner.lifecycle.removeObserver(lifecycle) }
    }
    LaunchedEffect(revision, changes) {
        records = withContext(Dispatchers.IO) { store.transactions() }
    }
    fun work(task: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                message = withContext(Dispatchers.IO) { task() }
                revision++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = e.message ?: "Could not complete this operation."
            } finally {
                busy = false
            }
        }
    }
    fun importSms() = work {
        var inspected = 0
        var skipped = 0
        val found = mutableListOf<TransactionRecord>()
        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        context.contentResolver
            .query(
                Uri.parse("content://sms/inbox"),
                arrayOf("body", "date"),
                "date >= ?",
                arrayOf(cutoff.toString()),
                "date DESC",
            )
            ?.use { c ->
                while (c.moveToNext() && inspected < 1000) {
                    inspected++
                    val text = c.getString(0).orEmpty()
                    val receipt =
                        if (
                            Regex("\\bUPI\\b|[A-Za-z0-9._-]+@[A-Za-z]", RegexOption.IGNORE_CASE)
                                .containsMatchIn(text)
                        )
                            TransactionEngine.parseReceipt(text, c.getLong(1), "SMS")
                        else null
                    if (receipt != null) found.add(receipt) else skipped++
                }
            }
            ?: error(
                "This device has no readable SMS inbox. Paste a receipt or import CSV instead."
            )
        val result = ledgerWrite { store.importTransactions(found) }
        "Reviewed $inspected messages from the last 90 days (maximum 1,000). ${result.first} added, ${result.second} matched existing references; $skipped unsupported or non-UPI messages ignored."
    }
    val smsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) importSms()
            else message = "SMS permission declined. Paste a receipt or import a CSV instead."
        }
    val csvPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                work {
                    val text =
                        context.contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)?.use {
                            reader ->
                            val out = StringBuilder()
                            val buffer = CharArray(8192)
                            while (true) {
                                val n = reader.read(buffer)
                                if (n < 0) break
                                out.append(buffer, 0, n)
                                require(out.length <= 2_000_000) { "Use a CSV smaller than 2 MB." }
                            }
                            out.toString()
                        } ?: error("Could not open the selected file.")
                    val parsed = TransactionEngine.readCsv(text)
                    val result = ledgerWrite { store.importTransactions(parsed.records) }
                    "${result.first} added, ${result.second} matched existing references, ${parsed.skipped} invalid rows skipped."
                }
        }
    val exportPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri
            ->
            if (uri != null)
                work {
                    context.contentResolver
                        .openOutputStream(uri, "wt")
                        ?.writer(Charsets.UTF_8)
                        ?.use { it.write(exportData) } ?: error("Could not save this file.")
                    "CSV saved to the location you selected. It contains payment details; share it only with someone you trust."
                }
        }
    val monthStart =
        LocalDate.now()
            .withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    val filtered =
        remember(records, search, direction, app, monthOnly) {
            records.filter { r ->
                (direction == "ALL" || r.direction == direction) &&
                    (app == "All apps" || r.app == app) &&
                    (!monthOnly || r.occurred >= monthStart) &&
                    (search.isBlank() ||
                        listOf(r.party, r.upi, r.reference, r.app).any {
                            it.contains(search, true)
                        })
            }
        }
    val sent =
        filtered
            .filter { it.direction == "DEBIT" && it.status == "SUCCEEDED" }
            .fold(BigDecimal.ZERO) { total, r -> total + r.amount.toBigDecimal() }
    val received =
        filtered
            .filter { it.direction == "CREDIT" && it.status == "SUCCEEDED" }
            .fold(BigDecimal.ZERO) { total, r -> total + r.amount.toBigDecimal() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Text("TRANSACTIONS", fontSize = 11.sp, color = LedgerMuted, letterSpacing = 1.5.sp)
            Text(
                "Your payments.\nOne history.",
                fontSize = 32.sp,
                lineHeight = 37.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Text(
                "A local record of imported receipts. Coverage depends on the messages and notifications available on this phone. Receipt status is not bank verification.",
                color = LedgerMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerCard(Modifier.weight(1f)) {
                    Text("SENT", color = LedgerMuted, fontSize = 10.sp)
                    Text(
                        ledgerMoney(sent),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = LedgerYellow,
                    )
                }
                LedgerCard(Modifier.weight(1f)) {
                    Text("RECEIVED", color = LedgerMuted, fontSize = 10.sp)
                    Text(
                        ledgerMoney(received),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = LedgerGreen,
                    )
                }
            }
            Text(
                "Successful receipts in the current filters. Pending and failed receipts are excluded.",
                color = LedgerMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            LedgerCard {
                Text("Collect new receipts", fontWeight = FontWeight.Bold)
                Text(
                    if (captureEnabled && access) "Payment notification capture is enabled."
                    else "Payment notification capture is off or Android access is missing.",
                    color = LedgerMuted,
                    fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = { captureDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NotificationsNone, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Manage payment apps")
                }
                Text(
                    "Captures supported new notifications after you opt in. It cannot retrieve past in-app histories.",
                    color = LedgerMuted,
                    fontSize = 11.sp,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { smsConsent = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import SMS")
                }
                OutlinedButton(
                    onClick = { pasteDialog = true },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Paste receipt")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = {
                        csvPicker.launch(
                            arrayOf("text/*", "application/csv", "application/vnd.ms-excel")
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Import CSV")
                }
                TextButton(
                    onClick = {
                        exportData = TransactionEngine.exportCsv(filtered)
                        exportPicker.launch("PayDar-transactions-${LocalDate.now()}.csv")
                    },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (filtered.isEmpty()) "CSV template" else "Export CSV")
                }
            }
            Button(
                onClick = { newEntry = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add a transaction")
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (message.isNotEmpty())
            item {
                LedgerCard {
                    Text(message, fontSize = 12.sp, lineHeight = 19.sp)
                    TextButton(onClick = { message = "" }) { Text("Dismiss") }
                }
            }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it.take(160) },
                label = { Text("Search recipient, app, UPI ID or reference") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("ALL" to "All", "DEBIT" to "Sent", "CREDIT" to "Received").forEach {
                    (key, label) ->
                    FilterChip(
                        selected = direction == key,
                        onClick = { direction = key },
                        label = { Text(label) },
                    )
                }
                FilterChip(
                    selected = monthOnly,
                    onClick = { monthOnly = !monthOnly },
                    label = { Text("This month") },
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (listOf("All apps") + records.map { it.app }.distinct().sorted()).forEach { label ->
                    FilterChip(
                        selected = app == label,
                        onClick = { app = label },
                        label = { Text(label) },
                    )
                }
            }
            Text(
                "${filtered.size} of ${records.size} receipts",
                fontSize = 11.sp,
                color = LedgerMuted,
            )
        }
        if (filtered.isEmpty())
            item {
                LedgerCard {
                    Icon(Icons.Outlined.ReceiptLong, null, tint = Color(0xFFAF89FF))
                    Text(
                        if (records.isEmpty()) "No transactions yet"
                        else "No matching transactions",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (records.isEmpty())
                            "Import your UPI SMS, paste a receipt, add a record, or enable capture for the payment apps you use. No sample transactions are added."
                        else "Adjust the search or filters to see your records.",
                        fontSize = 13.sp,
                        color = LedgerMuted,
                    )
                }
            }
        items(filtered, key = { it.id }) { r ->
            LedgerCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(r.party, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        (if (r.direction == "CREDIT") "+" else "−") +
                            ledgerMoney(r.amount.toBigDecimal()),
                        color = if (r.direction == "CREDIT") LedgerGreen else LedgerYellow,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("${r.app} · ${ledgerDate(r.occurred)}", fontSize = 11.sp, color = LedgerMuted)
                if (r.upi.isNotBlank()) Text(r.upi, fontSize = 12.sp)
                Text(
                    "${r.status.lowercase().replaceFirstChar { it.uppercase() }} receipt · ${if(r.reference.isBlank())"Reference not provided" else r.reference}",
                    fontSize = 11.sp,
                    color = if (r.status == "FAILED") LedgerRed else LedgerMuted,
                )
                Text(
                    "Source: ${r.sources.replace('\n',';')}",
                    fontSize = 10.sp,
                    color = LedgerMuted,
                )
                Row {
                    TextButton(onClick = { selected = r }, enabled = !busy) { Text("Edit") }
                    if (r.upi.isNotBlank())
                        TextButton(onClick = { onCheck(r.upi) }) { Text("Review ID") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { deleteEntry = r }, enabled = !busy) {
                        Icon(Icons.Outlined.DeleteOutline, "Delete transaction", tint = LedgerRed)
                    }
                }
            }
        }
    }
    if (newEntry || selected != null)
        TransactionEditor(
            selected,
            onDismiss = {
                selected = null
                newEntry = false
            },
        ) { entry ->
            work {
                ledgerWrite { store.saveTransaction(entry) }
                "Transaction saved."
            }
            selected = null
            newEntry = false
        }
    if (pasteDialog)
        ReceiptPaste(initialText = incomingReceipt, onDismiss = { pasteDialog = false }) { entry ->
            work {
                val result = ledgerWrite { store.importTransactions(listOf(entry)) }
                if (result.first == 1) "Receipt added."
                else "Matched an existing reference and updated its sources."
            }
            pasteDialog = false
        }
    if (smsConsent)
        AlertDialog(
            onDismissRequest = { smsConsent = false },
            title = { Text("Import UPI payment SMS?") },
            text = {
                Text(
                    "PayDar will inspect up to 1,000 inbox messages from the last 90 days locally. Only extracted transaction fields and duplicate fingerprints are stored. OTPs, requests, unrelated and unsupported messages are ignored. You can paste receipts or import CSV instead."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        smsConsent = false
                        if (
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_SMS,
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                            importSms()
                        else smsPermission.launch(Manifest.permission.READ_SMS)
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = { TextButton(onClick = { smsConsent = false }) { Text("Cancel") } },
        )
    if (deleteEntry != null)
        AlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = { Text("Delete this transaction?") },
            text = {
                Text(
                    "This removes only the local PayDar record. It does not reverse a payment or remove the original receipt."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = deleteEntry!!.id
                        deleteEntry = null
                        work {
                            ledgerWrite { store.deleteTransaction(id) }
                            "Transaction deleted."
                        }
                    }
                ) {
                    Text("Delete", color = LedgerRed)
                }
            },
            dismissButton = { TextButton(onClick = { deleteEntry = null }) { Text("Keep") } },
        )
    if (captureDialog)
        CaptureSettings(
            onDismiss = {
                captureDialog = false
                refreshAccess()
            }
        )
}

@Composable
private fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF17191D)),
        border = BorderStroke(1.dp, Color(0xFF30342C)),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            content = content,
        )
    }
}

@Composable
private fun TransactionEditor(
    existing: TransactionRecord?,
    onDismiss: () -> Unit,
    onSave: (TransactionRecord) -> Unit,
) {
    var party by remember { mutableStateOf(existing?.party.orEmpty()) }
    var amount by remember { mutableStateOf(existing?.amount.orEmpty()) }
    var upi by remember { mutableStateOf(existing?.upi.orEmpty()) }
    var reference by remember { mutableStateOf(existing?.reference.orEmpty()) }
    var app by remember {
        mutableStateOf(existing?.app?.takeUnless { it == "Not identified" }.orEmpty())
    }
    var date by remember {
        mutableStateOf(
            existing?.let {
                Instant.ofEpochMilli(it.occurred)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString()
            } ?: LocalDate.now().toString()
        )
    }
    var direction by remember { mutableStateOf(existing?.direction ?: "DEBIT") }
    var status by remember { mutableStateOf(existing?.status ?: "SUCCEEDED") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add transaction" else "Correct transaction") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LedgerField("Recipient or sender", party) { party = it.take(120) }
                LedgerField("Amount (INR)", amount) { amount = it.take(24) }
                LedgerField("Date (YYYY-MM-DD)", date) { date = it.take(10) }
                LedgerField("Payment app (optional)", app) { app = it.take(80) }
                LedgerField("UPI ID (optional)", upi) { upi = it.take(192) }
                LedgerField("Reference / UTR (optional)", reference) { reference = it.take(80) }
                Row {
                    listOf("DEBIT" to "Sent", "CREDIT" to "Received").forEach { (key, label) ->
                        FilterChip(direction == key, { direction = key }, { Text(label) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("SUCCEEDED", "PENDING", "FAILED").forEach { label ->
                        FilterChip(
                            status == label,
                            { status = label },
                            { Text(label.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                if (error.isNotEmpty()) Text(error, color = LedgerRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val day = LocalDate.parse(date)
                        val occurred =
                            if (
                                existing != null &&
                                    day ==
                                        Instant.ofEpochMilli(existing.occurred)
                                            .atZone(ZoneId.systemDefault())
                                            .toLocalDate()
                            )
                                existing.occurred
                            else day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val record =
                            TransactionEngine.validate(
                                TransactionRecord(
                                    id = existing?.id ?: 0,
                                    amount = amount,
                                    direction = direction,
                                    status = status,
                                    party = party,
                                    upi = upi,
                                    reference = reference,
                                    app = app,
                                    occurred = occurred,
                                    sources = (existing?.sources?.plus("\n") ?: "") + "Manual",
                                    fingerprint =
                                        if (
                                            existing?.reference == reference &&
                                                existing?.direction == direction &&
                                                existing?.amount == amount
                                        )
                                            existing?.fingerprint.orEmpty()
                                        else "",
                                )
                            )
                        onSave(record)
                    } catch (e: Exception) {
                        error = e.message ?: "Check the transaction fields."
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LedgerField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReceiptPaste(
    initialText: String = "",
    onDismiss: () -> Unit,
    onSave: (TransactionRecord) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var app by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a payment receipt") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Use the actual receipt date. Paste transaction text, without PINs or OTPs. Raw text is not saved.",
                    fontSize = 12.sp,
                    color = LedgerMuted,
                )
                OutlinedTextField(
                    text,
                    { text = it.take(5000) },
                    label = { Text("Receipt text") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                LedgerField("Date (YYYY-MM-DD)", date) { date = it.take(10) }
                LedgerField("Payment app (optional)", app) { app = it.take(80) }
                if (error.isNotEmpty()) Text(error, color = LedgerRed, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        val occurred =
                            LocalDate.parse(date)
                                .atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        val record =
                            TransactionEngine.parseReceipt(
                                text,
                                occurred,
                                "Pasted receipt",
                                app.ifBlank { "Not identified" },
                            )
                                ?: error(
                                    "Could not identify a supported payment receipt. Add the transaction manually instead."
                                )
                        onSave(record)
                    } catch (e: Exception) {
                        error = e.message ?: "Check the receipt date."
                    }
                }
            ) {
                Text("Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CaptureSettings(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(PaymentCapture.selected(context)) }
    var enabled by remember { mutableStateOf(PaymentCapture.enabled(context)) }
    var custom by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payment notification access") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Android notification access can expose notification content from all apps. PayDar processes only the payment apps you select below, ignores OTPs and requests, and stores extracted receipt fields locally. No payment data is uploaded. Access is optional and can be revoked in Android settings.",
                    fontSize = 12.sp,
                    color = LedgerMuted,
                )
                (TransactionEngine.paymentApps.keys + chosen).distinct().forEach { pkg ->
                    Row {
                        Checkbox(
                            checked = pkg in chosen,
                            onCheckedChange = { checked ->
                                PaymentCapture.select(context, pkg, checked)
                                chosen = PaymentCapture.selected(context)
                            },
                        )
                        Text(
                            PaymentCapture.label(pkg),
                            modifier = Modifier.padding(top = 13.dp),
                            fontSize = 13.sp,
                        )
                    }
                }
                LedgerField("Another payment app's package name", custom) { custom = it.take(160) }
                TextButton(
                    onClick = {
                        try {
                            PaymentCapture.select(context, custom.trim(), true)
                            chosen = PaymentCapture.selected(context)
                            custom = ""
                            error = ""
                        } catch (e: Exception) {
                            error = e.message.orEmpty()
                        }
                    }
                ) {
                    Text("Add payment app")
                }
                if (error.isNotEmpty()) Text(error, color = LedgerRed, fontSize = 12.sp)
                Row {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { checked ->
                            synchronized(PaymentCapture) {
                                PaymentCapture.setEnabled(context, checked)
                            }
                            enabled = checked
                        },
                    )
                    Text(
                        "Capture selected apps",
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                    )
                }
                Text(
                    "New supported payment notifications only. Missing details or disabled notifications can leave gaps. SMS often cannot identify which UPI app made the payment.",
                    fontSize = 11.sp,
                    color = LedgerMuted,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Android notification access")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
