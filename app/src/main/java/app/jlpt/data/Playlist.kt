package app.jlpt.data

/** One thing said in the audio playlist, with what to show on screen while it plays. */
data class Clip(val speaker: String, val jp: String, val en: String)

/** Builds the background audio: Japanese only, from what you are learning. */
object Playlist {
    val kinds = listOf(
        "words" to "My words",
        "sentences" to "Example sentences",
        "stories" to "Stories",
        "dialogues" to "Dialogues",
        "phrases" to "Daily phrases",
    )

    fun build(kind: String, content: Content, study: Study, level: Int): List<Clip> {
        val states = study.states.value
        fun started(id: String) = (states[id]?.stage ?: 0) > 0
        return when (kind) {
            "sentences" -> {
                val words = content.words.filter { started(it.id) && it.exampleJp.isNotBlank() }
                    .map { Clip("N", it.exampleJp, it.exampleEn) }
                val grammar = content.grammar.filter { started(it.id) }.flatMap { g -> g.examples.map { Clip("N", it.jp, it.en) } }
                (words + grammar).ifEmpty {
                    content.words.filter { it.level == level && it.exampleJp.isNotBlank() }.take(80).map { Clip("N", it.exampleJp, it.exampleEn) }
                }.shuffled().take(150)
            }
            "stories" -> content.stories.filter { it.level == level }.flatMap { st ->
                listOf(Clip("N", st.title, st.titleEn)) + st.paragraphs.flatMap { p ->
                    Speech.sentences(p.jp).map { Clip("N", it, p.en) }
                }
            }
            "dialogues" -> content.listenings.filter { it.level == level }.flatMap { l ->
                buildList {
                    if (l.setup.isNotBlank()) add(Clip("N", l.setup, l.title))
                    for (line in l.lines) add(Clip(line.speaker, line.jp, l.en))
                }
            }
            "phrases" -> content.phraseSets.flatMap { s -> s.phrases.map { Clip("N", it.jp, it.en) } }.shuffled().take(150)
            else -> {
                // A word, then the word in a sentence.
                val words = content.words.filter { started(it.id) }.ifEmpty { content.words.filter { it.level == level }.take(60) }
                words.shuffled().take(80).flatMap { w ->
                    buildList {
                        add(Clip("N", w.markup, w.meaning))
                        if (w.exampleJp.isNotBlank()) add(Clip("N", w.exampleJp, w.exampleEn))
                    }
                }
            }
        }
    }
}
