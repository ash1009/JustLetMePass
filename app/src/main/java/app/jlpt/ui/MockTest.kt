package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.JlptApp
import app.jlpt.data.Furigana
import app.jlpt.data.Listening
import app.jlpt.data.Quiz
import app.jlpt.data.TestResult
import kotlinx.coroutines.delay

/** One question of a mock test. A listening question carries its dialogue. */
private data class MockQ(
    val section: String,
    val quiz: Quiz,
    val passage: String? = null,
    val listening: Listening? = null,
    /** Hide furigana on the question (kanji reading questions). */
    val noFurigana: Boolean = false,
)

private val sections = listOf("Vocabulary", "Grammar", "Reading", "Listening")

/**
 * [covered] true: the weekly check, with vocabulary and grammar only from what you have met so far
 * (N5 up to [level]), so the score shows how well the plan's work so far has stuck.
 */
private fun buildTest(a: JlptApp, level: Int, covered: Boolean = false): List<MockQ> {
    val c = a.content
    val out = ArrayList<MockQ>()
    val met = { id: String -> a.study.stage(id) > 0 }
    val top = minOf(level, a.prefs.examLevel.value)
    val levelWords = if (covered) c.words.filter { it.level >= top && met(it.id) } else c.words.filter { it.level == level }
    val words = levelWords.filter { it.hasKanji && it.word != it.reading }.shuffled().let { if (covered && it.size < 9) c.words.filter { w -> w.level == level && w.hasKanji && w.word != w.reading }.shuffled() else it }
    val pool = levelWords.ifEmpty { c.words.filter { it.level == level } }
    fun others(n: Int, pick: (app.jlpt.data.Word) -> String, not: String) =
        pool.shuffled().map(pick).filter { it != not && it.isNotBlank() }.distinct().take(n)

    // Kanji reading: how is the underlined word read?
    for (w in words.take(5)) {
        val opts = (others(3, { it.reading }, w.reading) + w.reading).shuffled()
        val q = if (w.exampleJp.isNotBlank() && w.exampleJp.contains(w.word)) w.exampleJp.replace("{${w.word}|${w.reading}}", "【${w.word}】") else "【${w.word}】"
        out += MockQ("Vocabulary", Quiz(q + "\nHow do you read the word in 【 】?", opts, opts.indexOf(w.reading), w.exampleEn), noFurigana = true)
    }
    // Writing: which kanji fits the kana?
    for (w in words.drop(5).take(4)) {
        val opts = (others(3, { if (it.hasKanji) it.word else "" }, w.word) + w.word).shuffled()
        out += MockQ("Vocabulary", Quiz("「${w.reading}」\nWhich is the right way to write it?", opts, opts.indexOf(w.word), w.meaning), noFurigana = true)
    }
    // Meaning.
    for (w in pool.shuffled().take(5)) {
        val right = w.meaning.split(';').first()
        val opts = (others(3, { it.meaning.split(';').first() }, right) + right).shuffled()
        out += MockQ("Vocabulary", Quiz("${w.markup}\nWhat does it mean?", opts, opts.indexOf(right), ""))
    }
    // Grammar.
    val grammar = if (covered) c.grammar.filter { it.level >= top && met(it.id) }.ifEmpty { c.grammar.filter { it.level == level } } else c.grammar.filter { it.level == level }
    for (g in grammar.shuffled().take(8)) {
        val q = g.quiz.randomOrNull() ?: continue
        out += MockQ("Grammar", q)
    }
    // Reading.
    for (r in c.readings.filter { it.level == level }.shuffled().take(2)) {
        for (q in r.questions) out += MockQ("Reading", q, passage = r.text)
    }
    // Listening.
    for (l in c.listenings.filter { it.level == level }.shuffled().take(4)) {
        out += MockQ("Listening", l.quiz, listening = l)
    }
    return out
}

