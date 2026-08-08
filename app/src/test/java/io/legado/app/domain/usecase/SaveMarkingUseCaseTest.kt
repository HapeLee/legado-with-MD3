package io.legado.app.domain.usecase

import io.legado.app.data.entities.BookMarking
import io.legado.app.domain.gateway.BookMarkingGateway
import io.legado.app.domain.model.TextProcessAnchor
import io.legado.app.domain.model.TextProcessStyle
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMarkingUseCaseTest {

    private val bookName = "书名"
    private val bookAuthor = "作者"
    private val bookUrl = "book-url"

    @Test
    fun `save 落 book_marks 一条标记并归一化选中文本`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)

        useCase.save(
            bookName = bookName,
            bookAuthor = bookAuthor,
            bookUrl = bookUrl,
            chapterIndex = 2,
            chapterPosition = 100,
            selectedText = "　看见桌上的信 ",
            style = TextProcessStyle(underlineMode = 1, underlineColor = 0xFFFF0000.toInt()),
            chapterName = "第三章",
            note = "这段很重要",
        )

        val marks = gateway.getByBook(bookName, bookAuthor, 2)
        assertEquals(1, marks.size)
        val mark = marks.first()
        assertEquals(bookUrl, mark.bookUrl)
        assertEquals(bookName, mark.bookName)
        assertEquals(bookAuthor, mark.bookAuthor)
        assertEquals("第三章", mark.chapterName)
        assertEquals("这段很重要", mark.note)
        val anchor = GSON.fromJsonObject<TextProcessAnchor>(mark.anchorJson).getOrNull()!!
        assertEquals(100, anchor.chapterPosition)
        assertEquals("看见桌上的信", anchor.selectedText) // trim 归一化
        val style = GSON.fromJsonObject<TextProcessStyle>(mark.styleJson).getOrNull()!!
        assertEquals(1, style.underlineMode)
        assertEquals(0xFFFF0000.toInt(), style.underlineColor)
    }

    @Test
    fun `同锚点重复保存原地更新而非重建`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)

        val first = useCase.save(
            bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
            TextProcessStyle(underlineMode = 1), "第三章", "旧备注",
        )
        val second = useCase.save(
            bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
            TextProcessStyle(bgColor = 0x33FFD54F.toInt()), "第三章", "新备注",
        )

        // 保留 id 与 createdAt，只改 style/note —— 同锚点只有一条标记
        assertEquals(first.id, second.id)
        assertEquals(first.createdAt, second.createdAt)
        assertEquals(1, gateway.getByBook(bookName, bookAuthor, 2).size)
        val style = GSON.fromJsonObject<TextProcessStyle>(second.styleJson).getOrNull()!!
        assertEquals(0x33FFD54F.toInt(), style.bgColor)
        assertEquals("新备注", second.note)
    }

    @Test
    fun `find 按锚点命中已有标记，用于再次选中划线预填`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)
        useCase.save(
            bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
            TextProcessStyle(underlineMode = 2), "第三章", "备注",
        )

        val found = useCase.find(bookName, bookAuthor, 2, 100, "看见桌上的信")
        assertEquals("备注", found?.note)

        // 位置不同则不命中（视为新标记）
        val missed = useCase.find(bookName, bookAuthor, 2, 200, "看见桌上的信")
        assertEquals(null, missed)
    }

    @Test
    fun `findById 按 id 取标记，供编辑模式预填`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)
        val mark = useCase.save(
            bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
            TextProcessStyle(underlineMode = 1), "第三章", "备注",
        )

        val found = useCase.findById(mark.id)
        assertEquals(mark.id, found?.id)
        assertEquals("备注", found?.note)
        assertEquals(null, useCase.findById("not-exist"))
    }

    @Test
    fun `delete 只删 book_marks 行`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)
        val mark = useCase.save(
            bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
            TextProcessStyle(underlineMode = 1),
        )

        useCase.delete(mark.id)

        assertTrue(gateway.getByBook(bookName, bookAuthor, 2).isEmpty())
    }

    @Test
    fun `并发保存同锚点只留一条标记`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)

        // 快速双击：两个 save 并发（内部 withContext(IO)）。Mutex 串行化后，
        // 第二个能看到第一个的结果走原地更新，而不是各自查出「无旧标记」重复插入。
        coroutineScope {
            launch {
                useCase.save(
                    bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
                    TextProcessStyle(underlineMode = 1),
                )
            }
            launch {
                useCase.save(
                    bookName, bookAuthor, bookUrl, 2, 100, "看见桌上的信",
                    TextProcessStyle(underlineMode = 2),
                )
            }
        }

        val marks = gateway.getByBook(bookName, bookAuthor, 2)
        // Mutex 串行化后不会重复插入；两次保存的先后由 IO 调度决定，样式不具确定性
        assertEquals(1, marks.size)
    }

    @Test
    fun `空白选中文本拒绝保存`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)
        var threw = false
        try {
            useCase.save(
                bookName, bookAuthor, bookUrl, 2, 100, "   ",
                TextProcessStyle(),
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
        assertTrue(gateway.getByBook(bookName, bookAuthor, 2).isEmpty())
    }

    @Test
    fun `保存锚点前后文供换源定位消歧`() = runBlocking {
        val gateway = FakeBookMarkingGateway()
        val useCase = SaveMarkingUseCase(gateway)

        val mark = useCase.save(
            bookName = bookName,
            bookAuthor = bookAuthor,
            bookUrl = bookUrl,
            chapterIndex = 2,
            chapterPosition = 100,
            selectedText = "看见桌上的信",
            style = TextProcessStyle(),
            contextBefore = "她停在门前。",
            contextAfter = "信封已经泛黄。",
        )

        val anchor = GSON.fromJsonObject<TextProcessAnchor>(mark.anchorJson).getOrNull()!!
        assertEquals("她停在门前。", anchor.contextBefore)
        assertEquals("信封已经泛黄。", anchor.contextAfter)
    }

    private class FakeBookMarkingGateway : BookMarkingGateway {
        private val marks = mutableListOf<BookMarking>()

        override suspend fun getByBook(
            bookName: String,
            bookAuthor: String,
            chapterIndex: Int?,
        ): List<BookMarking> = getByBookSync(bookName, bookAuthor, chapterIndex)

        override fun flowByBook(
            bookName: String,
            bookAuthor: String,
        ): Flow<List<BookMarking>> = flowOf(getByBookSync(bookName, bookAuthor, null))

        private fun getByBookSync(
            bookName: String,
            bookAuthor: String,
            chapterIndex: Int?,
        ): List<BookMarking> =
            marks.filter {
                it.bookName == bookName &&
                        it.bookAuthor == bookAuthor &&
                        (chapterIndex == null || it.chapterIndex == chapterIndex)
            }

        override suspend fun getById(id: String): BookMarking? =
            marks.firstOrNull { it.id == id }

        override suspend fun upsert(bookMarking: BookMarking) {
            marks.removeAll { it.id == bookMarking.id }
            marks.add(bookMarking)
        }

        override suspend fun setEnabled(id: String, enabled: Boolean) {
            marks.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                marks[i] = marks[i].copy(enabled = enabled)
            }
        }

        override suspend fun delete(id: String) {
            marks.removeAll { it.id == id }
        }
    }
}
