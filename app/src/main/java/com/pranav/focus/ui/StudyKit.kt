package com.pranav.focus.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Subject

fun subjectIcon(s: Subject) = when (s) {
    Subject.PHYSICS -> R.drawable.p1_atom
    Subject.CHEMISTRY -> R.drawable.p1_chemistry_flask
    Subject.BIOLOGY -> R.drawable.p1_biology_leaf
}

fun subjectBlurb(s: Subject) = when (s) {
    Subject.PHYSICS -> "Mechanics, Thermodynamics, Electrodynamics"
    Subject.CHEMISTRY -> "Physical, Organic, Inorganic"
    Subject.BIOLOGY -> "Botany, Zoology, Human Physiology"
}

/** Top bar used by every sub-route: back circle, centred title, optional subtitle. */
@Composable
fun StudyTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        if (onBack != null) {
            Box(Modifier.align(Alignment.CenterStart).size(40.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_arrow_back, 22.dp)
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = C.Text2, fontSize = 12.sp)
        }
        if (trailing != null) Box(Modifier.align(Alignment.CenterEnd)) { trailing() }
    }
}

@Composable
fun SegPill(options: List<String>, sel: Int, modifier: Modifier = Modifier, onSel: (Int) -> Unit) {
    Row(modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(if (i == sel) C.White else Color.Transparent).clickable { onSel(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (i == sel) C.Black else C.Text2, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}

@Composable
fun Pill(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Box(
        modifier.height(30.dp).clip(CircleShape)
            .background(if (filled) C.White else C.Raised)
            .border(1.dp, if (filled) C.White else C.Hairline, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (filled) C.Black else C.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun DeltaChip(delta: Int?, suffix: String = "") {
    val t = if (delta == null) "—" else (if (delta >= 0) "↑ +" else "↓ ") + delta + suffix
    Text(t, color = C.White, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(C.Raised).border(1.dp, C.Hairline, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun AccRing(pct: Int, size: Dp, stroke: Dp = 5.dp, label: Boolean = true) {
    val sweep = rememberSweep(pct / 100f, 600)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx(); val inset = s / 2
            drawArc(C.Track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f * sweep, false, Offset(inset, inset), Size(this.size.width - s, this.size.height - s), style = Stroke(s + 3.dp.toPx(), cap = StrokeCap.Round))
            drawArc(Color.White, -90f, 360f * sweep, false, Offset(inset, inset), Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        if (label) Text("$pct%", color = C.White, fontSize = (size.value * 0.26f).sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SlimBar(frac: Float, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val f = rememberSweep(frac.coerceIn(0f, 1f), 400)
    Box(modifier.height(height).clip(CircleShape).background(C.Track)) { Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(C.White)) }
}

/** hollow < 50%, half 50–70%, filled > 70% (spec). */
@Composable
fun StatusDot(acc: Int, size: Dp = 16.dp) {
    val st = com.pranav.focus.data.StudyLogic.status(acc)
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2 - 1.dp.toPx()
        drawCircle(Color.White, r, style = Stroke(1.5.dp.toPx()))
        if (st == 2) drawCircle(Color.White, r)
        if (st == 1) drawArc(Color.White, 90f, 180f, true, Offset(1.dp.toPx(), 1.dp.toPx()), Size(this.size.width - 2.dp.toPx(), this.size.height - 2.dp.toPx()))
    }
}

/** Big − / + stepper tile used by B1 and B4A. */
@Composable
fun StepperTile(label: String, value: Int, style: Int, modifier: Modifier = Modifier, big: Boolean = true, onChange: (Int) -> Unit) {
    // style 0 solid white, 1 outlined, 2 dim
    val fg = if (style == 0) C.Black else if (style == 2) C.Text3 else C.White
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.clip(shape)
            .background(if (style == 0) C.White else C.Card)
            .border(1.dp, if (style == 1) Color.White.copy(alpha = 0.7f) else C.Hairline, shape)
            .padding(vertical = if (big) 14.dp else 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = if (style == 0) Color(0xFF3A3A3C) else C.Text2, fontSize = 12.sp)
        Text("$value", color = fg, fontSize = if (big) 40.sp else 30.sp, fontWeight = FontWeight.Medium, lineHeight = if (big) 46.sp else 36.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepBtn(false, style) { onChange((value - 1).coerceAtLeast(0)) }
            StepBtn(true, style) { onChange(value + 1) }
        }
    }
}

@Composable
private fun StepBtn(plus: Boolean, style: Int, onClick: () -> Unit) {
    val bg = if (style == 0) C.Black else Color.Transparent
    val fg = if (style == 0) C.White else if (style == 2) C.Text3 else C.White
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(bg).border(1.dp, if (style == 0) C.Black else C.Text3, CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(14.dp).height(2.dp).background(fg))
        if (plus) Box(Modifier.width(2.dp).height(14.dp).background(fg))
    }
}

@Composable
fun StatCell(v: String, l: String, modifier: Modifier = Modifier, sub: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(l, color = C.Text2, fontSize = 11.sp)
        Text(v, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        if (sub != null) Text(sub, color = C.Text3, fontSize = 10.sp)
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, pad: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().glass(radius).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(pad)) { content() }
}

fun relDue(daysLeft: Long) = if (daysLeft <= 0) "Today" else "${daysLeft}d"
