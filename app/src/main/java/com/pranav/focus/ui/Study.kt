package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Chapter
import com.pranav.focus.data.Mistake
import com.pranav.focus.data.MistakeType
import com.pranav.focus.data.Mock
import com.pranav.focus.data.QLog
import com.pranav.focus.data.Source
import com.pranav.focus.data.Store
import com.pranav.focus.data.StudyDb
import com.pranav.focus.data.StudyLogic
import com.pranav.focus.data.Subject
import com.pranav.focus.data.Tally
import com.pranav.focus.data.tally
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.Earning
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

sealed class SR {
    object Hub : SR()
    data class Log(val subject: Subject? = null, val chapterId: Int? = null, val taskId: String = "", val sessionId: String = "") : SR()
    data class Sub(val subject: Subject) : SR()
    data class Chap(val id: Int) : SR()
    object MockLog : SR()
    object MockTrend : SR()
    data class MockRes(val id: Long) : SR()
    data class Notebook(val subject: Subject? = null) : SR()
    object Syllabus : SR()
}

/** Which Study view is showing: 0 Practice (hub), 1 Stats. Study is its own tab. */
object StudyNav {
    var tab by mutableIntStateOf(0)
}

/** Snapshot of everything the Study screens read. Always derived from the persisted tables. */
class SData(val chapters: List<Chapter>, val books: List<com.pranav.focus.data.Book>, val logs: List<QLog>, val mocks: List<Mock>, val mistakes: List<Mistake>, val db: StudyDb) {
    private val zone = ZoneId.systemDefault()
    fun day(l: QLog): LocalDate = Instant.ofEpochMilli(l.ts).atZone(zone).toLocalDate()
    val active get() = chapters.filter { it.active }
    fun chapter(id: Int) = chapters.firstOrNull { it.id == id }
    fun between(from: LocalDate, toExcl: LocalDate) = logs.filter { day(it) >= from && day(it) < toExcl }
    fun week(offset: Int): List<QLog> { val end = AppClock.today().plusDays(1).minusDays(7L * offset); return between(end.minusDays(7), end) }
    fun chapterTally(id: Int) = logs.filter { it.chapterId == id }.tally()
}

@Composable
fun StudyRoot(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val stack = remember {
        mutableStateListOf<SR>(SR.Hub).also { st ->
            // Arrived from the "session complete" screen: open Quick Log pre-filled from the planner task that was just focused.
            val store = Store.get(ctx)
            if (store.str("study_open") == "mock_trend") { store.putStr("study_open", ""); st.add(SR.MockTrend) }
            if (store.int("study_prefill") == 1) {
                store.putInt("study_prefill", 0)
                val tid = store.str("last_session_task")
                val subj = Subject.byLabel(PlanState.get(tid)?.subject ?: store.str("last_session_subject"))
                if (subj != null) st.add(SR.Log(subj, null, tid, store.str("last_session_id")))
            }
        }
    }
    var ver by remember { mutableIntStateOf(0) }
    val tick = AppState.dataTick + ver
    val data = remember(tick) {
        val db = StudyDb.get(ctx)
        SData(db.chapters(true), db.books(true), db.logs(), db.mocks(), db.mistakes(), db)
    }
    fun push(r: SR) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1) { pop() }
    val bump: () -> Unit = { ver++; AppState.dataTick++ }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp)) {
        when (val r = stack.last()) {
            SR.Hub -> {
                SegFull(listOf("Practice", "Stats"), StudyNav.tab) { StudyNav.tab = it }
                Spacer(Modifier.height(12.dp))
                if (StudyNav.tab == 1) StudyStatsBody(data) else StudyHub(data, ::push)
            }
            is SR.Log -> QuickLog(data, r, ::pop, bump)
            is SR.Sub -> SubjectDetail(data, r.subject, ::pop, ::push)
            is SR.Chap -> ChapterDetail(data, r.id, ::pop, ::push, bump)
            SR.MockLog -> MockLogScreen(data, ::pop) { id -> stack.removeAt(stack.lastIndex); push(SR.MockRes(id)); bump() }
            SR.MockTrend -> MockTrendScreen(data, ::pop, ::push)
            is SR.MockRes -> MockResult(data, r.id, ::pop, ::push)
            is SR.Notebook -> Notebook(data, r.subject, ::pop, bump)
            SR.Syllabus -> Syllabus(data, ::pop, ::push, bump)
        }
    }
}

