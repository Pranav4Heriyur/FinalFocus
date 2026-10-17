package com.pranav.focus.workout

import java.time.DayOfWeek
import java.time.LocalDate

/** Pure workout rules: prescription parsing/formatting, time estimates, filters, ordering, schedule validation. */
object WorkoutLogic {
    data class Reps(val min: Int?, val max: Int?, val unit: String)

    private val RANGE = Regex("""^\s*(\d+)\s*[-–]\s*(\d+)\s*(.*)$""")
    private val SINGLE = Regex("""^\s*(\d+)\s*(.*)$""")

    /** "8-10" -> 8..10 reps; "30-40 m" -> 30..40 "m"; "10 per side" -> 10..10 "per side"; "Max time" -> null. */
    fun parseReps(text: String): Reps {
        RANGE.find(text)?.let { return Reps(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].trim()) }
        SINGLE.find(text)?.let { return Reps(it.groupValues[1].toInt(), it.groupValues[1].toInt(), it.groupValues[2].trim()) }
        return Reps(null, null, text.trim())
    }

    /** "4 × 8–10 reps" / "3 × 30–40 m" / "3 × 8–10 per leg" / "2 × Max time" / "30 sec on / 30 sec off". */
    fun prescription(sets: Int, repText: String, type: String = "reps"): String {
        val r = parseReps(repText)
        if (r.min == null) return if (sets > 1) "$sets × ${repText.trim()}" else repText.trim()
        val range = if (r.min == r.max) "${r.min}" else "${r.min}–${r.max}"
        val unit = when {
            r.unit.isEmpty() -> if (type == "reps" || type == "") "reps" else ""
            else -> r.unit
        }
        val body = if (unit.isEmpty()) range else "$range $unit"
        return if (sets > 1) "$sets × $body" else body
    }

    fun restLabel(seconds: Int, text: String = ""): String = when {
        text.isNotBlank() -> text
        seconds <= 0 -> "No rest"
        seconds < 120 -> "$seconds sec"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60}–${seconds / 60 + 1} min"
    }

    /** Minimal line shape for estimating; RoutineLine/DayLine in the repository both map onto it. */
    data class Est(val sets: Int, val restS: Int, val type: String)

    /**
     * Estimated minutes from sets and rest: per set ~40 s work (45 s for carries/holds), plus the prescribed rest, plus a 5 min
     * warm-up and 1 min of setup per exercise. Rounded to the nearest minute. Morning/circuit routines use their stored range instead.
     */
    fun estimateMinutes(lines: List<Est>): Int {
        if (lines.isEmpty()) return 0
        var s = 5 * 60
        for (l in lines) {
            val work = if (l.type == "carry" || l.type == "hold") 45 else 40
            s += l.sets * (work + l.restS) + 60
        }
        return Math.round(s / 60.0).toInt()
    }

    // ---- exercise filters (C5) ----
    val MUSCLE_FILTERS = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio")
    val EQUIPMENT_FILTERS = listOf("Barbell", "Dumbbell", "Machine", "Bodyweight", "Cable")

    fun muscleGroups(primary: String): Set<String> = primary.split(",").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull {
        when (it.lowercase()) {
            "chest" -> "Chest"
            "back", "lats", "grip" -> "Back"
            "quads", "hamstrings", "glutes", "calves", "legs", "hips" -> "Legs"
            "shoulders", "rear delts", "front delts", "side delts" -> "Shoulders"
            "triceps", "biceps", "arms", "forearms" -> "Arms"
            "core", "abs" -> "Core"
            "cardio", "full body" -> "Cardio"
            else -> null
        }
    }.toSet()

    /** C8: library sections. Presets sort by code (G1..G4, M1..M5); user routines follow, A-Z, under "Custom". Empty sections are dropped. */
    fun librarySections(all: List<Routine>): List<Pair<String, List<Routine>>> {
        fun presets(cat: String) = all.filter { it.category == cat && it.preset }.sortedWith(compareBy({ it.code.length }, { it.code }, { it.name }))
        return listOf(
            "Gym Routines" to presets("gym"),
            "Morning Routines" to presets("morning"),
            "Custom Routines" to all.filter { !it.preset }.sortedBy { it.name.lowercase() },
        ).filter { it.second.isNotEmpty() }
    }

    // ---- C6 custom exercise ----
    /** label -> stored `type` (existing model: reps / hold / carry). */
    val TRACKING_TYPES = listOf("Weight & reps" to "reps", "Bodyweight reps" to "reps", "Duration" to "hold", "Distance" to "carry")
    val MOVEMENT_PATTERNS = listOf("Push", "Pull", "Squat", "Hinge", "Lunge", "Carry", "Core", "Isolation", "Cardio", "Mobility")
    val ALL_MUSCLES = MUSCLE_FILTERS.filter { it != "Arms" } + SPECIFIC_MUSCLES
    val CUSTOM_EQUIPMENT = listOf("Barbell", "Dumbbell", "Machine", "Cable", "Bodyweight", "Band", "Kettlebell", "Other")
    fun defaultRepsFor(type: String) = when (type) { "hold" -> "30-45 sec"; "carry" -> "20-30 m"; else -> "8-12" }
    fun normaliseName(n: String) = n.lowercase().filter { it.isLetterOrDigit() }

    /** "Last 60 kg × 8"; null when there is no usable history. */
    fun lastUsedLabel(last: Pair<Double, Int?>?): String? {
        val (kg, reps) = last ?: return null
        if (kg <= 0.0) return null
        return "Last ${fmtKg(kg)} kg" + (reps?.takeIf { it > 0 }?.let { " × $it" } ?: "")
    }

    /** C5 specific-muscle filters (multi-select). */
    val SPECIFIC_MUSCLES = listOf("Biceps", "Triceps", "Front Delts", "Side Delts", "Rear Delts", "Forearms")

    /**
     * Specific muscles an exercise trains. Explicit tags win; a generic "Shoulders" entry (older catalogue / custom rows)
     * is resolved from its name so e.g. Lateral Raise still shows under Side Delts.
     */
    fun specificMuscles(primary: String, name: String = ""): Set<String> {
        val out = linkedSetOf<String>()
        primary.split(",").map { it.trim().lowercase() }.forEach {
            when (it) {
                "biceps" -> out += "Biceps"; "triceps" -> out += "Triceps"; "forearms" -> out += "Forearms"
                "front delts" -> out += "Front Delts"; "side delts" -> out += "Side Delts"; "rear delts" -> out += "Rear Delts"
                "shoulders" -> { val n = name.lowercase()
                    when {
                        "lateral" in n || "upright" in n -> out += "Side Delts"
                        "rear" in n || "face pull" in n || "reverse fly" in n -> out += "Rear Delts"
                        else -> out += "Front Delts"
                    } }
            }
        }
        return out
    }

    /** OR-match: an exercise passes when it matches ANY selected broad group or specific muscle (empty selection = no filter). */
    fun matchesMuscles(primary: String, name: String, selected: Set<String>): Boolean =
        selected.isEmpty() || (muscleGroups(primary) + specificMuscles(primary, name)).any { it in selected }

    fun equipmentMatches(equipment: String, filter: String): Boolean = equipment.lowercase().contains(filter.lowercase())

    fun matchesQuery(name: String, equipment: String, muscles: String, q: String): Boolean {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return true
        val hay = "$name $equipment $muscles".lowercase()
        return t.split(Regex("\\s+")).all { hay.contains(it) }
    }

    // ---- ordering ----
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        val m = list.toMutableList(); val x = m.removeAt(from); m.add(to, x); return m
    }

    // ---- plan validation ----
    const val NAME_MAX = 60
    const val DESC_MAX = 200
    const val NOTES_MAX = 200

    /** Null when valid; otherwise a user-facing message. [existing] are other plan names (case-insensitive duplicate check). */
    fun validatePlanName(name: String, existing: Collection<String>): String? {
        val t = name.trim()
        return when {
            t.isEmpty() -> "Enter a plan name."
            t.length > NAME_MAX -> "Plan name is too long (max $NAME_MAX)."
            existing.any { it.trim().equals(t, ignoreCase = true) } -> "A plan with this name already exists."
            else -> null
        }
    }

    // ---- schedule ----
    /** Slot (1..7) that falls on [date] when slot 1 sits on [slot1Weekday] (1 = Monday .. 7 = Sunday). */
    fun slotFor(date: LocalDate, slot1Weekday: Int): Int = ((date.dayOfWeek.value - slot1Weekday + 7) % 7) + 1

    fun weekdayOf(slot: Int, slot1Weekday: Int): DayOfWeek = DayOfWeek.of((slot1Weekday - 1 + slot - 1) % 7 + 1)

    /** [altMorningKind]/[altMorningCode] describe the optional alternative morning routine (e.g. M4 as the swap for M3 on slot 6). */
    data class SlotInfo(
        val slot: Int, val morningKind: String?, val gymHeavyLegs: Boolean, val morningCode: String?, val gymCode: String?,
        val altMorningKind: String? = null, val altMorningCode: String? = null,
    )
    data class ScheduleWarning(val slot: Int, val message: String)

    private fun isRunOrRuck(kind: String?) = kind == "run" || kind == "ruck"

    /**
     * Hard rule: no run or ruck (as the morning routine or as its alternative) may precede a heavy leg day (G1/G3), either the day
     * immediately before it or earlier on the same day. The week is cyclic (slot 7 precedes slot 1). Each result's `slot` is the slot
     * holding the offending run/ruck. [WorkoutRepo] refuses any edit that introduces one of these; see [newViolations].
     */
    fun scheduleViolations(days: List<SlotInfo>): List<ScheduleWarning> {
        val bySlot = days.associateBy { it.slot }
        val out = ArrayList<ScheduleWarning>()
        fun offending(d: SlotInfo): String? = when {
            isRunOrRuck(d.morningKind) -> d.morningCode ?: "Run/ruck"
            isRunOrRuck(d.altMorningKind) -> d.altMorningCode ?: "Run/ruck"
            else -> null
        }
        for (d in days) {
            if (!d.gymHeavyLegs) continue
            val gym = d.gymCode ?: "a heavy leg day"
            offending(d)?.let { out += ScheduleWarning(d.slot, "$it and $gym are both on day ${d.slot}; a run or ruck would come before the heavy leg session.") }
            val prev = bySlot[if (d.slot == 1) 7 else d.slot - 1] ?: continue
            offending(prev)?.let { out += ScheduleWarning(prev.slot, "$it on day ${prev.slot} is the day before $gym (day ${d.slot}).") }
        }
        return out
    }

    /** Alias kept for the UI: after the rule became a hard block this only reports plans that already broke it before the rule existed. */
    fun scheduleWarnings(days: List<SlotInfo>): List<ScheduleWarning> = scheduleViolations(days)

    /** Violations present in [after] that were not already in [before]: what an edit would newly introduce. */
    fun newViolations(before: List<SlotInfo>, after: List<SlotInfo>): List<ScheduleWarning> {
        val old = scheduleViolations(before).toSet()
        return scheduleViolations(after).filter { it !in old }
    }

    /** Hub/plan label for a day: the gym code when there is one, otherwise the morning code, otherwise "Rest". */
    fun dayLabel(morningCode: String?, gymCode: String?): String = gymCode ?: morningCode ?: "Rest"

    // ---- double progression ("next time") ----
    private fun fmtKg(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else v.toString()

    /**
     * Double progression from the previous session's working sets (load to reps pairs): when every set reached the top of the rep range,
     * suggest the smallest load step and reset to the bottom of the range; otherwise keep the load and add reps. Null with no data.
     */
    fun nextSuggestion(sets: List<Pair<Double, Int>>, repMin: Int, repMax: Int, step: Double = 2.5): String? {
        if (sets.isEmpty()) return null
        val load = sets.maxOf { it.first }
        return if (sets.all { it.second >= repMax }) "Add load: try ${fmtKg(load + step)} kg × $repMin"
        else "Stay at ${fmtKg(load)} kg and add reps (aim for $repMax)"
    }
}

