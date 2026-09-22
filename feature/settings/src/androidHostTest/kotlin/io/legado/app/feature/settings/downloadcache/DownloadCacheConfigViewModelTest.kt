package io.legado.app.feature.settings.downloadcache

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.BookCacheCleanupGateway
import io.legado.app.domain.gateway.DatabaseMaintenanceGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.model.settings.DownloadCacheSettings
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `DownloadCacheConfigViewModel` 的行为基线（M5-7 新增；迁移前零测试）。
 *
 * 本片的实质改动是**把五处平台直连收进 [DownloadCachePlatform]**，所以用例的重心也在
 * 契约交互上 —— 断言「VM 确实调了平台的哪个方法、按什么参数」，而不只是「状态变了」：
 *
 * - [DownloadCachePlatform.maxDownloadConcurrency] 要**进 UI state**（迁移前 Screen 自己读
 *   `CacheBook`，现在共享层只能从 state 拿）+ 顺手钉住 `SetCacheBookThreadCount` 的**夹取**；
 * - 缓存大小**字节 → MB** 的换算（`/ (1024.0 * 1024.0)`，写错量级不会有人发现）；
 * - 改 `bitmapCacheSize` 要**同时**写设置**并**让平台重新分配图片缓存（漏后半句就是静默失效）；
 * - 四个确认分支各自该调谁：清封面/漫画缓存走平台、清书缓存是**「先 use case 清条目，
 *   再平台清目录」两步**、收缩数据库只走 use case。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DownloadCacheConfigViewModelTest {

    @Test
    fun `平台并发上限进状态并用于夹取`() {
        val platform = FakeDownloadCachePlatform(maxDownloadConcurrency = 5)
        val gateway = FakeSettingsGateway(DownloadCacheSettings(cacheBookThreadCount = 3))
        val viewModel = createViewModel(gateway, platform)
        idle()

        assertEquals(
            "迁移前 Screen 自己读 CacheBook，现在只能从 state 拿",
            5,
            viewModel.uiState.value.maxDownloadConcurrency,
        )

        // 超上限 ⇒ 夹到 5
        viewModel.onIntent(DownloadCacheConfigIntent.SetCacheBookThreadCount(99))
        idle()
        assertEquals(5, gateway.currentSettings.cacheBookThreadCount)

        // 下限是 1
        viewModel.onIntent(DownloadCacheConfigIntent.SetCacheBookThreadCount(0))
        idle()
        assertEquals(1, gateway.currentSettings.cacheBookThreadCount)

        // 区间内原样
        viewModel.onIntent(DownloadCacheConfigIntent.SetCacheBookThreadCount(4))
        idle()
        assertEquals(4, gateway.currentSettings.cacheBookThreadCount)
    }

    @Test
    fun `缓存字节数换算成MB`() {
        val platform = FakeDownloadCachePlatform(
            coverBytes = 3L * 1024 * 1024,
            mangaBytes = 512L * 1024,
        )
        val viewModel = createViewModel(FakeSettingsGateway(), platform)
        idle()

        assertEquals(3.0, viewModel.uiState.value.coverCacheSizeMb, 1e-9)
        assertEquals(0.5, viewModel.uiState.value.mangaCacheSizeMb, 1e-9)
    }

    @Test
    fun `改图片缓存大小同时写设置并让平台重新分配`() {
        val platform = FakeDownloadCachePlatform()
        val gateway = FakeSettingsGateway()
        val viewModel = createViewModel(gateway, platform)
        idle()

        viewModel.onIntent(DownloadCacheConfigIntent.SetBitmapCacheSize(128))
        idle()

        assertEquals(128, gateway.currentSettings.bitmapCacheSize)
        assertEquals(
            "只写设置不重分配 = 静默失效，必须两边都发生",
            1,
            platform.imageCacheResizeCount,
        )
    }

    @Test
    fun `确认清封面缓存会调平台并归零大小`() {
        val platform = FakeDownloadCachePlatform(coverBytes = 1024L * 1024)
        val viewModel = createViewModel(FakeSettingsGateway(), platform)
        idle()
        assertEquals(1.0, viewModel.uiState.value.coverCacheSizeMb, 1e-9)

        viewModel.onIntent(
            DownloadCacheConfigIntent.ShowDialog(DownloadCacheConfigDialog.ClearCoverCache)
        )
        viewModel.onIntent(DownloadCacheConfigIntent.ConfirmDialog)
        idle()

        assertEquals(listOf(HttpCacheKind.COVER), platform.clearedCaches)
        assertEquals(0.0, viewModel.uiState.value.coverCacheSizeMb, 1e-9)
        assertEquals("弹窗要被关掉（迁移前是进 confirm 前先 copy(dialog = null)）", null, viewModel.uiState.value.dialog)
    }

    @Test
    fun `确认清书缓存是清条目加清目录两步`() {
        val platform = FakeDownloadCachePlatform()
        val bookCache = FakeBookCacheCleanupGateway()
        val viewModel = createViewModel(FakeSettingsGateway(), platform, bookCache = bookCache)
        idle()

        viewModel.onIntent(
            DownloadCacheConfigIntent.ShowDialog(DownloadCacheConfigDialog.ClearBookCache)
        )
        viewModel.onIntent(DownloadCacheConfigIntent.ConfirmDialog)
        idle()

        assertTrue("先由 :core:data 的 use case 清缓存条目", bookCache.clearedAll)
        assertEquals(
            "再由平台清缓存目录本身 —— 两步缺一不可",
            1,
            platform.cacheDirectoriesClearedCount,
        )
        assertTrue(
            "清书缓存**不该**去动 OkHttp 缓存",
            platform.clearedCaches.isEmpty(),
        )
    }

    @Test
    fun `确认收缩数据库只走usecase`() {
        val platform = FakeDownloadCachePlatform()
        val database = FakeDatabaseMaintenanceGateway()
        val viewModel = createViewModel(FakeSettingsGateway(), platform, database = database)
        idle()

        viewModel.onIntent(
            DownloadCacheConfigIntent.ShowDialog(DownloadCacheConfigDialog.ShrinkDatabase)
        )
        viewModel.onIntent(DownloadCacheConfigIntent.ConfirmDialog)
        idle()

        assertEquals(1, database.shrinkCount)
        assertTrue(platform.clearedCaches.isEmpty())
        assertEquals(0, platform.cacheDirectoriesClearedCount)
    }

    @Test
    fun `设置流变化会刷新state`() {
        val gateway = FakeSettingsGateway(DownloadCacheSettings(preDownloadNum = 10))
        val viewModel = createViewModel(gateway, FakeDownloadCachePlatform())
        idle()

        gateway.emit(DownloadCacheSettings(preDownloadNum = 42))
        idle()

        assertEquals(42, viewModel.uiState.value.settings.preDownloadNum)
    }

    private fun createViewModel(
        gateway: DownloadCacheSettingsGateway,
        platform: DownloadCachePlatform,
        bookCache: FakeBookCacheCleanupGateway = FakeBookCacheCleanupGateway(),
        database: FakeDatabaseMaintenanceGateway = FakeDatabaseMaintenanceGateway(),
    ) = DownloadCacheConfigViewModel(
        clearBookCacheUseCase = ClearBookCacheUseCase(bookCache),
        shrinkDatabaseUseCase = ShrinkDatabaseUseCase(database),
        settingsGateway = gateway,
        platform = platform,
    )

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
}

