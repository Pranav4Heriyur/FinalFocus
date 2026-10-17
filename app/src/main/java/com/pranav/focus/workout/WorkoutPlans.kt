package com.pranav.focus.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.ui.C
import com.pranav.focus.ui.FocusSheet
import com.pranav.focus.ui.Ico
import com.pranav.focus.ui.Screen

private fun weekdayName(slot: Int, plan: Plan) = WEEKDAY_SHORT[WorkoutLogic.weekdayOf(slot, plan.slot1Weekday).value - 1]

// ====================================================================================================================
// C1 — Plans
// ====================================================================================================================
@Composable
fun WorkoutPlansScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val plans = remember(tick) { repo.plans() }
    val routines = remember(tick) { repo.routines() }
    val byId = remember(routines) { routines.associateBy { it.id } }
    val gym = routines.filter { it.isGym }; val morning = routines.filter { !it.isGym }
    val active = plans.firstOrNull { it.active }
    val days = remember(tick, active?.id) { active?.let { repo.days(it.id) } ?: emptyList() }
    val warnings = remember(tick, active?.id) { active?.let { repo.scheduleWarnings(it.id) } ?: emptyList() }
    var tab by remember { mutableIntStateOf(0) }
    var manage by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Plan?>(null) }
    var dupFor by remember { mutableStateOf<Plan?>(null) }
    var dupName by remember { mutableStateOf("") }
    var dupErr by remember { mutableStateOf<String?>(null) }

    WkPage(title = "Plans", subtitle = "Prebuilt routines. Fully customizable.", tabBarClear = true, trailing = {
        WkButton(if (manage) "Done" else "Manage", icon = wkIco("filter"), height = 48.dp) { manage = !manage }
        Spacer(Modifier.width(8.dp))
        WkButton("New Plan", primary = true, icon = wkIco("plus"), height = 48.dp) { WorkoutNav.createTemplate = null; go(Screen.WorkoutCreate) }
    }) {
        SegTabs(listOf("My Plans", "Program Preset", "Templates", "Routines"), tab) { tab = it }

        when (tab) {
            0 -> {
                if (active == null) WkCard { Text("No active plan", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("Create a plan or activate one below.", color = C.Text2, fontSize = 14.sp) }
                else {
                    CoverArt(gym.firstOrNull(), Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, C.Hairline, RoundedCornerShape(22.dp))) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("medal"), 16.dp, C.White); Spacer(Modifier.width(8.dp)); Text("ACTIVE PROGRAM", color = C.Text2, fontSize = 11.sp, letterSpacing = 1.2.sp) }
                            Text(active.name, color = C.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                            val pr = remember(tick, active.id) { repo.planRoutines(active.id) }
                            Text("${pr.count { it.isGym }}-routine split • ${pr.count { !it.isGym }} morning routines • 7-day schedule", color = C.Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(60.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text("${pr.count { it.isGym }} gym • ${pr.count { !it.isGym }} morning", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("7 days / week", color = C.Text2, fontSize = 12.sp) }
                                WkButton("View Details", height = 48.dp) { WorkoutNav.planId = active.id; WorkoutNav.slot = 1; go(Screen.WorkoutDay) }
                            }
                        }
                    }
                }
                SectionTitle("Gym Routines", gym.size, "View all") { tab = 3 }
                gym.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { r -> GymTile(r, r.focus, Modifier.weight(1f), plansStyle = true) { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                SectionTitle("Morning Routines", morning.size, "View all") { tab = 3 }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(morning, key = { it.id }) { r -> MorningTile(r, Modifier.width(128.dp), true) { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) } }
                }
                if (active != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Weekly Schedule", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold); Text("7-day structure (editable)", color = C.Text2, fontSize = 13.sp) }
                        WkButton("Edit Schedule", icon = wkIco("calendar"), height = 48.dp) { WorkoutNav.planId = active.id; WorkoutNav.slot = WorkoutLogic.slotFor(com.pranav.focus.data.AppClock.today(), active.slot1Weekday); go(Screen.WorkoutDay) }
                    }
                    ScheduleStrip(active, days, byId, WorkoutLogic.slotFor(com.pranav.focus.data.AppClock.today(), active.slot1Weekday)) { slot -> WorkoutNav.planId = active.id; WorkoutNav.slot = slot; go(Screen.WorkoutDay) }
                    warnings.forEach { w -> Row(verticalAlignment = Alignment.Top) { Ico(wkIco("info"), 16.dp, C.Text2); Spacer(Modifier.width(8.dp)); Text(w.message + " Heavy leg days recover better without a run or ruck the day before.", color = C.Text2, fontSize = 12.sp) } }
                }
                RulesRow()
                // other plans
                val others = plans.filter { it.id != active?.id }
                if (others.isNotEmpty()) {
                    Text("Other plans", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    others.forEach { p -> PlanRow(p, manage, repo, onOpen = { WorkoutNav.planId = p.id; WorkoutNav.slot = 1; go(Screen.WorkoutDay) }, onActivate = { repo.setActivePlan(p.id); WorkoutNav.bump() }, onDuplicate = { dupFor = p; dupName = p.name + " copy"; dupErr = null }, onDelete = { confirmDelete = p }) }
                }
                if (active != null && manage) PlanRow(active, true, repo, onOpen = {}, onActivate = {}, onDuplicate = { dupFor = active; dupName = active.name + " copy"; dupErr = null }, onDelete = { confirmDelete = active })
            }
            1 -> {
                val presets = plans.filter { it.preset }
                presets.forEach { p ->
                    WkCard {
                        Text(p.name, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(p.description, color = C.Text2, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WkButton("Open", Modifier.weight(1f), height = 48.dp) { WorkoutNav.planId = p.id; WorkoutNav.slot = 1; go(Screen.WorkoutDay) }
                            WkButton("Duplicate to edit", Modifier.weight(1f), primary = true, height = 48.dp) { dupFor = p; dupName = p.name + " copy"; dupErr = null }
                        }
                    }
                }
                Text("Presets are protected: edits to a day are stored per plan, and the preset routines themselves are never changed. Duplicate a preset for a fully independent copy.", color = C.Text3, fontSize = 12.sp)
            }
            2 -> WorkoutCatalog.TEMPLATES.forEach { t ->
                WkCard(onClick = if (t.available) ({ WorkoutNav.createTemplate = t.id; go(Screen.WorkoutCreate) }) else null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = if (t.available) C.White else C.Text3, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (t.available) "${t.subtitle} • ${t.routineIds.size} routines • 7-day schedule" else "${t.subtitle} • no routines supplied yet", color = C.Text2, fontSize = 12.sp)
                        }
                        if (t.available) Ico(wkIco("chevron-right"), 18.dp, C.Text2)
                    }
                }
            }
            else -> {
                routines.forEach { r ->
                    WkCard(onClick = { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) }, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AccentDot(r.accent); Spacer(Modifier.width(10.dp)); Tag(r.code); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) { Text(r.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("${r.exerciseCount} exercises • ${r.labelPlans}", color = C.Text2, fontSize = 12.sp) }
                            Ico(wkIco("chevron-right"), 16.dp, C.Text2)
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { p ->
        ConfirmDialog("Delete \"${p.name}\"?", "The plan and its schedule are removed. Your logged workouts are kept.", "Delete", {
            repo.deletePlan(p.id); confirmDelete = null; WorkoutNav.bump()
        }, { confirmDelete = null })
    }
    dupFor?.let { p ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dupFor = null }, containerColor = C.Sheet, titleContentColor = C.White,
            title = { Text("Duplicate plan", fontWeight = FontWeight.SemiBold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { WkField(dupName, { dupName = it }, "New plan name", maxLen = WorkoutLogic.NAME_MAX); dupErr?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp) } } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { runCatching { repo.duplicatePlan(p.id, dupName) }.onSuccess { dupFor = null; WorkoutNav.bump() }.onFailure { dupErr = it.message } }) { Text("Duplicate", color = C.White, fontWeight = FontWeight.SemiBold) } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { dupFor = null }) { Text("Cancel", color = C.Text2) } },
        )
    }
}

