package com.pranav.focus.workout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.data.AppClock
import com.pranav.focus.net.HealthSync
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico
import com.pranav.focus.ui.Screen
import java.time.ZoneId

/** Cross-screen workout navigation state (survives recomposition; a plain object like PlanState in the Plan section). */
object WorkoutNav {
    var tick by mutableIntStateOf(0)
    var planId by mutableStateOf<String?>(null)
    var routineId by mutableStateOf<String?>(null)
    var slot by mutableIntStateOf(1)
    var sessionId by mutableStateOf<String?>(null)
    var createTemplate by mutableStateOf<String?>(null)
    var draft by mutableStateOf<DayDraft?>(null)
    var summary by mutableStateOf<String?>(null)
    /** C10: the saved session to show, and a one-shot flag set by C9 so the Active screen forwards to C10 after C9 pops. */
    var completedSessionId by mutableStateOf<String?>(null)
    var pendingComplete by mutableStateOf(false)
    /** C12 integration point: when History/Details exists, set this to navigate to it with the session id. Null until C12 is built. */
    var openWorkoutDetails: ((String) -> Unit)? = null
    var detailSessionId by mutableStateOf<String?>(null)
    /** Opens the full C12 history list (set by WorkoutHost). */
    var openHistory: (() -> Unit)? = null
    /** Opens C11 (set by WorkoutHost). Diagram edge C12 -> C11. */
    var openStats: (() -> Unit)? = null
    /** Set by the Active Workout screen so the existing Set Entry (C9) opens on the chosen exercise. */
    var entryExerciseId by mutableStateOf<String?>(null)
    fun bump() { tick++ }
}

/** Working copy of one plan day while the Day Builder is open. Nothing is written until Save Day. */
data class DayDraft(
    val planId: String, val slot: Int, val morning: String?, val altMorning: String?, val gym: String?, val notes: String,
    val lines: List<Line>, val dirty: Boolean = false,
)

@Composable
fun rememberRepo(): WorkoutRepo { val ctx = LocalContext.current; return remember { WorkoutDb.get(ctx).repo } }

private class HubData(
    val plan: Plan?, val days: List<PlanDay>, val routines: Map<String, Routine>, val gym: List<Routine>, val morning: List<Routine>,
    val todaySlot: Int, val todayLineCount: Int, val todayMinutes: Int, val stats: WeekStats, val active: Session?,
)

private fun loadHub(repo: WorkoutRepo): HubData {
    val plan = repo.activePlan()
    val all = repo.routines()
    val days = plan?.let { repo.days(it.id) } ?: emptyList()
    val today = AppClock.today()
    val slot = plan?.let { WorkoutLogic.slotFor(today, it.slot1Weekday) } ?: 1
    val d = days.firstOrNull { it.slot == slot }
    val base = repo.routine(d?.gym ?: d?.morning)
    val lines = if (plan != null && base != null) repo.dayLines(plan.id, slot) else emptyList()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val zone = ZoneId.systemDefault()
    val from = monday.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = monday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    return HubData(
        plan, days, all.associateBy { it.id }, all.filter { it.isGym }, all.filter { !it.isGym }, slot, lines.size,
        base?.let { repo.estimate(it, lines) } ?: 0, repo.weekStats(from, to), repo.activeSession(),
    )
}

/** Starts (or resumes) the planned session for [slot] and opens the Active Workout (A). The back stack is first reset to C0 so Exit and C10 "Done" always return to the Workout Hub, whichever screen started the workout. Run/ruck routines are logged via the cardio dialog instead. */
fun startPlanned(repo: WorkoutRepo, plan: Plan?, slot: Int, routine: Routine, go: (Screen) -> Unit) {
    val existing = repo.activeSession()
    if (existing != null) { WorkoutNav.sessionId = existing.id; go(Screen.Workout); go(Screen.WorkoutActive); return }
    // Only link the session to a plan day when that day's exercise list really belongs to this routine.
    val day = plan?.let { repo.day(it.id, slot) }
    val usePlan = plan != null && day != null && (day.gym ?: day.morning) == routine.id
    val id = repo.startSession(if (usePlan) plan?.id else null, if (usePlan) slot else null, routine.id, "${routine.code} · ${routine.name}", if (routine.isGym) "gym" else "morning")
    WorkoutNav.sessionId = id; WorkoutNav.bump(); go(Screen.Workout); go(Screen.WorkoutActive)
}

