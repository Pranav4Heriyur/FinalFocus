package com.pranav.focus.data

/** Types of Time Bank movements. [delta] on the event is the signed change to the balance. */
enum class LedgerType { OPENING, EARN, PENALTY, TOPUP_RESERVE, TOPUP_REFUND }

/** One append-only ledger row. [id] is globally unique; [day] is the local calendar day (yyyy-MM-dd) it belongs to. */
data class LedgerEvent(val id: String, val ts: Long, val day: String, val type: LedgerType, val delta: Int, val pkg: String = "", val ref: String = "")

/**
 * Append-only Time Bank ledger. Pure (no Android) so it is unit-testable; persistence is injected.
 * Balance = sum of deltas and never expires at midnight. Every mutation is idempotent on the event id
 * and serialised by one lock, so concurrent spends cannot overdraw and duplicates cannot double-charge.
 */
class Ledger(initial: List<LedgerEvent>, private val persist: (List<LedgerEvent>) -> Unit = {}) {
    private val events = ArrayList(initial)
    private val ids = HashSet(initial.map { it.id })

    @Synchronized fun all(): List<LedgerEvent> = events.toList()
    @Synchronized fun balance(): Int = events.sumOf { it.delta }.coerceAtLeast(0)
    @Synchronized fun has(id: String) = id in ids

    /** Returns false (and changes nothing) when [e] was already recorded. */
    @Synchronized fun append(e: LedgerEvent): Boolean {
        if (!ids.add(e.id)) return false
        events.add(e); persist(events.toList()); return true
    }

    @Synchronized fun earn(id: String, ts: Long, day: String, minutes: Int, ref: String = ""): Boolean =
        minutes > 0 && append(LedgerEvent(id, ts, day, LedgerType.EARN, minutes, ref = ref))

    @Synchronized fun penalty(id: String, ts: Long, day: String, minutes: Int, ref: String = ""): Boolean =
        minutes > 0 && append(LedgerEvent(id, ts, day, LedgerType.PENALTY, -minOf(minutes, balance()), ref = ref))

    /**
     * Reserve up to [want] minutes for a top-up (needs prior user confirmation). Grants min(want, balance).
     * Replaying the same [id] returns the original grant and charges nothing more.
     */
    @Synchronized fun reserveTopUp(id: String, ts: Long, day: String, pkg: String, want: Int): Int {
        events.firstOrNull { it.id == id }?.let { return -it.delta }
        val granted = minOf(want, balance())
        if (granted <= 0) return 0
        append(LedgerEvent(id, ts, day, LedgerType.TOPUP_RESERVE, -granted, pkg))
        return granted
    }

    /** Minutes reserved by [reserveId] not yet refunded. */
    @Synchronized fun grantOf(reserveId: String): Int =
        events.firstOrNull { it.id == reserveId && it.type == LedgerType.TOPUP_RESERVE }?.let { -it.delta } ?: 0

    /** Settle a reservation: refund the part of the grant that was not [usedMin]. Idempotent per reservation. */
    @Synchronized fun settleTopUp(reserveId: String, usedMin: Int, ts: Long, day: String): Int {
        val r = events.firstOrNull { it.id == reserveId && it.type == LedgerType.TOPUP_RESERVE } ?: return 0
        val refund = (-r.delta - usedMin.coerceAtLeast(0)).coerceAtLeast(0)
        if (refund == 0) { append(LedgerEvent("settle:$reserveId", ts, day, LedgerType.TOPUP_REFUND, 0, r.pkg, reserveId)); return 0 }
        return if (append(LedgerEvent("settle:$reserveId", ts, day, LedgerType.TOPUP_REFUND, refund, r.pkg, reserveId))) refund else 0
    }

    /** Reservations from days before [today] that have no settlement yet. */
    @Synchronized fun unsettledBefore(today: String): List<LedgerEvent> =
        events.filter { it.type == LedgerType.TOPUP_RESERVE && it.day < today && "settle:${it.id}" !in ids }

    /**
     * Canonical top-up entitlement for [pkg] on [day], rebuilt purely from reservations. There is no second
     * "granted" counter anywhere, so a crash between reserving and applying cannot leave the two disagreeing.
     */
    @Synchronized fun topUpGrantedOn(day: String, pkg: String): Int =
        events.filter { it.type == LedgerType.TOPUP_RESERVE && it.day == day && it.pkg == pkg }.sumOf { -it.delta }

    /** Minutes of a reservation returned to the wallet (0 until settled). */
    @Synchronized fun refundedOf(reserveId: String): Int = events.firstOrNull { it.id == "settle:$reserveId" }?.delta ?: 0
    /** Reserved - refunded, once settled. */
    @Synchronized fun consumedOf(reserveId: String): Int = if (has("settle:$reserveId")) grantOf(reserveId) - refundedOf(reserveId) else 0

    /**
     * Settle every reservation from days before [today] exactly once. [bankUsed] gives the bank-funded minutes
     * actually consumed for (day, pkg), or null when that cannot be verified (the group then stays reserved and is retried later).
     * It is spread over that group's reservations in order, and the rest is refunded.
     * Safe to call repeatedly or after a crash: settlement events are keyed on the reservation id.
     */
    @Synchronized fun settlePast(today: String, ts: Long, bankUsed: (String, String) -> Int?): Int {
        var refunded = 0
        val groups = events.filter { it.type == LedgerType.TOPUP_RESERVE && it.day < today }.groupBy { it.day to it.pkg }
        for ((k, rs) in groups) {
            if (rs.none { "settle:${it.id}" !in ids }) continue
            var budget = (bankUsed(k.first, k.second) ?: continue).coerceAtLeast(0)   // null = cannot verify: defer, never guess
            for (r in rs) {
                val used = minOf(-r.delta, budget); budget -= used
                if ("settle:${r.id}" !in ids) refunded += settleTopUp(r.id, used, ts, today)
            }
        }
        return refunded
    }

    /** Net minutes spent on top-ups for [day] (reserved minus refunded). */
    @Synchronized fun spentOn(day: String): Int =
        events.filter { it.day == day && (it.type == LedgerType.TOPUP_RESERVE || it.type == LedgerType.TOPUP_REFUND) }.sumOf { -it.delta }
    @Synchronized fun earnedOn(day: String): Int = events.filter { it.day == day && it.type == LedgerType.EARN }.sumOf { it.delta }
    /** Minutes already credited on [day] under one earn source (for example "study_q"). */
    @Synchronized fun earnedFrom(day: String, ref: String): Int = events.filter { it.day == day && it.type == LedgerType.EARN && it.ref == ref }.sumOf { it.delta }
    @Synchronized fun penaltyOn(day: String): Int = events.filter { it.day == day && it.type == LedgerType.PENALTY }.sumOf { -it.delta }

    companion object {
        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
        fun encode(l: List<LedgerEvent>): String = l.joinToString("\n") {
            listOf(clean(it.id), it.ts, it.day, it.type.name, it.delta, clean(it.pkg), clean(it.ref)).joinToString("\t")
        }
        fun decode(s: String?): List<LedgerEvent> = s.orEmpty().lines().mapNotNull { ln ->
            val p = ln.split('\t'); if (p.size < 7) return@mapNotNull null
            runCatching { LedgerEvent(p[0], p[1].toLong(), p[2], LedgerType.valueOf(p[3]), p[4].toInt(), p[5], p[6]) }.getOrNull()
        }
    }
}
