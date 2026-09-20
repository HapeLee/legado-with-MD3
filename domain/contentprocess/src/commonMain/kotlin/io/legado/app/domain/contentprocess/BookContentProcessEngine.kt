package io.legado.app.domain.contentprocess

import io.legado.app.core.platform.JsonCodec
import io.legado.app.domain.model.TextProcessAction
import io.legado.app.domain.model.TextProcessAnchor
import kotlin.math.abs

/**
 * 正文处理引擎（M4-8 从 `:core:data` 的 `io.legado.app.domain.model` 搬来）。
 *
 * 纯函数 object：**零 Room、零 Android、零 `:app` 依赖**，只依赖 `JsonCodec`（`:core:platform`）
 * 与 `TextProcessAnchor` / `TextProcessAction`（`:core:model`）。它在语义上本来就是 domain 层
 * 的东西，此前只是因为历史原因躺在 `:core:data` 里 —— 本片随正文处理域一起收进来。
 *
 * 三处搬迁改动，别的逐字照抄：
 *  1. `package` 从 `io.legado.app.domain.model` 改成 `io.legado.app.domain.contentprocess`。
 *  2. `TextProcessAnchor` / `TextProcessAction` 原本与它**同包**（跨模块同包，`:core:model`
 *     与 `:core:data` 都有 `io.legado.app.domain.model`）⇒ 此前不需要 import，搬走后必须
 *     **显式 import**。这是"跨模块同包"这个本仓既有现象的代价，不是新机制。
 *  3. `List<BookContentProcess>` 的类型从 Room 实体换成领域模型（同包的 [BookContentProcess]），
 *     逻辑一行未动 —— `STATUS_ACTIVE` / `STAGE_CONTENT` / `KIND_USER_*` 现在解析到模型自己
 *     的 companion 常量，取值与实体一致（由 `BookContentProcessMapperTest` 逐值钉住）。
 */
object BookContentProcessEngine {

    data class ApplyResult(
        val text: String,
        val effectiveProcesses: List<BookContentProcess>,
    )

    fun apply(
        content: String,
        processes: List<BookContentProcess>,
    ): ApplyResult {
        if (content.isEmpty() || processes.isEmpty()) {
            return ApplyResult(content, emptyList())
        }
        var output = content
        val effectiveProcesses = arrayListOf<BookContentProcess>()
        processes
            .filter {
                it.enabled &&
                    it.status == BookContentProcess.STATUS_ACTIVE &&
                    it.stage == BookContentProcess.STAGE_CONTENT
            }
            .forEach { process ->
                if (process.kind == BookContentProcess.KIND_USER_UNDERLINE ||
                    process.kind == BookContentProcess.KIND_USER_HIGHLIGHT
                ) {
                    // 用户划线/高亮标记：不改文本。锚点能在正文里解析到说明标记仍有效，
                    // 计入 effectiveProcesses 供渲染层把样式应用到区间。
                    val anchor = JsonCodec.fromJsonObject(process.anchorJson, TextProcessAnchor::class)
                        ?: return@forEach
                    if (findTargetRange(output, anchor) != null) {
                        effectiveProcesses.add(process)
                    }
                    return@forEach
                }
                val anchor = JsonCodec.fromJsonObject(process.anchorJson, TextProcessAnchor::class)
                    ?: return@forEach
                val action = JsonCodec.fromJsonObject(process.actionJson, TextProcessAction::class)
                    ?: return@forEach
                val range = findTargetRange(output, anchor) ?: return@forEach
                val next = when (action.type) {
                    TextProcessAction.TYPE_REPLACE -> {
                        output.replaceRange(
                            range,
                            normalizeProcessText(action.replacement.orEmpty())
                        )
                    }

                    TextProcessAction.TYPE_DELETE -> output.removeRange(range)
                    TextProcessAction.TYPE_INSERT_BEFORE -> {
                        output.replaceRange(
                            range.first,
                            range.first,
                            normalizeProcessText(action.text.orEmpty())
                        )
                    }

                    TextProcessAction.TYPE_INSERT_AFTER -> {
                        output.replaceRange(
                            range.last + 1,
                            range.last + 1,
                            normalizeProcessText(action.text.orEmpty())
                        )
                    }

                    else -> output
                }
                if (next != output) {
                    output = next
                    effectiveProcesses.add(process)
                }
            }
        return ApplyResult(output, effectiveProcesses)
    }

