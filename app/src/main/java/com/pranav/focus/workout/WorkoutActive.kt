package com.pranav.focus.workout

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.FocusSheet
import com.pranav.focus.ui.Ico
import com.pranav.focus.ui.Screen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The one rest timer (monotonic clock). It keeps counting while the app process is alive; there is no background alert. */
val RestTimer = RestTimerState(clock = { SystemClock.elapsedRealtime() })
private val REST_PRESETS = listOf(30, 60, 90, 120, 180)

/**
 * Binds the one shared [RestTimer] to a session (idempotent). Every tracked rest, from Active Workout or C9 Set Entry, is persisted through here:
 * the timer reports each ended rest once (uid), the repo ignores a uid it already has, and the timer's state is persisted for process recreation.
 */
fun bindRestTimer(repo: WorkoutRepo, sessionId: String) {
    if (ActiveFlow.restBoundTo == sessionId) return
    ActiveFlow.restBoundTo = sessionId
    RestTimer.onChange = null
    RestTimer.onRestEnded = { r -> runCatching { repo.recordRest(sessionId, r.seconds, r.outcome, r.uid, r.startedAt) } }
    RestTimer.restore(repo.loadRest(sessionId), System.currentTimeMillis())
    RestTimer.onChange = { runCatching { repo.saveRest(sessionId, RestTimer.snapshot(System.currentTimeMillis())) } }
}

/** Small in-memory flow state shared by the Active Workout states (the data itself is always in the database). */
object ActiveFlow {
    var sessionId: String? = null
    var pausedAt by mutableStateOf<Long?>(null)
    /** Monotonic reading (SystemClock) when the current pause started in THIS process; null after process death (the persisted wall-clock start is used then). */
    var pauseMono: Long? = null
    /** True when pausing the workout also paused a running rest, so resuming gives it back (rest and pause never overlap). */
    var restAutoPaused = false
    /** Session the shared RestTimer is currently bound to. */
    var restBoundTo: String? = null
    var autoRest by mutableStateOf(true)
    var seenInit = false
    val seen = HashSet<Long>()
    val celebrated = HashSet<String>()
    fun bind(id: String) { if (sessionId != id) { sessionId = id; pausedAt = null; pauseMono = null; restAutoPaused = false; seenInit = false; seen.clear(); celebrated.clear() } }
}

private class ActiveData(val session: Session?, val focus: String, val base: List<Line>, val extra: List<Line>, val logged: List<SetLog>)
private class EditTarget(val line: Line, val log: SetLog?, val setNo: Int)
private class PrEvt(val exercise: String, val r: PrResult)

private fun fmtN(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else v.toString()
private fun unitOf(type: String) = when (type) { "carry" -> "m"; "hold" -> "sec"; else -> "reps" }
private fun hms(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) }
private fun mmss(ms: Long): String { val s = ((ms + 999) / 1000).toInt(); return String.format(Locale.US, "%02d:%02d", s / 60, s % 60) }
private fun setText(s: SetLog, unit: String): String {
    val v = when (unit) { "sec" -> s.durationS; "m" -> s.distanceM?.toInt(); else -> s.reps } ?: return "—"
    val tail = if (unit == "reps") "$v" else "$v $unit"
    return (s.loadKg?.let { fmtN(it) + " kg × " } ?: if (unit == "reps") "BW × " else "") + tail
}
private fun planText(l: Line): String { val u = unitOf(l.type); return "${l.sets} sets × ${l.repText}" + if (u == "reps") " reps" else " $u" }

@Composable
private fun Thumb(exerciseId: String, size: Dp = 44.dp) {
    val img = remember(exerciseId) { runCatching { WorkoutImages.exerciseIllustration(exerciseId, thumb = true) }.getOrNull() }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (img != null) Image(painterResource(img), null, Modifier.size(size), contentScale = ContentScale.Crop)
        else Ico(WorkoutImages.pictogram(exerciseId), size * 0.55f, C.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun Circle(done: Boolean, size: Dp = 26.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(if (done) C.White else Color.Transparent).border(1.5.dp, if (done) C.White else C.Text2, CircleShape), contentAlignment = Alignment.Center) {
        if (done) Ico(wkIco("check"), size * 0.6f, C.Black)
    }
}

@Composable
private fun WkDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Sheet).border(1.dp, C.Hairline, RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun SheetHeader(title: String, icon: String? = null, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Ico(wkIco(icon), 20.dp, C.White); Spacer(Modifier.width(10.dp)) }
        Text(title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 20.dp, C.White) }
    }
}

