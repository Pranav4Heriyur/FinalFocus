package com.pranav.focus

import com.pranav.focus.workout.*
import org.junit.Assert.*
import org.junit.Test

/** The hard rule: no run or ruck (M3/M4) before a heavy leg day (G1/G3), the day before or earlier the same day. */
class WorkoutScheduleRulesTest {
    private val pid = WorkoutCatalog.PRESET_PLAN_ID
    private fun fresh(): Pair<JdbcRunner, WorkoutRepo> {
        val db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, 1_760_000_000_000L)
        return db to WorkoutRepo(db) { 1_760_000_000_000L }
    }
    private fun assertRejected(block: () -> Unit): String =
        try { block(); fail("expected the schedule rule to reject this edit"); "" } catch (e: IllegalArgumentException) { e.message ?: "" }

    // ---- pure rule ----
    private fun info(slot: Int, morning: String? = null, gym: String? = null, alt: String? = null): WorkoutLogic.SlotInfo {
        val kinds = mapOf("m1" to "circuit", "m2" to "mobility", "m3" to "run", "m4" to "ruck", "m5" to "mobility")
        return WorkoutLogic.SlotInfo(slot, kinds[morning], gym == "g1" || gym == "g3", morning?.uppercase(), gym?.uppercase(), kinds[alt], alt?.uppercase())
    }
    private fun week(vararg d: Triple<String?, String?, String?>) = d.mapIndexed { i, t -> info(i + 1, t.first, t.second, t.third) }
    private val none = Triple<String?, String?, String?>(null, null, null)

    @Test fun runTheDayBeforeG3IsAViolation() {
        val w = week(none, none, Triple("m3", null, null), Triple("m1", "g3", null), none, none, none)
        assertEquals(listOf(3), WorkoutLogic.scheduleViolations(w).map { it.slot })
    }

    @Test fun ruckTheDayBeforeG1IsAViolationAndTheWeekWrapsAround() {
        val w = week(Triple("m1", "g1", null), none, none, none, none, none, Triple("m4", null, null))
        assertEquals(listOf(7), WorkoutLogic.scheduleViolations(w).map { it.slot })
    }

    @Test fun alternativeMorningRoutineCounts() {
        val w = week(none, none, Triple("m5", null, "m4"), Triple("m1", "g3", null), none, none, none)
        assertEquals(listOf(3), WorkoutLogic.scheduleViolations(w).map { it.slot })
    }

    @Test fun runOnTheSameMorningAsAHeavyLegDayIsAViolation() {
        assertEquals(listOf(1), WorkoutLogic.scheduleViolations(week(Triple("m3", "g1", null), none, none, none, none, none, none)).map { it.slot })
        assertEquals(listOf(4), WorkoutLogic.scheduleViolations(week(none, none, none, Triple("m4", "g3", null), none, none, none)).map { it.slot })
    }

    @Test fun nonHeavyDaysAndSafeNeighboursAreFine() {
        // G2/G4 are upper body: a run before them is allowed; a run the day AFTER a heavy leg day is allowed.
        val w = week(Triple("m1", "g1", null), Triple("m3", "g2", null), Triple("m3", null, null), Triple("m1", "g3", null), Triple("m4", "g4", null), Triple("m5", null, null), none)
        // slot 3 (m3) precedes G3 on slot 4 -> only that one
        assertEquals(listOf(3), WorkoutLogic.scheduleViolations(w).map { it.slot })
        val ok = week(Triple("m1", "g1", null), Triple("m3", "g2", null), Triple("m5", null, null), Triple("m1", "g3", null), Triple("m4", "g4", null), Triple("m3", null, "m4"), Triple("m5", null, null))
        assertTrue(WorkoutLogic.scheduleViolations(ok).isEmpty())
    }

    @Test fun newViolationsOnlyReportsWhatAnEditIntroduces() {
        val legacyBad = week(none, none, Triple("m3", null, null), Triple("m1", "g3", null), none, none, none)
        assertTrue(WorkoutLogic.newViolations(legacyBad, legacyBad).isEmpty())
        val worse = legacyBad.toMutableList().also { it[6] = info(7, "m3") ; it[0] = info(1, "m1", "g1") }
        assertEquals(listOf(7), WorkoutLogic.newViolations(legacyBad, worse).map { it.slot })
    }

    // ---- shipped default ----
    @Test fun defaultScheduleIsCompliantAndHasNoRunBeforeG3() {
        val (_, repo) = fresh()
        assertTrue(repo.scheduleWarnings(pid).isEmpty())
        assertEquals("g3", repo.day(pid, 4)!!.gym)
        assertEquals("m5", repo.day(pid, 3)!!.morning)                  // the day before G3 is no longer a run
        assertNull(repo.day(pid, 3)!!.gym)
        assertEquals("m3", repo.day(pid, 6)!!.morning); assertEquals("m4", repo.day(pid, 6)!!.altMorning)
    }

    @Test fun everyTemplateScheduleIsCompliant() {
        val routines = WorkoutCatalog.ROUTINES.associateBy { it.id }
        for (t in WorkoutCatalog.TEMPLATES.filter { it.available }) {
            val infos = t.days.mapIndexed { i, d ->
                val m = routines[d.morning]; val a = routines[d.altMorning]; val g = routines[d.gym]
                WorkoutLogic.SlotInfo(i + 1, m?.kind, g?.heavyLegs == true, m?.code, g?.code, a?.kind, a?.code)
            }
            assertEquals("${t.id}: ${WorkoutLogic.scheduleViolations(infos)}", 0, WorkoutLogic.scheduleViolations(infos).size)
        }
    }

    @Test fun plansCreatedFromTheTemplateStartCompliant() {
        val (_, repo) = fresh()
        val id = repo.createPlan("Mine", "", "hybrid", emptyList(), "strength_conditioning_4day")
        assertTrue(repo.scheduleWarnings(id).isEmpty())
        assertEquals("m5", repo.day(id, 3)!!.morning)
    }

    @Test fun routinesAndExercisesAreUntouched() {
        val (_, repo) = fresh()
        assertEquals(listOf("g1", "g2", "g3", "g4"), repo.routines("gym").map { it.id })
        assertEquals(listOf(6, 7, 7, 8), repo.routines("gym").map { it.exerciseCount })
        assertEquals(setOf("m1", "m2", "m3", "m4", "m5"), repo.routines("morning").map { it.id }.toSet())
    }

    // ---- enforcement on create / edit ----
    @Test fun cannotPutARunBeforeG3() {
        val (_, repo) = fresh()
        val msg = assertRejected { repo.assignDay(pid, 3, "m3", null, null) }
        assertTrue(msg, msg.contains("G3"))
        assertEquals("m5", repo.day(pid, 3)!!.morning)                  // nothing was written
        assertRejected { repo.assignDay(pid, 3, "m4", null, null) }     // ruck too
        assertRejected { repo.assignDay(pid, 3, "m5", "m4", null) }     // ...even as the alternative
        assertEquals(null, repo.day(pid, 3)!!.altMorning)
    }

    @Test fun cannotPutARunBeforeG1AcrossTheWeekWrap() {
        val (_, repo) = fresh()
        assertRejected { repo.assignDay(pid, 7, "m3", null, null) }
        assertRejected { repo.assignDay(pid, 7, "m5", "m4", null) }
        assertEquals("m5", repo.day(pid, 7)!!.morning)
    }

    @Test fun cannotPutARunOnTheSameDayAsAHeavyLegSession() {
        val (_, repo) = fresh()
        assertRejected { repo.assignDay(pid, 1, "m3", null, "g1") }
        assertRejected { repo.assignDay(pid, 4, "m4", null, "g3") }
        assertEquals("m1", repo.day(pid, 1)!!.morning)
    }

    @Test fun cannotMoveAHeavyLegDayOntoTheDayAfterARun() {
        val (_, repo) = fresh()
        // slot 6 holds M3/M4; G1 or G3 on slot 7 would follow it, and on slot 6 would share its morning
        assertRejected { repo.assignDay(pid, 7, "m5", null, "g1") }
        assertRejected { repo.assignDay(pid, 6, "m3", "m4", "g3") }
        assertNull(repo.day(pid, 7)!!.gym)
    }

    @Test fun saveDayAndCopyDayAreEnforcedAndRollBack() {
        val (_, repo) = fresh()
        val before = repo.days(pid)
        assertRejected { repo.saveDay(pid, 3, "m3", null, null, "note", emptyList()) }
        assertEquals(before, repo.days(pid))                            // notes/lines/routine all rolled back
        assertRejected { repo.copyDay(pid, 6, 3) }                      // copies the run onto day 3
        assertEquals(before, repo.days(pid))
    }

    @Test fun safeEditsStillWork() {
        val (_, repo) = fresh()
        repo.assignDay(pid, 5, "m3", null, "g4")                        // run before an upper-body day is fine
        repo.assignDay(pid, 3, "m1", null, null)                        // a non-run day before G3 is fine
        repo.assignDay(pid, 7, "m2", null, null)
        repo.saveDay(pid, 1, "m1", null, "g1", "n", repo.dayLines(pid, 1).drop(1))   // reordering/removing lines is untouched by the rule
        assertTrue(repo.scheduleWarnings(pid).isEmpty())
    }

    @Test fun legacyBadPlanCanStillBeFixedAndEditedElsewhere() {
        val (db, repo) = fresh()
        db.exec("UPDATE plan_day SET morning_routine_id = 'm3' WHERE plan_id = ? AND slot = 3", pid)   // a plan saved before the rule existed
        assertEquals(listOf(3), repo.scheduleWarnings(pid).map { it.slot })
        repo.assignDay(pid, 2, "m2", null, "g2")                                                      // unrelated edit is not blocked
        repo.assignDay(pid, 3, "m5", null, null)                                                      // fixing it is
        assertTrue(repo.scheduleWarnings(pid).isEmpty())
    }

    // ---- upgrade of installs seeded before the fix ----
    private fun asV1(db: JdbcRunner) { db.exec("UPDATE plan_day SET morning_routine_id = 'm3' WHERE plan_id = ? AND slot = 3", pid); db.exec("INSERT OR REPLACE INTO meta(key, value) VALUES('seed_version','1')") }

    @Test fun upgradeFixesUntouchedV1PresetDay() {
        val (db, repo) = fresh()
        asV1(db)
        assertEquals(listOf(3), repo.scheduleWarnings(pid).map { it.slot })
        WorkoutSeeder.seed(db, 1_760_000_000_000L)
        assertEquals("m5", repo.day(pid, 3)!!.morning)
        assertTrue(repo.scheduleWarnings(pid).isEmpty())
        assertEquals(listOf(6, 7, 7, 8), repo.routines("gym").map { it.exerciseCount })
        assertEquals(1, repo.plans().size)
    }

    @Test fun upgradeLeavesUserChangedDaysAlone() {
        val (db, repo) = fresh()
        asV1(db)
        db.exec("UPDATE plan_day SET notes = 'my 5k day' WHERE plan_id = ? AND slot = 3", pid)
        WorkoutSeeder.seed(db, 1_760_000_000_000L)
        assertEquals("m3", repo.day(pid, 3)!!.morning)                  // user's own note => not ours to rewrite
        assertEquals("my 5k day", repo.day(pid, 3)!!.notes)
    }

    @Test fun upgradeRunsOnceAndIsIdempotent() {
        val (db, repo) = fresh()
        asV1(db)
        WorkoutSeeder.seed(db, 1_760_000_000_000L)
        repo.assignDay(pid, 3, "m1", null, null)                        // user edits after the upgrade
        repeat(3) { WorkoutSeeder.seed(db, 1_760_000_000_000L) }
        assertEquals("m1", repo.day(pid, 3)!!.morning)
        assertEquals(WorkoutCatalog.SEED_VERSION, db.query("SELECT value FROM meta WHERE key = 'seed_version'").first().str("value").toInt())
    }
}
