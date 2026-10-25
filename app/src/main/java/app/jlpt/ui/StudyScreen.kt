package app.jlpt.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.ClaudeClient
import app.jlpt.data.Furigana
import app.jlpt.data.Question
import app.jlpt.data.Step
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Counts study time from your taps: the gap between one tap and the next, up to [capSeconds].
 * Leaving the screen open without doing anything adds nothing, and time with the app in the
 * background never counts.
 */
@Composable
fun StudyClock(lastTouch: Long, capSeconds: Int = 60) {
    val a = app()
    val prev = remember { longArrayOf(System.currentTimeMillis()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> prev[0] = 0L
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (prev[0] == 0L) prev[0] = System.currentTimeMillis()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(lastTouch) {
        val p = prev[0]
        if (p > 0 && lastTouch > p) {
            a.study.addSeconds((((lastTouch - p) + 500) / 1000).toInt().coerceAtMost(capSeconds))
        }
        prev[0] = lastTouch
    }
}

@Composable
fun StudyScreen(extraNew: Int, onClose: () -> Unit, onExplainInChat: (String) -> Unit, weak: Boolean = false) {
    val a = app()
    val scope = rememberCoroutineScope()
    val steps = remember { mutableStateListOf<Step>() }
    var loading by remember { mutableStateOf(true) }
    var index by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    val met = remember { mutableStateListOf<String>() }
    val retried = remember { HashSet<String>() }
    val countedNew = remember { HashSet<String>() }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val seenWeak = remember { HashSet<String>() }
    var round by remember { mutableIntStateOf(0) }

    StudyClock(lastTouch, 60)

    LaunchedEffect(round) {
        loading = true
        val list = if (weak) a.study.buildWeakRound(seenWeak) else if (round == 0) a.study.buildRound(extraNew) else a.study.buildRound(more = true)
        if (weak) seenWeak += list.map { it.id }
        steps.clear(); steps.addAll(list)
        index = 0
        loading = false
    }

    DisposableEffect(Unit) { onDispose { a.speech.stop() } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Stop") }
            Meter(if (steps.isEmpty()) 0f else index / steps.size.toFloat(), Modifier.weight(1f))
            HGap(10.dp)
            Text("${minOf(index + 1, steps.size)}/${steps.size}", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.muted)
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            steps.isEmpty() && weak -> WeakEmpty(onClose)
            steps.isEmpty() -> NothingDue(onMore = { round++ }, onClose = onClose)
            index >= steps.size -> RoundDone(asked, right, met.size, onMore = { round++ }, onClose = onClose)
            else -> {
                val step = steps[index]
                val next: () -> Unit = {
                    a.speech.stop()
                    lastTouch = System.currentTimeMillis()
                    index++
                }
                when (step) {
                    is Step.Meet -> MeetStep(step.id) {
                        if (step.id !in met) met += step.id
                        next()
                    }
                    is Step.Ask -> AskStep(
                        key = "$index-${step.id}",
                        id = step.id,
                        onAnswered = { correct, way ->
                            lastTouch = System.currentTimeMillis()
                            asked++
                            if (correct) right++
                            val wasNew = a.study.stage(step.id) == 0 && countedNew.add(step.id)
                            scope.launch { a.study.answer(step.id, correct, wasNew, way, step.practice) }
                            // Ask again a few cards later, once.
                            if (!correct && retried.add(step.id)) {
                                val at = minOf(steps.size, index + 4)
                                steps.add(at, Step.Ask(step.id, practice = true))
                            }
                        },
                        onNext = next,
                        onExplainInChat = onExplainInChat,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetStep(id: String, onNext: () -> Unit) {
    val a = app()
    LaunchedEffect(id) {
        if (a.prefs.autoPlay.value) {
            delay(300)
            sayFor(a, id)?.let { a.speech.say(it) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("New", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge) }
            Gap()
            ItemCard(id)
        }
        Button(onClick = onNext, enabled = readyToTap(id), modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().heightIn(min = 54.dp)) {
            Text("Got it", fontSize = 17.sp)
        }
    }
}

/** What to say out loud for an item. */
private fun sayFor(a: app.jlpt.JlptApp, id: String): String? = when (app.jlpt.data.Kind.of(id)) {
    app.jlpt.data.Kind.HIRA, app.jlpt.data.Kind.KATA -> app.jlpt.data.Kana.byId[id]?.kana
    app.jlpt.data.Kind.KWORD -> a.content.kataById[id.removePrefix("kw:")]?.jp
    app.jlpt.data.Kind.WORD -> a.content.wordById[id]?.reading
    app.jlpt.data.Kind.KANJI -> null
    app.jlpt.data.Kind.GRAMMAR -> a.content.grammarById[id]?.examples?.firstOrNull()?.jp
    app.jlpt.data.Kind.PHRASE -> a.content.phraseById[id]?.jp
}

@Composable
private fun AskStep(
    key: String,
    id: String,
    onAnswered: (Boolean, Int) -> Unit,
    onNext: () -> Unit,
    onExplainInChat: (String) -> Unit,
) {
    val a = app()
    val scope = rememberCoroutineScope()
    val q: Question? = remember(key) {
        a.questions.make(id, a.study.stage(id), a.speech.ready, a.prefs.showRomaji.value, a.study.missingWays(id))
    }
    if (q == null) {
        LaunchedEffect(key) { onNext() }
        return
    }
    var chosen by remember(key) { mutableIntStateOf(-1) }
    var explain by remember(key) { mutableStateOf<String?>(null) }
    var explaining by remember(key) { mutableStateOf(false) }
    val answered = chosen >= 0
    val tapOk = readyToTap(key)
    val ex = LocalExtra.current

    // Right answers move on by themselves after a moment (long enough to hear the word);
    // wrong ones wait for Next so there is time to look.
    var moved by remember(key) { mutableStateOf(false) }
    LaunchedEffect(chosen) {
        if (chosen >= 0 && chosen == q.answer) {
            delay(if (a.prefs.autoPlay.value && q.audio != null && !q.audioOnly) 1700 else 1100)
            if (!moved) { moved = true; onNext() }
        }
    }

    LaunchedEffect(key) {
        if (q.audioOnly && q.audio != null) {
            delay(250)
            a.speech.say(q.audio)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Gap(8.dp)
            Text(q.ask, style = MaterialTheme.typography.titleMedium, color = ex.muted, textAlign = TextAlign.Center)
            Gap(20.dp)
            when {
                q.audioOnly -> Box(
                    Modifier.size(110.dp).clip(RoundedCornerShape(55.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { q.audio?.let { a.speech.say(it) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Headphones, "Play again", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                q.promptEnglish -> Text(q.prompt, fontSize = if (q.promptBig) 34.sp else 24.sp, textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineSmall)
                else -> Ruby(
                    q.prompt,
                    size = if (q.promptBig) (if (Furigana.plain(q.prompt).length <= 2) 72.sp else 44.sp) else 24.sp,
                    show = if (!q.promptFurigana) false else null,
                    center = true,
                    family = if (q.promptBig) JpBig else null,
                )
            }
            if (q.audioOnly) {
                Gap(6.dp)
                TextButton(onClick = { q.audio?.let { a.speech.say(it, slow = true) } }) { Text("Play slowly") }
            }
            Gap(24.dp)
            q.options.forEachIndexed { i, opt ->
                val isRight = i == q.answer
                val bg = when {
                    !answered -> ex.card
                    isRight -> ex.goodSoft
                    i == chosen -> ex.badSoft
                    else -> ex.card
                }
                val border = when {
                    !answered -> ex.line
                    isRight -> ex.good
                    i == chosen -> ex.bad
                    else -> ex.line
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bg)
                        .border(if (answered && (isRight || i == chosen)) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                        .clickable(enabled = !answered && tapOk) {
                            chosen = i
                            onAnswered(i == q.answer, q.way)
                            if (a.prefs.autoPlay.value && q.audio != null && !q.audioOnly) a.speech.say(q.audio)
                        }
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (q.optionsEnglish) Text(opt, fontSize = 18.sp, textAlign = TextAlign.Center)
                    else Ruby(opt, size = 22.sp, center = true, show = if (q.promptFurigana) null else false)
                }
            }
            AnimatedVisibility(answered) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    val good = chosen == q.answer
                    Text(
                        if (good) listOf("Nice!", "Correct!", "That's it!", "すごい!", "Yes!").random() else "Not quite",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (good) ex.good else ex.bad,
                    )
                    Gap(8.dp)
                    Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Ruby(q.revealJp, size = 22.sp, show = true)
                                Gap(4.dp)
                                Translation(q.revealEn, style = MaterialTheme.typography.bodyLarge)
                                if (q.revealNote.isNotBlank()) {
                                    Gap(8.dp)
                                    Ruby(q.revealNote, size = 15.sp, color = ex.muted)
                                }
                            }
                            val say = q.audio ?: q.revealJp
                            HGap(8.dp)
                            SpeakButton(say, size = 40.dp)
                        }
                    }
                    if (!good && a.claude.hasKey) {
                        Gap(8.dp)
                        when {
                            explaining -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                HGap(8.dp)
                                Text("Asking Claude…", color = ex.muted)
                            }
                            explain != null -> Panel(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
                                Label("Claude explains")
                                Gap(6.dp)
                                Ruby(explain!!, size = 16.sp)
                                Gap(6.dp)
                                TextButton(onClick = { onExplainInChat(explainPrompt(q, chosen)) }) { Text("Ask more in Chat") }
                            }
                            else -> OutlinedButton(onClick = {
                                explaining = true
                                scope.launch {
                                    val r = a.claude.ask(
                                        "You are a friendly Japanese tutor for an English speaker studying for the JLPT. " + ClaudeClient.STYLE,
                                        explainPrompt(q, chosen) + "\n\nExplain in 2-4 short sentences why the right answer is right and mine is wrong. Add one short example if it helps.",
                                        smart = false, maxTokens = 500,
                                    )
                                    explain = when (r) {
                                        is ClaudeClient.Reply.Text -> r.text
                                        is ClaudeClient.Reply.Failed -> r.message
                                    }
                                    explaining = false
                                }
                            }) { Text("Why? Ask Claude") }
                        }
                    }
                    Gap(8.dp)
                }
            }
        }
        // Right answers move on by themselves, so there is no button to tap by mistake.
        if (answered && chosen != q.answer) {
            Button(
                onClick = { if (!moved) { moved = true; onNext() } },
                enabled = readyToTap(key),
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().heightIn(min = 54.dp),
            ) {
                Text("Next", fontSize = 17.sp)
            }
        }
    }
}

private fun explainPrompt(q: Question, chosen: Int): String = buildString {
    append("I got this practice question wrong.\n")
    append("Question: ${q.ask}\n")
    if (q.prompt.isNotBlank()) append("Shown: ${Furigana.plain(q.prompt)}\n")
    if (q.audioOnly) append("(It was a listening question; the audio said: ${q.audio})\n")
    append("Options: ${q.options.joinToString(" / ") { Furigana.plain(it) }}\n")
    append("I chose: ${Furigana.plain(q.options.getOrElse(chosen) { "" })}\n")
    append("Right answer: ${Furigana.plain(q.options[q.answer])}\n")
    append("Full answer: ${Furigana.plain(q.revealJp)} = ${q.revealEn}")
}

@Composable
private fun WeakEmpty(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("💪", fontSize = 56.sp)
        Gap()
        Text("No weak spots right now", style = MaterialTheme.typography.headlineSmall)
        Gap(8.dp)
        Text("Things you get wrong show up here, so you can drill them.", textAlign = TextAlign.Center, color = LocalExtra.current.muted)
        Gap(24.dp)
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Back") }
    }
}

@Composable
private fun NothingDue(onMore: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🎉", fontSize = 56.sp)
        Gap()
        Text("All caught up", style = MaterialTheme.typography.headlineSmall)
        Gap(8.dp)
        Text(
            "No reviews are due and today's new items are done. Learn a few extra if you like.",
            textAlign = TextAlign.Center, color = LocalExtra.current.muted,
        )
        Gap(24.dp)
        Button(onClick = onMore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Learn 5 more") }
        Gap(8.dp)
        TextButton(onClick = onClose) { Text("Back to Today") }
    }
}

@Composable
private fun RoundDone(asked: Int, right: Int, met: Int, onMore: () -> Unit, onClose: () -> Unit) {
    val pct = if (asked == 0) 0 else right * 100 / asked
    val line = when {
        asked == 0 -> "Done!"
        pct >= 90 -> "Brilliant. The exam paper on the icon stopped crying."
        pct >= 70 -> "Good round. Keep it up!"
        pct >= 50 -> "Getting there. The tricky ones will come back soon."
        else -> "Tough one! That's how learning feels. They'll come back."
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Round done", style = MaterialTheme.typography.headlineMedium)
        Gap(8.dp)
        Text(line, textAlign = TextAlign.Center, color = LocalExtra.current.muted)
        Gap(24.dp)
        Panel {
            StatRow("$asked" to "answered", "$pct%" to "right", "$met" to "new")
        }
        Gap(24.dp)
        Button(onClick = onMore, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Keep going") }
        Gap(8.dp)
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done for now") }
    }
}


/** False for the first half second of a new card, so a quick tap meant for the last one does nothing. */
@Composable
private fun readyToTap(key: Any): Boolean {
    var ready by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { delay(500); ready = true }
    return ready
}
