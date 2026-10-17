package com.pranav.focus

import com.pranav.focus.workout.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutDbTest {
    private var now = 1_760_000_000_000L
    private fun fresh(): Pair<JdbcRunner, WorkoutRepo> {
        val db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, now)
        return db to WorkoutRepo(db) { now++ }
    }

    // ---- seeding ----
    @Test fun seedCreatesCatalogueRoutinesAndPresetPlan() {
        val (_, repo) = fresh()
        assertEquals(WorkoutCatalog.EXERCISES.size, repo.exercises().size)
        assertEquals(4, repo.routines("gym").size); assertEquals(5, repo.routines("morning").size)
        assertEquals(listOf(6, 7, 7, 8), repo.routines("gym").map { it.exerciseCount })
        val plan = repo.activePlan()!!
        assertEquals(WorkoutCatalog.PRESET_PLAN_ID, plan.id); assertTrue(plan.preset)
        assertEquals(7, repo.days(plan.id).size)
        assertEquals(9, repo.planRoutines(plan.id).size)
    }

    @Test fun seedIsIdempotent() {
        val (db, repo) = fresh()
        fun counts() = listOf("exercise", "exercise_alias", "routine", "routine_exercise", "plan", "plan_day", "plan_routine").map { db.count("SELECT COUNT(*) FROM $it") }
        val before = counts()
        repeat(3) { WorkoutSeeder.seed(db, now) }
        assertEquals(before, counts())
        // even with the version marker gone (e.g. a partial earlier run) a re-seed must not duplicate
        db.exec("DELETE FROM meta"); WorkoutSeeder.seed(db, now)
        assertEquals(before, counts())
        assertEquals(1, repo.plans().size)
    }

    @Test fun reseedDoesNotOverwriteUserEdits() {
        val (db, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        repo.assignDay(pid, 3, "m5", null, null)
        db.exec("UPDATE plan SET name = 'My split' WHERE id = ?", pid)
        db.exec("DELETE FROM meta"); WorkoutSeeder.seed(db, now)
        assertEquals("My split", repo.plan(pid)!!.name)
        assertEquals("m5", repo.day(pid, 3)!!.morning)
    }

    @Test fun routinesMatchSuppliedPrescriptions() {
        val (_, repo) = fresh()
        val g1 = repo.routineLines("g1")
        assertEquals(listOf("4 × 8–10 reps", "3 × 8–10 reps", "3 × 8–10 reps", "3 × 10–12 reps", "3 × 12–15 reps", "3 × 30–40 m"), g1.map { it.prescription })
        assertEquals(listOf("2–3 min", "2 min", "2 min", "90 sec", "60 sec", "90 sec"), g1.map { it.rest })
        val g2 = repo.routineLines("g2")
        assertEquals(listOf("A", "A"), g2.mapNotNull { it.superset })
        assertEquals(0, g2.first { it.exerciseId == "lateral_raise" }.restS)
        val g4 = repo.routineLines("g4")
        assertEquals(listOf("B", "B", "C", "C"), g4.mapNotNull { it.superset })
        assertEquals("2 × Max time", g4.last().prescription)
        assertEquals("30 m per side", repo.routineLines("g3")[5].repText)
    }

    @Test fun everySeedLineReferencesACatalogueExercise() {
        val ids = WorkoutCatalog.EXERCISES.map { it.id }.toSet()
        assertEquals(WorkoutCatalog.EXERCISES.size, ids.size)
        WorkoutCatalog.ROUTINES.flatMap { it.lines }.forEach { assertTrue(it.exerciseId, it.exerciseId in ids) }
        WorkoutCatalog.ILLUSTRATION_KEY.keys.forEach { assertTrue(it, it in ids) }
    }

    // ---- id mapping ----
    @Test fun legacyAndManifestIdsMapExplicitly() {
        val (_, repo) = fresh()
        assertEquals("leg_press", repo.resolveExerciseId("g1-leg-press"))
        assertEquals("face_pull", repo.resolveExerciseId("g4-face-pulls"))
        assertEquals("face_pull", repo.resolveExerciseId("face_pulls"))
        assertEquals("farmers_carry", repo.resolveExerciseId("farmer_s_carry"))
        assertEquals("leg_press", repo.resolveExerciseId("leg_press"))
        assertEquals("unknown-x", repo.resolveExerciseId("unknown-x"))
        assertEquals("Leg Press", repo.exercise("g1-leg-press")!!.name)
    }

    @Test fun legacyMapIsCompleteAndOneToOne() {
        val ids = WorkoutCatalog.EXERCISES.map { it.id }.toSet()
        // C5 added exercises (no legacy icon ids); the legacy map must still cover every original entry one-to-one
        val c5 = setOf("preacher_curl", "incline_dumbbell_curl", "skull_crusher", "close_grip_push_up", "front_raise", "dumbbell_shoulder_press",
            "cable_lateral_raise", "upright_row", "reverse_pec_deck", "bent_over_rear_delt_fly", "wrist_curl", "reverse_curl")
        assertEquals(WorkoutCatalog.EXERCISES.size - c5.size, WorkoutCatalog.LEGACY_TO_CATALOG.size)
        assertEquals(ids - c5, WorkoutCatalog.LEGACY_TO_CATALOG.values.toSet())
        assertTrue(WorkoutCatalog.MANIFEST_TO_CATALOG.values.all { it in ids })
    }

    // ---- plans / days / persistence ----
    @Test fun createFromTemplateCopiesScheduleAndRejectsBadNames() {
        val (_, repo) = fresh()
        val id = repo.createPlan("Summer cut", "desc", "hybrid", listOf("Strength"), "strength_conditioning_4day")
        assertEquals("g1", repo.day(id, 1)!!.gym); assertEquals("m4", repo.day(id, 6)!!.altMorning)
        assertEquals(9, repo.planRoutines(id).size)
        assertFalse(repo.plan(id)!!.preset)
        assertThrows { repo.createPlan("  ", "", "gym", emptyList(), null) }
        assertThrows { repo.createPlan("summer CUT", "", "gym", emptyList(), null) }          // duplicate, case-insensitive
        assertThrows { repo.createPlan("Push", "", "gym", emptyList(), "push_pull_legs") }    // template has no supplied content
    }

    @Test fun blankPlanStartsEmptyAndTracksAssignedRoutines() {
        val (_, repo) = fresh()
        val id = repo.createPlan("Blank", "", "gym", emptyList(), null)
        assertTrue(repo.days(id).all { it.gym == null && it.morning == null })
        repo.assignDay(id, 2, "m1", null, "g2")
        assertEquals(setOf("m1", "g2"), repo.planRoutines(id).map { it.id }.toSet())
    }

    @Test fun dayEditsAreCopyOnWriteAndPresetRoutineStaysIntact() {
        val (_, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        val before = repo.dayLines(pid, 1)
        assertEquals(6, before.size)
        // reorder: move calf raise to the top, drop the carry, add a custom exercise
        val custom = repo.createCustomExercise("Sissy squat", "Bodyweight", "Quads")
        val edited = WorkoutLogic.move(before, 4, 0).dropLast(1) + repo.newLine(custom, -1)!!
        repo.saveDay(pid, 1, "m1", null, "g1", "legs day", edited)
        val after = repo.dayLines(pid, 1)
        assertEquals(edited.map { it.exerciseId }, after.map { it.exerciseId })
        assertEquals("standing_seated_calf_raise", after.first().exerciseId)
        assertTrue(repo.day(pid, 1)!!.customized); assertEquals("legs day", repo.day(pid, 1)!!.notes)
        // the shared routine and other days that use the same routine are untouched
        assertEquals(before.map { it.exerciseId }, repo.routineLines("g1").map { it.exerciseId })
        val other = repo.createPlan("Second", "", "gym", emptyList(), "strength_conditioning_4day")
        assertEquals(before.map { it.exerciseId }, repo.dayLines(other, 1).map { it.exerciseId })
        // saving the original order again clears the override
        repo.saveDay(pid, 1, "m1", null, "g1", "", repo.routineLines("g1"))
        assertFalse(repo.day(pid, 1)!!.customized)
        assertEquals(0, repo.dayLines(pid, 1).size - 6)
    }

    @Test fun orderingIsPersistedExactly() {
        val (_, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        var lines = repo.dayLines(pid, 2)
        lines = WorkoutLogic.move(lines, 0, 3); lines = WorkoutLogic.move(lines, 6, 1)
        repo.saveDay(pid, 2, "m2", null, "g2", "", lines)
        assertEquals(lines.map { it.exerciseId }, repo.dayLines(pid, 2).map { it.exerciseId })
        assertEquals(lines.map { it.superset }, repo.dayLines(pid, 2).map { it.superset })
    }

    @Test fun changingRoutineDropsOldCustomisation() {
        val (_, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        repo.saveDay(pid, 1, "m1", null, "g1", "", repo.dayLines(pid, 1).drop(1))
        assertTrue(repo.day(pid, 1)!!.customized)
        repo.assignDay(pid, 1, "m1", null, "g3")
        assertFalse(repo.day(pid, 1)!!.customized)
        assertEquals(7, repo.dayLines(pid, 1).size)
    }

    @Test fun duplicateAndDeletePlan() {
        val (db, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        repo.saveDay(pid, 1, "m1", null, "g1", "n", repo.dayLines(pid, 1).drop(1))
        val copy = repo.duplicatePlan(pid, "Copy of S&C")
        assertEquals(repo.dayLines(pid, 1).map { it.exerciseId }, repo.dayLines(copy, 1).map { it.exerciseId })
        assertEquals(7, repo.days(copy).size)
        assertFalse(repo.deletePlan(pid))                       // presets are protected
        assertTrue(repo.deletePlan(copy))
        assertEquals(0, db.count("SELECT COUNT(*) FROM plan_day WHERE plan_id = ?", copy))
        assertEquals(0, db.count("SELECT COUNT(*) FROM plan_day_exercise WHERE plan_id = ?", copy))
        assertEquals(1, repo.plans().size)
    }

    @Test fun activePlanSwitching() {
        val (_, repo) = fresh()
        val b = repo.createPlan("B", "", "gym", emptyList(), null)
        repo.setActivePlan(b)
        assertEquals(b, repo.activePlan()!!.id); assertEquals(1, repo.plans().count { it.active })
    }

    @Test fun copyDay() {
        val (_, repo) = fresh()
        val pid = WorkoutCatalog.PRESET_PLAN_ID
        repo.copyDay(pid, 1, 3)
        assertEquals("g1", repo.day(pid, 3)!!.gym)
        assertEquals(repo.dayLines(pid, 1).map { it.exerciseId }, repo.dayLines(pid, 3).map { it.exerciseId })
    }

    // schedule rules (no run/ruck before G1/G3): see WorkoutScheduleRulesTest

    @Test fun slotMapping() {
        val mon = java.time.LocalDate.of(2026, 10, 12)         // a Monday
        assertEquals(1, WorkoutLogic.slotFor(mon, 1)); assertEquals(7, WorkoutLogic.slotFor(mon.plusDays(6), 1))
        assertEquals(1, WorkoutLogic.slotFor(mon.plusDays(2), 3))   // user starts slot 1 on Wednesday
        assertEquals(6, WorkoutLogic.slotFor(mon, 3))
        assertEquals(java.time.DayOfWeek.WEDNESDAY, WorkoutLogic.weekdayOf(1, 3))
    }

    // ---- exercises ----
    @Test fun searchAndFilters() {
        val (_, repo) = fresh()
        assertTrue(repo.searchExercises("squat").map { it.id }.contains("hack_squat_or_smith_machine_squat"))
        assertTrue(repo.searchExercises("zzzz").isEmpty())
        assertTrue(repo.searchExercises("", muscle = "Legs").all { "Legs" in WorkoutLogic.muscleGroups(it.primary) })
        assertTrue(repo.searchExercises("", equipment = "Cable").all { it.equipment.contains("Cable") })
        val cu = repo.createCustomExercise("Zercher squat", "Barbell", "Quads")
        assertTrue(repo.exercise(cu)!!.custom)
        assertThrows { repo.createCustomExercise("zercher SQUAT", "", "") }
        repo.toggleFavourite("leg_press")
        assertEquals(listOf("leg_press"), repo.searchExercises("", favouritesOnly = true).map { it.id })
    }

    // ---- logging / history ----
    @Test fun sessionSurvivesReopen() {
        val path = java.io.File.createTempFile("workout", ".db").also { it.deleteOnExit() }.absolutePath
        val db1 = JdbcRunner.file(path)
        WorkoutSchema.upgradeSteps(0, 1).forEach { db1.exec(it) }; WorkoutSeeder.seed(db1)
        val r1 = WorkoutRepo(db1)
        val sid = r1.startSession(WorkoutCatalog.PRESET_PLAN_ID, 1, "g1", "G1 · Lower Body A")
        r1.logSet(sid, "leg_press", 1, 100.0, 10, 8)
        val db2 = JdbcRunner.file(path)                           // "process death": a brand-new connection
        WorkoutSeeder.seed(db2)
        val r2 = WorkoutRepo(db2)
        assertEquals(sid, r2.activeSession()!!.id)
        assertEquals(listOf(100.0), r2.sessionSets(sid).map { it.loadKg })
        assertEquals(1, db2.count("SELECT COUNT(*) FROM plan"))
    }

    @Test fun historyIsNotRewrittenByRoutineOrPlanEdits() {
        val (_, repo) = fresh()
        val sid = repo.startSession(WorkoutCatalog.PRESET_PLAN_ID, 1, "g1", "x")
        repo.logSet(sid, "leg_press", 1, 100.0, 10, 8); repo.finishSession(sid)
        val tmp = repo.createPlan("Tmp", "", "gym", emptyList(), "strength_conditioning_4day")
        repo.saveDay(WorkoutCatalog.PRESET_PLAN_ID, 1, "m1", null, "g1", "", repo.dayLines(WorkoutCatalog.PRESET_PLAN_ID, 1).drop(1))
        repo.deletePlan(tmp)
        val sets = repo.sessionSets(sid)
        assertEquals(1, sets.size); assertEquals("Leg Press", sets[0].exerciseName)
    }

    @Test fun previousSetsAndWeekStats() {
        val (_, repo) = fresh()
        val a = repo.startSession(null, null, "g1", "A"); repo.logSet(a, "leg_press", 1, 100.0, 10, null); repo.logSet(a, "leg_press", 2, 100.0, 9, null); repo.finishSession(a)
        val b = repo.startSession(null, null, "g1", "B")
        assertEquals(listOf(10, 9), repo.previousSets("leg_press", b).map { it.reps })
        assertTrue(repo.previousSets("leg_press", a).isEmpty())
        repo.logCardio("run", 3.2, 25, 6)
        val st = repo.weekStats(0, Long.MAX_VALUE)
        assertEquals(1, st.workouts); assertEquals(1, st.runRuck); assertEquals(1900.0, st.volumeKg, 0.001)
        assertThrows { repo.logCardio("swim", 1.0, 10, null) }
        assertThrows { repo.logBodyWeight(java.time.LocalDate.of(2026, 10, 15), 5.0) }
        repo.logBodyWeight(java.time.LocalDate.of(2026, 10, 15), 72.4)
        assertEquals(72.4, repo.latestBodyWeight()!!.second, 0.001)
    }

    // ---- schema / migrations ----
    @Test fun schemaCreationIsRepeatableAndLeavesStudyDbAlone() {
        val db = JdbcRunner.memory()
        repeat(2) { WorkoutSchema.applySafely(WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION)) { sql -> db.exec(sql) } }   // IF NOT EXISTS / duplicate-column tolerant => safe re-run
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").map { it.str("name") }.toSet()
        assertTrue(tables.containsAll(setOf("exercise", "routine", "routine_exercise", "plan", "plan_day", "plan_day_exercise", "set_log", "workout_session", "body_weight_log")))
        assertFalse(tables.any { it in setOf("chapter", "question_log", "mock_test", "mistake", "book") })
        assertTrue(WorkoutSchema.upgradeSteps(1, 1).isEmpty())
    }

    // ---- pure logic ----
    @Test fun prescriptionFormatting() {
        assertEquals("4 × 8–10 reps", WorkoutLogic.prescription(4, "8-10"))
        assertEquals("3 × 8–10 per leg", WorkoutLogic.prescription(3, "8-10 per leg"))
        assertEquals("3 × 15 reps", WorkoutLogic.prescription(3, "15"))
        assertEquals("3 × 10 per side", WorkoutLogic.prescription(3, "10 per side"))
        assertEquals("2 × Max time", WorkoutLogic.prescription(2, "Max time", "hold"))
        assertEquals("30–40 min", WorkoutLogic.prescription(1, "30-40 min", "ruck"))
        assertEquals(WorkoutLogic.Reps(8, 10, ""), WorkoutLogic.parseReps("8-10"))
        assertEquals("90 sec", WorkoutLogic.restLabel(90)); assertEquals("2 min", WorkoutLogic.restLabel(120)); assertEquals("No rest", WorkoutLogic.restLabel(0))
    }

    @Test fun estimateIsPlausibleForG1() {
        val (_, repo) = fresh()
        val g1 = repo.routine("g1")!!
        val m = repo.estimate(g1, repo.routineLines("g1"))
        assertTrue("G1 estimate $m should sit inside 40..75", m in 40..75)
        assertEquals(17, repo.estimate(repo.routine("m1")!!, repo.routineLines("m1")))
    }

    @Test fun nextTimeSuggestion() {
        assertEquals("Add load: try 62.5 kg × 8", WorkoutLogic.nextSuggestion(listOf(60.0 to 10, 60.0 to 10, 60.0 to 10), 8, 10))
        assertEquals("Stay at 60 kg and add reps (aim for 10)", WorkoutLogic.nextSuggestion(listOf(60.0 to 10, 60.0 to 9, 60.0 to 8), 8, 10))
        assertNull(WorkoutLogic.nextSuggestion(emptyList(), 8, 10))
    }

    @Test fun planNameValidation() {
        assertNull(WorkoutLogic.validatePlanName("A", listOf("B")))
        assertNotNull(WorkoutLogic.validatePlanName("b ", listOf("B")))
        assertNotNull(WorkoutLogic.validatePlanName("x".repeat(61), emptyList()))
    }

    private fun assertThrows(block: () -> Unit) {
        try { block(); fail("expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
    }

    // ---- C5 Add Exercise: selection + filtering ----
    @Test fun specificMuscleFiltersHaveEntries() {
        val (_, repo) = fresh()
        for (m in WorkoutLogic.SPECIFIC_MUSCLES) assertTrue("no exercise for $m", repo.searchExercises("", setOf(m)).isNotEmpty())
        assertTrue(repo.searchExercises("", setOf("Side Delts")).any { it.id == "lateral_raise" })
        assertTrue(repo.searchExercises("", setOf("Rear Delts")).any { it.id == "face_pull" })
        assertTrue(repo.searchExercises("", setOf("Forearms")).any { it.id == "wrist_curl" })
    }

    @Test fun multiMuscleAndEquipmentFiltersCombine() {
        val (_, repo) = fresh()
        val ids = repo.searchExercises("", setOf("Biceps", "Triceps")).map { it.id }.toSet()
        assertTrue("hammer_curl" in ids && "triceps_rope_pushdown" in ids && "leg_press" !in ids)
        val cable = repo.searchExercises("", setOf("Biceps", "Triceps"), setOf("Cable")).map { it.id }
        assertTrue(cable.isNotEmpty() && cable.all { repo.exercise(it)!!.equipment.contains("Cable") })
        assertTrue(repo.searchExercises("curl", setOf("Forearms", "Biceps"), setOf("Dumbbell", "Machine")).isNotEmpty())
        assertTrue(repo.searchExercises("zzzz", setOf("Biceps")).isEmpty())
    }

    @Test fun customExercisesSurviveReseedAndAreFilterable() {
        val (db, repo) = fresh()
        val id = repo.createCustomExercise("Zottman Curl", "Dumbbells", "Biceps, Forearms")
        db.exec("DELETE FROM meta"); WorkoutSeeder.seed(db, now)
        assertEquals(1, repo.exercises().count { it.id == id })
        assertTrue(repo.searchExercises("", setOf("Forearms")).any { it.id == id })
        assertEquals(WorkoutCatalog.EXERCISES.size + 1, repo.exercises().size)
    }

    @Test fun selectedExercisesBecomeDayLines() {
        val (_, repo) = fresh()
        var uid = -1L
        val lines = listOf("front_raise", "wrist_curl", "preacher_curl").mapNotNull { repo.newLine(it, uid--) }
        assertEquals(3, lines.size); assertTrue(lines.all { it.uid < 0 })
        repo.saveDay(WorkoutCatalog.PRESET_PLAN_ID, 2, null, null, null, "", lines)
        assertEquals(listOf("front_raise", "wrist_curl", "preacher_curl"), repo.dayLines(WorkoutCatalog.PRESET_PLAN_ID, 2).map { it.exerciseId })
    }

    @Test fun lastUsedLoadsFromSetLogs() {
        val (_, repo) = fresh()
        assertTrue(repo.lastUsedLoads().isEmpty()); assertNull(WorkoutLogic.lastUsedLabel(null))
        val sid = repo.startSession(null, null, null, "t")
        repo.logSet(sid, "leg_press", 1, 40.0, 10, null, warmup = true)
        repo.logSet(sid, "leg_press", 2, 100.0, 8, null)
        repo.logSet(sid, "leg_press", 3, 110.0, 6, null)
        repo.logSet(sid, "plank", 1, null, null, null, durationS = 40)
        val m = repo.lastUsedLoads()
        assertEquals(110.0 to 6, m["leg_press"]); assertNull(m["plank"]); assertNull(m["hip_thrust"])
        assertEquals("Last 110 kg × 6", WorkoutLogic.lastUsedLabel(m["leg_press"]))
        assertEquals("Last 22.5 kg", WorkoutLogic.lastUsedLabel(22.5 to null))
    }

    // ---- C6 custom exercise ----
    @Test fun customExerciseSavesAllFieldsAndShowsInC5() {
        val (_, repo) = fresh()
        val id = repo.createCustomExercise("  Cable   Zottman Curl ", "Cable", "Biceps, Forearms", "reps", secondary = "Rear Delts", movementPattern = "Pull")
        val e = repo.exercise(id)!!
        assertEquals("Cable Zottman Curl", e.name); assertTrue(e.custom)
        assertEquals("Biceps, Forearms", e.primary); assertEquals("Rear Delts", e.secondary); assertEquals("Pull", e.movementPattern); assertNull(e.bodyweightFactor)
        assertTrue(repo.searchExercises("zottman", setOf("Forearms"), setOf("Cable")).any { it.id == id })
        val bw = repo.createCustomExercise("Archer Push-up", "Bodyweight", "Chest, Triceps", "reps", bodyweightFactor = 0.7)
        assertEquals(0.7, repo.exercise(bw)!!.bodyweightFactor!!, 1e-9)
        val dur = repo.createCustomExercise("Wall Sit", "Bodyweight", "Quads", "hold"); assertEquals("30-45 sec", repo.exercise(dur)!!.reps)
    }

    @Test fun customExerciseValidation() {
        val (_, repo) = fresh()
        val before = repo.exercises().size
        assertThrows { repo.createCustomExercise("   ", "", "Chest") }
        assertThrows { repo.createCustomExercise("No muscles", "", "") }
        assertThrows { repo.createCustomExercise("leg-press", "", "Legs") }      // duplicate of catalogue entry (case/punctuation-insensitive)
        assertThrows { repo.createCustomExercise("Bad factor", "", "Chest", bodyweightFactor = 5.0) }
        repo.createCustomExercise("Mine", "", "Chest")
        assertThrows { repo.createCustomExercise(" MINE ", "", "Chest") }
        assertEquals(before + 1, repo.exercises().size)   // nothing saved by failed attempts (and cancel never calls the repo)
    }

    @Test fun schemaV2UpgradesFromV1WithoutLosingData() {
        val db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, 1).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, now)
        WorkoutSchema.upgradeSteps(1, 2).forEach { db.exec(it) }
        val repo = WorkoutRepo(db) { now++ }
        assertEquals(WorkoutCatalog.EXERCISES.size, repo.exercises().size)
        assertEquals("", repo.exercise("leg_press")!!.movementPattern)
        assertEquals(4, repo.routines("gym").size); assertEquals(5, repo.routines("morning").size)
    }

    // ---- C8 routines library ----
    @Test fun librarySectionsListRealRoutinesWithoutDuplicates() {
        val (db, repo) = fresh()
        db.exec("DELETE FROM meta"); WorkoutSeeder.seed(db, now); WorkoutSeeder.seed(db, now)   // re-seeding must not duplicate
        val secs = WorkoutLogic.librarySections(repo.routines())
        assertEquals(listOf("Gym Routines", "Morning Routines"), secs.map { it.first })
        assertEquals(listOf("G1", "G2", "G3", "G4"), secs[0].second.map { it.code })
        assertEquals(listOf("M1", "M2", "M3", "M4", "M5"), secs[1].second.map { it.code })
        assertTrue((secs[0].second + secs[1].second).all { it.exerciseCount == repo.routineLines(it.id).size })
    }

    // ---- schema ----
    @Test fun schemaUpgradeToCurrentKeepsCatalogue() {
        val db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, 4).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, now)
        val repo = WorkoutRepo(db) { now++ }
        assertEquals(WorkoutCatalog.EXERCISES.size, repo.exercises().size)
    }

    // ---- C8/9 active workout (A-J) ----
    @Test fun sessionExtrasPersistWithoutDuplicates() {
        val (_, repo) = fresh()
        val sid = repo.startSession(null, null, "g1", "t")
        repo.addSessionExercise(sid, "front_raise"); repo.addSessionExercise(sid, "wrist_curl"); repo.addSessionExercise(sid, "front_raise")
        assertEquals(listOf("front_raise", "wrist_curl"), repo.sessionExtraLines(sid).map { it.exerciseId })
        assertTrue(repo.sessionExtraLines(repo.startSession(null, null, "g2", "x")).isEmpty())
        assertThrows { repo.addSessionExercise(sid, "does_not_exist") }
    }

    @Test fun editSetUpdatesInPlaceAndValidates() {
        val (_, repo) = fresh()
        val sid = repo.startSession(null, null, "g1", "t")
        val id = repo.logSet(sid, "leg_press", 1, 100.0, 8, null)
        repo.updateSet(id, 110.0, 6, null, null, "  felt heavy ")
        val s = repo.sessionSets(sid).single()      // still exactly one row
        assertEquals(110.0, s.loadKg!!, 1e-9); assertEquals(6, s.reps); assertEquals("felt heavy", s.note)
        assertThrows { repo.updateSet(id, 110.0, 0, null, null, "") }
        assertThrows { repo.updateSet(id, 5000.0, 5, null, null, "") }
        assertThrows { repo.updateSet(9999L, 100.0, 5, null, null, "") }
        assertEquals(6, repo.sessionSets(sid).single().reps)
        repo.deleteSet(id); assertTrue(repo.sessionSets(sid).isEmpty())
    }

    @Test fun previousSetsNewestFirstEmptyIsGraceful() {
        val (_, repo) = fresh()
        assertTrue(repo.exerciseHistory("leg_press", null).isEmpty())
        val a = repo.startSession(null, null, "g1", "a"); repo.logSet(a, "leg_press", 1, 90.0, 10, null)
        val b = repo.startSession(null, null, "g1", "b"); repo.logSet(b, "leg_press", 1, 100.0, 8, null); repo.logSet(b, "leg_press", 2, 50.0, 12, null, warmup = true)
        val cur = repo.startSession(null, null, "g1", "c"); repo.logSet(cur, "leg_press", 1, 105.0, 6, null)
        val h = repo.exerciseHistory("leg_press", cur)
        assertEquals(listOf(b, a), h.map { it.sessionId }); assertEquals(1, h[0].sets.size)   // warm-up excluded, current excluded
    }

    @Test fun prOnlyWhenGenuine() {
        assertNull(detectPr(emptyList(), 100.0, 5)); assertNull(detectPr(listOf(100.0 to 5), 100.0, 8)); assertNull(detectPr(listOf(100.0 to 5), 90.0, 9))
        assertNull(detectPr(listOf(100.0 to 5), null, 5)); assertNull(detectPr(listOf(100.0 to 5), 0.0, 5))
        assertEquals(PrResult(100.0, 5, 105.0, 3), detectPr(listOf(80.0 to 8, 100.0 to 5), 105.0, 3))
        val (_, repo) = fresh()
        val a = repo.startSession(null, null, "g1", "a"); repo.logSet(a, "leg_press", 1, 40.0, 10, null, warmup = true); repo.logSet(a, "leg_press", 2, 100.0, 5, null)
        val last = repo.logSet(a, "leg_press", 3, 120.0, 3, null)
        assertEquals(listOf(100.0 to 5), repo.prSetsFor("leg_press", last))
        assertNotNull(detectPr(repo.prSetsFor("leg_press", last), 120.0, 3)); assertTrue(repo.prSetsFor("hip_thrust").isEmpty())
    }

    @Test fun workoutTimeStartsOnPlayAndPauseShiftsStart() {
        val (_, repo) = fresh()
        val sid = repo.startSession(null, null, "g1", "t"); val s0 = repo.session(sid)!!.startedAt
        repo.beginSession(sid); assertTrue(repo.session(sid)!!.startedAt >= s0)
        val s1 = repo.session(sid)!!.startedAt
        assertFalse(repo.beginSession(sid)); assertEquals(s1, repo.session(sid)!!.startedAt)                 // Play is idempotent: the real start is stamped once
        repo.pauseSession(sid); repo.resumeSession(sid)                                                      // canonical model: pauses never move started_at
        assertEquals(s1, repo.session(sid)!!.startedAt)
        repo.finishSession(sid, false); assertFalse(repo.pauseSession(sid)); assertFalse(repo.resumeSession(sid))   // only active sessions change
    }

    @Test fun restTimerUsesMonotonicClock() {
        var t = 0L; val r = RestTimerState { t }
        assertEquals(90_000L, r.leftMs()); r.select(60); assertEquals(60_000L, r.leftMs())
        r.start(60, "Bench"); t = 20_000; assertEquals(40_000L, r.leftMs())
        r.pause(); t = 500_000; assertEquals(40_000L, r.leftMs()); assertTrue(r.paused)
        r.resume(); t += 10_000; assertEquals(30_000L, r.leftMs()); assertTrue(r.running)
        t += 100_000; r.tick(); assertEquals(0L, r.leftMs()); assertTrue(r.finished)
        r.reset(); assertFalse(r.finished); assertEquals(60_000L, r.leftMs())
    }

    // ---- C8/9 fidelity corrections ----
    @Test fun pausedWorkoutTimerPersistsAndSessionStateClears() {
        val (_, repo) = fresh()
        val sid = repo.startSession(null, null, "g1", "t")
        assertNull(repo.pausedAt(sid)); repo.setPausedAt(sid, 123456L); assertEquals(123456L, repo.pausedAt(sid))
        repo.saveRest(sid, "p|90|45000|Bench"); assertEquals("p|90|45000|Bench", repo.loadRest(sid))
        repo.clearSessionState(sid); assertNull(repo.pausedAt(sid)); assertNull(repo.loadRest(sid))
    }

    @Test fun restTimerSnapshotRestoreIsConsistent() {
        var t = 1_000L; val a = RestTimerState { t }
        a.start(90, "Bench"); t += 30_000
        val runSnap = a.snapshot(wallNow = 10_000_000L)
        val b = RestTimerState { 5_000L }                                   // fresh process, different monotonic clock
        b.restore(runSnap, wallNow = 10_020_000L)                          // reopened 20 s later
        assertTrue(b.running); assertEquals(40_000L, b.leftMs())           // 60 s left at save, 20 s passed
        val late = RestTimerState { 0L }; late.restore(runSnap, wallNow = 11_000_000L)
        assertFalse(late.running); assertTrue(late.finished); assertEquals(0L, late.leftMs())   // ended while closed: finished, never negative
        a.pause(); val p = RestTimerState { 0L }; p.restore(a.snapshot(99L), 5_000_000L)
        assertTrue(p.paused); assertFalse(p.running); assertEquals(60_000L, p.leftMs())          // stays paused, same remaining time
        val idle = RestTimerState { 0L }; idle.restore("garbage", 1L); assertFalse(idle.running); assertEquals(90_000L, idle.leftMs())
        var n = 0; a.onChange = { n++ }; a.resume(); a.reset(); assertEquals(2, n)
    }

    @Test fun removeExerciseKeepsRecordedSets() {
        val (_, repo) = fresh()
        val sid = repo.startSession(null, null, "g1", "t")
        repo.addSessionExercise(sid, "front_raise"); repo.addSessionExercise(sid, "wrist_curl")
        repo.removeExerciseFromWorkout(sid, "front_raise", addedDuringSession = true)           // added exercise: row deleted
        assertEquals(listOf("wrist_curl"), repo.sessionExtraLines(sid).map { it.exerciseId })
        repo.logSet(sid, "wrist_curl", 1, 20.0, 12, null)
        assertThrows { repo.removeExerciseFromWorkout(sid, "wrist_curl", true) }                  // has logged sets: refused
        assertEquals(1, repo.sessionSets(sid).size); assertEquals(1, repo.sessionExtraLines(sid).size)
        repo.removeExerciseFromWorkout(sid, "leg_press", addedDuringSession = false)            // planned exercise: hidden for this session only
        assertEquals(setOf("leg_press"), repo.hiddenExercises(sid)); assertTrue(repo.hiddenExercises(repo.startSession(null, null, "g2", "o")).isEmpty())
        repo.addSessionExercise(sid, "leg_press"); assertTrue(repo.hiddenExercises(sid).isEmpty())   // re-adding un-hides, no duplicate row
        assertEquals(listOf("wrist_curl"), repo.sessionExtraLines(sid).map { it.exerciseId })
    }

    // ---- C10 Workout Completed ----
    @Test fun completionReportsOnlyGenuinePrsAndRealTotals() {
        val (_, repo) = fresh()
        val a = repo.startSession(null, null, "g1", "Push"); repo.logSet(a, "leg_press", 1, 100.0, 10, null); repo.finishSession(a)
        val b = repo.startSession(null, null, "g1", "Push"); repo.logSet(b, "leg_press", 1, 110.0, 8, null); repo.logSet(b, "leg_press", 2, 100.0, 10, null, warmup = true)
        assertThrows(IllegalStateException::class.java) { WorkoutCompletion.load(repo, b) }   // not saved yet -> no C10
        repo.finishSession(b); repo.finishSession(b)                                          // second save is a no-op
        val w = WorkoutCompletion.load(repo, b)
        assertEquals(1, w.sets); assertEquals(880.0, w.volumeKg, 0.001)
        assertEquals(1, w.prs.size); assertEquals(10.0, w.prs[0].deltaKg, 0.001)
        val first = WorkoutCompletion.load(repo, a)
        assertTrue(first.prs.isEmpty())                                                       // nothing earlier to beat -> no PR
    }
}
