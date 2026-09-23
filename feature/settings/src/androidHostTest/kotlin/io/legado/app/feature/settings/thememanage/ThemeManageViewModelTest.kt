package io.legado.app.feature.settings.thememanage

import android.app.Application
import android.os.Looper
import io.legado.app.domain.model.settings.ThemeExportData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * `ThemeManageViewModel` 的行为基线（M5-16b 新增；迁移前零测试）。
 *
 * M5-16a 把这个 VM 迁进了共享层，实质改动是**平台读写收成一个窄契约**
 * [ThemeManagePlatform] ⇒ 用例重心在**契约交互**：断言「VM 调了契约的哪个方法、按什么参数、
 * 结果如何映射到状态与 effect」，而不只是「状态变了」。
 *
 * 几条特意钉死的：
 *
 * - **互斥用的是 `tryLock` 而不是排队**：`launchExclusive` 在锁被持有时**直接丢弃**新意图
 *   （迁移前就是这样）。这条很容易被后来的重构改成 `withLock`（排队）而没人发现 ——
 *   后果是用户连点两次会执行两次导出/导入。注意它是**同步**判定：`tryLock()` 在
 *   `viewModelScope.launch` **之前**，所以「第一次的协程还没跑」与「第二次被丢弃」是确定的，
 *   不依赖 `idle()` 时机（这一点与 M5-9a 那批踩过 IO 尾巴的用例不同）。
 * - **失败 → 语义枚举 + detail**：迁移前是 `R.string.*` 的 id，M5-16a 换成
 *   [ThemeManageText]。映射写错的表现是「失败时提示成成功」，而这类 bug 只有单测能拦。
 * - **改名保存要删旧名**（`replacedTheme.name != name` 才删）：写反的表现是「改个名字就多出
 *   一条主题」或「同一次保存把自己删掉」。
 * - **旧版迁移的 `hasLegacyThemes` 取自 `failedCount > 0`**（不是「迁移过就为 false」）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ThemeManageViewModelTest {

    @Test
    fun `初始加载把主题列表与旧版标记放进状态`() {
        val platform = FakeThemeManagePlatform(
            themes = listOf(summary("晨间"), summary("夜间")),
            hasLegacy = true,
        )

        val viewModel = createViewModel(platform)
        idle()

        assertEquals(listOf("晨间", "夜间"), viewModel.uiState.value.savedThemes.map { it.name })
        assertTrue("旧版主题存在时要能提示迁移入口", viewModel.uiState.value.hasLegacyThemes)
        assertFalse(viewModel.uiState.value.loading)
        assertEquals("列表与旧版标记各读一次", 1, platform.loadCount)
        assertEquals(1, platform.hasLegacyCount)
    }

    @Test
    fun `保存成功后刷新列表`() {
        val platform = FakeThemeManagePlatform()
        val viewModel = createViewModel(platform)
        idle()

        viewModel.onIntent(ThemeManageIntent.SaveTheme(name = "新主题", data = ThemeExportData(appTheme = "1")))
        idle()

        assertEquals(listOf("新主题" to "1"), platform.saved)
        assertEquals("保存后要刷新（新主题得出现在列表里）", 2, platform.loadCount)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `保存失败映射到保存失败并带 detail`() {
        val platform = FakeThemeManagePlatform(saveError = IllegalStateException("disk full"))
        val viewModel = createViewModel(platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(ThemeManageIntent.SaveTheme(name = "新主题"))
        idle()

        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.SaveFailed, "disk full")),
            effects.toList(),
        )
        assertFalse("失败后 loading 要落下来，否则界面一直转圈", viewModel.uiState.value.loading)
        assertEquals("失败不该触发刷新", 1, platform.loadCount)
    }

    @Test
    fun `改名保存会删掉旧名字`() {
        val platform = FakeThemeManagePlatform(themes = listOf(summary("旧名")))
        val viewModel = createViewModel(platform)
        idle()

        viewModel.onIntent(
            ThemeManageIntent.SaveTheme(
                name = "新名",
                data = ThemeExportData(appTheme = "2"),
                replacedTheme = summary("旧名"),
            )
        )
        idle()

        assertEquals(listOf("旧名"), platform.deleted)
        assertEquals(listOf("新名" to "2"), platform.saved)
    }

    @Test
    fun `名字没变时不删自己`() {
        val platform = FakeThemeManagePlatform(themes = listOf(summary("同名")))
        val viewModel = createViewModel(platform)
        idle()

        viewModel.onIntent(
            ThemeManageIntent.SaveTheme(
                name = "同名",
                data = ThemeExportData(appTheme = "3"),
                replacedTheme = summary("同名"),
            )
        )
        idle()

        assertEquals("`takeIf { it.name != intent.name }` 这条分支写反就会把自己删掉", emptyList<String>(), platform.deleted)
        assertEquals(1, platform.saved.size)
    }

    @Test
    fun `应用失败映射到应用失败`() {
        val platform = FakeThemeManagePlatform(
            themes = listOf(summary("坏主题")),
            applyResult = Result.failure(IllegalStateException("missing manifest")),
        )
        val viewModel = createViewModel(platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(ThemeManageIntent.ApplySavedTheme(summary("坏主题")))
        idle()

        assertEquals(listOf("坏主题"), platform.applied)
        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.ApplyFailed, "missing manifest")),
            effects.toList(),
        )
    }

    @Test
    fun `删除失败映射到删除失败`() {
        val platform = FakeThemeManagePlatform(
            themes = listOf(summary("删不掉")),
            deleteResult = Result.failure(IllegalStateException("read only")),
        )
        val viewModel = createViewModel(platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(ThemeManageIntent.DeleteSavedTheme(summary("删不掉")))
        idle()

        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.DeleteFailed, "read only")),
            effects.toList(),
        )
    }

    @Test
    fun `导出成功与失败分别映射且都带上了保存主题名`() {
        val ok = FakeThemeManagePlatform()
        val okViewModel = createViewModel(ok)
        idle()
        val okEffects = collect(okViewModel)

        okViewModel.onIntent(
            ThemeManageIntent.ExportPackage(
                uri = "content://export/1",
                themeName = "晨间",
                themeData = ThemeExportData(appTheme = "1"),
                savedThemeName = "晨间",
            )
        )
        idle()

        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.ExportSuccess)),
            okEffects.toList(),
        )
        assertEquals(listOf("content://export/1" to "晨间"), ok.exports)

        val bad = FakeThemeManagePlatform(exportResult = Result.failure(IllegalStateException("zip failed")))
        val badViewModel = createViewModel(bad)
        idle()
        val badEffects = collect(badViewModel)

        badViewModel.onIntent(ThemeManageIntent.ExportPackage(uri = "content://export/2"))
        idle()

        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.ExportFailed, "zip failed")),
            badEffects.toList(),
        )
    }

    @Test
    fun `导入成功刷新列表失败只提示`() {
        val platform = FakeThemeManagePlatform()
        val viewModel = createViewModel(platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(ThemeManageIntent.ImportPackage("content://import/1"))
        idle()

        assertEquals(
            listOf(ThemeManageEffect.ShowResult(ThemeManageText.ImportSuccess)),
            effects.toList(),
        )
        assertEquals("导入成功要刷新（新主题得出现在列表里）", 2, platform.loadCount)

        platform.importResult = Result.failure(IllegalStateException("bad zip"))
        viewModel.onIntent(ThemeManageIntent.ImportLegacyJson("content://import/2"))
        idle()

        assertEquals(
            listOf("content://import/2"),
            platform.legacyJsons,
        )
        assertEquals(
            ThemeManageEffect.ShowResult(ThemeManageText.ImportFailed, "bad zip"),
            effects.last(),
        )
        assertEquals("失败不该刷新", 2, platform.loadCount)
    }

    @Test
    fun `旧版迁移把两个计数发出去并在有失败时保留旧版标记`() {
        val platform = FakeThemeManagePlatform(migration = ThemeMigrationResult(migratedCount = 3, failedCount = 0))
        val viewModel = createViewModel(platform)
        idle()
        val effects = collect(viewModel)

        viewModel.onIntent(ThemeManageIntent.MigrateLegacyThemes)
        idle()

        assertEquals(
            listOf(ThemeManageEffect.LegacyMigrationFinished(migratedCount = 3, failedCount = 0)),
            effects.toList(),
        )
        assertFalse("全部迁完 ⇒ 不再提示还有旧版主题", viewModel.uiState.value.hasLegacyThemes)

        val partial = FakeThemeManagePlatform(migration = ThemeMigrationResult(migratedCount = 1, failedCount = 2))
        val partialViewModel = createViewModel(partial)
        idle()
        partialViewModel.onIntent(ThemeManageIntent.MigrateLegacyThemes)
        idle()

        assertTrue(
            "有失败 ⇒ 要保留标记（否则用户以为迁干净了）",
            partialViewModel.uiState.value.hasLegacyThemes,
        )
    }

    @Test
    fun `前一个操作仍挂起时新意图被丢弃而不是排队`() {
        // `viewModelScope` 在 `Dispatchers.Main.immediate` 上 ⇒ `launch` 会**内联执行到第一个
        // 挂起点**。所以「第一次已开始」不需要 `idle()`；而要让锁**仍被持有**，第一次必须真的
        // 挂在契约调用上 —— 用一个闸门卡住它（这一点是本用例第一次写错时学到的：
        // 若契约 fake 全同步，第一次当场跑完、锁已释放，第二次自然不被丢弃，测不到互斥）。
        val gate = CompletableDeferred<Unit>()
        val platform = FakeThemeManagePlatform().apply { importGate = gate }
        val viewModel = createViewModel(platform)
        idle()

        viewModel.onIntent(ThemeManageIntent.ImportPackage("content://first"))
        assertEquals(
            "第一次必须已经开始，否则这个用例根本没在测互斥",
            listOf("content://first"),
            platform.imports,
        )

        // 第一次还挂在契约上（闸门未放行）⇒ 锁仍被持有 ⇒ 第二次必须被同步丢弃
        viewModel.onIntent(ThemeManageIntent.ImportPackage("content://second"))
        assertEquals(
            "第二次意图必须被丢弃 —— 改成 withLock（排队）会让导出/导入被执行两遍",
            listOf("content://first"),
            platform.imports,
        )

        gate.complete(Unit)
        idle()
        assertEquals("放行后也不该补跑第二次", listOf("content://first"), platform.imports)
    }

    @Test
    fun `弹层意图只改状态且 UpdateSaveName 在非 Save 弹层时不动状态`() {
        val viewModel = createViewModel(FakeThemeManagePlatform())
        idle()

        viewModel.onIntent(ThemeManageIntent.OpenSaveDialog)
        viewModel.onIntent(ThemeManageIntent.UpdateSaveName("我的主题"))
        assertEquals(ThemeManageDialog.Save("我的主题"), viewModel.uiState.value.dialog)

        viewModel.onIntent(ThemeManageIntent.DismissDialog)
        assertNull(viewModel.uiState.value.dialog)

        // 守卫分支：没有 Save 弹层时 UpdateSaveName 必须原样返回状态
        viewModel.onIntent(ThemeManageIntent.UpdateSaveName("没人接收"))
        assertNull("没有 Save 弹层时不该凭空造一个", viewModel.uiState.value.dialog)

        viewModel.onIntent(ThemeManageIntent.OpenApplyDialog(summary("晨间")))
        assertEquals(ThemeManageDialog.Apply(summary("晨间")), viewModel.uiState.value.dialog)
    }

    private fun createViewModel(platform: ThemeManagePlatform) = ThemeManageViewModel(platform)

    private fun collect(viewModel: ThemeManageViewModel): MutableList<ThemeManageEffect> {
        val out = mutableListOf<ThemeManageEffect>()
        CoroutineScope(Dispatchers.Unconfined).launch {
            viewModel.effects.collect { out += it }
        }
        return out
    }

    private fun idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun summary(name: String) = SavedThemeSummary(name = name, data = ThemeExportData())
}

