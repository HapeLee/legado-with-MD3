package io.legado.app.ui.book.read

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.ReadStyleColorKey
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadStyleMutation
import io.legado.app.domain.gateway.ReadStyleStringKey
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.model.settings.ThemeSettings
import io.legado.app.domain.model.settings.isEyeProtectionConfigured
import io.legado.app.model.ReadSessionState
import io.legado.app.utils.hexString
import io.legado.app.utils.postEvent
import io.legado.app.utils.share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.graphics.ColorUtils as AndroidColorUtils

/**
 * 阅读样式域（R2.2 续批）：字体、取色、背景图、样式方案导入导出、日夜切换与其提醒、护眼。
 *
 * **无自持状态**：`styleConfig` / `sheetConfig` / `activeReminder` / `eyeProtection`
 * 都被菜单栏与各设置弹层直读，且 `styleConfig` 的重建由 VM 的 `collectReadStyle()`
 * 统一驱动（见那里的三处触发说明）。故与 [ReadConfigUpdateDelegate] 同形：
 * 状态留在 [ReadBookUiState]，读写一律经 [Host]。
 *
 * 日夜提醒的三个私有变量（冷却时间戳、两个"已被划掉"标记）和提醒队列搬进本类——
 * 它们只被日夜逻辑读写，`ReadConfigUpdateDelegate` 改主题时经
 * [resetDayNightReminderDismissal] 复位。
 */
