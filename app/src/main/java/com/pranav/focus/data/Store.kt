package com.pranav.focus.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** All app data lives in one private SharedPreferences file. Tokens are encrypted separately (see Secure). */
class Store private constructor(ctx: Context) {
    private val app = ctx.applicationContext
    val sp: SharedPreferences = app.getSharedPreferences("focus", Context.MODE_PRIVATE)

    // ---- tiny helpers -----------------------------------------------------------
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun int(k: String, d: Int = 0): Int = sp.getInt(k, d)
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun long(k: String, d: Long = 0): Long = sp.getLong(k, d)
    fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()
    fun bool(k: String, d: Boolean = false): Boolean = sp.getBoolean(k, d)
    fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun strSet(k: String): Set<String> = sp.getStringSet(k, emptySet()) ?: emptySet()
    fun putSet(k: String, v: Set<String>) = sp.edit().putStringSet(k, v.toSet()).apply()

    fun today(): String = AppClock.today().toString()

    // ---- session -------------------------------------------------------------
    val sessionActive: Boolean get() = bool("s_active")
    val sessionMinutes: Int get() = int("s_minutes")
    val sessionName: String get() = str("s_name")
    val sessionSubject: String get() = str("s_subject", "Other")

    private fun bootCount(): Int = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)

    fun startSession(minutes: Int, name: String, subject: String) {
        val ms = minutes * 60_000L
        sp.edit()
            .putBoolean("s_active", true)
            .putString("s_name", name)
            .putString("s_subject", subject)
            .putInt("s_minutes", minutes)
            .putLong("s_end_wall", System.currentTimeMillis() + ms)
            .putLong("s_end_elapsed", SystemClock.elapsedRealtime() + ms)
            .putInt("s_boot", bootCount())
            .commit()
    }

    fun endSession() {
        sp.edit().putBoolean("s_active", false).commit()
    }

    /** Monotonic clock within a boot (immune to clock changes); wall clock after a reboot. */
    fun remainingMs(): Long {
        if (!sessionActive) return 0
        val sameBoot = bootCount() == int("s_boot", -2)
        val rem = if (sameBoot) long("s_end_elapsed") - SystemClock.elapsedRealtime()
        else long("s_end_wall") - System.currentTimeMillis()
        return rem.coerceAtLeast(0)
    }

    // ---- focus session settings ------------------------------------------------
    var allowedApps: Set<String>
        get() = strSet("allowed_apps")
        set(v) = putSet("allowed_apps", v)

    var strictLock: Boolean
        get() = bool("strict_lock", true)
        set(v) = putBool("strict_lock", v)

    var lastMinutes: Int
        get() = int("last_minutes", 25)
        set(v) = putInt("last_minutes", v)

    var lastName: String
        get() = str("last_name")
        set(v) = putStr("last_name", v)

    var lastSubject: String
        get() = str("last_subject", "Physics")
        set(v) = putStr("last_subject", v)

    fun ensureDefaults(defaultPackages: Set<String>) {
        if (!bool("defaults_done")) {
            allowedApps = defaultPackages
            putBool("defaults_done", true)
        }
    }

    // ---- callers ------------------------------------------------------------
    var callersOnly: Boolean
        get() = bool("callers_only", true)
        set(v) = putBool("callers_only", v)

    var callers: List<Caller>
        get() = Codec.callers(str("callers", "[]"))
        set(v) = putStr("callers", Codec.callers(v))

    private fun weekKey(): String {
        val d = AppClock.today()
        return "blocked_" + d.minusDays((d.dayOfWeek.value - 1).toLong())
    }

    fun addBlockedCall() = putInt("${weekKey()}_${AppClock.today().dayOfWeek.value}", int("${weekKey()}_${AppClock.today().dayOfWeek.value}") + 1)
    fun blockedCallsThisWeek(): List<Int> = (1..7).map { int("${weekKey()}_$it") }

    // ---- rules ---------------------------------------------------------------
    var appRules: List<AppRule>
        get() = Codec.appRules(str("app_rules", "[]"))
        set(v) = putStr("app_rules", Codec.appRules(v))

    var unlockRules: List<UnlockRule>
        get() = Codec.unlockRules(str("unlock_rules", "[]"))
        set(v) = putStr("unlock_rules", Codec.unlockRules(v))

    var blockedDuring: Set<String>
        get() = strSet("blocked_during")
        set(v) = putSet("blocked_during", v)

    var blockedAlways: Set<String>
        get() = strSet("blocked_always")
        set(v) = putSet("blocked_always", v)

    /** Master switch ("Disable app lock" in Profile). */
    var restrictionsEnabled: Boolean
        get() = bool("restrictions_on", true)
        set(v) = putBool("restrictions_on", v)

    // ---- daily record (expires at midnight) -------------------------------------
    fun day(date: String = today()): DayRec = DayRec.fromJson(sp.getString("day_$date", null))

    fun saveDay(r: DayRec, date: String = today()) {
        val e = sp.edit().putString("day_$date", r.toJson())
        val cutoff = AppClock.today().minusDays(45).toString()
        sp.all.keys.filter { it.startsWith("day_") && it.substring(4) < cutoff }.forEach { e.remove(it) }
        e.apply()
    }

    /** Minutes in the pool that can still be spent on unlocking apps today. */
    val bankMinutes: Int get() { rollover(); return ledger.balance() }

    // ---- persistent Time Bank ledger (never expires at midnight) -------------------
    @Volatile private var ledgerRef: Ledger? = null
    @Volatile private var xpRef: XpLedger? = null

    val ledger: Ledger get() = ledgerRef ?: synchronized(this) { ledgerRef ?: openLedger().also { ledgerRef = it } }
    val xpLedger: XpLedger get() = xpRef ?: synchronized(this) { xpRef ?: openXp().also { xpRef = it } }

    /** v1 -> v2 migration: carry today's spendable pool over as one OPENING event; nothing is lost or doubled. */
    private fun openLedger(): Ledger {
        val persist = { l: List<LedgerEvent> -> sp.edit().putString("ledger_v2", Ledger.encode(l)).commit(); Unit }
        if (sp.contains("ledger_v2")) return Ledger(Ledger.decode(str("ledger_v2")), persist)
        val d = day(); val t = today()
        val future = sp.all.keys.filter { it.startsWith("day_") && it.substring(4) > t }.sumOf { day(it.substring(4)).penalty }
        val open = (TimeBank.poolAvailable(d.pool, d.penalty, d.fromPool) - future).coerceAtLeast(0)
        val l = Ledger(emptyList(), persist)
        if (open > 0) l.append(LedgerEvent("opening:v2", System.currentTimeMillis(), t, LedgerType.OPENING, open, ref = "migrated"))
        else persist(emptyList())
        return l
    }

    private fun openXp(): XpLedger {
        val persist = { l: List<XpEvent> -> sp.edit().putString("xp_ledger_v1", XpLedger.encode(l)).commit(); Unit }
        if (sp.contains("xp_ledger_v1")) return XpLedger(XpLedger.decode(str("xp_ledger_v1")), persist)
        val legacy = int("xp"); val x = XpLedger(emptyList(), persist)
        if (legacy > 0) x.record(XpEvent("opening:xp:v1", System.currentTimeMillis(), today(), "migrated", legacy)) else persist(emptyList())
        return x
    }

    /** Apply penalties that came due today and refund unused top-ups from earlier days. Idempotent. */
    @Synchronized fun rollover() {
        val t = today(); val now = System.currentTimeMillis()
        val pen = day(t).penalty
        if (pen > 0) ledger.penalty("pen:$t", now, t, pen, "mirror")
        ledger.settlePast(t, now) { d0, pkg -> verifiedBankUse(d0, pkg) }
    }

    /**
     * Bank-funded minutes actually used on [date] for [pkg], or null when unverifiable.
     * Takes the LARGER of (a) the in-app snapshot the Enforcer kept and (b) a fresh reconstruction from Android's usage
     * events for that local day, so a stopped Enforcer can only lower a refund, never raise it. With no usage access, or no
     * events retained for that day, we return null and the reservation stays unsettled rather than being over-credited.
     */
    private fun verifiedBankUse(date: String, pkg: String): Int? {
        val (from, to) = PlanDates.dayBoundsMs(LocalDate.parse(date), AppClock.zone())
        if (!com.pranav.focus.ui.UsageHelper.windowVerifiable(app, from, to)) return null
        val rule = appRules.firstOrNull { it.pkg == pkg }
        val base = if (rule == null) 0 else if (rule.locked) rule.baseMin else rule.limitMin   // unknown rule -> assume nothing was free
        val measured = Allowance.bankUsedFromMeasured(
            com.pranav.focus.ui.UsageHelper.usedBetweenMs(app, from, to)[pkg] ?: 0L, studyMs(pkg, date), base, day(date).alloc[pkg] ?: 0)
        val snapshot = (int("bankuse_${date}_$pkg") - (day(date).alloc[pkg] ?: 0)).coerceAtLeast(0)
        return maxOf(measured, snapshot)
    }

    /** Idempotent XP award. Returns false when [id] was already recorded. */
    fun awardXp(id: String, source: String, delta: Int): Boolean = xpLedger.record(XpEvent(id, System.currentTimeMillis(), today(), source, delta))

    // ---- YouTube study exemptions -----------------------------------------------------
    var studyChannels: List<StudyChannel>
        get() = str("study_channels").lines().mapNotNull { ln ->
            val p = ln.split('\t')
            if (p.size < 5) null else runCatching { StudyChannel(p[0], p[1], ExemptKind.valueOf(p[2]), p[3] == "1", p[4]) }.getOrNull()
        }
        set(v) = putStr("study_channels", v.joinToString("\n") { c ->
            listOf(c.id, c.name, c.kind.name, if (c.enabled) "1" else "0", c.handle).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
        })
    /** Detection is opt-in and off by default; it needs the Accessibility service to read the YouTube window. */
    var studyDetect: Boolean get() = bool("study_detect"); set(v) = putBool("study_detect", v)
    /** Weaker, spoofable name-only matching. Off by default and never reported as verified. */
    var studyNameMatch: Boolean get() = bool("study_name_match"); set(v) = putBool("study_name_match", v)

    @Synchronized fun addStudyMs(pkg: String, ms: Long, date: String = today()) {
        if (ms > 0) sp.edit().putLong("sx_${date}_$pkg", long("sx_${date}_$pkg") + ms).commit()
    }
    fun studyMs(pkg: String, date: String = today()): Long = long("sx_${date}_$pkg")

    // ---- progress ------------------------------------------------------------
    /** Derived from the XP event ledger; award via [awardXp] so every grant is idempotent. */
    val xp: Int get() = xpLedger.total()

    var totalFocusMinutes: Int
        get() = int("total_minutes")
        set(v) = putInt("total_minutes", v)

    /** Focus minutes per day: date -> minutes. Kept for ~400 days. */
    fun focusDays(): Map<String, Int> = Codec.intMap(str("focus_days"))

    val todayFocusMinutes: Int get() = focusDays()[today()] ?: 0

    private fun addFocus(minutes: Int) {
        val m = Codec.intMap(str("focus_days"))
        m[today()] = (m[today()] ?: 0) + minutes
        val cutoff = AppClock.today().minusDays(400).toString()
        m.keys.removeAll { it < cutoff }
        putStr("focus_days", Codec.intMap(m))
    }

    /** Current streak: consecutive days with a completed focus session, ending today or yesterday. */
    val streak: Int
        get() {
            val days = focusDays()
            var d = AppClock.today()
            if (!ProgressionPolicy.streakQualifies(days[d.toString()] ?: 0)) d = d.minusDays(1)
            var n = 0
            while (ProgressionPolicy.streakQualifies(days[d.toString()] ?: 0)) { n++; d = d.minusDays(1) }
            return n
        }

    /** Idempotent per session (keyed by its end stamp): replays after an interrupted completion add nothing. */
    @Synchronized fun recordCompleted(minutes: Int): Int {
        val key = "sess_${long("s_end_wall")}"
        if (sp.contains("done_$key")) return int("done_$key")
        val earned = TimeBank.earnedFromFocus(minutes)
        awardXp("focus:$key", "focus", Rewards.xpForSession(minutes))
        ledger.earn("earn:$key", System.currentTimeMillis(), today(), earned, "focus")
        totalFocusMinutes += minutes
        addFocus(minutes)
        val d = day(); d.earnedTotal += earned; saveDay(d)
        sp.edit().putInt("done_$key", earned).commit()
        return earned
    }

    @Synchronized fun recordQuit() {
        awardXp("quit:sess_${long("s_end_wall")}", "quit", -Rewards.QUIT_XP_PENALTY)
    }

    // ---- activities (Strava cache) -----------------------------------------------
    var activities: List<Activity>
        get() = Codec.activities(str("activities", "[]"))
        set(v) = putStr("activities", Codec.activities(v))

    fun activitiesOn(date: String): List<Activity> = activities.filter { it.startDate == date }

    /** Questions attempted (correct + wrong) on a local date, derived from the saved study logs (the single source of truth). */
    fun studyAttemptedOn(date: String): Int {
        val (from, to) = PlanDates.dayBoundsMs(LocalDate.parse(date), AppClock.zone())
        return StudyDb.get(app).attemptedBetween(from, to)
    }

    val studyToday: Int get() = studyAttemptedOn(today())

    // ---- preferences -----------------------------------------------------------
    var watchMe: Boolean
        get() = bool("watch_me", true)
        set(v) {
            if (v == watchMe && sp.contains("watch_me")) return
            putBool("watch_me", v)
            val now = System.currentTimeMillis()
            if (v) putLong("watch_open", now)
            else {
                val start = long("watch_open", 0)
                if (start > 0 && now - start > 60_000) {
                    val arr = JSONArray(str("watch_windows", "[]"))
                    arr.put(JSONObject().put("s", start).put("e", now))
                    val keep = JSONArray()
                    val cutoff = now - 60L * 86_400_000L
                    for (i in 0 until arr.length()) if (arr.getJSONObject(i).getLong("e") > cutoff) keep.put(arr.getJSONObject(i))
                    putStr("watch_windows", keep.toString())
                }
                putLong("watch_open", 0)
            }
        }

    /** Closed windows plus the currently open one. Pairs of (startMs, endMs). */
    fun watchWindows(): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        runCatching {
            val arr = JSONArray(str("watch_windows", "[]"))
            for (i in 0 until arr.length()) out.add(arr.getJSONObject(i).getLong("s") to arr.getJSONObject(i).getLong("e"))
        }
        val open = long("watch_open", 0)
        if (watchMe && open > 0) out.add(open to System.currentTimeMillis())
        return out
    }

    var imperial: Boolean
        get() = bool("imperial")
        set(v) = putBool("imperial", v)

    /** 0 = Normal, 1 = Hard, 2 = Goggins */
    var tone: Int
        get() = int("tone", 2)
        set(v) = putInt("tone", v)

    var setupDone: Boolean
        get() = bool("setup_done")
        set(v) = putBool("setup_done", v)

    // ---- security --------------------------------------------------------------
    var biometricLock: Boolean
        get() = bool("sec_bio")
        set(v) = putBool("sec_bio", v)

    var lockOnExit: Boolean
        get() = bool("sec_exit")
        set(v) = putBool("sec_exit", v)

    var hideInRecents: Boolean
        get() = bool("sec_recents")
        set(v) = putBool("sec_recents", v)

    /** Seconds in the background before the app re-locks; 0 = immediately, -1 = never. */
    var autoLockSeconds: Int
        get() = int("sec_auto", 30)
        set(v) = putInt("sec_auto", v)

    // ---- cookie jar, mirror ------------------------------------------------------
    var jar: List<Entry>
        get() = Codec.entries(str("jar", "[]"))
        set(v) = putStr("jar", Codec.entries(v))

    var avoided: List<Entry>
        get() = Codec.entries(str("avoided", "[]"))
        set(v) = putStr("avoided", Codec.entries(v))

    /** Per-day review answers: agenda item id -> done. Stored as ids marked done for the day. */
    fun reviewDone(date: String = today()): Set<String> = strSet("review_done_$date")
    fun putReviewDone(ids: Set<String>, date: String = today()) = putSet("review_done_$date", ids)
    fun reviewSubmitted(date: String = today()): Boolean = bool("review_submitted_$date")
    fun putReviewSubmitted(date: String = today()) = putBool("review_submitted_$date", true)

    // ---- weekly targets --------------------------------------------------------
    var targetScale: Float
        get() = sp.getFloat("target_scale", 1f)
        set(v) = sp.edit().putFloat("target_scale", v).apply()

    var targetWeek: String
        get() = str("target_week")
        set(v) = putStr("target_week", v)

    // ---- wipe ------------------------------------------------------------------
    fun resetAll() {
        sp.edit().clear().commit(); ledgerRef = null; xpRef = null
    }

    companion object {
        @Volatile private var inst: Store? = null
        fun get(ctx: Context): Store =
            inst ?: synchronized(this) { inst ?: Store(ctx).also { inst = it } }
    }
}
