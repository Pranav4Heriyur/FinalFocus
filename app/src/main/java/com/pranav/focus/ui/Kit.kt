package com.pranav.focus.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pranav.focus.R
import kotlinx.coroutines.delay

/** Phase-1 shared kit: glass card surface, glow text, staggered reveal, reduce-motion handling. */
fun Modifier.glass(radius: Dp = 24.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(C.Card)
        .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent), endY = 24.dp.toPx())) }
        .border(1.dp, C.Hairline, shape)
}

val GlowShadow = Shadow(Color.White.copy(alpha = 0.30f), Offset.Zero, 28f)
val SoftGlowShadow = Shadow(Color.White.copy(alpha = 0.18f), Offset.Zero, 16f)

fun glow(size: Float, weight: Int = 300, soft: Boolean = false) =
    TextStyle(fontSize = size.sp(), fontWeight = androidx.compose.ui.text.font.FontWeight(weight), shadow = if (soft) SoftGlowShadow else GlowShadow, color = C.White)

private fun Float.sp() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)

val GrayFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun reduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Card entrance: opacity + 8dp rise, 220 ms, 35 ms stagger (motion spec). */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduce = reduceMotion()
    val a = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) { delay(index * 35L); a.animateTo(1f, tween(220)) }
    }
    Box(modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 8.dp.toPx() }) { content() }
}

/** 0..1 progress animated from 0 on first composition (ring sweeps, bars). */
@Composable
fun rememberSweep(target: Float, durationMs: Int = 600, delayMs: Int = 0): Float {
    val reduce = reduceMotion()
    val a = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target) {
        if (reduce) a.snapTo(target) else a.animateTo(target, tween(durationMs, delayMillis = delayMs))
    }
    return a.value
}

fun agendaIconRes(key: String): Int = when (key) {
    "Run" -> R.drawable.p1_run_sprinter
    "Physics", "Focus" -> R.drawable.p1_study_book
    "Chemistry" -> R.drawable.p1_chemistry_flask
    "Biology" -> R.drawable.p1_biology_leaf
    "Workout", "Gym" -> R.drawable.p1_dumbbell
    "Mirror" -> R.drawable.p1_profile_outline
    else -> R.drawable.p1_check_outline
}

/** "6:00 AM" -> minutes since midnight (sort key); unknown = end of day. */
fun parseClock(t: String): Int {
    val m = Regex("""(\d{1,2}):(\d{2})\s*([AaPp][Mm])?""").find(t) ?: return 24 * 60
    var h = m.groupValues[1].toInt()
    val ap = m.groupValues[3].lowercase()
    if (ap == "pm" && h < 12) h += 12
    if (ap == "am" && h == 12) h = 0
    return h * 60 + m.groupValues[2].toInt()
}