private class FakeThemeManagePlatform(
    themes: List<SavedThemeSummary> = emptyList(),
    hasLegacy: Boolean = false,
    saveError: Throwable? = null,
    var applyResult: Result<Unit> = Result.success(Unit),
    var deleteResult: Result<Unit> = Result.success(Unit),
    var exportResult: Result<Unit> = Result.success(Unit),
    var importResult: Result<Unit> = Result.success(Unit),
    var migration: ThemeMigrationResult = ThemeMigrationResult(0, 0),
) : ThemeManagePlatform {

    var themes: List<SavedThemeSummary> = themes
    var hasLegacy: Boolean = hasLegacy
    var saveError: Throwable? = saveError

    var loadCount = 0
    var hasLegacyCount = 0
    val saved = mutableListOf<Pair<String, String?>>()
    val deleted = mutableListOf<String>()
    val applied = mutableListOf<String>()
    val exports = mutableListOf<Pair<String, String?>>()
    val imports = mutableListOf<String>()
    val legacyJsons = mutableListOf<String>()
    var migrationCount = 0

    /** 见互斥那条用例：卡住 [importPackage] 以模拟「操作仍在挂起」。 */
    var importGate: CompletableDeferred<Unit>? = null

    override suspend fun loadSavedThemes(): List<SavedThemeSummary> {
        loadCount++
        return themes
    }

    override suspend fun hasLegacySavedThemes(): Boolean {
        hasLegacyCount++
        return hasLegacy
    }

    override suspend fun saveTheme(name: String, data: ThemeExportData?) {
        saveError?.let { throw it }
        saved += name to data?.appTheme
        // 模拟宿主行为：存完能在列表里看到
        themes = themes.filterNot { it.name == name } + SavedThemeSummary(name, data ?: ThemeExportData())
    }

    override suspend fun applySavedTheme(name: String): Result<Unit> {
        applied += name
        return applyResult
    }

    override suspend fun deleteSavedTheme(name: String): Result<Unit> {
        deleted += name
        if (deleteResult.isSuccess) {
            themes = themes.filterNot { it.name == name }
        }
        return deleteResult
    }

    override suspend fun exportPackage(
        uri: String,
        themeName: String?,
        themeData: ThemeExportData?,
        savedThemeName: String?,
    ): Result<Unit> {
        exports += uri to savedThemeName
        return exportResult
    }

    override suspend fun importPackage(uri: String): Result<Unit> {
        imports += uri
        importGate?.await()
        return importResult
    }

    override suspend fun importLegacyJson(uri: String): Result<Unit> {
        legacyJsons += uri
        return importResult
    }

    override suspend fun migrateLegacySavedThemes(): ThemeMigrationResult {
        migrationCount++
        return migration
    }
}
