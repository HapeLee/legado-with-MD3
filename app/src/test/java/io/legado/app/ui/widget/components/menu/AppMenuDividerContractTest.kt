package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * 分段线契约：产品口径允许分组标题不保留文字，因此 [AppAction.dividerBefore]、
 * [AppAction.groupTitleBefore]、[AppActionGroup.title] 与多 group 的组首都只表达「一条纯分段线」。
 *
 * 纯函数用例锁定 [dividerBeforeAt] 这个四引擎共用判定；Compose 用例锁定「分段线不吞条目 /
 * 不吞稳定 ID / 标题文本四引擎都不渲染」，以及 Material 无级联时子项不丢 ID。
 * 分段线的视觉形态（Miuix 长线 vs 其它引擎短胶囊）与各引擎原生动画只能真机验证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class AppMenuDividerContractTest {
    @get:Rule val compose = createComposeRule()

    private val dividerGroups = persistentListOf(
        AppActionGroup(
            id = "first",
            actions = persistentListOf(
                AppAction("alpha", "Alpha"),
                AppAction("beta", "Beta", dividerBefore = true),
            ),
        ),
        AppActionGroup(
            id = "second",
            title = "Group title",
            actions = persistentListOf(
                AppAction("gamma", "Gamma"),
                AppAction("delta", "Delta", groupTitleBefore = "Inline title"),
            ),
        ),
    )

    private val childGroups = persistentListOf(
        AppActionGroup(
            id = "share_group",
            actions = persistentListOf(
                AppAction(
                    id = "share",
                    label = "Share",
                    children = persistentListOf(
                        AppAction("share_link", "Copy link"),
                        AppAction("share_file", "Send file"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun plainFirstItemHasNoDivider() {
        assertFalse(dividerGroups[0].dividerBeforeAt(0, 0))
    }

    @Test
    fun explicitDividerBeforeAppliesInsideAGroup() {
        assertTrue(dividerGroups[0].dividerBeforeAt(1, 0))
    }

    @Test
    fun groupTitleOnlySignalsADivider() {
        assertTrue(dividerGroups[1].dividerBeforeAt(0, 1))
    }

    @Test
    fun inlineGroupTitleOnlySignalsADivider() {
        assertTrue(dividerGroups[1].dividerBeforeAt(1, 1))
    }

    @Test
    fun secondGroupStartIsADividerEvenWithoutFlags() {
        val plain = AppActionGroup("second", actions = persistentListOf(AppAction("gamma", "Gamma")))
        assertFalse(plain.dividerBeforeAt(0, 0))
        assertTrue(plain.dividerBeforeAt(0, 1))
    }

    private fun showActionMenu(
        engine: String,
        groups: ImmutableList<AppActionGroup>,
        onAction: (String) -> Unit,
    ) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                AppActionMenu(groups = groups, onAction = onAction) {
                    AppIcon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "More",
                        modifier = this.modifier,
                    )
                }
            }
        }
    }

    private fun openMenu(engine: String) {
        val trigger = compose.onNode(hasClickAction(), useUnmergedTree = true)
        // Mochi 是 Press 手势，语义点击不保证触发库的按钮。
        if (engine == "mochi") trigger.performTouchInput { click() } else trigger.performClick()
        compose.waitForIdle()
    }

    private fun assertDividerContract(engine: String) {
        var selected: String? = null
        showActionMenu(engine, dividerGroups) { selected = it }
        openMenu(engine)
        listOf("Alpha", "Beta", "Gamma", "Delta").forEach { label ->
            compose.onNodeWithText(label, useUnmergedTree = true).assertExists()
        }
        // 口径：标题文字四引擎都不渲染，只保留分段线。
        compose.onNodeWithText("Group title", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Inline title", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Delta", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("delta", selected)
    }

    @Test
    fun materialDividerKeepsAllItemsAndStableIds() = assertDividerContract("material")

    @Test
    fun mochiDividerKeepsAllItemsAndStableIds() = assertDividerContract("mochi")

    @Test
    fun miuixDividerKeepsAllItemsAndStableIds() = assertDividerContract("miuix")

    @Test
    fun couiDividerKeepsAllItemsAndStableIds() = assertDividerContract("coui")

    @Test
    fun materialFlattensChildrenWithoutLosingIds() {
        var selected: String? = null
        showActionMenu("material", childGroups) { selected = it }
        openMenu("material")
        compose.onNodeWithText("Copy link", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("share_link", selected)
    }

    @Test
    fun miuixCascadingChildStillCommitsChildId() {
        var selected: String? = null
        showActionMenu("miuix", childGroups) { selected = it }
        openMenu("miuix")
        compose.onNodeWithText("Share", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Copy link", useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        assertEquals("share_link", selected)
    }

    private fun showChoice(engine: String, compact: Boolean, onSelect: (String) -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                AppChoiceDropdown(
                    options = persistentListOf(
                        AppChoiceOption("one", "One"),
                        AppChoiceOption("two", "Two", dividerBefore = true),
                    ),
                    selectedId = "one",
                    onSelect = onSelect,
                    title = "Choice",
                    compact = compact,
                ) {
                    AppIcon(
                        imageVector = AppIcons.MoreVert,
                        contentDescription = "Choice",
                        modifier = this.modifier,
                    )
                }
            }
        }
    }

    @Test
    fun materialChoiceDividerKeepsStableId() {
        var selected: String? = null
        showChoice("material", compact = false) { selected = it }
        openMenu("material")
        compose.onNodeWithText("Two", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("two", selected)
    }

    @Test
    fun miuixCompactChoiceDividerKeepsStableId() {
        var selected: String? = null
        showChoice("miuix", compact = true) { selected = it }
        openMenu("miuix")
        compose.onNodeWithText("Two", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("two", selected)
    }
}
