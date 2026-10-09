package com.paydar.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.Executors

object PaymentCapture {
    private fun prefs(context: Context) =
        context.getSharedPreferences("transaction_capture", Context.MODE_PRIVATE)

    fun selected(context: Context): Set<String> =
        prefs(context).getStringSet("apps", emptySet())?.toSet().orEmpty()

    fun select(context: Context, packageName: String, enabled: Boolean) {
        require(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+").matches(packageName)) {
            "Enter a valid Android package name."
        }
        val next =
            selected(context).toMutableSet().apply {
                if (enabled) add(packageName) else remove(packageName)
            }
        prefs(context).edit().putStringSet("apps", next).apply()
    }

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean("enabled", enabled).commit()

    fun reset(context: Context) = prefs(context).edit().clear().commit()

    fun label(packageName: String) = TransactionEngine.paymentApps[packageName] ?: packageName
}

class PaymentNotifications : NotificationListenerService() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!PaymentCapture.enabled(this) || sbn.packageName !in PaymentCapture.selected(this))
            return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val detail =
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()
                .orEmpty()
        val lines =
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        val text =
            listOf(title, detail, lines).filter { it.isNotBlank() }.distinct().joinToString("\n")
        val record =
            TransactionEngine.parseReceipt(
                text,
                sbn.postTime,
                "Notification: ${PaymentCapture.label(sbn.packageName)}",
                PaymentCapture.label(sbn.packageName),
                sbn.key,
            ) ?: return
        worker.execute {
            // Re-check opt-in inside the queue: disabling capture cancels queued writes.
            synchronized(PaymentCapture) {
                if (
                    PaymentCapture.enabled(this) && sbn.packageName in PaymentCapture.selected(this)
                ) {
                    runCatching {
                        LocalStore(applicationContext).use { it.importTransactions(listOf(record)) }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
