package com.pranav.focus.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pranav.focus.R
import com.pranav.focus.data.Caller
import com.pranav.focus.data.Store
import com.pranav.focus.lock.Allowed
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.SessionController
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

enum class Screen { Home, Focus, Study, Stats, Profile, Apps, Callers, Setup, Rules, NewRule, TimeBank, Mirror, Jar, Rank, Connections, Security, Workout,
    WorkoutPlans, WorkoutRoutine, WorkoutCreate, WorkoutDay, WorkoutAdd, WorkoutSet, WorkoutLibrary, WorkoutActive, WorkoutComplete, WorkoutStats, WorkoutHistory, WorkoutDetail,
    Plan, PlanSubjects, PlanAdd, PlanEdit, PlanReorder, PlanDetail, PlanCalendar, PlanFocus, PlanSummary, PlanSettings, StudyExempt }


// --------------------------------------------------------------------------------------
// A2: Start focus session
// --------------------------------------------------------------------------------------
@Composable
fun FocusStartScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var minutes by remember { mutableIntStateOf(store.lastMinutes) }
    var name by remember { mutableStateOf(store.lastName) }
    var subject by remember { mutableStateOf(store.lastSubject) }
    var strict by remember { mutableStateOf(store.strictLock) }
    var askCustom by remember { mutableStateOf(false) }
    if (askCustom) NameDialog("Custom time (minutes)", minutes.toString(), emptyList(), "", { askCustom = false }) { n, _ ->
        n.filter { it.isDigit() }.toIntOrNull()?.let { minutes = it.coerceIn(1, 600) }; askCustom = false
    }

    val pm = ctx.packageManager
    val appNames = remember(store.allowedApps) {
        val labels = store.allowedApps.filter { Allowed.isInstalled(ctx, it) }
            .map { runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }.getOrDefault(it) }
        (listOf("Phone") + labels.take(2)).joinToString(", ") + if (labels.size > 2) " +${labels.size - 2}" else ""
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ScreenHeader("Start Focus Session", { go(Screen.Home) }, "Choose a time, set your focus, and lock in.")
            MinutePicker(minutes) { d -> minutes = (minutes + d).coerceIn(5, 240) }

            Text("Session name", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                placeholder = { Text("e.g. Physics Practice") },
                leadingIcon = { Ico(R.drawable.ic_menu_book, 22.dp) },
                trailingIcon = { if (name.isNotEmpty()) Box(Modifier.clickable { name = "" }.padding(8.dp)) { Ico(R.drawable.ic_close, 18.dp, C.Text2) } },
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Focus on", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Physics", "Chemistry", "Biology", "Workout", "Other").forEach { s ->
                    val sel = s == subject
                    Column(
                        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) C.White else C.Card).border(1.dp, if (sel) C.White else C.Hairline, RoundedCornerShape(16.dp))
                            .clickable { subject = s },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Ico(subjectIcon(s), 26.dp, if (sel) C.Black else C.White)
                        Spacer(Modifier.height(4.dp))
                        Text(s, color = if (sel) C.Black else C.Text2, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Preset time", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(25, 45, 90)
                presets.forEach { p ->
                    val sel = minutes == p
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(CircleShape).background(if (sel) C.White else C.Card)
                            .border(1.dp, if (sel) C.White else C.Hairline, CircleShape).clickable { minutes = p },
                        contentAlignment = Alignment.Center,
                    ) { Text("$p min", color = if (sel) C.Black else C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                }
                val custom = minutes !in presets
                Box(
                    Modifier.weight(1f).height(48.dp).clip(CircleShape).background(if (custom) C.White else C.Card)
                        .border(1.dp, if (custom) C.White else C.Hairline, CircleShape).clickable { askCustom = true },
                    contentAlignment = Alignment.Center,
                ) { Text(if (custom) "$minutes min" else "Custom", color = if (custom) C.Black else C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(16.dp))
            SettingRow(R.drawable.ic_grid_view, "Allowed apps", appNames, onEdit = { go(Screen.Apps) })
            Spacer(Modifier.height(10.dp))
            SettingRow(R.drawable.ic_call, "Allowed callers", "${store.callers.size} contacts", onEdit = { go(Screen.Callers) })
            Spacer(Modifier.height(10.dp))
            FocusCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_lock)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Strict lock", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("Blocks all other apps", color = C.Text2, fontSize = 14.sp)
                    }
                    FocusSwitch(strict) { strict = it; store.strictLock = it }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (AppState.coreReady) {
            PrimaryButton("Begin Focus") { SessionController.start(ctx, minutes, name.trim(), subject) }
        } else {
            PrimaryButton("Finish setup to begin") { go(Screen.Setup) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingRow(icon: Int, title: String, sub: String, onEdit: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = C.Text2, fontSize = 14.sp, maxLines = 1)
            }
            Row(
                Modifier.clip(CircleShape).border(1.dp, C.Text3, CircleShape).clickable { onEdit() }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Edit", color = C.White, fontSize = 14.sp)
                Ico(R.drawable.ic_chevron_right, 18.dp)
            }
        }
    }
}

