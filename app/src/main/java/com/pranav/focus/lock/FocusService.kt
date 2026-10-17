package com.pranav.focus.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.pranav.focus.MainActivity
import com.pranav.focus.R
import com.pranav.focus.data.Store

/** Keeps the process alive during a session and ends it when the timer runs out. */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastShownMin = -1L

    private val ticker = object : Runnable {
        override fun run() {
            val s = Store.get(this@FocusService)
            if (!s.sessionActive) {
                stopSelf()
                return
            }
            val rem = s.remainingMs()
            if (rem <= 0) {
                SessionController.complete(this@FocusService)
                return
            }
            val min = (rem + 59_999) / 60_000
            if (min != lastShownMin) {
                lastShownMin = min
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build("$min min left"))
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Focus session", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIF_ID, build("Locked in"))
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun build(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Focus session")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "focus_session"
        const val NOTIF_ID = 1
    }
}

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Store.get(context)
        Reminder.schedule(context)
        if (s.sessionActive && s.remainingMs() > 0) {
            androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, FocusService::class.java))
        }
    }
}
