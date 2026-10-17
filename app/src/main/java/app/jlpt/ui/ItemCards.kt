package app.jlpt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jlpt.data.Furigana
import app.jlpt.data.Kana
import app.jlpt.data.Kind

/** Everything about one item, shown when you first meet it and in Learn. */
@Composable
fun ItemCard(id: String, modifier: Modifier = Modifier) {
    when (Kind.of(id)) {
        Kind.HIRA, Kind.KATA -> KanaCard(id, modifier)
        Kind.KWORD -> KataWordCard(id, modifier)
        Kind.WORD -> WordCard(id, modifier)
        Kind.KANJI -> KanjiCard(id, modifier)
        Kind.GRAMMAR -> GrammarCard(id, modifier)
        Kind.PHRASE -> PhraseCard(id, modifier)
    }
}

@Composable
private fun KanaCard(id: String, modifier: Modifier) {
    val k = Kana.byId[id] ?: return
    val other = if (k.katakana) Furigana.katakanaToHiragana(k.kana) else Furigana.hiraganaToKatakana(k.kana)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label(if (k.katakana) "Katakana · ${k.group}" else "Hiragana · ${k.group}")
        Gap()
        if (k.kana.length == 1) StrokeOrder(k.kana, size = 170.dp)
        else Text(k.kana, fontSize = 88.sp, fontFamily = JpBig)
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(k.romaji, style = MaterialTheme.typography.headlineMedium)
            HGap()
            SpeakButton(k.kana)
        }
        Gap(6.dp)
        Text(
            "${if (k.katakana) "Hiragana" else "Katakana"}: $other",
            color = LocalExtra.current.muted, style = MaterialTheme.typography.bodyMedium,
        )
        if (k.kana.length == 1) {
            Gap(4.dp)
            Text("Tap the box to see the strokes again", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted)
        }
    }
}

@Composable
private fun KataWordCard(id: String, modifier: Modifier) {
    val a = app()
    val w = a.content.kataById[id.removePrefix("kw:")] ?: return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("Katakana word")
        Gap()
        Text(w.jp, fontSize = 44.sp, fontFamily = JpBig)
        Gap(6.dp)
        Text(romajiOf(w.jp), color = LocalExtra.current.muted, style = MaterialTheme.typography.bodyLarge)
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.en, style = MaterialTheme.typography.titleLarge)
            HGap()
            SpeakButton(w.jp)
        }
        if (w.from.isNotBlank() && !w.from.equals(w.en, true)) {
            Gap(4.dp)
            Text("From: ${w.from}", color = LocalExtra.current.muted)
        }
    }
}

