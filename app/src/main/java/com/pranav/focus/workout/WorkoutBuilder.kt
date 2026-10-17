package com.pranav.focus.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.FocusSheet
import com.pranav.focus.ui.Ico
import com.pranav.focus.ui.Screen
import kotlinx.coroutines.delay

private var uidSeq = -1L
private fun newUid(): Long = uidSeq--

// ====================================================================================================================
// C4 — Day Builder
// ====================================================================================================================
@Composable
fun WorkoutDayScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val plan = remember(tick, WorkoutNav.planId) { WorkoutNav.planId?.let { repo.plan(it) } ?: repo.activePlan() }
    if (plan == null) { WkPage("Day Builder", onBack = back) { Text("Create a plan first.", color = C.Text2) }; return }
    val slot = WorkoutNav.slot.coerceIn(1, 7)
    val days = remember(tick, plan.id) { repo.days(plan.id) }
    val routines = remember { repo.routines() }
    val byId = remember(routines) { routines.associateBy { it.id } }
    val loaded = remember(tick, plan.id, slot) {
        val d = repo.day(plan.id, slot)
        DayDraft(plan.id, slot, d?.morning, d?.altMorning, d?.gym, d?.notes ?: "", repo.dayLines(plan.id, slot))
    }
    val draft = WorkoutNav.draft?.takeIf { it.planId == plan.id && it.slot == slot } ?: loaded
    fun upd(f: (DayDraft) -> DayDraft) { WorkoutNav.draft = f(draft).copy(dirty = true) }

    var edit by remember { mutableStateOf<Int?>(null) }
    var pickKind by remember { mutableStateOf<String?>(null) }   // "morning" | "gym"
    var copySheet by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(false) }
    var discardThen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun leave(action: () -> Unit) { if (draft.dirty) discardThen = action else action() }
    fun save(): Boolean = runCatching { repo.saveDay(plan.id, slot, draft.morning, draft.altMorning, draft.gym, draft.notes, draft.lines) }
        .onSuccess { WorkoutNav.draft = null; WorkoutNav.bump(); msg = "Saved" }.onFailure { info = it.message ?: "Could not save." }.isSuccess

    val gymR = byId[draft.gym]; val morningR = byId[draft.morning]
    val minutes = if (gymR != null || draft.lines.isNotEmpty() && morningR == null) WorkoutLogic.estimateMinutes(draft.lines.map { WorkoutLogic.Est(it.sets, it.restS, it.type) })
                  else morningR?.let { (it.minLow + it.minHigh) / 2 } ?: 0
    val warnings = remember(tick, plan.id) { repo.scheduleWarnings(plan.id) }

    WkPage(
        title = "Day Builder", subtitle = "Customize your training day.", onBack = { leave(back) },
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WkButton("Preview Day", Modifier.weight(1f), icon = wkIco("eye"), height = 56.dp) { preview = true }
                WkButton("Save Day", Modifier.weight(1.2f), primary = true, icon = wkIco("check"), height = 56.dp) { save() }
            }
        },
    ) {
        // ---- weekday strip ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEach { d ->
                val m = byId[d.morning]; val g = byId[d.gym]; val alt = byId[d.altMorning]
                val label = listOfNotNull(g?.code, if (m != null && alt != null) "${m.code}/${alt.code}" else m?.code).joinToString(" + ").ifBlank { "Rest" }
                DayCell(WEEKDAY_SHORT[WorkoutLogic.weekdayOf(d.slot, plan.slot1Weekday).value - 1], "Day ${d.slot}", label, (g ?: m)?.accent ?: "grey", d.slot == slot, Modifier.weight(1f)) {
                    if (d.slot != slot) leave { WorkoutNav.draft = null; WorkoutNav.slot = d.slot; WorkoutNav.bump() }
                }
            }
        }
        warnings.firstOrNull { it.slot == slot || it.slot == slot - 1 }?.let { Text("Heads up: ${it.message}", color = C.Text2, fontSize = 12.sp) }

        // ---- day plan ----
        WkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Day $slot Plan", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("Assign morning and gym routines for this day.", color = C.Text2, fontSize = 12.sp) }
                WkButton("Copy to…", icon = wkIco("layers"), height = 44.dp) { copySheet = true }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoutineSlotCard("Morning Routine", wkIco("sun"), morningR, Modifier.weight(1f)) { pickKind = "morning" }
                RoutineSlotCard("Gym Routine", wkIco("dumbbell"), gymR, Modifier.weight(1f)) { pickKind = "gym" }
            }
        }

        // ---- exercise order ----
        WkCard(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Schedule & Exercise Order", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text("Reorder, add, remove or swap exercises for this day.", color = C.Text2, fontSize = 12.sp) }
                WkButton("Add", icon = wkIco("plus"), height = 44.dp) { WorkoutNav.draft = draft; go(Screen.WorkoutAdd) }
            }
            Spacer(Modifier.height(10.dp))
            if (draft.lines.isEmpty()) Text("No exercises yet. Choose a routine above or add exercises.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.lines.forEachIndexed { i, l ->
                    androidx.compose.runtime.key(l.uid) {
                        BuilderRow(l, i, draft.lines.size,
                            onMove = { from, to -> upd { it.copy(lines = WorkoutLogic.move(it.lines, from, to)) } },
                            onEdit = { edit = i },
                            onRemove = { upd { it.copy(lines = it.lines.filterIndexed { j, _ -> j != i }) } })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { WorkoutNav.draft = draft; go(Screen.WorkoutAdd) }
                    .drawBehind { drawRoundRect(Color.White.copy(alpha = 0.25f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) { Text("${draft.lines.size + 1}", color = C.Text2, fontSize = 14.sp); Spacer(Modifier.width(14.dp)); Ico(wkIco("plus"), 20.dp, C.White); Spacer(Modifier.width(8.dp)); Text("Add Exercise", color = C.White, fontSize = 15.sp) }
        }

        // ---- notes + time ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WkCard(Modifier.weight(1.3f), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("note"), 18.dp, C.White); Spacer(Modifier.width(8.dp)); Text("Day Notes", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(" (optional)", color = C.Text2, fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                WkField(draft.notes, { n -> upd { it.copy(notes = n) } }, "Add notes for this day…", singleLine = false, maxLen = WorkoutLogic.NOTES_MAX, minHeight = 64.dp)
                Text("${draft.notes.length}/${WorkoutLogic.NOTES_MAX}", color = C.Text3, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
            }
            WkCard(Modifier.weight(1f), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("clock"), 18.dp, C.White); Spacer(Modifier.width(8.dp)); Text("Estimated Time", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                Text("~ $minutes min", color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("Based on sets, reps and rest times", color = C.Text2, fontSize = 11.sp)
            }
        }
        msg?.let { Text(it, color = C.Text2, fontSize = 13.sp); LaunchedEffect(it) { delay(1800); msg = null } }
    }

    // ---- sheets & dialogs ----
    pickKind?.let { kind ->
        val options = routines.filter { if (kind == "gym") it.isGym else !it.isGym }
        FocusSheet({ pickKind = null }) {
            Text(if (kind == "gym") "Gym routine" else "Morning routine", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
            Text("Changing the routine replaces this day's exercise list with that routine's.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            (options.map<Routine, Routine?> { it } + listOf(null)).forEach { r ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                    val gym = if (kind == "gym") r?.id else draft.gym
                    val morning = if (kind == "morning") r?.id else draft.morning
                    val alt = if (kind == "morning") null else draft.altMorning
                    val lines = (gym ?: morning)?.let { repo.routineLines(it) } ?: emptyList()
                    WorkoutNav.draft = draft.copy(gym = gym, morning = morning, altMorning = alt, lines = lines, dirty = true); pickKind = null
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r == null) "None (rest)" else "${r.code} · ${r.name}", color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if ((kind == "gym" && r?.id == draft.gym && r != null) || (kind == "morning" && r?.id == draft.morning && r != null)) Ico(wkIco("check"), 18.dp, C.White)
                }
            }
        }
    }
    if (copySheet) FocusSheet({ copySheet = false }) {
        Text("Copy Day $slot to…", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
        Text("Saves this day first, then overwrites the chosen day.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        days.filter { it.slot != slot }.forEach { d ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                copySheet = false
                if (save()) runCatching { repo.copyDay(plan.id, slot, d.slot) }.onSuccess { WorkoutNav.bump(); msg = "Copied to Day ${d.slot}" }.onFailure { info = it.message }
            }, verticalAlignment = Alignment.CenterVertically) { Text("${WEEKDAY_SHORT[WorkoutLogic.weekdayOf(d.slot, plan.slot1Weekday).value - 1]} · Day ${d.slot}", color = C.White, fontSize = 16.sp) }
        }
    }
    edit?.let { idx ->
        val l = draft.lines.getOrNull(idx)
        if (l == null) edit = null else LineEditorSheet(l, { nl -> upd { it.copy(lines = it.lines.mapIndexed { j, x -> if (j == idx) nl else x }) }; edit = null }, { edit = null })
    }
    if (preview) InfoDialog("Day $slot preview", (listOfNotNull(morningR?.let { "Morning: ${it.code} · ${it.name}" }, gymR?.let { "Gym: ${it.code} · ${it.name}" }) +
        draft.lines.mapIndexed { i, l -> "${i + 1}. ${l.name} — ${l.prescription}, rest ${l.rest}" + (l.superset?.let { " (superset $it)" } ?: "") } + "~ $minutes min").joinToString("\n")) { preview = false }
    discardThen?.let { act -> ConfirmDialog("Discard changes?", "You have unsaved changes to this day.", "Discard", { WorkoutNav.draft = null; discardThen = null; act() }, { discardThen = null }) }
    info?.let { InfoDialog("Day Builder", it) { info = null } }
}

