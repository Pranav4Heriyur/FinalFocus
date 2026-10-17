package com.pranav.focus.ui

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pranav.focus.R
import com.pranav.focus.data.ExitRules
import com.pranav.focus.data.Store
import com.pranav.focus.lock.Allowed
import com.pranav.focus.lock.SessionController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun formatClock(ms: Long): String {
    val total = (ms + 999) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun subjectIcon(subject: String): Int = when (subject) {
    "Physics" -> R.drawable.ic_atr
    "Chemistry" -> R.drawable.ic_science
    "Biology" -> R.drawable.ic_eco
    "Workout" -> R.drawable.ic_fitness_center
    else -> R.drawable.ic_more_horiz
}

/** A3: locked timer, with the A4 leave-early sheet on top when the user taps End. */
@Composable
fun SessionScreen() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val totalMs = remember { (store.sessionMinutes * 60_000L).coerceAtLeast(1) }
    var remaining by remember { mutableLongStateOf(store.remainingMs()) }
    var sheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            remaining = store.remainingMs()
            if (remaining <= 0) {
                SessionController.complete(ctx)
                break
            }
            delay(250)
        }
    }

    val progress = (remaining.toFloat() / totalMs).coerceIn(0f, 1f)
    val subject = store.sessionSubject
    val name = store.sessionName.ifBlank { subject }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconTile(subjectIcon(subject))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Focus Session • ${store.sessionMinutes} min", color = C.Text2, fontSize = 12.sp)
                }
                Row(
                    Modifier.clip(CircleShape).border(1.dp, C.Hairline, CircleShape).background(C.Card)
                        .clickable { sheet = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(R.drawable.ic_close, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("End", color = C.White, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.weight(1f))
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val topLeft = Offset(stroke * 1.5f, stroke * 1.5f)
                    val arcSize = Size(size.width - stroke * 3, size.height - stroke * 3)
                    drawArc(C.Track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    // cheap bloom: wider, faint arcs behind the bright one
                    drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f), -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke * 3.2f, cap = StrokeCap.Round))
                    drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f), -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke * 2f, cap = StrokeCap.Round))
                    drawArc(C.White, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatClock(remaining), color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.SemiBold)
                    Text("/ ${formatClock(totalMs)}", color = C.Text3, fontSize = 18.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Deep Work: $subject", color = C.Text2, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clip(CircleShape).background(C.Sheet).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(R.drawable.ic_lock, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text("Locked", color = C.White, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))

            AllowedTiles()
            Spacer(Modifier.height(16.dp))
            Text("Hold to exit", color = C.Text3, fontSize = 12.sp, modifier = Modifier.clickable { sheet = true }.padding(8.dp))
        }

        if (sheet) {
            val pct = ((1f - progress) * 100).toInt()
            ExitSheet(percent = pct, onDismiss = { sheet = false })
        }
    }
}

@Composable
private fun AllowedTiles() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val pm = ctx.packageManager
    val dialer = remember { Allowed.dialerPackage(ctx) }
    val pkgs = remember {
        buildList {
            if (dialer != null) add(dialer)
            store.allowedApps.filter { Allowed.isInstalled(ctx, it) && it != dialer }.forEach { add(it) }
        }
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        pkgs.forEach { pkg ->
            val label = remember(pkg) { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
            val icon = remember(pkg) { runCatching { pm.getApplicationIcon(pkg).toBitmap(120, 120).asImageBitmap() }.getOrNull() }
            Column(
                Modifier.clickable {
                    val launch = if (pkg == dialer) Intent(Intent.ACTION_DIAL) else pm.getLaunchIntentForPackage(pkg)
                    launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(C.Sheet).border(1.dp, C.Hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (pkg == dialer) Ico(R.drawable.ic_call, 28.dp)
                    else if (icon != null) Image(icon, label, Modifier.size(40.dp).clip(CircleShape))
                    else Text(label.take(1), color = C.White)
                }
                Spacer(Modifier.height(6.dp))
                Text(if (pkg == dialer) "Phone" else label, color = C.Text2, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** A4: leave early. Hold 60 s, type the phrase, then exit. */
@Composable
private fun ExitSheet(percent: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var holdDone by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.64f)).clickable(enabled = true) { })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(C.Sheet)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(C.Text3))
            Spacer(Modifier.height(20.dp))
            Text("You’re at $percent%.", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
            Text("You can do more than you think.", color = C.Text2, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, C.Hairline, RoundedCornerShape(20.dp)).background(C.Card).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(R.drawable.ic_local_fire_department, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Leaving costs 50 XP and resets your ${store.streak}-day streak.",
                    color = C.White, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f),
                )
            }
            val win = remember { store.jar.randomOrNull() }
            if (win != null) {
                Spacer(Modifier.height(12.dp))
                Text("Remember: ${win.title}", color = C.Text2, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
            HoldRing(ExitRules.HOLD_SECONDS, holdDone) { holdDone = true }
            Spacer(Modifier.height(16.dp))
            Text("Type to confirm", color = C.Text2, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = phrase, onValueChange = { phrase = it }, singleLine = true,
                placeholder = { Text("Type: ${ExitRules.PHRASE}") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Exit session", enabled = holdDone && ExitRules.phraseOk(phrase)) { SessionController.quit(ctx) }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Stay focused") { onDismiss() }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Press-and-hold ring. Releasing early resets it. */
@Composable
fun HoldRing(seconds: Int, done: Boolean, onDone: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shown = if (done) 1f else progress
    Box(
        Modifier.size(200.dp).clip(CircleShape).pointerInput(done) {
            if (done) return@pointerInput
            detectTapGestures(onPress = {
                val start = SystemClock.elapsedRealtime()
                val job = scope.launch {
                    while (true) {
                        val p = (SystemClock.elapsedRealtime() - start) / (seconds * 1000f)
                        progress = p.coerceAtMost(1f)
                        if (p >= 1f) {
                            onDone()
                            break
                        }
                        delay(50)
                    }
                }
                tryAwaitRelease()
                job.cancel()
                if (!done) progress = 0f
            })
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val tl = Offset(stroke * 1.5f, stroke * 1.5f)
            val sz = Size(size.width - stroke * 3, size.height - stroke * 3)
            drawArc(C.Track, 0f, 360f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f), -90f, 360f * shown, false, tl, sz, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(C.White, -90f, 360f * shown, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ico(if (done) R.drawable.ic_check else R.drawable.ic_hourglass_top, 28.dp)
            Spacer(Modifier.height(6.dp))
            Caps(if (done) "Unlocked" else "Hold to confirm")
            val left = kotlin.math.ceil((1f - shown) * seconds).toInt()
            Text(if (done) "Now type below" else "$left s", color = C.White, fontSize = if (done) 14.sp else 34.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
