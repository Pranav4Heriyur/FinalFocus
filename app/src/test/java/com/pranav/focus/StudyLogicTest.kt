package com.pranav.focus

import com.pranav.focus.data.Mock
import com.pranav.focus.data.StudyLogic
import com.pranav.focus.data.Tally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StudyLogicTest {
    @Test fun marksAndAccuracy() {
        assertEquals(105, StudyLogic.marks(29, 11)) // 4*29 - 11
        assertEquals(73, StudyLogic.accPct(29, 11)) // 72.5 rounds half up
        assertEquals(0, StudyLogic.accPct(0, 0))
        assertEquals(-3, StudyLogic.marks(0, 3))
    }

    @Test fun deltaNeedsBothPeriods() {
        assertNull(StudyLogic.deltaPct(Tally(5, 5), Tally()))
        assertEquals(10, StudyLogic.deltaPct(Tally(6, 4), Tally(5, 5)))
        assertEquals(-20, StudyLogic.deltaPct(Tally(3, 7), Tally(5, 5)))
    }

    @Test fun revisionIntervalsExpand() {
        val d = LocalDate.of(2026, 10, 11)
        assertEquals(d.plusDays(1), StudyLogic.nextDue(d, 0))
        assertEquals(d.plusDays(3), StudyLogic.nextDue(d, 1))
        assertEquals(d.plusDays(7), StudyLogic.nextDue(d, 2))
        assertEquals(d.plusDays(21), StudyLogic.nextDue(d, 3))
        assertEquals(d.plusDays(21), StudyLogic.nextDue(d, 9))
    }

    @Test fun weakestUsesAttemptFloor() {
        val items = listOf("a" to Tally(2, 6), "b" to Tally(20, 20), "c" to Tally(9, 1), "d" to Tally(3, 4))
        // a has 8 attempts (below floor 10) and d has 7, both ignored
        assertEquals(listOf("b", "c"), StudyLogic.weakest(items).map { it.first })
    }

    @Test fun mockTotals() {
        // Physics 118 + Chemistry 142 + Biology 252 = 512
        val m = Mock(1, "2026-10-01", "t", listOf(32, 38, 66), listOf(10, 10, 12), listOf(3, 0, 2), 600)
        assertEquals(118, m.marks(0)); assertEquals(142, m.marks(1)); assertEquals(252, m.marks(2))
        assertEquals(512, m.score)
        assertEquals(32, m.negative)
    }

    @Test fun xpIsCapped() {
        assertEquals(60, StudyLogic.xpFor(20, 20, 0))
        assertEquals(10, StudyLogic.xpFor(40, 40, 290))
        assertEquals(0, StudyLogic.xpFor(10, 5, 300))
    }

    @Test fun status() {
        assertEquals(0, StudyLogic.status(48)); assertEquals(1, StudyLogic.status(60)); assertEquals(2, StudyLogic.status(71))
    }
}
