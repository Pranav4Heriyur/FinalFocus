# DESIGN SYSTEM v3: BLACK + PREMIUM WHITE (monochrome)

This **replaces** the colour system in section 1 of `FOCUS_APP_WORKOUT_SPEC.md`, and the subject colours in `STUDY_SECTION.md`. Screen layouts and prompts stay the same. Only the look changes.

## 1. Master prompt (paste first in a new ChatGPT chat)

```
You are a senior Apple Human Interface designer. I'm designing an Android app called "Focus" (focus lock + fitness + study + earned screen time). Follow this MONOCHROME system on every screen. Premium Apple-product feel (iOS 26 polish), strictly black and white. NO colour anywhere.

PALETTE (greys only)
- Background: true black #000000. Cards: #0B0B0C, raised cards #141416, sheets #1C1C1E.
- Premium white: pure #FFFFFF reserved for the most important thing on each screen (the hero number, today's data, active states, the primary button). Secondary text #A1A1A6, tertiary #636366. Hairlines: white at 10%.
- Hierarchy comes from BRIGHTNESS and SIZE only: peak white, 70%, 40%, 20%.

PREMIUM WHITE FINISH
- Pure white elements carry a soft white bloom (outer glow, 12-20px, 25% opacity), like a lit display.
- Thin specular edge on cards: a 1px top-edge highlight (white 8%), faint etched hairline border, a barely-there film grain (2-3%) on the background for a matte, tactile feel.
- Large numerals in light-weight SF Pro Rounded or SF Pro Display Light, huge and confident; labels in small tracked caps.

COMPONENTS
- Primary button: solid white capsule, black text, 56px, soft white bloom. Secondary: dark capsule with a white hairline outline. No red anywhere: destructive actions are outlined capsules with a "x" icon and a hold-to-confirm.
- Icons: SF Symbols style, medium weight, rounded, white. Icon tiles are dark squircles (#1C1C1E) with a white glyph. The ACTIVE tile inverts: white tile, black glyph.
- Corners: continuous squircle, 24px cards, capsules for buttons. 16px gutters, 12px card gaps, generous whitespace.
- Floating blurred tab bar (frosted dark) with 5 icons; the active tab is an inverted white pill. Blur only on the tab bar and bottom sheets.

DATA VISUALISATION (white only)
- Strava route, run or ride line: a crisp 3px WHITE line with a soft white bloom over a muted dark-grey map (faint grey streets, no colour). The line fades in brighter toward the finish. Start = ring marker, finish = solid white dot.
- Rings: bright white ring with bloom on a dark track (#2A2A2D). For multiple rings: outer = pure white, middle = 70%, inner = 40%, differing in thickness.
- Bar charts: today = solid white, other days = white at 35%. Line charts: main series solid white, comparison series dashed grey; a third series dotted. Heatmaps: five steps of white opacity. Legends use line style and labels, never colour.
- Subjects (Physics / Chemistry / Biology): told apart by icon glyph and by brightness or line style, never by colour.
- Celebrations (PR, ring closed, time earned): a white radial bloom and fine white sparks. Time Bank number = huge white numeral with bloom.

Realistic sample data (Indian context, metric units). Confirm, then wait for my first screen.
```

## 2. How the old colour roles translate

| Was | Now |
|---|---|
| Focus (blue) | Solid white ring with bloom |
| Workout (green) | Brightest white, solid tiles |
| Running / Cycling (orange) | White route line with bloom |
| Screen time (pink) | White at 70%, segmented bar with gaps |
| Time Bank (gold) | Giant white numeral with bloom |
| XP / Rank (purple) | Thin white progress bar, rank in tracked caps |
| Streak (red-orange) | White flame glyph, brightness grows with streak |
| Danger (red) | Outlined capsule + hold-to-confirm |
| Locked / unlocked state | Lock glyph at 40% vs. open lock at 100% |

## 3. Replacement for the Home hero (1A, section b)

```
HERO: a full-width tall dark card with a muted dark-grey map and a glowing WHITE route line (3px, soft white bloom, brightening toward the finish, ring marker at the start and a solid white dot at the end). A frosted dark overlay shows "Morning Run" and 3 huge light-weight white numerals: 5.2 km, 5'48"/km, 30:12. Below, heart rate, elevation, calories in small grey caps. A "Run | Ride" segmented control where the selected segment is an inverted white pill. A tiny "Powered by Strava" mark in the corner.
```

## 4. Caveats

- Without hue, colour-coding is gone, so every chart must be readable through brightness, thickness, line style and labels. The prompt enforces that.
- Strava's API terms ask apps that show Strava data to display "Powered by Strava" and follow their brand guidelines. I believe this is right, but check their guidelines before publishing the app.
- Keep contrast high: the lowest text level (#636366) is for non-essential captions only.
