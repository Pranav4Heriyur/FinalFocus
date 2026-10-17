package com.pranav.focus.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Study data lives in its own SQLite file (framework API, no extra dependency). */
class StudyDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "study.db", null, 3) {
    private val prefs = ctx.applicationContext.getSharedPreferences("study_prefs", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE chapter(id INTEGER PRIMARY KEY, subject INT, name TEXT, unit_order INT, grp TEXT DEFAULT '', active INT DEFAULT 1)")
        db.execSQL("CREATE TABLE book(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, active INT DEFAULT 1)")
        StudySeed.BOOKS.forEach { b -> db.insert("book", null, ContentValues().apply { put("name", b) }) }
        db.execSQL("CREATE TABLE question_log(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INT, subject INT, chapter_id INT, source INT, correct INT, wrong INT, skipped INT, minutes INT, verified INT, book_id INT DEFAULT 0, agenda_task_id TEXT, focus_session_id TEXT)")
        db.execSQL("CREATE INDEX idx_qlog_task ON question_log(agenda_task_id)")
        db.execSQL("CREATE INDEX idx_qlog_session ON question_log(focus_session_id)")
        db.execSQL("CREATE TABLE mock_test(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT, name TEXT, p_c INT,p_w INT,p_s INT, c_c INT,c_w INT,c_s INT, b_c INT,b_w INT,b_s INT, target INT)")
        db.execSQL("CREATE TABLE mistake(id INTEGER PRIMARY KEY AUTOINCREMENT, subject INT, chapter_id INT, ref TEXT, type INT, note TEXT, due TEXT, done_count INT)")
        var i = 0
        StudySeed.all().forEach { (s, n) ->
            db.insert("chapter", null, ContentValues().apply { put("id", ++i); put("subject", s.ordinal); put("name", n); put("unit_order", i); put("grp", StudySeed.groupOf(s, n)) })
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) {
            db.execSQL("ALTER TABLE chapter ADD COLUMN grp TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE chapter ADD COLUMN active INT DEFAULT 1")
            db.execSQL("ALTER TABLE question_log ADD COLUMN book_id INT DEFAULT 0")
            db.execSQL("CREATE TABLE book(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, active INT DEFAULT 1)")
            StudySeed.BOOKS.forEach { b -> db.insert("book", null, ContentValues().apply { put("name", b) }) }
            // earlier logs used the Source enum (NCERT, PYQ, Module, Mock) = book ids 1..4
            db.execSQL("UPDATE question_log SET book_id = source + 1")
            db.rawQuery("SELECT id, subject, name FROM chapter", null).use {
                while (it.moveToNext()) db.execSQL("UPDATE chapter SET grp = ? WHERE id = ?", arrayOf(StudySeed.groupOf(Subject.values()[it.getInt(1)], it.getString(2)), it.getInt(0)))
            }
        }
        if (o < 3) migrateToV3(db)
    }

    /**
     * v3: nullable links from a log to the agenda task / focus session it came from. Existing rows stay NULL (no guessed backfill).
     * Foreign keys are intentionally NOT declared: agenda tasks live in SharedPreferences and focus sessions have no table,
     * so there is no parent row to reference. Dangling ids are tolerated by readers; indexes keep lookups cheap.
     */
    internal fun migrateToV3(db: SQLiteDatabase) = StudyMigrations.V3.forEach { db.execSQL(it) }

    // ---- prefs ----------------------------------------------------------------
    /** Optional user setting. Null until the user sets it; any previously saved explicit date is kept as-is. */
    var neetDate: LocalDate?
        get() = prefs.getString("neet", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) { prefs.edit().apply { if (v == null) remove("neet") else putString("neet", v.toString()) }.apply() }

    var targetScore: Int
        get() = prefs.getInt("target", 600)
        set(v) = prefs.edit().putInt("target", v).apply()

    // ---- chapters --------------------------------------------------------------
    fun chapters(includeHidden: Boolean = false): List<Chapter> {
        val out = ArrayList<Chapter>()
        readableDatabase.rawQuery("SELECT id, subject, name, grp, active FROM chapter ${if (includeHidden) "" else "WHERE active = 1"} ORDER BY unit_order, id", null).use {
            while (it.moveToNext()) out.add(Chapter(it.getInt(0), Subject.values()[it.getInt(1)], it.getString(2), it.getString(3) ?: "", it.getInt(4) == 1))
        }
        return out
    }

