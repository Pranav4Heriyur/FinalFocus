# FOCUS APP: ALL REMAINING SCREEN PROMPTS (black + premium white)

**How to use**
1. Start a fresh ChatGPT chat and paste the master prompt from `DESIGN_SYSTEM_MONO.md` (section 1). Wait for "ready".
2. Home 1A and 1B you already have (sent in chat).
3. Send the prompts below one per message, in order: Part A (core app), Part B (study), Part C (workout).
4. Every prompt ends with the same line, which is already included below as `[STYLE]`. Always append it:

`[STYLE]` = *"Use the Focus black-and-white premium system. No colour. Show the floating blurred tab bar (Home, Focus, Study, Stats, Profile) unless this screen says no tab bar."*

Fixes: "Render at 390x1100, don't squish" / "Strictly black, white and grey" / "More bloom, larger light numerals, more empty space".

---

# PART H: HOME

### H1A: Home, top of the scroll
```
Screen H1A: HOME, top of the scroll (the first screen the user sees), cut off naturally at the bottom so it reads as a scroll. Top to bottom:
(a) Header: "Good evening, Pranav", the date, a small avatar in a dark squircle, and a dark pill on the right "Watch: Me" with a small white dot.
(b) HERO (biggest element, full width, tall): the Strava card. A muted dark-grey map with a glowing WHITE route line (3px, soft bloom, brightening toward the finish, ring marker at the start, solid white dot at the end). A frosted dark overlay shows "Morning Run" and 3 huge light-weight white numerals: 5.2 km, 5'48"/km, 30:12. Below: heart rate, elevation, calories in small grey caps. A "Run | Ride" segmented control where the selected segment is an inverted white pill. A tiny "Powered by Strava" mark in the corner.
(c) SPIDER WEB card "Today's Balance": a 6-axis radar chart with a bright white filled polygon (white at 15% fill, solid white outline with bloom) over faint web rings. Axes: Focus, Workout, Running, Study, Sleep, Discipline. One line at the bottom: "Weakest: Sleep".
(d) PHONE SCREEN TIME, a compact one-row card: "3h 12m today" as a big numeral, a slim segmented bar in white brightness levels, and 3 tiny app icons with minutes (Instagram 48m, YouTube 41m, Spotify 22m). No expand arrow, no extra detail.
[STYLE]
```

### H1B: Home, scrolled down
```
Screen H1B: HOME, scrolled down, continuing from H1A. Tall layout (390x1100) so nothing is squished. The top edge shows the bottom of the screen-time card for continuity. Then:
(a) AGENDA card "Today's Agenda": 5 quick-glance rows, each with the time on the left, a title, and a round check circle on the right. Rows: 6:00 AM Run, 9:00 AM Focus: Physics, 1:00 PM Gym: Push Day, 4:00 PM Focus: Chemistry, 9:00 PM Accountability Mirror. Completed rows are dimmed (40%) with a filled white check; the current row is brightest with a white hairline outline and a soft bloom. "3 of 5 done" top right and a small round "+" button.
(b) TIME BANK pill-card: a huge white numeral "47 min" with bloom, caption "earned today", a thin white bar showing earned vs. spent, and a small "Unlock" capsule.
(c) STUDY strip (compact): "Study today: 84 questions, 71% accuracy, +196 marks", three tiny icon tiles for Physics, Chemistry, Biology (different glyphs, different brightness).
(d) DAILY RINGS card: 3 concentric white rings (outer pure white, middle 70%, inner 40%, different thickness) for Focus, Workout, Screen-time budget, with the numbers on the right.
(e) STREAK and XP strip: a white flame glyph with "12 days", a slim white XP bar, and the rank "Uncommon" in tracked caps.
(f) Two half-width cards side by side: "Start Focus" (inverted white round play button, last session length) and "Today's Workout" (name, sets done).
(g) WEEKLY FOCUS chart: 7 rounded bars, today solid white with bloom, the others white at 35%.
[STYLE]
```

---

# PART A: CORE APP

