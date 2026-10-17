package com.pranav.focus

import com.pranav.focus.workout.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkoutStatsTest {
    private val z = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 17)   // Saturday; week = Mon 12 Oct .. Sun 18 Oct
    private fun ms(d: LocalDate, h: Int = 10) = d.atTime(h, 0).atZone(z).toInstant().toEpochMilli()
    private var sid = 0L
    private fun sess(id: String, d: LocalDate, activity: String = "gym") = StatSession(id, "W", activity, ms(d), ms(d) + 3_600_000L)
    private fun set(s: String, ex: String, kg: Double?, reps: Int?, d: LocalDate) = StatSet(++sid, s, ex, ex, kg, reps, ms(d))
    private val muscles = mapOf("bench" to ("Chest" to ""), "squat" to ("Quads, Glutes" to ""), "run" to ("Cardio" to ""), "mystery" to ("Full body" to ""))

    @Test fun emptyDataDoesNotCrashAndIsZero() {
        StatsRange.values().forEach { r ->
            val x = WorkoutStats.compute(r, today, z, emptyList(), emptyList(), muscles)
            assertEquals(0, x.workouts); assertEquals(0.0, x.volumeKg, 0.0); assertTrue(x.muscles.isEmpty()); assertTrue(x.lifts.isEmpty())
        }
    }

    @Test fun filtersChangeTheNumbers() {
        val s = listOf(sess("a", LocalDate.of(2026, 10, 13)), sess("b", LocalDate.of(2026, 10, 2)), sess("c", LocalDate.of(2026, 9, 20)))
        val sets = listOf(set("a", "bench", 100.0, 5, LocalDate.of(2026, 10, 13)), set("b", "bench", 100.0, 5, LocalDate.of(2026, 10, 2)), set("c", "bench", 50.0, 10, LocalDate.of(2026, 9, 20)))
        val w = WorkoutStats.compute(StatsRange.WEEK, today, z, s, sets, muscles)
        val m = WorkoutStats.compute(StatsRange.MONTH, today, z, s, sets, muscles)
        val a = WorkoutStats.compute(StatsRange.ALL, today, z, s, sets, muscles)
        assertEquals(listOf(1, 2, 3), listOf(w.workouts, m.workouts, a.workouts))
        assertEquals(listOf(500.0, 1000.0, 1500.0), listOf(w.volumeKg, m.volumeKg, a.volumeKg))
        assertEquals(7, w.buckets.size); assertEquals(500.0, w.buckets[1].volumeKg, 0.0)   // Tuesday 13 Oct
        assertEquals(100, m.volumeDelta!!.pct)   // October 1000 kg vs September 500 kg
    }

    @Test fun cardioSessionsCountAsWorkoutsButNotSetsOrVolume() {
        val s = listOf(sess("g", LocalDate.of(2026, 10, 13)), sess("r", LocalDate.of(2026, 10, 14), "run"))
        val sets = listOf(set("g", "bench", 60.0, 10, LocalDate.of(2026, 10, 13)), set("r", "run", null, null, LocalDate.of(2026, 10, 14)))
        val x = WorkoutStats.compute(StatsRange.WEEK, today, z, s, sets, muscles)
        assertEquals(2, x.workouts); assertEquals(1, x.sets); assertEquals(600.0, x.volumeKg, 0.0); assertEquals(120L, x.minutes)
    }

    @Test fun unsavedSessionsAreIgnoredBecauseSetsWithoutASavedSessionDoNotCount() {
        val x = WorkoutStats.compute(StatsRange.ALL, today, z, listOf(sess("a", LocalDate.of(2026, 10, 13))), listOf(set("a", "bench", 50.0, 5, today), set("ghost", "bench", 500.0, 5, today)), muscles)
        assertEquals(1, x.sets); assertEquals(250.0, x.volumeKg, 0.0)
    }

    @Test fun muscleSharesSplitPrimaryCreditAndReportUnmapped() {
        val d = LocalDate.of(2026, 10, 13)
        val sets = listOf(set("a", "bench", 50.0, 5, d), set("a", "squat", 80.0, 5, d), set("a", "squat", 80.0, 5, d), set("a", "mystery", 10.0, 5, d))
        val x = WorkoutStats.compute(StatsRange.WEEK, today, z, listOf(sess("a", d)), sets, muscles)
        assertEquals(1, x.unmappedSets); assertTrue("Full body" in x.unmappedLabels)
        val by = x.muscles.associateBy { it.key }
        assertEquals(1.0, by["chest"]!!.sets, 1e-9); assertEquals(1.0, by["quads"]!!.sets, 1e-9); assertEquals(1.0, by["glutes"]!!.sets, 1e-9)
        assertEquals(1.0, x.muscles.sumOf { it.share }, 1e-9); assertEquals(1.0 / 3, by["chest"]!!.share, 1e-9)
    }

    @Test fun prsNeedPriorHistoryAndAreStrictlyHeavier() {
        val d1 = LocalDate.of(2026, 10, 1); val d2 = LocalDate.of(2026, 10, 8); val d3 = LocalDate.of(2026, 10, 13); val d4 = LocalDate.of(2026, 10, 14)
        val s = listOf(sess("1", d1), sess("2", d2), sess("3", d3), sess("4", d4))
        val sets = listOf(set("1", "bench", 60.0, 8, d1), set("2", "bench", 70.0, 5, d2), set("3", "bench", 70.0, 6, d3), set("4", "bench", 75.0, 3, d4))
        val x = WorkoutStats.compute(StatsRange.ALL, today, z, s, sets, muscles)
        assertEquals(listOf(75.0, 70.0), x.prs.map { it.kg })      // first session is a baseline, equal 70 is not a PR
        val lift = x.lifts.single(); assertTrue(lift.isVerifiedPr); assertEquals(70.0, lift.previousBestKg!!, 0.0); assertEquals(7, lift.changePct)
        val single = WorkoutStats.compute(StatsRange.ALL, today, z, listOf(sess("1", d1)), listOf(set("1", "bench", 60.0, 8, d1)), muscles)
        assertTrue(single.prs.isEmpty()); assertFalse(single.lifts.single().isVerifiedPr); assertNull(single.lifts.single().previousBestKg)
    }

    @Test fun epleyOnlyForSensibleReps() {
        assertEquals(100 * (1 + 5 / 30.0), WorkoutStats.epley1rm(100.0, 5)!!, 1e-9); assertNull(WorkoutStats.epley1rm(100.0, 20)); assertNull(WorkoutStats.epley1rm(100.0, null))
    }
}
