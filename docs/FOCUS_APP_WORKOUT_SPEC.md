# FOCUS APP: Project Handoff + Workout Section Spec

> Paste this file into any new Claude / ChatGPT chat to resume instantly.
> Owner: Pranav. Platform: native Android (Kotlin + Jetpack Compose). Sideloaded APK, built via GitHub Actions.

---

## 0. PROJECT SNAPSHOT

**Goal:** Goggins-style discipline app. Locks distracting apps, tracks fitness, and pays screen time as the reward for real-world effort.

**Core features (decided):**
1. **Focus lock:** a full-screen timer. Accessibility Service + overlay + foreground service. No Device Owner needed. Allowed apps during a session: Phone, Claude, Gemini, plus anything the user picks.
2. **Exit friction:** hold 60 s, then type a long phrase. Costs XP and breaks the streak. The "40% rule" screen shows on exit attempts.
3. **Allowed callers:** a number whitelist (CallScreeningService). Other calls are rejected during a session and logged.
4. **Earned Time Bank:** chosen apps (Instagram, YouTube, ...) are LOCKED by default. Verified effort earns minutes. Minutes expire at midnight. Only verified sources pay out: locked focus sessions, Strava, Health Connect, the workout log. Manual entries pay nothing.
5. **Per-app limits and unlock rules**, e.g. "unlock Instagram after a 5 km run".
6. **Integrations:** Strava (OAuth + API), Notion (API), Health Connect (Samsung watch data).
7. **Watch toggle:** "Watch worn by me". Health Connect data is kept only inside the toggled windows, so the father's wear time is discarded.
8. **Accountability Mirror:** a nightly Done / Not done review of the agenda. Misses debit minutes from tomorrow's bank.
9. **Goggins mechanics:** no streak freezes, a cookie jar (log of the hardest things done), auto-scaling weekly targets (+5% on a hit), a notification tone setting (Normal / Hard / Goggins), and ranks instead of cosmetic rewards.
10. **Workout engine** (section 4): a Strong-equivalent log plus a better, science-based load model.
11. **Web tracker:** a charts dashboard (later phase).
12. **Security (robust but light):** biometric app lock, encrypted local DB (SQLCipher or EncryptedSharedPreferences), tokens in Android Keystore, no secrets in the APK, minimal permissions, and no analytics SDKs.

**Build order:**
1. Focus lock, timer, whitelist
2. Allowed callers
3. Time Bank + app limits and unlock rules
4. Workout engine + logger
5. Health Connect + watch toggle
6. Strava + Notion sync
7. Accountability Mirror + ranks
8. Web tracker

**Known limits (be honest in-app):** the lock can't survive a reboot, safe mode, or the user switching off the Accessibility permission. Settings is blocked during a session, and if the permission is switched off anyway the session fails and the streak breaks. A Device Owner "strict mode" is an optional phase 2 (needs a one-time adb command).

---

## 1. DESIGN SYSTEM: MATTE BLACK + VIVID COLOUR (final direction)

Paste this into ChatGPT FIRST, then send screens one at a time:

```
You are a senior Apple Human Interface designer. I'm designing an Android app called "Focus" (focus lock + fitness + earned screen time). Follow this system on every screen. Premium Apple look (iOS 26 / Apple Fitness polish), MATTE BLACK with VIVID COLOUR:

- Canvas 390x844, dark only. Background true matte black #000. Cards solid matte charcoal #151517, raised cards #1C1C1F. Matte only: no gloss, no gradient sheens, no heavy blur. Faint 1px hairline border (white 8%), tiny soft shadow. Blur/glass ONLY on the floating tab bar and bottom sheets.
- Corners: continuous squircle, 24px cards, capsule buttons.
- Colour is the dopamine. Each area owns one saturated colour:
  Focus = electric blue #0A84FF, Workout = neon green #30D158, Running/Cycling = orange #FF9F0A (Strava hint #FC4C02), Screen time = hot pink #FF375F, Time Bank = gold #FFD60A, XP/Rank = purple #BF5AF2, Streak = fire red-orange #FF453A.
- Icons: SF Symbols style, bold rounded, white glyph inside a solid colour squircle tile (like iOS Settings), in the area colour.
- Data: neon rings, rounded bars, smooth lines in the area colour with soft glow. Today is brightest.
- Buttons: solid saturated capsules (Primary blue, Success green, Danger red), 56px, white 17px semibold, subtle pressed depth, NOT glossy.
- Text: white / 60% / 35% hierarchy, SF Pro, big SF Rounded numerals.
- Celebration states: closed ring with sparkle, gold PR badge, gold "+15 min earned" pill.
- 16px gutters, 12px card gaps, generous whitespace, realistic sample data (Indian context, metric units).
- Floating blurred tab bar: Home, Focus, Rules, Stats, Profile. (Workout opens from Home and from the Stats tab.)
Confirm, then wait for my first screen.
```

