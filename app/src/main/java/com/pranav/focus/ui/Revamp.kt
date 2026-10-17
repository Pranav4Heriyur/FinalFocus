package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Entry
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Ranks
import com.pranav.focus.data.Rewards
import com.pranav.focus.data.Store
import com.pranav.focus.data.Weekly
import com.pranav.focus.lock.AppState
import com.pranav.focus.net.Notion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------------------------------
// shared bits
// ---------------------------------------------------------------------------------------
@Composable
private fun BackCircle(onBack: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
        Ico(R.drawable.ic_arrow_back, 20.dp)
    }
}

@Composable
private fun Eyebrow(t: String) = Text(t.uppercase(), color = C.Text2, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)

/** Animated pill switch (thumb slide 240 ms, surface lights up). */
@Composable
fun RSwitch(on: Boolean, locked: Boolean = false, onChange: (Boolean) -> Unit) {
    val rm = reduceMotion()
    val x = remember { Animatable(if (on) 1f else 0f) }
    LaunchedEffect(on) { x.animateTo(if (on) 1f else 0f, tween(if (rm) 0 else 240, easing = FastOutSlowInEasing)) }
    val track by animateColorAsState(if (on) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f), tween(240), label = "t")
    Box(
        Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(track).border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable(enabled = !locked) { onChange(!on) },
    ) {
        Box(Modifier.offset(x = (4 + 20 * x.value).dp, y = 4.dp).size(20.dp).clip(CircleShape).background(if (locked) Color.White.copy(alpha = 0.55f) else Color.White))
    }
}

@Composable
private fun reduceMotionNow(): Boolean = reduceMotion()

private fun Modifier.rowCard(r: Dp = 18.dp): Modifier = this.fillMaxWidth().glass(r)

