package com.pranav.focus.data

import kotlin.math.max
import kotlin.math.min

/** Pure logic, no Android dependencies, so it is unit-testable. */
object TimeBank {
    /** Verified effort only: a completed locked session earns 1 minute per 5 focused minutes. */
    const val FOCUS_DIVISOR = 5

    fun earnedFromFocus(focusedMinutes: Int): Int =
        if (focusedMinutes <= 0) 0 else focusedMinutes / FOCUS_DIVISOR

    /** Manual entries are unverified and never earn time. */
    @Suppress("UNUSED_PARAMETER")
    fun earnedFromManual(minutes: Int): Int = 0

    /** Minutes still usable today in a locked app: capped by the daily limit and by what was unlocked. */
    fun remaining(limitMin: Int, usedMin: Double, allocMin: Int): Int =
        max(0.0, min(limitMin - usedMin, allocMin - usedMin)).toInt()

    /** Pool minutes still free to spend: earned, minus yesterday's missed-item penalty, minus what was already unlocked. */
    fun poolAvailable(poolEarned: Int, penalty: Int, fromPool: Int): Int =
        (poolEarned - penalty - fromPool).coerceAtLeast(0)
}

object Rewards {
    const val XP_PER_FOCUS_MINUTE = 2
    const val QUIT_XP_PENALTY = 50
    const val ACTION_XP = 10
    /** Base XP for one completed in-app workout, before the consistency multiplier. */
    const val WORKOUT_XP = 20
    const val RULE_XP = 20
    const val REVIEW_ITEM_XP = 5
    const val MISSED_ITEM_PENALTY_MIN = 10

    fun xpForSession(minutes: Int) = minutes.coerceAtLeast(0) * XP_PER_FOCUS_MINUTE
    fun xpAfterQuit(xp: Int) = (xp - QUIT_XP_PENALTY).coerceAtLeast(0)
}

data class Rank(val name: String, val minXp: Int, val tagline: String)

object Ranks {
    val ALL: List<Rank> get() = ProgressionPolicy.RANKS

    fun forXp(xp: Int): Rank = ALL.last { xp >= it.minXp }
    fun next(xp: Int): Rank? = ALL.firstOrNull { it.minXp > xp }

    /** 0..1 progress from the current rank threshold to the next. */
    fun progress(xp: Int): Float {
        val cur = forXp(xp)
        val nxt = next(xp) ?: return 1f
        return ((xp - cur.minXp).toFloat() / (nxt.minXp - cur.minXp)).coerceIn(0f, 1f)
    }
}

object CallMatcher {
    /** Compare on the last 10 digits so +91 / 0 / spaces / dashes do not matter. */
    fun normalize(number: String?): String {
        val digits = number.orEmpty().filter { it.isDigit() }
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun matches(incoming: String?, saved: List<String>): Boolean {
        val n = normalize(incoming)
        if (n.length < 5) return false // private / unknown numbers are never allowed
        return saved.any { normalize(it) == n }
    }
}

object ExitRules {
    const val HOLD_SECONDS = 60
    const val PHRASE = "I choose distraction over my goals"

    fun phraseOk(input: String) = input.trim().equals(PHRASE, ignoreCase = true)
}

object Fmt {
    fun hm(minutes: Int): String = if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

    fun km(meters: Double, imperial: Boolean): String =
        if (imperial) "%.1f".format(meters / 1609.344) else "%.1f".format(meters / 1000.0)

    fun pace(distM: Double, movingS: Int, imperial: Boolean): String {
        if (distM <= 0 || movingS <= 0) return "—"
        val unit = if (imperial) 1609.344 else 1000.0
        val secPer = movingS / (distM / unit)
        val m = (secPer / 60).toInt()
        val s = (secPer % 60).toInt()
        return "%d'%02d\"".format(m, s)
    }

    fun clock(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}

/** Decodes a Google encoded polyline (Strava route summaries) into lat/lng pairs. */
object Polyline {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
            out.add(lat / 1e5 to lng / 1e5)
        }
        return out
    }

    fun encode(pts: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var pLat = 0
        var pLng = 0
        fun enc(v0: Int) {
            var v = if (v0 < 0) (v0 shl 1).inv() else v0 shl 1
            while (v >= 0x20) { sb.append((((v and 0x1f) or 0x20) + 63).toChar()); v = v shr 5 }
            sb.append((v + 63).toChar())
        }
        for ((la, ln) in pts) {
            val a = Math.round(la * 1e5).toInt(); val b = Math.round(ln * 1e5).toInt()
            enc(a - pLat); enc(b - pLng); pLat = a; pLng = b
        }
        return sb.toString()
    }
}
