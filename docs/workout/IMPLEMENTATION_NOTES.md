# Workout module — implementation notes

Storage: `workout.db` (framework SQLite, like StudyDb). `study.db`, SharedPreferences and Store are untouched.
Layers (package `com.pranav.focus.workout`):
- Android-free, JVM-tested: `WorkoutSql` (SqlRunner + schema/migrations), `WorkoutCatalog` (seed data, ID maps), `WorkoutSeeder`, `WorkoutLogic`, `WorkoutRepo`, `WorkoutModels`.
- Android/Compose: `WorkoutDb`, `WorkoutImages`, `WorkoutUi`, `WorkoutHub` (C0), `WorkoutPlans` (C1/C2/C3), `WorkoutBuilder` (C4/C5), `WorkoutSetEntry` (C9), `WorkoutHost`.

Decisions
- Presets are never edited: day edits are copy-on-write rows in `plan_day_exercise`; saving the original order clears the override.
- History tables (`workout_session`, `set_log`) have no FKs and snapshot exercise names, so plan/routine edits and deletes never alter history.
- ID mapping: `exercise_alias` (legacy WorkoutAssets ids + asset-manifest ids -> catalogue ids), seeded explicitly.
- Images: 11 generated illustrations (NOT photographs) are used only where the movement matches. The manifest's back_squat->hack squat, barbell bench->chest press/DB bench, pull_up->dead hang pairings were rejected. Everything else uses the vector pictogram.
- Schedule rule (hard block): no run or ruck (M3/M4, as the morning routine or its alternative) may sit the day before G1/G3 (the week is cyclic: day 7 precedes day 1) or on the same day as them. `WorkoutRepo.assignDay` (and so `saveDay`/`copyDay`) throws a user-facing IllegalArgumentException and writes nothing; template-created plans are checked too. Only violations an edit would *introduce* are blocked, so a plan saved before the rule can still be fixed or edited elsewhere (it keeps showing the warning until fixed).
- Default schedule: the supplied table had M3 on day 3, the day before G3. Day 3 is now M5 Recovery Mobility; day 6 (M3 or M4) is the week's run/ruck. Installs seeded with v1 are migrated (SEED_VERSION 2) only if day 3 of the preset plan is still exactly what v1 seeded; user-changed days and user-created plans are not rewritten.

Known gaps: see final report.
