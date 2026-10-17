package com.pranav.focus.workout

import android.os.SystemClock
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import com.pranav.focus.ui.fieldColors

private fun fmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) r.toInt().toString() else r.toString()
}

/**
 * C9 — Set Entry, wired to the active session. Every logged set is written immediately (survives process death); reopening
 * the screen resumes at the first unlogged set. Muscle highlighting from the mock-up is NOT implemented (no exercise-specific anatomy assets).
 */
@Composable
fun WorkoutSetEntryScreen(back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val sid = WorkoutNav.sessionId
    val session = remember(sid, tick) { sid?.let { repo.session(it) } }
    if (WorkoutNav.summary != null) LaunchedEffect(Unit) { WorkoutNav.summary = null; WorkoutNav.pendingComplete = true; back() }
    if (session == null || session.status != "active") { WkPage("Set Entry", onBack = back) { if (WorkoutNav.summary == null) Text("No active workout.", color = C.Text2) }; return }
    val lines = remember(sid) {
        if (session.planId != null && session.slot != null) repo.dayLines(session.planId, session.slot) else session.routineId?.let { repo.routineLines(it) } ?: emptyList()
    }.let { (it + repo.sessionExtraLines(session.id)).filterNot { l -> l.exerciseId in repo.hiddenExercises(session.id) } }
    if (lines.isEmpty()) { WkPage("Set Entry", onBack = back) { Text("This routine has no exercises.", color = C.Text2) }; return }
    val logged = remember(sid, tick) { repo.sessionSets(session.id).filter { !it.warmup } }

    // first unlogged (line, set): consume logged counts per exercise in line order
    val counts = logged.groupingBy { it.exerciseId }.eachCount().toMutableMap()
    var lineIdx = -1; var setNo = 1
    // opened from Active Workout for one exercise: start on its next unlogged set
    val prefIdx = WorkoutNav.entryExerciseId?.let { eid -> lines.indexOfFirst { it.exerciseId == eid }.takeIf { i -> i >= 0 && logged.count { s -> s.exerciseId == eid } < lines[i].sets } }
    if (prefIdx != null) { lineIdx = prefIdx; setNo = logged.count { it.exerciseId == lines[prefIdx].exerciseId } + 1 } else
    for ((i, l) in lines.withIndex()) {
        val done = minOf(counts[l.exerciseId] ?: 0, l.sets); counts[l.exerciseId] = (counts[l.exerciseId] ?: 0) - done
        if (done < l.sets) { lineIdx = i; setNo = done + 1; break }
    }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }   // "finish" | "discard"
    var infoOpen by remember { mutableStateOf(false) }

    fun closeSession(done: Boolean) {
        val sets = repo.sessionSets(session.id).filter { !it.warmup }
        val vol = sets.sumOf { (it.loadKg ?: 0.0) * (it.reps ?: 0) }
        RestTimer.reset("cancelled")                       // a rest still running is closed (and recorded once) before the workout ends
        val saved = repo.finishSession(session.id, done)
        // elapsed minutes from the SAVED record (same canonical definition as C10/C11/C12), never from the screen clock
        val mins = (repo.session(session.id)?.let { WorkoutTiming.of(it).elapsedMin } ?: 0L)
        if (done && (saved || repo.session(session.id)?.status == "completed")) WorkoutNav.completedSessionId = session.id
        WorkoutNav.sessionId = null; WorkoutNav.bump()
        if (done) WorkoutNav.summary = "${sets.size} sets • ${Math.round(vol)} kg volume • $mins min" else back()
    }

    if (lineIdx < 0) {
        // everything logged
        LaunchedEffect(session.id) { closeSession(true) }
        return
    }
    val line = lines[lineIdx]
    val ex = remember(line.exerciseId) { repo.exercise(line.exerciseId) }
    val prev = remember(line.exerciseId, sid) { repo.previousSets(line.exerciseId, session.id) }
    val rng = WorkoutLogic.parseReps(line.repText)
    val unit = when (line.type) { "carry" -> "m"; "hold" -> "sec"; else -> "reps" }
    val startVal = prev.getOrNull(setNo - 1)?.let { p -> if (unit == "m") p.distanceM?.toInt() else if (unit == "sec") p.durationS else p.reps } ?: rng.min ?: if (unit == "sec") 30 else 8
    val startKg = prev.getOrNull(setNo - 1)?.loadKg ?: logged.lastOrNull { it.exerciseId == line.exerciseId }?.loadKg ?: 0.0
    var kg by remember(lineIdx, setNo) { mutableDoubleStateOf(startKg) }
    var reps by remember(lineIdx, setNo) { mutableIntStateOf(startVal) }
    var rpe by remember(lineIdx, setNo) { mutableStateOf<Int?>(null) }
    var note by remember(lineIdx, setNo) { mutableStateOf("") }
    // C9 rest card = the shared RestTimer (same tracker as Active Workout): visuals unchanged, but every rest run here is now measured and saved once.
    remember(session.id) { bindRestTimer(repo, session.id); 0 }
    var restTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(RestTimer.running) { while (RestTimer.running) { RestTimer.tick(); restTick = SystemClock.elapsedRealtime(); delay(250) } }
    val restActive = RestTimer.running || RestTimer.paused
    val running = RestTimer.running
    val restLeft = if (restActive) ((RestTimer.leftMs().also { restTick.hashCode() } + 999) / 1000).toInt() else line.restS

    val thisEx = logged.filter { it.exerciseId == line.exerciseId }
    val suggestion = if (unit == "reps" && rng.min != null && rng.max != null && prev.isNotEmpty() && prev.all { it.loadKg != null && it.reps != null })
        WorkoutLogic.nextSuggestion(prev.map { it.loadKg!! to it.reps!! }, rng.min, rng.max) else null

    WkPage(
        title = null, onBack = back,
        trailing = {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Set Entry", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${session.title} • ${lineIdx + 1}/${lines.size}", color = C.Text2, fontSize = 13.sp, maxLines = 1)
            }
            Box {
                RoundBtn("Workout menu", R.drawable.plan_ic_more) { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(C.Sheet)) {
                    DropdownMenuItem(text = { Text("Finish workout", color = C.White) }, onClick = { menu = false; confirm = "finish" })
                    DropdownMenuItem(text = { Text("Discard workout", color = androidx.compose.ui.graphics.Color(0xFFFF8A8A)) }, onClick = { menu = false; confirm = "discard" })
                }
            }
        },
        bottomBar = {
            WkButton("Log Set & Next", Modifier.fillMaxWidth(), primary = true, icon = wkIco("check"), height = 58.dp) {
                repo.logSet(
                    session.id, line.exerciseId, setNo, if (unit == "reps" || unit == "m") kg.takeIf { it > 0 } else kg.takeIf { it > 0 },
                    if (unit == "reps") reps else null, rpe, note, if (unit == "sec") reps else null, if (unit == "m") reps.toDouble() else null,
                )
                WorkoutNav.bump()
            }
        },
    ) {
        // ---- exercise ----
        WkCard {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(line.name, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${line.equipment} • ${line.muscleTag}", color = C.Text2, fontSize = 14.sp)
                }
                WkButton("Exercise Info", icon = wkIco("info"), height = 44.dp) { infoOpen = true }
            }
            Spacer(Modifier.height(12.dp))
            val img = WorkoutImages.exerciseIllustration(line.exerciseId, thumb = false)
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(14.dp)).background(C.Raised), contentAlignment = Alignment.Center) {
                if (img != null) Image(painterResource(img), "Exercise illustration", Modifier.fillMaxWidth().height(170.dp), contentScale = ContentScale.Crop)
                else Ico(WorkoutImages.pictogram(line.exerciseId), 72.dp, C.White.copy(alpha = 0.8f))
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("Primary: " + (ex?.primary ?: line.primary))
                line.superset?.let { Tag("Superset $it") }
            }
        }

        // ---- set entry ----
        WkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set $setNo of ${line.sets}", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ValueColumn("kg", kg, 2.5, decimal = true, onSet = { kg = it.coerceAtLeast(0.0) }, onMinus = { kg = (kg - 2.5).coerceAtLeast(0.0) }, onPlus = { kg += 2.5 }, modifier = Modifier.weight(1f))
                ValueColumn(unit, reps.toDouble(), 1.0, decimal = false, onSet = { reps = it.toInt().coerceAtLeast(0) }, onMinus = { reps = (reps - 1).coerceAtLeast(0) }, onPlus = { reps += 1 }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            val quick = listOf("+2.5 kg" to { kg += 2.5 }, "+5 kg" to { kg += 5.0 }, "−2.5 kg" to { kg = (kg - 2.5).coerceAtLeast(0.0) }, "−5 kg" to { kg = (kg - 5).coerceAtLeast(0.0) },
                "−1 $unit" to { reps = (reps - 1).coerceAtLeast(0) }, "+1 $unit" to { reps += 1 }, "−5 $unit" to { reps = (reps - 5).coerceAtLeast(0) }, "+5 $unit" to { reps += 5 })
            quick.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (t, f) -> Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)).clickable(role = Role.Button) { f() }, contentAlignment = Alignment.Center) { Text(t, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium) } }
                }
            }
        }

        // ---- previous sets ----
        WkCard(padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sets", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("Target: ${line.prescription}", color = C.Text2, fontSize = 12.sp)
            }
            suggestion?.let { Text("Next time (from last session): $it", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(8.dp))
            for (n in 1..line.sets) {
                val done = thisEx.getOrNull(n - 1)
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (n == setNo) C.Raised else C.Black.copy(alpha = 0f)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$n", color = C.White, fontSize = 14.sp, modifier = Modifier.width(28.dp))
                    val txt = when {
                        done != null -> "${done.loadKg?.let { fmt(it) + " kg × " } ?: ""}${done.reps ?: done.durationS ?: done.distanceM?.toInt() ?: 0} ${unit}"
                        n == setNo -> "${fmt(kg)} kg × $reps $unit"
                        else -> "–"
                    }
                    Text(txt, color = if (done != null || n == setNo) C.White else C.Text3, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(22.dp).clip(CircleShape).background(if (done != null) C.White else androidx.compose.ui.graphics.Color.Transparent).border(1.5.dp, if (done != null || n == setNo) C.White else C.Text3, CircleShape), contentAlignment = Alignment.Center) { if (done != null) Ico(wkIco("check"), 13.dp, C.Black) }
                }
            }
        }

        // ---- RPE + notes ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WkCard(Modifier.weight(1.2f), padding = 12.dp) {
                Row { Text("RPE", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(" (optional)", color = C.Text2, fontSize = 13.sp) }
                Text("How hard was this set?", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (6..10).forEach { n -> Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(if (rpe == n) C.White else C.Raised).clickable(role = Role.RadioButton) { rpe = if (rpe == n) null else n }, contentAlignment = Alignment.Center) { Text("$n", color = if (rpe == n) C.Black else C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Easy", color = C.Text3, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text("Max effort", color = C.Text3, fontSize = 10.sp) }
            }
            WkCard(Modifier.weight(1f), padding = 12.dp) {
                Row { Text("Notes", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(" (optional)", color = C.Text2, fontSize = 13.sp) }
                Spacer(Modifier.height(6.dp))
                WkField(note, { note = it }, "E.g. felt strong", singleLine = false, maxLen = 200, minHeight = 64.dp)
            }
        }

        // ---- rest timer ----
        WkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("timer"), 26.dp, C.White); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text("Rest Timer", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(if (line.restS > 0) line.rest else "No rest", color = C.Text2, fontSize = 13.sp) }
                Text(String.format(java.util.Locale.US, "%02d:%02d", restLeft / 60, restLeft % 60), color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(52.dp).clip(CircleShape).background(C.White).clickable(role = Role.Button) {
                    when {
                        RestTimer.running -> RestTimer.pause()
                        RestTimer.paused -> if (repo.pausedAt(session.id) == null) RestTimer.resume()
                        line.restS > 0 && repo.pausedAt(session.id) == null -> RestTimer.start(line.restS, line.name)   // not while the workout itself is paused
                    }
                }, contentAlignment = Alignment.Center) { Ico(wkIco(if (running) "pause" else "play"), 22.dp, C.Black) }
                Spacer(Modifier.width(8.dp))
                RoundBtn("Reset rest timer", wkIco("repeat")) { RestTimer.reset("skipped") }
            }
        }
    }

    if (infoOpen) InfoDialog(line.name, "Equipment: ${line.equipment}\nPrimary muscles: ${ex?.primary ?: line.primary}\nPrescription: ${line.prescription}, rest ${line.rest}" + (if (line.note.isNotBlank()) "\n${line.note}" else "") + "\n\nNo coaching text is stored for this exercise yet.") { infoOpen = false }
    when (confirm) {
        "finish" -> ConfirmDialog("Finish workout?", "Logged sets are kept. Remaining sets are skipped.", "Finish", { confirm = null; closeSession(true) }, { confirm = null })
        "discard" -> ConfirmDialog("Discard workout?", "The session is marked abandoned. Its logged sets stay in your history.", "Discard", { confirm = null; closeSession(false) }, { confirm = null })
    }
}

