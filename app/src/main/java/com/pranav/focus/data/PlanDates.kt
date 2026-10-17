package com.pranav.focus.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure date arithmetic for the A18 planner (no Android, unit-testable). */
object PlanDates {
    const val DAYS_BACK = 30L
    const val DAYS_AHEAD = 60L

    /** Contiguous run of dates the day strip can show. Always contains both today and the selected date. */
    data class Strip(val first: LocalDate, val last: LocalDate) {
        val size: Int get() = (ChronoUnit.DAYS.between(first, last) + 1).toInt()
        fun at(index: Int): LocalDate = first.plusDays(index.toLong())
        fun indexOf(d: LocalDate): Int = ChronoUnit.DAYS.between(first, d).toInt()
    }

    fun strip(today: LocalDate, selected: LocalDate): Strip = Strip(
        minOf(today.minusDays(DAYS_BACK), selected.minusDays(7)),
        maxOf(today.plusDays(DAYS_AHEAD), selected.plusDays(7)),
    )

    /**
     * Midnight rollover: if the user was looking at "today" they follow it to the new day;
     * an intentional other date (history or future) is left alone.
     */
    fun followToday(selected: LocalDate, previousToday: LocalDate, newToday: LocalDate): LocalDate =
        if (selected == previousToday) newToday else selected

    /** [startMs, endMs) of a local calendar day in [zone]; 23/25 h on DST changes. */
    fun dayBoundsMs(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}
