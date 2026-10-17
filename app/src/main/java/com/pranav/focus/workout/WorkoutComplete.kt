package com.pranav.focus.workout

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------------------------------------ data

data class CompletedExercise(val exerciseId: String, val name: String, val muscle: String, val sets: Int, val volumeKg: Double)
data class CompletedPr(val exerciseId: String, val name: String, val muscle: String, val newKg: Double, val newReps: Int?, val prevKg: Double, val prevReps: Int?) {
    val deltaKg get() = newKg - prevKg
}
data class CompletedWorkout(
    val sessionId: String, val title: String, val category: String, val startedAt: Long, val endedAt: Long?,
    val exercises: List<CompletedExercise>, val sets: Int, val volumeKg: Double, val prs: List<CompletedPr>,
    val timing: SessionTiming = SessionTiming(null, null, null, null, false),
) {
    /** Elapsed minutes (start to finish, pauses and rests included). Same definition as C11/C12; see [WorkoutTiming]. */
    val durationMin: Long? get() = timing.elapsedMin
}

object WorkoutCompletion {
    /**
     * Builds C10's data from the persisted, *completed* session only, through the same loader as C12 ([WorkoutHistory.load]) so
     * the numbers always match. PRs come from the completion snapshot (heaviest weight beating every earlier saved session).
     */
    fun load(repo: WorkoutRepo, sessionId: String): CompletedWorkout {
        val s = repo.session(sessionId) ?: error("This workout could not be found.")
        check(s.status == "completed") { "This workout has not been saved." }
        val d = WorkoutHistory.load(repo, sessionId)
        val exercises = d.exercises.filter { it.workingSets > 0 }.map { CompletedExercise(it.exerciseId, it.name, it.muscle, it.workingSets, it.volumeKg) }
        return CompletedWorkout(d.id, d.title, d.subtitle, d.startedAt, d.endedAt, exercises, d.workingSets, d.volumeKg, d.prs, d.timing)
    }
}

private fun kgText(v: Double): String = if (v == Math.floor(v)) NumberFormat.getIntegerInstance().format(v.toLong()) else String.format(Locale.getDefault(), "%.1f", v)
private fun prValue(kg: Double, reps: Int?) = kgText(kg) + " kg" + (reps?.let { " × $it" } ?: "")
private fun whenText(startedAt: Long): String {
    val z = ZoneId.systemDefault(); val dt = Instant.ofEpochMilli(startedAt).atZone(z)
    val day = if (dt.toLocalDate() == LocalDate.now(z)) "Today, " else ""
    return day + dt.format(DateTimeFormatter.ofPattern("d MMM yyyy • h:mm a", Locale.getDefault()))
}

// ------------------------------------------------------------------------------------------------ screen

@Composable
fun WorkoutCompleteScreen(back: () -> Unit) {
    val repo = rememberRepo()
    val id = WorkoutNav.completedSessionId
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<Result<CompletedWorkout>?>(null, id, retry) {
        value = null
        value = if (id == null) Result.failure(IllegalStateException("No saved workout to show.")) else runCatching { WorkoutCompletion.load(repo, id) }
    }
    val ctx = LocalContext.current
    fun done() { WorkoutNav.completedSessionId = null; WorkoutNav.bump(); back() }
    BackHandler { done() }
    Box(Modifier.fillMaxSize().background(C.Black)) {
        val r = state
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading your workout…", color = C.Text2, fontSize = 15.sp) }
            r.isFailure -> Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
                WkCard {
                    Text("Couldn't show your workout summary", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Your workout data is not lost. ${r.exceptionOrNull()?.message.orEmpty()}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    WkButton("Retry", Modifier.fillMaxWidth(), primary = true) { retry++ }
                    Spacer(Modifier.height(8.dp)); WkButton("Done", Modifier.fillMaxWidth()) { done() }
                }
            }
            else -> {
                val w = r.getOrThrow()
                CompletedContent(w, onDetails = {
                    val open = WorkoutNav.openWorkoutDetails
                    if (open != null) open(w.sessionId) else Toast.makeText(ctx, "Workout details aren't available yet. Your workout is saved.", Toast.LENGTH_SHORT).show()
                }, onDone = ::done)
            }
        }
    }
}

