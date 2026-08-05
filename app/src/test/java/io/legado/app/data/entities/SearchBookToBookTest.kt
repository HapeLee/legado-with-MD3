package io.legado.app.data.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchBookToBookTest {

    @Test
    fun toBook_keepsListIntroSeparateFromIntro() {
        val searchBook = SearchBook(
            bookUrl = "http://example.com/book/1",
            origin = "http://example.com",
            name = "某某传",
            author = "张三",
            intro = "列表规则给的简介"
        )

        val book = searchBook.toBook()

        //详情规则随后会覆盖 intro, listIntro 必须留住列表规则那一份
        assertEquals("列表规则给的简介", book.intro)
        assertEquals("列表规则给的简介", book.listIntro)

        book.intro = "📡 当前服务：https://example.cf"
        assertEquals("列表规则给的简介", book.listIntro)
    }
}
