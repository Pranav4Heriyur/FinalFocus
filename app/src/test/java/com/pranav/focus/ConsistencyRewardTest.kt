package com.pranav.focus

import com.pranav.focus.data.ProgressionPolicy
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.StudyLogic
import com.pranav.focus.data.Subject
import com.pranav.focus.data.Mock
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsistencyRewardTest {

    @Test fun multiplierSteps() {
        assertEquals(1.0, ProgressionPolicy.consistencyMultiplier(0), 0.0)
        assertEquals(1.0, ProgressionPolicy.consistencyMultiplier(1), 0.0)
        assertEquals(1.2, ProgressionPolicy.consistencyMultiplier(2), 0.0)
        assertEquals(1.2, ProgressionPolicy.consistencyMultiplier(3), 0.0)
        assertEquals(1.35, ProgressionPolicy.consistencyMultiplier(4), 0.0)
        assertEquals(1.35, ProgressionPolicy.consistencyMultiplier(5), 0.0)
        assertEquals(1.5, ProgressionPolicy.consistencyMultiplier(6), 0.0)
        assertEquals(1.5, ProgressionPolicy.consistencyMultiplier(7), 0.0)
    }

    @Test fun multiplierNeverDropsBelowOne() {
        for (d in -3..0) assertEquals(1.0, ProgressionPolicy.consistencyMultiplier(d), 0.0)
    }

    @Test fun workoutXpWithBonusRoundsToWholePoints() {
        // 20 base XP x 1.35 = 27 XP, a whole number as the ledger requires
        assertEquals(27, Math.round(Rewards.WORKOUT_XP * ProgressionPolicy.consistencyMultiplier(4)).toInt())
        assertEquals(20, Math.round(Rewards.WORKOUT_XP * ProgressionPolicy.consistencyMultiplier(1)).toInt())
    }

    @Test fun workoutMinutesWithBonusRoundToWholeMinutes() {
        // 10 base minutes x 1.5 = 15 minutes
        assertEquals(15, Math.round(10 * ProgressionPolicy.consistencyMultiplier(7)).toInt())
    }

    @Test fun mockScoreUsesNegativeMarking() {
        val m = Mock(1, "2026-10-01", "Mock", c = listOf(40, 30, 60), w = listOf(10, 5, 0), s = listOf(0, 0, 0), target = 600)
        // Physics: 4*40-10=150, Chemistry: 4*30-5=115, Biology: 4*60=240
        assertEquals(150, m.marks(Subject.PHYSICS.ordinal))
        assertEquals(115, m.marks(Subject.CHEMISTRY.ordinal))
        assertEquals(240, m.marks(Subject.BIOLOGY.ordinal))
        assertEquals(505, m.score)
        assertEquals(StudyLogic.marks(40, 10), m.marks(0))
    }
}