@Composable
private fun WordCard(id: String, modifier: Modifier) {
    val a = app()
    val w = a.content.wordById[id] ?: return
    val kanjiHere = remember(id) { w.word.filter { Furigana.isKanji(it) }.toList().distinct().mapNotNull { a.content.kanjiByChar[it.toString()] } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("Word · ${lvl(w.level)}")
        Gap()
        Ruby(w.markup, size = 44.sp, show = if (a.study.stage(id) > 0) null else true, center = true, family = JpBig)
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(w.meaning, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
            HGap()
            SpeakButton(w.reading)
        }
        if (w.exampleJp.isNotBlank()) {
            Gap(16.dp)
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Ruby(w.exampleJp, size = 18.sp)
                        Gap(4.dp)
                        Text(w.exampleEn, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                    }
                    HGap(8.dp)
                    SpeakButton(w.exampleJp, size = 38.dp)
                }
            }
        }
        if (kanjiHere.isNotEmpty()) {
            Gap()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                for (k in kanjiHere.take(4)) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(k.char, fontSize = 26.sp, fontFamily = JpBig)
                        Text(k.meanings.firstOrNull() ?: "", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun KanjiCard(id: String, modifier: Modifier) {
    val a = app()
    val k = a.content.kanjiByChar[id.removePrefix("c:")] ?: return
    val examples = remember(id) {
        a.content.words.filter { it.word.contains(k.char) && it.level >= k.level - 1 }
            .sortedBy { it.word.length }.take(4)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("Kanji · ${lvl(k.level)} · ${k.strokes} strokes")
        Gap()
        StrokeOrder(k.char, size = 170.dp)
        Gap()
        Text(k.meanings.joinToString(", "), style = MaterialTheme.typography.titleLarge)
        Gap(8.dp)
        if (k.on.isNotEmpty()) ReadingLine("On", k.on.joinToString("、"))
        if (k.kun.isNotEmpty()) ReadingLine("Kun", k.kun.joinToString("、"))
        if (examples.isNotEmpty()) {
            Gap()
            Panel(color = MaterialTheme.colorScheme.surfaceContainer) {
                Label("Words with it")
                Gap(6.dp)
                for (w in examples) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Ruby(w.markup, size = 20.sp)
                        HGap()
                        Text(w.meaning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingLine(label: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, style = SmallCaps, color = LocalExtra.current.muted)
        HGap(8.dp)
        Text(text, fontSize = 18.sp)
    }
}

@Composable
fun GrammarCard(id: String, modifier: Modifier = Modifier) {
    val a = app()
    val g = a.content.grammarById[id] ?: return
    Column(modifier.fillMaxWidth()) {
        Label("Grammar · ${lvl(g.level)}")
        Gap(6.dp)
        Text(g.title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(g.meaning, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Gap()
        Panel(color = MaterialTheme.colorScheme.surfaceContainer, padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            Label("How to make it")
            Gap(4.dp)
            Ruby(g.structure, size = 16.sp)
        }
        Gap()
        Ruby(g.explain, size = 16.sp)
        Gap()
        Label("Examples")
        for (e in g.examples) {
            Gap(8.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Ruby(e.jp, size = 18.sp)
                    Text(e.en, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
                }
                HGap(8.dp)
                SpeakButton(e.jp, size = 36.dp)
            }
        }
    }
}

@Composable
private fun PhraseCard(id: String, modifier: Modifier) {
    val a = app()
    val p = a.content.phraseById[id] ?: return
    val set = remember(id) { a.content.phraseSets.firstOrNull { s -> s.phrases.any { it.id == id } } }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("Daily life · ${set?.title ?: ""}")
        Gap()
        Ruby(p.jp, size = 28.sp, center = true, show = if (a.study.stage(id) > 0) null else true)
        Gap()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.en, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f, fill = false))
            HGap()
            SpeakButton(p.jp)
        }
        if (p.note.isNotBlank()) {
            Gap()
            Text(p.note, style = MaterialTheme.typography.bodyMedium, color = LocalExtra.current.muted)
        }
    }
}

private val romaTable: Map<String, String> by lazy {
    val m = HashMap<String, String>()
    for (k in Kana.katakana + Kana.hiragana) m[k.kana] = k.romaji
    m["ヴ"] = "vu"; m["ヴィ"] = "vi"; m["ヴェ"] = "ve"; m["ヴォ"] = "vo"
    m["ァ"] = "a"; m["ィ"] = "i"; m["ゥ"] = "u"; m["ェ"] = "e"; m["ォ"] = "o"
    m["ャ"] = "ya"; m["ュ"] = "yu"; m["ョ"] = "yo"
    m
}

/** Rough romaji for a katakana or hiragana word, as a hint while learning. */
fun romajiOf(kana: String): String {
    val sb = StringBuilder()
    var i = 0
    var double = false
    while (i < kana.length) {
        val c = kana[i]
        if (c == 'ッ' || c == 'っ') { double = true; i++; continue }
        if (c == 'ー') { sb.lastOrNull()?.let { sb.append(it) }; i++; continue }
        val two = if (i + 1 < kana.length) kana.substring(i, i + 2) else null
        val r = (two?.let { romaTable[it] }?.also { i += 2 }) ?: (romaTable[c.toString()]?.also { i++ }) ?: run { i++; c.toString() }
        if (double && r.isNotEmpty()) sb.append(r[0])
        double = false
        sb.append(r)
    }
    return sb.toString()
}