### A1: Permissions setup
```
Screen A1: SET UP YOUR LOCK. Title "Set up your lock" and a one-line subtitle "5 permissions make the lock work". Dark rows with white glyph tiles: Accessibility (blocks apps), Display over other apps (timer overlay), Usage access (screen time), Call screening (allowed callers), Notifications. Each row has a status circle (empty = needed, filled white check = granted) and a small outlined "Allow" capsule. A progress line "3 of 5 granted". A solid white capsule "Continue" at the bottom. Footnote "You can change these later in Settings". No tab bar. [STYLE]
```

### A2: Focus setup
```
Screen A2: START FOCUS SESSION. A big wheel-style time picker (45 min) in huge light numerals. Session name field with a subject chip row (Physics, Chemistry, Biology, Workout, Other, each with a different glyph). Preset chips 25 / 45 / 90 / Custom. Glass-free dark list rows: "Allowed apps: Phone, Claude, Gemini" with Edit, "Allowed callers: 4 contacts" with Edit, and a "Strict lock" toggle. A solid white capsule "Begin Focus" with bloom. [STYLE]
```

### A3: Locked timer
```
Screen A3: LOCKED FOCUS SESSION. Fully immersive, no tab bar. A huge centered white countdown ring with bloom, "38:12" in a giant light numeral, "Deep Work: Physics" beneath, a small "Locked" pill with a lock glyph. Calm, almost empty. At the bottom 3 round dark icon buttons (Phone, Claude, Gemini). Below those, tiny grey text "Hold to exit". Extremely minimal and serene. [STYLE]
```

### A4: Exit friction
```
Screen A4: LEAVE EARLY? A dark bottom sheet over the dimmed timer. Title "You're at 40%." Subtitle "You can do more than you think." A warning card "Leaving costs 50 XP and resets your 12-day streak". A circular hold-to-confirm ring button (60 s) in white, then a text field "Type: I choose distraction over my goals", then an OUTLINED capsule "Exit session" (disabled) and a solid white capsule "Stay focused". [STYLE]
```

### A5: Session complete
```
Screen A5: SESSION COMPLETE. A large white check inside a ring with a bloom burst and fine sparks. "45:00 locked in", "Physics". Stats row: Focus time, Streak 13 days, XP +40. A big card "+9 min earned" with a huge white numeral and caption "added to your Time Bank". A "Log questions" outlined capsule and a solid white "Done" capsule. [STYLE]
```

### A6: Rules
```
Screen A6: APP RULES. Segmented control (Limits | Unlock rules | Blocked). Limits: dark rows with app icons (Instagram, YouTube, Spotify), usage vs limit as a slim white bar, and a slider; a "Locked by default" toggle per app. Unlock rules: cards like "Run 5 km -> unlock Instagram 30 min" with a live progress ring (3.1 / 5 km) and a "Verified by Strava" tag. A floating white capsule "+ New rule". [STYLE]
```

### A7: New unlock rule
```
Screen A7: NEW RULE. Sentence builder: "IF [Run 5 km] THEN unlock [Instagram] for [30 min]". Tappable chips for each blank. Condition picker sheet previews: Run distance, Ride distance, Workout done, Focus minutes, Study questions (with accuracy over 60%). A small "Verified sources only: Strava, Health Connect, locked focus, workout log" note. "Resets at midnight" row. A "Save rule" white capsule. [STYLE]
```

### A8: Allowed callers
```
Screen A8: ALLOWED CALLERS. Large title, subtitle "Only these numbers can reach you during focus". A search bar, a list of contacts with monogram avatars, numbers and toggles, swipe-to-delete hint, an outlined "Import from contacts" capsule and a white "+ Add number" capsule, and a stat card "Blocked calls this week: 7" with a tiny bar sparkline. [STYLE]
```

### A9: Allowed apps
```
Screen A9: ALLOWED APPS DURING FOCUS. Subtitle "Everything else is locked". A grid of app icons in dark squircle tiles: Phone, Claude, Gemini, Calculator, Notes with a white check badge for allowed apps. A search bar and a "Select apps" list with toggles for the rest. A footer note "Settings is always blocked during a session". [STYLE]
```

### A10: Time Bank
```
Screen A10: TIME BANK. Hero: a huge white numeral "47 min" with bloom and caption "earned today", a thin earned-vs-spent bar. "Locked apps" dark rows (Instagram, YouTube, Spotify) with lock glyphs, minutes available, and a white "Unlock 15 min" capsule on each. An "Earn more" list with progress rings: Focus 25 min, Run 5 km, Workout, Study 60 questions, each showing "+ minutes". "Resets at midnight" countdown at the bottom. [STYLE]
```

