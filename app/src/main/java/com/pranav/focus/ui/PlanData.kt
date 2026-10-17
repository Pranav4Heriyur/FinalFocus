package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pranav.focus.data.PlanDates
import com.pranav.focus.data.Store
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** One planned block on the agenda. [start] and [dur] are minutes (start = minutes since midnight). */
data class PlanTask(
    val id: String,
    val date: String,
    val title: String,
    val category: String,   // Study | Workout | Personal | Other
    val subject: String,    // Physics | Chemistry | Biology | Workout | Personal | Other
    val start: Int,
    val dur: Int,
    val priority: Int = 1,  // 0 Low, 1 Medium, 2 High
    val notes: String = "",
    val done: Boolean = false,
    val checklist: List<Pair<String, Boolean>> = emptyList(),
) {
    val end get() = start + dur
    fun overlaps(o: PlanTask) = id != o.id && date == o.date && start < o.end && o.start < end
}

val PLAN_CATEGORIES = listOf("Study", "Workout", "Personal", "Other")
val PLAN_SUBJECTS = listOf("Physics", "Chemistry", "Biology")
val PLAN_PRIORITIES = listOf("Low", "Medium", "High")
val PLAN_SUBJECT_ORDER = listOf("Physics", "Chemistry", "Biology", "Workout", "Personal", "Other")
const val PLAN_DAY_MINUTES = 14 * 60 // planning window used by the "planned / 14h" meter

fun fmtT(m: Int): String {
    val h = (m / 60) % 24
    val ap = if (h < 12) "AM" else "PM"
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%02d:%02d %s".format(h12, m % 60, ap)
}

fun fmtD(m: Int): String = "${m / 60}h ${m % 60}m"

object PlanState {
    var tasks by mutableStateOf<List<PlanTask>>(emptyList())
    var date by mutableStateOf(AppClock.today())
    var selectedId by mutableStateOf<String?>(null)
    private var loaded = false
    private var seenToday = AppClock.today()

    /** The real current local date (single clock). */
    val today: LocalDate get() = AppClock.today()

    /** Jump to the actual current date (fresh entry / "Today" button). */
    fun selectToday() { seenToday = AppClock.today(); date = seenToday }

    /**
     * Call on resume and at midnight. Moves a selection that was following "today" onto the new day,
     * but never overwrites an intentionally selected historical or future date.
     */
    fun syncToday() {
        val n = AppClock.today()
        if (n != seenToday) { date = PlanDates.followToday(date, seenToday, n); seenToday = n }
    }

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        // No sample data: a new install is genuinely empty. If stored data cannot be parsed it is
        // kept as a backup and never silently overwritten by an empty list.
        val store = Store.get(ctx)
        val raw = store.str("plan_tasks", "")
        tasks = if (raw.isBlank()) emptyList() else runCatching { decode(raw) }.getOrElse {
            store.putStr("plan_tasks_unreadable_backup", raw)
            emptyList()
        }
    }

    fun save(ctx: Context) = Store.get(ctx).putStr("plan_tasks", encode(tasks))

    fun upsert(ctx: Context, t: PlanTask) {
        tasks = if (tasks.any { it.id == t.id }) tasks.map { if (it.id == t.id) t else it } else tasks + t
        save(ctx)
    }

    fun delete(ctx: Context, id: String) { tasks = tasks.filter { it.id != id }; save(ctx) }

    fun get(id: String?) = tasks.firstOrNull { it.id == id }

    fun forDate(d: LocalDate = date) = tasks.filter { it.date == d.toString() }.sortedWith(compareBy<PlanTask>({ it.start }, { it.end }, { it.id }))

    fun newId() = UUID.randomUUID().toString()

    private fun encode(l: List<PlanTask>) = JSONArray(l.map {
        JSONObject().put("id", it.id).put("date", it.date).put("title", it.title).put("cat", it.category).put("subj", it.subject)
            .put("start", it.start).put("dur", it.dur).put("pr", it.priority).put("notes", it.notes).put("done", it.done)
            .put("cl", JSONArray(it.checklist.map { c -> JSONObject().put("t", c.first).put("d", c.second) }))
    }).toString()

    private fun decode(s: String): List<PlanTask> {
        val a = JSONArray(s)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val cl = o.optJSONArray("cl")
            PlanTask(
                o.getString("id"), o.getString("date"), o.getString("title"), o.getString("cat"), o.getString("subj"),
                o.getInt("start"), o.getInt("dur"), o.optInt("pr", 1), o.optString("notes"), o.optBoolean("done"),
                if (cl == null) emptyList() else (0 until cl.length()).map { i -> cl.getJSONObject(i).let { c -> c.getString("t") to c.optBoolean("d") } },
            )
        }
    }
}

/** Planner preferences (A18_11), persisted in the app Store. */
object PlanPrefs {
    fun showDone(c: Context) = Store.get(c).bool("plan_show_done", true)
    fun showFree(c: Context) = Store.get(c).bool("plan_show_free", true)
    fun quickHome(c: Context) = Store.get(c).bool("plan_quick_home", true)
    fun defStart(c: Context) = Store.get(c).int("plan_def_start", 540)
    fun defDur(c: Context) = Store.get(c).int("plan_def_dur", 60)
    fun remind(c: Context) = Store.get(c).int("plan_remind", 10)
}
