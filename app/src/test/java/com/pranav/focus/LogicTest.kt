package com.pranav.focus

import com.pranav.focus.data.CallMatcher
import com.pranav.focus.data.ExitRules
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.TimeBank
import com.pranav.focus.data.Ranks
import com.pranav.focus.data.Polyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun focusEarnsHalf() {
        assertEquals(5, TimeBank.earnedFromFocus(25))
        assertEquals(12, TimeBank.earnedFromFocus(60))
        assertEquals(0, TimeBank.earnedFromFocus(0))
    }

    @Test fun manualEarnsNothing() = assertEquals(0, TimeBank.earnedFromManual(120))

    @Test fun quitPenaltyNeverNegative() {
        assertEquals(50, Rewards.xpAfterQuit(100))
        assertEquals(0, Rewards.xpAfterQuit(20))
    }

    @Test fun callNumbersNormalise() {
        assertEquals("9876543210", CallMatcher.normalize("+91 98765-43210"))
        assertTrue(CallMatcher.matches("+919876543210", listOf("98765 43210")))
        assertFalse(CallMatcher.matches("9876500000", listOf("9876543210")))
        assertFalse(CallMatcher.matches(null, listOf("9876543210")))
        assertFalse(CallMatcher.matches("", listOf("")))
    }

    @Test fun phraseCheck() {
        assertTrue(ExitRules.phraseOk("  i choose distraction over my goals "))
        assertFalse(ExitRules.phraseOk("i quit"))
    }

    @Test fun remainingRespectsLimitAndAlloc() {
        assertEquals(10, TimeBank.remaining(30, 5.0, 15))
        assertEquals(0, TimeBank.remaining(30, 20.0, 15))
        assertEquals(0, TimeBank.remaining(30, 0.0, 0))
        assertEquals(25, TimeBank.remaining(30, 5.0, 60))
    }

    @Test fun poolAvailable() {
        assertEquals(7, TimeBank.poolAvailable(20, 10, 3))
        assertEquals(0, TimeBank.poolAvailable(5, 10, 0))
    }

    @Test fun ranks() {
        assertEquals("Recruit", Ranks.forXp(0).name)
        assertEquals("Grinder", Ranks.forXp(250).name)
        assertEquals("Legendary", Ranks.forXp(99999).name)
        assertEquals(0.5f, Ranks.progress(125), 0.001f)
    }

    @Test fun polylineDecodes() {
        val pts = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].first, 1e-6)
        assertEquals(-120.2, pts[0].second, 1e-6)
    }
}
