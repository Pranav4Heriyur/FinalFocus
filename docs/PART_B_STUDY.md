# PART B: STUDY (NEET question tracker). Engineer brief

Read `docs/COMMON_CONTEXT.md` first. Specs: `docs/STUDY_SECTION.md` (logic, data model; ignore its colour hexes), `docs/ALL_SCREEN_PROMPTS_MONO.md` "PART B" (B0 to B6, the mono mockups), `docs/DESIGN_SYSTEM_MONO.md`.

## FOLLOW THE DESIGN ZIP EXACTLY (do this before writing any UI)
Pranav has the design handoff zip for this part (the mockup images plus its handoff md describing the whole design system). He will unzip it into `docs/mockups/partB/` in the repo (or attach it in chat). **Read the handoff md and open every image with the Read tool, then build each screen to match it**: layout, spacing, type sizes, corner radii, icons, copy, states and the black-and-white scheme. If a mockup disagrees with a text spec or an old colour palette, the mockup wins. A screen with no mockup follows the same visual language and the prompt in `docs/ALL_SCREEN_PROMPTS_MONO.md`. If the zip contents are not available to you, stop and ask Pranav for them; do not invent the design.

## Goal
Replace the Study tab placeholder with a fast, light NEET tracker: Physics, Chemistry, Biology (always this order), question logging, accuracy and NEET marks, chapters, mock tests, a mistake notebook with spaced revision, and study stats. Logging must take under 10 seconds.

## Files you create
```
data/StudyModels.kt     Chapter, QuestionLog, MockTest, Mistake, enums (Subject, Source, MistakeType)
data/StudyDb.kt         SQLiteOpenHelper "study.db" (tables below) + simple DAO functions
data/StudyLogic.kt      pure: marks, accuracy, deltas, revision schedule, weak-chapter ranking
data/StudySeed.kt       NEET chapter list per subject (Class 11 + 12), embedded as Kotlin lists
ui/StudyRoot.kt         internal nav + S0..S6 screens (split into StudyHub.kt, StudyLog.kt, StudyMock.kt, StudyMistakes.kt, StudyStats.kt if large)
test: StudyLogicTest.kt
```
Wire-up: in `MainActivity.kt` replace `Screen.Study -> PlaceholderScreen(...)` with `Screen.Study -> StudyRoot()`. In `data/Weekly.kt` switch the questions counter to `studyall_<date>`.

## Data (SQLite, version 1)
```
chapter(id INTEGER PK, subject INT, name TEXT, unit_order INT)          -- seeded once
question_log(id PK, ts INT, subject INT, chapter_id INT, source INT, attempted INT, correct INT, wrong INT, skipped INT,
             minutes INT NULL, focus_session_id TEXT NULL, verified INT)  -- verified=1 only if tied to a completed locked session
mock_test(id PK, date TEXT, name TEXT, p_c INT,p_w INT,p_s INT, c_c INT,c_w INT,c_s INT, b_c INT,b_w INT,b_s INT, target INT)
mistake(id PK, log_id INT NULL, subject INT, chapter_id INT, ref TEXT, type INT, note TEXT, due TEXT, done_count INT)
```
Subject: 0 Physics, 1 Chemistry, 2 Biology. Source: NCERT, PYQ, Module, Mock. MistakeType: CONCEPT, SILLY, CALC, GUESS, TIME.
Settings (SharedPreferences "study_prefs"): NEET date, target score (default 600).

## Logic (pure, unit tested)
- `marks = 4*correct - wrong`; max 720 (P 180, C 180, B 360). `accuracy = correct / attempted` (0 if none). `attempted = correct + wrong` (skipped separate). Show the live line "Attempted 40, Accuracy 72%, Marks +112 (4 x 29 - 11)".
- Delta chips: accuracy and marks vs previous period (last 7 days vs the 7 before; or last mock vs previous mock). Small chips only, never big charts.
- Weak chapters: sort by accuracy ascending with at least 10 attempted; status dot thresholds: under 50% / 50-70% / over 70% (use filled/ring/empty marks, not colours).
- Revision schedule: expanding 1, 3, 7, 21 days from today; `done_count` selects the next interval; overdue = due <= today.
- Countdown to the NEET date in days.

## Time Bank and XP rule (decision already made, implement exactly)
Logging questions alone earns **no screen time** (unverifiable). A log counts as **verified** only if it is saved from the post-session "Log questions" action on `CompleteScreen` of a completed locked Focus session whose subject matches, or within 15 minutes after such a session completes. Implementation: `SessionController.complete` already sets `AppState.summary`; add a tiny `study_prefs` key `last_session_end_ms` + subject written by Part B when `onLog` fires (the existing `onLog` in MainActivity routes to `Screen.Study`; open S1 prefilled with the session's subject).
- Verified log: add attempted count to both `study_<date>` and `studyall_<date>`; unverified: only `studyall_<date>`. Then call `Earning.evaluate(ctx)` and `AppState.dataTick++`.
- XP: +1 per question attempted, +2 per correct, capped 300/day; +25 per mock logged. Use `store.xp += n`.

## Screens (match the mockups pixel-for-pixel in spirit)
S0 Hub (NEET countdown chip, three subject cards with accuracy ring + today count + delta chip, Today strip, weak-chapter chips, last mock card, "Log questions" capsule) -> S1 Quick log (subject seg, chapter picker sheet with search, source chips, three big stepper tiles Correct/Wrong/Skipped with live marks line, optional collapsed time + mistake-type row, Save) -> S2 Subject detail (ring, totals, seg Chapters/Mistakes/Trend, weakest-first list, "Practise weakest") -> S3 Chapter detail (accuracy, 8-session line chart, stats row, mistake donut, last 3 sessions, Log / Add to revision) -> S4A/4B Mock log + result (steppers per subject, live score, delta vs last test, bar to target, "where marks were lost", next focus) -> S5 Mistake notebook (filter chips, due-today pill, cards with Revised check; marking Revised advances the schedule) -> S6 Study stats (Week/Month/All, questions-per-day bars, accuracy trend, Δ table, study-day heatmap).
Wrong answers entered in S1 with a mistake type create `mistake` rows. Reuse `BarChart`/`buckets` from `ui/Stage2.kt`, `SegFull`, `FocusSheet`, `Wheel` from `ui/Components.kt`.

## Tests (`StudyLogicTest.kt`)
marks/accuracy edge cases (0 attempted), delta calc, revision intervals, weak-chapter ordering with the 10-attempt floor, mock total across subjects (P 118 + C 142 + B 252 = 512).

## Status log
(append: date, what shipped, APK link, known gaps)
