package com.pranav.focus.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R

/** Monochrome tokens from the design handoff. */
object C {
    val Black = Color(0xFF000000)
    val Card = Color(0xFF0B0B0C)
    val Raised = Color(0xFF141416)
    val Sheet = Color(0xFF1C1C1E)
    val Track = Color(0xFF2A2A2D)
    val White = Color(0xFFFFFFFF)
    val Text2 = Color(0xFFA1A1A6)
    val Text3 = Color(0xFF636366)
    val Hairline = Color(0x1AFFFFFF)
}

@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(R.font.inter_variable, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

@Composable
fun FocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.White, onPrimary = C.Black,
            background = C.Black, onBackground = C.White,
            surface = C.Card, onSurface = C.White,
            surfaceVariant = C.Raised, onSurfaceVariant = C.Text2,
            outline = C.Hairline,
        ),
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = Inter, shadow = BodyGlow)) {
            content()
        }
    }
}

@Composable
fun Ico(@DrawableRes id: Int, size: Dp = 24.dp, tint: Color = C.White, modifier: Modifier = Modifier) {
    Icon(painterResource(id), null, modifier.size(size), tint)
}

/** Cheap "lit display" bloom: stacked translucent rounded rects behind the element (no blur, safe on Helio G80). */
fun Modifier.bloom(radius: Dp = 14.dp, alpha: Float = 0.22f, corner: Dp = 999.dp): Modifier = drawBehind {
    val r = radius.toPx()
    val cr = corner.toPx()
    for (i in 1..4) {
        val e = r * i / 4f
        drawRoundRect(
            Color.White.copy(alpha = alpha / 4f * (1.2f - i * 0.2f)),
            topLeft = Offset(-e, -e),
            size = Size(size.width + 2 * e, size.height + 2 * e),
            cornerRadius = CornerRadius(cr + e),
        )
    }
}

@Composable
fun Caps(text: String, modifier: Modifier = Modifier, color: Color = C.Text2) {
    Text(text.uppercase(), modifier = modifier, color = color, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp)
}

@Composable
fun FocusCard(
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    radius: Dp = 24.dp, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.glass(radius)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Dark squircle tile with a white glyph; inverts when active. */
@Composable
fun IconTile(@DrawableRes icon: Int, active: Boolean = false, size: Dp = 48.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(14.dp)).background(if (active) C.White else C.Sheet),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 24.dp, if (active) C.Black else C.White) }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth().height(56.dp)
            .then(if (enabled) Modifier.bloom(10.dp, 0.2f) else Modifier)
            .clip(CircleShape)
            .background(if (enabled) C.White else C.Track)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) C.Black else C.Text3, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: Int? = null, onClick: () -> Unit) {
    val c = if (enabled) C.White else C.Text3
    Row(
        modifier
            .fillMaxWidth().height(56.dp)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, if (enabled) C.Text3 else C.Track), CircleShape)
            .clickable(enabled = enabled) { onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Ico(icon, 20.dp, c)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, color = c, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FocusSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.Black, checkedTrackColor = C.White, checkedBorderColor = C.White,
            uncheckedThumbColor = C.Text2, uncheckedTrackColor = C.Track, uncheckedBorderColor = C.Track,
        ),
    )
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, subtitle: String? = null) {
    Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape).clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Ico(R.drawable.ic_arrow_back, 22.dp) }
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        }
        Text(title, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, color = C.Text2, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = C.White, unfocusedTextColor = C.White,
    focusedBorderColor = C.White, unfocusedBorderColor = C.Track,
    cursorColor = C.White,
    focusedLabelColor = C.Text2, unfocusedLabelColor = C.Text3,
    focusedPlaceholderColor = C.Text3, unfocusedPlaceholderColor = C.Text3,
)

/** Aliases used by the Plan screens. */
@Composable
fun FocusToggle(checked: Boolean, onChange: (Boolean) -> Unit) = FocusSwitch(checked, onChange)

@Composable
fun ProgBar(frac: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(C.Track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(C.White))
    }
}
