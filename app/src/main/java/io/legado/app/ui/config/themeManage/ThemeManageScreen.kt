package io.legado.app.ui.config.themeManage

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemeImportExport
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManageScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }
    var newThemeName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var applyTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var exportTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var editTarget by remember { mutableStateOf<SavedTheme?>(null) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var savedThemesVersion by remember { mutableIntStateOf(0) }
    val savedThemes = remember(savedThemesVersion) { ThemeImportExport.savedThemes.toList() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            val target = exportTarget
            if (target != null) {
                exportTarget = null
                if (ThemeImportExport.exportSavedThemeToFile(context, target, it)) {
                    context.toastOnUi(R.string.theme_manage_export_success)
                } else {
                    context.toastOnUi(R.string.theme_manage_export_failed)
                }
            } else if (ThemeImportExport.exportToFile(context, it)) {
                context.toastOnUi(R.string.theme_manage_export_success)
            } else {
                context.toastOnUi(R.string.theme_manage_export_failed)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            if (ThemeImportExport.importFromUri(context, it)) {
                context.toastOnUi(R.string.theme_manage_import_success)
                showRestartDialog = true
            } else {
                context.toastOnUi(R.string.theme_manage_import_failed)
            }
        }
    }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.theme_pack),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_save_current),
                        description = stringResource(R.string.theme_manage_save_current_summary),
                        onClick = {
                            newThemeName = ""
                            showSaveDialog = true
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_export_current),
                        description = stringResource(R.string.theme_manage_export_current_summary),
                        onClick = {
                            exportTarget = null
                            exportLauncher.launch("legado_theme_${System.currentTimeMillis()}.json")
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_import_config),
                        description = stringResource(R.string.theme_manage_import_config_summary),
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    )
                }
            }

            if (savedThemes.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AppText(
                        text = stringResource(R.string.theme_manage_saved_themes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                items(savedThemes, key = { it.name }) { theme ->
                    SavedThemeItem(
                        theme = theme,
                        onApply = { applyTarget = theme },
                        onEdit = { editTarget = theme },
                        onExport = {
                            exportTarget = theme
                            exportLauncher.launch("${theme.name}.json")
                        },
                        onDelete = { deleteTarget = theme }
                    )
                }
            }
        }
    }

    // Restart dialog
    AppAlertDialog(
        show = showRestartDialog,
        onDismissRequest = { showRestartDialog = false },
        title = stringResource(R.string.restart_required_message),
        onConfirm = {
            showRestartDialog = false
            Handler(Looper.getMainLooper()).postDelayed({
                context.restart()
            }, 100)
        },
        confirmText = stringResource(R.string.ok),
        onDismiss = {
            showRestartDialog = false
            context.toastOnUi(R.string.restart_later_message)
        },
        dismissText = stringResource(R.string.cancel)
    )

    // Save theme dialog
    AppAlertDialog(
        show = showSaveDialog,
        onDismissRequest = { showSaveDialog = false },
        title = stringResource(R.string.theme_manage_save_theme),
        confirmText = stringResource(R.string.theme_manage_save),
        onConfirm = {
            if (newThemeName.isNotBlank()) {
                ThemeImportExport.saveCurrentAsTheme(newThemeName)
                savedThemesVersion++
                showSaveDialog = false
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showSaveDialog = false },
        content = {
            AppTextField(
                value = newThemeName,
                onValueChange = { newThemeName = it },
                placeholder = { AppText(text = stringResource(R.string.theme_manage_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )

    // Apply theme dialog
    AppAlertDialog(
        show = applyTarget != null,
        onDismissRequest = { applyTarget = null },
        title = stringResource(R.string.theme_manage_apply_theme),
        confirmText = stringResource(R.string.theme_manage_apply),
        onConfirm = {
            applyTarget?.let { theme ->
                ThemeImportExport.applySavedTheme(theme)
                showRestartDialog = true
            }
            applyTarget = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { applyTarget = null },
        text = stringResource(R.string.theme_manage_apply_message, applyTarget?.name.orEmpty())
    )

    // Delete theme dialog
    AppAlertDialog(
        show = deleteTarget != null,
        onDismissRequest = { deleteTarget = null },
        title = stringResource(R.string.theme_manage_delete_theme),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            deleteTarget?.let { theme ->
                ThemeImportExport.deleteSavedTheme(theme)
                savedThemesVersion++
            }
            deleteTarget = null
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { deleteTarget = null },
        text = stringResource(R.string.theme_manage_delete_message, deleteTarget?.name.orEmpty())
    )

    // Edit theme sheet
    EditThemeSheet(
        show = editTarget != null,
        themeData = editTarget?.data,
        themeName = editTarget?.name ?: "",
        onDismissRequest = { editTarget = null },
        onSave = { newName, newData ->
            editTarget?.let { old ->
                ThemeImportExport.deleteSavedTheme(old)
            }
            ThemeImportExport.saveCurrentAsTheme(newName, newData)
            savedThemesVersion++
            editTarget = null
        }
    )
}

@Composable
private fun SavedThemeItem(
    theme: SavedTheme,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        onClick = onApply,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            val lightPrimary = if (theme.data.themeColor != 0) Color(theme.data.themeColor)
            else if (theme.data.cPrimary != 0) Color(theme.data.cPrimary)
            else MaterialTheme.colorScheme.primary

            val darkPrimary = if (theme.data.cNPrimary != 0) Color(theme.data.cNPrimary)
            else lightPrimary

            val lightBg = if (theme.data.themeBackgroundColor != 0) Color(theme.data.themeBackgroundColor)
            else Color(0xFFF7F2FA)

            val darkBg = if (theme.data.isPureBlack) Color.Black else Color(0xFF1C1B1F)

            // 预览区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
            ) {
                // 日间行
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(lightBg)
                ) {
                    AppText(
                        text = stringResource(R.string.theme_manage_preview_day),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = if (theme.data.primaryTextColor != 0) Color(theme.data.primaryTextColor).copy(alpha = 0.6f)
                        else Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .size(28.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(6.dp),
                                ambientColor = lightPrimary,
                                spotColor = lightPrimary
                            )
                            .background(lightPrimary, RoundedCornerShape(6.dp))
                    )
                }

                // 夜间行
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(darkBg)
                ) {
                    AppText(
                        text = stringResource(R.string.theme_manage_preview_night),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .size(28.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = RoundedCornerShape(6.dp),
                                ambientColor = darkPrimary,
                                spotColor = darkPrimary
                            )
                            .background(darkPrimary, RoundedCornerShape(6.dp))
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                AppText(
                    text = theme.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallIconButton(
                        onClick = onEdit,
                        imageVector = Icons.Default.Edit
                    )
                    SmallIconButton(
                        onClick = onExport,
                        imageVector = Icons.Default.Share
                    )
                    SmallIconButton(
                        onClick = onDelete,
                        imageVector = Icons.Default.Delete
                    )
                }
            }
        }
    }
}
