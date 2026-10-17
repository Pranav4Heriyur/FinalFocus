package com.pranav.focus.workout

/**
 * Android-free SQL plumbing for workout.db. The repository and seeder talk to this interface only, so
 * a JVM test can run the real SQL against sqlite-jdbc (see WorkoutDbTest) while the app uses SQLiteOpenHelper.
 *
 * NOTE: `query` args are bound as strings on Android (rawQuery limitation). Never use `?` in LIMIT or in
 * expressions without a column on the other side; bind only against columns so SQLite applies column affinity.
 */
typealias Row = Map<String, Any?>

interface SqlRunner {
    fun exec(sql: String, vararg args: Any?)
    fun query(sql: String, vararg args: Any?): List<Row>
    /** Runs [block] atomically; nested calls join the outer transaction. */
    fun <T> tx(block: () -> T): T
}

fun Row.str(k: String): String = (this[k] as? String) ?: this[k]?.toString() ?: ""
fun Row.strOrNull(k: String): String? = this[k]?.toString()
fun Row.int(k: String): Int = (this[k] as? Number)?.toInt() ?: 0
fun Row.long(k: String): Long = (this[k] as? Number)?.toLong() ?: 0L
fun Row.dbl(k: String): Double = (this[k] as? Number)?.toDouble() ?: 0.0
fun Row.flag(k: String): Boolean = int(k) != 0

fun SqlRunner.count(sql: String, vararg args: Any?): Int = query(sql, *args).firstOrNull()?.values?.firstOrNull()?.let { (it as? Number)?.toInt() } ?: 0
fun SqlRunner.lastInsertId(): Long = query("SELECT last_insert_rowid() AS id").first().long("id")

/** workout.db schema. Version history is append-only: never edit a shipped step, add a new one. */
object WorkoutSchema {
    const val VERSION = 6

