package com.pranav.focus.data

import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

/** [minPerQuestionHundredths] = Time Bank minutes earned per logged question, in hundredths: Physics 1.10, Chemistry 0.78, Biology 0.34. */
enum class Subject(val label: String, val maxMarks: Int, val minPerQuestionHundredths: Int) {
    PHYSICS("Physics", 180, 110), CHEMISTRY("Chemistry", 180, 78), BIOLOGY("Biology", 360, 34);
    companion object { fun byLabel(s: String) = values().firstOrNull { it.label.equals(s, true) } }
}

/** Sub-sections per subject (user can move a chapter between them). */
fun groupsFor(s: Subject): List<String> = when (s) {
    Subject.BIOLOGY -> listOf("Botany", "Zoology")
    Subject.CHEMISTRY -> listOf("Physical", "Organic", "Inorganic")
    else -> emptyList()
}

enum class Source(val label: String) { NCERT("NCERT"), PYQ("PYQ"), MODULE("Module"), MOCK("Mock") }
enum class MistakeType(val label: String) { CONCEPT("Concept"), SILLY("Silly"), CALC("Calculation"), GUESS("Guess"), TIME("Time") }

data class Chapter(val id: Int, val subject: Subject, val name: String, val group: String = "", val active: Boolean = true)
data class Book(val id: Int, val name: String, val active: Boolean = true)
data class QLog(
    val id: Long, val ts: Long, val subject: Subject, val chapterId: Int, val source: Source,
    val correct: Int, val wrong: Int, val skipped: Int, val minutes: Int, val verified: Boolean, val bookId: Int = 0,
    val agendaTaskId: String? = null, val focusSessionId: String? = null,
) { val attempted get() = correct + wrong }

data class Mock(val id: Long, val date: String, val name: String, val c: List<Int>, val w: List<Int>, val s: List<Int>, val target: Int) {
    fun marks(i: Int) = StudyLogic.marks(c[i], w[i])
    val score get() = (0..2).sumOf { marks(it) }
    val negative get() = w.sum()
    val skippedTotal get() = s.sum()
}

data class Mistake(
    val id: Long, val subject: Subject, val chapterId: Int, val ref: String, val type: MistakeType,
    val note: String, val due: String, val doneCount: Int,
)

data class Tally(val correct: Int = 0, val wrong: Int = 0, val skipped: Int = 0) {
    val attempted get() = correct + wrong
    val marks get() = StudyLogic.marks(correct, wrong)
    val accPct get() = StudyLogic.accPct(correct, wrong)
    operator fun plus(o: Tally) = Tally(correct + o.correct, wrong + o.wrong, skipped + o.skipped)
}

fun List<QLog>.tally() = fold(Tally()) { a, l -> a + Tally(l.correct, l.wrong, l.skipped) }

/** Pure NEET logic. Marks = +4 correct, -1 wrong. */
object StudyLogic {
    val INTERVALS = listOf(1, 3, 7, 21)
    const val XP_DAILY_CAP = 300
    const val MOCK_XP = 25

    fun marks(correct: Int, wrong: Int) = 4 * correct - wrong
    fun accuracy(correct: Int, wrong: Int): Float = if (correct + wrong == 0) 0f else correct.toFloat() / (correct + wrong)
    fun accPct(correct: Int, wrong: Int) = (accuracy(correct, wrong) * 100).roundToInt()

    fun nextDue(today: LocalDate, doneCount: Int): LocalDate = today.plusDays(INTERVALS[min(doneCount, INTERVALS.lastIndex)].toLong())

    fun daysTo(target: LocalDate, today: LocalDate = AppClock.today()): Long = java.time.temporal.ChronoUnit.DAYS.between(today, target)

    /** Change in accuracy percentage points, or null when either period has no attempts. */
    fun deltaPct(cur: Tally, prev: Tally): Int? = if (cur.attempted == 0 || prev.attempted == 0) null else cur.accPct - prev.accPct

    /** Weakest first, ignoring anything below the attempt floor. */
    fun <T> weakest(items: List<Pair<T, Tally>>, floor: Int = 10): List<Pair<T, Tally>> =
        items.filter { it.second.attempted >= floor }.sortedBy { it.second.accuracy() }

    private fun Tally.accuracy() = StudyLogic.accuracy(correct, wrong)

    /** Whole Time Bank minutes for a day's questions: sum of (questions x rate), kept in hundredths, then floored. */
    fun timeBankMinutes(bySubject: Map<Subject, Int>): Int = bySubject.entries.sumOf { (s, n) -> n.coerceAtLeast(0) * s.minPerQuestionHundredths } / 100

    fun xpFor(attempted: Int, correct: Int, alreadyToday: Int): Int =
        min(attempted + 2 * correct, (XP_DAILY_CAP - alreadyToday).coerceAtLeast(0))

    /** 0 hollow (<50%), 1 half (50-70%), 2 filled (>70%). */
    fun status(accPct: Int) = when { accPct < 50 -> 0; accPct <= 70 -> 1; else -> 2 }
}
