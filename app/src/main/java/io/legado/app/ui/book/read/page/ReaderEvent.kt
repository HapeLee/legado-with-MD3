package io.legado.app.ui.book.read.page

/**
 * `ReadView` 的出站业务意图（Track D·D1，见 docs/dev/mad-modernization-plan.md §Track D）。
 *
 * `ReadView` 只做绘制/手势/动画，不认识 `ReadBook`/`ReadAloud`：手势判定出的业务命令以事件发出，
 * 由外层（`ReadBookController`）翻译成 Intent 或会话调用。
 *
 * 不属于业务状态的**瞬时 UI 副作用**（系统栏可见性、息屏计时、文本选择菜单等）不走这里，
 * 仍由 `ReadView.CallBack` 与宿主直接协作。
 */
sealed interface ReaderEvent {

    data object ShowActionMenu : ReaderEvent

    data object AutoPageStop : ReaderEvent

    data object OpenChapterList : ReaderEvent

    data object OpenContentEdit : ReaderEvent

    data object OpenSearch : ReaderEvent

    data object AddBookmark : ReaderEvent

    data object ChangeReplaceRuleState : ReaderEvent

    data object NextChapter : ReaderEvent

    data object PrevChapter : ReaderEvent

    data object ReadAloudPrevParagraph : ReaderEvent

    data object ReadAloudNextParagraph : ReaderEvent

    data object ToggleReadAloudPause : ReaderEvent

    data object SyncProgress : ReaderEvent
}

fun interface ReaderEventListener {
    fun onEvent(event: ReaderEvent)
}
