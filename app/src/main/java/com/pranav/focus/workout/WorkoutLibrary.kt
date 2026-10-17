package com.pranav.focus.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Screen

/** C8 Routines Library: every saved routine (presets G1-G4, M1-M5 and user routines) from workout.db, grouped by category. */
@Composable
fun WorkoutLibraryScreen(go: (Screen) -> Unit, back: () -> Unit) {
    val repo = rememberRepo()
    val tick = WorkoutNav.tick
    val all = remember(tick) { repo.routines() }
    val plan = remember(tick) { repo.activePlan() }
    val days = remember(tick, plan?.id) { plan?.let { repo.days(it.id) } ?: emptyList() }
    var message by remember { mutableStateOf<String?>(null) }
    val sections = remember(all) { WorkoutLogic.librarySections(all) }

    fun daysUsing(r: Routine): String =
        if (plan == null) "" else days.filter { it.gym == r.id || it.morning == r.id || it.altMorning == r.id }
            .joinToString(", ") { WEEKDAY_SHORT[WorkoutLogic.weekdayOf(it.slot, plan.slot1Weekday).value - 1] }

    WkPage(title = "Routines", subtitle = "Gym G1–G4, morning M1–M5 and your own.", onBack = back) {
        message?.let { Text(it, color = C.Text2, fontSize = 13.sp) }
        if (all.isEmpty()) Text("No routines saved yet.", color = C.Text2, fontSize = 14.sp)
        sections.forEach { (title, list) ->
            SectionTitle(title, count = list.size)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.forEach { r ->
                    val used = daysUsing(r)
                    val cardio = r.kind == "run" || r.kind == "ruck"
                    WkCard(onClick = { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r.code.isNotBlank()) { Tag(r.code); Spacer(Modifier.width(10.dp)) } else if (!r.preset) { Tag("Custom"); Spacer(Modifier.width(10.dp)) }
                            Text(r.name, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                        if (r.focus.isNotBlank()) Text(r.focus, color = C.Text2, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        Text(
                            listOf("${r.exerciseCount} exercises", r.labelPlans.removePrefix("~ ").ifBlank { r.labelHub }, r.difficulty).filter { it.isNotBlank() }.joinToString(" • "),
                            color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (used.isNotEmpty()) Text("Scheduled: $used", color = C.Text3, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WkButton("View", Modifier.weight(1f), height = 44.dp) { WorkoutNav.routineId = r.id; go(Screen.WorkoutRoutine) }
                            WkButton("Edit", Modifier.weight(1f), height = 44.dp) {
                                val slot = days.firstOrNull { it.gym == r.id || it.morning == r.id }?.slot
                                if (plan != null && slot != null) { WorkoutNav.planId = plan.id; WorkoutNav.slot = slot; go(Screen.WorkoutDay) }
                                else message = "${r.name} isn't on your weekly schedule yet. Open it and use Schedule This Plan, then edit that day."
                            }
                            WkButton("Start", Modifier.weight(1f), primary = true, height = 44.dp) {
                                if (cardio) message = "Use Log Run / Log Ruck on the Workout hub to record distance and time."
                                else startPlanned(repo, plan, days.firstOrNull { it.gym == r.id || it.morning == r.id }?.slot ?: 1, r, go)
                            }
                        }
                    }
                }
            }
        }
    }
}
