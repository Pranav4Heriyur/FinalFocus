package com.pranav.focus.data

/**
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
    /** CONFIRM: streak day qualifies when at least this many focus minutes were completed (today: any > 0). */
    const val STREAK_MIN_FOCUS_MINUTES = 1
    /** CONFIRM: XP values mirror existing [Rewards]/[StudyLogic] constants. */
    const val XP_PER_FOCUS_MINUTE = Rewards.XP_PER_FOCUS_MINUTE
    const val QUIT_PENALTY = Rewards.QUIT_XP_PENALTY
    const val STUDY_DAILY_CAP = StudyLogic.XP_DAILY_CAP

    fun streakQualifies(focusMinutes: Int) = focusMinutes >= STREAK_MIN_FOCUS_MINUTES
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
