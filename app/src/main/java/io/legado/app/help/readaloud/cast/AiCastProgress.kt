package io.legado.app.help.readaloud.cast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AI 分配角色的共享进度状态（help 层，非 DAO）。
 *
 * 执行侧（ReadAloudCastDelegate）写入，悬浮窗 UI 直接 collectAsState——
 * 与 ReadBook.upMsg 同款的全局可观察状态，避免为一次性进度撑大 ReadBookUiState。
 */
object AiCastProgress {

    /**
     * 流式正文的显示窗口：只保留最后这么多字，更早的部分折叠成一行计数。
     *
     * 滚动窗口让这一段在长思考时永远在动，不致于内容冻住却无失败提示；
     * 被丢掉的字数用 folded 记账。
     */
    private const val MAX_TRANSCRIPT_CHARS = 4000

    /**
     * 请求回显上限：一整章带锚点的正文就有四五千字，截太短等于看不见正文，
     * 而「AI 到底收到了什么」正是分配不准时唯一要看的东西。
     */
    private const val MAX_REQUEST_CHARS = 24000

    data class State(
        val running: Boolean = false,
        val chapterTitle: String = "",
        val done: Int = 0,
        val total: Int = 0,
        val lastError: String? = null,
        /** 完成收尾消息（null = 无）。 */
        val finishedMessage: String? = null,
        /** 当前章 AI 思考过程的最新一段（模型未返回思考时为空）。 */
        val reasoning: String = "",
        /** [reasoning] 之前被折叠掉的字数。 */
        val reasoningFolded: Int = 0,
        /**
         * 本章思考耗时（秒），0 = 还在想或没量到。
         *
         * 思考过程默认收起，收起时头部只剩一个计数，所以这个数不能只靠界面的墙钟——
         * 换章时它必须归零重新计，否则后面的章会顶着上一章的秒数。
         */
        val reasoningSeconds: Int = 0,
        /** [reasoningSeconds] 的起点（本章第一个思考 token 的时间戳，0 = 本章还没有思考）。 */
        val reasoningStartedAt: Long = 0L,
        /** 当前章 AI 回答正文，逐字累加（就是最终被解析成 assignments 的那段）。 */
        val answer: String = "",
        /** [answer] 之前被折叠掉的字数。 */
        val answerFolded: Int = 0,
        /**
         * 最近一次请求的原文（系统提示词 + 用户 JSON）。分配不准时先看它：
         * 到底是正文没送全、档案里带着错名字，还是模型自己判错了。
         */
        val request: String = "",
        /**
         * 本次跑失败的章号（**0 基**，与 chapter_role_assignments.chapterIndex 同一口径；
         * 界面显示一律 +1，写入侧见 `AiCastAssignUseCase.execute` 的 onProgress）。
         * 「重试」按钮只重跑这些章，不重跑整段范围。
         */
        val failedChapters: List<Int> = emptyList(),
        /** 失败明细：每行「第 N 章《章名》：原因」，断网/连不上/AI 乱答都落在这里。 */
        val failureText: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun update(transform: (State) -> State) = _state.update(transform)

    /** 追加一段流式文本：超出窗口就丢最旧的，返回（窗口内容, 累计折叠字数）。 */
    fun append(current: String, folded: Int, delta: String): Pair<String, Int> {
        val merged = current + delta
        if (merged.length <= MAX_TRANSCRIPT_CHARS) return merged to folded
        return merged.takeLast(MAX_TRANSCRIPT_CHARS) to
            folded + (merged.length - MAX_TRANSCRIPT_CHARS)
    }

    /** 记下最近一次发给模型的原文（system + user），超长在末尾标注截断。 */
    fun setRequest(systemPrompt: String, userJson: String) = update { state ->
        val text = "【系统提示词 ${systemPrompt.length} 字】\n$systemPrompt\n\n" +
            "【用户 JSON ${userJson.length} 字】\n$userJson"
        state.copy(
            request = if (text.length <= MAX_REQUEST_CHARS) text
            else text.take(MAX_REQUEST_CHARS) + "\n…（共 ${text.length} 字，已截断）",
        )
    }

    fun reset() {
        _state.value = State()
    }
}
