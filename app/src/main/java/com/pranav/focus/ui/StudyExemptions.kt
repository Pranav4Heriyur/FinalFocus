package com.pranav.focus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pranav.focus.data.ExemptKind
import com.pranav.focus.data.Store
import com.pranav.focus.data.StudyChannel
import com.pranav.focus.data.StudyExempt
import com.pranav.focus.lock.AppState
import com.pranav.focus.lock.Enforcer

/** Four separate, labelled numbers, all read from the same ledger/usage source. */
@Composable
fun UsageTotalsCard(refresh: Int = 0) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val t = remember(AppState.dataTick, AppState.tick, refresh) {
        if (com.pranav.focus.ui.UsageHelper.hasAccess(ctx)) Enforcer.totals(ctx, store) else null
    }
    FocusCard(Modifier.fillMaxWidth()) {
        Caps("Today's allowance use")
        Spacer(Modifier.height(8.dp))
        if (t == null) Text("Usage access is off, so allowance use can't be measured. Balance: ${store.bankMinutes}m.", color = C.Text2, fontSize = 13.sp)
        else {
            TotalRow("Base entertainment usage", "${t.baseMin}m")
            TotalRow("Exempt study usage (verified)", "${t.studyMin}m")
            TotalRow("Bank-funded usage", "${t.bankMin}m")
            TotalRow("Time Bank balance", "${t.balance}m")
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.Text2, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** App Rules → YouTube → Study Exemptions. */
@Composable
fun StudyExemptionsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    var list by remember { mutableStateOf(store.studyChannels) }
    var detect by remember { mutableStateOf(store.studyDetect) }
    var nameMatch by remember { mutableStateOf(store.studyNameMatch) }
    var input by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun save(l: List<StudyChannel>) { list = l; store.studyChannels = l }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Study Exemptions", back, "Approved YouTube study time is free.")
        FocusCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Detect approved study", color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (AppState.a11yOn) "Uses the Focus accessibility service." else "Needs the Focus accessibility service (currently off), so nothing is exempted.",
                        color = C.Text3, fontSize = 12.sp)
                }
                FocusSwitch(detect) { detect = it; store.studyDetect = it }
            }
            Spacer(Modifier.height(8.dp))
            Text("While YouTube is open, Focus reads only that window and compares it on your device against the ids and names below. " +
                "No text is saved, only time totals. Time counts as study only when a channel or video ID matches. " +
                "Android does not always expose IDs; when identity can't be confirmed the time counts as normal YouTube use.",
                color = C.Text2, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Also accept name matches", color = C.White, fontSize = 15.sp)
                    Text("Less reliable: names can be copied. Off by default; shown as unverified.", color = C.Text3, fontSize = 12.sp)
                }
                FocusSwitch(nameMatch) { nameMatch = it; store.studyNameMatch = it }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Approved channels and videos", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("None yet.", color = C.Text3, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        list.forEachIndexed { i, c ->
            FocusCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name.ifBlank { c.handle.ifBlank { c.id } }, color = C.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (c.kind == ExemptKind.VIDEO) "Video · " else "Channel · ") +
                                (if (c.id.isNotEmpty()) c.id else "@${c.handle} · no ID yet, can't verify"),
                            color = C.Text3, fontSize = 12.sp,
                        )
                    }
                    FocusSwitch(c.enabled) { on -> save(list.mapIndexed { j, x -> if (j == i) x.copy(enabled = on) else x }) }
                }
                Text("Remove", color = C.Text2, fontSize = 13.sp, modifier = Modifier.clickable { save(list.filterIndexed { j, _ -> j != i }) }.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Add", color = C.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(input, { input = it; err = null }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Channel ID, channel URL or video URL") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Display name (optional label)") })
        err?.let { Text(it, color = C.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Add", enabled = input.isNotBlank()) {
            val p = StudyExempt.parse(input)
            when {
                p == null -> err = "Not a recognised channel ID or YouTube URL."
                p.id.isNotEmpty() && list.any { it.id == p.id } -> err = "Already added."
                else -> {
                    save(list + StudyChannel(p.id, label.trim(), p.kind, true, p.handle))
                    if (p.id.isEmpty()) err = "Saved, but @handles have no stable ID, so this entry can't be verified until you add its channel ID."
                    input = ""; label = ""
                }
            }
        }
        Spacer(Modifier.height(100.dp))
    }
}
