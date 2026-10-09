package com.paydar.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

object Reminders {
    const val CHANNEL = "subscriptions"

    fun enabled(context: Context) =
        context.getSharedPreferences("paydar", Context.MODE_PRIVATE).getBoolean("reminders", false)

    fun available(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance !=
                NotificationManager.IMPORTANCE_NONE
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Subscription reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        context
            .getSharedPreferences("paydar", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("reminders", enabled)
            .apply()
        if (!enabled) manager.cancel(100)
        schedule(context)
    }

    fun schedule(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        alarm.cancel(pending)
        if (!enabled(context) || !available(context)) return
        val time =
            LocalDate.now()
                .plusDays(1)
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Reminders.enabled(context)) return
        val store = LocalStore(context)
        val due =
            try {
                store.subscriptions().count {
                    it.active && !LocalDate.parse(it.nextDate).isAfter(LocalDate.now().plusDays(2))
                }
            } finally {
                store.close()
            }
        if (
            due > 0 &&
                Reminders.available(context) &&
                (Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED)
        ) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    Reminders.CHANNEL,
                    "Subscription reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
            val pending =
                PendingIntent.getActivity(
                    context,
                    2,
                    Intent(context, MainActivity::class.java).putExtra("screen", "subscriptions"),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            manager.notify(
                100,
                NotificationCompat.Builder(context, Reminders.CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Subscription review")
                    .setContentText(
                        "You have recurring payments to review. Open PayDar for details."
                    )
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build(),
            )
        }
        Reminders.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context)
    }
}
