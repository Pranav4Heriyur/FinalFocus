package com.pranav.focus.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pranav.focus.R
import com.pranav.focus.lock.Allowed
import kotlinx.coroutines.flow.distinctUntilChanged

private val iconCache = HashMap<String, ImageBitmap>()

fun appLabel(ctx: android.content.Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

@Composable
fun AppIcon(pkg: String, size: Dp = 44.dp, mono: Boolean = false) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        iconCache[pkg] ?: runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }
            .getOrNull()?.also { iconCache[pkg] = it }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(C.Sheet)) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), colorFilter = if (mono) GrayFilter else null)
    }
}

/** Full-width segmented control. */
@Composable
fun SegFull(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).border(1.dp, C.Hairline, CircleShape).background(C.Raised).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(CircleShape).then(if (sel) Modifier.background(C.White) else Modifier)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o, color = if (sel) C.Black else C.Text2, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier) {
    Text(text, color = C.Text2, fontSize = 11.sp, modifier = modifier.clip(CircleShape).background(C.Sheet).padding(horizontal = 10.dp, vertical = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.Sheet, contentColor = C.White,
    ) { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) { content() } }
}

/** iOS-style snapping wheel. Value index = first visible item (two blank rows above and below). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Wheel(values: List<String>, index: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val h = 44.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceIn(0, values.lastIndex))
    val fling = rememberSnapFlingBehavior(state)
    LaunchedEffect(state) {
        snapshotFlow { if (state.isScrollInProgress) -1 else state.firstVisibleItemIndex }
            .distinctUntilChanged().collect { if (it >= 0) onChange(it.coerceIn(0, values.lastIndex)) }
    }
    val sel = state.firstVisibleItemIndex
    Box(modifier.fillMaxWidth().height(h * 5), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(12.dp)).background(C.Track.copy(alpha = 0.6f)))
        LazyColumn(Modifier.fillMaxSize(), state = state, flingBehavior = fling) {
            items(2) { Spacer(Modifier.height(h)) }
            itemsIndexed(values) { i, v ->
                Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                    Text(v, color = if (i == sel) C.White else C.Text3, fontSize = if (i == sel) 22.sp else 18.sp, fontWeight = FontWeight.Medium)
                }
            }
            items(2) { Spacer(Modifier.height(h)) }
        }
    }
}

@Composable
fun WheelSheet(title: String, values: List<String>, index: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var cur by remember { mutableStateOf(index) }
    FocusSheet(onDismiss) {
        Text(title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Wheel(values, index, { cur = it })
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Done") { onPick(cur); onDismiss() }
    }
}

@Composable
fun AppChooserSheet(title: String, exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { Allowed.launchableApps(ctx) }
    var q by remember { mutableStateOf("") }
    val shown = apps.filter { it.pkg !in exclude && it.label.contains(q, true) }
    FocusSheet(onDismiss) {
        Text(title, color = C.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search apps") },
            colors = fieldColors(), shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.height(380.dp)) {
            items(shown, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().clickable { onPick(a.pkg); onDismiss() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg, 40.dp)
                    Text(a.label, color = C.White, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
