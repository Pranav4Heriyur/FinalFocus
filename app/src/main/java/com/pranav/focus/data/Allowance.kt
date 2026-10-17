package com.pranav.focus.data

/** Per-app daily breakdown. All values are whole minutes and mutually exclusive categories of foreground time. */
data class AllowanceSplit(
    val baseUsedMin: Int,      // entertainment time covered by the free base allowance
    val studyExemptMin: Int,   // verified study time: never charged to base or bank
    val bankUsedMin: Int,      // entertainment time beyond base, covered by bank top-ups / earned unlocks
    val overMin: Int,          // entertainment time beyond everything granted (should be ~0 when enforced)
    val remainingMin: Int,     // minutes left before the app locks
)

object Allowance {
    /**
     * [totalMs] = Android foreground time today. [studyMs] = time the accessibility detector attributed to verified study
     * (clamped to total, never negative). Entertainment = total - study.
     * [baseMin] free minutes; [grantedMin] minutes added today by top-ups or earned unlocks; [limitMin] hard daily cap.
     */
    fun split(totalMs: Long, studyMs: Long, baseMin: Int, grantedMin: Int, limitMin: Int): AllowanceSplit {
        val study = studyMs.coerceIn(0L, totalMs.coerceAtLeast(0L))
        val ent = (totalMs - study).coerceAtLeast(0L) / 60_000.0
        val base = baseMin.coerceAtLeast(0)
        val baseUsed = minOf(ent, base.toDouble())
        val allowed = minOf((base + grantedMin).toDouble(), limitMin.toDouble())
        val bankUsed = (minOf(ent, allowed) - baseUsed).coerceAtLeast(0.0)
        val over = (ent - allowed).coerceAtLeast(0.0)
        return AllowanceSplit(baseUsed.toInt(), (study / 60_000L).toInt(), bankUsed.toInt(), over.toInt(), (allowed - ent).coerceAtLeast(0.0).toInt())
    }

    /** Bank-funded minutes implied by measured foreground time: entertainment above the base, net of earned (free) grants. */
    fun bankUsedFromMeasured(totalMs: Long, studyMs: Long, baseMin: Int, earnedGrantMin: Int): Int {
        val ent = (totalMs - studyMs.coerceIn(0L, totalMs.coerceAtLeast(0L))).coerceAtLeast(0L) / 60_000.0
        return (ent - baseMin.coerceAtLeast(0) - earnedGrantMin.coerceAtLeast(0)).coerceAtLeast(0.0).toInt()
    }

    /** Offered top-up sizes, only those the bank can currently pay. Nothing is ever spent without confirmation. */
    fun topUpOptions(balance: Int, sizes: List<Int> = listOf(10, 15, 30)): List<Int> = sizes.filter { it <= balance }
}
