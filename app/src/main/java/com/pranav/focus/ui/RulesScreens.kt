package com.pranav.focus.ui

import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.AppRule
import com.pranav.focus.data.Cond
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Store
import com.pranav.focus.data.UnlockRule
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.Earning
import com.pranav.focus.lock.Enforcer
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

private fun condIcon(c: Cond) = when (c) {
    Cond.RUN -> R.drawable.ic_directions_run
    Cond.RIDE -> R.drawable.ic_directions_bike
    Cond.WORKOUT -> R.drawable.ic_fitness_center
    Cond.FOCUS -> R.drawable.ic_adjust
    Cond.STUDY -> R.drawable.ic_menu_book
}

private fun fmtProgress(c: Cond, v: Double, target: Int): String {
    val cur = if (c == Cond.RUN || c == Cond.RIDE) "%.1f".format(v) else "${v.toInt()}"
    return "$cur / $target ${c.unit}"
}

@Composable
private fun ProgressRing(frac: Float, size: Int = 48, icon: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 4.dp.toPx()
            val inset = w / 2
            val sz = Size(this.size.width - w, this.size.height - w)
            drawArc(C.Track, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(C.White, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Ico(icon, 20.dp)
    }
}

// --------------------------------------------------------------------------------------
// A6 App Rules
// --------------------------------------------------------------------------------------
@Composable
fun RulesScreen(back: () -> Unit, onNewRule: () -> Unit, onStudy: () -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var tab by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf(store.appRules) }
    var unlocks by remember { mutableStateOf(store.unlockRules) }
    var during by remember { mutableStateOf(store.blockedDuring) }
    var always by remember { mutableStateOf(store.blockedAlways) }
    var chooser by remember { mutableStateOf<String?>(null) }
    val tick = AppState.dataTick

    fun saveRules(l: List<AppRule>) { rules = l; store.appRules = l }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("App Rules", back, "Control how apps behave and what they cost.")
        SegFull(listOf("Limits", "Unlock rules", "Blocked"), tab) { tab = it }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> {
                if (rules.isEmpty()) EmptyNote("No limits yet. Add an app: it stays locked until you earn time.")
                rules.forEach { r ->
                    val used = remember(tick, r.pkg) { com.pranav.focus.ui.UsageHelper.usedMin(ctx, r.pkg).toInt() }
                    FocusCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(r.pkg)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(appLabel(ctx, r.pkg), color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("${r.limitMin} min/day cap · ${r.baseMin} free base · used ${used}m", color = C.Text2, fontSize = 13.sp)
                            }
                            Ico(R.drawable.ic_delete, 22.dp, C.Text3, Modifier.clickable { saveRules(rules.filter { it.pkg != r.pkg }) }.padding(4.dp))
                        }
                        Slider(
                            value = r.limitMin.toFloat(), valueRange = 5f..180f, steps = 34,
                            onValueChange = { v -> saveRules(rules.map { if (it.pkg == r.pkg) it.copy(limitMin = v.toInt()) else it }) },
                            colors = SliderDefaults.colors(thumbColor = C.White, activeTrackColor = C.White, inactiveTrackColor = C.Track,
                                activeTickColor = C.White, inactiveTickColor = C.Track),
                        )
                        Text("Free daily base: ${r.baseMin} min (resets at local midnight)", color = C.Text2, fontSize = 12.sp)
                        Slider(
                            value = r.baseMin.toFloat(), valueRange = 0f..r.limitMin.toFloat().coerceAtLeast(1f), steps = 0,
                            onValueChange = { v -> saveRules(rules.map { if (it.pkg == r.pkg) it.copy(baseMin = (v / 5).toInt() * 5) else it }) },
                            colors = SliderDefaults.colors(thumbColor = C.White, activeTrackColor = C.White, inactiveTrackColor = C.Track),
                        )
                        if (r.pkg == com.pranav.focus.lock.FocusAccessibilityService.YT) {
                            Text("Study Exemptions  ›", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().clickable { onStudy() }.padding(vertical = 10.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Locked by default", color = C.White, fontSize = 15.sp)
                                Text("Earn minutes to unlock", color = C.Text3, fontSize = 12.sp)
                            }
                            FocusSwitch(r.locked) { on -> saveRules(rules.map { if (it.pkg == r.pkg) it.copy(locked = on) else it }) }
                        }
                    }
                }
                GhostButton("Add app", icon = R.drawable.ic_add) { chooser = "limit" }
            }
            1 -> {
                if (unlocks.isEmpty()) EmptyNote("No rules yet. Example: run 5 km, unlock Instagram for 15 min.")
                val day = remember(tick) { store.day() }
                unlocks.forEach { u ->
                    val prog = remember(tick, u.id) { Earning.progress(store, u.cond) }
                    val done = u.id in day.granted
                    FocusCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProgressRing((prog / u.target).toFloat(), 52, condIcon(u.cond))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(u.title(), color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                Text("Unlock ${appLabel(ctx, u.pkg)} for ${u.minutes} min", color = C.Text2, fontSize = 13.sp)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Chip(if (done) "Unlocked today" else fmtProgress(u.cond, prog, u.target))
                                    Chip("Verified · ${u.cond.source}")
                                }
                            }
                            AppIcon(u.pkg, 36.dp)
                            Ico(R.drawable.ic_delete, 22.dp, C.Text3, Modifier.clickable {
                                unlocks = unlocks.filter { it.id != u.id }; store.unlockRules = unlocks
                            }.padding(start = 8.dp))
                        }
                    }
                }
                PrimaryButton("New rule") { onNewRule() }
            }
            else -> {
                BlockList("Blocked while focusing", "These stay shut during any session.", during, { chooser = "during" }) {
                    during = during - it; store.blockedDuring = during
                }
                Spacer(Modifier.height(20.dp))
                BlockList("Always blocked", "Never usable. No time earns them back.", always, { chooser = "always" }) {
                    always = always - it; store.blockedAlways = always
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
    chooser?.let { which ->
        val exclude = when (which) { "limit" -> rules.map { it.pkg }.toSet(); "during" -> during; else -> always }
        AppChooserSheet(when (which) { "limit" -> "Add app"; "during" -> "Block while focusing"; else -> "Always block" }, exclude, { pkg ->
            when (which) {
                "limit" -> saveRules(rules + AppRule(pkg))
                "during" -> { during = during + pkg; store.blockedDuring = during }
                else -> { always = always + pkg; store.blockedAlways = always }
            }
        }) { chooser = null }
    }
}

@Composable
private fun EmptyNote(t: String) {
    Text(t, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun BlockList(title: String, sub: String, pkgs: Set<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val ctx = LocalContext.current
    Text(title, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Text(sub, color = C.Text2, fontSize = 13.sp)
    Spacer(Modifier.height(8.dp))
    pkgs.forEach { p ->
        FocusCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(p, 40.dp)
                Text(appLabel(ctx, p), color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Ico(R.drawable.ic_close, 22.dp, C.Text3, Modifier.clickable { onRemove(p) }.padding(4.dp))
            }
        }
    }
    GhostButton("Add app", icon = R.drawable.ic_add) { onAdd() }
}

// --------------------------------------------------------------------------------------
// A7 New rule
// --------------------------------------------------------------------------------------
private fun targetValues(c: Cond): List<Int> = when (c) {
    Cond.RUN, Cond.RIDE -> (1..50).toList()
    Cond.WORKOUT -> (1..5).toList()
    Cond.FOCUS -> (1..48).map { it * 5 }
    Cond.STUDY -> (1..30).map { it * 10 }
}

private fun defaultTarget(c: Cond) = when (c) { Cond.RUN, Cond.RIDE -> 5; Cond.WORKOUT -> 1; Cond.FOCUS -> 25; Cond.STUDY -> 60 }

@Composable
fun NewRuleScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var cond by remember { mutableStateOf(Cond.RUN) }
    var target by remember { mutableIntStateOf(5) }
    var pkg by remember { mutableStateOf<String?>(null) }
    var minutes by remember { mutableIntStateOf(15) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var midnight by remember { mutableStateOf(true) }
    val mins = remember { (1..24).map { it * 5 } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("New rule", back, "If you do this, you unlock that.")
        Caps("If")
        Spacer(Modifier.height(6.dp))
        FocusCard(Modifier.fillMaxWidth(), onClick = { sheet = "cond" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(condIcon(cond), active = true)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(cond.title, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(cond.sub, color = C.Text2, fontSize = 13.sp)
                }
                Ico(R.drawable.ic_chevron_right, 22.dp, C.Text3)
            }
        }
        Spacer(Modifier.height(10.dp))
        FocusCard(Modifier.fillMaxWidth(), onClick = { sheet = "target" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Target", color = C.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("$target ${cond.unit}", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Caps("Then unlock")
        Spacer(Modifier.height(6.dp))
        FocusCard(Modifier.fillMaxWidth(), onClick = { sheet = "app" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pkg != null) AppIcon(pkg!!) else IconTile(R.drawable.ic_grid_view)
                Text(pkg?.let { appLabel(ctx, it) } ?: "Choose an app", color = if (pkg != null) C.White else C.Text2, fontSize = 17.sp,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Ico(R.drawable.ic_chevron_right, 22.dp, C.Text3)
            }
        }
        Spacer(Modifier.height(10.dp))
        FocusCard(Modifier.fillMaxWidth(), onClick = { sheet = "mins" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("For", color = C.Text2, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("$minutes min", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(20.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.ic_check_circle, 24.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Verified by ${cond.source}", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Manual entries never count.", color = C.Text2, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Resets at midnight", color = C.White, fontSize = 15.sp)
                    Text("Unlocked time never carries over.", color = C.Text3, fontSize = 12.sp)
                }
                FocusSwitch(midnight) { midnight = true } // always on: the Time Bank rule is fixed
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Save rule", enabled = pkg != null) {
            val p = pkg ?: return@PrimaryButton
            store.unlockRules = store.unlockRules + UnlockRule(UUID.randomUUID().toString(), cond, target, p, minutes)
            if (store.appRules.none { it.pkg == p }) store.appRules = store.appRules + AppRule(p)
            Earning.evaluate(ctx)
            AppState.dataTick++
            back()
        }
        Spacer(Modifier.height(32.dp))
    }
    when (sheet) {
        "cond" -> FocusSheet({ sheet = null }) {
            Text("If you complete", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Cond.values().forEach { c ->
                FocusCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { cond = c; target = defaultTarget(c); sheet = null }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(condIcon(c), active = c == cond, size = 40.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.title, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(c.sub, color = C.Text2, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        "target" -> {
            val v = targetValues(cond)
            WheelSheet("Target (${cond.unit})", v.map { "$it" }, v.indexOf(target).coerceAtLeast(0), { target = v[it] }) { sheet = null }
        }
        "mins" -> WheelSheet("Unlock for (min)", mins.map { "$it" }, mins.indexOf(minutes).coerceAtLeast(0), { minutes = mins[it] }) { sheet = null }
        "app" -> AppChooserSheet("Unlock which app?", emptySet(), { pkg = it }) { sheet = null }
    }
}

// --------------------------------------------------------------------------------------
// A10 Time Bank
// --------------------------------------------------------------------------------------
@Composable
fun TimeBankScreen(back: () -> Unit, onRules: () -> Unit, onFocus: () -> Unit, onSetup: () -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val tick = AppState.dataTick + AppState.tick
    var bump by remember { mutableIntStateOf(0) }
    var help by remember { mutableStateOf(false) }
    val bank = remember(tick, bump) { store.bankMinutes }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = LocalDateTime.now() } }
    var topFor by remember { mutableStateOf<String?>(null) }
    val day = remember(tick, bump) { store.day() }
    val earned = store.ledger.earnedOn(store.today())
    val spent = store.ledger.spentOn(store.today())
    val penalty = store.ledger.penaltyOn(store.today())
    val left = run {
        val d = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay())
        "%02d:%02d:%02d".format(d.toHours(), d.toMinutes() % 60, d.seconds % 60)
    }
    val locked = remember(tick, bump) { store.appRules.filter { it.locked } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Time Bank", back, "Earn it. Then spend it.")
        if (!AppState.coreReady) {
            GateBanner("Rules can't block yet", "Turn on Accessibility and display-over-apps so limits and top-ups take effect.", "Fix", onSetup)
            Spacer(Modifier.height(12.dp))
        }
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Caps("Available now")
                    val shown by androidx.compose.animation.core.animateFloatAsState(bank.toFloat(), androidx.compose.animation.core.tween(450), label = "bank")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${shown.toInt()}", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold)
                        Text(" min", color = C.Text2, fontSize = 20.sp, modifier = Modifier.padding(bottom = 10.dp))
                    }
                }
                androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_stack), null, Modifier.size(width = 112.dp, height = 133.dp))
            }
            Spacer(Modifier.height(10.dp))
            val tot = earned.coerceAtLeast(1).toFloat()
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Track)) {
                Box(Modifier.fillMaxWidth((1f - spent / tot).coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(C.White))
            }
            Spacer(Modifier.height(8.dp))
            Text("Earned today ${earned}m · Top-ups ${spent}m" + if (penalty > 0) " · Penalty −${penalty}m" else "",
                color = C.Text2, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        val earnedBy = remember(tick, bump) {
            store.ledger.all().filter { it.day == store.today() && it.type == com.pranav.focus.data.LedgerType.EARN }
                .groupBy { it.ref }.mapValues { e -> e.value.sumOf { it.delta } }
        }
        val sourceLabel = mapOf("focus" to "Focus sessions", "run" to "Run goal", "workout" to "Workout goal", "study" to "Study goal", "study_q" to "Study questions")
        Spacer(Modifier.height(12.dp))
        SectionCard(pad = 14.dp, radius = 22.dp) {
            Caps("Earned today, by source")
            if (earnedBy.isEmpty()) {
                Text("Nothing yet. Focus sessions, a run, a workout or 60 study questions all earn minutes.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            } else earnedBy.entries.sortedByDescending { it.value }.forEach { (ref, mins) ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(sourceLabel[ref] ?: ref, color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("+$mins min", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        val wkFlags = remember(tick) { (6L downTo 0L).map { store.workoutsOn(com.pranav.focus.data.AppClock.today().minusDays(it).toString()) > 0 } }
        val wkDays = wkFlags.count { it }
        val wkMult = com.pranav.focus.data.ProgressionPolicy.consistencyMultiplier(wkDays)
        FocusCard(Modifier.fillMaxWidth()) {
            Caps("Workout consistency · last 7 days")
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$wkDays", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(" / 7 days with a workout", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                wkFlags.forEach { on -> Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (on) C.White else C.Track)) }
            }
            Text("Bonus on workout minutes and workout XP: ×${"%.2f".format(wkMult)}", color = C.White, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            Text("1 day ×1.00 · 2–3 days ×1.20 · 4–5 days ×1.35 · 6–7 days ×1.50", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Free allowances reset at midnight", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Saved minutes carry over", color = C.Text2, fontSize = 13.sp)
                }
                Text(left, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        UsageTotalsCard(tick + bump)
        Spacer(Modifier.height(20.dp))
        Text("Locked apps", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (locked.isEmpty()) EmptyNote("Nothing locked yet. Add apps in App Rules.")
        locked.forEach { r ->
            val rem = Enforcer.remainingMin(ctx, store, r.pkg)
            FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(r.pkg)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(appLabel(ctx, r.pkg), color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (rem > 0) "$rem min left today" else "Locked", color = C.Text2, fontSize = 13.sp)
                    }
                    val can = bank >= 1
                    Text("Top up", color = if (can) C.Black else C.Text3, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(CircleShape).background(if (can) C.White else C.Track)
                            .clickable(enabled = can) { topFor = r.pkg }
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Earn more", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), onClick = onFocus) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(R.drawable.ic_adjust, size = 44.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Finish a focus session", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("1 min per 5 min focused", color = C.Text2, fontSize = 13.sp)
                }
                Ico(R.drawable.ic_chevron_right, 22.dp, C.Text3)
            }
        }
        Earning.ACTIONS.forEach { a ->
            val prog = Earning.progress(store, a.cond)
            val done = a.id in day.actions
            FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing((prog / a.target).toFloat(), 44, condIcon(a.cond))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(UnlockRule("", a.cond, a.target, "", 0).title(), color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (done) "Earned today" else fmtProgress(a.cond, prog, a.target), color = C.Text2, fontSize = 13.sp)
                    }
                    Text("+${a.minutes}m", color = if (done) C.Text3 else C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Manage rules") { onRules() }
        Spacer(Modifier.height(8.dp))
        Text("How it works", color = C.Text2, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { help = true }.padding(12.dp))
        Spacer(Modifier.height(100.dp))
    }
    topFor?.let { pkg ->
        TopUpSheet(pkg, bank, onClose = { topFor = null; bump++; AppState.dataTick++ })
    }
    if (help) AlertDialog(
        onDismissRequest = { help = false },
        confirmButton = { TextButton({ help = false }) { Text("Got it", color = C.White) } },
        containerColor = C.Sheet,
        title = { Text("How the Time Bank works", color = C.White) },
        text = {
            Text("Locked apps stay shut until you earn time. Verified effort (focus sessions, runs, workouts, study) adds minutes. " +
                "Each app can have a free daily base that resets at local midnight; saved minutes carry over. When the base runs out you may confirm a top-up. Study time on approved YouTube channels never costs base or bank minutes. Missed Accountability Mirror items cost 10 minutes tomorrow. Manual entries never earn time.",
                color = C.Text2, fontSize = 14.sp)
        },
    )
}

// --------------------------------------------------------------------------------------
// Blocked screen
// --------------------------------------------------------------------------------------
/**
 * Spend any amount from the Time Bank on one app. Nothing is charged until the user confirms; the ledger reservation
 * is the grant. [onSpent] runs only after a confirmed spend, [onClose] after either outcome.
 */
@Composable
fun TopUpSheet(pkg: String, bank: Int, onClose: () -> Unit, onSpent: (Int) -> Unit = {}) {
    val ctx = LocalContext.current
    val reqId = remember(pkg) { java.util.UUID.randomUUID().toString() }
    // Kept as text so the user can type any amount; the slider and quick picks write into the same field.
    var txt by remember(pkg) { mutableStateOf(minOf(10, bank).coerceAtLeast(1).toString()) }
    val amt = txt.toIntOrNull() ?: 0
    val valid = amt in 1..bank
    fun setAmt(m: Int) { txt = m.coerceIn(1, maxOf(bank, 1)).toString() }
    FocusSheet(onClose) {
        Text("Top up ${appLabel(ctx, pkg)}", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Nothing is spent until you confirm. Unused minutes are refunded at midnight.", color = C.Text2, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        if (bank <= 0) {
            Text("Your Time Bank is empty. Focus, run, work out or study to earn minutes.", color = C.Text2, fontSize = 14.sp)
        } else {
            OutlinedTextField(
                value = txt,
                onValueChange = { v -> txt = v.filter { it.isDigit() }.take(4) },
                label = { Text("Minutes (1–$bank)") },
                singleLine = true,
                isError = txt.isNotEmpty() && !valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.pranav.focus.data.Allowance.topUpOptions(bank).forEach { m -> Pill("$m min", filled = amt == m) { setAmt(m) } }
            }
            if (bank > 1) Slider(
                value = amt.coerceIn(1, bank).toFloat(), valueRange = 1f..bank.toFloat(), steps = bank - 2,
                onValueChange = { v -> setAmt((v + 0.5f).toInt()) },
                modifier = Modifier.padding(top = 8.dp),
                colors = SliderDefaults.colors(thumbColor = C.White, activeTrackColor = C.White, inactiveTrackColor = C.Track),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (valid) "Spend $amt min ($bank → ${bank - amt})" else "Enter 1–$bank min", enabled = valid) {
                Earning.unlockFromPool(ctx, pkg, amt, reqId)
                onSpent(amt); onClose()
            }
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Cancel") { onClose() }
    }
}

@Composable
fun BlockedScreen(pkg: String, onFocus: () -> Unit, onBank: () -> Unit, onHome: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val always = pkg in store.blockedAlways
    val hasRule = store.appRules.any { it.pkg == pkg }
    val sessionBlock = store.sessionActive && pkg in store.blockedDuring
    // A top-up can only lift a time limit. It cannot lift an always-block or a focus-session block.
    val canTopUp = hasRule && !always && !sessionBlock
    var topUp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        AppIcon(pkg, 72.dp)
        Spacer(Modifier.height(20.dp))
        Text(appLabel(ctx, pkg), color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(if (always) "Always blocked." else "Locked. Earn time to open it.", color = C.Text2, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("${store.bankMinutes} min in your Time Bank", color = C.Text3, fontSize = 13.sp)
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Start focus session") { onFocus() }
        Spacer(Modifier.height(10.dp))
        if (canTopUp && store.bankMinutes > 0) {
            PrimaryButton("Top up ${appLabel(ctx, pkg)}") { topUp = true }
            Spacer(Modifier.height(10.dp))
        }
        if (!always) { GhostButton("Open Time Bank") { onBank() }; Spacer(Modifier.height(10.dp)) }
        GhostButton("Go home") { onHome() }
    }
    if (topUp) TopUpSheet(pkg, store.bankMinutes, onClose = { topUp = false }, onSpent = {
        // Enforcer now allows the app, so hand the user straight back to it.
        AppState.blockedPkg = null
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { i ->
            runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    })
}
