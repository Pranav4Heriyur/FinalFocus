package com.pranav.focus

import com.pranav.focus.data.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.time.LocalDate

class TimeBankRecoveryTest {
    private var disk = ""
    private fun open(balance: Int = 0): Ledger {
        val l = Ledger(Ledger.decode(disk)) { disk = Ledger.encode(it) }
        if (balance > 0 && !l.has("o")) l.append(LedgerEvent("o", 0, "2026-10-12", LedgerType.OPENING, balance))
        return l
    }
    private fun restart() = open()

    @Test fun crashAfterReserveStillGrantsAllowance() {
        val l = open(40)
        l.reserveTopUp("t1", 1, "2026-10-12", "yt", 15)     // process "dies" here: nothing else was written
        val r = restart()
        assertEquals(15, r.topUpGrantedOn("2026-10-12", "yt"))   // grant reconstructed purely from the ledger
        assertEquals(25, r.balance())
    }
    @Test fun retryAfterCrashDoesNotChargeAgain() {
        open(40).reserveTopUp("t1", 1, "d", "yt", 15)
        val r = restart()
        assertEquals(15, r.reserveTopUp("t1", 2, "d", "yt", 15)); assertEquals(25, r.balance()); assertEquals(15, r.topUpGrantedOn("d", "yt"))
    }
    @Test fun settleRefundsUnusedExactlyOnceAcrossRestarts() {
        open(40).reserveTopUp("t1", 1, "2026-10-12", "yt", 30)
        assertEquals(20, restart().settlePast("2026-10-13", 5) { _, _ -> 10 })
        assertEquals(0, restart().settlePast("2026-10-13", 6) { _, _ -> 10 })
        val r = restart(); assertEquals(30, r.balance()); assertEquals(10, r.consumedOf("t1")); assertEquals(20, r.refundedOf("t1"))
    }
    @Test fun usageIsSpreadAcrossReservationsInOrder() {
        val l = open(60); l.reserveTopUp("a", 1, "d1", "yt", 10); l.reserveTopUp("b", 2, "d1", "yt", 15)
        assertEquals(13, l.settlePast("d2", 3) { _, _ -> 12 })   // a used 10, b used 2 -> refund 0 + 13
    }
    @Test fun unverifiableUsageDefersAndNeverRefunds() {
        val l = open(30); l.reserveTopUp("a", 1, "d1", "yt", 30)
        assertEquals(0, l.settlePast("d2", 2) { _, _ -> null }); assertEquals(0, l.balance()); assertEquals(1, l.unsettledBefore("d2").size)
        assertEquals(30, restart().settlePast("d3", 3) { _, _ -> 0 })      // verified later -> refunded once
    }
    @Test fun refundNeverExceedsReservation() {
        val l = open(30); l.reserveTopUp("a", 1, "d1", "yt", 10)
        assertEquals(0, l.settlePast("d2", 2) { _, _ -> 999 }); assertEquals(20, l.balance())
        val m = open(30); m.reserveTopUp("b", 1, "d1", "yt", 10); assertEquals(10, m.settlePast("d2", 2) { _, _ -> -5 }); assertEquals(30, m.balance())
    }
    @Test fun measuredUseSubtractsBaseAndEarnedGrantsAndStudy() {
        assertEquals(15, Allowance.bankUsedFromMeasured(80 * 60_000L, 10 * 60_000L, 50, 5))   // 70 ent - 50 base - 5 earned
        assertEquals(0, Allowance.bankUsedFromMeasured(30 * 60_000L, 0, 50, 0))
        assertEquals(0, Allowance.bankUsedFromMeasured(60 * 60_000L, 999 * 60_000L, 0, 0))    // study clamped to total
    }
    @Test fun fullyUnusedIsFullyRefunded() {
        val l = open(30); l.reserveTopUp("a", 1, "d1", "yt", 30); assertEquals(30, l.settlePast("d2", 2) { _, _ -> 0 }); assertEquals(30, l.balance())
    }
    @Test fun midnightKeepsWalletAndHistoryButResetsDailyCounters() {
        val l = open(0); l.earn("e1", 1, "2026-10-12", 20)
        assertEquals(20, l.earnedOn("2026-10-12")); assertEquals(0, l.earnedOn("2026-10-13"))
        assertEquals(20, restart().balance()); assertEquals(1, restart().all().size)
    }
    @Test fun sameRequestIdFromManyThreadsChargesOnce() {
        val l = open(50); val pool = Executors.newFixedThreadPool(8); val go = CountDownLatch(1)
        repeat(16) { pool.execute { go.await(); l.reserveTopUp("same", 1, "d", "yt", 20) } }
        go.countDown(); pool.shutdown(); pool.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(30, l.balance()); assertEquals(20, l.topUpGrantedOn("d", "yt"))
    }
    @Test fun concurrentDistinctSpendsNeverOverdrawAndSurviveRestart() {
        val l = open(30); val pool = Executors.newFixedThreadPool(8)
        repeat(12) { i -> pool.execute { l.reserveTopUp("r$i", 1, "d", "yt", 10) } }
        pool.shutdown(); pool.awaitTermination(5, TimeUnit.SECONDS)
        assertEquals(0, l.balance()); assertEquals(30, restart().topUpGrantedOn("d", "yt"))
    }

