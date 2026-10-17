package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.Kana
import app.jlpt.data.Kind
import app.jlpt.data.Srs
import kotlinx.coroutines.launch

@Composable
fun stageColor(stage: Int): Color {
    val c = MaterialTheme.colorScheme.primary
    return when {
        stage <= 0 -> Color.Transparent
        stage < 3 -> c.copy(alpha = 0.25f)
        stage < Srs.KNOWN -> c.copy(alpha = 0.5f)
        stage < Srs.MAX -> c.copy(alpha = 0.85f)
        else -> LocalExtra.current.good
    }
}

@Composable
private fun SectionRow(title: String, sub: String, started: Int, known: Int, total: Int, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
        if (total > 0) {
            Gap(8.dp)
            DoubleMeter(started / total.toFloat(), known / total.toFloat())
        }
    }
}

@Composable
fun LearnScreen(nav: (Screen) -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val examLevel by a.prefs.examLevel.state.collectAsState()
    var level by rememberSaveable { mutableIntStateOf(examLevel) }
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        TitleBar("Learn")
        Text(
            "Light bar: started. Strong bar: known. Tap anything to look inside.",
            style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        )
        Gap(16.dp)
        Label("Kana")
        Gap(8.dp)
        val h = remember(states) { a.study.count(a.study.ids(Kind.HIRA)) }
        val k = remember(states) { a.study.count(a.study.ids(Kind.KATA)) }
        val kw = remember(states) { a.study.count(a.study.ids(Kind.KWORD)) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                SectionRow("ひらがな", "${h.known}/${h.total}", h.started, h.known, h.total) { nav(Screen.KanaList(false)) }
            }
            Box(Modifier.weight(1f)) {
                SectionRow("カタカナ", "${k.known}/${k.total}", k.started, k.known, k.total) { nav(Screen.KanaList(true)) }
            }
        }
        Gap(10.dp)
        SectionRow("Katakana words", "${kw.known}/${kw.total} known", kw.started, kw.known, kw.total) { nav(Screen.KataWords) }

        Gap(24.dp)
        Label("By level")
        Gap(8.dp)
        ChoiceRow((5 downTo 1).map { it to lvl(it) }, level) { level = it }
        Gap(12.dp)
        val words = remember(states, level) { a.study.count(a.content.words.filter { it.level == level }.map { it.id }) }
        val kanji = remember(states, level) { a.study.count(a.content.kanji.filter { it.level == level }.map { it.id }) }
        val grammar = remember(states, level) { a.study.count(a.content.grammar.filter { it.level == level }.map { it.id }) }
        val doneIds = remember(done) { done.map { it.id }.toSet() }
        val readings = a.content.readings.filter { it.level == level }
        val listenings = a.content.listenings.filter { it.level == level }
        SectionRow("Words", "${words.known}/${words.total} known", words.started, words.known, words.total) { nav(Screen.Words(level)) }
        Gap(10.dp)
        SectionRow("Kanji", "${kanji.known}/${kanji.total} known", kanji.started, kanji.known, kanji.total) { nav(Screen.KanjiList(level)) }
        Gap(10.dp)
        SectionRow("Grammar", "${grammar.known}/${grammar.total} known", grammar.started, grammar.known, grammar.total) { nav(Screen.GrammarList(level)) }
        Gap(10.dp)
        val rDone = readings.count { it.id in doneIds }
        SectionRow("Reading", "$rDone/${readings.size} done", rDone, rDone, readings.size) { nav(Screen.Readings(level)) }
        Gap(10.dp)
        val lDone = listenings.count { it.id in doneIds }
        SectionRow("Listening", "$lDone/${listenings.size} done", lDone, lDone, listenings.size) { nav(Screen.Listenings(level)) }
        Gap(10.dp)
        val stories = a.content.stories.filter { it.level == level }
        val sDone = stories.count { it.id in doneIds }
        SectionRow("Stories", "$sDone/${stories.size} read", sDone, sDone, stories.size) { nav(Screen.Stories(level)) }

        Gap(24.dp)
        Label("Beyond the exam")
        Gap(8.dp)
        val p = remember(states) { a.study.count(a.study.ids(Kind.PHRASE)) }
        SectionRow("Daily life Japanese", "${a.content.phraseSets.size} topics", p.started, p.known, p.total) { nav(Screen.PhraseSets) }
        Gap(24.dp)
    }
}

// ---------------- lists ----------------

@Composable
private fun LevelTabs(level: Int, onPick: (Int) -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        ChoiceRow((5 downTo 1).map { it to lvl(it) }, level, onPick)
    }
}

@Composable
private fun StageDot(stage: Int) {
    Box(
        Modifier.size(10.dp).clip(CircleShape)
            .background(if (stage <= 0) LocalExtra.current.line else stageColor(stage)),
    )
}

