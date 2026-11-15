package com.tencent.kuiklybase.markdown

import com.tencent.kuikly.core.log.KLog
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/** Minimal SSE parser for OpenAI-compatible chat completion streams. */
internal class SseEventParser(
    private val diagnosticsEnabled: Boolean = false,
    private val onTextDelta: (String) -> Unit,
) {
    private val buffer = StringBuilder()
    private val lock = SynchronizedObject()
    private var dataLines = 0
    private var parsedObjects = 0
    private var deltaChars = 0

    fun feed(chunk: String): Unit = synchronized(lock) {
        buffer.append(chunk)
        while (true) {
            val boundary = findLineBoundary() ?: break
            val line = buffer.substring(0, boundary.start).trimEnd('\r')
            buffer.delete(0, boundary.end)
            consumeLine(line)
        }
    }

    fun finish(): Unit = synchronized(lock) {
        if (buffer.isNotEmpty()) {
            consumeLine(buffer.toString().trimEnd('\r'))
            buffer.setLength(0)
        }
        if (diagnosticsEnabled) KLog.i("AiChatDemo", "parser finish lines=$dataLines parsed=$parsedObjects chars=$deltaChars")
    }

    private fun consumeLine(line: String) {
        if (line.isEmpty() || line.startsWith(":")) return
        val payload = if (line.startsWith("data:")) line.substring(5).trimStart() else return
        if (payload == "[DONE]") return
        dataLines++
        val delta = extractContent(payload) ?: return
        parsedObjects++
        if (delta.isNotEmpty()) {
            deltaChars += delta.length
            onTextDelta(delta)
        }
        if (diagnosticsEnabled && (dataLines <= 3 || dataLines % 25 == 0)) {
            KLog.i("AiChatDemo", "parser lines=$dataLines parsed=$parsedObjects chars=$deltaChars buffer=${buffer.length}")
        }
    }

    private fun extractContent(payload: String): String? {
        val root = parseObject(payload) ?: return null
        val choices = root.optJSONArray("choices") ?: return null
        val first = choices.optJSONObject(0) ?: return null
        val delta = first.optJSONObject("delta") ?: return null
        return delta.optString("content")
    }

    private fun parseObject(raw: String): JSONObject? = try {
        JSONObject(raw)
    } catch (_: Throwable) {
        null
    }

    private fun findLineBoundary(): LineBoundary? {
        for (index in buffer.indices) {
            if (buffer[index] == '\n') return LineBoundary(index, index + 1)
        }
        return null
    }

    private data class LineBoundary(val start: Int, val end: Int)
}

internal fun chatCompletionBody(
    prompt: String,
    model: String,
    history: List<Pair<String, String>> = listOf("user" to prompt),
): JSONObject {
    val messages = JSONArray()
        .put(
            JSONObject()
                .put("role", "system")
                .put(
                    "content",
                    "You are a helpful assistant. Answer in Markdown with headings, concise paragraphs, " +
                        "bullet lists, and a small table when it helps demonstrate streaming rendering.",
                ),
        )
    history.forEach { (role, text) ->
        messages.put(JSONObject().put("role", role).put("content", text))
    }

    return JSONObject()
        .put("model", model)
        .put("messages", messages)
        .put("stream", true)
        .put("temperature", 1)
}
