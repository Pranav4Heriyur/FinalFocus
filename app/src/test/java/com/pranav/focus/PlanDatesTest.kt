package com.pranav.focus

import com.pranav.focus.data.AppClock
import com.pranav.focus.data.Mock
import com.pranav.focus.data.PlanDates
import com.pranav.focus.data.QLog
import com.pranav.focus.data.Source
import com.pranav.focus.data.Subject
import com.pranav.focus.data.tally
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PlanDatesTest {
    @After fun reset() { AppClock.override = null }

    @Test fun todayComesFromTheClockAndZone() {
        // 2026-10-13 20:00 UTC is already the 14th in Asia/Kolkata (+05:30)
        val instant = Instant.parse("2026-10-13T20:00:00Z")
        AppClock.override = Clock.fixed(instant, ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 10, 13), AppClock.today())
        AppClock.override = Clock.fixed(instant, ZoneId.of("Asia/Kolkata"))
        assertEquals(LocalDate.of(2026, 10, 14), AppClock.today())
    }

    @Test fun weekdayAndDayNumberComeFromTheSameDate() {
        val d = LocalDate.of(2026, 10, 14)
        assertEquals(java.time.DayOfWeek.WEDNESDAY, d.dayOfWeek)
        assertEquals(14, d.dayOfMonth)
    }

    @Test fun stripAlwaysContainsTodayAndSelected() {
        val today = LocalDate.of(2026, 10, 13)
        for (sel in listOf(today, today.minusDays(200), today.plusDays(400), today.plusDays(1))) {
            val s = PlanDates.strip(today, sel)
            assertTrue(s.indexOf(today) in 0 until s.size)
            assertTrue(s.indexOf(sel) in 0 until s.size)
            assertEquals(sel, s.at(s.indexOf(sel)))
        }
    }

    @Test fun stripIsCalendarArithmeticNotAFixedArray() {
        val s = PlanDates.strip(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13))
        assertEquals(LocalDate.of(2026, 9, 13), s.first)       // 30 days back
        assertEquals(LocalDate.of(2026, 12, 12), s.last)       // 60 days ahead
        assertEquals(LocalDate.of(2026, 11, 1), s.at(s.indexOf(LocalDate.of(2026, 10, 31)) + 1)) // month rollover
    }

    @Test fun midnightFollowsTodayButKeepsAnIntentionalDate() {
        val yesterday = LocalDate.of(2026, 10, 13)
        val today = yesterday.plusDays(1)
        assertEquals(today, PlanDates.followToday(yesterday, yesterday, today))
        val picked = LocalDate.of(2026, 9, 1)
        assertEquals(picked, PlanDates.followToday(picked, yesterday, today))
    }

    @Test fun dayBoundsCoverExactlyOneLocalDay() {
        val zone = ZoneId.of("Asia/Kolkata")
        val (a, b) = PlanDates.dayBoundsMs(LocalDate.of(2026, 10, 13), zone)
        assertEquals(24 * 3_600_000L, b - a)
        val (n, _) = PlanDates.dayBoundsMs(LocalDate.of(2026, 10, 14), zone)
        assertEquals(b, n) // adjacent days never overlap or leave gaps
        val dst = ZoneId.of("America/New_York")
        val (c, d) = PlanDates.dayBoundsMs(LocalDate.of(2026, 3, 8), dst) // spring-forward day
        assertEquals(23 * 3_600_000L, d - c)
    }

    @Test fun studyTotalsAreReproducibleFromLogs() {
        val logs = listOf(
            QLog(1, 0, Subject.PHYSICS, 1, Source.PYQ, 29, 11, 5, 30, false),
            QLog(2, 0, Subject.PHYSICS, 2, Source.NCERT, 10, 0, 0, 10, false),
        )
        val t = logs.tally()
        assertEquals(39, t.correct); assertEquals(11, t.wrong); assertEquals(5, t.skipped)
        assertEquals(50, t.attempted)                // skipped is separate from attempted
        assertEquals(4 * 39 - 11, t.marks)           // +4 / -1, as documented in docs/PART_B_STUDY.md
        assertEquals(78, t.accPct)                   // 39 / 50
    }

    @Test fun mockTotalEqualsSumOfSubjectMarks() {
        val m = Mock(1, "2026-10-01", "t", listOf(32, 38, 66), listOf(10, 10, 12), listOf(3, 0, 2), 600)
        assertEquals((0..2).sumOf { m.marks(it) }, m.score)
    }
}