/** Drag up or down to change minutes in steps of 5. */
@Composable
private fun MinutePicker(value: Int, onDelta: (Int) -> Unit) {
    val cb = rememberUpdatedState(onDelta)
    val stepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 34.dp.toPx() }
    var acc by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(290.dp).pointerInput(Unit) {
            detectVerticalDragGestures { _, dy ->
                acc += dy
                while (acc <= -stepPx) { cb.value(5); acc += stepPx }
                while (acc >= stepPx) { cb.value(-5); acc -= stepPx }
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.White.copy(alpha = 0.06f), 140.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.04f), 110.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            listOf(-10, -5).forEach { d ->
                val v = value + d
                Text(if (v >= 5) "$v" else " ", color = C.White.copy(alpha = if (d == -10) 0.18f else 0.38f), fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                Modifier.padding(vertical = 4.dp).clip(CircleShape).border(1.dp, C.Hairline, CircleShape).background(C.Raised)
                    .padding(horizontal = 36.dp, vertical = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("$value", color = C.White, fontSize = 72.sp, lineHeight = 80.sp, fontWeight = FontWeight.Bold)
                Text(" min", color = C.Text2, fontSize = 18.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            listOf(5, 10).forEach { d ->
                val v = value + d
                Text(if (v <= 240) "$v" else " ", color = C.White.copy(alpha = if (d == 5) 0.38f else 0.18f), fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Profile + placeholders
// --------------------------------------------------------------------------------------
@Composable
fun ProfileStat(v: String, l: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = C.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Caps(l)
    }
}

@Composable
fun ProfileRow(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), onClick = onClick) {
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

@Composable
fun PlaceholderScreen(title: String, note: String) {
    Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 100.dp)) {
        ScreenHeader(title, null)
        FocusCard(Modifier.fillMaxWidth()) {
            Text("Coming next", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(note, color = C.Text2, fontSize = 14.sp)
        }
    }
}

// --------------------------------------------------------------------------------------
// A9 / A8 / A1
// --------------------------------------------------------------------------------------
@Composable
fun AppsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val apps = remember { Allowed.launchableApps(ctx) }
    var selected by remember { mutableStateOf(store.allowedApps) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Allowed apps", back, "Phone is always allowed. Everything else is locked during a session unless switched on here.")
        Spacer(Modifier.height(4.dp))
        var q by remember { mutableStateOf("") }
        val sel = apps.filter { it.pkg in selected }
        if (sel.isNotEmpty()) {
            Caps("Allowed · ${sel.size}")
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sel, key = { it.pkg }) { a -> AppIcon(a.pkg, 52.dp) }
            }
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search apps") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(10.dp))
        Caps("Select apps")
        Spacer(Modifier.height(4.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(apps.filter { q.isBlank() || it.label.contains(q, true) }, key = { it.pkg }) { app ->
                val icon = remember(app.pkg) { runCatching { ctx.packageManager.getApplicationIcon(app.pkg).toBitmap(80, 80).asImageBitmap() }.getOrNull() }
                val on = app.pkg in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        selected = if (on) selected - app.pkg else selected + app.pkg
                        store.allowedApps = selected
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) Image(icon, null, Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
                    else Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Sheet))
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    FocusSwitch(on) { v ->
                        selected = if (v) selected + app.pkg else selected - app.pkg
                        store.allowedApps = selected
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(back: (() -> Unit)?, requestNotif: () -> Unit, requestRole: () -> Unit, onContinue: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    fun open(i: Intent) = ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val pkgUri = Uri.parse("package:${ctx.packageName}")
    val usageOk = remember(AppState.tick) { UsageHelper.hasAccess(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (back != null) ScreenHeader("Permissions", back)
        val granted = listOf(AppState.a11yOn, AppState.overlayOn, AppState.notifOn, AppState.callRoleOn, AppState.batteryOk).count { it }
        val frac by animateFloatAsState(granted / 5f, tween(450), label = "perm")
        Box(Modifier.fillMaxWidth().height(142.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(132.dp)) {
                val w = 2.dp.toPx()
                drawCircle(Color.White.copy(alpha = .10f), size.minDimension / 2 - w, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
                drawArc(Color.White, -90f, 360f * frac, false, topLeft = Offset(w, w), size = Size(size.width - 2 * w, size.height - 2 * w),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
            Image(painterResource(R.drawable.art_lock), null, Modifier.size(width = 84.dp, height = 86.dp))
        }
        Text("Set up Focus", color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Grant these so Focus can lock your phone properly.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$granted of 5 granted", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${granted * 20}%", color = C.Text2, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Track)) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(CircleShape).background(C.White))
        }
        Spacer(Modifier.height(18.dp))
        SetupRow(R.drawable.ic_lock, "1. Accessibility service", "Required. Lets Focus see which app is on screen and send you back. Find “Focus lock” and turn it on.", AppState.a11yOn) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        SetupRow(R.drawable.ic_layers, "2. Display over other apps", "Required. Lets the timer screen come to the front.", AppState.overlayOn) {
            open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
        }
        SetupRow(R.drawable.ic_notifications, "3. Notifications", "Shows the ongoing session notification and keeps the timer alive.", AppState.notifOn) { requestNotif() }
        SetupRow(R.drawable.ic_call, "4. Call screening", "Needed for the allowed-callers list. Choose Focus when asked.", AppState.callRoleOn) { requestRole() }
        SetupRow(R.drawable.ic_bolt, "5. Battery: unrestricted", "Stops Samsung from killing the timer. Allow it here, then also set Focus to Unrestricted in Settings > Apps > Focus > Battery.", AppState.batteryOk) {
            open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
        }
        SetupRow(R.drawable.ic_bar_chart, "6. Usage access", "Optional. Shows your screen time and top apps on Home.", usageOk) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        FocusCard(Modifier.fillMaxWidth()) {
            Text("Accessibility greyed out?", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Android 13 blocks sideloaded apps from this. Open Settings > Apps > Focus, tap the three dots at the top right, choose “Allow restricted settings”, then go back to step 1. If the three dots are missing, open the accessibility toggle once first so Android shows the option.",
                color = C.Text2, fontSize = 14.sp,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Open Focus app info") { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) }
        }
        if (onContinue != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(if (AppState.coreReady) "Continue" else "Continue without lock") { onContinue() }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SetupRow(icon: Int, title: String, desc: String, done: Boolean, action: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(bottom = 12.dp), onClick = if (done) null else action) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, active = done)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(desc, color = C.Text2, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Text(if (done) "On" else "Set", color = if (done) C.White else C.Black, fontSize = 13.sp,
                modifier = Modifier.clip(CircleShape).background(if (done) C.Track else C.White).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