@Composable
fun WorkoutHubScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val d = remember(tick) { loadHub(repo) }
    var dialog by remember { mutableStateOf<String?>(null) }   // "run" | "ruck" | "weight"
    val watch = remember { HealthSync.connected(ctx) }
    val todayDay = d.days.firstOrNull { it.slot == d.todaySlot }
    val gymToday = d.routines[todayDay?.gym]
    val morningToday = d.routines[todayDay?.morning]
    val primary = gymToday ?: morningToday

    WkPage(title = "Workout", subtitle = "Build strength. Stay consistent.", tabBarClear = true, trailing = {
        Row(
            Modifier.heightIn(min = 48.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape)
                .clickable(role = Role.Button) { go(Screen.Connections) }.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ico(wkIco("heart"), 18.dp, C.White); Spacer(Modifier.width(8.dp))
            Text(if (watch) "Watch: Connected" else "Watch: Not connected", color = C.White, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Ico(wkIco("chevron-right"), 14.dp, C.Text2)
        }
    }) {
        // ---- today hero ----
        CoverArt(gymToday ?: morningToday, Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.dp, C.Hairline, RoundedCornerShape(24.dp))) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("calendar"), 16.dp, C.Text2); Spacer(Modifier.width(8.dp)); Text("TODAY", color = C.Text2, fontSize = 11.sp, letterSpacing = 1.2.sp) }
                if (primary == null) {
                    Text("Rest day", color = C.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(if (d.plan == null) "No active plan. Create one in Plans." else "Nothing scheduled in slot ${d.todaySlot} of 7.", color = C.Text2, fontSize = 14.sp)
                    Spacer(Modifier.height(130.dp))
                    WkButton("Open Plans", Modifier.fillMaxWidth(), icon = wkIco("layers")) { go(Screen.WorkoutPlans) }
                } else {
                    Text("${primary.code} · ${primary.name}", color = C.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    val meta = if (primary.isGym) "${primary.focus.substringBefore(" •")} • ${d.todayLineCount} exercises • ~ ${d.todayMinutes} min" else primary.labelHub
                    Text(meta, color = C.Text2, fontSize = 14.sp)
                    if (gymToday != null && morningToday != null) Text("Morning: ${morningToday.code} · ${morningToday.name}", color = C.Text2, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("check"), 16.dp, C.Text2); Spacer(Modifier.width(8.dp)); Text("Slot ${d.todaySlot} of 7", color = C.Text2, fontSize = 13.sp) }
                    Spacer(Modifier.height(if (d.active != null) 70.dp else 56.dp))
                    if (d.active != null) Text("Workout in progress: ${d.active.title}", color = C.White, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val isCardio = primary.kind == "run" || primary.kind == "ruck"
                        val label = when { d.active != null -> "Resume Workout"; primary.kind == "run" -> "Log Run"; primary.kind == "ruck" -> "Log Ruck"; else -> "Start Workout" }
                        WkButton(label, Modifier.weight(1.3f), primary = true, icon = wkIco("play")) {
                            if (d.active != null || !isCardio) startPlanned(repo, d.plan, d.todaySlot, primary, go) else dialog = primary.kind
                        }
                        WkButton("View Plan", Modifier.weight(1f), icon = wkIco("note")) { WorkoutNav.routineId = primary.id; go(Screen.WorkoutRoutine) }
                    }
                }
            }
        }

        // ---- week strip ----
        if (d.plan != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            d.days.forEach { day ->
                val g = d.routines[day.gym]; val m = d.routines[day.morning]
                val wd = WEEKDAY_SHORT[WorkoutLogic.weekdayOf(day.slot, d.plan.slot1Weekday).value - 1]
                DayCell(
                    wd, WorkoutLogic.dayLabel(m?.code, g?.code).let { if (g == null && m != null) m.name.take(7) else it }, null,
                    (g ?: m)?.accent ?: "grey", day.slot == d.todaySlot, Modifier.weight(1f),
                    icon = wkIco(when { g != null -> "dumbbell"; m?.kind == "run" -> "run"; m?.kind == "ruck" -> "ruck"; m != null -> "mobility"; else -> "sun" }),
                ) { (g ?: m)?.let { r -> WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) } }
            }
        }

        // ---- gym routines ----
        SectionTitle("Gym Routines", action = "View all") { go(Screen.WorkoutLibrary) }
        d.gym.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { r -> GymTile(r, d.plan?.let { _ -> null }, Modifier.weight(1f)) { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // ---- morning routines ----
        SectionTitle("Morning Routines", action = "View all") { go(Screen.WorkoutLibrary) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(d.morning, key = { it.id }) { r -> MorningTile(r, Modifier.width(112.dp), false) { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) } }
        }

        // ---- program ----
        if (d.plan != null) WkCard(onClick = { go(Screen.WorkoutPlans) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("chart"), 22.dp, C.White); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your Program", color = C.Text2, fontSize = 12.sp)
                    Text(d.plan.name, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text("${d.gym.size} gym routines • ${d.morning.size} morning routines • 7-day schedule", color = C.Text2, fontSize = 12.sp)
                }
            }
        }

        // ---- this week ----
        WkCard(onClick = { go(Screen.WorkoutStats) }) {
            Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("chart"), 18.dp, C.Text2); Spacer(Modifier.width(8.dp)); Text("This Week", color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("Statistics", color = C.Text2, fontSize = 13.sp); Ico(wkIco("chevron-right"), 18.dp, C.Text2) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                StatCell(wkIco("flame"), "${d.stats.workouts}", "Workouts", Modifier.weight(1f))
                StatCell(wkIco("run"), "${d.stats.runRuck}", "Run / Ruck", Modifier.weight(1f))
                StatCell(wkIco("clock"), "${d.stats.minutes}", "Minutes", Modifier.weight(1f))
                StatCell(wkIco("barbell"), String.format(java.util.Locale.US, "%,d kg", Math.round(d.stats.volumeKg)), "Volume", Modifier.weight(1f))
            }
            if (d.stats.workouts + d.stats.runRuck == 0) Text("Nothing logged yet this week.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }

        WkCard(onClick = { go(Screen.WorkoutHistory) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("clock"), 22.dp, C.White); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) { Text("Workout History", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("Past sessions and full breakdowns", color = C.Text2, fontSize = 12.sp) }
                Ico(wkIco("chevron-right"), 18.dp, C.Text2)
            }
        }

        // ---- quick actions ----
        Text("Quick Actions", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickAction("Log Workout", wkIco("dumbbell"), Modifier.weight(1f)) {
                val r = gymToday ?: morningToday
                if (r != null && r.kind != "run" && r.kind != "ruck") startPlanned(repo, d.plan, d.todaySlot, r, go) else go(Screen.WorkoutPlans)
            }
            QuickAction("Log Run", wkIco("run"), Modifier.weight(1f)) { dialog = "run" }
            QuickAction("Log Ruck", wkIco("ruck"), Modifier.weight(1f)) { dialog = "ruck" }
            QuickAction("Log Body Weight", wkIco("body-weight"), Modifier.weight(1f)) { dialog = "weight" }
        }
    }

    when (dialog) {
        "run", "ruck" -> CardioDialog(dialog!!, { km, min, effort, pack ->
            repo.logCardio(dialog!!, km, min, effort, pack); WorkoutNav.bump(); dialog = null
        }, { dialog = null })
        "weight" -> WeightDialog(repo.latestBodyWeight()?.second, { kg -> repo.logBodyWeight(AppClock.today(), kg); WorkoutNav.bump(); dialog = null }, { dialog = null })
    }
}

