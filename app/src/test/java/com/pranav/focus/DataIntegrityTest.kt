package com.pranav.focus

import com.pranav.focus.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DataIntegrityTest {
    private val UC = "UC" + "a".repeat(22)
    private fun ledger(open: Int = 0) = Ledger(emptyList()).also { if (open > 0) it.append(LedgerEvent("o", 0, "d1", LedgerType.OPENING, open)) }

    // ---- midnight reset ----
    @Test fun midnightRolloverUsesLocalZone() {
        val z = ZoneId.of("Asia/Kolkata")
        AppClock.override = Clock.fixed(Instant.parse("2026-10-13T18:29:59Z"), z)   // 23:59:59 IST
        val before = AppClock.today()
        AppClock.override = Clock.fixed(Instant.parse("2026-10-13T18:30:00Z"), z)   // 00:00:00 IST next day
        assertEquals(before.plusDays(1), AppClock.today()); AppClock.override = null
    }
    @Test fun baseResetsDailyButBankPersists() {
        val l = ledger(40)
        val day1 = Allowance.split(50 * 60_000L, 0, 50, 0, 120)
        assertEquals(0, day1.remainingMin)
        val day2 = Allowance.split(0, 0, 50, 0, 120)          // new day, zero usage
        assertEquals(50, day2.remainingMin); assertEquals(40, l.balance())
    }

    // ---- allowance ----
    @Test fun exhaustionThenOverageIsBankFunded() {
        val s = Allowance.split(65 * 60_000L, 0, 50, 15, 120)
        assertEquals(50, s.baseUsedMin); assertEquals(15, s.bankUsedMin); assertEquals(0, s.remainingMin); assertEquals(0, s.overMin)
        assertEquals(listOf(10, 15, 30), Allowance.topUpOptions(100)); assertEquals(listOf(10), Allowance.topUpOptions(12))
    }
    @Test fun studyTimeNeverConsumesAllowance() {
        val s = Allowance.split(80 * 60_000L, 40 * 60_000L, 50, 0, 120)
        assertEquals(40, s.baseUsedMin); assertEquals(40, s.studyExemptMin); assertEquals(0, s.bankUsedMin); assertEquals(10, s.remainingMin)
        assertEquals(10, Allowance.split(10 * 60_000L, 999 * 60_000L, 50, 0, 120).studyExemptMin) // clamped to total
    }

    // ---- channel matching ----
    private val list = listOf(StudyChannel(UC, "Physics Wallah"), StudyChannel("dQw4w9WgXcQ", "", ExemptKind.VIDEO), StudyChannel("UC" + "b".repeat(22), "Off", enabled = false))
    @Test fun idMatchVerifies() {
        assertEquals(Verdict.VERIFIED, StudyExempt.verdict(Observed(channelId = UC), list))
        assertEquals(Verdict.VERIFIED, StudyExempt.verdict(Observed(videoId = "dQw4w9WgXcQ"), list))
    }
    @Test fun unverifiedAndDisabledNeverExempt() {
        assertEquals(Verdict.UNVERIFIED, StudyExempt.verdict(null, list))
        assertEquals(Verdict.UNVERIFIED, StudyExempt.verdict(Observed(channelId = "UC" + "b".repeat(22)), list))
        assertEquals(Verdict.UNVERIFIED, StudyExempt.verdict(Observed(channelId = "UC" + "z".repeat(22), channelName = "Physics Wallah"), list)) // wrong id beats name
        val n = StudyExempt.verdict(Observed(channelName = "physics wallah"), list)
        assertEquals(Verdict.NAME_MATCHED, n); assertFalse(StudyExempt.exempts(n, false)); assertTrue(StudyExempt.exempts(n, true))
    }
    @Test fun urlParsing() {
        assertEquals(StudyExempt.Parsed(ExemptKind.CHANNEL, UC), StudyExempt.parse("https://www.youtube.com/channel/$UC"))
        assertEquals(StudyExempt.Parsed(ExemptKind.VIDEO, "dQw4w9WgXcQ"), StudyExempt.parse("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("pw", StudyExempt.parse("https://youtube.com/@pw")?.handle); assertNull(StudyExempt.parse("hello"))
    }

    // ---- top-up settlement, refunds, duplicates, concurrency ----
    @Test fun topUpSettlesAndRefundsUnused() {
        val l = ledger(40)
        assertEquals(30, l.reserveTopUp("t1", 1, "d1", "yt", 30)); assertEquals(10, l.balance())
        assertEquals(20, l.settleTopUp("t1", 10, 2, "d2")); assertEquals(30, l.balance())
        assertEquals(0, l.settleTopUp("t1", 10, 3, "d2")); assertEquals(30, l.balance()) // idempotent
    }
    @Test fun duplicateEventsChargeOnce() {
        val l = ledger(40)
        assertEquals(15, l.reserveTopUp("t", 1, "d", "yt", 15)); assertEquals(15, l.reserveTopUp("t", 2, "d", "yt", 15))
        assertEquals(25, l.balance())
        assertTrue(l.earn("e", 1, "d", 5)); assertFalse(l.earn("e", 1, "d", 5)); assertEquals(30, l.balance())
    }
    @Test fun grantNeverExceedsBalance() { assertEquals(7, ledger(7).reserveTopUp("t", 1, "d", "yt", 30)) ; assertEquals(0, ledger(0).reserveTopUp("t", 1, "d", "yt", 10)) }
    @Test fun concurrentSpendCannotOverdraw() {
        val l = ledger(50); val pool = Executors.newFixedThreadPool(8)
        repeat(20) { i -> pool.execute { l.reserveTopUp("c$i", 1, "d", "yt", 10) } }
        pool.shutdown(); pool.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(0, l.balance()); assertEquals(50, l.all().filter { it.type == LedgerType.TOPUP_RESERVE }.sumOf { -it.delta })
    }
    @Test fun unsettledReservationsFromEarlierDaysAreFound() {
        val l = ledger(40); l.reserveTopUp("t", 1, "2026-10-12", "yt", 10)
        assertEquals(1, l.unsettledBefore("2026-10-13").size); l.settleTopUp("t", 10, 2, "2026-10-13"); assertEquals(0, l.unsettledBefore("2026-10-13").size)
    }
    @Test fun penaltyNeverGoesBelowZeroAndIsIdempotent() {
        val l = ledger(5); l.penalty("p", 1, "d", 10); l.penalty("p", 1, "d", 10); assertEquals(0, l.balance())
        assertEquals(l.all(), Ledger(Ledger.decode(Ledger.encode(l.all()))).all())
    }

    // ---- progression ----
    @Test fun xpEventsAreIdempotentAndClamped() {
        val x = XpLedger(); assertTrue(x.record(XpEvent("a", 1, "d", "focus", 40))); assertFalse(x.record(XpEvent("a", 1, "d", "focus", 40)))
        x.record(XpEvent("q", 2, "d", "quit", -50)); assertEquals(0, x.total()); assertEquals(40, x.on("d", "focus"))
    }
    @Test fun oneRankTableAndStreakRule() {
        assertSame(ProgressionPolicy.RANKS, Ranks.ALL); assertEquals("Grinder", Ranks.forXp(250).name); assertEquals("Recruit", Ranks.forXp(249).name)
        assertFalse(ProgressionPolicy.streakQualifies(0)); assertTrue(ProgressionPolicy.streakQualifies(1))
    }
}
