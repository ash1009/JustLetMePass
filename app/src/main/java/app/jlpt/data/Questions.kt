package app.jlpt.data

import kotlin.random.Random

/**
 * One multiple choice question. Japanese text uses the {kanji|reading} markup.
 * [promptFurigana] false hides the readings on the prompt (for reading practice).
 */
data class Question(
    val itemId: String,
    val ask: String,
    val prompt: String,
    val promptBig: Boolean,
    val promptFurigana: Boolean = true,
    val promptEnglish: Boolean = false,
    val audio: String? = null,
    val audioOnly: Boolean = false,
    val options: List<String>,
    val optionsEnglish: Boolean,
    val answer: Int,
    /** Shown after answering: the item in full. */
    val revealJp: String,
    val revealEn: String,
    val revealNote: String = "",
    /** Which way the item was asked (meaning, reading, the reverse...), for "known every way". */
    val way: Int = 0,
)

class QuestionMaker(private val content: Content) {
    private val rnd = Random(System.nanoTime())

    private val stopWords = setOf(
        "to", "a", "an", "the", "of", "be", "one", "s", "in", "on", "at", "for", "and", "or", "with", "by", "as",
        "is", "are", "it", "this", "that", "something", "someone", "somebody", "etc", "eg", "e", "g", "ie", "very",
        "you", "your", "i", "my", "me", "we", "please", "not", "no", "get", "go", "have", "do", "make", "up", "out",
    )

    private fun contentWords(s: String): Set<String> =
        s.lowercase().split(Regex("[^a-z']+")).map { it.trim('\'') }.filter { it.length > 1 && it !in stopWords }.toSet()

    /**
     * True when two English meanings share enough words that a learner could fairly pick either:
     * such an option must never sit next to the right answer as a "wrong" one.
     */
    private fun similar(a: String, b: String, share: Double = 0.01): Boolean {
        val x = contentWords(a)
        val y = contentWords(b)
        if (x.isEmpty() || y.isEmpty()) return a.trim().equals(b.trim(), ignoreCase = true)
        val both = x.intersect(y).size
        return both > 0 && both.toDouble() / minOf(x.size, y.size) >= share
    }

    /** Wrong options, each different from the answer and from each other; [ok] drops any that could also be right. */
    private fun <T> pickOthers(pool: List<T>, n: Int, key: (T) -> String, exclude: Set<String>, ok: (T) -> Boolean = { true }): List<T> {
        val out = ArrayList<T>()
        val used = HashSet(exclude)
        var tries = 0
        while (out.size < n && tries < 200 && pool.isNotEmpty()) {
            tries++
            val c = pool[rnd.nextInt(pool.size)]
            val k = key(c)
            if (k.isBlank() || k in used || !ok(c)) continue
            used += k
            out += c
        }
        return out
    }

    /** Puts the right answer among the wrong ones at a random place. */
    private fun shuffle(right: String, wrong: List<String>): Pair<List<String>, Int> {
        val all = (wrong + right).shuffled(rnd)
        return all to all.indexOf(right)
    }

    private fun firstMeaning(m: String): String = m.split(';').first().trim()

    private var lastType = 0
    private var need: Set<Int> = emptySet()

    /** Picks a way of asking, preferring one the item still needs. */
    private fun choose(options: List<Int>): Int = options.filter { it in need }.randomOrNull(rnd) ?: options[rnd.nextInt(options.size)]

    /** [missing] are the ways this item has not been answered right yet; they are asked first. */
    fun make(id: String, stage: Int, audioOk: Boolean, romaji: Boolean, missing: Set<Int> = emptySet()): Question? {
        need = missing
        lastType = 0
        return makeAny(id, stage, audioOk, romaji)?.let { it.copy(way = lastType) }
    }

