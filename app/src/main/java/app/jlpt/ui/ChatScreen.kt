package app.jlpt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import app.jlpt.JlptApp
import app.jlpt.data.ChatMsg
import app.jlpt.data.ClaudeClient
import app.jlpt.data.Furigana
import app.jlpt.data.Kind
import kotlinx.coroutines.launch

private val starters = listOf(
    "What's the difference between は and が?",
    "When do I use ～てしまう?",
    "Teach me 5 useful words for shopping",
    "Quiz me on the て-form",
    "How do I say \"I'd like to go to Kyoto next year\"?",
    "Make a short dialogue at a café, at my level",
)

private fun systemPrompt(a: JlptApp): String {
    val st = a.study.states.value.values
    val words = st.count { Kind.of(it.id) == Kind.WORD && it.stage > 0 }
    val kanji = st.count { Kind.of(it.id) == Kind.KANJI && it.stage > 0 }
    val grammar = st.count { Kind.of(it.id) == Kind.GRAMMAR && it.stage > 0 }
    val hard = st.filter { it.wrong > 0 }.sortedByDescending { it.wrong - it.right / 3 }.take(12).mapNotNull { s ->
        when (Kind.of(s.id)) {
            Kind.WORD -> a.content.wordById[s.id]?.let { "${it.word} (${it.meaning.split(';').first()})" }
            Kind.KANJI -> s.id.removePrefix("c:")
            Kind.GRAMMAR -> a.content.grammarById[s.id]?.title
            Kind.KATA, Kind.HIRA -> s.id.substringAfter(':')
            else -> null
        }
    }
    val level = a.prefs.examLevel.value
    return buildString {
        append("You are a friendly, patient Japanese tutor inside a study app. ")
        append("The learner speaks English, is studying for the JLPT N$level on ${a.prefs.exam()}, ")
        append("and also wants Japanese for everyday life. ")
        append(if (a.prefs.hiraganaKnown.value) "They can read hiragana. " else "They are still learning hiragana. ")
        val kata = a.study.count(a.study.ids(Kind.KATA))
        if (kata.known < kata.total) append("They are still learning katakana. ")
        append("So far they have started $words words, $kanji kanji and $grammar grammar points. ")
        if (hard.isNotEmpty()) append("Things they often get wrong: ${hard.joinToString(", ")}. ")
        append("Keep answers short and clear: a few sentences or a short list. ")
        append("Use example sentences at N$level level or easier, with an English translation. ")
        append("If they ask to be quizzed, ask one question at a time and wait for their answer. ")
        append(ClaudeClient.STYLE)
    }
}

@Composable
fun ChatScreen(seed: String?, onSeedUsed: () -> Unit) {
    val a = app()
    val scope = rememberCoroutineScope()
    val msgs by remember { a.db.dao().chatFlow() }.collectAsState(initial = emptyList())
    val key by a.prefs.claudeKey.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        input = ""
        error = null
        busy = true
        scope.launch {
            a.db.dao().addChat(ChatMsg(role = "user", text = t, time = System.currentTimeMillis()))
            val history = a.db.dao().chat().takeLast(20).map { ClaudeClient.Turn(it.role, it.text) }
                .dropWhile { it.role != "user" }
            when (val r = a.claude.chat(systemPrompt(a), history)) {
                is ClaudeClient.Reply.Text -> a.db.dao().addChat(ChatMsg(role = "assistant", text = r.text, time = System.currentTimeMillis()))
                is ClaudeClient.Reply.Failed -> error = r.message
            }
            busy = false
        }
    }

    LaunchedEffect(seed, key) {
        if (seed != null && key.isNotBlank()) {
            onSeedUsed()
            send(seed)
        }
    }
    LaunchedEffect(msgs.size, busy) {
        if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size + if (busy) 1 else 0)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TitleBar("Chat", actions = {
            if (msgs.isNotEmpty()) TextButton(onClick = { scope.launch { a.db.dao().clearChat() } }) { Text("New chat") }
        })
        if (key.isBlank()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💬", fontSize = 48.sp)
                Gap()
                Text("Ask anything about Japanese", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Gap(8.dp)
                Text(
                    "Chat uses Claude with your own API key. Add it in Settings, under Claude. The rest of the app works without it.",
                    textAlign = TextAlign.Center, color = LocalExtra.current.muted,
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.weight(1f), state = list,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (msgs.isEmpty()) {
                item {
                    Text("Ask anything. Claude knows your level and what you find hard.", color = LocalExtra.current.muted)
                    Gap(12.dp)
                    for (s in starters) {
                        Panel(onClick = { send(s) }, padding = PaddingValues(12.dp)) { Ruby(s, size = 15.sp) }
                        Gap(8.dp)
                    }
                }
            }
            items(msgs, key = { it.id }) { m -> Bubble(m) }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    HGap(8.dp)
                    Text("Thinking…", color = LocalExtra.current.muted)
                }
            }
            error?.let { e -> item { Text(e, color = LocalExtra.current.bad) } }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                placeholder = { Text("Ask about Japanese…") },
                modifier = Modifier.weight(1f), maxLines = 5,
                shape = RoundedCornerShape(24.dp),
            )
            HGap(8.dp)
            IconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !busy) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg) {
    val mine = m.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) MaterialTheme.colorScheme.secondaryContainer else LocalExtra.current.card)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (mine) Text(m.text, fontSize = 16.sp)
            else Column {
                Ruby(cleanMarkdown(m.text), size = 16.sp)
                if (Furigana.hasKanji(Furigana.plain(m.text))) {
                    Row(Modifier.padding(top = 4.dp)) {
                        SpeakButton(japaneseOnly(m.text), size = 30.dp)
                    }
                }
            }
        }
    }
}

/** Claude sometimes uses **bold**; the screen shows plain text. */
private fun cleanMarkdown(s: String): String = s.replace("**", "").replace(Regex("(?m)^#+\\s*"), "")

/** Just the Japanese parts, for reading out loud. */
private fun japaneseOnly(s: String): String =
    Furigana.plain(s).split('\n').filter { line -> line.count { it.code >= 0x3040 } > line.length / 3 }.joinToString("。")