    val V1: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)",

        // ---- catalogue ----
        "CREATE TABLE IF NOT EXISTS exercise(id TEXT PRIMARY KEY, name TEXT NOT NULL, equipment TEXT NOT NULL DEFAULT ''," +
            " primary_muscles TEXT NOT NULL DEFAULT '', secondary_muscles TEXT NOT NULL DEFAULT '', type TEXT NOT NULL DEFAULT 'reps'," +
            " default_sets INT NOT NULL DEFAULT 3, default_reps TEXT NOT NULL DEFAULT '8-12', default_rest_s INT NOT NULL DEFAULT 90," +
            " unilateral INT NOT NULL DEFAULT 0, instructions TEXT NOT NULL DEFAULT '', is_custom INT NOT NULL DEFAULT 0," +
            " favourite INT NOT NULL DEFAULT 0, last_used_ts INT, archived INT NOT NULL DEFAULT 0)",
        // old id schemes (legacy WorkoutAssets ids, asset-manifest ids) -> catalogue id. Explicit, never string-matched.
        "CREATE TABLE IF NOT EXISTS exercise_alias(alias TEXT PRIMARY KEY, exercise_id TEXT NOT NULL, source TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS idx_alias_ex ON exercise_alias(exercise_id)",

        // ---- routines (G1-G4, M1-M5 and user routines) ----
        "CREATE TABLE IF NOT EXISTS routine(id TEXT PRIMARY KEY, code TEXT NOT NULL DEFAULT '', name TEXT NOT NULL, category TEXT NOT NULL," +
            " focus TEXT NOT NULL DEFAULT '', primary_focus TEXT NOT NULL DEFAULT '', description TEXT NOT NULL DEFAULT ''," +
            " difficulty TEXT NOT NULL DEFAULT 'Intermediate', minutes_low INT NOT NULL DEFAULT 0, minutes_high INT NOT NULL DEFAULT 0," +
            " label_hub TEXT NOT NULL DEFAULT '', label_plans TEXT NOT NULL DEFAULT '', accent TEXT NOT NULL DEFAULT 'grey'," +
            " kind TEXT NOT NULL DEFAULT 'strength', heavy_legs INT NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT ''," +
            " is_preset INT NOT NULL DEFAULT 0, user_modified INT NOT NULL DEFAULT 0, archived INT NOT NULL DEFAULT 0, sort INT NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS routine_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, routine_id TEXT NOT NULL, exercise_id TEXT NOT NULL," +
            " position INT NOT NULL, sets INT NOT NULL, rep_text TEXT NOT NULL, rep_min INT, rep_max INT, rest_s INT NOT NULL DEFAULT 0," +
            " rest_text TEXT NOT NULL DEFAULT '', unilateral INT NOT NULL DEFAULT 0, superset_group TEXT, note TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS idx_re_routine ON routine_exercise(routine_id, position)",

        // ---- plans & weekly schedule ----
        "CREATE TABLE IF NOT EXISTS plan(id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT ''," +
            " plan_type TEXT NOT NULL DEFAULT 'gym', tags TEXT NOT NULL DEFAULT '', template_id TEXT NOT NULL DEFAULT ''," +
            " is_active INT NOT NULL DEFAULT 0, is_preset INT NOT NULL DEFAULT 0, slot1_weekday INT NOT NULL DEFAULT 1," +
            " created_at INT NOT NULL DEFAULT 0, updated_at INT NOT NULL DEFAULT 0, archived INT NOT NULL DEFAULT 0)",
        "CREATE TABLE IF NOT EXISTS plan_routine(plan_id TEXT NOT NULL, routine_id TEXT NOT NULL, position INT NOT NULL DEFAULT 0, PRIMARY KEY(plan_id, routine_id))",
        "CREATE TABLE IF NOT EXISTS plan_day(plan_id TEXT NOT NULL, slot INT NOT NULL, morning_routine_id TEXT, alt_morning_routine_id TEXT," +
            " gym_routine_id TEXT, notes TEXT NOT NULL DEFAULT '', customized INT NOT NULL DEFAULT 0, PRIMARY KEY(plan_id, slot))",
        // copy-on-write per-day exercise list: presets stay untouched, edits live here
        "CREATE TABLE IF NOT EXISTS plan_day_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, plan_id TEXT NOT NULL, slot INT NOT NULL," +
            " exercise_id TEXT NOT NULL, position INT NOT NULL, sets INT NOT NULL, rep_text TEXT NOT NULL, rep_min INT, rep_max INT," +
            " rest_s INT NOT NULL DEFAULT 0, rest_text TEXT NOT NULL DEFAULT '', unilateral INT NOT NULL DEFAULT 0, superset_group TEXT, note TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS idx_pde_day ON plan_day_exercise(plan_id, slot, position)",

        // ---- history (soft links on purpose: history must outlive plan/routine edits and deletes) ----
        "CREATE TABLE IF NOT EXISTS workout_session(id TEXT PRIMARY KEY, plan_id TEXT, slot INT, routine_id TEXT, title TEXT NOT NULL DEFAULT ''," +
            " activity TEXT NOT NULL DEFAULT 'gym', status TEXT NOT NULL DEFAULT 'active', started_at INT NOT NULL, ended_at INT, notes TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS idx_session_start ON workout_session(started_at)",
        "CREATE TABLE IF NOT EXISTS set_log(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, exercise_id TEXT NOT NULL," +
            " exercise_name TEXT NOT NULL DEFAULT '', set_no INT NOT NULL, load_kg REAL, reps INT, rpe INT, duration_s INT, distance_m REAL," +
            " side TEXT, warmup INT NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '', completed_at INT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS idx_setlog_session ON set_log(session_id)",
        "CREATE INDEX IF NOT EXISTS idx_setlog_ex ON set_log(exercise_id, completed_at)",
        "CREATE TABLE IF NOT EXISTS body_weight_log(date TEXT PRIMARY KEY, kg REAL NOT NULL, ts INT NOT NULL)",
    )

    /** V2 (C6): extra custom-exercise attributes in a side table, so the shipped `exercise` table is never altered. */
    val V2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS exercise_meta(exercise_id TEXT PRIMARY KEY, movement_pattern TEXT NOT NULL DEFAULT '', bodyweight_factor REAL)",
    )

    /** V3 (C8/9): exercises added to a running session (not part of its routine / plan day). */
    val V3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS session_exercise(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, exercise_id TEXT NOT NULL, position INT NOT NULL DEFAULT 0)",
        "CREATE INDEX IF NOT EXISTS idx_session_ex ON session_exercise(session_id)",
    )

    /** V4 (C10): rest time actually taken between sets (from the rest timer), per session. */
    val V4: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS rest_log(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, seconds INT NOT NULL, ts INT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS idx_rest_session ON rest_log(session_id)",
    )


    /**
     * V5 (final repair): historical snapshots + timing. Additive only, existing rows are untouched (new columns are NULL / 0 for old sessions,
     * which is how the app recognises "not recorded" and shows it as unavailable instead of guessing).
     *  - workout_session.snap_v = 1 once a completion snapshot exists; routine_name/category_label are the values AT completion.
     *  - workout_session.timing_v = 1 when paused_ms / rest_ms were tracked for the session (active duration can then be derived).
     *  - session_snapshot_ex: one row per exercise performed, in the order performed, with name/equipment/muscles as they were at completion.
     *  - session_pr: verified heaviest-weight PRs of the session, computed once at completion from sets that existed before it.
     */
    val V5: List<String> = listOf(
        "ALTER TABLE workout_session ADD COLUMN snap_v INT NOT NULL DEFAULT 0",
        "ALTER TABLE workout_session ADD COLUMN timing_v INT NOT NULL DEFAULT 0",
        "ALTER TABLE workout_session ADD COLUMN paused_ms INT NOT NULL DEFAULT 0",
        "ALTER TABLE workout_session ADD COLUMN rest_ms INT NOT NULL DEFAULT 0",
        "ALTER TABLE workout_session ADD COLUMN routine_name TEXT",
        "ALTER TABLE workout_session ADD COLUMN category_label TEXT",
        "CREATE TABLE IF NOT EXISTS session_snapshot_ex(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, exercise_id TEXT NOT NULL, position INT NOT NULL," +
            " name TEXT NOT NULL, equipment TEXT NOT NULL DEFAULT '', primary_muscles TEXT NOT NULL DEFAULT '', secondary_muscles TEXT NOT NULL DEFAULT '', type TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS idx_snapex_session ON session_snapshot_ex(session_id, position)",
        "CREATE TABLE IF NOT EXISTS session_pr(id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, exercise_id TEXT NOT NULL, name TEXT NOT NULL," +
            " muscle TEXT NOT NULL DEFAULT '', new_kg REAL NOT NULL, new_reps INT, prev_kg REAL NOT NULL, prev_reps INT)",
        "CREATE INDEX IF NOT EXISTS idx_sessionpr_session ON session_pr(session_id)",
    )

    /**
     * V6 (accuracy repair): additive only.
     *  - rest_log gets rest_uid / started_at / outcome so every tracked rest is ONE row (INSERT OR IGNORE on the unique (session_id, rest_uid) index makes a
     *    reopened/restored timer unable to count twice). Old rows keep NULLs = "outcome/start not recorded"; their seconds are still counted.
     *  - Unique indexes on session_pr / session_snapshot_ex stop a repeated completion from storing a PR or exercise twice. Exact duplicate rows
     *    (none are expected) are collapsed first so the index can be created; no other data is touched.
     *  - Canonical timing: new sessions are written with timing_v = 2 (started_at is the REAL start, paused_ms/rest_ms are separate). timing_v 1 sessions
     *    (started_at shifted forward by pauses) and 0 (no tracking) are read with their original meaning, see WorkoutTiming.
     */
    val V6: List<String> = listOf(
        "ALTER TABLE rest_log ADD COLUMN rest_uid TEXT",
        "ALTER TABLE rest_log ADD COLUMN started_at INT",
        "ALTER TABLE rest_log ADD COLUMN outcome TEXT",
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_rest_uid ON rest_log(session_id, rest_uid)",
        "DELETE FROM session_pr WHERE id NOT IN (SELECT MIN(id) FROM session_pr GROUP BY session_id, exercise_id)",
        "DELETE FROM session_snapshot_ex WHERE id NOT IN (SELECT MIN(id) FROM session_snapshot_ex GROUP BY session_id, exercise_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_sessionpr ON session_pr(session_id, exercise_id)",
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_snapex ON session_snapshot_ex(session_id, exercise_id)",
    )

    /** Runs [steps] in order. A repeated `ALTER TABLE .. ADD COLUMN` ("duplicate column name") counts as already applied; any other error is rethrown. */
    fun applySafely(steps: List<String>, exec: (String) -> Unit) {
        steps.forEach { sql ->
            try { exec(sql) } catch (e: Exception) { if (e.message?.contains("duplicate column", ignoreCase = true) != true) throw e }
        }
    }

    /** Steps to move an existing database from [from] to [to]. Additive only; each step must be idempotent-safe. */
    fun upgradeSteps(from: Int, to: Int): List<String> {
        val out = ArrayList<String>()
        if (from < 1 && to >= 1) out += V1
        if (from < 2 && to >= 2) out += V2
        if (from < 3 && to >= 3) out += V3
        if (from < 4 && to >= 4) out += V4
        if (from < 5 && to >= 5) out += V5
        if (from < 6 && to >= 6) out += V6
        // if (from < 7 && to >= 7) out += V7   <- future steps go here
        return out
    }
}
