package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Polyline
import com.pranav.focus.data.Ranks
import com.pranav.focus.data.StudyDb
import com.pranav.focus.data.Store
import com.pranav.focus.data.Subject
import com.pranav.focus.data.tally
import com.pranav.focus.net.HealthSync
import com.pranav.focus.lock.AppState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick = AppState.dataTick + AppState.tick
    var watchMe by remember { mutableStateOf(store.watchMe) }
    var mode by remember { mutableIntStateOf(0) }
    var avatarVer by remember { mutableIntStateOf(0) }
    var doneIds by remember(tick) { mutableStateOf(store.reviewDone()) }

    val act = remember(mode, tick) { store.activities.firstOrNull { when (mode) { 0 -> it.isRun; 1 -> it.isWalk; else -> it.isRide } } }
    val connected = remember(tick) { HealthSync.connected(ctx) }
    val usage = remember(tick) { if (UsageHelper.hasAccess(ctx)) UsageHelper.today(ctx) else emptyList() }
    val usageOn = remember(tick) { UsageHelper.hasAccess(ctx) }
    val screenMs = usage.sumOf { it.ms }
    val budgetMin = store.int("screen_budget", 240)
    val day = remember(tick) { store.day() }
    val today = store.today()
    val studyLogs = remember(tick) { runCatching { StudyDb.get(ctx).logs(UsageHelper.midnight()) }.getOrDefault(emptyList()) }
    val focusMin = store.todayFocusMinutes
    val workoutMin = remember(tick) { store.activitiesOn(today).filter { it.isWorkout }.sumOf { it.movingS } / 60 }
    val runKm = remember(tick) { store.activitiesOn(today).filter { it.isRun }.sumOf { it.distM } / 1000.0 }
    val hour = LocalTime.now().hour
    val greeting = if (hour < 12) "Good morning," else if (hour < 17) "Good afternoon," else "Good evening,"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Reveal(0) { Header(greeting, watchMe, avatarVer, { avatarVer++ }) { watchMe = !watchMe; store.watchMe = watchMe } }
        Reveal(1) { HeroCard(store, act, mode, connected, store.imperial, { mode = it }) { go(Screen.Connections) } }
        Reveal(2) { TodayCard(go) }
        Reveal(2) {
            BalanceCard(
                listOf(
                    (focusMin / 120f), (day.actions.size + (store.inAppWorkoutsOn(today.toString()))).coerceAtMost(1).toFloat(),
                    (runKm / 5.0).toFloat(), (studyLogs.sumOf { it.attempted } / 60f), (store.int("sleep_min") / 480f), (store.streak / 30f),
                ).map { it.coerceIn(0f, 1f) }
            )
        }
        Reveal(3) { SleepCard(remember(tick) { HealthSync.nights(ctx) }, connected) { go(Screen.Connections) } }
        Reveal(3) { ScreenTimeCard(usage, screenMs, budgetMin, usageOn) { go(Screen.Setup) } }
        Reveal(4) {
            // The agenda IS today's Daily Planner (single source of truth).
            PlanState.load(ctx)
            val todays = PlanState.forDate(AppClock.today())
            val items = todays.map { com.pranav.focus.data.AgendaItem(it.id, it.title, it.subject, it.subject, fmtT(it.start).trimStart('0')) }
            val doneSet = todays.filter { it.done }.map { it.id }.toSet()
            AgendaCard(items, doneSet, onToggle = { id -> PlanState.get(id)?.let { PlanState.upsert(ctx, it.copy(done = !it.done)) } }, onAdd = { go(Screen.PlanAdd) })
        }
        Reveal(5) { TimeBankCard(store.bankMinutes, store.ledger.earnedOn(store.today()), store.ledger.spentOn(store.today())) { go(Screen.TimeBank) } }
        Reveal(6) { StudyCard(studyLogs) { go(Screen.Study) } }
        Reveal(7) { RingsCard(focusMin, workoutMin, (screenMs / 60_000).toInt(), budgetMin) { go(Screen.Stats) } }
        Reveal(8) { StreakCard(store.streak, store.xp) { go(Screen.Rank) } }
        Reveal(9) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StartFocusCard(store.lastMinutes, Modifier.weight(1f)) { go(Screen.Focus) }
                WorkoutCard(Modifier.weight(1f)) { go(Screen.Workout) }
            }
        }
        Reveal(10) { WeeklyCard(store.focusDays()) }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun Header(greeting: String, watchMe: Boolean, ver: Int, onPhoto: () -> Unit, onWatch: () -> Unit) {
    val ctx = LocalContext.current
    val file = java.io.File(ctx.filesDir, "avatar.jpg")
    val bmp = remember(ver) { if (file.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() else null }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            val src = android.graphics.BitmapFactory.decodeStream(ctx.contentResolver.openInputStream(uri)) ?: return@runCatching
            val side = minOf(src.width, src.height)
            val sq = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = android.graphics.Bitmap.createScaledBitmap(sq, 320, 320, true)
            file.outputStream().use { out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            onPhoto()
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Image(
            bmp ?: remember { android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.avatar_default).asImageBitmap() }, null,
            Modifier.size(50.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, C.Hairline, RoundedCornerShape(17.dp))
                .clickable { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(greeting, color = Color(0xFFDDDDDD), fontSize = 14.sp, lineHeight = 17.sp)
            Text("Pranav", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
            Text(AppClock.today().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")), color = C.Text2, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Row(
            Modifier.height(38.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).clickable { onWatch() }.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (watchMe) C.White else C.Text3))
            Spacer(Modifier.width(9.dp))
            Text(if (watchMe) "Watch: Me" else "Watch: Dad", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HeroCard(store: Store, act: com.pranav.focus.data.Activity?, mode: Int, connected: Boolean, imperial: Boolean, onMode: (Int) -> Unit, onConnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(28.dp)) {
        Box(Modifier.fillMaxWidth().height(183.dp).background(Color(0xFF070708))) {
            MapRoute(act, connected, Modifier.fillMaxSize().clickable(enabled = !connected) { onConnect() })
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(13.dp)).background(C.Sheet).border(1.dp, C.Hairline, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Ico(when (mode) { 0 -> R.drawable.p1_run_sprinter; 1 -> R.drawable.wk_ui_ruck; else -> R.drawable.p1_ride_bicycle }, 22.dp)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(act?.name?.ifBlank { null } ?: when (mode) { 0 -> "Latest run"; 1 -> "Latest walk"; else -> "Latest ride" }, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        when { act != null -> relDay(act.startDate); connected -> "No recent activity"; else -> "Health Connect not connected" },
                        color = C.Text2, fontSize = 12.sp, maxLines = 1,
                    )
                }
                RunRide(mode, onMode)
            }
            Text(act?.let { store.str("src_" + it.id) }?.takeIf { it.isNotBlank() }?.let { "via $it" } ?: "via Health Connect", color = C.Text3, fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 10.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(61.dp), verticalAlignment = Alignment.CenterVertically) {
                HeroMetric(if (act != null) Fmt.km(act.distM, imperial) else "—", if (imperial) "mi" else "km", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(44.dp).background(C.Hairline))
                HeroMetric(if (act != null) Fmt.pace(act.distM, act.movingS, imperial) else "—", if (imperial) "/mi" else "/km", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(44.dp).background(C.Hairline))
                HeroMetric(if (act != null) Fmt.clock(act.movingS) else "—", "time", Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(16.dp)).background(C.Raised).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubStat(R.drawable.p1_heart_outline, if (act != null && act.avgHr > 0) "${act.avgHr.toInt()} bpm" else "—", "avg heart rate", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(C.Hairline))
                SubStat(R.drawable.p1_elevation_mountain, if (act != null && act.elevM > 0) "${act.elevM.toInt()} m" else "—", "elevation gain", Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(C.Hairline))
                SubStat(R.drawable.p1_calories_flame, store.int("kcal_" + (act?.id ?: 0)).let { if (act != null && it > 0) "$it kcal" else "—" }, "calories", Modifier.weight(1f))
            }
        }
    }
}

private fun relDay(date: String): String {
    val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    return when (java.time.temporal.ChronoUnit.DAYS.between(d, AppClock.today())) {
        0L -> "Today"; 1L -> "Yesterday"; else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

@Composable
private fun RunRide(mode: Int, onMode: (Int) -> Unit) {
    Row(Modifier.width(168.dp).height(34.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape).padding(3.dp)) {
        listOf("Run", "Walk", "Ride").forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(if (i == mode) C.White else Color.Transparent).clickable { onMode(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == mode) C.Black else C.Text2, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun HeroMetric(v: String, unit: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = glow(34f, 300), maxLines = 1)
        Text(unit, color = C.Text2, fontSize = 13.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SubStat(icon: Int, v: String, l: String, modifier: Modifier) {
    Row(modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, 22.dp)
        Column(Modifier.padding(start = 8.dp)) {
            Text(v, color = C.White, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(l, color = C.Text2, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
        }
    }
}

/** Faint road grid, lake shapes and the glowing route with hollow start / solid finish markers. */
@Composable
private fun MapRoute(act: com.pranav.focus.data.Activity?, connected: Boolean, modifier: Modifier) {
    val pts = remember(act?.id) { act?.polyline?.takeIf { it.isNotBlank() }?.let { Polyline.decode(it) } ?: emptyList() }
    val frac = rememberSweep(1f, 700)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 34.dp.toPx()
            var x = -step
            while (x < size.width) { drawLine(Color.White.copy(alpha = 0.035f), Offset(x, 0f), Offset(x + 60f, size.height), 1f); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y + 22f), 1f); y += step * 0.8f }
            drawOval(Color.White.copy(alpha = 0.025f), Offset(size.width * 0.52f, size.height * 0.45f), Size(size.width * 0.3f, size.height * 0.55f))
            if (pts.size > 1) {
                val minLat = pts.minOf { it.first }; val maxLat = pts.maxOf { it.first }
                val minLng = pts.minOf { it.second }; val maxLng = pts.maxOf { it.second }
                val kx = cos(Math.toRadians((minLat + maxLat) / 2))
                val w = ((maxLng - minLng) * kx).coerceAtLeast(1e-6)
                val h = (maxLat - minLat).coerceAtLeast(1e-6)
                val padX = 36.dp.toPx(); val top = 70.dp.toPx(); val bottom = 22.dp.toPx()
                val aw = size.width - 2 * padX; val ah = size.height - top - bottom
                val sc = minOf(aw / w, ah / h).toFloat()
                val ox = padX + (aw - (w * sc).toFloat()) / 2
                val oy = top + (ah - (h * sc).toFloat()) / 2
                val path = Path()
                var first = Offset.Zero; var last = Offset.Zero
                pts.forEachIndexed { i, (la, ln) ->
                    val p = Offset(ox + (((ln - minLng) * kx) * sc).toFloat(), oy + ((maxLat - la) * sc).toFloat())
                    if (i == 0) { path.moveTo(p.x, p.y); first = p } else path.lineTo(p.x, p.y)
                    last = p
                }
                val m = PathMeasure(); m.setPath(path, false)
                val part = Path(); m.getSegment(0f, m.length * frac, part, true)
                drawPath(part, Color.White.copy(alpha = 0.12f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(part, Color.White.copy(alpha = 0.22f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(part, Color.White, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(Color.Black, 7.dp.toPx(), first); drawCircle(Color.White, 7.dp.toPx(), first, style = Stroke(2.5.dp.toPx()))
                if (frac > 0.98f) { drawCircle(Color.White.copy(alpha = 0.25f), 11.dp.toPx(), last); drawCircle(Color.White, 7.dp.toPx(), last) }
            }
        }
        if (pts.size <= 1) {
            Text(if (connected) "No route recorded" else "Connect Health Connect to see your route", color = C.Text3, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).padding(top = 30.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun BalanceCard(values: List<Float>) {
    val labels = listOf("Focus", "Workout", "Running", "Study", "Sleep", "Discipline")
    val weakest = if (values.all { it == 0f }) "—" else labels[values.indexOf(values.min())]
    val sweep = rememberSweep(1f, 600)
    Box(Modifier.fillMaxWidth().height(223.dp).glass(24.dp)) {
        Text("Today’s Balance", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(20.dp, 15.dp))
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, 120.dp.toPx())
            val r = 76.dp.toPx()
            fun pt(i: Int, f: Float): Offset {
                val a = Math.toRadians(-90.0 + i * 60.0)
                return Offset(c.x + (r * f * cos(a)).toFloat(), c.y + (r * f * sin(a)).toFloat())
            }
            for (lvl in 1..4) {
                val p = Path()
                for (i in 0 until 6) { val o = pt(i, lvl / 4f); if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                p.close(); drawPath(p, Color.White.copy(alpha = 0.19f), style = Stroke(1f))
            }
            for (i in 0 until 6) { drawLine(Color.White.copy(alpha = 0.19f), c, pt(i, 1f), 1f); drawCircle(Color.White.copy(alpha = 0.35f), 1.8.dp.toPx(), pt(i, 1f)) }
            val poly = Path()
            for (i in 0 until 6) { val o = pt(i, (values[i] * sweep).coerceAtLeast(0.05f)); if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y) }
            poly.close()
            drawPath(poly, Color.White.copy(alpha = 0.13f))
            drawPath(poly, Color.White.copy(alpha = 0.18f), style = Stroke(5.dp.toPx(), join = StrokeJoin.Round))
            drawPath(poly, Color.White, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            for (i in 0 until 6) drawCircle(Color.White, 3.5.dp.toPx(), pt(i, (values[i] * sweep).coerceAtLeast(0.05f)))
        }
        val lab = Modifier
        Text("Focus", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).offset(0.dp, 32.dp))
        Text("Workout", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).offset((-26).dp, 76.dp))
        Text("Running", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).offset((-26).dp, 144.dp))
        Text("Study", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).offset(0.dp, 194.dp))
        Text("Sleep", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).offset(23.dp, 144.dp))
        Text("Discipline", color = C.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).offset(24.dp, 76.dp))
        Text("Weakest: $weakest", color = C.Text2, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).offset(22.dp, 189.dp))
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun ScreenTimeCard(usage: List<AppUse>, totalMs: Long, budgetMin: Int, usageOn: Boolean, onSetup: () -> Unit) {
    val top = usage.take(3)
    val usedMin = (totalMs / 60_000).toInt()
    val left = (budgetMin - usedMin).coerceAtLeast(0)
    val frac = rememberSweep(if (budgetMin == 0) 0f else (usedMin / budgetMin.toFloat()).coerceIn(0f, 1f), 500)
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 14.dp)) {
        Text("Phone Screen Time", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (usageOn) UsageHelper.format(totalMs).ifEmpty { "0m" } else "—", style = glow(39f, 300))
                Text("today", color = C.Text2, fontSize = 11.sp, modifier = Modifier.offset(1.dp, (-4).dp))
            }
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f).height(9.dp).clip(CircleShape).background(C.Track.copy(alpha = 0.7f))) {
                val alphas = listOf(1f, 0.55f, 0.28f)
                top.forEachIndexed { i, a ->
                    Box(Modifier.weight((a.ms / totalMs.coerceAtLeast(1).toFloat()).coerceAtLeast(0.02f)).fillMaxHeight().background(Color.White.copy(alpha = alphas[i])))
                }
                val rest = 1f - top.sumOf { it.ms }.toFloat() / totalMs.coerceAtLeast(1)
                if (rest > 0.01f) Spacer(Modifier.weight(rest).fillMaxHeight())
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!usageOn) {
            Text("Allow usage access to see screen time", color = C.Text2, fontSize = 13.sp, modifier = Modifier.clickable { onSetup() }.padding(vertical = 8.dp))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                top.forEach { a ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF191A1C)).border(1.dp, C.Hairline, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                            AppIcon(a.pkg, 22.dp, mono = true)
                        }
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(UsageHelper.format(a.ms), color = C.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp)
                            Text(a.label, color = C.Text2, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.Track.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(C.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${Fmt.hm(usedMin)} used", color = C.Text2, fontSize = 11.sp)
            Text("${Fmt.hm(left)} left", color = C.Text2, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun AgendaCard(items: List<com.pranav.focus.data.AgendaItem>, done: Set<String>, onToggle: (String) -> Unit, onAdd: () -> Unit) {
    val current = items.firstOrNull { it.id !in done }?.id
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Today’s Agenda", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${items.count { it.id in done }} of ${items.size} done", color = C.Text2, fontSize = 11.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(30.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape).clickable { onAdd() }, contentAlignment = Alignment.Center) {
                Ico(R.drawable.p1_plus, 16.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        items.forEachIndexed { i, a ->
            val isDone = a.id in done
            val isCur = a.id == current
            val dim = if (isDone) 0.55f else 1f
            Box(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 8.dp)) {
                if (isCur) Box(Modifier.fillMaxHeight().fillMaxWidth().padding(start = 44.dp).clip(RoundedCornerShape(13.dp)).border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(13.dp)))
                Row(Modifier.fillMaxSize().padding(start = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.time.ifBlank { "—" }, color = if (isCur) C.White else C.Text2, fontSize = 11.sp, modifier = Modifier.width(50.dp))
                    Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val x = size.width / 2
                            drawLine(C.Hairline, Offset(x, if (i == 0) size.height / 2 else 0f), Offset(x, if (i == items.lastIndex) size.height / 2 else size.height), 1.dp.toPx())
                            drawCircle(if (isCur) Color.White else Color.White.copy(alpha = 0.35f), if (isCur) 3.5.dp.toPx() else 2.5.dp.toPx(), Offset(x, size.height / 2))
                        }
                    }
                    Ico(agendaIconRes(a.icon), 18.dp, Color.White.copy(alpha = dim), Modifier.padding(start = 12.dp))
                    Text(a.title, color = if (isDone) C.Text2 else C.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    val sc = rememberSweep(if (isDone) 1f else 0f, 250)
                    Box(
                        Modifier.padding(end = 10.dp).size(19.dp).clip(CircleShape).clickable { onToggle(a.id) }
                            .then(if (isDone) Modifier.background(C.White) else Modifier.border(1.5.dp, C.White.copy(alpha = 0.9f), CircleShape)),
                        contentAlignment = Alignment.Center,
                    ) { if (isDone) Ico(R.drawable.p1_checkmark, (13 * (0.65f + 0.35f * sc)).dp, C.Black) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun TimeBankCard(balance: Int, earned: Int, spent: Int, onUnlock: () -> Unit) {
    val frac = rememberSweep(if (balance + spent == 0) 0f else balance / (balance + spent).toFloat(), 500)
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Time Bank", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(Modifier.height(31.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape).clickable { onUnlock() }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Unlock", color = C.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Ico(R.drawable.ic_arrow_forward, 14.dp, C.White, Modifier.padding(start = 6.dp))
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$balance", style = glow(76f, 300).copy(lineHeight = 80.sp, letterSpacing = (-2).sp))
            Text("min", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp))
        }
        Caps("Available now", color = C.Text2)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(C.Track.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(CircleShape).background(C.White))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$earned min earned today", color = C.Text2, fontSize = 11.sp)
            Text("$spent min top-ups", color = C.Text2, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun StudyCard(logs: List<com.pranav.focus.data.QLog>, onClick: () -> Unit) {
    val all = logs.tally()
    Column(Modifier.fillMaxWidth().glass(24.dp).clickable { onClick() }.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Study today", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                Ico(R.drawable.p1_chevron_right, 16.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StudyMetric("${all.attempted}", "Questions")
            Box(Modifier.width(1.dp).height(44.dp).background(C.Hairline))
            StudyMetric(if (all.attempted == 0) "—" else "${all.accPct}%", "Accuracy")
            Box(Modifier.width(1.dp).height(44.dp).background(C.Hairline))
            StudyMetric(if (all.attempted == 0) "—" else (if (all.marks >= 0) "+" else "") + "${all.marks}", "Marks")
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Subject.values().forEach { s ->
                val t = logs.filter { it.subject == s }.tally()
                Row(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(14.dp)).background(C.Raised).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico(when (s) { Subject.PHYSICS -> R.drawable.p1_atom; Subject.CHEMISTRY -> R.drawable.p1_chemistry_flask; else -> R.drawable.p1_biology_leaf }, 20.dp)
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(s.label, color = C.White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("${t.attempted} q · ${if (t.attempted == 0) "—" else "${t.accPct}%"}", color = C.Text2, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                    Canvas(Modifier.size(width = 10.dp, height = 20.dp)) {
                        val h = size.height * (if (t.attempted == 0) 0.15f else t.accPct / 100f)
                        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset.Zero, size, androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        drawRoundRect(Color.White, Offset(0f, size.height - h), Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyMetric(v: String, l: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, style = glow(30f, 300, soft = true))
        Caps(l)
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun RingsCard(focusMin: Int, workoutMin: Int, screenMin: Int, budgetMin: Int, onClick: () -> Unit) {
    val rows = listOf(
        Triple("Focus", "${Fmt.hm(focusMin)} / 6h", (focusMin / 360f)),
        Triple("Workout", "${workoutMin}m / 60m", (workoutMin / 60f)),
        Triple("Screen-time Budget", "${Fmt.hm(screenMin)} / ${Fmt.hm(budgetMin)}", if (budgetMin == 0) 0f else screenMin / budgetMin.toFloat()),
    )
    val icons = listOf(R.drawable.p1_study_book, R.drawable.p1_dumbbell, R.drawable.ic_smartphone)
    val sweeps = rows.mapIndexed { i, r -> rememberSweep(r.third.coerceIn(0f, 1f), 600, i * 80) }
    Column(Modifier.fillMaxWidth().glass(24.dp).clickable { onClick() }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily Rings", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.size(30.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape), contentAlignment = Alignment.Center) { Ico(R.drawable.p1_chevron_right, 16.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(116.dp)) {
                val radii = listOf(54.dp.toPx(), 40.dp.toPx(), 26.dp.toPx())
                val widths = listOf(8.dp.toPx(), 8.dp.toPx(), 8.dp.toPx())
                val cols = listOf(1f, 0.75f, 0.5f)
                radii.forEachIndexed { i, r ->
                    val tl = Offset(size.width / 2 - r, size.height / 2 - r)
                    drawArc(C.Track, 0f, 360f, false, tl, Size(r * 2, r * 2), style = Stroke(widths[i]))
                    drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f * sweeps[i], false, tl, Size(r * 2, r * 2), style = Stroke(widths[i] + 5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Color.White.copy(alpha = cols[i]), -90f, 360f * sweeps[i], false, tl, Size(r * 2, r * 2), style = Stroke(widths[i], cap = StrokeCap.Round))
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Ico(icons[i], 17.dp) }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(r.first, color = C.White, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                            Text(r.second, color = C.Text2, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                        }
                        Box(Modifier.width(44.dp).height(6.dp).clip(CircleShape).background(C.Track)) {
                            Box(Modifier.fillMaxWidth(sweeps[i]).fillMaxHeight().background(C.White))
                        }
                        Text("${(r.third * 100).toInt().coerceAtMost(999)}%", color = C.Text2, fontSize = 9.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
@Composable
private fun StreakCard(streak: Int, xp: Int, onClick: () -> Unit) {
    val cur = Ranks.forXp(xp)
    val nxt = Ranks.next(xp)
    val bar = rememberSweep(Ranks.progress(xp), 500)
    Row(Modifier.fillMaxWidth().height(67.dp).glass(24.dp).clickable { onClick() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(R.drawable.p1_calories_flame, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text("$streak", style = glow(35f, 300, soft = true))
        Column(Modifier.padding(start = 6.dp)) {
            Text("days", color = C.White, fontSize = 15.sp, lineHeight = 16.sp)
            Caps("Streak", color = C.White)
        }
        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(36.dp).background(C.Hairline))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth()) {
                Text("XP  ", color = C.Text2, fontSize = 12.sp)
                Text("%,d".format(xp), color = C.White, fontSize = 13.sp)
                Text(" / %,d".format(nxt?.minXp ?: xp), color = C.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
                Spacer(Modifier.weight(1f))
                Caps(cur.name, color = C.White)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(bar).fillMaxHeight().background(C.White)) }
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape), contentAlignment = Alignment.Center) { Ico(R.drawable.p1_chevron_right, 15.dp) }
    }
}

@Composable
private fun StartFocusCard(lastMin: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(93.dp).glass(24.dp).clickable { onClick() }.padding(14.dp)) {
        Text("Start Focus", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).bloomCircle().clip(CircleShape).background(C.White), contentAlignment = Alignment.Center) { Ico(R.drawable.p1_play_filled, 22.dp, C.Black) }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Last session", color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp)
                Text(Fmt.hm(lastMin), color = C.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun Modifier.bloomCircle(): Modifier = this.bloom(8.dp, 0.25f, 999.dp)

@Composable
private fun WorkoutCard(modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(93.dp).glass(24.dp).clickable { onClick() }.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Today’s Workout", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.size(26.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape), contentAlignment = Alignment.Center) { Ico(R.drawable.p1_chevron_right, 14.dp) }
        }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(C.Raised), contentAlignment = Alignment.Center) { Ico(R.drawable.p1_dumbbell, 24.dp) }
            Column(Modifier.padding(start = 10.dp)) {
                Text("No plan yet", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Text("Open Workout", color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
/** Everything due today in one place, each row opening the screen that handles it. */
@Composable
private fun TodayCard(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick = AppState.dataTick + AppState.tick
    val tasksLeft = remember(tick) { PlanState.load(ctx); PlanState.forDate(AppClock.today()).count { !it.done } }
    val mistakesDue = remember(tick) {
        runCatching { val t = AppClock.today().toString(); StudyDb.get(ctx).mistakes().count { it.due <= t } }.getOrDefault(0)
    }
    val workoutToday = remember(tick) { store.workoutsOn(store.today()) }
    val rows = listOf(
        Triple("Plan tasks left", "$tasksLeft", Screen.Plan),
        Triple("Workout today", if (workoutToday > 0) "Done" else "Not yet", Screen.Workout),
        Triple("Mistakes due for review", "$mistakesDue", Screen.Study),
        Triple("Study questions today", "${store.studyToday}", Screen.Study),
        Triple("Time Bank available", "${store.bankMinutes} min", Screen.TimeBank),
    )
    SectionCard(pad = 14.dp, radius = 22.dp) {
        Caps("Today")
        rows.forEach { (label, value, target) ->
            Row(Modifier.fillMaxWidth().clickable { go(target) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(value, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun WeeklyCard(focus: Map<String, Int>) {
    val today = AppClock.today()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val hours = days.map { (focus[it.toString()] ?: 0) / 60f }
    val max = (hours.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val g = rememberSweep(1f, 400)
    Column(Modifier.fillMaxWidth().glass(24.dp).padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly Focus", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Row(Modifier.height(22.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Hours", color = C.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                Ico(R.drawable.p1_chevron_down, 12.dp, C.White, Modifier.padding(start = 3.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(78.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            hours.forEachIndexed { i, h ->
                val isToday = i == hours.lastIndex
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.width(36.dp)) {
                    Text("%.1fh".format(h), color = if (isToday) C.White else C.Text2, fontSize = 9.sp, lineHeight = 11.sp)
                    Spacer(Modifier.height(3.dp))
                    val bh = (8 + 44 * (h / max)) * g
                    Box(
                        Modifier.width(27.dp).height(bh.dp).clip(RoundedCornerShape(5.dp))
                            .then(if (isToday) Modifier.bloom(5.dp, 0.22f, 5.dp) else Modifier)
                            .background(if (isToday) C.White else Color.White.copy(alpha = 0.22f))
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                Text(if (i == days.lastIndex) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                    color = if (i == days.lastIndex) C.White else C.Text2, fontSize = 10.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
