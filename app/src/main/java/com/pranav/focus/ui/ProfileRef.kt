package com.pranav.focus.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Ranks
import com.pranav.focus.data.Store
import com.pranav.focus.lock.AppState
import com.pranav.focus.net.HealthSync
import com.pranav.focus.net.Notion
import org.json.JSONObject

private fun exportAll(ctx: Context): String {
    val o = JSONObject()
    Store.get(ctx).sp.all.forEach { (k, v) -> o.put(k, if (v is Set<*>) org.json.JSONArray(v.toList()) else v) }
    return o.toString(2)
}

// ---------------------------------------------------------------------------------------
// Building blocks (monochrome, matches PROFILE reference)
// ---------------------------------------------------------------------------------------
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 2.4.sp,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(C.Card).border(1.dp, C.Hairline, shape)) { content() }
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(1.dp).background(C.Hairline))
}

@Composable
private fun GlyphTile(icon: Int) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 22.dp, C.White) }
}

@Composable
private fun ProfSettingRow(
    icon: Int, title: String, subtitle: String, subtitleColor: Color = C.Text2,
    chevron: Boolean = true, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphTile(icon)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(title, color = C.White, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = subtitleColor, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2)
        }
        trailing()
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Ico(R.drawable.ic_chevron_right, 20.dp, C.Text2)
        }
    }
}

@Composable
private fun StatusCapsule(on: Boolean) {
    Text(
        if (on) "Connected" else "Connect", color = if (on) C.White else C.Text2, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(CircleShape).background(if (on) C.Raised else Color.Transparent)
            .border(1.dp, if (on) C.Text3 else C.Track, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

/** Solid white disc + black tick when granted; hollow ring when not. Never shows success unless granted. */
@Composable
private fun PermState(granted: Boolean) {
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .then(if (granted) Modifier.background(C.White) else Modifier.border(1.5.dp, C.Text3, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (granted) Ico(R.drawable.focus_checkmark_black, 15.dp, C.Black) }
}

/** Animated native toggle: track colour and thumb position tween; thumb carries a soft bloom when ON. */
@Composable
private fun GlowToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, tween(220), label = "thumb")
    val track by animateColorAsState(if (checked) Color(0xFF3A3A3D) else C.Track, tween(220), label = "track")
    val thumb by animateColorAsState(if (checked) C.White else C.Text2, tween(220), label = "thumbc")
    Box(
        Modifier.size(width = 56.dp, height = 32.dp)
            .semantics { role = Role.Switch }
            .clip(CircleShape).background(track).clickable { onChange(!checked) }.padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(26.dp)
                .then(if (checked) Modifier.bloom(6.dp, 0.35f, 13.dp) else Modifier)
                .clip(CircleShape).background(thumb),
        )
    }
}

@Composable
private fun ToneSegment(selected: Int, onSelect: (Int) -> Unit) {
    val opts = listOf("Normal", "Hard", "Goggins")
    Row(Modifier.clip(CircleShape).border(1.dp, C.Track, CircleShape).padding(2.dp)) {
        opts.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) C.White else Color.Transparent, tween(180), label = "seg")
            Box(
                Modifier.height(34.dp).clip(CircleShape).background(bg).clickable { onSelect(i) }.padding(horizontal = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (sel) C.Black else C.Text2, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}

@Composable
private fun MiniStat(icon: Int, value: String, label: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.clip(shape).background(C.Card).border(1.dp, C.Hairline, shape).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, 22.dp, C.White)
        Column(Modifier.padding(start = 8.dp)) {
            Text(value, color = C.White, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = C.Text2, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
        }
    }
}

/** Monochrome summit artwork (vector reconstruction; the original photographic art was not supplied). */
@Composable
private fun SummitArt(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cx = w * 0.62f; val cy = h * 0.34f; val r = h * 0.2f
        for (i in 1..3) drawCircle(Color.White.copy(alpha = 0.05f * (4 - i)), r + i * 5.dp.toPx(), Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.85f), r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
        val ridge = Path().apply {
            moveTo(0f, h); lineTo(w * 0.18f, h * 0.78f); lineTo(w * 0.34f, h * 0.84f); lineTo(w * 0.62f, h * 0.5f)
            lineTo(w * 0.8f, h * 0.74f); lineTo(w * 0.9f, h * 0.66f); lineTo(w, h * 0.8f); lineTo(w, h); close()
        }
        drawPath(ridge, Color(0xFF1C1C1E))
        drawPath(ridge, Color.White.copy(alpha = 0.18f), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color.White, 2.5.dp.toPx(), Offset(w * 0.62f, h * 0.47f))
    }
}

