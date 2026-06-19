package io.legado.app.ui.main.ai.compose

import android.content.Context
import androidx.compose.runtime.Immutable
import io.legado.app.R
import io.legado.app.ui.main.ai.AiChatMessage
import org.json.JSONObject

@Immutable
sealed class AiChatUiItem {
    abstract val id: String

    @Immutable
    data class User(
        override val id: String,
        val content: String
    ) : AiChatUiItem()

    @Immutable
    data class Assistant(
        override val id: String,
        val parts: List<AiMessagePartUi>
    ) : AiChatUiItem()
}

@Immutable
sealed class AiMessagePartUi {
    abstract val id: String

    @Immutable
    data class Text(
        override val id: String,
        val content: String,
        val pending: Boolean
    ) : AiMessagePartUi()

    @Immutable
    data class ProcessChain(
        override val id: String,
        val steps: List<AiProcessStepUi>
    ) : AiMessagePartUi()

    @Immutable
    data class SearchBooks(
        override val id: String,
        val books: List<AiSearchBookUi>
    ) : AiMessagePartUi()

    @Immutable
    data class Images(
        override val id: String,
        val images: List<AiImageResultUi>
    ) : AiMessagePartUi()
}

@Immutable
data class AiProcessStepUi(
    val id: String,
    val type: AiProcessStepType,
    val title: String,
    val subtitle: String,
    val detail: String,
    val pending: Boolean,
    val success: Boolean,
    val collapsed: Boolean,
    val payload: AiToolDisplayPayload? = null
)

@Immutable
data class AiToolDisplayPayload(
    val type: AiToolPreviewType,
    val title: String,
    val summary: String,
    val raw: String,
    val books: List<AiSearchBookUi> = emptyList(),
    val webResults: List<AiWebResultUi> = emptyList(),
    val images: List<AiImageResultUi> = emptyList()
)

enum class AiToolPreviewType {
    BookResults,
    WebResults,
    ImageResult,
    Generic
}

@Immutable
data class AiSearchBookUi(
    val name: String,
    val author: String,
    val originName: String,
    val kind: String,
    val intro: String,
    val latestChapterTitle: String,
    val coverUrl: String,
    val bookUrl: String,
    val origin: String,
    val target: String
)

@Immutable
data class AiWebResultUi(
    val title: String,
    val url: String,
    val content: String
)

@Immutable
data class AiImageResultUi(
    val imageId: String,
    val image: String,
    val prompt: String
)

fun buildAiChatUiItems(
    context: Context,
    messages: List<AiChatMessage>
): List<AiChatUiItem> {
    val result = mutableListOf<AiChatUiItem>()
    var assistantId: String? = null
    val assistantMessages = mutableListOf<AiChatMessage>()

    fun flushAssistant() {
        val id = assistantId
        if (id != null && assistantMessages.isNotEmpty()) {
            val processSteps = assistantMessages
                .filter { it.isProcessMessage() }
                .map { it.toProcessStep(context) }
            val processImages = processSteps
                .flatMap { it.payload?.images.orEmpty() }
                .distinctBy { it.imageKey() }
            val assistantParts = mutableListOf<AiMessagePartUi>()
            if (processSteps.isNotEmpty()) {
                assistantParts += AiMessagePartUi.ProcessChain(
                    id = "process-${processSteps.first().id}",
                    steps = processSteps
                )
            }
            assistantMessages
                .filterNot { it.isProcessMessage() }
                .forEach { assistantParts += it.toTextParts() }
            if (processImages.isNotEmpty()) {
                val existingImageKeys = assistantParts
                    .filterIsInstance<AiMessagePartUi.Images>()
                    .flatMap { it.images }
                    .map { it.imageKey() }
                    .toSet()
                val visibleImages = processImages.filterNot { it.imageKey() in existingImageKeys }
                if (visibleImages.isNotEmpty()) {
                    assistantParts += AiMessagePartUi.Images(
                        id = "process-images-${processSteps.first().id}",
                        images = visibleImages
                    )
                }
            }
            if (assistantParts.isNotEmpty()) {
                result += AiChatUiItem.Assistant(
                    id = id,
                    parts = assistantParts.toList()
                )
            }
        }
        assistantId = null
        assistantMessages.clear()
    }

    messages.filterNot { (it.kind ?: AiChatMessage.Kind.TEXT) == AiChatMessage.Kind.STATUS }
        .forEach { message ->
            when (message.role) {
                AiChatMessage.Role.USER -> {
                    flushAssistant()
                    result += AiChatUiItem.User(
                        id = message.id,
                        content = message.content
                    )
                }
                AiChatMessage.Role.ASSISTANT -> {
                    if (assistantId == null) {
                        assistantId = "assistant-${message.id}"
                    }
                    assistantMessages += message
                }
            }
        }
    flushAssistant()
    return result
}

