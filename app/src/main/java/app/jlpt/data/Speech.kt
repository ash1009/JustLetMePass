package app.jlpt.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Japanese audio from the phone's own text-to-speech voice (free and offline).
 *
 * Longer audio (a passage, a dialogue, a story) is played as a list of parts, one sentence or line
 * each, so it can be paused and picked up again from the part it stopped at, or restarted.
 */
class Speech(context: Context, private val prefs: Prefs) {

    enum class Status { STARTING, READY, NO_JAPANESE, NONE }

    /** What is playing now: [key] names the audio, [index] is the part reached out of [total]. */
    data class Playing(val key: String, val index: Int, val total: Int, val playing: Boolean, val done: Boolean = false)

    private val _status = MutableStateFlow(Status.STARTING)
    val status: StateFlow<Status> get() = _status

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> get() = _speaking

    private val _now = MutableStateFlow<Playing?>(null)
    val now: StateFlow<Playing?> get() = _now

    private var tts: TextToSpeech? = null
    private var counter = 0
    private var jaVoices: List<String> = emptyList()
    private var defaultVoice: String? = null
    private var parts: List<Pair<String, String>> = emptyList()
    private var gap = 450L
    private var session = 0
    private var firstPart = 0
    private var firstOffset = 0
    @Volatile private var posInPart = 0

    /** The parts now loaded, to show the one being said. */
    fun partAt(index: Int): Pair<String, String>? = parts.getOrNull(index)

    // Voice objects, looked up once (asking the engine for its voices is slow).
    private var voiceByName: Map<String, android.speech.tts.Voice> = emptyMap()
    private var currentVoice: String? = null
    private var currentPitch = -1f

    /** All talking to the voice engine happens on this one thread, in order, so the screen never freezes. */
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun useVoice(index: Int?) {
        val t = tts ?: return
        val name = if (index == null || jaVoices.size < 2) defaultVoice else jaVoices[index % jaVoices.size]
        name ?: return
        if (name == currentVoice) return
        runCatching { voiceByName[name]?.let { t.voice = it; currentVoice = name } }
    }

    private fun usePitch(p: Float) {
        if (p == currentPitch) return
        tts?.setPitch(p)
        currentPitch = p
    }

    init {
        tts = TextToSpeech(context.applicationContext) { code ->
            if (code != TextToSpeech.SUCCESS) {
                _status.value = Status.NONE
                return@TextToSpeech
            }
            val t = tts ?: return@TextToSpeech
            val r = t.setLanguage(Locale.JAPAN)
            _status.value = if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) Status.NO_JAPANESE else Status.READY
            // Two different Japanese voices (if the phone has them) make dialogues easier to follow.
            jaVoices = runCatching {
                t.voices.orEmpty()
                    .filter { it.locale.language == "ja" && !it.isNetworkConnectionRequired }
                    .sortedBy { it.name }
                    .map { it.name }
            }.getOrDefault(emptyList())
            defaultVoice = runCatching { t.voice?.name }.getOrNull()
            voiceByName = runCatching { t.voices.orEmpty().filter { it.locale.language == "ja" }.associateBy { it.name } }.getOrDefault(emptyMap())
            currentVoice = defaultVoice
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                    val p = parse(utteranceId) ?: return
                    val n = _now.value
                    if (n != null && n.key == p.second && n.playing) {
                        posInPart = if (p.first == firstPart) firstOffset else 0
                        _now.value = n.copy(index = p.first)
                    }
                }

