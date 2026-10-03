package io.legado.app.help.readaloud.cast

import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.CastCharacter
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiStreamEvent
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiMessage
import io.legado.app.domain.model.AiMessageRole
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiTaskType
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AI 分配执行过程中可实时展示的流式片段（供悬浮窗显示，不参与解析）。 */
sealed interface AiCastStream {
    /** 新的一章开始请求，UI 侧据此清空上一章的流式文本。 */
    data class ChapterStart(val chapterTitle: String) : AiCastStream
    data class Reasoning(val delta: String) : AiCastStream
    data class Answer(val delta: String) : AiCastStream
}

/**
 * AI 分配角色引擎：从指定章起批量识别对话说话人并写入分配。
 *
 * 锚点与渲染侧完全一致：正文取 ContentProcessor 处理后的 textList，用同一个
 * [CastMarkers.CastQuoteTracker] 数一级开引号 → ordinal 与注入/胶囊一一对应。
 * 上下文防误判：按原文顺序整段送正文（锚点标成 `⟦i⟧`），另带上一章末尾节选续语气。
 * 模型没给的锚点一律留空走默认音，不做本地推断（一问一答的排版最容易推断错）。
 * 提示词三层：预设要求（[AiCastPresetStore]，用户可编辑）+ 临时要求（仅本次）
 * + 固定输出契约；书级记忆（[io.legado.app.data.entities.BookCastMemory]）
 * 逐章滚动更新，把同一人物的不同称呼归并为同一角色。
 * 已有角色直接复用；确属新人物才建档（身份 = 名字+池）。
 * 跑哪几章由 [castChapterPlan] 决定：连续范围（当前章 + N，或悬浮窗直接指定的起止章）
 * 或只跑失败的那几章（重试）。每章开跑前把进度写进本书 prefs
 * （[AiCastPresetStore.AiCastRunState]），取消后能原地续跑。
 */
