# FOCUS_NAVIGATION_AUDIT — Mermaid compliance + Workout tab conflict audit

**Build/test status: NOT RUN.** Nothing was compiled, no Gradle/emulator/tests. Source-level audit only; Kotlin edits are small but unverified by a compiler.

## 1. Navigation architecture (from source)
- No NavHost / Navigation-Compose / deep links / ViewModels-per-route. Navigation is a single hand-rolled back stack in `MainActivity.Root()` (`stack: SnapshotStateList<Screen>`, `go()`, `back()`), routed by the `Screen` enum (`ui/Screens.kt`). Workout screens render through `workout/WorkoutHost.kt`; cross-screen state lives in `WorkoutNav` (`WorkoutHub.kt`). Workout data is persisted in `workout.db`, so a live session survives screen changes.
- Tab screens (`TAB_SCREENS`, incl. Workout and Stats) clear the stack on `go()`. Manifest declares no workout deep links/notifications.
- The diagram image is large and low-resolution; node names were read where legible. Edge **targets** in crowded areas (notably the long routing lines) are uncertain and are marked below. I did not invent destinations.

## 2. Canonical workout routes found
| C-map | Screen enum / composable |
|---|---|
| C0 Workout Hub | `Screen.Workout` → `WorkoutHubScreen` |
| C1 Plans | `Screen.WorkoutPlans` → `WorkoutPlansScreen` |
| C2 Plan Detail | **uncertain**: nearest implementation is `Screen.WorkoutRoutine` → `WorkoutRoutineScreen` (routine detail); plan view opens the day builder |
| C3 Create Plan | `Screen.WorkoutCreate` |
| C4 Day Builder | `Screen.WorkoutDay` |
| C5 Add Exercise | `Screen.WorkoutAdd` (picker) |
| C6 Custom Exercise | nested `CustomExerciseSheet` inside C5 (no own route; consistent with "nested state") |
| C7 Template Hub | deferred; not implemented (templates are only cards inside C1/C3) |
| C8 Routines Library | `Screen.WorkoutLibrary` |
| C9 Set Entry | `Screen.WorkoutSet` → `WorkoutSetEntryScreen` (preserved) |
| C10 / C11 / C12 | `WorkoutComplete` / `WorkoutStats` / `WorkoutHistory` (list) + `WorkoutDetail` (selected session) |
| A Active Workout | `Screen.WorkoutActive` |
| D–F, H–L | dialogs/sheets inside Active (Rest Timer, Exercise Info, Finish confirm, Previous Sets, Edit Set, PR, Exit) — nested, correctly not routes. Overview (C)/Exercise Completed (K) are in-screen states. |

## 3. Duplicate / legacy search result
Searched all `.kt`, manifest, docs for legacy C13–C25 and alternate hubs/completion/history/stats/set-entry screens. **No duplicate or legacy workout screen implementation exists in source.** The `Screen` enum has exactly one destination per canonical screen; there is no second C10/C11/C12, no old hub, no second set-entry. The only C13–C25 references are in `docs/ALL_SCREEN_PROMPTS_MONO.md` (design-prompt text, not code; left untouched, not reachable). "Workouts" elsewhere (Home ring, Stats-tab chart, Profile text) are non-workout-tab summaries, not screens. Nothing was deleted.
The Plan tab's `PlanDetail`/`PlanFocus` etc. are the planner, unrelated to workout C2 despite the name (not a duplicate).

