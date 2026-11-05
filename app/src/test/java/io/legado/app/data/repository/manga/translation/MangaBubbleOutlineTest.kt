package io.legado.app.data.repository.manga.translation

import android.app.Application
import android.graphics.*
import io.legado.app.domain.model.manga.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 「本地轮廓推断」只用源图像素：新增来源不得改变既有来源的气泡几何。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaBubbleOutlineTest {
    private val text = MangaTextBox(375f, 466f, 625f, 554f)

    private fun plan() = MangaTranslationPlan(listOf(MangaTranslatedRegion("r0", "HELLO", "你好", text)))

    /** 竖长闭合气泡：只有显式选择该来源时才写入 mask。 */
    @Test fun `outline source persists a mask only when it is selected`() {
        val bitmap = balloon()
        try {
            val previous = MangaPageComposer.prepare(bitmap, plan(), emptyList(), 1f, MangaBubbleSource.DEVICE)
            assertNull("既有来源的行为不能被这条新来源带动", previous.regions.single().bubbleShape)

            val outlined = MangaPageComposer.prepare(bitmap, plan(), emptyList(), 1f, MangaBubbleSource.OUTLINE)
            val shape = outlined.regions.single().bubbleShape
            assertNotNull("闭合气泡必须能只靠像素得到轮廓", shape)
            shape!!.validate()
            // 轮廓必须比整页紧，并真正包住原文。
            assertTrue(shape.bounds.left > 100f && shape.bounds.right < 900f)
            assertEquals(1, outlined.regions.size)
        } finally { bitmap.recycle() }
    }

    /** AI 排版自带 layoutBox：该来源只补 mask，不能挪动模型给的坐标。 */
    @Test fun `outline source adds a mask without moving AI layout boxes`() {
        val bitmap = balloon()
        try {
            val typesetting = MangaAiTypesetting(lines = listOf("你好"), fontSize = 24f)
            val region = MangaTranslatedRegion("r0", "HELLO", "你好", text, layoutBox = text, typesetting = typesetting)
            val outlined = MangaPageComposer.prepare(bitmap, MangaTranslationPlan(listOf(region)), emptyList(), 1f, MangaBubbleSource.OUTLINE)
            val result = outlined.regions.single()
            assertNotNull(result.bubbleShape)
            assertEquals(text, result.textBox)
            assertEquals(text, result.layoutBox)
            assertEquals(typesetting, result.typesetting)
        } finally { bitmap.recycle() }
    }

    /** 没有闭合容器（背景一直延伸到裁剪边缘）时必须放弃，等价于既有行为。 */
    @Test fun `outline source keeps lettering on open artwork unchanged`() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 24f }
            Canvas(bitmap).drawText("HELLO", 124f, 132f, paint)

            val previous = MangaPageComposer.prepare(bitmap, plan(), emptyList(), 1f, MangaBubbleSource.DEVICE)
            val outlined = MangaPageComposer.prepare(bitmap, plan(), emptyList(), 1f, MangaBubbleSource.OUTLINE)
            assertNull(outlined.regions.single().bubbleShape)
            assertEquals(previous.regions, outlined.regions)
        } finally { bitmap.recycle() }
    }

    /** 浅灰页面上的白底黑描边竖长气泡。 */
    private fun balloon(): Bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.LTGRAY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE
        Canvas(this).drawOval(RectF(94f, 14f, 226f, 222f), paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; paint.color = Color.BLACK
        Canvas(this).drawOval(RectF(94f, 14f, 226f, 222f), paint)
        paint.style = Paint.Style.FILL; paint.textSize = 24f
        Canvas(this).drawText("HELLO", 124f, 132f, paint)
    }
}
