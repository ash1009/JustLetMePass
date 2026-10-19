package app.jlpt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.ExerciseDone
import app.jlpt.data.Furigana
import app.jlpt.data.Story
import app.jlpt.data.Speech

/** Roughly how long a story takes to read at its level, in minutes. */
private fun minutes(s: Story): Int {
    val perMinute = when (s.level) { 5 -> 120; 4 -> 150; 3 -> 200; 2 -> 250; else -> 300 }
    return (s.length / perMinute).coerceAtLeast(1)
}

@Composable
fun StoriesScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    var genre by rememberSaveable { mutableStateOf("All") }
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val byId = done.associateBy { it.id }
    val atLevel = a.content.stories.filter { it.level == level }
    val genres = listOf("All") + atLevel.map { it.genre }.distinct().sorted()
    val list = atLevel.filter { genre == "All" || it.genre == genre }
    val read = atLevel.count { it.id in byId }

    Column(Modifier.fillMaxSize()) {
        BackBar("Stories", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow((5 downTo 1).map { it to "${lvl(it)} · ${a.content.stories.count { s -> s.level == it }}" }, level) {
                level = it; genre = "All"
            }
            Gap(8.dp)
            ChoiceRow(genres.map { it to it }, genre) { genre = it }
            Gap(8.dp)
            Text(
                "$read of ${atLevel.size} read. Furigana fades on words and kanji you know; tap a word to peek, hold it for the meaning.",
                style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { s ->
                Panel(onClick = { nav(Screen.StoryOne(s.id)) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Ruby(s.title, size = 18.sp)
                            Text(s.titleEn, style = MaterialTheme.typography.bodyMedium)
                            Text("${s.genre} · about ${minutes(s)} min", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                        byId[s.id]?.let { d ->
                            HGap(8.dp)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.CheckCircle, "Read", tint = LocalExtra.current.good)
                                Text("${d.score}/${d.total}", style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.good)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoryScreen(id: String, onBack: () -> Unit, onOpen: (String) -> Unit, explainInChat: (String) -> Unit) {
    val a = app()
    val s = a.content.stories.firstOrNull { it.id == id } ?: return
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    val next = remember(done, id) { nextId(a.content.stories.filter { it.level == s.level }.map { it.id }, id, done.map { it.id }.toSet()) }
    val shownEn = remember(id) { mutableStateMapOf<Int, Boolean>() }
    var allEn by remember(id) { mutableStateOf(false) }
    val chosen = remember(id) { mutableStateMapOf<Int, Int>() }
    var meaning by remember(id) { mutableStateOf<Pair<String, String>?>(null) }
    var lastTouch by remember(id) { mutableLongStateOf(System.currentTimeMillis()) }
    val scroll = rememberScrollState()
    val allDone = s.questions.isNotEmpty() && chosen.size == s.questions.size
    StudyClock(lastTouch, 180)
    DisposableEffect(id) { onDispose { a.speech.stop() } }
    // Scrolling through the story counts as reading.
    LaunchedEffect(scroll.value / 400) { lastTouch = System.currentTimeMillis() }
    LaunchedEffect(allDone) {
        if (allDone) {
            val score = s.questions.indices.count { chosen[it] == s.questions[it].answer }
            a.db.dao().putExercise(ExerciseDone(s.id, score, s.questions.size, System.currentTimeMillis()))
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar(s.titleEn, onBack, actions = {
            TextButton(onClick = { allEn = !allEn; lastTouch = System.currentTimeMillis() }) { Text(if (allEn) "Hide English" else "English") }
        })
        CompositionLocalProvider(LocalWordLookup provides { text, reading ->
            lastTouch = System.currentTimeMillis()
            meaning = "$text（$reading）" to (a.content.lookup(text, reading) ?: "Not in the word list. Ask in Chat.")
        }) {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 18.dp)) {
                Label("${lvl(s.level)} · ${s.genre} · about ${minutes(s)} min")
                Gap(6.dp)
                Ruby(s.title, size = 26.sp, family = JpBig)
                Gap(12.dp)
                AudioBar(
                    "story:${s.id}", s.paragraphs.flatMap { p -> Speech.sentences(p.jp) }.map { "N" to it },
                    label = "Listen to the story", onPlay = { lastTouch = System.currentTimeMillis() },
                )
                Gap(16.dp)
                s.paragraphs.forEachIndexed { i, p ->
                    Ruby(p.jp, size = 20.sp, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { shownEn[i] = !(shownEn[i] ?: false); lastTouch = System.currentTimeMillis() }) {
                            Text(if (allEn || shownEn[i] == true) "Hide" else "English", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = { a.speech.say(p.jp); lastTouch = System.currentTimeMillis() }) {
                            Text("Listen", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    AnimatedVisibility(allEn || shownEn[i] == true) {
                        Text(p.en, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted,
                            modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Gap(10.dp)
                }
                if (s.questions.isNotEmpty()) {
                    Gap(8.dp)
                    Label("Did you get it?")
                    s.questions.forEachIndexed { qi, q ->
                        Gap(12.dp)
                        QuizBlock(q, chosen[qi]) { pick -> chosen[qi] = pick; lastTouch = System.currentTimeMillis() }
                    }
                }
                if (allDone) {
                    val score = s.questions.indices.count { chosen[it] == s.questions[it].answer }
                    Gap(16.dp)
                    Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Text("$score of ${s.questions.size} right", style = MaterialTheme.typography.titleLarge)
                        if (a.claude.hasKey) {
                            TextButton(onClick = {
                                explainInChat("Help me with this Japanese story. Explain the hard sentences simply, and list useful words from it.\n\n" +
                                    s.paragraphs.joinToString("\n") { Furigana.plain(it.jp) })
                            }) { Text("Ask Claude about this story") }
                        }
                    }
                }
                if (next != null) {
                    Gap(12.dp)
                    Button(onClick = { onOpen(next) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Next story") }
                }
                Gap(32.dp)
            }
        }
        meaning?.let { (word, text) ->
            Row(
                Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                IconButton(onClick = { meaning = null }) { Icon(Icons.Rounded.Close, "Close") }
            }
        }
    }
}
