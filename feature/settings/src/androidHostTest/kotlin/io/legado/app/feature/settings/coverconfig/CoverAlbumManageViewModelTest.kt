package io.legado.app.feature.settings.coverconfig

import android.app.Application
import android.os.Looper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `CoverAlbumManageViewModel` 的行为基线（M5-14a 新增；迁移前零测试）。
 *
 * 本片把该 VM 迁进共享层：它原来直接吃 `Context` + `CoverAlbumUseCase`，现在吃
 * [CoverAlbumProvider] ⇒ 用例重心在**契约交互**（断言调了哪个方法、按什么参数）。
 *
 * 几条特意钉死的：
 *
 * - **建相册后进入编辑态**（`createAlbum` 返回的新 id 要写进 `editingAlbumId`，
 *   否则用户建完相册看不到它被选中）；
 * - **删除的分支**：删的若是**当前正在编辑**的相册要一并退出编辑态，删别的则**保留**
 *   （这条写反的表现很隐蔽：删了 A 之后编辑面板挂在已不存在的 B 上）；
 * - **`ImagesSelected` 收到空列表不打契约**（选择器取消就是这个路径）；
 * - **`AddImagesClick` 只发 Effect、不打契约**（真正落盘要等选择器回来）；
 * - **异常 → `ShowMessage(effect)`**；`CancellationException` 要**照原样抛出**（不能被当成错误提示）。
 *
 * ⚠️ `uiState` 是 `stateIn(WhileSubscribed(5_000))` ⇒ **没有订阅者时 `value` 不会更新**。
 * 所以每个用例都要先挂一个收集者（[collect]），这与真实 UI 的行为一致；否则断言到的是初始值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CoverAlbumManageViewModelTest {

    @Test
    fun `新建相册后进入编辑态`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)

        viewModel.onIntent(CoverAlbumIntent.CreateClick)
        assertEquals(CoverAlbumDialog.Create, viewModel.uiState.value.dialog)

        viewModel.onIntent(CoverAlbumIntent.SaveName(" 新相册 "))
        idle()

        assertEquals("名字要去首尾空白", listOf("新相册"), provider.created)
        assertEquals("建完要选中它，否则用户看不到自己刚建的相册", "new-id", viewModel.uiState.value.editingAlbumId)
        assertEquals("对话框要关掉", null, viewModel.uiState.value.dialog)
    }

    @Test
    fun `空名字不落盘`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)

        viewModel.onIntent(CoverAlbumIntent.CreateClick)
        viewModel.onIntent(CoverAlbumIntent.SaveName("   "))
        idle()

        assertTrue("全是空白 ⇒ 什么都不该发生", provider.created.isEmpty())
    }

    @Test
    fun `重命名走契约`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(CoverAlbumIntent.RenameClick("album-1"))
        assertEquals(
            CoverAlbumDialog.Rename("album-1", "相册一"),
            viewModel.uiState.value.dialog,
        )

        viewModel.onIntent(CoverAlbumIntent.SaveName("改名后"))
        idle()

        assertEquals(listOf("album-1" to "改名后"), provider.renamed)
    }

    @Test
    fun `删除正在编辑的相册会退出编辑态`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(CoverAlbumIntent.EditClick("album-1"))
        viewModel.onIntent(CoverAlbumIntent.DeleteClick("album-1"))
        viewModel.onIntent(CoverAlbumIntent.ConfirmDelete)
        idle()

        assertEquals(listOf("album-1"), provider.deleted)
        assertEquals("删的正是在编辑的那个 ⇒ 必须退出编辑态", null, viewModel.uiState.value.editingAlbumId)
    }

    @Test
    fun `删除别的相册不影响当前编辑态`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(CoverAlbumIntent.EditClick("album-1"))
        viewModel.onIntent(CoverAlbumIntent.DeleteClick("album-2"))
        viewModel.onIntent(CoverAlbumIntent.ConfirmDelete)
        idle()

        assertEquals(listOf("album-2"), provider.deleted)
        assertEquals("编辑的是别的相册 ⇒ 不该被连带清掉", "album-1", viewModel.uiState.value.editingAlbumId)
    }

    @Test
    fun `加图按钮只发选择器effect不打契约`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        val effects = collectEffects(viewModel)
        idle()

        viewModel.onIntent(CoverAlbumIntent.AddImagesClick("album-1", isDark = true))
        idle()

        assertEquals(
            listOf(CoverAlbumEffect.SelectImages("album-1", true)),
            effects.toList(),
        )
        assertTrue("真正落盘要等选择器回来", provider.added.isEmpty())
    }

    @Test
    fun `选择器返回后把URI串交给契约`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(
            CoverAlbumIntent.ImagesSelected(
                albumId = "album-1",
                isDark = false,
                uriStrings = listOf("content://a", "content://b"),
            )
        )
        idle()

        assertEquals(
            listOf(Triple("album-1", false, listOf("content://a", "content://b"))),
            provider.added,
        )
    }

    @Test
    fun `选择器返回空列表不打契约`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(
            CoverAlbumIntent.ImagesSelected(albumId = "album-1", isDark = false, uriStrings = emptyList())
        )
        idle()

        assertTrue("取消选择就是这个路径", provider.added.isEmpty())
    }

    @Test
    fun `删图走契约`() {
        val provider = FakeCoverAlbumManageProvider()
        val viewModel = createViewModel(provider)
        observe(viewModel)
        idle()

        viewModel.onIntent(
            CoverAlbumIntent.RemoveImage(albumId = "album-1", isDark = true, imageId = "img-9")
        )
        idle()

        assertEquals(listOf(Triple("album-1", true, "img-9")), provider.removed)
    }

    @Test
    fun `契约抛异常时发提示而不是崩溃`() {
        val provider = FakeCoverAlbumManageProvider(failOnDelete = true)
        val viewModel = createViewModel(provider)
        val effects = collectEffects(viewModel)
        idle()

        viewModel.onIntent(CoverAlbumIntent.DeleteClick("album-1"))
        viewModel.onIntent(CoverAlbumIntent.ConfirmDelete)
        idle()

        assertEquals(
            listOf(CoverAlbumEffect.ShowMessage("boom")),
            effects.toList(),
        )
    }

    private fun createViewModel(provider: CoverAlbumProvider) =
        CoverAlbumManageViewModel(provider = provider)

    /**
     * 挂一个 `uiState` 收集者 —— 它是 `stateIn(WhileSubscribed(5_000))`，**没人订阅就不更新**
     * ⇒ 不挂的话断言到的是初始值。与真实 UI 的行为一致。
     */
    private fun observe(viewModel: CoverAlbumManageViewModel) {
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.uiState.collect { } }
        idle()
    }

    /** 同上，另外收 Effect。 */
    private fun collectEffects(viewModel: CoverAlbumManageViewModel): MutableList<CoverAlbumEffect> {
        val out = mutableListOf<CoverAlbumEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.uiState.collect { } }
        CoroutineScope(Dispatchers.Unconfined).launch { viewModel.effects.collect { out += it } }
        idle()
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private class FakeCoverAlbumManageProvider(
    private val failOnDelete: Boolean = false,
) : CoverAlbumProvider {

    private val state = MutableStateFlow(
        CoverAlbumSelectionUiState(
            albums = persistentListOf(
                CoverAlbumItemUi(id = "album-1", name = "相册一"),
                CoverAlbumItemUi(id = "album-2", name = "相册二"),
            ),
            selectedAlbumId = null,
        )
    )

    val created = mutableListOf<String>()
    val renamed = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()
    val added = mutableListOf<Triple<String, Boolean, List<String>>>()
    val removed = mutableListOf<Triple<String, Boolean, String>>()

    override val selection: Flow<CoverAlbumSelectionUiState> = state

    override suspend fun selectAlbum(albumId: String?) = Unit

    override suspend fun createAlbum(name: String): String {
        created += name
        return "new-id"
    }

    override suspend fun renameAlbum(albumId: String, name: String) {
        renamed += albumId to name
    }

    override suspend fun deleteAlbum(albumId: String) {
        if (failOnDelete) error("boom")
        deleted += albumId
    }

    override suspend fun addImages(albumId: String, isDark: Boolean, uriStrings: List<String>) {
        added += Triple(albumId, isDark, uriStrings)
    }

    override suspend fun removeImage(albumId: String, isDark: Boolean, imageId: String) {
        removed += Triple(albumId, isDark, imageId)
    }
}
