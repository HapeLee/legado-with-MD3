package io.legado.app.ui.book.read.page.provider

import android.app.Application
import io.legado.app.data.entities.BookChapter
import io.legado.app.ui.book.read.page.ReaderPageSource
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.entities.TextChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

/**
 * 阅读器核心第一批测试（Track D·D1c）：翻页边界与换章。
 *
 * 这些规则以前只能靠真机翻页试出来——`TextPageFactory` 直接调 `ReadBook`，JVM 里构造不出来。
 * D2 把页位置命令收进 [ReaderPageSource] 之后，取页器的依赖只剩两个接口，可以用假实现断言
 * 「哪个命令被下达了」，而不必真的动一本书。
 *
 * 覆盖的都是**容易回归且真机上难复现**的分支：滚动模式翻到章尾而下一章还没加载、
 * 上一章排版没完成时不许后退、章内翻页与跨章翻页走不同命令。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TextPageFactoryTest {

    private lateinit var dataSource: FakeDataSource
    private lateinit var pageSource: FakePageSource
    private lateinit var factory: TextPageFactory

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        dataSource = FakeDataSource()
        pageSource = FakePageSource()
        factory = TextPageFactory(dataSource, pageSource)
    }

    // ── hasPrev / hasNext / hasNextPlus 边界 ──────────────────────────────

    @Test
    fun `章首且没有上一章时没有上一页`() {
        dataSource.pageIndex = 0
        dataSource.hasPrev = false

        assertFalse(factory.hasPrev())
    }

    @Test
    fun `章首但有上一章时仍有上一页`() {
        dataSource.pageIndex = 0
        dataSource.hasPrev = true

        assertTrue(factory.hasPrev())
    }

    @Test
    fun `章内非首页总是有上一页`() {
        dataSource.pageIndex = 1
        dataSource.hasPrev = false

        assertTrue(factory.hasPrev())
    }

    @Test
    fun `没有当前章也没有下一章时没有下一页`() {
        dataSource.currentChapter = null
        dataSource.hasNext = false

        assertFalse(factory.hasNext())
    }

    @Test
    fun `章内非末页有下一页`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0
        dataSource.hasNext = false

        assertTrue(factory.hasNext())
    }

    @Test
    fun `排完版的末页且没有下一章时没有下一页`() {
        dataSource.currentChapter = chapter(pageCount = 3, completed = true)
        dataSource.pageIndex = 2
        dataSource.hasNext = false

        assertFalse(factory.hasNext())
    }

    @Test
    fun `还没排完版的末页仍算有下一页`() {
        // isLastIndex 要求 isCompleted——排版还在继续时不能判定「到底了」，
        // 否则用户会在章节排到一半时被挡住。
        dataSource.currentChapter = chapter(pageCount = 3, completed = false)
        dataSource.pageIndex = 2
        dataSource.hasNext = false

        assertTrue(factory.hasNext())
    }

    @Test
    fun `hasNextPlus 以倒数第二页为界`() {
        dataSource.currentChapter = chapter(pageCount = 5)
        dataSource.hasNext = false

        dataSource.pageIndex = 2
        assertTrue(factory.hasNextPlus())

        dataSource.pageIndex = 3
        assertFalse(factory.hasNextPlus())
    }

    // ── moveToNext：章内翻页 vs 跨章 ──────────────────────────────────────

    @Test
    fun `章内前进只改页码不换章`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0

        assertTrue(factory.moveToNext(upContent = true))
        assertEquals(listOf("setPageIndex(1)"), pageSource.commands)
    }

    @Test
    fun `章尾前进下达换章命令`() {
        dataSource.currentChapter = chapter(pageCount = 3, completed = true)
        dataSource.pageIndex = 2
        dataSource.nextChapter = chapter(pageCount = 2)
        dataSource.hasNext = true

        assertTrue(factory.moveToNext(upContent = true))
        assertEquals(listOf("moveToNextChapter(true)"), pageSource.commands)
    }

    @Test
    fun `滚动模式下一章还没加载出来时章尾不前进`() {
        // 滚动模式把下一章的内容接在当前章后面渲染，下一章缺席时换章会渲染出空白，
        // 所以这里必须原地不动。非滚动模式反而可以先换章再等加载。
        dataSource.isScroll = true
        dataSource.currentChapter = chapter(pageCount = 3, completed = true)
        dataSource.pageIndex = 2
        dataSource.nextChapter = null
        dataSource.hasNext = true

        assertFalse(factory.moveToNext(upContent = true))
        assertTrue(pageSource.commands.isEmpty())
    }

    @Test
    fun `没有下一页时前进不下达任何命令`() {
        dataSource.currentChapter = chapter(pageCount = 3, completed = true)
        dataSource.pageIndex = 2
        dataSource.hasNext = false

        assertFalse(factory.moveToNext(upContent = true))
        assertTrue(pageSource.commands.isEmpty())
    }

    // ── moveToPrev：章内翻页 vs 跨章 ──────────────────────────────────────

    @Test
    fun `章内后退只改页码不换章`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 2

        assertTrue(factory.moveToPrev(upContent = true))
        assertEquals(listOf("setPageIndex(1)"), pageSource.commands)
    }

    @Test
    fun `章首后退下达换章命令`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0
        dataSource.prevChapter = chapter(pageCount = 2, completed = true)
        dataSource.hasPrev = true

        assertTrue(factory.moveToPrev(upContent = true))
        assertEquals(listOf("moveToPrevChapter(true)"), pageSource.commands)
    }

    @Test
    fun `上一章排版没完成时章首不后退`() {
        // 往前翻要落在上一章的**最后一页**，页数没定下来就没法定位，
        // 所以排版未完成时原地不动等排完。
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0
        dataSource.prevChapter = chapter(pageCount = 2, completed = false)
        dataSource.hasPrev = true

        assertFalse(factory.moveToPrev(upContent = true))
        assertTrue(pageSource.commands.isEmpty())
    }

    @Test
    fun `没有上一页时后退不下达任何命令`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0
        dataSource.hasPrev = false

        assertFalse(factory.moveToPrev(upContent = true))
        assertTrue(pageSource.commands.isEmpty())
    }

    // ── upContent 的联动 ────────────────────────────────────────────────

    @Test
    fun `翻页刷内容时不重置滚动位置`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0

        factory.moveToNext(upContent = true)

        assertEquals(listOf(0 to false), dataSource.upContentCalls)
    }

    @Test
    fun `upContent 为 false 时只改位置不刷内容`() {
        dataSource.currentChapter = chapter(pageCount = 3)
        dataSource.pageIndex = 0

        factory.moveToNext(upContent = false)

        assertEquals(listOf("setPageIndex(1)"), pageSource.commands)
        assertTrue(dataSource.upContentCalls.isEmpty())
    }

    // ── moveToFirst / moveToLast ────────────────────────────────────────

    @Test
    fun `moveToLast 落在最后一页`() {
        dataSource.currentChapter = chapter(pageCount = 4)

        factory.moveToLast()

        assertEquals(listOf("setPageIndex(3)"), pageSource.commands)
    }

    @Test
    fun `没有页可落时 moveToLast 回到第一页`() {
        dataSource.currentChapter = chapter(pageCount = 0)

        factory.moveToLast()

        assertEquals(listOf("setPageIndex(0)"), pageSource.commands)
    }

    @Test
    fun `没有当前章时 moveToLast 回到第一页`() {
        dataSource.currentChapter = null

        factory.moveToLast()

        assertEquals(listOf("setPageIndex(0)"), pageSource.commands)
    }

    @Test
    fun `moveToFirst 回到第一页`() {
        factory.moveToFirst()

        assertEquals(listOf("setPageIndex(0)"), pageSource.commands)
    }

    private companion object {

        /**
         * 造一个「有 [pageCount] 页」的章节。
         *
         * 两处不得不用反射/占位，原因都是**被测逻辑之外的全局依赖**，记在这里省得下次重新踩：
         *
         * 1. `TextChapter.textPages` 是 private，只由 `TextChapterLayout` 填充（排版引擎要
         *    Canvas 和真实度量），单测够不着，所以直接往那个 list 里塞。
         * 2. 塞进去的**不是真 `TextPage`**：`TextPage` 的属性初始化器会拉起
         *    `CanvasRecorderFactory → ReadConfig → Koin`、`ChapterProvider.paddingTop →
         *    ReadBookConfig → ReadStyleConfigStore → ReadStyleRepository → Room`
         *    一整条链。而本类断言的翻页边界只用到 `pages.size`
         *    （`pageSize` / `isLastIndex` / `isLastIndexCurrent`），从不碰页对象本身，
         *    所以按 JVM 泛型擦除塞占位对象即可。
         *
         * 将来若有测试真要读页内容，这里会抛 `ClassCastException`——**响亮地**失败，
         * 不会静默给出错结论。届时该做的是给渲染实体解开构造期的全局依赖（D2/E5 议题），
         * 而不是在测试里把那条链拉起来。
         */
        fun chapter(pageCount: Int, completed: Boolean = true): TextChapter {
            val chapter = TextChapter(
                chapter = BookChapter(),
                position = 0,
                title = "章节",
                chaptersSize = 3,
                sameTitleRemoved = false,
                isVip = false,
                isPay = false,
                effectiveReplaceRules = null,
            )
            if (pageCount > 0) {
                val field = TextChapter::class.java.getDeclaredField("textPages")
                field.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val pages = field.get(chapter) as ArrayList<Any>
                repeat(pageCount) { pages.add(PAGE_COUNT_ONLY_PLACEHOLDER) }
            }
            chapter.isCompleted = completed
            return chapter
        }

        /** 只用来把 `pages.size` 撑起来，见 [chapter] 的说明 */
        val PAGE_COUNT_ONLY_PLACEHOLDER = Any()
    }

    private class FakeDataSource(
        override var pageIndex: Int = 0,
        override var currentChapter: TextChapter? = null,
        override var nextChapter: TextChapter? = null,
        override var prevChapter: TextChapter? = null,
        override var isScroll: Boolean = false,
        var hasNext: Boolean = false,
        var hasPrev: Boolean = false,
    ) : DataSource {

        val upContentCalls = mutableListOf<Pair<Int, Boolean>>()

        override fun hasNextChapter(): Boolean = hasNext

        override fun hasPrevChapter(): Boolean = hasPrev

        override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
            upContentCalls += relativePosition to resetPageOffset
        }
    }

    private class FakePageSource : ReaderPageSource {

        override var durChapterIndex: Int = 0
        override var durPageIndex: Int = 0
        override var simulatedChapterSize: Int = 0
        override var pageAnim: Int = 0
        override var msg: String? = null

        /** 下达过的页位置命令，按顺序记录——断言的是「谁被调了」而不是内部状态 */
        val commands = mutableListOf<String>()

        override fun textChapter(chapterOnDur: Int): TextChapter? = null

        override fun setPageIndex(index: Int) {
            commands += "setPageIndex($index)"
        }

        override fun moveToNextChapter(upContent: Boolean) {
            commands += "moveToNextChapter($upContent)"
        }

        override fun moveToPrevChapter(upContent: Boolean) {
            commands += "moveToPrevChapter($upContent)"
        }
    }
}
