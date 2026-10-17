package com.pranav.focus.workout

/** One exercise of a completed session as it was when the workout was saved (V5 snapshot). */
data class SnapEx(val exerciseId: String, val position: Int, val name: String, val equipment: String, val primary: String, val secondary: String, val type: String)

/**
 * The canonical duration model used by C8/C9 (live), C10, C11 and C12:
 *  - ELAPSED = finishedAt - startedAt, INCLUDING pauses and timed rests.
 *  - ACTIVE  = max(0, elapsed - totalPaused - totalTimedRest). Only available when the session tracked timing ([tracked]).
 *  - Timed rest = only rests the user ran with the rest timer (rest_log). Ordinary gaps between sets are NOT rest. A running rest is paused when the
 *    workout is paused, so a rest and a pause can never overlap and nothing is subtracted twice.
 * Sessions saved before timing was tracked (timing_v 0) report elapsed only; active/paused/rest stay null (never reconstructed, never shown as 0).
 * timing_v 1 sessions (started_at shifted by pauses) are read with their original meaning: elapsed = (end - start) + paused.
 */
data class SessionTiming(val elapsedMs: Long?, val activeMs: Long?, val pausedMs: Long?, val restMs: Long?, val tracked: Boolean) {
    val elapsedMin: Long? get() = elapsedMs?.let { it / 60_000L }
    val activeMin: Long? get() = activeMs?.let { it / 60_000L }
}

object WorkoutTiming {
    const val CANONICAL = 2

    fun of(s: Session): SessionTiming = of(s.startedAt, s.endedAt, s.pausedMs, s.restMs, s.timingV)

    fun of(startedAt: Long, endedAt: Long?, pausedMs: Long, restMs: Long, timingV: Int): SessionTiming {
        val end = endedAt ?: return SessionTiming(null, null, null, null, false)
        val span = (end - startedAt).coerceAtLeast(0L)
        val paused = pausedMs.coerceAtLeast(0L); val rest = restMs.coerceAtLeast(0L)
        return when {
            timingV >= CANONICAL -> {
                val p = paused.coerceAtMost(span)                                   // a pause can never exceed the workout itself
                SessionTiming(span, (span - p - rest).coerceAtLeast(0L), p, rest, true)
            }
            timingV == 1 -> SessionTiming(span + paused, (span - rest).coerceAtLeast(0L), paused, rest, true)
            else -> SessionTiming(span, null, null, null, false)
        }
    }

    /** The live workout clock (C8): time since start excluding pauses, frozen while paused. [pausedAt] = start of the open pause, if any. */
    fun runningMs(startedAt: Long, pausedMs: Long, timingV: Int, wallNow: Long, pausedAt: Long?): Long {
        val upTo = if (pausedAt != null) minOf(pausedAt, wallNow) else wallNow
        return (upTo - startedAt - if (timingV >= CANONICAL) pausedMs.coerceAtLeast(0L) else 0L).coerceAtLeast(0L)
    }

    fun fmtMin(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

    /** Label/value pairs for the metric rows: "Elapsed" (+ "Active" when tracked); legacy sessions show a plain "Duration". */
    fun metrics(t: SessionTiming): List<Pair<String, String>> = buildList {
        t.elapsedMin?.let { add((if (t.tracked) "Elapsed" else "Duration") to fmtMin(it)) }
        if (t.tracked) t.activeMin?.let { add("Active" to fmtMin(it)) }
    }
}
