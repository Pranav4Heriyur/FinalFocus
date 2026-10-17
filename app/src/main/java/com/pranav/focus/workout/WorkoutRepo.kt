package com.pranav.focus.workout

import java.time.LocalDate
import java.util.UUID

/** All workout persistence. Pure Kotlin over [SqlRunner]; the app wires it to workout.db via WorkoutDb. */
class WorkoutRepo(private val db: SqlRunner, private val clock: () -> Long = { System.currentTimeMillis() }) {

    // ---------------------------------------------------------------- exercises
    private fun ex(r: Row) = Exercise(
        r.str("id"), r.str("name"), r.str("equipment"), r.str("primary_muscles"), r.str("secondary_muscles"), r.str("type"),
        r.int("default_sets"), r.str("default_reps"), r.int("default_rest_s"), r.flag("unilateral"), r.flag("is_custom"), r.flag("favourite"),
        r["last_used_ts"]?.let { (it as Number).toLong() },
        r.str("movement_pattern"), (r["bodyweight_factor"] as? Number)?.toDouble(), r.str("instructions"),
    )
    private val EX_SQL = "SELECT e.*, m.movement_pattern, m.bodyweight_factor FROM exercise e LEFT JOIN exercise_meta m ON m.exercise_id = e.id"

    fun exercise(id: String): Exercise? = db.query("$EX_SQL WHERE e.id = ?", resolveExerciseId(id)).firstOrNull()?.let(::ex)

    fun exercises(): List<Exercise> = db.query("$EX_SQL WHERE e.archived = 0 ORDER BY e.name COLLATE NOCASE").map(::ex)

    /** Maps any known id scheme (catalogue, legacy icon id, asset-manifest id) to the catalogue id. Unknown ids come back unchanged. */
    fun resolveExerciseId(anyId: String): String {
        if (db.count("SELECT COUNT(*) FROM exercise WHERE id = ?", anyId) > 0) return anyId
        return db.query("SELECT exercise_id FROM exercise_alias WHERE alias = ?", anyId).firstOrNull()?.str("exercise_id") ?: anyId
    }

    fun searchExercises(query: String, muscle: String? = null, equipment: String? = null, favouritesOnly: Boolean = false): List<Exercise> =
        exercises().filter { e ->
            WorkoutLogic.matchesQuery(e.name, e.equipment, e.primary, query) &&
                (muscle == null || WorkoutLogic.matchesMuscles(e.primary, e.name, setOf(muscle))) &&
                (equipment == null || WorkoutLogic.equipmentMatches(e.equipment, equipment)) &&
                (!favouritesOnly || e.favourite)
        }

    /** C5: multi-select muscle + equipment filters (OR within a filter row, AND between rows and with the text query). */
    fun searchExercises(query: String, muscles: Set<String>, equipments: Set<String> = emptySet(), favouritesOnly: Boolean = false): List<Exercise> =
        exercises().filter { e ->
            WorkoutLogic.matchesQuery(e.name, e.equipment, e.primary, query) &&
                WorkoutLogic.matchesMuscles(e.primary, e.name, muscles) &&
                (equipments.isEmpty() || equipments.any { WorkoutLogic.equipmentMatches(e.equipment, it) }) &&
                (!favouritesOnly || e.favourite)
        }

    /** Returns the new id, or throws IllegalArgumentException with a user-facing message. */
    fun createCustomExercise(
        name: String, equipment: String, muscles: String, type: String = "reps", sets: Int = 3, reps: String = "",
        restS: Int = 90, secondary: String = "", movementPattern: String = "", bodyweightFactor: Double? = null, unilateral: Boolean = false,
    ): String = db.tx {
        val n = name.trim().replace(Regex("\\s+"), " ")
        require(n.isNotEmpty()) { "Enter an exercise name." }
        require(n.length <= 60) { "Exercise name is too long (60 characters max)." }
        require(muscles.split(",").any { it.isNotBlank() }) { "Choose at least one muscle group." }
        val key = WorkoutLogic.normaliseName(n)
        require(key.isNotEmpty()) { "Exercise name must contain letters or numbers." }
        require(exercises().none { WorkoutLogic.normaliseName(it.name) == key }) { "An exercise named \"$n\" already exists." }
        require(bodyweightFactor == null || bodyweightFactor in 0.0..2.0) { "Bodyweight factor must be between 0 and 2." }
        val id = "custom_" + UUID.randomUUID().toString().take(8)
        db.exec(
            "INSERT INTO exercise(id, name, equipment, primary_muscles, secondary_muscles, type, default_sets, default_reps, default_rest_s, unilateral, is_custom) VALUES(?,?,?,?,?,?,?,?,?,?,1)",
            id, n, equipment.trim(), muscles.trim(), secondary.trim(), type, sets.coerceIn(1, 10), reps.ifBlank { WorkoutLogic.defaultRepsFor(type) }, restS.coerceIn(0, 600), if (unilateral) 1 else 0,
        )
        if (movementPattern.isNotBlank() || bodyweightFactor != null)
            db.exec("INSERT OR REPLACE INTO exercise_meta(exercise_id, movement_pattern, bodyweight_factor) VALUES(?,?,?)", id, movementPattern.trim(), bodyweightFactor)
        id
    }

    /**
     * C5: last logged working load per exercise in ONE query (no per-row lookups). Newest non-warm-up set with a load > 0;
     * if several sets share that timestamp the heaviest wins. Exercises with no history are simply absent from the map.
     */
    fun lastUsedLoads(): Map<String, Pair<Double, Int?>> {
        val out = HashMap<String, Pair<Double, Int?>>()
        db.query(
            "SELECT s.exercise_id, s.load_kg, s.reps FROM set_log s WHERE s.warmup = 0 AND s.load_kg > 0 AND s.completed_at = " +
                "(SELECT MAX(m.completed_at) FROM set_log m WHERE m.exercise_id = s.exercise_id AND m.warmup = 0 AND m.load_kg > 0)",
        ).forEach { r ->
            val id = resolveExerciseId(r.str("exercise_id")); val kg = r.dbl("load_kg")
            val reps = (r["reps"] as? Number)?.toInt()
            if (out[id]?.first?.let { it >= kg } != true) out[id] = kg to reps
        }
        return out
    }

