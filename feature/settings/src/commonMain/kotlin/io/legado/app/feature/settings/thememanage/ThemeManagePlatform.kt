package io.legado.app.feature.settings.thememanage

import io.legado.app.domain.model.settings.ThemeExportData

/**
 * 已保存主题在共享层的**投影**（M5-16a）。
 *
 * 为什么不直接用 `:app` 的 `SavedTheme`：它定义在 `help/config/ThemeImportExport.kt`，携带
 * `ThemePackageManifest`，而后者靠 **GSON 反射**读写（`GSON.toJson/fromJson`）⇒ 带
 * `@Keep` + `@SerializedName` 两个注解。`com.google.gson.annotations` 与
 * `androidx.annotation.Keep` **都进不了 `commonMain`**（与 M5-12a 的 `CoverAlbumImageInput`
 * 同一类阻塞：`java.io.InputStream` 出不了 `:app`）。
 *
 * ⚠️ 这里**刻意只投影 UI 真正需要的两样**：`name`（标识 + 展示）与 `data`（编辑页要改的
 * 配置，本身已是共享模型 [ThemeExportData]）。`packageRootPath` / `packageManifest` 是平台侧
 * 的组织细节，留在 `:app`（与 `CoverRulePlatform` 只传 `CoverRuleSpec` 原始值同一判据）。
 */
data class SavedThemeSummary(
    val name: String,
    val data: ThemeExportData,
)

/**
 * 旧版主题（`*.json`）迁移结果。
 *
 * 对应 `:app` 的 `LegacyThemeMigrationResult`（留在宿主）—— 只是两个计数，
 * 共享层自己定义一个，避免把宿主类型拉进契约面。
 */
data class ThemeMigrationResult(
    val migratedCount: Int,
    val failedCount: Int,
)

/**
 * `themeManage` 页的平台面（M5-16a）。
 *
 * 迁移前这些调用在 `:app` 的 `ThemeManageViewModel` 里。本片把 VM 搬进 `:feature:settings`
 * 后，**读写**留在宿主侧 —— `ThemePackageManager`（1254 行）深绑 `Context` / `Uri` /
 * `AppCompatDelegate` / GSON，出不了 `:app`。
 *
 * ### 键为什么是 `name`
 *
 * 宿主侧 `savedThemesRoot` 下**每个主题一个目录、目录名即主题名**
 * （`savedThemeDir(theme.name)`），旧版 JSON 也是 `"$name.json"` ⇒ `name` 在宿主侧本就是唯一
 * 标识。于是契约不必把 `SavedTheme` 整个穿过来：调用方给名字，实现侧自己解析回真实对象
 * （`exportPackage` 需要 `packageRootPath` / `packageManifest`，这些只有宿主知道）。
 *
 * ### `uri` 为什么是 `String`
 *
 * 迁移前 VM 里是 `Uri.parse(intent.uri)`。`android.net.Uri` 进不了共享层 ⇒ 契约收原始字符串，
 * 由实现侧 `Uri.parse`（与 `CoverAlbumProvider` 收 URI 串同一处理）。
 *
 * 实现：[io.legado.app.platform.AndroidThemeManagePlatform]（`:app`），Koin 注入。
 */
interface ThemeManagePlatform {

    suspend fun loadSavedThemes(): List<SavedThemeSummary>

    suspend fun hasLegacySavedThemes(): Boolean

    /** 失败时**抛异常**（迁移前 `ThemePackageManager.saveTheme` 就是这么定义的），由 VM 包 `runCatching`。 */
    suspend fun saveTheme(name: String, data: ThemeExportData?)

    suspend fun applySavedTheme(name: String): Result<Unit>

    suspend fun deleteSavedTheme(name: String): Result<Unit>

    suspend fun exportPackage(
        uri: String,
        themeName: String? = null,
        themeData: ThemeExportData? = null,
        savedThemeName: String? = null,
    ): Result<Unit>

    suspend fun importPackage(uri: String): Result<Unit>

    suspend fun importLegacyJson(uri: String): Result<Unit>

    suspend fun migrateLegacySavedThemes(): ThemeMigrationResult
}
