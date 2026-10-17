# FOCUS_STATUS — Workout Accuracy & Historical Integrity Repair

## Build / test status — NOT COMPILED, NOT RUN
This pass was code-level implementation and review only. **Nothing was compiled, no Gradle build, emulator or unit test was run.** Kotlin may contain compile errors. The tests I touched/added in `WorkoutFinalRepairTest` / `WorkoutDbTest` are unrun. First step on your machine: `./gradlew testDebugUnitTest assembleDebug`.
Note: the project uses a hand-rolled SQLite helper (`workout.db`, `WorkoutSchema`), **not Room**; "schema version" below is `WorkoutSchema.VERSION` (5 → 6).

## Exact files changed
Main (`app/src/main/java/com/pranav/focus/workout/`): `WorkoutSql.kt`, `WorkoutTiming.kt`, `WorkoutModels.kt`, `WorkoutLogic.kt`, `WorkoutRepo.kt`, `WorkoutActive.kt`, `WorkoutSetEntry.kt`, `WorkoutHistoryLogic.kt`, `WorkoutHistoryUi.kt`, `WorkoutStatsLogic.kt`, `WorkoutStats.kt`.
Tests: `WorkoutFinalRepairTest.kt`, `WorkoutDbTest.kt`. Docs: `FOCUS_STATUS.md`. No Git operations; C0–C9 layout/visuals untouched.

## Database (v5 → v6, additive only)
`WorkoutSchema.V6`, applied through the existing `applySafely` path (repeat-safe):
- `rest_log` += `rest_uid`, `started_at`, `outcome` (NULL for old rows = "not recorded"; their seconds still count) + unique index `(session_id, rest_uid)`.
- Unique indexes on `session_pr(session_id, exercise_id)` and `session_snapshot_ex(session_id, exercise_id)`. Before creating them, exact duplicate rows in those two tables (none expected) are collapsed; this is the only DELETE and touches no workout/set data.
- No table dropped or recreated; no durations/rests backfilled; no zeros invented. Legacy availability is carried by existing `timing_v` (0/1) and `snap_v` (0).

## Canonical timing model (`WorkoutTiming`, used by live screen, C10, C11, C12, Hub)
- `timing_v = 2` (all new sessions): `started_at` = real start (stamped once when Play is pressed), `ended_at` = finish, `paused_ms` and `rest_ms` accumulated separately.
- **elapsed** = `ended_at − started_at` (includes pauses and timed rests). **active** = `max(0, elapsed − paused − rest)`. Paused is clamped to elapsed; no negative values.
- `timing_v = 1` (previous repair; start was shifted forward by pauses): read with its original meaning, elapsed = (end−start)+paused. `timing_v = 0` (oldest): elapsed only; active/paused/rest are `null` and shown as unavailable ("Duration"), never 0.
- Timed rest = only rests run on the rest timer. Gaps between sets are never counted.
- Live clock (C8) = `WorkoutTiming.runningMs` (excludes pauses, frozen while paused).

## Pause / resume
- `pauseSession` opens a pause only if none is open (persisted start in `meta`); `resumeSession` closes it and adds its duration once, then clears it; repeated calls are no-ops. Duration uses the in-process monotonic clock when the pause began in this process, otherwise the persisted wall-clock start; clamped ≥ 0 and ≤ workout time so far.
- Finishing while paused closes the pause (same `closePause`) inside the finish transaction. A restored paused workout reads the single persisted pause start, so overlapping intervals cannot be created.
- Pausing the workout also pauses a running rest (and resume gives it back); starting a rest is blocked while the workout is paused. So a pause and a rest never overlap and nothing is subtracted twice.
- `beginSession` is idempotent (meta flag; refused if sets exist, paused, or already begun), so re-opening the screen can't reset the clock. The Play/“started” state is restored from the DB after process death.

## Rest tracking (one shared tracker)
- `RestTimerState` (monotonic countdown) is the only rest measurer. Active Workout **and C9 Set Entry now drive the same `RestTimer`** (C9 visuals and set-entry behaviour unchanged; its old local, untracked countdown was removed). Both bind via `bindRestTimer`.
- Each rest has a uid and is reported once with an outcome: `completed` (ran to zero), `skipped` (user reset/skip), `cancelled` (replaced by a new rest or workout ended). Recorded seconds = countdown time actually run; a stopped rest stops counting; time with the rest itself paused is not rest.
- `rest_log` insert is `INSERT OR IGNORE` on (session, uid): reopening/restoring cannot double count. `rest_ms` = SUM(seconds) of the session's rows, written at finish.

