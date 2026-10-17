package com.pranav.focus.lock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.pranav.focus.data.Store

/** Observable UI state shared by the activity and the services. */
data class SessionSummary(val minutes: Int, val subject: String, val earnedMin: Int, val xp: Int, val streak: Int)

object AppState {
    var active by mutableStateOf(false)
    var tick by mutableStateOf(0)
    var summary by mutableStateOf<SessionSummary?>(null)
    var blockedPkg by mutableStateOf<String?>(null)
    var locked by mutableStateOf(false)
    var openMirror by mutableStateOf(false)
    var dataTick by mutableStateOf(0)

    var a11yOn by mutableStateOf(false)
    var overlayOn by mutableStateOf(false)
    var notifOn by mutableStateOf(false)
    var callRoleOn by mutableStateOf(false)
    var batteryOk by mutableStateOf(false)

    val coreReady: Boolean get() = a11yOn && overlayOn

    fun refresh(ctx: Context) {
        tick++
        val s = Store.get(ctx)
        active = s.sessionActive && s.remainingMs() > 0
        a11yOn = isAccessibilityEnabled(ctx)
        overlayOn = Settings.canDrawOverlays(ctx)
        notifOn = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        callRoleOn = ctx.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        batteryOk = ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val me = ComponentName(ctx, FocusAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

object SessionController {
    fun start(ctx: Context, minutes: Int, name: String, subject: String, taskId: String = "") {
        val s = Store.get(ctx)
        s.putStr("s_task", taskId)
        s.lastMinutes = minutes
        s.lastName = name
        s.lastSubject = subject
        s.startSession(minutes, name, subject)
        ContextCompat.startForegroundService(ctx, Intent(ctx, FocusService::class.java))
        AppState.refresh(ctx)
    }

    /** Timer ran out: reward and unlock. */
    @Synchronized fun complete(ctx: Context) {
        val s = Store.get(ctx)
        if (!s.sessionActive) return
        val minutes = s.sessionMinutes
        val subject = s.sessionSubject
        val earned = s.recordCompleted(minutes)
        s.endSession()
        val taskId = s.str("s_task")
        s.putStr("last_session_task", taskId)
        s.putStr("last_session_id", "sess_${s.long("s_end_wall")}")
        if (taskId.isNotEmpty()) s.putInt("tfocus_$taskId", s.int("tfocus_$taskId") + minutes)
        s.putLong("last_session_end", System.currentTimeMillis()); s.putStr("last_session_subject", subject)
        Earning.evaluate(ctx)
        if (com.pranav.focus.net.Notion.configured(ctx)) com.pranav.focus.net.Notion.pushAsync(ctx, "Focus · $minutes min · $subject", "Completed a locked $minutes-minute focus session ($subject). Streak: ${s.streak} days. XP: ${s.xp}.", report = false)
        ctx.stopService(Intent(ctx, FocusService::class.java))
        AppState.summary = SessionSummary(minutes, subject, earned, com.pranav.focus.data.Rewards.xpForSession(minutes), s.streak)
        AppState.refresh(ctx)
    }

    /** Quit through the friction flow: penalty applies. */
    @Synchronized fun quit(ctx: Context) {
        val s = Store.get(ctx)
        if (!s.sessionActive) return
        s.recordQuit()
        s.endSession()
        ctx.stopService(Intent(ctx, FocusService::class.java))
        AppState.refresh(ctx)
    }
}