    // ---------------------------------------------------------------- C8/9 active workout
    /**
     * Workout time starts when the user presses Play. Idempotent: the real start is stamped once (meta flag); a second call, a call after a set was
     * logged, or a call on a paused/finished workout changes nothing, so re-opening the screen can never reset the clock or drop a pause.
     */
    fun beginSession(id: String): Boolean = db.tx {
        if (meta("wt_begun:$id") != null || pausedAt(id) != null) return@tx false
        if (db.count("SELECT COUNT(*) FROM workout_session WHERE id = ? AND status = 'active' AND paused_ms = 0", id) == 0) return@tx false
        if (db.count("SELECT COUNT(*) FROM set_log WHERE session_id = ?", id) > 0) { setMeta("wt_begun:$id", clock().toString()); return@tx false }
        val now = clock()
        db.exec("UPDATE workout_session SET started_at = ? WHERE id = ? AND status = 'active'", now, id)
        setMeta("wt_begun:$id", now.toString())
        true
    }
    fun hasBegun(id: String): Boolean = meta("wt_begun:$id") != null

    /** Opens a pause. No-op (false) when already paused or the session is not active, so repeated taps never create a second/overlapping interval. */
    fun pauseSession(id: String): Boolean = db.tx {
        if (db.count("SELECT COUNT(*) FROM workout_session WHERE id = ? AND status = 'active'", id) == 0 || pausedAt(id) != null) return@tx false
        setPausedAt(id, clock()); true
    }

    /**
     * Closes the open pause and adds its duration exactly once (returns false when nothing was open). [monotonicMs] is the in-process monotonic
     * measurement when the caller has one; otherwise the persisted wall-clock start is used. Never negative, never longer than the workout so far.
     */
    fun resumeSession(id: String, monotonicMs: Long? = null): Boolean = db.tx {
        val p = pausedAt(id) ?: return@tx false
        val s = session(id)
        if (s == null || s.status != "active") { setPausedAt(id, null); return@tx false }
        closePause(s, clock(), p, monotonicMs); true
    }

    private fun closePause(s: Session, now: Long, pausedSince: Long, monotonicMs: Long? = null) {
        var d = (monotonicMs ?: (now - pausedSince)).coerceAtLeast(0L)
        if (s.timingV >= WorkoutTiming.CANONICAL) {
            d = d.coerceAtMost((now - s.startedAt - s.pausedMs).coerceAtLeast(0L))
            db.exec("UPDATE workout_session SET paused_ms = paused_ms + ? WHERE id = ? AND status = 'active'", d, s.id)
        } else {
            // timing_v 0/1 sessions that were already running keep their original (shifted-start) bookkeeping
            db.exec("UPDATE workout_session SET started_at = started_at + ?, paused_ms = paused_ms + ? WHERE id = ? AND status = 'active'", d, d, s.id)
        }
        setPausedAt(s.id, null)
    }

    /** Exercises added to a running session, in the order added (persisted). */
    fun sessionExtraLines(sessionId: String): List<Line> =
        db.query("SELECT exercise_id FROM session_exercise WHERE session_id = ? ORDER BY position, id", sessionId)
            .mapIndexedNotNull { i, r -> newLine(r.str("exercise_id"), -(i + 1).toLong()) }

    /** Adds an exercise to the session once; a second add of the same exercise is ignored (no duplicates). */
    fun addSessionExercise(sessionId: String, exerciseId: String) = db.tx {
        require(exercise(exerciseId) != null) { "Unknown exercise." }
        if (exerciseId in hiddenExercises(sessionId)) setMeta("hidden:$sessionId", (hiddenExercises(sessionId) - exerciseId).joinToString(",").ifEmpty { null })   // re-adding a removed planned exercise just un-hides it
        else if (db.count("SELECT COUNT(*) FROM session_exercise WHERE session_id = ? AND exercise_id = ?", sessionId, exerciseId) == 0)
            db.exec("INSERT INTO session_exercise(session_id, exercise_id, position) VALUES(?,?,?)", sessionId, exerciseId, db.count("SELECT COUNT(*) FROM session_exercise WHERE session_id = ?", sessionId))
        Unit
    }

    // ---- small per-session state kept in the existing meta table (no schema change) ----
    fun meta(key: String): String? = db.query("SELECT value FROM meta WHERE key = ?", key).firstOrNull()?.strOrNull("value")
    fun setMeta(key: String, value: String?) { if (value == null) db.exec("DELETE FROM meta WHERE key = ?", key) else db.exec("INSERT OR REPLACE INTO meta(key, value) VALUES(?,?)", key, value) }
    fun pausedAt(sessionId: String): Long? = meta("wt_pause:$sessionId")?.toLongOrNull()
    fun setPausedAt(sessionId: String, ms: Long?) = setMeta("wt_pause:$sessionId", ms?.toString())
    fun loadRest(sessionId: String): String? = meta("rest:$sessionId")
    fun saveRest(sessionId: String, snapshot: String?) = setMeta("rest:$sessionId", snapshot)
    fun hiddenExercises(sessionId: String): Set<String> = meta("hidden:$sessionId")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    fun clearSessionState(sessionId: String) { setMeta("wt_pause:$sessionId", null); setMeta("wt_begun:$sessionId", null); setMeta("rest:$sessionId", null); setMeta("hidden:$sessionId", null) }

