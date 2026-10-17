package com.pranav.focus

import com.pranav.focus.workout.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutHistoryTest {
    private var now = 1_760_000_000_000L
    private fun fresh(): WorkoutRepo {
        val db = JdbcRunner.memory()
        WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION).forEach { db.exec(it) }
        WorkoutSeeder.seed(db, now)
        return WorkoutRepo(db) { now += 60_000L; now }
    }
    private fun session(repo: WorkoutRepo, title: String, vararg sets: Triple<String, Double?, Int?>): String {
        val id = repo.startSession(null, null, "g1", title)
        sets.forEachIndexed { i, (ex, kg, reps) -> repo.logSet(id, ex, i + 1, kg, reps, null) }
        repo.finishSession(id); return id
    }
    private fun detail(repo: WorkoutRepo, id: String): SessionDetail {
        val s = repo.session(id)!!
        val earlier = repo.statsSessions().filter { it.startedAt < s.startedAt }.map { it.id }.toSet()
        return WorkoutHistory.detail(s, repo.sessionSets(id).map { it.copy(exerciseId = repo.resolveExerciseId(it.exerciseId)) }, repo.statsSets().filter { it.sessionId in earlier }, repo.exerciseMuscles(), "Gym")
    }

    @Test fun historyListsOnlySavedWorkoutsAndSearchesTitleAndExercises() {
        val repo = fresh()
        val a = session(repo, "Leg Day", Triple("leg_press", 100.0, 10)); val b = session(repo, "Pull", Triple("pull_up", null, 8))
        val unsaved = repo.startSession(null, null, "g1", "Never saved"); repo.logSet(unsaved, "leg_press", 1, 200.0, 5, null)
        val items = WorkoutHistory.items(repo.statsSessions(), repo.statsSets())
        assertEquals(setOf(a, b), items.map { it.id }.toSet())
        assertEquals(b, WorkoutHistory.filter(items, "").first().id)                      // newest first
        assertEquals(a, WorkoutHistory.filter(items, "  LEG PRESS ").single().id)         // exercise-name match, case-insensitive
        assertEquals(a, WorkoutHistory.filter(items, "leg day").single().id)              // title match
        assertTrue(WorkoutHistory.filter(items, "zzz").isEmpty())
        assertEquals(1000.0, items.first { it.id == a }.volumeKg, 0.0); assertEquals(0.0, items.first { it.id == b }.volumeKg, 0.0)
    }

    @Test fun detailKeepsRecordedNameAndListsWarmupsWithoutCountingThem() {
        val repo = fresh()
        val id = repo.startSession(null, null, "g1", "Snapshot")
        repo.logSet(id, "leg_press", 1, 40.0, 10, null, warmup = true); repo.logSet(id, "leg_press", 2, 100.0, 8, 7, note = "felt heavy")
        repo.finishSession(id)
        val d = detail(repo, id); val e = d.exercises.single()
        assertEquals(2, e.sets.size); assertTrue(e.sets[0].warmup); assertEquals(1, e.workingSets); assertEquals(800.0, d.volumeKg, 0.0)
        assertEquals("felt heavy", e.sets[1].note); assertEquals(7, e.sets[1].rpe)
        assertEquals("Snapshot", d.title)
    }

    @Test fun prIsJudgedAgainstEarlierSessionsOnly() {
        val repo = fresh()
        val first = session(repo, "One", Triple("leg_press", 100.0, 10))
        val second = session(repo, "Two", Triple("leg_press", 120.0, 8))
        val third = session(repo, "Three", Triple("leg_press", 110.0, 8))
        assertTrue(detail(repo, first).prs.isEmpty())                       // baseline
        assertEquals(20.0, detail(repo, second).prs.single().deltaKg, 0.0)
        assertTrue(detail(repo, third).prs.isEmpty())                       // lighter than 120
        session(repo, "Four", Triple("leg_press", 200.0, 1))
        assertEquals(20.0, detail(repo, second).prs.single().deltaKg, 0.0)  // a later heavier session doesn't rewrite history
    }

    @Test fun shareTextUsesRecordedValues() {
        val repo = fresh(); val id = session(repo, "Leg Day", Triple("leg_press", 100.0, 10))
        val t = WorkoutHistory.shareText(detail(repo, id), "today")
        assertTrue(t.contains("Leg Day")); assertTrue(t.contains("100×10")); assertTrue(t.contains("1000 kg"))
    }
}