// ---------------------------------------------------------------------------------------
// B0 Hub
// ---------------------------------------------------------------------------------------
@Composable
private fun StudyHub(d: SData, push: (SR) -> Unit) {
    val today = d.between(AppClock.today(), AppClock.today().plusDays(1))
    val tt = today.tally()
    var exam by remember { mutableStateOf(d.db.neetDate) }
    var editExam by remember { mutableStateOf(false) }
    var examText by remember { mutableStateOf("") }
    var examErr by remember { mutableStateOf(false) }
    val days = exam?.let { StudyLogic.daysTo(it, AppClock.today()) }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Study", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (days == null) Pill("Set exam date", onClick = { examText = ""; examErr = false; editExam = true })
        else Pill(if (days < 0) "NEET date passed  ›" else "NEET: $days days  ›", onClick = { StudyNav.tab = 1 })
    }
    if (editExam) androidx.compose.material3.AlertDialog(
        onDismissRequest = { editExam = false },
        containerColor = C.Sheet,
        title = { Text("Exam date", color = C.White) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(examText, { examText = it; examErr = false }, singleLine = true, placeholder = { Text("YYYY-MM-DD") })
                if (examErr) Text("Use the format YYYY-MM-DD.", color = C.Text2, fontSize = 12.sp)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton({
                val v = runCatching { java.time.LocalDate.parse(examText.trim()) }.getOrNull()
                if (v == null) examErr = true else { d.db.neetDate = v; exam = v; editExam = false }
            }) { Text("Save", color = C.White) }
        },
        dismissButton = { if (exam != null) androidx.compose.material3.TextButton({ d.db.neetDate = null; exam = null; editExam = false }) { Text("Clear", color = C.Text2) } },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Subject.values().forEachIndexed { i, s ->
            Reveal(i) {
                val cur = d.week(0).filter { it.subject == s }.tally()
                val prev = d.week(1).filter { it.subject == s }.tally()
                val all = d.logs.filter { it.subject == s }.tally()
                val todayQ = today.filter { it.subject == s }.sumOf { it.attempted + it.skipped }
                SectionCard(onClick = { push(SR.Sub(s)) }, pad = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(subjectIcon(s), size = 52.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.label, color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(subjectBlurb(s), color = C.Text2, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2)
                        }
                        AccRing(if (all.attempted == 0) 0 else all.accPct, 54.dp)
                        Column(Modifier.padding(start = 10.dp).width(88.dp)) {
                            Text("Today", color = C.Text2, fontSize = 10.sp)
                            Text("$todayQ questions", color = C.White, fontSize = 12.sp)
                            Spacer(Modifier.height(3.dp))
                            DeltaChip(StudyLogic.deltaPct(cur, prev), "%")
                        }
                    }
                }
            }
        }
        Reveal(3) {
            SectionCard {
                Text("Today", color = C.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    HubStat(R.drawable.p1_study_book, "Questions", "${tt.attempted + tt.skipped}", Modifier.weight(1f))
                    HubStat(R.drawable.p1_check_outline, "Accuracy", if (tt.attempted == 0) "—" else "${tt.accPct}%", Modifier.weight(1f))
                    HubStat(R.drawable.p1_stats_bars, "Net marks", if (tt.attempted == 0) "—" else (if (tt.marks >= 0) "+" else "") + tt.marks, Modifier.weight(1f))
                }
            }
        }
        Reveal(4) {
            val weak = StudyLogic.weakest(d.active.map { it to d.chapterTally(it.id) }).take(3)
            SectionCard(pad = 14.dp) {
                Text("Weak chapters", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (weak.isEmpty()) Text("Log at least 10 questions in a chapter to see it here.", color = C.Text2, fontSize = 12.sp)
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weak.forEach { (c, t) ->
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(C.Raised).clickable { push(SR.Chap(c.id)) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AccRing(t.accPct, 26.dp, 3.dp, label = false)
                            Column(Modifier.padding(start = 6.dp)) {
                                Text(c.name, color = C.White, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2)
                                Text("${t.accPct}%", color = C.Text2, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        Reveal(5) {
            val last = d.mocks.firstOrNull(); val prev = d.mocks.getOrNull(1)
            SectionCard(onClick = { push(if (last != null) SR.MockRes(last.id) else SR.MockLog) }, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(R.drawable.plan_ic_notes, size = 44.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Last mock", color = C.Text2, fontSize = 11.sp)
                        Text(if (last != null) "${last.score} / 720" else "No mock yet", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                    if (last != null && prev != null) DeltaChip(last.score - prev.score)
                    Ico(R.drawable.p1_chevron_right, 18.dp, C.Text2, Modifier.padding(start = 8.dp))
                }
            }
        }
        SectionCard(onClick = { push(SR.MockTrend) }, pad = 14.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.p1_stats_bars, 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Mock trend", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Score graph over time, per subject, against target", color = C.Text2, fontSize = 12.sp)
                }
                Ico(R.drawable.p1_chevron_right, 18.dp, C.Text2)
            }
        }
        SectionCard(onClick = { push(SR.Syllabus) }, pad = 14.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.plan_ic_list_checks, 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Syllabus tracker", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text("All chapters, books, last done, accuracy", color = C.Text2, fontSize = 12.sp) }
                Ico(R.drawable.p1_chevron_right, 18.dp, C.Text2)
            }
        }
        PrimaryButton("Log questions") { push(SR.Log()) }
    }
}

@Composable
private fun HubStat(icon: Int, l: String, v: String, m: Modifier) {
    Column(m) {
        Row(verticalAlignment = Alignment.CenterVertically) { Ico(icon, 14.dp, C.Text2); Text(" $l", color = C.Text2, fontSize = 11.sp) }
        Text(v, style = glow(32f, 300, soft = true))
    }
}

// ---------------------------------------------------------------------------------------
// B1 Quick log
// ---------------------------------------------------------------------------------------
@Composable
private fun QuickLog(d: SData, r: SR.Log, back: () -> Unit, bump: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var subject by remember { mutableIntStateOf((r.subject ?: d.chapter(r.chapterId ?: -1)?.subject ?: Subject.PHYSICS).ordinal) }
    var chapterId by remember { mutableIntStateOf(r.chapterId ?: -1) }
    var bookId by remember { mutableIntStateOf(d.books.firstOrNull { it.active }?.id ?: 0) }
    var newBook by remember { mutableStateOf(false) }
    var c by remember { mutableIntStateOf(0) }
    var w by remember { mutableIntStateOf(0) }
    var s by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var minutes by remember { mutableIntStateOf(0) }
    val types = remember { mutableStateListOf<MistakeType>() }
    var picking by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) } // a double tap must not log (or award XP) twice
    val sub = Subject.values()[subject]
    val chapters = d.active.filter { it.subject == sub }
    val chap = chapters.firstOrNull { it.id == chapterId } ?: chapters.first().also { chapterId = it.id }
    val t = Tally(c, w, s)

    StudyTopBar("Quick Log", "Log your questions from any source.", back)
    SegPill(Subject.values().map { it.label }, subject) { subject = it; chapterId = -1; picking = false }
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 12.dp, radius = 20.dp, onClick = { picking = !picking }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.plan_ic_book, 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Chapter", color = C.Text2, fontSize = 11.sp); Text(chap.name, color = C.White, fontSize = 15.sp, maxLines = 1) }
            Ico(if (picking) R.drawable.p1_chevron_down else R.drawable.p1_chevron_right, 18.dp, C.Text2)
        }
    }
    if (picking) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp).height(220.dp).clip(RoundedCornerShape(16.dp)).background(C.Sheet).verticalScroll(rememberScrollState())) {
            chapters.forEach { ch ->
                Text(ch.name, color = if (ch.id == chap.id) C.White else C.Text2, fontSize = 14.sp, fontWeight = if (ch.id == chap.id) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { chapterId = ch.id; picking = false }.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Text("Book / source", color = C.Text2, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.books.filter { it.active }.forEach { b -> Pill(b.name, filled = bookId == b.id, onClick = { bookId = b.id }) }
        Pill("+ New") { newBook = true }
    }
    if (newBook) NameDialog("Add book or source", "", emptyList(), "", { newBook = false }) { n, _ -> bookId = d.db.addBook(n).toInt(); newBook = false; bump() }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StepperTile("Correct", c, 0, Modifier.weight(1f)) { c = it }
        StepperTile("Wrong", w, 1, Modifier.weight(1f)) { w = it }
        StepperTile("Skipped", s, 2, Modifier.weight(1f)) { s = it }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 14.dp, radius = 20.dp) {
        Row {
            StatCell("${t.attempted}", "Attempted", Modifier.weight(1f), "($c + $w)")
            StatCell(if (t.attempted == 0) "—" else "${t.accPct}%", "Accuracy", Modifier.weight(1f), "($c / ${t.attempted})")
            StatCell((if (t.marks >= 0) "+" else "") + t.marks, "Marks", Modifier.weight(1f), "(4 × $c − $w)")
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 14.dp, radius = 20.dp, onClick = { more = !more }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.p1_clock, 20.dp)
            Text("Time and mistake types", color = C.White, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Ico(if (more) R.drawable.p1_chevron_down else R.drawable.p1_chevron_right, 18.dp, C.Text2)
        }
        if (more) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minutes spent", color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f))
                StepperMini(minutes) { minutes = it }
            }
            Spacer(Modifier.height(10.dp))
            Text("Mistake types (for the wrong ones)", color = C.Text2, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MistakeType.values().forEach { m ->
                    Pill(m.label, filled = m in types, modifier = Modifier.weight(1f)) { if (m in types) types.remove(m) else types.add(m) }
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Save", enabled = t.attempted + s > 0 && !saved) {
      if (!saved) {
        saved = true
        val now = System.currentTimeMillis()
        val inLast = now - store.long("last_session_end") < 60 * 60_000L
        val bookName = d.books.firstOrNull { it.id == bookId }?.name ?: "Practice"
        val notes = if (w > 0) types.map { m -> Mistake(0, sub, chap.id, "$bookName · ${chap.name}", m, "", AppClock.today().toString(), 0) } else emptyList()
        val logId = d.db.insertLogWithMistakes(QLog(0, now, sub, chap.id, Source.values().getOrElse(bookId - 1) { Source.NCERT }, c, w, s, minutes, inLast, bookId,
            r.taskId.ifEmpty { null }, r.sessionId.ifEmpty { null }), notes)
        if (r.taskId.isNotEmpty()) store.putInt("tq_${r.taskId}", store.int("tq_${r.taskId}") + t.attempted)
        val gain = StudyLogic.xpFor(t.attempted, c, store.xpLedger.on(store.today(), "study"))
        if (gain > 0) store.awardXp("study:${logId}", "study", gain)
        runCatching { Earning.creditStudyQuestions(ctx) }   // per-question Time Bank minutes, on top of the daily 60-question rule
        runCatching { Earning.evaluate(ctx) }
        bump(); back()
      }
    }
}

@Composable
private fun StepperMini(v: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, C.Text3, CircleShape).clickable { onChange((v - 5).coerceAtLeast(0)) }, contentAlignment = Alignment.Center) { Text("−", color = C.White, fontSize = 18.sp) }
        Text("$v", color = C.White, fontSize = 16.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Box(Modifier.size(32.dp).clip(CircleShape).border(1.dp, C.Text3, CircleShape).clickable { onChange(v + 5) }, contentAlignment = Alignment.Center) { Text("+", color = C.White, fontSize = 18.sp) }
    }
}

