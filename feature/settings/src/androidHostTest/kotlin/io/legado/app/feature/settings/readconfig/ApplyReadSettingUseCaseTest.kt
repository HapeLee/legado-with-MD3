package io.legado.app.feature.settings.readconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M5-11a：钉 `ApplyReadSettingUseCase` 那张「intent → 通知阅读器做什么」的映射表。
 *
 * 迁移前这张表**零测试** —— 它直接调 `ReadBook` / `ReadConfigUpdateBus` / `postEvent`，
 * 在 `:app` 里无从下手。本片把四个阅读器直连收成 [ReadConfigApplyPlatform] 之后，
 * 映射表成了纯逻辑 ⇒ 第一次可断言。
 *
 * ⚠️ 断言的是**调了平台的哪个方法**（而不是「有没有副作用」）：假实现只记录方法名，
 * 所以「接错线」会被抓住。⚠️ 映射表本身不是可以顺手整理的地方 —— 改错的表现是
 * 「改了设置、当前页没反应」或「无谓地重排整本书」，都不易在评审里看出来。
 */
class ApplyReadSettingUseCaseTest {

    private val calls = mutableListOf<String>()
    // 用块体而非 `= calls.add(...)`：后者返回 Boolean，与方法的 Unit 不符
    // （和 M5-7 里 `deleteRecursively()` 那次是同一类错误）。
    private val platform = object : ReadConfigApplyPlatform {
        override fun updateSystemUiAndStyle() {
            calls.add("systemUiAndStyle")
        }

        override fun updateActionBar() {
            calls.add("actionBar")
        }

        override fun reloadContent() {
            calls.add("reloadContent")
        }

        override fun updateSeekBar() {
            calls.add("seekBar")
        }

        override fun updatePageSlopSquare() {
            calls.add("pageSlopSquare")
        }

        override fun updatePageAnim(animate: Boolean) {
            calls.add("pageAnim:$animate")
        }

        override fun invalidateTextPage() {
            calls.add("invalidateTextPage")
        }
    }
    private val useCase = ApplyReadSettingUseCase(platform)

    @Test
    fun `状态栏与导航栏改动同时刷系统UI和样式`() {
        useCase(ReadConfigIntent.HideStatusBarChanged(true))
        useCase(ReadConfigIntent.HideNavigationBarChanged(false))
        assertEquals(listOf("systemUiAndStyle", "systemUiAndStyle"), calls)
    }

    @Test
    fun `菜单相关的四项改动只刷操作栏`() {
        useCase(ReadConfigIntent.ReadMenuBlurAlphaChanged(50))
        useCase(ReadConfigIntent.ReadSliderModeChanged("1"))
        useCase(ReadConfigIntent.ShowReadTitleAdditionChanged(true))
        useCase(ReadConfigIntent.ShowMenuIconChanged(false))
        assertEquals(listOf("actionBar", "actionBar", "actionBar", "actionBar"), calls)
    }

    @Test
    fun `排版相关的四项改动触发重新排版`() {
        useCase(ReadConfigIntent.TextFullJustifyChanged(true))
        useCase(ReadConfigIntent.TextBottomJustifyChanged(false))
        useCase(ReadConfigIntent.UseZhLayoutChanged(true))
        useCase(ReadConfigIntent.DoubleHorizontalPageChanged("1"))
        assertEquals(listOf("reloadContent", "reloadContent", "reloadContent", "reloadContent"), calls)
    }

    @Test
    fun `进度条行为只刷进度条`() {
        useCase(ReadConfigIntent.ProgressBarBehaviorChanged("page"))
        assertEquals(listOf("seekBar"), calls)
    }

    @Test
    fun `触摸热区只刷热区`() {
        useCase(ReadConfigIntent.PageTouchSlopChanged(12))
        assertEquals(listOf("pageSlopSquare"), calls)
    }

    @Test
    fun `下划线改动让文本页失效`() {
        useCase(ReadConfigIntent.UseUnderlineChanged(true))
        assertEquals(listOf("invalidateTextPage"), calls)
    }

    @Test
    fun `关掉翻页动画是不带参数的翻页动画刷新`() {
        // 迁移前是 `renderCallBack.upPageAnim()` —— `upPageAnim(upRecorder: Boolean = false)`，
        // 即 `false`。这里钉住：本片把默认参数显式化，值必须是 false 而不是 true。
        useCase(ReadConfigIntent.NoAnimScrollPageChanged(true))
        assertEquals(listOf("pageAnim:false"), calls)
    }

    @Test
    fun `渲染优化是先带参数的翻页动画刷新再重新排版`() {
        // 迁移前 `updateStyle()` = `upPageAnim(true)` + `loadContent(false)`，**两步且有序**。
        useCase(ReadConfigIntent.OptimizeRenderChanged(true))
        assertEquals(listOf("pageAnim:true", "reloadContent"), calls)
    }

    @Test
    fun `不在映射表里的设置改动不通知阅读器`() {
        // 这些设置进的是 RenderStyle 快照 / 下次打开才生效，改了**不该**触发任何重排
        // （迁移前也是 `else -> Unit`）。
        useCase(ReadConfigIntent.ClickImgWayChanged("1"))
        useCase(ReadConfigIntent.MouseWheelPageChanged(false))
        useCase(ReadConfigIntent.SelectTextChanged(true))
        useCase(ReadConfigIntent.PageKeysChanged("1", "2"))
        useCase(ReadConfigIntent.EyeProtectionEnabledChanged(true))
        assertTrue("这些都不该通知阅读器，实际调了：$calls", calls.isEmpty())
    }
}
