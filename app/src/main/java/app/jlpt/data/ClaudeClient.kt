package app.jlpt.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Anthropic's Claude with your own API key. The key stays on the phone.
 * Model names change, so the app asks which models the key can use and picks the newest
 * Sonnet for chat (or the best there is) and a Haiku for quick jobs.
 */
class ClaudeClient(private val prefs: Prefs) {

    sealed interface Reply {
        data class Text(val text: String) : Reply
        data class Failed(val message: String) : Reply
    }

    data class Turn(val role: String, val text: String)

    val hasKey: Boolean get() = prefs.claudeKey.value.isNotBlank()

    suspend fun ask(system: String, prompt: String, smart: Boolean = false, maxTokens: Int = 1200): Reply =
        chat(system, listOf(Turn("user", prompt)), smart, maxTokens)

    suspend fun chat(system: String, turns: List<Turn>, smart: Boolean = true, maxTokens: Int = 1500): Reply =
        withContext(Dispatchers.IO) {
            val key = prefs.claudeKey.value.trim()
            if (key.isEmpty()) return@withContext Reply.Failed("Add your Claude key in Settings first.")
            val model = try {
                pickModel(key, smart)
            } catch (e: HttpError) {
                return@withContext Reply.Failed(e.friendly)
            } catch (e: IOException) {
                return@withContext Reply.Failed("No internet, so Claude could not be reached.")
            } ?: return@withContext Reply.Failed("This key has no Claude model it can use.")
            var tries = 0
            while (true) {
                try {
                    return@withContext Reply.Text(message(key, model, system, turns, maxTokens))
                } catch (e: HttpError) {
                    if ((e.code == 429 || e.code == 529 || e.code >= 500) && tries < 3) {
                        tries++
                        delay(e.retryAfterSeconds?.coerceIn(1, 30)?.times(1000L) ?: (4_000L * tries))
                        continue
                    }
                    if (e.code == 404) prefs.claudeModels = ""
                    return@withContext Reply.Failed(e.friendly)
                } catch (e: IOException) {
                    if (tries < 1) { tries++; delay(2_000); continue }
                    return@withContext Reply.Failed("No internet, so Claude could not be reached.")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Reply.Failed("Claude did not answer.")
        }

    /** Checks a key right away, for the Settings page. */
    suspend fun test(): Reply = ask("Reply with just: OK", "Say OK", smart = false, maxTokens = 10)

    private fun pickModel(key: String, smart: Boolean): String? {
        val remembered = prefs.claudeModels.split(',').filter { it.isNotBlank() }
        val models = remembered.ifEmpty { listModels(key).also { prefs.claudeModels = it.joinToString(",") } }
        // The list comes newest first.
        val haiku = models.firstOrNull { it.contains("haiku") }
        val sonnet = models.firstOrNull { it.contains("sonnet") }
        val opus = models.firstOrNull { it.contains("opus") }
        return if (smart) sonnet ?: opus ?: haiku ?: models.firstOrNull() else haiku ?: sonnet ?: models.firstOrNull()
    }

    private fun listModels(key: String): List<String> {
        val body = request("GET", "$BASE/models?limit=100", key, null)
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { data.getJSONObject(it).optString("id").takeIf { id -> id.startsWith("claude") } }
    }

    private fun message(key: String, model: String, system: String, turns: List<Turn>, maxTokens: Int): String {
        val msgs = JSONArray()
        for (t in turns) msgs.put(JSONObject().put("role", t.role).put("content", t.text))
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", msgs)
        val body = request("POST", "$BASE/messages", key, payload.toString())
        val content = JSONObject(body).optJSONArray("content") ?: throw IOException("empty answer")
        return (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
            .trim()
    }

    private class HttpError(val code: Int, val body: String, val retryAfterSeconds: Long? = null) : Exception("HTTP $code") {
        val friendly: String
            get() = when {
                body.contains("credit balance", ignoreCase = true) -> "Your Claude API credits have run out."
                code == 401 -> "That Claude key is not valid. Copy it again from the Claude Console."
                code == 403 -> "Claude refused the key. Check it in the Claude Console."
                code == 429 -> "Too many requests. Try again in a minute."
                code == 529 || code >= 500 -> "Claude is busy right now. Try again soon."
                code == 404 -> "That Claude model is not available. Try again."
                else -> "Claude said no (error $code)."
            }
    }

    private fun request(method: String, url: String, key: String, json: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 120_000
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.setRequestProperty("Accept", "application/json")
            if (json != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpError(code, body, c.getHeaderField("retry-after")?.trim()?.toDoubleOrNull()?.toLong())
            return body
        } finally {
            c.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.anthropic.com/v1"

        /** How Claude should write, so the app can show furigana over its Japanese. */
        const val STYLE = """Write Japanese with furigana markup: put every kanji word in braces with its hiragana reading, like {日本語|にほんご}を{話|はな}します. Leave okurigana outside the braces. Never write kanji outside braces. Do not use markdown headings or tables; short paragraphs and simple lists with "- " are fine. Use simple, friendly English."""
    }
}