@Composable
private fun RoutineSlotCard(title: String, icon: Int, r: Routine?, modifier: Modifier, onChange: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Ico(icon, 18.dp, C.White); Spacer(Modifier.width(8.dp)); Text(title, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        if (r == null) Text("None — rest", color = C.Text2, fontSize = 13.sp, modifier = Modifier.heightIn(min = 70.dp))
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CoverArt(r, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), scrim = false)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${r.code} · ${r.name}", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
                Text(r.focus, color = C.Text2, fontSize = 11.sp, maxLines = 1); Text(r.labelPlans, color = C.Text2, fontSize = 11.sp, maxLines = 1)
            }
        }
        WkButton("Change", Modifier.fillMaxWidth(), icon = wkIco("swap"), height = 44.dp, onClick = onChange)
    }
}

/** One exercise row. Drag the grip to reorder (swaps when the finger crosses 60% of a row); the overflow menu offers the same without dragging. */
@Composable
private fun BuilderRow(l: Line, index: Int, count: Int, onMove: (Int, Int) -> Unit, onEdit: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableIntStateOf(1) }
    val idx by rememberUpdatedState(index)
    val last by rememberUpdatedState(count - 1)
    val move by rememberUpdatedState(onMove)
    Row(
        Modifier.fillMaxWidth().onSizeChanged { h = it.height }.graphicsLayer { translationY = dragY }
            .clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, if (dragY != 0f) C.White.copy(alpha = 0.6f) else C.Hairline, RoundedCornerShape(14.dp)).padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", color = C.White, fontSize = 15.sp, modifier = Modifier.width(22.dp))
        ExerciseThumb(l.exerciseId, 52.dp, 10.dp); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(role = Role.Button, onClick = onEdit), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(l.name, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 17.sp)
            Text("${l.equipment} · ${l.prescription} · ${l.rest}", color = C.Text2, fontSize = 11.sp, maxLines = 2, lineHeight = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { if (l.muscleTag.isNotEmpty()) Tag(l.muscleTag); l.superset?.let { Tag("Superset $it") } }
        }
        Box {
            Box(Modifier.size(44.dp).clickable(role = Role.Button) { menu = true }.semantics { contentDescription = "Exercise options" }, contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_more, 20.dp, C.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit sets / reps / rest", color = C.White) }, onClick = { menu = false; onEdit() })
                if (index > 0) DropdownMenuItem(text = { Text("Move up", color = C.White) }, onClick = { menu = false; onMove(index, index - 1) })
                if (index < count - 1) DropdownMenuItem(text = { Text("Move down", color = C.White) }, onClick = { menu = false; onMove(index, index + 1) })
                DropdownMenuItem(text = { Text("Remove from day", color = Color(0xFFFF8A8A)) }, onClick = { menu = false; onRemove() })
            }
        }
        Box(
            Modifier.size(44.dp).semantics { contentDescription = "Drag to reorder" }.pointerInput(l.uid) {
                detectDragGestures(onDragEnd = { dragY = 0f }, onDragCancel = { dragY = 0f }) { change, amount ->
                    change.consume(); dragY += amount.y
                    val rowH = h.toFloat().coerceAtLeast(1f)
                    if (dragY > rowH * 0.6f && idx < last) { move(idx, idx + 1); dragY -= rowH }
                    else if (dragY < -rowH * 0.6f && idx > 0) { move(idx, idx - 1); dragY += rowH }
                }
            },
            contentAlignment = Alignment.Center,
        ) { Ico(R.drawable.plan_ic_grip_dots, 20.dp, C.Text2) }
    }
}

