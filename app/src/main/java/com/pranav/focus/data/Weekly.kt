package com.pranav.focus.data

import java.time.LocalDate

/** Weekly targets and aggregates (Monday-based weeks). */
object Weekly {
    /**
     * focusMin: rolls over to the focus time actually achieved last week.
     * runKm: run distance against the run goal (RUN_GOAL_KM x BASE_RUNS). Not scaled.
     * workoutDays: days with a workout (the same measure as the consistency bonus). Not scaled.
     * questions: the only target that scales (+5% when met).
     */
    data class W(val focusMin: Int, val runKm: Double, val workoutDays: Int, val questions: Int)

    const val BASE_FOCUS_MIN = 720
    const val BASE_QUESTIONS = 400
    const val BASE_RUNS = 3

    fun weekStart(d: LocalDate = AppClock.today()): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun stats(s: Store, start: LocalDate): W {
        val focus = s.focusDays()
        var f = 0; var runKm = 0.0; var workoutDays = 0; var q = 0
        for (i in 0..6) {
            val d = start.plusDays(i.toLong()).toString()
            f += focus[d] ?: 0
            q += s.studyAttemptedOn(d)
            var hasWorkout = s.inAppWorkoutsOn(d) > 0
            s.activitiesOn(d).forEach {
                if (it.isRun) runKm += it.distM / 1000.0
                if (it.isWorkout) hasWorkout = true
            }
            if (hasWorkout) workoutDays++
        }
        return W(f, runKm, workoutDays, q)
    }

    /** Lowest focus target a week can roll over to, so an empty week never sets a zero target. */
    const val MIN_FOCUS_TARGET_MIN = 60

    /** Next week's focus target is the focus time actually achieved last week, not last week's target plus a percentage. */
    fun nextFocusTargetMin(achievedMin: Int): Int = achievedMin.coerceAtLeast(MIN_FOCUS_TARGET_MIN)

    /** This week's focus target: the rolled-over value if set, otherwise the base. */
    fun focusTarget(s: Store): Int = s.targetFocusMin.takeIf { it > 0 } ?: BASE_FOCUS_MIN

    /** Only questions use the scale; run distance and workout days are fixed by their own metrics. */
    fun target(scale: Float, focusMin: Int = (BASE_FOCUS_MIN * scale).toInt()): W = W(
        focusMin = focusMin,
        runKm = ProgressionPolicy.RUN_GOAL_KM.toDouble() * BASE_RUNS,
        workoutDays = ProgressionPolicy.WORKOUT_DAYS_TARGET,
        questions = (BASE_QUESTIONS * scale).toInt(),
    )

    /**
     * Called when a new week starts.
     * Focus target = focus time actually achieved last week (e.g. 12 h target, 18 h achieved -> 18 h next week).
     * Questions target: +5% when last week's questions target was met. Runs and workouts are not scaled.
     */
    fun rollover(s: Store) {
        val thisWeek = weekStart().toString()
        if (s.targetWeek == thisWeek) return
        if (s.targetWeek.isNotEmpty()) {
            val last = LocalDate.parse(s.targetWeek)
            val achieved = stats(s, last)
            if (achieved.questions >= target(s.targetScale).questions) s.targetScale = s.targetScale * 1.05f
            s.targetFocusMin = nextFocusTargetMin(achieved.focusMin)
        }
        s.targetWeek = thisWeek
    }
}