Tip: if ChatGPT adds glass or gradients, say "No glassmorphism, no gloss. Flat matte surfaces only." To get more dopamine, say "increase the accent saturation and make the rings glow more."

---

## 2. WORKOUT SECTION: SITEMAP (all pages and subpages)

```
WORKOUT
├── W0  Workout Hub (entry)
├── W1  Plans
│   ├── W1a Plans list
│   ├── W1b Plan detail (days + schedule)
│   ├── W1c Create / edit plan
│   ├── W1d Day builder (custom day)
│   ├── W1e Add exercise picker
│   ├── W1f Create custom exercise
│   └── W1g Schedule editor (weekly or rotating cycle)
├── W2  Active workout
│   ├── W2a Active workout (main)
│   ├── W2b Set entry sheet (weight / reps / RPE keypad)
│   ├── W2c Rest timer
│   ├── W2d Exercise sheet in-workout (notes, history, swap, superset, remove)
│   ├── W2e Plate calculator
│   ├── W2f Finish summary (load, volume, PRs, time earned)
│   └── W2g PR celebration
├── W3  Quick log (any non-lifting activity: yoga, football, swim, ...)
├── W4  History
│   ├── W4a History list + calendar
│   ├── W4b Past workout detail
│   └── W4c Edit past workout
├── W5  Exercise library
│   ├── W5a Library list
│   └── W5b Exercise detail (PRs, e1RM chart, volume chart, history)
├── W6  Progress
│   ├── W6a Overview (Fitness / Fatigue / Form + weekly load)
│   ├── W6b Muscle balance (hard sets per muscle, body map)
│   ├── W6c Strength trends (e1RM per lift)
│   └── W6d PR board
└── W7  Workout settings
```

---

## 3. WORKOUT SECTION: CHATGPT PROMPTS (send one at a time, after the design system)

Each prompt below ends with the same line. Always append: *"Use the Focus matte-black design system. Workout colour is neon green #30D158."*

### W0: Workout Hub
```
Screen W0: WORKOUT HUB. Large title "Workout". Hero card: "Today: Push Day" with a green neon ring showing the week's plan progress, the exercise count and an estimated time, and a solid green "Start workout" capsule. Beside it a secondary dark "Empty workout" capsule. Row of 3 stat tiles: "This week 3 sessions", "Load 412", "Form +8 (Fresh)" with coloured icon squircles. A "My plans" horizontal carousel of plan cards. "Recent" list of the last 3 workouts (name, date, duration, volume, PR gold dot). A small "+ Quick log" link for non-lifting activity.
```

### W1a: Plans list
```
Screen W1a: PLANS LIST. Title "Plans", a "+" capsule top right. Plan cards (Push/Pull/Legs, Upper/Lower, Custom 5-day) each with a green squircle icon, days count, last done, and an "Active" green pill on the current one. Swipe-left hint for Duplicate and Delete. Footer text "Tap a plan to see its days".
```

### W1b: Plan detail
```
Screen W1b: PLAN DETAIL "Push Pull Legs". Header with Edit and Duplicate. A 7-day schedule strip (Mon Push, Tue Pull, Wed Legs, Thu Rest, ...) with the current day highlighted in green. Below, day cards (Push A, Pull A, Legs A) each showing exercise count and muscle tags, with a "Start" capsule and an expand chevron. A "Set active plan" success capsule at the bottom.
```

### W1c: Create / edit plan
```
Screen W1c: CREATE PLAN. Fields: plan name, an icon colour picker, "Schedule type" segmented control (Weekly | Rotating cycle). List of days with drag handles, an "+ Add day" row, and a "Rest day" quick-add chip. Primary green "Save plan" capsule.
```

