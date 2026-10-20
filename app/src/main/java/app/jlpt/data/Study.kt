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
    /** New exam items needed a day, and days left to meet them (a week before the exam). */
    val examPerDay: Int = 0,
    val studyDays: Long = 0,
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
        // Everything for the exam is met a week early; that last week is reviews and mocks only.
        val studyDays = (days - 7).coerceAtLeast(1)
        val byExam = if (days <= 7 || left == 0) 5 else ceil(left / studyDays.toDouble()).toInt()
        // About 1 in 5 new items is beyond the exam (daily phrases, katakana words, N3), so add room for those.
        val withExtras = ceil(byExam * 1.25).toInt()
        val set = prefs.newPerDay.value
        val target = if (set > 0) set else withExtras.coerceAtLeast(5)
        return Plan(target, days, left, scope.size, tight = days > 7 && withExtras > byTime, examPerDay = byExam, studyDays = studyDays)
    }

    // ---- building a round ----

    /** Hiragana you already read well never comes back as a review. */
    private fun skipReview(id: String) = prefs.hiraganaKnown.value && id.startsWith("h:")

    fun dueIds(now: Long = System.currentTimeMillis()): List<String> =
        _states.value.values.filter { it.stage > 0 && it.due <= now && !skipReview(it.id) }.sortedBy { it.due }.map { it.id }

    suspend fun newToday(): Int = dao.day(today())?.newItems ?: 0

    /** The next items to meet, mixed across words, kanji, grammar, katakana and daily phrases. */
    fun pickNew(n: Int, focus: Kind? = null): List<String> {
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
        }.let { p -> if (focus != null) p + listOf(focus, focus) else p }
        val allowed = { k: Kind ->
            when (k) {
                Kind.PHRASE -> prefs.includeDaily.value
                Kind.KWORD -> katakanaBasicsStarted()
                else -> true
            }
        }
        val out = ArrayList<String>()
        // Families: a kanji with words that use it, and a word's new kanji just before the word.
        fun addWord(id: String) {
            val w = content.wordById[id]
            if (w != null) {
                for (c in w.word.filter { Furigana.isKanji(it) }.toSet()) {
                    val kid = "c:$c"
                    val k = content.kanjiByChar[c.toString()] ?: continue
                    if (stage(kid) == 0 && kid !in out && k.level >= w.level && out.size < n - 1) {
                        queue(Kind.KANJI).remove(kid)
                        out += kid
                    }
                }
            }
            out += id
        }
        fun addKanji(id: String) {
            out += id
            val c = id.removePrefix("c:")
            val lvl = content.kanjiByChar[c]?.level ?: 5
            val words = queue(Kind.WORD).filter { wid ->
                content.wordById[wid]?.let { it.word.contains(c) && it.level >= lvl } == true
            }.take(2)
            for (wid in words) {
                if (out.size >= n) break
                queue(Kind.WORD).remove(wid)
                out += wid
            }
        }
        fun add(kind: Kind, id: String) = when (kind) {
            Kind.WORD -> addWord(id)
            Kind.KANJI -> addKanji(id)
            else -> { out += id }
        }
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
                addWord(w.removeFirst())
            } else {
                add(k, q.removeFirst())
            }
            misses = 0
        }
        return out.distinct().take(n)
    }

    /**
     * A round of about 15 minutes: reviews that are due, plus a few new things, each new one
     * shown first and asked a little later.
     */
    /** Things you get wrong most: the most mistakes for how often you get them right. */
    fun weakIds(n: Int = 20, skip: Set<String> = emptySet()): List<String> =
        _states.value.values
            .filter { it.stage > 0 && it.wrong > 0 && it.id !in skip && !skipReview(it.id) }
            .sortedWith(compareByDescending<ItemState> { it.wrong.toDouble() / (it.right + 1) }.thenByDescending { it.lastSeen })
            .take(n)
            .map { it.id }

    fun weakCount(): Int = _states.value.values.count { it.stage > 0 && it.wrong > 0 && !skipReview(it.id) }

    /** A round of only weak spots, hardest first, mixed a little so it does not feel the same each time. */
    suspend fun buildWeakRound(skip: Set<String> = emptySet()): List<Step> {
        load()
        return weakIds(20, skip).shuffled().map { Step.Ask(it) }
    }

    /** Everything you have started, for the speed round. */
    fun startedIds(): List<String> = _states.value.values.filter { it.stage > 0 && !skipReview(it.id) }.map { it.id }

    /** The section that went worst in the last mock at the exam level (in the past two weeks). */
    suspend fun weakSection(): Pair<String, Int>? {
        val since = System.currentTimeMillis() - 14L * 86_400_000L
        val t = dao.tests().firstOrNull { it.level == prefs.examLevel.value && it.time >= since } ?: return null
        return t.sections.split(';').mapNotNull { part ->
            val name = part.substringBefore('=')
            val (got, of) = part.substringAfter('=').split('/').map { it.toIntOrNull() ?: 0 }.let { it.getOrElse(0) { 0 } to it.getOrElse(1) { 0 } }
            if (of == 0) null else name to got * 100 / of
        }.minByOrNull { it.second }
    }

    /**
     * A round sized to fit [minutes]: the reviews that are due, then new items (towards today's
     * target) until the time is full. A review takes about 12 seconds, a new item about 40.
     */
    suspend fun buildRound(extraNew: Int = 0, minutes: Int = prefs.roundMinutes.value): List<Step> {
        load()
        val budget = minutes * 60
        val allDue = dueIds()
        val due = allDue.take((budget / 12).coerceAtLeast(10))
        val plan = plan()
        val left = (plan.newPerDay - newToday()).coerceAtLeast(0)
        val room = ((budget - due.size * 12) / 40).coerceAtLeast(0)
        var newCount = minOf(room, left) + extraNew
        // With a big pile of reviews, catch up before adding more.
        if (allDue.size > 150) newCount = minOf(newCount, 5 + extraNew)
        val focus = when (weakSection()?.first) {
            "Vocabulary" -> Kind.WORD
            "Grammar" -> Kind.GRAMMAR
            else -> null
        }
        val fresh = pickNew(newCount, focus)

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

    /** The ways an item must be answered right before it can count as known. */
    fun neededWays(id: String): Set<Int> = when (Kind.of(id)) {
        Kind.WORD -> content.wordById[id]?.let { if (it.hasKanji && it.word != it.reading) setOf(0, 1, 2) else setOf(0, 1) } ?: emptySet()
        Kind.KANJI -> setOf(0, 1, 2)
        Kind.KATA, Kind.HIRA, Kind.PHRASE -> setOf(0, 1)
        else -> emptySet()
    }

    fun missingWays(id: String): Set<Int> {
        val got = _states.value[id]?.ways ?: 0
        return neededWays(id).filter { got and (1 shl it) == 0 }.toSet()
    }

    suspend fun answer(id: String, correct: Boolean, wasNew: Boolean, way: Int = -1) = lock.withLock {
        val now = System.currentTimeMillis()
        val old = _states.value[id] ?: ItemState(id, Kind.of(id).name)
        var updated = Srs.answer(old, correct, now, prefs.spacing.value)
        if (correct && way >= 0) updated = updated.copy(ways = updated.ways or (1 shl way))
        // Not known until it has been answered right every way: hold it just below known.
        val missing = neededWays(id).any { updated.ways and (1 shl it) == 0 }
        if (missing && updated.stage >= Srs.KNOWN) {
            updated = updated.copy(stage = Srs.KNOWN - 1, due = now + Srs.interval(Srs.KNOWN - 1, prefs.spacing.value))
        }
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
