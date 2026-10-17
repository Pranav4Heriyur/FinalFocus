package com.pranav.focus.workout

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * C11 calculations. Pure Kotlin (no Android), unit-tested.
 *
 * Rules (also documented in FOCUS_STATUS.md):
 *  - Only saved workouts count: status 'completed' with an end time (the repo queries enforce this).
 *  - A workout belongs to the day of its start time in the supplied zone. Weeks start on Monday.
 *  - Duration: ELAPSED = start to finish incl. pauses/rests (every saved workout); ACTIVE = elapsed minus pauses and timed rests (only workouts that tracked timing).
 *    Same definitions as C10/C12 (see WorkoutTiming). Negative values are clamped to 0.
 *  - Averages: volume / workouts with weighted sets; elapsed / all workouts in the period; active / workouts that have an active value.
 *  - Total training time = sum of ELAPSED minutes of all saved workouts (documented; active time is a separate, never-mixed aggregate).
 *  - Muscle shares use the muscles recorded in each workout's snapshot; legacy workouts without one use the current exercise records.
 *  - "Sets" = non-warm-up set rows of gym sessions (cardio sessions store one distance row, which is not a set).
 *  - Volume = load x reps for sets with load > 0 and reps > 0. Bodyweight/duration/carry sets add no volume.
 *  - Muscle share = share of working sets. A set's single credit is split equally across the muscles listed in the exercise's
 *    PRIMARY muscles. Secondary muscles are not weighted (no evidence for any weighting). Unmappable labels are reported, not guessed.
 */
enum class StatsRange(val label: String) { WEEK("Weekly"), MONTH("Monthly"), ALL("All-time") }

/** [primary] = primary muscles recorded in the workout's completion snapshot; null for legacy workouts (current exercise definitions are used then). */
data class StatSet(val id: Long, val sessionId: String, val exerciseId: String, val name: String, val loadKg: Double?, val reps: Int?, val completedAt: Long, val primary: String? = null)
data class StatSession(
    val id: String, val title: String, val activity: String, val startedAt: Long, val endedAt: Long,
    val pausedMs: Long = 0L, val restMs: Long = 0L, val timingV: Int = 0,
) {
    private val t get() = WorkoutTiming.of(startedAt, endedAt, pausedMs, restMs, timingV)
    /** Elapsed = start to finish incl. pauses and rests (all saved workouts have it). */
    val elapsedMs: Long get() = t.elapsedMs ?: 0L
    /** Active = elapsed minus pauses and timed rests; null for workouts saved before timing was tracked (never reconstructed). */
    val activeMs: Long? get() = t.activeMs
}
data class Bucket(val label: String, val volumeKg: Double, val workouts: Int)
data class Delta(val pct: Int)          // % change vs previous period; only built when the previous period had data
data class MuscleShare(val key: String, val label: String, val sets: Double, val share: Double)
data class RecentWorkout(val id: String, val title: String, val startedAt: Long, val minutes: Long, val exercises: Int, val volumeKg: Double)
data class LiftPoint(val dayMs: Long, val kg: Double, val reps: Int?)
data class PrEvent(val exerciseId: String, val name: String, val atMs: Long, val kg: Double, val reps: Int?, val prevKg: Double)
data class LiftProgress(
    val exerciseId: String, val name: String, val points: List<LiftPoint>, val currentKg: Double, val currentReps: Int?,
    val previousBestKg: Double?, val changePct: Int?, val isVerifiedPr: Boolean, val est1rm: Double?, val sessionCountAllTime: Int,
)
data class StatsResult(
    val range: StatsRange, val fromDay: LocalDate?, val toDay: LocalDate,
    val workouts: Int, val minutes: Long, val sets: Int, val volumeKg: Double, val exercisesDistinct: Int,
    val workoutsDelta: Delta?, val volumeDelta: Delta?,
    val buckets: List<Bucket>, val activeDays: Int, val daysInPeriod: Int, val avgWorkoutsPerWeek: Double?,
    val muscles: List<MuscleShare>, val unmappedSets: Int, val unmappedLabels: List<String>,
    val lifts: List<LiftProgress>, val prs: List<PrEvent>, val recent: List<RecentWorkout>,
    /** Average volume per workout = volume / [volumeWorkouts] (gym workouts in the period that have at least one weighted set). Null when there are none. */
    val avgVolumeKg: Double? = null, val volumeWorkouts: Int = 0,
    /** Average elapsed duration = total elapsed / workouts in the period. Null when there are no workouts. */
    val avgElapsedMin: Long? = null,
    /** Active time is summed only over [activeWorkouts] (workouts that tracked timing); the average uses that same count. */
    val activeMinutes: Long? = null, val activeWorkouts: Int = 0, val avgActiveMin: Long? = null,
)

