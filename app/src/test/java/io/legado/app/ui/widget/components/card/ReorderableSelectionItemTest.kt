package io.legado.app.ui.widget.components.card

import android.app.Application
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.ui.theme.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReorderableSelectionItemTest {
    @get:Rule val compose = createComposeRule()
    private val ids = mutableStateOf((0 until 80).toList())
    private val listState = mutableStateOf(LazyListState())
    private lateinit var reorderState: ReorderableLazyListState

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun `downward edge drag keeps scrolling through swaps and ends on release`() {
        showList()
        startDrag()
        moveToEdge(bottom = true)
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle {
            assertTrue("List must keep scrolling across multiple rows", listState.value.firstVisibleItemIndex > 6)
            assertTrue(reorderState.isAnyItemDragging)
            assertTrue(ids.value.indexOf(1) > 6)
        }
        compose.onNodeWithTag("list").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(reorderState.isAnyItemDragging) }
    }

    @Test
    fun `upward edge drag keeps scrolling through swaps and clears on cancellation`() {
        listState.value = LazyListState(firstVisibleItemIndex = 30)
        showList()
        startDrag()
        moveToEdge(bottom = false)
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle {
            assertTrue("List must keep scrolling upwards", listState.value.firstVisibleItemIndex < 24)
            assertTrue(reorderState.isAnyItemDragging)
            assertTrue(ids.value.indexOf(31) < 24)
        }
        compose.onNodeWithTag("list").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(reorderState.isAnyItemDragging) }
    }

    @Test
    fun `remembered handle follows replacement reorder state`() {
        showList()
        val previousState = reorderState
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { listState.value = LazyListState() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertNotSame(previousState, reorderState) }
        startDrag()
        compose.runOnIdle {
            assertTrue(
                "Handle must start the current reorder state; old dragging=${previousState.isAnyItemDragging}, " +
                    "visible=${listState.value.layoutInfo.visibleItemsInfo.map { it.key }}",
                reorderState.isAnyItemDragging,
            )
        }
        compose.onNodeWithTag("list").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertFalse(reorderState.isAnyItemDragging) }
    }

    private fun showList() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                AppTheme(AppUiConfiguration(), applyBackground = false) {
                    val state = rememberReorderableLazyListState(listState.value) { from, to ->
                        ids.value = ids.value.toMutableList().apply { add(to.index, removeAt(from.index)) }
                    }
                    reorderState = state
                    LazyColumn(
                        state = listState.value,
                        modifier = Modifier.width(320.dp).height(400.dp).testTag("list"),
                    ) {
                        items(ids.value, key = { it }) { id ->
                            ReorderableSelectionItem(
                                state = state, key = id, title = "Item $id",
                                modifier = Modifier.height(64.dp),
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun startDrag() {
        compose.onNodeWithTag("list").performTouchInput {
            down(Offset(centerX, with(compose.density) { 96.dp.toPx() }))
        }
        compose.mainClock.advanceTimeBy(650)
    }

    private fun moveToEdge(bottom: Boolean) {
        compose.onNodeWithTag("list").performTouchInput {
            moveTo(Offset(centerX, if (bottom) height - 4f else 4f))
        }
    }
}
