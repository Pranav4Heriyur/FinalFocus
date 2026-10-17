package com.pranav.focus.lock

import android.content.Context
import com.pranav.focus.data.Cond
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.Store

/** Verified effort -> minutes. Everything resets at midnight (DayRec is per day). */
object Earning {
    data class Action(val id: String, val cond: Cond, val target: Int, val minutes: Int)

    val ACTIONS = listOf(
        Action("run", Cond.RUN, 5, 15),
        Action("workout", Cond.WORKOUT, 1, 10),
        Action("study", Cond.STUDY, 60, 20),
    )

    fun progress(store: Store, cond: Cond): Double {
        val today = store.activitiesOn(store.today())
        return when (cond) {
            Cond.RUN -> today.filter { it.isRun }.sumOf { it.distM } / 1000.0
            Cond.RIDE -> today.filter { it.isRide }.sumOf { it.distM } / 1000.0
            Cond.WORKOUT -> (today.count { it.isWorkout } + store.int("workouts_${store.today()}")).toDouble()
            Cond.FOCUS -> store.todayFocusMinutes.toDouble()
            Cond.STUDY -> store.studyToday.toDouble()
        }
    }

    /** Pay out anything newly achieved. Safe to call often. */
    fun evaluate(ctx: Context): Boolean {
        val s = Store.get(ctx)
        val d = s.day()
        var changed = false
        for (a in ACTIONS) {
            if (a.id in d.actions) continue
            if (progress(s, a.cond) >= a.target) {
                d.actions.add(a.id); d.earnedTotal += a.minutes
                s.ledger.earn("act:${s.today()}:${a.id}", System.currentTimeMillis(), s.today(), a.minutes, a.id)
                s.awardXp("act:${s.today()}:${a.id}", "action", Rewards.ACTION_XP); changed = true
            }
        }
        for (r in s.unlockRules) {
            if (r.id in d.granted) continue
            if (progress(s, r.cond) >= r.target) {
                d.granted.add(r.id)
                d.alloc[r.pkg] = (d.alloc[r.pkg] ?: 0) + r.minutes
                d.earnedTotal += r.minutes
                s.awardXp("rule:${s.today()}:${r.id}", "rule", Rewards.RULE_XP); changed = true
            }
        }
        if (changed) s.saveDay(d)
        return changed
    }

    /**
     * Spend Time Bank minutes on a top-up. Call ONLY after the user confirmed the amount.
     * [requestId] makes a retry/double-tap idempotent (same id never charges twice).
     */
    @Synchronized
    fun unlockFromPool(ctx: Context, pkg: String, want: Int, requestId: String = java.util.UUID.randomUUID().toString()): Int {
        val s = Store.get(ctx)
        s.rollover()
        val id = "topup:$requestId"
        val m = s.ledger.reserveTopUp(id, System.currentTimeMillis(), s.today(), pkg, want)
        // The ledger reservation IS the grant (Enforcer reads it back); nothing else is written, so no crash window.
        return m
    }
}