class AiCastAssignUseCase(
    private val aiProfileGateway: AiProfileGateway,
    private val aiTextGateway: AiTextGateway,
) {

    /**
     * 每章进度回调：(chapterIndex, chapterTitle, doneChapters, totalChapters, error?)。
     *
     * [chapterIndex] 是**0 基**目录下标（与 chapter_role_assignments.chapterIndex 同一口径，
     * 显示成「第 N 章」时 +1）；执行侧据此记账哪几章失败，重试只跑这些章。
     */
    suspend fun execute(
        book: Book,
        startChapter: Int,
        chapterCount: Int,
        reassign: Boolean,
        presetId: String = "",
        temporaryInstruction: String = "",
        reasoningLevel: AiReasoningLevel = AiReasoningLevel.OFF,
        /** 显式范围终点（含，0 基）；-1 = 由 [chapterCount] 推出（旧「当前章 + N」口径）。 */
        endChapter: Int = -1,
        /** 只重跑这些章（失败重试）；非空时忽略 [startChapter] / [chapterCount] / [endChapter]。 */
        onlyChapters: List<Int> = emptyList(),
        onProgress: suspend (Int, String, Int, Int, String?) -> Unit,
        onStream: suspend (AiCastStream) -> Unit = { },
    ): Result<Int> {
        val toc = withContext(Dispatchers.IO) { appDb.bookChapterDao.getChapterList(book.bookUrl) }
        if (toc.isEmpty()) return Result.failure(IllegalStateException("目录为空"))
        val plan = castChapterPlan(toc.size, startChapter, chapterCount, endChapter, onlyChapters)
        if (plan.isEmpty()) return Result.success(0)
        val total = plan.size
        val pools = withContext(Dispatchers.IO) { VoicePoolStore.enabledPoolNames() }
        val preset = withContext(Dispatchers.IO) { AiCastPresetStore.resolvePreset(presetId) }
        val systemPrompt = AiCastPresetStore.buildSystemPrompt(
            requirement = preset?.instruction ?: AiCastPresetStore.DEFAULT_REQUIREMENT,
            temporaryInstruction = temporaryInstruction,
        )
        val failed = ArrayList<Int>()
        var assignedChapters = 0
        for ((offset, index) in plan.withIndex()) {
            // 开跑前一章先记下「跑到这儿了」：用户中途取消时这一章就是续分配的起点，
            // 悬浮窗关掉再打开也还在（见 AiCastPresetStore.AiCastRunState）
            persistRunState(book.bookUrl, plan, index, failed)
            val error = runCatching {
                assignChapter(
                    book, toc, index, reassign, pools, systemPrompt, reasoningLevel, onStream,
                )
            }.exceptionOrNull()
            // 取消不算失败：这一章没跑完，靠上面写下的 resumeChapter 续跑就够了
            if (error is CancellationException) throw error
            if (error == null) assignedChapters++ else failed += index
            onProgress(
                index,
                toc[index].title,
                offset + 1,
                total,
                // 异常没有 message 时也要给个说法，否则界面只剩「失败」两个字没有原因
                error?.let { failureReason(it) },
            )
        }
        // 整趟跑完：没有要续的章了，但失败清单要留着让重试按钮读
        persistRunState(book.bookUrl, plan, AiCastPresetStore.NO_RESUME_CHAPTER, failed)
        return Result.success(assignedChapters)
    }

    /**
     * 把本次范围 / 停在第几章 / 哪些章失败写进本书 prefs。
     *
     * 调用点在逐章循环开头（每章一次）+ 循环正常结束后一次（resume 归
     * [AiCastPresetStore.NO_RESUME_CHAPTER]），取消协程不需要任何额外收尾就能留下正确的续跑位置。
     */
    private suspend fun persistRunState(
        bookUrl: String,
        plan: List<Int>,
        resumeChapter: Int,
        failed: List<Int>,
    ) = withContext(Dispatchers.IO) {
        AiCastPresetStore.saveCastRunState(
            bookUrl,
            AiCastPresetStore.AiCastRunState(
                startChapter = plan.first(),
                endChapter = plan.last(),
                resumeChapter = resumeChapter,
                failedChapters = failed.toList(),
            ),
        )
    }

    private suspend fun assignChapter(
        book: Book,
        toc: List<BookChapter>,
        chapterIndex: Int,
        reassign: Boolean,
        pools: List<String>,
        systemPrompt: String,
        reasoningLevel: AiReasoningLevel,
        onStream: suspend (AiCastStream) -> Unit,
    ) = withContext(Dispatchers.IO) {
        // isValidName 走标记语法，先装载用户配置的符号
        CastSyntaxStore.current()
        val chapter = toc[chapterIndex]
        val chapterContent = BookHelp.getContent(book, chapter)
            ?: error("无法读取本章内容")
        // 锚点必须与渲染侧数出来的引号逐一对上：含图 / HTML / 分页标记的段落渲染器不喂跟踪器，
        // 这里如果按原始段落数，整章序号会从第一个图片段开始错位，名字全挂到别的句子上。
        val paragraphs = ReaderChapterSourceParser.castAnchorText(
            paragraphs = ContentProcessor.get(book)
                .getContent(book, chapter, chapterContent, includeTitle = false)
                .textList,
            adaptSpecialStyle = AppConfig.adaptSpecialStyle,
        )
        // 与注入侧同规则数锚点：逐段喂同一字符流，tracker 章级共享。
        // 一段可以有多个锚点，全部逐条送出（旧实现段内只保留最后一个，前面的句子 AI 从没见过）。
        val tracker = CastMarkers.CastQuoteTracker()
        val anchors = ArrayList<List<Pair<Int, Int>>>()
        // 单引号开口的那几个锚点 = 心声那一类台词：建行时默认给它套「心声混响」，
        // 这里顺手记下来是为了不必让 CastAssignmentStore.assign 每句再读一遍本章正文
        // （口径与 CastAssignmentStore.thoughtOrdinals 完全一致，判档看 CastMarkers.SingleQuoteOpens）。
        val thoughtOrdinals = HashSet<Int>()
        for (paragraph in paragraphs) {
            val hits = ArrayList<Pair<Int, Int>>()
            for ((pos, ch) in paragraph.withIndex()) {
                if (tracker.feed(ch)) {
                    hits += tracker.lastCastOrdinal to pos
                    if (ch in CastMarkers.SingleQuoteOpens) thoughtOrdinals += tracker.lastCastOrdinal
                }
            }
            anchors += hits
        }
        val windows = buildWindows(paragraphs, anchors)
        if (windows.isEmpty()) return@withContext
        val preset = aiProfileGateway.getTaskPreset(AiTaskType.IDENTIFY_CHARACTERS)
            ?: aiProfileGateway.getTaskPreset(AiTaskType.CHAT)
            ?: error("未配置 AI 模型，无法使用 AI 分配角色")
        onStream(AiCastStream.ChapterStart(chapter.title))
        // 场景头部取开头几段：只取第一段常常拿到一个光秃秃的章节号，对判断谁在说话毫无用处
        val scene = paragraphs.asSequence().filter { it.isNotBlank() }.take(SCENE_PARAGRAPHS)
            .joinToString("\n") { it.trim() }.take(SCENE_CHARS)
        val contextBefore = chapterExcerpt(toc, book, chapterIndex - 1)
        // 重新分配在发第一个请求之前就清干净：某一块失败也不该留下半新半旧的分配
        if (reassign) {
            appDb.chapterRoleAssignmentDao.deleteChapter(book.bookUrl, chapterIndex)
        }
        // 角色档案与本书记忆跨块续用：每块各拿一份空白候选，同一个人会被重复建档
        CastAssignmentStore.migrateLegacyProfiles(book.bookUrl)
        val known = appDb.castCharacterDao.getByBook(book.bookUrl).toMutableList()
        // 档案整本取一次给称呼归并用（别名在这里），逐句再查库会把一趟分配变成几百次查询
        val profiles = appDb.bookKnowledgeDao.getCharacterProfiles(book.bookUrl, 500)
        var bookMemory = appDb.bookCastMemoryDao.get(book.bookUrl)?.memory.orEmpty()
        var previousSpeakers = emptyList<AiCastPayload.Speaker>()
        for (window in windows) {
            val payload = AiCastPayload.Request(
                pools = pools,
                // 代词/「旁白」这类占位主名不送：契约要求「优先沿用已有主名」，
                // 送出「我」就等于把每一个没依据的句子都推向它
                characters = known.filterNot { isPlaceholderName(it.name) }
                    .map { AiCastPayload.Character(it.name, it.poolLabel) },
                scene = scene,
                chapterTitle = chapter.title,
                contextBefore = contextBefore,
                excerpt = window.excerpt,
                pending = window.pending,
                previousSpeakers = previousSpeakers,
                bookMemory = bookMemory,
            )
            val parsed = parseResponse(
                streamChunk(
                    model = preset.model,
                    // 除思考开关外全部沿用用户在该预设里配的参数（与原版识别角色同一写法）
                    params = preset.params.copy(
                        temperature = 0f,
                        reasoningLevel = reasoningLevel
                            .takeUnless { it == AiReasoningLevel.AUTO }
                            ?: preset.params.reasoningLevel,
                    ),
                    systemPrompt = systemPrompt,
                    payload = payload,
                    onStream = onStream,
                ),
            ) ?: error("AI 的回复里读不出 JSON（被截断或换了格式）")
            // 书级记忆滚动更新：AI 返回非空 memory 才覆盖（返回原样也安全）
            parsed.memory.takeIf { !it.isNullOrBlank() }?.let {
                AiCastPresetStore.setMemory(book.bookUrl, it)
                bookMemory = it
            }
            val speakerOf = writeAssignments(
                book.bookUrl, chapterIndex, window, parsed.assignments, pools, known,
                thoughtOrdinals, profiles,
            )
            // 下一块只带最后几条已定说话人：跨块时同一个人接着说不至于换个名字
            previousSpeakers = speakerOf.takeLast(PREVIOUS_SPEAKERS)
        }
    }

    /**
     * 一块台词一次流式请求。走流式只为把过程实时呈现出来，解析用的正文与非流式等价。
     *
     * `params` 由调用方从预设的 `preset.params` 复制而来（与官方 `IdentifyBookCharactersUseCase`
     * 同一写法：只改 temperature 与 reasoningLevel）。这里绝不自己 new 一个 AiGenerationParams：
     * 那等于把用户在 AI 设置里填的最大输出/上下文参数全部丢掉。
     */
    private suspend fun streamChunk(
        model: AiModelConfig,
        params: AiGenerationParams,
        systemPrompt: String,
        payload: AiCastPayload.Request,
        onStream: suspend (AiCastStream) -> Unit,
    ): String {
        val userJson = GSON.toJson(payload)
        // 分错了先看这里：留一份与模型收到的逐字一致的请求原文
        AiCastProgress.setRequest(systemPrompt, userJson)
        val request = AiGenerateRequest(
            model = model,
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
            // 断网 / DNS 解不开 / 超时 / 非 2xx 全在这里冒出来：原因必须原样带上去，
            // 上层是按「第 N 章：这句原因」直接显示给用户的
            error("AI 调用失败：${failureReason(e)}")
        }
        if (answer.length == 0) error("AI 没有返回任何内容（连接被中断或服务商空回）")
        return answer.toString()
    }

    /**
     * 一块的分配结果落库。
     *
     * 只认这一块送出去的锚点序号：模型常把别的块的 i 也写进来，照单全收会把别句的
     * 名字安到这句上。
     *
     * 模型没给的句子**一律留空**，不做任何本地推断——「同一人连说组」这类假设不成立：
     * 作者写一问一答时同样不加叙述行，相邻两句未必同一人。
     * 宁可让这句由默认引擎念（用户口径：只读分配到的话，其它走默认），也不能安错人。
     *
     * [thoughtOrdinals] 是本章里以单引号开口的锚点序号（由 [assignChapter] 数锚点时顺手记下的，
     * 口径见 [CastMarkers.SingleQuoteOpens]），逐条传给 [CastAssignmentStore.assign]：
     * 心声那类台词新建分配行时默认套「心声混响」，这里先算好就不用每句重读一遍正文。
     */
    private suspend fun writeAssignments(
        bookUrl: String,
        chapterIndex: Int,
        window: CastWindow,
        assignments: List<AiCastPayload.Assignment>,
        pools: List<String>,
        known: MutableList<CastCharacter>,
        thoughtOrdinals: Set<Int>,
        profiles: List<BookCharacterProfile>,
    ): List<AiCastPayload.Speaker> {
        val pending = window.pending.toSet()
        val byOrdinal = assignments
            .filter { it.i in pending }
            .associateBy({ it.i }, { it })
        val speakers = ArrayList<AiCastPayload.Speaker>()
        for (ordinal in window.pending) {
            val answer = byOrdinal[ordinal] ?: continue
            val raw = answer.name.trim()
            if (raw.isEmpty() || !CastMarkers.isValidName(raw) || isPlaceholderName(raw)) continue
            val name = raw.take(24)
            // 池只给全新角色挑；已有角色一律原样复用，否则同一个人会被换出一堆音色
            val pool = resolvePool(answer.pool, pools, known)
            var character = matchOrCreate(bookUrl, name, pool, known, profiles)
            if (character.poolLabel.isBlank() && pool.isNotBlank()) {
                // 档案里池为空时补上；非空一律不改，改了就等于换声音
                character = character.copy(poolLabel = pool, updatedAt = System.currentTimeMillis())
                appDb.castCharacterDao.update(character)
                // 补上的池也要出现在人物详情页：档案里那一列一直空着就是「没分配过」的假象
                CastProfileMirror.ensure(character)
            }
            // 音色由本地按池补（不联网、不猜），AI 永远没有改音色的权力
            if (character.voiceId.isBlank()) {
                character = CastVoicePicker.ensureVoice(character)
            }
            val knownIndex = known.indexOfFirst { it.id == character.id }
            if (knownIndex >= 0) known[knownIndex] = character else known += character
            CastAssignmentStore.assign(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                quoteOrdinal = ordinal,
                characterId = character.id,
                characterName = character.name,
                voicePoolLabel = character.poolLabel,
                thoughtQuote = ordinal in thoughtOrdinals,
            )
            speakers += AiCastPayload.Speaker(ordinal, character.name)
        }
        return speakers
    }

    /**
     * 名字命中已有角色就**原样复用**（池与音色都不动）。
     *
     * 同名命中不改池与音色：AI 给的池可能来自「用得最少的启用池」兜底，
     * 一命中就改池会让同一个名字被配出多种声音。
     * 只有本书从没出现过的名字才新建档案，此时才需要挑池。
     *
     * 匹配先过 [canonicalCastName]：AI 常按书里的叫法给称呼（「小花」而不是「李小花」），
     * 只按主名精确匹配会把同一个人拆成两条角色档案。
     */
    private suspend fun matchOrCreate(
        bookUrl: String,
        name: String,
        pool: String,
        characters: List<CastCharacter>,
        profiles: List<BookCharacterProfile>,
    ): CastCharacter {
        canonicalCastName(name, characters, profiles)
            ?.let { main -> characters.firstOrNull { it.name == main }?.let { return it } }
        val created = CastCharacter(
            id = UUID.randomUUID().toString(),
            bookUrl = bookUrl,
            name = name,
            poolLabel = pool,
        )
        appDb.castCharacterDao.insertIgnore(created)
        CastProfileMirror.ensure(created)
        return created
    }

    /**
     * AI 返回的池名 → 启用池列表里的真实名字，保证角色一定有池。
     *
     * 优先级：精确 > 去符号/全半角规范化 > 唯一包含匹配 > 本书用得最少的启用池
     * （并列按列表顺序取第一个，结果可复现）。池里有没有音色不影响候选资格，那是选音阶段的事；
     * 只有启用池列表本身为空时才允许留空。
     */
    private fun resolvePool(
        raw: String,
        pools: List<String>,
        characters: List<CastCharacter>,
    ): String {
        if (pools.isEmpty()) return ""
        val candidate = raw.trim()
        pools.firstOrNull { it == candidate }?.let { return it }
        val normalized = normalizePoolName(candidate)
        if (normalized.isNotEmpty()) {
            pools.firstOrNull { normalizePoolName(it) == normalized }?.let { return it }
            pools.filter {
                val name = normalizePoolName(it)
                name.isNotEmpty() && (name.contains(normalized) || normalized.contains(name))
            }.singleOrNull()?.let { return it }
        }
        val usage = characters.groupingBy { it.poolLabel }.eachCount()
        return pools.minByOrNull { usage[it] ?: 0 } ?: pools.first()
    }

    /** 只留字母数字并折半角，「男声·青年」/「男声-青年」/「 男声青年 」视为同名。 */
    private fun normalizePoolName(name: String): String = buildString {
        for (ch in name) {
            val folded = if (ch in '\uFF01'..'\uFF5E') ch - 0xFEE0 else ch
            if (folded.isLetterOrDigit()) append(folded.lowercaseChar())
        }
    }

    /**
     * 占位主名：代词和叙述称谓不是任何人物的名字。
     *
     * 第一人称小说里叙述者往往一半功句都是他说的，模型定不出姓名时就写「我」，这个名字一进
     * 角色档案就再也出不去（契约要求优先沿用已有主名），之后每次重跑都在复用；更糟的是它太
     * 通用，任何没依据的句子都能往它身上安——这就是「分得离谱还越改越乱」的放大器。
     * 这类名字既不送给模型，模型返回了也不落库，让那句话走默认音。
     */
    private fun isPlaceholderName(name: String): Boolean {
        val trimmed = name.trim()
        return trimmed in PlaceholderNames || trimmed.toIntOrNull() != null
    }

    /**
     * 把整章正文切成**连续的、带锚点标记的正文窗口**。
     *
     * 旧实现一句台词配「前后最近的叙述段」发出去，看着给了上下文，其实给了假上下文：
     * 一章里连着好几段对话（中间没有叙述行）时，`neighborNarrative` 会跳过所有带引号的段，
     * 于是同一组里五六个**不同说话人**拿到的 before/after 一字不差。真机对着一章群戏跑过：
     * 73 个锚点里 72 个没有段内「某某说」，第 3~7 句的 before 全是「砰！李振富元老猛地拍桌而起」，
     * 模型只能把整组都安成李振富 —— 这就是「完全没根据上下文、分得乱」的根因。
     *
     * 现在按原顺序把正文整段送出去，只在每个锚点的开引号后面插一个 `⟦序号⟧`：
     * 谁先说、谁接话、哪句后面跟着「对面另一名元老拍案而起」，模型都看得见。
     *
     * 切窗口只在不打断对话流的位置切（前后都是对话段就不切），一问一答不会被从中间劈开；
     * 真机这一章 171 段 / 73 个锚点 / 约 4000 字，一个窗口就装得下。
     */
    private fun buildWindows(
        paragraphs: List<String>,
        anchors: List<List<Pair<Int, Int>>>,
    ): List<CastWindow> {
        val lines = ArrayList<String>()
        val lineOrdinals = ArrayList<List<Int>>()
        for ((paragraphIndex, hits) in anchors.withIndex()) {
            val paragraph = paragraphs[paragraphIndex]
            if (paragraph.isBlank()) continue
            if (hits.isEmpty()) {
                lines += paragraph.trim()
                lineOrdinals += emptyList<Int>()
                continue
            }
            val startByPos = hits.associate { it.second to it.first }
            val sb = StringBuilder()
            val ids = ArrayList<Int>()
            for ((position, ch) in paragraph.withIndex()) {
                sb.append(ch)
                startByPos[position]?.let { ordinal ->
                    sb.append(ANCHOR_OPEN).append(ordinal).append(ANCHOR_CLOSE)
                    ids += ordinal
                }
            }
            lines += sb.toString()
            lineOrdinals += ids
        }
        val windows = ArrayList<CastWindow>()
        var from = 0
        var chars = 0
        var pending = 0
        for (index in lines.indices) {
            val length = lines[index].length + 1
            val midRun = pending > 0 && index > from &&
                lineOrdinals[index].isNotEmpty() && lineOrdinals[index - 1].isNotEmpty()
            if ((chars + length > EXCERPT_CHARS || pending >= MAX_PENDING) && !midRun) {
                if (pending > 0) windows += windowOf(lines, lineOrdinals, from, index)
                from = index
                chars = 0
                pending = 0
            }
            chars += length
            pending += lineOrdinals[index].size
        }
        if (pending > 0) windows += windowOf(lines, lineOrdinals, from, lines.size)
        return windows
    }

    private fun windowOf(
        lines: List<String>,
        lineOrdinals: List<List<Int>>,
        from: Int,
        to: Int,
    ): CastWindow = CastWindow(
        excerpt = (from until to).joinToString("\n") { lines[it] },
        pending = (from until to).flatMap { lineOrdinals[it] },
    )

    /** 上一章末尾节选（800 字），只用于跨章语气延续；失败给空串不阻塞。 */
    private fun chapterExcerpt(
        toc: List<BookChapter>,
        book: Book,
        index: Int,
    ): String {
        if (index < 0 || index > toc.lastIndex) return ""
        return runCatching {
            // 与本章正文同一视图：上一章末尾若是 <img> 标签，几百字 base64 会挤掉真正的语气线索
            val raw = BookHelp.getContent(book, toc[index]) ?: return ""
            ReaderChapterSourceParser.castAnchorText(
                paragraphs = raw.split('\n'),
                adaptSpecialStyle = AppConfig.adaptSpecialStyle,
            ).joinToString("\n").takeLast(800)
        }.getOrDefault("")
    }

    /**
     * 提取响应最外层 JSON：assignments 数组 + 可选 memory 更新。
     *
     * 返回 null = 回复里根本没有 JSON 对象（模型只回了一段白话、或者被截断）；
     * 结构在但读不出 assignments = 抛错。两者必须区分，否则断网重连后拿到半截回复、
     * 服务商改了输出格式这类问题，界面上一个字的提示都没有。
     */
    private fun parseResponse(raw: String): AiCastPayload.Response? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            val root = JsonParser.parseString(raw.substring(start, end + 1)).asJsonObject
            val assignments = root.getAsJsonArray("assignments")?.map { element ->
                val obj = element.asJsonObject
                AiCastPayload.Assignment(
                    i = obj.get("i").asInt,
                    name = obj.get("name").asString,
                    pool = obj.get("pool")?.asString.orEmpty(),
                )
            }.orEmpty()
            val memoryElement = root.get("memory")
            val memory = memoryElement?.takeIf { it.isJsonPrimitive }?.asString
            AiCastPayload.Response(assignments, memory)
        }.getOrElse { error("AI 返回的 JSON 读不出来：${failureReason(it)}") }
    }
}

