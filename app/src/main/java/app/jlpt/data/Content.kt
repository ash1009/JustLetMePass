package app.jlpt.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Word(
    val id: String, val word: String, val reading: String, val meaning: String, val level: Int,
    val exampleJp: String, val exampleEn: String,
) {
    val hasKanji: Boolean get() = Furigana.hasKanji(word)
    /** The word with its reading, in the study markup. */
    val markup: String get() = if (hasKanji && word != reading) "{$word|$reading}" else word
}

data class Kanji(
    val char: String, val level: Int, val strokes: Int, val meanings: List<String>,
    val on: List<String>, val kun: List<String>,
) { val id: String get() = "c:$char" }

data class Example(val jp: String, val en: String)

data class Quiz(val q: String, val options: List<String>, val answer: Int, val en: String, val qEn: String = "", val why: String = "")

data class Grammar(
    val id: String, val level: Int, val title: String, val meaning: String, val structure: String,
    val explain: String, val examples: List<Example>, val quiz: List<Quiz>,
)

data class Reading(val id: String, val level: Int, val title: String, val kind: String, val text: String, val en: String, val questions: List<Quiz>)

data class Line(val speaker: String, val jp: String)

data class Listening(
    val id: String, val level: Int, val title: String, val kind: String, val setup: String,
    val lines: List<Line>, val quiz: Quiz, val en: String,
)

data class Phrase(val id: String, val jp: String, val en: String, val note: String)
data class PhraseSet(val id: String, val title: String, val emoji: String, val phrases: List<Phrase>)

data class Paragraph(val jp: String, val en: String)

data class Story(
    val id: String, val level: Int, val title: String, val titleEn: String, val genre: String,
    val paragraphs: List<Paragraph>, val questions: List<Quiz>,
) {
    /** Length in characters, as normally written. */
    val length: Int get() = paragraphs.sumOf { Furigana.plain(it.jp).length }
}

/** A JLPT ★ question: four pieces go in the blanks between [before] and [after]. */
data class StarQ(
    val id: String, val level: Int, val before: String, val after: String,
    val pieces: List<String>, val order: List<Int>, val star: Int, val en: String, val why: String,
) {
    val answer: Int get() = order[star]
    val full: String get() = before + order.joinToString("") { pieces[it] } + after
}

data class KataWord(val id: String, val jp: String, val en: String, val from: String)

/** Everything to study, read once from the files inside the app. */
class Content(private val context: Context) {

