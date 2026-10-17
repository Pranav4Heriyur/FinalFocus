package com.pranav.focus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R

data class NavItem(val screen: Screen, val label: String, val icon: Int, val iconActive: Int = icon)

val NAV_ITEMS = listOf(
    NavItem(Screen.Home, "Home", R.drawable.ic_home, R.drawable.p1_home_filled),
    NavItem(Screen.Focus, "Focus", R.drawable.p1_focus_ring),
    NavItem(Screen.Study, "Study", R.drawable.p1_study_book),
    NavItem(Screen.Plan, "Plan", R.drawable.plan_ic_plan),
    NavItem(Screen.Workout, "Workout", R.drawable.p1_dumbbell),
    NavItem(Screen.Stats, "Stats", R.drawable.p1_stats_bars),
    NavItem(Screen.TimeBank, "Time Bank", R.drawable.p1_clock),
    NavItem(Screen.Profile, "Profile", R.drawable.p1_profile_outline),
)

val TAB_SCREENS = NAV_ITEMS.map { it.screen }

/**
 * Seven destinations in a genuinely horizontally scrollable floating bar. Scrolling never navigates;
 * tapping does. The selected item is scrolled into view whenever the route changes.
 */
@Composable
fun TabBar(current: Screen, onSelect: (Screen) -> Unit) {
    val state = rememberLazyListState()
    val itemW = 80.dp
    val itemWpx = with(LocalDensity.current) { itemW.toPx() }
    val idx = NAV_ITEMS.indexOfFirst { it.screen == current }
    LaunchedEffect(current) {
        if (idx >= 0) {
            val vp = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
            state.animateScrollToItem(idx, -((vp - itemWpx) / 2).toInt())
        }
    }
    LazyRow(
        state = state,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp).fillMaxWidth().height(64.dp)
            .clip(CircleShape).background(C.Raised.copy(alpha = 0.94f)).border(1.dp, C.Hairline, CircleShape),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(NAV_ITEMS) { _, item ->
            val sel = item.screen == current
            val bg = animateColorAsState(if (sel) C.White else androidx.compose.ui.graphics.Color.Transparent, tween(260), label = "navBg").value
            val fg = animateColorAsState(if (sel) C.Black else C.Text2, tween(260), label = "navFg").value
            Column(
                Modifier.width(itemW).fillMaxHeight().clip(CircleShape).background(bg).clickable { onSelect(item.screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Ico(if (sel) item.iconActive else item.icon, 24.dp, fg)
                Text(item.label, color = fg, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Extra bottom space screens reserve above the tab bar (bar is laid out in a Column, so little is needed). */
val NAV_CLEARANCE = 12.dp
