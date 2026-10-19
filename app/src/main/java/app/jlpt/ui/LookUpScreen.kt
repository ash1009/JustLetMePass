package app.jlpt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.ClaudeClient
import app.jlpt.data.Found
import app.jlpt.data.Furigana
import app.jlpt.data.ReadResult
import app.jlpt.data.Reader
import app.jlpt.data.Speech
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Japanese shared from any app: shown with furigana and meanings, and the words can go straight
 * into your reviews. Works offline; with a Claude key it also gets the readings right for words
 * outside the word list and adds a translation.
 */
@Composable
fun LookUpScreen(raw: String, onBack: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    // Braces would clash with the furigana markup.
    val text = remember(raw) { raw.replace('{', '｛').replace('}', '｝').replace('|', '｜').trim() }
    val reader = remember { Reader(a.content) }
    var result by remember(text) { mutableStateOf(reader.read(text)) }
    var claudeBusy by remember(text) { mutableStateOf(false) }
    var claudeNote by remember(text) { mutableStateOf<String?>(null) }
    var showEn by remember(text) { mutableStateOf(false) }
    var meaning by remember(text) { mutableStateOf<Pair<String, String>?>(null) }
    val states by a.study.states.collectAsState()
    val hasJapanese = remember(text) { text.any { Furigana.isKanji(it) || Furigana.isKana(it) } }

    fun askClaude() {
        claudeBusy = true
        claudeNote = null
        scope.launch {
            val r = a.claude.ask(
                "You help an English speaker read Japanese. " + ClaudeClient.STYLE + " Reply with JSON only.",
                """Add furigana markup to this Japanese text without changing any other character, translate it, and list its useful words (not particles).
Reply exactly as: {"markup":"...","en":"...","words":[{"word":"dictionary form","reading":"hiragana","meaning":"short English"}]}

$text""",
                smart = false, maxTokens = 3000,
            )
            when (r) {
                is ClaudeClient.Reply.Failed -> claudeNote = r.message
                is ClaudeClient.Reply.Text -> runCatching {
                    val o = JSONObject(r.text.substring(r.text.indexOf('{'), r.text.lastIndexOf('}') + 1))
                    val ws = o.getJSONArray("words")
                    val words = (0 until ws.length()).map { i ->
                        val w = ws.getJSONObject(i)
                        val known = a.content.words.firstOrNull { it.word == w.optString("word") }
                        Found(w.optString("word"), w.optString("reading"), w.optString("meaning"), known?.id, known?.level)
                    }
                    // Keep the offline result if Claude changed the text itself.
                    val markup = o.optString("markup").takeIf { Furigana.plain(it).replace(" ", "") == text.replace(" ", "") } ?: result.markup
                    result = ReadResult(markup, words, o.optString("en"))
                }.onFailure { claudeNote = "Claude's answer could not be read. Try again." }
            }
            claudeBusy = false
        }
    }
    LaunchedEffect(text) { if (a.claude.hasKey && hasJapanese) askClaude() }

    Column(Modifier.fillMaxSize()) {
        BackBar("Read", onBack)
        CompositionLocalProvider(LocalWordLookup provides { t, reading ->
            meaning = "$t（$reading）" to (a.content.lookup(t, reading) ?: "Not in the word list.")
        }) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (!hasJapanese) {
                    Text("There's no Japanese in what you shared.", style = MaterialTheme.typography.titleMedium)
                    return@Column
                }
                Panel {
                    Ruby(result.markup, size = 20.sp, modifier = Modifier.fillMaxWidth())
                    Gap(10.dp)
                    AudioBar("lookup:${text.hashCode()}", Speech.sentences(Furigana.plain(result.markup)).map { "N" to it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (result.en.isNotBlank()) {
                        TextButton(onClick = { showEn = !showEn }) { Text(if (showEn) "Hide English" else "Show English") }
                    }
                    if (claudeBusy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        HGap(8.dp)
                        Text("Claude is reading it…", color = LocalExtra.current.muted)
                    } else if (a.claude.hasKey && result.en.isBlank()) {
                        TextButton(onClick = { askClaude() }) { Text("Read with Claude") }
                    }
                }
                claudeNote?.let { Text(it, color = LocalExtra.current.bad, style = MaterialTheme.typography.bodySmall) }
                if (showEn) Text(result.en, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                if (!a.claude.hasKey) {
                    Text(
                        "Readings come from the app's word list. Add a Claude key in Settings for every word and a translation.",
                        style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted,
                    )
                }
                Gap(16.dp)
                val newOnes = result.words.filter { it.id != null && (states[it.id]?.stage ?: 0) == 0 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Words (${result.words.size})", Modifier.weight(1f))
                    if (newOnes.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { a.study.markStarted(newOnes.mapNotNull { it.id }) } }) {
                            Text("Learn all ${newOnes.size} new")
                        }
                    }
                }
                for (w in result.words) {
                    Gap(6.dp)
                    Panel(padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Ruby(if (Furigana.hasKanji(w.text) && w.reading.isNotBlank() && w.reading != w.text) "{${w.text}|${w.reading}}" else w.text, size = 20.sp, show = true)
                                Text(w.meaning, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                w.level?.let { Text(lvl(it), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted) }
                            }
                            val id = w.id
                            when {
                                id == null -> {}
                                (states[id]?.stage ?: 0) > 0 -> Text("Learning", style = MaterialTheme.typography.labelLarge, color = LocalExtra.current.good)
                                else -> OutlinedButton(onClick = { scope.launch { a.study.markStarted(listOf(id)) } }) { Text("Learn") }
                            }
                        }
                    }
                }
                Gap(24.dp)
            }
        }
        meaning?.let { (word, m) ->
            Panel(Modifier.padding(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(word, style = MaterialTheme.typography.titleMedium)
                Text(m, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { meaning = null }) { Text("Close") }
            }
        }
    }
}
