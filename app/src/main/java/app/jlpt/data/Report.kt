package app.jlpt.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A plain-text summary of progress, to copy or share (for example to ask Claude about it). */
suspend fun Study.progressReport(dao: StudyDao, prefs: Prefs, content: Content): String {
    val now = System.currentTimeMillis()
    val all = dao.allStates().filter { it.stage > 0 }
    val hour = 3_600_000L
    fun inTime(due: Long): String {
        val w = due - now
        return when {
            w <= 0 -> "due now"
            w < hour -> "in ${w / 60_000 + 1} min"
            w < 48 * hour -> "in ${w / hour + 1} h"
            else -> "in ${w / (24 * hour) + 1} days"
        }
    }
    fun name(id: String): String = when (Kind.of(id)) {
        Kind.WORD -> content.wordById[id]?.let { "${it.word} (${it.reading})" } ?: id
        Kind.KANJI -> id.removePrefix("c:")
        Kind.GRAMMAR -> content.grammarById[id]?.title ?: id
        else -> id
    }
    val day = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK).format(Date(now))
    val plan = plan()
    return buildString {
        appendLine("Just Let me Pass This: my progress ($day)")
        appendLine("Exam: ${prefs.examLevel.value.let { "N$it" }}, ${plan.daysLeft} days left. Working on N${currentLevel()}. New kanji/words open up to N${openLevel()}.")
        appendLine("Plan: ${plan.newPerDay} new a day. Reviews due now: ${dueIds().size}. Spacing: ${prefs.spacing.value}.")
        appendLine()
        appendLine("Started items by stage (1 to 4 learning, 5+ known):")
        for (k in Kind.values()) {
            val mine = all.filter { Kind.of(it.id) == k }
            if (mine.isEmpty()) continue
            val byStage = (1..Srs.MAX).map { s -> mine.count { it.stage == s } }
            appendLine("  ${k.label}: ${mine.size} started, ${mine.count { it.stage >= Srs.KNOWN }} known | " +
                byStage.mapIndexed { i, c -> "s${i + 1}=$c" }.joinToString(" "))
        }
        appendLine()
        val close = all.filter { it.stage in 3 until Srs.KNOWN }.sortedBy { it.due }
        appendLine("Close to known (stage 3 or 4): ${close.size}")
        val held = all.filter { it.stage == Srs.KNOWN - 1 && missingWays(it.id).isNotEmpty() }
        appendLine("Held at stage 4 only because a way is missing: ${held.size}")
        for (st in close.take(40)) {
            val miss = missingWays(st.id).sorted().map { wayName(st.id, it) }
            appendLine("  ${name(st.id)} | stage ${st.stage} | next ${inTime(st.due)} | right ${st.right} wrong ${st.wrong}" +
                if (miss.isNotEmpty()) " | still needs: ${miss.joinToString(", ")}" else "")
        }
        appendLine()
        val dueSoon = listOf("now" to 0L, "next 8 h" to 8 * hour, "next 24 h" to 24 * hour, "next 3 days" to 72 * hour)
        appendLine("Reviews coming: " + dueSoon.joinToString(", ") { (label, w) -> "$label ${all.count { it.due <= now + w }}" })
        val wrongOften = all.filter { it.wrong > 0 }.sortedByDescending { it.wrong.toDouble() / (it.right + 1) }.take(10)
        if (wrongOften.isNotEmpty()) appendLine("Most missed: " + wrongOften.joinToString(", ") { "${name(it.id)} (${it.wrong} wrong)" })
        appendLine()
        appendLine("Last 7 days (minutes, answers, % right, new):")
        for (d in dao.days().take(7)) {
            val pct = if (d.answers == 0) 0 else d.correct * 100 / d.answers
            appendLine("  ${d.day}: ${d.seconds / 60} min, ${d.answers} answers, $pct%, ${d.newItems} new")
        }
        val tests = dao.tests().take(5)
        if (tests.isNotEmpty()) {
            appendLine()
            appendLine("Recent mocks:")
            for (t in tests) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date(t.time))
                appendLine("  $date N${t.level}: ${t.score}/${t.total} | ${t.sections.split(';').filter { '=' in it }.joinToString(" ")}")
            }
        }
    }
}
