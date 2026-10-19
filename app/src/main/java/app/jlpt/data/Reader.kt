package app.jlpt.data

/** A word found in shared text. [id] is set when it is in the app's word list (so it can be learned). */
data class Found(val text: String, val reading: String, val meaning: String, val id: String?, val level: Int?)

data class ReadResult(val markup: String, val words: List<Found>, val en: String = "")

/**
 * Reads any Japanese text without the internet: finds the longest known word at each point and
 * adds its reading. Words written in kana are left as they are; kanji it does not know stay
 * plain (tap and hold still looks them up).
 */
class Reader(private val content: Content) {

    private data class Entry(val surface: String, val reading: String, val word: Word)

    private val entries: Map<String, List<Entry>> by lazy {
        val out = HashMap<String, MutableList<Entry>>()
        fun add(surface: String, reading: String, w: Word) {
            if (surface.isEmpty()) return
            out.getOrPut(surface) { mutableListOf() } += Entry(surface, reading, w)
        }
        for (w in content.words) {
            val word = w.word.replace("～", "")
            if (word.isEmpty()) continue
            if (Furigana.hasKanji(word)) {
                add(word, w.reading, w)
                // The kanji part on its own, so 食べた and 食べない are found from 食べる.
                val tail = word.takeLastWhile { Furigana.isKana(it) }
                val stem = word.dropLast(tail.length)
                if (tail.isNotEmpty() && stem.isNotEmpty() && w.reading.endsWith(tail)) {
                    add(stem, w.reading.dropLast(tail.length), w)
                }
            } else if (word.all { it in '゠'..'ヿ' } && word.length >= 2) {
                add(word, w.reading, w)
            }
        }
        out
    }
    private val longest: Int by lazy { entries.keys.maxOfOrNull { it.length }?.coerceAtMost(10) ?: 1 }

    fun read(text: String): ReadResult {
        val sb = StringBuilder()
        val found = LinkedHashMap<String, Found>()
        var i = 0
        while (i < text.length) {
            var hit: Entry? = null
            for (len in minOf(longest, text.length - i) downTo 1) {
                val list = entries[text.substring(i, i + len)] ?: continue
                // Lower level first: the most common reading.
                hit = list.maxByOrNull { it.word.level }
                break
            }
            if (hit == null) {
                sb.append(text[i]); i++; continue
            }
            val s = hit.surface
            if (Furigana.hasKanji(s) && hit.reading.isNotEmpty() && hit.reading != s) sb.append("{").append(s).append("|").append(hit.reading).append("}")
            else sb.append(s)
            val w = hit.word
            found.putIfAbsent(w.id, Found(w.word, w.reading, w.meaning, w.id, w.level))
            i += s.length
        }
        return ReadResult(sb.toString(), found.values.toList())
    }
}