/** 锚点标记：`⟦序号⟧` 插在开引号之后，正文里不会出现这两个括号，模型只需回序号。 */
private const val ANCHOR_OPEN = "⟦"
private const val ANCHOR_CLOSE = "⟧"

/**
 * 本次要处理的章（**0 基**、升序去重、夹到目录范围内）。角色与配乐两趟共用同一份计划，
 * 免得两边走出口径不一致。
 *
 * [onlyChapters] 非空 = 重试：只跑失败的/被跳过的这些章，不重跑整段范围。
 * 否则按范围跑：[endChapter] ≥ 0 时用显式终点（悬浮窗的「指定范围」），
 * 为 -1 时回落到旧的「当前章 + [chapterCount] - 1」。
 * 越界不报错而是夹回目录：悬浮窗已经实时显示实际章数，跑到书尾就是书尾。
 */
internal fun castChapterPlan(
    tocSize: Int,
    startChapter: Int,
    requestedCount: Int,
    endChapter: Int,
    onlyChapters: List<Int>,
): List<Int> {
    if (tocSize <= 0) return emptyList()
    val last = tocSize - 1
    if (onlyChapters.isNotEmpty()) {
        return onlyChapters.filter { it in 0..last }.distinct().sorted()
    }
    val start = startChapter.coerceIn(0, last)
    val end = (if (endChapter >= 0) endChapter else start + requestedCount - 1)
        .coerceIn(start, last)
    return (start..end).toList()
}

