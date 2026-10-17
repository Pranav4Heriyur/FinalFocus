package com.pranav.focus

import com.pranav.focus.workout.Row
import com.pranav.focus.workout.SqlRunner
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types

/** sqlite-jdbc backed [SqlRunner] for JVM tests (same SQL the app runs on android.database.sqlite). */
class JdbcRunner(private val c: Connection) : SqlRunner {
    private var depth = 0
    private fun bind(ps: java.sql.PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> ps.setNull(i + 1, Types.NULL)
                is Int -> ps.setLong(i + 1, a.toLong())
                is Long -> ps.setLong(i + 1, a)
                is Double -> ps.setDouble(i + 1, a)
                else -> ps.setString(i + 1, a.toString())   // like Android rawQuery: everything arrives as text
            }
        }
    }
    override fun exec(sql: String, vararg args: Any?) { c.prepareStatement(sql).use { bind(it, args); it.executeUpdate() } }
    override fun query(sql: String, vararg args: Any?): List<Row> = c.prepareStatement(sql).use { ps ->
        bind(ps, args)
        ps.executeQuery().use { rs ->
            val md = rs.metaData; val out = ArrayList<Row>()
            while (rs.next()) out.add((1..md.columnCount).associate { md.getColumnLabel(it) to rs.getObject(it) })
            out
        }
    }
    override fun <T> tx(block: () -> T): T {
        if (depth > 0) return block()
        depth++; c.autoCommit = false
        try { val r = block(); c.commit(); return r } catch (t: Throwable) { c.rollback(); throw t } finally { depth--; c.autoCommit = true }
    }
    companion object {
        fun memory(): JdbcRunner = JdbcRunner(DriverManager.getConnection("jdbc:sqlite::memory:"))
        fun file(path: String): JdbcRunner = JdbcRunner(DriverManager.getConnection("jdbc:sqlite:$path"))
    }
}
