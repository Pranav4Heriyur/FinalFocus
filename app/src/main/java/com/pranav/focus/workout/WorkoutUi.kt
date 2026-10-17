package com.pranav.focus.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.ui.C
import com.pranav.focus.ui.Ico
import com.pranav.focus.ui.Inter

/** Shared building blocks for the Workout screens (monochrome FOCUS tokens; routine accents are 6 dp dots only). */
fun wkIco(key: String): Int = WorkoutAssets.ui(key)

fun accentColor(a: String): Color = when (a) {
    "red" -> Color(0xFFFF5A5F); "blue" -> Color(0xFF4C9AFF); "green" -> Color(0xFF34C759); "purple" -> Color(0xFFAF7BFF); else -> C.Text3
}

val ScreenPad = 20.dp
val TabBarClearance = 100.dp

@Composable
fun WkPage(
    title: String?, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {},
    tabBarClear: Boolean = false, bottomBar: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = ScreenPad)
                .padding(bottom = if (tabBarClear) TabBarClearance else 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) { RoundBtn("Back", R.drawable.ic_arrow_back, onBack); Spacer(Modifier.width(12.dp)) }
                Spacer(Modifier.weight(1f))
                trailing()
            }
            if (title != null) Column(Modifier.padding(top = if (onBack != null) 0.dp else 0.dp)) {
                Text(title, color = C.White, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, color = C.Text2, fontSize = 15.sp)
            }
            content()
        }
        if (bottomBar != null) Box(Modifier.fillMaxWidth().background(C.Black).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) { bottomBar() }
    }
}

@Composable
fun RoundBtn(desc: String, icon: Int, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, 22.dp) }
}

@Composable
fun WkCard(modifier: Modifier = Modifier, radius: Dp = 20.dp, padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.clip(shape).background(C.Card).border(BorderStroke(1.dp, C.Hairline), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding),
        content = content,
    )
}

/** Pill button. [primary] = white fill / black text, else outlined. Min height 48 dp. */
@Composable
fun WkButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, icon: Int? = null, enabled: Boolean = true, height: Dp = 52.dp, onClick: () -> Unit) {
    val fg = if (!enabled) C.Text3 else if (primary) C.Black else C.White
    Row(
        modifier.heightIn(min = 48.dp).height(height).clip(CircleShape)
            .then(if (primary) Modifier.background(if (enabled) C.White else C.Track) else Modifier.background(C.Card).border(1.dp, C.Hairline, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Ico(icon, 20.dp, fg); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.Text2, fontSize = 11.sp, maxLines = 1, modifier = modifier.clip(RoundedCornerShape(8.dp)).background(C.Sheet).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 34.dp).clip(CircleShape).background(if (selected) C.White else C.Raised)
            .border(1.dp, if (selected) C.White else C.Hairline, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = if (selected) "$text, selected" else text }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) C.Black else C.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
}

@Composable
fun SegTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, C.Hairline, CircleShape).background(C.Card).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(CircleShape).then(if (sel) Modifier.background(C.White) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (sel) C.Black else C.Text2, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        }
    }
}

@Composable
fun SectionTitle(title: String, count: Int? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (count != null) Text("  ($count)", color = C.Text2, fontSize = 20.sp)
        Spacer(Modifier.weight(1f))
        if (action != null) Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction), verticalAlignment = Alignment.CenterVertically) {
            Text(action, color = C.White, fontSize = 14.sp); Spacer(Modifier.width(4.dp)); Ico(wkIco("chevron-right"), 16.dp, C.White)
        }
    }
}

/** Routine artwork: generated illustration when one exists, otherwise a pictogram on a raised tile. Scrimmed for legible overlays. */
@Composable
fun CoverArt(routine: Routine?, modifier: Modifier = Modifier, scrim: Boolean = true, content: @Composable BoxScope.() -> Unit = {}) {
    val img = routine?.let { WorkoutImages.routineCover(it.id) }
    Box(modifier.background(C.Raised)) {
        if (img != null) Image(painterResource(img), "${routine?.name.orEmpty()} illustration", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Ico(wkIco(if (routine?.kind == "mobility") "mobility" else "dumbbell"), 44.dp, C.White.copy(alpha = 0.35f))
        }
        if (scrim) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
        content()
    }
}

@Composable
fun AccentDot(accent: String) { Box(Modifier.size(7.dp).clip(CircleShape).background(accentColor(accent))) }

/** Boxed single-line / multi-line text input (BasicTextField so we control every colour). */
@Composable
fun WkField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true, maxLen: Int = 200,
    numeric: Boolean = false, minHeight: Dp = 52.dp, trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = C.Text3, fontSize = 15.sp)
            BasicTextField(
                value = value, onValueChange = { if (it.length <= maxLen) onChange(it) }, singleLine = singleLine,
                textStyle = TextStyle(color = C.White, fontSize = 15.sp, fontFamily = Inter), cursorBrush = SolidColor(C.White),
                keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, dismiss: String = "Cancel") {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Sheet, titleContentColor = C.White, textContentColor = C.Text2,
        title = { Text(title, fontWeight = FontWeight.SemiBold) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = C.White, fontWeight = FontWeight.SemiBold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss, color = C.Text2) } },
    )
}

@Composable
fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Sheet, titleContentColor = C.White, textContentColor = C.Text2,
        title = { Text(title, fontWeight = FontWeight.SemiBold) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = C.White, fontWeight = FontWeight.SemiBold) } },
    )
}

/** One of the 7 weekday cells (Hub strip, Plans schedule, Day Builder). */
@Composable
fun DayCell(
    weekday: String, line1: String, line2: String?, accent: String, selected: Boolean, modifier: Modifier = Modifier, icon: Int? = null, onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.heightIn(min = 84.dp).clip(shape).background(C.Card).border(if (selected) 1.5.dp else 1.dp, if (selected) C.White else C.Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(weekday, color = if (selected) C.White else C.Text2, fontSize = 11.sp)
        Text(line1, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (line2 != null) Text(line2, color = C.Text2, fontSize = 10.sp, maxLines = 1)
        if (icon != null) Ico(icon, 18.dp, C.White)
        AccentDot(accent)
    }
}

val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