    fun addChapter(s: Subject, name: String, grp: String): Long = writableDatabase.insert("chapter", null, ContentValues().apply {
        put("subject", s.ordinal); put("name", name.trim()); put("grp", grp); put("active", 1)
        put("unit_order", 100000 + (System.currentTimeMillis() % 100000).toInt())
    })
    fun updateChapter(id: Int, name: String, grp: String) {
        writableDatabase.update("chapter", ContentValues().apply { put("name", name.trim()); put("grp", grp) }, "id = ?", arrayOf(id.toString()))
    }
    /** Hidden, not deleted: question history and mistakes stay intact. */
    fun setChapterActive(id: Int, on: Boolean) {
        writableDatabase.update("chapter", ContentValues().apply { put("active", if (on) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    // ---- books / sources ---------------------------------------------------------
    fun books(includeHidden: Boolean = false): List<Book> {
        val out = ArrayList<Book>()
        readableDatabase.rawQuery("SELECT id, name, active FROM book ${if (includeHidden) "" else "WHERE active = 1"} ORDER BY id", null).use {
            while (it.moveToNext()) out.add(Book(it.getInt(0), it.getString(1), it.getInt(2) == 1))
        }
        return out
    }
    fun addBook(name: String): Long = writableDatabase.insert("book", null, ContentValues().apply { put("name", name.trim()); put("active", 1) })
    fun setBookActive(id: Int, on: Boolean) {
        writableDatabase.update("book", ContentValues().apply { put("active", if (on) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    // ---- logs ------------------------------------------------------------------
    fun insertLog(l: QLog): Long = writableDatabase.insert("question_log", null, ContentValues().apply {
        put("ts", l.ts); put("subject", l.subject.ordinal); put("chapter_id", l.chapterId); put("source", l.source.ordinal)
        put("correct", l.correct); put("wrong", l.wrong); put("skipped", l.skipped); put("minutes", l.minutes); put("verified", if (l.verified) 1 else 0); put("book_id", l.bookId)
        put("agenda_task_id", l.agendaTaskId); put("focus_session_id", l.focusSessionId)
    })

    fun logs(sinceMs: Long = 0): List<QLog> {
        val out = ArrayList<QLog>()
        readableDatabase.rawQuery(
            "SELECT id, ts, subject, chapter_id, source, correct, wrong, skipped, minutes, verified, book_id, agenda_task_id, focus_session_id FROM question_log WHERE ts >= ? ORDER BY ts",
            arrayOf(sinceMs.toString()),
        ).use {
            while (it.moveToNext()) out.add(QLog(it.getLong(0), it.getLong(1), Subject.values()[it.getInt(2)], it.getInt(3),
                Source.values()[it.getInt(4)], it.getInt(5), it.getInt(6), it.getInt(7), it.getInt(8), it.getInt(9) == 1, it.getInt(10), it.getString(11), it.getString(12)))
        }
        return out
    }

    /** Total attempted (correct + wrong) for logs with ts in [fromMs, toMs). */
    fun attemptedBetween(fromMs: Long, toMs: Long): Int =
        readableDatabase.rawQuery("SELECT COALESCE(SUM(correct + wrong), 0) FROM question_log WHERE ts >= ? AND ts < ?", arrayOf(fromMs.toString(), toMs.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    /** Saves a log and its mistake notes atomically: either all rows exist or none do. */
    fun insertLogWithMistakes(l: QLog, mistakes: List<Mistake>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = insertLog(l)
            mistakes.forEach { insertMistake(it) }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    // ---- mocks -----------------------------------------------------------------
    fun insertMock(m: Mock): Long = writableDatabase.insert("mock_test", null, ContentValues().apply {
        put("date", m.date); put("name", m.name)
        put("p_c", m.c[0]); put("p_w", m.w[0]); put("p_s", m.s[0])
        put("c_c", m.c[1]); put("c_w", m.w[1]); put("c_s", m.s[1])
        put("b_c", m.c[2]); put("b_w", m.w[2]); put("b_s", m.s[2]); put("target", m.target)
    })

    fun mocks(): List<Mock> {
        val out = ArrayList<Mock>()
        readableDatabase.rawQuery("SELECT id, date, name, p_c,p_w,p_s,c_c,c_w,c_s,b_c,b_w,b_s,target FROM mock_test ORDER BY date DESC, id DESC", null).use {
            while (it.moveToNext()) out.add(Mock(it.getLong(0), it.getString(1), it.getString(2),
                listOf(it.getInt(3), it.getInt(6), it.getInt(9)), listOf(it.getInt(4), it.getInt(7), it.getInt(10)),
                listOf(it.getInt(5), it.getInt(8), it.getInt(11)), it.getInt(12)))
        }
        return out
    }

    // ---- mistakes --------------------------------------------------------------
    fun insertMistake(m: Mistake): Long = writableDatabase.insert("mistake", null, ContentValues().apply {
        put("subject", m.subject.ordinal); put("chapter_id", m.chapterId); put("ref", m.ref); put("type", m.type.ordinal)
        put("note", m.note); put("due", m.due); put("done_count", m.doneCount)
    })

    fun mistakes(): List<Mistake> {
        val out = ArrayList<Mistake>()
        readableDatabase.rawQuery("SELECT id, subject, chapter_id, ref, type, note, due, done_count FROM mistake ORDER BY due, id", null).use {
            while (it.moveToNext()) out.add(Mistake(it.getLong(0), Subject.values()[it.getInt(1)], it.getInt(2), it.getString(3),
                MistakeType.values()[it.getInt(4)], it.getString(5), it.getString(6), it.getInt(7)))
        }
        return out
    }

    fun markRevised(m: Mistake) {
        writableDatabase.update("mistake", ContentValues().apply {
            put("done_count", m.doneCount + 1); put("due", StudyLogic.nextDue(AppClock.today(), m.doneCount).toString())
        }, "id = ?", arrayOf(m.id.toString()))
    }

    companion object {
        @Volatile private var inst: StudyDb? = null
        fun get(ctx: Context): StudyDb = inst ?: synchronized(this) { inst ?: StudyDb(ctx).also { inst = it } }
    }
}
