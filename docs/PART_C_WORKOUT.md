# PART C: WORKOUT (Strong-style logger + Effective Load model). Engineer brief

Read `docs/COMMON_CONTEXT.md` first. Specs: `docs/FOCUS_APP_WORKOUT_SPEC.md` (sections 2 to end: sitemap W0 to W18, data model, logic; ignore its colour palette), `docs/EFFECTIVE_LOAD_MODEL.md` + reference implementation `docs/effective_load.py` (**the numbers in section 8 of the model doc are the ground truth for your unit tests**), `docs/ALL_SCREEN_PROMPTS_MONO.md` "PART C" (C0 to C18 mockups), `docs/DESIGN_SYSTEM_MONO.md`.

## FOLLOW THE DESIGN ZIP EXACTLY (do this before writing any UI)
Pranav has the design handoff zip for this part (the mockup images plus its handoff md describing the whole design system). He will unzip it into `docs/mockups/partC/` in the repo (or attach it in chat). **Read the handoff md and open every image with the Read tool, then build each screen to match it**: layout, spacing, type sizes, corner radii, icons, copy, states and the black-and-white scheme. If a mockup disagrees with a text spec or an old colour palette, the mockup wins. A screen with no mockup follows the same visual language and the prompt in `docs/ALL_SCREEN_PROMPTS_MONO.md`. If the zip contents are not available to you, stop and ask Pranav for them; do not invent the design.

## Goal
A fast gym logger at least as good as Strong (plans, active workout, sets, rest timer, PRs, plate calculator, history, edit past workouts) with a better load model that works for any exercise or activity, including ones the app has never seen.

## Files you create
```
data/EffectiveLoad.kt    faithful Kotlin port of effective_load.py (pure, no Android imports)
data/WorkoutModels.kt    Exercise, Plan, PlanDay, Workout, WorkoutExercise, SetEntry, enums
data/WorkoutDb.kt        SQLiteOpenHelper "workout.db" + DAO; seed library from data/WorkoutSeed.kt
data/WorkoutSeed.kt      ~120 common exercises (name, primary/secondary muscles, equipment, type, base multipliers)
data/WorkoutLogic.kt     1RM (Epley), volume, PR detection, plate calc, rest suggestions, schedule resolution
ui/WorkoutRoot.kt        internal nav + screens (split into WorkoutHub/Plans/Active/History/*.kt as needed)
tests: EffectiveLoadTest.kt, WorkoutLogicTest.kt
```
Wire-up: add `Workout` to `enum class Screen` (one line); `MainActivity.kt`: `Screen.Workout -> WorkoutRoot(back = { back() })`; Home: add a "Today's workout" card that calls `go(Screen.Workout)` (Home already has Start Focus; place it below the Time Bank card).

## Step 1: port the model first (CI-green before any UI)
Port `effective_load.py` function by function into `EffectiveLoad.kt`: per-set effective load from reps, load, RPE/RIR and exercise multipliers; Hard-Set Equivalents (HSE) per muscle; unknown-exercise handling (infer muscle map and multiplier from name keywords + user-chosen type; flag `confidence`); Load Units (LU) for non-lifting activity from duration + effort; fitness/fatigue/form (Banister impulse-response with the constants in the doc). `EffectiveLoadTest.kt` must reproduce every worked example in model doc section 8 to the printed precision. Do not "improve" constants without Pranav's approval.

## Step 2: data and screens
Use the data model in the workout spec (Exercise, Plan, Day, Workout, SetEntry with weight, reps, RPE, type: normal/warm-up/drop/failure, notes). Weights stored in kg; show lb if `Store.imperial`. Screens in build order (ship each as soon as it works):
1. C8 Active workout + C9 set keypad sheet + C10 rest timer (foreground-safe: use `SystemClock.elapsedRealtime`, keep screen on, notification for rest end) + C13 finish summary + C14 PR celebration (white ring + "PR" badge, no confetti libs).
2. C0 hub, C1 to C7 plans/days/exercise picker/custom exercise/schedule (weekly or rotating cycle).
3. C11 exercise sheet (history, PRs, 1RM chart), C12 plate calculator, C15 quick log for non-lifting activity (duration + effort -> LU), C16 to C18 history, past detail, edit.
Resume an in-progress workout after process death (persist every set immediately).

## Time Bank, XP and integration (implement exactly)
- A workout is **verified** when it has at least 5 completed working sets (not warm-ups) and at least 20 minutes between start and finish, finished via the Finish button. Then: `workouts_<date> += 1`, `Earning.evaluate(ctx)`, `AppState.dataTick++`.
- Quick log (C15) and edited past workouts are **unverified**: no Time Bank, no XP beyond 5.
- XP on verified finish: 40 base + 5 per PR (max 4), `store.xp += n`.
- Strava already pays runs/rides. Do not log Strava runs as workouts. You may list them read-only in History from `store.activities`.
- Rest-day recommendation (optional, small): show on the hub from Fatigue/Form per the model doc.

## Out of scope
Health Connect, wearables heart rate, cloud sync/web tracker, social features.

## Status log
(append: date, what shipped, APK link, known gaps)