// ---------------------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------------------
@Composable
private fun StatTile(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.clip(shape).background(C.Card).border(1.dp, C.Hairline, shape).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label.uppercase(), color = C.Text2, fontSize = 10.sp, lineHeight = 13.sp, letterSpacing = 1.2.sp, maxLines = 1)
        Text(value, color = C.White, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        Text(unit, color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
    }
}

@Composable
fun ProfileScreenRef(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick = AppState.tick + AppState.dataTick
    var watch by remember { mutableStateOf(store.watchMe) }
    var tone by remember { mutableIntStateOf(store.tone) }
    var imperial by remember { mutableStateOf(store.imperial) }
    var lockOn by remember { mutableStateOf(store.restrictionsEnabled) }
    var dialog by remember { mutableIntStateOf(0) } // 1 reset, 2 disable lock, 3 delete account

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(exportAll(ctx).toByteArray()) } }
    }

    val xp = store.xp
    val rank = Ranks.forXp(xp)
    val nextXp = Ranks.next(xp)?.minXp ?: xp
    val xpFrac by animateFloatAsState(Ranks.progress(xp), tween(600), label = "xp")
    val hcOn = remember(tick) { HealthSync.connected(ctx) }
    val notionOn = remember(tick) { Notion.configured(ctx) }
    val usageOn = remember(tick) { UsageHelper.hasAccess(ctx) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "—" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
    ) {
        // ---- hero ----
        Box(Modifier.fillMaxWidth()) {
            SummitArt(Modifier.align(Alignment.TopEnd).size(width = 170.dp, height = 110.dp))
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(80.dp).bloom(5.dp, 0.25f, 40.dp).clip(CircleShape).background(C.Sheet).border(2.dp, C.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("P", color = C.White, fontSize = 32.sp, fontWeight = FontWeight.Light) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("Pranav Heriyur", color = C.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(rank.name.uppercase(), color = C.Text2, fontSize = 11.sp, letterSpacing = 3.sp, modifier = Modifier.padding(top = 2.dp))
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                            Text("%,d".format(xp), color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(" / %,d XP".format(nextXp), color = C.Text2, fontSize = 13.sp)
                        }
                        Box(Modifier.padding(top = 6.dp).fillMaxWidth(0.8f).height(5.dp).clip(CircleShape).background(C.Track)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(xpFrac.coerceIn(0.02f, 1f)).clip(CircleShape).background(C.White))
                        }
                    }
                }
            }
        }

        // ---- your stats: every number is labelled ----
        SectionLabel("Your stats")
        val wkDays = remember(tick) { store.workoutDaysInWeek() }
        val bestMock = remember(tick) { com.pranav.focus.data.StudyDb.get(ctx).mocks().maxOfOrNull { it.score } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Day streak", "${store.streak}", "days in a row", Modifier.weight(1f))
                StatTile("Time Bank", "${store.bankMinutes}", "min available", Modifier.weight(1f))
                StatTile("Focus today", "${store.todayFocusMinutes}", "min", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Total XP", "%,d".format(xp), rank.name, Modifier.weight(1f))
                StatTile("Workout days", "$wkDays", "of the last 7", Modifier.weight(1f))
                StatTile("Best mock", bestMock?.toString() ?: "—", if (bestMock == null) "no test yet" else "of 720", Modifier.weight(1f))
            }
        }

        // ---- focus and rules ----
        SectionLabel("Focus and rules")
        GroupCard {
            ProfSettingRow(R.drawable.ic_grid_view, "Allowed apps", "Apps you can open during a session", onClick = { go(Screen.Apps) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_call, "Allowed callers", "Numbers that get through a session", onClick = { go(Screen.Callers) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_tune, "App rules", "Daily limits and unlock rules", onClick = { go(Screen.Rules) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_hourglass_top, "Time Bank", "Earn minutes, then spend them", onClick = { go(Screen.TimeBank) })
        }

        // ---- study and plan ----
        SectionLabel("Study and plan")
        GroupCard {
            ProfSettingRow(R.drawable.plan_ic_notes, "Mock test trend", "Score graph over time, per subject") {
                store.putStr("study_open", "mock_trend"); go(Screen.Study)
            }
            RowDivider()
            ProfSettingRow(R.drawable.ic_menu_book, "Study hub", "Quick log, syllabus and mistakes", onClick = { go(Screen.Study) })
            RowDivider()
            ProfSettingRow(R.drawable.plan_ic_plan, "Plan", "Today's tasks and schedule", onClick = { go(Screen.Plan) })
        }

        // ---- workouts ----
        SectionLabel("Workouts")
        GroupCard {
            ProfSettingRow(R.drawable.p1_dumbbell, "Workout hub", "Plans, routines and the active session", onClick = { go(Screen.Workout) })
            RowDivider()
            ProfSettingRow(R.drawable.wk_ui_chart, "Workout stats", "Volume, strength and trends", onClick = { go(Screen.WorkoutStats) })
            RowDivider()
            ProfSettingRow(R.drawable.wk_ui_calendar, "Workout history", "Every saved session", onClick = { go(Screen.WorkoutHistory) })
        }

        // ---- rewards ----
        SectionLabel("Rewards")
        GroupCard {
            ProfSettingRow(R.drawable.ic_emoji_events, "Rank", "${rank.name} · ${"%,d".format(xp)} XP", onClick = { go(Screen.Rank) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_visibility_off, "Accountability mirror", "Review today", onClick = { go(Screen.Mirror) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_redeem, "Cookie jar", "${store.jar.size} wins saved", onClick = { go(Screen.Jar) })
        }

        // ---- connections ----
        SectionLabel("Connections")
        GroupCard {
            ProfSettingRow(R.drawable.ic_directions_run, "Strava", "Sync your runs, rides and workouts", onClick = { go(Screen.Connections) }) { StatusCapsule(hcOn) }
            RowDivider()
            ProfSettingRow(R.drawable.ic_menu_book, "Notion", "Sync your study sessions and notes", onClick = { go(Screen.Connections) }) { StatusCapsule(notionOn) }
            RowDivider()
            ProfSettingRow(R.drawable.ic_monitor_heart, "Health Connect", "Access your activity and health data", onClick = { go(Screen.Connections) }) { StatusCapsule(hcOn) }
        }

        // ---- watch ----
        SectionLabel("Watch")
        GroupCard {
            ProfSettingRow(R.drawable.ic_watch, "Watch worn by me", "Track workouts, HR and activity", chevron = false,
                onClick = { watch = !watch; store.watchMe = watch }) { GlowToggle(watch) { watch = it; store.watchMe = it } }
        }

        // ---- permissions and security ----
        SectionLabel("Permissions and security")
        GroupCard {
            ProfSettingRow(R.drawable.focus_accessibility_white, "Accessibility", "Block apps during focus", onClick = { go(Screen.Setup) }) { PermState(AppState.a11yOn) }
            RowDivider()
            ProfSettingRow(R.drawable.focus_overlay_windows_white, "Display over other apps", "Show timer and block screen", onClick = { go(Screen.Setup) }) { PermState(AppState.overlayOn) }
            RowDivider()
            ProfSettingRow(R.drawable.focus_usage_bars_white, "Usage access", "Track app usage and screen time", onClick = { go(Screen.Setup) }) { PermState(usageOn) }
            RowDivider()
            ProfSettingRow(R.drawable.focus_phone_call_white, "Call screening", "Only allow selected callers",
                onClick = { go(if (AppState.callRoleOn) Screen.Callers else Screen.Setup) }) { PermState(AppState.callRoleOn) }
            RowDivider()
            ProfSettingRow(R.drawable.focus_notification_bell_white, "Notifications", "Show essential alerts", onClick = { go(Screen.Setup) }) { PermState(AppState.notifOn) }
            RowDivider()
            ProfSettingRow(R.drawable.ic_fingerprint, "Security", if (store.biometricLock) "Biometric lock on" else "App lock off", onClick = { go(Screen.Security) })
            RowDivider()
            ProfSettingRow(R.drawable.ic_shield, "Setup checklist", if (AppState.coreReady) "Core permissions ready" else "Action needed", onClick = { go(Screen.Setup) })
        }

        // ---- preferences ----
        SectionLabel("Preferences")
        GroupCard {
            ProfSettingRow(R.drawable.focus_notification_bell_white, "Notification tone", "Sound when session starts/ends", chevron = false) {
                ToneSegment(tone) { tone = it; store.tone = it }
            }
            RowDivider()
            ProfSettingRow(R.drawable.ic_straighten, "Units", "Distance, weight, temperature", onClick = { imperial = !imperial; store.imperial = imperial }) {
                Text(if (imperial) "Imperial (mi, lb, °F)" else "Metric (km, kg, °C)", color = C.Text2, fontSize = 12.sp, textAlign = TextAlign.End)
            }
            RowDivider()
            ProfSettingRow(R.drawable.ic_database, "Export data", "Download your data", onClick = { export.launch("focus-export.json") })
            RowDivider()
            ProfSettingRow(R.drawable.ic_info, "About", "Version $version", subtitleColor = C.Text2, onClick = { })
        }

        // ---- danger zone ----
        SectionLabel("Danger zone")
        DangerRow(R.drawable.focus_trash_white, "Reset all data", "Delete all your progress") { dialog = 1 }
        Spacer(Modifier.height(8.dp))
        DangerRow(
            R.drawable.ic_power_settings_new, if (lockOn) "Disable app lock" else "Enable app lock",
            if (lockOn) "Turn off all restrictions" else "Turn restrictions back on",
        ) { if (lockOn) dialog = 2 else { lockOn = true; store.restrictionsEnabled = true } }
        Spacer(Modifier.height(8.dp))
        DangerRow(R.drawable.ic_person, "Delete account", "Permanently delete your account") { dialog = 3 }
    }

    if (dialog != 0) {
        val (title, body, confirm) = when (dialog) {
            1 -> Triple("Reset everything?", "XP, streak, rules, jar and history are erased. This can’t be undone.", "Reset")
            2 -> Triple("Turn off app lock?", "All app rules and blocks pause until you turn this back on.", "Disable")
            else -> Triple("No online account", "Focus keeps everything on this phone, so there is no account to delete. Use “Reset all data” to erase your data.", "OK")
        }
        AlertDialog(
            onDismissRequest = { dialog = 0 },
            containerColor = C.Sheet,
            title = { Text(title, color = C.White) },
            text = { Text(body, color = C.Text2) },
            confirmButton = {
                TextButton({
                    when (dialog) {
                        1 -> { store.resetAll(); AppState.dataTick++; AppState.refresh(ctx); watch = store.watchMe; tone = store.tone; imperial = store.imperial; lockOn = store.restrictionsEnabled }
                        2 -> { store.restrictionsEnabled = false; lockOn = false }
                    }
                    dialog = 0
                }) { Text(confirm, color = C.White) }
            },
            dismissButton = { if (dialog != 3) TextButton({ dialog = 0 }) { Text("Cancel", color = C.Text2) } },
        )
    }
}

@Composable
private fun DangerRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).border(1.dp, C.Text3, shape).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, 22.dp, C.White)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, color = C.White, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = C.Text2, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Ico(R.drawable.ic_chevron_right, 20.dp, C.Text2)
    }
}