@Composable
private fun PlanRow(p: Plan, manage: Boolean, repo: WorkoutRepo, onOpen: () -> Unit, onActivate: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    WkCard(onClick = onOpen, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text((if (p.preset) "Preset" else "Custom") + (if (p.active) " • Active" else ""), color = C.Text2, fontSize = 12.sp)
            }
        }
        if (manage) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!p.active) WkButton("Set active", Modifier.weight(1f), height = 48.dp, onClick = onActivate)
            WkButton("Duplicate", Modifier.weight(1f), height = 48.dp, onClick = onDuplicate)
            if (!p.preset) WkButton("Delete", Modifier.weight(1f), height = 48.dp, onClick = onDelete)
        }
    }
}

@Composable
fun ScheduleStrip(plan: Plan, days: List<PlanDay>, byId: Map<String, Routine>, todaySlot: Int, onTap: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        days.forEach { d ->
            val m = byId[d.morning]; val g = byId[d.gym]; val alt = byId[d.altMorning]
            val mCode = if (m != null && alt != null) "${m.code} / ${alt.code}" else m?.code
            Column(
                Modifier.weight(1f).heightIn(min = 132.dp).clip(RoundedCornerShape(12.dp)).background(C.Card)
                    .border(if (d.slot == todaySlot) 1.5.dp else 1.dp, if (d.slot == todaySlot) C.White else C.Hairline, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onTap(d.slot) }.padding(vertical = 8.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(weekdayName(d.slot, plan), color = C.Text2, fontSize = 11.sp)
                Text(mCode ?: "—", color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(m?.name?.substringBefore(" +") ?: "", color = C.Text2, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Clip)
                Box(Modifier.width(24.dp).height(1.dp).background(C.Hairline))
                Text(g?.code ?: "Rest", color = if (g != null) C.White else C.Text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(g?.name?.substringBefore(" Body") ?: "", color = C.Text2, fontSize = 9.sp, maxLines = 1)
                Ico(wkIco(when { g != null -> "dumbbell"; m?.kind == "run" -> "run"; m?.kind == "ruck" -> "ruck"; m != null -> "mobility"; else -> "sun" }), 16.dp, C.White)
                AccentDot((g ?: m)?.accent ?: "grey")
            }
        }
    }
}

@Composable
fun RulesRow() {
    Text("Global Program Rules", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkoutCatalog.PROGRAM_RULES.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (t, body, icon) ->
                    WkCard(Modifier.weight(1f), radius = 16.dp, padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco(icon), 16.dp, C.White); Spacer(Modifier.width(8.dp)); Text(t, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        Text(body, color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

// ====================================================================================================================
// C2 — Plan (routine) detail
// ====================================================================================================================
private val ROUTINE_TABS = listOf("Exercises", "Overview", "Progression", "Notes")

@Composable
fun WorkoutRoutineScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val rid = WorkoutNav.routineId
    val r = remember(rid, tick) { repo.routine(rid) }
    if (r == null) { WkPage("Routine not found", onBack = back) {}; return }
    val lines = remember(rid, tick) { repo.routineLines(r.id) }
    val plan = remember(tick) { repo.activePlan() }
    val days = remember(tick, plan?.id) { plan?.let { repo.days(it.id) } ?: emptyList() }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf(false) }
    var scheduleErr by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val minutes = repo.estimate(r, lines)
    val usedSlot = days.firstOrNull { it.gym == r.id || it.morning == r.id }?.slot
    val isCardio = r.kind == "run" || r.kind == "ruck"

    WkPage(
        title = null, onBack = back, trailing = {
            WkButton("Edit Plan", icon = wkIco("edit"), height = 48.dp) {
                if (plan != null && usedSlot != null) { WorkoutNav.planId = plan.id; WorkoutNav.slot = usedSlot; go(Screen.WorkoutDay) } else message = "Add this routine to a day first (Schedule This Plan), then edit that day."
            }
            Spacer(Modifier.width(8.dp))
            Box {
                RoundBtn("More", com.pranav.focus.R.drawable.plan_ic_more) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = C.Sheet) {
                    DropdownMenuItem(text = { Text("Open weekly schedule", color = C.White) }, onClick = { menu = false; go(Screen.WorkoutPlans) })
                }
            }
        },
        bottomBar = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WkButton("Schedule This Plan", Modifier.weight(1f), icon = wkIco("calendar")) { schedule = true }
                WkButton(if (isCardio) "Log ${r.name}" else "Start ${r.code} · ${r.name}", Modifier.weight(1.2f), primary = true, icon = wkIco("play")) {
                    if (isCardio) { message = "Use Log Run / Log Ruck on the Workout hub to record distance and time." } else startPlanned(repo, plan, usedSlot ?: 1, r, go)
                }
            }
        },
    ) {
        CoverArt(r, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).border(1.dp, C.Hairline, RoundedCornerShape(24.dp))) {
            Text(if (WorkoutImages.routineCover(r.id) != null) "Generated illustration" else "No artwork for this routine yet", color = C.Text3, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Tag(r.code); Spacer(Modifier.width(8.dp)); Text(if (r.isGym) "GYM ROUTINE" else "MORNING ROUTINE", color = C.Text2, fontSize = 12.sp, letterSpacing = 1.sp) }
            Text(r.name, color = C.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text(r.focus, color = C.Text2, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact(wkIco("dumbbell"), "${lines.size}", "exercises", Modifier.weight(1f))
            Fact(wkIco("clock"), r.labelPlans.removePrefix("~ "), "", Modifier.weight(1f))
            Fact(wkIco("chart"), r.difficulty, "", Modifier.weight(1f))
            Fact(wkIco("target"), r.primaryFocus.ifBlank { r.focus }, "Primary focus", Modifier.weight(1f))
        }
        Text(r.description, color = C.Text2, fontSize = 15.sp, lineHeight = 21.sp)
        SegTabs(ROUTINE_TABS, tab) { tab = it }

        when (tab) {
            0 -> WkCard(padding = 12.dp) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Exercises", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text("  (${lines.size})", color = C.Text2, fontSize = 18.sp)
                    Spacer(Modifier.weight(1f))
                    if (usedSlot != null && plan != null) WkButton("Reorder", icon = com.pranav.focus.R.drawable.plan_ic_reorder, height = 44.dp) { WorkoutNav.planId = plan.id; WorkoutNav.slot = usedSlot; go(Screen.WorkoutDay) }
                }
                if (lines.isEmpty()) Text("No exercises in this routine.", color = C.Text2, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lines.forEachIndexed { i, l ->
                        val sel = i == selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, if (sel) C.White.copy(alpha = 0.5f) else C.Hairline, RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) { selected = i }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${i + 1}", color = C.White, fontSize = 15.sp, modifier = Modifier.width(24.dp))
                            ExerciseThumb(l.exerciseId, 64.dp, 10.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(l.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(l.equipment + (l.superset?.let { "  •  Superset $it" } ?: ""), color = C.Text2, fontSize = 12.sp, maxLines = 1)
                                Text("${l.prescription}   ·   ${l.rest}   ·   ${l.muscleTag}", color = C.Text2, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                }
                val alts = lines.getOrNull(selected)?.let { alternativesOf(it.name) } ?: emptyList()
                if (alts.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("swap"), 18.dp, C.White); Spacer(Modifier.width(8.dp)); Text("Exercise alternatives", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Text("Tap an exercise above to see its alternatives. Swap them from the Day Builder.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(alts) { a -> Tag(a, Modifier.heightIn(min = 32.dp).padding(vertical = 2.dp)) } }
                }
            }
            1 -> WkCard {
                Text("Overview", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(r.description, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                if (r.isGym) { Spacer(Modifier.height(10.dp)); WorkoutCatalog.PROGRAM_RULES.forEach { (t, b, _) -> Text("$t: $b", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp)) } }
                Text("Estimated time: ~ $minutes min (from sets, rest and a 5 min warm-up)", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            2 -> WkCard {
                Text("Progression", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (r.isGym) "Double progression: start at the bottom of the rep range with 1–3 reps in reserve (3–4 in weeks 1–2). When every set reaches the top of the range, add the smallest sensible load (about 2.5 kg) and drop back to the bottom. Carries: add load before distance. Holds: add time. Optional deload: about half the sets for one week every six weeks, or after two stalled weeks."
                    else r.notes.ifBlank { "Increase time or reps gradually." }, color = C.Text2, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
            else -> WkCard {
                Text("Notes", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(r.notes.ifBlank { "No notes for this routine." }, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                if (r.isGym) Text("Superset rule: A/B pairs have no rest between the two exercises, then 60–90 sec after the pair.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (schedule && plan != null) FocusSheet({ schedule = false; scheduleErr = null }) {
        Text("Schedule ${r.code} in ${plan.name}", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
        scheduleErr?.let { Text(it, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        days.forEach { d ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) {
                runCatching {
                    if (r.isGym) repo.assignDay(plan.id, d.slot, d.morning, d.altMorning, r.id) else repo.assignDay(plan.id, d.slot, r.id, null, d.gym)
                }.onSuccess { WorkoutNav.bump(); schedule = false; scheduleErr = null }.onFailure { scheduleErr = it.message ?: "Could not schedule." }
            }, verticalAlignment = Alignment.CenterVertically) {
                Text("${weekdayName(d.slot, plan)} · Day ${d.slot}", color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(listOfNotNull(d.morning?.uppercase(), d.gym?.uppercase()).joinToString(" + ").ifBlank { "Rest" }, color = C.Text2, fontSize = 13.sp)
            }
        }
    }
    message?.let { InfoDialog(r.name, it) { message = null } }
}

@Composable
private fun Fact(icon: Int, value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Ico(icon, 20.dp, C.White); Text(value, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp); if (label.isNotEmpty()) Text(label, color = C.Text2, fontSize = 11.sp)
    }
}

/** "Hack Squat (or Smith Machine Squat)" -> [Hack Squat, Smith Machine Squat]; "Lying / Seated Leg Curl" -> [Lying Leg Curl, Seated Leg Curl] is not attempted: we split on " / " and " or " only. */
fun alternativesOf(name: String): List<String> {
    val cleaned = name.replace("(or ", " / ").replace(")", "")
    return cleaned.split(" / ", " or ").map { it.trim() }.filter { it.isNotEmpty() }
}

// ====================================================================================================================
// C3 — Create plan
// ====================================================================================================================
@Composable
fun WorkoutCreateScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val plans = remember(tick) { repo.plans() }
    val routines = remember { repo.routines() }
    val byId = remember(routines) { routines.associateBy { it.id } }
    var mode by remember { mutableIntStateOf(if (WorkoutNav.createTemplate != null) 0 else 0) }
    var tplId by remember { mutableStateOf(WorkoutNav.createTemplate ?: "strength_conditioning_4day") }
    val tpl = WorkoutCatalog.TEMPLATES.first { it.id == tplId }
    var name by remember { mutableStateOf(if (WorkoutNav.createTemplate != null) "" else "") }
    var desc by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(setOf<String>()) }
    var type by remember { mutableStateOf("hybrid") }
    var sourceId by remember { mutableStateOf(plans.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var dirtyName by remember { mutableStateOf(false) }

    // pre-fill from the template until the user types their own name
    androidx.compose.runtime.LaunchedEffect(mode, tplId) {
        if (mode == 0 && !dirtyName && tpl.available) { name = suggestName(tpl.name, plans.map { it.name }); desc = tpl.blurb; type = tpl.planType }
    }

    fun create(openBuilder: Boolean) {
        runCatching {
            when (mode) {
                2 -> repo.duplicatePlan(sourceId ?: throw IllegalArgumentException("Pick a plan to duplicate."), name)
                1 -> repo.createPlan(name, desc, type, tags.toList(), null)
                else -> repo.createPlan(name, desc, type, tags.toList(), tpl.id.takeIf { tpl.available } ?: throw IllegalArgumentException("Pick a template with routines, or use Blank Plan."))
            }
        }.onSuccess { id ->
            WorkoutNav.bump(); WorkoutNav.createTemplate = null
            if (openBuilder) { WorkoutNav.planId = id; WorkoutNav.slot = 1; back(); go(Screen.WorkoutDay) } else back()
        }.onFailure { error = it.message }
    }

    WkPage(
        title = "Create Plan", subtitle = "Build a plan that fits your goals.", onBack = back,
        trailing = { WkButton("Preview", icon = wkIco("eye"), height = 48.dp) { preview = true } },
        bottomBar = { WkButton("Create Plan", Modifier.fillMaxWidth(), primary = true, icon = wkIco("check"), height = 56.dp) { create(false) } },
    ) {
        SegTabs(listOf("From Template", "Blank Plan", "Duplicate Plan"), mode) { mode = it; error = null }

        WkCard {
            Text("Plan details", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Text("Plan name", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
            WkField(name, { name = it; dirtyName = true; error = null }, "e.g. Strength & Conditioning", maxLen = WorkoutLogic.NAME_MAX, trailing = {
                if (name.isNotEmpty()) Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button) { name = ""; dirtyName = true }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 16.dp, C.Text2) }
            })
            error?.let { Text(it, color = Color(0xFFFF8A8A), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (mode != 2) {
                Spacer(Modifier.height(12.dp))
                Text("Description (optional)", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
                WkField(desc, { desc = it }, "What is this plan for?", singleLine = false, maxLen = WorkoutLogic.DESC_MAX, minHeight = 88.dp)
                Text("${desc.length}/${WorkoutLogic.DESC_MAX}", color = C.Text3, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Strength", "Hypertrophy", "General Fitness", "4-Day Split")) { t -> FilterPill(t, t in tags) { tags = if (t in tags) tags - t else tags + t } }
                }
            }
        }

        if (mode != 2) {
            WkCard {
                Text("Plan type", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("gym", "Gym Plan", "Weights & strength"), Triple("hybrid", "Hybrid Plan", "Gym + cardio"), Triple("conditioning", "Conditioning", "Runs, rucks, bodyweight")).forEach { (k, t, s) ->
                        val sel = type == k
                        Column(
                            Modifier.weight(1f).heightIn(min = 76.dp).clip(RoundedCornerShape(14.dp)).background(C.Raised).border(if (sel) 1.5.dp else 1.dp, if (sel) C.White else C.Hairline, RoundedCornerShape(14.dp))
                                .clickable(role = Role.RadioButton) { type = k }.padding(10.dp),
                        ) { Text(t, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); Text(s, color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp); if (sel) Text("Selected", color = C.White, fontSize = 10.sp) }
                    }
                }
            }
        }

        if (mode == 0) {
            WkCard {
                Text("Select from program template", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WorkoutCatalog.TEMPLATES) { t ->
                        val sel = t.id == tplId
                        Column(
                            Modifier.width(118.dp).heightIn(min = 92.dp).clip(RoundedCornerShape(14.dp)).background(C.Raised).border(if (sel) 1.5.dp else 1.dp, if (sel) C.White else C.Hairline, RoundedCornerShape(14.dp))
                                .clickable(enabled = t.available, role = Role.RadioButton) { tplId = t.id; name = ""; dirtyName = false }.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(t.name.substringBefore(" —"), color = if (t.available) C.White else C.Text3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, lineHeight = 15.sp)
                            Text(if (t.available) t.subtitle else "No routines supplied yet", color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp)
                        }
                    }
                }
            }
            if (tpl.available) {
                WkCard {
                    Text("Included routines", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Gym routines (${tpl.routineIds.count { byId[it]?.isGym == true }})", color = C.Text2, fontSize = 13.sp)
                    LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tpl.routineIds.mapNotNull { byId[it] }.filter { it.isGym }) { r -> RoutineChip(r) } }
                    Text("Morning routines (${tpl.routineIds.count { byId[it]?.isGym == false }})", color = C.Text2, fontSize = 13.sp)
                    LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tpl.routineIds.mapNotNull { byId[it] }.filter { !it.isGym }) { r -> RoutineChip(r) } }
                }
                Text("Weekly schedule (7 days)", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tpl.days.forEachIndexed { i, d ->
                        val m = byId[d.morning]; val g = byId[d.gym]; val alt = byId[d.altMorning]
                        DayCell(WEEKDAY_SHORT[i], listOfNotNull(if (m != null && alt != null) "${m.code}/${alt.code}" else m?.code, g?.code).joinToString(" + ").ifBlank { "Rest" }, null, (g ?: m)?.accent ?: "grey", false, Modifier.weight(1f))
                    }
                }
                Text("You can edit every day after creating the plan.", color = C.Text3, fontSize = 12.sp)
                Text("Program rules (included)", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                WorkoutCatalog.PROGRAM_RULES.forEach { (t, b, _) -> Text("$t: $b", color = C.Text2, fontSize = 12.sp) }
                WkButton("Create & customize", Modifier.fillMaxWidth(), icon = wkIco("edit")) { create(true) }
            } else Text("This template has no routines yet. Use Strength & Conditioning or a Blank Plan.", color = C.Text2, fontSize = 13.sp)
        }
        if (mode == 1) Text("A blank plan starts with 7 empty days. Assign morning and gym routines and exercises in the Day Builder.", color = C.Text2, fontSize = 13.sp)
        if (mode == 2) WkCard {
            Text("Duplicate which plan?", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            plans.forEach { p -> Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) { sourceId = p.id }, verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = C.White, fontSize = 15.sp, modifier = Modifier.weight(1f)); if (sourceId == p.id) Ico(wkIco("check"), 18.dp, C.White)
            } }
        }
    }

    if (preview) InfoDialog(name.ifBlank { "Untitled plan" }, when (mode) {
        0 -> if (tpl.available) "From template: ${tpl.name}\n${tpl.routineIds.size} routines • 7-day schedule • type ${type}" else "Template has no routines yet."
        1 -> "Blank plan • type $type • 7 empty days"
        else -> "Copy of: ${plans.firstOrNull { it.id == sourceId }?.name ?: "—"}"
    }) { preview = false }
}

@Composable
private fun RoutineChip(r: Routine) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentDot(r.accent); Spacer(Modifier.width(8.dp))
        Column { Text(r.code, color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold); Text(r.name, color = C.Text2, fontSize = 11.sp, maxLines = 1) }
    }
}

private fun suggestName(base: String, existing: List<String>): String {
    if (existing.none { it.equals(base, ignoreCase = true) }) return base
    var i = 2; while (existing.any { it.equals("$base ($i)", ignoreCase = true) }) i++
    return "$base ($i)"
}