                // Where in the sentence the voice is, so pause can carry on from that point.
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    val p = parse(utteranceId) ?: return
                    val n = _now.value ?: return
                    if (n.key != p.second || !n.playing) return
                    posInPart = start + if (p.first == firstPart) firstOffset else 0
                }

                override fun onDone(utteranceId: String?) {
                    val id = utteranceId ?: return
                    if (id.startsWith("last")) _speaking.value = false
                    val p = parse(id) ?: return
                    val n = _now.value ?: return
                    // Ignore a "done" that only came because the audio was paused or stopped.
                    if (n.key != p.second || !n.playing) return
                    if (p.first >= n.total - 1) {
                        _now.value = n.copy(index = 0, playing = false, done = true)
                        posInPart = 0
                        _speaking.value = false
                    } else {
                        _now.value = n.copy(index = p.first + 1)
                        posInPart = 0
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
            })
        }
    }

    /** Parts are named "p<session>|<index>|<key>"; old sessions (before a pause) are ignored. */
    private fun parse(id: String?): Pair<Int, String>? {
        if (id == null || !id.startsWith("p")) return null
        val bits = id.drop(1).split('|', limit = 3)
        if (bits.size < 3) return null
        if (bits[0].toIntOrNull() != session) return null
        val index = bits[1].toIntOrNull() ?: return null
        return index to bits[2]
    }

    val ready: Boolean get() = _status.value == Status.READY

    private fun rate(): Float = prefs.speechRate.value / 100f

    /** Says something short, like a word or one sentence (study markup is fine). */
    fun say(text: String, slow: Boolean = false) {
        val t = tts ?: return
        if (!ready || text.isBlank()) return
        _now.value = null
        session++
        val speed = if (slow) rate() * 0.7f else rate()
        worker.execute {
            usePitch(1f)
            useVoice(null)
            t.setSpeechRate(speed)
            t.speak(Furigana.plain(text), TextToSpeech.QUEUE_FLUSH, Bundle(), "last${counter++}")
        }
    }

    /**
     * Plays [list] of (speaker, text) parts from part [from]: M lower voice, F higher, anything
     * else the narrator, with a short pause between parts. [key] names it for pause and resume.
     */
    fun play(key: String, list: List<Pair<String, String>>, from: Int = 0, gapMs: Long = 450, fromChar: Int = 0) {
        val t = tts ?: return
        if (!ready || list.isEmpty()) return
        session++
        val mine = session
        parts = list
        gap = gapMs
        val start = from.coerceIn(0, list.lastIndex)
        firstPart = start
        posInPart = 0
        // Where the first part starts, when carrying on from the middle of it.
        val firstText = Furigana.plain(list[start].second)
        firstOffset = if (fromChar in 1 until firstText.length) {
            val back = firstText.lastIndexOfAny(charArrayOf('、', '，', ' ', '「', '。'), fromChar - 1)
            if (back >= 0 && fromChar - back < 25) back + 1 else fromChar
        } else 0
        _now.value = Playing(key, start, list.size, true)
        val speed = rate()
        worker.execute {
            if (session != mine) return@execute
            t.stop()
            t.setSpeechRate(speed)
            for (i in start until list.size) {
                if (session != mine) return@execute   // paused or something else started
                val (speaker, raw) = list[i]
                val text = if (i == start) firstText.substring(firstOffset) else Furigana.plain(raw)
                useVoice(when (speaker) { "M" -> 1; "F" -> 0; else -> null })
                usePitch(when (speaker) { "M" -> 0.85f; "F" -> 1.2f; else -> 1.0f })
                t.speak(text, if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "p$mine|$i|$key")
                if (i != list.lastIndex) t.playSilentUtterance(gapMs, TextToSpeech.QUEUE_ADD, "gap${counter++}")
            }
        }
    }

    /** Jumps forward or back by [by] parts and keeps playing. */
    fun skip(by: Int) {
        val n = _now.value ?: return
        if (parts.isEmpty()) return
        play(n.key, parts, (n.index + by).coerceIn(0, parts.lastIndex), gap)
    }

    /** Stops for now; [resume] carries on from the point it had reached. */
    fun pause() {
        val n = _now.value ?: return
        _now.value = n.copy(playing = false)
        session++
        worker.execute { tts?.stop() }
        _speaking.value = false
    }

    fun resume(key: String) {
        val n = _now.value
        if (n != null && n.key == key && parts.isNotEmpty()) {
            if (n.done) play(key, parts, 0, gap) else play(key, parts, n.index, gap, posInPart)
        }
    }

    /** Older name: plays a whole dialogue from the start. */
    fun dialogue(lines: List<Pair<String, String>>) = play("dialogue", lines)

    fun stop() {
        session++
        worker.execute { tts?.stop() }
        _speaking.value = false
        _now.value = null
    }

    companion object {
        /** Splits a passage into sentences, keeping the 。！？ with each one. */
        fun sentences(text: String): List<String> =
            Regex("""[^。！？!?\n]+[。！？!?」』）]*""").findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
    }
}
