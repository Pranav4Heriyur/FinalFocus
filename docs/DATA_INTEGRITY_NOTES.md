# Data-integrity patch: verification & limits

## Run locally (JDK 17, Android SDK 35)
    ./gradlew testDebugUnitTest assembleDebug
Unit tests: LogicTest, StudyLogicTest, PlanDatesTest, DataIntegrityTest, TimeBankRecoveryTest.
Test-only dependency added: `org.xerial:sqlite-jdbc:3.46.0.0` (JVM SQLite for the study.db v2->v3 migration test; fetched from Maven Central).

## Known limits
- Top-up refunds are settled at day rollover from Android usage events for that local day (kept by Android only for a few days)
  and the in-app snapshot; the larger use wins. Without usage access or retained events the reservation stays unsettled (deferred), not refunded.
- Channel/video verification needs the Accessibility service; Android rarely exposes IDs, so most YouTube time stays unverified.
- Study log task/session links are soft (no parent tables, no FK / ON DELETE SET NULL).
- Rank thresholds, streak rule and XP amounts are the shipped values, marked CONFIRM in Progression.kt.
- NEET exam date has no default; it is set by the user from the Study hub.
