# COMMON CONTEXT: read this first (Part B Study and Part C Workout engineers)

You are finishing one part of **Focus**, a private sideloaded Android app for **Pranav** (Samsung Galaxy A32 SM-A325, Android 13, 411x914 dp, Helio G80, so keep it light). Part A (lock, Time Bank, rules, Mirror, Jar, Rank, Stats, Profile, Strava, Notion, security) is DONE and merged on `main`. Several engineers work in parallel from the same GitHub repo `Pranav4Heriyur/FOCUS`, each on one part. **Do not touch another part's files.**

Standing user preference: be brief, work efficiently, no fluff. Final APK must stay **under 100 MB** (it is ~20 MB now). Add no heavy libraries.

## 1. Stack and hard constraints
- Kotlin 1.9.24, Jetpack Compose (BOM 2024.06.00, Material3 only), AGP 8.5.2, minSdk 30, targetSdk 33. Package `com.pranav.focus`.
- **No Navigation library, no Room/KSP, no Hilt, no image libs.** Use plain Compose state. For databases use framework `android.database.sqlite.SQLiteOpenHelper` (no dependency) or JSON in SharedPreferences for small data. Adding any dependency needs a strong reason and a size check.
- **You cannot compile locally.** The sandbox has no Android SDK. CI is the compiler (section 3). Write conservative Kotlin, add `@OptIn` where Compose says "experimental", and expect 1 to 3 CI rounds.
- No network calls in your part unless the spec says so. All data stays on the phone.

## 2. Repo map (what exists)
```
app/src/main/java/com/pranav/focus/
  MainActivity.kt        Root(): back stack list<Screen>, tab bar, session/blocked/lock gates
  data/Store.kt          single SharedPreferences "focus": session, xp, streak, focusDays, DayRec (Time Bank), activities (Strava), prefs
  data/Models.kt         Cond, AppRule, UnlockRule, DayRec, Activity, Entry, AgendaItem, Caller, Codec(JSON)
  data/Logic.kt          TimeBank, Rewards, Ranks, CallMatcher, ExitRules, Fmt, Polyline (pure Kotlin, unit tested)
  data/Weekly.kt         weekly stats + targets (reads study_<date>, workouts_<date>, activities)
  lock/                  FocusService, FocusAccessibilityService, Enforcer, Earning, SessionController (AppState), Reminder
  net/                   Secure (Keystore), Strava, Notion
  ui/Theme.kt            tokens C.*, Inter font, Ico, FocusCard, IconTile, PrimaryButton, GhostButton, FocusSwitch, ScreenHeader, Caps, fieldColors, bloom
  ui/Components.kt       AppIcon, SegFull, Chip, FocusSheet (bottom sheet), Wheel, WheelSheet, AppChooserSheet
  ui/Screens.kt          Screen enum, TabBar, HomeScreen, FocusStart, Setup, Apps
  ui/Stage2.kt           Mirror, Jar, Rank, Stats (BarChart, buckets), Profile
  ui/Stage3.kt           StravaHero, Connections, Security, Callers
  ui/RulesScreens.kt     Rules, NewRule, TimeBank, Blocked
app/src/main/res/drawable/ic_*.xml   ~110 Material Symbols vectors (use `Ico(R.drawable.ic_x)`; tint at use)
docs/                    specs (below). Design handoff images are in docs/mockups/ if present.
```
Read `ui/Theme.kt`, `ui/Components.kt` and one finished screen (`ui/Stage2.kt` MirrorScreen) before writing UI. **Match their patterns exactly.**

## 3. Build and CI loop (the only way to compile)
`.github/workflows/build.yml` runs unit tests + `assembleDebug` on every push to `main`, and publishes the APK to a GitHub Release `build-N`. It prints Kotlin errors as **annotations** because raw logs are not downloadable from the sandbox. Use this helper (save as `ci.sh`):
```bash
git add -A && git commit -qm "$1" && git push -q origin HEAD:main
sleep 45
id=$(gh run list --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch $id >/dev/null 2>&1
gh run view $id --json conclusion --jq .conclusion
gh api repos/Pranav4Heriyur/FOCUS/actions/runs/$id/jobs --jq '.jobs[0].id' | xargs -I{} gh api repos/Pranav4Heriyur/FOCUS/check-runs/{}/annotations --jq '.[] | .message' | grep -v "Node.js\|setup-java\|ubuntu\|exit code 1"
```
Latest APK link: `gh api repos/Pranav4Heriyur/FOCUS/releases --jq '.[0].assets[].browser_download_url'`.
Commit trailer on every commit: `Co-Authored-By: Claude <noreply@anthropic.com>`.