/** One finished tracked rest, as stored in rest_log. [outcome] = completed | skipped | cancelled. */
data class RestRecord(val uid: String, val startedAt: Long, val seconds: Int, val outcome: String)

/**
 * THE shared rest tracker (Active Workout dialog and C9 Set Entry both drive this one object; nothing else measures rest).
 * Driven by a monotonic end timestamp (no tick counting). Each rest has a uid and is reported ONCE through [onRestEnded] when it completes, is
 * skipped, is replaced, or is cancelled; the repo stores it with INSERT OR IGNORE on the uid, so a reopened or restored timer cannot count twice.
 * Seconds = countdown time actually run (time spent with the rest paused is not rest). Idle shows the selected preset; finished = 0 left.
 */
class RestTimerState(private val clock: () -> Long, private val wall: () -> Long = { System.currentTimeMillis() }) {
    /** Called after every state change so the owner can persist [snapshot]. */
    var onChange: (() -> Unit)? = null
    /** Called once per tracked rest when it ends. */
    var onRestEnded: ((RestRecord) -> Unit)? = null
    var total by androidx.compose.runtime.mutableIntStateOf(90); private set
    var running by androidx.compose.runtime.mutableStateOf(false); private set
    var label by androidx.compose.runtime.mutableStateOf(""); private set
    private var endAt = 0L
    private var pausedLeft = -1L
    private var uid: String? = null
    private var startedWall = 0L
    val finished get() = !running && pausedLeft == 0L
    val paused get() = !running && pausedLeft > 0L
    fun leftMs(): Long = when { running -> (endAt - clock()).coerceAtLeast(0L); pausedLeft >= 0L -> pausedLeft; else -> total * 1000L }
    private fun ranSeconds(): Int = ((total * 1000L - leftMs()).coerceIn(0L, total * 1000L) / 1000L).toInt()
    /** Ends the open rest (running or paused) with [outcome]; no-op when none is open, so it can never report twice. */
    private fun closeOpen(outcome: String) {
        val u = uid ?: return
        if (running || paused) { val s = ranSeconds(); if (s > 0) onRestEnded?.invoke(RestRecord(u, startedWall, s, outcome)) }
        uid = null
    }
    fun start(seconds: Int = total, forLabel: String = "") {
        if (seconds <= 0) return
        closeOpen("cancelled"); total = seconds; endAt = clock() + seconds * 1000L; pausedLeft = -1L; running = true; label = forLabel
        uid = java.util.UUID.randomUUID().toString().take(12); startedWall = wall(); onChange?.invoke()
    }
    fun pause() { if (running) { pausedLeft = leftMs(); running = false; onChange?.invoke() } }
    fun resume() { if (paused) { endAt = clock() + pausedLeft; pausedLeft = -1L; running = true; onChange?.invoke() } }
    /** Skip / reset / cancel: back to idle at the current preset. [outcome] "skipped" (user) or "cancelled" (workout ended). */
    fun reset(outcome: String = "skipped") { closeOpen(outcome); running = false; pausedLeft = -1L; label = ""; onChange?.invoke() }
    fun select(seconds: Int) { if (running) start(seconds, label) else { total = seconds; reset() } }
    fun tick() {
        if (running && leftMs() <= 0L) {
            uid?.let { onRestEnded?.invoke(RestRecord(it, startedWall, total, "completed")) }; uid = null
            running = false; pausedLeft = 0L; onChange?.invoke()
        }
    }

