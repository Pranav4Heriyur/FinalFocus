package com.pranav.focus.lock

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import com.pranav.focus.data.Store

data class AppEntry(val pkg: String, val label: String)

object Allowed {
    /** System surfaces and phone/call UIs that must never be blocked. */
    private val ALWAYS = setOf(
        "com.android.systemui", "android",
        "com.android.server.telecom", "com.android.phone", "com.android.incallui",
        "com.samsung.android.incallui", "com.samsung.android.dialer",
        "com.google.android.dialer", "com.android.dialer",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
    )

    /** Suggested defaults: Claude and Gemini, when installed. */
    val DEFAULT_PACKAGES = setOf("com.anthropic.claude", "com.google.android.apps.bard")

    fun dialerPackage(ctx: Context): String? =
        ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

    private fun imePackages(ctx: Context): Set<String> =
        ctx.getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()

    /** Surfaces that are never blocked by any rule: this app, system UI, phone/dialer, keyboards. */
    fun isCore(ctx: Context, pkg: String): Boolean =
        pkg == ctx.packageName || pkg in ALWAYS || pkg == dialerPackage(ctx) || pkg in imePackages(ctx)

    fun isAllowed(ctx: Context, store: Store, pkg: String): Boolean =
        isCore(ctx, pkg) || pkg in store.allowedApps

    /** Every app with a launcher icon, sorted by name. */
    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .map { AppEntry(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_INSTALLED != 0
    } catch (e: Exception) {
        false
    }
}