/** Active Workout: A (before Play) / C (in progress) with D, E, F, G, H, I, J and the exit confirmation. Set Entry (B) is the existing C9 screen. */
@Composable
fun WorkoutActiveScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val ctxW = androidx.compose.ui.platform.LocalContext.current
    val tick = WorkoutNav.tick
    var retry by remember { mutableIntStateOf(0) }
    val loaded = remember(WorkoutNav.sessionId, tick, retry) {
        runCatching {
            val s = (WorkoutNav.sessionId?.let { repo.session(it) }?.takeIf { it.status == "active" }) ?: repo.activeSession()?.also { WorkoutNav.sessionId = it.id }
            if (s == null) ActiveData(null, "", emptyList(), emptyList(), emptyList()) else ActiveData(
                s, s.routineId?.let { repo.routine(it)?.focus }.orEmpty(),
                (if (s.planId != null && s.slot != null) repo.dayLines(s.planId, s.slot) else s.routineId?.let { repo.routineLines(it) } ?: emptyList()).filter { l -> l.exerciseId !in repo.hiddenExercises(s.id) },
                repo.sessionExtraLines(s.id), repo.sessionSets(s.id).filter { !it.warmup },
            )
        }
    }
    loaded.exceptionOrNull()?.let { e ->
        WkPage("Workout", onBack = back) {
            WkCard {
                Text("Couldn't load your workout", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Your saved sets are safe. ${e.message.orEmpty()}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                WkButton("Retry", Modifier.fillMaxWidth(), primary = true) { retry++ }
                Spacer(Modifier.height(8.dp)); WkButton("Return", Modifier.fillMaxWidth(), onClick = back)
            }
        }; return
    }
    val data = loaded.getOrThrow()
    val session = data.session
    if (session == null) { LaunchedEffect(Unit) { val p = WorkoutNav.pendingComplete; WorkoutNav.pendingComplete = false; if (p && WorkoutNav.completedSessionId != null) { go(Screen.Workout); go(Screen.WorkoutComplete) } else back() }; WkPage("Workout", onBack = back) { Text("No active workout.", color = C.Text2) }; return }
    ActiveFlow.bind(session.id)
    remember(session.id) {
        ActiveFlow.pausedAt = repo.pausedAt(session.id)                       // a paused workout stays paused after reopening
        bindRestTimer(repo, session.id)
        0
    }

    val lines = data.base + data.extra
    val logged = data.logged
    val byEx = logged.groupBy { it.exerciseId }
    var started by rememberSaveable(session.id) { mutableStateOf(repo.hasBegun(session.id)) }
    val inProgress = started || logged.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    var restOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<Line?>(null) }
    var historyFor by remember { mutableStateOf<Line?>(null) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var finishOpen by remember { mutableStateOf(false) }
    var exitOpen by remember { mutableStateOf(false) }
    var discardOpen by remember { mutableStateOf(false) }
    var pr by remember { mutableStateOf<PrEvt?>(null) }
    var removeFor by remember { mutableStateOf<Line?>(null) }
    var blockedFor by remember { mutableStateOf<Line?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun rowsFor(l: Line) = maxOf(l.sets, byEx[l.exerciseId]?.maxOfOrNull { it.setNo } ?: 0)
    val doneCount = { l: Line -> byEx[l.exerciseId]?.size ?: 0 }
    val completeEx = lines.count { doneCount(it) >= it.sets && it.sets > 0 }
    val plannedSets = lines.sumOf { rowsFor(it) }
    val volume = logged.sumOf { (it.loadKg ?: 0.0) * (it.reps ?: 0) }
    val current = lines.firstOrNull { doneCount(it) < it.sets }?.exerciseId

    fun celebrate(exerciseId: String, name: String, setId: Long, kg: Double?, reps: Int?) {
        val r = detectPr(repo.prSetsFor(exerciseId, setId), kg, reps) ?: return
        if (ActiveFlow.celebrated.add("$exerciseId:${r.newKg}")) pr = PrEvt(name, r)
    }
    // sets logged in Set Entry (C9) or here: auto-start rest, and detect a genuine PR once per record
    LaunchedEffect(session.id, logged.size) {
        if (!ActiveFlow.seenInit) { ActiveFlow.seen += logged.map { it.id }; ActiveFlow.seenInit = true; return@LaunchedEffect }
        val fresh = logged.filter { it.id !in ActiveFlow.seen }
        if (fresh.isEmpty()) return@LaunchedEffect
        ActiveFlow.seen += fresh.map { it.id }
        if (ActiveFlow.autoRest && ActiveFlow.pausedAt == null) fresh.last().let { l -> lines.firstOrNull { it.exerciseId == l.exerciseId }?.restS?.takeIf { it > 0 }?.let { RestTimer.start(it, l.exerciseName) } }
        fresh.filter { it.reps != null }.forEach { celebrate(it.exerciseId, it.exerciseName, it.id, it.loadKg, it.reps) }
    }

    fun openSetEntry(l: Line) { WorkoutNav.entryExerciseId = l.exerciseId; go(Screen.WorkoutSet) }
    fun endState() { RestTimer.onChange = null; RestTimer.onRestEnded = null; RestTimer.reset("cancelled"); ActiveFlow.restBoundTo = null; ActiveFlow.restAutoPaused = false; runCatching { repo.clearSessionState(session.id) } }
    fun leave(discard: Boolean) { if (discard || logged.isEmpty()) { RestTimer.reset("cancelled"); repo.finishSession(session.id, false); WorkoutNav.sessionId = null; WorkoutNav.bump(); endState() }; back() }
    fun requestExit() { if (logged.isEmpty() && !inProgress) leave(true) else exitOpen = true }
    fun togglePause() {
        if (ActiveFlow.pausedAt == null) {
            runCatching { repo.pauseSession(session.id); repo.pausedAt(session.id) ?: error("not paused") }
                .onSuccess { since ->
                    ActiveFlow.pausedAt = since; ActiveFlow.pauseMono = SystemClock.elapsedRealtime()
                    if (RestTimer.running) { RestTimer.pause(); ActiveFlow.restAutoPaused = true }   // a rest must not run inside a pause (no double subtraction)
                }.onFailure { actionError = "Couldn't pause the workout timer." }
        } else {
            val mono = ActiveFlow.pauseMono?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) }
            runCatching { repo.resumeSession(session.id, mono) }
                .onSuccess { ActiveFlow.pausedAt = null; ActiveFlow.pauseMono = null; if (ActiveFlow.restAutoPaused) { RestTimer.resume(); ActiveFlow.restAutoPaused = false }; WorkoutNav.bump() }
                .onFailure { actionError = "Couldn't resume the workout timer." }
        }
    }
    BackHandler(enabled = true) { requestExit() }

    WkPage(
        title = null, onBack = null,
        bottomBar = if (inProgress) { @Composable {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { RestButton { restOpen = true } }
                WkButton("Finish Workout", Modifier.weight(1f), enabled = logged.isNotEmpty(), height = 56.dp) { finishOpen = true }
            }
        } } else null,
    ) {
        val header: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("Back", R.drawable.ic_arrow_back) { requestExit() }
                if (inProgress) Text(session.title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) else Spacer(Modifier.weight(1f))
                Box {
                    RoundBtn("More options", R.drawable.plan_ic_more) { menu = true }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(C.Sheet)) {
                        DropdownMenuItem(text = { Text("Add exercise", color = C.White) }, onClick = { menu = false; addOpen = true })
                        DropdownMenuItem(text = { Text("Finish workout", color = C.White) }, onClick = { menu = false; finishOpen = true })
                        DropdownMenuItem(text = { Text("Exit workout", color = C.White) }, onClick = { menu = false; requestExit() })
                    }
                }
            }
        }
        if (!inProgress) {
            // ================= A — Active Workout (before Play) =================
            Box(Modifier.fillMaxWidth().heightIn(min = 184.dp)) {
                HeroArt(session.routineId, Modifier.align(Alignment.TopEnd).width(230.dp).height(196.dp))
                Column {
                    header()
                    Spacer(Modifier.height(14.dp))
                    Text(session.title, color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    if (data.focus.isNotBlank()) Text(data.focus, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, end = 90.dp))
                }
            }
            WkCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Workout Time", color = C.Text2, fontSize = 13.sp); Text("00:00:00", color = C.White, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
                    Box(Modifier.size(64.dp).clip(CircleShape).background(C.White).semantics { contentDescription = "Start workout" }.clickable(role = Role.Button) { repo.beginSession(session.id); started = true; WorkoutNav.bump() }, contentAlignment = Alignment.Center) { Ico(wkIco("play"), 30.dp, C.Black) }
                }
            }
            StatsRow(completeEx, lines.size, logged.size, plannedSets, volume)
            Text("Exercises", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (lines.isEmpty()) Text("This workout has no exercises yet. Add one below.", color = C.Text2, fontSize = 14.sp)
            lines.forEachIndexed { i, l ->
                WkCard(padding = 10.dp, onClick = { openSetEntry(l) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = C.Text2, fontSize = 14.sp, modifier = Modifier.width(22.dp))
                        Thumb(l.exerciseId); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${l.equipment} • ${l.muscleTag}", color = C.Text2, fontSize = 12.sp, maxLines = 1)
                            Text(planText(l), color = C.Text3, fontSize = 12.sp)
                        }
                        Circle(doneCount(l) >= l.sets && l.sets > 0)
                    }
                }
            }
            WkButton("Add Exercise", Modifier.fillMaxWidth(), primary = true, icon = wkIco("plus")) { addOpen = true }
            WkButton("Finish Workout", Modifier.fillMaxWidth()) { finishOpen = true }
        } else {
            // ================= C — Workout Overview (in progress) =================
            header()
            Row(verticalAlignment = Alignment.CenterVertically) {
                ElapsedText(session, Modifier.weight(1f))
                val paused = ActiveFlow.pausedAt != null
                Box(Modifier.size(48.dp).clip(CircleShape).border(1.5.dp, C.White, CircleShape).semantics { contentDescription = if (paused) "Resume workout timer" else "Pause workout timer" }.clickable(role = Role.Button) { togglePause() }, contentAlignment = Alignment.Center) { Ico(wkIco(if (paused) "play" else "pause"), 22.dp, C.White) }
            }
            StatsRow(completeEx, lines.size, logged.size, plannedSets, volume)
            lines.forEachIndexed { i, l ->
                val rows = rowsFor(l); val sets = byEx[l.exerciseId].orEmpty(); val unit = unitOf(l.type)
                val complete = sets.size >= l.sets && l.sets > 0
                WkCard(Modifier.alpha(if (complete) 0.72f else 1f), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clickable(role = Role.Button) { infoFor = l }.semantics { contentDescription = "Exercise info for ${l.name}" }, verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(CircleShape).border(1.dp, C.Text3, CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", color = C.White, fontSize = 12.sp) }
                            Spacer(Modifier.width(10.dp)); Thumb(l.exerciseId, 48.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(l.name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                    if (l.exerciseId == current) { Spacer(Modifier.width(8.dp)); Tag("Current") }
                                }
                                Text(planText(l), color = C.Text2, fontSize = 12.sp)
                            }
                        }
                        Box(Modifier.size(44.dp).clickable(role = Role.Button) { if (sets.isNotEmpty()) blockedFor = l else removeFor = l }.semantics { contentDescription = "Remove ${l.name} from this workout" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 18.dp, C.Text2) }
                    }
                    Spacer(Modifier.height(8.dp))
                    for (n in 1..rows) {
                        val log = sets.lastOrNull { it.setNo == n }
                        val isNext = log == null && l.exerciseId == current && n == sets.size + 1
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(C.Raised)
                                .then(if (isNext) Modifier.border(1.dp, C.Text3, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable(role = Role.Button) { if (log != null) editing = EditTarget(l, log, n) else openSetEntry(l) }
                                .semantics { contentDescription = if (log != null) "Set $n ${setText(log, unit)}. Tap to edit" else "Set $n not logged. Tap to log" }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("$n", color = C.Text2, fontSize = 13.sp, modifier = Modifier.width(26.dp))
                            Text(log?.let { setText(it, unit) } ?: "–", color = if (log != null) C.White else C.Text3, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            Circle(log != null)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    WkButton("Add Set", Modifier.fillMaxWidth(), icon = wkIco("plus"), height = 46.dp) { editing = EditTarget(l, null, (sets.maxOfOrNull { it.setNo } ?: 0) + 1) }
                }
            }
        }
    }

    // ---------------- overlays ----------------
    if (restOpen) RestTimerDialog { restOpen = false }
    infoFor?.let { ExerciseInfoSheet(repo, session.id, it, onHistory = { historyFor = it; infoFor = null }) { infoFor = null } }
    historyFor?.let { PreviousSetsSheet(repo, session.id, it) { historyFor = null } }
    if (addOpen) AddExerciseSheet(repo, session.id, lines.map { it.exerciseId }.toSet(), onAdded = { WorkoutNav.bump(); addOpen = false }) { addOpen = false }
    editing?.let { t ->
        EditSetSheet(repo, session.id, t, onSaved = { setId, kg, reps -> editing = null; WorkoutNav.bump(); if (reps != null) celebrate(t.line.exerciseId, t.line.name, setId, kg, reps) }) { editing = null }
    }
    pr?.let { PrDialog(it) { pr = null } }
    if (finishOpen) {
        val body = if (logged.isEmpty()) "No sets have been logged, so there is nothing to save. This workout will be discarded."
        else (if (completeEx > 0) "You've completed $completeEx ${if (completeEx == 1) "exercise" else "exercises"}." else "You've logged ${logged.size} ${if (logged.size == 1) "set" else "sets"}.") +
            " Your workout will be saved and you'll return to the Workout screen. Sets you haven't logged are skipped."
        FinishDialog(body, if (logged.isEmpty()) "Discard Workout" else "Finish Workout", onConfirm = {
            finishOpen = false
            if (logged.isEmpty()) leave(true)
            else if (!saving) {
                saving = true
                runCatching { RestTimer.reset("cancelled"); check(repo.finishSession(session.id, true) || repo.session(session.id)?.status == "completed") { "not saved" }; check(repo.session(session.id)?.status == "completed") { "not saved" } }
                    .onSuccess { WorkoutNav.completedSessionId = session.id; WorkoutNav.sessionId = null; WorkoutNav.bump(); endState(); runCatching { com.pranav.focus.lock.Earning.workoutSaved(ctxW, session.id) }; go(Screen.Workout); go(Screen.WorkoutComplete) }
                    .onFailure { saving = false; actionError = "Couldn't save the workout. Your sets are still recorded — try again." }
            }
        }) { finishOpen = false }
    }
    removeFor?.let { l ->
        ConfirmDialog("Remove ${l.name}?", "It will be removed from this workout only. Your plan and routines aren't changed, and nothing has been logged for it.", "Remove", {
            removeFor = null
            runCatching { repo.removeExerciseFromWorkout(session.id, l.exerciseId, data.extra.any { it.exerciseId == l.exerciseId }) }.onSuccess { WorkoutNav.bump() }.onFailure { actionError = it.message ?: "Couldn't remove the exercise." }
        }, { removeFor = null })
    }
    blockedFor?.let { l -> InfoDialog("Can't remove ${l.name}", "It has logged sets in this workout. Tap a set and choose Delete Set first, so no recorded data is lost.") { blockedFor = null } }
    actionError?.let { InfoDialog("Something went wrong", it) { actionError = null } }
    if (exitOpen) ExitDialog(onSave = { exitOpen = false; leave(false) }, onDiscard = { exitOpen = false; discardOpen = true }) { exitOpen = false }
    if (discardOpen) ConfirmDialog("Discard workout?", "The session is marked abandoned and can't be resumed. Sets you logged stay in your history.", "Discard", { discardOpen = false; leave(true) }, { discardOpen = false })
}

/** State A hero: the routine's own cover artwork (project asset), faded into the black page. Nothing is drawn when the routine has no cover. */
@Composable
private fun HeroArt(routineId: String?, modifier: Modifier) {
    val img = remember(routineId) { routineId?.let { runCatching { WorkoutImages.routineCover(it) }.getOrNull() } } ?: return
    Box(modifier) {
        Image(painterResource(img), null, Modifier.fillMaxSize().alpha(0.92f), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(C.Black, Color.Transparent, Color.Transparent))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, C.Black))))
    }
}

