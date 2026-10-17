package com.pranav.focus

import com.pranav.focus.workout.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Final repair: schema v5 migration, completion snapshot, duration definitions, PR basis, averages. */
class WorkoutFinalRepairTest {
    private var t = 1_760_000_000_000L
    private lateinit var db: JdbcRunner
    private fun fresh(): WorkoutRepo {
        db = JdbcRunner.memory()
        WorkoutSchema.applySafely(WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION)) { db.exec(it) }
        WorkoutSeeder.seed(db, t)
        return WorkoutRepo(db) { t }
    }

    @Test fun migrationFromV4KeepsOldWorkoutsAndMarksSnapshotAndActiveTimeUnavailable() {
        db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, 4).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, t)
        db.exec("INSERT INTO workout_session(id, routine_id, title, activity, status, started_at, ended_at) VALUES('old','g1','Old Leg Day','gym','completed',1000000,4600000)")
        db.exec("INSERT INTO set_log(session_id, exercise_id, exercise_name, set_no, load_kg, reps, completed_at) VALUES('old','leg_press','Leg Press',1,100.0,10,2000000)")
        repeat(2) { WorkoutSchema.applySafely(WorkoutSchema.upgradeSteps(4, 5)) { db.exec(it) } }      // upgrade, then a harmless re-run
        val repo = WorkoutRepo(db) { t }
        val d = WorkoutHistory.load(repo, "old")
        assertEquals("Old Leg Day", d.title); assertEquals(1, d.workingSets); assertEquals(1000.0, d.volumeKg, 0.0)
        assertFalse(d.historical)
        assertEquals("Gym", d.subtitle)                                   // no category invented from today's routine record
        assertEquals(60L, d.timing.elapsedMin); assertNull(d.timing.activeMs); assertFalse(d.timing.tracked)
        assertEquals(listOf("Duration"), WorkoutTiming.metrics(d.timing).map { it.first })
        assertTrue(d.prs.isEmpty())                                       // first recorded lift = baseline, no PR invented
    }

    @Test fun snapshotSurvivesRenamingEditingAndDeletingSourceRecords() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "Leg Day")
        repo.logSet(id, "leg_press", 1, 100.0, 10, null); t += 1000
        val before = repo.exercise("leg_press")!!
        assertTrue(repo.finishSession(id))
        val a = WorkoutHistory.load(repo, id)
        assertTrue(a.historical); assertTrue(a.subtitle.startsWith("Gym"))
        db.exec("UPDATE exercise SET name = 'Renamed Press', primary_muscles = 'Biceps' WHERE id = 'leg_press'")
        db.exec("UPDATE routine SET name = 'Edited', category = 'morning', focus = 'zzz' WHERE id = 'g1'")
        db.exec("DELETE FROM routine WHERE id = 'g1'")
        val b = WorkoutHistory.load(repo, id)
        assertEquals(a.subtitle, b.subtitle)
        assertEquals("Leg Press", b.exercises.single().name)
        assertEquals(before.primary, b.exercises.single().muscle)         // muscles come from the snapshot, not today's record
        assertEquals(a.muscles, b.muscles)
        assertEquals(before.name, repo.snapshotExercises(id).single().name)
    }

    @Test fun snapshotKeepsExerciseOrderAndVerifiedPrsIncludingLaterEditsOfHistory() {
        val repo = fresh()
        val a = repo.startSession(null, null, "g1", "A"); repo.logSet(a, "leg_press", 1, 100.0, 10, null); t += 1000; repo.finishSession(a); t += 1000
        val b = repo.startSession(null, null, "g1", "B")
        repo.logSet(b, "pull_up", 1, null, 8, null); repo.logSet(b, "leg_press", 1, 110.0, 8, null)
        val unsaved = repo.startSession(null, null, "g2", "never saved"); repo.logSet(unsaved, "leg_press", 1, 500.0, 1, null)   // must not count as a prior record
        t += 1000; repo.finishSession(b)
        assertEquals(listOf("pull_up", "leg_press"), repo.snapshotExercises(b).map { it.exerciseId })
        val d = WorkoutHistory.load(repo, b)
        assertEquals(listOf("pull_up", "leg_press"), d.exercises.map { it.exerciseId })
        assertEquals(1, d.prs.size); assertEquals(10.0, d.prs[0].deltaKg, 0.001)
        db.exec("DELETE FROM set_log WHERE session_id = '$a'")             // later data changes do not rewrite the stored PR
        assertEquals(1, WorkoutHistory.load(repo, b).prs.size)
    }

    @Test fun elapsedAndActiveDurationDefinitions() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "T"); repo.beginSession(id)            // start S = t
        t += 600_000L
        assertTrue(repo.pauseSession(id)); assertFalse(repo.pauseSession(id)); t += 120_000L; assertTrue(repo.resumeSession(id)); assertFalse(repo.resumeSession(id))   // 2 min pause, counted once
        repo.recordRest(id, 90)
        repo.logSet(id, "leg_press", 1, 100.0, 10, null)
        t += 600_000L
        assertTrue(repo.finishSession(id))
        val s = repo.session(id)!!
        assertEquals(120_000L, s.pausedMs); assertEquals(90_000L, s.restMs); assertEquals(2, s.timingV)
        val tm = WorkoutTiming.of(s)
        assertEquals(1_320_000L, tm.elapsedMs)                              // 10 + 2 + 10 minutes
        assertEquals(1_110_000L, tm.activeMs)                               // elapsed - pause - 90 s rest
        assertEquals(listOf("Elapsed", "Active"), WorkoutTiming.metrics(tm).map { it.first })
        assertEquals(22L, WorkoutHistory.load(repo, id).minutes); assertEquals(22L, WorkoutCompletion.load(repo, id).durationMin)
    }

    @Test fun finishingWhilePausedClosesThePauseAndDuplicateFinishIsRejected() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "T"); repo.beginSession(id)
        repo.logSet(id, "leg_press", 1, 100.0, 10, null)
        t += 300_000L; repo.setPausedAt(id, t); t += 60_000L
        assertTrue(repo.finishSession(id))
        assertFalse(repo.finishSession(id)); assertFalse(repo.finishSession(id, false))
        val s = repo.session(id)!!
        assertEquals("completed", s.status); assertEquals(60_000L, s.pausedMs)
        assertEquals(360_000L, WorkoutTiming.of(s).elapsedMs); assertEquals(300_000L, WorkoutTiming.of(s).activeMs)
        assertNull(repo.pausedAt(id))
        assertEquals(1, db.count("SELECT COUNT(*) FROM session_snapshot_ex WHERE session_id = ?", id))
    }

    @Test fun restOnlyRecordedForActiveSessions() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "T"); repo.logSet(id, "leg_press", 1, 50.0, 5, null)
        repo.recordRest(id, 0); repo.recordRest(id, 45); repo.finishSession(id); repo.recordRest(id, 60)
        assertEquals(45_000L, repo.session(id)!!.restMs); assertEquals(1, db.count("SELECT COUNT(*) FROM rest_log WHERE session_id = ?", id))
    }

    @Test fun statsAveragesUseDocumentedDenominators() {
        val z = ZoneId.of("UTC"); val today = LocalDate.of(2026, 10, 14)
        fun ms(d: LocalDate) = d.atStartOfDay(z).toInstant().toEpochMilli() + 3_600_000L
        val s1 = StatSession("a", "A", "gym", ms(today.minusDays(1)), ms(today.minusDays(1)) + 3_600_000L)                                   // legacy: elapsed only
        val s2 = StatSession("b", "B", "gym", ms(today), ms(today) + 3_000_000L, pausedMs = 600_000L, restMs = 300_000L, timingV = 1)       // elapsed 60 min, active 45 min
        val sets = listOf(StatSet(1, "a", "x", "X", 100.0, 10, ms(today)), StatSet(2, "b", "x", "X", 50.0, 10, ms(today)))
        val r = WorkoutStats.compute(StatsRange.WEEK, today, z, listOf(s1, s2), sets, emptyMap())
        assertEquals(2, r.workouts); assertEquals(120L, r.minutes); assertEquals(60L, r.avgElapsedMin)
        assertEquals(1, r.activeWorkouts); assertEquals(45L, r.activeMinutes); assertEquals(45L, r.avgActiveMin)
        assertEquals(1500.0, r.volumeKg, 0.0); assertEquals(750.0, r.avgVolumeKg!!, 0.0); assertEquals(2, r.volumeWorkouts)
        val empty = WorkoutStats.compute(StatsRange.WEEK, today, z, emptyList(), emptyList(), emptyMap())
        assertNull(empty.avgVolumeKg); assertNull(empty.avgElapsedMin); assertNull(empty.avgActiveMin); assertNull(empty.activeMinutes)
    }

    // ---- accuracy repair pass ----
    @Test fun restWithSameUidCountsOnceAndOutcomesAreKept() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "T"); repo.logSet(id, "leg_press", 1, 50.0, 5, null)
        repo.recordRest(id, 60, "completed", "u1", 1L); repo.recordRest(id, 60, "completed", "u1", 1L); repo.recordRest(id, 20, "skipped", "u2", 2L)
        repo.finishSession(id)
        assertEquals(80_000L, repo.session(id)!!.restMs)
        assertEquals(2, db.count("SELECT COUNT(*) FROM rest_log WHERE session_id = ? AND outcome IS NOT NULL", id))
    }

    @Test fun restTimerReportsEachRestOnceWithOutcome() {
        var m = 0L; val r = RestTimerState({ m }, { 1_000L }); val got = ArrayList<RestRecord>(); r.onRestEnded = { got += it }
        r.start(60); m += 20_000; r.reset("skipped"); r.reset("skipped")
        r.start(30); m += 31_000; r.tick(); r.tick(); r.reset("skipped")
        assertEquals(listOf("skipped" to 20, "completed" to 30), got.map { it.outcome to it.seconds })
        r.start(60); m += 10_000; val snap = r.snapshot(5_000L)
        val rec = RestTimerState.closeFromSnapshot(snap, 5_000L + 1L)!!                 // workout finished with that rest still stored as open
        assertEquals("cancelled", rec.outcome); assertTrue(rec.seconds in 9..10)
    }

    @Test fun loggingSameSetTwiceAndEditingSavedWorkoutAreRejected() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "T")
        val a = repo.logSet(id, "leg_press", 1, 100.0, 10, null); val b = repo.logSet(id, "leg_press", 1, 100.0, 10, null)
        assertEquals(a, b); assertEquals(1, repo.sessionSets(id).size)
        repo.finishSession(id)
        mustReject { repo.updateSet(a, 120.0, 10, null, null, "") }; mustReject { repo.deleteSet(a) }; mustReject { repo.logSet(id, "leg_press", 2, 100.0, 10, null) }
    }

    @Test fun canonicalTimingNeverNegativeAndLegacyStaysUnavailable() {
        val tm = WorkoutTiming.of(1_000L, 2_000L, 5_000L, 5_000L, 2)
        assertEquals(1_000L, tm.elapsedMs); assertEquals(0L, tm.activeMs)
        val legacy = WorkoutTiming.of(1_000L, 601_000L, 0L, 0L, 0)
        assertEquals(600_000L, legacy.elapsedMs); assertNull(legacy.activeMs); assertNull(legacy.restMs); assertFalse(legacy.tracked)
    }

    private fun mustReject(b: () -> Unit) { try { b(); fail("expected IllegalArgumentException") } catch (e: IllegalArgumentException) { } }
}
