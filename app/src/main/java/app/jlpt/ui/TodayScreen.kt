package app.jlpt.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.Kind
import app.jlpt.data.Speech
import java.time.LocalTime

@Composable
fun TodayScreen(nav: (Screen) -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val days by remember { a.db.dao().daysFlow() }.collectAsState(initial = emptyList())
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val minutesGoal by a.prefs.dailyMinutes.state.collectAsState()
    val examLevel by a.prefs.examLevel.state.collectAsState()
    val examDate by a.prefs.examDate.state.collectAsState()
    val speech by a.speech.status.collectAsState()
    var streak by remember { mutableIntStateOf(0) }

    val today = days.firstOrNull { it.day == a.study.today() }
    LaunchedEffect(days) { streak = a.study.streak() }

    // Worked out again whenever progress or settings change.
    val plan = remember(states, examLevel, examDate, minutesGoal) { a.study.plan() }
    val due = remember(states) { a.study.dueIds().size }
    val newLeft = (plan.newPerDay - (today?.newItems ?: 0)).coerceAtLeast(0)
    val minutes = (today?.seconds ?: 0) / 60
    val doneIds = remember(done) { done.map { it.id }.toSet() }
    val reading = remember(doneIds, examLevel) {
        a.content.readings.filter { it.level >= examLevel && it.id !in doneIds }.maxByOrNull { it.level }
    }
    val listening = remember(doneIds, examLevel) {
        a.content.listenings.filter { it.level >= examLevel && it.id !in doneIds }.maxByOrNull { it.level }
    }
    val story = remember(doneIds, examLevel) {
        a.content.stories.firstOrNull { it.level == examLevel && it.id !in doneIds }
            ?: a.content.stories.firstOrNull { it.level >= examLevel && it.id !in doneIds }
    }
    val kataLeft = remember(states) { a.study.count(a.study.ids(Kind.KATA)).let { it.total - it.started } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val h = LocalTime.now().hour
                Text(
                    when { h < 11 -> "おはよう"; h < 18 -> "こんにちは"; else -> "こんばんは" },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    when { h < 11 -> "Good morning"; h < 18 -> "Good afternoon"; else -> "Good evening" } +
                        if (streak > 1) " · $streak day streak 🔥" else "",
                    color = LocalExtra.current.muted,
                )
            }
            IconButton(onClick = { nav(Screen.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
        }
        Gap(16.dp)

        // Today's goal and the big button.
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                    Ring(minutes / minutesGoal.coerceAtLeast(1).toFloat(), Modifier.size(92.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$minutes", style = MaterialTheme.typography.headlineSmall)
                        Text("of $minutesGoal min", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                    }
                }
                HGap(16.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            minutes >= minutesGoal -> "Goal done today!"
                            due == 0 && newLeft == 0 -> "All caught up"
                            else -> "Ready when you are"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Gap(4.dp)
                    Text("$due reviews due", color = LocalExtra.current.muted)
                    Text("$newLeft new left today", color = LocalExtra.current.muted)
                }
            }
            Gap(12.dp)
            val roundMin by a.prefs.roundMinutes.state.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Round", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
                HGap(10.dp)
                ChoiceRow(listOf(15 to "15 min", 30 to "30 min", 60 to "60 min"), roundMin) { a.prefs.roundMinutes.value = it }
            }
            Gap(12.dp)
            Button(
                onClick = { nav(Screen.Study(0)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text(if (due == 0 && newLeft == 0) "Learn a few extra" else "Study now", fontSize = 18.sp)
            }
        }
        Gap()

        // A check-up once a week, inside the app (never a notification).
        val tests by remember { a.db.dao().testsFlow() }.collectAsState(initial = emptyList())
        val lastWeekly = tests.firstOrNull { it.sections.startsWith("weekly") && it.level == examLevel }
        val weeklyDue = plan.daysLeft > 0 && (lastWeekly == null || System.currentTimeMillis() - lastWeekly.time > 7L * 86_400_000L)
        if (weeklyDue) {
            Panel(onClick = { nav(Screen.MockTest(examLevel, weekly = true)) }, color = MaterialTheme.colorScheme.secondaryContainer) {
                Label("Weekly check", color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("Time for this week's mock", style = MaterialTheme.typography.titleMedium)
                Text("About 25 minutes, on what you've covered so far. Your weakest part gets more practice next week.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Gap()
        }

        // The exam.
        Panel(onClick = { nav(Screen.Plan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("${lvl(examLevel)} exam")
                    Text(
                        when {
                            plan.daysLeft > 1 -> "${plan.daysLeft} days to go"
                            plan.daysLeft == 1L -> "Tomorrow! がんばって"
                            plan.daysLeft == 0L -> "Today! You've got this"
                            else -> "Exam done. On to the next level?"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Text("${plan.newPerDay}/day", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Gap(10.dp)
            val started = plan.examTotal - plan.examLeft
            Meter(started / plan.examTotal.coerceAtLeast(1).toFloat())
            Gap(6.dp)
            Text(
                "$started of ${plan.examTotal} words, kanji, grammar and kana met" +
                    if (plan.tight) ". To stay on plan this needs about ${(plan.newPerDay / 0.6).toInt()} min a day. Tap for the plan." else ". Tap for the plan.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }

        if (speech == Speech.Status.NO_JAPANESE || speech == Speech.Status.NONE) {
            Gap()
            val ctx = LocalContext.current
            Panel(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("No Japanese voice on this phone yet", style = MaterialTheme.typography.titleMedium)
                Gap(4.dp)
                Text("Listening practice needs it. Install the Japanese voice for Speech Services by Google (free).")
                TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(Intent("android.speech.tts.engine.INSTALL_TTS_DATA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }) { Text("Install voice") }
            }
        }

        Gap(20.dp)
        Label("Also today")
        Gap(8.dp)
        if (reading != null) {
            TaskRow(Icons.AutoMirrored.Rounded.MenuBook, "Read: ${reading.title}", "${lvl(reading.level)} reading · ${plural(reading.questions.size, "question")}") {
                nav(Screen.ReadingOne(reading.id))
            }
            Gap(8.dp)
        }
        if (listening != null) {
            TaskRow(Icons.Rounded.Headphones, "Listen: ${listening.title}", "${lvl(listening.level)} listening") {
                nav(Screen.ListeningOne(listening.id))
            }
            Gap(8.dp)
        }
        if (story != null) {
            TaskRow(Icons.Rounded.AutoStories, "Story: ${story.titleEn}", "${lvl(story.level)} · ${story.genre}") {
                nav(Screen.StoryOne(story.id))
            }
            Gap(8.dp)
        }
        TaskRow(Icons.Rounded.Visibility, "Furigana practice", "Read sentences with the readings hidden") {
            nav(Screen.FuriganaPractice)
        }
        if (kataLeft > 0) {
            Gap(8.dp)
            Text(
                "Katakana comes first in your new items: $kataLeft to go.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
        Gap(24.dp)
    }
}

@Composable
fun TaskRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        }
    }
}

fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
