package com.pranav.focus.data

/**
 * CAPS AND MULTIPLIERS, ONE PLACE (read this before adding a reward):
 * - Focus: 2 XP per minute. Quitting a session costs 50 XP (never below 0).
 * - Study: XP = attempted + 2 x correct, capped at StudyLogic.XP_DAILY_CAP (300) per day.
 * - Earning actions (Time Bank): run 15 min, workout 10 min (x consistency), study 20 min. Each once per day.
 * - Workout XP: 20 base x consistency multiplier, once per saved session.
 * - Consistency multiplier: days with a workout in the last 7 -> 1 = x1.0, 2-3 = x1.2, 4-5 = x1.35, 6-7 = x1.5.
 * - Weekly focus target: next week = focus time achieved this week (floor 60 min). Other weekly targets: +5% when met.
 *
 * Single, versioned progression policy. Rank thresholds live ONLY here ([Ranks] reads them).
 * Values marked CONFIRM were not specified by the product owner: they keep today's shipped behaviour
 * and are isolated here so they can be changed in one place after confirmation.
 */
object ProgressionPolicy {
    const val VERSION = 1
    /** CONFIRM: current shipped thresholds, unchanged. */
    val RANKS: List<Rank> = listOf(
        Rank("Recruit", 0, "Building the habit."), Rank("Grinder", 250, "Showing up consistently."),
        Rank("Uncommon", 500, "You do what most people don’t."), Rank("Relentless", 1000, "Higher standards. No excuses."),
        Rank("Disciplined", 2000, "Results over feelings."), Rank("Elite", 4000, "A different breed."),
        Rank("Legendary", 8000, "Inspires others."),
    )
    /** Run goal per day (Time Bank "run" action) and the basis of the weekly run-distance target. */
    const val RUN_GOAL_KM = 5
    /** Days with a workout per week that the consistency tier x1.35 is built around. Weekly workout target. */
    const val WORKOUT_DAYS_TARGET = 4

    /** CONFIRM: streak day qualifies when at least this many focus minutes were completed (today: any > 0). */
    const val STREAK_MIN_FOCUS_MINUTES = 1
    /** CONFIRM: XP values mirror existing [Rewards]/[StudyLogic] constants. */
    const val XP_PER_FOCUS_MINUTE = Rewards.XP_PER_FOCUS_MINUTE
    const val QUIT_PENALTY = Rewards.QUIT_XP_PENALTY
    const val STUDY_DAILY_CAP = StudyLogic.XP_DAILY_CAP

    fun streakQualifies(focusMinutes: Int) = focusMinutes >= STREAK_MIN_FOCUS_MINUTES

    /**
     * Consistency multiplier for workouts, from the number of days with a workout in the last 7 (today included).
     * Applies to workout Time Bank minutes and workout XP. 1 day = x1.0, 2-3 = x1.2, 4-5 = x1.35, 6-7 = x1.5.
     */
    fun consistencyMultiplier(daysWithWorkoutIn7: Int): Double = when {
        daysWithWorkoutIn7 >= 6 -> 1.5
        daysWithWorkoutIn7 >= 4 -> 1.35
        daysWithWorkoutIn7 >= 2 -> 1.2
        else -> 1.0
    }
}

/** One recorded XP movement; [id] makes it idempotent. */
data class XpEvent(val id: String, val ts: Long, val day: String, val source: String, val delta: Int)

/** Pure XP ledger: balance is the sum of events; a repeated id is ignored. */
class XpLedger(initial: List<XpEvent> = emptyList(), private val persist: (List<XpEvent>) -> Unit = {}) {
    private val ev = ArrayList(initial); private val ids = HashSet(initial.map { it.id })
    @Synchronized fun total(): Int = ev.sumOf { it.delta }.coerceAtLeast(0)
    @Synchronized fun on(day: String, source: String? = null) = ev.filter { it.day == day && (source == null || it.source == source) }.sumOf { it.delta }
    /** Quit penalty never takes XP below zero: the applied delta is clamped to the current total. */
    @Synchronized fun record(e: XpEvent): Boolean {
        if (!ids.add(e.id)) return false
        val d = if (e.delta < 0) maxOf(e.delta, -total()) else e.delta
        ev.add(e.copy(delta = d)); persist(ev.toList()); return true
    }
    @Synchronized fun all() = ev.toList()
    companion object {
        fun encode(l: List<XpEvent>) = l.joinToString("\n") { listOf(it.id.replace('\t', ' '), it.ts, it.day, it.source, it.delta).joinToString("\t") }
        fun decode(s: String?) = s.orEmpty().lines().mapNotNull { ln -> val p = ln.split('\t'); if (p.size < 5) null else runCatching { XpEvent(p[0], p[1].toLong(), p[2], p[3], p[4].toInt()) }.getOrNull() }
    }
}
