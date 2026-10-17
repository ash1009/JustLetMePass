package app.jlpt.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** One step of a study round: meet something new, or answer a question about it. */
sealed interface Step {
    val id: String
    data class Meet(override val id: String) : Step
    data class Ask(override val id: String) : Step
}

data class Plan(
    val newPerDay: Int,
    val daysLeft: Long,
    val examLeft: Int,
    val examTotal: Int,
    /** True when the daily time is too short to meet everything before the exam. */
    val tight: Boolean,
)

/** Keeps track of what is known and decides what to study next. */
class Study(private val content: Content, private val dao: StudyDao, private val prefs: Prefs) {

    private val _states = MutableStateFlow<Map<String, ItemState>>(emptyMap())
    val states: StateFlow<Map<String, ItemState>> get() = _states
    private val lock = Mutex()
    private var loaded = false

    suspend fun load() = lock.withLock {
        if (!loaded) {
            _states.value = dao.allStates().associateBy { it.id }
            loaded = true
        }
    }

    fun stage(id: String): Int = _states.value[id]?.stage ?: 0

    fun today(): String = LocalDate.now().toString()

    // ---- the tracks, in learning order ----

    fun ids(kind: Kind): List<String> = when (kind) {
        Kind.HIRA -> Kana.hiragana.map { it.id }
        Kind.KATA -> Kana.katakana.map { it.id }
        Kind.KWORD -> content.kataWords.map { "kw:${it.id}" }
        Kind.WORD -> content.words.map { it.id }
        Kind.KANJI -> content.kanji.map { it.id }
        Kind.GRAMMAR -> content.grammar.map { it.id }
        Kind.PHRASE -> content.phraseSets.flatMap { s -> s.phrases.map { it.id } }
    }

    fun level(id: String): Int = when (Kind.of(id)) {
        Kind.WORD -> content.wordById[id]?.level ?: 5
        Kind.KANJI -> content.kanjiByChar[id.removePrefix("c:")]?.level ?: 5
        Kind.GRAMMAR -> content.grammarById[id]?.level ?: 5
        else -> 5
    }

    private fun katakanaBasicsStarted(): Boolean =
        Kana.katakana.filter { it.group == "Basic" }.all { stage(it.id) > 0 }

    /** Everything up to the exam level (N5 counts towards N4 and so on). */
    private fun examScope(): List<String> {
        val lvl = prefs.examLevel.value
        val out = ArrayList<String>()
        if (!prefs.hiraganaKnown.value) out += ids(Kind.HIRA)
        out += ids(Kind.KATA)
        out += content.words.filter { it.level >= lvl }.map { it.id }
        out += content.kanji.filter { it.level >= lvl }.map { it.id }
        out += content.grammar.filter { it.level >= lvl }.map { it.id }
        return out
    }

    fun plan(): Plan {
        val scope = examScope()
        val left = scope.count { stage(it) == 0 }
        val days = ChronoUnit.DAYS.between(LocalDate.now(), prefs.exam())
        val minutes = prefs.dailyMinutes.value
        val byTime = (minutes * 0.6).toInt().coerceAtLeast(5)
        // Keep the last week before the exam for reviews only.
        val studyDays = (days - 7).coerceAtLeast(1)
        val byExam = if (days <= 0 || left == 0) byTime / 2 else ceil(left / studyDays.toDouble()).toInt()
        val set = prefs.newPerDay.value
        val target = if (set > 0) set else byExam.coerceIn(5, byTime)
        return Plan(target, days, left, scope.size, tight = days > 0 && byExam > byTime)
    }

    // ---- building a round ----

    fun dueIds(now: Long = System.currentTimeMillis()): List<String> =
        _states.value.values.filter { it.stage > 0 && it.due <= now }.sortedBy { it.due }.map { it.id }

    suspend fun newToday(): Int = dao.day(today())?.newItems ?: 0

