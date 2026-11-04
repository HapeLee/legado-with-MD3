package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.text.font.FontSynthesis
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.robolectric.Shadows
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// application = Application::class 与其余 Robolectric 测试一致：不指定的话 Robolectric 会从
// manifest 取真的 io.legado.app.App，App.onCreate() 要拉 Koin/DB/Cronet，单测里必炸。
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class LoadCustomFontTest {

    @Test
    fun wrapsFontPathInFontListWithSingleNormalEntry() {
        // 单 Normal 声明的 FontList 才能让 Bold 请求走 synthesizeTypeface 合成
        // 假粗；FontFamily(typeface)（LoadedFontFamily）忽略字重——加粗静默
        // 丢失的回归锚点（loadCustomFont 的产物即该形态，此处直接以真实字体
        // 文件构造同一形态做 resolve 级验证的入口）
        val fontList = FontFamily(Font(realFontFile())) as FontListFontFamily
        assertEquals(1, fontList.fonts.size)
        assertEquals(FontWeight.Normal, fontList.fonts.first().weight)
    }

    private fun realFontFile(): File {
        // 用应用自带的真实字体（assets/font/number.ttf）落盘成 Font(File) 路径
        val target = File(RuntimeEnvironment.getApplication().cacheDir, "real-number.ttf")
        RuntimeEnvironment.getApplication().assets.open("font/number.ttf").use { input ->
            target.outputStream().use(input::copyTo)
        }
        return target
    }

    @Test
    fun resolvesNormalAndSynthesizesBoldWithRealFont() {
        val context = RuntimeEnvironment.getApplication()
        val family = FontFamily(Font(realFontFile()))

        // FontListFontFamilyTypefaceAdapter 对单 Normal 声明做 synthesizeTypeface：
        // Normal 取字体本体，Bold 合成假粗（isBold 置位）。FontFamily(typeface)
        // 路径（LoadedFontFamily）对两个请求原样返回同一 Typeface、isBold 恒
        // false——即本 PR 修复的加粗静默丢失
        val resolver = createFontFamilyResolver(context)
        val normal = resolver.resolveAsTypeface(
            family!!, FontWeight.Normal, FontStyle.Normal, FontSynthesis.All,
        ).value
        val bold = resolver.resolveAsTypeface(
            family, FontWeight.Bold, FontStyle.Normal, FontSynthesis.All,
        ).value
        // Robolectric 的 shadow 不为合成粗体置 isBold 标志，可断言的区分点是
        // 实例：FontList 单 Normal 声明下 Bold 请求经 Typeface.create 合成返回
        // 新实例；被修的 FontFamily(typeface)（LoadedFontFamily）路径对两个
        // 请求返回同一 Typeface 实例（字重被忽略）
        assertTrue(normal !== bold)
    }

    @Test
    fun missingFontFileFallsBackToNull() {
        val context = RuntimeEnvironment.getApplication()
        // Font(File) 构造不读盘，缺失文件必须在此拦截回退 null（系统默认字体），
        // 否则失败被推迟到布局期
        assertNull(loadCustomFont(context, "/data/local/tmp/not-exist.ttf"))
    }

    @Test
    fun legacyContentUriCopiedToCacheFileThenLoads() {
        val context = RuntimeEnvironment.getApplication()
        val bytes = context.assets.open("font/number.ttf").use { it.readBytes() }
        val uri = Uri.parse("content://mock/fonts/number.ttf")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())

        val family = loadCustomFont(context, uri.toString())
        assertNotNull(family)

        val resolver = createFontFamilyResolver(context)
        val normal = resolver.resolveAsTypeface(
            family!!, FontWeight.Normal, FontStyle.Normal, FontSynthesis.All,
        ).value
        assertTrue(!normal.isBold)
    }
}
