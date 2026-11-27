package io.legado.app.domain.model

internal object LocalBookshelfSort {
    const val FILE_SIZE = 6

    fun isMetric(sort: Int): Boolean = sort == FILE_SIZE

    fun resolveEffectiveSort(groupBookSort: Int?, globalSort: Int): Int =
        if (groupBookSort != null && groupBookSort >= 0) groupBookSort else globalSort

    fun resolveBaseSort(globalSort: Int): Int =
        if (isMetric(globalSort)) 0 else globalSort
}
