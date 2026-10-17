package com.pranav.focus.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.pranav.focus.MainActivity
import android.view.accessibility.AccessibilityNodeInfo
import com.pranav.focus.data.ExemptKind
import com.pranav.focus.data.Observed
import com.pranav.focus.data.Store
import com.pranav.focus.data.StudyExempt
import com.pranav.focus.data.Verdict

/**
 * Bounces blocked apps back to Focus: the allow-list during a strict session, "blocked" lists,
 * and time-limited apps whose unlocked minutes ran out. Reads only the package name of the front window.
 */
class FocusAccessibilityService : AccessibilityService() {
    private var lastRedirect = 0L
    private var current: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recheck = object : Runnable {
        override fun run() {
            val pkg = current ?: return
            val store = Store.get(this@FocusAccessibilityService)
            if (!decide(store, pkg) && Enforcer.isTimed(store, pkg)) handler.postDelayed(this, 15_000)
        }
    }

    // ---- YouTube study detection (opt-in via Study Exemptions; reads only the YouTube window; stores durations only) ----
    private var lastSampleAt = 0L
    private var lastExempt = false
    private val studyTick = object : Runnable {
        override fun run() {
            if (current != YT) { lastSampleAt = 0L; return }
            sampleStudy(); handler.postDelayed(this, 10_000)
        }
    }

    private fun sampleStudy() {
        val store = Store.get(this)
        if (!store.studyDetect || current != YT) return
        val now = SystemClock.elapsedRealtime()
        val exemptNow = StudyExempt.exempts(StudyExempt.verdict(observe(rootInActiveWindow, store), store.studyChannels), store.studyNameMatch)
        // Conservative: time counts as study only when BOTH this and the previous sample were exempt; gaps are capped.
        if (lastSampleAt > 0 && lastExempt && exemptNow) store.addStudyMs(YT, (now - lastSampleAt).coerceIn(0, 20_000))
        lastSampleAt = now; lastExempt = exemptNow
    }

    /** Bounded walk of the YouTube window. Text is compared locally against approved ids/names and discarded. */
    private fun observe(root: AccessibilityNodeInfo?, store: Store): Observed? {
        root ?: return null
        val names = store.studyChannels.filter { it.enabled && it.kind == ExemptKind.CHANNEL && it.name.isNotBlank() }.map { it.name.trim() }
        var channelId: String? = null; var videoId: String? = null; var name: String? = null
        var budget = 400
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || budget-- <= 0 || depth > 30) return
            for (t in listOf(n.text, n.contentDescription)) {
                val x = t?.toString()?.take(300) ?: continue
                channelId = channelId ?: StudyExempt.findChannelId(x)
                if (videoId == null) Regex("[?&]v=([0-9A-Za-z_-]{11})").find(x)?.let { videoId = it.groupValues[1] }
                if (name == null) names.firstOrNull { it.equals(x.trim(), ignoreCase = true) }?.let { name = it }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return if (channelId == null && videoId == null && name == null) null else Observed(channelId, videoId, name)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = SystemClock.elapsedRealtime()
            if (event.packageName?.toString() == YT && current == YT && now - lastSampleAt > 3_000) sampleStudy()
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != YT) { handler.removeCallbacks(studyTick); lastSampleAt = 0L }
        if (Allowed.isCore(this, pkg)) return
        val store = Store.get(this)
        current = pkg
        handler.removeCallbacks(recheck)
        if (pkg == YT && store.studyDetect) { handler.removeCallbacks(studyTick); handler.postDelayed(studyTick, 10_000) }
        if (store.sessionActive && store.remainingMs() <= 0) {
            SessionController.complete(this)
            return
        }
        if (!decide(store, pkg) && Enforcer.isTimed(store, pkg)) handler.postDelayed(recheck, 15_000)
    }

    /** True when the app was blocked and we redirected. */
    private fun decide(store: Store, pkg: String): Boolean {
        if (!store.restrictionsEnabled) return false
        if (store.sessionActive && store.strictLock) {
            if (Allowed.isAllowed(this, store, pkg)) return false
            redirect(null)
            return true
        }
        if (Enforcer.shouldBlock(this, store, pkg)) {
            redirect(pkg)
            return true
        }
        return false
    }

    private fun redirect(blocked: String?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 300) return
        lastRedirect = now
        val i = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        if (blocked != null) i.putExtra("blocked_pkg", blocked)
        startActivity(i)
    }

    companion object { const val YT = "com.google.android.youtube" }

    override fun onInterrupt() { handler.removeCallbacks(recheck) }
}
