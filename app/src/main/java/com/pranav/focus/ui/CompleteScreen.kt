package com.pranav.focus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.lock.SessionSummary
import kotlin.math.cos
import kotlin.math.sin

/** A5: session complete. */
@Composable
fun CompleteScreen(s: SessionSummary, onDone: () -> Unit, onLog: () -> Unit, taskTitle: String? = null, onMarkDone: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                // fine rays and sparks (static, cheap)
                for (i in 0 until 36) {
                    val a = Math.toRadians(i * 10.0)
                    val inner = 100.dp.toPx()
                    val outer = inner + (if (i % 3 == 0) 70.dp.toPx() else 36.dp.toPx())
                    drawLine(
                        Color.White.copy(alpha = if (i % 3 == 0) 0.35f else 0.15f),
                        Offset(c.x + (inner * cos(a)).toFloat(), c.y + (inner * sin(a)).toFloat()),
                        Offset(c.x + (outer * cos(a)).toFloat(), c.y + (outer * sin(a)).toFloat()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
                for (i in 0 until 40) {
                    val a = Math.toRadians(i * 9.0 + 4)
                    val r = (118 + (i * 37) % 70).dp.toPx()
                    drawCircle(Color.White.copy(alpha = 0.55f), radius = (1 + i % 3).dp.toPx() * 0.6f, center = Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat()))
                }
                drawCircle(Color.White.copy(alpha = 0.08f), 112.dp.toPx(), c)
                drawCircle(Color.White.copy(alpha = 0.14f), 100.dp.toPx(), c)
                drawCircle(Color.Black, 88.dp.toPx(), c)
                drawCircle(Color.White, 88.dp.toPx(), c, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            }
            Ico(R.drawable.ic_check, 76.dp)
        }
        Text(formatClock(s.minutes * 60_000L), color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold)
        Text("locked in", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(subjectIcon(s.subject), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(s.subject, color = C.Text2, fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))

        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Metric(R.drawable.ic_schedule, formatClock(s.minutes * 60_000L), "Focus time")
                Metric(R.drawable.ic_local_fire_department, "${s.streak} days", "Streak")
                Metric(R.drawable.ic_flare, "+${s.xp} XP", "Earned")
            }
        }
        Spacer(Modifier.height(12.dp))
        FocusCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(R.drawable.ic_schedule, size = 32.dp)
                        Spacer(Modifier.width(8.dp))
                        Caps("Time bank")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("+${s.earnedMin} min", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold)
                    Text("added to your Time Bank", color = C.Text2, fontSize = 14.sp)
                }
                Ico(R.drawable.ic_layers, 72.dp, C.White.copy(alpha = 0.9f))
            }
        }
        Spacer(Modifier.height(16.dp))
        GhostButton("Log questions", icon = R.drawable.ic_menu_book) { onLog() }
        Spacer(Modifier.height(12.dp))
        if (taskTitle != null && onMarkDone != null) {
            GhostButton("Mark “$taskTitle” done") { onMarkDone() }
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton("Done") { onDone() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Metric(icon: Int, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, size = 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(value, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Caps(label)
    }
}
