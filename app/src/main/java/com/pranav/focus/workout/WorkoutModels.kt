package com.pranav.focus.workout

data class Exercise(
    val id: String, val name: String, val equipment: String, val primary: String, val secondary: String, val type: String,
    val sets: Int, val reps: String, val restS: Int, val unilateral: Boolean, val custom: Boolean, val favourite: Boolean, val lastUsedTs: Long?,
    val movementPattern: String = "", val bodyweightFactor: Double? = null, val instructions: String = "",
)

data class Routine(
    val id: String, val code: String, val name: String, val category: String, val focus: String, val primaryFocus: String, val description: String,
    val difficulty: String, val minLow: Int, val minHigh: Int, val labelHub: String, val labelPlans: String, val accent: String, val kind: String,
    val heavyLegs: Boolean, val notes: String, val preset: Boolean, val exerciseCount: Int,
) { val isGym get() = category == "gym" }

/** One prescribed exercise in a routine or in a plan day. [uid] is the DB row id, or a negative number for a not-yet-saved line. */
data class Line(
    val uid: Long, val exerciseId: String, val name: String, val equipment: String, val primary: String, val type: String,
    val sets: Int, val repText: String, val restS: Int, val restText: String, val unilateral: Boolean, val superset: String?, val note: String = "",
) {
    val prescription get() = WorkoutLogic.prescription(sets, repText, type)
    val rest get() = WorkoutLogic.restLabel(restS, restText)
    val muscleTag get() = primary.split(",").firstOrNull()?.trim().orEmpty()
}

data class Plan(
    val id: String, val name: String, val description: String, val planType: String, val tags: List<String>, val templateId: String,
    val active: Boolean, val preset: Boolean, val slot1Weekday: Int, val createdAt: Long, val updatedAt: Long,
)

data class PlanDay(
    val planId: String, val slot: Int, val morning: String?, val altMorning: String?, val gym: String?, val notes: String, val customized: Boolean,
)

/**
 * [timingV] 2 (canonical): [startedAt] is the real start, [endedAt] the real finish; [pausedMs]/[restMs] are accumulated separately.
 * [timingV] 1 (earlier repair): [startedAt] was moved forward by every pause, so endedAt - startedAt excludes pauses. [timingV] 0: only start/end are known.
 * [snapV] >= 1 means a completion snapshot exists.
 */
data class Session(
    val id: String, val planId: String?, val slot: Int?, val routineId: String?, val title: String, val activity: String, val status: String, val startedAt: Long, val endedAt: Long?,
    val pausedMs: Long = 0L, val restMs: Long = 0L, val timingV: Int = 0, val snapV: Int = 0, val routineName: String? = null, val categoryLabel: String? = null,
)

data class SetLog(
    val id: Long, val sessionId: String, val exerciseId: String, val exerciseName: String, val setNo: Int, val loadKg: Double?, val reps: Int?, val rpe: Int?,
    val durationS: Int?, val distanceM: Double?, val note: String, val warmup: Boolean, val completedAt: Long,
)

data class WeekStats(val workouts: Int, val runRuck: Int, val minutes: Int, val volumeKg: Double)

/** One past workout's sets for an exercise (Previous Sets). */
data class HistorySession(val sessionId: String, val date: Long, val sets: List<SetLog>)