    /**
     * Remove an exercise from THIS workout only. Refused when it has logged sets (recorded data is never deleted or orphaned).
     * An exercise added during the session is deleted from session_exercise; a planned one is hidden for this session (plans/routines untouched).
     */
    fun removeExerciseFromWorkout(sessionId: String, exerciseId: String, addedDuringSession: Boolean) = db.tx {
        val n = db.count("SELECT COUNT(*) FROM set_log WHERE session_id = ? AND exercise_id = ?", sessionId, exerciseId)
        require(n == 0) { "This exercise has $n logged ${if (n == 1) "set" else "sets"}. Delete those sets first to remove it." }
        if (addedDuringSession) db.exec("DELETE FROM session_exercise WHERE session_id = ? AND exercise_id = ?", sessionId, exerciseId)
        else setMeta("hidden:$sessionId", (hiddenExercises(sessionId) + exerciseId).joinToString(","))
    }

    /** True when [setId] belongs to a still-active session. Saved workouts are immutable: editing them would leave their PRs/snapshots stale. */
    private fun setIsEditable(setId: Long) = db.count("SELECT COUNT(*) FROM set_log s JOIN workout_session w ON w.id = s.session_id WHERE s.id = ? AND w.status = 'active'", setId) == 1

    fun deleteSet(id: Long) { require(setIsEditable(id)) { "Saved workouts can't be edited." }; db.exec("DELETE FROM set_log WHERE id = ?", id) }

    /** Edit a recorded set in place (no new row). Throws IllegalArgumentException with a user-facing message; nothing is written on failure. */
    fun updateSet(id: Long, loadKg: Double?, reps: Int?, durationS: Int?, distanceM: Double?, note: String) {
        require(loadKg == null || loadKg in 0.0..1000.0) { "Weight must be between 0 and 1000 kg." }
        require((reps ?: durationS ?: distanceM?.toInt() ?: 0) > 0) { "Enter a value greater than 0." }
        require(db.count("SELECT COUNT(*) FROM set_log WHERE id = ?", id) == 1) { "This set no longer exists." }
        require(setIsEditable(id)) { "Saved workouts can't be edited." }
        db.exec("UPDATE set_log SET load_kg = ?, reps = ?, duration_s = ?, distance_m = ?, note = ? WHERE id = ?", loadKg?.takeIf { it > 0 }, reps, durationS, distanceM, note.trim().take(200), id)
    }

    /** Every persisted working set (load > 0) of the exercise except [excludeSetId], as (kg, reps): the basis for PR detection. */
    fun prSetsFor(exerciseId: String, excludeSetId: Long = -1L): List<Pair<Double, Int?>> =
        db.query("SELECT load_kg, reps FROM set_log WHERE exercise_id = ? AND warmup = 0 AND load_kg > 0 AND reps > 0 AND id <> ?", exerciseId, excludeSetId)
            .map { it.dbl("load_kg") to (it["reps"] as? Number)?.toInt() }

    // ---- C11 statistics sources (read-only) ----
    /** Saved workouts only: status 'completed' with an end time. Abandoned/active sessions are excluded. */
    fun statsSessions(): List<Session> = db.query("SELECT * FROM workout_session WHERE status = 'completed' AND ended_at IS NOT NULL ORDER BY started_at").map(::session)

    /** Working (non-warm-up) sets of saved workouts, exercise ids resolved through aliases. */
    fun statsSets(): List<SetLog> = db.query(
        "SELECT s.* FROM set_log s JOIN workout_session w ON w.id = s.session_id WHERE w.status = 'completed' AND w.ended_at IS NOT NULL AND s.warmup = 0 ORDER BY s.completed_at, s.id",
    ).map(::setLog).map { it.copy(exerciseId = resolveExerciseId(it.exerciseId)) }

    /** (sessionId, exerciseId) -> primary muscles recorded in that workout's completion snapshot. Sessions without a snapshot are absent (C11 then falls back to current definitions, and says so). */
    fun snapshotPrimaries(): Map<Pair<String, String>, String> =
        db.query("SELECT session_id, exercise_id, primary_muscles FROM session_snapshot_ex").associate { (it.str("session_id") to it.str("exercise_id")) to it.str("primary_muscles") }

    fun snapshottedSessionIds(): Set<String> = db.query("SELECT id FROM workout_session WHERE snap_v >= 1 AND status = 'completed'").map { it.str("id") }.toSet()

    /** exercise id -> (primary_muscles, secondary_muscles), including archived and custom exercises. */
    fun exerciseMuscles(): Map<String, Pair<String, String>> =
        db.query("SELECT id, primary_muscles, secondary_muscles FROM exercise").associate { it.str("id") to (it.str("primary_muscles") to it.str("secondary_muscles")) }

    /** Sets of [exerciseId] grouped by workout, newest first (one query). */
    fun exerciseHistory(exerciseId: String, excludeSessionId: String?, maxSessions: Int = 20): List<HistorySession> {
        val rows = db.query("SELECT * FROM set_log WHERE exercise_id = ? AND warmup = 0 AND session_id <> ? ORDER BY completed_at DESC, id DESC LIMIT 300", exerciseId, excludeSessionId ?: "").map(::setLog)
        return rows.groupBy { it.sessionId }.values.take(maxSessions).map { l -> HistorySession(l.first().sessionId, l.maxOf { it.completedAt }, l.sortedBy { it.setNo }) }
    }

    fun toggleFavourite(id: String) = db.exec("UPDATE exercise SET favourite = 1 - favourite WHERE id = ?", id)

    // ---------------------------------------------------------------- routines
    private fun routine(r: Row) = Routine(
        r.str("id"), r.str("code"), r.str("name"), r.str("category"), r.str("focus"), r.str("primary_focus"), r.str("description"), r.str("difficulty"),
        r.int("minutes_low"), r.int("minutes_high"), r.str("label_hub"), r.str("label_plans"), r.str("accent"), r.str("kind"), r.flag("heavy_legs"),
        r.str("notes"), r.flag("is_preset"), r.int("n"),
    )