private fun AiChatMessage.isProcessMessage(): Boolean {
    return when (kind ?: AiChatMessage.Kind.TEXT) {
        AiChatMessage.Kind.THINKING,
        AiChatMessage.Kind.TOOL -> true
        else -> false
    }
}

private fun AiChatMessage.toTextParts(): List<AiMessagePartUi> {
    val parsed = parseAssistantContent(content)
    val parts = mutableListOf<AiMessagePartUi>()
    if (parsed.content.isNotBlank() || pending) {
        parts += AiMessagePartUi.Text(
            id = "$id-text",
            content = parsed.content.ifBlank { if (pending) "..." else " " },
            pending = pending
        )
    }
    if (parsed.books.isNotEmpty()) {
        parts += AiMessagePartUi.SearchBooks(
            id = "$id-books",
            books = parsed.books
        )
    }
    if (parsed.images.isNotEmpty()) {
        parts += AiMessagePartUi.Images(
            id = "$id-images",
            images = parsed.images
        )
    }
    return parts
}

private fun AiChatMessage.toProcessStep(context: Context): AiProcessStepUi {
    val kind = kind ?: AiChatMessage.Kind.TEXT
    val detail = statusDetail?.takeIf { it.isNotBlank() } ?: content
    return when (kind) {
        AiChatMessage.Kind.THINKING -> {
            val title = statusLabel?.takeIf { it.isNotBlank() }
                ?: context.getString(if (pending) R.string.ai_chat_thinking else R.string.ai_chat_thinking_done)
            AiProcessStepUi(
                id = id,
                type = AiProcessStepType.Thinking,
                title = normalizeStepLabel(title),
                subtitle = summarizeProcessDetail(detail, context.getString(R.string.ai_chat_thinking_done)),
                detail = detail,
                pending = pending,
                success = true,
                collapsed = collapsed
            )
        }
        AiChatMessage.Kind.TOOL -> {
            val payload = parseToolDisplayPayload(this, context)
            val state = statusLabel?.takeIf { it.isNotBlank() } ?: context.getString(
                when {
                    pending -> R.string.ai_tool_status_calling
                    statusSuccess -> R.string.ai_tool_status_done
                    else -> R.string.ai_tool_status_failed
                }
            )
            AiProcessStepUi(
                id = id,
                type = AiProcessStepType.Tool,
                title = payload?.title
                    ?: statusName?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.ai_tool_default_name),
                subtitle = payload?.summary?.ifBlank { state } ?: state,
                detail = detail,
                pending = pending,
                success = statusSuccess,
                collapsed = collapsed,
                payload = payload
            )
        }
        else -> AiProcessStepUi(
            id = id,
            type = AiProcessStepType.Thinking,
            title = "",
            subtitle = summarizeProcessDetail(detail, ""),
            detail = detail,
            pending = pending,
            success = true,
            collapsed = collapsed
        )
    }
}

private fun AiImageResultUi.imageKey(): String {
    return imageId.ifBlank { image }
}

private data class ParsedAssistantContent(
    val content: String,
    val books: List<AiSearchBookUi>,
    val images: List<AiImageResultUi>
)

