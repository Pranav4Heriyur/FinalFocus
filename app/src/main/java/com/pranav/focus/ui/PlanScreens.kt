package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.PlanDates
import com.pranav.focus.data.Store
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.SessionController
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * A18 Plan My Day: 12 destinations.
 *   Routes (Screen enum): Plan, PlanSubjects, PlanAdd, PlanEdit, PlanReorder, PlanDetail, PlanCalendar, PlanFocus, PlanSummary, PlanSettings.
 *   A18_09 Conflict Warning and A18_12 Quick Add are modal sheets opened from those screens (no standalone route).
 * Data: PlanState (persisted in Store). Navigation: the app's existing stack + shared TabBar (Plan tab).
 */

// --------------------------------------------------------------------------------------
// Host
// --------------------------------------------------------------------------------------
@Composable
fun PlanHost(screen: Screen, go: (Screen) -> Unit, back: () -> Unit) {
    PlanState.load(LocalContext.current)
    // Fresh entry into the planner opens on the real current date.
    LaunchedEffect(Unit) { PlanState.selectToday() }
    // Resume (AppState.refresh bumps tick): refresh "today" without disturbing a deliberately chosen other date.
    LaunchedEffect(AppState.tick) { PlanState.syncToday() }
    // Midnight while the screen stays open.
    LaunchedEffect(Unit) {
        while (true) {
            val now = java.time.ZonedDateTime.now(AppClock.zone())
            delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis() + 500)
            PlanState.syncToday()
        }
    }
    when (screen) {
        Screen.Plan -> PlanMain(go)
        Screen.PlanSubjects -> PlanSubjects(go, back)
        Screen.PlanAdd -> PlanForm(null, back)
        Screen.PlanEdit -> PlanForm(PlanState.selectedId, back)
        Screen.PlanReorder -> PlanReorder(back)
        Screen.PlanDetail -> PlanDetail(go, back)
        Screen.PlanCalendar -> PlanCalendar(go, back)
        Screen.PlanFocus -> PlanFocus(go, back)
        Screen.PlanSummary -> PlanSummary(back)
        Screen.PlanSettings -> PlanSettings(back)
        else -> Unit
    }
}

// --------------------------------------------------------------------------------------
// Shared bits
// --------------------------------------------------------------------------------------
/** Card entrance: opacity + 8 dp rise, 420 ms, 35 ms stagger. Skipped when the system animation scale is 0. */
@Composable
private fun Modifier.enter(i: Int): Modifier {
    val ctx = LocalContext.current
    val reduce = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var on by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { if (!reduce) { delay(i * 35L); on = true } }
    val a by animateFloatAsState(if (on) 1f else 0f, tween(if (reduce) 0 else 420), label = "enter")
    return this.graphicsLayer { alpha = a; translationY = (1f - a) * 8.dp.toPx() }
}

@Composable
private fun Page(onBack: (() -> Unit)?, title: String, sub: String? = null, trailing: @Composable RowScope.() -> Unit = {}, navClear: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = if (navClear) NAV_CLEARANCE + 76.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().enter(0), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                    Ico(R.drawable.plan_ic_back, 22.dp)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (sub != null) Text(sub, color = C.Text2, fontSize = 13.sp, maxLines = 1)
            }
            trailing()
        }
        content()
    }
}

@Composable
private fun RoundBtn(icon: Int, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).clickable { onClick() }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 22.dp) }
}

@Composable
private fun PChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.White else C.Raised, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) C.Black else C.Text2, tween(180), label = "chipFg")
    Box(
        modifier.heightIn(min = 44.dp).clip(CircleShape).background(bg).border(1.dp, if (selected) C.White else C.Hairline, CircleShape)
            .clickable { onClick() }.padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

private fun taskIcon(t: PlanTask): Int = when {
    t.title.startsWith("Run") -> R.drawable.plan_ic_run
    t.title.startsWith("Break") -> R.drawable.plan_ic_coffee
    t.title.startsWith("Breakfast") || t.title.startsWith("Lunch") || t.title.startsWith("Dinner") -> R.drawable.plan_ic_utensils
    t.subject == "Physics" -> R.drawable.focus_physics_atom_white
    t.subject == "Chemistry" -> R.drawable.focus_agenda_flask_white
    t.subject == "Biology" -> R.drawable.focus_biology_leaf_white
    t.category == "Workout" -> R.drawable.plan_ic_dumbbell
    t.category == "Personal" -> R.drawable.plan_ic_coffee
    else -> R.drawable.plan_ic_list_checks
}

@Composable
private fun TaskTile(t: PlanTask, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Ico(taskIcon(t), (size - 18).dp)
    }
}

