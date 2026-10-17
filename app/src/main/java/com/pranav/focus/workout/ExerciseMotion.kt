package com.pranav.focus.workout

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Native replacement for the pack's `*-animated.svg` files (SMIL is not supported on Android).
 * The importer verifies every source SVG uses exactly these parameters:
 *   halo ring  r=29 / 64-unit canvas, stroke 1.4, stroke-opacity .08 -> .28 -> .08 over 2.4 s
 *   pictogram  translateY 0 -> -1.2 -> 0 (64-unit canvas) over 1.6 s
 * Subtle UI motion only - not a technique demonstration.
 */
private const val HALO_MS = 2400
private const val BOB_MS = 1600
private const val BOB_UNITS = 1.2f
private const val VIEWBOX = 64f

/** True when the user disabled animations (Developer options / "Remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/**
 * Exercise pictogram addressed by its stable catalog ID. Static when [animated] is false or
 * reduced motion is on, so long lists stay cheap and accessible.
 */
@Composable
fun ExerciseIcon(
    exerciseId: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    animated: Boolean = false,
    tint: Color? = null,
) {
    val asset = WorkoutAssets.exercise(exerciseId)
    val motion = animated && !rememberReducedMotion()
    val t = rememberInfiniteTransition(label = "exMotion")
    val halo = if (motion) t.animateFloat(
        .08f, .28f,
        infiniteRepeatable(keyframes {
            durationMillis = HALO_MS; .08f at 0 using FastOutSlowInEasing; .28f at HALO_MS / 2 using FastOutSlowInEasing; .08f at HALO_MS
        }), label = "halo",
    ).value else 0f
    val bob = if (motion) t.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = BOB_MS; 0f at 0 using LinearEasing; -1f at BOB_MS / 2 using LinearEasing; 0f at BOB_MS
        }), label = "bob",
    ).value else 0f

    Box(
        modifier.size(size).semantics { contentDescription = asset?.name ?: "" }
            .drawBehind {
                if (motion) {
                    val u = this.size.minDimension / VIEWBOX
                    drawCircle(
                        Color.White.copy(alpha = halo), radius = 29f * u,
                        center = Offset(this.size.width / 2, this.size.height / 2),
                        style = Stroke(width = 1.4f * u),
                    )
                }
            },
    ) {
        if (asset != null) Image(
            painterResource(asset.icon), contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = bob * BOB_UNITS / VIEWBOX * this.size.height },
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
    }
}

/** Tintable UI icon from the pack by stable key (e.g. "dumbbell", "chevron-right"). */
@Composable
fun WkIcon(key: String, modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.White, description: String? = null) {
    Image(
        painterResource(WorkoutAssets.ui(key)), contentDescription = description,
        modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint),
    )
}
