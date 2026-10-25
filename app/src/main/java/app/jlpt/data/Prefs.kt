package app.jlpt.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** Settings and small bits of state, saved on the phone. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    // The Claude key is kept apart so it is never part of a backup.
    private val secret: SharedPreferences = context.getSharedPreferences("secret", Context.MODE_PRIVATE)

    inner class IntPref(private val key: String, default: Int) {
        private val flow = MutableStateFlow(sp.getInt(key, default))
        val state: StateFlow<Int> get() = flow
        var value: Int
            get() = flow.value
            set(v) { sp.edit().putInt(key, v).apply(); flow.value = v }
    }

    inner class StrPref(private val key: String, default: String, private val store: SharedPreferences = sp) {
        private val flow = MutableStateFlow(store.getString(key, default) ?: default)
        val state: StateFlow<String> get() = flow
        var value: String
            get() = flow.value
            set(v) { store.edit().putString(key, v).apply(); flow.value = v }
    }

    inner class BoolPref(private val key: String, default: Boolean) {
        private val flow = MutableStateFlow(sp.getBoolean(key, default))
        val state: StateFlow<Boolean> get() = flow
        var value: Boolean
            get() = flow.value
            set(v) { sp.edit().putBoolean(key, v).apply(); flow.value = v }
    }

    // ---- the three main settings ----
    val dailyMinutes = IntPref("daily_minutes", 60)
    val examLevel = IntPref("exam_level", 4)
    val examDate = StrPref("exam_date", "2026-12-06")
    val reminderOn = BoolPref("reminder_on", true)
    val reminderHour = IntPref("reminder_hour", 19)
    val reminderMinute = IntPref("reminder_minute", 0)

    // ---- "More" settings, all with sensible defaults ----
    /** auto: furigana fades as you learn each kanji; always; never (tap a word to peek). */
    val furigana = StrPref("furigana", "auto")
    /** 0 = worked out from the exam date. */
    val newPerDay = IntPref("new_per_day", 0)
    /** gentle (more reviews), normal, fast (fewer reviews). */
    val spacing = StrPref("spacing", "normal")
    /** Speech speed in percent. */
    val speechRate = IntPref("speech_rate", 90)
    val autoPlay = BoolPref("auto_play", true)
    val showRomaji = BoolPref("show_romaji", true)
    val includeDaily = BoolPref("include_daily", true)
    val dark = StrPref("theme", "system")
    val speedBest = IntPref("speed_best", 0)
    /** How long one Study round is, in minutes. */
    val roundMinutes = IntPref("round_minutes", 15)
    /** Mock papers already taken, so each test is a fresh one. */
    val papersDone = StrPref("papers_done", "")
    /** Items already given back a wrong mark from a broken question. */
    val forgiven = StrPref("forgiven", "")
    /** Katakana words that duplicate JLPT words have had their progress moved over. */
    val kataMerged = BoolPref("kata_merged", false)
    /** Audio playlist: what to play and how long to pause between parts. */
    val listenKind = StrPref("listen_kind", "words")
    val listenGap = IntPref("listen_gap", 1500)

    // ---- Claude ----
    val claudeKey = StrPref("claude_key", "", secret)
    var claudeModels: String
        get() = sp.getString("claude_models", "") ?: ""
        set(v) { sp.edit().putString("claude_models", v).apply() }

    // ---- state ----
    val onboarded = BoolPref("onboarded", false)
    val hiraganaKnown = BoolPref("hiragana_known", false)

    var lastReminderDay: String
        get() = sp.getString("last_reminder_day", "") ?: ""
        set(v) { sp.edit().putString("last_reminder_day", v).apply() }

    fun exam(): LocalDate = runCatching { LocalDate.parse(examDate.value) }.getOrElse { LocalDate.of(2026, 12, 6) }
}
