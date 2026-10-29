package app.jlpt.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.sp
import app.jlpt.data.Furigana
import app.jlpt.data.Known

/** The furigana setting and what is known, so every Japanese text on screen can follow it. */
data class FuriganaRule(val mode: String, val known: Known)

val LocalFurigana = compositionLocalOf { FuriganaRule("auto", Known.NONE) }

/** When set, pressing and holding a kanji word asks for its meaning (used by Stories). */
val LocalWordLookup = compositionLocalOf<((String, String) -> kotlin.Unit)?> { null }

private sealed interface Seg {
    data class Plain(val text: String) : Seg
    data class Word(val index: Int, val text: String, val reading: String) : Seg
    data object Break : Seg
}

private fun units(markup: String): List<Seg> {
    val out = ArrayList<Seg>()
    var idx = 0
    for (p in Furigana.parse(markup)) {
        if (p.reading != null) {
            out += Seg.Word(idx++, p.text, p.reading)
            continue
        }
        // Japanese breaks between any two characters; keep latin words and numbers together.
        val sb = StringBuilder()
        fun flush() { if (sb.isNotEmpty()) { out += Seg.Plain(sb.toString()); sb.clear() } }
        for (c in p.text) {
            when {
                c == '\n' -> { flush(); out += Seg.Break }
                c.code < 0x2E80 && c != ' ' -> sb.append(c)
                c == ' ' -> { sb.append(c); flush() }
                else -> {
                    flush()
                    // Keep closing punctuation with the character before it.
                    if (c in "、。」』）！？…ー・" && out.isNotEmpty() && out.last() is Seg.Plain) {
                        val last = out.removeAt(out.lastIndex) as Seg.Plain
                        out += Seg.Plain(last.text + c)
                    } else out += Seg.Plain(c.toString())
                }
            }
        }
        flush()
    }
    return out
}

/**
 * Japanese text with furigana over the kanji.
 * [show]: null follows the setting (fading as you learn), true always shows, false hides.
 * Tapping a word shows or hides its reading.
 */
@Composable
fun Ruby(
    markup: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    color: Color = LocalContentColor.current,
    show: Boolean? = null,
    center: Boolean = false,
    family: FontFamily? = null,
    weight: FontWeight? = null,
) {
    val rule = LocalFurigana.current
    val ex = LocalExtra.current
    val list = remember(markup) { units(markup) }
    val peeked = remember(markup) { mutableStateMapOf<Int, Boolean>() }
    val hasWords = list.any { it is Seg.Word }
    if (!hasWords) {
        Text(
            markup, modifier = modifier, fontSize = size, color = color, fontFamily = family, fontWeight = weight,
            textAlign = if (center) TextAlign.Center else null, lineHeight = size * 1.5f,
        )
        return
    }
    val rubySize = size * 0.5f
    val rubyHeight = with(LocalDensity.current) { (rubySize * 1.25f).toDp() }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        for (u in list) {
            when (u) {
                is Seg.Break -> Box(Modifier.fillMaxWidth().height(rubyHeight / 3))
                is Seg.Plain -> Column {
                    Spacer(Modifier.height(rubyHeight))
                    Text(u.text, fontSize = size, color = color, fontFamily = family, fontWeight = weight, lineHeight = size * 1.2f)
                }
                is Seg.Word -> {
                    // Mock tests: no furigana at all, and tapping or holding cannot show it.
                    val locked = rule.mode == "locked"
                    val base = if (locked) false else when (show) {
                        true -> true
                        false -> false
                        null -> when (rule.mode) {
                            "always" -> true
                            "never" -> false
                            else -> !rule.known.canHide(u.text)
                        }
                    }
                    val visible = if (locked) false else peeked[u.index] ?: base
                    val lookup = if (locked) null else LocalWordLookup.current
                    RubyWord(
                        u.text, u.reading, visible, size, rubySize, rubyHeight, color, ex.ruby, family, weight,
                        onHold = lookup?.let { l -> { l(u.text, u.reading) } },
                    ) { if (!locked) peeked[u.index] = !visible }
                }
            }
        }
    }
}

/**
 * A kanji word with its reading on top. The reading may stick out a little past the word, as in
 * printed Japanese, so the line does not get gaps.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RubyWord(
    text: String, reading: String, visible: Boolean, size: TextUnit, rubySize: TextUnit, rubyHeight: Dp,
    color: Color, rubyColor: Color, family: FontFamily?, weight: FontWeight?, onHold: (() -> kotlin.Unit)? = null, onTap: () -> kotlin.Unit,
) {
    Layout(
        content = {
            Text(
                reading, fontSize = rubySize, color = rubyColor, maxLines = 1, softWrap = false,
                lineHeight = rubySize * 1.25f, modifier = Modifier.alpha(if (visible) 1f else 0f),
            )
            Text(text, fontSize = size, color = color, fontFamily = family, fontWeight = weight, lineHeight = size * 1.2f)
        },
        modifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            onLongClick = onHold, onClick = onTap,
        ),
    ) { measurables, _ ->
        val loose = Constraints(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)
        val r = measurables[0].measure(loose)
        val b = measurables[1].measure(loose)
        val rh = rubyHeight.roundToPx()
        val w = if (visible) maxOf(b.width, (r.width * 0.8f).toInt()) else b.width
        layout(w, rh + b.height) {
            r.place((w - r.width) / 2, rh - r.height)
            b.place((w - b.width) / 2, rh)
        }
    }
}

/** Plain Japanese without readings, for places where furigana would be too small to matter. */
fun plainJp(markup: String): String = Furigana.plain(markup)
