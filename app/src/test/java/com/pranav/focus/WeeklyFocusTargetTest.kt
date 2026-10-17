package com.pranav.focus

import com.pranav.focus.data.Weekly
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyFocusTargetTest {
    @Test fun nextTargetIsWhatWasAchievedNotTargetPlusPercent() {
        // 12 h target, 18 h achieved in the week -> next week's target is 18 h
        assertEquals(18 * 60, Weekly.nextFocusTargetMin(18 * 60))
    }

    @Test fun underachievingLowersTheTargetButNotBelowTheFloor() {
        assertEquals(5 * 60, Weekly.nextFocusTargetMin(5 * 60))
        assertEquals(Weekly.MIN_FOCUS_TARGET_MIN, Weekly.nextFocusTargetMin(0))
    }

    @Test fun targetUsesTheRolledOverFocusValue() {
        val t = Weekly.target(1f, 18 * 60)
        assertEquals(18 * 60, t.focusMin)
    }

    @Test fun runAndWorkoutTargetsDoNotScaleWithTheQuestionsScale() {
        val t = Weekly.target(1.5f, 18 * 60)
        assertEquals(15.0, t.runKm, 0.0)          // 5 km goal x 3 runs
        assertEquals(4, t.workoutDays)            // consistency tier threshold
        assertEquals(600, t.questions)            // questions do scale: 400 x 1.5
    }
}
