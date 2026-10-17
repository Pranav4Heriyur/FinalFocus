package com.pranav.focus.workout

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** workout.db: its own SQLite file, fully independent of study.db and SharedPreferences. */
class WorkoutDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "workout.db", null, WorkoutSchema.VERSION) {
    override fun onCreate(db: SQLiteDatabase) { WorkoutSchema.applySafely(WorkoutSchema.upgradeSteps(0, WorkoutSchema.VERSION)) { db.execSQL(it) } }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) { WorkoutSchema.applySafely(WorkoutSchema.upgradeSteps(o, n)) { db.execSQL(it) } }

    private val runner = object : SqlRunner {
        private val lock = Any()
        private var depth = 0
        override fun exec(sql: String, vararg args: Any?) = synchronized(lock) {
            if (args.isEmpty()) writableDatabase.execSQL(sql) else writableDatabase.execSQL(sql, arrayOf(*args))
        }
        override fun query(sql: String, vararg args: Any?): List<Row> = synchronized(lock) {
            writableDatabase.rawQuery(sql, Array(args.size) { args[it].toString() }).use { c -> rows(c) }
        }
        override fun <T> tx(block: () -> T): T = synchronized(lock) {
            if (depth > 0) return block()
            val db = writableDatabase
            depth++; db.beginTransaction()
            try { val r = block(); db.setTransactionSuccessful(); r } finally { db.endTransaction(); depth-- }
        }
        private fun rows(c: Cursor): List<Row> {
            val out = ArrayList<Row>(); val n = c.columnCount
            while (c.moveToNext()) {
                val m = HashMap<String, Any?>(n)
                for (i in 0 until n) m[c.getColumnName(i)] = when (c.getType(i)) {
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> c.getString(i)
                    else -> null
                }
                out.add(m)
            }
            return out
        }
    }

    val repo = WorkoutRepo(runner)

    companion object {
        @Volatile private var inst: WorkoutDb? = null
        /** Opens workout.db and runs the idempotent seed once per process. */
        fun get(ctx: Context): WorkoutDb = inst ?: synchronized(this) {
            inst ?: WorkoutDb(ctx).also { it.seedOnce(); inst = it }
        }
    }

    private fun seedOnce() = WorkoutSeeder.seed(runner)
}
