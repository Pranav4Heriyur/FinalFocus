package com.pranav.focus.data

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one place the app asks "what day is it?". Uses the device's current zone on every call,
 * so a timezone change is picked up. Tests can pin the clock via [override].
 */
object AppClock {
    @Volatile var override: Clock? = null

    fun today(): LocalDate = override?.let { LocalDate.now(it) } ?: LocalDate.now()
    fun zone(): ZoneId = override?.zone ?: ZoneId.systemDefault()
}
