package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Caller
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Polyline
import com.pranav.focus.data.Store
import com.pranav.focus.lock.AppState
import com.pranav.focus.net.NetState
import com.pranav.focus.net.Notion
import com.pranav.focus.net.Secure
import com.pranav.focus.net.HealthSync
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos

// --------------------------------------------------------------------------------------
// A16 Connections
// --------------------------------------------------------------------------------------
@Composable
fun ConnectionsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick = AppState.dataTick
    val status = NetState.health
    val nstatus = NetState.notion
    var nTok by remember { mutableStateOf("") }
    var nDb by remember { mutableStateOf("") }
    var watch by remember { mutableStateOf(store.watchMe) }
    val connected = remember(tick, status) { HealthSync.connected(ctx) }
    val permLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        Thread { HealthSync.refreshPermissions(ctx); AppState.dataTick++; HealthSync.sync(ctx) }.start()
    }
    val routeLauncher = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { r -> HealthSync.saveRoute(ctx, r) }
    androidx.compose.runtime.LaunchedEffect(Unit) { Thread { HealthSync.refreshPermissions(ctx); AppState.dataTick++ }.start() }
    val nOn = remember(tick, nstatus) { Notion.configured(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Connections", back, "Your data, only where you send it.")

        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_monitor_heart, active = connected)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Health Connect", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (connected) "Connected · runs, rides, workouts" else "Reads runs, walks and routes from your watch", color = C.Text2, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!HealthSync.sdkAvailable(ctx)) {
                Text("Install or update Health Connect from Play Store, then come back.", color = C.Text2, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                GhostButton("Open Play Store") {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else if (!connected) {
                Text("In Samsung Health (or your watch app) turn on Health Connect sync, so runs and GPS routes are written to Health Connect. Then allow Focus to read exercise, route, distance and heart rate. Data never leaves your phone.", color = C.Text2, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Allow access") { permLauncher.launch(HealthSync.PERMISSIONS) }
            } else {
                GhostButton("Sync now", icon = R.drawable.ic_sync) { HealthSync.syncAsync(ctx) }
                if (store.str("hc_route_pending").isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Allow route for latest run") { routeLauncher.launch(store.str("hc_route_pending")) }
                }
            }
            if (status.isNotBlank()) Text(status, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }

        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_database, active = nOn)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Notion", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (nOn) "Each finished session is logged" else "Log sessions to a database", color = C.Text2, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (nOn) {
                GhostButton("Send today’s summary") {
                    Notion.pushAsync(ctx, "Focus · ${AppClock.today()}",
                        "Focused ${Fmt.hm(store.todayFocusMinutes)} today. Streak ${store.streak} days. XP ${store.xp}.")
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Disconnect") { Notion.disconnect(ctx); AppState.dataTick++ }
            } else {
                Text("Create an internal integration at notion.so/my-integrations, share your database with it, then paste the token and database link.", color = C.Text2, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(nTok, { nTok = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Integration token") },
                    colors = fieldColors(), visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(nDb, { nDb = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Database link or ID") }, colors = fieldColors())
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Save", enabled = nTok.isNotBlank() && nDb.isNotBlank()) {
                    Notion.save(ctx, nTok, nDb); nTok = ""; NetState.notion = "Saved. Finished sessions will be logged."; AppState.dataTick++
                }
            }
            if (nstatus.isNotBlank()) Text(nstatus, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
        }

        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_watch, active = watch)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Watch worn by me", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Turn off when someone else wears it.", color = C.Text2, fontSize = 13.sp)
                }
                FocusSwitch(watch) { watch = it; store.watchMe = it; AppState.dataTick++ }
            }
            val wins = remember(tick, watch) { store.watchWindows().sortedByDescending { it.first } }
            val zone = ZoneId.systemDefault()
            val dayStart = AppClock.today().atStartOfDay(zone).toInstant().toEpochMilli()
            val todayMs = wins.sumOf { (s, e) -> (e - maxOf(s, dayStart)).coerceAtLeast(0) }
            Spacer(Modifier.height(10.dp))
            Text("On wrist today: ${Fmt.hm((todayMs / 60_000).toInt())}", color = C.Text2, fontSize = 13.sp)
            val f = DateTimeFormatter.ofPattern("d MMM HH:mm")
            wins.take(4).forEach { (s, e) ->
                Text("${Instant.ofEpochMilli(s).atZone(zone).format(f)} → ${Instant.ofEpochMilli(e).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}

// --------------------------------------------------------------------------------------
// A17 Security
// --------------------------------------------------------------------------------------
fun canLockDevice(ctx: android.content.Context): Boolean {
    val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
    return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
        BiometricManager.BIOMETRIC_SUCCESS
}


@Composable
private fun SwitchCard(icon: Int, title: String, sub: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, active = on)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(sub, color = C.Text2, fontSize = 13.sp)
            }
            FocusSwitch(on) { if (enabled) onChange(it) }
        }
    }
}

@Composable
private fun TokenRow(name: String, on: Boolean, revoke: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(if (on) "Token stored" else "Not connected", color = C.Text2, fontSize = 13.sp)
            }
            if (on) Text("Revoke", color = C.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(C.White).clickable { revoke() }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Ico(R.drawable.ic_lock, 48.dp)
        Spacer(Modifier.height(16.dp))
        Text("Focus is locked", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Unlock") { onUnlock() }
    }
}

// --------------------------------------------------------------------------------------
// A8 Allowed callers
// --------------------------------------------------------------------------------------
@Composable
fun CallersScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var list by remember { mutableStateOf(store.callers) }
    var only by remember { mutableStateOf(store.callersOnly) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    val blocked = remember { store.blockedCallsThisWeek().sum() }

    fun save(l: List<Caller>) { list = l; store.callers = l }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri: Uri? = r.data?.data
        if (r.resultCode == Activity.RESULT_OK && uri != null) {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
                if (it.moveToFirst()) save(list + Caller(it.getString(0) ?: "Contact", it.getString(1) ?: ""))
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Allowed callers", back, "During a session, only these numbers can ring you.")
        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Only these numbers can ring", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text("Needs call screening in Permissions.", color = C.Text2, fontSize = 14.sp)
                }
                FocusSwitch(only) { only = it; store.callersOnly = it }
            }
            Spacer(Modifier.height(8.dp))
            Text("$blocked blocked this week", color = C.Text3, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (true) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search") }, colors = fieldColors(), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
        }
        list.filter { q.isBlank() || it.name.contains(q, true) || it.number.contains(q) }.forEach { c ->
            FocusCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.ic_person, active = c.enabled, size = 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.name, color = C.White, fontSize = 17.sp)
                        Text(c.number, color = C.Text2, fontSize = 14.sp)
                    }
                    FocusSwitch(c.enabled) { on -> save(list.map { if (it === c) c.copy(enabled = on) else it }) }
                    Box(Modifier.clickable { save(list.filter { it !== c }) }.padding(8.dp)) { Ico(R.drawable.ic_delete, 20.dp, C.Text2) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Import from contacts", icon = R.drawable.ic_contacts) {
            pick.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }
        Spacer(Modifier.height(16.dp))
        Caps("Add a number")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            number, { number = it }, label = { Text("Phone number") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Add", enabled = name.isNotBlank() && number.filter { it.isDigit() }.length >= 5) {
            save(list + Caller(name.trim(), number.trim())); name = ""; number = ""
        }
        Spacer(Modifier.height(32.dp))
    }
}