### W1d: Day builder
```
Screen W1d: DAY BUILDER "Push A". Editable day name. Exercise cards: name, muscle tag chips, target "4 x 8-10 @ RPE 8", drag handle, overflow menu. A superset link icon joining two cards (shown as a green bracket). Rest time chip per exercise. "+ Add exercise" dashed card. A bottom summary bar: "6 exercises, 22 sets, ~65 min". Primary green "Save day" capsule.
```

### W1e: Add exercise picker
```
Screen W1e: ADD EXERCISE. Search bar, filter chips (Chest, Back, Legs, Shoulders, Arms, Core, Cardio) and equipment chips (Barbell, Dumbbell, Machine, Bodyweight, Cable). Exercise rows with a colour squircle icon, name, primary muscle, and a last-used weight. Multi-select checkmarks, a sticky "Add 3 exercises" capsule. A "+ Create custom exercise" row at the top.
```

### W1f: Create custom exercise
```
Screen W1f: CUSTOM EXERCISE. Name field. "Type" selector cards: Weight x Reps, Bodyweight x Reps, Weighted bodyweight, Assisted, Duration, Distance. Primary muscle picker plus secondary muscles (multi-chip). Equipment picker. For bodyweight types, a "Bodyweight factor" slider with a hint like "Pull-up ~100%, Push-up ~65%". Notes field. Primary "Save exercise" capsule.
```

### W1g: Schedule editor
```
Screen W1g: SCHEDULE. Segmented control "Weekly | Rotating". Weekly: 7 rows (Mon-Sun), each with a day picker chip or "Rest". Rotating: an ordered cycle (Push, Pull, Legs, Rest) with a "Repeats every 4 days" caption and a "Start date" picker. Preview strip of the next 14 days. Primary "Save schedule" capsule.
```

### W2a: Active workout (main)
```
Screen W2a: ACTIVE WORKOUT "Push Day". Top bar: live timer, live "Load 128" pill, and a green "Finish" capsule. Exercise sections each with a table header (SET | PREVIOUS | KG | REPS | RPE | check). Rows: set number, previous workout in dim text ("60 x 8"), editable kg and reps fields, an optional RPE field, and a round check circle (checked = solid green). Warm-up sets marked "W" in a grey badge. A gold PR badge on a set that beat a record. "+ Add set" row and "+ Add exercise" dashed card. A floating rest-timer pill at the bottom ("1:12 rest" with -15 / +15 buttons).
```

### W2b: Set entry sheet
```
Screen W2b: SET ENTRY SHEET (bottom sheet over the active workout). Large kg and reps steppers with - / + buttons and a custom numeric keypad (matte keys, large numerals). A "Set type" segmented control (Normal | Warm-up | Drop | Failure). An RPE selector row (6 7 8 9 10) with a caption "2 reps left" under the chosen value. Solid green "Log set" capsule.
```

### W2c: Rest timer
```
Screen W2c: REST TIMER. A large green neon countdown ring "1:23", the next set preview ("Bench Press, set 3: 60 kg x 8"), -15s / +15s / Skip capsules, and a small "Rest default 90s" caption. Calm, minimal, matte.
```

### W2d: Exercise sheet in-workout
```
Screen W2d: EXERCISE SHEET (bottom sheet). Exercise name, muscle tags, a mini e1RM sparkline, "Last 3 sessions" mini table, a notes field, and action rows: Replace exercise, Add superset, Reorder, Set rest time, Remove (red).
```

### W2e: Plate calculator
```
Screen W2e: PLATE CALCULATOR. Input "Target 100 kg", bar weight chip (20 kg), a side-on barbell illustration showing coloured plates (25 red, 20 blue, 15 yellow, 10 green, 5 white, 2.5, 1.25) with labels, and the text "Per side: 25 + 15 + 5". Plate inventory settings link.
```

### W2f: Finish summary
```
Screen W2f: WORKOUT COMPLETE. A big green check ring. Stats grid: Duration, Total volume, Hard sets, Session load. A "Muscles worked" row of coloured chips. A PR list with gold badges (e.g. "Bench press e1RM 82 kg"). A gold earn card "+30 min earned, unlocked to your Time Bank", with a small line "Capped by today's recovery". Notes field. A "Save workout" success capsule and a "Discard" text link.
```

