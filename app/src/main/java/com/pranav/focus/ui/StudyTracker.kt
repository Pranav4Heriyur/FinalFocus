package com.pranav.focus.ui

import com.pranav.focus.data.AppClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.R
import com.pranav.focus.data.Chapter
import com.pranav.focus.data.Subject
import com.pranav.focus.data.groupsFor
import com.pranav.focus.data.tally
import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun agoText(day: LocalDate?): String {
    if (day == null) return "Never practised"
    return when (val n = ChronoUnit.DAYS.between(day, AppClock.today())) {
        0L -> "Today"; 1L -> "Yesterday"; in 2..30 -> "${n}d ago"
        else -> day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }
}

@Composable
fun NameDialog(title: String, initial: String, groups: List<String>, initialGroup: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var g by remember { mutableStateOf(initialGroup.ifEmpty { groups.firstOrNull() ?: "" }) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Sheet,
        title = { Text(title, color = C.White) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Raised).border(1.dp, C.Hairline, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    if (name.isEmpty()) Text("Name", color = C.Text3, fontSize = 15.sp)
                    BasicTextField(name, { name = it }, singleLine = true, textStyle = TextStyle(color = C.White, fontSize = 15.sp), cursorBrush = SolidColor(C.White))
                }
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { groups.forEach { x -> Pill(x, filled = g == x, onClick = { g = x }) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), g) }) { Text("Save", color = C.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Text2) } },
    )
}

