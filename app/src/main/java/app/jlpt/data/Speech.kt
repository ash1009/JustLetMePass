package app.jlpt.data

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Japanese audio from the phone's own text-to-speech voice (free and offline). */
class Speech(context: Context, private val prefs: Prefs) {

    enum class Status { STARTING, READY, NO_JAPANESE, NONE }

    private val _status = MutableStateFlow(Status.STARTING)
    val status: StateFlow<Status> get() = _status

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> get() = _speaking

    private var tts: TextToSpeech? = null
    private var counter = 0
    private var jaVoices: List<String> = emptyList()
    private var defaultVoice: String? = null

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
                override fun onStart(utteranceId: String?) { _speaking.value = true }
                override fun onDone(utteranceId: String?) { if (utteranceId?.startsWith("last") == true) _speaking.value = false }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { _speaking.value = false }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { _speaking.value = false }
            })
        }
    }

    val ready: Boolean get() = _status.value == Status.READY

    private fun rate(): Float = prefs.speechRate.value / 100f

    /** Says [text] (study markup is fine: the readings are dropped). */
    fun say(text: String, slow: Boolean = false) {
        val t = tts ?: return
        if (!ready || text.isBlank()) return
        t.setPitch(1f)
        useVoice(null)
        t.setSpeechRate(if (slow) rate() * 0.7f else rate())
        t.speak(Furigana.plain(text), TextToSpeech.QUEUE_FLUSH, Bundle(), "last${counter++}")
    }

    /**
     * Plays a dialogue: each line with a voice pitch for the speaker (M lower, F higher,
     * N the narrator) and a short pause between lines.
     */
    fun dialogue(lines: List<Pair<String, String>>) {
        val t = tts ?: return
        if (!ready) return
        t.stop()
        t.setSpeechRate(rate())
        lines.forEachIndexed { i, (speaker, text) ->
            val params = Bundle()
            val pitch = when (speaker) { "M" -> 0.85f; "F" -> 1.2f; else -> 1.0f }
            // Voice and pitch are taken when each line is queued.
            useVoice(when (speaker) { "M" -> 1; "F" -> 0; else -> null })
            t.setPitch(pitch)
            val id = if (i == lines.lastIndex) "last${counter++}" else "line${counter++}"
            t.speak(Furigana.plain(text), if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id)
            if (i != lines.lastIndex) t.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "gap${counter++}")
        }
    }

    fun stop() { tts?.stop(); _speaking.value = false }
}
