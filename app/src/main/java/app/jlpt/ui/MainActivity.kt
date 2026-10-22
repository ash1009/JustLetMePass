package app.jlpt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import app.jlpt.JlptApp

/** Every page of the app. Tabs sit at the bottom; the rest open on top and Back closes them. */
sealed interface Screen {
    data object Today : Screen
    data object Learn : Screen
    data object Practice : Screen
    data object Chat : Screen
    data class Study(val extra: Int, val weak: Boolean = false) : Screen
    data object Speed : Screen
    data class Sentences(val level: Int) : Screen
    data object Listen : Screen
    data class LookUp(val text: String) : Screen
    data object Settings : Screen
    data object Progress : Screen
    data class KanaList(val katakana: Boolean) : Screen
    data object KataWords : Screen
    data class Words(val level: Int) : Screen
    data class KanjiList(val level: Int) : Screen
    data class GrammarList(val level: Int) : Screen
    data class Item(val id: String, val list: List<String> = emptyList()) : Screen
    data class Readings(val level: Int) : Screen
    data class ReadingOne(val id: String, val list: List<String> = emptyList()) : Screen
    data class Listenings(val level: Int) : Screen
    data class ListeningOne(val id: String, val list: List<String> = emptyList()) : Screen
    data object PhraseSets : Screen
    data class PhraseSetOne(val id: String, val list: List<String> = emptyList()) : Screen
    data object FuriganaPractice : Screen
    data class MockTest(val level: Int, val weekly: Boolean = false) : Screen
    data object Plan : Screen
    data object AiReading : Screen
    data class Stories(val level: Int) : Screen
    data class StoryOne(val id: String, val list: List<String> = emptyList()) : Screen
}

private val tabs = listOf(Screen.Today, Screen.Learn, Screen.Practice, Screen.Chat)

