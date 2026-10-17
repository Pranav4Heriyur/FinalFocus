# FOCUS — NAV_MAP (navigation as implemented) + engineering plan

Status: workout module verified against source. Non-workout edges partly verified (marked). Build/test: NOT RUN.
Architecture: no NavHost. One back stack in `MainActivity.Root()` (`go()` / `back()`), `Screen` enum in `ui/Screens.kt`. Tab items clear the stack on tap. Workout screens render via `workout/WorkoutHost.kt`; shared state in `WorkoutNav`.

## 1. Map of the app as implemented

```mermaid
flowchart TD
    START(["Launch"]) --> ROOT["MainActivity.Root · single back stack"]
    ROOT -->|"setup not done"| SETUP["Setup / Permissions"]
    SETUP --> HOME
    ROOT --> HOME

    subgraph BAR["BOTTOM BAR · 8 items, scrollable · tap clears the stack"]
        HOME["Home"]
        FOCUS["Focus"]
        STUDY["Study"]
        PLAN["Plan"]
        WORKOUT["Workout · C0"]
        STATS["Stats"]
        TB["Time Bank"]
        PROFILE["Profile"]
    end

    subgraph OVERLAYS["APP-LEVEL OVERLAYS · AppState, not routes"]
        ACTIVEF["Active Focus Session · back disabled"]
        SUMMARY["Session Summary"]
        BLOCKED["Blocked-app screen"]
    end
    FOCUS --> ACTIVEF --> SUMMARY
    SUMMARY -->|"Done"| HOME
    SUMMARY -->|"Log"| STUDY
    BLOCKED --> FOCUS
    BLOCKED --> TB

    TB --> RULES["App Rules"]
    TB --> FOCUS
    RULES --> NEWRULE["New Rule"]
    RULES --> SEXEMPT["Study Exemptions"]
    HOME --> WORKOUT
    HOME --> STATS
    HOME --> PADD["Plan Add"]

    subgraph PLANMOD["PLAN TAB (planner, not workout) · partly verified"]
        PLAN --> PDETAIL["Plan Detail"]
        PLAN --> PCAL["Calendar"]
        PLAN --> PSUM["Summary"]
        PLAN --> PSUBJ["Subjects"]
        PLAN --> PREORD["Reorder"]
        PLAN --> PSET["Settings"]
        PLAN --> PADD
        PDETAIL --> PEDIT["Edit"]
        PDETAIL --> PFOCUS["Plan Focus"]
    end

    subgraph PROFMOD["PROFILE · partly verified"]
        PROFILE --> CONN["Connections"]
        PROFILE --> SEC["Security"]
        PROFILE --> RULES
        PROFILE --> APPS["Allowed Apps"]
        PROFILE --> CALLERS["Allowed Callers"]
        PROFILE --> RANK["Rank"]
        PROFILE --> JAR["Jar"]
        PROFILE --> MIRROR["Mirror"]
    end

    subgraph WMOD["WORKOUT MODULE · verified against source"]
        C0["C0 Hub"]
        C1["C1 Plans"]
        C2["C2 Routine / Plan Detail"]
        C3["C3 Create Plan"]
        C4["C4 Day Builder"]
        C5["C5 Add Exercise"]
        C6["C6 Custom Exercise · sheet in C5"]
        C8["C8 Routines Library"]
        A["Active Workout"]
        C9["C9 Set Entry"]
        C10["C10 Completed"]
        C11["C11 Statistics"]
        C12L["C12 History list"]
        C12D["C12 Session Detail"]
        NEST["Sheets/dialogs in Active:<br/>Rest Timer · Exercise Info · Previous Sets<br/>Edit Set · PR · Exercise Completed<br/>Finish Confirm · Exit Confirm"]

        C0 --> C1 & C8 & C11 & C12L
        C1 --> C3 & C2
        C3 --> C4
        C2 --> C4
        C4 --> C5 --> C6
        C0 & C1 & C2 & C8 --> A
        A --> C9 --> A
        A --> NEST --> A
        NEST -->|"Finish, save OK"| C10
        NEST -->|"Exit confirm"| C0
        C10 -->|"Done"| C0
        C10 --> C12D
        C11 --> C12L
        C11 --> C12D
        C12L --> C12D
        C12L --> C11
    end
    WORKOUT --> C0
    STATS -->|"Workouts card"| C12L
```

Stack rules: C1, C8, C10, C11 and the C12 list appear at most once on the stack. A push equal to the current screen is ignored. Starting a workout always yields `[C0, Active]`; finishing yields `[C0, C10]`.

## 2. Differences vs the intended Mermaid diagram

| # | Difference | Type |
|---|---|---|
| 1 | App bar has 8 items; diagram has 6 (and is titled "five tabs") | Decision |
| 2 | Plan tab exists in app, absent from diagram | Decision |
| 3 | Time Bank is a tab in app, a Home sub-destination in diagram | Decision |
| 4 | Splash / Onboarding / Sign Up / Log In have no routes (only Setup) | Decision |
| 5 | Study flow (Session, Question, Result, Log), Goals, Achievements, Body Stats, Export, About: not verified | Unverified |
| 6 | Diagram has C4 → C6; app reaches C6 via C5 (sheet) | Minor, retained |

## 3. Plan
- **Step 0, decide (product owner):** recommend keeping Plan and Time Bank as tabs and updating the diagram (removing them deletes working features); decide whether auth/onboarding is real or should be dropped from the diagram.
- **Step 1, freeze:** this file is the single source of truth.
- **Step 2, verify (read-only):** walk Focus, Study, Profile click paths and mark each edge verified/missing.
- **Step 3, fix only real defects, one change per edge**, stating the edge, the back destination, and tab-vs-push behaviour.

Rules for engineers: (1) no new `Screen` without a map update; (2) nested states stay dialogs/sheets; (3) never `back(); go()` to replace a screen, reset to the intended base; (4) anything that saves needs a double-tap guard and idempotent save; (5) report NOT RUN when it is.

Acceptance: every node has an entry point; Back lands where the map says; start→exit from every entry returns to C0; no screen twice on the stack.