@Composable
fun MockTestScreen(level: Int, onBack: () -> Unit, covered: Boolean = false) {
    val a = app()
    val test = remember(level) { buildTest(a, level, covered) }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    var i by remember { mutableIntStateOf(-1) }
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finished by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 120)

    LaunchedEffect(i) {
        while (i >= 0 && !finished) { now = System.currentTimeMillis(); delay(1000) }
    }
    DisposableEffect(Unit) { onDispose { a.speech.stop() } }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (covered) "Weekly check" else "${lvl(level)} mock test", { if (i >= 0 && !finished) leaving = true else onBack() }, actions = {
            if (i >= 0 && !finished) {
                val s = ((now - start) / 1000).coerceAtLeast(0)
                Text("%d:%02d".format(s / 60, s % 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp))
            }
        })
        when {
            i < 0 -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("${test.size} questions", style = MaterialTheme.typography.headlineSmall)
                Gap(8.dp)
                for (s in sections) {
                    val n = test.count { it.section == s }
                    if (n > 0) Text("$s: $n", style = MaterialTheme.typography.bodyLarge)
                }
                Gap(12.dp)
                Text(
                    "Like the real test, you see your score at the end. Furigana is hidden on reading questions. Take your time; the clock is just for you.",
                    color = LocalExtra.current.muted,
                )
                Gap(24.dp)
                Button(onClick = { start = System.currentTimeMillis(); i = 0 }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Start") }
            }
            finished -> Results(level, test, answers, ((now - start) / 60000).toInt(), onBack, covered)
            else -> {
                val q = test[i]
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(q.section, Modifier.weight(1f))
                        Text("${i + 1} / ${test.size}", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
                    }
                    Gap(6.dp)
                    Meter((i + 1) / test.size.toFloat(), height = 4.dp)
                    Gap(16.dp)
                    if (q.passage != null) {
                        Panel { Ruby(q.passage, size = 18.sp) }
                        Gap(16.dp)
                    }
                    if (q.listening != null) {
                        MockPlayer(q.listening)
                        Gap(16.dp)
                    }
                    val text = if (q.listening != null) "" else q.quiz.q
                    if (text.isNotBlank()) {
                        Ruby(text, size = 20.sp, show = if (q.noFurigana) false else null)
                        Gap(12.dp)
                    }
                    q.quiz.options.forEachIndexed { oi, opt ->
                        val sel = answers[i] == oi
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else LocalExtra.current.card)
                                .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.secondary else LocalExtra.current.line, RoundedCornerShape(14.dp))
                                .clickable { answers[i] = oi; lastTouch = System.currentTimeMillis() }
                                .heightIn(min = 50.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${oi + 1}", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
                            HGap(12.dp)
                            Ruby(opt, size = 18.sp, modifier = Modifier.weight(1f), show = if (q.noFurigana) false else null)
                        }
                    }
                    Gap(24.dp)
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (i > 0) OutlinedButton(onClick = { a.speech.stop(); i-- }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Back") }
                    Button(
                        onClick = {
                            a.speech.stop()
                            if (i == test.lastIndex) finished = true else i++
                        },
                        Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(if (i == test.lastIndex) "Finish" else if (answers[i] == null) "Skip" else "Next") }
                }
            }
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text("Leave the test?") },
            text = { Text("Your answers so far won't be saved.") },
            confirmButton = { TextButton(onClick = { leaving = false; onBack() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun MockPlayer(l: Listening) {
    Panel {
        AudioBar("mock:${l.id}", l.audioLines(), big = true, label = "Play the dialogue, then answer")
    }
}

@Composable
private fun Results(level: Int, test: List<MockQ>, answers: Map<Int, Int>, minutes: Int, onBack: () -> Unit, weekly: Boolean = false) {
    val a = app()
    val bySection = sections.mapNotNull { s ->
        val idx = test.indices.filter { test[it].section == s }
        if (idx.isEmpty()) null else Triple(s, idx.count { answers[it] == test[it].quiz.answer }, idx.size)
    }
    val score = bySection.sumOf { it.second }
    val total = bySection.sumOf { it.third }
    val pct = if (total == 0) 0 else score * 100 / total
    val weakest = bySection.minByOrNull { it.second / it.third.toFloat() }
    val pass = pct >= 55 && bySection.all { it.second * 100 / it.third >= 35 }

    LaunchedEffect(Unit) {
        a.db.dao().addTest(TestResult(
            level = level, time = System.currentTimeMillis(),
            sections = (if (weekly) "weekly;" else "") + bySection.joinToString(";") { "${it.first}=${it.second}/${it.third}" },
            score = score, total = total,
        ))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("$pct%", fontSize = 56.sp, style = MaterialTheme.typography.headlineMedium)
        Text(
            if (pass) "That would likely be a pass. 合格!" else "Not a pass yet. That's what practice is for.",
            style = MaterialTheme.typography.titleMedium,
            color = if (pass) LocalExtra.current.good else MaterialTheme.colorScheme.primary,
        )
        Text("$score of $total right in $minutes min", color = LocalExtra.current.muted)
        Gap(16.dp)
        Panel {
            for ((s, got, of) in bySection) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(s, Modifier.weight(0.35f))
                    Meter(got / of.toFloat(), Modifier.weight(0.45f))
                    Text("$got/$of", Modifier.weight(0.2f), textAlign = TextAlign.End)
                }
            }
        }
        if (weakest != null && !pass) {
            Gap(8.dp)
            Text("Focus next on: ${weakest.first.lowercase()}.", color = LocalExtra.current.muted)
        }
        Gap(20.dp)
        Label("Your mistakes")
        val wrong = test.indices.filter { answers[it] != test[it].quiz.answer }
        if (wrong.isEmpty()) Text("None! すごい!")
        for (idx in wrong) {
            val q = test[idx]
            Gap(10.dp)
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                Label(q.section)
                Gap(4.dp)
                val text = if (q.listening != null) q.listening.quiz.q else q.quiz.q
                Ruby(text, size = 16.sp, show = true)
                Gap(4.dp)
                answers[idx]?.let { Text("You: ${Furigana.plain(q.quiz.options[it])}", color = LocalExtra.current.bad) }
                    ?: Text("Skipped", color = LocalExtra.current.muted)
                Ruby("Right: " + q.quiz.options[q.quiz.answer], size = 16.sp, color = LocalExtra.current.good, show = true)
                if (q.quiz.en.isNotBlank()) Text(q.quiz.en, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                if (q.quiz.why.isNotBlank()) Text(q.quiz.why, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
        }
        Gap(24.dp)
        Button(onClick = onBack, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
        Gap(24.dp)
    }
}