// ---------------------------------------------------------------------------------------
// A11 Accountability Mirror
// ---------------------------------------------------------------------------------------
@Composable
fun MirrorScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    PlanState.load(ctx)
    val today = AppClock.today()
    val tasks = PlanState.forDate(today)           // the agenda IS the Daily Planner
    var submitted by remember { mutableStateOf(store.reviewSubmitted()) }
    var avoided by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val missed = tasks.count { !it.done }
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(missed) { pulse.snapTo(0.35f); pulse.animateTo(0f, tween(260)) }

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_moon), null, Modifier.align(Alignment.TopEnd).offset(y = 60.dp).size(width = 149.dp, height = 154.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BackCircle(back); Spacer(Modifier.weight(1f)); Text("9:00 PM", color = C.Text2, fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
            Spacer(Modifier.height(26.dp))
            Reveal(0) {
                Column {
                    Eyebrow("Nightly review")
                    Spacer(Modifier.height(8.dp))
                    Text("Face the day.", color = C.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Be honest. This is for a better you tomorrow.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.width(250.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text("Today’s agenda", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(today.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)), color = C.Text3, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (tasks.isEmpty()) Text("Nothing planned for today. Add tasks in Plan and they appear here.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                tasks.forEachIndexed { i, t ->
                    Reveal(i + 1) {
                        Row(Modifier.rowCard(16.dp).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Sheet), contentAlignment = Alignment.Center) { Ico(agendaIconRes(t.subject), 20.dp) }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(t.title, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${fmtT(t.start).trimStart('0')} · ${fmtD(t.dur)}", color = C.Text2, fontSize = 11.sp, maxLines = 1)
                            }
                            StatePill("Done", t.done, !submitted) { PlanState.upsert(ctx, t.copy(done = true)) }
                            Spacer(Modifier.width(6.dp))
                            StatePill("Not done", !t.done, !submitted) { PlanState.upsert(ctx, t.copy(done = false)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.rowCard(16.dp).background(Color.White.copy(alpha = pulse.value * 0.25f)).padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).border(1.dp, C.Text3, CircleShape), contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_warning, 18.dp) }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(if (missed == 0) "Nothing missed." else "Missed $missed item${if (missed == 1) "" else "s"}: −${missed * Rewards.MISSED_ITEM_PENALTY_MIN} min tomorrow",
                        color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (missed == 0) "Keep it that way." else "Be consistent. Your future self is watching.", color = C.Text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("One thing you avoided today", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Be honest with yourself. Write it down.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            Box(Modifier.fillMaxWidth().height(100.dp).rowCard(16.dp).padding(14.dp)) {
                if (avoided.isEmpty()) Text("E.g. I avoided starting the Chemistry question set…", color = C.Text3, fontSize = 13.sp)
                BasicTextField(avoided, { if (it.length <= 300 && !submitted) avoided = it }, Modifier.fillMaxSize(), textStyle = TextStyle(color = C.White, fontSize = 14.sp), cursorBrush = SolidColor(C.White))
            }
            Text("${avoided.length}/300", color = C.Text3, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
            Spacer(Modifier.height(10.dp))
            if (submitted) Text("Review submitted for today.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            else PrimaryButton("Face it") { confirm = true }
            Text("Your review is private and saved on this device.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = TextAlign.Center)
        }
    }
    if (confirm) FocusSheet({ confirm = false }) {
        Text("Submit tonight’s review?", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(if (missed == 0) "Everything done. +${tasks.size * Rewards.REVIEW_ITEM_XP} XP." else "$missed missed = −${missed * Rewards.MISSED_ITEM_PENALTY_MIN} min from tomorrow’s Time Bank. It can’t be changed after.", color = C.Text2, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Face it") {
            val tomorrow = today.plusDays(1).toString()
            val d = store.day(tomorrow)
            d.penalty += missed * Rewards.MISSED_ITEM_PENALTY_MIN
            store.saveDay(d, tomorrow)
            store.awardXp("review:${today}", "review", (tasks.size - missed) * Rewards.REVIEW_ITEM_XP)
            if (avoided.isNotBlank()) store.avoided = listOf(Entry(UUID.randomUUID().toString(), System.currentTimeMillis(), avoided.trim(), "")) + store.avoided
            store.putReviewSubmitted(); submitted = true; confirm = false
            AppState.dataTick++
        }
        Spacer(Modifier.height(8.dp))
        GhostButton("Not yet") { confirm = false }
    }
}

@Composable
private fun StatePill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected && label == "Done") C.White else if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent, tween(200), label = "p")
    Box(
        Modifier.height(32.dp).clip(CircleShape).background(bg)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(enabled = enabled) { onClick() }.padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected && label == "Done") C.Black else if (selected) C.White else C.Text2, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

// ---------------------------------------------------------------------------------------
// A12 Cookie Jar
// ---------------------------------------------------------------------------------------
@Composable
fun JarScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var items by remember { mutableStateOf(store.jar) }
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Entry?>(null) }
    val zone = java.time.ZoneId.systemDefault()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BackCircle(back); Spacer(Modifier.weight(1f))
            Row(Modifier.height(40.dp).clip(CircleShape).background(C.White).clickable { adding = true }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.p1_plus, 16.dp, C.Black); Text("  Add", color = C.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Cookie jar")
                Spacer(Modifier.height(8.dp))
                Text("The hardest things you’ve done.", color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(8.dp))
                Text("Remember this when you want to quit.", color = C.Text2, fontSize = 13.sp)
            }
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_jar), null, Modifier.size(width = 120.dp, height = 133.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (items.isEmpty()) Text("Empty. Add your first win.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { i, e ->
                Reveal(i.coerceAtMost(10)) {
                    val d = java.time.Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalDate()
                    Row(Modifier.rowCard(16.dp).clickable { open = e }.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(48.dp)) {
                            Text(d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase(), color = C.Text2, fontSize = 10.sp, lineHeight = 12.sp)
                            Text(d.year.toString(), color = C.Text3, fontSize = 10.sp)
                        }
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Sheet).border(1.dp, C.Hairline, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_star, 20.dp) }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(e.title, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (e.note.isNotBlank()) Text(e.note, color = C.Text2, fontSize = 11.sp, maxLines = 1)
                        }
                        Ico(R.drawable.p1_chevron_right, 16.dp, C.White)
                    }
                }
            }
        }
    }
    open?.let { e ->
        FocusSheet({ open = null }) {
            Text(e.title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (e.note.isNotBlank()) Text(e.note, color = C.Text2, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            Text(java.time.Instant.ofEpochMilli(e.dateMs).atZone(zone).toLocalDate().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)), color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(16.dp))
            GhostButton("Remove from jar") { items = items.filter { it.id != e.id }; store.jar = items; open = null }
        }
    }
    if (adding) {
        var t by remember { mutableStateOf("") }
        var n by remember { mutableStateOf("") }
        FocusSheet({ adding = false }) {
            Text("New win", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            SheetField(t, "What did you do?", singleLine = true) { t = it }
            Spacer(Modifier.height(8.dp))
            SheetField(n, "Why it mattered", singleLine = false) { n = it }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Save", enabled = t.isNotBlank()) {
                items = listOf(Entry(UUID.randomUUID().toString(), System.currentTimeMillis(), t.trim(), n.trim())) + items
                store.jar = items; adding = false
            }
        }
    }
}

@Composable
private fun SheetField(v: String, hint: String, singleLine: Boolean, onChange: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(14.dp)) {
        if (v.isEmpty()) Text(hint, color = C.Text3, fontSize = 15.sp)
        BasicTextField(v, onChange, singleLine = singleLine, textStyle = TextStyle(color = C.White, fontSize = 15.sp), cursorBrush = SolidColor(C.White))
    }
}

