package io.legado.app.help.source

import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.BookSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSourceGetBookTypeTest {

    @Test
    fun videoSourceMapsToVideoBookType() {
        val source = BookSource(
            bookSourceUrl = "https://example.com",
            bookSourceType = BookSourceType.video,
        )
        assertEquals(BookType.video, source.getBookType())
    }

    @Test
    fun audioSourceStillMapsToAudioBookType() {
        val source = BookSource(
            bookSourceUrl = "https://example.com",
            bookSourceType = BookSourceType.audio,
        )
        assertEquals(BookType.audio, source.getBookType())
    }
}