object WorkoutStats {
    // ---- muscle mapping (documented, explicit) ----
    private val MAP: Map<String, String> = mapOf(
        "chest" to "chest", "shoulders" to "shoulders", "front delts" to "shoulders", "side delts" to "shoulders", "rear delts" to "rear delts",
        "biceps" to "biceps", "triceps" to "triceps", "forearms" to "forearms", "core" to "abs", "abs" to "abs", "back" to "back",
        "glutes" to "glutes", "quads" to "quads", "hamstrings" to "hamstrings", "calves" to "calves",
    )
    val LABEL: Map<String, String> = mapOf(
        "chest" to "Chest", "shoulders" to "Shoulders", "rear delts" to "Rear delts", "biceps" to "Biceps", "triceps" to "Triceps", "forearms" to "Forearms",
        "abs" to "Core", "back" to "Back", "glutes" to "Glutes", "quads" to "Quads", "hamstrings" to "Hamstrings", "calves" to "Calves",
    )
    /** Returns (mapped keys, unmapped labels). "Full body", "Cardio", "Hips", "Grip" etc. are deliberately unmapped. */
    fun mapMuscles(primary: String): Pair<List<String>, List<String>> {
        val parts = primary.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val mapped = parts.mapNotNull { MAP[it.lowercase()] }.distinct()
        return mapped to parts.filter { MAP[it.lowercase()] == null }
    }

    fun setVolume(s: StatSet): Double = if ((s.loadKg ?: 0.0) > 0.0 && (s.reps ?: 0) > 0) s.loadKg!! * s.reps!! else 0.0
    fun epley1rm(kg: Double, reps: Int?): Double? = if (reps != null && reps in 1..12 && kg > 0) kg * (1 + reps / 30.0) else null

    private fun day(ms: Long, z: ZoneId) = Instant.ofEpochMilli(ms).atZone(z).toLocalDate()

    fun bounds(range: StatsRange, today: LocalDate): Pair<LocalDate?, LocalDate> = when (range) {
        StatsRange.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
        StatsRange.MONTH -> YearMonth.from(today).let { it.atDay(1) to it.atEndOfMonth() }
        StatsRange.ALL -> null to today
    }

    private fun pct(cur: Double, prev: Double): Delta? = if (prev > 0.0) Delta(Math.round((cur - prev) / prev * 100).toInt()) else null