    private fun array(name: String): JSONArray =
        JSONArray(context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() })

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    private fun JSONObject.str(k: String) = optString(k, "")

    private fun quiz(o: JSONObject) = Quiz(
        q = o.str("q").ifEmpty { o.str("question") },
        options = o.getJSONArray("options").strings(),
        answer = o.getInt("answer"),
        en = o.str("en"), qEn = o.str("q_en"), why = o.str("why"),
    )

    val words: List<Word> by lazy {
        val a = array("vocab.json")
        (0 until a.length()).map {
            val r = a.getJSONArray(it)
            Word(r.getString(0), r.getString(1), r.getString(2), r.getString(3), r.getInt(4), r.getString(5), r.getString(6))
        }
    }
    val wordById: Map<String, Word> by lazy { words.associateBy { it.id } }
    /** For fading furigana: a written word to its entry. */
    val wordBySurface: Map<String, Word> by lazy {
        words.filter { it.hasKanji }.groupBy { it.word }.mapValues { it.value.first() }
    }

    val kanji: List<Kanji> by lazy {
        array("kanji.json").objects().map {
            Kanji(it.str("c"), it.getInt("l"), it.getInt("s"), it.getJSONArray("m").strings(),
                it.getJSONArray("on").strings(), it.getJSONArray("kun").strings())
        }
    }
    val kanjiByChar: Map<String, Kanji> by lazy { kanji.associateBy { it.char } }

    private val strokesJson: JSONObject by lazy {
        JSONObject(context.assets.open("strokes.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    fun strokes(char: String): List<String> =
        strokesJson.optJSONArray(char)?.strings() ?: emptyList()

    val grammar: List<Grammar> by lazy {
        array("grammar.json").objects().map { o ->
            Grammar(
                o.str("id"), o.getInt("level"), o.str("title_raw"), o.str("meaning"), o.str("structure"), o.str("explain"),
                o.getJSONArray("examples").objects().map { Example(it.str("jp"), it.str("en")) },
                o.getJSONArray("quiz").objects().map { quiz(it) },
            )
        }
    }
    val grammarById: Map<String, Grammar> by lazy { grammar.associateBy { it.id } }

    val readings: List<Reading> by lazy {
        array("reading.json").objects().map { o ->
            Reading(o.str("id"), o.getInt("level"), o.str("title_raw"), o.str("kind"), o.str("text"), o.str("en"),
                o.getJSONArray("questions").objects().map { quiz(it) })
        }
    }

    val listenings: List<Listening> by lazy {
        array("listening.json").objects().map { o ->
            Listening(
                o.str("id"), o.getInt("level"), o.str("title_raw"), o.str("kind"), o.str("setup"),
                o.getJSONArray("lines").objects().map { Line(it.str("s"), it.str("jp")) },
                Quiz(o.str("question"), o.getJSONArray("options").strings(), o.getInt("answer"), o.str("en"), o.str("q_en"), o.str("why")),
                o.str("en"),
            )
        }
    }

    val phraseSets: List<PhraseSet> by lazy {
        array("daily.json").objects().map { o ->
            PhraseSet(o.str("id"), o.str("title_raw"), o.str("emoji_raw"),
                o.getJSONArray("phrases").objects().map { Phrase(it.str("id"), it.str("jp"), it.str("en"), it.str("note")) })
        }
    }
    val phraseById: Map<String, Phrase> by lazy { phraseSets.flatMap { it.phrases }.associateBy { it.id } }

    val stories: List<Story> by lazy {
        array("stories.json").objects().map { o ->
            Story(
                o.str("id"), o.getInt("level"), o.str("title"), o.str("title_en"), o.str("genre_raw"),
                o.getJSONArray("paragraphs").objects().map { Paragraph(it.str("jp"), it.str("en")) },
                o.getJSONArray("questions").objects().map { quiz(it) },
            )
        }
    }

    /** What a written word means, for looking it up while reading. */
    fun lookup(text: String, reading: String): String? {
        wordBySurface[text]?.let { return "${it.reading} · ${it.meaning}" }
        // A verb or adjective written without its ending: 食 → 食べる.
        words.firstOrNull { it.word.startsWith(text) && it.word.drop(text.length).all { c -> Furigana.isKana(c) } && it.reading.startsWith(reading) }
            ?.let { return "${it.word} (${it.reading}) · ${it.meaning}" }
        if (text.length == 1) kanjiByChar[text]?.let { return "Kanji: ${it.meanings.joinToString(", ")}" }
        val parts = text.mapNotNull { kanjiByChar[it.toString()]?.meanings?.firstOrNull()?.let { m -> "$it $m" } }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    val sentences: List<StarQ> by lazy {
        runCatching {
            array("sentences.json").objects().map { o ->
                val order = o.getJSONArray("order")
                StarQ(
                    o.str("id"), o.getInt("level"), o.str("before"), o.str("after"),
                    o.getJSONArray("pieces").strings(), (0 until order.length()).map { order.getInt(it) },
                    o.getInt("star"), o.str("en"), o.str("why"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Full mock papers in the real JLPT format, used only in mock tests and weekly checks. */
    /** Which words use each reading of a kanji, and a tip (from notes.json; missing levels show nothing). */
    data class ReadingWords(val reading: String, val type: String, val words: List<Triple<String, String, Int>>)
    data class KanjiNote(val readings: List<ReadingWords>, val tip: String)

    val kanjiNotes: Map<String, KanjiNote> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("notes.json").bufferedReader().readText())
            val k = o.getJSONArray("kanji").objects()
            k.associate { n ->
                n.getString("kanji") to KanjiNote(
                    n.getJSONArray("readings").objects().map { r ->
                        ReadingWords(r.optString("r"), r.optString("type"),
                            r.getJSONArray("words").objects().map { w -> Triple(w.optString("w"), w.optString("en"), w.optInt("lvl")) })
                    },
                    n.optString("tip"),
                )
            }
        }.getOrDefault(emptyMap())
    }

    /** A short way to remember each word (by word id). */
    val wordHooks: Map<String, String> by lazy {
        runCatching {
            val o = JSONObject(context.assets.open("notes.json").bufferedReader().readText())
            o.getJSONArray("words").objects().associate { it.getString("id") to it.getString("hook") }
        }.getOrDefault(emptyMap())
    }

    val papers: List<JSONObject> by lazy {
        runCatching { array("mocks.json").objects() }.getOrDefault(emptyList())
    }

    private val allKataWords: List<KataWord> by lazy {
        array("katakana.json").objects().map { KataWord(it.str("id"), it.str("jp"), it.str("en"), it.str("from_raw")) }
    }

    /** Katakana words that are also in the JLPT word lists live there only; this maps them to that word. */
    val kataInWords: Map<String, String> by lazy {
        val byText = HashMap<String, String>()
        for (w in words) { byText.putIfAbsent(w.word, w.id); byText.putIfAbsent(w.reading, w.id) }
        allKataWords.mapNotNull { k -> byText[k.jp]?.let { "kw:${k.id}" to it } }.toMap()
    }

    /** Katakana words not found anywhere in the JLPT word lists. */
    val kataWords: List<KataWord> by lazy { allKataWords.filter { "kw:${it.id}" !in kataInWords } }
    val kataById: Map<String, KataWord> by lazy { kataWords.associateBy { it.id } }

    /** Reads every file now, so the first study screen opens without a pause. */
    fun warmUp() {
        words; wordBySurface; kanji; grammar; phraseSets; kataWords; readings; listenings
    }
}