    /** Persistable state. A running countdown is stored as a wall-clock end time, so after a restart it reflects real elapsed time. Fields: kind|total|value|label|uid|startedWall. */
    fun snapshot(wallNow: Long): String { val l = label.replace('|', ' ').replace('\n', ' '); val u = uid ?: ""
        return when { running -> "r|$total|${wallNow + leftMs()}|$l|$u|$startedWall"; paused -> "p|$total|$pausedLeft|$l|$u|$startedWall"; finished -> "f|$total|0|$l||0"; else -> "i|$total|0|||0" } }

    /**
     * Restores [snapshot]. Paused stays paused with the same remaining time. A running timer whose end time passed while the app was closed is
     * reported once as completed (its persisted end time proves the full rest elapsed) and shows as finished; a still-running one keeps its uid
     * so it is reported once, later. Never negative. Set [onRestEnded] BEFORE calling.
     */
    fun restore(snap: String?, wallNow: Long) {
        val p = snap?.split("|")
        val t = p?.getOrNull(1)?.toIntOrNull()
        val v = p?.getOrNull(2)?.toLongOrNull()
        if (p == null || p.size < 4 || t == null || t <= 0 || v == null) { running = false; pausedLeft = -1L; label = ""; uid = null; return }
        total = t; label = p[3]
        val u = p.getOrNull(4)?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString().take(12)   // pre-V6 snapshots carry no uid
        val sw = p.getOrNull(5)?.toLongOrNull() ?: wallNow
        when (p[0]) {
            "r" -> { val left = v - wallNow
                if (left > 0L) { endAt = clock() + left; pausedLeft = -1L; running = true; uid = u; startedWall = sw }
                else { running = false; pausedLeft = 0L; uid = null; onRestEnded?.invoke(RestRecord(u, sw, t, "completed")); onChange?.invoke() } }
            "p" -> { running = false; pausedLeft = v.coerceIn(1L, t * 1000L); uid = u; startedWall = sw }
            "f" -> { running = false; pausedLeft = 0L; uid = null }
            else -> { running = false; pausedLeft = -1L; label = ""; uid = null }
        }
    }