class ReadStyleDelegate(
    private val context: Context,
    private val scope: CoroutineScope,
    private val host: Host,
    private val readSettingsRepository: ReadSettingsRepository,
    private val readStyleGateway: ReadStyleGateway,
    private val appShellSettingsGateway: AppShellSettingsGateway,
    private val themeSettingsGateway: ThemeSettingsGateway,
) {

    interface Host {
        val uiState: ReadBookUiState

        /** 当前是否深色主题。取自 AppUiConfiguration，不是排版配置。 */
        val isNightTheme: Boolean

        fun updateState(transform: (ReadBookUiState) -> ReadBookUiState)

        fun emitEffect(effect: ReadBookEffect)

        fun applyConfigUpdate(update: ConfigUpdate)
    }

    private var lastSwitchDayNightReminderTime: Long = 0L
    private val reminderQueue = ArrayDeque<ReminderUiState>()
    private var hasDismissedDarkReminder = false
    private var hasDismissedLightReminder = false

    // --- 字体 ---

    fun selectFont(path: String) {
        readStyleGateway.updateCurrentStyle(stringMutation(ReadStyleStringKey.TextFont, path))
        emitConfigUpdate(
            ConfigUpdateAction.UpdateChapterStyle,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdateStyle,
        )
    }

    fun selectTitleFont(path: String) {
        readStyleGateway.updateCurrentStyle(stringMutation(ReadStyleStringKey.TitleFont, path))
        emitConfigUpdate(
            ConfigUpdateAction.UpdateChapterStyle,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdateStyle,
        )
    }

    fun selectSystemTypeface(index: Int) = selectSystemTypeface(ReadStyleStringKey.TextFont, index)

    fun selectTitleSystemTypeface(index: Int) =
        selectSystemTypeface(ReadStyleStringKey.TitleFont, index)

    private fun selectSystemTypeface(key: ReadStyleStringKey, index: Int) {
        readStyleGateway.updateCurrentStyle(stringMutation(key, ""))
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            readSettingsRepository.setSystemTypefaces(index)
        }
        emitConfigUpdate(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }

    // --- 样式方案 ---

    fun setThemeGridColumns(columns: Int) {
        scope.launch {
            readSettingsRepository.update { it.copy(themeGridColumns = columns) }
        }
    }

    fun saveCurrentStyle() {
        readStyleGateway.save()
    }

    fun addStyle() {
        host.applyConfigUpdate(ConfigUpdate.StyleSelect(readStyleGateway.addStyle()))
    }

    /**
     * 删除"当前正在编辑/正在使用"的那份样式。返回是否真的删了。
     *
     * **不在这里关编辑面板**：关面板 + 复位菜单统一由调用方走
     * [ReadBookViewModel.dismissActiveSheet]（浮层全关那条路）。之前这里直接
     * `activeSheet = null`，绕过了 `menuHiddenBySheet` 的复位 ⇒ 主题列表面板会留在
     * "滑出屏幕"的位移上再也回不来（点"展开背景"出不来）。
     */
    fun deleteCurrentStyle(): Boolean {
        if (!readStyleGateway.deleteCurrentStyle()) return false
        // 旧 View deleteDur = [1, 2, 5]。
        emitConfigUpdate(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdatePageAnim,
        )
        return true
    }

    fun moveStyle(from: Int, to: Int) {
        if (!readStyleGateway.moveStyle(from, to)) return
        emitConfigUpdate(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }

    fun deleteStylesAt(indices: List<Int>) {
        if (!readStyleGateway.deleteStylesAt(indices)) return
        emitConfigUpdate(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.ReloadContent,
            ConfigUpdateAction.UpdatePageAnim,
        )
    }

    /** 指定下标样式的名字（空名回落"阅读主题"）—— 单份分享/导出的文件名用。 */
    private fun styleNameAt(index: Int): String =
        host.uiState.styleConfig.styleItems.getOrNull(index)?.name
            ?.takeIf { it.isNotBlank() } ?: "阅读主题"

    fun requestExportStyles(indices: List<Int>) {
        if (indices.isEmpty()) return
        val singleIndex = indices.singleOrNull()
        host.emitEffect(
            ReadBookEffect.OpenReadStyleConfigsExport(
                // 单选 ⇒ 默认文件名就是 <样式名>.zip；多选维持 阅读主题.zip
                fileName = readStyleExportFileName(
                    singleIndex?.let(::styleNameAt) ?: "阅读主题"
                ),
                indices = indices,
            )
        )
    }

    fun exportStylesAt(uri: Uri, indices: List<Int>) {
        withStyleFileOperation("导出阅读样式失败") {
            // 与分享同构：单选直接导出单份（不套外层大包），多选才打批量包
            val singleIndex = indices.singleOrNull()
            val bytes = if (singleIndex != null) {
                readStyleGateway.exportStyleAt(singleIndex)
                    ?: error("style index out of range: $singleIndex")
            } else {
                readStyleGateway.exportStyles(indices)
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw FileNotFoundException(uri.toString())
            context.getString(R.string.export_success)
        }
    }

    /**
     * 分享选中的主题：复用导出的打包结果，写进 cache 让 FileProvider 可读，
     * 再直接调系统分享面板（[io.legado.app.utils.share]，authority 与 manifest 里的一致）。
     * 不弹文件选择器、不改数据，因此也不清空选中。
     *
     * 只选中 1 个 ⇒ 直接分享**单份**（文件名 = `<样式名>.zip`），不套"阅读主题.zip"外层大包；
     * 多选 ⇒ 保持原来的批量包。
     */
    fun shareStyles(indices: List<Int>) {
        if (indices.isEmpty()) return
        withStyleFileOperation("分享阅读主题失败") {
            val singleIndex = indices.singleOrNull()
            val bytes: ByteArray
            val fileName: String
            if (singleIndex != null) {
                bytes = readStyleGateway.exportStyleAt(singleIndex)
                    ?: error("style index out of range: $singleIndex")
                fileName = readStyleExportFileName(styleNameAt(singleIndex))
            } else {
                bytes = readStyleGateway.exportStyles(indices)
                fileName = readStyleExportFileName("阅读主题")
            }
            val file = File(context.cacheDir, fileName)
            file.writeBytes(bytes)
            context.share(file, "application/zip", context.getString(R.string.share))
            null
        }
    }

    fun moveStylesToEdge(indices: List<Int>, toTop: Boolean) {
        if (!readStyleGateway.moveStylesToEdge(indices, toTop)) return
        emitConfigUpdate(ConfigUpdateAction.UpdateStyle, ConfigUpdateAction.ReloadContent)
    }

    fun applyPresetTheme(presetIndex: Int) {
        if (!readStyleGateway.applyPreset(presetIndex)) return
        // 旧 View 恢复预设 = [1, 2, 5]；Compose 另需 RefreshInlineImages（重拉书源图片）与
        // UpdateSystemUi（状态栏图标随样式里的 darkStatusIcon 变化，Compose 不重建 Activity）。
        emitConfigUpdate(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.UpdateSystemUi,
            ConfigUpdateAction.RefreshInlineImages,
            ConfigUpdateAction.ReloadContent,
        )
    }

    fun openBgTextConfig(index: Int) {
        scope.launch {
            readSettingsRepository.setStyleSelect(ReadSessionState.isComic, index)
        }
        host.updateState {
            val next = it.withSheet(ReadBookSheet.BgTextConfig)
            if (it.menuState.currentRoute == ReadBookMenuRoute.ThemeList) {
                next.copy(menuState = it.menuState.copy(visible = false), menuHiddenBySheet = true)
            } else {
                next
            }
        }
    }

    // --- 背景图与导入导出 ---

    fun applyBackgroundImage(uri: Uri) {
        withStyleFileOperation("选择阅读背景图失败") {
            val path = saveBackgroundImage(uri)
            readStyleGateway.setCurrentBackgroundImage(path)
            emitConfigUpdate(ConfigUpdateAction.UpdateBackground)
            context.getString(R.string.success)
        }
    }

    fun applyBackgroundImageForMode(uri: Uri, isNight: Boolean) {
        withStyleFileOperation("选择阅读背景图失败") {
            val path = saveBackgroundImage(uri)
            readStyleGateway.setCurrentBackgroundImageForMode(path, isNight)
            emitConfigUpdate(ConfigUpdateAction.UpdateBackground)
            context.getString(R.string.success)
        }
    }

    fun importConfig(uri: Uri) {
        withStyleFileOperation("导入阅读样式失败") {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw FileNotFoundException(uri.toString())
            readStyleGateway.importCurrentStyle(bytes)
            // 旧 View 导入配置 = [1, 2, 5]。
            emitConfigUpdate(
                ConfigUpdateAction.UpdateBackground,
                ConfigUpdateAction.UpdateStyle,
                ConfigUpdateAction.ReloadContent,
            )
            context.getString(R.string.import_success)
        }
    }

    /**
     * ⋮ 菜单"新增导入"：纯新增（追加到列表末尾，绝不碰任何已有主题）。
     *
     * 单份 ⇒ 立刻应用这个新主题（与"＋新建主题"同一条 `ConfigUpdate.StyleSelect` 路径，阅读背景马上变）；
     * 批量 ⇒ 保持静默，只刷新列表、绝不切换正在使用的主题。
     */
    fun importStylesAsNew(uri: Uri) {
        withStyleFileOperation("导入阅读样式失败") {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw FileNotFoundException(uri.toString())
            val newIndex = readStyleGateway.importStylesAsNew(bytes)
            if (newIndex != null) {
                // 单份：立刻应用新主题
                host.applyConfigUpdate(ConfigUpdate.StyleSelect(newIndex))
            } else {
                // 批量：只刷新列表，不动选中
                emitConfigUpdate(ConfigUpdateAction.UpdateStyle)
            }
            context.getString(R.string.import_success)
        }
    }

    fun exportConfig(uri: Uri) {
        withStyleFileOperation("导出阅读样式失败") {
            val bytes = readStyleGateway.exportCurrentStyle()
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw FileNotFoundException(uri.toString())
            context.getString(R.string.export_success)
        }
    }

    private fun saveBackgroundImage(uri: Uri): String {
        val name = queryDisplayName(uri)
        return context.contentResolver.openInputStream(uri)?.use {
            readStyleGateway.saveBackgroundImage(it, name)
        } ?: throw FileNotFoundException(uri.toString())
    }

    private inline fun withStyleFileOperation(
        logTag: String,
        // 返回 null = 不弹成功提示（如"分享"：系统分享面板本身就是反馈）
        crossinline block: () -> String?,
    ) {
        scope.launch(IO) {
            runCatching { block() }
                .onSuccess { message ->
                    if (message != null) host.emitEffect(ReadBookEffect.ShowToast(message))
                }
                .onFailure { throwable ->
                    AppLog.put(logTag, throwable)
                    host.emitEffect(
                        ReadBookEffect.LongToast(
                            throwable.localizedMessage ?: context.getString(R.string.error)
                        )
                    )
                }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
    }

    // --- 取色 ---

    fun colorSelected(dialogId: Int, color: Int) {
        when (dialogId) {
            ReadBookColorPickerIds.SHADOW_COLOR -> {
                readStyleGateway.updateCurrentStyle(colorMutation(ReadStyleColorKey.Shadow, color))
                emitRepaintTextActions()
            }

            ReadBookColorPickerIds.TEXT_COLOR -> {
                readStyleGateway.updateCurrentStyle(colorMutation(ReadStyleColorKey.Text, color))
                emitRepaintTextActions()
                postActionBarUpdateIfFollowingPage()
            }

            ReadBookColorPickerIds.TEXT_ACCENT_COLOR -> {
                readStyleGateway.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.TextAccent, color)
                )
                emitRepaintTextActions()
                postActionBarUpdateIfFollowingPage()
            }

            ReadBookColorPickerIds.BG_COLOR -> {
                readStyleGateway.updateCurrentStyle(
                    ReadStyleMutation.Background(0, "#${color.hexString}")
                )
                emitConfigUpdate(ConfigUpdateAction.UpdateBackground)
                postActionBarUpdateIfFollowingPage()
            }

            ReadBookColorPickerIds.TIP_HEADER_COLOR ->
                updateTipColor(ReadStyleColorKey.TipHeader, color)

            ReadBookColorPickerIds.TIP_FOOTER_COLOR ->
                updateTipColor(ReadStyleColorKey.TipFooter, color)

            ReadBookColorPickerIds.TIP_DIVIDER_COLOR ->
                updateTipColor(ReadStyleColorKey.TipDivider, color)

            ReadBookColorPickerIds.TITLE_COLOR -> {
                readStyleGateway.updateCurrentStyle(colorMutation(ReadStyleColorKey.Title, color))
                emitConfigUpdate(
                    ConfigUpdateAction.UpdateChapterStyle,
                    ConfigUpdateAction.ReloadContent,
                )
            }

            ReadBookColorPickerIds.MENU_BG_COLOR -> {
                scope.launch { readSettingsRepository.setReadMenuBgColor(color) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            ReadBookColorPickerIds.MENU_ACCENT_COLOR -> {
                scope.launch { readSettingsRepository.setReadMenuAccentColor(color) }
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            ReadBookColorPickerIds.UNDERLINE_COLOR -> {
                readStyleGateway.updateCurrentStyle(
                    colorMutation(ReadStyleColorKey.Underline, color)
                )
                emitRepaintTextActions()
            }
        }
    }

    private fun updateTipColor(key: ReadStyleColorKey, color: Int) {
        readStyleGateway.updateCurrentStyle(colorMutation(key, color))
        postEvent(EventBus.TIP_COLOR, "")
        emitConfigUpdate(ConfigUpdateAction.UpdateStyle)
    }

    /** 改颜色要重绘已排好的 TextPage，不只是换 paint。 */
    private fun emitRepaintTextActions() = emitConfigUpdate(
        ConfigUpdateAction.UpdateStyle,
        ConfigUpdateAction.UpdateContent,
        ConfigUpdateAction.InvalidateTextPage,
        ConfigUpdateAction.SubmitRenderTask,
    )

    private fun postActionBarUpdateIfFollowingPage() {
        if (readSettingsRepository.currentSettings.readBarStyleFollowPage) {
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }
    }

    // --- 日夜 ---

    fun toggleDayNight() {
        lastSwitchDayNightReminderTime = System.currentTimeMillis()
        resetDayNightReminderDismissal()
        // ThemeSettings reaches AppUiConfiguration through DataStore asynchronously. The
        // legacy View changed this mode synchronously before calling upBg()/upStyle(). Do
        // the same for the reader resolver, otherwise the immediately-started pagination
        // captures the old text color while Compose has already switched its background.
        val nextIsNight = !(ReadSessionState.isDarkThemeOverride ?: host.isNightTheme)
        val nextMode = if (nextIsNight) "2" else "1"
        ReadSessionState.isDarkThemeOverride = nextIsNight
        scope.launch {
            appShellSettingsGateway.update { it.copy(themeMode = nextMode) }
        }
        host.updateState {
            val newActiveReminder = if (it.activeReminder?.type is ReminderType.DayNightReminder) {
                null
            } else {
                it.activeReminder
            }
            it.copy(activeReminder = newActiveReminder)
        }
        // 排版值没变但解析后的生效值变了（颜色按模式取），经 gateway 统一重新发布，
        // 由 collectReadStyle 重建 styleConfig + sheetConfig。
        readStyleGateway.notifyModeChanged()
        reminderQueue.removeAll { it.type is ReminderType.DayNightReminder }
        emitConfigUpdate(
            ConfigUpdateAction.UpdateBackground,
            ConfigUpdateAction.UpdateStyle,
            ConfigUpdateAction.UpdateContent,
            ConfigUpdateAction.InvalidateTextPage,
            ConfigUpdateAction.SubmitRenderTask,
            ConfigUpdateAction.UpdateSystemUi,
        )
    }

    fun resetDayNightReminderDismissal() {
        hasDismissedDarkReminder = false
        hasDismissedLightReminder = false
    }

    fun isDayNightSwitchCoolingDown(): Boolean =
        System.currentTimeMillis() - lastSwitchDayNightReminderTime < REMINDER_COOLDOWN_MS

    /**
     * 环境光变化时判断要不要提示切换日夜。
     * 只有「当前背景确实与目标模式相反」才提示——深色模式配浅色背景图时不该劝用户切回去。
     */
    fun checkSwitchDayNight(lux: Float) {
        if (
            !readSettingsRepository.currentSettings.autoSuggestDayNight ||
            isDayNightSwitchCoolingDown()
        ) return
        val isNight = host.isNightTheme
        val styleConfig = host.uiState.styleConfig
        if (!isNight && lux <= DARK_LUX_THRESHOLD) {
            if (hasDismissedDarkReminder) return
            val bgType = styleConfig.bgType
            val isLightBg = if (bgType == 0) {
                val colorInt = runCatching { styleConfig.bgStr.toColorInt() }
                    .getOrDefault(0xFFEEEEEE.toInt())
                isReadBgLight(colorInt)
            } else {
                val meanColor = ReadSessionState.backgroundMeanColor
                if (meanColor != 0) isReadBgLight(meanColor) else true
            }
            if (isLightBg) {
                lastSwitchDayNightReminderTime = System.currentTimeMillis()
                showReminder(
                    ReminderUiState(
                        message = context.getString(R.string.switch_to_dark_mode_tip),
                        actionText = context.getString(R.string.switch_action),
                        actionIntent = ReadBookIntent.ToggleDayNight,
                        type = ReminderType.DayNightReminder(targetIsNight = true),
                    )
                )
            }
        } else if (isNight && lux >= BRIGHT_LUX_THRESHOLD) {
            if (hasDismissedLightReminder) return
            val bgTypeNight = styleConfig.bgTypeNight
            val isDarkBg = if (bgTypeNight == 0) {
                val colorInt = runCatching { styleConfig.bgStrNight.toColorInt() }
                    .getOrDefault(0xFF000000.toInt())
                !isReadBgLight(colorInt)
            } else {
                val meanColor = ReadSessionState.backgroundMeanColor
                if (meanColor != 0) !isReadBgLight(meanColor) else true
            }
            if (isDarkBg) {
                lastSwitchDayNightReminderTime = System.currentTimeMillis()
                showReminder(
                    ReminderUiState(
                        message = context.getString(R.string.switch_to_light_mode_tip),
                        actionText = context.getString(R.string.switch_action),
                        actionIntent = ReadBookIntent.ToggleDayNight,
                        type = ReminderType.DayNightReminder(targetIsNight = false),
                    )
                )
            }
        }
    }

    private fun isReadBgLight(colorInt: Int): Boolean {
        // io.legado.app.utils.ColorUtils.isColorLight 判断条件是 >= 0.5
        // 实际很多肉眼觉得亮的颜色会被判断为false，例如 0xFFC5B098
        return AndroidColorUtils.calculateLuminance(colorInt) >= LIGHT_LUMINANCE_THRESHOLD
    }

    private fun showReminder(reminder: ReminderUiState) {
        if (host.uiState.activeReminder == null && reminderQueue.isEmpty()) {
            host.updateState { it.copy(activeReminder = reminder) }
        } else {
            reminderQueue.addLast(reminder)
        }
    }

    fun dismissReminder() {
        val currentReminder = host.uiState.activeReminder
        if (currentReminder != null) {
            when (val type = currentReminder.type) {
                is ReminderType.DayNightReminder -> {
                    if (type.targetIsNight) {
                        hasDismissedDarkReminder = true
                    } else {
                        hasDismissedLightReminder = true
                    }
                }
                else -> {}
            }
        }
        host.updateState { it.copy(activeReminder = null) }
        if (reminderQueue.isNotEmpty()) {
            scope.launch {
                //延迟一下，让上一个提醒的动画结束
                delay(500.milliseconds)
                if (host.uiState.activeReminder == null && reminderQueue.isNotEmpty()) {
                    val next = reminderQueue.removeFirst()
                    host.updateState { it.copy(activeReminder = next) }
                }
            }
        }
    }

    // --- 护眼 ---

    fun collectEyeProtectionSettings() {
        scope.launch {
            themeSettingsGateway.settings.collect { settings ->
                host.updateState {
                    it.copy(
                        eyeProtection = EyeProtectionUiState(
                            enabled = settings.eyeProtectionEnabled,
                            intensity = settings.colorTemperature,
                            autoNight = settings.eyeProtectionAutoNight,
                            schedule = settings.eyeProtectionSchedule,
                            startTime = settings.eyeProtectionStartTime,
                            endTime = settings.eyeProtectionEndTime,
                        )
                    )
                }
            }
        }
    }

    fun toggleEyeProtection() = updateEyeProtection {
        if (it.isEyeProtectionConfigured) {
            it.copy(eyeProtectionEnabled = false, eyeProtectionAutoNight = false)
        } else {
            it.copy(eyeProtectionEnabled = true)
        }
    }

    fun updateEyeProtection(transform: (ThemeSettings) -> ThemeSettings) {
        scope.launch { themeSettingsGateway.update(transform) }
    }

    private fun emitConfigUpdate(vararg actions: ConfigUpdateAction) {
        host.emitEffect(ReadBookEffect.UpdateReaderConfig(actions.toSet()))
    }

    private companion object {
        const val DARK_LUX_THRESHOLD = 8f
        const val BRIGHT_LUX_THRESHOLD = 100f
        const val LIGHT_LUMINANCE_THRESHOLD = 0.35
        const val REMINDER_COOLDOWN_MS = 10 * 60 * 1000L
    }
}
