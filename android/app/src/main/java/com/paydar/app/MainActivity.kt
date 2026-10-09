@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.paydar.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF080909)
private val Surface = Color(0xFF17191D)
private val Line = Color(0xFF30342C)
private val Yellow = Color(0xFFE5FF00)
private val Purple = Color(0xFFAF89FF)
private val Muted = Color(0xFFA0A798)
private val White = Color(0xFFF1F3EB)
private val Red = Color(0xFFFF898C)
private val Green = Color(0xFF8CF7B1)
private val heading = FontFamily(Font(R.font.oswald))
private val body = FontFamily(Font(R.font.manrope))

private fun currency(value: String) =
    NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        .format(value.toBigDecimalOrNull() ?: BigDecimal.ZERO)

private fun day(created: Long) =
    Instant.ofEpochMilli(created)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM · HH:mm"))

class MainActivity : ComponentActivity() {
    private lateinit var store: LocalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = LocalStore(this)
        setContent {
            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = Yellow,
                        onPrimary = Ink,
                        secondary = Purple,
                        background = Ink,
                        surface = Surface,
                        onSurface = White,
                        onBackground = White,
                        outline = Line,
                        error = Red,
                    ),
                typography =
                    Typography(
                        bodyLarge =
                            TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 25.sp),
                        bodyMedium =
                            TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 22.sp),
                        labelLarge =
                            TextStyle(
                                fontFamily = body,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                        titleLarge =
                            TextStyle(
                                fontFamily = body,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            ),
                    ),
            ) {
                val sharedReceipt =
                    if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain")
                        intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(5000)
                    else ""
                PayDar(
                    store,
                    if (sharedReceipt.isNotBlank()) "transactions"
                    else intent.getStringExtra("screen") ?: "home",
                    sharedReceipt,
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        store.close()
    }
}