    private fun makeAny(id: String, stage: Int, audioOk: Boolean, romaji: Boolean): Question? = when (Kind.of(id)) {
        Kind.HIRA, Kind.KATA -> kana(id, stage, audioOk)
        Kind.KWORD -> kataWord(id, stage, audioOk)
        Kind.WORD -> word(id, stage, audioOk)
        Kind.KANJI -> kanji(id, stage)
        Kind.GRAMMAR -> grammar(id, stage)
        Kind.PHRASE -> phrase(id, stage, audioOk)
    }

    private fun kana(id: String, stage: Int, audioOk: Boolean): Question? {
        val k = Kana.byId[id] ?: return null
        val pool = if (k.katakana) Kana.katakana else Kana.hiragana
        val same = pool.filter { it.group == k.group }.ifEmpty { pool }
        val others = pickOthers(same.ifEmpty { pool }, 3, { it.romaji }, setOf(k.romaji))
        val type = (if (stage < 2) 0 else choose(if (audioOk) listOf(0, 1, 2) else listOf(0, 1))).also { lastType = it }
        return when (type) {
            0 -> {
                val (opts, ans) = shuffle(k.romaji, others.map { it.romaji })
                Question(id, "How do you read this?", k.kana, true, options = opts, optionsEnglish = true, answer = ans,
                    revealJp = k.kana, revealEn = k.romaji, audio = k.kana)
            }
            1 -> {
                val (opts, ans) = shuffle(k.kana, others.map { it.kana })
                Question(id, "Which one is \"${k.romaji}\"?", k.romaji, true, promptEnglish = true, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = k.kana, revealEn = k.romaji, audio = k.kana)
            }
            else -> {
                val (opts, ans) = shuffle(k.kana, others.map { it.kana })
                Question(id, "Which sound did you hear?", "", false, audio = k.kana, audioOnly = true, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = k.kana, revealEn = k.romaji)
            }
        }
    }

    private fun kataWord(id: String, stage: Int, audioOk: Boolean): Question? {
        val w = content.kataById[id.removePrefix("kw:")] ?: return null
        val type = (if (stage < 2 || !audioOk) 0 else choose(listOf(0, 1))).also { lastType = it }
        val others = pickOthers(content.kataWords, 3, { if (type == 0) it.en else it.jp }, setOf(w.en, w.jp)) { !similar(it.en, w.en) }
        val note = if (w.from.isNotBlank() && !w.from.equals(w.en, true)) "From: ${w.from}" else ""
        return if (type == 0) {
            val (opts, ans) = shuffle(w.en, others.map { it.en })
            Question(id, "Read it. What does it mean?", w.jp, true, options = opts, optionsEnglish = true, answer = ans,
                revealJp = w.jp, revealEn = w.en, revealNote = note, audio = w.jp)
        } else {
            val (opts, ans) = shuffle(w.jp, others.map { it.jp })
            Question(id, "Which word did you hear?", "", false, audio = w.jp, audioOnly = true, options = opts,
                optionsEnglish = false, answer = ans, revealJp = w.jp, revealEn = w.en, revealNote = note)
        }
    }

    private fun sameLevelWords(w: Word): List<Word> {
        val all = content.words
        val near = all.filter { it.level == w.level && it.id != w.id }
        return near.ifEmpty { all }
    }

