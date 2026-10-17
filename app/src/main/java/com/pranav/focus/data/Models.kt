package com.pranav.focus.data

import org.json.JSONArray
import org.json.JSONObject

enum class Cond(val title: String, val sub: String, val unit: String, val source: String) {
    RUN("Run distance", "Track runs via Health Connect", "km", "Health Connect"),
    RIDE("Ride distance", "Track rides via Health Connect", "km", "Health Connect"),
    WORKOUT("Workout done", "Log a completed workout", "workout", "Health"),
    FOCUS("Focus minutes", "Complete a focus session", "min", "Focus"),
    STUDY("Study questions", "Answer questions with accuracy", "questions", "Study"),
}

data class AppRule(val pkg: String, val locked: Boolean = true, val limitMin: Int = 30, val baseMin: Int = 0)

data class UnlockRule(val id: String, val cond: Cond, val target: Int, val pkg: String, val minutes: Int) {
    fun title(): String = when (cond) {
        Cond.RUN -> "Run $target km"
        Cond.RIDE -> "Ride $target km"
        Cond.WORKOUT -> if (target == 1) "Complete a workout" else "Complete $target workouts"
        Cond.FOCUS -> "Focus for $target min"
        Cond.STUDY -> "Solve $target questions"
    }
}

/** Everything that expires at midnight. */
data class DayRec(
    var pool: Int = 0,
    var fromPool: Int = 0,
    var earnedTotal: Int = 0,
    var penalty: Int = 0,
    val alloc: MutableMap<String, Int> = HashMap(),
    val granted: MutableSet<String> = HashSet(),
    val actions: MutableSet<String> = HashSet(),
) {
    fun toJson(): String = JSONObject()
        .put("pool", pool).put("fromPool", fromPool).put("earnedTotal", earnedTotal).put("penalty", penalty)
        .put("alloc", JSONObject(alloc as Map<*, *>))
        .put("granted", JSONArray(granted.toList()))
        .put("actions", JSONArray(actions.toList()))
        .toString()

    companion object {
        fun fromJson(s: String?): DayRec {
            if (s.isNullOrBlank()) return DayRec()
            return runCatching {
                val o = JSONObject(s)
                val r = DayRec(o.optInt("pool"), o.optInt("fromPool"), o.optInt("earnedTotal"), o.optInt("penalty"))
                o.optJSONObject("alloc")?.let { a -> a.keys().forEach { k -> r.alloc[k] = a.getInt(k) } }
                o.optJSONArray("granted")?.let { a -> for (i in 0 until a.length()) r.granted.add(a.getString(i)) }
                o.optJSONArray("actions")?.let { a -> for (i in 0 until a.length()) r.actions.add(a.getString(i)) }
                r
            }.getOrDefault(DayRec())
        }
    }
}

data class Activity(
    val id: Long, val type: String, val name: String, val startMs: Long, val startDate: String,
    val distM: Double, val movingS: Int, val avgHr: Double, val elevM: Double, val polyline: String,
) {
    val isRun get() = type == "Run" || type == "TrailRun" || type == "VirtualRun"
    val isRide get() = type == "Ride" || type == "VirtualRide" || type == "EBikeRide" || type == "GravelRide" || type == "MountainBikeRide"
    val isWalk get() = type == "Walk" || type == "Hike"
    val isWorkout get() = type in setOf("Workout", "WeightTraining", "Crossfit", "HighIntensityIntervalTraining", "Yoga", "Pilates")
}

data class Entry(val id: String, val dateMs: Long, val title: String, val note: String)
data class AgendaItem(val id: String, val title: String, val sub: String, val icon: String, val time: String = "")
data class Caller(val name: String, val number: String, val enabled: Boolean = true)

object Codec {
    fun appRules(l: List<AppRule>): String = JSONArray(l.map { JSONObject().put("pkg", it.pkg).put("locked", it.locked).put("limit", it.limitMin).put("base", it.baseMin) }).toString()
    fun appRules(s: String?): List<AppRule> = arr(s) { AppRule(it.getString("pkg"), it.optBoolean("locked", true), it.optInt("limit", 30), it.optInt("base", 0)) }

    fun unlockRules(l: List<UnlockRule>): String = JSONArray(l.map {
        JSONObject().put("id", it.id).put("cond", it.cond.name).put("target", it.target).put("pkg", it.pkg).put("min", it.minutes)
    }).toString()

    fun unlockRules(s: String?): List<UnlockRule> = arr(s) {
        UnlockRule(it.getString("id"), Cond.valueOf(it.getString("cond")), it.getInt("target"), it.getString("pkg"), it.getInt("min"))
    }

    fun activities(l: List<Activity>): String = JSONArray(l.map {
        JSONObject().put("id", it.id).put("type", it.type).put("name", it.name).put("start", it.startMs).put("date", it.startDate)
            .put("dist", it.distM).put("time", it.movingS).put("hr", it.avgHr).put("elev", it.elevM).put("poly", it.polyline)
    }).toString()

    fun activities(s: String?): List<Activity> = arr(s) {
        Activity(it.getLong("id"), it.getString("type"), it.optString("name"), it.getLong("start"), it.getString("date"),
            it.optDouble("dist"), it.optInt("time"), it.optDouble("hr", 0.0), it.optDouble("elev", 0.0), it.optString("poly"))
    }

    fun entries(l: List<Entry>): String = JSONArray(l.map { JSONObject().put("id", it.id).put("date", it.dateMs).put("title", it.title).put("note", it.note) }).toString()
    fun entries(s: String?): List<Entry> = arr(s) { Entry(it.getString("id"), it.getLong("date"), it.getString("title"), it.optString("note")) }

    fun agenda(l: List<AgendaItem>): String = JSONArray(l.map { JSONObject().put("id", it.id).put("title", it.title).put("sub", it.sub).put("icon", it.icon).put("t", it.time) }).toString()
    fun agenda(s: String?): List<AgendaItem> = arr(s) { AgendaItem(it.getString("id"), it.getString("title"), it.optString("sub"), it.optString("icon"), it.optString("t")) }

    fun callers(l: List<Caller>): String = JSONArray(l.map { JSONObject().put("name", it.name).put("number", it.number).put("on", it.enabled) }).toString()
    fun callers(s: String?): List<Caller> = arr(s) { Caller(it.getString("name"), it.getString("number"), it.optBoolean("on", true)) }

    fun intMap(m: Map<String, Int>): String = JSONObject(m as Map<*, *>).toString()
    fun intMap(s: String?): MutableMap<String, Int> {
        val out = HashMap<String, Int>()
        if (s.isNullOrBlank()) return out
        runCatching { val o = JSONObject(s); o.keys().forEach { k -> out[k] = o.getInt(k) } }
        return out
    }

    private inline fun <T> arr(s: String?, f: (JSONObject) -> T): List<T> {
        if (s.isNullOrBlank()) return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { f(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}
