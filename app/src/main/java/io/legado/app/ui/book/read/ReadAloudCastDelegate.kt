package io.legado.app.ui.book.read

import android.content.Context
import io.legado.app.R
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.help.readaloud.cast.AiCastAssignUseCase
import io.legado.app.help.readaloud.cast.AiCastProgress
import io.legado.app.help.readaloud.cast.AiCastStream
import io.legado.app.help.readaloud.cast.AiSceneAssignUseCase
import io.legado.app.data.entities.Book
import io.legado.app.help.readaloud.cast.CastAssignmentStore
import io.legado.app.help.readaloud.cast.CastAssignmentStore.CastResult
import io.legado.app.help.readaloud.cast.BgmSceneStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多角色分配域：确认（分配 + 更新已有角色状态）/ 创建（新角色身份 = 名字+声音池）/
 * 取消分配，以及成功后的「重排当前章 + 关悬浮窗」、失败后的 toast 收尾。
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

    private var aiCastJob: kotlinx.coroutines.Job? = null

    /** AI 分配：从 startChapter 起 count 章；进度与流式文本写 AiCastProgress，完成重排+toast。 */
    fun startAiCast(request: ReadBookIntent.StartAiCast) {
        val book = ReadBook.book ?: return
        if (aiCastJob?.isActive == true) return
        val startChapter = request.startChapter
        val count = request.count
        AiCastProgress.update {
            it.copy(
                running = true,
                done = 0,
                total = count,
                lastError = null,
                finishedMessage = null,
                reasoning = "",
                reasoningFolded = 0,
                reasoningSeconds = 0,
                reasoningStartedAt = 0L,
                answer = "",
                answerFolded = 0,
            )
        }
        aiCastJob = scope.launch(Dispatchers.IO) {
            // 纯场景入口（背景音乐区的「AI 识别场景」）不跑角色那趟：用户没开多角色朗读时，
            // 认角色、建角色这些动作对他毫无意义，还会白烧一次 token
            val message = if (request.rolesPass) {
                val result = aiCastUseCase.execute(
                    book = book,
                    startChapter = startChapter,
                    chapterCount = count,
                    reassign = request.reassign,
                    presetId = request.presetId,
                    temporaryInstruction = request.temporaryInstruction,
                    // 推理强度由悬浮窗那一行单独选。以前这里拿「显示思考过程」开关顶成
                    // HIGH/OFF：开=把档位拉到最高（一章能想二十几分钟），关=OFF 在多数服务商上
                    // 根本不发参数，模型照想不误——两头都不诚实。显示与否已与它解耦。
                    reasoningLevel = request.reasoningLevel,
                    onProgress = { title, done, total, error ->
                        AiCastProgress.update {
                            it.copy(chapterTitle = title, done = done, total = total, lastError = error)
                        }
                    },
                    onStream = ::applyStreamEvent,
                )
                result.fold(
                    onSuccess = { context.getString(R.string.ai_cast_finished, it) },
                    onFailure = {
                        if (it is kotlinx.coroutines.CancellationException) null
                        else it.message ?: "AI cast failed"
                    },
                )
            } else {
                null
            }
            // 配乐清的是第二趟：与角色分配串行跑，两趟共用一份流式回显才不会互相覆盖。
            // 角色那趟被取消时协程本身已取消，这里不会执行；场景那趟的取消由 execute 抛出。
            val sceneMessage = if (request.assignScene) {
                runScenePass(
                    book = book,
                    startChapter = startChapter,
                    count = count,
                    reassign = request.reassign,
                    reasoningLevel = request.reasoningLevel,
                )
            } else {
                null
            }
            AiCastProgress.update {
                it.copy(
                    running = false,
                    finishedMessage = listOfNotNull(message, sceneMessage).joinToString("；")
                        .takeIf { text -> text.isNotBlank() },
                )
            }
            withContext(Dispatchers.Main) {
                reloadChapter()
                refreshAloudCast()
                message?.let { emitToast(it) }
            }
        }
    }

    /** 配乐场景那一趟：失败/取消只回一句话，不影响已经写好的角色分配。 */
    private suspend fun runScenePass(
        book: Book,
        startChapter: Int,
        count: Int,
        reassign: Boolean,
        reasoningLevel: AiReasoningLevel,
    ): String? {
        // 折叠计数与正文一起清：只清正文会让配乐那一趟顶着一句「前面 N 字已折叠」
        AiCastProgress.update {
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
            startChapter = startChapter,
            chapterCount = count,
            reassign = reassign,
            reasoningLevel = reasoningLevel,
            onProgress = { title, done, total, error ->
                AiCastProgress.update {
                    it.copy(chapterTitle = "配乐·$title", done = done, total = total, lastError = error)
                }
            },
            onStream = ::applyStreamEvent,
        )
        return result.fold(
            onSuccess = { context.getString(R.string.ai_scene_finished, it) },
            onFailure = {
                if (it is kotlinx.coroutines.CancellationException) null
                else it.message ?: "AI scene failed"
            },
        )
    }

    /** 两趟共用的流式回显：换章清空，reasoning/answer 各自累加。 */
    private fun applyStreamEvent(event: AiCastStream) {
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

    /** 取消进行中的 AI 分配。 */
    fun cancelAiCast() {
        aiCastJob?.cancel()
        aiCastJob = null
        AiCastProgress.update { it.copy(running = false) }
    }

    /** 删除整章分配（AI 分配悬浮窗「删除分配」），随后重排。 */
    fun deleteChapterAssignments(chapterIndex: Int, alsoScenes: Boolean) {
        val book = ReadBook.book ?: return
        scope.launch(Dispatchers.IO) {
            CastAssignmentStore.deleteChapter(book.bookUrl, chapterIndex)
            // 「删除分配」要连这次一起删的东西：勾了配乐场景（或纯场景入口）时，
            // 只删角色会让用户看到「删了但场景还在」
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
