package io.legado.app.help.readaloud.cast

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 分配背景音乐场景：从指定章起，按正文情绪/场景转折在若干段落上换背景音乐池。
 *
 * 与 AI 分配角色同一套走法（逐章请求、流式回显、锚点用段落序号），差别有两点：
 * 1. 锚点是**段落序号**（[BgmSceneStore.ordinals]），不是开引号序号——换音乐看的是段落，
 *    一句话换一次音乐没有意义；
 * 2. 池名**只认用户已有的背景音乐池**，认不出来就丢弃这条。角色池可以让本地兜底补一个，
 *    音乐池补错了会播出一段用户根本没打算用的曲子。
 */
class AiSceneAssignUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) {

    /**
     * 配乐那一趟与角色那一趟跑同一份计划（[castChapterPlan]）：悬浮窗选了范围就两趟都按范围走，
     * 点重试也只重跑失败的那几章。进度回调的 [onProgress] 第一个参数是 0 基章号，
     * 口径与 `AiCastAssignUseCase.execute` 完全一致。
     */
    suspend fun execute(
        book: Book,
        startChapter: Int,
        chapterCount: Int,
        reassign: Boolean,
        reasoningLevel: AiReasoningLevel = AiReasoningLevel.OFF,
        endChapter: Int = -1,
        onlyChapters: List<Int> = emptyList(),
        onProgress: suspend (Int, String, Int, Int, String?) -> Unit,
        onStream: suspend (AiCastStream) -> Unit = { },
    ): Result<Int> {
        val toc = withContext(Dispatchers.IO) { appDb.bookChapterDao.getChapterList(book.bookUrl) }
        if (toc.isEmpty()) return Result.failure(IllegalStateException("目录为空"))
        val plan = castChapterPlan(toc.size, startChapter, chapterCount, endChapter, onlyChapters)
        if (plan.isEmpty()) return Result.success(0)
        val total = plan.size
        val pools = withContext(Dispatchers.IO) { BgmPoolStore.enabledPoolNames() }
        if (pools.isEmpty()) {
            return Result.failure(IllegalStateException("还没有可用的背景音乐池，请先在背景音乐池里创建"))
        }
        var written = 0
        for ((offset, index) in plan.withIndex()) {
            val error = runCatching {
                assignChapter(book, toc, index, reassign, pools, reasoningLevel, onStream)
            }.exceptionOrNull()
            if (error is CancellationException) throw error
            if (error == null) written++
            onProgress(
                index,
                toc[index].title,
                offset + 1,
                total,
                error?.let { failureReason(it) },
            )
        }
        return Result.success(written)
    }