    /** The next items to meet, mixed across words, kanji, grammar, katakana and daily phrases. */
    fun pickNew(n: Int): List<String> {
        if (n <= 0) return emptyList()
        val queues = HashMap<Kind, ArrayDeque<String>>()
        fun queue(kind: Kind): ArrayDeque<String> = queues.getOrPut(kind) {
            ArrayDeque(ids(kind).filter { stage(it) == 0 })
        }
        val hira = !prefs.hiraganaKnown.value && queue(Kind.HIRA).isNotEmpty()
        val kata = queue(Kind.KATA).isNotEmpty()
        val pattern = when {
            hira -> listOf(Kind.HIRA, Kind.HIRA, Kind.WORD, Kind.HIRA, Kind.PHRASE)
            kata -> listOf(Kind.KATA, Kind.WORD, Kind.KATA, Kind.KANJI, Kind.KATA, Kind.GRAMMAR, Kind.WORD, Kind.PHRASE)
            else -> listOf(Kind.WORD, Kind.KANJI, Kind.WORD, Kind.GRAMMAR, Kind.WORD, Kind.PHRASE, Kind.KWORD, Kind.WORD, Kind.KANJI, Kind.GRAMMAR)
        }
        val allowed = { k: Kind ->
            when (k) {
                Kind.PHRASE -> prefs.includeDaily.value
                Kind.KWORD -> katakanaBasicsStarted()
                else -> true
            }
        }
        val out = ArrayList<String>()
        var i = 0
        var misses = 0
        while (out.size < n && misses < pattern.size * 2) {
            val k = pattern[i % pattern.size]
            i++
            val q = if (allowed(k)) queue(k) else null
            if (q == null || q.isEmpty()) {
                // Fall back to words, the biggest track.
                val w = queue(Kind.WORD)
                if (w.isEmpty()) { misses++; continue }
                out += w.removeFirst()
            } else {
                out += q.removeFirst()
            }
            misses = 0
        }
        return out
    }

    /**
     * A round of about 15 minutes: reviews that are due, plus a few new things, each new one
     * shown first and asked a little later.
     */
    suspend fun buildRound(extraNew: Int = 0): List<Step> {
        load()
        val due = dueIds().take(40)
        val plan = plan()
        val left = (plan.newPerDay - newToday()).coerceAtLeast(0)
        var newCount = minOf(10, left) + extraNew
        // With a big pile of reviews, catch up before adding more.
        if (dueIds().size > 80) newCount = minOf(newCount, 3 + extraNew)
        val fresh = pickNew(newCount)

        val steps = ArrayList<Step>()
        val reviews = ArrayDeque(due.shuffled())
        val pendingAsks = ArrayDeque<String>()
        // Start with a few reviews to warm up.
        repeat(minOf(3, reviews.size)) { steps += Step.Ask(reviews.removeFirst()) }
        for (id in fresh) {
            steps += Step.Meet(id)
            if (pendingAsks.isNotEmpty()) steps += Step.Ask(pendingAsks.removeFirst())
            pendingAsks += id
            if (reviews.isNotEmpty()) steps += Step.Ask(reviews.removeFirst())
        }
        while (pendingAsks.isNotEmpty()) {
            steps += Step.Ask(pendingAsks.removeFirst())
            if (reviews.isNotEmpty()) steps += Step.Ask(reviews.removeFirst())
        }
        while (reviews.isNotEmpty()) steps += Step.Ask(reviews.removeFirst())
        return steps
    }

    // ---- saving answers and time ----

    suspend fun answer(id: String, correct: Boolean, wasNew: Boolean) = lock.withLock {
        val now = System.currentTimeMillis()
        val old = _states.value[id] ?: ItemState(id, Kind.of(id).name)
        val updated = Srs.answer(old, correct, now, prefs.spacing.value)
        dao.putState(updated)
        _states.value = _states.value + (id to updated)
        val d = dao.day(today()) ?: DayLog(today())
        dao.putDay(d.copy(
            answers = d.answers + 1,
            correct = d.correct + if (correct) 1 else 0,
            newItems = d.newItems + if (wasNew) 1 else 0,
        ))
    }

