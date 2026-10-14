package app.jlpt

import app.jlpt.data.Furigana
import app.jlpt.data.ItemState
import app.jlpt.data.Kana
import app.jlpt.data.Srs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentTest {
    private fun asset(name: String): String {
        // Tests run from the app folder.
        val f = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name")).first { it.exists() }
        return f.readText()
    }

    @Test
    fun furiganaParses() {
        val p = Furigana.parse("{日本語|にほんご}を{話|はな}します。")
        assertEquals(4, p.size)
        assertEquals("日本語", p[0].text)
        assertEquals("にほんご", p[0].reading)
        assertEquals("日本語を話します。", Furigana.plain("{日本語|にほんご}を{話|はな}します。"))
        assertEquals("にほんごをはなします。", Furigana.kana("{日本語|にほんご}を{話|はな}します。"))
    }

    @Test
    fun srsMovesUpAndDown() {
        val s0 = ItemState("v5-0001", "WORD")
        val s1 = Srs.answer(s0, true, 0, "normal")
        assertEquals(1, s1.stage)
        val s2 = Srs.answer(s1, true, 0, "normal")
        val s3 = Srs.answer(s2, true, 0, "normal")
        val down = Srs.answer(s3, false, 0, "normal")
        assertEquals(1, down.stage)
        assertTrue(s3.due > s2.due)
    }

    @Test
    fun kanaTablesAreComplete() {
        assertEquals(46, Kana.hiragana.count { it.group == "Basic" })
        assertTrue(Kana.katakana.size > Kana.hiragana.size)
    }

    @Test
    fun everyQuizAnswerIsAnOption() {
        for (name in listOf("grammar.json", "reading.json", "stories.json")) {
            val a = JSONArray(asset(name))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val qs = o.optJSONArray("quiz") ?: o.getJSONArray("questions")
                for (j in 0 until qs.length()) {
                    val q = qs.getJSONObject(j)
                    val n = q.getJSONArray("options").length()
                    assertTrue("${o.getString("id")} answer", q.getInt("answer") in 0 until n)
                }
            }
        }
        val l = JSONArray(asset("listening.json"))
        for (i in 0 until l.length()) {
            val o = l.getJSONObject(i)
            assertTrue(o.getInt("answer") in 0 until o.getJSONArray("options").length())
        }
    }

    @Test
    fun wordsAndKanjiLoad() {
        val v = JSONArray(asset("vocab.json"))
        assertTrue(v.length() > 8000)
        val k = JSONArray(asset("kanji.json"))
        assertTrue(k.length() > 2000)
        val strokes = JSONObject(asset("strokes.json"))
        assertTrue(strokes.has("日"))
        assertTrue(strokes.has("ア"))
    }
}