    private val ROUTINE_SQL = "SELECT r.*, (SELECT COUNT(*) FROM routine_exercise x WHERE x.routine_id = r.id) AS n FROM routine r"

    fun routines(category: String? = null): List<Routine> =
        db.query("$ROUTINE_SQL WHERE r.archived = 0 ${if (category != null) "AND r.category = ?" else ""} ORDER BY r.sort, r.name", *(if (category != null) arrayOf<Any?>(category) else emptyArray()))
            .map(::routine)

    fun routine(id: String?): Routine? = if (id == null) null else db.query("$ROUTINE_SQL WHERE r.id = ?", id).firstOrNull()?.let(::routine)

    private val LINE_COLS = "e.name AS ename, e.equipment AS eequip, e.primary_muscles AS eprim, e.type AS etype"

    private fun line(r: Row) = Line(
        r.long("id"), r.str("exercise_id"), r.str("ename"), r.str("eequip"), r.str("eprim"), r.str("etype"), r.int("sets"), r.str("rep_text"),
        r.int("rest_s"), r.str("rest_text"), r.flag("unilateral"), r.strOrNull("superset_group"), r.str("note"),
    )

    fun routineLines(routineId: String): List<Line> = db.query(
        "SELECT x.*, $LINE_COLS FROM routine_exercise x JOIN exercise e ON e.id = x.exercise_id WHERE x.routine_id = ? ORDER BY x.position, x.id", routineId,
    ).map(::line)

    /** Estimated minutes for a routine's lines; morning/circuit routines fall back to their stored range midpoint. */
    fun estimate(r: Routine, lines: List<Line>): Int =
        if (r.isGym && lines.isNotEmpty()) WorkoutLogic.estimateMinutes(lines.map { WorkoutLogic.Est(it.sets, it.restS, it.type) })
        else (r.minLow + r.minHigh) / 2

    // ---------------------------------------------------------------- plans
    private fun plan(r: Row) = Plan(
        r.str("id"), r.str("name"), r.str("description"), r.str("plan_type"), r.str("tags").split(",").map { it.trim() }.filter { it.isNotEmpty() },
        r.str("template_id"), r.flag("is_active"), r.flag("is_preset"), r.int("slot1_weekday"), r.long("created_at"), r.long("updated_at"),
    )

    fun plans(): List<Plan> = db.query("SELECT * FROM plan WHERE archived = 0 ORDER BY is_active DESC, created_at, name").map(::plan)
    fun plan(id: String): Plan? = db.query("SELECT * FROM plan WHERE id = ?", id).firstOrNull()?.let(::plan)
    fun activePlan(): Plan? = db.query("SELECT * FROM plan WHERE is_active = 1 AND archived = 0 LIMIT 1").firstOrNull()?.let(::plan)

    fun setActivePlan(id: String) = db.tx {
        db.exec("UPDATE plan SET is_active = 0")
        db.exec("UPDATE plan SET is_active = 1, updated_at = ? WHERE id = ?", clock(), id)
    }

    fun planRoutines(planId: String): List<Routine> = db.query(
        "SELECT r.*, (SELECT COUNT(*) FROM routine_exercise x WHERE x.routine_id = r.id) AS n FROM routine r JOIN plan_routine p ON p.routine_id = r.id WHERE p.plan_id = ? ORDER BY p.position", planId,
    ).map(::routine)

    /**
     * Creates a plan. From a template (only templates with supplied content are available) it copies the template's routines and
     * 7-day schedule; blank plans start with 7 empty days. Throws IllegalArgumentException with a user-facing message on invalid input.
     */
    fun createPlan(name: String, description: String, planType: String, tags: List<String>, templateId: String?, makeActive: Boolean = false): String = db.tx {
        WorkoutLogic.validatePlanName(name, plans().map { it.name })?.let { throw IllegalArgumentException(it) }
        require(description.length <= WorkoutLogic.DESC_MAX) { "Description is too long (max ${WorkoutLogic.DESC_MAX})." }
        val tpl = templateId?.let { id -> WorkoutCatalog.TEMPLATES.firstOrNull { it.id == id } }
        require(tpl == null || tpl.available) { "That template has no routines yet." }
        val id = "plan_" + UUID.randomUUID().toString().take(8)
        val now = clock()
        db.exec(
            "INSERT INTO plan(id, name, description, plan_type, tags, template_id, is_active, is_preset, slot1_weekday, created_at, updated_at) VALUES(?,?,?,?,?,?,0,0,1,?,?)",
            id, name.trim(), description.trim(), planType, tags.joinToString(","), tpl?.id ?: "", now, now,
        )
        for (slot in 1..7) {
            val d = tpl?.days?.getOrNull(slot - 1)
            db.exec("INSERT INTO plan_day(plan_id, slot, morning_routine_id, alt_morning_routine_id, gym_routine_id) VALUES(?,?,?,?,?)", id, slot, d?.morning, d?.altMorning, d?.gym)
        }
        tpl?.routineIds?.forEachIndexed { i, rid -> db.exec("INSERT OR IGNORE INTO plan_routine(plan_id, routine_id, position) VALUES(?,?,?)", id, rid, i) }
        check(scheduleWarnings(id).isEmpty()) { "Template schedule breaks the no run/ruck before G1/G3 rule." }
        if (makeActive || plans().size == 1) setActivePlan(id)
        id
    }