@Composable
private fun StatsRow(exDone: Int, exTotal: Int, sets: Int, planned: Int, volume: Double) {
    WkCard(padding = 0.dp) {
        Row(Modifier.fillMaxWidth()) {
            listOf("Exercises" to "$exDone / $exTotal", "Sets" to "$sets / $planned", "Volume" to "${String.format(Locale.US, "%,d", Math.round(volume))} kg").forEachIndexed { i, (l, v) ->
                Column(Modifier.weight(1f).then(if (i > 0) Modifier.border(width = 0.dp, color = Color.Transparent) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(l, color = C.Text2, fontSize = 12.sp); Text(v, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ElapsedText(session: Session, modifier: Modifier) {
    val startedAt = session.startedAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(500) } }
    Text(hms(WorkoutTiming.runningMs(startedAt, session.pausedMs, session.timingV, now, ActiveFlow.pausedAt)), color = C.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, modifier = modifier)
}

@Composable
private fun RestButton(onClick: () -> Unit) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(RestTimer.running) { while (RestTimer.running) { RestTimer.tick(); now = SystemClock.elapsedRealtime(); delay(250) } }
    val active = RestTimer.running || RestTimer.paused
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.Card).border(1.dp, C.White.copy(alpha = 0.35f), RoundedCornerShape(28.dp)).clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(wkIco("timer"), 22.dp, C.White); Spacer(Modifier.width(8.dp))
        Text(if (active) RestTimer.leftMs().also { now.hashCode() }.let { mmss(it) } else "Rest Timer", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- D — Rest Timer
@Composable
private fun RestTimerDialog(onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(RestTimer.running) { while (RestTimer.running) { RestTimer.tick(); now = SystemClock.elapsedRealtime(); delay(200) } }
    val left = RestTimer.leftMs().also { now.hashCode() }
    val frac = if (RestTimer.total <= 0) 0f else (left / (RestTimer.total * 1000f)).coerceIn(0f, 1f)
    WkDialog(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text("Rest Timer", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("Between sets", color = C.Text2, fontSize = 13.sp) }
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 20.dp, C.White) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(210.dp)) {
                val w = 12.dp.toPx(); val i = w / 2
                drawArc(C.Track, 0f, 360f, false, Offset(i, i), Size(size.width - w, size.height - w), style = Stroke(w))
                drawArc(C.White, -90f, 360f * frac, false, Offset(i, i), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(mmss(left), color = C.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
                Text(if (RestTimer.finished) "Rest over" else "Remaining", color = C.Text2, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(REST_PRESETS) { s -> FilterPill("${s}s", RestTimer.total == s) { RestTimer.select(s) } } }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).background(C.White).clickable(role = Role.Button) { when { RestTimer.running -> RestTimer.pause(); RestTimer.paused -> if (ActiveFlow.pausedAt == null) RestTimer.resume(); else -> if (ActiveFlow.pausedAt == null) RestTimer.start(RestTimer.total) } },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(wkIco(if (RestTimer.running) "pause" else "play"), 22.dp, C.Black); Spacer(Modifier.width(8.dp))
                Text(if (RestTimer.running) "Pause" else if (RestTimer.paused) "Resume" else "Start", color = C.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).border(1.5.dp, C.Text2, CircleShape).clickable(role = Role.Button) { RestTimer.reset() }.semantics { contentDescription = "Skip rest" }, contentAlignment = Alignment.Center) { Ico(wkIco("repeat"), 22.dp, C.White) }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(wkIco("timer"), 18.dp, C.Text2); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text("Auto-start after each set", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium); Text("Starts this timer when a set is logged. Runs while the app is open.", color = C.Text2, fontSize = 12.sp) }
            Switch(ActiveFlow.autoRest, { ActiveFlow.autoRest = it }, colors = SwitchDefaults.colors(checkedThumbColor = C.Black, checkedTrackColor = C.White, uncheckedThumbColor = C.Text2, uncheckedTrackColor = C.Track, uncheckedBorderColor = Color.Transparent))
        }
    }
}

// ---------------------------------------------------------------- E — Exercise Info
@Composable
private fun ExerciseInfoSheet(repo: WorkoutRepo, sessionId: String, line: Line, onHistory: () -> Unit, onDismiss: () -> Unit) {
    val ex = remember(line.exerciseId) { runCatching { repo.exercise(line.exerciseId) }.getOrNull() }
    val img = remember(line.exerciseId) { runCatching { WorkoutImages.exerciseIllustration(line.exerciseId, thumb = false) }.getOrNull() }
    var tab by remember { mutableIntStateOf(0) }
    val primary = (ex?.primary ?: line.primary).split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val secondary = ex?.secondary.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val equipment = (ex?.equipment ?: line.equipment).trim()
    val steps = ex?.instructions.orEmpty().split("\n").flatMap { it.split(Regex("(?<=\\.)\\s+(?=[A-Z])")) }.map { it.trim().trimStart('-', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }.filter { it.isNotEmpty() }
    FocusSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { Text(line.name, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(listOf(equipment, line.muscleTag).filter { it.isNotBlank() }.joinToString(" • "), color = C.Text2, fontSize = 13.sp) }
                Box(Modifier.size(44.dp).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 20.dp, C.White) }
            }
            Spacer(Modifier.height(10.dp))
            SegTabs(listOf("Overview", "Muscles", "History"), tab) { tab = it }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> {
                    if (img != null) Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(C.Raised)) { Image(painterResource(img), line.name, Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop) }
                    if (primary.isNotEmpty() || secondary.isNotEmpty()) MuscleLegend(primary, secondary)
                    if (equipment.isNotBlank()) InfoRow("Equipment", equipment)
                    InfoRow("Tracking", when (unitOf(line.type)) { "sec" -> "Duration"; "m" -> "Distance"; else -> "Weight & reps" })
                    if (steps.isNotEmpty()) {
                        Text("Instructions", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
                        steps.forEachIndexed { i, s -> Row(Modifier.padding(vertical = 3.dp)) { Text("${i + 1}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.width(22.dp)); Text(s, color = C.White, fontSize = 14.sp, lineHeight = 20.sp) } }
                    }
                }
                1 -> if (primary.isEmpty() && secondary.isEmpty()) Text("No muscle information is available for this exercise.", color = C.Text2, fontSize = 14.sp) else MuscleLegend(primary, secondary)
                else -> { PreviousSetsList(repo, sessionId, line, 360.dp); Spacer(Modifier.height(8.dp)); WkButton("Open Previous Sets", Modifier.fillMaxWidth(), height = 46.dp, onClick = onHistory) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MuscleLegend(primary: List<String>, secondary: List<String>) {
    Text("Muscles Worked", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (primary.isNotEmpty()) Column { Text("Primary", color = C.Text2, fontSize = 12.sp); primary.forEach { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(C.White)); Spacer(Modifier.width(6.dp)); Text(it, color = C.White, fontSize = 14.sp) } } }
        if (secondary.isNotEmpty()) Column { Text("Secondary", color = C.Text2, fontSize = 12.sp); secondary.forEach { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(CircleShape).background(C.Text3)); Spacer(Modifier.width(6.dp)); Text(it, color = C.White, fontSize = 14.sp) } } }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Text(label, color = C.Text2, fontSize = 14.sp, modifier = Modifier.width(110.dp)); Text(value, color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f)) }
}

// ---------------------------------------------------------------- H — Previous Sets
@Composable
private fun PreviousSetsSheet(repo: WorkoutRepo, sessionId: String, line: Line, onDismiss: () -> Unit) {
    FocusSheet(onDismiss) {
        SheetHeader("Previous Sets", "clock", onDismiss)
        Text(line.name, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
        PreviousSetsList(repo, sessionId, line, 460.dp)
        Spacer(Modifier.height(12.dp))
    }
}

/** Real recorded sets from earlier workouts, newest first (one query). Tap a date to see every set of that day. */
@Composable
private fun PreviousSetsList(repo: WorkoutRepo, sessionId: String, line: Line, maxHeight: Dp) {
    var attempt by remember { mutableIntStateOf(0) }
    val result = remember(line.exerciseId, attempt) { runCatching { repo.exerciseHistory(line.exerciseId, sessionId) } }
    val history = result.getOrNull()
    val unit = unitOf(line.type)
    val fmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    var open by remember { mutableStateOf<String?>(null) }
    when {
        history == null -> Column { Text("Previous sets are unavailable right now.", color = C.Text2, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); WkButton("Retry", Modifier.fillMaxWidth(), height = 46.dp) { attempt++ } }
        history.isEmpty() -> Text("No previous sets recorded for this exercise yet.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        else -> LazyColumn(Modifier.heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.sessionId }) { h ->
                val top = h.sets.maxWithOrNull(compareBy({ it.loadKg ?: 0.0 }, { it.reps ?: 0 })) ?: h.sets.first()
                val expanded = open == h.sessionId
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).clickable(role = Role.Button) { open = if (expanded) null else h.sessionId }.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(fmt.format(Date(h.date)), color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text(setText(top, unit), color = C.Text2, fontSize = 13.sp) }
                        Ico(wkIco(if (expanded) "chevron-down" else "chevron-right"), 18.dp, C.Text2)
                    }
                    if (expanded) { Spacer(Modifier.height(6.dp)); h.sets.forEach { s -> Row(Modifier.padding(vertical = 2.dp)) { Text("Set ${s.setNo}", color = C.Text3, fontSize = 13.sp, modifier = Modifier.width(56.dp)); Text(setText(s, unit), color = C.White, fontSize = 13.sp) } } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- G — Add Exercise
@Composable
private fun AddExerciseSheet(repo: WorkoutRepo, sessionId: String, inWorkout: Set<String>, onAdded: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    val list = remember(query, filter) {
        runCatching {
            val all = repo.searchExercises(query, emptySet())
            when (filter) { 1 -> all.filter { it.lastUsedTs != null }.sortedByDescending { it.lastUsedTs }.take(40); 2 -> all.filter { it.custom }; else -> all }
        }.getOrDefault(emptyList())
    }
    FocusSheet(onDismiss) {
        SheetHeader("Add Exercise", null, onDismiss)
        Spacer(Modifier.height(8.dp))
        WkField(query, { query = it }, "Search exercises...", maxLen = 60, trailing = { Ico(wkIco("search"), 18.dp, C.Text2) })
        Spacer(Modifier.height(10.dp))
        SegTabs(listOf("All", "Recent", "Custom"), filter) { filter = it }
        Spacer(Modifier.height(8.dp))
        err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        if (list.isEmpty()) Text(when (filter) { 1 -> "No recently used exercises yet."; 2 -> "No custom exercises yet."; else -> "No exercises match." }, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
        LazyColumn(Modifier.heightIn(max = 430.dp)) {
            items(list, key = { it.id }) { e ->
                val has = e.id in inWorkout
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = !has, role = Role.Button) { runCatching { repo.addSessionExercise(sessionId, e.id) }.onSuccess { onAdded() }.onFailure { err = it.message ?: "Couldn't add the exercise." } }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(e.id, 44.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(e.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium); Text(e.primary.ifBlank { e.equipment }, color = C.Text2, fontSize = 12.sp, maxLines = 1) }
                    if (has) Text("Added", color = C.Text3, fontSize = 12.sp) else Box(Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, C.White, CircleShape), contentAlignment = Alignment.Center) { Ico(wkIco("plus"), 16.dp, C.White) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- I — Edit Set (also logs an extra set)
@Composable
private fun EditSetSheet(repo: WorkoutRepo, sessionId: String, t: EditTarget, onSaved: (Long, Double?, Int?) -> Unit, onDismiss: () -> Unit) {
    val unit = unitOf(t.line.type)
    val step = if (unit == "reps") 1.0 else 5.0
    val start = t.log
    var kg by remember(t) { mutableStateOf(start?.loadKg ?: 0.0) }
    var v by remember(t) { mutableStateOf((when (unit) { "sec" -> start?.durationS?.toDouble(); "m" -> start?.distanceM; else -> start?.reps?.toDouble() }) ?: (if (unit == "reps") 8.0 else 30.0)) }
    var note by remember(t) { mutableStateOf(start?.note.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf<Int?>(null) }
    FocusSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SheetHeader(if (start == null) "Add Set" else "Edit Set", null, onDismiss)
            Text("${t.line.name} • Set ${t.setNo}", color = C.Text2, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundStep("−", "Decrease weight") { kg = (kg - 2.5).coerceAtLeast(0.0); err = null }
                Wheel(kg, 2.5, 0.0, "kg", Modifier.weight(1f), { kg = it; err = null }) { typing = 0 }
                RoundStep("+", "Increase weight") { kg = (kg + 2.5).coerceAtMost(1000.0); err = null }
                Wheel(v, step, step, unit, Modifier.weight(1f), { v = it; err = null }) { typing = 1 }
                RoundStep("+", "Increase $unit") { v += step; err = null }
            }
            Spacer(Modifier.height(10.dp))
            QuickRow(listOf("+2.5 kg" to { kg = (kg + 2.5).coerceAtMost(1000.0) }, "+5 kg" to { kg = (kg + 5).coerceAtMost(1000.0) }, "−2.5 kg" to { kg = (kg - 2.5).coerceAtLeast(0.0) }, "−5 kg" to { kg = (kg - 5).coerceAtLeast(0.0) }))
            Spacer(Modifier.height(6.dp))
            val u = if (unit == "reps") "rep" else unit
            QuickRow(listOf("−1 $u" to { v = (v - 1).coerceAtLeast(1.0) }, "+1 $u" to { v += 1 }, "−5 ${if (unit == "reps") "reps" else unit}" to { v = (v - 5).coerceAtLeast(1.0) }, "+5 ${if (unit == "reps") "reps" else unit}" to { v += 5 }))
            Spacer(Modifier.height(14.dp))
            Text("Notes (Optional)", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            WkField(note, { note = it }, "E.g. felt strong, form was good...", singleLine = false, maxLen = 200, minHeight = 70.dp)
            err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (start != null) WkButton("Delete Set", Modifier.weight(1f), height = 52.dp) { askDelete = true }
                WkButton(if (start == null) "Save Set" else "Save Changes", Modifier.weight(1f), primary = true, height = 52.dp) {
                    runCatching {
                        val value = v.toInt(); val load = kg.takeIf { it > 0 }
                        val r = if (unit == "reps") value else null; val d = if (unit == "sec") value else null; val m = if (unit == "m") value.toDouble() else null
                        val id = if (start != null) { repo.updateSet(start.id, load, r, d, m, note); start.id }
                        else repo.logSet(sessionId, t.line.exerciseId, t.setNo, load, r, null, note.trim().take(200), d, m)
                        Triple(id, load, r)
                    }.onSuccess { (id, load, r) -> onSaved(id, load, r) }.onFailure { err = it.message ?: "Couldn't save. Your values are still here — try again." }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    typing?.let { which ->
        var txt by remember { mutableStateOf(fmtN(if (which == 0) kg else v)) }
        AlertDialog(
            onDismissRequest = { typing = null }, containerColor = C.Sheet,
            title = { Text(if (which == 0) "Weight (kg)" else unit.replaceFirstChar { it.uppercase() }, color = C.White) },
            text = { WkField(txt, { txt = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, "0", numeric = true) },
            confirmButton = { TextButton({ txt.toDoubleOrNull()?.let { n -> if (which == 0) kg = n.coerceIn(0.0, 1000.0) else v = n.coerceAtLeast(1.0) }; typing = null }) { Text("Set", color = C.White) } },
            dismissButton = { TextButton({ typing = null }) { Text("Cancel", color = C.Text2) } },
        )
    }
    if (askDelete && start != null) ConfirmDialog("Delete set ${t.setNo}?", "This removes the recorded set. You can log it again afterwards.", "Delete", {
        askDelete = false
        runCatching { repo.deleteSet(start.id) }.onSuccess { WorkoutNav.bump(); onDismiss() }.onFailure { err = it.message ?: "Couldn't delete the set." }
    }, { askDelete = false })
}

@Composable
private fun RoundStep(sign: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).border(1.5.dp, C.Text2, CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) { Text(sign, color = C.White, fontSize = 20.sp) }
}

/** Wheel-style value picker: neighbours are tappable, the centre value opens a number entry. */
@Composable
private fun Wheel(value: Double, step: Double, min: Double, label: String, modifier: Modifier, onChange: (Double) -> Unit, onCenter: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(vertical = 4.dp)) {
            for (k in -2..2) {
                val x = value + k * step
                if (x < min - 1e-9) Spacer(Modifier.height(36.dp)) else Box(
                    Modifier.fillMaxWidth().height(36.dp).then(if (k == 0) Modifier.background(C.Sheet) else Modifier).clickable(role = Role.Button) { if (k == 0) onCenter() else onChange(x) },
                    contentAlignment = Alignment.Center,
                ) { Text(fmtN(x), color = if (k == 0) C.White else C.Text3.copy(alpha = if (k == 1 || k == -1) 1f else 0.6f), fontSize = if (k == 0) 22.sp else 15.sp, fontWeight = if (k == 0) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        Text(label, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun QuickRow(items: List<Pair<String, () -> Unit>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (l, f) -> Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, C.Hairline, RoundedCornerShape(10.dp)).background(C.Card).clickable(role = Role.Button, onClick = f), contentAlignment = Alignment.Center) { Text(l, color = C.White, fontSize = 12.sp, maxLines = 1) } }
    }
}

// ---------------------------------------------------------------- J — New Personal Record
@Composable
private fun PrDialog(e: PrEvt, onDismiss: () -> Unit) {
    fun v(kg: Double, reps: Int?) = fmtN(kg) + " kg" + (reps?.let { " × $it" } ?: "")
    WkDialog(onDismiss) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val cx = size.width / 2; val cy = size.height / 2
                listOf(-120f to -40f, -95f to 30f, -70f to -50f, 70f to -50f, 95f to 30f, 120f to -40f, -50f to 52f, 50f to 52f).forEachIndexed { i, (dx, dy) ->
                    rotate(20f + i * 37f, Offset(cx + dx.dp.toPx(), cy + dy.dp.toPx())) { drawRect(C.White.copy(alpha = 0.28f), Offset(cx + dx.dp.toPx(), cy + dy.dp.toPx()), Size(3.dp.toPx(), 9.dp.toPx())) }
                }
            }
            Box(Modifier.size(84.dp).clip(CircleShape).border(2.dp, C.White.copy(alpha = 0.7f), CircleShape).background(C.Raised), contentAlignment = Alignment.Center) { Ico(wkIco("trophy"), 40.dp, C.White) }
        }
        Text("New Personal Record!", color = C.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(e.exercise, color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Previous", color = C.Text2, fontSize = 12.sp); Text(v(e.r.prevKg, e.r.prevReps), color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
            Text("→", color = C.Text2, fontSize = 18.sp)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.5.dp, C.White, RoundedCornerShape(14.dp)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("New", color = C.Text2, fontSize = 12.sp); Text(v(e.r.newKg, e.r.newReps), color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        WkButton("Continue", Modifier.fillMaxWidth(), primary = true, onClick = onDismiss)
    }
}

// ---------------------------------------------------------------- F / exit
@Composable
private fun FinishDialog(body: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    WkDialog(onCancel) {
        Box(Modifier.size(56.dp).clip(CircleShape).border(2.dp, C.White, CircleShape), contentAlignment = Alignment.Center) { Ico(wkIco("check"), 28.dp, C.White) }
        Spacer(Modifier.height(14.dp))
        Text("Finish Workout?", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(body, color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        WkButton(confirm, Modifier.fillMaxWidth(), primary = true, onClick = onConfirm)
        Spacer(Modifier.height(8.dp)); WkButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
    }
}

@Composable
private fun ExitDialog(onSave: () -> Unit, onDiscard: () -> Unit, onCancel: () -> Unit) {
    WkDialog(onCancel) {
        Ico(wkIco("back"), 34.dp, C.White)
        Spacer(Modifier.height(12.dp))
        Text("Exit Workout?", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Your progress will be saved and you can continue later.", color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        WkButton("Save & Exit", Modifier.fillMaxWidth(), primary = true, onClick = onSave)
        Spacer(Modifier.height(8.dp)); WkButton("Discard Workout", Modifier.fillMaxWidth(), onClick = onDiscard)
        Spacer(Modifier.height(8.dp)); WkButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
    }
}
