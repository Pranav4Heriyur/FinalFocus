package com.pranav.focus.workout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private class RawStats(val sessions: List<StatSession>, val sets: List<StatSet>, val muscles: Map<String, Pair<String, String>>)

private val Green = Color(0xFF34C759)
private val Heat = Color(0xFFE8663C)
private fun n0(v: Double) = NumberFormat.getIntegerInstance().format(Math.round(v))
private fun kg(v: Double) = if (v == Math.floor(v)) NumberFormat.getIntegerInstance().format(v.toLong()) else String.format(Locale.getDefault(), "%.1f", v)
private fun dur(min: Long) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
private fun fmtDay(ms: Long, pattern: String = "d MMM") = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
private fun axisK(v: Double) = if (v >= 1000) String.format(Locale.getDefault(), "%.1fK", v / 1000).replace(".0K", "K") else n0(v)

@Composable
fun WorkoutStatsScreen(back: () -> Unit) {
    val repo = rememberRepo()
    var retry by remember { mutableIntStateOf(0) }
    var rangeName by rememberSaveable { mutableStateOf(StatsRange.WEEK.name) }
    val range = StatsRange.valueOf(rangeName)
    val raw by produceState<Result<RawStats>?>(null, retry) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching { RawStats(repo.statsSessions().map { StatSession(it.id, it.title, it.activity, it.startedAt, it.endedAt ?: it.startedAt, it.pausedMs, it.restMs, it.timingV) }, repo.snapshotPrimaries().let { snap -> repo.statsSets().map { StatSet(it.id, it.sessionId, it.exerciseId, it.exerciseName, it.loadKg, it.reps, it.completedAt, snap[it.sessionId to it.exerciseId]) } }, repo.exerciseMuscles()) }
        }
    }
    Column(Modifier.fillMaxSize().background(C.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("Back", R.drawable.ic_arrow_back, back)
            Text("Workout Analytics", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        val r = raw
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading statistics…", color = C.Text2, fontSize = 15.sp) }
            r.isFailure -> Column(Modifier.padding(20.dp)) {
                WkCard {
                    Text("Couldn't load statistics", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your workouts are safe. ${r.exceptionOrNull()?.message.orEmpty()}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    WkButton("Retry", Modifier.fillMaxWidth(), primary = true) { retry++ }
                }
            }
            else -> {
                val data = r.getOrThrow()
                val res = remember(range, data) { runCatching { WorkoutStats.compute(range, LocalDate.now(ZoneId.systemDefault()), ZoneId.systemDefault(), data.sessions, data.sets, data.muscles) } }
                res.exceptionOrNull()?.let { Text("Couldn't calculate statistics: ${it.message}", color = C.Text2, modifier = Modifier.padding(20.dp)) }
                res.getOrNull()?.let { StatsBody(it, range) { rangeName = it.name } }
            }
        }
    }
}

@Composable
private fun StatsBody(s: StatsResult, range: StatsRange, onRange: (StatsRange) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScrollCompat().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // filters
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatsRange.values().forEach { r ->
                val sel = r == range
                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(if (sel) C.White else Color.Transparent)
                    .clickable(role = Role.Tab) { onRange(r) }.semantics { contentDescription = "${r.label} filter" + if (sel) ", selected" else "" }, contentAlignment = Alignment.Center) {
                    Text(r.label, color = if (sel) C.Black else C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        val period = when (range) { StatsRange.WEEK -> "${fmtDay(s.fromDay!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())} – ${fmtDay(s.toDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "d MMM")}"
            StatsRange.MONTH -> fmtDay(s.fromDay!!.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), "MMMM yyyy"); StatsRange.ALL -> "All saved workouts" }
        Text(period, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))

        // overview
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(wkIco("flame"), s.workouts.toString(), "Workouts", s.workoutsDelta, Modifier.weight(1f))
            Kpi(wkIco("clock"), if (s.workouts == 0) "—" else dur(s.minutes), "Elapsed time", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(wkIco("check"), s.sets.toString(), "Completed sets", null, Modifier.weight(1f))
            Kpi(wkIco("barbell"), if (s.volumeKg > 0) "${n0(s.volumeKg)} kg" else "—", "Volume", s.volumeDelta, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(wkIco("barbell"), s.avgVolumeKg?.let { "${n0(it)} kg" } ?: "—", "Avg volume / workout", null, Modifier.weight(1f))
            Kpi(wkIco("clock"), s.avgElapsedMin?.let { dur(it) } ?: "—", "Avg elapsed / workout", null, Modifier.weight(1f))
        }
        if (s.activeWorkouts > 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi(wkIco("timer"), s.activeMinutes?.let { dur(it) } ?: "—", "Active time", null, Modifier.weight(1f))
            Kpi(wkIco("timer"), s.avgActiveMin?.let { dur(it) } ?: "—", "Avg active / workout", null, Modifier.weight(1f))
        }
        if (s.workouts > 0) Text(
            "Elapsed = start to finish incl. pauses and rests. Active = elapsed minus pauses and timed rests" + (if (s.activeWorkouts < s.workouts) " (${s.activeWorkouts} of ${s.workouts} workouts; older ones didn't record it)" else "") +
                ". Avg volume uses the ${s.volumeWorkouts} ${if (s.volumeWorkouts == 1) "workout" else "workouts"} with weighted sets.",
            color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (s.workouts == 0) EmptyCard("No saved workouts in this period", "Finish a workout and it will show up here. Only saved workouts are counted.")

        // volume chart
        WkCard(radius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("chart"), 20.dp); Text("Training Volume", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (s.volumeKg > 0) "Total ${n0(s.volumeKg)} kg" else "No volume", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    s.volumeDelta?.let { DeltaText(it, if (range == StatsRange.WEEK) "vs previous week" else "vs previous month") }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (s.buckets.isEmpty() || s.buckets.all { it.volumeKg == 0.0 }) Text("No weighted sets logged in this period.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(vertical = 24.dp))
            else BarChart(s.buckets.map { it.label }, s.buckets.map { it.volumeKg }, ::axisK, "Training volume per ${if (range == StatsRange.ALL) "month" else if (range == StatsRange.MONTH) "week" else "day"}")
            Text("Volume = weight × reps of working sets. Bodyweight and timed sets add no volume.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        // frequency
        WkCard(radius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("calendar"), 20.dp); Text("Workout Frequency", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            val consistency = when {
                s.workouts == 0 -> "No workouts in this period."
                s.range == StatsRange.ALL -> "${String.format(Locale.getDefault(), "%.1f", s.avgWorkoutsPerWeek ?: 0.0)} workouts per week on average"
                else -> "Trained on ${s.activeDays} of ${s.daysInPeriod} days so far (${if (s.daysInPeriod > 0) Math.round(s.activeDays * 100.0 / s.daysInPeriod) else 0}%)"
            }
            Text(consistency, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (s.workouts > 0) { Spacer(Modifier.height(8.dp)); BarChart(s.buckets.map { it.label }, s.buckets.map { it.workouts.toDouble() }, { Math.round(it).toString() }, "Workouts per period") }
        }

        // muscles
        WkCard(radius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("target"), 20.dp); Text("Muscle Group Distribution", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp)) }
            Spacer(Modifier.height(10.dp))
            if (s.muscles.isEmpty()) Text("No sets with a mappable muscle group in this period.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
            else {
                val max = s.muscles.maxOf { it.share }.toFloat()
                val heat = s.muscles.associate { it.key to (it.share.toFloat() / max) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnatomyBody(true, heat, Modifier.weight(1f).aspectRatio(100f / 210f)); AnatomyBody(false, heat, Modifier.weight(1f).aspectRatio(100f / 210f))
                    }
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.muscles.take(8).forEach { m ->
                            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${m.label} ${Math.round(m.share * 100)} percent of sets" }) {
                                Row { Text(m.label, color = C.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)); Text("${Math.round(m.share * 100)}%", color = C.Text2, fontSize = 12.sp) }
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(m.share.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(C.White)) }
                            }
                        }
                    }
                }
            }
            Text("Metric: share of completed working sets. Each set's credit is split equally across the exercise's listed primary muscles; secondary muscles are not weighted. This is training distribution, not measured muscle activation.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
            if (s.unmappedSets > 0) Text("${s.unmappedSets} ${if (s.unmappedSets == 1) "set" else "sets"} not mapped to a muscle group (${s.unmappedLabels.joinToString()}); excluded from percentages.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // strength
        StrengthCard(s)

        // recent
        WkCard(radius = 22.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("clock"), 20.dp); Text("Recent Workouts", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button) { WorkoutNav.openHistory?.invoke() }.padding(horizontal = 12.dp).semantics { contentDescription = "See all workouts" }, contentAlignment = Alignment.Center) {
                    Text("See All", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (s.recent.isEmpty()) Text("No saved workouts in this period.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            s.recent.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp).clickable(role = Role.Button) { WorkoutNav.openWorkoutDetails?.invoke(w.id) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(w.title, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${fmtDay(w.startedAt, "d MMM yyyy")} • ${dur(w.minutes)}", color = C.Text2, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (w.volumeKg > 0) Text("${n0(w.volumeKg)} kg", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${w.exercises} ${if (w.exercises == 1) "exercise" else "exercises"}", color = C.Text2, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(20.dp))
    }
}

@Composable private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
private fun StrengthCard(s: StatsResult) {
    var sel by rememberSaveable(s.range) { mutableStateOf<String?>(null) }
    WkCard(radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) { Ico(wkIco("trophy"), 20.dp); Text("Strength Progress", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp)) }
        if (s.lifts.isEmpty()) { Text("No weighted sets in this period yet. Log loaded sets to track progression and personal records.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp)); return@WkCard }
        val lift = s.lifts.firstOrNull { it.exerciseId == sel } ?: s.lifts.first()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            s.lifts.take(8).forEach { l ->
                val on = l.exerciseId == lift.exerciseId
                Text(l.name, color = if (on) C.Black else C.White, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium,
                    modifier = Modifier.heightIn(min = 40.dp).clip(CircleShape).background(if (on) C.White else C.Raised).clickable(role = Role.Tab) { sel = l.exerciseId }.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (lift.points.size >= 2) LineChart(lift.points.map { it.dayMs }, lift.points.map { it.kg }, "Best set weight per workout for ${lift.name}")
        else Text("Only one workout with this lift in the period — a trend needs at least two.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(12.dp)) {
                Text("Current best set", color = C.Text2, fontSize = 11.sp)
                Text("${kg(lift.currentKg)} kg" + (lift.currentReps?.let { " × $it" } ?: ""), color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                lift.changePct?.let { if (it != 0) Text("${if (it > 0) "▲ +" else "▼ "}$it% vs previous best", color = if (it > 0) Green else C.Text2, fontSize = 11.sp) }
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(12.dp)) {
                Text("Previous best", color = C.Text2, fontSize = 11.sp)
                Text(lift.previousBestKg?.let { "${kg(it)} kg" } ?: "—", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(lift.est1rm?.let { "Est. 1RM ${n0(it)} kg (Epley)" } ?: "Est. 1RM n/a", color = C.Text2, fontSize = 11.sp)
            }
        }
        Text(if (lift.isVerifiedPr) "Verified PR: the latest session beat every earlier session's heaviest set." else if (lift.previousBestKg == null) "Not enough history to call a record — this is the first logged session." else "Not a new record.", color = if (lift.isVerifiedPr) Green else C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (s.prs.isNotEmpty()) {
            Text("PR HISTORY", color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            s.prs.take(5).forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(p.name, color = C.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(fmtDay(p.atMs, "d MMM yyyy"), color = C.Text2, fontSize = 11.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("${kg(p.kg)} kg" + (p.reps?.let { " × $it" } ?: ""), color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text("▲ +${kg(p.kg - p.prevKg)} kg", color = Green, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun Kpi(icon: Int, value: String, label: String, delta: Delta?, modifier: Modifier) {
    WkCard(modifier, radius = 20.dp, padding = 14.dp) {
        Ico(icon, 20.dp, C.Text2)
        Text(value, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(label, color = C.Text2, fontSize = 12.sp, maxLines = 1)
        delta?.let { DeltaText(it, null) }
    }
}

@Composable
private fun DeltaText(d: Delta, suffix: String?) {
    val up = d.pct > 0; val flat = d.pct == 0
    Text((if (up) "▲ +" else if (flat) "● " else "▼ ") + "${d.pct}%" + (suffix?.let { " $it" } ?: ""), color = if (up) Green else C.Text2, fontSize = 11.sp, maxLines = 1)
}

@Composable
private fun EmptyCard(title: String, body: String) {
    WkCard(radius = 22.dp) {
        Text(title, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Bars grow in on change. Y labels: max / half / 0. */
@Composable
private fun BarChart(labels: List<String>, values: List<Double>, fmt: (Double) -> String, desc: String) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val prog by animateFloatAsState(1f, tween(500, easing = FastOutSlowInEasing), label = "bars")
    Column(Modifier.semantics { contentDescription = desc + ": " + labels.zip(values).joinToString { "${it.first} ${fmt(it.second)}" } }) {
        Row(Modifier.fillMaxWidth().height(120.dp)) {
            Column(Modifier.width(40.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(max, max / 2, 0.0).forEach { Text(fmt(it), color = C.Text3, fontSize = 10.sp, maxLines = 1) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val n = values.size; val slot = size.width / n; val bw = (slot * 0.55f).coerceAtMost(28.dp.toPx())
                drawLine(C.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                values.forEachIndexed { i, v ->
                    val h = (v / max).toFloat() * size.height * prog
                    if (v > 0) drawRoundRect(Color(0xFFD8D8DC), Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(4.dp.toPx()))
                    else drawCircle(C.Track, 2.dp.toPx(), Offset(i * slot + slot / 2, size.height - 2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) { labels.forEach { Text(it, color = C.Text2, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
    }
}

@Composable
private fun LineChart(times: List<Long>, values: List<Double>, desc: String) {
    val lo = values.min(); val hi = values.max(); val pad = ((hi - lo) * 0.15).coerceAtLeast(1.0)
    val a = (lo - pad).coerceAtLeast(0.0); val b = hi + pad
    val t0 = times.first(); val t1 = times.last().coerceAtLeast(t0 + 1)
    Column(Modifier.semantics { contentDescription = desc + ": from ${kg(values.first())} to ${kg(values.last())} kg" }) {
        Row(Modifier.fillMaxWidth().height(130.dp)) {
            Column(Modifier.width(40.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) { Text("${n0(b)}", color = C.Text3, fontSize = 10.sp); Text("${n0(a)}", color = C.Text3, fontSize = 10.sp) }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                fun pt(i: Int) = Offset(((times[i] - t0).toFloat() / (t1 - t0)) * (size.width - 8.dp.toPx()) + 4.dp.toPx(), size.height - ((values[i] - a) / (b - a)).toFloat() * size.height)
                drawLine(C.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                val path = Path().apply { moveTo(pt(0).x, pt(0).y); for (i in 1 until values.size) lineTo(pt(i).x, pt(i).y) }
                drawPath(path, C.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                values.indices.forEach { drawCircle(C.White, 3.dp.toPx(), pt(it)); drawCircle(C.Black, 1.5.dp.toPx(), pt(it)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)) { Text(fmtDay(times.first()), color = C.Text2, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text(fmtDay(times.last()), color = C.Text2, fontSize = 10.sp) }
    }
}

/** Front or back vector body; muscles in [heat] (key -> 0..1 relative to the highest share) are tinted. Original artwork drawn in code. */
@Composable
fun AnatomyBody(front: Boolean, heat: Map<String, Float>, modifier: Modifier = Modifier) {
    Canvas(modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0E10)).semantics { contentDescription = if (front) "Front muscle diagram" else "Back muscle diagram" }) {
        val sc = size.height / 210f; val ox = (size.width - 100f * sc) / 2f
        fun o(x: Float, y: Float) = Offset(ox + x * sc, y * sc)
        val base = Color(0xFF3A3A3E)
        fun rr(x: Float, y: Float, w: Float, h: Float, r: Float = 4f) = drawRoundRect(base, o(x, y), Size(w * sc, h * sc), CornerRadius(r * sc))
        drawCircle(base, 8f * sc, o(50f, 14f)); rr(46f, 22f, 8f, 8f)
        rr(28f, 32f, 44f, 28f, 8f); rr(36f, 58f, 28f, 50f)
        rr(15f, 36f, 12f, 40f, 6f); rr(73f, 36f, 12f, 40f, 6f); rr(13f, 76f, 10f, 40f, 5f); rr(77f, 76f, 10f, 40f, 5f)
        rr(35f, 106f, 14f, 62f, 6f); rr(51f, 106f, 14f, 62f, 6f); rr(36f, 168f, 11f, 38f, 5f); rr(53f, 168f, 11f, 38f, 5f)
        (if (front) FRONT else BACK).forEach { (key, regions) ->
            val h = heat[key] ?: return@forEach
            regions.forEach { r -> drawOval(Heat.copy(alpha = 0.3f + 0.65f * h), o(r.cx - r.rx, r.cy - r.ry), Size(r.rx * 2 * sc, r.ry * 2 * sc)) }
        }
    }
}
