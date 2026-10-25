package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.ClaudeClient
import app.jlpt.data.ExerciseDone
import app.jlpt.data.Furigana
import app.jlpt.data.Listening
import app.jlpt.data.Quiz
import app.jlpt.data.Reading
import app.jlpt.data.Speech
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PracticeScreen(nav: (Screen) -> Unit) {
    val a = app()
    var level by rememberSaveable { mutableIntStateOf(a.study.currentLevel()) }
    val tests by remember { a.db.dao().testsFlow() }.collectAsState(initial = emptyList())
    val key by a.prefs.claudeKey.state.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TitleBar("Practice")
        val states by a.study.states.collectAsState()
        val weak = remember(states) { a.study.weakCount() }
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        Label("Review")
        Gap(8.dp)
        TaskRow(Icons.Rounded.Healing, "Weak spots", if (weak == 0) "Nothing yet: things you get wrong land here" else "$weak things you often get wrong") {
            nav(Screen.Study(0, weak = true))
        }
        Gap(8.dp)
        TaskRow(Icons.Rounded.Bolt, "Speed round", "60 seconds, as many as you can · best ${a.prefs.speedBest.value}") { nav(Screen.Speed) }
        Gap(8.dp)
        TaskRow(Icons.Rounded.Headphones, "Listen", "Japanese audio in the background, screen off") { nav(Screen.Listen) }
        Gap(8.dp)
        TaskRow(Icons.Rounded.ContentPaste, "Read anything", "Paste Japanese (or share it here from any app) for furigana and meanings") {
            val t = clipboard.getText()?.text.orEmpty()
            nav(Screen.LookUp(t))
        }
        Gap(20.dp)
        Label("By level")
        Gap(8.dp)
        ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { level = it }
        Gap(12.dp)
        TaskRow(Icons.Rounded.Reorder, "Sentence building ★", "Put the words in order, like the real test, at ${lvl(level)}") { nav(Screen.Sentences(level)) }
        Gap(12.dp)
        Panel(color = MaterialTheme.colorScheme.primaryContainer) {
            Label("Mock test", color = MaterialTheme.colorScheme.onPrimaryContainer)
            Gap(4.dp)
            Text("${lvl(level)} practice exam", style = MaterialTheme.typography.titleLarge)
            Text(
                "Vocabulary, grammar, reading and listening, like the real thing. About 25 minutes.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Gap(12.dp)
            Button(onClick = { nav(Screen.MockTest(level)) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start mock test") }
            val last = tests.filter { it.level == level }.take(3)
            if (last.isNotEmpty()) {
                Gap(10.dp)
                for (t in last) {
                    val date = Instant.ofEpochMilli(t.time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))
                    Text("$date: ${t.score * 100 / t.total.coerceAtLeast(1)}%  (${t.score}/${t.total})", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Gap(12.dp)
        TaskRow(Icons.Rounded.AutoStories, "Stories", "${a.content.stories.count { it.level == level }} graded stories at ${lvl(level)}") { nav(Screen.Stories(level)) }
        Gap(8.dp)
        TaskRow(Icons.AutoMirrored.Rounded.MenuBook, "Reading", "${a.content.readings.count { it.level == level }} passages at ${lvl(level)}") { nav(Screen.Readings(level)) }
        Gap(8.dp)
        TaskRow(Icons.Rounded.Headphones, "Listening", "${a.content.listenings.count { it.level == level }} dialogues at ${lvl(level)}") { nav(Screen.Listenings(level)) }
        Gap(8.dp)
        TaskRow(Icons.Rounded.Visibility, "Furigana practice", "Read sentences with words you know, readings hidden") { nav(Screen.FuriganaPractice) }
        Gap(8.dp)
        TaskRow(
            Icons.Rounded.AutoAwesome, "A new reading, just for you",
            if (key.isBlank()) "Add your Claude key in Settings to use this" else "Claude writes one with words you've learned",
        ) { if (key.isNotBlank()) nav(Screen.AiReading) else nav(Screen.Settings) }
        Gap(24.dp)
    }
}

// ---------------- reading ----------------

@Composable
fun ReadingsScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val byId = done.associateBy { it.id }
    val list = a.content.readings.filter { it.level == level }
    Column(Modifier.fillMaxSize()) {
        BackBar("Reading · ${lvl(level)}", onBack)
        Box(Modifier.padding(horizontal = 16.dp)) { ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { level = it } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { r ->
                ExerciseRow(r.title, "${r.kind} · ${plural(r.questions.size, "question")}", byId[r.id]) { nav(Screen.ReadingOne(r.id, list.map { it.id })) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(title: String, sub: String, done: ExerciseDone?, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            }
            if (done != null) {
                Text("${done.score}/${done.total}", color = LocalExtra.current.good, style = MaterialTheme.typography.labelLarge)
                HGap(6.dp)
                Icon(Icons.Rounded.CheckCircle, null, tint = LocalExtra.current.good)
            }
        }
    }
}

@Composable
fun ReadingScreen(id: String, onBack: () -> Unit, explainInChat: (String) -> Unit, onOpen: (String) -> Unit) {
    val a = app()
    val r = a.content.readings.firstOrNull { it.id == id } ?: return
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val next = remember(done, id) { nextId(a.content.readings.filter { it.level == r.level }.map { it.id }, id, done.map { it.id }.toSet()) }
    Column(Modifier.fillMaxSize()) {
        BackBar(r.title, onBack)
        ReadingView(r, save = true, explainInChat = explainInChat, onNext = next?.let { n -> { onOpen(n) } })
    }
}

/** The next one to do after [id]: the first not done yet, going on from [id] and wrapping round. */
fun nextId(ids: List<String>, id: String, done: Set<String>): String? {
    val i = ids.indexOf(id)
    if (i < 0 || ids.size < 2) return null
    val order = ids.drop(i + 1) + ids.take(i)
    return order.firstOrNull { it !in done } ?: order.first()
}

/** A passage with its questions. Furigana follows the setting; tap a word to peek. */
@Composable
fun ReadingView(r: Reading, save: Boolean, explainInChat: (String) -> Unit, onNext: (() -> Unit)? = null) {
    val a = app()
    var english by remember(r.id) { mutableStateOf(false) }
    val chosen = remember(r.id) { mutableStateMapOf<Int, Int>() }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 180)
    val allDone = chosen.size == r.questions.size

    LaunchedEffect(allDone) {
        if (allDone && save && r.questions.isNotEmpty()) {
            val score = r.questions.indices.count { chosen[it] == r.questions[it].answer }
            a.db.dao().putExercise(ExerciseDone(r.id, score, r.questions.size, System.currentTimeMillis()))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(remember(r.id) { androidx.compose.foundation.ScrollState(0) }).padding(horizontal = 16.dp)) {
        Panel {
            Label("${lvl(r.level)} · ${r.kind}")
            Gap(8.dp)
            Ruby(r.text, size = 19.sp)
            Gap(10.dp)
            AudioBar("reading:${r.id}", Speech.sentences(r.text).map { "N" to it }, onPlay = { lastTouch = System.currentTimeMillis() })
        }
        Gap(8.dp)
        TextButton(onClick = { english = !english; lastTouch = System.currentTimeMillis() }) { Text(if (english) "Hide English" else "Show English") }
        if (english) {
            Text(r.en, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
            Gap()
        }
        r.questions.forEachIndexed { qi, q ->
            Gap(12.dp)
            QuizBlock(q, chosen[qi]) { pick ->
                lastTouch = System.currentTimeMillis()
                chosen[qi] = pick
            }
        }
        if (allDone) {
            val score = r.questions.indices.count { chosen[it] == r.questions[it].answer }
            Gap(16.dp)
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                Text("$score of ${r.questions.size} right", style = MaterialTheme.typography.titleLarge)
                if (a.claude.hasKey) {
                    TextButton(onClick = {
                        explainInChat("Help me understand this Japanese passage. Explain any hard parts simply.\n\n${Furigana.plain(r.text)}")
                    }) { Text("Ask Claude about this passage") }
                }
            }
            if (onNext != null) {
                Gap(12.dp)
                Button(onClick = onNext, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Next reading") }
            }
        }
        Gap(32.dp)
    }
}

/** A question with its options; shows right and wrong after a pick. */
@Composable
fun QuizBlock(q: Quiz, chosen: Int?, onPick: (Int) -> Unit) {
    val ex = LocalExtra.current
    var english by remember(q.q) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Ruby(q.q, size = 18.sp, modifier = Modifier.weight(1f))
            if (q.qEn.isNotBlank() && q.q.isNotBlank()) {
                TextButton(onClick = { english = !english }) { Text(if (english) "Hide" else "EN", style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (english && q.qEn.isNotBlank()) {
            Text(q.qEn, style = MaterialTheme.typography.bodySmall, color = ex.muted)
        }
        Gap(8.dp)
        q.options.forEachIndexed { i, opt ->
            val answered = chosen != null
            val isRight = i == q.answer
            val bg = when { !answered -> ex.card; isRight -> ex.goodSoft; i == chosen -> ex.badSoft; else -> ex.card }
            val border = when { !answered -> ex.line; isRight -> ex.good; i == chosen -> ex.bad; else -> ex.line }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                    .border(1.dp, border, RoundedCornerShape(14.dp))
                    .clickable(enabled = !answered) { onPick(i) }
                    .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = ex.muted)
                HGap(12.dp)
                Ruby(opt, size = 17.sp, modifier = Modifier.weight(1f))
            }
        }
        if (chosen != null && q.why.isNotBlank()) {
            Gap(4.dp)
            Text(q.why, style = MaterialTheme.typography.bodyMedium, color = if (chosen == q.answer) ex.good else ex.bad)
        }
    }
}

// ---------------- listening ----------------

@Composable
fun ListeningsScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val byId = done.associateBy { it.id }
    val list = a.content.listenings.filter { it.level == level }
    Column(Modifier.fillMaxSize()) {
        BackBar("Listening · ${lvl(level)}", onBack)
        Box(Modifier.padding(horizontal = 16.dp)) { ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { level = it } }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { l ->
                ExerciseRow(l.title, kindLabel(l.kind), byId[l.id]) { nav(Screen.ListeningOne(l.id, list.map { it.id })) }
            }
        }
    }
}

private fun kindLabel(kind: String) = when (kind) {
    "task" -> "What to do next"
    "point" -> "Catch the key point"
    "utterance" -> "What do you say?"
    "response" -> "Quick reply"
    "summary" -> "What's it about?"
    "integrated" -> "Long talk"
    else -> kind
}

/** The narrator's setup, the dialogue, then the question, as the real test plays it. */
fun Listening.audioLines(): List<Pair<String, String>> = buildList {
    if (setup.isNotBlank()) add("N" to setup)
    for (l in lines) add(l.speaker to l.jp)
    if (quiz.q.isNotBlank()) add("N" to quiz.q)
}

@Composable
fun ListeningScreen(id: String, onBack: () -> Unit, explainInChat: (String) -> Unit, onOpen: (String) -> Unit) {
    val a = app()
    val l = a.content.listenings.firstOrNull { it.id == id } ?: return
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val next = remember(done, id) { nextId(a.content.listenings.filter { it.level == l.level }.map { it.id }, id, done.map { it.id }.toSet()) }
    Column(Modifier.fillMaxSize()) {
        BackBar(l.title, onBack)
        ListeningView(l, save = true, explainInChat = explainInChat, onNext = next?.let { n -> { onOpen(n) } })
    }
}

@Composable
fun ListeningView(l: Listening, save: Boolean, explainInChat: (String) -> Unit = {}, compact: Boolean = false, onNext: (() -> Unit)? = null, onAnswer: (Int) -> Unit = {}) {
    val a = app()
    val scope = rememberCoroutineScope()
    var played by remember(l.id) { mutableStateOf(false) }
    var chosen by remember(l.id) { mutableStateOf<Int?>(null) }
    var script by remember(l.id) { mutableStateOf(false) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (!compact) StudyClock(lastTouch, 90)
    Column(Modifier.fillMaxSize().verticalScroll(remember(l.id) { androidx.compose.foundation.ScrollState(0) }).padding(horizontal = 16.dp)) {
        if (!compact) {
            Label("${lvl(l.level)} · ${kindLabel(l.kind)}")
            Gap(8.dp)
        }
        Panel {
            AudioBar(
                "listening:${l.id}", l.audioLines(), big = true,
                label = if (played) "Play again any time" else "Listen first, then answer",
                onPlay = { played = true; lastTouch = System.currentTimeMillis() },
            )
        }
        if (!a.speech.ready) {
            Gap(8.dp)
            Text("No Japanese voice on this phone yet. See the tip on the Today page.", color = LocalExtra.current.bad,
                style = MaterialTheme.typography.bodySmall)
        }
        Gap(16.dp)
        QuizBlock(l.quiz.copy(q = if (played || chosen != null) l.quiz.q else ""), chosen) { pick ->
            lastTouch = System.currentTimeMillis()
            chosen = pick
            onAnswer(pick)
            if (save) scope.launch {
                a.db.dao().putExercise(ExerciseDone(l.id, if (pick == l.quiz.answer) 1 else 0, 1, System.currentTimeMillis()))
            }
        }
        if (chosen != null && onNext != null) {
            Gap(16.dp)
            Button(onClick = onNext, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Next listening") }
        }
        Gap(12.dp)
        if (chosen != null || !compact) {
            TextButton(onClick = { script = !script }) { Text(if (script) "Hide the script" else "Show the script") }
        }
        if (script) {
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                if (l.setup.isNotBlank()) { Ruby(l.setup, size = 16.sp, color = LocalExtra.current.muted); Gap(8.dp) }
                for (line in l.lines) {
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(when (line.speaker) { "M" -> "男"; "F" -> "女"; else -> "" }, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 10.dp, end = 8.dp))
                        Ruby(line.jp, size = 17.sp, modifier = Modifier.weight(1f))
                    }
                }
                Gap(8.dp)
                Translation(l.en)
                if (a.claude.hasKey) {
                    TextButton(onClick = {
                        explainInChat("Help me understand this listening practice.\n\n" +
                            l.audioLines().joinToString("\n") { (s, t) -> "$s: ${Furigana.plain(t)}" })
                    }) { Text("Ask Claude about it") }
                }
            }
        }
        Gap(32.dp)
    }
}

// ---------------- furigana practice ----------------

@Composable
fun FuriganaPracticeScreen(onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val pool = remember {
        val started = a.content.words.filter { (states[it.id]?.stage ?: 0) > 0 && it.exampleJp.contains('{') }
        val base = started.ifEmpty { a.content.words.filter { it.level == 5 && it.exampleJp.contains('{') }.take(150) }
        base.shuffled().take(10)
    }
    var i by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var good by remember { mutableIntStateOf(0) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 60)

    Column(Modifier.fillMaxSize()) {
        BackBar("Furigana practice", onBack)
        if (i >= pool.size) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("$good of ${pool.size} read without help", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Gap(16.dp)
                Button(onClick = onBack) { Text("Done") }
            }
            return@Column
        }
        val w = pool[i]
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text("Read it out loud, then check the readings.", color = LocalExtra.current.muted)
            Text("Tap a single word to peek at just that one.", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
            Gap(24.dp)
            Ruby(w.exampleJp, size = 28.sp, show = shown, modifier = Modifier.fillMaxWidth())
            if (shown) {
                Gap(16.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Translation(w.exampleEn, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    SpeakButton(w.exampleJp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!shown) {
                Button(onClick = { shown = true; lastTouch = System.currentTimeMillis() }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                    Text("Show readings")
                }
            } else {
                OutlinedButton(onClick = { i++; shown = false; lastTouch = System.currentTimeMillis() }, Modifier.weight(1f).heightIn(min = 54.dp)) {
                    Text("Missed some")
                }
                Button(onClick = { good++; i++; shown = false; lastTouch = System.currentTimeMillis() }, Modifier.weight(1f).heightIn(min = 54.dp)) {
                    Text("Read it all")
                }
            }
        }
    }
}

// ---------------- a new reading from Claude ----------------

@Composable
fun AiReadingScreen(onBack: () -> Unit, explainInChat: (String) -> Unit) {
    val a = app()
    var reading by remember { mutableStateOf<Reading?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var run by remember { mutableIntStateOf(0) }

    LaunchedEffect(run) {
        reading = null; error = null
        val level = a.study.currentLevel()
        val states = a.study.states.value
        val known = a.content.words.filter { (states[it.id]?.stage ?: 0) > 0 }.shuffled().take(40).joinToString("、") { it.word }
        val r = a.claude.ask(
            "You write JLPT practice material. " + ClaudeClient.STYLE + " Reply with JSON only, no other text.",
            """Write a short JLPT N$level style reading passage (150 to 300 characters) about everyday life, using N$level grammar and vocabulary or easier.
Try to use some of these words the learner knows: $known
Reply with JSON exactly like:
{"title":"English title","text":"passage with furigana markup","en":"English translation","questions":[{"q":"question in Japanese with markup","q_en":"English question","options":["...","...","...","..."],"answer":0,"why":"one short sentence"}]}
Give 2 or 3 questions. Vary the position of the right answer.
Like the real JLPT, exactly one option must be right: no wrong option may also fit the passage, and no option may be a polite or casual variant of the right one.""",
            smart = true, maxTokens = 2500,
        )
        when (r) {
            is ClaudeClient.Reply.Failed -> error = r.message
            is ClaudeClient.Reply.Text -> {
                reading = runCatching {
                    val json = r.text.substring(r.text.indexOf('{'), r.text.lastIndexOf('}') + 1)
                    val o = JSONObject(json)
                    val qs = o.getJSONArray("questions")
                    Reading(
                        "ai-${System.currentTimeMillis()}", level, o.optString("title"), "made for you",
                        o.getString("text"), o.optString("en"),
                        (0 until qs.length()).map { i ->
                            val q = qs.getJSONObject(i)
                            val opts = q.getJSONArray("options")
                            Quiz(q.getString("q"), (0 until opts.length()).map { opts.getString(it) }, q.getInt("answer"),
                                "", q.optString("q_en"), q.optString("why"))
                        },
                    )
                }.getOrElse { error = "Claude's answer could not be read. Try again."; null }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(reading?.title ?: "A new reading", onBack, actions = {
            if (reading != null || error != null) TextButton(onClick = { run++ }) { Text("Another") }
        })
        when {
            error != null -> Column(Modifier.padding(24.dp)) {
                Text(error!!)
                Gap()
                Button(onClick = { run++ }) { Text("Try again") }
            }
            reading == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Gap()
                Text("Claude is writing your reading…", color = LocalExtra.current.muted)
            }
            else -> ReadingView(reading!!, save = false, explainInChat = explainInChat)
        }
    }
}
