package com.pranav.focus

import android.Manifest
import android.app.role.RoleManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pranav.focus.data.Store
import com.pranav.focus.lock.Allowed
import com.pranav.focus.lock.AppState
import android.content.Intent
import androidx.compose.runtime.mutableStateListOf
import com.pranav.focus.lock.Earning
import com.pranav.focus.ui.BlockedScreen
import com.pranav.focus.ui.NewRuleScreen
import com.pranav.focus.ui.RulesScreen
import com.pranav.focus.ui.TimeBankScreen
import com.pranav.focus.ui.AppsScreen
import com.pranav.focus.ui.C
import com.pranav.focus.ui.CallersScreen
import com.pranav.focus.ui.CompleteScreen
import com.pranav.focus.ui.FocusStartScreen
import com.pranav.focus.ui.FocusTheme
import com.pranav.focus.ui.HomeScreen
import com.pranav.focus.ui.PlaceholderScreen
import com.pranav.focus.ui.Screen
import com.pranav.focus.ui.SessionScreen
import com.pranav.focus.ui.SetupScreen
import com.pranav.focus.ui.TAB_SCREENS
import com.pranav.focus.ui.TabBar
import com.pranav.focus.ui.StatsHub
import com.pranav.focus.ui.StatsNav
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class MainActivity : ComponentActivity() {
    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { AppState.refresh(this) }
    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { AppState.refresh(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.get(this).ensureDefaults(Allowed.DEFAULT_PACKAGES.filter { Allowed.isInstalled(this, it) }.toSet())
        // Roll the weekly focus target over at app start, not only when the Rank screen happens to open.
        com.pranav.focus.data.Weekly.rollover(Store.get(this))
        AppState.blockedPkg = intent?.getStringExtra("blocked_pkg")
        AppState.locked = Store.get(this).biometricLock
        com.pranav.focus.lock.Reminder.schedule(this)
        AppState.refresh(this)
        setContent { FocusTheme { Root() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("blocked_pkg")?.let { AppState.blockedPkg = it }
        AppState.refresh(this)
    }

    private var bgAt = 0L

    override fun onStop() {
        super.onStop()
        bgAt = android.os.SystemClock.elapsedRealtime()
        val st = Store.get(this)
        if (st.biometricLock && st.lockOnExit && !AppState.active) AppState.locked = true
    }

    override fun onStart() {
        super.onStart()
        val st = Store.get(this)
        if (st.biometricLock && !AppState.locked && bgAt > 0 && st.autoLockSeconds >= 0 &&
            android.os.SystemClock.elapsedRealtime() - bgAt > st.autoLockSeconds * 1000L) AppState.locked = true
    }

    private fun promptUnlock() {
        val info = android.hardware.biometrics.BiometricPrompt.Builder(this)
            .setTitle("Unlock Focus")
            .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        info.authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                AppState.locked = false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (Store.get(this).hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (intent?.getStringExtra("open") == "mirror") { intent.removeExtra("open"); AppState.openMirror = true }
        Earning.evaluate(this)
        if (com.pranav.focus.net.HealthSync.connected(this)) com.pranav.focus.net.HealthSync.syncAsync(this)
        AppState.dataTick++
        AppState.refresh(this)
    }

    private fun goHome() {
        AppState.blockedPkg = null
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private val WORKOUT_SINGLETONS = setOf(Screen.WorkoutPlans, Screen.WorkoutLibrary, Screen.WorkoutStats, Screen.WorkoutHistory, Screen.WorkoutComplete)

    @Composable
    private fun Root() {
        val store = remember { Store.get(this) }
        var setupDone by remember { mutableStateOf(store.setupDone) }
        // One back stack per tab: leaving a tab and coming back restores where you were inside it.
        val tabStacks = remember {
            mutableStateMapOf<Screen, SnapshotStateList<Screen>>().also { m ->
                (TAB_SCREENS + Screen.Focus).forEach { t -> m[t] = mutableStateListOf(t) }
            }
        }
        var currentTab by remember { mutableStateOf(Screen.Home) }
        val stack = tabStacks.getValue(currentTab)
        val screen = stack.last()
        val active = AppState.active
        val summary = AppState.summary
        val blocked = AppState.blockedPkg

        fun go(target: Screen) {
            var s = target
            // Study and the workout stats now live inside the Stats tab as sub-tabs.
            if (s == Screen.Study) { StatsNav.tab = 1; s = Screen.Stats }
            if (s == Screen.WorkoutStats) { StatsNav.tab = 2; s = Screen.Stats }
            if (s in tabStacks.keys) {
                // Tab root: switch to it. Tapping the tab you are already on returns it to its root.
                if (s == currentTab) { val st = tabStacks.getValue(s); while (st.size > 1) st.removeAt(st.lastIndex) }
                else currentTab = s
                return
            }
            if (stack.last() == s) return                                   // double tap: never stack the same screen twice
            if (s in WORKOUT_SINGLETONS) {                                  // list/summary destinations exist once on the back stack: return to the existing one instead of looping
                val i = stack.lastIndexOf(s)
                if (i >= 0) { while (stack.size > i + 1) stack.removeAt(stack.lastIndex); return }
            }
            stack.add(s)
        }
        fun back() {
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
            else if (currentTab != Screen.Home) currentTab = Screen.Home     // Back on a tab root goes to Home
        }

        androidx.compose.runtime.LaunchedEffect(AppState.openMirror) {
            if (AppState.openMirror) { AppState.openMirror = false; go(Screen.Mirror) }
        }
        androidx.compose.runtime.LaunchedEffect(AppState.locked, active) {
            if (AppState.locked && !active) promptUnlock()
        }
        BackHandler(enabled = active) { /* no exit during a session */ }
        BackHandler(enabled = !active && blocked != null) { goHome() }
        BackHandler(enabled = !active && blocked == null && summary == null && screen != Screen.Home) { back() }

        DisposableEffect(active) {
            if (active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { }
        }

        val reqNotif = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        val reqRole = {
            getSystemService(RoleManager::class.java)
                ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                ?.let { roleLauncher.launch(it) }
            Unit
        }

        Box(Modifier.fillMaxSize().background(C.Black)) {
            when {
                active -> SessionScreen()
                summary != null -> CompleteScreen(
                    summary,
                    onDone = { AppState.summary = null; go(Screen.Home) },
                    onLog = { AppState.summary = null; store.putInt("study_prefill", 1); go(Screen.Study) },
                    taskTitle = store.str("last_session_task").let { id -> com.pranav.focus.ui.PlanState.get(id)?.takeIf { !it.done }?.title },
                    onMarkDone = {
                        val id = store.str("last_session_task")
                        com.pranav.focus.ui.PlanState.get(id)?.let { com.pranav.focus.ui.PlanState.upsert(this@MainActivity, it.copy(done = true)) }
                        AppState.summary = null; go(Screen.Home)
                    },
                )
                AppState.locked && store.biometricLock -> com.pranav.focus.ui.LockScreen { promptUnlock() }
                !setupDone -> SetupScreen(null, reqNotif, reqRole, onContinue = { store.setupDone = true; setupDone = true })
                blocked != null -> BlockedScreen(
                    blocked,
                    onFocus = { AppState.blockedPkg = null; go(Screen.Focus) },
                    onBank = { AppState.blockedPkg = null; go(Screen.TimeBank) },
                    onHome = { goHome() },
                )
                else -> {
                    val showBar = screen in TAB_SCREENS || screen == Screen.WorkoutPlans
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (screen) {
                                Screen.Home -> HomeScreen { go(it) }
                                Screen.Focus -> FocusStartScreen { go(it) }
                                Screen.Study -> StatsHub({ go(it) }, { back() })
                                Screen.Workout, Screen.WorkoutPlans, Screen.WorkoutRoutine, Screen.WorkoutCreate, Screen.WorkoutDay, Screen.WorkoutAdd, Screen.WorkoutSet, Screen.WorkoutLibrary, Screen.WorkoutActive, Screen.WorkoutComplete, Screen.WorkoutStats, Screen.WorkoutHistory, Screen.WorkoutDetail ->
                                    com.pranav.focus.workout.WorkoutHost(screen, { go(it) }, { back() })
                                Screen.Stats -> StatsHub({ go(it) }, { back() })
                                Screen.Profile -> com.pranav.focus.ui.ProfileScreenRef { go(it) }
                                Screen.Apps -> AppsScreen { back() }
                                Screen.Callers -> CallersScreen { back() }
                                Screen.Setup -> SetupScreen({ back() }, reqNotif, reqRole)
                                Screen.Rules -> RulesScreen({ back() }, { go(Screen.NewRule) }, { go(Screen.StudyExempt) })
                                Screen.StudyExempt -> com.pranav.focus.ui.StudyExemptionsScreen { back() }
                                Screen.NewRule -> NewRuleScreen { back() }
                                Screen.Mirror -> com.pranav.focus.ui.MirrorScreen { back() }
                                Screen.Jar -> com.pranav.focus.ui.JarScreen { back() }
                                Screen.Rank -> com.pranav.focus.ui.RankScreen { back() }
                                Screen.Connections -> com.pranav.focus.ui.ConnectionsScreen { back() }
                                Screen.Security -> com.pranav.focus.ui.SecurityScreen { back() }
                                Screen.Plan, Screen.PlanSubjects, Screen.PlanAdd, Screen.PlanEdit, Screen.PlanReorder,
                                Screen.PlanDetail, Screen.PlanCalendar, Screen.PlanFocus, Screen.PlanSummary, Screen.PlanSettings ->
                                    com.pranav.focus.ui.PlanHost(screen, { go(it) }, { back() })
                                Screen.TimeBank -> TimeBankScreen({ back() }, { go(Screen.Rules) }, { go(Screen.Focus) }, { go(Screen.Setup) })
                            }
                        }
                        if (showBar) TabBar(if (screen == Screen.WorkoutPlans) Screen.Workout else currentTab) { go(it) }
                    }
                }
            }
        }
    }
}
