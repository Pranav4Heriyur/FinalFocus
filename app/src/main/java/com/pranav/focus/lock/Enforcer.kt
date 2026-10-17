package com.pranav.focus.lock

import android.content.Context
import com.pranav.focus.data.AllowanceSplit
import com.pranav.focus.data.Allowance
import com.pranav.focus.data.Store
import com.pranav.focus.data.TimeBank
import com.pranav.focus.ui.UsageHelper

/** Decides whether an app must be bounced back to Focus right now. */
object Enforcer {
    /** Today's categorised usage for a rule app. Base, study-exempt and bank-funded minutes are kept apart. */
    fun split(ctx: Context, store: Store, pkg: String): AllowanceSplit? {
        val rule = store.appRules.firstOrNull { it.pkg == pkg } ?: return null
        val total = UsageHelper.usedTodayMs(ctx)[pkg] ?: 0L
        val base = if (rule.locked) rule.baseMin else rule.limitMin
        val sp = Allowance.split(total, store.studyMs(pkg), base, (store.day().alloc[pkg] ?: 0) + store.ledger.topUpGrantedOn(store.today(), pkg), rule.limitMin)
        // snapshot for top-up settlement at rollover (monotonic within the day)
        val k = "bankuse_${store.today()}_$pkg"
        if (sp.bankUsedMin > store.int(k)) store.putInt(k, sp.bankUsedMin)
        return sp
    }

    /** Same source for Home, App Rules, Time Bank and Stats: four separate, labelled numbers. */
    data class DayTotals(val baseMin: Int, val studyMin: Int, val bankMin: Int, val balance: Int)

    fun totals(ctx: Context, store: Store): DayTotals {
        val sp = store.appRules.mapNotNull { split(ctx, store, it.pkg) }
        return DayTotals(sp.sumOf { it.baseUsedMin }, sp.sumOf { it.studyExemptMin }, sp.sumOf { it.bankUsedMin }, store.bankMinutes)
    }

    /** Minutes left today in a rule app. */
    fun remainingMin(ctx: Context, store: Store, pkg: String): Int = split(ctx, store, pkg)?.remainingMin ?: Int.MAX_VALUE

    fun shouldBlock(ctx: Context, store: Store, pkg: String): Boolean {
        if (!store.restrictionsEnabled) return false
        if (Allowed.isCore(ctx, pkg)) return false
        if (pkg in store.blockedAlways) return true
        if (store.sessionActive && pkg in store.blockedDuring) return true
        if (store.appRules.none { it.pkg == pkg }) return false
        return remainingMin(ctx, store, pkg) <= 0
    }

    fun isTimed(store: Store, pkg: String) = store.appRules.any { it.pkg == pkg }
}
