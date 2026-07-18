package io.legado.app.ui.config.themeManage

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.SavedTheme
import io.legado.app.help.config.ThemePackageManager
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeManageScreen(
    state: ThemeManageUiState,
    onIntent: (ThemeManageIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val savedThemes = state.savedThemes

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
                        onClick = { onIntent(ThemeManageIntent.OpenSaveDialog) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_export_current),
                        description = stringResource(R.string.theme_manage_export_current_summary),
                        onClick = { onIntent(ThemeManageIntent.RequestExport()) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_import_package),
                        description = stringResource(R.string.theme_manage_import_package_summary),
                        onClick = { onIntent(ThemeManageIntent.RequestImportPackage) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.theme_manage_import_config),
                        description = stringResource(R.string.theme_manage_import_config_summary),
                        onClick = { onIntent(ThemeManageIntent.RequestImportLegacyJson) }
                    )
                    if (state.hasLegacyThemes) {
                        ClickableSettingItem(
                            title = stringResource(R.string.theme_manage_migrate_legacy),
                            description = stringResource(
                                R.string.theme_manage_migrate_legacy_summary
                            ),
                            onClick = {
                                onIntent(ThemeManageIntent.MigrateLegacyThemes)
                            }
                        )
                    }
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
                        onApply = { onIntent(ThemeManageIntent.OpenApplyDialog(theme)) },
                        onEdit = { onIntent(ThemeManageIntent.OpenEditSheet(theme)) },
                        onExport = { onIntent(ThemeManageIntent.RequestExport(theme)) },
                        onDelete = { onIntent(ThemeManageIntent.OpenDeleteDialog(theme)) }
                    )
                }
            }
        }
    }

    // Save theme dialog
    AppAlertDialog(
        show = state.dialog is ThemeManageDialog.Save,
        onDismissRequest = { onIntent(ThemeManageIntent.DismissDialog) },
        title = stringResource(R.string.theme_manage_save_theme),
        confirmText = stringResource(R.string.theme_manage_save),
        onConfirm = {
            val name = (state.dialog as? ThemeManageDialog.Save)?.name.orEmpty()
            if (name.isNotBlank()) {
                onIntent(ThemeManageIntent.SaveTheme(name))
                onIntent(ThemeManageIntent.DismissDialog)
            }
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ThemeManageIntent.DismissDialog) },
        content = {
            AppTextField(
                value = (state.dialog as? ThemeManageDialog.Save)?.name.orEmpty(),
                onValueChange = { onIntent(ThemeManageIntent.UpdateSaveName(it)) },
                placeholder = { AppText(text = stringResource(R.string.theme_manage_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    )

    // Apply theme dialog
    AppAlertDialog(
        show = state.dialog is ThemeManageDialog.Apply,
        onDismissRequest = { onIntent(ThemeManageIntent.DismissDialog) },
        title = stringResource(R.string.theme_manage_apply_theme),
        confirmText = stringResource(R.string.theme_manage_apply),
        onConfirm = {
            (state.dialog as? ThemeManageDialog.Apply)?.theme?.let { theme ->
                onIntent(ThemeManageIntent.ApplySavedTheme(theme))
            }
            onIntent(ThemeManageIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ThemeManageIntent.DismissDialog) },
        text = stringResource(
            R.string.theme_manage_apply_message,
            (state.dialog as? ThemeManageDialog.Apply)?.theme?.name.orEmpty(),
        )
    )

    // Delete theme dialog
    AppAlertDialog(
        show = state.dialog is ThemeManageDialog.Delete,
        onDismissRequest = { onIntent(ThemeManageIntent.DismissDialog) },
        title = stringResource(R.string.theme_manage_delete_theme),
        confirmText = stringResource(R.string.delete),
        onConfirm = {
            (state.dialog as? ThemeManageDialog.Delete)?.theme?.let { theme ->
                onIntent(ThemeManageIntent.DeleteSavedTheme(theme))
            }
            onIntent(ThemeManageIntent.DismissDialog)
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { onIntent(ThemeManageIntent.DismissDialog) },
        text = stringResource(
            R.string.theme_manage_delete_message,
            (state.dialog as? ThemeManageDialog.Delete)?.theme?.name.orEmpty(),
        )
    )

    // Edit theme sheet
    EditThemeSheet(
        show = state.dialog is ThemeManageDialog.Edit,
        themeData = (state.dialog as? ThemeManageDialog.Edit)?.theme?.data,
        themeName = (state.dialog as? ThemeManageDialog.Edit)?.theme?.name.orEmpty(),
        onDismissRequest = { onIntent(ThemeManageIntent.DismissDialog) },
        onSave = { newName, newData ->
            onIntent(
                ThemeManageIntent.SaveTheme(
                    name = newName,
                    data = newData,
                    replacedTheme = (state.dialog as? ThemeManageDialog.Edit)?.theme,
                )
            )
            onIntent(ThemeManageIntent.DismissDialog)
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

            val darkPrimary = if (theme.data.themeColorNight != 0) {
                Color(theme.data.themeColorNight)
            } else if (theme.data.cNPrimary != 0) {
                Color(theme.data.cNPrimary)
            } else {
                lightPrimary
            }

            val lightBg = if (theme.data.themeBackgroundColor != 0) Color(theme.data.themeBackgroundColor)
            else Color(0xFFF7F2FA)

            val darkBg = if (theme.data.themeBackgroundColorNight != 0) {
                Color(theme.data.themeBackgroundColorNight)
            } else if (theme.data.enableDeepPersonalization &&
                theme.data.themeBackgroundColor != 0
            ) {
                Color(theme.data.themeBackgroundColor)
            } else if (theme.data.isPureBlack) {
                Color.Black
            } else {
                Color(0xFF1C1B1F)
            }

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
                        color = if (theme.data.primaryTextColorNight != 0) {
                            Color(theme.data.primaryTextColorNight).copy(alpha = 0.6f)
                        } else if (theme.data.enableDeepPersonalization &&
                            theme.data.primaryTextColor != 0
                        ) {
                            Color(theme.data.primaryTextColor).copy(alpha = 0.6f)
                        } else {
                            Color.White.copy(alpha = 0.5f)
                        },
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
                    SmallPlainButton(
                        onClick = onEdit,
            icon = Icons.Default.Edit,
            contentDescription = stringResource(R.string.edit)
                    )
                    SmallPlainButton(
                        onClick = onExport,
            icon = Icons.Default.Share,
            contentDescription = stringResource(R.string.share)
                    )
                    SmallPlainButton(
                        onClick = onDelete,
            icon = Icons.Default.Delete,
            contentDescription = stringResource(R.string.delete)
                    )
                }
            }
        }
    }
}
