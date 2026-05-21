package io.legado.app.data.repository

import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.main.bookshelf.BookShelfItem
import io.legado.app.utils.cnCompare
import kotlin.math.max

class BookshelfRepository {
    fun sortBooks(
        list: List<BookShelfItem>,
        group: BookGroup?,
        sort: Int,
        sortOrder: Int
    ): List<BookShelfItem> {
        val bookSort = if (group != null && group.bookSort >= 0) {
            group.bookSort
        } else {
            sort
        }
        val isDescending = sortOrder == 1

        return when (bookSort) {
            1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
            else list.sortedBy { it.latestChapterTime }

            2 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
            else
                list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

            3 -> if (isDescending) list.sortedByDescending { it.order }
            else list.sortedBy { it.order }

            4 -> if (isDescending) list.sortedByDescending {
                max(
                    it.latestChapterTime,
                    it.durChapterTime
                )
            }
            else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

            5 -> if (isDescending)
                list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
            else
                list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }

            else -> if (isDescending) list.sortedByDescending { it.durChapterTime }
            else list.sortedBy { it.durChapterTime }
        }
    }
}