@Composable
private fun PayDar(store: LocalStore, initialScreen: String, incomingReceipt: String = "") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var raw by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var smsConsent by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Verdict?>(null) }
    var payment by remember { mutableStateOf<Payment?>(null) }
    val history = remember(revision) { store.history() }
    val subscriptions = remember(revision) { store.subscriptions() }
    val records = remember(revision) { store.records() }
    val reports = remember(revision) { store.reports() }
    val scrollState = rememberScrollState()
    LaunchedEffect(screen) { scrollState.scrollTo(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                revision++
                Reminders.schedule(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    fun navigate(next: String) {
        screen = next
        status = ""
    }
    fun acceptQr(value: String) {
        raw = value
        screen = "scan"
        result = null
        payment = null
        status = "QR decoded. Review the recipient and compare the expected ID before checking."
    }
    val scanner =
        rememberLauncherForActivityResult(ScanContract()) { scan ->
            if (scan.contents != null) acceptQr(scan.contents)
            else
                status =
                    "Camera scan closed. You can try again, choose an image, or paste the recipient ID."
        }
    fun launchScanner() {
        scanner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan a UPI QR · no payment is initiated")
                .setBeepEnabled(false)
                .setOrientationLocked(true)
        )
    }
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchScanner()
            else
                status =
                    "Camera permission declined. Choose a QR image or paste the ID. You can change camera access in Android settings."
        }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val decoded =
                            withContext(Dispatchers.IO) {
                                val bounds =
                                    BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                context.contentResolver.openInputStream(uri).use {
                                    BitmapFactory.decodeStream(it, null, bounds)
                                }
                                require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                                    "Choose a valid image."
                                }
                                var sample = 1
                                while (
                                    bounds.outWidth / sample > 2000 ||
                                        bounds.outHeight / sample > 2000
                                ) sample *= 2
                                val bitmap =
                                    context.contentResolver.openInputStream(uri).use {
                                        BitmapFactory.decodeStream(
                                            it,
                                            null,
                                            BitmapFactory.Options().apply { inSampleSize = sample },
                                        )
                                    } ?: error("Could not open this image.")
                                try {
                                    val pixels = IntArray(bitmap.width * bitmap.height)
                                    bitmap.getPixels(
                                        pixels,
                                        0,
                                        bitmap.width,
                                        0,
                                        0,
                                        bitmap.width,
                                        bitmap.height,
                                    )
                                    MultiFormatReader()
                                        .decode(
                                            BinaryBitmap(
                                                HybridBinarizer(
                                                    RGBLuminanceSource(
                                                        bitmap.width,
                                                        bitmap.height,
                                                        pixels,
                                                    )
                                                )
                                            )
                                        )
                                        .text
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        acceptQr(decoded)
                    } catch (_: com.google.zxing.NotFoundException) {
                        status = "No readable QR found. Try a clearer image or paste the UPI ID."
                    } catch (e: Exception) {
                        status = e.message ?: "Could not read this QR image."
                    } finally {
                        busy = false
                    }
                }
        }
    fun importSms() {
        scope.launch {
            busy = true
            try {
                val counts =
                    withContext(Dispatchers.IO) {
                        var matched = 0
                        var added = 0
                        var inspected = 0
                        val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                        LocalStore(context.applicationContext).use { importStore ->
                            context.contentResolver
                                .query(
                                    Uri.parse("content://sms/inbox"),
                                    arrayOf("body", "date"),
                                    "date >= ?",
                                    arrayOf(cutoff.toString()),
                                    "date DESC",
                                )
                                ?.use { cursor ->
                                    while (cursor.moveToNext() && inspected < 1000) {
                                        inspected++
                                        val text = cursor.getString(0).orEmpty()
                                        val parsed = PayDarEngine.parseSms(text)
                                        if (parsed != null) {
                                            matched++
                                            val date =
                                                Instant.ofEpochMilli(cursor.getLong(1))
                                                    .atZone(ZoneId.systemDefault())
                                                    .toLocalDate()
                                            if (importStore.importMessage(parsed, date)) added++
                                        }
                                    }
                                } ?: error("SMS inbox is unavailable on this device.")
                        }
                        Triple(inspected, matched, added)
                    }
                revision++
                status =
                    "Reviewed ${counts.first} recent messages locally. ${counts.second} matched; ${counts.third} new message records added. Review merchants, amounts and estimated renewal dates."
                screen = "subscriptions"
            } catch (_: SecurityException) {
                status =
                    "SMS access was not granted. You can paste individual payment messages instead."
            } catch (_: Exception) {
                status = "SMS import could not complete. Try pasting individual payment messages."
            } finally {
                busy = false
            }
        }
    }
    val smsPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) importSms()
            else {
                status =
                    "SMS permission declined. Paste messages instead, or grant access later in Android settings."
            }
        }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Reminders.setEnabled(context, true)
                revision++
                status =
                    "Local review reminders are enabled. Delivery time may vary with Android battery settings."
            } else {
                Reminders.setEnabled(context, false)
                revision++
                status =
                    "Notifications are disabled. Subscription tracking still works. Enable notifications in Android settings to receive reminders."
            }
        }
    val feedPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val count =
                            withContext(Dispatchers.IO) {
                                val data =
                                    context.contentResolver
                                        .openInputStream(uri)
                                        ?.bufferedReader()
                                        ?.use { reader ->
                                            val out = StringBuilder()
                                            val buffer = CharArray(8192)
                                            while (true) {
                                                val n = reader.read(buffer)
                                                if (n < 0) break
                                                out.append(buffer, 0, n)
                                                require(out.length <= 1_000_000) {
                                                    "Import a JSON file smaller than 1 MB."
                                                }
                                            }
                                            out.toString()
                                        } ?: error("Could not open file.")
                                LocalStore(context.applicationContext).use { it.importFeed(data) }
                            }
                        revision++
                        status =
                            "Imported $count records. Source labels are user-supplied and unauthenticated."
                    } catch (e: Exception) {
                        status = "Import failed: ${e.message}. Your previous list was preserved."
                    } finally {
                        busy = false
                    }
                }
        }
    BackHandler(screen != "home") { navigate("home") }
    Scaffold(
        containerColor = Ink,
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (screen != "home")
                    IconButton(onClick = { navigate("home") }) {
                        Icon(Icons.Outlined.ArrowBack, "Back to radar", tint = White)
                    }
                Image(painterResource(R.drawable.paydar_logo), null, Modifier.size(33.dp))
                Spacer(Modifier.width(7.dp))
                Text("PAY", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Text("DAR", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Yellow)
                Spacer(Modifier.weight(1f))
                Text("BETA 0.3", color = Muted, fontSize = 11.sp)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                listOf(
                        Triple("home", "Radar", Icons.Outlined.Radar),
                        Triple("transactions", "Payments", Icons.Outlined.ReceiptLong),
                        Triple("history", "Checks", Icons.Outlined.History),
                        Triple("subscriptions", "Subs", Icons.Outlined.NotificationsNone),
                        Triple("settings", "Account", Icons.Outlined.AccountCircle),
                    )
                    .forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = screen == route,
                            onClick = { navigate(route) },
                            icon = { Icon(icon, label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor = Yellow,
                                    selectedTextColor = Yellow,
                                    indicatorColor = Color(0xFF242B10),
                                    unselectedIconColor = Muted,
                                    unselectedTextColor = Muted,
                                ),
                        )
                    }
            }
        },
    ) { padding ->
        if (screen == "transactions") {
            Box(Modifier.padding(padding).fillMaxSize()) {
                TransactionsScreen(store, incomingReceipt) { upi ->
                    raw = upi
                    result = null
                    payment = null
                    navigate("check")
                }
            }
        } else
            Column(
                Modifier.padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Yellow)
                if (status.isNotEmpty()) Info(status, Icons.Outlined.Info, action = { status = "" })
                when (screen) {
                    "home" -> {
                        Eyebrow("YOUR PAYMENT RADAR")
                        Text(
                            "CHECK BEFORE\nYOU PAY.",
                            fontFamily = heading,
                            fontSize = 43.sp,
                            lineHeight = 49.sp,
                            color = White,
                        )
                        Text("Before your money moves.", color = Muted, fontSize = 15.sp)
                        Card(
                            onClick = {
                                navigate("scan")
                                result = null
                                payment = null
                            },
                            colors = CardDefaults.cardColors(containerColor = Yellow),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Scan a UPI QR",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 24.sp,
                                        color = Ink,
                                    )
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        "See who you’re paying.",
                                        color = Color(0xFF455015),
                                        fontSize = 14.sp,
                                    )
                                }
                                Icon(
                                    Icons.Outlined.QrCodeScanner,
                                    null,
                                    tint = Ink,
                                    modifier = Modifier.size(38.dp),
                                )
                            }
                        }
                        ToolTile(
                            "Transactions",
                            "Payment receipts from your apps, in one local history",
                            Icons.Outlined.ReceiptLong,
                        ) {
                            navigate("transactions")
                        }
                        Eyebrow("YOUR TOOLKIT")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolTile(
                                "FraudCheck",
                                "Look up a UPI ID",
                                Icons.Outlined.Shield,
                                Modifier.weight(1f),
                            ) {
                                navigate("check")
                                result = null
                                payment = null
                            }
                            ToolTile(
                                "MuleGuard",
                                "Spot a risky request",
                                Icons.Outlined.WarningAmber,
                                Modifier.weight(1f),
                            ) {
                                navigate("mule")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToolTile(
                                "WrongPay",
                                "Prepare a report",
                                Icons.Outlined.Undo,
                                Modifier.weight(1f),
                            ) {
                                navigate("rescue")
                            }
                            ToolTile(
                                "SubWatcher",
                                "Review repeat charges",
                                Icons.Outlined.NotificationsNone,
                                Modifier.weight(1f),
                            ) {
                                navigate("subscriptions")
                            }
                        }
                        Info(
                            "Offline beta · no authoritative fraud feed. An unknown ID is never a safety verdict.",
                            Icons.Outlined.WifiOff,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Eyebrow("RECENT CHECKS")
                            TextButton(onClick = { navigate("history") }) {
                                Text("View all", color = Yellow)
                            }
                        }
                        if (history.isEmpty())
                            Empty(
                                "Your next check starts here.",
                                "Scan a QR or look up an ID. Your recent checks will stay on this phone.",
                                Icons.Outlined.History,
                            )
                        else history.take(3).forEach { HistoryRow(it) }
                    }
                    "scan",
                    "check" -> {
                        PageTitle(
                            if (screen == "scan") "ScanSafe" else "FraudCheck",
                            if (screen == "scan") "LOOK BEYOND THE SQUARE."
                            else "CHECK THE ID. READ THE SIGNALS.",
                        )
                        if (screen == "scan") {
                            Primary(
                                "Open QR camera",
                                Icons.Outlined.QrCodeScanner,
                                enabled = !busy,
                            ) {
                                if (
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA,
                                    ) == PackageManager.PERMISSION_GRANTED
                                )
                                    launchScanner()
                                else cameraPermission.launch(Manifest.permission.CAMERA)
                            }
                            OutlinedButton(
                                onClick = { imagePicker.launch("image/*") },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Image, null)
                                Spacer(Modifier.width(10.dp))
                                Text("Choose a QR image")
                            }
                        }
                        var expected by rememberSaveable { mutableStateOf("") }
                        Field(
                            if (screen == "scan") "UPI QR text or recipient ID"
                            else "Recipient UPI ID",
                            raw,
                            {
                                raw = it
                                result = null
                                payment = null
                            },
                            placeholder = "name@bank",
                        )
                        if (screen == "scan")
                            Field(
                                "Expected recipient UPI ID (optional)",
                                expected,
                                {
                                    expected = it
                                    result = null
                                    payment = null
                                },
                                placeholder = "From the person or business",
                            )
                        Primary(
                            "Run local check",
                            Icons.Outlined.Search,
                            enabled = raw.isNotBlank() && !busy,
                        ) {
                            try {
                                require(
                                    screen != "scan" ||
                                        expected.isBlank() ||
                                        PayDarEngine.validUpi(expected)
                                ) {
                                    "The expected recipient must be a valid UPI ID."
                                }
                                val p = PayDarEngine.parsePayment(raw)
                                val verdict =
                                    PayDarEngine.check(
                                        p,
                                        records,
                                        if (screen == "scan") expected else "",
                                    )
                                payment = p
                                result = verdict
                                store.addCheck(p, verdict)
                                revision++
                                status = ""
                            } catch (e: Exception) {
                                result = null
                                payment = null
                                status = e.message ?: "Unable to check this ID."
                            }
                        }
                        payment?.let { p ->
                            SurfaceCard {
                                Eyebrow("DECODED RECIPIENT")
                                Text(p.upi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                if (p.name.isNotBlank()) Text("QR name: ${p.name}", color = Muted)
                                p.amount?.let {
                                    Text(
                                        currency(it.toPlainString()),
                                        fontSize = 26.sp,
                                        color = Yellow,
                                    )
                                }
                                Text(
                                    "A QR name is supplied by its creator. Verify it in person and in your payment app.",
                                    fontSize = 12.sp,
                                    color = Muted,
                                )
                            }
                        }
                        result?.let { VerdictCard(it) }
                        result?.let {
                            SurfaceCard {
                                Eyebrow("OFFICIAL SUSPECT SEARCH")
                                Text(
                                    "I4C provides a public UPI-ID search based on reported complaints. Its database is incomplete and entries are not proven fraud. Complete the search and CAPTCHA on the official website.",
                                    fontSize = 13.sp,
                                    color = Muted,
                                )
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(
                                                    "https://cybercrime.gov.in/Webform/suspect_search_repository.aspx"
                                                ),
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.OpenInNew, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open I4C suspect search")
                                }
                                TextButton(
                                    onClick = {
                                        payment?.let { p ->
                                            context
                                                .getSystemService(ClipboardManager::class.java)
                                                .setPrimaryClip(
                                                    ClipData.newPlainText("Recipient UPI ID", p.upi)
                                                )
                                            status =
                                                "Recipient ID copied. Paste it only into a service you trust."
                                        }
                                    }
                                ) {
                                    Text("Copy recipient ID", color = Purple)
                                }
                                Text(
                                    "External service · internet required in your browser. PayDar does not submit the ID automatically.",
                                    fontSize = 11.sp,
                                    color = Muted,
                                )
                            }
                        }
                        Info(
                            "${records.size} imported records · local rules active. QR decoding does not detect pasted stickers. Tamper detection and on-device AI are not active in this beta.",
                            Icons.Outlined.Info,
                        )
                    }
                    "mule" -> {
                        PageTitle("MuleGuard", "SOME FAVOURS COST TOO MUCH.")
                        Text(
                            "Answer three questions about the request. Your answers stay here.",
                            color = Muted,
                        )
                        var answer0 by rememberSaveable { mutableStateOf("") }
                        var answer1 by rememberSaveable { mutableStateOf("") }
                        var answer2 by rememberSaveable { mutableStateOf("") }
                        var verdict by remember { mutableStateOf<Verdict?>(null) }
                        val answers = listOf(answer0, answer1, answer2)
                        PayDarEngine.muleQuestions.forEachIndexed { i, q ->
                            SurfaceCard {
                                Eyebrow("QUESTION 0${i+1} / 03")
                                Text(q, fontSize = 20.sp, lineHeight = 29.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    listOf("Yes", "No").forEach { option ->
                                        FilterChip(
                                            selected = answers[i] == option,
                                            onClick = {
                                                when (i) {
                                                    0 -> answer0 = option
                                                    1 -> answer1 = option
                                                    else -> answer2 = option
                                                }
                                                verdict = null
                                            },
                                            label = { Text(option) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        Primary(
                            "Review the signals",
                            Icons.Outlined.WarningAmber,
                            enabled = answers.all { it.isNotEmpty() },
                        ) {
                            verdict = PayDarEngine.mule(answers.map { it == "Yes" })
                        }
                        verdict?.let { VerdictCard(it) }
                        TextButton(
                            onClick = {
                                answer0 = ""
                                answer1 = ""
                                answer2 = ""
                                verdict = null
                            }
                        ) {
                            Text("Start again", color = Muted)
                        }
                    }
                    "rescue" -> {
                        PageTitle("WrongPay Rescue", "LESS PANIC. CLEAR NEXT STEPS.")
                        Info(
                            "Contact your payment app and bank promptly. This tool prepares a draft; it does not submit a dispute or reverse a payment.",
                            Icons.Outlined.Undo,
                        )
                        var recipient by rememberSaveable { mutableStateOf("") }
                        var amount by rememberSaveable { mutableStateOf("") }
                        var utr by rememberSaveable { mutableStateOf("") }
                        var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
                        var notes by rememberSaveable { mutableStateOf("") }
                        var draft by rememberSaveable { mutableStateOf("") }
                        var saved by remember { mutableStateOf(false) }
                        if (draft.isEmpty()) {
                            Eyebrow("01 / ADD THE TRANSACTION")
                            Field("Recipient UPI ID", recipient, { recipient = it }, "name@bank")
                            Field(
                                "Amount (INR)",
                                amount,
                                { amount = it },
                                "180.00",
                                KeyboardType.Decimal,
                            )
                            Field(
                                "Transaction reference / UTR",
                                utr,
                                { utr = it },
                                "From your payment receipt",
                            )
                            Field("Transaction date", date, { date = it }, "YYYY-MM-DD")
                            Field(
                                "What happened?",
                                notes,
                                { notes = it.take(2000) },
                                "Do not include your PIN, OTP or passwords",
                                multiline = true,
                            )
                            Primary("Prepare recovery draft", Icons.Outlined.Description) {
                                try {
                                    draft = PayDarEngine.report(recipient, amount, utr, date, notes)
                                    saved = false
                                    status = ""
                                } catch (e: Exception) {
                                    status = e.message ?: "Check the transaction details."
                                }
                            }
                        } else {
                            Eyebrow("02 / REVIEW YOUR DRAFT")
                            SurfaceCard { Text(draft, fontSize = 13.sp, lineHeight = 22.sp) }
                            Primary(
                                if (saved) "Saved on this phone" else "Save draft locally",
                                Icons.Outlined.Save,
                                enabled = !saved,
                            ) {
                                store.saveReport(draft)
                                revision++
                                saved = true
                                status = "Saved locally. No complaint has been submitted."
                            }
                            OutlinedButton(
                                onClick = { share(context, draft) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Share, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Share reviewed draft")
                            }
                            TextButton(
                                onClick = {
                                    draft = ""
                                    saved = false
                                }
                            ) {
                                Text("Edit transaction details", color = Purple)
                            }
                            SupportLinks(context)
                        }
                        if (reports.isNotEmpty()) {
                            Eyebrow("SAVED DRAFTS")
                            reports.forEach { report ->
                                SurfaceCard {
                                    Text(day(report.created), color = Muted, fontSize = 12.sp)
                                    Text(
                                        report.body
                                            .lineSequence()
                                            .drop(1)
                                            .take(3)
                                            .joinToString("\n"),
                                        fontSize = 13.sp,
                                    )
                                    TextButton(
                                        onClick = {
                                            draft = report.body
                                            saved = true
                                        }
                                    ) {
                                        Text("Open draft", color = Yellow)
                                    }
                                }
                            }
                        }
                    }
                    "subscriptions" -> {
                        PageTitle("SubWatcher", "REVIEW YOUR RECURRING PAYMENTS")
                        val total =
                            subscriptions
                                .filter { it.active }
                                .fold(BigDecimal.ZERO) { sum, s -> sum + s.amount.toBigDecimal() }
                        SurfaceCard {
                            Eyebrow("ESTIMATED MONTHLY TOTAL")
                            Text(
                                currency(total.toPlainString()),
                                fontFamily = heading,
                                fontSize = 46.sp,
                                color = Yellow,
                            )
                            Text(
                                "${subscriptions.count{it.active}} tracked · assumes monthly recurrence",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        var sms by rememberSaveable { mutableStateOf("") }
                        var editSub by remember { mutableStateOf<Subscription?>(null) }
                        var editor by remember { mutableStateOf(false) }
                        var deleting by remember { mutableStateOf<Subscription?>(null) }
                        Field(
                            "Paste a payment message",
                            sms,
                            { sms = it.take(5000) },
                            "Paste only the message you want to analyse",
                            multiline = true,
                        )
                        Primary(
                            "Read this message locally",
                            Icons.Outlined.Sms,
                            enabled = sms.isNotBlank() && !busy,
                        ) {
                            val parsed = PayDarEngine.parseSms(sms)
                            if (parsed == null)
                                status =
                                    "No supported payment found. OTPs, credits and failed payments are ignored. You can add or edit a subscription manually."
                            else {
                                val added = store.importMessage(parsed, LocalDate.now())
                                revision++
                                sms = ""
                                status =
                                    if (added)
                                        "Added ${parsed.merchant}. Confirm the amount and estimated next date; a payment is not proof of a subscription."
                                    else "This exact message was already imported."
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { smsConsent = true },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Import SMS")
                            }
                            OutlinedButton(
                                onClick = {
                                    editSub = null
                                    editor = true
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Add manually")
                            }
                        }
                        Info(
                            "Dates from message imports are estimates. Confirm each recurring charge. Pausing tracking does not cancel the provider’s subscription.",
                            Icons.Outlined.Info,
                        )
                        if (subscriptions.isEmpty())
                            Empty(
                                "No surprise charges on this screen.",
                                "Paste a payment message or add a subscription to start your list.",
                                Icons.Outlined.NotificationsNone,
                            )
                        subscriptions.forEach { s ->
                            SurfaceCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(42.dp)
                                            .background(
                                                Color(0xFF302645),
                                                RoundedCornerShape(10.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            s.merchant.take(1).uppercase(),
                                            color = Purple,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 22.sp,
                                        )
                                    }
                                    Spacer(Modifier.width(13.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            s.merchant,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text("Next: ${s.nextDate}", fontSize = 12.sp, color = Muted)
                                    }
                                    Text(currency(s.amount), fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Switch(
                                        checked = s.active,
                                        onCheckedChange = {
                                            store.toggleSubscription(s.id, it)
                                            revision++
                                        },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (s.active) "Tracking" else "Tracking paused",
                                        fontSize = 12.sp,
                                        color = Muted,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            editSub = s
                                            editor = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            "Edit ${s.merchant}",
                                            tint = Purple,
                                        )
                                    }
                                    IconButton(onClick = { deleting = s }) {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            "Delete ${s.merchant}",
                                            tint = Muted,
                                        )
                                    }
                                }
                            }
                        }
                        if (editor)
                            SubscriptionEditor(
                                editSub,
                                onDismiss = { editor = false },
                                onSave = { name, amount, date ->
                                    try {
                                        store.saveSubscription(editSub?.id, name, amount, date)
                                        revision++
                                        editor = false
                                        status = "Saved on this phone."
                                        null
                                    } catch (e: Exception) {
                                        if (e is android.database.sqlite.SQLiteConstraintException)
                                            "That merchant already exists. Edit its current entry."
                                        else e.message ?: "Check all fields."
                                    }
                                },
                            )
                        deleting?.let { s ->
                            AlertDialog(
                                onDismissRequest = { deleting = null },
                                title = { Text("Delete ${s.merchant}?") },
                                text = {
                                    Text(
                                        "This removes the local tracking record. It does not cancel a subscription with the provider."
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            store.deleteSubscription(s.id)
                                            revision++
                                            deleting = null
                                        }
                                    ) {
                                        Text("Delete")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { deleting = null }) { Text("Keep") }
                                },
                            )
                        }
                    }
                    "history" -> {
                        PageTitle("Your checks", "ON YOUR PHONE. NOWHERE ELSE.")
                        if (history.isEmpty()) {
                            Empty(
                                "Nothing checked yet.",
                                "Your QR and ID checks appear here after you run them.",
                                Icons.Outlined.History,
                            )
                        } else {
                            Text("Most recent 100 checks", color = Muted, fontSize = 12.sp)
                            history.forEach { entry ->
                                SurfaceCard {
                                    HistoryRow(entry)
                                    TextButton(
                                        onClick = {
                                            raw = entry.upi
                                            result = null
                                            payment = null
                                            navigate("check")
                                        }
                                    ) {
                                        Text("Check again", color = Yellow)
                                    }
                                }
                            }
                        }
                    }
                    "settings" -> {
                        PageTitle("Your account.\nYour control.", "ACCOUNT & PRIVACY")
                        AccountCard()
                        Info(
                            "Optional Google sign-in uses an internet connection. No advertising SDK. Android backup is disabled. Your app data is stored in this device’s private app storage.",
                            Icons.Outlined.Lock,
                        )
                        SurfaceCard {
                            Eyebrow("INTEGRATION STATUS")
                            IntegrationRow("QR decoding", "On-device", Green)
                            IntegrationRow("Unified transaction history", "On-device", Green)
                            OutlinedButton(onClick = { navigate("transactions") }) {
                                Text("Manage transaction imports and capture")
                            }
                            IntegrationRow("Local rules", "Active", Green)
                            IntegrationRow(
                                "Imported ID records",
                                "${records.size} · unverified",
                                Yellow,
                            )
                            IntegrationRow("Authoritative fraud feed", "Not connected", Muted)
                            IntegrationRow("QR sticker-tamper model", "Not available", Muted)
                            IntegrationRow("On-device LLM", "Not installed", Muted)
                            Text(
                                "The beta uses transparent rules. It does not claim AI inference or an NPU latency benchmark.",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                        SurfaceCard {
                            Eyebrow("LOCAL REVIEW REMINDERS")
                            Text(
                                "A discreet reminder around 9 AM for charges due within two days. Android may delay delivery.",
                                fontSize = 14.sp,
                                color = Muted,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable reminders", Modifier.weight(1f))
                                Switch(
                                    checked = Reminders.enabled(context),
                                    onCheckedChange = { on ->
                                        if (
                                            on &&
                                                Build.VERSION.SDK_INT >= 33 &&
                                                ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.POST_NOTIFICATIONS,
                                                ) != PackageManager.PERMISSION_GRANTED
                                        )
                                            notificationPermission.launch(
                                                Manifest.permission.POST_NOTIFICATIONS
                                            )
                                        else {
                                            Reminders.setEnabled(context, on)
                                            revision++
                                        }
                                    },
                                )
                            }
                        }
                        SurfaceCard {
                            Eyebrow("IMPORT A LOCAL RISK LIST")
                            Text(
                                "Choose a JSON file you trust. This replaces your current list. Source labels are not proof of authenticity; matching entries remain unverified reports.",
                                fontSize = 14.sp,
                                color = Muted,
                            )
                            OutlinedButton(
                                onClick = { feedPicker.launch("application/json") },
                                enabled = !busy,
                            ) {
                                Text("Choose JSON file")
                            }
                            Text(
                                "Format: source, expires (YYYY-MM-DD), records [{upi, reason}]. Up to 5,000 records / 1 MB.",
                                fontSize = 12.sp,
                                color = Muted,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Manage Android permissions")
                        }
                        SurfaceCard {
                            Eyebrow("YOU CONTROL WHAT LEAVES")
                            Text(
                                "Google sign-in sends account information to Google and Firebase. Sharing a report or opening an external support page hands control to another app. PayDar does not send your payment data to a server. Raw SMS messages are processed in memory and not saved; only extracted records and duplicate fingerprints are stored.",
                                fontSize = 14.sp,
                                color = Muted,
                            )
                        }
                        TextButton(onClick = { deleteDialog = true }, enabled = !busy) {
                            Icon(Icons.Outlined.DeleteOutline, null, tint = Red)
                            Spacer(Modifier.width(10.dp))
                            Text("Delete local PayDar data", color = Red)
                        }
                        Text(
                            "PayDar 0.3.0-beta · Team Hydra\nTesting build, not a production fraud protection service.",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
            }
    }
    if (smsConsent)
        AlertDialog(
            onDismissRequest = { smsConsent = false },
            title = { Text("Read payment SMS locally?") },
            text = {
                Text(
                    "With your permission, PayDar will review up to 1,000 inbox messages from the last 90 days on this phone. Only extracted merchant/amount/date records are saved. OTPs and unsupported messages are ignored. You can paste messages instead."
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
            dismissButton = {
                TextButton(onClick = { smsConsent = false }) { Text("Use paste instead") }
            },
        )
    if (deleteDialog)
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("Delete all local data?") },
            text = {
                Text(
                    "This permanently removes check history, transaction receipts, subscription records, imported lists and recovery drafts from PayDar. Reminders and payment notification capture will be disabled. Files you previously shared to other apps are not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        synchronized(PaymentCapture) {
                            PaymentCapture.reset(context)
                            store.clearAll()
                        }
                        Reminders.setEnabled(context, false)
                        context
                            .getSystemService(android.app.NotificationManager::class.java)
                            .cancelAll()
                        revision++
                        raw = ""
                        result = null
                        payment = null
                        screen = "home"
                        status = "All local PayDar records were deleted."
                        deleteDialog = false
                    }
                ) {
                    Text("Delete all", color = Red)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Keep data") } },
        )
}

@Composable
private fun PageTitle(title: String, kicker: String) {
    Eyebrow(kicker)
    Text(title, fontFamily = heading, fontSize = 40.sp, lineHeight = 46.sp, color = White)
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        color = Muted,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
            content = content,
        )
    }
}

@Composable
private fun ToolTile(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: () -> Unit,
) {
    Card(
        onClick = action,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Column(
            Modifier.padding(17.dp).heightIn(min = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = Purple, modifier = Modifier.size(26.dp))
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 11.sp, lineHeight = 16.sp, color = Muted)
        }
    }
}

@Composable
private fun Primary(label: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
    Button(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 17.dp, vertical = 14.dp),
    ) {
        Icon(icon, null, Modifier.size(21.dp))
        Spacer(Modifier.width(11.dp))
        Text(label)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    multiline: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 6 else 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrect = false),
        shape = RoundedCornerShape(10.dp),
        textStyle = TextStyle(fontSize = 15.sp, fontFamily = body),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yellow,
                unfocusedBorderColor = Line,
                focusedLabelColor = Yellow,
                cursorColor = Yellow,
            ),
    )
}

@Composable
private fun Info(text: String, icon: ImageVector, action: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .background(Color(0xFF1B2016), RoundedCornerShape(12.dp))
            .padding(15.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, null, tint = Color(0xFFC3D1A3), modifier = Modifier.size(19.dp))
        Text(
            text,
            color = Color(0xFFB7C0AB),
            fontSize = 12.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f),
        )
        if (action != null)
            IconButton(onClick = action, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Outlined.Close, "Dismiss message", Modifier.size(17.dp), tint = Muted)
            }
    }
}