    fun duplicatePlan(sourceId: String, newName: String): String = db.tx {
        val src = plan(sourceId) ?: throw IllegalArgumentException("Plan not found.")
        val id = createPlan(newName, src.description, src.planType, src.tags, null)
        db.exec("UPDATE plan SET template_id = ?, slot1_weekday = ? WHERE id = ?", src.templateId, src.slot1Weekday, id)
        db.exec("DELETE FROM plan_day WHERE plan_id = ?", id)
        db.exec("INSERT INTO plan_day(plan_id, slot, morning_routine_id, alt_morning_routine_id, gym_routine_id, notes, customized) SELECT ?, slot, morning_routine_id, alt_morning_routine_id, gym_routine_id, notes, customized FROM plan_day WHERE plan_id = ?", id, sourceId)
        db.exec("INSERT INTO plan_day_exercise(plan_id, slot, exercise_id, position, sets, rep_text, rep_min, rep_max, rest_s, rest_text, unilateral, superset_group, note) SELECT ?, slot, exercise_id, position, sets, rep_text, rep_min, rep_max, rest_s, rest_text, unilateral, superset_group, note FROM plan_day_exercise WHERE plan_id = ?", id, sourceId)
        db.exec("INSERT INTO plan_routine(plan_id, routine_id, position) SELECT ?, routine_id, position FROM plan_routine WHERE plan_id = ?", id, sourceId)
        id
    }

    /** Presets cannot be deleted (duplicate them instead). Workout history is never touched. */
    fun deletePlan(id: String): Boolean = db.tx {
        val p = plan(id) ?: return@tx false
        if (p.preset) return@tx false
        for (t in listOf("plan_day", "plan_day_exercise", "plan_routine")) db.exec("DELETE FROM $t WHERE plan_id = ?", id)
        db.exec("DELETE FROM plan WHERE id = ?", id)
        if (p.active) db.query("SELECT id FROM plan WHERE archived = 0 ORDER BY created_at LIMIT 1").firstOrNull()?.let { setActivePlan(it.str("id")) }
        true
    }

    fun setSlot1Weekday(planId: String, weekday: Int) = db.exec("UPDATE plan SET slot1_weekday = ?, updated_at = ? WHERE id = ?", weekday.coerceIn(1, 7), clock(), planId)

    // ---------------------------------------------------------------- days
    private fun day(r: Row) = PlanDay(r.str("plan_id"), r.int("slot"), r.strOrNull("morning_routine_id"), r.strOrNull("alt_morning_routine_id"), r.strOrNull("gym_routine_id"), r.str("notes"), r.flag("customized"))

    fun days(planId: String): List<PlanDay> = db.query("SELECT * FROM plan_day WHERE plan_id = ? ORDER BY slot", planId).map(::day)
    fun day(planId: String, slot: Int): PlanDay? = db.query("SELECT * FROM plan_day WHERE plan_id = ? AND slot = ?", planId, slot).firstOrNull()?.let(::day)

    /** The routine whose exercise list the Day Builder shows: the gym routine, else the morning routine. */
    fun baseRoutineId(d: PlanDay): String? = d.gym ?: d.morning

    /** Day's effective exercise list: the per-day override when the user customised it, otherwise the routine's own lines. */
    fun dayLines(planId: String, slot: Int): List<Line> {
        val d = day(planId, slot) ?: return emptyList()
        if (d.customized) return db.query(
            "SELECT x.*, $LINE_COLS FROM plan_day_exercise x JOIN exercise e ON e.id = x.exercise_id WHERE x.plan_id = ? AND x.slot = ? ORDER BY x.position, x.id", planId, slot,
        ).map(::line)
        return baseRoutineId(d)?.let(::routineLines) ?: emptyList()
    }

    private fun sameShape(a: List<Line>, b: List<Line>) = a.size == b.size && a.zip(b).all { (x, y) ->
        x.exerciseId == y.exerciseId && x.sets == y.sets && x.repText == y.repText && x.restS == y.restS && x.restText == y.restText && x.superset == y.superset && x.note == y.note
    }

    private fun touchPlanRoutine(planId: String, routineId: String?) {
        if (routineId == null) return
        val pos = db.count("SELECT COUNT(*) FROM plan_routine WHERE plan_id = ?", planId)
        db.exec("INSERT OR IGNORE INTO plan_routine(plan_id, routine_id, position) VALUES(?,?,?)", planId, routineId, pos)
    }

    /** Reassigns the day's routines. Dropping a routine's customisation is deliberate: the old per-day list belonged to the old routine. */
    fun assignDay(planId: String, slot: Int, morning: String?, altMorning: String?, gym: String?) = db.tx {
        requireScheduleRule(planId, slot, morning, altMorning, gym)
        val old = day(planId, slot)
        val changed = old == null || old.morning != morning || old.gym != gym
        db.exec("UPDATE plan_day SET morning_routine_id = ?, alt_morning_routine_id = ?, gym_routine_id = ? WHERE plan_id = ? AND slot = ?", morning, altMorning, gym, planId, slot)
        if (changed) { db.exec("UPDATE plan_day SET customized = 0 WHERE plan_id = ? AND slot = ?", planId, slot); db.exec("DELETE FROM plan_day_exercise WHERE plan_id = ? AND slot = ?", planId, slot) }
        touchPlanRoutine(planId, morning); touchPlanRoutine(planId, altMorning); touchPlanRoutine(planId, gym)
        db.exec("UPDATE plan SET updated_at = ? WHERE id = ?", clock(), planId)
    }