@Composable
private fun CompletedContent(w: CompletedWorkout, onDetails: () -> Unit, onDone: () -> Unit) {
    var celebrate by rememberSaveable(w.sessionId) { mutableStateOf(w.prs.isNotEmpty()) }   // PR animation: only for verified PRs, once per visit
    val big = LocalDensity.current.fontScale > 1.15f
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { Hero(w) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryCard(w, big)
                    if (w.prs.isNotEmpty()) PrSection(w.prs)
                    if (w.exercises.isNotEmpty()) Text("EXERCISE BREAKDOWN", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { contentDescription = "Exercise breakdown, ${w.exercises.size} exercises" })
                }
            }
            items(w.exercises, key = { it.exerciseId }) { e ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnatomyThumb(e.muscle, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOfNotNull("${e.sets} ${if (e.sets == 1) "set" else "sets"}", e.volumeKg.takeIf { it > 0 }?.let { "${kgText(it)} kg total" }).joinToString(" • "), color = C.Text2, fontSize = 13.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(C.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WkButton("View Workout Details", Modifier.weight(1f), icon = wkIco("note"), height = 56.dp, onClick = onDetails)
            WkButton("Done", Modifier.weight(1f), primary = true, icon = wkIco("check"), height = 56.dp, onClick = onDone)
        }
    }
    if (celebrate) PrCelebration(w.prs) { celebrate = false }
}

@Composable
private fun Hero(w: CompletedWorkout) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        HeroArt(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, C.Black), startY = 300f)))
        Column(Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CompletionCheck(Modifier.size(56.dp))
            Spacer(Modifier.height(10.dp))
            Text("WORKOUT COMPLETED", color = C.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, letterSpacing = 0.5.sp)
            Text("Great work. Your workout has been saved.", color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        }
    }
}

