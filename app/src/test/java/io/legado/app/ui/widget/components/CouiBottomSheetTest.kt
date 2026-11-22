package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.SheetAction
import io.legado.app.ui.widget.components.modalBottomSheet.toCouiSheetButton
import io.legado.app.ui.widget.components.text.AppText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * COUI 引擎下的 [AppModalBottomSheet]。
 *
 * 引擎打开后必须分流到 `com.itos.coui.sheet.BottomSheet`，而不是继续在 M3 的窗口里补
 * COUI 采样根；材质与窗口背景源成对传入，缺一时退化为普通填色。
 * 材质会实时采样页面窗口，Robolectric 编译不了 Kawase 的 RuntimeShader，所以渲染只锁
 * 排版与语义，观感与拖拽留给真机。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class CouiBottomSheetTest {
    @get:Rule val compose = createComposeRule()

    private fun setCouiContent(content: @Composable () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            CompositionLocalProvider(LocalCouiContourGlowEnabled provides false) {
                AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = "coui"))) {
                    content()
                }
            }
        }
    }

    @Test
    fun rendersTitleAndBodyInTheCouiPanel() {
        setCouiContent {
            AppModalBottomSheet(show = true, onDismissRequest = {}, title = "面板标题") {
                AppText("面板正文")
            }
        }
        compose.onNodeWithText("面板标题").assertIsDisplayed()
        compose.onNodeWithText("面板正文").assertIsDisplayed()
    }

    /**
     * 头部左右槽位只有声明式一种形态，单颗操作交给 COUI 库工具栏渲染。
     *
     * 库按钮在组合期就构造 `CouiFloatingRenderer`，Robolectric 造不出 `MeshSpecification`
     * （见类注释），所以这里锁调用协议，标题与左右按钮的实际排版留给真机。
     */
    @Test
    fun keepsHeaderSlotsForStartAndEndActions() {
        val source = sourceOf(SHEET)
        assertTrue(
            "左右槽位必须由声明式操作承担",
            source.contains("val startSlot = sheetActionSlot(startActions)") &&
                source.contains("val endSlot = sheetActionSlot(endActions)"),
        )
        assertTrue(
            "标题行必须接收两侧槽位",
            source.contains("startAction = startSlot,") && source.contains("endAction = endSlot,"),
        )
        assertFalse(
            "Composable 槽已移除，不能再留兼容分支",
            source.contains("startAction: @Composable (() -> Unit)? = null"),
        )
    }

    @Test
    fun contentWithoutHeaderSkipsTheHeaderRow() {
        setCouiContent {
            Column {
                AppModalBottomSheet(show = true, onDismissRequest = {}) {
                    AppText("纯内容面板")
                }
            }
        }
        compose.onNodeWithText("纯内容面板").assertIsDisplayed()
    }

    /**
     * 图标选择面板（LauncherIconPickerSheet）这类正文直接放 `LazyVerticalGrid` 的调用点：
     * 面板若自己套 `couiVerticalScroll`，网格会拿到无限高度并在测量阶段抛
     * IllegalStateException。正文滚动归调用方，与 M3 分支同口径。
     */
    @Test
    fun lazyGridContentIsMeasuredWithBoundedHeight() {
        setCouiContent {
            AppModalBottomSheet(show = true, onDismissRequest = {}, title = "应用图标") {
                LazyVerticalGrid(columns = GridCells.Fixed(3)) {
                    items(12) { AppText("图标 $it") }
                }
            }
        }
        compose.onNodeWithText("图标 0").assertIsDisplayed()
    }

    @Test
    fun couiRoutesToItsOwnSheetBeforeTheM3Window() {
        val source = sourceOf(SHEET)
        val guard = "if (composeEngine == ComposeEngine.Coui && ThemeResolver.isCouiSupported())"
        assertTrue("COUI 未分流到自己的面板", source.contains(guard))
        assertTrue(
            "分流必须发生在 M3 窗口之前",
            source.indexOf(guard) in 1 until source.indexOf("when (composeEngine)"),
        )
        assertTrue(
            "COUI 面板必须来自 coui 库而不是 M3 的 ModalBottomSheet",
            source.contains("import com.itos.coui.sheet.BottomSheet"),
        )
        assertTrue(
            "标题行必须由各引擎共用，避免 COUI 与 M3 各写一份",
            source.contains("private fun SheetHeader(") && source.contains("SheetHeader("),
        )
        assertTrue(
            "面板不能自带滚动容器：内层 Lazy 列表会拿到无限高度并崩溃",
            source.contains("scrollableContent = false"),
        )
        assertTrue(
            "面板高度必须与 M3 分支一样有上限，内层 Lazy 列表才有有限约束",
            source.contains(".heightIn(max = maxHeight)"),
        )
    }

    /**
     * 声明式操作映射成 CouiSheetButton：库要求 label 与 icon 二选一，文字优先；
     * 只有图标时把 contentDescription 带进 Icon。
     *
     * 真机才验证得了外观与按压：API 34 的 `TopAppBarButton` 在组合期就构造
     * `CouiFloatingRenderer`，Robolectric 造不出 `MeshSpecification`，所以这里只锁映射。
     */
    @Test
    fun sheetActionMapsToExactlyOneCouiButtonPayload() {
        val withText = SheetAction(
            onClick = {},
            text = "文字",
            icon = Icons.Default.Add,
            contentDescription = "新增",
        ).toCouiSheetButton()
        assertEquals("文字", withText.label)
        assertNull("文字优先时不能再给图标，库会拒绝两个都给", withText.icon)

        val iconOnly = SheetAction(
            onClick = {},
            icon = Icons.Default.Check,
            contentDescription = "确认",
        ).toCouiSheetButton()
        assertNull(iconOnly.label)
        assertNotNull("只有图标时必须带上图标内容", iconOnly.icon)
    }

    /**
     * 库的 SheetToolbar 只接受 CouiSheetButton，App 的任意 Composable 操作槽只能自绘；
     * 自绘就必须补回工具栏那两段留白（11dp 顶部间距 + 50dp 高度），否则正文和按钮
     * 会顶到 45dp 圆角边缘。
     */
    @Test
    fun couiPanelKeepsToolbarLikeTopSpacingForTheAppHeader() {
        val source = sourceOf(SHEET)
        assertTrue(
            "走库工具栏时不能再叠加顶部间距",
            source.contains("libraryToolbar -> 0.dp"),
        )
        assertTrue(
            "自绘头部时必须按库工具栏的顶部间距起排",
            source.contains("hasHeader -> CouiSheetDefaults.toolbarTopSpacing"),
        )
        assertTrue(
            "没有头部时正文自己让开 45dp 圆角",
            source.contains("else -> 16.dp"),
        )
        assertTrue(
            "自绘的标题行必须复刻库工具栏高度，操作按钮才不会贴住上边缘",
            source.contains("modifier = Modifier.heightIn(min = CouiSheetDefaults.toolbarHeight)"),
        )
    }

    /**
     * 声明式操作映射成 CouiSheetButton；库工具栏每侧只有一个按钮槽、按钮也挂不了菜单，
     * 所以「同侧多按钮 / 带菜单」两类都退回 App 自绘头部，
     * 但自绘头部里的按钮仍必须是库按钮，不能退回 App 的 Tonal 按钮。
     */
    @Test
    fun sheetActionsMapToCouiButtonsAndFallBackForMultipleOrMenuedActions() {
        val source = sourceOf(SHEET)
        assertTrue(
            "声明式操作必须映射成库按钮",
            source.contains("leadingButton = if (libraryToolbar) startSheetAction?.toCouiSheetButton() else null"),
        )
        assertTrue(
            "Composable 槽进不了按钮槽，必须整条退回自绘头部",
            source.contains("val libraryToolbar = useLibraryToolbar && hasHeader"),
        )
        assertTrue(
            "同侧多按钮放不进库工具栏，必须退回自绘头部",
            source.contains("startActions.size <= 1 && endActions.size <= 1"),
        )
        assertTrue(
            "库按钮没有菜单能力，带菜单的操作必须退回自绘头部",
            source.contains("startActions.none { it.menu.isNotEmpty() }"),
        )
        assertTrue(
            "自绘头部里的操作仍要渲染成库按钮",
            source.contains("CouiSheetActionButton(action.toCouiSheetButton(onClick))"),
        )
        assertTrue(
            "标题同时交给库的居中标题槽",
            source.contains("titleContent = if (libraryToolbar && sheetTitle != null)"),
        )
        assertTrue(
            "操作必须是声明式数据类",
            sourceOf(SHEET_ACTION).contains("data class SheetAction("),
        )
    }

    @Test
    fun materialIsPairedWithTheWindowBackdrop() {
        val source = sourceOf(SHEET)
        assertTrue(
            "材质只能由页面背景源与平台能力共同决定",
            source.contains("val material = if (CouiDialogDefaults.isMaterialSupported() && windowBackdrop != null)"),
        )
        assertTrue("材质必须有对应窗口背景源", source.contains("backdrop = windowBackdrop,"))
        assertFalse(
            "COUI 已分流，M3 窗口里不应再给 COUI 搭采样根",
            source.contains("ProvideCouiMenuBackdrop"),
        )
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
        const val SHEET = "io/legado/app/ui/widget/components/modalBottomSheet/AppModalBottomSheet.kt"
        const val SHEET_ACTION = "io/legado/app/ui/widget/components/modalBottomSheet/SheetAction.kt"
    }
}