class MainActivity : ComponentActivity() {
    companion object {
        /** A page to open from outside: shared text, or the playlist from its control. */
        val incoming = kotlinx.coroutines.flow.MutableStateFlow<Screen?>(null)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent, first = false)
    }

    private fun handle(intent: android.content.Intent?, first: Boolean) {
        intent ?: return
        val text = when (intent.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            android.content.Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(android.content.Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        when {
            !text.isNullOrBlank() -> incoming.value = Screen.LookUp(text.take(3000))
            !first && intent.getStringExtra("route") == "listen" -> incoming.value = Screen.Listen
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as JlptApp
        // For the screenshot check on the build server: open straight on a page.
        val testKey = intent?.getStringExtra("claude_key")
        if (!testKey.isNullOrBlank()) app.prefs.claudeKey.value = testKey
        if (intent?.getBooleanExtra("skip_intro", false) == true) {
            app.prefs.onboarded.value = true
            app.prefs.hiraganaKnown.value = true
        }
        val start = intent?.getStringExtra("route")
        handle(intent, first = true)
        setContent {
            val theme by app.prefs.dark.state.collectAsState()
            JlptTheme(theme) { Root(start) }
        }
    }
}

@Composable
private fun Root(start: String?) {
    val a = app()
    val onboarded by a.prefs.onboarded.state.collectAsState()
    val furigana by a.prefs.furigana.state.collectAsState()
    val states by a.study.states.collectAsState()
    val known = remember(states) { a.study.knownForFurigana() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CompositionLocalProvider(LocalFurigana provides FuriganaRule(furigana, known)) {
            if (!onboarded) {
                Box(Modifier.safeDrawingPadding()) { Onboarding() }
            } else {
                Main(start)
            }
        }
    }
}

private fun routeScreen(route: String?): Screen? = when (route) {
    "learn" -> Screen.Learn
    "practice" -> Screen.Practice
    "chat" -> Screen.Chat
    "study" -> Screen.Study(0)
    "settings" -> Screen.Settings
    "progress" -> Screen.Progress
    "kana" -> Screen.KanaList(true)
    "words" -> Screen.Words(5)
    "kanji" -> Screen.KanjiList(5)
    "kanji1" -> Screen.Item("c:語")
    "grammar" -> Screen.GrammarList(4)
    "grammar1" -> Screen.Item("g4-001")
    "reading" -> Screen.ReadingOne("r4-01")
    "listening" -> Screen.ListeningOne("l4-04")
    "daily" -> Screen.PhraseSetOne("d01")
    "furigana" -> Screen.FuriganaPractice
    "test" -> Screen.MockTest(4)
    "plan" -> Screen.Plan
    "katawords" -> Screen.KataWords
    "listen" -> Screen.Listen
    "speed" -> Screen.Speed
    "sentences" -> Screen.Sentences(4)
    "weak" -> Screen.Study(0, weak = true)
    "lookup" -> Screen.LookUp("昨日、友達と新しいラーメン屋に行きました。とてもおいしかったです。")
    "stories" -> Screen.Stories(4)
    "story" -> Screen.StoryOne("s4-001")
    else -> null
}

@Composable
private fun Main(start: String?) {
    var tab by rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(routeScreen(start)?.let { tabs.indexOf(it) }?.takeIf { it >= 0 } ?: 0)
    }
    val incoming by MainActivity.incoming.collectAsState()
    val stack = remember {
        mutableStateListOf<Screen>().apply {
            when (val s = routeScreen(start)) {
                null -> {}
                in tabs -> {}
                else -> add(s)
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(incoming) {
        incoming?.let { stack.add(it); MainActivity.incoming.value = null }
    }
    var chatSeed by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    val nav: (Screen) -> Unit = { s ->
        val i = tabs.indexOf(s)
        if (i >= 0) { stack.clear(); tab = i } else stack.add(s)
    }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    val explainInChat: (String) -> Unit = { text ->
        chatSeed = text
        stack.clear()
        tab = 3
    }

    BackHandler(enabled = stack.isNotEmpty() || tab != 0) {
        if (stack.isNotEmpty()) back() else tab = 0
    }

    val top = stack.lastOrNull()
    if (top != null) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Page(top, nav, back, explainInChat) { s -> stack[stack.lastIndex] = s }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).statusBarsPadding().swipeNav(
            onPrev = if (tab > 0) ({ tab-- }) else null,
            onNext = if (tab < tabs.lastIndex) ({ tab++ }) else null,
        )) {
            when (tab) {
                0 -> TodayScreen(nav)
                1 -> LearnScreen(nav)
                2 -> PracticeScreen(nav)
                else -> ChatScreen(seed = chatSeed, onSeedUsed = { chatSeed = null })
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            val items = listOf<Triple<String, ImageVector, Int>>(
                Triple("Today", Icons.Rounded.WbSunny, 0),
                Triple("Learn", Icons.Rounded.AutoStories, 1),
                Triple("Practice", Icons.Rounded.EditNote, 2),
                Triple("Chat", Icons.AutoMirrored.Rounded.Chat, 3),
            )
            for ((label, icon, i) in items) {
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(icon, null) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
        }
    }
}

@Composable
private fun Page(s: Screen, nav: (Screen) -> Unit, back: () -> Unit, explainInChat: (String) -> Unit, replace: (Screen) -> Unit) {
    val a = app()
    when (s) {
        is Screen.Study -> StudyScreen(s.extra, onClose = back, onExplainInChat = explainInChat, weak = s.weak)
        Screen.Speed -> SpeedScreen(back)
        is Screen.Sentences -> SentencesScreen(s.level, back)
        Screen.Listen -> ListenScreen(back)
        is Screen.LookUp -> LookUpScreen(s.text, back)
        Screen.Settings -> SettingsScreen(back)
        Screen.Progress -> ProgressScreen(back)
        is Screen.KanaList -> KanaListScreen(s.katakana, nav, back)
        Screen.KataWords -> KataWordsScreen(nav, back)
        is Screen.Words -> WordsScreen(s.level, nav, back)
        is Screen.KanjiList -> KanjiListScreen(s.level, nav, back)
        is Screen.GrammarList -> GrammarListScreen(s.level, nav, back)
        is Screen.Item -> SwipePages(s.list, s.id, { replace(s.copy(id = it)) }) { id ->
            ItemScreen(id, back, pos = s.list.indexOf(id).takeIf { it >= 0 }?.let { "${it + 1} / ${s.list.size}" })
        }
        is Screen.Readings -> ReadingsScreen(s.level, nav, back)
        is Screen.ReadingOne -> {
            val ids = s.list.ifEmpty { a.content.readings.let { all -> val l = all.firstOrNull { it.id == s.id }?.level; all.filter { it.level == l }.map { it.id } } }
            SwipePages(ids, s.id, { replace(s.copy(id = it, list = ids)) }) { id -> ReadingScreen(id, back, explainInChat) { replace(s.copy(id = it, list = ids)) } }
        }
        is Screen.Listenings -> ListeningsScreen(s.level, nav, back)
        is Screen.ListeningOne -> {
            val ids = s.list.ifEmpty { a.content.listenings.let { all -> val l = all.firstOrNull { it.id == s.id }?.level; all.filter { it.level == l }.map { it.id } } }
            SwipePages(ids, s.id, { replace(s.copy(id = it, list = ids)) }) { id -> ListeningScreen(id, back, explainInChat) { replace(s.copy(id = it, list = ids)) } }
        }
        Screen.PhraseSets -> PhraseSetsScreen(nav, back)
        is Screen.PhraseSetOne -> {
            val ids = s.list.ifEmpty { a.content.phraseSets.map { it.id } }
            SwipePages(ids, s.id, { replace(s.copy(id = it, list = ids)) }) { id -> PhraseSetScreen(id, nav, back) }
        }
        Screen.FuriganaPractice -> FuriganaPracticeScreen(back)
        is Screen.MockTest -> MockTestScreen(s.level, back, covered = s.weekly)
        Screen.Plan -> PlanScreen(nav, back)
        Screen.AiReading -> AiReadingScreen(back, explainInChat)
        is Screen.Stories -> StoriesScreen(s.level, nav, back)
        is Screen.StoryOne -> {
            val ids = s.list.ifEmpty { a.content.stories.let { all -> val l = all.firstOrNull { it.id == s.id }?.level; all.filter { it.level == l }.map { it.id } } }
            SwipePages(ids, s.id, { replace(s.copy(id = it, list = ids)) }) { id -> StoryScreen(id, back, { replace(s.copy(id = it, list = ids)) }, explainInChat) }
        }
        else -> {}
    }
}
