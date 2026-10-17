package com.pranav.focus.data

/** Plain SQL for study.db upgrades, kept Android-free so a JVM test can run it against a real old-schema database. */
object StudyMigrations {
    /** question_log exactly as shipped in schema v2. */
    const val V2_QUESTION_LOG = "CREATE TABLE question_log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INT, subject INT, chapter_id INT, source INT, correct INT, wrong INT, skipped INT, minutes INT, verified INT, book_id INT DEFAULT 0)"

    /** v2 -> v3: additive only. Soft links (no parent tables exist, so no FOREIGN KEY / ON DELETE SET NULL). Old rows keep NULL. */
    val V3 = listOf(
        "ALTER TABLE question_log ADD COLUMN agenda_task_id TEXT",
        "ALTER TABLE question_log ADD COLUMN focus_session_id TEXT",
        "CREATE INDEX IF NOT EXISTS idx_qlog_task ON question_log(agenda_task_id)",
        "CREATE INDEX IF NOT EXISTS idx_qlog_session ON question_log(focus_session_id)",
    )
}