@Composable
private fun Empty(title: String, description: String, icon: ImageVector) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 25.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, null, tint = Purple, modifier = Modifier.size(35.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun VerdictCard(verdict: Verdict) {
    val color =
        if (verdict.level == "HIGH") Red else if (verdict.level == "DEMO") Purple else Yellow
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .09f)),
        border = BorderStroke(1.dp, color.copy(alpha = .5f)),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Icon(
                if (verdict.level == "HIGH") Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                null,
                tint = color,
                modifier = Modifier.size(34.dp),
            )
            Text(
                verdict.title,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(verdict.detail, fontSize = 14.sp, lineHeight = 23.sp, color = White)
            Text(
                "NO PAYMENT HAS BEEN INITIATED",
                fontSize = 10.sp,
                color = Muted,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun HistoryRow(entry: CheckEntry) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Shield,
            null,
            tint = if (entry.level == "HIGH") Red else Yellow,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.upi, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(entry.title, fontSize = 12.sp, color = Muted)
        }
        Text(day(entry.created), fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun IntegrationRow(label: String, state: String, color: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(state, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun SubscriptionEditor(
    existing: Subscription?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> String?,
) {
    var name by remember { mutableStateOf(existing?.merchant ?: "") }
    var amount by remember { mutableStateOf(existing?.amount ?: "") }
    var date by remember {
        mutableStateOf(existing?.nextDate ?: LocalDate.now().plusMonths(1).toString())
    }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Track a subscription" else "Edit subscription") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Field("Merchant", name, { name = it.take(80) })
                Field(
                    "Monthly amount (INR)",
                    amount,
                    { amount = it },
                    keyboard = KeyboardType.Decimal,
                )
                Field("Next charge date", date, { date = it }, "YYYY-MM-DD")
                error?.let { Text(it, color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { error = onSave(name, amount, date) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun share(context: Context, body: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Transaction support draft")
                putExtra(Intent.EXTRA_TEXT, body)
            },
            "Share your reviewed recovery draft",
        )
    )
}

@Composable
private fun SupportLinks(context: Context) {
    Eyebrow("03 / CONTACT OFFICIAL SUPPORT")
    Text(
        "First open the transaction in your payment app and contact your bank using its official support channel. For a further complaint, use the official NPCI portal. Never call numbers supplied by an unknown sender.",
        fontSize = 14.sp,
        color = Muted,
    )
    OutlinedButton(
        onClick = {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.npci.org.in/register-a-complaint"),
                )
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.OpenInNew, null)
        Spacer(Modifier.width(8.dp))
        Text("Open official NPCI guidance")
    }
    Text(
        "Opens your browser. No transaction details are added to the link.",
        fontSize = 11.sp,
        color = Muted,
    )
}