    /**
     * Saves the Day Builder. Routine assignment, notes and exercise order/prescriptions are written atomically. If [lines] equal the base routine's
     * lines the day stays un-customised (no override rows); otherwise a copy-on-write override is stored and the preset routine is never modified.
     */
    fun saveDay(planId: String, slot: Int, morning: String?, altMorning: String?, gym: String?, notes: String, lines: List<Line>) = db.tx {
        require(notes.length <= WorkoutLogic.NOTES_MAX) { "Notes are too long (max ${WorkoutLogic.NOTES_MAX})." }
        assignDay(planId, slot, morning, altMorning, gym)
        db.exec("UPDATE plan_day SET notes = ? WHERE plan_id = ? AND slot = ?", notes, planId, slot)
        val base = (gym ?: morning)?.let(::routineLines) ?: emptyList()
        db.exec("DELETE FROM plan_day_exercise WHERE plan_id = ? AND slot = ?", planId, slot)
        if (sameShape(lines, base)) { db.exec("UPDATE plan_day SET customized = 0 WHERE plan_id = ? AND slot = ?", planId, slot); return@tx }
        lines.forEachIndexed { i, l ->
            val p = WorkoutLogic.parseReps(l.repText)
            db.exec(
                "INSERT INTO plan_day_exercise(plan_id, slot, exercise_id, position, sets, rep_text, rep_min, rep_max, rest_s, rest_text, unilateral, superset_group, note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                planId, slot, l.exerciseId, i, l.sets, l.repText, p.min, p.max, l.restS, l.restText, if (l.unilateral) 1 else 0, l.superset, l.note,
            )
        }
        db.exec("UPDATE plan_day SET customized = 1 WHERE plan_id = ? AND slot = ?", planId, slot)
    }

    fun copyDay(planId: String, from: Int, to: Int) = db.tx {
        require(from != to) { "Pick a different day." }
        val src = day(planId, from) ?: return@tx
        val lines = dayLines(planId, from)
        saveDay(planId, to, src.morning, src.altMorning, src.gym, src.notes, lines)
    }

    /** A new line for [exerciseId] using the catalogue defaults. */
    fun newLine(exerciseId: String, uid: Long): Line? = exercise(exerciseId)?.let { e ->
        Line(uid, e.id, e.name, e.equipment, e.primary, e.type, e.sets, e.reps, e.restS, WorkoutLogic.restLabel(e.restS), e.unilateral, null)
    }

    private fun slotInfos(planId: String, override: PlanDay? = null): List<WorkoutLogic.SlotInfo> =
        days(planId).map { if (override != null && it.slot == override.slot) override else it }.map { d ->
            val m = routine(d.morning); val alt = routine(d.altMorning); val g = routine(d.gym)
            WorkoutLogic.SlotInfo(d.slot, m?.kind, g?.heavyLegs == true, m?.code, g?.code, alt?.kind, alt?.code)
        }

    fun scheduleWarnings(planId: String): List<WorkoutLogic.ScheduleWarning> = WorkoutLogic.scheduleViolations(slotInfos(planId))

    /** Throws IllegalArgumentException (user-facing) if assigning these routines to [slot] would put a run/ruck before G1/G3. */
    private fun requireScheduleRule(planId: String, slot: Int, morning: String?, altMorning: String?, gym: String?) {
        val cur = day(planId, slot) ?: return
        val bad = WorkoutLogic.newViolations(slotInfos(planId), slotInfos(planId, cur.copy(morning = morning, altMorning = altMorning, gym = gym)))
        if (bad.isNotEmpty()) throw IllegalArgumentException(bad.joinToString(" ") { it.message } + " No run or ruck before G1 or G3.")
    }

    // ---------------------------------------------------------------- sessions / logging
    private fun session(r: Row) = Session(
        r.str("id"), r.strOrNull("plan_id"), r["slot"]?.let { (it as Number).toInt() }, r.strOrNull("routine_id"), r.str("title"), r.str("activity"), r.str("status"), r.long("started_at"),
        r["ended_at"]?.let { (it as Number).toLong() }, r.long("paused_ms"), r.long("rest_ms"), r.int("timing_v"), r.int("snap_v"), r.strOrNull("routine_name"), r.strOrNull("category_label"),
    )

    fun startSession(planId: String?, slot: Int?, routineId: String?, title: String, activity: String = "gym"): String {
        val id = "s_" + UUID.randomUUID().toString().take(10)
        db.exec("INSERT INTO workout_session(id, plan_id, slot, routine_id, title, activity, status, started_at, timing_v) VALUES(?,?,?,?,?,?,'active',?,${WorkoutTiming.CANONICAL})", id, planId, slot, routineId, title, activity, clock())
        return id
    }

    fun activeSession(): Session? = db.query("SELECT * FROM workout_session WHERE status = 'active' ORDER BY started_at DESC LIMIT 1").firstOrNull()?.let(::session)
    fun session(id: String): Session? = db.query("SELECT * FROM workout_session WHERE id = ?", id).firstOrNull()?.let(::session)

    /**
     * Ends an ACTIVE session atomically and returns true; returns false (changing nothing) if it is unknown or already finished, so a duplicate
     * call can never save twice. A workout finished while paused has the open pause closed first. Completing also writes the historical snapshot
     * (exercise names/muscles/order, category, verified PRs) and the tracked rest total, in the same transaction.
     */
    fun finishSession(id: String, completed: Boolean = true): Boolean = db.tx {
        val s = session(id) ?: return@tx false
        if (s.status != "active") return@tx false
        val now = clock()
        pausedAt(id)?.let { p -> closePause(s, now, p) }                                  // finishing while paused closes the pause exactly once
        RestTimerState.closeFromSnapshot(loadRest(id), now)?.let { r -> recordRest(id, r.seconds, r.outcome, r.uid, r.startedAt) }   // a rest still open in storage ends now (INSERT OR IGNORE: never twice)
        val restMs = db.query("SELECT COALESCE(SUM(seconds), 0) AS t FROM rest_log WHERE session_id = ?", id).firstOrNull()?.long("t")?.times(1000L) ?: 0L
        db.exec("UPDATE workout_session SET status = ?, ended_at = ?, rest_ms = ? WHERE id = ? AND status = 'active'", if (completed) "completed" else "abandoned", now, restMs, id)
        if (completed) snapshotSession(id)
        true
    }