    /** Marks things as already known (for example hiragana you can read). */
    suspend fun markKnown(ids: List<String>) = lock.withLock {
        val now = System.currentTimeMillis()
        val list = ids.map { id ->
            (_states.value[id] ?: ItemState(id, Kind.of(id).name)).copy(
                stage = Srs.KNOWN, due = now + 7L * 86_400_000L, firstSeen = now, lastSeen = now,
            )
        }
        dao.putStates(list)
        _states.value = _states.value + list.associateBy { it.id }
    }

    /** Adds things to the reviews now (from Learn), or puts them back to the start with [again]. */
    suspend fun markStarted(ids: List<String>, again: Boolean = false) = lock.withLock {
        val now = System.currentTimeMillis()
        val list = ids.map { id ->
            val old = _states.value[id] ?: ItemState(id, Kind.of(id).name)
            if (old.stage > 0 && !again) old
            else old.copy(stage = 1, due = now, firstSeen = if (old.firstSeen == 0L) now else old.firstSeen, lastSeen = now)
        }
        dao.putStates(list)
        _states.value = _states.value + list.associateBy { it.id }
    }

    /** Puts something back to the start, to learn it again. */
    suspend fun reset(id: String) = lock.withLock {
        val s = ItemState(id, Kind.of(id).name)
        dao.putState(s)
        _states.value = _states.value + (id to s)
    }

    suspend fun addSeconds(seconds: Int) {
        if (seconds <= 0) return
        lock.withLock {
            val d = dao.day(today()) ?: DayLog(today())
            dao.putDay(d.copy(seconds = d.seconds + seconds.coerceAtMost(600)))
        }
    }

    suspend fun resetAll() = lock.withLock {
        dao.clearStates(); dao.clearDays(); dao.clearExercises(); dao.clearTests()
        _states.value = emptyMap()
    }

    // ---- what the furigana can leave out ----

    /** Kanji and words already met at least once: their furigana stays hidden (tap to see). */
    fun knownForFurigana(): Known {
        val chars = HashSet<Char>()
        val words = HashSet<String>()
        for (s in _states.value.values) {
            if (s.stage <= 0) continue
            when {
                s.id.startsWith("c:") -> s.id.getOrNull(2)?.let { chars += it }
                s.id.startsWith("v") -> content.wordById[s.id]?.let {
                    words += it.word
                    // Also the kanji part on its own, as written before an ending: 食べる → 食.
                    words += it.word.trimEnd { c -> Furigana.isKana(c) }
                }
            }
        }
        return Known(chars, words)
    }

    // ---- counts for the screens ----

    data class Count(val total: Int, val started: Int, val known: Int)

    fun count(ids: List<String>): Count {
        val m = _states.value
        var started = 0
        var known = 0
        for (id in ids) {
            val s = m[id]?.stage ?: 0
            if (s > 0) started++
            if (s >= Srs.KNOWN) known++
        }
        return Count(ids.size, started, known)
    }

    suspend fun streak(): Int {
        val days = dao.days().associateBy { it.day }
        var d = LocalDate.now()
        var n = 0
        // Today only counts once it has some study; it does not break the streak before that.
        val todayLog = days[d.toString()]
        if (todayLog != null && todayLog.answers > 0) n++
        d = d.minusDays(1)
        while (true) {
            val log = days[d.toString()] ?: break
            if (log.answers == 0 && log.seconds < 60) break
            n++
            d = d.minusDays(1)
        }
        return n
    }
}

data class Known(val kanji: Set<Char>, val words: Set<String>) {
    /** Whether the reading over [text] can be left out. */
    fun canHide(text: String): Boolean =
        text in words || text.filter { Furigana.isKanji(it) }.let { k -> k.isNotEmpty() && k.all { it in kanji } }

    companion object { val NONE = Known(emptySet(), emptySet()) }
}
