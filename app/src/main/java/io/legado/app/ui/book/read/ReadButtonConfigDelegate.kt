package io.legado.app.ui.book.read

import android.content.Context
import android.net.Uri
import io.legado.app.help.coroutine.Coroutine
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 阅读菜单按钮配置域（R2.2 续批）。
 *
 * 管顶栏/底栏按钮的排序与开关，以及两处自定义图标的落盘。按钮列表本身仍住在
 * [ReadMenuConfig] 里（菜单栏各处直读，搬出去要改一大片 UI），故本 delegate
 * **无自持状态**，和 [ReadConfigUpdateDelegate] 同形：一切读写经 [Host]。
 */
class ReadButtonConfigDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
) {

    interface Host {
        fun updateMenuConfig(transform: (ReadMenuConfig) -> ReadMenuConfig)

        /** 自定义图标落盘后走配置更新分发，通知菜单重读。 */
        fun applyConfigUpdate(update: ConfigUpdate)
    }

    /** 从 SharedPreferences 读回两条按钮配置，刷进 menuConfig。VM 构造时调一次。 */
    fun refresh() {
        val titleBarButtons = loadButtonConfig(TITLE_BAR_ICON_PREFS, TITLE_BAR_ICON_KEY)
        val bottomBarButtons = loadButtonConfig(TOOL_BUTTON_PREFS, TOOL_BUTTON_KEY)
        host.updateMenuConfig {
            it.copy(
                titleBarButtons = titleBarButtons.toImmutableList(),
                bottomBarButtons = bottomBarButtons.toImmutableList(),
            )
        }
    }

    fun saveTitleBarButtons(items: List<ReadBookButtonConfigItem>) {
        val normalized = normalizeButtonConfig(items)
        saveButtonConfig(TITLE_BAR_ICON_PREFS, TITLE_BAR_ICON_KEY, normalized)
        host.updateMenuConfig { it.copy(titleBarButtons = normalized.toImmutableList()) }
    }

    fun saveMenuButtons(items: List<ReadBookButtonConfigItem>) {
        val normalized = normalizeButtonConfig(items)
        saveButtonConfig(TOOL_BUTTON_PREFS, TOOL_BUTTON_KEY, normalized)
        host.updateMenuConfig { it.copy(bottomBarButtons = normalized.toImmutableList()) }
    }

    fun saveMenuCustomIcon(id: String, uri: Uri) {
        copyCustomIcon(id, uri, "read_menu_icons") { path ->
            ConfigUpdate.MenuCustomIcon(id, path)
        }
    }

    fun saveTitleBarCustomIcon(id: String, uri: Uri) {
        copyCustomIcon(id, uri, "title_bar_icons") { path ->
            ConfigUpdate.TitleBarCustomIcon(id, path)
        }
    }

    private fun copyCustomIcon(
        id: String,
        uri: Uri,
        directoryName: String,
        update: (path: String) -> ConfigUpdate,
    ) {
        Coroutine.async(scope, Dispatchers.IO) {
            val iconFile = File(context.filesDir, "$directoryName/$id.png")
            iconFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                iconFile.outputStream().use { output -> input.copyTo(output) }
            }
            host.applyConfigUpdate(update(iconFile.absolutePath))
        }
    }

    private fun loadButtonConfig(
        preferenceName: String,
        key: String,
    ): List<ReadBookButtonConfigItem> {
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val raw = prefs.getString(key, null)
            ?.split(";")
            ?.mapNotNull { token ->
                val parts = token.split(",")
                val id = parts.getOrNull(0)?.takeIf { it in ReadBookButtonIds }
                val enabled = parts.getOrNull(1)?.toBooleanStrictOrNull()
                if (id != null && enabled != null) {
                    ReadBookButtonConfigItem(id, enabled)
                } else {
                    null
                }
            }
            ?: emptyList()

        return if (raw.isEmpty()) {
            ReadBookButtonIds.map { id ->
                ReadBookButtonConfigItem(
                    id = id,
                    enabled = id in DEFAULT_ENABLED_BUTTON_IDS,
                )
            }
        } else {
            normalizeButtonConfig(raw)
        }
    }

    private fun saveButtonConfig(
        preferenceName: String,
        key: String,
        items: List<ReadBookButtonConfigItem>,
    ) {
        val value = items.joinToString(";") { "${it.id},${it.enabled}" }
        context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }

    private fun normalizeButtonConfig(
        items: List<ReadBookButtonConfigItem>,
    ): List<ReadBookButtonConfigItem> {
        val seen = mutableSetOf<String>()
        val normalized = items.mapNotNull { item ->
            val id = item.id
            if (id in ReadBookButtonIds && seen.add(id)) {
                ReadBookButtonConfigItem(id, item.enabled)
            } else {
                null
            }
        }.toMutableList()
        ReadBookButtonIds.forEach { id ->
            if (seen.add(id)) {
                normalized.add(ReadBookButtonConfigItem(id, false))
            }
        }
        return normalized
    }

    private companion object {
        const val TITLE_BAR_ICON_PREFS = "title_bar_icons"
        const val TITLE_BAR_ICON_KEY = "icons"
        const val TOOL_BUTTON_PREFS = "tool_button_config"
        const val TOOL_BUTTON_KEY = "tool_buttons"
        val DEFAULT_ENABLED_BUTTON_IDS = setOf(
            "search",
            "auto_page",
            "catalog",
            "read_aloud",
            "setting",
        )
    }
}