/**
 * 把 AI 给的称呼归并到本书已有角色的**主名**；归不到返回 null（确实是本书没出现过的人）。
 *
 * 别名权威在 `book_character_profiles.aliasesJson`（与本书角色记忆双向同步，
 * 见 [CastMemoryMirror.applyProfileToMemory]），所以这里读档案而不是读记忆文本。
 *
 * 不归并的代价不是「多一个名字」这么简单：[matchOrCreate] 会另起一条 cast_characters
 * （新 UUID、`bubbleRuleJson` 空），紧接着 [CastProfileMirror.ensure] 按名字找档案时
 * `getCharacterProfile` 连 aliasesJson 一起 LIKE，于是命中用户原来那条档案并把它改名——
 * 档案（改了名、头像还挂着）与 cast_characters（带气泡的旧行没动）就此错配：
 * 正文气泡按 cast_characters 的名字查 → 查不到；头像按档案的名字查 → 查得到；
 * 配音列表按 `profile.id` 关联角色行 → 新角色关联不上整条不显示。
 * 停用（[BookCharacterProfile.STATUS_DISABLED]）的档案不参与：那是用户从配音列表删掉的人，
 * 不能靠别名把它长回来（`deleteCharacter` 特意置成停用而不是删除以保住官方资料）。
 *
 * **档案比角色行更权威**：命中档案时先按档案的 id 找回角色，而不是先用「名字完全相等」那条。
 * 这一条同时负责修已坏数据：库里可能残留没档案的孤儿 cast_characters（名字就是那个别名、
 * 气泡为空），按名字优先会一直命中它，于是「重新分配也修不回来」；
 * 按档案找回带气泡的那一条，重跑一次那一章就正了。
 */
