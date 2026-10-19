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
    data class Playing(val key: String, val index: Int, val total: Int, val playing: Boolean)

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

    private fun useVoice(index: Int?) {
        val t = tts ?: return
        val name = if (index == null || jaVoices.size < 2) defaultVoice else jaVoices[index % jaVoices.size]
        name ?: return
        runCatching { t.voices.firstOrNull { it.name == name }?.let { t.voice = it } }
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
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                    // Parts are named "part|<key>|<index>": remember how far it got.
                    val id = utteranceId ?: return
                    if (!id.startsWith("part|")) return
                    val index = id.substringAfterLast('|').toIntOrNull() ?: return
                    val key = id.removePrefix("part|").substringBeforeLast('|')
                    val n = _now.value
                    if (n != null && n.key == key) _now.value = n.copy(index = index, playing = true)
                }

                override fun onDone(utteranceId: String?) {
                    val id = utteranceId ?: return
                    if (id.startsWith("last")) _speaking.value = false
                    if (id.startsWith("part|")) {
                        val index = id.substringAfterLast('|').toIntOrNull() ?: return
                        val key = id.removePrefix("part|").substringBeforeLast('|')
                        val n = _now.value
                        // The last part finished: the next play starts from the beginning.
                        if (n != null && n.key == key && index == n.total - 1) {
                            _now.value = n.copy(index = 0, playing = false)
                            _speaking.value = false
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
            })
        }
    }

    val ready: Boolean get() = _status.value == Status.READY

    private fun rate(): Float = prefs.speechRate.value / 100f

    /** Says something short, like a word or one sentence (study markup is fine). */
    fun say(text: String, slow: Boolean = false) {
        val t = tts ?: return
        if (!ready || text.isBlank()) return
        _now.value = null
        t.setPitch(1f)
        useVoice(null)
        t.setSpeechRate(if (slow) rate() * 0.7f else rate())
        t.speak(Furigana.plain(text), TextToSpeech.QUEUE_FLUSH, Bundle(), "last${counter++}")
    }

    /**
     * Plays [list] of (speaker, text) parts from part [from]: M lower voice, F higher, anything
     * else the narrator, with a short pause between parts. [key] names it for pause and resume.
     */
    fun play(key: String, list: List<Pair<String, String>>, from: Int = 0) {
        val t = tts ?: return
        if (!ready || list.isEmpty()) return
        t.stop()
        parts = list
        val start = from.coerceIn(0, list.lastIndex)
        _now.value = Playing(key, start, list.size, true)
        t.setSpeechRate(rate())
        for (i in start until list.size) {
            val (speaker, text) = list[i]
            useVoice(when (speaker) { "M" -> 1; "F" -> 0; else -> null })
            t.setPitch(when (speaker) { "M" -> 0.85f; "F" -> 1.2f; else -> 1.0f })
            t.speak(Furigana.plain(text), if (i == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), "part|$key|$i")
            if (i != list.lastIndex) t.playSilentUtterance(450, TextToSpeech.QUEUE_ADD, "gap${counter++}")
        }
    }

    /** Stops for now; [resume] carries on from the part it was saying. */
    fun pause() {
        val n = _now.value ?: return
        tts?.stop()
        _speaking.value = false
        _now.value = n.copy(playing = false)
    }

    fun resume(key: String) {
        val n = _now.value
        if (n != null && n.key == key && parts.isNotEmpty()) play(key, parts, n.index)
    }

    /** Older name: plays a whole dialogue from the start. */
    fun dialogue(lines: List<Pair<String, String>>) = play("dialogue", lines)

    fun stop() {
        tts?.stop()
        _speaking.value = false
        _now.value = null
    }

    companion object {
        /** Splits a passage into sentences, keeping the 。！？ with each one. */
        fun sentences(text: String): List<String> =
            Regex("""[^。！？!?\n]+[。！？!?」』）]*""").findAll(text).map { it.value.trim() }.filter { it.isNotBlank() }.toList()
    }
}
