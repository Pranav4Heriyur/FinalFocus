package com.pranav.focus.lock

import com.pranav.focus.data.AppClock
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pranav.focus.MainActivity
import com.pranav.focus.R
import com.pranav.focus.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 9 PM nudge to do the Accountability Mirror review (inexact alarm, no special permission). */
object Reminder {
    private const val CH = "review"

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        var t = AppClock.today().atTime(21, 0)
        if (t.isBefore(LocalDateTime.now())) t = t.plusDays(1)
        val pi = PendingIntent.getBroadcast(ctx, 21, Intent(ctx, ReviewReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.set(AlarmManager.RTC, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    fun notifyNow(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH, "Daily review", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 22, Intent(ctx, MainActivity::class.java).putExtra("open", "mirror"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(21, NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Face the mirror").setContentText("Did you do what you said you would? Review your day.")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}

class ReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Store.get(context).reviewSubmitted()) Reminder.notifyNow(context)
        Reminder.schedule(context)
    }
}
