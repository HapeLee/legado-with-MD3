package io.legado.app.platform

import android.net.Uri
import io.legado.app.domain.model.settings.ThemeExportData
import io.legado.app.feature.settings.thememanage.SavedThemeSummary
import io.legado.app.feature.settings.thememanage.ThemeManagePlatform
import io.legado.app.feature.settings.thememanage.ThemeMigrationResult
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemePackageManager

/**
 * M5-16a：[ThemeManagePlatform] 的 Android 实现。
 *
 * 迁移前这些调用在 `:app` 的 `ThemeManageViewModel` 里。本片把 VM 搬进 `:feature:settings`
 * 后，**读写**留在宿主侧 —— `ThemePackageManager`（1254 行）深绑 `Context` / `Uri` /
 * `AppCompatDelegate` / GSON，进不了共享层；`SavedTheme` 又携带靠 GSON 反射读写的
 * `ThemePackageManifest`（带 `@Keep` / `@SerializedName`）⇒ 只能以 [SavedThemeSummary] 投影过去。
 *
 * ### 按名字解析回真实对象的代价（有意接受）
 *
 * 契约以 `name` 为键（宿主侧每个主题一个目录、目录名即主题名），所以 `apply` / `delete` /
 * `export` 都要在这里先 `loadSavedThemes()` 找到真实 [SavedTheme]。这多一次目录扫描，
 * 但这些都是**用户点一次的**操作、且本身就要读盘 ⇒ 换取的是契约面里不出现平台路径与清单类型
 * （与 `CoverRulePlatform` 只传 `CoverRuleSpec` 原始值同一判据）。
 *
 * 一个刻意的宽松处：`deleteSavedTheme` 找不到对象时**仍然继续删**（用只带 `name` 的替身）——
 * 迁移前 `ThemePackageManager.deleteSavedTheme` 本身就只用到 `name` 与 `packageRootPath`，
 * 而「删一个界面上还看得见、但目录已被外部清掉的主题」不该报一个别的错。
 * `apply` 则相反：没有清单就**无法**应用 ⇒ 明确失败（迁移前那里是 `requireNotNull`）。
 */
class AndroidThemeManagePlatform(
    private val themePackageManager: ThemePackageManager,
) : ThemeManagePlatform {

    override suspend fun loadSavedThemes(): List<SavedThemeSummary> =
        themePackageManager.loadSavedThemes().map { it.toSummary() }

    override suspend fun hasLegacySavedThemes(): Boolean =
        themePackageManager.hasLegacySavedThemes()

    override suspend fun saveTheme(name: String, data: ThemeExportData?) {
        themePackageManager.saveTheme(name = name, data = data)
    }

    override suspend fun applySavedTheme(name: String): Result<Unit> {
        val theme = resolve(name).getOrElse { return Result.failure(it) }
        return themePackageManager.applySavedTheme(theme)
    }

    override suspend fun deleteSavedTheme(name: String): Result<Unit> {
        // 见 KDoc：找不到也照删（只有 name 是必需的）。
        val theme = resolve(name).getOrNull() ?: SavedTheme(name = name, data = ThemeExportData())
        return themePackageManager.deleteSavedTheme(theme)
    }

    override suspend fun exportPackage(
        uri: String,
        themeName: String?,
        themeData: ThemeExportData?,
        savedThemeName: String?,
    ): Result<Unit> {
        val savedTheme = savedThemeName
            ?.let { resolve(it).getOrElse { error -> return Result.failure(error) } }
        return themePackageManager.exportPackage(
            uri = Uri.parse(uri),
            themeName = themeName,
            themeData = themeData,
            savedTheme = savedTheme,
        )
    }

    override suspend fun importPackage(uri: String): Result<Unit> =
        themePackageManager.importPackage(Uri.parse(uri))

    override suspend fun importLegacyJson(uri: String): Result<Unit> =
        themePackageManager.importLegacyJson(Uri.parse(uri))

    override suspend fun migrateLegacySavedThemes(): ThemeMigrationResult {
        val result = themePackageManager.migrateLegacySavedThemes()
        return ThemeMigrationResult(
            migratedCount = result.migratedCount,
            failedCount = result.failedCount,
        )
    }

    private suspend fun resolve(name: String): Result<SavedTheme> =
        themePackageManager.loadSavedThemes()
            .firstOrNull { it.name == name }
            ?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("Saved theme not found: $name"))

    private fun SavedTheme.toSummary(): SavedThemeSummary = SavedThemeSummary(
        name = name,
        data = data,
    )
}