    /**
     * Rest actually taken with the rest timer (seconds), one row per tracked rest. [uid] identifies the rest: recording the same uid again is ignored
     * (a restored/reopened timer cannot count twice). [outcome] = completed | skipped | cancelled (null for rests saved before outcomes existed).
     * Ignored unless the session is still active.
     */
    fun recordRest(sessionId: String, seconds: Int, outcome: String? = null, uid: String? = null, startedAt: Long? = null) {
        if (seconds !in 1..3600) return
        if (db.count("SELECT COUNT(*) FROM workout_session WHERE id = ? AND status = 'active'", sessionId) == 0) return
        db.exec("INSERT OR IGNORE INTO rest_log(session_id, seconds, ts, rest_uid, started_at, outcome) VALUES(?,?,?,?,?,?)", sessionId, seconds, clock(), uid, startedAt, outcome)
    }

    // ---- completion snapshot (V5) ----
    private fun categoryLabelFor(s: Session): String {
        val r = routine(s.routineId)
        return listOfNotNull(r?.category?.replaceFirstChar { it.uppercase() }, r?.focus?.takeIf { it.isNotBlank() }).joinToString(" • ")
            .ifBlank { s.activity.replaceFirstChar { it.uppercase() } }
    }

    /** Working sets of other SAVED sessions that started before [startedAt] (the only sets a PR may be compared against). */
    fun priorWorkingSets(exerciseId: String, sessionId: String, startedAt: Long): List<Pair<Double, Int?>> = db.query(
        "SELECT s.load_kg, s.reps FROM set_log s JOIN workout_session w ON w.id = s.session_id WHERE (s.exercise_id = ? OR s.exercise_id IN (SELECT alias FROM exercise_alias WHERE exercise_id = ?))" +
            " AND s.warmup = 0 AND s.load_kg > 0 AND s.reps > 0 AND w.status = 'completed' AND w.ended_at IS NOT NULL AND w.id <> ? AND w.started_at < ?",
        exerciseId, exerciseId, sessionId, startedAt,
    ).map { it.dbl("load_kg") to (it["reps"] as? Number)?.toInt() }

    private fun computePrs(s: Session, sets: List<SetLog>): List<CompletedPr> {
        if (s.activity != "gym") return emptyList()
        return sets.filter { !it.warmup }.groupBy { resolveExerciseId(it.exerciseId) }.mapNotNull { (exId, l) ->
            val best = l.filter { (it.loadKg ?: 0.0) > 0.0 && (it.reps ?: 0) > 0 }.maxWithOrNull(compareBy({ it.loadKg ?: 0.0 }, { it.reps ?: 0 })) ?: return@mapNotNull null
            val r = detectPr(priorWorkingSets(exId, s.id, s.startedAt), best.loadKg, best.reps) ?: return@mapNotNull null
            CompletedPr(exId, best.exerciseName, exercise(exId)?.primary.orEmpty(), r.newKg, r.newReps, r.prevKg, r.prevReps)
        }.sortedByDescending { it.deltaKg }
    }

    /** Writes (or rewrites) the snapshot of a session from the data as it is right now. Called once, inside [finishSession]. */
    private fun snapshotSession(id: String) = db.tx {
        val s = session(id) ?: return@tx
        val sets = sessionSets(id)
        val first = LinkedHashMap<String, SetLog>()
        sets.forEach { first.putIfAbsent(resolveExerciseId(it.exerciseId), it) }
        db.exec("DELETE FROM session_snapshot_ex WHERE session_id = ?", id)
        db.exec("DELETE FROM session_pr WHERE session_id = ?", id)
        first.entries.forEachIndexed { i, (exId, set) ->
            val e = exercise(exId)
            db.exec(
                "INSERT INTO session_snapshot_ex(session_id, exercise_id, position, name, equipment, primary_muscles, secondary_muscles, type) VALUES(?,?,?,?,?,?,?,?)",
                id, exId, i, set.exerciseName.ifBlank { e?.name ?: exId }, e?.equipment.orEmpty(), e?.primary.orEmpty(), e?.secondary.orEmpty(), e?.type.orEmpty(),
            )
        }
        computePrs(s, sets).forEach { p ->
            db.exec("INSERT INTO session_pr(session_id, exercise_id, name, muscle, new_kg, new_reps, prev_kg, prev_reps) VALUES(?,?,?,?,?,?,?,?)", id, p.exerciseId, p.name, p.muscle, p.newKg, p.newReps, p.prevKg, p.prevReps)
        }
        db.exec("UPDATE workout_session SET snap_v = 1, routine_name = ?, category_label = ? WHERE id = ?", routine(s.routineId)?.name, categoryLabelFor(s), id)
    }

    fun snapshotExercises(sessionId: String): List<SnapEx> = db.query("SELECT * FROM session_snapshot_ex WHERE session_id = ? ORDER BY position, id", sessionId)
        .map { SnapEx(it.str("exercise_id"), it.int("position"), it.str("name"), it.str("equipment"), it.str("primary_muscles"), it.str("secondary_muscles"), it.str("type")) }

    fun storedPrs(sessionId: String): List<CompletedPr> = db.query("SELECT * FROM session_pr WHERE session_id = ? ORDER BY (new_kg - prev_kg) DESC, id", sessionId)
        .map { CompletedPr(it.str("exercise_id"), it.str("name"), it.str("muscle"), it.dbl("new_kg"), (it["new_reps"] as? Number)?.toInt(), it.dbl("prev_kg"), (it["prev_reps"] as? Number)?.toInt()) }

    fun logSet(sessionId: String, exerciseId: String, setNo: Int, loadKg: Double?, reps: Int?, rpe: Int?, note: String = "", durationS: Int? = null, distanceM: Double? = null, warmup: Boolean = false): Long = db.tx {
        require(db.count("SELECT COUNT(*) FROM workout_session WHERE id = ? AND status = 'active'", sessionId) == 1) { "This workout is no longer active." }
        // a double tap on "Log Set" must not store the same set twice (that would double volume and could fake a PR)
        db.query("SELECT id FROM set_log WHERE session_id = ? AND exercise_id = ? AND set_no = ? AND warmup = ?", sessionId, exerciseId, setNo, if (warmup) 1 else 0).firstOrNull()?.let { return@tx it.long("id") }
        val name = exercise(exerciseId)?.name ?: exerciseId
        val now = clock()
        db.exec(
            "INSERT INTO set_log(session_id, exercise_id, exercise_name, set_no, load_kg, reps, rpe, duration_s, distance_m, warmup, note, completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            sessionId, exerciseId, name, setNo, loadKg, reps, rpe, durationS, distanceM, if (warmup) 1 else 0, note, now,
        )
        val id = db.lastInsertId()
        db.exec("UPDATE exercise SET last_used_ts = ? WHERE id = ?", now, exerciseId)
        id
    }