### A11: Accountability Mirror
```
Screen A11: ACCOUNTABILITY MIRROR (nightly review, 9 PM). Title "Face the day." Agenda items as dark cards with a two-state control: Done (white) / Not done (outlined). A bold line "Missed 2 items: -20 min tomorrow". A free-text field "One thing you avoided today". A solid white capsule "Face it". Quiet, serious, lots of black space. [STYLE]
```

### A12: Cookie Jar
```
Screen A12: COOKIE JAR (the hardest things you've done). A scrolling list of entries (date, title like "Ran 10 km in rain", "6-hour study block") in dark cards with a white star glyph, a "+ Add" white capsule, and a header line "Remember this when you want to quit". [STYLE]
```

### A13: Rank and weekly targets
```
Screen A13: RANK. A big rank name in tracked caps "UNCOMMON" with a thin white progress bar and the next rank below. A ladder of ranks (Recruit, Grinder, Uncommon, Relentless, ...) as a vertical timeline with the current one lit. A "This week's targets" card: Focus 12 h, Runs 3, Workouts 4, Questions 400, each with a progress bar, plus a caption "Hit all four and next week's targets rise 5%". [STYLE]
```

### A14: Stats (overall)
```
Screen A14: STATS. Segmented Week | Month | Year. Cards: focus hours (rounded bars, today solid white), distance (white line, dashed line for last week), screen-time donut in 3 white brightness levels with app names, workout load (bars), a heatmap of focus streaks in five white steps. Entries to deeper pages: "Workout progress", "Study stats". [STYLE]
```

### A15: Profile and settings
```
Screen A15: PROFILE. Avatar, name, rank, streak. Grouped dark inset lists with white glyph tiles: Connections, Watch, Permissions, Security, Notifications tone (Normal / Hard / Goggins), Units, Export data, About. A "Danger zone" group with outlined rows only. [STYLE]
```

### A16: Connections and watch toggle
```
Screen A16: CONNECTIONS. Rows for Strava, Notion, Health Connect each with a "Connected" status (white dot) or "Connect" outlined capsule. Below, a prominent card "Watch worn by me" with a large toggle and the caption "Data counts only while ON". A list of recent windows ("Today 6:02 AM - 7:15 AM, 1h 13m") and a small timeline bar of today showing ON segments in white. [STYLE]
```

### A17: Security
```
Screen A17: SECURITY. Rows with toggles: Biometric lock, Lock app on exit, Hide in recents, Encrypt data (always on, shown locked), Auto-lock after 30 s. A "Connected tokens" row: "Stored in secure hardware" with a lock glyph. A subtle shield illustration made of white line art. [STYLE]
```

---

# PART B: STUDY (NEET)

Subjects are told apart by glyph and brightness, never colour. Always order Physics, Chemistry, Biology.

### B0: Study hub
```
Screen B0: STUDY HUB. Title "Study", countdown chip "NEET: 218 days". Three stacked subject cards (Physics, Chemistry, Biology), each with a distinct glyph tile, an accuracy ring in white (71%), "Today: 32 questions" and a tiny delta chip "+3% vs last week". A "Today" strip: Questions 84, Accuracy 71%, Net marks +196. "Weak chapters" chips (e.g. "Rotational Motion 48%"). A "Last mock: 512 / 720" card with a "+24" delta chip. A solid white capsule "Log questions" above the tab bar, Study active. [STYLE]
```

### B1: Quick log
```
Screen B1: QUICK LOG, fast and thumb-friendly. A subject segmented control (Physics | Chemistry | Biology), a chapter picker row, source chips (NCERT, PYQ, Module, Mock). Three big stepper tiles: Correct (solid white), Wrong (outlined), Skipped (dim), each with large - / + buttons. A live line "Attempted 40, Accuracy 72%, Marks +112 (4 x 29 - 11)". A collapsed row "Time and mistake types". Solid white "Save" capsule. [STYLE]
```

