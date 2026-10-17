package com.pranav.focus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.data.Fmt
import com.pranav.focus.data.Store
import com.pranav.focus.data.Weekly
import com.pranav.focus.lock.AppState

/** Which Stats sub-tab is showing. Workout stats are reached through here. */
object StatsNav {
    var tab by mutableIntStateOf(0)   // 0 Overview, 1 Workouts
}

/** The Stats tab: overview and workout stats. Study has its own tab now. */
@Composable
fun StatsHub(go: (Screen) -> Unit, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
            SegFull(listOf("Overview", "Workouts"), StatsNav.tab) { StatsNav.tab = it }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (StatsNav.tab) {
                1 -> com.pranav.focus.workout.WorkoutStatsScreen(back)
                else -> StatsScreen(go)
            }
        }
    }
}

/** Weekly focus target: this week's progress, and how next week's target is set. */
@Composable
fun WeeklyTargetCard(store: Store) {
    val tick = AppState.dataTick
    val achieved = remember(tick) { Weekly.stats(store, Weekly.weekStart()).focusMin }
    val target = Weekly.focusTarget(store)
    val frac = (achieved.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    SectionCard(pad = 16.dp, radius = 22.dp) {
        Caps("Weekly focus target")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.hm(achieved), color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(" of ${Fmt.hm(target)} this week", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        SlimBar(frac, Modifier.fillMaxWidth().padding(top = 10.dp), 6.dp)
        Text(
            "Next week's target is the focus time you reach this week, so a big week raises it and a light week lowers it.",
            color = C.Text3, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Shown on a feature whose behaviour depends on permissions that are missing. */
@Composable
fun GateBanner(title: String, body: String, fixLabel: String, onFix: () -> Unit) {
    SectionCard(pad = 14.dp, radius = 20.dp, onClick = onFix) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(body, color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(fixLabel, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