@Composable
private fun StatCell(icon: Int, value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Ico(icon, 20.dp, C.White); Text(value, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(label, color = C.Text2, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun QuickAction(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) { Ico(icon, 22.dp, C.White); Spacer(Modifier.height(6.dp)); Text(label, color = C.White, fontSize = 11.sp, maxLines = 2, lineHeight = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
}

/** Gym routine card: artwork left, code/name/meta right. [subtitle] overrides the focus line. */
@Composable
fun GymTile(r: Routine, subtitle: String?, modifier: Modifier = Modifier, plansStyle: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.heightIn(min = 112.dp).clip(shape).background(C.Card).border(1.dp, C.Hairline, shape).clickable(role = Role.Button, onClick = onClick)) {
        CoverArt(r, Modifier.width(92.dp).fillMaxHeight().heightIn(min = 112.dp), scrim = false)
        Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (plansStyle) Tag(r.code) else Text("${r.code} · ${r.name}", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (plansStyle) Text(r.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(subtitle ?: r.focus, color = C.Text2, fontSize = 12.sp, maxLines = 2)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("dumbbell"), 14.dp, C.Text2); Spacer(Modifier.width(5.dp)); Text("${r.exerciseCount} exercises", color = C.Text2, fontSize = 11.sp, maxLines = 1) }
            Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("clock"), 14.dp, C.Text2); Spacer(Modifier.width(5.dp)); Text(if (plansStyle) r.labelPlans else r.labelHub, color = C.Text2, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
fun MorningTile(r: Routine, modifier: Modifier = Modifier, detailed: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    CoverArt(r, modifier.height(if (detailed) 214.dp else 168.dp).clip(shape).border(1.dp, C.Hairline, shape).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (detailed) Tag(r.code) else Text(r.code, color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(r.name, color = C.White, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
            if (detailed) Text(r.description, color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 3)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco(when (r.kind) { "run" -> "shoe"; "ruck" -> "ruck"; else -> "clock" }), 13.dp, C.Text2); Spacer(Modifier.width(4.dp))
                Text(if (detailed) r.labelPlans else r.labelHub, color = C.Text2, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CardioDialog(activity: String, onSave: (Double, Int, Int?, Double?) -> Unit, onDismiss: () -> Unit) {
    var km by remember { mutableStateOf("") }; var min by remember { mutableStateOf("") }
    var effort by remember { mutableStateOf("") }; var pack by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Sheet, titleContentColor = C.White,
        title = { Text(if (activity == "run") "Log Run" else "Log Ruck", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WkField(km, { km = it }, "Distance (km)", numeric = true, maxLen = 6)
                WkField(min, { min = it }, "Duration (minutes)", numeric = true, maxLen = 4)
                WkField(effort, { effort = it }, "Effort 1–10 (optional)", numeric = true, maxLen = 2)
                if (activity == "ruck") WkField(pack, { pack = it }, "Pack weight kg (optional)", numeric = true, maxLen = 5)
                err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val k = km.toDoubleOrNull(); val m = min.toDoubleOrNull()?.toInt(); val e = effort.toIntOrNull()
                err = when {
                    k == null || k <= 0 -> "Enter the distance in km."
                    m == null || m !in 1..600 -> "Enter the duration in minutes."
                    effort.isNotBlank() && (e == null || e !in 1..10) -> "Effort must be 1–10."
                    else -> null
                }
                if (err == null) runCatching { onSave(k!!, m!!, e, pack.toDoubleOrNull()) }.onFailure { err = it.message }
            }) { Text("Save", color = C.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = C.Text2) } },
    )
}

@Composable
private fun WeightDialog(last: Double?, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    var kg by remember { mutableStateOf(last?.toString() ?: "") }
    var err by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Sheet, titleContentColor = C.White,
        title = { Text("Log Body Weight", fontWeight = FontWeight.SemiBold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { WkField(kg, { kg = it }, "Weight (kg)", numeric = true, maxLen = 6); err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) } } },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { runCatching { onSave(kg.toDouble()) }.onFailure { err = if (it is IllegalArgumentException && it.message?.startsWith("Enter") == true) it.message else "Enter a weight in kg." } }) { Text("Save", color = C.White, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = C.Text2) } },
    )
}
