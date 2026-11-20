package io.legado.app.ui.main

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.oxygenx.theme.OxygenTheme
import io.oxygenx.ui.navigation.TopAppBar
import io.oxygenx.ui.navigation.rememberTopAppBarState
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

/**
 * 验证首页连同 OxygenX 制品渲染的标题块落位。
 *
 * 横划顶栏的对齐框架是「栏头（指示器带）以下的内容区」：标题 + 副标题作为一块在内容区居中，
 * 副标题行盒始终预留，所以栏高与整块中心都不随这一页有没有副标题变化；缺副标题时标题在
 * 预留块里居中。这里只断言结构性不变量与令牌推导出的中心，具体像素校准留在 OxygenX 侧。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainPagerTopBarTitleAlignmentTest {
    @get:Rule
    val compose = createComposeRule()

    /** 从组合里读到的横划栏令牌，避免在测试里重复写死 dp。 */
    private data class ThemeMetrics(
        val indicatorBand: Dp,
        val collapsedHeight: Dp,
        val expandedHeight: Dp,
    )

    private val metrics = AtomicReference<ThemeMetrics?>()

    @Composable
    private fun Bar(
        tag: String,
        title: String,
        withSubtitle: Boolean,
        collapsed: Boolean,
    ) {
        val host = remember { MainPagerTopBarHost() }
        val pagerState = rememberPagerState { 2 }
        val appBarState = rememberTopAppBarState(
            initialCollapsedFraction = if (collapsed) 1f else 0f
        )
        SideEffect {
            host.update(
                page = 0,
                chrome = MainPagerPageChrome(
                    subtitle = if (withSubtitle) "$title 副标题" else null,
                    actions = {},
                    bottomContent = {},
                ),
            )
        }
        MainPagerTopBar(
            pagerState = pagerState,
            titles = persistentListOf(title, "发现"),
            host = host,
            appBarState = appBarState,
            modifier = Modifier.testTag(tag),
        )
    }

    private fun barBounds(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun textBounds(text: String) =
        compose.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** 「标题 + 副标题」整块的中心（相对栏顶）；没有副标题时就是标题自己的中心。 */
    private fun blockCenter(tag: String, title: String, subtitle: String?): Dp {
        val barTop = barBounds(tag).top
        val top = textBounds(title).top
        val bottom = textBounds(subtitle ?: title).bottom
        return (top + bottom) / 2 - barTop
    }

    /** 整块中心应落在「指示器带 + 栏高」这段内容区的正中。 */
    private fun assertBlockCenteredInContent(
        tag: String,
        title: String,
        subtitle: String?,
        barHeight: Dp,
        label: String,
    ) {
        val expected = requireNotNull(metrics.get()) { "未从组合中读到 OxygenX 令牌" }
        val expectedCenter = (expected.indicatorBand + barHeight) / 2
        val actual = blockCenter(tag, title, subtitle)
        assertTrue(
            "$label 未落在内容区中心: 期望 $expectedCenter，实际 $actual",
            abs((actual - expectedCenter).value) <= 2f,
        )
    }

    @Test
    fun centersTitleBlockAndKeepsCollapsedGeometry() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = "oxygen")),
                darkTheme = false,
                applyBackground = false,
            ) {
                metrics.set(
                    ThemeMetrics(
                        indicatorBand = OxygenTheme.spacing.appBarPagerIndicatorBand,
                        collapsedHeight = OxygenTheme.spacing.appBarPagerCollapsedHeight,
                        expandedHeight = OxygenTheme.layout.appBarLargeHeight,
                    ),
                )
                Column(Modifier.fillMaxSize().background(Color.White)) {
                    Bar("bar-expanded-sub", "标题甲", withSubtitle = true, collapsed = false)
                    Bar("bar-expanded-nosub", "标题乙", withSubtitle = false, collapsed = false)
                    Bar("bar-collapsed-sub", "标题丙", withSubtitle = true, collapsed = true)
                    Bar("bar-collapsed-nosub", "标题丁", withSubtitle = false, collapsed = true)
                    TopAppBar(
                        title = { io.oxygenx.ui.basic.Text("标准双行") },
                        subtitle = { io.oxygenx.ui.basic.Text("标准副标题") },
                        modifier = Modifier.testTag("standard-sub"),
                    )
                }
            }
        }
        compose.waitForIdle()
        val theme = requireNotNull(metrics.get()) { "未从组合中读到 OxygenX 令牌" }

        // 展开态：双行页与单行页的整块中心都落在内容区正中。
        assertBlockCenteredInContent("bar-expanded-sub", "标题甲", "标题甲 副标题", theme.expandedHeight, "展开双行")
        assertBlockCenteredInContent("bar-expanded-nosub", "标题乙", null, theme.expandedHeight, "展开单行")

        // 收拢态：栏高只由令牌决定，有/无副标题都一样。
        val collapsedSub = barBounds("bar-collapsed-sub")
        val collapsedNoSub = barBounds("bar-collapsed-nosub")
        assertTrue(
            "收拢横划栏高: 期望 ${theme.collapsedHeight}，双行 ${collapsedSub.bottom - collapsedSub.top}，" +
                "单行 ${collapsedNoSub.bottom - collapsedNoSub.top}",
            abs(((collapsedSub.bottom - collapsedSub.top) - theme.collapsedHeight).value) <= 1f &&
                abs(((collapsedNoSub.bottom - collapsedNoSub.top) - theme.collapsedHeight).value) <= 1f,
        )

        compose.onNodeWithText("标题丙 副标题", useUnmergedTree = true).assertExists()

        // 对齐单位是「标题 + 副标题」整块：双行页的块中心与单行页的标题中心重合，
        // 也就是缺副标题时标题在预留块里居中，而不是贴着块顶、把空白全留在下面。
        val subBlockCenter = blockCenter("bar-collapsed-sub", "标题丙", "标题丙 副标题")
        val noSubBlockCenter = blockCenter("bar-collapsed-nosub", "标题丁", null)
        assertTrue(
            "收拢态块中心不一致: 双行 $subBlockCenter，单行 $noSubBlockCenter",
            abs((subBlockCenter - noSubBlockCenter).value) <= 1.5f,
        )
        assertTrue(
            "单行页标题应落在预留块正中（低于双行页标题）: ${textBounds("标题丁").top} vs ${textBounds("标题丙").top}",
            textBounds("标题丁").top > textBounds("标题丙").top,
        )

        // 收拢横划栏比标准顶栏高：仍留指示器带与副标题。
        val standard = barBounds("standard-sub")
        assertTrue(
            "收拢横划栏应高于标准顶栏: ${collapsedSub.bottom - collapsedSub.top} vs ${standard.bottom - standard.top}",
            (collapsedSub.bottom - collapsedSub.top) > (standard.bottom - standard.top),
        )
    }
}
