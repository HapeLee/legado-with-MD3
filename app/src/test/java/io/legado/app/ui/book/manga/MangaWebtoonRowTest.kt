package io.legado.app.ui.book.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

class MangaWebtoonRowTest {

    private fun page(key: String) = MangaReaderItemUi.Page(
        key = key, imageUrl = "https://example.com/$key", bookUrl = "book",
        chapterIndex = 1, chapterCount = 3, pageIndex = 0, pageCount = 1,
        chapterName = "c", chapterSubject = "c",
    )

    @Test
    fun `slot height cap follows the constraints bit ladder`() {
        assertEquals(262_142, webtoonSlotHeightCap(1_260))
        assertEquals(262_142, webtoonSlotHeightCap(8_190))
        assertEquals(65_534, webtoonSlotHeightCap(8_191))
        assertEquals(65_534, webtoonSlotHeightCap(32_766))
        assertEquals(32_766, webtoonSlotHeightCap(32_767))
        assertEquals(8_190, webtoonSlotHeightCap(65_535))
        assertEquals(8_190, webtoonSlotHeightCap(262_142))
        assertEquals(1, webtoonSlotHeightCap(262_143))
    }

    @Test
    fun `issue 2475 geometry splits and stays representable at max zoom`() {
        // 1260px 宽、720x288000 整章拼接图：显示高度 504000。
        val width = 1_260
        val height = floor(width / 0.0025f).toInt()
        val count = webtoonSegmentCount(height, width)
        assertTrue("需要拆分", count > 1)
        // 每段在最大缩放下仍可表示（含 +1px 重叠）。
        for (index in 0 until count) {
            val start = webtoonSegmentStart(height, index, count)
            val end = webtoonSegmentEnd(height, index, count)
            assertTrue(end - start >= 1)
            assertTrue(
                "放大后段高越界: ${(end - start) * MAX_WEBTOON_ZOOM.toInt()}",
                (end - start) * MAX_WEBTOON_ZOOM.toInt() + 1 <= webtoonSlotHeightCap(width),
            )
        }
    }

    @Test
    fun `normal pages never split`() {
        assertEquals(1, webtoonSegmentCount(1, 1_260))
        assertEquals(1, webtoonSegmentCount(80_000, 1_260))
        // 上限/3 以内都是单行。
        assertEquals(1, webtoonSegmentCount(87_380, 1_260))
        assertEquals(2, webtoonSegmentCount(87_381, 1_260))
    }

    @Test
    fun `segment spans tile contiguously and cover the total`() {
        val total = 504_000
        val count = webtoonSegmentCount(total, 1_260)
        var previousEnd = 0
        for (index in 0 until count) {
            val start = webtoonSegmentStart(total, index, count)
            val end = webtoonSegmentEnd(total, index, count)
            assertEquals(previousEnd, start)
            previousEnd = end
        }
        assertEquals(total, previousEnd)
    }

    @Test
    fun `unknown ratio keeps single rows with page keys`() {
        val items = listOf<MangaReaderItemUi>(page("a"), page("b"))
        val rows = buildMangaWebtoonRows(items, emptyMap(), 1_260f)
        assertEquals(listOf("a", "b"), rows.map { it.key })
        assertTrue(rows.all { it.segmentIndex == null && it.segmentCount == null })
        assertEquals(0, rows.webtoonRowIndexOfPageTop(0))
        assertEquals(1, rows.webtoonRowIndexOfPageTop(1))
    }

    @Test
    fun `tall pages split into keyed segments and keep page mapping`() {
        val items = listOf<MangaReaderItemUi>(page("short"), page("tall"))
        val ratios = mapOf("short" to 0.5f, "tall" to 0.0025f)
        val rows = buildMangaWebtoonRows(items, ratios, 1_260f)
        val tallRows = rows.filter { it.pageKey == "tall" }
        assertTrue(tallRows.size > 1)
        assertEquals(tallRows.size, tallRows.first().segmentCount)
        assertEquals(
            (0 until tallRows.size).toList(),
            tallRows.mapNotNull { it.segmentIndex },
        )
        // 未拆分页行键等于页键；拆分段键携带段号且互不相同。
        assertEquals("short", rows.first().key)
        assertEquals(tallRows.size, tallRows.map { it.key }.toSet().size)
        assertNotEquals("tall", tallRows.first().key)
        // 页首行定位：短页是自身，长页是第一段。
        assertEquals(0, rows.webtoonRowIndexOfPageTop(0))
        assertEquals(1, rows.webtoonRowIndexOfPageTop(1))
        // 行反查页下标。
        assertTrue(rows.all { it.itemIndex == 0 || it.itemIndex == 1 })
    }

    @Test
    fun `row scroll target maps page offsets into segments`() {
        val items = listOf<MangaReaderItemUi>(page("tall"))
        val rows = buildMangaWebtoonRows(items, mapOf("tall" to 0.0025f), 1_260f)
        val total = floor(1_260f / 0.0025f).toInt()
        val count = rows.size
        assertTrue(count > 1)
        // 页顶 → 第一段行内偏移 0。
        assertEquals(0 to 0, webtoonRowScrollTarget(rows, "tall", 0, total))
        // 页尾 → 末段行内偏移即末段内位置。
        val lastStart = webtoonSegmentStart(total, count - 1, count)
        val target = webtoonRowScrollTarget(rows, "tall", total, total)
        assertEquals(count - 1, target?.first)
        assertEquals(total - lastStart, target?.second)
        // 越界钳制到合法范围。
        val clamped = webtoonRowScrollTarget(rows, "tall", total + 100, total)
        assertEquals(count - 1, clamped?.first)
    }

    @Test
    fun `resize target keeps page offset when a page splits`() {
        val before = listOf(MangaWebtoonRow("tall", 0, "tall", null, null))
        val after = buildMangaWebtoonRows(
            listOf(page("tall")), mapOf("tall" to 0.0025f), 1_260f,
        )
        val totalAfter = floor(1_260f / 0.0025f).toInt()
        // 比例首达：旧行是兜底高度，totalBefore 未知，锚点偏移按页顶基准保留。
        val target = webtoonResizeScrollTarget(
            before, after, "tall", 30_000, totalBefore = 0, totalAfter = totalAfter,
        )
        val targetRow = target!!.let { after[it.first] }
        val targetStart = webtoonSegmentStart(
            totalAfter, targetRow.segmentIndex!!, targetRow.segmentCount!!,
        )
        assertEquals(30_000 - targetStart, target.second)

        // 未拆分 → 未拆分：与旧行为等价（同行、同偏移）。
        val unchanged = webtoonResizeScrollTarget(
            before, listOf(MangaWebtoonRow("tall", 0, "tall", null, null)),
            "tall", 30_000, totalBefore = 0, totalAfter = 0,
        )
        assertEquals(0 to 30_000, unchanged)

        // 拆分 → 拆分（比例刷新）：页内偏移按段换算。
        val reSplit = webtoonResizeScrollTarget(
            after, after, after[2].key, 100,
            totalBefore = totalAfter, totalAfter = totalAfter,
        )
        val seg2Start = webtoonSegmentStart(totalAfter, 2, after.size)
        assertEquals(2 to 100, reSplit)
        assertTrue(seg2Start >= 0)
    }
}
