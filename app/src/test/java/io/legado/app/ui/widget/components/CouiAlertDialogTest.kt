package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.itos.coui.backdrop.CouiBackdrop
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.menu.rememberCouiWindowBackdrop
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
 * COUI 引擎下的 [AppAlertDialog]。
 *
 * 原生分支的按钮由库绘制，没有 Material 的 dismiss 语义；外部点击与返回键一律走
 * onDismissRequest。表单弹窗仍由宿主决定何时关闭，所以只锁渲染与回调，不锁自动关闭。
 * 材质会实时采样页面窗口，Robolectric 无法编译 Kawase 的 RuntimeShader，因此材质接入
 * 用源码结构锁定，观感留给真机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class CouiAlertDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun setCouiContent(content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            // 轮廓光走 RuntimeShader，Robolectric 下关闭只影响描边，不影响排版与语义。
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides false) {
                AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                    content()
                }
            }
        }
    }

    @Test
    fun rendersTitleMessageAndBothActions() {
        var confirmed = 0
        var dismissed = 0
        setCouiContent {
            AppAlertDialog(
                show = true,
                onDismissRequest = {},
                title = "删除书源",
                text = "该操作不可撤销",
                confirmText = "删除",
                onConfirm = { confirmed++ },
                dismissText = "取消",
                onDismiss = { dismissed++ },
            )
        }
        compose.onNodeWithText("删除书源").assertIsDisplayed()
        compose.onNodeWithText("该操作不可撤销").assertIsDisplayed()
        compose.onNode(hasText("删除") and hasClickAction()).performClick()
        compose.onNode(hasText("取消") and hasClickAction()).performClick()
        compose.runOnIdle {
            assertEquals(1, confirmed)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun confirmEnabledFalseKeepsButtonDisabled() {
        val enabled = mutableStateOf(false)
        var confirmed = 0
        setCouiContent {
            AppAlertDialog(
                show = true,
                onDismissRequest = {},
                title = "重命名",
                confirmText = "保存",
                confirmEnabled = enabled.value,
                onConfirm = { confirmed++ },
                onDismiss = {},
            )
        }
        compose.onNode(hasText("保存") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { enabled.value = true }
        compose.onNode(hasText("保存") and hasClickAction()).performClick()
        compose.runOnIdle { assertEquals(1, confirmed) }
    }

    @Test
    fun dialogWithoutActionsStillRendersContent() {
        setCouiContent {
            AppAlertDialog(
                show = true,
                onDismissRequest = {},
                title = "提示",
                content = {
                    Column { AppText("异步任务已完成") }
                },
            )
        }
        compose.onNodeWithText("提示").assertIsDisplayed()
        compose.onNodeWithText("异步任务已完成").assertIsDisplayed()
    }

    @Test
    fun materialIsAlwaysUsedForCouiDialogs() {
        val source = sourceOf(DIALOG)
        assertFalse("COUI 弹窗不应再有可关闭材质的入口", source.contains("couiMaterial"))
        assertTrue(
            "材质只由页面背景源与平台能力决定",
            source.contains("val useMaterial = windowBackdrop != null &&") &&
                source.contains("CouiDialogDefaults.isMaterialSupported()"),
        )
        assertTrue("材质与背景源必须成对传入", source.contains("backdrop = windowBackdrop.takeIf { useMaterial }"))
        assertTrue("主操作配色必须由宿主提供", source.contains("CouiDialogDefaults.primaryActionColors("))
    }

    @Test
    fun pageWindowProvidesBackdropForDialogMaterial() {
        var backdrop: CouiBackdrop? = null
        setCouiContent {
            backdrop = rememberCouiWindowBackdrop()
        }
        compose.runOnIdle {
            assertNotNull("COUI 引擎必须在页面根提供弹窗材质源", backdrop)
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
        const val DIALOG = "io/legado/app/ui/widget/components/alert/AppAlertDialog.kt"
    }
}