private fun parseAssistantContent(content: String): ParsedAssistantContent {
    val books = mutableListOf<AiSearchBookUi>()
    val images = mutableListOf<AiImageResultUi>()
    val withoutToolEvents = toolEventBlockRegex.replace(content) { match ->
        runCatching {
            val events = JSONObject(match.groupValues[1]).optJSONArray("events") ?: return@runCatching
            for (index in 0 until events.length()) {
                val item = events.optJSONObject(index) ?: continue
                if (item.optString("stage") == "result" && item.optString("name") == "generate_image") {
                    parseImageResult(item.optString("content"))?.let(images::add)
                }
            }
        }
        ""
    }
    val visibleContent = searchResultBlockRegex.replace(withoutToolEvents) { match ->
        runCatching {
            val results = JSONObject(match.groupValues[1]).optJSONArray("results") ?: return@runCatching
            for (index in 0 until results.length()) {
                results.optJSONObject(index)?.toSearchBookUi()?.let(books::add)
            }
        }
        ""
    }.trim()
    return ParsedAssistantContent(
        content = visibleContent,
        books = books.distinctBy { it.bookUrl },
        images = images.distinctBy { it.image }
    )
}

private fun parseToolDisplayPayload(
    message: AiChatMessage,
    context: Context
): AiToolDisplayPayload? {
    val name = message.statusName.orEmpty()
    val raw = message.statusDetail?.takeIf { it.isNotBlank() } ?: message.content
    if (raw.isBlank()) return null
    val contentRoot = runCatching { JSONObject(message.content) }.getOrNull()
    val rawRoot = runCatching { JSONObject(raw) }.getOrNull()
    val root = contentRoot ?: rawRoot
    return when (name) {
        "search_book_source" -> {
            val books = root?.optJSONArray("results")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optJSONObject(index)?.toSearchBookUi()?.let(::add)
                    }
                }
            }.orEmpty()
            AiToolDisplayPayload(
                type = AiToolPreviewType.BookResults,
                title = context.getString(R.string.ai_tool_book_source_search),
                summary = if (books.isNotEmpty()) {
                    "找到 ${books.size} 条结果"
                } else {
                    root?.optString("keyword")?.takeIf { it.isNotBlank() }?.let { "搜索：$it" }
                        ?: summarizeProcessDetail(raw, context.getString(R.string.ai_tool_status_calling))
                },
                raw = raw,
                books = books
            )
        }
        "search_web_tavily" -> {
            val webResults = root?.optJSONArray("results")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        add(
                            AiWebResultUi(
                                title = item.optString("title"),
                                url = item.optString("url"),
                                content = item.optString("content")
                            )
                        )
                    }
                }
            }.orEmpty()
            AiToolDisplayPayload(
                type = AiToolPreviewType.WebResults,
                title = context.getString(R.string.ai_tool_web_search),
                summary = if (webResults.isNotEmpty()) {
                    "找到 ${webResults.size} 条网页"
                } else {
                    root?.optString("query")?.takeIf { it.isNotBlank() }?.let { "搜索：$it" }
                        ?: summarizeProcessDetail(raw, context.getString(R.string.ai_tool_status_calling))
                },
                raw = raw,
                webResults = webResults
            )
        }
        "generate_image" -> {
            val image = parseImageResult(raw)
            AiToolDisplayPayload(
                type = AiToolPreviewType.ImageResult,
                title = context.getString(R.string.ai_image_generate),
                summary = image?.prompt?.takeIf { it.isNotBlank() } ?: context.getString(R.string.ai_image_generated),
                raw = raw,
                images = listOfNotNull(image)
            )
        }
        "generate_book_character_avatar",
        "set_book_character_avatar_from_gallery" -> {
            val image = parseImageResult(raw)
            AiToolDisplayPayload(
                type = AiToolPreviewType.ImageResult,
                title = "角色头像",
                summary = image?.prompt?.takeIf { it.isNotBlank() } ?: "已更新角色头像",
                raw = raw,
                images = listOfNotNull(image)
            )
        }
        "list_book_characters" -> {
            val images = parseCharacterAvatarResults(root)
            if (images.isNotEmpty()) {
                AiToolDisplayPayload(
                    type = AiToolPreviewType.ImageResult,
                    title = "角色头像",
                    summary = "找到 ${images.size} 张角色头像",
                    raw = raw,
                    images = images
                )
            } else {
                AiToolDisplayPayload(
                    type = AiToolPreviewType.Generic,
                    title = "角色资料",
                    summary = summarizeProcessDetail(raw, context.getString(R.string.ai_tool_status_done)),
                    raw = raw
                )
            }
        }
        else -> AiToolDisplayPayload(
            type = AiToolPreviewType.Generic,
            title = name.ifBlank { context.getString(R.string.ai_tool_default_name) },
            summary = summarizeProcessDetail(raw, context.getString(R.string.ai_tool_status_done)),
            raw = raw
        )
    }
}