## 4. Defects found and corrected
1. **Back stack depended on entry point.** Starting a workout from Routine detail/Plans/Library left e.g. `[Workout, Plans, Routine, Active]`; after finish the code did `back(); go(C10)` so C10 "Done" and Exit returned to Routine/Plans, not C0. Fix: `startPlanned` and resume now do `go(Workout); go(Active)` (stack `[C0, A]`), and finish does `go(Workout); go(C10)` (stack `[C0, C10]`, also in the C9→A forwarding path). Exit/Done → C0 (matches diagram "Confirm exit → C0" and C10 → C0).
2. **Duplicate stacking.** `go()` could push the same screen twice (double tap) and loop list screens (e.g. Routine menu → "Open weekly schedule" stacked another Plans). Fix in `MainActivity.go()`: ignore a push equal to the top; for C1, C8, C11, C12-list and C10, return to the existing stack instance instead of pushing again. `back()` unchanged.
3. **Overall Stats → C12 missing.** Mermaid source: `OVERALL --> HISTORY`. The Workouts card on the Stats tab was inert; it now opens C12 history (`Screen.WorkoutHistory`); back returns to the Stats tab. (`Workout Load & PRs` is a terminal node in the source, so no link to C11 is implied.)
3b. **C12 → C11 missing.** Mermaid source: `HISTORY --> WSTATS` (and `WSTATS --> HISTORY`, already present). The history-list header's empty right slot now holds a chart button (same 48 dp footprint) that opens C11 via `WorkoutNav.openStats`; the singleton rule returns to the existing C11 instead of stacking, so C11↔C12 cannot loop.
4. Stale comment in `startPlanned` said it opens Set Entry; it opens Active (comment corrected).

Already correct (verified in source): C10 opens only after `finishSession` succeeds and status re-reads `completed`; repeated Finish taps are guarded (`saving`, idempotent `finishSession`); C10 "View Workout Details" → `openWorkoutDetails(id)` sets `detailSessionId` and opens C12 for that id; C10 Done clears `completedSessionId` and pops; C11 rows → C12 detail, See All → history; back from C12 detail returns to the originating screen (C10/C11/history list), so no C10↔C11↔C12 loop; leaving to another tab does not discard the session (hub shows resume).

## 5. Compliance table (diagram vs source)
| Diagram destination | Actual route | Reached from | Back goes to | Status | Action |
|---|---|---|---|---|---|
| WORKOUT tab → C0 | `Screen.Workout` | tab bar, Home card | Home/previous tab | MATCH | – |
| C0 → C1 Plans | `WorkoutPlans` | hub cards | C0 | MATCH | – |
| C0 → C8 Library | `WorkoutLibrary` | hub "View all" | C0 | MATCH | – |
| C0 → C10/C11/C12 | C11 stats card, C12 history card | hub | C0 | MATCH | – |
| C1 → C3 Create Plan | `WorkoutCreate` | "New Plan"/template | C1 | MATCH | – |
| C1 → C2 Plan Detail | `WorkoutRoutine` / `WorkoutDay` | tiles / "View Details" | C1 | MATCH (C2 ≈ routine detail; C2→C4 and C2→Active exist) | confirm mapping |
| C4 → C6 Custom Exercise (`BUILDER --> CUSTOM`) | sheet inside C5 | C4→C5→"Create custom exercise" | C5 | MISMATCH (retained) | C6 nested in C5, not directly from C4; left (no redesign) |
| Bottom bar: diagram "five tabs" (Home, Focus, Study, Workout, Stats, Profile) | app bar has 8 items incl. Plan and Time Bank | – | – | MISMATCH (non-workout, not changed) | decide: Time Bank is a Home sub-destination in the diagram; Plan tab absent from diagram |
| C1 → C7 | none | – | – | MISSING (deferred by design) | none |
| C3 → C4 Day Builder | `WorkoutDay` | after create | C3/C1 | MATCH | – |
| C2 → C4 | `WorkoutDay` | routine "schedule" actions | C2 | MATCH | – |
| C4 → C5 Add Exercise | `WorkoutAdd` | Add button | C4 | MATCH | – |
| C5 → C6 Custom | sheet in C5 | button | C5 | MATCH (nested) | – |
| C4/C2 → Active Workout | `WorkoutActive` | Start buttons | **C0** (was origin) | MISMATCH → fixed | stack reset |
| Active → C9 Set Entry | `WorkoutSet` | exercise row | Active | MATCH | – |
| Active → Rest Timer / Exercise Info / Add Exercise / Previous Sets / Edit Set / PR / Exercise Completed | dialogs/sheets | Active | Active | MATCH (nested) | – |
| Active → Finish Confirmation → (save) → C10 | `FinishDialog` → `WorkoutComplete` | Active | C0 (Done) | MATCH after fix | – |
| Finish Confirmation → Cancel | dismiss | – | Active | MATCH | – |
| Active → Exit Workout Confirmation → C0 | `ExitDialog` → `leave()` | Active/back | C0 | MATCH after fix | – |
| Exit Confirmation → Continue | dismiss | – | Active | MATCH | – |
| C10 → C12 | `WorkoutDetail` | "View Workout Details" | C10 | MATCH | – |
| C10 → C0 | Done | C10 | – | MATCH after fix | – |
| C12 ↔ C11 | C11 → C12 (See All/rows); C12 list header chart button → C11 | – | caller | MATCH after fix | – |
| STATS tab → Overall Stats | `Screen.Stats` | tab | Home | MATCH | – |
| Overall Stats → C12 (`OVERALL --> HISTORY`) | Workouts card → `WorkoutHistory` | Stats tab | Stats tab | MISSING → fixed | – |
| Overall Stats → Workout Load & PRs | in-screen card, no outgoing edge in source | Stats tab | – | MATCH | – |
| All non-workout nodes (Splash, Onboarding, Auth, Focus, Study, Plan, Profile, Time Bank, Settings subpages…) | unchanged | – | – | NOT AUDITED in depth / untouched | out of scope; Sign Up/Log In/Splash flows were not located as separate routes — **manual confirmation needed** |

