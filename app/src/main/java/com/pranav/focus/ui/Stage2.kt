package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.AgendaItem
import com.pranav.focus.data.Entry
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Ranks
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.Store
import com.pranav.focus.data.Weekly
import com.pranav.focus.lock.AppState
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun agendaIcon(k: String) = when (k) {
    "Physics" -> R.drawable.ic_atr
    "Chemistry" -> R.drawable.ic_science
    "Biology" -> R.drawable.ic_eco
    "Workout" -> R.drawable.ic_fitness_center
    "Run" -> R.drawable.p1_run_sprinter
    "Mirror" -> R.drawable.ic_visibility_off
    else -> R.drawable.ic_shield
}

// --------------------------------------------------------------------------------------
// A11 Accountability Mirror
// --------------------------------------------------------------------------------------

// --------------------------------------------------------------------------------------
// A12 Cookie Jar
// --------------------------------------------------------------------------------------

// --------------------------------------------------------------------------------------
// A13 Rank
// --------------------------------------------------------------------------------------

@Composable
private fun Bar(frac: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(C.Track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(C.White))
    }
}

@Composable
private fun Target(name: String, value: String, frac: Float) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row { Text(name, color = C.White, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text(value, color = C.Text2, fontSize = 13.sp) }
        Spacer(Modifier.height(6.dp))
        Bar(frac)
    }
}

// --------------------------------------------------------------------------------------
// A14 Stats
// --------------------------------------------------------------------------------------
fun buckets(range: Int, f: (LocalDate) -> Double): Pair<List<Float>, List<String>> {
    val today = AppClock.today()
    return when (range) {
        0 -> {
            val s = Weekly.weekStart()
            (0..6).map { f(s.plusDays(it.toLong())).toFloat() } to listOf("M", "T", "W", "T", "F", "S", "S")
        }
        1 -> {
            val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
            days.map { f(it).toFloat() } to days.map { if (it.dayOfMonth % 5 == 0) "${it.dayOfMonth}" else "" }
        }
        else -> {
            val months = (11 downTo 0).map { today.withDayOfMonth(1).minusMonths(it.toLong()) }
            months.map { m -> (0 until m.lengthOfMonth()).sumOf { f(m.plusDays(it.toLong())) }.toFloat() } to
                months.map { it.month.name.take(1) }
        }
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>, height: Int = 110) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val n = values.size
        val gap = 4.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            drawRoundRect(C.Track, Offset(x, 0f), Size(bw, size.height), CornerRadius(bw / 3))
            val h = (size.height * (v / max)).coerceAtLeast(if (v > 0) 3.dp.toPx() else 0f)
            drawRoundRect(Color.White, Offset(x, size.height - h), Size(bw, h), CornerRadius(bw / 3))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEach { Text(it, color = C.Text3, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1) }
    }
}

private val QUOTES = listOf(
    "Comfort is the thief. Show up anyway.",
    "Nobody is coming. Do the work.",
    "You don’t need motivation. You need a start.",
    "Hard today, free tomorrow.",
    "The standard is the standard.",
    "Be the person your excuses are afraid of.",
    "One more rep. One more page. One more minute.",
)

@Composable
fun StatsScreen(go: (com.pranav.focus.ui.Screen) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var range by remember { mutableIntStateOf(0) }
    val tick = AppState.dataTick
    val focus = remember(range, tick) { val m = store.focusDays(); buckets(range) { (m[it.toString()] ?: 0).toDouble() } }
    val acts = remember(tick) { store.activities }
    val dist = remember(range, tick) { buckets(range) { d -> acts.filter { it.startDate == d.toString() && (it.isRun || it.isRide || it.isWalk) }.sumOf { it.distM } / 1000.0 } }
    val wk = remember(range, tick) { buckets(range) { d -> acts.count { it.startDate == d.toString() && it.isWorkout } + store.inAppWorkoutsOn(d.toString()).toDouble() } }
    val apps = remember(tick) { if (UsageHelper.hasAccess(ctx)) UsageHelper.today(ctx).take(5) else emptyList() }
    val focusDays = remember(tick) { store.focusDays() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 100.dp)) {
        ScreenHeader("Stats", null)
        WeeklyTargetCard(store)
        Spacer(Modifier.height(12.dp))
        SegFull(listOf("Week", "Month", "Year"), range) { range = it }
        Spacer(Modifier.height(16.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row { Text("Focus", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Fmt.hm(focus.first.sum().toInt()), color = C.Text2, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            BarChart(focus.first, focus.second)
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row { Text("Distance", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Fmt.km(dist.first.sum() * 1000.0, store.imperial) + if (store.imperial) " mi" else " km", color = C.Text2, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            BarChart(dist.first, dist.second, 90)
            if (acts.isEmpty()) Text("Connect Health Connect to fill this in.", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth(), onClick = { go(Screen.WorkoutHistory) }) {   // diagram: OVERALL --> HISTORY (C12); back returns to the Stats tab
            Row { Text("Workouts", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${wk.first.sum().toInt()}", color = C.Text2, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            BarChart(wk.first, wk.second, 80)
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Text("Screen time today", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (apps.isEmpty()) Text("Allow usage access in Permissions to see this.", color = C.Text3, fontSize = 13.sp)
            else {
                val total = apps.sumOf { it.ms }.toFloat()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(110.dp)) {
                        var start = -90f
                        val w = 16.dp.toPx()
                        apps.forEachIndexed { i, a ->
                            val sweep = 360f * a.ms / total
                            drawArc(Color.White.copy(alpha = 1f - i * 0.17f), start, (sweep - 2f).coerceAtLeast(1f), false,
                                Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Butt))
                            start += sweep
                        }
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        apps.forEachIndexed { i, a ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 1f - i * 0.17f)))
                                Text("${a.label} ${UsageHelper.format(a.ms)}", color = C.Text2, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        UsageTotalsCard()
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row { Text("Focus streak", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${store.streak} days", color = C.Text2, fontSize = 14.sp) }
            Spacer(Modifier.height(10.dp))
            val start = Weekly.weekStart().minusWeeks(11)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val cell = (size.width - 11 * 4.dp.toPx()) / 12
                for (c in 0 until 12) for (r in 0 until 7) {
                    val d = start.plusDays((c * 7 + r).toLong())
                    if (d.isAfter(AppClock.today())) continue
                    val m = focusDays[d.toString()] ?: 0
                    val a = when { m <= 0 -> 0.08f; m < 30 -> 0.35f; m < 90 -> 0.65f; else -> 1f }
                    drawRoundRect(Color.White.copy(alpha = a), Offset(c * (cell + 4.dp.toPx()), r * (cell.coerceAtMost(14.dp.toPx()) + 2.dp.toPx())),
                        Size(cell, cell.coerceAtMost(14.dp.toPx())), CornerRadius(3.dp.toPx()))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Text("“${QUOTES[AppClock.today().dayOfYear % QUOTES.size]}”", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.Light, lineHeight = 26.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun NavRow(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = C.Text2, fontSize = 14.sp)
            }
            Ico(R.drawable.ic_chevron_right, 22.dp, C.Text3)
        }
    }
}

// --------------------------------------------------------------------------------------
// A15 Profile
// --------------------------------------------------------------------------------------