    private suspend fun assignChapter(
        book: Book,
        toc: List<BookChapter>,
        chapterIndex: Int,
        reassign: Boolean,
        pools: List<String>,
        reasoningLevel: AiReasoningLevel,
        onStream: suspend (AiCastStream) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val chapter = toc[chapterIndex]
        val content = BookHelp.getContent(book, chapter) ?: error("无法读取本章内容")
        val paragraphs = ContentProcessor.get(book)
            .getContent(book, chapter, content, includeTitle = false)
            .textList
        val ordinals = BgmSceneStore.ordinals(paragraphs)
        val rows = paragraphs.mapIndexed { index, text -> ordinals[index] to text.trim() }
            .filter { it.first >= 0 }
        if (rows.isEmpty()) return@withContext

        val preset = aiProfileGateway.getTaskPreset(AiTaskType.IDENTIFY_CHARACTERS)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("未配置 AI 模型，无法使用 AI 分配场景")
        // 提示词整段都在可编辑预设行里（分配要求管理页的「输出格式要求（背景音乐）」）；
        // 取一次，回显与发送用同一份，别出现看到的和发出去的不一样
        val systemPrompt = AiCastPresetStore.scenePrompt()
        // 除思考开关外全部沿用用户在该预设里配的参数（与原版识别角色同一写法）
        val params = preset.params.copy(
            temperature = 0f,
            reasoningLevel = reasoningLevel
                .takeUnless { it == AiReasoningLevel.AUTO } ?: preset.params.reasoningLevel,
        )
        onStream(AiCastStream.ChapterStart(chapter.title))
        // 重分配 = 先把本章原有的段前场景清空（这些全是分配出来的，没有手工数据要保）。
        // 清空要在「这次有没有结果」之前：模型这一章一条都没给时，旧场景也要跟着清掉，
        // 否则重新分配会表现为无作用。
        if (reassign) {
            BgmSceneStore.clearChapter(book.bookUrl, chapterIndex)
        }
        // 降频游标：上一条留下的池与它的段序号。跨块不重置，否则每块开头都算「刚换过池」，
        // 块边界上会连着切两次。
        var lastKeptPool: String? = null
        var lastKeptOrdinal = -1
        // 整章分块送完：超出单块容量（MAX_PARAGRAPHS）的正文也要送出，不落掉后半章
        for (chunk in rows.chunked(MAX_PARAGRAPHS)) {
            // 起点池：上一块末尾在用的池，没有才回头看上一章末尾在用的
            val previousPool = lastKeptPool ?: if (chapterIndex > 0) {
                appDb.bgmSceneDao.getChapter(book.bookUrl, chapterIndex - 1)
                    .lastOrNull { it.poolName.isNotBlank() }?.poolName.orEmpty()
            } else {
                ""
            }
            val payload = AiScenePayload.Request(
                pools = pools,
                chapterTitle = chapter.title,
                previousPool = previousPool,
                contextBefore = chapterExcerpt(toc, book, chapterIndex - 1, tail = true),
                paragraphs = chunk.map { (ordinal, text) ->
                    AiScenePayload.Paragraph(ordinal, text.take(PARAGRAPH_CHARS))
                },
                contextAfter = chapterExcerpt(toc, book, chapterIndex + 1, tail = false),
            )
            val userJson = GSON.toJson(payload)
            // 与 AI 分配角色同一个悬浮窗回显：分错了要能看见模型到底收到了什么
            AiCastProgress.setRequest(systemPrompt, userJson)
            val request = AiGenerateRequest(
                model = preset.model,
                messages = listOf(
                    AiMessage(AiMessageRole.SYSTEM, systemPrompt),
                    AiMessage(AiMessageRole.USER, userJson),
                ),
                params = params,
            )
            val answer = StringBuilder()
            try {
                aiTextGateway.generateStream(request).collect { event ->
                    when (event) {
                        is AiStreamEvent.Reasoning -> onStream(AiCastStream.Reasoning(event.text))
                        is AiStreamEvent.Content -> {
                            answer.append(event.text)
                            onStream(AiCastStream.Answer(event.text))
                        }

                        is AiStreamEvent.ToolCallDelta -> Unit
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 断网 / 连不上 / 超时 / 非 2xx 都从这儿出去，原因原样带进「第 N 章：…」提示
                error("AI 调用失败：${failureReason(e)}")
            }
            if (answer.length == 0) error("AI 没有返回任何内容（连接被中断或服务商空回）")
            // 只认真正送出去的那一段段号：模型串到别块的序号一律丢
            val valid = chunk.map { it.first }.toSet()
            for (item in parseResponse(answer.toString()).filter { it.i in valid }) {
                val pool = resolvePool(item.pool, pools) ?: continue
                // 同池连续两条 = 根本没切换，留着只会让总览和朗读都变啰嗦
                if (pool == lastKeptPool) continue
                // 切得太密的一律丢掉：模型很爱在每一小段对话后「换一个氛围」，
                // 但两三次呼吸就换一首曲子听感是灾难，间隔由代码兜底而不是指望提示词
                if (lastKeptPool != null && item.i - lastKeptOrdinal < MIN_SCENE_GAP) continue
                BgmSceneStore.put(book.bookUrl, chapterIndex, item.i, pool, "")
                lastKeptPool = pool
                lastKeptOrdinal = item.i
            }
        }
    }

    /** 精确 → 去符号/全半角规范化；都不中就丢弃这条（宁可没音乐，也不要放错音乐）。 */
    private fun resolvePool(raw: String, pools: List<String>): String? {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return null
        pools.firstOrNull { it == candidate }?.let { return it }
        val normalized = fold(candidate)
        if (normalized.isEmpty()) return null
        return pools.firstOrNull { fold(it) == normalized }
            ?: pools.singleOrNull { fold(it).contains(normalized) || normalized.contains(fold(it)) }
    }

    private fun fold(name: String): String = buildString {
        for (ch in name) {
            val folded = if (ch in '\uFF01'..'\uFF5E') ch - 0xFEE0 else ch
            if (folded.isLetterOrDigit()) append(folded.lowercaseChar())
        }
    }

    private fun chapterExcerpt(toc: List<BookChapter>, book: Book, index: Int, tail: Boolean): String {
        if (index < 0 || index > toc.lastIndex) return ""
        return runCatching {
            val content = BookHelp.getContent(book, toc[index]) ?: return ""
            // 段落里的 <img> 换成一个占位符：否则几百字 base64 把真正的情绪线索挤出 600 字窗口
            val text = ReaderChapterSourceParser.castAnchorText(
                paragraphs = content.split('\n'),
                adaptSpecialStyle = AppConfig.adaptSpecialStyle,
            ).joinToString("\n")
            if (tail) text.takeLast(600) else text.take(600)
        }.getOrDefault("")
    }

    /** 取响应里最外层 JSON 的 scenes 数组；解析失败给空列表（一章失败不影响后面的章）。 */
    private fun parseResponse(raw: String): List<AiScenePayload.Scene> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        return runCatching {
            JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject
                .getAsJsonArray("scenes")?.map { element ->
                    val obj = element.asJsonObject
                    AiScenePayload.Scene(
                        i = obj.get("i").asInt,
                        pool = obj.get("pool")?.asString.orEmpty(),
                    )
                }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private companion object {

        const val MAX_PARAGRAPHS = 400
        const val PARAGRAPH_CHARS = 120

        /**
         * 两次换曲之间最少隔多少段。
         *
         * 一章正文通常一两百段，10 段意味着一章最多换十几次；模型给得再碎也会被夹到这里。
         * 太小就成了「每句对话换一次音乐」，听感比没有音乐更糟。
         *
         * 注意：默认提示词里写的「至少隔 10 个段落」只是**告诉模型**的口径，用户可编辑；
         * 这一条才是真正生效的钳制。改了提示词里的数字不会改变这里。
         */
        const val MIN_SCENE_GAP = 10
    }
}

/**
 * AI 分配场景的 payload / 响应结构。
 *
 * @SerializedName 是必须的，不是风格问题：release 包开了 R8，本包不在 proguard-rules.pro 的
 * keep 名单里，字段名会被混淆成 a/b/c…，Gson 靠反射取字段名，模型就只收到一串单字母键。
 * 见 [AiCastPayload] 的同款说明。
 */
private object AiScenePayload {
    data class Paragraph(
        @SerializedName("i") val i: Int,
        @SerializedName("text") val text: String,
    )

    data class Request(
        @SerializedName("pools") val pools: List<String>,
        @SerializedName("chapterTitle") val chapterTitle: String,
        @SerializedName("previousPool") val previousPool: String,
        @SerializedName("contextBefore") val contextBefore: String,
        @SerializedName("paragraphs") val paragraphs: List<Paragraph>,
        @SerializedName("contextAfter") val contextAfter: String,
    )

    data class Scene(val i: Int, val pool: String)
}