## 4. Parallel-work rules (avoid merge hell)
1. **Always `git fetch origin main && git rebase origin/main` before every push.** Push small and often (every working screen).
2. Own only your files: Part B owns `ui/Study*.kt`, `data/Study*.kt`, tests `Study*Test.kt`. Part C owns `ui/Workout*.kt`, `data/Workout*.kt`, `data/EffectiveLoad.kt`, tests `Workout*Test.kt` / `EffectiveLoadTest.kt`.
3. **Shared files you may touch, minimally (one-line edits only):**
   - `ui/Screens.kt` `enum class Screen`: Part C adds exactly one value `Workout`. Part B adds none (Study already exists).
   - `MainActivity.kt` `when (screen)`: Part B replaces the `Screen.Study -> PlaceholderScreen(...)` line with `StudyRoot(...)`. Part C adds `Screen.Workout -> WorkoutRoot(...)`.
   - `ui/Screens.kt` HomeScreen: Part C wires the existing "Today's workout" entry (add a card/button that calls `go(Screen.Workout)`).
   - `data/Store.kt`: do **not** edit. Store your data in your own SQLite file or your own SharedPreferences name. Read shared state through the keys in section 5.
4. **Own your navigation inside your root composable** (a small internal back stack with `mutableStateListOf`). Register a `BackHandler(enabled = stack.size > 1)` inside it so system back pops your stack first, and falls through to the app's back handling at depth 1.
5. Screens must reserve bottom padding for the floating tab bar (`bottom = 100.dp`) when shown as a tab.
6. Do not rename or reformat other people's code. Do not add a dependency without telling Pranav.

## 5. Integration contract with Part A (do exactly this)
- Store keys read by Part A (write them with `Store.get(ctx).sp.edit()...apply()`):
  - `study_<yyyy-MM-dd>` (Int): **verified** questions answered that day. Feeds Time Bank unlock rules (Cond.STUDY) and the "Solve 60 questions +20 min" action in `lock/Earning.kt`.
  - `studyall_<yyyy-MM-dd>` (Int): **all** logged questions that day (verified or not). Used for Rank weekly target and Stats. (Part B must change `data/Weekly.kt` to read `studyall_` for `questions`; this is the one allowed edit of that file.)
  - `workouts_<yyyy-MM-dd>` (Int): count of completed, verified workouts that day. Feeds Cond.WORKOUT and the "+10 min" action.
- After writing any of those, call `com.pranav.focus.lock.Earning.evaluate(ctx)` then `AppState.dataTick++` so the Time Bank, Home and Stats refresh.
- XP: `Store.get(ctx).xp += n` (see `Rewards` in `data/Logic.kt`).
- Time Bank rule from the spec: **manual or self-reported effort never earns screen time.** Each part says what counts as verified.
- Strava activities are cached in `store.activities` (`isRun`, `isRide`, `isWorkout`); Part C may show them in history but must not duplicate their payout (Earning already counts them).

## 6. Design rules (the app is strictly monochrome now)
The coloured palette in older docs (`FOCUS_APP_WORKOUT_SPEC.md` section 1, `STUDY_SECTION.md` colours) is **superseded**. Use `docs/DESIGN_SYSTEM_MONO.md` and `ui/Theme.kt`: pure black canvas, white text, greys `C.Text2/C.Text3`, hairline borders, white capsule primary buttons, white ring/bars. **Distinguish subjects/muscles by icon and label, never by hue.** Charts: white bars/lines, grey tracks. Inter font, big light numerals. Touch targets 44 dp+. The mockup images for each screen are the source of truth; `docs/ALL_SCREEN_PROMPTS_MONO.md` holds the text prompts that generated them.

## 7. Definition of done (every screen)
Real data in and out (no hard-coded sample numbers), empty state written, back works, no crash on rotate/process death (state in Store/SQLite, not only in memory), unit tests for all pure logic, CI green, and a short note appended to your part doc's "Status log" with the APK link.