private fun JSONObject.toSearchBookUi(): AiSearchBookUi? {
    val bookUrl = optString("bookUrl")
    val origin = optString("origin")
    if (bookUrl.isBlank() || origin.isBlank()) return null
    return AiSearchBookUi(
        name = optString("name").ifBlank { "未命名" },
        author = optString("author"),
        originName = optString("originName"),
        kind = optString("kind"),
        intro = optString("intro").replace(Regex("\\s+"), " ").trim(),
        latestChapterTitle = optString("latestChapterTitle"),
        coverUrl = optString("coverUrl"),
        bookUrl = bookUrl,
        origin = origin,
        target = optString("target")
    )
}

private fun parseCharacterAvatarResults(root: JSONObject?): List<AiImageResultUi> {
    val characters = root?.optJSONArray("characters") ?: return emptyList()
    return buildList {
        for (index in 0 until characters.length()) {
            val character = characters.optJSONObject(index) ?: continue
            val avatarImage = character.optJSONObject("avatarImage")
            val image = avatarImage?.optString("imagePath")
                ?.takeIf { it.isNotBlank() }
                ?: character.optString("avatar").takeIf { it.isNotBlank() }
                ?: continue
            add(
                AiImageResultUi(
                    imageId = "",
                    image = image,
                    prompt = avatarImage?.optString("alt")
                        ?.takeIf { it.isNotBlank() }
                        ?: character.optString("name")
                )
            )
        }
    }.distinctBy { it.image }
}

private fun parseImageResult(raw: String): AiImageResultUi? {
    val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (!payload.optBoolean("success", payload.optBoolean("ok", false))) return null
    val nestedImage = payload.optJSONObject("image")
    val imageId = payload.optString("imageId")
        .ifBlank { nestedImage?.optString("imageId").orEmpty() }
        .ifBlank { nestedImage?.optString("id").orEmpty() }
    val image = payload.optString("imagePath")
        .ifBlank { payload.optString("image") }
        .ifBlank { nestedImage?.optString("imagePath").orEmpty() }
        .ifBlank { nestedImage?.optString("image").orEmpty() }
        .ifBlank { nestedImage?.optString("localPath").orEmpty() }
    if (imageId.isBlank() && !image.startsWith("http", true) && !image.startsWith("data:image", true)) {
        return null
    }
    return AiImageResultUi(
        imageId = imageId,
        image = image,
        prompt = payload.optString("prompt").ifBlank { nestedImage?.optString("prompt").orEmpty() }
    )
}

private fun summarizeProcessDetail(raw: String, fallback: String): String {
    return raw.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .replace(Regex("\\s+"), " ")
        .ifBlank { fallback }
        .let { if (it.length > 96) "${it.take(96)}..." else it }
}

private fun normalizeStepLabel(label: String): String {
    return label
        .replace("，点按展开", "")
        .replace("，点击展开", "")
        .replace(", tap to expand", "", ignoreCase = true)
        .replace(" tap to expand", "", ignoreCase = true)
        .trim()
}

private val toolEventBlockRegex = Regex(
    "```legado-tool-events\\s*\\n([\\s\\S]*?)\\n```",
    setOf(RegexOption.MULTILINE)
)

private val searchResultBlockRegex = Regex(
    "```legado-search-results\\s*\\n([\\s\\S]*?)\\n```",
    setOf(RegexOption.MULTILINE)
)