    fun compute(range: StatsRange, today: LocalDate, zone: ZoneId, allSessions: List<StatSession>, allSets: List<StatSet>, muscles: Map<String, Pair<String, String>>): StatsResult {
        val sessions = allSessions.distinctBy { it.id }
        val gymIds = sessions.filter { it.activity == "gym" }.map { it.id }.toSet()
        val sessionIds = sessions.map { it.id }.toSet()
        val sets = allSets.filter { it.sessionId in sessionIds }.distinctBy { it.id }
        val (from, to) = bounds(range, today)
        fun inRange(d: LocalDate) = (from == null || !d.isBefore(from)) && !d.isAfter(to)
        val sess = sessions.filter { inRange(day(it.startedAt, zone)) }
        val sessIds = sess.map { it.id }.toSet()
        val rangeSets = sets.filter { it.sessionId in sessIds && it.sessionId in gymIds }
        val vol = rangeSets.sumOf(::setVolume)

        // previous period (week/month only)
        var wd: Delta? = null; var vd: Delta? = null
        if (from != null) {
            val pFrom: LocalDate; val pTo: LocalDate
            if (range == StatsRange.WEEK) { pFrom = from.minusDays(7); pTo = from.minusDays(1) } else { val pm = YearMonth.from(from).minusMonths(1); pFrom = pm.atDay(1); pTo = pm.atEndOfMonth() }
            val ps = sessions.filter { val d = day(it.startedAt, zone); !d.isBefore(pFrom) && !d.isAfter(pTo) }
            val pIds = ps.map { it.id }.toSet()
            val pVol = sets.filter { it.sessionId in pIds && it.sessionId in gymIds }.sumOf(::setVolume)
            wd = pct(sess.size.toDouble(), ps.size.toDouble()); vd = pct(vol, pVol)
        }

        // buckets: week -> 7 days; month -> days grouped by Mon-Sun week; all -> months (last 12 with data span)
        val vById = rangeSets.groupBy { it.sessionId }.mapValues { (_, l) -> l.sumOf(::setVolume) }
        fun bucketOf(list: List<StatSession>, label: String) = Bucket(label, list.sumOf { vById[it.id] ?: 0.0 }, list.size)
        val buckets: List<Bucket> = when (range) {
            StatsRange.WEEK -> (0..6).map { i -> val d = from!!.plusDays(i.toLong()); bucketOf(sess.filter { day(it.startedAt, zone) == d }, d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }) }
            StatsRange.MONTH -> {
                val out = ArrayList<Bucket>(); var s = from!!; var n = 1
                while (!s.isAfter(to)) {
                    val e = minOf(s.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), to)
                    out.add(bucketOf(sess.filter { val d = day(it.startedAt, zone); !d.isBefore(s) && !d.isAfter(e) }, "W$n")); s = e.plusDays(1); n++
                }
                out
            }
            StatsRange.ALL -> {
                if (sess.isEmpty()) emptyList() else {
                    val first = YearMonth.from(day(sess.minOf { it.startedAt }, zone)); val last = YearMonth.from(today)
                    val start = maxOf(first, last.minusMonths(11)); val n = ChronoUnit.MONTHS.between(start, last).toInt()
                    (0..n).map { i -> val ym = start.plusMonths(i.toLong()); bucketOf(sess.filter { YearMonth.from(day(it.startedAt, zone)) == ym }, ym.month.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }) }
                }
            }
        }
        val activeDays = sess.map { day(it.startedAt, zone) }.distinct().size
        val endDay = if (from == null) today else minOf(to, today)
        val startDay = from ?: sess.minOfOrNull { day(it.startedAt, zone) }
        val daysIn = if (startDay == null) 0 else (ChronoUnit.DAYS.between(startDay, endDay).toInt() + 1).coerceAtLeast(0)
        val avgWk = if (range == StatsRange.ALL && daysIn > 0) sess.size / (daysIn / 7.0).coerceAtLeast(1.0) else null

        // muscle distribution
        val credit = LinkedHashMap<String, Double>(); var unmapped = 0; val unLabels = sortedSetOf<String>()
        rangeSets.forEach { st ->
            val (m, u) = mapMuscles(st.primary ?: muscles[st.exerciseId]?.first.orEmpty())
            if (m.isEmpty()) { unmapped++; u.forEach { unLabels.add(it) }; if (u.isEmpty()) unLabels.add("(no muscle listed)") }
            else m.forEach { credit[it] = (credit[it] ?: 0.0) + 1.0 / m.size }
        }
        val tot = credit.values.sum()
        val shares = credit.entries.sortedByDescending { it.value }.map { MuscleShare(it.key, LABEL[it.key] ?: it.key, it.value, if (tot > 0) it.value / tot else 0.0) }

        // strength: best set per exercise per workout day-session, over ALL history (for honest PR / previous-best), then clipped for display
        val sessionStart = sessions.associate { it.id to it.startedAt }
        val gymSets = sets.filter { it.sessionId in gymIds && (it.loadKg ?: 0.0) > 0.0 && (it.reps ?: 0) > 0 }   // same PR-compatible sets as C10/C12
        val perEx = gymSets.groupBy { it.exerciseId }
        val lifts = ArrayList<LiftProgress>(); val prs = ArrayList<PrEvent>()
        perEx.forEach { (exId, l) ->
            val bySession = l.groupBy { it.sessionId }.map { (sid, ss) -> sid to ss.maxWithOrNull(compareBy({ it.loadKg ?: 0.0 }, { it.reps ?: 0 }))!! }.sortedBy { sessionStart[it.first] ?: 0L }
            var runMax = 0.0; var seen = 0
            bySession.forEach { (sid, b) ->
                val kg = b.loadKg!!
                if (seen > 0 && kg > runMax) prs.add(PrEvent(exId, b.name, sessionStart[sid] ?: 0L, kg, b.reps, runMax))
                runMax = maxOf(runMax, kg); seen++
            }
            val shown = bySession.filter { inRange(day(sessionStart[it.first] ?: 0L, zone)) }
            if (shown.isEmpty()) return@forEach
            val pts = shown.map { (sid, b) -> LiftPoint(sessionStart[sid] ?: 0L, b.loadKg!!, b.reps) }
            val last = shown.last(); val lastIdx = bySession.indexOfFirst { it.first == last.first }
            val before = bySession.take(lastIdx).maxOfOrNull { it.second.loadKg!! }
            val cur = last.second.loadKg!!
            lifts.add(LiftProgress(exId, last.second.name, pts, cur, last.second.reps, before, before?.let { b -> if (b > 0) Math.round((cur - b) / b * 100).toInt() else null }, before != null && cur > before, epley1rm(cur, last.second.reps), bySession.size))
        }
        val recent = sess.sortedByDescending { it.startedAt }.take(5).map { s ->
            RecentWorkout(s.id, s.title.ifBlank { "Workout" }, s.startedAt, s.elapsedMs / 60_000L, rangeSets.filter { it.sessionId == s.id }.map { it.exerciseId }.distinct().size, vById[s.id] ?: 0.0)
        }
        val elapsedTotalMs = sess.sumOf { it.elapsedMs }
        val volWorkouts = sess.count { it.id in gymIds && (vById[it.id] ?: 0.0) > 0.0 }
        val activeSess = sess.filter { it.activeMs != null }
        val activeTotalMs = activeSess.sumOf { it.activeMs ?: 0L }
        return StatsResult(
            range, from, to, sess.size, elapsedTotalMs / 60_000L, rangeSets.size, vol, rangeSets.map { it.exerciseId }.distinct().size,
            wd, vd, buckets, activeDays, daysIn, avgWk, shares, unmapped, unLabels.toList(),
            lifts.sortedWith(compareByDescending<LiftProgress> { it.sessionCountAllTime }.thenBy { it.name }),
            prs.filter { inRange(day(it.atMs, zone)) }.sortedByDescending { it.atMs }, recent,
            if (volWorkouts > 0) vol / volWorkouts else null, volWorkouts,
            if (sess.isNotEmpty()) elapsedTotalMs / sess.size / 60_000L else null,
            if (activeSess.isNotEmpty()) activeTotalMs / 60_000L else null, activeSess.size, if (activeSess.isNotEmpty()) activeTotalMs / activeSess.size / 60_000L else null,
        )
    }
}
