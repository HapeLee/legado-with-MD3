package io.legado.app.ui.widget.components.privacy

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 锁定态封面必须真的被抹掉细节。
 *
 * 项目 minSdk 26，Compose 的 Modifier.blur 在 API 31 以下不生效，所以脱敏只能靠这个
 * Transformation；这里验证它对任意 API 都真的产生了混合像素，而不是原样返回。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PrivateCoverBlurTransformationTest {

    @Test
    fun `sharp edge is smoothed out`() = runTest {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    setPixel(x, y, if (x < width / 2) Color.BLACK else Color.WHITE)
                }
            }
        }

        val blurred = PrivateCoverBlurTransformation().transform(source, Size(64, 64))

        assertEquals(64, blurred.width)
        assertEquals(64, blurred.height)
        val edge = Color.red(blurred.getPixel(32, 32))
        assertNotEquals("边缘像素应被模糊成中间灰，而不是保留原始黑白", 0, edge)
        assertNotEquals("边缘像素应被模糊成中间灰，而不是保留原始黑白", 255, edge)
    }

    @Test
    fun `output keeps original size`() = runTest {
        val source = Bitmap.createBitmap(40, 56, Bitmap.Config.ARGB_8888)
        val blurred = PrivateCoverBlurTransformation().transform(source, Size(40, 56))
        assertEquals(40, blurred.width)
        assertEquals(56, blurred.height)
    }
}
