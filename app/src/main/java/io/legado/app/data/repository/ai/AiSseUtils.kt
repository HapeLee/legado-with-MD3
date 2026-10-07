package io.legado.app.data.repository.ai

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.domain.model.AiCapability
import io.legado.app.utils.GSON
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Response
import okio.BufferedSource

/**
 * Shared SSE parsing utilities used by all protocol handlers.
 */

/** [readSseData] 内部用的事件：一条完整的 `data:` 载荷、读完了，或者读的时候出错了。 */
internal sealed interface SseItem {
    data class Data(val payload: String) : SseItem
    data class Failure(val error: Throwable) : SseItem
    data object End : SseItem
}

/**
 * 边读 SSE 边把每条 `data:` 载荷交给 [onData]。
 *
 * 阻塞的 socket 读放在子协程里，父协程只从频道等：协程取消打断不了正在进行的读
 * （`aiOkHttpClient` 的 readTimeout 是 0，连接挂着就会一直等），而父协程从
 * `receive()` 里醒得过来——醒来把响应关掉， socket 一关那个读才会抛，
 * 否则这一路流式会永远卡在半章上（AI 分配「点了取消没反应」就是这么来的）。
 * [onData] 仍在调用方自己的协程里执行：`flow { }` 的 emit 不许换协程上下文。
 */
internal suspend fun Response.readSseData(onData: suspend (String) -> Unit) {
    val response = this
    val items = Channel<SseItem>(Channel.UNLIMITED)
    coroutineScope {
        launch {
            try {
                pumpSse(body.source(), items)
                items.trySend(SseItem.End)
            } catch (e: Throwable) {
                items.trySend(SseItem.Failure(e))
            }
        }
        try {
            while (true) {
                when (val item = items.receive()) {
                    is SseItem.Data -> onData(item.payload)
                    is SseItem.Failure -> throw item.error
                    SseItem.End -> break
                }
            }
        } finally {
            // 取消、正常读完、onData 出错三条路都关掉响应：不留一个还在读的 socket
            items.close()
            runCatching { response.close() }
        }
    }
}

/** 读原始字节流并按 SSE 协议成帧，把载荷发进 [items]；正常读完就返回。 */
private suspend fun pumpSse(source: BufferedSource, items: Channel<SseItem>) {
    val dataLines = mutableListOf<String>()
    try {
        var stopped = false
        while (!stopped && !source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n").trim()
                        dataLines.clear()
                        if (data == "[DONE]") {
                            stopped = true
                        } else if (data.isNotEmpty()) {
                            items.trySend(SseItem.Data(data))
                        }
                    }
                }

                line.startsWith("data:") -> {
                    dataLines += line.removePrefix("data:").trimStart()
                }
            }
        }
        if (dataLines.isNotEmpty()) {
            val data = dataLines.joinToString("\n").trim()
            if (data != "[DONE]" && data.isNotEmpty()) items.trySend(SseItem.Data(data))
        }
    } finally {
        source.close()
    }
}

internal fun String.toJsonObject(): JsonObject? {
    return runCatching {
        GSON.fromJson(this, JsonObject::class.java)
    }.getOrNull()
}

internal fun JsonObject.getString(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

internal fun JsonObject.extractApiErrorMessage(): String? {
    val error = get("error")?.asJsonObjectOrNull() ?: return null
    return error.getString("message") ?: error.getString("code") ?: "AI provider returned an error"
}

internal fun JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

internal fun JsonElement.asJsonArrayOrNull() = if (isJsonArray) asJsonArray else null

/**
 * Check if the model supports reasoning capability.
 */
internal fun hasReasoningCapability(capabilities: Set<String>): Boolean {
    return AiCapability.REASONING in capabilities
}