@Composable
fun WordsScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val list = remember(level) { a.content.words.filter { it.level == level } }
    Column(Modifier.fillMaxSize()) {
        BackBar("Words · ${lvl(level)}", onBack)
        LevelTabs(level) { level = it }
        Text(
            "In the order you'll learn them: most useful first.",
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
        )
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(list, key = { it.id }) { w ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav(Screen.Item(w.id)) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageDot(states[w.id]?.stage ?: 0)
                    HGap(12.dp)
                    Box(Modifier.weight(0.45f)) { Ruby(w.markup, size = 20.sp, show = if ((states[w.id]?.stage ?: 0) > 0) null else true) }
                    HGap(8.dp)
                    Text(w.meaning, Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun KanjiListScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val list = remember(level) { a.content.kanji.filter { it.level == level } }
    Column(Modifier.fillMaxSize()) {
        BackBar("Kanji · ${lvl(level)}", onBack)
        LevelTabs(level) { level = it }
        LazyVerticalGrid(
            GridCells.Adaptive(64.dp), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.char }) { k ->
                val st = states[k.id]?.stage ?: 0
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.card)
                        .clickable { nav(Screen.Item(k.id)) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(k.char, fontSize = 30.sp, fontFamily = JpBig)
                    Text(k.meanings.firstOrNull() ?: "", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        color = LocalExtra.current.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 2.dp))
                    Gap(4.dp)
                    Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (st <= 0) LocalExtra.current.line else stageColor(st)))
                }
            }
        }
    }
}

@Composable
fun GrammarListScreen(startLevel: Int, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val list = remember(level) { a.content.grammar.filter { it.level == level } }
    Column(Modifier.fillMaxSize()) {
        BackBar("Grammar · ${lvl(level)}", onBack)
        LevelTabs(level) { level = it }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { g ->
                Panel(onClick = { nav(Screen.Item(g.id)) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StageDot(states[g.id]?.stage ?: 0)
                        HGap(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleMedium)
                            Text(g.meaning, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KanaListScreen(katakana: Boolean, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    val list = if (katakana) Kana.katakana else Kana.hiragana
    val groups = list.groupBy { it.group }
    Column(Modifier.fillMaxSize()) {
        BackBar(if (katakana) "Katakana" else "Hiragana", onBack)
        LazyVerticalGrid(
            GridCells.Fixed(5), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for ((g, chars) in groups) {
                item(span = { GridItemSpan(maxLineSpan) }) { Label(g, Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(chars, key = { it.id }) { k ->
                    val st = states[k.id]?.stage ?: 0
                    Column(
                        Modifier.aspectRatio(0.9f).clip(RoundedCornerShape(12.dp)).background(LocalExtra.current.card)
                            .clickable { nav(Screen.Item(k.id)) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k.kana, fontSize = if (k.kana.length > 1) 20.sp else 26.sp, fontFamily = JpBig)
                        Text(k.romaji, style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted)
                        Gap(3.dp)
                        Box(Modifier.size(width = 20.dp, height = 3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (st <= 0) LocalExtra.current.line else stageColor(st)))
                    }
                }
            }
        }
    }
}

@Composable
fun KataWordsScreen(nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackBar("Katakana words", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(a.content.kataWords, key = { it.id }) { w ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { nav(Screen.Item("kw:${w.id}")) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageDot(states["kw:${w.id}"]?.stage ?: 0)
                    HGap(12.dp)
                    Text(w.jp, fontSize = 20.sp, modifier = Modifier.weight(0.5f))
                    Text(w.en, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
fun PhraseSetsScreen(nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val states by a.study.states.collectAsState()
    Column(Modifier.fillMaxSize()) {
        BackBar("Daily life Japanese", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(a.content.phraseSets, key = { it.id }) { s ->
                val c = remember(states) { a.study.count(s.phrases.map { it.id }) }
                Panel(onClick = { nav(Screen.PhraseSetOne(s.id)) }, padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.emoji, fontSize = 26.sp)
                        HGap(12.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            Text("${s.phrases.size} phrases · ${c.known} known", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhraseSetScreen(id: String, nav: (Screen) -> Unit, onBack: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    val set = a.content.phraseSets.firstOrNull { it.id == id } ?: return
    val states by a.study.states.collectAsState()
    val notStarted = set.phrases.filter { (states[it.id]?.stage ?: 0) == 0 }
    Column(Modifier.fillMaxSize()) {
        BackBar("${set.emoji} ${set.title}", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (notStarted.isNotEmpty()) item {
                OutlinedButton(onClick = {
                    scope.launch { a.study.markStarted(notStarted.map { it.id }) }
                }, Modifier.fillMaxWidth()) { Text("Add these to my reviews") }
            }
            items(set.phrases, key = { it.id }) { p ->
                Panel(padding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Ruby(p.jp, size = 20.sp)
                            Gap(2.dp)
                            Text(p.en, style = MaterialTheme.typography.bodyLarge)
                            if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                        HGap(8.dp)
                        SpeakButton(p.jp, size = 38.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ItemScreen(id: String, onBack: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    val states by a.study.states.collectAsState()
    val st = states[id]
    Column(Modifier.fillMaxSize()) {
        BackBar(Kind.of(id).label, onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            ItemCard(id)
            Gap(24.dp)
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Label("Your progress")
                        Text(Srs.stageName(st?.stage ?: 0), style = MaterialTheme.typography.titleMedium)
                        if (st != null && st.stage > 0) {
                            Text("Right ${st.right} · Wrong ${st.wrong}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
                        }
                    }
                    if ((st?.stage ?: 0) == 0) {
                        Button(onClick = { scope.launch { a.study.markStarted(listOf(id)) } }) { Text("Start learning") }
                    } else {
                        OutlinedButton(onClick = { scope.launch { a.study.markStarted(listOf(id), again = true) } }) { Text("Learn again") }
                    }
                }
            }
            Gap(24.dp)
        }
    }
}