    // ---- study.db v2 -> v3 against a real old-schema SQLite database ----
    @Test fun migrationPreservesEveryExistingLog() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { st ->
                st.execute(StudyMigrations.V2_QUESTION_LOG)
                (1..5).forEach { st.execute("INSERT INTO question_log(ts,subject,chapter_id,source,correct,wrong,skipped,minutes,verified,book_id) VALUES(${it * 1000},1,$it,0,$it,1,0,30,1,2)") }
                val before = st.executeQuery("SELECT id,ts,chapter_id,correct,book_id FROM question_log ORDER BY id").use { rs -> buildList { while (rs.next()) add(listOf(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))) } }
                StudyMigrations.V3.forEach { st.execute(it) }
                val after = st.executeQuery("SELECT id,ts,chapter_id,correct,book_id,agenda_task_id,focus_session_id FROM question_log ORDER BY id").use { rs -> buildList { while (rs.next()) { assertNull(rs.getString(6)); assertNull(rs.getString(7)); add(listOf(rs.getInt(1), rs.getLong(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))) } } }
                assertEquals(before, after); assertEquals(5, after.size)
                st.execute("INSERT INTO question_log(ts,subject,chapter_id,source,correct,wrong,skipped,minutes,verified,agenda_task_id,focus_session_id) VALUES(9,0,1,0,1,0,0,5,0,'task-1','sess_9')")
                assertEquals(1, st.executeQuery("SELECT COUNT(*) FROM question_log WHERE agenda_task_id='task-1'").use { it.next(); it.getInt(1) })
                assertEquals(2, st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name LIKE 'idx_qlog_%'").use { it.next(); it.getInt(1) })
            }
        }
    }

    // ---- date boundaries ----
    @Test fun monthYearAndLeapBoundaries() {
        assertEquals(LocalDate.of(2027, 1, 1), PlanDates.strip(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31)).at(PlanDates.DAYS_BACK.toInt() + 1))
        assertEquals(LocalDate.of(2028, 3, 1), LocalDate.of(2028, 2, 29).plusDays(1))
        val s = PlanDates.strip(LocalDate.of(2026, 10, 13), LocalDate.of(2025, 1, 1))     // intentional old date stays reachable
        assertTrue(s.first <= LocalDate.of(2024, 12, 25)); assertEquals(java.time.DayOfWeek.TUESDAY, LocalDate.of(2026, 10, 13).dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 14), PlanDates.followToday(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)))
        assertEquals(LocalDate.of(2026, 9, 1), PlanDates.followToday(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14)))
    }
}
