package io.legado.app.ui.theme

import android.app.Application
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
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
        // Font(File) 构造不读盘（字体加载在 Compose 解析期），路径真实性不影响
        // 构造形态断言；真机 appFontPath 恒为 /data/... 无 scheme 形态
        val family = loadCustomFont(
            RuntimeEnvironment.getApplication(),
            "/data/local/tmp/custom-font.ttf",
        )

        // 单 Normal 声明的 FontList 才能让 Bold 请求走 synthesizeTypeface 合成
        // 假粗；FontFamily(typeface)（LoadedFontFamily）忽略字重——加粗静默
        // 丢失的回归锚点
        val fontList = family as FontListFontFamily
        assertEquals(1, fontList.fonts.size)
        assertEquals(FontWeight.Normal, fontList.fonts.first().weight)
    }
}
