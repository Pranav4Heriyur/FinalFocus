package com.pranav.focus.data

import java.time.LocalDate

/** Weekly targets and aggregates (Monday-based weeks). */
object Weekly {
    data class W(val focusMin: Int, val runs: Int, val workouts: Int, val questions: Int, val km: Double)

    val BASE = W(720, 3, 4, 400, 0.0)

    fun weekStart(d: LocalDate = AppClock.today()): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun stats(s: Store, start: LocalDate): W {
        val focus = s.focusDays()
        val acts = s.activities
        var f = 0; var runs = 0; var wk = 0; var q = 0; var km = 0.0
        for (i in 0..6) {
            val d = start.plusDays(i.toLong()).toString()
            f += focus[d] ?: 0
            q += s.studyAttemptedOn(d)
            acts.filter { it.startDate == d }.forEach {
                if (it.isRun) { runs++; km += it.distM / 1000.0 }
                if (it.isRide) km += it.distM / 1000.0
                if (it.isWorkout) wk++
            }
            wk += s.int("workouts_$d")
        }
        return W(f, runs, wk, q, km)
    }

    fun target(scale: Float): W = W(
        (BASE.focusMin * scale).toInt(), Math.ceil(BASE.runs * scale.toDouble()).toInt(),
        Math.ceil(BASE.workouts * scale.toDouble()).toInt(), (BASE.questions * scale).toInt(), 0.0,
    )

    fun met(w: W, t: W) = w.focusMin >= t.focusMin && w.runs >= t.runs && w.workouts >= t.workouts && w.questions >= t.questions

    /** Called when a new week starts: +5% harder if last week's targets were all met. */
    fun rollover(s: Store) {
        val thisWeek = weekStart().toString()
        if (s.targetWeek == thisWeek) return
        if (s.targetWeek.isNotEmpty()) {
            val last = LocalDate.parse(s.targetWeek)
            if (met(stats(s, last), target(s.targetScale))) s.targetScale = s.targetScale * 1.05f
        }
        s.targetWeek = thisWeek
    }
}
