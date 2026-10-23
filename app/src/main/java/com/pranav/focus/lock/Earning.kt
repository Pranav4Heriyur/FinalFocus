package com.pranav.focus.lock

import android.content.Context
import com.pranav.focus.data.AppClock
import com.pranav.focus.data.Cond
import com.pranav.focus.data.PlanDates
import com.pranav.focus.data.StudyDb
import com.pranav.focus.data.StudyLogic
import com.pranav.focus.data.ProgressionPolicy
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.Store
import java.time.LocalDate

/** Verified effort -> minutes. Everything resets at midnight (DayRec is per day). */
object Earning {
    data class Action(val id: String, val cond: Cond, val target: Int, val minutes: Int)

    val ACTIONS = listOf(
        Action("run", Cond.RUN, ProgressionPolicy.RUN_GOAL_KM, 15),
        Action("workout", Cond.WORKOUT, 1, 10),
        Action("study", Cond.STUDY, 60, 20),
    )

    fun progress(store: Store, cond: Cond): Double {
        val today = store.activitiesOn(store.today())
        return when (cond) {
            Cond.RUN -> today.filter { it.isRun }.sumOf { it.distM } / 1000.0
            Cond.RIDE -> today.filter { it.isRide }.sumOf { it.distM } / 1000.0
            Cond.WORKOUT -> store.workoutsOn(store.today()).toDouble()
            Cond.FOCUS -> store.todayFocusMinutes.toDouble()
            Cond.STUDY -> store.studyToday.toDouble()
        }
    }

    /** Pay out anything newly achieved. Safe to call often. */
    fun evaluate(ctx: Context): Boolean {
        val s = Store.get(ctx)
        val d = s.day()
        var changed = false
        val mult = ProgressionPolicy.consistencyMultiplier(s.workoutDaysInWeek())
        for (a in ACTIONS) {
            if (a.id in d.actions) continue
            if (progress(s, a.cond) >= a.target) {
                // Workout minutes get the consistency bonus; other actions pay their base minutes.
                val pay = if (a.cond == Cond.WORKOUT) Math.round(a.minutes * mult).toInt() else a.minutes
                d.actions.add(a.id); d.earnedTotal += pay
                s.ledger.earn("act:${s.today()}:${a.id}", System.currentTimeMillis(), s.today(), pay, a.id)
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
     * Time Bank minutes for logged study questions (Physics 1.10, Chemistry 0.78, Biology 0.34 min each). This is on top of
     * the daily 60-question rule. The day's total is rebuilt from the saved logs, so calling it again credits only the new
     * part, and the ledger id is keyed on that total so a retry can never pay twice.
     */
    @Synchronized
    fun creditStudyQuestions(ctx: Context): Int {
        val s = Store.get(ctx)
        val day = s.today()
        val (from, to) = PlanDates.dayBoundsMs(LocalDate.parse(day), AppClock.zone())
        val target = StudyLogic.timeBankMinutes(StudyDb.get(ctx).attemptedBySubjectBetween(from, to))
        val already = s.ledger.earnedFrom(day, "study_q")
        if (target <= already) return 0
        s.ledger.earn("studyq:$day:$target", System.currentTimeMillis(), day, target - already, "study_q")
        return target - already
    }

    /**
     * Called once when an in-app workout is saved. XP is idempotent per session id, so a repeated call never pays twice.
     * XP = WORKOUT_XP x consistency multiplier. Time Bank is then re-evaluated so the daily workout minutes can pay out.
     */
    fun workoutSaved(ctx: Context, sessionId: String) {
        val s = Store.get(ctx)
        val mult = ProgressionPolicy.consistencyMultiplier(s.workoutDaysInWeek())
        s.awardXp("workout:$sessionId", "workout", Math.round(com.pranav.focus.data.Rewards.WORKOUT_XP * mult).toInt())
        evaluate(ctx)
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