    private fun setLog(r: Row) = SetLog(
        r.long("id"), r.str("session_id"), r.str("exercise_id"), r.str("exercise_name"), r.int("set_no"), r["load_kg"]?.let { (it as Number).toDouble() },
        r["reps"]?.let { (it as Number).toInt() }, r["rpe"]?.let { (it as Number).toInt() }, r["duration_s"]?.let { (it as Number).toInt() },
        r["distance_m"]?.let { (it as Number).toDouble() }, r.str("note"), r.flag("warmup"), r.long("completed_at"),
    )

    fun sessionSets(sessionId: String): List<SetLog> = db.query("SELECT * FROM set_log WHERE session_id = ? ORDER BY completed_at, id", sessionId).map(::setLog)

    /** Sets from the most recent *other* session that logged [exerciseId]. */
    fun previousSets(exerciseId: String, excludeSessionId: String?): List<SetLog> {
        val last = db.query(
            "SELECT session_id FROM set_log WHERE exercise_id = ? AND warmup = 0 AND session_id <> ? ORDER BY completed_at DESC LIMIT 1", exerciseId, excludeSessionId ?: "",
        ).firstOrNull()?.str("session_id") ?: return emptyList()
        return db.query("SELECT * FROM set_log WHERE session_id = ? AND exercise_id = ? AND warmup = 0 ORDER BY set_no, id", last, exerciseId).map(::setLog)
    }

    /** Run/ruck logged without a set-by-set flow: one completed session + one set_log row carrying distance/duration (+ pack kg as load). */
    fun logCardio(activity: String, distanceKm: Double, minutes: Int, effort: Int?, packKg: Double? = null): String = db.tx {
        require(activity == "run" || activity == "ruck") { "Unknown activity." }
        require(minutes in 1..600) { "Enter the duration in minutes." }
        require(distanceKm in 0.0..200.0) { "Distance looks wrong." }
        val now = clock()
        val id = "s_" + UUID.randomUUID().toString().take(10)
        val title = if (activity == "run") "Easy Run" else "Ruck Walk"
        db.exec("INSERT INTO workout_session(id, plan_id, slot, routine_id, title, activity, status, started_at, ended_at) VALUES(?,NULL,NULL,?,?,?,'completed',?,?)", id, if (activity == "run") "m3" else "m4", title, activity, now - minutes * 60_000L, now)
        db.exec(
            "INSERT INTO set_log(session_id, exercise_id, exercise_name, set_no, load_kg, rpe, duration_s, distance_m, completed_at) VALUES(?,?,?,?,?,?,?,?,?)",
            id, if (activity == "run") "easy_run" else "ruck_walk", title, 1, packKg, effort, minutes * 60, distanceKm * 1000.0, now,
        )
        id
    }

    fun logBodyWeight(date: LocalDate, kg: Double) {
        require(kg in 20.0..400.0) { "Enter a weight between 20 and 400 kg." }
        db.exec("INSERT OR REPLACE INTO body_weight_log(date, kg, ts) VALUES(?,?,?)", date.toString(), kg, clock())
    }

    /** Number of completed in-app workouts whose start is in [fromMs, toMs). Used by Time Bank and XP. */
    fun completedCountBetween(fromMs: Long, toMs: Long): Int =
        db.query("SELECT COUNT(*) AS n FROM workout_session WHERE status = 'completed' AND ended_at IS NOT NULL AND started_at >= ? AND started_at < ?", fromMs, toMs)
            .firstOrNull()?.long("n")?.toInt() ?: 0

    fun latestBodyWeight(): Pair<String, Double>? = db.query("SELECT date, kg FROM body_weight_log ORDER BY date DESC LIMIT 1").firstOrNull()?.let { it.str("date") to it.dbl("kg") }

    /** Completed sessions with started_at in [fromMs, toMs). Volume = sum(load x reps) over non-warm-up sets. */
    fun weekStats(fromMs: Long, toMs: Long): WeekStats {
        val sess = db.query("SELECT activity, started_at, ended_at, paused_ms, rest_ms, timing_v FROM workout_session WHERE status = 'completed' AND ended_at IS NOT NULL AND started_at >= ? AND started_at < ?", fromMs, toMs)
        val vol = db.query(
            "SELECT COALESCE(SUM(s.load_kg * s.reps), 0) AS v FROM set_log s JOIN workout_session w ON w.id = s.session_id" +
                " WHERE w.status = 'completed' AND w.started_at >= ? AND w.started_at < ? AND s.warmup = 0 AND s.load_kg IS NOT NULL AND s.reps IS NOT NULL", fromMs, toMs,
        ).firstOrNull()?.dbl("v") ?: 0.0
        // Hub "Minutes" = ELAPSED minutes, the same definition as C10/C11/C12 (WorkoutTiming)
        val minutes = sess.sumOf { (WorkoutTiming.of(it.long("started_at"), it.long("ended_at"), it.long("paused_ms"), it.long("rest_ms"), it.int("timing_v")).elapsedMs ?: 0L) / 60_000L }.toInt()
        return WeekStats(sess.count { it.str("activity") == "gym" }, sess.count { it.str("activity") == "run" || it.str("activity") == "ruck" }, minutes, vol)
    }
}
