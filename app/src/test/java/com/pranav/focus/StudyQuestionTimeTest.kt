package com.pranav.focus

import com.pranav.focus.data.StudyLogic
import com.pranav.focus.data.Subject
import org.junit.Assert.assertEquals
import org.junit.Test

class StudyQuestionTimeTest {
    @Test fun perQuestionRatesMatchSpec() {
        // Physics 1.10, Chemistry 0.78, Biology 0.34 minutes per question.
        assertEquals(11, StudyLogic.timeBankMinutes(mapOf(Subject.PHYSICS to 10)))
        assertEquals(7, StudyLogic.timeBankMinutes(mapOf(Subject.CHEMISTRY to 10)))   // 7.8 floors to 7
        assertEquals(3, StudyLogic.timeBankMinutes(mapOf(Subject.BIOLOGY to 10)))     // 3.4 floors to 3
    }
    @Test fun mixedSubjectsFloorOnlyTheDayTotal() {
        // 10 x 1.10 + 10 x 0.78 + 10 x 0.34 = 22.2 -> 22. Floor per subject would give 11 + 7 + 3 = 21 and lose a minute.
        assertEquals(22, StudyLogic.timeBankMinutes(mapOf(Subject.PHYSICS to 10, Subject.CHEMISTRY to 10, Subject.BIOLOGY to 10)))
    }
    @Test fun noQuestionsEarnNothing() {
        assertEquals(0, StudyLogic.timeBankMinutes(emptyMap()))
        assertEquals(0, StudyLogic.timeBankMinutes(mapOf(Subject.PHYSICS to 0)))
    }
    @Test fun totalGrowsMonotonicallyAsQuestionsAreAdded() {
        var last = 0
        for (n in 1..50) {
            val now = StudyLogic.timeBankMinutes(mapOf(Subject.PHYSICS to n))
            assert(now >= last)
            last = now
        }
        assertEquals(55, last)   // 50 x 1.10
    }
}