@Composable
private fun CheckDot(done: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (done) C.White else Color.Transparent, tween(180), label = "check")
    Box(Modifier.size(48.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(bg).border(1.5.dp, if (done) C.White else C.Text2, CircleShape), contentAlignment = Alignment.Center) {
            if (done) Ico(R.drawable.plan_ic_check, 15.dp, C.Black)
        }
    }
}

private fun dayConflicts(day: List<PlanTask>) = day.filter { a -> day.any { b -> a.overlaps(b) } }.map { it.id }.toSet()

// --------------------------------------------------------------------------------------
// A18_01 Plan My Day
// --------------------------------------------------------------------------------------
@Composable
private fun PlanMain(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val date = PlanState.date
    val all = PlanState.forDate(date)
    val showDone = remember { PlanPrefs.showDone(ctx) }
    val showFree = remember { PlanPrefs.showFree(ctx) }
    val quickHome = remember { PlanPrefs.quickHome(ctx) }
    val conflicts = dayConflicts(all)
    val planned = all.sumOf { it.dur }
    val pct = (planned * 100 / PLAN_DAY_MINUTES).coerceIn(0, 100)
    val shown = if (showDone) all else all.filter { !it.done }
    var quick by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Page(null, "Plan My Day", date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), trailing = {
            if (date != PlanState.today) { PChip("Today", false) { PlanState.selectToday() }; Spacer(Modifier.width(8.dp)) }
            RoundBtn(R.drawable.plan_ic_calendar, "Calendar") { go(Screen.PlanCalendar) }
            Spacer(Modifier.width(8.dp))
            RoundBtn(R.drawable.plan_ic_more, "Plan settings") { go(Screen.PlanSettings) }
        }, navClear = true) {
            // date strip: real calendar dates around today; scrolls to the selected date
            val today = PlanState.today
            val strip = PlanDates.strip(today, date)
            val stripState = rememberLazyListState(initialFirstVisibleItemIndex = (strip.indexOf(date) - 3).coerceAtLeast(0))
            LaunchedEffect(date) { stripState.animateScrollToItem((PlanDates.strip(PlanState.today, date).let { it.indexOf(date) } - 3).coerceAtLeast(0)) }
            LazyRow(Modifier.fillMaxWidth().enter(1), state = stripState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(strip.size) { i ->
                    val d = strip.at(i)
                    val sel = d == date
                    Column(
                        Modifier.width(48.dp).height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (sel) C.White else C.Card)
                            .border(1.dp, if (sel) C.White else if (d == today) C.Text2 else C.Hairline, RoundedCornerShape(18.dp)).clickable { PlanState.date = d },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), color = if (sel) C.Black else C.Text2, fontSize = 11.sp)
                        Text("${d.dayOfMonth}", color = if (sel) C.Black else C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // summary
            FocusCard(Modifier.fillMaxWidth().enter(2), onClick = { go(Screen.PlanSummary) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(pct / 100f, 64.dp, "$pct%")
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("${fmtD(planned)} / 14h planned", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${all.count { it.done }} completed · ${all.count { !it.done }} remaining · ${conflicts.size / 2 + conflicts.size % 2} overlap", color = C.Text2, fontSize = 12.sp)
                    }
                    Ico(R.drawable.plan_ic_chevron_right, 20.dp, C.Text2)
                }
            }
            SegFull(listOf("Timeline", "Subjects"), 0) { if (it == 1) go(Screen.PlanSubjects) }
            if (shown.isEmpty()) Text("Nothing planned for this day. Tap + to add a task.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
            shown.forEachIndexed { i, t ->
                if (showFree && i > 0 && t.start - shown[i - 1].end >= 30) {
                    Text("Free · ${fmtD(t.start - shown[i - 1].end)}", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(start = 76.dp))
                }
                FocusCard(Modifier.fillMaxWidth().enter(3 + i), onClick = { PlanState.selectedId = t.id; go(Screen.PlanDetail) }, radius = 20.dp, padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(62.dp)) {
                            Text(fmtT(t.start).substringBefore(" "), color = if (t.done) C.Text3 else C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(fmtT(t.start).substringAfter(" "), color = C.Text3, fontSize = 11.sp)
                        }
                        TaskTile(t)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t.title, color = if (t.done) C.Text3 else C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${fmtD(t.dur)} · ${t.subject}", color = C.Text2, fontSize = 12.sp, maxLines = 1)
                        }
                        if (t.id in conflicts) Ico(R.drawable.plan_ic_warning, 20.dp, C.White)
                        CheckDot(t.done) { PlanState.upsert(ctx, t.copy(done = !t.done)) }
                    }
                }
            }
            GhostButton("Reorder tasks", icon = R.drawable.plan_ic_reorder) { go(Screen.PlanReorder) }
        }
        if (quickHome) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = NAV_CLEARANCE + 12.dp).navigationBarsPadding().size(56.dp)
                    .bloom(8.dp, 0.2f).clip(CircleShape).background(C.White).clickable { quick = true },
                contentAlignment = Alignment.Center,
            ) { Ico(R.drawable.plan_ic_plus, 26.dp, C.Black) }
        }
    }
    if (quick) QuickAddSheet(date) { quick = false }
}