@Composable
fun Syllabus(d: SData, back: () -> Unit, push: (SR) -> Unit, bump: () -> Unit) {
    var si by remember { mutableIntStateOf(0) }
    var gi by remember { mutableIntStateOf(0) } // 0 = All
    var manage by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Chapter?>(null) }
    var removing by remember { mutableStateOf<Chapter?>(null) }
    var addingBook by remember { mutableStateOf(false) }
    val s = Subject.values()[si]
    val groups = groupsFor(s)
    val group = if (gi == 0 || groups.isEmpty()) null else groups[gi - 1]

    StudyTopBar("Syllabus tracker", "Every chapter, every book.", back) { Pill(if (manage) "Done" else "Manage", filled = manage) { manage = !manage } }

    // pie of questions across Physics / Chemistry / Botany / Zoology + lifetime numbers
    val parts = listOf(
        "Physics" to d.logs.filter { it.subject == Subject.PHYSICS }.tally().attempted,
        "Chemistry" to d.logs.filter { it.subject == Subject.CHEMISTRY }.tally().attempted,
        "Botany" to d.logs.filter { it.subject == Subject.BIOLOGY && d.chapter(it.chapterId)?.group != "Zoology" }.tally().attempted,
        "Zoology" to d.logs.filter { it.subject == Subject.BIOLOGY && d.chapter(it.chapterId)?.group == "Zoology" }.tally().attempted,
    )
    val all = d.logs.tally()
    val touched = d.active.count { c -> d.logs.any { it.chapterId == c.id } }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(parts.map { it.second }, Modifier.size(112.dp)) { Text("${all.attempted}", color = C.White, fontSize = 22.sp, fontWeight = FontWeight.Medium); Text("attempted", color = C.Text2, fontSize = 10.sp) }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                parts.forEachIndexed { i, (n, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(donutShade(i)))
                        Text(n, color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("$v", color = C.Text2, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            StatCell(if (all.attempted == 0) "—" else "${all.accPct}%", "Accuracy to date", Modifier.weight(1f))
            StatCell("$touched / ${d.active.size}", "Chapters practised", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    SegPill(Subject.values().map { it.label }, si) { si = it; gi = 0 }
    if (groups.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        SegPill(listOf("All") + groups, gi) { gi = it }
    }
    Spacer(Modifier.height(12.dp))
    val rows = d.active.filter { it.subject == s && (group == null || it.group == group) }
    SectionCard(pad = 4.dp) {
        if (rows.isEmpty()) Text("No chapters here yet.", color = C.Text2, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
        rows.forEach { c ->
            val logs = d.logs.filter { it.chapterId == c.id }
            val t = logs.tally()
            val last = logs.maxOfOrNull { d.day(it) }
            Row(Modifier.fillMaxWidth().clickable { if (!manage) push(SR.Chap(c.id)) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, color = C.White, fontSize = 14.sp, maxLines = 1)
                    Text("${agoText(last)} · ${t.attempted} q", color = C.Text2, fontSize = 11.sp)
                }
                if (!manage) {
                    SlimBar(if (t.attempted == 0) 0f else t.accPct / 100f, Modifier.padding(horizontal = 10.dp).width(54.dp))
                    Text(if (t.attempted == 0) "—" else "${t.accPct}%", color = C.White, fontSize = 13.sp, modifier = Modifier.width(38.dp))
                    StatusDot(if (t.attempted == 0) 0 else t.accPct)
                } else {
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { editing = c }, contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_pencil, 18.dp, C.Text2) }
                    Box(Modifier.size(34.dp).clip(CircleShape).clickable { removing = c }, contentAlignment = Alignment.Center) { Ico(R.drawable.plan_ic_close, 18.dp, C.Text2) }
                }
            }
        }
    }
    if (manage) {
        Spacer(Modifier.height(10.dp))
        GhostButton("Add chapter", icon = R.drawable.p1_plus) { adding = true }
        val hidden = d.chapters.filter { !it.active && it.subject == s }
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionCard(pad = 8.dp) {
                Text("Hidden (history kept)", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                hidden.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, color = C.Text2, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Pill("Restore") { d.db.setChapterActive(c.id, true); bump() }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Books and sources", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (manage) Pill("+ Add") { addingBook = true }
        }
        d.books.filter { manage || it.active }.forEach { b ->
            val t = d.logs.filter { it.bookId == b.id }.tally()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, color = if (b.active) C.White else C.Text3, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${t.attempted} q", color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                Text(if (t.attempted == 0) "—" else "${t.accPct}%", color = C.White, fontSize = 13.sp, modifier = Modifier.width(38.dp))
                if (manage) Pill(if (b.active) "Hide" else "Show") { d.db.setBookActive(b.id, !b.active); bump() }
            }
        }
    }

    if (adding) NameDialog("Add chapter", "", groups, "", { adding = false }) { n, g -> d.db.addChapter(s, n, g); adding = false; bump() }
    editing?.let { c -> NameDialog("Edit chapter", c.name, groupsFor(c.subject), c.group, { editing = null }) { n, g -> d.db.updateChapter(c.id, n, g); editing = null; bump() } }
    if (addingBook) NameDialog("Add book or source", "", emptyList(), "", { addingBook = false }) { n, _ -> d.db.addBook(n); addingBook = false; bump() }
    removing?.let { c ->
        AlertDialog(
            onDismissRequest = { removing = null }, containerColor = C.Sheet,
            title = { Text("Hide “${c.name}”?", color = C.White) },
            text = { Text("It disappears from your lists. Everything you logged stays and counts in your totals. You can restore it any time.", color = C.Text2) },
            confirmButton = { TextButton(onClick = { d.db.setChapterActive(c.id, false); removing = null; bump() }) { Text("Hide", color = C.White) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel", color = C.Text2) } },
        )
    }
}

/** B3 add-on: practice split by book (pie + rows) and when the chapter was last done. */
@Composable
fun ChapterBooks(d: SData, chapterId: Int) {
    val logs = d.logs.filter { it.chapterId == chapterId }
    val byBook = d.books.map { b -> b to logs.filter { it.bookId == b.id }.tally() }.filter { it.second.attempted + it.second.skipped > 0 }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Practice by book", color = C.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("Last: ${agoText(logs.maxOfOrNull { d.day(it) })}", color = C.Text2, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (byBook.isEmpty()) Text("Nothing logged for this chapter yet.", color = C.Text2, fontSize = 12.sp)
        else Row(verticalAlignment = Alignment.CenterVertically) {
            Donut(byBook.map { it.second.attempted }, Modifier.size(100.dp)) { Text("${byBook.sumOf { it.second.attempted }}", color = C.White, fontSize = 20.sp, fontWeight = FontWeight.Medium); Text("questions", color = C.Text2, fontSize = 9.sp) }
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                byBook.forEachIndexed { i, (b, t) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(donutShade(i)))
                        Text(b.name, color = C.White, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                        Text("${t.attempted} · ${if (t.attempted == 0) "—" else "${t.accPct}%"}", color = C.Text2, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
