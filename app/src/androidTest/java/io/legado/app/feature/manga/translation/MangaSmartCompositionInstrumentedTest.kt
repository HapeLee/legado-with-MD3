package io.legado.app.feature.manga.translation

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.repository.manga.translation.MangaPageComposer
import io.legado.app.data.repository.manga.translation.pixels
import io.legado.app.domain.model.manga.MangaAiTypesetting
import io.legado.app.domain.model.manga.MangaTextBox
import io.legado.app.domain.model.manga.MangaTranslatedRegion
import io.legado.app.domain.model.manga.MangaTranslationPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「本地擦字与排版」与「覆盖原文与排版」共用同一份局部几何，只有擦字与背景填充不同。
 * 一旦两条路径的墨迹包围盒出现差异，用户看到的就是"译文偏移"，所以把它钉在测试里。
 */
@RunWith(AndroidJUnit4::class)
class MangaSmartCompositionInstrumentedTest {
    private val width = 300
    private val height = 200

    @Test fun smartAndPatchPlaceTheSameTextInsideTheSameBox() {
        val box = MangaTextBox(200f, 250f, 800f, 750f)
        val region = MangaTranslatedRegion("r0", "これは原文です", "这是一段需要换行的中文译文。", box)
        val plan = MangaTranslationPlan(listOf(region))

        val smart = blank()
        val patch = blank()
        try {
            MangaPageComposer.erase(smart, plan)
            MangaPageComposer.draw(smart, plan, patch = false, fontScale = 1f)
            MangaPageComposer.draw(patch, plan, patch = true, fontScale = 1f)

            val smartInk = inkBounds(smart)
            val patchInk = inkBounds(patch)
            assertNotNull("SMART 未画出任何文字", smartInk)
            assertNotNull("PATCH 未画出任何文字", patchInk)
            assertEquals("SMART 与 PATCH 的墨迹包围盒不一致", patchInk, smartInk)
        } finally {
            smart.recycle(); patch.recycle()
        }
    }

    @Test fun smartLeavesPixelsOutsideTheTextAreaUntouched() {
        val box = MangaTextBox(200f, 250f, 800f, 750f)
        val region = MangaTranslatedRegion("r0", "これは原文です", "译文", box)
        val plan = MangaTranslationPlan(listOf(region))
        val bitmap = blank()
        try {
            val before = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
            MangaPageComposer.erase(bitmap, plan)
            MangaPageComposer.draw(bitmap, plan, patch = false, fontScale = 1f)
            val after = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
            val area = box.pixels(width, height)
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                if (after[i] != before[i] && !area.contains(x, y)) {
                    throw AssertionError("SMART 改动了文字区域之外的像素 ($x,$y)")
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun aiTypesettingKeepsItsPositionWhenOnlyTheBackgroundDiffers() {
        val box = MangaTextBox(200f, 250f, 800f, 750f)
        val style = MangaAiTypesetting(lines = listOf("这是译文第一行", "第二行"), fontSize = 120f)
        val region = MangaTranslatedRegion("r0", "これは原文です", "这是译文第一行第二行", box, typesetting = style)
        val plan = MangaTranslationPlan(listOf(region))
        val smart = blank()
        val patch = blank()
        try {
            MangaPageComposer.erase(smart, plan)
            MangaPageComposer.draw(smart, plan, patch = false, fontScale = 1f)
            MangaPageComposer.draw(patch, plan, patch = true, fontScale = 1f)
            val smartInk = inkBounds(smart)
            val patchInk = inkBounds(patch)
            assertNotNull("SMART 未画出任何文字", smartInk)
            assertEquals("AI 排版下 SMART 与 PATCH 的文字位置不一致", patchInk, smartInk)
        } finally {
            smart.recycle(); patch.recycle()
        }
    }

    /** AI 排版按 layoutBox 定位，不再按原文中心校正；框偏了多少，字就偏多少。 */
    @Test fun aiTypesettingFollowsTheAuthoredLayoutBox() {
        val text = MangaTextBox(200f, 250f, 800f, 750f)
        val layout = MangaTextBox(200f, 450f, 800f, 950f)
        val style = MangaAiTypesetting(lines = listOf("译文"), fontSize = 100f)
        val region = MangaTranslatedRegion("r0", "原文", "译文", text, layoutBox = layout, typesetting = style)
        val plan = MangaTranslationPlan(listOf(region))
        val bitmap = blank()
        try {
            MangaPageComposer.erase(bitmap, plan)
            MangaPageComposer.draw(bitmap, plan, patch = false, fontScale = 1f)
            val ink = inkBounds(bitmap)
            assertNotNull("未画出文字", ink)
            val area = layout.pixels(width, height)
            val source = text.pixels(width, height)
            assertTrue("墨迹应落在 layoutBox 内: $ink / $area", area.contains(ink!!))
            assertTrue("墨迹中心应明显低于 textBox 中心", ink.centerY() > source.centerY() + 10)
        } finally {
            bitmap.recycle()
        }
    }

    private fun blank() = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.WHITE) }

    private fun inkBounds(bitmap: Bitmap): Rect? {
        var left = Int.MAX_VALUE; var top = Int.MAX_VALUE; var right = -1; var bottom = -1
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val color = bitmap.getPixel(x, y)
            if (Color.red(color) < 120) {
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        return if (right < 0) null else Rect(left, top, right + 1, bottom + 1)
    }
}
