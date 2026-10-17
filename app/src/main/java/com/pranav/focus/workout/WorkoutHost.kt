package com.pranav.focus.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.pranav.focus.ui.Screen

/** Routes the Workout section's screens (Hub is the Workout tab; the rest are pushed on the app's back stack). */
@Composable
fun WorkoutHost(screen: Screen, go: (Screen) -> Unit, back: () -> Unit) {
    // C12 integration point: C10 / C11 call WorkoutNav.openWorkoutDetails(sessionId); the id is kept in WorkoutNav.detailSessionId.
    DisposableEffect(go) {
        WorkoutNav.openWorkoutDetails = { id -> WorkoutNav.detailSessionId = id; go(Screen.WorkoutDetail) }
        WorkoutNav.openHistory = { go(Screen.WorkoutHistory) }
        WorkoutNav.openStats = { go(Screen.WorkoutStats) }
        onDispose { WorkoutNav.openWorkoutDetails = null; WorkoutNav.openHistory = null; WorkoutNav.openStats = null }
    }
    when (screen) {
        Screen.Workout -> WorkoutHubScreen(go)
        Screen.WorkoutPlans -> WorkoutPlansScreen(go, back)
        Screen.WorkoutRoutine -> WorkoutRoutineScreen(go, back)
        Screen.WorkoutCreate -> WorkoutCreateScreen(go, back)
        Screen.WorkoutDay -> WorkoutDayScreen(go, back)
        Screen.WorkoutAdd -> WorkoutAddScreen(go, back)
        Screen.WorkoutLibrary -> WorkoutLibraryScreen(go, back)
        Screen.WorkoutActive -> WorkoutActiveScreen(go, back)
        Screen.WorkoutComplete -> WorkoutCompleteScreen(back)
        Screen.WorkoutStats -> WorkoutStatsScreen(back)
        Screen.WorkoutHistory -> WorkoutHistoryScreen(back)
        Screen.WorkoutDetail -> WorkoutDetailScreen(back)
        Screen.WorkoutSet -> WorkoutSetEntryScreen(back)
        else -> Unit
    }
}
