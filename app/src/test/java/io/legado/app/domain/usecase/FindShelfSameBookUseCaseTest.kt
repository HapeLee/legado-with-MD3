package io.legado.app.domain.usecase

import io.legado.app.data.entities.ShelfBookSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [matchSameBook] 的判定规则。
 *
 * 这是「在架」入口唯一的正确性来源：误命中会把用户导航到另一本书，
 * 因此「作者缺失 / 作者不同 / 排除自身 / 排序」这几条都必须锁住。
 * 纯函数，不需要 Room 或 Robolectric。
 */
class FindShelfSameBookUseCaseTest {

    private companion object {
        const val NAME = "斗破苍穹"
        const val AUTHOR = "天蚕土豆"
    }

    private fun shelf(
        bookUrl: String,
        name: String = NAME,
        author: String = AUTHOR,
        durChapterTime: Long = 0L,
    ) = ShelfBookSummary(
        bookUrl = bookUrl,
        name = name,
        author = author,
        durChapterTime = durChapterTime,
    )

    private fun match(
        summaries: List<ShelfBookSummary>,
        incomingBookUrl: String = "search-1",
        name: String = NAME,
        author: String = AUTHOR,
    ) = matchSameBook(
        summaries = summaries,
        incomingBookUrl = incomingBookUrl,
        name = name,
        author = author,
    )

    @Test
    fun `书名与作者都相同时命中`() {
        assertEquals(listOf("shelf-1"), match(listOf(shelf("shelf-1"))).map { it.bookUrl })
    }

    @Test
    fun `搜索结果缺作者时不命中`() {
        assertTrue(match(listOf(shelf("shelf-1")), author = "").isEmpty())
    }

    @Test
    fun `在架书籍缺作者时不命中`() {
        assertTrue(match(listOf(shelf("shelf-1", author = ""))).isEmpty())
    }

    @Test
    fun `作者不同时不命中`() {
        assertTrue(match(listOf(shelf("shelf-1")), author = "我吃西红柿").isEmpty())
    }

    @Test
    fun `书名不同时不命中`() {
        assertTrue(match(listOf(shelf("shelf-1")), name = "武动乾坤").isEmpty())
    }

    @Test
    fun `排除与自身 bookUrl 相同的书`() {
        assertTrue(match(listOf(shelf("same-url")), incomingBookUrl = "same-url").isEmpty())
    }

    @Test
    fun `全半角与空白差异归一化后仍命中`() {
        // 归一化只做 NFKC + 空白折叠 + 首尾 trim，不会删除字之间的空格。
        // 所以全角空格 / 连续空格会归到「单个普通空格」，
        // 但「无空格」的写法是另一个键（见 `空格有无会导致不命中`）。
        val result = match(
            summaries = listOf(shelf("shelf-1", name = "斗破 苍穹", author = "天蚕 土豆")),
            name = "斗破　苍穹",   // 全角空格
            author = "天蚕  土豆", // 连续空格
        )
        assertEquals(listOf("shelf-1"), result.map { it.bookUrl })
    }

    @Test
    fun `空格有无会导致不命中`() {
        // 与上面那条互补：这是归一化规则的边界，不是缺陷——继承上游行为，
        // 两侧空格形态必须一致才视为同一部作品。
        assertTrue(
            match(
                summaries = listOf(shelf("shelf-1", name = "斗破苍穹")),
                name = "斗破 苍穹",
            ).isEmpty()
        )
    }

    @Test
    fun `多本候按时按最后阅读时间降序`() {
        val result = match(
            listOf(
                shelf("old", durChapterTime = 100L),
                shelf("new", durChapterTime = 900L),
                shelf("mid", durChapterTime = 500L),
            )
        )
        assertEquals(listOf("new", "mid", "old"), result.map { it.bookUrl })
    }

    @Test
    fun `搜索结果书名为空时不命中`() {
        assertTrue(match(listOf(shelf("shelf-1")), name = "   ").isEmpty())
    }
}
