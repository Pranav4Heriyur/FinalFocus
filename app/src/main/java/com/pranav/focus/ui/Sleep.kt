package com.pranav.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.data.Fmt
import com.pranav.focus.net.Night
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Last night's sleep: total, bed and wake times, stage split, efficiency, wakings and a 7-night average. */
@Composable
fun SleepCard(nights: List<Night>, connected: Boolean, onClick: () -> Unit) {
    val last = nights.lastOrNull()
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val week = nights.takeLast(7)
    val avg = if (week.isEmpty()) 0 else week.sumOf { it.asleepMin } / week.size

    SectionCard(pad = 16.dp, radius = 24.dp, onClick = onClick) {
        Caps("Sleep · last night")
        if (last == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (connected) "No sleep recorded yet. Wear your watch to bed, then check Health Connect."
                else "Allow Health Connect sleep access to see your nights here.",
                color = C.Text2, fontSize = 13.sp, lineHeight = 18.sp,
            )
            return@SectionCard
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(Fmt.hm(last.asleepMin), style = glow(34f, 300, soft = true), maxLines = 1, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("${Instant.ofEpochMilli(last.startMs).atZone(zone).format(fmt)} → ${Instant.ofEpochMilli(last.endMs).atZone(zone).format(fmt)}",
                    color = C.Text2, fontSize = 12.sp)
                Text("in bed ${Fmt.hm(((last.endMs - last.startMs) / 60_000).toInt())}", color = C.Text3, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Stage bar: width is proportional to minutes in each stage.
        val stages = listOf(
            Triple("Deep", last.deepMin, 1f), Triple("REM", last.remMin, 0.7f),
            Triple("Light", last.lightMin, 0.4f), Triple("Awake", last.awakeMin, 0.15f),
        )
        val total = stages.sumOf { it.second }
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                stages.forEach { (_, m, shade) ->
                    if (m > 0) Box(Modifier.weight(m.toFloat()).fillMaxWidth().height(10.dp).background(C.White.copy(alpha = shade)))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                stages.forEach { (label, m, shade) ->
                    Column(horizontalAlignment = Alignment.Start) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(C.White.copy(alpha = shade)))
                            Spacer(Modifier.width(5.dp))
                            Text(label, color = C.Text2, fontSize = 11.sp)
                        }
                        Text(Fmt.hm(m), color = C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            val efficiency = if (last.endMs > last.startMs) (last.asleepMin * 60_000L * 100 / (last.endMs - last.startMs)).toInt() else 0
            StatCell("$efficiency%", "Efficiency", Modifier.weight(1f))
            StatCell("${last.wakings}", "Wakings", Modifier.weight(1f))
            StatCell(if (week.isEmpty()) "—" else Fmt.hm(avg), "7-night avg", Modifier.weight(1f))
        }
    }
}
