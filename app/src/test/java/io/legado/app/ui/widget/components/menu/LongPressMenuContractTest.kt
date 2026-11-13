package io.legado.app.ui.widget.components.menu

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppShellSettings
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
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
import java.io.File

/**
 * 第二十四轮：长按触发菜单的交互基线。
 *
 * 长按节点属于调用方（`combinedClickable(onLongClick = …)`），入口用受控弹层模式只渲染菜单。
 * 这组用例锁住三条语义：**单击不误开菜单、长按才开、选中后经 `onDismissRequest` 收口**，
 * 并确认受控入口没有额外渲染可点节点去抢占长按手势。
 * 覆盖 Material / Miuix / Mochi 三引擎；COUI 在 Robolectric 下退回 Material 分支，
 * 原生 `Menu` 的真机触点仍需人工复核。
 *
 * 静态契约同时冻结剩余边界：第三十一轮去掉了 `MainScreen` 书架 Rail 分组菜单的
 * `expanded` 跨层透传后，长按簇已无手画菜单，全部走受控 [AppActionMenu]。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w398dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class LongPressMenuContractTest {
    @get:Rule val compose = createComposeRule()

    private val groups = persistentListOf(
        AppActionGroup(
            id = "long_press_group",
            actions = persistentListOf(
                AppAction("search", "Search"),
                AppAction("expand", "Expand"),
            ),
        ),
    )

    @OptIn(ExperimentalFoundationApi::class)
    private fun showLongPressMenu(engine: String, onAction: (String) -> Unit, onPlainClick: () -> Unit) {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        compose.setContent {
            var expanded by remember { mutableStateOf(false) }
            AppTheme(AppUiConfiguration(appShell = AppShellSettings(composeEngine = engine))) {
                Box(Modifier.size(200.dp)) {
                    Text(
                        "LongPressTarget",
                        modifier = Modifier
                            .testTag(TARGET)
                            .combinedClickable(
                                onClick = onPlainClick,
                                onLongClick = { expanded = true },
                            ),
                    )
                    AppActionMenu(
                        groups = groups,
                        onAction = onAction,
                        modifier = Modifier.matchParentSize(),
                        embeddedExpanded = expanded,
                        onDismissRequest = { expanded = false },
                        trigger = { Text(ENTRY_TRIGGER) },
                    )
                    Text(if (expanded) STATE_OPEN else STATE_CLOSED)
                }
            }
        }
    }

    private fun assertSingleClickDoesNotOpenMenu(engine: String) {
        var plainClicks = 0
        showLongPressMenu(engine, {}, { plainClicks++ })
        compose.onNodeWithTag(TARGET, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("$engine 单击回调丢失", 1, plainClicks)
        compose.onNodeWithText("Search", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(STATE_CLOSED, useUnmergedTree = true).assertExists()
    }

    private fun assertLongPressOpensAndSelectionDismisses(engine: String) {
        var selected: String? = null
        var plainClicks = 0
        showLongPressMenu(engine, { selected = it }, { plainClicks++ })
        compose.onNodeWithTag(TARGET, useUnmergedTree = true)
            .performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("$engine 长按不应触发单击", 0, plainClicks)
        compose.onNodeWithText("Search", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Search", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("$engine 未回传稳定 ID", "search", selected)
        compose.onNodeWithText(STATE_CLOSED, useUnmergedTree = true).assertExists()
    }

    @Test
    fun materialSingleClickDoesNotOpenMenu() = assertSingleClickDoesNotOpenMenu("material")

    @Test
    fun miuixSingleClickDoesNotOpenMenu() = assertSingleClickDoesNotOpenMenu("miuix")

    @Test
    fun mochiSingleClickDoesNotOpenMenu() = assertSingleClickDoesNotOpenMenu("mochi")

    @Test
    fun materialLongPressOpensAndSelectionDismisses() =
        assertLongPressOpensAndSelectionDismisses("material")

    @Test
    fun miuixLongPressOpensAndSelectionDismisses() =
        assertLongPressOpensAndSelectionDismisses("miuix")

    @Test
    fun mochiLongPressOpensAndSelectionDismisses() =
        assertLongPressOpensAndSelectionDismisses("mochi")

    /** 受控入口不渲染 trigger、也不包可点节点；唯一可点节点是调用方的长按目标。 */
    @Test
    fun controlledEntryDoesNotStealLongPressTarget() {
        showLongPressMenu("material", {}, {})
        compose.onNodeWithText(ENTRY_TRIGGER, useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    /** 书名长按菜单：锚点、长按节点、条件文案、ID 声明与分派都保留。 */
    @Test
    fun bookInfoTitleMenuIsMigrated() {
        val source = sourceOf(BOOK_INFO)
        assertFalse("仍手画 RoundDropdownMenu", source.contains("RoundDropdownMenu("))
        assertTrue("未把开合交给页面", source.contains("embeddedExpanded = showTitleMenu"))
        assertTrue("未接页面关闭回调", source.contains("onDismissRequest = { showTitleMenu = false }"))
        assertTrue("长按节点丢失", source.contains("onLongClick = { showTitleMenu = true }"))
        assertTrue("单击回调丢失", source.contains("onClick = { onBookNameClick(false) }"))
        assertTrue("锚点未贴原 Box", source.contains("Modifier.matchParentSize()"))
        assertTrue("展开/折叠动态文案丢失", source.contains("R.string.collapse else R.string.expand"))
        listOf("TITLE_MENU_SEARCH" to "title_menu_search", "TITLE_MENU_TOGGLE_EXPAND" to "title_menu_toggle_expand")
            .forEach { (constant, id) ->
                assertTrue("未声明 $id", source.contains("private const val $constant = \"$id\""))
                assertTrue("缺少 $constant 分派", source.contains("$constant ->"))
            }
    }

    /**
     * 第二十七轮迁走 RSS / 发现两处长按菜单，第三十一轮清掉 `MainScreen` 书架 Rail 的最后一处：
     * 长按簇整体不再有手画菜单，开合统一由页面状态 + `embeddedExpanded` 承担。
     */
    @Test
    fun longPressMenusAreAllMigrated() {
        listOf(RSS_SCREEN, EXPLORE_SCREEN, MAIN_SCREEN).forEach { path ->
            assertEquals(
                "$path 应已无手画菜单",
                0,
                Regex("RoundDropdownMenu\\(").findAll(sourceOf(path)).count(),
            )
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
        const val BOOK_INFO = "io/legado/app/ui/book/info/BookInfoScreen.kt"
        const val RSS_SCREEN = "io/legado/app/ui/main/rss/RssScreen.kt"
        const val EXPLORE_SCREEN = "io/legado/app/ui/main/explore/ExploreScreen.kt"
        const val MAIN_SCREEN = "io/legado/app/ui/main/MainScreen.kt"
        const val TARGET = "long-press-target"
        const val STATE_OPEN = "STATE_OPEN"
        const val STATE_CLOSED = "STATE_CLOSED"
        const val ENTRY_TRIGGER = "ENTRY_TRIGGER_SHOULD_NOT_RENDER"
    }
}
