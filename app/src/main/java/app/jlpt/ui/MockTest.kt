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
import org.json.JSONArray
import org.json.JSONObject

/** One question of a mock test. A listening question carries its dialogue. */
private data class MockQ(
    val section: String,
    val quiz: Quiz,
    val passage: String? = null,
    val listening: Listening? = null,
    /** Hide furigana on the question (kanji reading questions). */
    val noFurigana: Boolean = false,
    /** The part of the paper, like the real test's 問題 headings. */
    val part: String = "",
    /** Options are plain text (kanji spellings with no readings). */
    val plainOptions: Boolean = false,
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


private val partNames = mapOf(
    "kanji_reading" to "漢字読み · Kanji reading", "orthography" to "表記 · Writing", "word_formation" to "語形成 · Word formation",
    "context" to "文脈規定 · Fill the word", "paraphrase" to "言い換え類義 · Same meaning", "usage" to "用法 · Usage",
    "form" to "文法形式 · Grammar form", "star" to "文の組み立て · ★ Sentence building", "text" to "文章の文法 · Text grammar",
    "short" to "内容理解（短文）· Short passages", "mid" to "内容理解（中文）· Mid passages", "long" to "内容理解（長文）· Long passage",
    "info" to "情報検索 · Information search", "integrated" to "統合理解 · Integrated", "opinion" to "主張理解 · Opinion",
    "task" to "課題理解 · Task", "point" to "ポイント理解 · Key point", "summary" to "概要理解 · Summary",
    "utterance" to "発話表現 · What to say", "response" to "即時応答 · Quick reply",
)

private fun JSONArray.strs(): List<String> = (0 until length()).map { getString(it) }
private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** Turns a written paper into questions, in the real test's order. */
private fun fromPaper(p: JSONObject): List<MockQ> {
    val out = ArrayList<MockQ>()
    var n = 0
    fun part(type: String) = partNames[type] ?: type
    for (o in p.optJSONArray("vocab")?.objs().orEmpty()) {
        val type = o.optString("type")
        val raw = o.has("q_raw")
        val q = if (raw) o.getString("q_raw") else o.optString("q").ifBlank { o.optString("word") }
        val opts = (o.optJSONArray("options") ?: o.optJSONArray("options_raw"))?.strs() ?: continue
        out += MockQ("Vocabulary", Quiz(q, opts, o.optInt("answer"), o.optString("en"), "", o.optString("why")),
            noFurigana = raw, part = part(type), plainOptions = o.has("options_raw"))
    }
    for (o in p.optJSONArray("grammar")?.objs().orEmpty()) {
        when (val type = o.optString("type")) {
            "star" -> {
                val pieces = o.getJSONArray("pieces").strs()
                val order = o.getJSONArray("order").let { a -> (0 until a.length()).map { a.getInt(it) } }
                val star = o.optInt("star")
                val slots = (0 until 4).joinToString(" ") { if (it == star) "＿★＿" else "＿＿＿" }
                val text = "${o.optString("before")} $slots ${o.optString("after")}".trim()
                val full = o.optString("before") + order.joinToString("") { pieces[it] } + o.optString("after")
                out += MockQ("Grammar", Quiz(text, pieces, order[star], o.optString("en"), "", o.optString("why").ifBlank { "Right order: " + Furigana.plain(full) }),
                    part = part(type))
            }
            "text" -> for (tq in o.getJSONArray("questions").objs()) {
                out += MockQ("Grammar", Quiz(tq.optString("q"), tq.getJSONArray("options").strs(), tq.optInt("answer"), o.optString("en"), "", tq.optString("why")),
                    passage = o.optString("passage"), part = part(type))
            }
            else -> out += MockQ("Grammar", Quiz(o.optString("q"), o.getJSONArray("options").strs(), o.optInt("answer"), o.optString("en"), "", o.optString("why")),
                part = part(type))
        }
    }
    for (o in p.optJSONArray("reading")?.objs().orEmpty()) {
        for (rq in o.getJSONArray("questions").objs()) {
            out += MockQ("Reading", Quiz(rq.optString("q"), rq.getJSONArray("options").strs(), rq.optInt("answer"), o.optString("en"), rq.optString("q_en"), rq.optString("why")),
                passage = o.optString("text"), part = part(o.optString("type")))
        }
    }
    for (o in p.optJSONArray("listening")?.objs().orEmpty()) {
        val quiz = Quiz(o.optString("question"), o.getJSONArray("options").strs(), o.optInt("answer"), o.optString("en"), "", o.optString("why"))
        val l = Listening("${p.optString("id")}-l${n++}", p.optInt("level"), "", o.optString("type"), o.optString("setup"),
            o.optJSONArray("lines")?.objs().orEmpty().map { app.jlpt.data.Line(it.optString("s"), it.optString("jp")) }, quiz, o.optString("en"))
        out += MockQ("Listening", quiz, listening = l, part = part(o.optString("type")))
    }
    return out
}

/** A timed part of the test, like the real sessions. */
private data class Session(val name: String, val minutes: Int, val sections: Set<String>)

private fun sessionsFor(level: Int): List<Session> = when (level) {
    5 -> listOf(Session("Vocabulary", 20, setOf("Vocabulary")), Session("Grammar · Reading", 40, setOf("Grammar", "Reading")), Session("Listening", 30, setOf("Listening")))
    4 -> listOf(Session("Vocabulary", 25, setOf("Vocabulary")), Session("Grammar · Reading", 55, setOf("Grammar", "Reading")), Session("Listening", 35, setOf("Listening")))
    3 -> listOf(Session("Vocabulary", 30, setOf("Vocabulary")), Session("Grammar · Reading", 70, setOf("Grammar", "Reading")), Session("Listening", 40, setOf("Listening")))
    2 -> listOf(Session("Language knowledge · Reading", 105, setOf("Vocabulary", "Grammar", "Reading")), Session("Listening", 50, setOf("Listening")))
    else -> listOf(Session("Language knowledge · Reading", 110, setOf("Vocabulary", "Grammar", "Reading")), Session("Listening", 55, setOf("Listening")))
}

/** The next paper not taken yet at [level]; once all are taken, the one taken longest ago. */
private fun pickPaper(a: JlptApp, level: Int): JSONObject? {
    val all = a.content.papers.filter { it.optInt("level") == level }
    if (all.isEmpty()) return null
    val done = a.prefs.papersDone.value.split(',').filter { it.isNotBlank() }
    return all.firstOrNull { it.optString("id") !in done } ?: all.minByOrNull { done.indexOf(it.optString("id")) }
}

@Composable
fun MockTestScreen(level: Int, onBack: () -> Unit, covered: Boolean = false) {
    val a = app()
    val paper = remember(level) { pickPaper(a, level) }
    val test = remember(level) { paper?.let { fromPaper(it) } ?: buildTest(a, level, covered) }
    val sessions = remember(level) {
        sessionsFor(level).map { s -> s to test.indices.filter { test[it].section in s.sections } }.filter { it.second.isNotEmpty() }
    }
    val answers = remember { mutableStateMapOf<Int, Int>() }
    var i by remember { mutableIntStateOf(-1) }
    var start by remember { mutableLongStateOf(0L) }
    var sessionStart by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finished by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 120)
    val sessionIndex = sessions.indexOfFirst { i in it.second }
    val session = sessions.getOrNull(sessionIndex)
    var intro by remember { mutableStateOf(true) }   // the screen before each timed part

    fun nextSession() {
        a.speech.stop()
        val next = sessions.getOrNull(sessionIndex + 1)
        if (next == null) finished = true else { i = next.second.first(); intro = true }
    }

    LaunchedEffect(i, intro) {
        while (i >= 0 && !finished && !intro) {
            now = System.currentTimeMillis()
            val s = session
            // Time is up for this part: move on, like the real test.
            if (s != null && paper != null && now - sessionStart >= s.first.minutes * 60_000L) { nextSession(); break }
            delay(1000)
        }
    }
    DisposableEffect(Unit) { onDispose { a.speech.stop() } }

    Column(Modifier.fillMaxSize()) {
        BackBar(if (covered) "Weekly check" else "${lvl(level)} mock test", { if (i >= 0 && !finished) leaving = true else onBack() }, actions = {
            if (i >= 0 && !finished && !intro && session != null) {
                val left = if (paper != null) (session.first.minutes * 60 - (now - sessionStart) / 1000).coerceAtLeast(0) else (now - sessionStart) / 1000
                Text("%d:%02d".format(left / 60, left % 60), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp),
                    color = if (paper != null && left < 120) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
            }
        })
        when {
            i < 0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
                Gap(24.dp)
                Text(paper?.optString("title_en")?.ifBlank { null } ?: "${lvl(level)} practice test", style = MaterialTheme.typography.headlineSmall)
                Gap(8.dp)
                Text("${test.size} questions in ${sessions.size} timed parts, like the real exam:", color = LocalExtra.current.muted)
                Gap(8.dp)
                for ((s, idx) in sessions) Text("· ${s.name}: ${idx.size} questions, ${s.minutes} min", style = MaterialTheme.typography.bodyLarge)
                Gap(12.dp)
                Text(
                    if (paper != null) "A fresh paper you haven't seen in practice. Each part has its own clock and moves on when time is up. Listening plays once, like the real test. Your score comes at the end."
                    else "All the full papers at this level are done, so this one is made from your study items.",
                    color = LocalExtra.current.muted,
                )
                Gap(24.dp)
                Button(onClick = { start = System.currentTimeMillis(); i = sessions.firstOrNull()?.second?.first() ?: 0; intro = true },
                    Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Start") }
            }
            finished -> Results(level, test, answers, ((now - start) / 60000).toInt(), onBack, covered, paper?.optString("id"))
            intro && session != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Label("Part ${sessionIndex + 1} of ${sessions.size}")
                Text(session.first.name, style = MaterialTheme.typography.headlineMedium)
                Gap(8.dp)
                Text("${session.second.size} questions · ${session.first.minutes} minutes", color = LocalExtra.current.muted)
                Gap(24.dp)
                Button(onClick = { sessionStart = System.currentTimeMillis(); now = sessionStart; intro = false },
                    Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("Begin") }
            }
            else -> {
                val q = test[i]
                val idx = session?.second ?: test.indices.toList()
                val pos = idx.indexOf(i)
                Column(Modifier.weight(1f).verticalScroll(remember(i) { androidx.compose.foundation.ScrollState(0) }).padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label(q.part.ifBlank { q.section }, Modifier.weight(1f))
                        Text("${pos + 1} / ${idx.size}", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
                    }
                    Gap(6.dp)
                    Meter((pos + 1) / idx.size.toFloat(), height = 4.dp)
                    Gap(16.dp)
                    if (q.passage != null) {
                        Panel { Ruby(q.passage, size = 18.sp) }
                        Gap(16.dp)
                    }
                    if (q.listening != null) {
                        MockPlayer(q.listening, once = paper != null)
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
                            if (q.plainOptions) Text(opt, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            else Ruby(opt, size = 18.sp, modifier = Modifier.weight(1f), show = if (q.noFurigana) false else null)
                        }
                    }
                    Gap(24.dp)
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Like the real test, you can go back within a part (not in listening).
                    if (pos > 0 && q.listening == null) OutlinedButton(onClick = { a.speech.stop(); i = idx[pos - 1] }, Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Back") }
                    Button(
                        onClick = {
                            a.speech.stop()
                            if (pos == idx.lastIndex) nextSession() else i = idx[pos + 1]
                        },
                        Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(if (pos == idx.lastIndex) (if (sessionIndex == sessions.lastIndex) "Finish" else "End this part") else if (answers[i] == null) "Skip" else "Next") }
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

/** Plays a listening question. In a full paper it plays once, like the real test. */
@Composable
private fun MockPlayer(l: Listening, once: Boolean) {
    val a = app()
    val now by a.speech.now.collectAsState()
    val key = "mock:${l.id}"
    val mine = now?.key == key
    var started by remember(l.id) { mutableStateOf(false) }
    DisposableEffect(l.id) { onDispose { if (a.speech.now.value?.key == key) a.speech.stop() } }
    if (!once) {
        Panel { AudioBar(key, l.audioLines(), big = true, label = "Play the dialogue, then answer") }
        return
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    started = true
                    // Don't say the question twice when the narrator's setup already asks it.
                    val q = Furigana.plain(l.quiz.q)
                    val parts = l.audioLines().let { p -> if (q.isNotBlank() && Furigana.plain(l.setup).contains(q) && p.size > 1) p.dropLast(1) else p }
                    a.speech.play(key, parts)
                },
                enabled = !started,
            ) { Text(if (!started) "Play" else if (mine && now?.done == false) "Playing…" else "Played") }
            HGap(12.dp)
            Text(
                if (!started) "It plays once, like the real test. The question is said at the end." else "Listen, then pick the answer.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** JLPT-style result: scaled section scores and the pass rules for the level (an estimate). */
private data class Scaled(val name: String, val score: Int, val max: Int, val min: Int)

private fun scale(level: Int, raw: Map<String, Pair<Int, Int>>): Pair<List<Scaled>, Int> {
    fun frac(vararg names: String): Double {
        val got = names.sumOf { raw[it]?.first ?: 0 }
        val of = names.sumOf { raw[it]?.second ?: 0 }
        return if (of == 0) 0.0 else got.toDouble() / of
    }
    return if (level >= 4) {
        listOf(
            Scaled("Language knowledge · Reading", (frac("Vocabulary", "Grammar", "Reading") * 120).toInt(), 120, 38),
            Scaled("Listening", (frac("Listening") * 60).toInt(), 60, 19),
        ) to if (level == 5) 80 else 90
    } else {
        listOf(
            Scaled("Language knowledge", (frac("Vocabulary", "Grammar") * 60).toInt(), 60, 19),
            Scaled("Reading", (frac("Reading") * 60).toInt(), 60, 19),
            Scaled("Listening", (frac("Listening") * 60).toInt(), 60, 19),
        ) to when (level) { 3 -> 95; 2 -> 90; else -> 100 }
    }
}

@Composable
private fun Results(level: Int, test: List<MockQ>, answers: Map<Int, Int>, minutes: Int, onBack: () -> Unit, weekly: Boolean = false, paperId: String? = null) {
    val a = app()
    val bySection = sections.mapNotNull { s ->
        val idx = test.indices.filter { test[it].section == s }
        if (idx.isEmpty()) null else Triple(s, idx.count { answers[it] == test[it].quiz.answer }, idx.size)
    }
    val score = bySection.sumOf { it.second }
    val total = bySection.sumOf { it.third }
    val pct = if (total == 0) 0 else score * 100 / total
    val weakest = bySection.minByOrNull { it.second / it.third.toFloat() }
    val (scaled, passMark) = scale(level, bySection.associate { it.first to (it.second to it.third) })
    val totalScaled = scaled.sumOf { it.score }
    val pass = totalScaled >= passMark && scaled.all { it.score >= it.min }

    LaunchedEffect(Unit) {
        if (paperId != null) {
            val done = a.prefs.papersDone.value.split(',').filter { it.isNotBlank() && it != paperId }
            a.prefs.papersDone.value = (done + paperId).joinToString(",")
        }
        a.db.dao().addTest(TestResult(
            level = level, time = System.currentTimeMillis(),
            sections = (if (weekly) "weekly;" else "") + (if (paperId != null) "paper:$paperId;" else "") + bySection.joinToString(";") { "${it.first}=${it.second}/${it.third}" },
            score = score, total = total,
        ))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("$totalScaled / 180", fontSize = 48.sp, style = MaterialTheme.typography.headlineMedium)
        Text(
            if (pass) "That would be a pass (pass mark $passMark). 合格!" else "Not a pass yet (pass mark $passMark, and a minimum in each part).",
            style = MaterialTheme.typography.titleMedium,
            color = if (pass) LocalExtra.current.good else MaterialTheme.colorScheme.primary,
        )
        Text("$score of $total right ($pct%) in $minutes min · scores are an estimate of the JLPT scale", color = LocalExtra.current.muted)
        Gap(12.dp)
        Panel {
            for (sc in scaled) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(sc.name, Modifier.weight(0.5f), style = MaterialTheme.typography.bodyMedium)
                    Text("${sc.score}/${sc.max}", Modifier.weight(0.25f), textAlign = TextAlign.End,
                        color = if (sc.score >= sc.min) LocalExtra.current.good else LocalExtra.current.bad)
                    Text("min ${sc.min}", Modifier.weight(0.25f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                }
            }
        }
        Gap(12.dp)
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
                Label(q.part.ifBlank { q.section })
                Gap(4.dp)
                if (q.passage != null) { Ruby(q.passage, size = 14.sp, show = true); Gap(4.dp) }
                if (q.listening != null) {
                    for (line in q.listening.lines) Ruby((when (line.speaker) { "M" -> "男："; "F" -> "女："; else -> "" }) + line.jp, size = 14.sp, show = true)
                    Gap(4.dp)
                }
                val text = if (q.listening != null) q.listening.quiz.q else q.quiz.q
                Ruby(text, size = 16.sp, show = true)
                Gap(4.dp)
                answers[idx]?.let { Ruby("You: " + q.quiz.options[it], size = 16.sp, color = LocalExtra.current.bad, show = true) }
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
