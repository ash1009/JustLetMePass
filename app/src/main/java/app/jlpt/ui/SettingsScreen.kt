package app.jlpt.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.jlpt.data.ClaudeClient
import app.jlpt.data.Reminder
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in options) Chip(label, v == selected, { onPick(v) })
    }
}

@Composable
private fun SettingTitle(title: String, sub: String? = null) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
    Gap(8.dp)
}

/** The few things that shape the plan, used on first run and in Settings. */
@Composable
fun PlanSettings() {
    val a = app()
    val ctx = LocalContext.current
    val level by a.prefs.examLevel.state.collectAsState()
    val date by a.prefs.examDate.state.collectAsState()
    val minutes by a.prefs.dailyMinutes.state.collectAsState()
    val remind by a.prefs.reminderOn.state.collectAsState()
    val hour by a.prefs.reminderHour.state.collectAsState()
    val minute by a.prefs.reminderMinute.state.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        SettingTitle("Exam level")
        ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { a.prefs.examLevel.value = it }
        Gap(20.dp)

        SettingTitle("Exam date", "The JLPT is twice a year, in July and December.")
        val d = remember(date) { a.prefs.exam() }
        Panel(onClick = {
            DatePickerDialog(ctx, { _, y, m, day ->
                a.prefs.examDate.value = LocalDate.of(y, m + 1, day).toString()
            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
            Text(d.format(dateFmt), style = MaterialTheme.typography.titleMedium)
        }
        Gap(20.dp)

        SettingTitle("Study time a day", "A goal, not a rule. Do more or less any day.")
        ChoiceRow(listOf(15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "1 hour", 90 to "1.5 hours", 120 to "2 hours"), minutes) {
            a.prefs.dailyMinutes.value = it
        }
        Gap(20.dp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Evening reminder", style = MaterialTheme.typography.titleMedium)
                Text("Only if you haven't met your goal yet", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            Switch(remind, { a.prefs.reminderOn.value = it; Reminder.schedule(ctx, a.prefs) })
        }
        if (remind) {
            Gap(8.dp)
            Panel(onClick = {
                TimePickerDialog(ctx, { _, h, m ->
                    a.prefs.reminderHour.value = h
                    a.prefs.reminderMinute.value = m
                    Reminder.schedule(ctx, a.prefs)
                }, hour, minute, true).show()
            }, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Text("At %02d:%02d".format(hour, minute), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val a = app()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var more by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Settings", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Label("Your plan")
            Gap(12.dp)
            PlanSettings()
            Gap(28.dp)
            HorizontalDivider(color = LocalExtra.current.line)
            Gap(20.dp)

            // ---- Claude ----
            Label("Claude (for Chat and explanations)")
            Gap(12.dp)
            val key by a.prefs.claudeKey.state.collectAsState()
            var draft by remember { mutableStateOf(key) }
            var status by remember { mutableStateOf<String?>(null) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.trim(); status = null },
                label = { Text("Claude API key") },
                placeholder = { Text("sk-ant-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    a.prefs.claudeKey.value = draft
                    a.prefs.claudeModels = ""
                    status = "Checking…"
                    scope.launch {
                        status = when (val r = a.claude.test()) {
                            is ClaudeClient.Reply.Text -> "Works! Chat is ready."
                            is ClaudeClient.Reply.Failed -> r.message
                        }
                    }
                }) { Text(if (draft == key && key.isNotBlank()) "Check key" else "Save key") }
                HGap()
                TextButton(onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys"))) }
                }) { Text("Get a key") }
            }
            status?.let { Text(it, color = LocalExtra.current.muted, style = MaterialTheme.typography.bodyMedium) }
            Text(
                "The key stays on this phone. Everything else works without it.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
            Gap(28.dp)
            HorizontalDivider(color = LocalExtra.current.line)

            // ---- More, tucked away ----
            Row(
                Modifier.fillMaxWidth().clickable { more = !more }.padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("More options", style = MaterialTheme.typography.titleMedium)
                    Text("Furigana, pace, audio, look. The defaults work well.", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
                Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(more) { MoreSettings(onReset = { confirmReset = true }) }
            Gap(24.dp)
            About()
            Gap(32.dp)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Start over?") },
            text = { Text("This clears all your progress, history and test results. Settings and your key stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        a.study.resetAll()
                        a.prefs.onboarded.value = false
                    }
                }) { Text("Clear everything") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MoreSettings(onReset: () -> Unit) {
    val a = app()
    val furigana by a.prefs.furigana.state.collectAsState()
    val newPerDay by a.prefs.newPerDay.state.collectAsState()
    val spacing by a.prefs.spacing.state.collectAsState()
    val rate by a.prefs.speechRate.state.collectAsState()
    val autoPlay by a.prefs.autoPlay.state.collectAsState()
    val daily by a.prefs.includeDaily.state.collectAsState()
    val theme by a.prefs.dark.state.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        SettingTitle("Furigana", "Readings over kanji. \"Hide once seen\" shows them only the first time you meet a word or kanji; tap any word to see it again.")
        ChoiceRow(listOf("auto" to "Hide once seen", "always" to "Always show", "never" to "Hide"), furigana) { a.prefs.furigana.value = it }
        Gap(20.dp)
        SettingTitle("New items a day", "Auto works it out from your exam date and daily time.")
        ChoiceRow(listOf(0 to "Auto", 10 to "10", 20 to "20", 30 to "30", 40 to "40", 50 to "50"), newPerDay) { a.prefs.newPerDay.value = it }
        Gap(20.dp)
        SettingTitle("Reviews", "How often things you know come back.")
        ChoiceRow(listOf("gentle" to "More often", "normal" to "Normal", "fast" to "Less often"), spacing) { a.prefs.spacing.value = it }
        Gap(20.dp)
        SettingTitle("Speech speed")
        ChoiceRow(listOf(70 to "Slow", 90 to "Normal", 110 to "Fast"), rate) { a.prefs.speechRate.value = it }
        Gap(12.dp)
        SwitchRow("Play audio by itself", "When you meet a new word or answer", autoPlay) { a.prefs.autoPlay.value = it }
        SwitchRow("Daily life phrases", "Everyday Japanese beyond the exam", daily) { a.prefs.includeDaily.value = it }
        Gap(12.dp)
        SettingTitle("Look")
        ChoiceRow(listOf("system" to "Like the phone", "light" to "Light", "dark" to "Dark"), theme) { a.prefs.dark.value = it }
        Gap(24.dp)
        OutlinedButton(onClick = onReset) { Text("Start over…") }
        Gap(8.dp)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        Switch(on, onChange)
    }
}

@Composable
private fun About() {
    Column {
        Label("About")
        Gap(6.dp)
        Text(
            "Word lists: Jonathan Waller's JLPT lists (tanos.co.uk, CC BY), via the Open Anki JLPT decks. " +
                "Kanji: KANJIDIC (EDRDG, CC BY-SA 4.0) via kanji-data. " +
                "Stroke order: KanjiVG by Ulrich Apel (CC BY-SA 3.0). " +
                "Example sentences, grammar, readings and dialogues were written for this app.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        )
    }
}
