package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.jlpt.data.Kind
import java.time.LocalDate

@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val days by remember { a.db.dao().daysFlow() }.collectAsState(initial = emptyList())
    val tests by remember { a.db.dao().testsFlow() }.collectAsState(initial = emptyList())
    val goal by a.prefs.dailyMinutes.state.collectAsState()
    val byDay = days.associateBy { it.day }

    Column(Modifier.fillMaxSize()) {
        BackBar("Progress", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Label("Last 14 days (minutes)")
            Gap(8.dp)
            Panel {
                Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    for (i in 13 downTo 0) {
                        val d = LocalDate.now().minusDays(i.toLong())
                        val m = (byDay[d.toString()]?.seconds ?: 0) / 60
                        val f = (m / goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(maxOf(f, 0.02f)).clip(RoundedCornerShape(4.dp))
                                    .background(if (m >= goal) LocalExtra.current.good else MaterialTheme.colorScheme.primary.copy(alpha = if (m > 0) 0.7f else 0.15f)),
                            )
                        }
                    }
                }
                Gap(6.dp)
                val week = (0..6).sumOf { (byDay[LocalDate.now().minusDays(it.toLong()).toString()]?.seconds ?: 0) } / 60
                val answers = (0..6).sumOf { byDay[LocalDate.now().minusDays(it.toLong()).toString()]?.answers ?: 0 }
                Text("This week: $week min, $answers answers", style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
            }
            Gap(20.dp)
            Label("What you know")
            Gap(8.dp)
            Panel {
                for (k in Kind.entries) {
                    val c = remember(states) { a.study.count(a.study.ids(k)) }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label, Modifier.weight(0.38f))
                        DoubleMeter(c.started / c.total.coerceAtLeast(1).toFloat(), c.known / c.total.coerceAtLeast(1).toFloat(), Modifier.weight(0.42f))
                        Text("${c.known}/${c.total}", Modifier.weight(0.2f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Gap(20.dp)
            Label("By level (words, kanji, grammar)")
            Gap(8.dp)
            Panel {
                for (l in 5 downTo 1) {
                    val ids = remember(l) {
                        a.content.studyWords.filter { it.level == l }.map { it.id } +
                            a.content.kanji.filter { it.level == l }.map { it.id } +
                            a.content.grammar.filter { it.level == l }.map { it.id }
                    }
                    val c = remember(states, l) { a.study.count(ids) }
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lvl(l), Modifier.weight(0.15f), style = MaterialTheme.typography.titleMedium)
                        DoubleMeter(c.started / c.total.toFloat(), c.known / c.total.toFloat(), Modifier.weight(0.6f))
                        Text("${c.known * 100 / c.total}%", Modifier.weight(0.25f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (tests.isNotEmpty()) {
                Gap(20.dp)
                Label("Mock tests")
                Gap(8.dp)
                Panel {
                    for (t in tests.take(10)) {
                        val d = java.time.Instant.ofEpochMilli(t.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        Text("$d · ${lvl(t.level)} · ${t.score * 100 / t.total.coerceAtLeast(1)}%  (${t.sections.replace(";", ", ")})",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
            Gap(32.dp)
        }
    }
}
