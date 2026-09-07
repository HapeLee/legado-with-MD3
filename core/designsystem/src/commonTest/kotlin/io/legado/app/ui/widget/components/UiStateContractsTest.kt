package io.legado.app.ui.widget.components

import io.legado.app.ui.widget.components.importComponents.BaseImportUiState
import io.legado.app.ui.widget.components.importComponents.ImportItemWrapper
import io.legado.app.ui.widget.components.importComponents.ImportStatus
import io.legado.app.ui.widget.components.list.InteractionState
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.list.SelectableItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 共享 UI 状态契约测试（commonTest，零 Compose）。
 *
 * 钉住这批从 app:ui.widget.components 下沉的纯数据叶子的跨平台可用性：
 * 它们不依赖 android.* / androidx.compose.*，在 desktop 也能编译运行，
 * 是后续 Feature Contract 下沉的前置基础设施。
 */
class UiStateContractsTest {

    private class FakeItem(override val id: Long) : SelectableItem<Long>

    @Test
    fun interactionStateDefaultsAreIdle() {
        val s = InteractionState()
        assertTrue(!s.isSearchMode)
        assertTrue(!s.isUploading)
        assertTrue(!s.isLoading)
    }

    @Test
    fun importStatusCoversAllStates() {
        assertEquals(4, ImportStatus.entries.size)
    }

    @Test
    fun importItemWrapperDefaultsToNewAndSelected() {
        val w = ImportItemWrapper(data = "x")
        assertTrue(w.isSelected)
        assertEquals(ImportStatus.New, w.status)
        assertEquals(null, w.oldData)
    }

    @Test
    fun baseImportUiStateVariantsAreWellTyped() {
        // Idle/Loading/Error 是单例/数据变体，直接构造即可编译（证明 sealed 形态稳定）。
        assertEquals("e", (BaseImportUiState.Error("e") as BaseImportUiState.Error).msg)
        val success = BaseImportUiState.Success(
            source = "s",
            items = emptyList<ImportItemWrapper<String>>(),
        )
        assertEquals(0, success.version)
        assertEquals("s", success.source)
    }

    @Test
    fun selectableItemCarriesId() {
        assertEquals(42L, FakeItem(42L).id)
    }
}