### W2g: PR celebration
```
Screen W2g: PR CELEBRATION (full-screen overlay for a moment). Dark matte background with a soft gold glow, a large gold trophy-style badge, "NEW PR", "Bench Press, e1RM 82 kg (+2.5)", a small previous-best comparison, and a gold "Keep going" capsule. Subtle confetti dots, no cartoons.
```

### W3: Quick log (non-lifting activity)
```
Screen W3: QUICK LOG. Activity type grid (Run, Ride, Swim, Walk, Yoga, Football, Cricket, HIIT, Other) with coloured icon tiles. Fields: start time, duration, optional distance. A big "How hard was it?" RPE slider 1-10 with plain-language labels (Easy ... All-out) and a live "Session load 320" readout. A notice chip "Manual entry earns 0 min. Connect Strava or the watch for verified time." Primary "Log activity" capsule.
```

### W4a: History list + calendar
```
Screen W4a: HISTORY. A month calendar with green dots for lift days, orange for cardio, and a gold ring for PR days. Below, a reverse-chronological list of workout cards (name, date, duration, volume, session load, PR badge). Filter chips (All, Lifting, Cardio).
```

### W4b: Past workout detail
```
Screen W4b: PAST WORKOUT DETAIL "Push Day, 8 Oct". Header stats (duration, volume, load), exercise cards with the full set tables, PR badges, notes, and action capsules: Repeat workout, Save as day, Edit, Delete (red).
```

### W4c: Edit past workout
```
Screen W4c: EDIT WORKOUT. Same layout as the active workout but static: editable sets, date and time picker, add / remove exercises, a "Recalculate load and PRs" notice, and a "Save changes" capsule.
```

### W5a: Exercise library
```
Screen W5a: EXERCISE LIBRARY. Search, filter chips, an alphabetical list with colour squircle icons, the primary muscle, and the best e1RM on the right. A "+ New" capsule top right.
```

### W5b: Exercise detail
```
Screen W5b: EXERCISE DETAIL "Barbell Bench Press". A green line chart of e1RM over time with the PR point in gold, a Records card (Heaviest 80 kg, Best e1RM 91 kg, Best set volume 560, Most reps 14), a volume bar chart per session, and a history list of recent sets. Segmented control 3M | 6M | 1Y | All.
```

### W6a: Progress overview
```
Screen W6a: PROGRESS OVERVIEW. Hero: a Fitness (blue), Fatigue (pink), Form (green) line chart over 8 weeks with today's marker. A status pill "Form +8: Fresh, ready to push". Weekly load bars with the 4-week average line. A "Training plan check" card: "Volume up 12%, add a deload in 2 weeks".
```

### W6b: Muscle balance
```
Screen W6b: MUSCLE BALANCE. A front/back body map with muscles shaded by weekly hard sets (cold blue under target, green on target, orange over). Below, rows per muscle group with a horizontal bar showing sets against a 10-20 target band, and a call-out "Hamstrings: 4 sets, under target".
```

### W6c: Strength trends
```
Screen W6c: STRENGTH TRENDS. A selector of the main lifts (Squat, Bench, Deadlift, OHP, Row) as chips. A large e1RM line chart with the trend line, a "+6.2 kg in 8 weeks" delta pill, a "Plateau?" detector card, and the suggested next target "Try 62.5 kg x 8".
```

### W6d: PR board
```
Screen W6d: PR BOARD. A grid of gold-edged cards per lift with the best e1RM, the date, and a "+2.5 kg" recent-gain tag. A "Recent PRs" timeline below. Matte black with gold accents.
```

### W7: Workout settings
```
Screen W7: WORKOUT SETTINGS. Grouped inset lists: Units (kg/lb), Default rest timer (90 s) with auto-start toggle, Track RPE toggle, Plate inventory, Bar weight, Warm-up sets count toward volume (toggle, off by default), Bodyweight, Earned-time rates (Workout = 30 min, Run per km, Focus ratio), Notification tone (Normal | Hard | Goggins), Export CSV, Import from Strong CSV.
```

---

## 4. WORKOUT MATH SPEC (what the app computes)