## Surviving backgrounding / process death
- Rest snapshot (kind|total|wall-end|label|uid|start) is persisted on every change. After restart: paused stays paused; a running rest whose stored end time passed is recorded once as `completed` (full duration, proven by the stored end) and shown finished; one still running continues with the same uid. Old snapshots without uid get a fresh one.
- If a workout is finished and a rest is still stored as open (timer not in memory), `finishSession` closes it from the snapshot (cancelled/completed, actual elapsed) once.
- Pause start, begin stamp and accumulated durations are persisted. Rule kept from the existing lifecycle: a workout left *running* keeps counting by wall-clock while the app is closed (it was running); a workout left *paused* does not. Nothing tries to guess whether the user was still exercising.

## Historical integrity
- C10/C12 read through one loader (`WorkoutHistory.load`). Workouts with a snapshot (`snap_v=1`) use **only** the snapshot (name, order, equipment, muscles, type, category label, routine name, PRs) plus recorded sets; **the live-definition fallback was removed for them** (`emptyMap()`), so renaming/editing/deleting an exercise/routine can't change them. Deleting a plan/routine never touches history (soft links, unchanged).
- Legacy (`snap_v=0`): no invented names/muscles/PRs. Muscles come from current exercise records and the UI says so (existing label); PRs are shown only where earlier saved sets prove them, and C12 now labels them as recomputed.
- C11 muscle distribution now uses each workout's snapshot muscles (`snapshotPrimaries`) and falls back to current definitions only for legacy workouts.
- Saved workouts are immutable: `updateSet`, `deleteSet`, `logSet` are rejected unless the session is active, so an edit can't leave stale PRs/volume.
- Workout deletion is not supported anywhere in the app, so there is nothing to recalculate; adding it later must recompute from persisted rows (C11 already does).

## Duplicate saves / duplicate PRs
- `finishSession` is a transaction that returns false unless status is `active` — second tap/call changes nothing (no second record, volume, snapshot, PR, rest). Active and C9 both treat "already completed" as success.
- `logSet` is idempotent per (session, exercise, set number, warm-up flag) → double-tap on “Log Set” can't double volume.
- PRs are computed once at finish from SAVED earlier sessions (unsaved never counted) and stored in `session_pr` with a unique (session, exercise) index. PR comparison now uses only weighted, rep-based working sets (`load>0, reps>0`) for the same resolved exercise, in `computePrs`, `priorWorkingSets`, `prSetsFor` (live celebration) and C11's PR/lift series; rep-based PRs are not inferred.

## C10 / C11 / C12 agreement
- One timing definition (`WorkoutTiming.of`) and one saved record. C10 shows Elapsed + Active separately (only when tracked); C12 shows the same values plus a one-line definition and, for tracked sessions, timed rest labelled as "time run on the rest timer". Hub "Minutes" now = elapsed minutes (was end−start, which differed for paused sessions).
- C11: aggregates come from persisted completed sessions/sets only. Total training time = sum of elapsed (unchanged label "Elapsed time"); active total/average use only sessions that have an active value ("N of M"); elapsed average = all saved workouts in range; volume average = gym workouts with ≥1 weighted set. Elapsed and active are never mixed.

## Remaining limitations / future pass
- Nothing compiled or run; migration SQL not executed against a real device DB.
- Legacy workouts: timing/snapshot unavailable stay unavailable; C11 muscle shares include current-definition data for those (no per-screen note added).
- Rest outcome is stored but not displayed; per-set rest and left/right side are still not recorded.
- A rest running when the process is killed is only recorded when the app reopens or the workout is finished (using the stored end time); if the user never returns it is never recorded.
- Wall-clock changes (manual time change) are only clamped, not detected; no workout deletion/edit-after-save feature exists.
- `RestTimerState.restore`/`closeFromSnapshot` assume the persisted wall-clock end time is truthful.
- Unrun tests: `WorkoutFinalRepairTest` (rest uid dedupe, outcomes, idempotent log/rejected edits, timing clamps), `WorkoutDbTest.workoutTimeStartsOnPlayAndPauseShiftsStart` rewritten for the canonical model.
