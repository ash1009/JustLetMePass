package app.jlpt.data

enum class Kind(val label: String) {
    HIRA("Hiragana"), KATA("Katakana"), KWORD("Katakana words"), WORD("Words"),
    KANJI("Kanji"), GRAMMAR("Grammar"), PHRASE("Daily life");

    companion object {
        fun of(id: String): Kind = when {
            id.startsWith("h:") -> HIRA
            id.startsWith("k:") -> KATA
            id.startsWith("kw:") -> KWORD
            id.startsWith("c:") -> KANJI
            id.startsWith("v") -> WORD
            id.startsWith("g") -> GRAMMAR
            else -> PHRASE
        }
    }
}

/**
 * Spaced repetition. Each right answer moves an item up a stage and it comes back later;
 * a wrong answer drops it two stages so it comes back soon.
 * Stage 0 is not started yet, 5 and up counts as known, 9 is mastered.
 */
object Srs {
    private val hours = intArrayOf(0, 4, 20, 44, 92, 188, 356, 716, 1436, 2876)
    const val KNOWN = 5
    const val MAX = 9

    fun multiplier(spacing: String): Double = when (spacing) {
        "gentle" -> 0.7
        "fast" -> 1.4
        else -> 1.0
    }

    /** How long until a review at [stage]. */
    fun interval(stage: Int, spacing: String): Long = (hours[stage.coerceIn(0, MAX)] * multiplier(spacing) * 3_600_000L).toLong()

    fun answer(old: ItemState, correct: Boolean, now: Long, spacing: String): ItemState {
        val m = multiplier(spacing)
        return if (correct) {
            val stage = (old.stage + 1).coerceAtMost(MAX)
            old.copy(
                stage = stage,
                due = now + (hours[stage] * m * 3_600_000L).toLong(),
                right = old.right + 1,
                firstSeen = if (old.firstSeen == 0L) now else old.firstSeen,
                lastSeen = now,
            )
        } else {
            val stage = if (old.stage <= 2) 1 else old.stage - 2
            old.copy(
                stage = stage,
                due = now + 10 * 60_000L,
                wrong = old.wrong + 1,
                lapses = if (old.stage >= KNOWN) old.lapses + 1 else old.lapses,
                firstSeen = if (old.firstSeen == 0L) now else old.firstSeen,
                lastSeen = now,
            )
        }
    }

    fun stageName(stage: Int): String = when {
        stage <= 0 -> "New"
        stage < 3 -> "Learning"
        stage < KNOWN -> "Getting there"
        stage < MAX -> "Known"
        else -> "Mastered"
    }
}
