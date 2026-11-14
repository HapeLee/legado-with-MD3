package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.assertEquals
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
import java.io.File

/**
 * COUI / Miuix 引擎下组件库的两处接入：输入框与多选悬浮操作栏。
 *
 * 输入框把两个引擎都能表达的形态搬到原生实现上：标签、占位提示、前后缀（合成进 icon 槽）、
 * 行数与排版回调；错误态与辅助文字两个引擎都没有，静默丢弃由本测试锁住。
 * 操作栏在缺少页面采样源或原生材质不可用时退回 M3。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CouiComponentLibraryTest {
    @get:Rule val compose = createComposeRule()

    private fun setContentWithEngine(engine: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(
                AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine)),
                content = content,
            )
        }
    }

    private fun setCouiContent(content: @Composable () -> Unit) =
        setContentWithEngine("coui", content)

    @Test
    fun couiTextFieldKeepsTextPlaceholderAndEditing() {
        val state = TextFieldState("")
        setCouiContent {
            AppTextField(state = state, placeholderText = "地点")
        }
        compose.onNodeWithText("地点").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("西湖")
        compose.runOnIdle { assertEquals("西湖", state.text.toString()) }
    }

    @Test
    fun labeledFieldRendersItsLabelNextToTheInput() {
        val state = TextFieldState("")
        setCouiContent {
            AppTextField(state = state, label = "名称")
        }
        compose.onNodeWithText("名称").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("斗破苍穹")
        compose.runOnIdle { assertEquals("斗破苍穹", state.text.toString()) }
    }

    @Test
    fun prefixAndSuffixAreComposedIntoTheIconSlots() {
        setCouiContent {
            AppTextField(
                value = "",
                onValueChange = {},
                placeholderText = "间隔",
                prefix = { AppText("每") },
                suffix = { AppText("秒") },
            )
        }
        // COUI 只有 leading / trailing 两个槽：前后缀必须仍然出现在卡片内、输入两侧。
        compose.onNodeWithText("每").assertIsDisplayed()
        compose.onNodeWithText("秒").assertIsDisplayed()
    }

    @Test
    fun couiSilentlyDropsErrorStateAndSupportingText() {
        setCouiContent {
            AppTextField(
                value = "/s",
                onValueChange = {},
                label = "超时",
                isError = true,
                supportingText = { AppText("毫秒") },
            )
        }
        // 两个引擎都没有错误角色与框下文字：标签照常，辅助文字刻意不渲染（不是回归）。
        compose.onNodeWithText("超时").assertIsDisplayed()
        compose.onNodeWithText("毫秒").assertDoesNotExist()
    }

    @Test
    fun miuixUsesPlaceholderTextAsNonFloatingLabel() {
        // 运行时不给 Miuix 引擎跑 Compose：Robolectric 的 RuntimeShader 无法编译 Miuix 玻璃材质的
        // SkSL（"color is not a valid layout qualifier"），这条只能锁源码结构 + 由真机验证观感。
        val source = sourceOf(TEXT_FIELD)
        assertTrue(
            "Miuix 没有占位槽，只能把 placeholderText 退化成 label",
            source.contains("label = label ?: placeholderText.orEmpty(),"),
        )
        assertTrue(
            "占位语义下 label 不浮动",
            source.contains("useLabelAsPlaceholder = label == null && placeholderText != null,"),
        )
        assertTrue(
            "前后缀同样合成进 icon 槽",
            source.contains("leadingIcon = mergeFieldSlots(leadingIcon, prefix),"),
        )
    }

    /**
     * 多选栏只在 `AppScaffold` 的浮层槽位里采样正文：槽位外的正文采样源会包含栏自身，
     * COUI 玻璃把源图层画进自己的 patch 后形成自引用，hwui 会栈溢出（native 闪退）。
     * 这里走真实调用路径（浮层槽位 + 正文源），只锁语义与可点性。
     */
    @Test
    fun selectionBarKeepsActionSemanticsInCouiPageOverlay() {
        val context = RuntimeEnvironment.getApplication()
        var selectAllClicks = 0
        var primaryClicks = 0
        setCouiContent {
            AppScaffold(
                topBar = { Box(Modifier.fillMaxWidth().height(56.dp)) },
                overlay = {
                    SelectionBottomBar(
                        modifier = Modifier.width(320.dp),
                        onSelectAll = { selectAllClicks++ },
                        onSelectInvert = {},
                        primaryAction = ActionItem(text = "删除", icon = Icons.Default.Delete) { primaryClicks++ },
                        secondaryActions = listOf(ActionItem(text = "收藏") {}),
                    )
                },
            ) {}
        }
        compose.onNode(hasContentDescription(context.getString(R.string.select_all)) and hasClickAction())
            .assertIsDisplayed()
            .performClick()
        compose.onNode(hasContentDescription("删除") and hasClickAction())
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle {
            assertEquals(1, selectAllClicks)
            assertEquals(1, primaryClicks)
        }
    }

    private fun sourceOf(path: String): String {
        var directory: File? = File("").absoluteFile
        while (directory != null) {
            val candidate = File(directory, "app/src/main/java/$path")
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
        }
        error("从 ${File("").absolutePath} 向上找不到 $path")
    }

    private companion object {
        const val TEXT_FIELD = "io/legado/app/ui/widget/components/AppTextField.kt"
    }
}