@Composable
private fun ProgressRing(frac: Float, size: androidx.compose.ui.unit.Dp, label: String) {
    val f by animateFloatAsState(frac, tween(650), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(C.Track, 0f, 360f, false, Offset(w / 2, w / 2), sz, style = Stroke(w))
            drawArc(Color.White, -90f, 360f * f, false, Offset(w / 2, w / 2), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(label, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --------------------------------------------------------------------------------------
// A18_02 Subjects View
// --------------------------------------------------------------------------------------
@Composable
private fun PlanSubjects(go: (Screen) -> Unit, back: () -> Unit) {
    val day = PlanState.forDate()
    val groups = PLAN_SUBJECT_ORDER.map { s -> s to day.filter { it.subject == s } }.filter { it.second.isNotEmpty() }
    val total = day.sumOf { it.dur }.coerceAtLeast(1)
    var picked by remember { mutableStateOf<String?>(null) }
    Page(back, "Subjects", PlanState.date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))) {
        SegFull(listOf("Timeline", "Subjects"), 1) { if (it == 0) back() }
        FocusCard(Modifier.fillMaxWidth().enter(1)) {
            Caps("Time distribution")
            Spacer(Modifier.height(10.dp))
            Canvas(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                var x = 0f
                groups.forEachIndexed { i, (_, ts) ->
                    val w = size.width * ts.sumOf { it.dur } / total
                    drawRect(Color.White.copy(alpha = (1f - i * 0.15f).coerceAtLeast(0.2f)), Offset(x, 0f), Size((w - 2.dp.toPx()).coerceAtLeast(1f), size.height))
                    x += w
                }
            }
        }
        groups.forEachIndexed { i, (name, ts) ->
            val mins = ts.sumOf { it.dur }
            FocusCard(
                Modifier.fillMaxWidth().enter(2 + i), onClick = { picked = if (picked == name) null else name }, radius = 20.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskTile(ts.first())
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${fmtD(mins)} · ${ts.size} task${if (ts.size == 1) "" else "s"}", color = C.Text2, fontSize = 12.sp)
                    }
                    Text("${mins * 100 / total}%", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(10.dp))
                ProgBar(mins / total.toFloat(), height = 5.dp)
                if (picked == name) ts.forEach { t ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { PlanState.selectedId = t.id; go(Screen.PlanDetail) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtT(t.start), color = C.Text2, fontSize = 12.sp, modifier = Modifier.width(76.dp))
                        Text(t.title, color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Ico(R.drawable.plan_ic_chevron_right, 18.dp, C.Text3)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// A18_03 Add / A18_04 Edit task form, with A18_09 conflict warning
// --------------------------------------------------------------------------------------
@Composable
private fun PlanForm(editId: String?, back: () -> Unit) {
    val ctx = LocalContext.current
    val editing = PlanState.get(editId)
    var name by remember { mutableStateOf(editing?.title ?: "") }
    var cat by remember { mutableStateOf(editing?.category ?: "Study") }
    var subj by remember { mutableStateOf(editing?.subject ?: "Physics") }
    var start by remember { mutableIntStateOf(editing?.start ?: PlanPrefs.defStart(ctx)) }
    var dur by remember { mutableIntStateOf(editing?.dur ?: PlanPrefs.defDur(ctx)) }
    var pr by remember { mutableIntStateOf(editing?.priority ?: 1) }
    var notes by remember { mutableStateOf(editing?.notes ?: "") }
    var sheet by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var conflict by remember { mutableStateOf<PlanTask?>(null) }
    var tried by remember { mutableStateOf(false) }
    val starts = remember { (0..287).map { it * 5 } }
    val durs = remember { (1..32).map { it * 15 } }
    val fits = start + dur <= 24 * 60
    val valid = name.isNotBlank() && fits

    fun build() = PlanTask(
        editing?.id ?: PlanState.newId(), editing?.date ?: PlanState.date.toString(), name.trim(), cat,
        if (cat == "Study") subj else cat, start, dur, pr, notes.trim(), editing?.done ?: false, editing?.checklist ?: emptyList(),
    )
    fun commit(t: PlanTask) { PlanState.upsert(ctx, t); back() }

    Page(back, if (editing == null) "Add New Task" else "Edit Task", PlanState.date.format(DateTimeFormatter.ofPattern("EEE, d MMM"))) {
        FocusCard(Modifier.fillMaxWidth().enter(1)) {
            Caps("Task type"); Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PLAN_CATEGORIES.forEach { c -> PChip(c, c == cat) { cat = c } }
            }
            if (cat == "Study") {
                Spacer(Modifier.height(14.dp)); Caps("Subject"); Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PLAN_SUBJECTS.forEach { s -> PChip(s, s == subj) { subj = s } } }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                name, { name = it }, label = { Text("Task name") }, singleLine = true, isError = tried && name.isBlank(),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            if (tried && name.isBlank()) Text("Give the task a name.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        }
        FocusCard(Modifier.fillMaxWidth().enter(2), padding = 6.dp) {
            FormRow("Start time", fmtT(start)) { sheet = "start" }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.Hairline))
            FormRow("Duration", fmtD(dur)) { sheet = "dur" }
        }
        if (!fits) Text("This runs past midnight. Shorten it or start earlier.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        FocusCard(Modifier.fillMaxWidth().enter(3)) {
            Caps("Priority"); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PLAN_PRIORITIES.forEachIndexed { i, p -> PChip(p, i == pr, Modifier.weight(1f)) { pr = i } } }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                notes, { notes = it }, label = { Text("Notes") }, placeholder = { Text("E.g. Chapter 5 - PYQs") },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), minLines = 2, colors = fieldColors(),
            )
        }
        PrimaryButton(if (editing == null) "Add Task" else "Save Changes", enabled = true) {
            tried = true
            if (!valid) return@PrimaryButton
            val t = build()
            val hit = PlanState.forDate(LocalDate.parse(t.date)).firstOrNull { t.overlaps(it) }
            if (hit != null) conflict = hit else commit(t)
        }
        if (editing != null) GhostButton("Delete Task") { confirmDelete = true }
    }
    when (sheet) {
        "start" -> WheelSheet("Start time", starts.map { fmtT(it) }, (start / 5).coerceIn(0, 287), { start = starts[it] }) { sheet = null }
        "dur" -> WheelSheet("Duration", durs.map { fmtD(it) }, durs.indexOf(dur).coerceAtLeast(0), { dur = durs[it] }) { sheet = null }
    }
    conflict?.let { hit ->
        val t = build()
        FocusSheet({ conflict = null }) {
            Ico(R.drawable.plan_ic_warning, 32.dp)
            Spacer(Modifier.height(8.dp))
            Text("Time conflict", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text("${t.title}: ${fmtT(t.start)}–${fmtT(t.end)} overlaps with ${hit.title}: ${fmtT(hit.start)}–${fmtT(hit.end)}.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            GhostButton("Adjust time") {
                // moves the new task to start right after the one it collides with (clamped to the day)
                start = (hit.end).coerceAtMost(24 * 60 - dur).coerceAtLeast(0); conflict = null; sheet = "start"
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Keep anyway") { conflict = null; commit(t) }
        }
    }
    if (confirmDelete && editing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = C.Sheet,
        title = { Text("Delete “${editing.title}”?", color = C.White) },
        text = { Text("This removes it from your plan.", color = C.Text2) },
        confirmButton = { TextButton({ PlanState.delete(ctx, editing.id); confirmDelete = false; back() }) { Text("Delete", color = C.White) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = C.Text2) } },
    )
}

@Composable
private fun FormRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable { onClick() }.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Ico(R.drawable.plan_ic_chevron_right, 20.dp, C.Text3, Modifier.padding(start = 6.dp))
    }
}

// --------------------------------------------------------------------------------------
// A18_05 Reorder
// --------------------------------------------------------------------------------------
@Composable
private fun PlanReorder(back: () -> Unit) {
    val ctx = LocalContext.current
    val rows = remember { mutableStateListOf<PlanTask>().apply { addAll(PlanState.forDate()) } }
    val slots = remember { rows.map { it.start }.sorted() }
    val rowH = 64.dp
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var acc by remember { mutableFloatStateOf(0f) }

    fun move(id: String, by: Int) {
        val i = rows.indexOfFirst { it.id == id }
        val j = i + by
        if (i >= 0 && j in rows.indices) rows.add(j, rows.removeAt(i))
    }

    Page(back, "Reorder Tasks", "Drag the handle. Times swap with their slot.", trailing = {
        Text("Done", color = C.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape).background(C.White).clickable {
                // the same set of start times is kept, handed out in the new order
                rows.forEachIndexed { i, t -> PlanState.upsert(ctx, t.copy(start = slots[i])) }
                back()
            }.padding(horizontal = 18.dp, vertical = 12.dp))
    }) {
        rows.forEachIndexed { i, t ->
            key(t.id) {
                val lifted = dragging == t.id
                Row(
                    Modifier.fillMaxWidth().height(rowH - 8.dp).graphicsLayer { translationY = if (lifted) acc else 0f; alpha = if (lifted) 0.92f else 1f }
                        .clip(RoundedCornerShape(18.dp)).background(if (lifted) C.Raised else C.Card)
                        .border(1.dp, if (lifted) Color.White.copy(alpha = 0.4f) else C.Hairline, RoundedCornerShape(18.dp))
                        .semantics { customActions = listOf(CustomAccessibilityAction("Move up") { move(t.id, -1); true }, CustomAccessibilityAction("Move down") { move(t.id, 1); true }) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(48.dp).pointerInput(t.id) {
                            detectDragGestures(
                                onDragStart = { dragging = t.id; acc = 0f },
                                onDragEnd = { dragging = null; acc = 0f },
                                onDragCancel = { dragging = null; acc = 0f },
                            ) { change, d ->
                                change.consume()
                                acc += d.y
                                if (acc > rowPx) { move(t.id, 1); acc -= rowPx } else if (acc < -rowPx) { move(t.id, -1); acc += rowPx }
                            }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Ico(R.drawable.plan_ic_grip_dots, 22.dp, C.Text2) }
                    Text(fmtT(slots.getOrElse(i) { t.start }), color = C.Text2, fontSize = 12.sp, modifier = Modifier.width(76.dp))
                    Text(t.title, color = C.White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(fmtD(t.dur), color = C.Text3, fontSize = 12.sp)
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// A18_06 Task Details
// --------------------------------------------------------------------------------------
@Composable
private fun PlanDetail(go: (Screen) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val t = PlanState.get(PlanState.selectedId)
    if (t == null) { LaunchedEffect(Unit) { back() }; return }
    var confirm by remember { mutableStateOf(false) }
    val doneCount = t.checklist.count { it.second }
    Page(back, t.title, "${fmtT(t.start)} – ${fmtT(t.end)}", trailing = { RoundBtn(R.drawable.plan_ic_pencil, "Edit task") { go(Screen.PlanEdit) } }) {
        FocusCard(Modifier.fillMaxWidth().enter(1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskTile(t, 52)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("${fmtD(t.dur)} · ${t.category} · ${t.subject}", color = C.White, fontSize = 15.sp)
                    Text("${PLAN_PRIORITIES[t.priority]} priority", color = C.Text2, fontSize = 13.sp)
                }
            }
            if (t.notes.isNotBlank()) { Spacer(Modifier.height(12.dp)); Caps("Notes"); Spacer(Modifier.height(4.dp)); Text(t.notes, color = C.Text2, fontSize = 14.sp, lineHeight = 20.sp) }
        }
        if (t.checklist.isNotEmpty()) FocusCard(Modifier.fillMaxWidth().enter(2), padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Checklist", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("$doneCount/${t.checklist.size}", color = C.Text2, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            ProgBar(doneCount / t.checklist.size.toFloat(), height = 5.dp)
            t.checklist.forEachIndexed { i, (label, d) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                    PlanState.upsert(ctx, t.copy(checklist = t.checklist.mapIndexed { j, c -> if (j == i) c.first to !c.second else c }))
                }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (d) C.White else Color.Transparent).border(1.5.dp, if (d) C.White else C.Text2, CircleShape), contentAlignment = Alignment.Center) {
                        if (d) Ico(R.drawable.plan_ic_check, 14.dp, C.Black)
                    }
                    Text(label, color = if (d) C.Text3 else C.White, fontSize = 15.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        run {
            val st = Store.get(ctx); val fm = st.int("tfocus_${t.id}"); val q = st.int("tq_${t.id}")
            if (fm > 0 || q > 0) Text("Linked: ${fm} min focused · $q questions logged", color = C.Text2, fontSize = 13.sp)
        }
        PrimaryButton("Start Focus") { go(Screen.PlanFocus) }
        GhostButton(if (t.done) "Mark as not done" else "Mark as Done") { PlanState.upsert(ctx, t.copy(done = !t.done)); back() }
        GhostButton("Delete Task") { confirm = true }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = C.Sheet,
        title = { Text("Delete “${t.title}”?", color = C.White) }, text = { Text("This removes it from your plan.", color = C.Text2) },
        confirmButton = { TextButton({ PlanState.delete(ctx, t.id); confirm = false; back() }) { Text("Delete", color = C.White) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel", color = C.Text2) } },
    )
}

// --------------------------------------------------------------------------------------
// A18_07 Calendar
// --------------------------------------------------------------------------------------
@Composable
private fun PlanCalendar(go: (Screen) -> Unit, back: () -> Unit) {
    var tab by remember { mutableIntStateOf(2) }
    var month by remember { mutableStateOf(YearMonth.from(PlanState.date)) }
    val sel = PlanState.date
    val counts = PlanState.tasks.groupingBy { it.date }.eachCount()

    Box(Modifier.fillMaxSize()) {
        Page(back, "Calendar", month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))) {
            SegFull(listOf("Day", "Week", "Month"), tab) { tab = it }
            if (tab == 2) FocusCard(Modifier.fillMaxWidth().enter(1), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clickable { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_back, 20.dp) }
                    Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Box(Modifier.size(44.dp).clickable { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_chevron_right, 20.dp) }
                }
                Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = C.Text3, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val cells = lead + month.lengthOfMonth()
                for (r in 0 until (cells + 6) / 7) Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNo = r * 7 + c - lead + 1
                        if (dayNo < 1 || dayNo > month.lengthOfMonth()) { Spacer(Modifier.weight(1f).height(46.dp)); continue }
                        val d = month.atDay(dayNo)
                        val s = d == sel
                        Column(Modifier.weight(1f).height(46.dp).clip(CircleShape).background(if (s) C.White else Color.Transparent).clickable { PlanState.date = d },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("$dayNo", color = if (s) C.Black else C.White, fontSize = 14.sp)
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if ((counts[d.toString()] ?: 0) > 0) (if (s) C.Black else C.Text2) else Color.Transparent))
                        }
                    }
                }
            }
            val days = if (tab == 1) (0..6).map { Weekly7(sel).plusDays(it.toLong()) } else listOf(sel)
            days.forEach { d ->
                if (tab == 1) Caps(d.format(DateTimeFormatter.ofPattern("EEE d MMM")))
                val ts = PlanState.forDate(d)
                if (ts.isEmpty()) Text("No events.", color = C.Text3, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
                ts.forEach { t ->
                    FocusCard(Modifier.fillMaxWidth(), onClick = { PlanState.selectedId = t.id; go(Screen.PlanDetail) }, radius = 18.dp, padding = 10.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${fmtT(t.start)}\n${fmtT(t.end)}", color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.width(76.dp))
                            Box(Modifier.width(3.dp).height(32.dp).clip(CircleShape).background(C.White.copy(alpha = if (t.done) 0.3f else 0.9f)))
                            Text(t.title, color = C.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).navigationBarsPadding().size(56.dp).bloom(8.dp, 0.2f).clip(CircleShape).background(C.White).clickable { go(Screen.PlanAdd) },
            contentAlignment = Alignment.Center,
        ) { Ico(R.drawable.plan_ic_plus, 26.dp, C.Black) }
    }
}

private fun Weekly7(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

// --------------------------------------------------------------------------------------
// A18_08 Focus from Agenda
// --------------------------------------------------------------------------------------
@Composable
private fun PlanFocus(go: (Screen) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val t = PlanState.get(PlanState.selectedId)
    if (t == null) { LaunchedEffect(Unit) { back() }; return }
    var strict by remember { mutableStateOf(store.strictLock) }
    Page(back, "Focus", "Start a session from your plan") {
        FocusCard(Modifier.fillMaxWidth().enter(1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskTile(t, 52)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(t.title, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${fmtT(t.start)} – ${fmtT(t.end)}", color = C.Text2, fontSize = 13.sp)
                    Text("${fmtD(t.dur)} · ${t.category} · ${t.subject}", color = C.Text2, fontSize = 13.sp)
                }
                RoundBtn(R.drawable.plan_ic_pencil, "Edit task") { go(Screen.PlanEdit) }
            }
        }
        FocusCard(Modifier.fillMaxWidth().enter(2), padding = 6.dp) {
            FormRow("Allowed apps", "${store.allowedApps.size} apps") { go(Screen.Apps) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.Hairline))
            FormRow("Allowed callers", "${store.callers.size} contacts") { go(Screen.Callers) }
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(C.Hairline))
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Strict lock", color = C.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
                FocusToggle(strict, { strict = it; store.strictLock = it })
            }
        }
        // the existing session controller needs a subject it knows; Personal maps to Other
        PrimaryButton(if (AppState.coreReady) "Start Focus" else "Finish setup to begin") {
            if (!AppState.coreReady) go(Screen.Setup)
            else SessionController.start(ctx, t.dur, t.title, if (t.subject == "Personal") "Other" else t.subject, t.id)
        }
    }
}

// --------------------------------------------------------------------------------------
// A18_10 Daily Summary
// --------------------------------------------------------------------------------------
@Composable
private fun PlanSummary(back: () -> Unit) {
    val day = PlanState.forDate()
    val planned = day.sumOf { it.dur }
    val pct = (planned * 100 / PLAN_DAY_MINUTES).coerceIn(0, 100)
    val focus = day.filter { it.done && it.category == "Study" }.sumOf { it.dur }
    val workout = day.filter { it.done && it.category == "Workout" }.sumOf { it.dur }
    val doneN = day.count { it.done }
    Page(back, "Daily Summary", PlanState.date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))) {
        FocusCard(Modifier.fillMaxWidth().enter(1)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(pct / 100f, 96.dp, "$pct%")
                Column(Modifier.padding(start = 16.dp)) {
                    Text("${fmtD(planned)} / 14h", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("planned today", color = C.Text2, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("$doneN completed · ${day.size - doneN} remaining · ${dayConflicts(day).size.let { (it + 1) / 2 }} overlap", color = C.Text2, fontSize = 12.sp)
                }
            }
        }
        listOf(
            Triple("Focus time", fmtD(focus), focus / 360f),
            Triple("Workout time", fmtD(workout), workout / 120f),
            Triple("Tasks completed", "$doneN / ${day.size}", if (day.isEmpty()) 0f else doneN / day.size.toFloat()),
        ).forEachIndexed { i, (label, value, f) ->
            FocusCard(Modifier.fillMaxWidth().enter(2 + i), radius = 20.dp) {
                Row { Text(label, color = C.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text(value, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(10.dp))
                ProgBar(f, height = 6.dp)
            }
        }
        PrimaryButton("View Agenda") { back() }
    }
}

// --------------------------------------------------------------------------------------
// A18_11 Plan Settings
// --------------------------------------------------------------------------------------
@Composable
private fun PlanSettings(back: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { Store.get(ctx) }
    var showDone by remember { mutableStateOf(PlanPrefs.showDone(ctx)) }
    var showFree by remember { mutableStateOf(PlanPrefs.showFree(ctx)) }
    var quick by remember { mutableStateOf(PlanPrefs.quickHome(ctx)) }
    var sync by remember { mutableStateOf(st.bool("plan_sync", false)) }
    var smart by remember { mutableStateOf(st.bool("plan_smart", true)) }
    var defStart by remember { mutableIntStateOf(PlanPrefs.defStart(ctx)) }
    var defDur by remember { mutableIntStateOf(PlanPrefs.defDur(ctx)) }
    var remind by remember { mutableIntStateOf(PlanPrefs.remind(ctx)) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val starts = remember { (0..287).map { it * 5 } }
    val durs = remember { (1..32).map { it * 15 } }
    val reminds = remember { listOf(0, 5, 10, 15, 30, 60) }

    @Composable fun Sw(title: String, sub: String?, v: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.White, fontSize = 15.sp)
                if (sub != null) Text(sub, color = C.Text3, fontSize = 12.sp)
            }
            FocusToggle(v, onChange)
        }
    }
    Page(back, "Plan Settings", "How your agenda looks and behaves") {
        FocusCard(Modifier.fillMaxWidth().enter(1), padding = 6.dp) {
            Sw("Show completed tasks", null, showDone) { showDone = it; st.putBool("plan_show_done", it) }
            Sw("Show free time slots", null, showFree) { showFree = it; st.putBool("plan_show_free", it) }
            Sw("Quick add on home", "Plus button on Plan My Day", quick) { quick = it; st.putBool("plan_quick_home", it) }
        }
        FocusCard(Modifier.fillMaxWidth().enter(2), padding = 6.dp) {
            FormRow("Default start time", fmtT(defStart)) { sheet = "start" }
            FormRow("Default duration", fmtD(defDur)) { sheet = "dur" }
            FormRow("Reminder before task", if (remind == 0) "Off" else "$remind minutes") { sheet = "remind" }
        }
        FocusCard(Modifier.fillMaxWidth().enter(3), padding = 6.dp) {
            // Stored only: calendar sync and smart suggestions are not wired to anything yet.
            Sw("Sync with calendar", "Saved. Calendar access is not connected yet.", sync) { sync = it; st.putBool("plan_sync", it) }
            Sw("Smart suggestions", "Saved. Suggestions are not generated yet.", smart) { smart = it; st.putBool("plan_smart", it) }
        }
    }
    when (sheet) {
        "start" -> WheelSheet("Default start time", starts.map { fmtT(it) }, (defStart / 5).coerceIn(0, 287), { defStart = starts[it]; st.putInt("plan_def_start", defStart) }) { sheet = null }
        "dur" -> WheelSheet("Default duration", durs.map { fmtD(it) }, durs.indexOf(defDur).coerceAtLeast(0), { defDur = durs[it]; st.putInt("plan_def_dur", defDur) }) { sheet = null }
        "remind" -> WheelSheet("Reminder (minutes before)", reminds.map { if (it == 0) "Off" else "$it" }, reminds.indexOf(remind).coerceAtLeast(0), { remind = reminds[it]; st.putInt("plan_remind", remind) }) { sheet = null }
    }
}

// --------------------------------------------------------------------------------------
// A18_12 Quick Add (sheet)
// --------------------------------------------------------------------------------------
@Composable
private fun QuickAddSheet(date: LocalDate, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf("Study") }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableIntStateOf(PlanPrefs.defStart(ctx)) }
    var dur by remember { mutableIntStateOf(30) }
    var picking by remember { mutableStateOf(false) }
    val starts = remember { (0..287).map { it * 5 } }
    FocusSheet(onDismiss) {
        Text("Quick Add", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { PLAN_CATEGORIES.forEach { c -> PChip(c, c == cat) { cat = c } } }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Task name") }, placeholder = { Text("NEET Questions") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).background(C.Raised).clickable { picking = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Start", color = C.Text2, modifier = Modifier.weight(1f)); Text(fmtT(start), color = C.White, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 45, 60, 90).forEach { d -> PChip(if (d >= 60) fmtD(d).replace(" 0m", "") else "$d min", d == dur, Modifier.weight(1f)) { dur = d } } }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Add Task", enabled = name.isNotBlank() && start + dur <= 24 * 60) {
            PlanState.upsert(ctx, PlanTask(PlanState.newId(), date.toString(), name.trim(), cat, if (cat == "Study") "Physics" else cat, start, dur))
            onDismiss()
        }
    }
    if (picking) WheelSheet("Start time", starts.map { fmtT(it) }, (start / 5).coerceIn(0, 287), { start = starts[it] }) { picking = false }
}