## 6. Remaining uncertain / missing / not changed
- C2 mapping (nearest match only). The Mermaid source was supplied after the image and used to resolve edges; the earlier image-only guess (Stats→C11) was corrected.
- Auth/Splash/Onboarding nodes of the diagram have no corresponding `Screen` routes in the source (setup uses `Screen.Setup`); left as-is, not invented.
- C7 intentionally absent. Not changed: schemas, timing, C0–C12 visuals.
- A user who taps another tab mid-workout leaves the Active screen (state persists; resume via hub) — documented, not altered.

## 7. Final route inventory
| Route/screen | Canonical destination | Entry point | Back destination | Status |
|---|---|---|---|---|
| Workout | C0 | tab, Home card | previous tab | canonical |
| WorkoutPlans | C1 | C0, C2 menu | C0 (no repeat stacking) | canonical |
| WorkoutRoutine | C2 (uncertain) | C0, C1, C8 | caller | canonical |
| WorkoutCreate | C3 | C1 | C1 | canonical |
| WorkoutDay | C4 | C1, C2, C3, C8 | caller | canonical |
| WorkoutAdd (+custom sheet) | C5/C6 | C4 | C4 | canonical |
| WorkoutLibrary | C8 | C0 | C0 | canonical |
| WorkoutActive | A | start/resume | C0 | canonical (fixed) |
| WorkoutSet | C9 | A | A | preserved |
| WorkoutComplete | C10 | A/C9 finish | C0 (Done) | canonical (fixed) |
| WorkoutStats | C11 | C0, Stats tab | caller | canonical (Stats entry added) |
| WorkoutHistory | C12 list | C0, C11 | caller | canonical |
| WorkoutDetail | C12 detail | C10, C11, history | caller | canonical |
| Screen.Stats | Overall Stats | tab | Home | non-workout, entry added |
| C7 | – | – | – | deferred |

## 8. Files changed
- `MainActivity.kt` — `go()` duplicate/loop guard (`WORKOUT_SINGLETONS`).
- `workout/WorkoutHub.kt` — `startPlanned` resets stack to C0 before Active; comment.
- `workout/WorkoutActive.kt` — finish and C9-forward paths land on `[C0, C10]`.
- `ui/Stage2.kt` — Stats-tab Workouts card opens C12 (`OVERALL --> HISTORY`).
- `workout/WorkoutHistoryUi.kt`, `workout/WorkoutHost.kt`, `workout/WorkoutHub.kt` — C12 → C11 button via `WorkoutNav.openStats`.
- `FOCUS_NAVIGATION_AUDIT.md` (this file); `FOCUS_STATUS.md` unchanged.

## 9. Checkpoint integrity
Full project in the ZIP (not only changed files). C0–C12 follow the canonical map; C7 deferred; no duplicate workout destination exists; C9 untouched; the diagram was compared with source (not merely restated), with uncertainties listed; build/test: **NOT RUN**.