### B2: Subject detail
```
Screen B2: PHYSICS DETAIL. Subject ring (accuracy 68%), totals (612 questions this month, net marks 1,420). Segmented: Chapters | Mistakes | Trend. Chapter rows sorted weakest first: name, attempted count, a slim white accuracy bar, a delta chip (+5% / -3%), and a status marker (hollow circle under 50%, half 50-70%, filled over 70%). A "Practise weakest" white capsule. [STYLE]
```

### B3: Chapter detail
```
Screen B3: CHAPTER "Rotational Motion". Huge "48%" with a "-4% vs last week" chip. A smooth white line chart of accuracy over 8 sessions. A compact row: attempted 96, correct 46, wrong 38, average 62 s. A donut of mistake types (Concept, Silly, Calculation, Guess, Time) in white brightness steps with a labelled legend. Last 3 sessions list. Buttons "Log questions" (white) and "Add to revision" (outlined). [STYLE]
```

### B4: Mock test (two screens)
```
Screen B4A: MOCK TEST LOG. Test name and date. Three subject blocks (Physics, Chemistry, Biology), each with correct / wrong / unattempted steppers. Live total "Score 512 / 720" at the top.
Screen B4B: MOCK RESULT. A giant white "512 / 720" with a "+24 vs last test" chip and a thin progress bar to the target (600). Three subject bars out of 180, 180, 360 with tiny delta chips. A "Where marks were lost" row: Negative marking -41, Skipped 22, Silly mistakes 9. A "Next focus" line naming the weakest subject. Clean, not data-heavy. [STYLE]
```

### B5: Mistake notebook
```
Screen B5: MISTAKE NOTEBOOK. Filter chips (All, Physics, Chemistry, Biology) and a "Due today: 6" pill. Compact cards: question reference ("NCERT Ex 7.12" or "PYQ 2023"), chapter, a mistake-type tag, a one-line note, a revise-due chip (Today, 3d, 7d, 21d), and a round "Revised" check. [STYLE]
```

### B6: Study stats
```
Screen B6: STUDY STATS. Segmented Week | Month | All. A grouped bar chart of questions per day (three white brightness levels per subject). A small accuracy trend with three thin lines (solid, dashed, dotted). The compact DIFFERENCE TABLE: rows Physics, Chemistry, Biology; columns This week, Last week, Delta (up / down arrow icons) for accuracy and net marks. A heatmap calendar of study days. Brief and uncluttered. [STYLE]
```

---

# PART C: WORKOUT

### C0: Workout hub
```
Screen C0: WORKOUT HUB. Large title "Workout". Hero card "Today: Push Day" with a white ring for the week's plan progress, exercise count, estimated time, and a solid white "Start workout" capsule beside an outlined "Empty workout". Three stat tiles: "This week 3 sessions", "Load 412", "Form +8 (Fresh)". A "My plans" horizontal carousel. "Recent" list of 3 workouts (name, date, duration, volume, PR marker). A small "+ Quick log" link. [STYLE]
```

### C1: Plans list
```
Screen C1: PLANS. Title "Plans" with a "+" capsule. Plan cards (Push/Pull/Legs, Upper/Lower, Custom 5-day): glyph tile, days count, last done, and an "Active" pill on the current one. Swipe-left hint for Duplicate and Delete. [STYLE]
```

### C2: Plan detail
```
Screen C2: PLAN DETAIL "Push Pull Legs". Edit and Duplicate actions. A 7-day strip (Mon Push, Tue Pull, Wed Legs, Thu Rest...) with today as an inverted white pill. Day cards (Push A, Pull A, Legs A) with exercise count, muscle tags, a "Start" capsule and an expand chevron. A "Set active plan" white capsule. [STYLE]
```

### C3: Create / edit plan
```
Screen C3: CREATE PLAN. Plan name field, "Schedule type" segmented control (Weekly | Rotating cycle). Days list with drag handles, "+ Add day" row, "Rest day" quick-add chip. A "Save plan" white capsule. [STYLE]
```

### C4: Day builder
```
Screen C4: DAY BUILDER "Push A". Editable day name. Exercise cards: name, muscle tag chips, target "4 x 8-10 @ RPE 8", drag handle, overflow menu, rest-time chip. A superset bracket joining two cards. "+ Add exercise" dashed card. A bottom bar "6 exercises, 22 sets, ~65 min". "Save day" white capsule. [STYLE]
```

