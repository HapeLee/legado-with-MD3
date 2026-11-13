package io.legado.app.ui.config.bubble

import io.legado.app.feature.settings.search.SearchableSetting
import io.legado.app.feature.settings.search.SearchableSettingsList
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import io.legado.app.ui.widget.components.coui.appVerticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextFieldSurface
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.dialog.ColorPickerSheet
import io.legado.app.ui.widget.components.filePicker.FilePickerSheet
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.importComponents.SourceInputDialog
import io.legado.app.ui.widget.components.menu.AppAction
import io.legado.app.ui.widget.components.menu.AppActionGroup
import io.legado.app.ui.widget.components.menu.AppActionMenu
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.settingItem.TinyColorSettingItem
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

private val BubbleThumbSize = 44.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubbleConfigScreen(
    state: BubbleConfigUiState,
    onIntent: (BubbleConfigIntent) -> Unit,
    onBackClick: () -> Unit,
    onImportZip: (Array<String>) -> Unit,
    onExportZip: (BubblePackageManager.Entry) -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val previewCache = remember { BubblePreviewCache() }
    var showImportSheet by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BubblePackageManager.Entry?>(null) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.bubble_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { showImportSheet = true },
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.bubble_add),
                    )
                },
            )
        }
    ) { paddingValues ->
        SearchableSettingsList(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "summary") {
                AppText(
                    text = stringResource(R.string.bubble_config_summary),
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                    style = LegadoTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }

            item(key = "forceSoftware") {
                SplicedColumnGroup {
                    SearchableSetting("bubble.bubble_force_software") {
                        SwitchSettingItem(
                            title = stringResource(R.string.bubble_force_software),
                            description = stringResource(R.string.bubble_force_software_desc),
                            checked = state.forceSoftwareParagraphBubble,
                            onCheckedChange = {
                                onIntent(BubbleConfigIntent.ForceSoftwareChanged(it))
                            },
                        )
                    }
                }
            }

            items(state.entries, key = { it.dirName }) { entry ->
                SplicedColumnGroup {
                    BubbleEntryRow(
                        entry = entry,
                        active = entry.dirName == state.activeDirName,
                        previewCache = previewCache,
                        onApply = { onIntent(BubbleConfigIntent.Apply(entry)) },
                        onEdit = { onIntent(BubbleConfigIntent.StartEdit(entry)) },
                        onExport = { onExportZip(entry) },
                        onDelete = { pendingDelete = entry },
                    )
                }
            }

            if (state.entries.isEmpty() && !state.loading) {
                item(key = "empty") {
                    AppText(
                        text = stringResource(R.string.bubble_empty),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
                    )
                }
            }

            item(key = "create") {
                SplicedColumnGroup {
                    SearchableSetting("bubble.bubble_add_manual") {
                        ClickableSettingItem(
                            title = stringResource(R.string.bubble_add_manual),
                            description = stringResource(R.string.bubble_add_manual_desc),
                            onClick = { onIntent(BubbleConfigIntent.StartCreate) },
                        )
                    }
                }
            }

            item(key = "help") {
                BubbleHelpGroup()
            }
        }
    }

    state.editor?.let { editor ->
        BubbleEditSheet(
            state = editor,
            previewCache = previewCache,
            onConfigChange = { onIntent(BubbleConfigIntent.UpdateDraft(it)) },
            onDismiss = { onIntent(BubbleConfigIntent.DismissEditor) },
            onConfirm = { onIntent(BubbleConfigIntent.SaveDraft) },
        )
    }

    pendingDelete?.let { entry ->
        AppAlertDialog(
            show = true,
            title = stringResource(R.string.bubble_delete),
            text = stringResource(R.string.bubble_delete_message, entry.config.name),
            onDismissRequest = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                onIntent(BubbleConfigIntent.Delete(entry))
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 与替换净化/目录规则一致的导入入口：系统文件 或 手填 URL
    FilePickerSheet(
        show = showImportSheet,
        onDismissRequest = { showImportSheet = false },
        title = stringResource(R.string.bubble_import),
        onSelectSysFile = { types ->
            showImportSheet = false
            onImportZip(types)
        },
        onManualInput = {
            showImportSheet = false
            showUrlDialog = true
        },
        allowExtensions = arrayOf("zip"),
    )
    SourceInputDialog(
        show = showUrlDialog,
        title = stringResource(R.string.bubble_import_url_title),
        hint = stringResource(R.string.bubble_import_url_hint),
        onDismissRequest = { showUrlDialog = false },
        onConfirm = { url ->
            showUrlDialog = false
            if (url.isNotBlank()) onIntent(BubbleConfigIntent.Import(url.trim()))
        },
    )
}

/** 气泡包菜单的稳定 ID：声明与分派共用，避免字面量漂移。 */
private enum class BubbleEntryMenuAction(val id: String, @StringRes val labelRes: Int) {
    Apply("bubble_apply", R.string.bubble_apply),
    Edit("bubble_edit", R.string.bubble_edit),
    ExportZip("bubble_export_zip", R.string.bubble_export_zip),
    Delete("bubble_delete", R.string.bubble_delete),
}

/** 内置包只能「应用」；删除项走 destructive，不再在调用点写死 error 色。 */
@Composable
private fun bubbleEntryMenuGroups(isBuiltin: Boolean): ImmutableList<AppActionGroup> {
    val actions = BubbleEntryMenuAction.entries.mapNotNull { action ->
        if (isBuiltin && action != BubbleEntryMenuAction.Apply) return@mapNotNull null
        AppAction(
            id = action.id,
            label = stringResource(action.labelRes),
            destructive = action == BubbleEntryMenuAction.Delete,
        )
    }
    return persistentListOf(
        AppActionGroup(id = "bubble_entry", actions = actions.toImmutableList()),
    )
}

@Composable
private fun BubbleEntryRow(
    entry: BubblePackageManager.Entry,
    active: Boolean,
    previewCache: BubblePreviewCache,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isBuiltin = entry.source == BubblePackageManager.Source.BUILTIN

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BubbleThumb(entry = entry, previewCache = previewCache)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            AppText(
                text = entry.config.name,
                color = if (active) {
                    LegadoTheme.colorScheme.primary
                } else {
                    LegadoTheme.colorScheme.onSurface
                },
                style = LegadoTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = when {
                    active -> stringResource(R.string.bubble_applied)
                    isBuiltin -> stringResource(R.string.bubble_source_builtin)
                    else -> stringResource(R.string.bubble_source_local)
                },
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                style = LegadoTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Box {
            SmallPlainButton(
                onClick = { menuExpanded = true },
                icon = AppIcons.MoreVert,
                contentDescription = stringResource(R.string.bubble_more),
            )
            // 开合与 ⋮ 的外观都留在页面，入口只渲染弹层；锚点取这层盒子（⋮ 所在位置）。
            AppActionMenu(
                groups = bubbleEntryMenuGroups(isBuiltin = isBuiltin),
                onAction = { id ->
                    when (id) {
                        BubbleEntryMenuAction.Apply.id -> onApply()
                        BubbleEntryMenuAction.Edit.id -> onEdit()
                        BubbleEntryMenuAction.ExportZip.id -> onExport()
                        BubbleEntryMenuAction.Delete.id -> onDelete()
                    }
                },
                modifier = Modifier.matchParentSize(),
                embeddedExpanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                trigger = {},
            )
        }
    }
}

@Composable
private fun BubbleThumb(
    entry: BubblePackageManager.Entry,
    previewCache: BubblePreviewCache,
) {
    val isNight = LegadoTheme.isDark
    val config = entry.config
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = entry.dirName,
        key2 = config.updatedAt,
        key3 = config.svgTemplate,
        key4 = isNight,
    ) {
        value = previewCache.load(entry = entry, sidePx = 132, isNightTheme = isNight)
    }
    Box(
        modifier = Modifier
            .size(BubbleThumbSize)
            .clip(RoundedCornerShape(10.dp))
            .background(LegadoTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AppText(
                text = stringResource(R.string.bubble_thumb_placeholder),
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                style = LegadoTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun BubbleHelpGroup() {
    var expanded by remember { mutableStateOf(false) }
    SplicedColumnGroup {
        ClickableSettingItem(
            title = stringResource(R.string.bubble_help_title),
            description = stringResource(R.string.bubble_help_summary),
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            AppText(
                text = stringResource(R.string.bubble_help_body),
                color = LegadoTheme.colorScheme.onSurfaceVariant,
                style = LegadoTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun BubbleEditSheet(
    state: BubbleEditorState,
    previewCache: BubblePreviewCache,
    onConfigChange: (BubblePackageManager.Config) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val config = state.config
    var pickerSlot by remember { mutableStateOf<BubbleColorSlot?>(null) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = if (state.original == null) {
            stringResource(R.string.bubble_add_manual)
        } else {
            stringResource(R.string.bubble_edit)
        },
        startAction = {
            MediumTonalButton(
                onClick = onDismiss,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        },
        endAction = {
            MediumTonalButton(
                onClick = onConfirm,
                enabled = !state.saving && config.name.isNotBlank(),
                icon = Icons.Default.Check,
                contentDescription = stringResource(R.string.action_save),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .appVerticalScroll(rememberScrollState()),
        ) {
            AppTextFieldSurface(
                value = config.name,
                onValueChange = { onConfigChange(config.copy(name = it)) },
                label = stringResource(R.string.bubble_name),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            TinySliderSettingItem(
                title = stringResource(R.string.bubble_size_scale),
                value = config.sizeScale.coerceIn(
                    BubblePackageManager.MIN_SIZE_SCALE,
                    BubblePackageManager.MAX_SIZE_SCALE,
                ),
                valueRange =
                    BubblePackageManager.MIN_SIZE_SCALE..BubblePackageManager.MAX_SIZE_SCALE,
                stepSize = 0.1f,
                showDecimal = true,
                valueFormat = { "%.1f×".format(it) },
                onValueChange = { onConfigChange(config.copy(sizeScale = it)) },
            )

            TinyColorSettingItem(
                title = stringResource(R.string.bubble_color_day_normal),
                colorValue = config.dayNormalColor.toColorIntOrZero(),
                description = config.dayNormalColor
                    ?: stringResource(R.string.bubble_color_follow_theme),
                onClick = { pickerSlot = BubbleColorSlot.DayNormal },
            )
            TinyColorSettingItem(
                title = stringResource(R.string.bubble_color_day_emphasis),
                colorValue = config.dayEmphasisColor.toColorIntOrZero(),
                description = config.dayEmphasisColor
                    ?: stringResource(R.string.bubble_color_follow_theme),
                onClick = { pickerSlot = BubbleColorSlot.DayEmphasis },
            )
            TinyColorSettingItem(
                title = stringResource(R.string.bubble_color_night_normal),
                colorValue = config.nightNormalColor.toColorIntOrZero(),
                description = config.nightNormalColor
                    ?: stringResource(R.string.bubble_color_follow_theme),
                onClick = { pickerSlot = BubbleColorSlot.NightNormal },
            )
            TinyColorSettingItem(
                title = stringResource(R.string.bubble_color_night_emphasis),
                colorValue = config.nightEmphasisColor.toColorIntOrZero(),
                description = config.nightEmphasisColor
                    ?: stringResource(R.string.bubble_color_follow_theme),
                onClick = { pickerSlot = BubbleColorSlot.NightEmphasis },
            )

            Spacer(modifier = Modifier.height(8.dp))
            AppTextFieldSurface(
                value = config.svgTemplate,
                onValueChange = { onConfigChange(config.copy(svgTemplate = it)) },
                label = stringResource(R.string.bubble_svg_template),
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BubbleEditPreview(state = state, previewCache = previewCache)
            }
        }
    }

    pickerSlot?.let { slot ->
        ColorPickerSheet(
            show = true,
            initialColor = slot.colorOf(config) ?: 0,
            onDismissRequest = { pickerSlot = null },
            onColorSelected = { color ->
                pickerSlot = null
                onConfigChange(slot.applyTo(config, String.format("#%06X", color and 0x00FFFFFF)))
            },
        )
    }
}

@Composable
private fun BubbleEditPreview(
    state: BubbleEditorState,
    previewCache: BubblePreviewCache,
) {
    val isNight = LegadoTheme.isDark
    val config = state.config
    val color = (if (isNight) config.nightNormalColor else config.dayNormalColor)
        ?.takeIf { it.isNotBlank() }
        ?: BubblePackageManager.DEFAULT_NORMAL_COLOR
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = config.svgTemplate,
        key2 = config.sizeScale,
        key3 = color,
        key4 = state.original?.dirName,
    ) {
        value = previewCache.load(
            dirName = state.original?.dirName.orEmpty(),
            config = config,
            root = state.original?.localDir,
            sidePx = (64 * 1.6f * config.sizeScale).toInt().coerceAtLeast(32),
            color = color,
        )
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(84.dp),
        )
    }
}

/** 配色未设置（null）时给色块按钮一个透明值，由 `AccentColorButton` 画成空圈。 */
private fun String?.toColorIntOrZero(): Int = this?.takeIf { it.isNotBlank() }
    ?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
    ?: 0

private enum class BubbleColorSlot {
    DayNormal,
    DayEmphasis,
    NightNormal,
    NightEmphasis;

    fun colorOf(config: BubblePackageManager.Config): Int? {
        val value = when (this) {
            DayNormal -> config.dayNormalColor
            DayEmphasis -> config.dayEmphasisColor
            NightNormal -> config.nightNormalColor
            NightEmphasis -> config.nightEmphasisColor
        } ?: return null
        return runCatching { android.graphics.Color.parseColor(value) }.getOrNull()
    }

    fun applyTo(config: BubblePackageManager.Config, hex: String): BubblePackageManager.Config =
        when (this) {
            DayNormal -> config.copy(dayNormalColor = hex)
            DayEmphasis -> config.copy(dayEmphasisColor = hex)
            NightNormal -> config.copy(nightNormalColor = hex)
            NightEmphasis -> config.copy(nightEmphasisColor = hex)
        }
}