@Composable
private fun SummaryCard(w: CompletedWorkout, big: Boolean) {
    WkCard(radius = 22.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape), contentAlignment = Alignment.Center) { Ico(wkIco("dumbbell"), 24.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(w.title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(w.category, color = C.Text2, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(whenText(w.startedAt), color = C.Text2, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        val metrics = buildList {
            WorkoutTiming.metrics(w.timing).forEach { (label, v) -> add(Triple("timer", v, label)) }
            add(Triple("layers", w.exercises.size.toString(), "Exercises"))
            add(Triple("check", w.sets.toString(), "Sets"))
            if (w.volumeKg > 0) add(Triple("dumbbell", "${kgText(w.volumeKg)} kg", "Volume"))
        }
        val rows = if (big) metrics.chunked(2) else listOf(metrics)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (ic, v, label) ->
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$label $v" }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Ico(wkIco(ic), 18.dp, C.Text2)
                        Text(v, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(label, color = C.Text2, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PrSection(prs: List<CompletedPr>) {
    val green = Color(0xFF34C759)
    WkCard(radius = 22.dp, padding = 12.dp) {
        Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico(wkIco("trophy"), 20.dp)
            Text("NEW PERSONAL RECORDS", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            Text(prs.size.toString(), color = C.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(C.Sheet).padding(horizontal = 9.dp, vertical = 3.dp))
        }
        prs.forEach { p ->
            Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AnatomyThumb(p.muscle, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Heaviest weight", color = C.Text2, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(prValue(p.newKg, p.newReps), color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("▲ +${kgText(p.deltaKg)} kg", color = green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ animations (native Compose; no Lottie dependency)

/** Ring sweeps, then the check strokes in. Plays once when C10 appears. */
@Composable
private fun CompletionCheck(modifier: Modifier = Modifier) {
    val ring = remember { Animatable(0f) }; val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing)); tick.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
    Canvas(modifier.semantics { contentDescription = "Workout completed" }) {
        val sw = 3.dp.toPx(); val inset = sw
        drawCircle(C.White.copy(alpha = 0.10f), radius = size.minDimension / 2 - inset, style = Stroke(sw))
        drawArc(C.White, -90f, 360f * ring.value, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(sw, cap = StrokeCap.Round))
        val path = Path().apply { moveTo(size.width * .30f, size.height * .52f); lineTo(size.width * .45f, size.height * .66f); lineTo(size.width * .72f, size.height * .37f) }
        val pm = android.graphics.PathMeasure(path.asAndroidPath(), false); val seg = android.graphics.Path()
        pm.getSegment(0f, pm.length * tick.value, seg, true)
        drawPath(seg.asComposePath(), C.White, style = Stroke(sw * 1.2f, cap = StrokeCap.Round))
    }
}

/** Full-screen PR celebration: trophy scales in, restrained monochrome confetti drifts down. Shown only for verified PRs. */
@Composable
private fun PrCelebration(prs: List<CompletedPr>, onContinue: () -> Unit) {
    BackHandler { onContinue() }
    val top = prs.first()
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    Box(Modifier.fillMaxSize().background(C.Black.copy(alpha = 0.96f)).clickable(enabled = false) {}) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 26) {
                val x = ((i * 53) % 100) / 100f * size.width
                val phase = ((i * 37) % 100) / 100f
                val y = (((t + phase) % 1f) * (size.height + 40.dp.toPx())) - 20.dp.toPx()
                rotate(i * 29f + t * 220f, Offset(x, y)) { drawRect(C.White.copy(alpha = 0.15f + (i % 4) * 0.08f), Offset(x, y), Size(3.dp.toPx(), (7 + i % 3 * 3).dp.toPx())) }
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size((96 * pop.value).dp).clip(CircleShape).border(2.dp, C.White.copy(alpha = 0.7f), CircleShape).background(C.Raised), contentAlignment = Alignment.Center) { Ico(wkIco("trophy"), (46 * pop.value).dp) }
            Spacer(Modifier.height(20.dp))
            Text("NEW PERSONAL RECORD", color = C.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(top.name, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
            Text(prValue(top.newKg, top.newReps), color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
            Text("+${kgText(top.deltaKg)} kg from previous best", color = Color(0xFF34C759), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            if (prs.size > 1) Text("+${prs.size - 1} more ${if (prs.size == 2) "record" else "records"}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            WkButton("Continue", Modifier.fillMaxWidth(), height = 56.dp, onClick = onContinue)
        }
    }
}

// ------------------------------------------------------------------------------------------------ vector art (local, resolution independent)

/** Dark cinematic hero: a rim-lit double-biceps silhouette drawn as vectors (the pack has no standalone hero image). */
@Composable
private fun HeroArt(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "Athlete silhouette" }) {
        val w = size.width; val h = size.height; val cx = w / 2
        drawRect(Brush.radialGradient(listOf(Color(0xFF2A2A2D), C.Black), Offset(cx, h * .35f), w * .8f))
        val body = Color(0xFF111113); val rim = Color.White.copy(alpha = 0.55f)
        fun shape(p: Path) { drawPath(p, body); drawPath(p, rim, style = Stroke(1.2.dp.toPx())) }
        val s = minOf(w / 360f.dp.toPx(), 1.3f).coerceAtLeast(0.7f) * 1f
        val u = 1.dp.toPx() * s
        val top = h * .14f
        // head + neck
        drawCircle(body, 22 * u, Offset(cx, top + 22 * u)); drawCircle(rim, 22 * u, Offset(cx, top + 22 * u), style = Stroke(1.2.dp.toPx()))
        // torso (V-taper)
        shape(Path().apply { moveTo(cx - 70 * u, top + 60 * u); lineTo(cx + 70 * u, top + 60 * u); lineTo(cx + 36 * u, top + 190 * u); lineTo(cx - 36 * u, top + 190 * u); close() })
        // arms flexed up (each: upper arm out, forearm up)
        for (sgn in listOf(-1f, 1f)) {
            shape(Path().apply {
                moveTo(cx + sgn * 62 * u, top + 62 * u); lineTo(cx + sgn * 128 * u, top + 52 * u); lineTo(cx + sgn * 138 * u, top + 80 * u); lineTo(cx + sgn * 74 * u, top + 98 * u); close()
            })
            shape(Path().apply {
                moveTo(cx + sgn * 120 * u, top + 54 * u); lineTo(cx + sgn * 140 * u, top - 14 * u); lineTo(cx + sgn * 164 * u, top - 8 * u); lineTo(cx + sgn * 142 * u, top + 78 * u); close()
            })
            drawCircle(body, 17 * u, Offset(cx + sgn * 128 * u, top + 62 * u)); drawCircle(rim, 17 * u, Offset(cx + sgn * 128 * u, top + 62 * u), style = Stroke(1.2.dp.toPx()))
        }
    }
}

internal class Region(val cx: Float, val cy: Float, val rx: Float, val ry: Float)

internal val FRONT: Map<String, List<Region>> = mapOf(
    "chest" to listOf(Region(41f, 50f, 9f, 7f), Region(59f, 50f, 9f, 7f)),
    "shoulders" to listOf(Region(29f, 41f, 6f, 6f), Region(71f, 41f, 6f, 6f)),
    "biceps" to listOf(Region(22f, 58f, 4.5f, 11f), Region(78f, 58f, 4.5f, 11f)),
    "forearms" to listOf(Region(19f, 88f, 4f, 12f), Region(81f, 88f, 4f, 12f)),
    "abs" to listOf(Region(50f, 72f, 8f, 14f)),
    "quads" to listOf(Region(41f, 130f, 7f, 20f), Region(59f, 130f, 7f, 20f)),
    "calves" to listOf(Region(41f, 180f, 5f, 15f), Region(59f, 180f, 5f, 15f)),
)
internal val BACK: Map<String, List<Region>> = mapOf(
    "traps" to listOf(Region(50f, 38f, 12f, 6f)),
    "back" to listOf(Region(41f, 58f, 8f, 14f), Region(59f, 58f, 8f, 14f)),
    "rear delts" to listOf(Region(29f, 41f, 6f, 6f), Region(71f, 41f, 6f, 6f)),
    "triceps" to listOf(Region(22f, 58f, 4.5f, 11f), Region(78f, 58f, 4.5f, 11f)),
    "glutes" to listOf(Region(43f, 108f, 8f, 9f), Region(57f, 108f, 8f, 9f)),
    "hamstrings" to listOf(Region(41f, 138f, 7f, 18f), Region(59f, 138f, 7f, 18f)),
    "calves" to listOf(Region(41f, 180f, 5f, 15f), Region(59f, 180f, 5f, 15f)),
)

/** Maps the exercise's primary-muscle text to (front?, region key). Unknown muscles return null (shows a neutral body, no fake highlight). */
private fun muscleKey(primary: String): Pair<Boolean, String>? {
    val m = primary.lowercase()
    return when {
        "chest" in m || "pec" in m -> true to "chest"
        "rear" in m && "delt" in m -> false to "rear delts"
        "shoulder" in m || "delt" in m -> true to "shoulders"
        "bicep" in m -> true to "biceps"
        "tricep" in m -> false to "triceps"
        "forearm" in m || "grip" in m -> true to "forearms"
        "lat" in m || "back" in m || "rhomboid" in m -> false to "back"
        "trap" in m -> false to "traps"
        "ab" in m || "core" in m -> true to "abs"
        "glute" in m || "hip" in m -> false to "glutes"
        "hamstring" in m || "posterior" in m -> false to "hamstrings"
        "quad" in m || "leg" in m -> true to "quads"
        "calf" in m || "calves" in m -> true to "calves"
        else -> null
    }
}

/** Anatomy thumbnail: a vector body (front or back view, whichever shows the muscle) with the exercise's primary muscle highlighted. */
@Composable
fun AnatomyThumb(primaryMuscle: String, size: Dp = 52.dp, modifier: Modifier = Modifier) {
    val key = remember(primaryMuscle) { muscleKey(primaryMuscle) }
    val front = key?.first ?: true
    Canvas(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E0E10)).semantics { contentDescription = "Anatomy: ${primaryMuscle.ifBlank { "body" }} highlighted" }) {
        val sc = this.size.height / 210f; val ox = (this.size.width - 100f * sc) / 2f
        fun o(x: Float, y: Float) = Offset(ox + x * sc, y * sc)
        val base = Color(0xFF3A3A3E)
        fun oval(r: Region, c: Color) = drawOval(c, o(r.cx - r.rx, r.cy - r.ry), Size(r.rx * 2 * sc, r.ry * 2 * sc))
        fun rr(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(base, o(x, y), Size(w * sc, h * sc), CornerRadius(4f * sc))
        drawCircle(base, 8f * sc, o(50f, 14f))
        rr(46f, 22f, 8f, 8f)
        rr(28f, 32f, 44f, 28f)          // chest/shoulders block
        rr(36f, 58f, 28f, 50f)          // torso
        rr(15f, 36f, 12f, 40f); rr(73f, 36f, 12f, 40f)      // upper arms
        rr(13f, 76f, 10f, 40f); rr(77f, 76f, 10f, 40f)      // forearms
        rr(35f, 106f, 14f, 62f); rr(51f, 106f, 14f, 62f)    // thighs
        rr(36f, 168f, 11f, 38f); rr(53f, 168f, 11f, 38f)    // calves
        if (key != null) (if (front) FRONT else BACK)[key.second]?.forEach { oval(it, Color(0xFFE8663C).copy(alpha = 0.92f)) }
    }
}