@Composable
private fun LineEditorSheet(l: Line, onSave: (Line) -> Unit, onDismiss: () -> Unit) {
    var sets by remember { mutableIntStateOf(l.sets) }
    var reps by remember { mutableStateOf(l.repText) }
    var rest by remember { mutableIntStateOf(l.restS) }
    var group by remember { mutableStateOf(l.superset) }
    FocusSheet(onDismiss) {
        Text(l.name, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Stepper("Sets", "$sets", { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(10) })
        Spacer(Modifier.height(10.dp))
        Text("Reps / distance / time (e.g. 8-10, 30 m, 45 sec)", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        WkField(reps, { reps = it }, "8-10", maxLen = 30)
        Spacer(Modifier.height(10.dp))
        Stepper("Rest", WorkoutLogic.restLabel(rest), { rest = (rest - 15).coerceAtLeast(0) }, { rest = (rest + 15).coerceAtMost(600) })
        Spacer(Modifier.height(10.dp))
        Text("Superset group (no rest inside a pair)", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf<String?>(null, "A", "B", "C", "D")) { g -> FilterPill(g ?: "None", group == g) { group = g } } }
        Spacer(Modifier.height(16.dp))
        WkButton("Apply", Modifier.fillMaxWidth(), primary = true) {
            val text = reps.trim().ifEmpty { l.repText }
            onSave(l.copy(sets = sets, repText = text, restS = rest, restText = WorkoutLogic.restLabel(rest).let { if (group != null && rest == 0) "No rest · superset" else it }, superset = group))
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        RoundBtn("Decrease $label", R.drawable.ic_remove, onMinus)
        Text(value, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(92.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundBtn("Increase $label", R.drawable.ic_add, onPlus)
    }
}

// ====================================================================================================================
// C5 — Add exercise (no reference screenshot was supplied; built from the canonical prompt + C0–C5 spec)
// ====================================================================================================================
@Composable
fun WorkoutAddScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    var query by remember { mutableStateOf("") }
    var muscles by remember { mutableStateOf(setOf<String>()) }
    var equips by remember { mutableStateOf(setOf<String>()) }
    var favs by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(listOf<String>()) }
    var custom by remember { mutableStateOf(false) }
    val draft = WorkoutNav.draft
    val inDay = draft?.lines?.map { it.exerciseId }?.toSet() ?: emptySet()
    val results = remember(tick, query, muscles, equips, favs) {
        repo.searchExercises(query, muscles, equips, favs).sortedWith(compareByDescending<Exercise> { it.favourite }.thenByDescending { it.lastUsedTs ?: 0L })
    }
    val lastLoads = remember(tick) { repo.lastUsedLoads() }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }

    WkPage(
        title = "Add Exercise", subtitle = "Search the library or create your own.", onBack = back,
        bottomBar = {
            WkButton(if (selected.isEmpty()) "Select exercises" else "Add ${selected.size} exercise${if (selected.size == 1) "" else "s"}", Modifier.fillMaxWidth(), primary = true, enabled = selected.isNotEmpty(), icon = wkIco("plus"), height = 56.dp) {
                val d = draft
                if (d != null) WorkoutNav.draft = d.copy(lines = d.lines + selected.mapNotNull { repo.newLine(it, newUid()) }, dirty = true)
                back()
            }
        },
    ) {
        WkField(query, { query = it }, "Search exercises", trailing = {
            if (query.isNotEmpty()) Box(Modifier.size(48.dp).clickable(role = Role.Button) { query = "" }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 16.dp, C.Text2) }
            else Ico(wkIco("search"), 20.dp, C.Text2)
        })
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).clickable(role = Role.Button) { custom = true }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(wkIco("plus"), 20.dp, C.White); Spacer(Modifier.width(12.dp)); Text("Create custom exercise", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterPill("Favourites", favs) { favs = !favs } }
            items(WorkoutLogic.MUSCLE_FILTERS) { m -> FilterPill(m, m in muscles) { muscles = if (m in muscles) muscles - m else muscles + m } }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(WorkoutLogic.SPECIFIC_MUSCLES) { m -> FilterPill(m, m in muscles) { muscles = if (m in muscles) muscles - m else muscles + m } } }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(WorkoutLogic.EQUIPMENT_FILTERS) { e -> FilterPill(e, e in equips) { equips = if (e in equips) equips - e else equips + e } } }

        if (results.isEmpty()) Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Ico(wkIco("search"), 32.dp, C.Text3); Text("No exercises found", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Text("Try another search or filter, or create a custom exercise.", color = C.Text2, fontSize = 13.sp)
        }
        Column {
            results.forEach { e ->
                val sel = e.id in selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(role = Role.Checkbox) { toggle(e.id) }.semantics { contentDescription = "${e.name}, ${if (sel) "selected" else "not selected"}" }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ExerciseThumb(e.id, 48.dp, 12.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(e.name, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                        Text(listOf(e.primary, e.equipment, WorkoutLogic.lastUsedLabel(lastLoads[e.id]).orEmpty()).filter { it.isNotBlank() }.joinToString(" • ") + (if (e.id in inDay) " • In this day" else ""), color = C.Text2, fontSize = 12.sp, maxLines = 1)
                    }
                    Box(Modifier.size(48.dp).clickable(role = Role.Button) { repo.toggleFavourite(e.id); WorkoutNav.bump() }.semantics { contentDescription = if (e.favourite) "Remove favourite" else "Favourite" }, contentAlignment = Alignment.Center) {
                        Ico(wkIco("heart"), 20.dp, if (e.favourite) C.White else C.Text3)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (sel) C.White else Color.Transparent).border(1.5.dp, if (sel) C.White else C.Text3, CircleShape), contentAlignment = Alignment.Center) {
                            if (sel) Ico(wkIco("check"), 16.dp, C.Black)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hairline))
            }
        }
    }

    if (custom) CustomExerciseSheet(repo, { id -> custom = false; WorkoutNav.bump(); selected = selected + id }, { custom = false })
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CustomExerciseSheet(repo: WorkoutRepo, onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf(setOf<String>()) }
    var secondary by remember { mutableStateOf(setOf<String>()) }
    var tracking by remember { mutableStateOf(0) }
    var pattern by remember { mutableStateOf<String?>(null) }
    var equip by remember { mutableStateOf<String?>(null) }
    var bwText by remember { mutableStateOf("1.0") }
    var err by remember { mutableStateOf<String?>(null) }
    val type = WorkoutLogic.TRACKING_TYPES[tracking].second
    val showBw = tracking == 1 || equip == "Bodyweight"
    @Composable fun label(t: String) = Text(t, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    FocusSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Custom exercise", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            WkField(name, { name = it; err = null }, "Exercise name", maxLen = 60)
            label("Tracking")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutLogic.TRACKING_TYPES.forEachIndexed { i, t -> FilterPill(t.first, tracking == i) { tracking = i; err = null } }
            }
            label("Movement pattern")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutLogic.MOVEMENT_PATTERNS.forEach { m -> FilterPill(m, pattern == m) { pattern = if (pattern == m) null else m } }
            }
            label("Primary muscles (choose one or more)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutLogic.ALL_MUSCLES.forEach { m -> FilterPill(m, m in primary) { primary = if (m in primary) primary - m else primary + m; secondary = secondary - m; err = null } }
            }
            label("Secondary muscles (optional)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutLogic.ALL_MUSCLES.filter { it !in primary }.forEach { m -> FilterPill(m, m in secondary) { secondary = if (m in secondary) secondary - m else secondary + m } }
            }
            label("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutLogic.CUSTOM_EQUIPMENT.forEach { e -> FilterPill(e, equip == e) { equip = if (equip == e) null else e } }
            }
            if (showBw) {
                label("Bodyweight factor (share of bodyweight moved, e.g. 0.65 for a push-up)")
                WkField(bwText, { bwText = it.filter { c -> c.isDigit() || c == '.' }.take(4); err = null }, "1.0", numeric = true)
            }
            err?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(18.dp))
            WkButton("Save Exercise", Modifier.fillMaxWidth(), primary = true) {
                val bw = if (showBw) bwText.toDoubleOrNull().also { if (it == null) { err = "Enter a bodyweight factor like 0.65, or 1 for full bodyweight."; return@WkButton } } else null
                runCatching {
                    repo.createCustomExercise(
                        name, equip ?: "", WorkoutLogic.ALL_MUSCLES.filter { it in primary }.joinToString(", "), type,
                        secondary = WorkoutLogic.ALL_MUSCLES.filter { it in secondary }.joinToString(", "),
                        movementPattern = pattern ?: "", bodyweightFactor = bw,
                    )
                }.onSuccess(onCreated).onFailure { err = it.message ?: "Could not save exercise." }
            }
            Spacer(Modifier.height(8.dp))
            WkButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