/**
 * One set value (kg or reps). Drag up or down to rotate it like the timer dial, one [step] per 26 dp.
 * Double-tap the number to type an exact value. The +/- buttons stay as they were.
 */
@Composable
private fun ValueColumn(
    label: String, value: Double, step: Double, decimal: Boolean,
    onSet: (Double) -> Unit, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier,
) {
    val setCb = rememberUpdatedState(onSet)
    val latest = rememberUpdatedState(value)
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 26.dp.toPx() }
    val drag = remember { floatArrayOf(0f) }        // pixels dragged since the last step
    val base = remember { doubleArrayOf(0.0) }      // value while a drag is running
    var typing by remember { mutableStateOf(false) }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("Decrease $label", R.drawable.ic_remove, onMinus)
            Column(
                Modifier.width(76.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { typing = true }) }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { base[0] = latest.value; drag[0] = 0f },
                            onDragEnd = { drag[0] = 0f },
                            onDragCancel = { drag[0] = 0f },
                        ) { _, dy ->
                            drag[0] += dy
                            while (drag[0] <= -stepPx) { base[0] = (base[0] + step).coerceAtLeast(0.0); setCb.value(base[0]); drag[0] += stepPx }
                            while (drag[0] >= stepPx) { base[0] = (base[0] - step).coerceAtLeast(0.0); setCb.value(base[0]); drag[0] -= stepPx }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(-2.0, -1.0).forEach { k ->
                    val x = value + k * step
                    Text(if (x >= 0) fmt(x) else " ", color = C.Text3, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
                Text(
                    fmt(value), color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(C.Raised).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                listOf(1.0, 2.0).forEach { k -> Text(fmt(value + k * step), color = C.Text3, fontSize = 15.sp, textAlign = TextAlign.Center) }
            }
            RoundBtn("Increase $label", R.drawable.ic_add, onPlus)
        }
        Text(label, color = C.White, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    }

    if (typing) {
        var txt by remember { mutableStateOf(fmt(value)) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { typing = false },
            containerColor = C.Sheet,
            title = { Text("Enter $label", color = C.White) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = txt,
                    onValueChange = { s -> txt = s.filter { it.isDigit() || (decimal && it == '.') } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                    colors = fieldColors(),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    txt.toDoubleOrNull()?.let { setCb.value(it.coerceAtLeast(0.0)) }
                    typing = false
                }) { Text("Set", color = C.White) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { typing = false }) { Text("Cancel", color = C.Text2) }
            },
        )
    }
}