    companion object {
        /** The rest still open in a stored [snapshot] at [wallNow] (used when a workout is finished without the timer being in memory), or null. */
        fun closeFromSnapshot(snap: String?, wallNow: Long): RestRecord? {
            val p = snap?.split("|") ?: return null
            val t = p.getOrNull(1)?.toIntOrNull() ?: return null
            val v = p.getOrNull(2)?.toLongOrNull() ?: return null
            val u = p.getOrNull(4)?.takeIf { it.isNotBlank() } ?: return null
            val sw = p.getOrNull(5)?.toLongOrNull() ?: wallNow
            return when (p[0]) {
                "r" -> { val left = (v - wallNow).coerceAtLeast(0L); val ran = ((t * 1000L - left).coerceIn(0L, t * 1000L) / 1000L).toInt()
                    if (ran > 0) RestRecord(u, sw, ran, if (left == 0L) "completed" else "cancelled") else null }
                "p" -> { val ran = ((t * 1000L - v).coerceIn(0L, t * 1000L) / 1000L).toInt(); if (ran > 0) RestRecord(u, sw, ran, "cancelled") else null }
                else -> null
            }
        }
    }
}

/** A genuine weight PR. */
data class PrResult(val prevKg: Double, val prevReps: Int?, val newKg: Double, val newReps: Int?)

/** A PR only if heavier than the heaviest earlier working set. No earlier loaded set -> nothing to beat -> null. Equal is not a PR. */
fun detectPr(prior: List<Pair<Double, Int?>>, loadKg: Double?, reps: Int?): PrResult? {
    if (loadKg == null || loadKg <= 0.0) return null
    val best = prior.filter { it.first > 0.0 }.maxWithOrNull(compareBy({ it.first }, { it.second ?: 0 })) ?: return null
    return if (loadKg > best.first) PrResult(best.first, best.second, loadKg, reps) else null
}
