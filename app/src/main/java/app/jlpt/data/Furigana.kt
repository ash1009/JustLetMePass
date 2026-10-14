package app.jlpt.data

/**
 * Study text marks each kanji word with its reading: {日本語|にほんご}を{話|はな}します。
 * A piece is either plain text or a kanji word with its reading.
 */
data class Piece(val text: String, val reading: String? = null)

object Furigana {
    private val group = Regex("""\{([^{}|]+)\|([^{}|]+)\}""")

    fun parse(s: String): List<Piece> {
        val out = ArrayList<Piece>()
        var last = 0
        for (m in group.findAll(s)) {
            if (m.range.first > last) out += Piece(s.substring(last, m.range.first))
            out += Piece(m.groupValues[1], m.groupValues[2])
            last = m.range.last + 1
        }
        if (last < s.length) out += Piece(s.substring(last))
        return out
    }

    /** As Japanese is normally written: 日本語を話します。 */
    fun plain(s: String): String = group.replace(s) { it.groupValues[1] }

    /** All in kana: にほんごをはなします。 */
    fun kana(s: String): String = group.replace(s) { it.groupValues[2] }

    fun isKanji(c: Char): Boolean =
        c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿' || c == '々'

    fun hasKanji(s: String): Boolean = s.any { isKanji(it) }

    fun isKana(c: Char): Boolean = c in '぀'..'ヿ' || c == 'ー'

    fun hiraganaToKatakana(s: String): String =
        buildString { for (c in s) append(if (c in 'ぁ'..'ゖ') (c + 0x60) else c) }

    fun katakanaToHiragana(s: String): String =
        buildString { for (c in s) append(if (c in 'ァ'..'ヶ') (c - 0x60) else c) }
}