// ---------------------------------------------------------------------------------------
// A13 Rank
// ---------------------------------------------------------------------------------------
@Composable
fun RankScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    remember { Weekly.rollover(store) }
    val xp = store.xp
    val cur = Ranks.forXp(xp)
    val nxt = Ranks.next(xp)
    val w = remember { Weekly.stats(store, Weekly.weekStart()) }
    val t = Weekly.target(store.targetScale, Weekly.focusTarget(store))
    var sel by remember { mutableIntStateOf(Ranks.ALL.indexOf(cur)) }
    val glowA = remember { Animatable(0.08f) }
    LaunchedEffect(Unit) { glowA.animateTo(0.20f, tween(350)); glowA.animateTo(0.08f, tween(350)) }
    val icons = listOf(R.drawable.rk_mountain, R.drawable.p1_calories_flame, R.drawable.plan_ic_star, R.drawable.rk_bolt, R.drawable.p1_stats_bars, R.drawable.rk_crown, R.drawable.plan_ic_star)
    val ws = Weekly.weekStart()
    val bar = rememberSweep(Ranks.progress(xp), 1000)

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_summit), null, Modifier.align(Alignment.TopEnd).offset(y = 47.dp).size(width = 132.dp, height = 127.dp), alpha = (0.88f + glowA.value).coerceAtMost(1f))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { BackCircle(back); Spacer(Modifier.width(12.dp)); Eyebrow("Your rank") }
            Spacer(Modifier.height(10.dp))
            Text(cur.name.uppercase(), color = C.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, style = TextStyle(shadow = GlowShadow))
            Text(cur.tagline, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(36.dp))
            Row(Modifier.rowCard(18.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) { Text("$xp", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(" / ${nxt?.minXp ?: xp} XP", color = C.Text2, fontSize = 12.sp) }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(bar).fillMaxHeight().background(C.White)) }
                }
                Column(Modifier.padding(start = 16.dp), horizontalAlignment = Alignment.End) {
                    Text("Next rank", color = C.Text2, fontSize = 11.sp)
                    Text((nxt?.name ?: "Top").uppercase(), color = C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicHeightHack), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.8f).glass(24.dp).padding(8.dp)) {
                    Ranks.ALL.forEachIndexed { i, r ->
                        val on = i == sel
                        val reached = xp >= r.minXp
                        Row(
                            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                                .border(1.dp, if (on) Color.White.copy(alpha = 0.85f) else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { sel = i }.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).border(1.dp, C.Text3, CircleShape), contentAlignment = Alignment.Center) { if (r == cur || reached) Box(Modifier.size(7.dp).clip(CircleShape).background(if (r == cur) C.White else C.Text3)) }
                            Spacer(Modifier.width(10.dp))
                            Ico(icons[i], 20.dp, if (reached) C.White else C.Text3)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(r.name.uppercase(), color = if (reached) C.White else C.Text3, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Text(r.tagline, color = C.Text2, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight().glass(24.dp)) {
                    androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_peaks), null, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(119.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Column(Modifier.padding(14.dp)) {
                        Text("“Discipline today builds a version of you most people will never know.”", color = C.White, fontSize = 17.sp, lineHeight = 23.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.height(14.dp))
                        Box(Modifier.width(24.dp).height(1.dp).background(C.Text2))
                        Spacer(Modifier.height(12.dp))
                        Text("HIGHER STANDARDS. A CALMER YOU.", color = C.Text2, fontSize = 8.sp, letterSpacing = 1.5.sp, lineHeight = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.rowCard(24.dp).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This week’s targets", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${ws.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))} – ${ws.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}", color = C.Text3, fontSize = 10.sp)
                }
                Spacer(Modifier.height(8.dp))
                TargetRow(R.drawable.p1_clock, "Focus for ${Fmt.hm(t.focusMin)}", "${Fmt.hm(w.focusMin)} / ${Fmt.hm(t.focusMin)}", w.focusMin.toFloat() / t.focusMin)
                TargetRow(R.drawable.p1_run_sprinter, "Run ${"%.0f".format(t.runKm)} km", "${"%.1f".format(w.runKm)} / ${"%.0f".format(t.runKm)} km", (w.runKm / t.runKm).toFloat())
                TargetRow(R.drawable.p1_dumbbell, "Workout on ${t.workoutDays} days", "${w.workoutDays} / ${t.workoutDays} days", w.workoutDays.toFloat() / t.workoutDays)
                TargetRow(R.drawable.p1_study_book, "Solve ${t.questions} questions", "${w.questions} / ${t.questions}", w.questions.toFloat() / t.questions)
                Text(if (store.targetScale > 1f) "Targets scaled to ${(store.targetScale * 100).toInt()}%." else "Hit all four and next week’s targets rise 5%.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

private val IntrinsicHeightHack = 390.dp

@Composable
private fun TargetRow(icon: Int, label: String, value: String, frac: Float) {
    val f = rememberSweep(frac.coerceIn(0f, 1f), 1000)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Sheet).border(1.dp, C.Hairline, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Ico(icon, 18.dp) }
            Text(label, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1)
            Text(value, color = C.Text2, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(C.White)) }
    }
}

// ---------------------------------------------------------------------------------------
// A17 Security
// ---------------------------------------------------------------------------------------
@Composable
fun SecurityScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var bio by remember { mutableStateOf(store.biometricLock) }
    var exit by remember { mutableStateOf(store.lockOnExit) }
    var recents by remember { mutableStateOf(store.hideInRecents) }
    var auto by remember { mutableIntStateOf(when (store.autoLockSeconds) { 0 -> 0; 30 -> 1; 300 -> 2; else -> 3 }) }
    var pick by remember { mutableStateOf(false) }
    var tokens by remember { mutableStateOf(false) }
    val tick = AppState.dataTick
    val can = remember { canLockDevice(ctx) }
    val nOn = remember(tick) { Notion.configured(ctx) }
    val autoLabel = listOf("Now", "30 seconds", "5 minutes", "Never")

    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.art_shield), null, Modifier.align(Alignment.TopEnd).offset(y = 71.dp).size(width = 172.dp, height = 175.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp)) {
            BackCircle(back)
            Spacer(Modifier.height(22.dp))
            Eyebrow("Security")
            Spacer(Modifier.height(8.dp))
            Text("Your data.\nYour discipline.", color = C.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            Spacer(Modifier.height(8.dp))
            Text("Built to keep you focused, private and in control.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.width(210.dp))
            Spacer(Modifier.height(40.dp))
            Column(Modifier.rowCard(24.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                SecRow(R.drawable.ic_fingerprint, "Biometric lock", if (can) "Use fingerprint or screen lock to open the app" else "Set a screen lock in Android settings first") { RSwitch(bio, !can && !bio) { bio = it; store.biometricLock = it } }
                SecDivider()
                SecRow(R.drawable.ic_lock, "Lock app on exit", "Require biometric to reopen") { RSwitch(exit, !bio) { exit = it; store.lockOnExit = it } }
                SecDivider()
                SecRow(R.drawable.ic_visibility_off, "Hide in recents", "Blank preview in the app switcher") { RSwitch(recents) { recents = it; store.hideInRecents = it } }
                SecDivider()
                SecRow(R.drawable.ic_database, "Encrypt data", "All data is encrypted on device", chip = "Always on") {
                    Box(Modifier.size(width = 48.dp, height = 28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) { Ico(R.drawable.ic_lock, 14.dp, C.Text2) }
                }
                SecDivider()
                SecRow(R.drawable.p1_clock, "Auto-lock after", "Lock the app when inactive") {
                    Row(Modifier.height(32.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape).clickable { pick = true }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(autoLabel[auto], color = C.White, fontSize = 12.sp); Ico(R.drawable.p1_chevron_right, 14.dp, C.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.rowCard(24.dp).clickable { tokens = true }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.Sheet), contentAlignment = Alignment.Center) { Ico(R.drawable.ic_key, 20.dp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Connected tokens", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Stored in secure hardware", color = C.Text2, fontSize = 12.sp)
                    }
                    Ico(R.drawable.p1_chevron_right, 18.dp)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hairline))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(R.drawable.ic_shield to "Stored in\nsecure hardware", R.drawable.ic_shield to "Never shared\nwith third parties", R.drawable.ic_database to "Minimal logs\n(on your device)").forEach { (ic, tx) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Ico(ic, 18.dp); Text(tx, color = C.Text2, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
    if (pick) FocusSheet({ pick = false }) {
        Text("Auto-lock after", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        autoLabel.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                auto = i; store.autoLockSeconds = when (i) { 0 -> 0; 1 -> 30; 2 -> 300; else -> -1 }; pick = false
            }.padding(vertical = 14.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l, color = C.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (i == auto) Ico(R.drawable.p1_checkmark, 20.dp)
            }
        }
    }
    if (tokens) FocusSheet({ tokens = false }) {
        Text("Connected tokens", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Encrypted with a key that never leaves the phone’s secure hardware.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Notion", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text(if (nOn) "Token stored" else "Not connected", color = C.Text2, fontSize = 13.sp) }
            if (nOn) Text("Revoke", color = C.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clip(CircleShape).background(C.White).clickable { Notion.disconnect(ctx); AppState.dataTick++ }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun SecDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hairline))

@Composable
private fun SecRow(icon: Int, title: String, sub: String, chip: String? = null, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(65.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Sheet), contentAlignment = Alignment.Center) { Ico(icon, 20.dp) }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (chip != null) Text(chip, color = C.White, fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp))
            }
            Text(sub, color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
        }
        trailing()
    }
}