internal fun canonicalCastName(
    name: String,
    characters: List<CastCharacter>,
    profiles: List<BookCharacterProfile>,
): String? {
    val byName = characters.firstOrNull { it.name == name }
    val anchor = profiles.firstOrNull { profile ->
        profile.status == BookCharacterProfile.STATUS_ACTIVE &&
            (profile.name == name || profile.aliasNames().contains(name))
    } ?: return byName?.name
    val resolved = characters.firstOrNull { it.id == anchor.id }
        ?: characters.firstOrNull { it.name == anchor.name }
    return (resolved ?: byName)?.name
}

/** 档案里记的别名（JSON 字符串数组）；读坏了当没有。 */
private fun BookCharacterProfile.aliasNames(): List<String> =
    GSON.fromJsonArray<String>(aliasesJson).getOrNull().orEmpty().map { it.trim() }

/** 异常 → 给用户看的一句话原因（message 常为空，尤其是 IOException/超时之外的异常）。 */
internal fun failureReason(error: Throwable): String =
    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

/** 一次请求送多少正文 / 多少个锚点；跨块时带上文最后几条已定说话人。 */
private const val EXCERPT_CHARS = 9000
private const val MAX_PENDING = 120
private const val PREVIOUS_SPEAKERS = 12
private const val SCENE_CHARS = 200
private const val SCENE_PARAGRAPHS = 4

