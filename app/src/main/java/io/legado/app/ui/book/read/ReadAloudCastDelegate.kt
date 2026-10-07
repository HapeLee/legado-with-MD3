package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.help.readaloud.cast.AiCastAssignUseCase
import io.legado.app.help.readaloud.cast.AiCastPresetStore
import io.legado.app.help.readaloud.cast.AiCastProgress
import io.legado.app.help.readaloud.cast.AiCastStream
import io.legado.app.help.readaloud.cast.AiSceneAssignUseCase
import io.legado.app.data.entities.Book
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.help.readaloud.cast.CastAssignmentStore.CastResult
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.help.readaloud.cast.castChapterPlan
import io.legado.app.help.readaloud.cast.failureReason
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多角色分配域：确认（分配 + 更新已有角色状态）/ 创建（新角色身份 = 名字+声音池）/
 * 取消分配，以及 AI 批量分配的成功与取消收尾（同一套「重排当前章 + 朗读队列」，见
 * [refreshAfterCast]）与失败提示。
 *
 * DAO 访问收口在 CastAssignmentStore（架构护栏）；章节重排、关窗与 toast 三条通道
 * 由 VM 以 lambda 注入（`_effects` / `contentProcessDelegate` 只有 VM 能碰）。
 * VM 侧只剩三个意图分支的单行转发。
 */
class ReadAloudCastDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val aiCastUseCase: AiCastAssignUseCase,
    private val aiSceneUseCase: AiSceneAssignUseCase,
    private val reloadChapter: () -> Unit,
    private val sendIntent: (ReadBookIntent) -> Unit,
    private val emitToast: (String) -> Unit,
) {

    /** 确认 = 分配这句话 + 更新已有角色状态（永不创建）。 */
    fun confirm(intent: ReadBookIntent.ConfirmRoleCast) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            val result = CastAssignmentStore.confirm(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                quoteOrdinal = intent.ordinal,
                selectedCharacterId = intent.selectedCharacterId,
                characterName = intent.characterName,
                voicePoolLabel = intent.voicePoolLabel,
                voiceId = intent.voiceId,
                voiceEffect = intent.voiceEffect,
            )
            applyResult(result)
        }
    }

    /** 创建 = 新增配音角色并分配给这句话。 */
    fun create(intent: ReadBookIntent.CreateRoleCast) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            val result = CastAssignmentStore.create(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                quoteOrdinal = intent.ordinal,
                characterName = intent.characterName,
                voicePoolLabel = intent.voicePoolLabel,
                voiceId = intent.voiceId,
                voiceEffect = intent.voiceEffect,
            )
            applyResult(result)
        }
    }

    /** cast 系意图统一入口（ReadBookViewModel 行数预算，域内分发在此收口）。 */
    fun onCastIntent(intent: ReadBookIntent) {
        when (intent) {
            ReadBookIntent.OpenAiCastDialog ->
                sendIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AiCastDialog()))
            ReadBookIntent.OpenAiSceneDialog ->
                sendIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AiCastDialog(sceneOnly = true)))
            is ReadBookIntent.StartAiCast -> startAiCast(intent)
            ReadBookIntent.CancelAiCast -> cancelAiCast()
            is ReadBookIntent.DeleteChapterCastAssignments ->
                deleteChapterAssignments(intent.chapterIndex, intent.alsoScenes)
            is ReadBookIntent.UnassignRoleCast -> unassign(intent.ordinal)
            is ReadBookIntent.SetBgmScene -> setBgmScene(intent)
            is ReadBookIntent.ClearBgmScene -> clearBgmScene(intent.paragraphIndex)
            is ReadBookIntent.UpdateBgmScene -> updateBgmScene(intent)
            is ReadBookIntent.DeleteBgmScene -> deleteBgmScene(intent.paragraphIndex)
            else -> Unit
        }
    }

    private var aiCastJob: Job? = null

    /** 收尾协程（等 worker 退出 → 重排 → 提示）；与 [aiCastJob] 分开存，见 [cancelAiCast]。 */
    private var windDownJob: Job? = null

    /** 最近一次分配请求：取消时要按同一份计划算出停在第几章（见 [snapshotOnCancel]）。 */
    private var lastCastRequest: ReadBookIntent.StartAiCast? = null

    /**
     * 本次分配的编号：每次开始 +1，每次取消也 +1。
     *
     * 取消之后那半截协程可能还挂着（正在等的回包未必立刻断），晚到的回调会把 running 顶回 true、
     * 把已经取消的内容写进进度与失败清单——所有进度写入只认当前编号那一次，见 [isLiveRun]。
     */
    private var castRun = 0

    private fun isLiveRun(run: Int) = run == castRun

    /**
     * AI 分配：跑 [request] 指定的那些章（范围或重试清单）。
     *
     * 进度、流式文本与**失败明细**写 [AiCastProgress]；收尾统一走 [refreshAfterCast]，
     * 取消走的是同一份收尾（见 [cancelAiCast]）。
     */
    fun startAiCast(request: ReadBookIntent.StartAiCast) {
        val book = ReadBook.book ?: return
        if (aiCastJob?.isActive == true) return
        // 上一次取消的收尾还没跑完就重开：那份收尾会把「已取消」的 toast 打进这一趟里
        windDownJob?.cancel()
        windDownJob = null
        lastCastRequest = request
        val run = ++castRun
        // 界面先按请求算一个章数，真正以 use case 回调里的 total 为准（范围会被夹到目录末尾）
        val planned = when {
            request.onlyChapters.isNotEmpty() -> request.onlyChapters.size
            request.endChapter >= 0 -> request.endChapter - request.startChapter + 1
            else -> request.count
        }
        AiCastProgress.update {
            it.copy(
                running = true,
                done = 0,
                total = planned,
                lastError = null,
                finishedMessage = null,
                failedChapters = emptyList(),
                failureText = "",
                reasoning = "",
                reasoningFolded = 0,
                reasoningSeconds = 0,
                reasoningStartedAt = 0L,
                answer = "",
                answerFolded = 0,
            )
        }
        aiCastJob = scope.launch(Dispatchers.IO) {
            // 0 基章号，两趟共用一份：重试按钮只跑这些章
            val failed = sortedSetOf<Int>()
            // 纯场景入口（背景音乐区的「AI 识别场景」）不跑角色那趟：用户没开多角色朗读时，
            // 认角色、建角色这些动作对他毫无意义，还会白烧一次 token
            val message = if (request.rolesPass) {
                val result = aiCastUseCase.execute(
                    book = book,
                    startChapter = request.startChapter,
                    chapterCount = request.count,
                    reassign = request.reassign,
                    presetId = request.presetId,
                    temporaryInstruction = request.temporaryInstruction,
                    // 推理强度与「显示思考过程」各自独立：强度只由悬浮窗那一行的
                    // reasoningLevel 决定，开关仅控制是否展示思考内容。
                    reasoningLevel = request.reasoningLevel,
                    endChapter = request.endChapter,
                    onlyChapters = request.onlyChapters,
                    onProgress = { index, title, done, total, error ->
                        reportChapter(run, index, title, done, total, error, failed)
                    },
                    onStream = { event -> applyStreamEvent(run, event) },
                )
                result.fold(
                    onSuccess = { context.getString(R.string.ai_cast_finished, it) },
                    onFailure = {
                        if (it is CancellationException) null
                        else it.message?.takeIf { text -> text.isNotBlank() }
                            ?: failureReason(it)
                    },
                )
            } else {
                null
            }
            // 配乐清的是第二趟：与角色分配串行跑，两趟共用一份流式回显才不会互相覆盖。
            // 角色那趟被取消时协程本身已取消，这里不会执行；场景那趟的取消由 execute 抛出。
            val sceneMessage = if (request.assignScene) {
                runScenePass(
                    run = run,
                    book = book,
                    request = request,
                    failed = failed,
                )
            } else {
                null
            }
            if (!isLiveRun(run)) return@launch
            val finished = listOfNotNull(message, sceneMessage).joinToString("；")
            AiCastProgress.update {
                it.copy(
                    running = false,
                    finishedMessage = finished.takeIf { text -> text.isNotBlank() },
                )
            }
            val toast = if (failed.isEmpty()) {
                finished.takeIf { text -> text.isNotBlank() }
            } else {
                // 断网 / 连不上 AI / 回复读不出来：一条原因直接进 toast，其余在悬浮窗里逐章看
                (
                    context.getString(R.string.ai_cast_run_failed, failed.size) + "：" +
                        AiCastProgress.state.value.failureText
                            .lines().filter { line -> line.isNotBlank() }.joinToString("；")
                    ).take(MAX_TOAST_CHARS)
            }
            withContext(Dispatchers.IO) {
                // 失败清单落盘：只有角色那一趟逐章写进度，配乐那一趟的失败不在这儿补一次，
                // 重开悬浮窗就看不到这些章（续跑起点已经由角色那趟写完，这里只当它跑干净了）
                val stored = AiCastPresetStore.loadCastRunState(book.bookUrl)
                AiCastPresetStore.saveCastRunState(
                    book.bookUrl,
                    stored.copy(
                        resumeChapter = AiCastPresetStore.NO_RESUME_CHAPTER,
                        failedChapters = failed.toList(),
                    ),
                )
            }
            withContext(Dispatchers.Main) {
                refreshAfterCast()
                toast?.let { emitToast(it) }
            }
        }
    }

    /**
     * 配乐那一趟：失败只记账并回一句话，不影响已经写好的角色分配。
     * 范围/重试清单与角色那一趟同源（都读 [ReadBookIntent.StartAiCast]），否则两趟会跑不同的章。
     */
    private suspend fun runScenePass(
        run: Int,
        book: Book,
        request: ReadBookIntent.StartAiCast,
        failed: MutableSet<Int>,
    ): String? {
        // 折叠计数与正文一起清：只清正文会让配乐那一趟顶着一句「前面 N 字已折叠」
        if (isLiveRun(run)) AiCastProgress.update {
            it.copy(
                reasoning = "",
                reasoningFolded = 0,
                reasoningSeconds = 0,
                reasoningStartedAt = 0L,
                answer = "",
                answerFolded = 0,
                lastError = null,
            )
        }
        val result = aiSceneUseCase.execute(
            book = book,
            startChapter = request.startChapter,
            chapterCount = request.count,
            reassign = request.reassign,
            reasoningLevel = request.reasoningLevel,
            endChapter = request.endChapter,
            onlyChapters = request.onlyChapters,
            onProgress = { index, title, done, total, error ->
                reportChapter(run, index, title, done, total, error, failed, titlePrefix = SCENE_PREFIX)
            },
            onStream = { event -> applyStreamEvent(run, event) },
        )
        return result.fold(
            onSuccess = { context.getString(R.string.ai_scene_finished, it) },
            onFailure = {
                if (it is CancellationException) null
                else it.message?.takeIf { text -> text.isNotBlank() } ?: failureReason(it)
            },
        )
    }

    /**
     * 逐章回调：进度 + 失败明细（章号、章名、原因一个都不少）。
     *
     * 一章失败既不中断循环也不静默吞掉：界面读 failureText、toast 读条数、
     * 重试按钮读 [AiCastProgress.State.failedChapters]。
     * [run] 不是当前那次（已经取消或已经重开）就整条丢弃，见 [isLiveRun]。
     */
    private fun reportChapter(
        run: Int,
        chapterIndex: Int,
        title: String,
        done: Int,
        total: Int,
        error: String?,
        failed: MutableSet<Int>,
        titlePrefix: String = "",
    ) {
        if (!isLiveRun(run)) return
        val line = error?.let {
            context.getString(R.string.ai_cast_chapter_failed, chapterIndex + 1, "$titlePrefix$title", it)
        }
        if (error != null) failed += chapterIndex
        AiCastProgress.update { state ->
            state.copy(
                chapterTitle = titlePrefix + title,
                done = done,
                total = total,
                lastError = line,
                failedChapters = failed.toList(),
                failureText = if (line == null) {
                    state.failureText
                } else {
                    (state.failureText.lines() + line)
                        .filter { text -> text.isNotBlank() }
                        .takeLast(MAX_FAILURE_LINES)
                        .joinToString("\n")
                },
            )
        }
    }

    /** 两趟共用的流式回显：换章清空，reasoning/answer 各自累加。过期那一次的尾巴直接丢。 */
    private fun applyStreamEvent(run: Int, event: AiCastStream) {
        if (!isLiveRun(run)) return
        when (event) {
            is AiCastStream.ChapterStart -> AiCastProgress.update {
                it.copy(
                    reasoning = "",
                    reasoningFolded = 0,
                    answer = "",
                    answerFolded = 0,
                    reasoningSeconds = 0,
                    reasoningStartedAt = 0L,
                )
            }

            is AiCastStream.Reasoning -> AiCastProgress.update {
                val (text, folded) = AiCastProgress.append(it.reasoning, it.reasoningFolded, event.delta)
                it.copy(
                    reasoning = text,
                    reasoningFolded = folded,
                    reasoningStartedAt = if (it.reasoningStartedAt > 0L) {
                        it.reasoningStartedAt
                    } else {
                        System.currentTimeMillis()
                    },
                )
            }

            is AiCastStream.Answer -> AiCastProgress.update {
                val (text, folded) = AiCastProgress.append(it.answer, it.answerFolded, event.delta)
                it.copy(
                    answer = text,
                    answerFolded = folded,
                    // 正文一开始回，思考这一段就到头了：把耗时定格，之后显示的是「想了多久」而不是还在走的秒表
                    reasoningSeconds = if (it.reasoningSeconds > 0 || it.reasoningStartedAt == 0L) {
                        it.reasoningSeconds
                    } else {
                        ((System.currentTimeMillis() - it.reasoningStartedAt) / 1000L)
                            .toInt()
                            .coerceAtLeast(1)
                    },
                )
            }
        }
    }

    /**
     * 取消进行中的 AI 分配。
     *
     * **界面在取消这一刻就解锁**（`running = false` + 「已取消，停在第 N 章」）：那半截协程可能
     * 还挂在一个不会再来的回包上，等它退出才改状态就成了「点取消没反应、重试与继续也按不动」，
     * 只能关窗退回书架重进。已经跑完的章按原样留着，续分配的起点按取消时的进度算。
     *
     * **取消仍要跑完和正常结束同一套收尾**（[refreshAfterCast]），只是挪到等协程退出之后。
     * 分配行是逐章写库的，正在处理的那一章已经写了半章；而正文里的角色胶囊是分页时从库里现取现注入的
     * （`ReadBook.contentLoadFinish` → `CastAssignmentStore.labelsForChapter`），分页结果又按
     * [io.legado.app.model.reader.ReaderChapterInput] 的章节身份缓存。协程被 cancel 掉以后
     * 原先排在末尾的那句 reloadChapter() 根本不会执行，旧页就一直挂着「未分配」——退出重进也不变
     * （同书重进走 `initBook` 的 isSameBook 分支，只 `upContent` 不重排），翻一章再翻回来才对。
     */
    fun cancelAiCast() {
        val worker = aiCastJob
        aiCastJob = null
        if (worker == null) {
            // 已经在收尾（或压根没在跑）：收尾协程负责重排，这里再发一次只会多排一遍
            if (windDownJob?.isActive != true) AiCastProgress.update { it.copy(running = false) }
            return
        }
        // 先作废这次运行的编号：协程退出前的最后一次回调不该再把 running 顶回 true
        castRun++
        val snapshot = snapshotOnCancel(ReadBook.book)
        val resume = snapshot?.resumeChapter ?: AiCastPresetStore.NO_RESUME_CHAPTER
        val cancelledMessage = if (resume >= 0) {
            context.getString(R.string.ai_cast_cancelled, resume + 1)
        } else {
            null
        }
        AiCastProgress.update {
            it.copy(running = false, finishedMessage = cancelledMessage)
        }
        worker.cancel()
        windDownJob?.cancel()
        // 收尾协程单独一个字段：连点两次「取消」不会把正在跑的那次收尾也撤掉
        windDownJob = scope.launch {
            // 等被取消的协程真的退出：它还在写库时先重排会读到半章数据
            worker.join()
            if (snapshot != null) persistCancelSnapshot(snapshot)
            withContext(Dispatchers.Main) {
                refreshAfterCast()
                cancelledMessage?.let { emitToast(it) }
            }
        }
    }

    /**
     * 取消那一刻的快照：章号计划与停在第几章在界面解锁时就算好（读的是**本次运行**的 `done`，
     * 不是库里那一份——配乐那一趟不写进度，只有按本次计划取才能两趟都对）；
     * 落盘要等协程真退出之后再做（[persistCancelSnapshot]），两件事不能挤在一趟里。
     */
    private fun snapshotOnCancel(book: Book?): CancelSnapshot? {
        val request = lastCastRequest ?: return null
        val tocSize = book?.totalChapterNum ?: return null
        val plan = castChapterPlan(
            tocSize = tocSize,
            startChapter = request.startChapter,
            requestedCount = request.count,
            endChapter = request.endChapter,
            onlyChapters = request.onlyChapters,
        )
        val state = AiCastProgress.state.value
        return CancelSnapshot(
            bookUrl = book.bookUrl,
            startChapter = plan.firstOrNull(),
            endChapter = plan.lastOrNull(),
            resumeChapter = plan.getOrNull(state.done) ?: AiCastPresetStore.NO_RESUME_CHAPTER,
            failedChapters = state.failedChapters,
        )
    }

    /** 把取消时的进度写回本书 prefs：悬浮窗关掉重开还能接着跑。 */
    private suspend fun persistCancelSnapshot(snapshot: CancelSnapshot) {
        withContext(Dispatchers.IO) {
            val stored = AiCastPresetStore.loadCastRunState(snapshot.bookUrl)
            AiCastPresetStore.saveCastRunState(
                snapshot.bookUrl,
                stored.copy(
                    startChapter = snapshot.startChapter ?: stored.startChapter,
                    endChapter = snapshot.endChapter ?: stored.endChapter,
                    resumeChapter = snapshot.resumeChapter,
                    // 本次跑真的失败了几个就以本次为准；一个都没有（纯取消）保留上一次那份清单
                    failedChapters = snapshot.failedChapters.ifEmpty { stored.failedChapters },
                ),
            )
        }
    }

    /** [snapshotOnCancel] 与 [persistCancelSnapshot] 之间传的那一份。 */
    private data class CancelSnapshot(
        val bookUrl: String,
        val startChapter: Int?,
        val endChapter: Int?,
        val resumeChapter: Int,
        val failedChapters: List<Int>,
    )

    /**
     * 改动落库后的可见收尾：重排当前章窗口（±1），正在朗读时连带重排朗读队列。
     *
     * 调用方必须在主线程，且**任何**写完分配行的路径都要调一次（成功、部分成功、取消都一样）：
     * 不调的就是「胶囊没跟着更新」那个 bug。窗口外的章不用管，翻到时 [ReadBook.loadContent]
     * 会重新按库里的分配注入标记。
     */
    private fun refreshAfterCast() {
        reloadChapter()
        refreshAloudCast()
    }

    /** 删除整章分配（AI 分配悬浮窗「删除分配」），随后重排。 */
    fun deleteChapterAssignments(chapterIndex: Int, alsoScenes: Boolean) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            CastAssignmentStore.deleteChapter(book.bookUrl, chapterIndex)
            // 「删除分配」要连这次一起删的东西：勾了配乐场景（或纯场景入口）时，
            // 只删角色会留下场景，表现为「删了但场景还在」
            if (alsoScenes) BgmSceneStore.clearChapter(book.bookUrl, chapterIndex)
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
            }
        }
    }

    /** 取消一句话的分配并重排当前章。 */
    fun unassign(ordinal: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            CastAssignmentStore.unassign(book.bookUrl, ReadBook.durChapterIndex, ordinal)
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /** 确认/创建共用收尾：成功重排+关窗；失败 toast 说明原因并保持悬浮窗打开。 */
    /**
     * 段首配乐：只写 bgm_scene_marks，正文文本一个字符都不动（胶囊是零语义字符的视觉 span），
     * 所以这里同样只需重排当前章，不需要碰朗读内容。
     */
    fun setBgmScene(intent: ReadBookIntent.SetBgmScene) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            if (intent.poolName.isBlank() && intent.trackName.isBlank()) {
                BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, intent.paragraphIndex)
            } else {
                BgmSceneStore.put(
                    bookUrl = book.bookUrl,
                    chapterIndex = ReadBook.durChapterIndex,
                    ordinal = intent.paragraphIndex,
                    poolName = intent.poolName,
                    trackName = intent.trackName,
                    volume = intent.volume,
                )
            }
            withContext(Dispatchers.Main) {
                reloadChapter()
                emitToast(context.getString(R.string.cast_bgm_scene_saved))
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /** 清除一段的配乐分配并重排当前章。 */
    fun clearBgmScene(paragraphIndex: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, paragraphIndex)
            withContext(Dispatchers.Main) {
                reloadChapter()
                sendIntent(ReadBookIntent.DismissSheet)
            }
        }
    }

    /**
     * 总览里就地改一段（池/曲目/音量）：写库 + 重排，但不关窗、不弹 toast。
     *
     * 不关窗是因为总览要连着改好几段；重排是池/曲目变了段首胶囊的文字要跟着改（音量不在
     * 胶囊文字里，重排只是顺带，朗读中的配乐轨由 BgmSceneStore.version 自己发现）。
     */
    fun updateBgmScene(intent: ReadBookIntent.UpdateBgmScene) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            if (intent.poolName.isBlank() && intent.trackName.isBlank()) return@launch
            BgmSceneStore.put(
                bookUrl = book.bookUrl,
                chapterIndex = ReadBook.durChapterIndex,
                ordinal = intent.paragraphIndex,
                poolName = intent.poolName,
                trackName = intent.trackName,
                volume = intent.volume,
            )
            withContext(Dispatchers.Main) { reloadChapter() }
        }
    }

    /** 总览里删除一段配乐：重排让胶囊消失，但总览窗口保持打开。 */
    fun deleteBgmScene(paragraphIndex: Int) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            BgmSceneStore.clear(book.bookUrl, ReadBook.durChapterIndex, paragraphIndex)
            withContext(Dispatchers.Main) { reloadChapter() }
        }
    }


    /**
     * 让改动出声：正在朗读时按当前朗读位置重排本章的朗读队列。
     *
     * 只重排队列，不重开服务、不回到章首——正在播的那句仍用旧音色播完，改动从下一句生效。
     * 配乐轨不在这儿管（它自己靠 BgmSceneStore.version 发现改动）。
     */
    private fun refreshAloudCast() {
        if (BaseReadAloudService.isRun) ReadAloud.refreshCastQueue(context)
    }

    private suspend fun applyResult(result: CastResult) {
        if (result == CastResult.OK) {
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
                sendIntent(ReadBookIntent.DismissSheet)
            }
            return
        }
        val messageRes = when (result) {
            CastResult.INVALID_NAME -> R.string.cast_invalid_name
            CastResult.NOT_FOUND -> R.string.cast_confirm_not_found
            CastResult.EXISTS -> R.string.cast_create_exists
            CastResult.OK -> return
        }
        val message = context.getString(messageRes)
        withContext(Dispatchers.Main) { emitToast(message) }
    }
}

/** 配乐那一趟的章名前缀：两趟共用一份进度，靠它区分「角色·第 N 章」还是「配乐·第 N 章」。 */
private const val SCENE_PREFIX = "配乐·"

/** 失败明细在悬浮窗里最多留几行（一章一行，再多就把卡片顶到状态栏）。 */
private const val MAX_FAILURE_LINES = 12

/** 失败汇总 toast 的字数上限：toast 太长会被系统截断，剩下的在悬浮窗里逐章看。 */
private const val MAX_TOAST_CHARS = 400