### C5: Add exercise
```
Screen C5: ADD EXERCISE. Search bar, muscle filter chips (Chest, Back, Legs, Shoulders, Arms, Core, Cardio) and equipment chips (Barbell, Dumbbell, Machine, Bodyweight, Cable). Rows with a glyph tile, name, primary muscle, last-used weight. Multi-select checkmarks and a sticky "Add 3 exercises" capsule. A "+ Create custom exercise" row at the top. [STYLE]
```

### C6: Custom exercise
```
Screen C6: CUSTOM EXERCISE. Name field. "Type" cards: Weight x Reps, Bodyweight x Reps, Weighted bodyweight, Assisted, Duration, Distance. Movement pattern chips (Horizontal push, Vertical push, Vertical pull, Horizontal pull, Squat, Hinge, Lunge, Core, Carry, Isolation), primary muscle picker, equipment picker. For bodyweight types a "Bodyweight factor" slider with the hint "Pull-up ~100%, Push-up ~65%". "Save exercise" white capsule. [STYLE]
```

### C7: Schedule
```
Screen C7: SCHEDULE. Segmented "Weekly | Rotating". Weekly: 7 rows (Mon-Sun) each with a day picker chip or "Rest". Rotating: an ordered cycle (Push, Pull, Legs, Rest) with "Repeats every 4 days" and a start-date picker. A preview strip of the next 14 days. "Save schedule" white capsule. [STYLE]
```

### C8: Active workout
```
Screen C8: ACTIVE WORKOUT "Push Day". Top bar: live timer, a "Load 128" pill, a white "Finish" capsule. Exercise sections with a table header (SET | PREVIOUS | KG | REPS | RPE | check). Rows: set number, previous in dim text ("60 x 8"), editable kg and reps fields, optional RPE, a round check circle (checked = solid white). Warm-up sets marked "W" in a grey badge. A star badge on a set that beat a record. "+ Add set" row and "+ Add exercise" dashed card. A floating rest-timer pill at the bottom ("1:12" with -15 / +15). [STYLE]
```

### C9: Set entry sheet
```
Screen C9: SET ENTRY (bottom sheet). Large kg and reps steppers with - / + buttons and a custom numeric keypad with large numerals. "Set type" segmented control (Normal | Warm-up | Drop | Failure). An RPE row (6 7 8 9 10) with the caption "2 reps left" under the chosen value. "Log set" white capsule. [STYLE]
```

### C10: Rest timer
```
Screen C10: REST TIMER. A big white countdown ring "1:23" with bloom, the next set preview ("Bench Press, set 3: 60 kg x 8"), -15s / +15s / Skip capsules and a "Rest default 90s" caption. Calm and minimal. [STYLE]
```

### C11: Exercise sheet
```
Screen C11: EXERCISE SHEET (bottom sheet). Exercise name, muscle tags, a mini e1RM sparkline, "Last 3 sessions" mini table, a notes field, and action rows: Replace exercise, Add superset, Reorder, Set rest time, Remove (outlined). [STYLE]
```

### C12: Plate calculator
```
Screen C12: PLATE CALCULATOR. Input "Target 100 kg", bar weight chip (20 kg), a side-on barbell illustration in white line art with plates labelled by weight (25, 20, 15, 10, 5, 2.5, 1.25) differing in thickness, and "Per side: 25 + 15 + 5". A link to plate inventory settings. [STYLE]
```

### C13: Finish summary
```
Screen C13: WORKOUT COMPLETE. A big white check ring with bloom. Stats grid: Duration, Total volume, Hard sets, Session load. A "Muscles worked" row of chips. PR list with star markers (e.g. "Bench press e1RM 82 kg"). A card "+30 min earned" with a huge white numeral and the line "Capped by today's recovery". Notes field. "Save workout" white capsule and "Discard" text link. [STYLE]
```

### C14: PR celebration
```
Screen C14: PR CELEBRATION (full-screen moment). Black background with a soft white radial bloom, a large white badge, "NEW PR", "Bench Press, e1RM 82 kg (+2.5)", a small previous-best comparison, and a white "Keep going" capsule. Fine white spark particles, nothing cartoonish. [STYLE]
```