    private fun word(id: String, stage: Int, audioOk: Boolean): Question? {
        val w = content.wordById[id] ?: return null
        val meaning = firstMeaning(w.meaning)
        val types = buildList {
            add(0)                       // Japanese to meaning
            if (stage >= 2) add(1)       // meaning to Japanese
            if (stage >= 1 && w.hasKanji) { add(2); add(2) }   // how do you read it (furigana practice)
            if (stage >= 2 && audioOk) add(3)                    // listen
        }
        val type = (if (stage == 0) 0 else choose(types)).also { lastType = it }
        val note = if (w.exampleJp.isNotBlank()) w.exampleJp else ""
        val reveal = Triple(w.markup, w.meaning, note)
        return when (type) {
            1 -> {
                val others = pickOthers(sameLevelWords(w), 3, { it.word }, setOf(w.word)) { !similar(it.meaning, w.meaning) && it.reading != w.reading }
                val (opts, ans) = shuffle(w.markup, others.map { it.markup })
                Question(id, "Which one means this?", meaning, false, promptEnglish = true, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = reveal.first, revealEn = reveal.second,
                    revealNote = reveal.third, audio = w.reading)
            }
            2 -> {
                val pool = sameLevelWords(w).filter { it.reading != w.reading && kotlin.math.abs(it.reading.length - w.reading.length) <= 1 }
                // Another reading of the same written word would also be right, so leave those out.
                val sameWritten = content.words.filter { it.word == w.word }.map { it.reading }.toSet()
                val wrong = pickOthers(pool.ifEmpty { sameLevelWords(w) }, 3, { it.reading }, setOf(w.reading) + sameWritten).map { it.reading }.toMutableList()
                // A near miss is the best test: add or drop a small っ or a long vowel.
                nearMiss(w.reading)?.let { if (it !in wrong && it != w.reading && it !in sameWritten && wrong.isNotEmpty()) wrong[0] = it }
                val (opts, ans) = shuffle(w.reading, wrong)
                Question(id, "How do you read this word?", w.word, true, promptFurigana = false, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = reveal.first, revealEn = reveal.second,
                    revealNote = reveal.third, audio = w.reading)
            }
            3 -> {
                val others = pickOthers(sameLevelWords(w), 3, { firstMeaning(it.meaning) }, setOf(meaning)) { !similar(it.meaning, w.meaning) && it.reading != w.reading }
                val (opts, ans) = shuffle(meaning, others.map { firstMeaning(it.meaning) })
                Question(id, "What did you hear?", "", false, audio = w.reading, audioOnly = true, options = opts,
                    optionsEnglish = true, answer = ans, revealJp = reveal.first, revealEn = reveal.second, revealNote = reveal.third)
            }
            else -> {
                val others = pickOthers(sameLevelWords(w), 3, { firstMeaning(it.meaning) }, setOf(meaning)) { !similar(it.meaning, w.meaning) }
                val (opts, ans) = shuffle(meaning, others.map { firstMeaning(it.meaning) })
                Question(id, "What does this mean?", w.markup, true, options = opts, optionsEnglish = true, answer = ans,
                    revealJp = reveal.first, revealEn = reveal.second, revealNote = reveal.third, audio = w.reading)
            }
        }
    }

    /** A reading that is easy to confuse with the real one. */
    private fun nearMiss(r: String): String? {
        val swaps = listOf("っ" to "", "ゅう" to "ゅ", "ょう" to "ょ", "おう" to "お", "えい" to "え", "い" to "いい")
        for ((a, b) in swaps.shuffled(rnd)) {
            if (a.isNotEmpty() && r.contains(a)) return r.replaceFirst(a, b)
        }
        val fixes = listOf("ゅ" to "ゅう", "ょ" to "ょう")
        for ((a, b) in fixes) if (r.contains(a) && !r.contains(b)) return r.replaceFirst(a, b)
        return null
    }