// ---------------------------------------------------------------------------------------
// B2 Subject
// ---------------------------------------------------------------------------------------
@Composable
private fun SubjectDetail(d: SData, s: Subject, back: () -> Unit, push: (SR) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val all = d.logs.filter { it.subject == s }
    val tAll = all.tally()
    val month = d.between(AppClock.today().minusDays(29), AppClock.today().plusDays(1)).filter { it.subject == s }.tally()
    StudyTopBar("", null, back)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(subjectIcon(s), size = 56.dp)
        Column(Modifier.padding(start = 14.dp)) {
            Text(s.label, color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subjectBlurb(s), color = C.Text2, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
    SectionCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccRing(if (tAll.attempted == 0) 0 else tAll.accPct, 84.dp, 7.dp)
            StatCell("${month.attempted + month.skipped}", "Questions", Modifier.weight(1f), "this month")
            StatCell((if (month.marks >= 0) "+" else "") + month.marks, "Net marks", Modifier.weight(1f), "this month")
        }
    }
    Spacer(Modifier.height(12.dp))
    SegPill(listOf("Chapters", "Mistakes", "Trend"), tab) { tab = it }
    Spacer(Modifier.height(12.dp))
    when (tab) {
        0 -> {
            val rows = d.active.filter { it.subject == s }.map { it to d.chapterTally(it.id) }
                .sortedWith(compareBy({ it.second.attempted == 0 }, { it.second.accPct }))
            SectionCard(pad = 4.dp) {
                Text("Chapters (weakest first)", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                rows.forEachIndexed { i, (c, t) ->
                    val prev = d.week(1).filter { it.chapterId == c.id }.tally(); val cur = d.week(0).filter { it.chapterId == c.id }.tally()
                    Row(Modifier.fillMaxWidth().clickable { push(SR.Chap(c.id)) }.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = C.Text3, fontSize = 11.sp, modifier = Modifier.width(18.dp))
                        Text(c.name, color = C.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${t.attempted}", color = C.Text2, fontSize = 12.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                        SlimBar(if (t.attempted == 0) 0f else t.accPct / 100f, Modifier.padding(horizontal = 8.dp).width(46.dp))
                        Text(if (t.attempted == 0) "—" else "${t.accPct}%", color = C.White, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                        Spacer(Modifier.width(6.dp))
                        DeltaChip(StudyLogic.deltaPct(cur, prev), "%")
                        Spacer(Modifier.width(8.dp))
                        StatusDot(if (t.attempted == 0) 0 else t.accPct)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Practise weakest") {
                val w = rows.firstOrNull { it.second.attempted > 0 }?.first ?: rows.first().first
                push(SR.Log(s, w.id))
            }
        }
        1 -> { PrimaryButton("Open mistake notebook") { push(SR.Notebook(s)) } }
        else -> {
            val pts = (13 downTo 0).map { k ->
                val day = AppClock.today().minusDays(k.toLong())
                val t = all.filter { d.day(it) == day }.tally(); if (t.attempted == 0) null else t.accPct / 100f
            }
            SectionCard { Text("Accuracy, last 14 days", color = C.White, fontSize = 14.sp); Spacer(Modifier.height(8.dp)); LineChart(listOf(pts), Modifier.fillMaxWidth().height(140.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------------------
// B3 Chapter
// ---------------------------------------------------------------------------------------
@Composable
private fun ChapterDetail(d: SData, id: Int, back: () -> Unit, push: (SR) -> Unit, bump: () -> Unit) {
    val ch = d.chapter(id) ?: run { back(); return }
    val logs = d.logs.filter { it.chapterId == id }
    val t = logs.tally()
    val cur = d.week(0).filter { it.chapterId == id }.tally(); val prev = d.week(1).filter { it.chapterId == id }.tally()
    val mistakes = d.mistakes.filter { it.chapterId == id }
    StudyTopBar(ch.name, ch.subject.label, back)
    SectionCard {
        Text("Accuracy", color = C.Text2, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (t.attempted == 0) "—" else "${t.accPct}%", style = glow(52f, 300))
            Spacer(Modifier.width(12.dp)); DeltaChip(StudyLogic.deltaPct(cur, prev), "% vs last week")
        }
        Spacer(Modifier.height(8.dp))
        LineChart(listOf(logs.takeLast(8).map { if (it.attempted == 0) null else it.correct.toFloat() / it.attempted }), Modifier.fillMaxWidth().height(120.dp))
        Text("Last ${logs.takeLast(8).size} sessions", color = C.Text3, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 12.dp, radius = 20.dp) {
        val mins = logs.sumOf { it.minutes }
        Row {
            StatCell("${t.attempted}", "Attempted", Modifier.weight(1f))
            StatCell("${t.correct}", "Correct", Modifier.weight(1f))
            StatCell("${t.wrong}", "Wrong", Modifier.weight(1f))
            StatCell(if (mins == 0 || t.attempted == 0) "—" else "${mins * 60 / t.attempted}s", "Avg time", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text("Mistake types", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        val counts = MistakeType.values().map { m -> mistakes.count { it.type == m } }
        val total = counts.sum()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(counts, Modifier.size(120.dp)) { Text("$total", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 24.sp, fontWeight = FontWeight.Medium); Text("mistakes", color = C.Text2, fontSize = 10.sp) }
            Column(Modifier.weight(1f).padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MistakeType.values().forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(donutShade(i)))
                        Text(m.label, color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text(if (total == 0) "—" else "${counts[i] * 100 / total}%", color = C.Text2, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ChapterBooks(d, id)
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 14.dp) {
        Text("Last 3 sessions", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (logs.isEmpty()) Text("No sessions yet.", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        logs.takeLast(3).reversed().forEach { l ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.day(l).format(DateTimeFormatter.ofPattern("d MMM yyyy")), color = C.White, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Text("${l.attempted + l.skipped} questions", color = C.Text2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                val a = if (l.attempted == 0) 0 else l.correct * 100 / l.attempted
                Text("$a%", color = C.White, fontSize = 12.sp); SlimBar(a / 100f, Modifier.padding(start = 8.dp).width(40.dp))
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Log questions", modifier = Modifier.weight(1f)) { push(SR.Log(ch.subject, ch.id)) }
        GhostButton("Add to revision", modifier = Modifier.weight(1f)) {
            d.db.insertMistake(Mistake(0, ch.subject, ch.id, "Revision · ${ch.name}", MistakeType.CONCEPT, "Added from chapter", AppClock.today().toString(), 0)); bump()
        }
    }
}

// ---------------------------------------------------------------------------------------
// B4A / B4B Mock
// ---------------------------------------------------------------------------------------
@Composable
private fun MockLogScreen(d: SData, back: () -> Unit, saved: (Long) -> Unit) {
    var name by remember { mutableStateOf("Mock Test ${d.mocks.size + 1}") }
    val c = remember { mutableStateListOf(0, 0, 0) }
    val w = remember { mutableStateListOf(0, 0, 0) }
    val u = remember { mutableStateListOf(0, 0, 0) }
    val score = (0..2).sumOf { StudyLogic.marks(c[it], w[it]) }
    StudyTopBar("Mock Test Log", "Enter your marks for each subject.", back)
    SectionCard(pad = 12.dp, radius = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.plan_ic_notes, 24.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                androidx.compose.foundation.text.BasicTextField(name, { name = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(C.White))
                Text(AppClock.today().format(DateTimeFormatter.ofPattern("d MMM yyyy")), color = C.Text2, fontSize = 12.sp)
            }
            Text("Score $score / 720", color = C.White, fontSize = 13.sp)
        }
    }
    Subject.values().forEachIndexed { i, s ->
        Spacer(Modifier.height(12.dp))
        SectionCard(pad = 12.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(subjectIcon(s), 22.dp); Text(s.label, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text("Out of ${s.maxMarks}", color = C.Text2, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepperTile("Correct", c[i], 0, Modifier.weight(1f), big = false) { c[i] = it }
                StepperTile("Wrong", w[i], 1, Modifier.weight(1f), big = false) { w[i] = it }
                StepperTile("Unattempted", u[i], 2, Modifier.weight(1f), big = false) { u[i] = it }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Save Test", enabled = c.sum() + w.sum() + u.sum() > 0) {
        val id = d.db.insertMock(Mock(0, AppClock.today().toString(), name.ifBlank { "Mock Test" }, c.toList(), w.toList(), u.toList(), d.db.targetScore))
        saved(id)
    }
}

@Composable
private fun MockResult(d: SData, id: Long, back: () -> Unit, push: (SR) -> Unit) {
    val i = d.mocks.indexOfFirst { it.id == id }
    val m = d.mocks.getOrNull(i) ?: run { back(); return }
    val prev = d.mocks.getOrNull(i + 1)
    val weakest = Subject.values().minByOrNull { m.marks(it.ordinal).toFloat() / it.maxMarks }!!
    StudyTopBar("Mock Result", null, back)
    SectionCard(pad = 12.dp, radius = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.plan_ic_notes, 24.dp)
            Column(Modifier.padding(start = 12.dp)) { Text(m.name, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold); Text(m.date, color = C.Text2, fontSize = 12.sp) }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${m.score}", style = glow(56f, 600)); Text(" / 720", color = C.Text2, fontSize = 30.sp, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
        if (prev != null) Column(horizontalAlignment = Alignment.CenterHorizontally) { DeltaChip(m.score - prev.score); Text("vs last test", color = C.Text3, fontSize = 10.sp) }
    }
    SlimBar((m.score / d.db.targetScore.toFloat()).coerceIn(0f, 1f), Modifier.fillMaxWidth().padding(top = 8.dp), 6.dp)
    Text("Target ${d.db.targetScore}", color = C.Text2, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.End)
    Subject.values().forEach { s ->
        Spacer(Modifier.height(10.dp))
        SectionCard(pad = 12.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(subjectIcon(s), 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Row { Text(s.label, color = C.White, fontSize = 14.sp, modifier = Modifier.weight(1f)); Text("${m.marks(s.ordinal)} / ${s.maxMarks}", color = C.White, fontSize = 13.sp) }
                    SlimBar((m.marks(s.ordinal).toFloat() / s.maxMarks).coerceIn(0f, 1f), Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                if (prev != null) DeltaChip(m.marks(s.ordinal) - prev.marks(s.ordinal))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text("Where marks were lost", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            StatCell("-${m.negative}", "Negative marking", Modifier.weight(1f))
            StatCell("${m.skippedTotal}", "Skipped", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(10.dp))
    SectionCard(pad = 14.dp, radius = 20.dp, onClick = { push(SR.Sub(weakest)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(R.drawable.plan_ic_target, 22.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text("Next focus", color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text("Improve ${weakest.label} (lowest score)", color = C.Text2, fontSize = 12.sp) }
            Ico(R.drawable.p1_chevron_right, 18.dp, C.Text2)
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PrimaryButton("Review mistakes", modifier = Modifier.weight(1f)) { push(SR.Notebook()) }
        GhostButton("Take another test", modifier = Modifier.weight(1f)) { push(SR.MockLog) }
    }
    Spacer(Modifier.height(10.dp))
    GhostButton("View score trend", modifier = Modifier.fillMaxWidth()) { push(SR.MockTrend) }
}

// ---------------------------------------------------------------------------------------
// B4C Mock trend: score over time, per subject, against target
// ---------------------------------------------------------------------------------------
@Composable
private fun MockTrendScreen(d: SData, back: () -> Unit, push: (SR) -> Unit) {
    StudyTopBar("Mock Trend", "Score out of 720, oldest to latest", back)
    if (d.mocks.isEmpty()) {
        SectionCard(pad = 14.dp, radius = 20.dp) {
            Text("No mock tests yet", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Log a test to start the graph.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Log a mock test") { push(SR.MockLog) }
        return
    }
    val seq = d.mocks.asReversed()          // oldest first, for the chart
    val target = d.db.targetScore
    val latest = d.mocks.first()
    val prev = d.mocks.getOrNull(1)
    val best = d.mocks.maxOf { it.score }
    val last3 = d.mocks.take(3)
    val avg3 = last3.sumOf { it.score } / last3.size
    val gap = target - latest.score

    SectionCard(pad = 14.dp, radius = 20.dp) {
        Row(Modifier.fillMaxWidth()) {
            StatCell("${latest.score}", "Latest", Modifier.weight(1f), sub = "of 720")
            StatCell("$best", "Best", Modifier.weight(1f), sub = "all tests")
            StatCell("$avg3", "Average of last ${last3.size}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            StatCell(if (gap > 0) "$gap" else "Reached", "To target", Modifier.weight(1f), sub = "target $target")
            StatCell("${d.mocks.size}", "Tests taken", Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(12.dp))
    var view by remember { mutableIntStateOf(0) }   // 0 = total, 1..3 = one subject
    SegFull(listOf("Total") + Subject.values().map { it.label }, view) { view = it }

    val subj = if (view == 0) null else Subject.values()[view - 1]
    val pts = seq.map { m ->
        val v = if (subj == null) m.score / 720f else m.marks(subj.ordinal) / subj.maxMarks.toFloat()
        v.coerceIn(0f, 1f)
    }
    val series: List<List<Float?>> = if (subj == null) listOf(pts, seq.map { (target / 720f).coerceIn(0f, 1f) }) else listOf(pts)
    SectionCard(pad = 14.dp, radius = 20.dp) {
        if (seq.size < 2) {
            Text("Take a second test to see a trend.", color = C.Text2, fontSize = 13.sp)
        } else {
            LineChart(series, Modifier.fillMaxWidth().height(180.dp), dashed = true)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(seq.first().date, color = C.Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(seq.last().date, color = C.Text2, fontSize = 11.sp)
            }
            Text(
                if (subj == null) "Solid line: your score. Dashed line: target $target." else "Solid line: ${subj.label} marks as a share of ${subj.maxMarks}.",
                color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    prev?.let { p ->
        val changes = Subject.values().map { s -> s to (latest.marks(s.ordinal) - p.marks(s.ordinal)) }
        val up = changes.maxByOrNull { it.second }!!
        val down = changes.minByOrNull { it.second }!!
        fun signed(x: Int) = if (x >= 0) "+$x" else "$x"
        Spacer(Modifier.height(12.dp))
        SectionCard(pad = 14.dp, radius = 20.dp) {
            Text("Since the previous test", color = C.Text2, fontSize = 12.sp)
            Text("Total ${signed(latest.score - p.score)}", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text("Best change: ${up.first.label} ${signed(up.second)} · Worst: ${down.first.label} ${signed(down.second)}", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }

    Spacer(Modifier.height(14.dp))
    Text("Every test", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    d.mocks.forEachIndexed { i, m ->
        val p = d.mocks.getOrNull(i + 1)
        Spacer(Modifier.height(8.dp))
        SectionCard(pad = 12.dp, radius = 18.dp, onClick = { push(SR.MockRes(m.id)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(m.date, color = C.Text2, fontSize = 12.sp)
                }
                Text("${m.score}", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (p != null) DeltaChip(m.score - p.score)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Log a mock test") { push(SR.MockLog) }
}

// ---------------------------------------------------------------------------------------
// B5 Notebook
// ---------------------------------------------------------------------------------------
@Composable
private fun Notebook(d: SData, initial: Subject?, back: () -> Unit, bump: () -> Unit) {
    var f by remember { mutableIntStateOf(if (initial == null) 0 else initial.ordinal + 1) }
    val today = AppClock.today()
    val rows = d.mistakes.filter { f == 0 || it.subject.ordinal == f - 1 }
    val due = d.mistakes.count { runCatching { LocalDate.parse(it.due) <= today }.getOrDefault(true) }
    StudyTopBar("Mistake Notebook", "Review, learn and don't repeat.", back) { Pill("Due today: $due") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All") .plus(Subject.values().map { it.label }).forEachIndexed { i, o -> Pill(o, filled = f == i, modifier = Modifier.weight(1f), onClick = { f = i }) }
    }
    Spacer(Modifier.height(12.dp))
    if (rows.isEmpty()) Text("No mistakes logged yet. Tag mistake types when you save a Quick Log.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
    rows.forEach { m ->
        val left = ChronoUnit.DAYS.between(today, runCatching { LocalDate.parse(m.due) }.getOrDefault(today))
        Spacer(Modifier.height(8.dp))
        SectionCard(pad = 12.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(R.drawable.plan_ic_book, 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(m.ref, color = C.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("${d.chapter(m.chapterId)?.name ?: ""} · ${m.subject.label}", color = C.Text2, fontSize = 11.sp, maxLines = 1)
                }
                Pill(m.type.label); Spacer(Modifier.width(6.dp)); Pill(relDue(left))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(26.dp).clip(CircleShape).border(1.5.dp, C.White, CircleShape).clickable { d.db.markRevised(m); bump() }, contentAlignment = Alignment.Center) {
                    if (m.doneCount > 0 && left > 0) Ico(R.drawable.p1_checkmark, 14.dp)
                }
            }
            if (m.note.isNotBlank()) Text(m.note, color = C.Text2, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------
// B6 Stats
// ---------------------------------------------------------------------------------------
// ---------------------------------------------------------------------------------------
// Study stats (Study tab, Stats segment). Every chart is a line, read from the same logs.
// ---------------------------------------------------------------------------------------
@Composable
private fun StudyStatsBody(d: SData) {
    var p by remember { mutableIntStateOf(0) }
    val today = AppClock.today()
    val n = when (p) { 0 -> 7; 1 -> 30; else -> 84 }
    val step = if (p == 2) 7 else 1
    val buckets = remember(p) { (n / step - 1 downTo 0).map { k -> today.minusDays((k * step).toLong()) } }
    fun inB(day: LocalDate, b: LocalDate) = !day.isAfter(b) && day.isAfter(b.minusDays(step.toLong()))
    val rangeStart = today.minusDays((n - 1).toLong())
    val inRange = remember(d, p) { d.logs.filter { val x = d.day(it); !x.isBefore(rangeStart) && !x.isAfter(today) } }
    val rangeT = inRange.tally()
    val rangeQ = rangeT.attempted + rangeT.skipped
    fun qCount(logs: List<QLog>): Float = logs.sumOf { it.attempted + it.skipped }.toFloat()
    fun scale(v: List<Float>): List<Float?> { val m = (v.maxOrNull() ?: 0f).coerceAtLeast(1f); return v.map { it / m } }

    val totals = remember(d, p) { buckets.map { b -> qCount(d.logs.filter { inB(d.day(it), b) }) } }
    val perSub = remember(d, p) { Subject.values().map { s -> buckets.map { b -> qCount(d.logs.filter { it.subject == s && inB(d.day(it), b) }) } } }
    val accLines: List<List<Float?>> = remember(d, p) { Subject.values().map { s ->
        buckets.map { b -> val t = d.logs.filter { it.subject == s && inB(d.day(it), b) }.tally(); if (t.attempted == 0) null else t.accPct / 100f }
    } }

    Text("Study stats", color = C.White, style = com.pranav.focus.ui.HeadGlow, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
    SegPill(listOf("Week", "Month", "3 months"), p) { p = it }
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 14.dp) {
        Row(Modifier.fillMaxWidth()) {
            StatCell("$rangeQ", "Questions", Modifier.weight(1f))
            StatCell(if (rangeT.attempted == 0) "—" else "${rangeT.accPct}%", "Accuracy", Modifier.weight(1f))
            StatCell((if (rangeT.marks >= 0) "+" else "") + rangeT.marks, "Net marks", Modifier.weight(1f))
        }
    }

    // 1. Total questions attempted per day
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text("Total questions per day", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(if (p == 2) "Weekly totals" else "Daily totals", color = C.Text2, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LineChart(listOf(scale(totals)), Modifier.fillMaxWidth().height(140.dp))
        RangeLabels(buckets)
    }

    // 2. Questions per subject, one card and line graph each
    Spacer(Modifier.height(12.dp))
    Subject.values().forEachIndexed { i, s ->
        val q = perSub[i]
        SectionCard(pad = 14.dp, radius = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(subjectIcon(s), size = 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.label, color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${q.sum().toInt()} questions in this range", color = C.Text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            LineChart(listOf(scale(q)), Modifier.fillMaxWidth().height(90.dp))
            RangeLabels(buckets)
        }
        Spacer(Modifier.height(10.dp))
    }

    // 3. Accuracy per subject
    SectionCard {
        Text("Accuracy per subject", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LineChart(accLines, Modifier.fillMaxWidth().height(120.dp), dashed = true)
        RangeLabels(buckets)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Subject.values().forEachIndexed { i, s ->
                val t = inRange.filter { it.subject == s }.tally()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    LineKey(i)
                    Text(s.label, color = C.Text2, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    Text(if (t.attempted == 0) "—" else "${t.accPct}%", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // 4. Mock test marks trend
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mock marks trend", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (d.mocks.isNotEmpty()) Text("${d.mocks.first().score} / 720", color = C.White, fontSize = 15.sp)
        }
        if (d.mocks.size < 2) {
            Text(
                if (d.mocks.isEmpty()) "No mock tests yet. Log one to start the graph." else "Take a second test to see a trend.",
                color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            val seq = d.mocks.asReversed()
            val target = d.db.targetScore
            Spacer(Modifier.height(10.dp))
            LineChart(
                listOf(seq.map { (it.score / 720f).coerceIn(0f, 1f) }, seq.map { (target / 720f).coerceIn(0f, 1f) }),
                Modifier.fillMaxWidth().height(140.dp), dashed = true,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(seq.first().date, color = C.Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(seq.last().date, color = C.Text2, fontSize = 11.sp)
            }
            Text("Solid line: your score. Dashed line: target $target.", color = C.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }

    // Subject comparison (unchanged from the old Study stats)
    Spacer(Modifier.height(12.dp))
    SectionCard(pad = 12.dp) {
        Text("Subject comparison", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            listOf("Subject" to 1.3f, "Acc this" to 1f, "Acc last" to 1f, "Δ" to 0.8f, "Marks" to 1f, "Δ" to 0.8f).forEach { (t, wgt) -> Text(t, color = C.Text3, fontSize = 10.sp, modifier = Modifier.weight(wgt)) }
        }
        Subject.values().forEach { s ->
            val cur = d.week(0).filter { it.subject == s }.tally(); val pre = d.week(1).filter { it.subject == s }.tally()
            val da = StudyLogic.deltaPct(cur, pre)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1.3f))
                Text(if (cur.attempted == 0) "—" else "${cur.accPct}%", color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (pre.attempted == 0) "—" else "${pre.accPct}%", color = C.Text2, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (da == null) "—" else (if (da >= 0) "↑" else "↓") + Math.abs(da) + "%", color = C.White, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                Text("${cur.marks}", color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                val dm = cur.marks - pre.marks
                Text((if (dm >= 0) "↑" else "↓") + Math.abs(dm), color = C.White, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
            }
        }
    }

    // Study calendar (unchanged from the old Study stats)
    Spacer(Modifier.height(12.dp))
    SectionCard {
        Text("Study calendar", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        val weeks = 15
        val perDay = d.logs.groupBy { d.day(it) }.mapValues { e -> e.value.sumOf { it.attempted + it.skipped } }
        val maxD = (perDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
        val start = today.minusDays((weeks * 7 - 1).toLong())
        Canvas(Modifier.fillMaxWidth().height(86.dp)) {
            val cell = size.width / weeks
            val g = (cell * 0.12f)
            for (wk in 0 until weeks) for (dy in 0 until 7) {
                val day = start.plusDays((wk * 7 + dy).toLong())
                if (day.isAfter(today)) continue
                val v = (perDay[day] ?: 0) / maxD.toFloat()
                drawRoundRect(Color.White.copy(alpha = if (v == 0f) 0.06f else 0.2f + 0.8f * v), Offset(wk * cell + g, dy * (size.height / 7) + g / 2), Size(cell - 2 * g, size.height / 7 - g), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
        }
        val all = d.logs.tally()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            StatCell("${all.attempted + all.skipped}", "Total questions", Modifier.weight(1f))
            StatCell(if (all.attempted == 0) "—" else "${all.accPct}%", "Avg accuracy", Modifier.weight(1f))
            StatCell((if (all.marks >= 0) "+" else "") + all.marks, "Net marks", Modifier.weight(1f))
        }
    }
}

/** Date labels under a chart: first and last bucket. */
@Composable
private fun RangeLabels(buckets: List<LocalDate>) {
    val f = DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(buckets.first().format(f), color = C.Text2, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(buckets.last().format(f), color = C.Text2, fontSize = 11.sp)
    }
}

/** Short line sample matching LineChart's style for series i: solid, dashed, dotted. */
@Composable
private fun LineKey(i: Int) {
    Canvas(Modifier.width(22.dp).height(6.dp)) {
        val eff = when (i) { 1 -> PathEffect.dashPathEffect(floatArrayOf(8f, 6f)); 2 -> PathEffect.dashPathEffect(floatArrayOf(2f, 5f)); else -> null }
        drawLine(Color.White, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = eff)
    }
}

// ---------------------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------------------
@Composable
fun LineChart(series: List<List<Float?>>, modifier: Modifier, dashed: Boolean = false) {
    val reveal = rememberSweep(1f, 600)
    Canvas(modifier) {
        for (i in 1..3) drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f)
        series.forEachIndexed { si, pts ->
            if (pts.size < 2) return@forEachIndexed
            val path = Path(); var started = false; var lastPt: Offset? = null
            pts.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val p = Offset(size.width * i / (pts.size - 1), size.height * (1f - v))
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
                lastPt = p
            }
            val eff = if (dashed) when (si) { 1 -> PathEffect.dashPathEffect(floatArrayOf(14f, 10f)); 2 -> PathEffect.dashPathEffect(floatArrayOf(3f, 8f)); else -> null } else null
            drawContext.canvas.save()
            drawContext.canvas.clipRect(0f, 0f, size.width * reveal, size.height)
            drawPath(path, Color.White.copy(alpha = if (dashed) 1f else 0.18f), style = Stroke((if (dashed) 2.dp else 6.dp).toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = eff))
            if (!dashed) drawPath(path, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawContext.canvas.restore()
            if (!dashed) lastPt?.let { drawCircle(Color.White, 4.dp.toPx(), it) }
        }
    }
}

fun donutShade(i: Int) = Color.White.copy(alpha = listOf(1f, 0.75f, 0.55f, 0.38f, 0.22f)[i % 5])

@Composable
fun Donut(values: List<Int>, modifier: Modifier, center: @Composable () -> Unit) {
    val total = values.sum()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 14.dp.toPx()
            drawArc(C.Track, 0f, 360f, false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s))
            var a = -90f
            if (total > 0) values.forEachIndexed { i, v ->
                val sw = 360f * v / total
                if (v > 0) drawArc(donutShade(i), a, (sw - 2f).coerceAtLeast(1f), false, Offset(s / 2, s / 2), Size(size.width - s, size.height - s), style = Stroke(s))
                a += sw
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { center() }
    }
}