/** 正文窗口：带 `⟦i⟧` 标记的连续正文 + 本窗口需要回答的锚点序号。 */
private class CastWindow(val excerpt: String, val pending: List<Int>)

/**
 * 占位主名黑名单：代词与叙述称谓不是人物名字。契约里已明令禁止输出，落库前再挡一道——
 * 模型偶尔仍会把没依据的句子写成「我」，而这个名字一旦建档就再也删不干净。
 */
private val PlaceholderNames = setOf(
    "我", "你", "您", "他", "她", "它", "咱", "俺", "本人", "自己", "人家", "别人",
    "旁白", "叙述者", "叙述", "作者", "读者", "主播", "系统", "无", "未知", "说话人",
)

/**
 * AI 分配 payload/响应结构。
 *
 * 字段一律显式 @SerializedName：release 包开了 R8（app/build.gradle.kts 的 isMinifyEnabled），
 * proguard-rules.pro 只 keep 了 data.entities 和一批 DTO，本包不在内，字段名会被混淆成
 * a/b/c…。Gson 靠反射取字段名，于是模型收到的是
 * `{"a":["女童",…],"b":[{"a":"李振富","b":"男中年"}],…}`，九个键全靠猜——
 * debug / noR8 包名字完好，所以这个问题只在正式包上出现，真机验证时极易误判成提示词写得不好。
 * 解析侧不受影响：parseResponse 用 JsonParser 读字面量键名，字符串常量不参与混淆。
 */
