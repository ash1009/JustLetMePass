package app.jlpt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.jlpt.data.Kind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * The plan, worked out from the exam date with nothing to set: everything for the exam is met a
 * week early (that last week is reviews and mocks), with N3 and daily Japanese alongside.
 */
@Composable
fun PlanScreen(nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val days by remember { a.db.dao().daysFlow() }.collectAsState(initial = emptyList())
    val tests by remember { a.db.dao().testsFlow() }.collectAsState(initial = emptyList())
    val examLevel by a.prefs.examLevel.state.collectAsState()
    val goal by a.prefs.dailyMinutes.state.collectAsState()
    val plan = remember(states, examLevel) { a.study.plan() }
    var weak by remember { mutableStateOf<Pair<String, Int>?>(null) }
    LaunchedEffect(tests) { weak = a.study.weakSection() }

    val exam = a.prefs.exam()
    val doneBy = exam.minusDays(7)
    val today = LocalDate.now()
    val byDay = days.associateBy { it.day }
    val weekNew = (0..6).sumOf { byDay[today.minusDays(it.toLong()).toString()]?.newItems ?: 0 }
    val weekMin = (0..6).sumOf { byDay[today.minusDays(it.toLong()).toString()]?.seconds ?: 0 } / 60
    val weeksLeft = ceil(plan.studyDays / 7.0).toInt().coerceAtLeast(1)
    // Only the days since you started count, up to a week (today included).
    val firstDay = days.minByOrNull { it.day }?.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
    val daysIn = (java.time.temporal.ChronoUnit.DAYS.between(firstDay, today) + 1).coerceIn(1, 7).toInt()
    val weekTarget = plan.newPerDay * daysIn
    val met = plan.examTotal - plan.examLeft
    val todayLog = byDay[today.toString()]
    val newLeftToday = (plan.newPerDay - (todayLog?.newItems ?: 0)).coerceAtLeast(0)
    val due = remember(states) { a.study.dueIds().size }
    val known = remember(states, examLevel) {
        val ids = a.content.studyWords.filter { it.level >= examLevel }.map { it.id } +
            a.content.kanji.filter { it.level >= examLevel }.map { it.id } +
            a.content.grammar.filter { it.level >= examLevel }.map { it.id } + a.study.ids(Kind.KATA)
        a.study.count(ids)
    }
    val weekly = tests.filter { it.sections.startsWith("weekly") }
    val current = remember(states) { a.study.currentLevel() }

    Column(Modifier.fillMaxSize()) {
        BackBar("Your plan", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Panel {
                Label("${lvl(examLevel)} exam · ${exam.format(dayFmt)} · working on ${lvl(current)}")
                Text(
                    if (plan.daysLeft > 7) "Meet everything by ${doneBy.format(dayFmt)}" else "Last week: reviews and mocks only",
                    style = MaterialTheme.typography.titleLarge,
                )
                Gap(4.dp)
                Text(
                    "${plan.daysLeft} days left · ${plan.examLeft} exam items still to meet · about ${plan.examPerDay} a day " +
                        "(${plan.newPerDay} with N3 and daily phrases)",
                    style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                )
                Gap(10.dp)
                Meter(met / plan.examTotal.coerceAtLeast(1).toFloat())
                Gap(4.dp)
                Text("$met of ${plan.examTotal} met · ${known.known} known", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            Gap()
            Panel {
                Label("This week")
                val ahead = weekNew - weekTarget
                Text(
                    when {
                        plan.daysLeft <= 7 -> "Review week: no new items needed"
                        ahead >= 0 -> "On track ✓  ($weekNew of $weekTarget new)"
                        else -> "Behind by ${-ahead} ($weekNew of $weekTarget new)"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (ahead >= 0 || plan.daysLeft <= 7) LocalExtra.current.good else MaterialTheme.colorScheme.primary,
                )
                Text("$weekMin min studied in the last 7 days · $weeksLeft weeks of new items left", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            Gap()
            Panel {
                Label("Today")
                Text("$due reviews · $newLeftToday new · goal $goal min", style = MaterialTheme.typography.titleMedium)
                if (plan.tight) {
                    Text(
                        "To keep this pace you need about ${(plan.newPerDay / 0.6).toInt()} minutes a day. A longer round helps.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Gap(10.dp)
                Button(onClick = { nav(Screen.Study(0)) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Study now") }
            }
            Gap()
            Panel {
                Label("Weekly check")
                val last = weekly.firstOrNull()
                if (last == null) {
                    Text("No check yet", style = MaterialTheme.typography.titleMedium)
                } else {
                    val d = Instant.ofEpochMilli(last.time).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("Last: ${last.score * 100 / last.total.coerceAtLeast(1)}% on ${d.format(dayFmt)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        last.sections.removePrefix("weekly;").replace(";", " · ").replace("=", " "),
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                    )
                    if (weekly.size > 1) {
                        Text("Before: " + weekly.drop(1).take(4).joinToString("  ") { "${it.score * 100 / it.total.coerceAtLeast(1)}%" },
                            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                    }
                }
                weak?.let { (name, pct) ->
                    Gap(6.dp)
                    Text("Focus now: ${name.lowercase()} ($pct%). " + when (name) {
                        "Vocabulary" -> "More new words and kanji in your rounds."
                        "Grammar" -> "More grammar points in your rounds."
                        "Reading" -> "Read a story or passage each day."
                        else -> "Do a listening each day, and use Listen in the background."
                    }, style = MaterialTheme.typography.bodyMedium)
                }
                Gap(8.dp)
                Text("It tests what you've covered so far, so the score shows whether it's sticking.",
                    style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                Gap(8.dp)
                OutlinedButton(onClick = { nav(Screen.MockTest(current, weekly = true)) }, Modifier.fillMaxWidth()) { Text("Take the weekly check") }
            }
            Gap()
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { nav(Screen.Progress) }) { Text("All progress") }
            }
            Gap(32.dp)
        }
    }
}
