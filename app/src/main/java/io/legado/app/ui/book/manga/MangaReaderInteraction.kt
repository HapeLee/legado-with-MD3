package io.legado.app.ui.book.manga

internal fun mangaClickRegionIndex(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Int {
    val column = (x / (width.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    val row = (y / (height.coerceAtLeast(1) / 3f)).toInt().coerceIn(0, 2)
    return row * 3 + column
}

internal fun nextMangaClickAction(action: Int): Int = when (action) {
    -1 -> 0
    0 -> 1
    1 -> 2
    else -> -1
}

internal fun mangaPageStepTarget(
    currentIndex: Int,
    itemCount: Int,
    direction: Int,
): Int? {
    if (itemCount <= 0) return null
    val target = (currentIndex + direction).coerceIn(0, itemCount - 1)
    return target.takeIf { it != currentIndex }
}
