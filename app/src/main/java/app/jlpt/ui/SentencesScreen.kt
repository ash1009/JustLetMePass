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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.ExerciseDone
import app.jlpt.data.StarQ
import kotlinx.coroutines.launch

private const val ROUND = 10

/**
 * JLPT ★ sentence building: four pieces, four blanks, one marked ★. Pick the piece that goes in ★.
 * Ones you have not done come first; ones you got wrong come back.
 */
@Composable
fun SentencesScreen(startLevel: Int, onBack: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    var level by rememberSaveable { mutableIntStateOf(startLevel) }
    val done by remember { a.db.dao().exercisesFlow() }.collectAsState(initial = emptyList())
    var round by remember { mutableIntStateOf(0) }
    val list = remember(level, round) {
        val byId = done.associateBy { it.id }
        val all = a.content.sentences.filter { it.level == level }
        val fresh = all.filter { it.id !in byId }.shuffled()
        val missed = all.filter { byId[it.id]?.score == 0 }.shuffled()
        val rest = all.filter { byId[it.id]?.score == 1 }.shuffled()
        (fresh + missed + rest).take(ROUND)
    }
    var i by remember(level, round) { mutableIntStateOf(0) }
    var right by remember(level, round) { mutableIntStateOf(0) }
    var chosen by remember(level, round, i) { mutableStateOf<Int?>(null) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    StudyClock(lastTouch, 120)

    Column(Modifier.fillMaxSize()) {
        BackBar("Sentence building ★", onBack, actions = {
            if (list.isNotEmpty() && i < list.size) Text("${i + 1}/${list.size}", Modifier.padding(end = 16.dp), color = LocalExtra.current.muted)
        })
        Box(Modifier.padding(horizontal = 16.dp)) {
            ChoiceRow((5 downTo 1).map { it to "${lvl(it)} · ${a.content.sentences.count { s -> s.level == it }}" }, level) { level = it }
        }
        when {
            list.isEmpty() -> Text("No questions at this level yet.", Modifier.padding(24.dp))
            i >= list.size -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$right of ${list.size} right", style = MaterialTheme.typography.headlineMedium)
                Gap(8.dp)
                val all = a.content.sentences.filter { it.level == level }
                val ok = all.count { s -> done.any { it.id == s.id && it.score == 1 } }
                Text("$ok of ${all.size} at ${lvl(level)} solved so far", color = LocalExtra.current.muted)
                Gap(24.dp)
                Button(onClick = { round++ }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Another $ROUND") }
                Gap(8.dp)
                OutlinedButton(onClick = onBack, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Done") }
            }
            else -> {
                val q = list[i]
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Gap(12.dp)
                    Text("Which piece goes in ★?", style = MaterialTheme.typography.titleMedium, color = LocalExtra.current.muted)
                    Gap(16.dp)
                    Ruby(blanks(q, chosen != null), size = 22.sp, modifier = Modifier.fillMaxWidth())
                    Gap(20.dp)
                    val ex = LocalExtra.current
                    q.pieces.forEachIndexed { pi, piece ->
                        val answered = chosen != null
                        val isRight = pi == q.answer
                        val bg = when { !answered -> ex.card; isRight -> ex.goodSoft; pi == chosen -> ex.badSoft; else -> ex.card }
                        val border = when { !answered -> ex.line; isRight -> ex.good; pi == chosen -> ex.bad; else -> ex.line }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                                .border(1.dp, border, RoundedCornerShape(14.dp))
                                .clickable(enabled = !answered) {
                                    chosen = pi
                                    lastTouch = System.currentTimeMillis()
                                    val good = pi == q.answer
                                    if (good) right++
                                    scope.launch { a.db.dao().putExercise(ExerciseDone(q.id, if (good) 1 else 0, 1, System.currentTimeMillis())) }
                                }
                                .heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${pi + 1}", style = MaterialTheme.typography.labelLarge, color = ex.muted)
                            HGap(12.dp)
                            Ruby(piece, size = 20.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    if (chosen != null) {
                        Gap(16.dp)
                        Text(if (chosen == q.answer) "Yes!" else "Not quite", style = MaterialTheme.typography.titleLarge,
                            color = if (chosen == q.answer) ex.good else ex.bad)
                        Gap(8.dp)
                        Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                            Label("Right order")
                            Gap(4.dp)
                            Text(q.order.joinToString("  →  ") { "${it + 1}" }, style = MaterialTheme.typography.titleMedium)
                            Gap(8.dp)
                            Ruby(q.full, size = 20.sp, show = true)
                            Gap(4.dp)
                            Translation(q.en)
                            if (q.why.isNotBlank()) {
                                Gap(6.dp)
                                Ruby(q.why, size = 14.sp, color = ex.muted)
                            }
                            Gap(6.dp)
                            SpeakButton(q.full, size = 38.dp)
                        }
                    }
                    Gap(24.dp)
                }
                if (chosen != null) {
                    Button(
                        onClick = { a.speech.stop(); i++; lastTouch = System.currentTimeMillis() },
                        Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 54.dp),
                    ) { Text("Next", fontSize = 17.sp) }
                }
            }
        }
    }
}

/** The sentence with four blanks, ★ in one; filled in once answered. */
private fun blanks(q: StarQ, filled: Boolean): String {
    val slots = (0 until 4).joinToString(" ") { b ->
        when {
            filled -> if (b == q.star) "【${q.pieces[q.order[b]]}】" else q.pieces[q.order[b]]
            b == q.star -> "＿★＿"
            else -> "＿＿＿"
        }
    }
    return "${q.before} $slots ${q.after}".trim()
}