private class FakeDownloadCachePlatform(
    override val maxDownloadConcurrency: Int = 8,
    private val coverBytes: Long = 0,
    private val mangaBytes: Long = 0,
) : DownloadCachePlatform {

    val clearedCaches = mutableListOf<HttpCacheKind>()
    var cacheDirectoriesClearedCount = 0
    var imageCacheResizeCount = 0

    override suspend fun httpCacheSizeBytes(kind: HttpCacheKind): Long = when (kind) {
        HttpCacheKind.COVER -> coverBytes
        HttpCacheKind.MANGA -> mangaBytes
    }

    override suspend fun clearHttpCache(kind: HttpCacheKind) {
        clearedCaches += kind
    }

    override suspend fun clearCacheDirectories() {
        cacheDirectoriesClearedCount++
    }

    override fun resizeImageCache() {
        imageCacheResizeCount++
    }
}

private class FakeSettingsGateway(
    initial: DownloadCacheSettings = DownloadCacheSettings(),
) : DownloadCacheSettingsGateway {

    private val state = MutableStateFlow(initial)

    override val currentSettings: DownloadCacheSettings get() = state.value
    override val settings: Flow<DownloadCacheSettings> = state

    override suspend fun update(transform: (DownloadCacheSettings) -> DownloadCacheSettings) {
        state.value = transform(state.value)
    }

    fun emit(value: DownloadCacheSettings) {
        state.value = value
    }
}

private class FakeBookCacheCleanupGateway : BookCacheCleanupGateway {
    var clearedAll = false
    override fun clearAll() {
        clearedAll = true
    }

    override suspend fun clear(bookUrl: String): Boolean = true
}

private class FakeDatabaseMaintenanceGateway : DatabaseMaintenanceGateway {
    var shrinkCount = 0
    override suspend fun shrink() {
        shrinkCount++
    }
}
