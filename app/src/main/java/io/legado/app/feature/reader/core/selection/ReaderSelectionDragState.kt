package io.legado.app.feature.reader.core.selection

/**
 * 区分“长按已成立”与“选区 drag 已开始”的粘性闸门，不是 Compose 状态。
 *
 * 长按通过 `startWord()` 建好初始选区后，未达 [update] 阈值的小抖动不应破坏刚选出的
 * 整词；一旦判定为 drag（或抓到了把手）就永久 started——回到原点也不会退回，避免
 * 选区在阈值附近来回跳。阈值取自 `ReaderGestureSettingsPolicy.selectionDragSlopPx`，
 * 与防误触翻页的 `pageTouchSlop` 无关。
 */
data class ReaderSelectionDragState(val started: Boolean = false) {
    fun update(
        longPressed: Boolean,
        handleGrabbed: Boolean,
        distancePx: Float,
        dragSlopPx: Float,
    ): ReaderSelectionDragState = if (
        started || handleGrabbed || (longPressed && distancePx >= dragSlopPx)
    ) copy(started = true) else this
}