    private fun kanji(id: String, stage: Int): Question? {
        val k = content.kanjiByChar[id.removePrefix("c:")] ?: return null
        val pool = content.kanji.filter { it.level == k.level && it.char != k.char }.ifEmpty { content.kanji }
        val meaning = k.meanings.firstOrNull() ?: return null
        val readings = (k.kun.take(2) + k.on.take(2)).joinToString("・")
        val reveal = "${k.meanings.joinToString(", ")}"
        val note = buildString {
            if (k.on.isNotEmpty()) append("On: ${k.on.joinToString("、")}  ")
            if (k.kun.isNotEmpty()) append("Kun: ${k.kun.joinToString("、")}")
        }
        val type = (if (stage < 2) 0 else choose(listOf(0, 1, 2))).also { lastType = it }
        return when (type) {
            1 -> {
                val right = k.on.firstOrNull() ?: k.kun.firstOrNull()?.substringBefore('(') ?: return kanji(id, 0)
                // None of this kanji's own readings may show up as a "wrong" option.
                val own = (k.on + k.kun.map { it.substringBefore('(') }).toSet()
                val wrong = pickOthers(pool, 3, { it.on.firstOrNull() ?: "" }, own).mapNotNull { it.on.firstOrNull() }
                if (wrong.size < 3) return kanji(id, 0)
                val (opts, ans) = shuffle(right, wrong)
                Question(id, "Which is a reading of this kanji?", k.char, true, promptFurigana = false, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = k.char, revealEn = reveal, revealNote = note)
            }
            2 -> {
                val others = pickOthers(pool, 3, { it.char }, setOf(k.char)) { o -> o.meanings.none { m -> k.meanings.any { similar(it, m) } } }
                val (opts, ans) = shuffle(k.char, others.map { it.char })
                Question(id, "Which kanji means \"$meaning\"?", meaning, false, promptEnglish = true, options = opts,
                    optionsEnglish = false, answer = ans, revealJp = k.char, revealEn = reveal, revealNote = note)
            }
            else -> {
                val others = pickOthers(pool, 3, { it.meanings.firstOrNull() ?: "" }, setOf(meaning)) { o -> o.meanings.none { m -> k.meanings.any { similar(it, m) } } }
                val (opts, ans) = shuffle(meaning, others.mapNotNull { it.meanings.firstOrNull() })
                Question(id, "What does this kanji mean?", k.char, true, options = opts, optionsEnglish = true, answer = ans,
                    revealJp = k.char, revealEn = reveal, revealNote = note.ifEmpty { readings })
            }
        }
    }

    private fun grammar(id: String, stage: Int): Question? {
        val g = content.grammarById[id] ?: return null
        if (g.quiz.isEmpty()) return null
        val q = g.quiz[(stage + rnd.nextInt(g.quiz.size)) % g.quiz.size]
        // Shuffle the options so the place of the right answer is not remembered.
        val right = q.options[q.answer]
        val (opts, ans) = shuffle(right, q.options.filterIndexed { i, _ -> i != q.answer })
        return Question(id, "Fill the gap", q.q, false, options = opts, optionsEnglish = false,
            answer = ans, revealJp = q.q.replace("＿＿", right), revealEn = q.en,
            revealNote = q.why.ifBlank { "${g.title}: ${g.meaning}" }, audio = Furigana.plain(q.q.replace("＿＿", right)))
    }

    private fun phrase(id: String, stage: Int, audioOk: Boolean): Question? {
        val p = content.phraseById[id] ?: return null
        val set = content.phraseSets.firstOrNull { s -> s.phrases.any { it.id == id } }
        val pool = (set?.phrases ?: emptyList()) + content.phraseSets.flatMap { it.phrases }.shuffled(rnd).take(30)
        val type = (if (stage < 2) 0 else choose(listOf(0, 1))).also { lastType = it }
        return if (type == 0) {
            val others = pickOthers(pool, 3, { it.en }, setOf(p.en)) { !similar(it.en, p.en, 0.6) }
            val (opts, ans) = shuffle(p.en, others.map { it.en })
            Question(id, "What does this mean?", p.jp, false, options = opts, optionsEnglish = true, answer = ans,
                revealJp = p.jp, revealEn = p.en, revealNote = p.note, audio = Furigana.plain(p.jp))
        } else {
            val others = pickOthers(pool, 3, { it.jp }, setOf(p.jp)) { !similar(it.en, p.en, 0.6) }
            val (opts, ans) = shuffle(p.jp, others.map { it.jp })
            Question(id, "How would you say this?", p.en, false, promptEnglish = true, options = opts,
                optionsEnglish = false, answer = ans, revealJp = p.jp, revealEn = p.en, revealNote = p.note,
                audio = Furigana.plain(p.jp))
        }
    }
}
