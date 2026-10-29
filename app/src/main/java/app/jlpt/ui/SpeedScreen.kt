package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.Furigana
import app.jlpt.data.Kind
import app.jlpt.data.Question
import kotlinx.coroutines.delay

private const val SECONDS = 60

/**
 * 60 seconds, as many right answers as you can, from things you have already met.
 * Practice only: it does not move anything in your reviews.
 */
@Composable
fun SpeedScreen(onBack: () -> Unit) {
    val a = app()
    var running by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var start by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var right by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val misses = remember { mutableStateListOf<Question>() }
    var q by remember { mutableStateOf<Question?>(null) }
    var flash by remember { mutableStateOf<Int?>(null) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 30)

    val pool = remember {
        a.study.startedIds().filter { Kind.of(it) != Kind.GRAMMAR }.ifEmpty { a.study.ids(Kind.KATA).take(46) }
    }
    fun nextQuestion() {
        var tries = 0
        var made: Question? = null
        while (made == null && tries < 10) {
            val id = pool.random()
            made = a.questions.make(id, 1 + (0..3).random(), false, false)?.takeIf { !it.audioOnly }
            tries++
        }
        q = made
    }

    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            if (now - start >= SECONDS * 1000L) {
                running = false
                finished = true
                if (right > a.prefs.speedBest.value) a.prefs.speedBest.value = right
            }
            delay(200)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Speed round", onBack, actions = {
            if (running) {
                val left = (SECONDS - (now - start) / 1000).coerceAtLeast(0)
                Text("${left}s  ·  $right", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 16.dp))
            }
        })
        when {
            finished -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("$right right", fontSize = 48.sp, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "out of $total in $SECONDS seconds · best ${a.prefs.speedBest.value}",
                    color = LocalExtra.current.muted,
                )
                if (misses.isNotEmpty()) {
                    Gap(20.dp)
                    Label("Missed")
                    for (m in misses) {
                        Gap(8.dp)
                        Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                            Ruby(m.revealJp, size = 20.sp, show = true)
                            Translation(m.revealEn)
                        }
                    }
                }
                Gap(24.dp)
                Button(onClick = {
                    right = 0; total = 0; misses.clear(); finished = false
                    start = System.currentTimeMillis(); now = start; running = true; nextQuestion()
                }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Go again") }
                Gap(8.dp)
                OutlinedButton(onClick = onBack, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
            }
            !running -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("⚡", fontSize = 56.sp)
                Gap()
                Text("60 seconds. Go fast.", style = MaterialTheme.typography.headlineSmall)
                Gap(8.dp)
                Text(
                    "Words, kanji and kana you've already met. Just practice: your reviews don't change. Best: ${a.prefs.speedBest.value}",
                    textAlign = TextAlign.Center, color = LocalExtra.current.muted,
                )
                Gap(24.dp)
                Button(onClick = {
                    start = System.currentTimeMillis(); now = start; running = true; nextQuestion()
                }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start", fontSize = 18.sp) }
            }
            else -> {
                val cur = q
                if (cur == null) {
                    Text("Nothing to practise yet. Do a study round first.", Modifier.padding(24.dp))
                    return@Column
                }
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Meter(1f - (now - start) / (SECONDS * 1000f), height = 6.dp)
                    Gap(16.dp)
                    Text(cur.ask, color = LocalExtra.current.muted, style = MaterialTheme.typography.titleMedium)
                    Gap(16.dp)
                    if (cur.promptEnglish) Text(cur.prompt, fontSize = 26.sp, textAlign = TextAlign.Center)
                    else Ruby(
                        cur.prompt, size = if (Furigana.plain(cur.prompt).length <= 2) 64.sp else 36.sp,
                        show = if (cur.promptShowReading) true else if (!cur.promptFurigana) false else null, center = true, family = JpBig,
                    )
                    Gap(20.dp)
                    val ex = LocalExtra.current
                    cur.options.forEachIndexed { i, opt ->
                        val shown = flash != null
                        val bg = when { !shown -> ex.card; i == cur.answer -> ex.goodSoft; i == flash -> ex.badSoft; else -> ex.card }
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                                .border(1.dp, ex.line, RoundedCornerShape(14.dp))
                                .clickable(enabled = !shown) {
                                    lastTouch = System.currentTimeMillis()
                                    total++
                                    if (i == cur.answer) right++ else misses += cur
                                    flash = i
                                }
                                .heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cur.optionsEnglish) Text(opt, fontSize = 18.sp, textAlign = TextAlign.Center)
                            else Ruby(opt, size = 22.sp, center = true, show = if (cur.promptFurigana) null else false)
                        }
                    }
                }
                LaunchedEffect(flash) {
                    if (flash != null) {
                        delay(if (flash == cur.answer) 250 else 700)
                        flash = null
                        nextQuestion()
                    }
                }
            }
        }
    }
}
