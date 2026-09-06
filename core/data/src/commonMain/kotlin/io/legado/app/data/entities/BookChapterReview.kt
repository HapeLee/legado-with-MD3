package io.legado.app.data.entities

import androidx.room.ColumnInfo

class BookChapterReview(
    @ColumnInfo(defaultValue = "0")
    var bookId: Long = 0,
    var chapterId: Long = 0,
    var summaryUrl: String = "",
) {

}