    /**
     * 在给定正文里解析锚点对应的字符区间，供渲染层把用户划线/高亮应用到该区间。
     * 容错：按文本匹配 + 就近章节位置取最近命中，跨空白归一化。
     */
    fun resolveRange(content: String, anchor: TextProcessAnchor): IntRange? =
        findTargetRange(content, anchor)

    fun normalizeProcessText(text: String): String {
        return text.lines()
            .joinToString("\n") { line ->
                line.trim { it.code <= 0x20 || it == '　' }
            }
            .trim()
    }

    private fun findTargetRange(
        content: String,
        anchor: TextProcessAnchor,
    ): IntRange? {
        val candidates = listOf(
            anchor.selectedText,
            normalizeProcessText(anchor.selectedText),
        ).map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val approximatePosition = anchor.chapterPosition ?: 0
        for (candidate in candidates) {
            val start = findClosestOccurrence(content, candidate, approximatePosition)
            if (start >= 0) {
                return start until start + candidate.length
            }
        }
        val normalizedContent = normalizeForMatching(content)
        for (candidate in candidates) {
            val normalizedCandidate = normalizeForMatching(candidate).text
            if (normalizedCandidate.isEmpty()) continue
            val range = findClosestNormalizedOccurrence(
                normalizedContent = normalizedContent,
                normalizedSelectedText = normalizedCandidate,
                approximatePosition = approximatePosition,
            )
            if (range != null) return range
        }
        return null
    }

    private fun normalizeForMatching(text: String): NormalizedText {
        val normalized = StringBuilder(text.length)
        val sourceIndices = ArrayList<Int>(text.length)
        text.forEachIndexed { index, char ->
            if (!char.isProcessWhitespace()) {
                normalized.append(char)
                sourceIndices.add(index)
            }
        }
        return NormalizedText(normalized.toString(), sourceIndices)
    }

    private fun Char.isProcessWhitespace(): Boolean {
        return isWhitespace() || this == '　'
    }

    private fun findClosestNormalizedOccurrence(
        normalizedContent: NormalizedText,
        normalizedSelectedText: String,
        approximatePosition: Int,
    ): IntRange? {
        var match = normalizedContent.text.indexOf(normalizedSelectedText)
        if (match < 0) return null
        var closestStart = normalizedContent.sourceIndices[match]
        var closestEnd = normalizedContent.sourceIndices[match + normalizedSelectedText.length - 1] + 1
        var closestDistance = abs(closestStart - approximatePosition)
        while (match >= 0) {
            val sourceStart = normalizedContent.sourceIndices[match]
            val distance = abs(sourceStart - approximatePosition)
            if (distance < closestDistance) {
                closestStart = sourceStart
                closestEnd = normalizedContent.sourceIndices[match + normalizedSelectedText.length - 1] + 1
                closestDistance = distance
            }
            match = normalizedContent.text.indexOf(normalizedSelectedText, match + 1)
        }
        return closestStart until closestEnd
    }

    private fun findClosestOccurrence(
        content: String,
        selectedText: String,
        approximatePosition: Int,
    ): Int {
        var match = content.indexOf(selectedText)
        if (match < 0) return -1
        var closest = match
        var closestDistance = abs(match - approximatePosition)
        while (match >= 0) {
            val distance = abs(match - approximatePosition)
            if (distance < closestDistance) {
                closest = match
                closestDistance = distance
            }
            match = content.indexOf(selectedText, match + 1)
        }
        return closest
    }

    private data class NormalizedText(
        val text: String,
        val sourceIndices: List<Int>,
    )
}
