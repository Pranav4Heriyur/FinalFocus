package com.pranav.focus.workout

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Green = Color(0xFF34C759)
private fun n0(v: Double) = NumberFormat.getIntegerInstance().format(Math.round(v))
private fun kgS(v: Double) = if (v == Math.floor(v)) NumberFormat.getIntegerInstance().format(v.toLong()) else String.format(Locale.getDefault(), "%.1f", v)
private fun durS(min: Long) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
private fun fmt(ms: Long, p: String) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(p, Locale.getDefault()))

// ------------------------------------------------------------------------------------------------ history list

@Composable
fun WorkoutHistoryScreen(back: () -> Unit) {
    val repo = rememberRepo()
    var retry by remember { mutableIntStateOf(0) }
    val data by produceState<Result<List<HistoryItem>>?>(null, retry) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { WorkoutHistory.items(repo.statsSessions(), repo.statsSets()) } }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var newest by rememberSaveable { mutableStateOf(true) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Column(Modifier.fillMaxSize().background(C.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundBtn("Back", R.drawable.ic_arrow_back, back)
            Text("Workout History", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            RoundBtn("Workout statistics", wkIco("chart")) { WorkoutNav.openStats?.invoke() }   // C12 -> C11 (diagram); C11 -> C12 already exists, the stack never loops
        }
        val r = data
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading history…", color = C.Text2, fontSize = 15.sp) }
            r.isFailure -> Column(Modifier.padding(20.dp)) { WkCard {
                Text("Couldn't load your history", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Your workouts are safe. ${r.exceptionOrNull()?.message.orEmpty()}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                WkButton("Retry", Modifier.fillMaxWidth(), primary = true) { retry++ }
            } }
            else -> {
                val all = r.getOrThrow()
                if (all.isEmpty()) Column(Modifier.padding(20.dp)) { WkCard(radius = 22.dp) {
                    Text("No saved workouts yet", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Finish a workout and it will appear here with its full breakdown.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                } }
                else {
                    val shown = remember(all, query, newest) { WorkoutHistory.filter(all, query, newest) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(wkIco("search"), 20.dp, C.Text2); Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search workouts or exercises", color = C.Text3, fontSize = 15.sp)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = C.White, fontSize = 15.sp), cursorBrush = SolidColor(C.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search workouts or exercises" })
                        }
                        if (query.isNotEmpty()) Box(Modifier.size(48.dp).clickable(role = Role.Button) { query = "" }.semantics { contentDescription = "Clear search" }, contentAlignment = Alignment.Center) { Ico(wkIco("close"), 18.dp, C.Text2) }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${shown.size} ${if (shown.size == 1) "workout" else "workouts"}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(if (newest) "Newest first" else "Oldest first", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { newest = !newest }.padding(horizontal = 8.dp).wrapContentHeight(Alignment.CenterVertically))
                    }
                    if (shown.isEmpty()) Text("No workouts match \"${query.trim()}\".", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(shown, key = { it.id }) { h -> HistoryRow(h) }
                        item { Spacer(Modifier.navigationBarsPadding()) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(h: HistoryItem) {
    WkCard(radius = 20.dp, onClick = { WorkoutNav.detailSessionId = h.id; WorkoutNav.openWorkoutDetails?.invoke(h.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(h.title, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(fmt(h.startedAt, "EEE, d MMM yyyy • h:mm a"), color = C.Text2, fontSize = 12.sp)
            }
            Ico(wkIco("chevron-right"), 18.dp, C.Text2)
        }
        Text(
            listOfNotNull(durS(h.minutes), "${h.exercises} ${if (h.exercises == 1) "exercise" else "exercises"}", h.sets.takeIf { it > 0 }?.let { "$it sets" },
                h.volumeKg.takeIf { it > 0 }?.let { "${n0(it)} kg" }, h.distanceKm.takeIf { it > 0 && h.activity != "gym" }?.let { String.format(Locale.getDefault(), "%.1f km", it) }).joinToString(" • "),
            color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ------------------------------------------------------------------------------------------------ detail

@Composable
fun WorkoutDetailScreen(back: () -> Unit) {
    val repo = rememberRepo()
    val id = WorkoutNav.detailSessionId
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<Result<SessionDetail>?>(null, id, retry) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching { WorkoutHistory.load(repo, id) } }
    }
    Column(Modifier.fillMaxSize().background(C.Black)) {
        val r = state
        when {
            r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading workout…", color = C.Text2, fontSize = 15.sp) }
            r.isFailure -> Column(Modifier.statusBarsPadding().padding(20.dp)) { WkCard {
                Text("Couldn't open this workout", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(r.exceptionOrNull()?.message.orEmpty(), color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                WkButton("Retry", Modifier.fillMaxWidth(), primary = true) { retry++ }; Spacer(Modifier.height(8.dp)); WkButton("Back", Modifier.fillMaxWidth(), onClick = back)
            } }
            else -> DetailContent(r.getOrThrow(), back)
        }
    }
}

@Composable
private fun ColumnScope.DetailContent(d: SessionDetail, back: () -> Unit) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val big = LocalDensity.current.fontScale > 1.15f
    val whenText = fmt(d.startedAt, "d MMM yyyy • h:mm a")
    Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Hero(d, big, back) }
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Summary", "Exercises", "Charts").forEachIndexed { i, t ->
                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(if (tab == i) C.White else Color.Transparent).clickable(role = Role.Tab) { tab = i }
                            .semantics { contentDescription = "$t tab" + if (tab == i) ", selected" else "" }, contentAlignment = Alignment.Center) { Text(t, color = if (tab == i) C.Black else C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
                    }
                }
            }
            when (tab) {
                0 -> summary(d)
                1 -> exercisesTab(d)
                else -> chartsTab(d)
            }
        }
    }
    Row(Modifier.fillMaxWidth().background(C.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WkButton("Share", Modifier.weight(1f), icon = wkIco("share"), height = 56.dp) {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, d.title).putExtra(Intent.EXTRA_TEXT, WorkoutHistory.shareText(d, whenText))
            try { ctx.startActivity(Intent.createChooser(send, "Share workout")) } catch (e: Exception) { Toast.makeText(ctx, "Couldn't open the share sheet.", Toast.LENGTH_SHORT).show() }
        }
        WkButton("View in Calendar", Modifier.weight(1f), primary = true, icon = wkIco("calendar"), height = 56.dp) {
            // Opens the user's calendar app at the workout's date. It does not create an event and needs no permission.
            val uri = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), d.startedAt).build()
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e: ActivityNotFoundException) { Toast.makeText(ctx, "No calendar app is available on this device.", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(ctx, "Couldn't open the calendar.", Toast.LENGTH_SHORT).show() }
        }
    }
}

@Composable
private fun Hero(d: SessionDetail, big: Boolean, back: () -> Unit) {
    val cover = d.routineId?.let { WorkoutImages.routineCover(it) }
    Column {
        Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(250.dp).background(C.Card)) {
                if (cover != null) Image(painterResource(cover), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), C.Black))))
            }
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                RoundBtn("Back", R.drawable.ic_arrow_back, back)
                Spacer(Modifier.height(48.dp))
                Text(fmt(d.startedAt, "d MMM yyyy • h:mm a").uppercase(), color = C.Text2, fontSize = 12.sp, letterSpacing = 1.sp)
                Text(d.title, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(d.subtitle, color = C.Text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val metrics = buildList {
            WorkoutTiming.metrics(d.timing).forEach { (label, v) -> add(Triple("timer", v, label)) }
            add(Triple("layers", d.exercises.size.toString(), "Exercises"))
            if (d.isGym) add(Triple("check", d.workingSets.toString(), "Sets"))
            if (d.volumeKg > 0) add(Triple("dumbbell", "${n0(d.volumeKg)} kg", "Volume"))
            if (!d.isGym && d.distanceKm > 0) add(Triple("run", String.format(Locale.getDefault(), "%.1f km", d.distanceKm), "Distance"))
        }
        WkCard(Modifier.padding(horizontal = 16.dp), radius = 22.dp) {
            if (d.isGym && !d.timing.tracked && d.timing.elapsedMs != null) Text("Duration is the stored value for this older workout. Active training time wasn't recorded, so it isn't shown.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            else if (d.timing.tracked) Text("Elapsed = start to finish including pauses and rests. Active = elapsed minus pauses and rests run with the rest timer (gaps between sets are not counted as rest).", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            (if (big) metrics.chunked(2) else listOf(metrics)).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (ic, v, l) ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "$l $v" }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Ico(wkIco(ic), 18.dp, C.Text2); Text(v, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)); Text(l, color = C.Text2, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.summary(d: SessionDetail) {
    // volume
    item {
        WkCard(Modifier.padding(horizontal = 16.dp), radius = 22.dp) {
            Text("Volume", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val withVol = d.exercises.filter { it.volumeKg > 0 }
            if (withVol.size < 2) Text(if (d.volumeKg > 0) "${n0(d.volumeKg)} kg total. A breakdown needs at least two weighted exercises." else "No weighted sets in this workout, so there is no volume to show.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            else {
                Text("${n0(d.volumeKg)} kg", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                HBars(withVol.map { Triple(it.name, it.volumeKg, "${n0(it.volumeKg)} kg") })
            }
        }
    }
    // muscles
    item {
        WkCard(Modifier.padding(horizontal = 16.dp), radius = 22.dp) {
            Text("Muscle Groups", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (d.muscles.isEmpty()) Text("No sets with a mappable muscle group.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            else {
                val max = d.muscles.maxOf { it.share }.toFloat(); val heat = d.muscles.associate { it.key to it.share.toFloat() / max }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(0.9f), horizontalArrangement = Arrangement.spacedBy(4.dp)) { AnatomyBody(true, heat, Modifier.weight(1f).aspectRatio(100f / 210f)); AnatomyBody(false, heat, Modifier.weight(1f).aspectRatio(100f / 210f)) }
                    Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.muscles.take(8).forEach { m ->
                            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${m.label} ${Math.round(m.share * 100)} percent of sets" }) {
                                Row { Text(m.label, color = C.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)); Text("${Math.round(m.share * 100)}%", color = C.Text2, fontSize = 12.sp) }
                                Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(m.share.toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(C.White)) }
                            }
                        }
                    }
                }
            }
            Text("Share of this workout's completed working sets, split across each exercise's primary muscles (" + (if (d.historical) "as recorded when the workout was saved" else "read from the current exercise records, because this workout was saved before muscle data was recorded") + "). Not measured activation." + if (d.unmappedSets > 0) " ${d.unmappedSets} sets could not be mapped and are excluded." else "", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
    // PRs
    item {
        WkCard(Modifier.padding(horizontal = 16.dp), radius = 22.dp, padding = 12.dp) {
            Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(wkIco("trophy"), 20.dp); Text("Personal Records", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                if (d.prs.isNotEmpty()) Text(d.prs.size.toString(), color = C.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).background(C.Sheet).padding(horizontal = 9.dp, vertical = 3.dp))
            }
            if (d.prs.isEmpty()) Text("No verified records in this workout. A record needs an earlier session of the same lift to beat.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(4.dp))
            if (d.isGym && d.prsRecomputed) Text("Saved before records were stored: shown only where earlier saved sets prove them; none are guessed.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            d.prs.forEach { p ->
                Row(Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnatomyThumb(p.muscle, 52.dp); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(p.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text("Heaviest weight", color = C.Text2, fontSize = 12.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("${kgS(p.newKg)} kg" + (p.newReps?.let { " × $it" } ?: ""), color = C.White, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text("▲ +${kgS(p.deltaKg)} kg", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
    // breakdown
    item { Text("EXERCISE BREAKDOWN", color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 20.dp)) }
    items(d.exercises, key = { "b" + it.exerciseId }) { e ->
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Hairline, RoundedCornerShape(16.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnatomyThumb(e.muscle, 52.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(e.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(exTotals(e, d.isGym), color = C.Text2, fontSize = 13.sp) }
        }
    }
}

private fun exTotals(e: ExerciseDetail, gym: Boolean): String = listOfNotNull(
    if (gym) "${e.workingSets} ${if (e.workingSets == 1) "set" else "sets"}" else null, e.volumeKg.takeIf { it > 0 }?.let { "${n0(it)} kg total" },
    if (e.volumeKg <= 0 && e.totalReps > 0) "${e.totalReps} reps" else null, e.totalDurationS.takeIf { it > 0 }?.let { "${it / 60}m ${it % 60}s" }, e.totalDistanceM.takeIf { it > 0 }?.let { "${n0(it)} m" },
).joinToString(" • ")

private fun setText(r: SetRow): String {
    val main = when {
        (r.loadKg ?: 0.0) > 0 && r.reps != null -> "${kgS(r.loadKg!!)} kg × ${r.reps}"
        (r.loadKg ?: 0.0) > 0 && r.durationS != null -> "${kgS(r.loadKg!!)} kg • ${r.durationS} s"
        (r.loadKg ?: 0.0) > 0 && r.distanceM != null -> "${kgS(r.loadKg!!)} kg • ${n0(r.distanceM)} m"
        r.reps != null -> "Bodyweight × ${r.reps}"
        r.durationS != null && r.distanceM != null -> "${n0(r.distanceM)} m in ${r.durationS / 60}m ${r.durationS % 60}s"
        r.durationS != null -> "${r.durationS} s"
        r.distanceM != null -> "${n0(r.distanceM)} m"
        else -> "Recorded without values"
    }
    return main + (r.rpe?.let { " • RPE $it" } ?: "")
}

private fun androidx.compose.foundation.lazy.LazyListScope.exercisesTab(d: SessionDetail) {
    if (d.exercises.isEmpty()) { item { Text("No sets were recorded in this workout.", color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp)) }; return }
    item { Text("Rest per set and left/right side are not recorded by the app, so they are not shown." + ((if (d.timing.tracked) d.timing.restMs?.takeIf { it > 0 }?.let { " Timed rest = time run on the rest timer: ${WorkoutTiming.fmtMin(it / 60_000L)}." } else null) ?: ""), color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
    items(d.exercises, key = { "e" + it.exerciseId }) { e ->
        WkCard(Modifier.padding(horizontal = 16.dp), radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnatomyThumb(e.muscle, 48.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(e.name, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(exTotals(e, d.isGym).ifBlank { "No totals" }, color = C.Text2, fontSize = 12.sp) }
            }
            e.sets.forEachIndexed { i, r ->
                Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (r.warmup) "Warm-up" else "Set ${r.no}", color = if (r.warmup) C.Text2 else C.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                        Text(setText(r), color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    }
                    if (r.note.isNotBlank()) Text(r.note, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
    item { Text("Rest times and left/right side are not stored per set, so they are not shown. Warm-up sets are listed but not counted in totals.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.chartsTab(d: SessionDetail) {
    val ex = d.exercises
    val vol = ex.filter { it.volumeKg > 0 }; val sets = ex.filter { it.workingSets > 0 }; val reps = ex.filter { it.totalReps > 0 }
    @Composable fun card(title: String, rows: List<Triple<String, Double, String>>, note: String) = WkCard(Modifier.padding(horizontal = 16.dp), radius = 22.dp) {
        Text(title, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (rows.size < 2) Text(note, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) else HBars(rows)
    }
    item { card("Volume by exercise", vol.map { Triple(it.name, it.volumeKg, "${n0(it.volumeKg)} kg") }, "Needs at least two exercises with weighted sets.") }
    item { card("Sets by exercise", sets.map { Triple(it.name, it.workingSets.toDouble(), "${it.workingSets}") }, "Needs at least two exercises with recorded sets.") }
    item { card("Reps by exercise", reps.map { Triple(it.name, it.totalReps.toDouble(), "${it.totalReps}") }, "Needs at least two exercises with recorded reps.") }
    item { Text("Charts use only this workout's recorded working sets.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
}

/** Horizontal labelled bars; width is proportional to the largest value. */
@Composable
private fun HBars(rows: List<Triple<String, Double, String>>) {
    val max = rows.maxOf { it.second }.coerceAtLeast(1.0)
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, v, shown) ->
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $shown" }) {
                Row { Text(label, color = C.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); Text(shown, color = C.Text2, fontSize = 12.sp, maxLines = 1) }
                Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight().background(Color(0xFFD8D8DC))) }
            }
        }
    }
}