private object AiCastPayload {
    data class Character(
        @SerializedName("name") val name: String,
        @SerializedName("pool") val pool: String,
    )

    /** 上一窗口末尾已确定的说话人，用于跨窗口接续同一个人。 */
    data class Speaker(
        @SerializedName("i") val i: Int,
        @SerializedName("name") val name: String,
    )

    /**
     * excerpt 是**按原文顺序**的正文，锚点已插成 `⟦i⟧`；pending 是本窗口要回答的序号。
     * 说话人归属几乎全在引号外的叙述行里，所以必须整段连续送，不能只送孤立台词。
     */
    data class Request(
        @SerializedName("pools") val pools: List<String>,
        @SerializedName("characters") val characters: List<Character>,
        @SerializedName("scene") val scene: String,
        @SerializedName("chapterTitle") val chapterTitle: String,
        @SerializedName("contextBefore") val contextBefore: String,
        @SerializedName("excerpt") val excerpt: String,
        @SerializedName("pending") val pending: List<Int>,
        @SerializedName("previousSpeakers") val previousSpeakers: List<Speaker>,
        @SerializedName("bookMemory") val bookMemory: String,
    )

    data class Assignment(val i: Int, val name: String, val pool: String)
    data class Response(val assignments: List<Assignment>, val memory: String?)
}