### C15: Quick log (non-lifting)
```
Screen C15: QUICK LOG. Activity grid (Run, Ride, Swim, Walk, Yoga, Football, Cricket, HIIT, Other) in dark glyph tiles. Fields: start time, duration, optional distance. A big "How hard was it?" slider 1-10 with plain labels (Easy ... All-out) and a live "Session load 320". A notice "Manual entry earns 0 min. Connect Strava or the watch for verified time." "Log activity" white capsule. [STYLE]
```

### C16: History
```
Screen C16: HISTORY. A month calendar: lift days as solid white dots, cardio days as ring dots, PR days with a star. Below, a list of workout cards (name, date, duration, volume, load, PR marker). Filter chips (All, Lifting, Cardio). [STYLE]
```

### C17: Past workout detail
```
Screen C17: PAST WORKOUT "Push Day, 8 Oct". Header stats (duration, volume, load), exercise cards with full set tables, PR markers, notes, and capsules: Repeat workout (white), Save as day, Edit, Delete (outlined). [STYLE]
```

### C18: Edit past workout
```
Screen C18: EDIT WORKOUT. Same layout as the active workout but static: editable sets, date and time picker, add / remove exercises, a "Recalculate load and PRs" notice, "Save changes" white capsule. [STYLE]
```

### C19: Exercise library
```
Screen C19: EXERCISE LIBRARY. Search, filter chips, an alphabetical list with glyph tiles, primary muscle, and best e1RM on the right. A "+ New" capsule top right. [STYLE]
```

### C20: Exercise detail
```
Screen C20: EXERCISE DETAIL "Barbell Bench Press". A white line chart of e1RM over time with the PR point as a star. A Records card (Heaviest 80 kg, Best e1RM 91 kg, Best set volume 560, Most reps 14). A volume bar chart per session, recent sets list. Segmented 3M | 6M | 1Y | All. [STYLE]
```

### C21: Progress overview
```
Screen C21: PROGRESS OVERVIEW. Hero chart with three lines over 8 weeks: Fitness (solid white), Fatigue (dashed), Form (dotted), with today's marker. A status pill "Form +8: Fresh, ready to push". Weekly load bars with the 4-week average line. A "Training check" card: "Volume up 12%, add a deload in 2 weeks". [STYLE]
```

### C22: Muscle balance
```
Screen C22: MUSCLE BALANCE. A front/back body map as white line art, muscles shaded by weekly hard sets in white brightness steps (dim = under target, bright = on target, outlined = over). Rows per muscle group with a horizontal bar against a 10-20 target band, and a call-out "Hamstrings: 4 sets, under target". [STYLE]
```

### C23: Strength trends
```
Screen C23: STRENGTH TRENDS. Lift chips (Squat, Bench, Deadlift, OHP, Row). A large e1RM line chart with a dashed trend line, a "+6.2 kg in 8 weeks" chip, a "Plateau?" card, and a suggested next target "Try 62.5 kg x 8". [STYLE]
```

### C24: PR board
```
Screen C24: PR BOARD. A grid of cards per lift with the best e1RM, date and a "+2.5 kg" recent-gain tag, each with a star glyph. A "Recent PRs" timeline below. [STYLE]
```

### C25: Workout settings
```
Screen C25: WORKOUT SETTINGS. Grouped inset lists: Units (kg/lb), Default rest timer (90 s) with auto-start toggle, Track RPE toggle, Plate inventory, Bar weight, "Warm-up sets count toward volume" (off by default), Bodyweight, Earned-time rates (Workout = 30 min, Run per km, Focus ratio), Notification tone (Normal | Hard | Goggins), Export CSV, Import from Strong CSV. [STYLE]
```

---

## Checklist

- [ ] Home 1A, 1B (sent earlier in chat)
- [ ] Part A: A1-A17 (17 screens)
- [ ] Part B: B0-B6 (8 screens, B4 is two)
- [ ] Part C: C0-C25 (26 screens)
- [ ] Logo (prompt sent earlier in chat)
- [ ] Send me the final images, one per screen, and I'll build the UI to match.

Total: 53 screens. Don't try to do them all today unless you must: core first (A2, A3, A4, A6, A10), then study (B0, B1), then workout (C0, C8). The rest can follow the same pattern after the first working build.