### 4.1 Strong-equivalent basics
- Set volume = weight x reps. Workout volume = sum over working sets (warm-ups excluded by default).
- PR types per exercise: heaviest weight, best e1RM, best set volume, most reps at a given weight.
- Strong's exact 1RM formula is not publicly documented. I believe it's Brzycki, but I couldn't verify it. Matching to the decimal isn't required.

### 4.2 Better per-set strength estimate
- Brzycki: `e1RM = w * 36 / (37 - r)` for r in 1..5
- Epley: `e1RM = w * (1 + r / 30)` for r in 6..12
- Ignore sets above 12 reps for e1RM (too unreliable). They still count for volume.
- RIR adjustment: if RPE is logged, effective reps to failure = `r + (10 - RPE)`, then apply the formula above.
- Bodyweight moves: `effective_load = bodyweight * factor + added`. Factors: pull-up 1.0, chin-up 1.0, dip 0.95, push-up 0.65, inverted row 0.5. Assisted: subtract assistance.

### 4.3 Muscle-level stimulus
- A hard set = a working set with 0-4 reps in reserve (or RPE >= 6 if logged, or >= 65% of e1RM when RPE is not logged).
- Credit primary muscle 1.0 and secondary muscles 0.5 per hard set.
- Weekly target 10-20 hard sets per muscle group. Feeds the spider chart and the W6b screen.

### 4.4 One load number for any activity
- sRPE load (Foster) = `session RPE (1-10) * duration (min)`.
- If heart rate exists (Strava or Health Connect), compute Banister TRIMP and blend with sRPE (average if both exist).
- Lifting session load = sRPE load, with the volume-based load as a cross-check.

### 4.5 Fitness / Fatigue / Form
- Daily load series L(t).
- Fitness (CTL) = exponentially weighted average, 42-day time constant.
- Fatigue (ATL) = exponentially weighted average, 7-day time constant.
- Form = Fitness - Fatigue (positive = fresh, strongly negative = overreaching).
- Use as a readiness guide only, not an injury predictor.

### 4.6 Earned time from workouts
- Base: 30 min per verified workout above a minimum load, and per-km for runs and rides (all user-editable).
- Cap: daily earnings from training are capped when Form is very negative, so the app doesn't reward overtraining.
- Verified only: workout log (minimum sets and duration), Strava, Health Connect. Manual quick-log earns 0.

### 4.7 Plateau and progression
- Fit a linear trend to the last 6-8 e1RM points per lift. A slope near 0 or negative flags a plateau.
- Suggest double progression: when all sets hit the top of the rep range at RPE <= 8, add the smallest plate increment.

---

## 5. DATA MODEL (Room / SQLite, encrypted)

```
Exercise(id, name, type[WEIGHT_REPS|BW_REPS|WEIGHTED_BW|ASSISTED|DURATION|DISTANCE],
         primaryMuscle, secondaryMuscles[], equipment, bwFactor, isCustom)
Plan(id, name, colour, scheduleType[WEEKLY|ROTATING], cycleLength, startDate, isActive)
PlanDay(id, planId, name, orderIndex, weekday?)
PlanDayExercise(id, dayId, exerciseId, orderIndex, targetSets, repMin, repMax, targetRpe, restSec, supersetGroup?)
Workout(id, planDayId?, name, startTs, endTs, bodyweightKg, notes, sessionRpe?, load, source[LOG|STRAVA|HEALTH|MANUAL])
WorkoutSet(id, workoutId, exerciseId, orderIndex, type[NORMAL|WARMUP|DROP|FAILURE],
           weightKg, reps, rpe?, durationSec?, distanceM?, e1rm, isPR)
PersonalRecord(id, exerciseId, kind, value, workoutSetId, ts)
DailyLoad(date, load, fitness, fatigue, form)
TimeBankEntry(id, ts, minutes, source, verified, note)
```

---

## 6. NEXT STEPS CHECKLIST

- [ ] Generate every workout screen in ChatGPT (section 3), one per message
- [ ] Re-do Home 1A / 1B, Time Bank, and Accountability Mirror in the matte style
- [ ] Send the final images (one per screen) so the UI can be matched in Compose
- [ ] Phase 1 build: focus lock + timer + whitelist (Kotlin + GitHub Actions APK)
- [ ] Phase 4 build: workout engine as a tested Kotlin module (e1RM, load, Fitness/Fatigue/Form), including unit tests
- [ ] Decide the default earn rates for the Time Bank
