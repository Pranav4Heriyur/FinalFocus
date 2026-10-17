# STUDY SECTION (NEET question tracker): screens, logic and ChatGPT prompts

Add-on to `FOCUS_APP_WORKOUT_SPEC.md`. Same matte-black design system. Append to every prompt:
*"Use the Focus matte-black design system. Study subject colours: Physics indigo #5E5CE6, Chemistry sky #64D2FF, Biology lime #C7F23A. Always show subjects in the order Physics, Chemistry, Biology."*

## 1. What it tracks (kept light)

- Per question batch: subject, chapter, source (NCERT / PYQ / Module / Mock), attempted, correct, wrong, skipped, minutes.
- NEET scoring is computed automatically: **marks = 4 x correct - 1 x wrong**, maximum 720 (Physics 180, Chemistry 180, Biology 360, which can be split into Botany 180 and Zoology 180).
- **Accuracy** = correct / attempted. **Net marks** = marks after negatives.
- **Point-wise difference (Δ)** is deliberately small: one chip per subject or chapter showing the change in accuracy and marks against the previous period (last week, or last test). It's never a big chart.
- Time per question and mistake type (Concept, Silly, Calculation, Guess, Time) are optional one-tap fields.
- **Time Bank rule:** logging questions alone earns no screen time, because it can't be verified. Minutes come from a locked Focus session tagged with a subject. Question logs earn XP and feed the stats only.

## 2. Sitemap

```
STUDY
├── S0 Study hub (Physics, Chemistry, Biology cards + today + last mock)
├── S1 Quick log (fast stepper entry)
├── S2 Subject detail (chapters sorted weakest first)
│   └── S3 Chapter detail (trend, mistake types, recent sessions)
├── S4 Mock test: log + result (subject split, Δ vs last test and target)
├── S5 Mistake notebook (wrong questions, revision due dates)
└── S6 Study stats (weekly subject comparison + Δ table)
```

**Navigation suggestion:** the tab bar becomes Home, Focus, Study, Stats, Profile. "Rules" moves inside the Focus tab as a segmented section.

## 3. Prompts (send one per message)

### S0: Study hub
```
Screen S0: STUDY HUB. Title "Study", a countdown chip "NEET: 218 days" (user-set date). Three subject cards stacked in order Physics, Chemistry, Biology, each with a squircle icon in its subject colour, an accuracy ring (71%), "Today: 32 questions", and a tiny delta chip like "+3% vs last week". Below, a compact "Today" strip: Questions 84, Accuracy 71%, Net marks +196. A "Weak chapters" row of 3 chips (e.g. "Rotational Motion 48%"). A card "Last mock: 512 / 720" with a small "+24" delta chip. A large solid blue capsule "Log questions" pinned above the tab bar. Floating blurred tab bar: Home, Focus, Study (active), Stats, Profile.
```

### S1: Quick log
```
Screen S1: QUICK LOG (a bottom-sheet style full screen, must be fast, thumb-friendly). A subject segmented control (Physics | Chemistry | Biology), a chapter picker row, a source chips row (NCERT, PYQ, Module, Mock). Three big stepper tiles: Correct (green), Wrong (red), Skipped (grey), each with large - / + buttons. A live result line at the bottom of the tiles: "Attempted 40, Accuracy 72%, Marks +112 (4 x 29 - 11)". Optional collapsed row "Time and mistake types". Solid green "Save" capsule.
```

### S2: Subject detail
```
Screen S2: PHYSICS DETAIL. Header with the subject ring (accuracy 68%) and totals (questions this month 612, net marks 1,420). Segmented control: Chapters | Mistakes | Trend. Chapter list sorted weakest first: chapter name, attempted count, a slim accuracy bar in the subject colour, a delta chip (+5% / -3%), and a status dot (red under 50%, amber 50-70%, green over 70%). Keep each row compact. A "Practise weakest" solid capsule.
```

### S3: Chapter detail
```
Screen S3: CHAPTER DETAIL "Rotational Motion". Big accuracy number 48% with a delta chip "-4% vs last week". A smooth line chart of accuracy over the last 8 sessions. A compact stats row: attempted 96, correct 46, wrong 38, avg 62 s per question. A donut of mistake types (Concept, Silly, Calculation, Guess, Time) with a one-line legend. A list of the last 3 sessions (date, source, score). Buttons: "Log questions" and "Add to revision".
```

### S4: Mock test
```
Screen S4A: MOCK TEST LOG. Fields for test name and date. Three subject blocks in order Physics, Chemistry, Biology, each with correct / wrong / unattempted steppers. A live total at the top "Score 512 / 720".
Screen S4B: MOCK RESULT. A giant score "512 / 720" in white with a delta chip "+24 vs last test" and a thin progress bar to the target (600). Three subject bars out of 180, 180, 360 (Physics 118/180, Chemistry 142/180, Biology 252/360), each with a tiny delta chip. A "Where marks were lost" row: Negative marking -41, Skipped 22 questions, Silly mistakes 9. A "Next focus" line naming the weakest subject. Keep it clean, not data-heavy.
```

### S5: Mistake notebook
```
Screen S5: MISTAKE NOTEBOOK. Filter chips (All, Physics, Chemistry, Biology) and a "Due today: 6" pill. Cards, each: question reference ("NCERT Ex 7.12" or "PYQ 2023"), chapter, a mistake-type tag (Concept / Silly / Calculation / Guess), a one-line note, a revise-due chip (Today, 3d, 7d, 21d), and a round "Revised" check. Keep it compact and scannable.
```

### S6: Study stats
```
Screen S6: STUDY STATS. Segmented control Week | Month | All. A grouped bar chart of questions per day with the three subject colours. A small Accuracy trend (three thin lines). The compact DIFFERENCE TABLE: rows Physics, Chemistry, Biology; columns This week, Last week, Delta (green up arrow / red down arrow), showing accuracy and net marks. A heatmap calendar of study days. Nothing extra: keep it brief.
```

## 4. Data model add-on

```
QuestionLog(id, ts, subject, chapterId, source, attempted, correct, wrong, skipped, minutes?, focusSessionId?)
Chapter(id, subject, name, unitOrder)
MockTest(id, date, name, phyCorrect, phyWrong, phySkipped, chemCorrect, ..., bioCorrect, ...)
Mistake(id, logId?, subject, chapterId, ref, type[CONCEPT|SILLY|CALC|GUESS|TIME], note, due, doneCount)
```

Revision due dates use an expanding schedule (1, 3, 7, 21 days). Spaced practice is among the best-supported study techniques (Cepeda et al. 2006), but verify the reference before citing it.
