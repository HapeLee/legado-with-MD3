package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleanHands
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.MenuItemIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.reader.readerMenuLiquidGlassAvailable

@Composable
internal fun FloatingIconRow(
    state: ReadBookUiState,
    eyeProtectionActive: Boolean,
    colors: ReadMenuColors,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    onIntent: (ReadBookIntent) -> Unit,
    backdrop: Backdrop?,
) {
    val context = LocalContext.current
    val floatingIcons = remember(
        state.menuConfig.titleBarButtons,
        state.isLocalBook,
        state.isLocalTxt,
        state.bookSource,
        state.isReadAloudRunning,
        state.isAutoPage,
        state.translationMode,
        state.useReplaceRule,
        state.useMultiSpeaker,
        state.multiRoleCast,
        eyeProtectionActive,
    ) {
        loadFloatingIcons(
            context = context,
            state = state,
            eyeProtectionActive = eyeProtectionActive,
            onIntent = onIntent,
        )
    }

    if (floatingIcons.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(all = 16.dp),
        horizontalArrangement = when (alignment) {
            Alignment.Start -> Arrangement.Start
            Alignment.End -> Arrangement.End
            else -> Arrangement.Center
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        floatingIcons.forEach { iconDef ->
            // 按 id 记槽位：按钮列表随配置重排时，展开态不会被错配到别的按钮上
            key(iconDef.id) {
                val customPath = remember(state.menuConfig.titleBarCustomIcons, iconDef.id) {
                    state.menuConfig.titleBarCustomIcons[iconDef.id]
                }
                val isCustom = !customPath.isNullOrBlank()
                val glassEnabled = !isCustom && state.menuConfig.readMenuFloatingIconLiquidGlass &&
                        readerMenuLiquidGlassAvailable(backdrop)
                var expandedMenu by remember { mutableStateOf(false) }
                Box {
                    ReadMenuGlassButtonSurface(
                        onClick = iconDef.onClick,
                        colors = colors,
                        backdrop = backdrop,
                        menuConfig = state.menuConfig,
                        glassEnabled = glassEnabled,
                        iconStyle = 1,
                        selected = iconDef.isActive,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        onLongClick = when {
                            iconDef.longPressMenu != null -> ({ expandedMenu = true })
                            else -> iconDef.onLongClick
                        },
                        contentDescription = iconDef.label,
                    ) {
                        if (isCustom) {
                            AsyncImage(
                                model = customPath,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            AppIcon(
                                imageVector = iconDef.icon,
                                contentDescription = null,
                                tint = if (iconDef.isActive) {
                                    LegadoTheme.colorScheme.primary
                                } else {
                                    colors.content
                                },
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    ReadMenuLongPressDropdown(
                        menu = iconDef.longPressMenu,
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

@Composable
private fun OverflowDropdownMenu(
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val showIcon = state.menuConfig.showMenuIcon
    val menuIcon: (ImageVector) -> (@Composable () -> Unit)? = { imageVector ->
        if (showIcon) {
            { MenuItemIcon(imageVector = imageVector) }
        } else {
            null
        }
    }

    RoundDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) { dismiss ->
        var imageStyleExpanded by remember { mutableStateOf(false) }

        // Compact mode: moved from top bar buttons
        if (state.menuConfig.titleBarCompact) {
            if (!state.isLocalBook) {
                RoundDropdownMenuItem(
                    text = stringResource(R.string.change_origin),
                    leadingIcon = menuIcon(Icons.Default.SwapHoriz),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuChangeSource) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.read_aloud_audio_download_entry),
                    leadingIcon = menuIcon(Icons.Default.Download),
                    onClick = {
                        dismiss()
                        onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AudioDownload))
                    },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.menu_refresh_dur),
                    leadingIcon = menuIcon(Icons.Default.Refresh),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshDur) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.menu_refresh_after),
                    leadingIcon = menuIcon(Icons.Default.Refresh),
                    onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAfter) },
                )
                RoundDropdownMenuItem(
                    text = stringResource(R.string.offline_cache),
                    leadingIcon = menuIcon(Icons.Default.CloudDownload),
                    onClick = { dismiss(); onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Download)) },
                )
            } else {
                if (state.isLocalTxt) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.txt_toc_rule),
                        leadingIcon = menuIcon(Icons.AutoMirrored.Filled.Toc),
                        onClick = { dismiss(); onIntent(ReadBookIntent.MenuTocRegex) },
                    )
                }
                RoundDropdownMenuItem(
                    text = stringResource(R.string.set_charset),
                    leadingIcon = menuIcon(Icons.Default.Translate),
                    onClick = { dismiss(); onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.Charset)) },
                )
            }
            PillDivider()
        }

        // 内容处理
        RoundDropdownMenuItem(
            text = stringResource(R.string.edit_content),
            leadingIcon = menuIcon(Icons.Default.Edit),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.OpenContentEdit)
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.bookmark_add),
            leadingIcon = menuIcon(Icons.Default.Bookmark),
            onClick = { dismiss(); onIntent(ReadBookIntent.AddBookmark) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.highlight_rule_config),
            leadingIcon = menuIcon(Icons.Default.Tune),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.HighlightRuleConfig))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.text_processing),
            leadingIcon = menuIcon(Icons.Default.FindReplace),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.TextProcessing))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.reverse_content),
            leadingIcon = menuIcon(Icons.Default.SwapVert),
            onClick = { dismiss(); onIntent(ReadBookIntent.MenuReverseContent) },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.re_segment),
            leadingIcon = menuIcon(Icons.AutoMirrored.Filled.Toc),
            isSelected = state.reSegment,
            onClick = { onIntent(ReadBookIntent.MenuReSegment) },
        )
        if (state.isEpub) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_ruby_tag),
                leadingIcon = menuIcon(Icons.Default.CleanHands),
                isSelected = state.delRubyTag,
                onClick = { onIntent(ReadBookIntent.MenuDelRubyTag) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.del_h_tag),
                leadingIcon = menuIcon(Icons.Default.CleanHands),
                isSelected = state.delHTag,
                onClick = { onIntent(ReadBookIntent.MenuDelHTag) },
            )
        }

        PillDivider()

        // 阅读设置
        Box {
            RoundDropdownMenuItem(
                text = stringResource(R.string.image_style),
                leadingIcon = menuIcon(Icons.Default.Image),
                onClick = { imageStyleExpanded = true },
            )
            RoundDropdownMenu(
                expanded = imageStyleExpanded,
                onDismissRequest = { imageStyleExpanded = false },
            ) { subDismiss ->
                listOf(
                    R.string.btn_default_s to Book.imgStyleDefault,
                    R.string.image_style_full to Book.imgStyleFull,
                    R.string.image_style_text to Book.imgStyleText,
                    R.string.image_style_single to Book.imgStyleSingle,
                ).forEach { (label, style) ->
                    RoundDropdownMenuItem(
                        text = stringResource(label),
                        onClick = {
                            subDismiss()
                            onIntent(ReadBookIntent.MenuImageStyle(style))
                        },
                    )
                }
            }
        }
        RoundDropdownMenuItem(
            text = stringResource(R.string.book_page_anim),
            leadingIcon = menuIcon(Icons.Default.Animation),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.PageAnim))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.simulated_reading),
            leadingIcon = menuIcon(Icons.Default.AutoStories),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.SimulatedReading))
            },
        )

        PillDivider()

        // 书源
        if (!state.isLocalBook) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.menu_refresh_all),
                leadingIcon = menuIcon(Icons.Default.Replay),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuRefreshAll) },
            )
            PillDivider()
        }

        // 进度同步（与上游一致：在书架 + 已配云端才显示）
        if (state.inBookshelf && state.isReadingProgressSyncConfigured) {
            RoundDropdownMenuItem(
                text = stringResource(R.string.get_book_progress),
                leadingIcon = menuIcon(Icons.Default.Sync),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuGetProgress) },
            )
            RoundDropdownMenuItem(
                text = stringResource(R.string.cover_book_progress),
                leadingIcon = menuIcon(Icons.Default.Sync),
                onClick = { dismiss(); onIntent(ReadBookIntent.MenuCoverProgress) },
            )
            PillDivider()
        }

        // 其他
        RoundDropdownMenuItem(
            text = stringResource(R.string.config_btn),
            leadingIcon = menuIcon(Icons.Default.Extension),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.ToolButtonConfig))
            },
        )
        RoundDropdownMenuItem(
            text = stringResource(R.string.log),
            leadingIcon = menuIcon(Icons.Default.BugReport),
            onClick = {
                dismiss()
                onIntent(ReadBookIntent.ShowSheet(ReadBookSheet.AppLog))
            },
        )
    }
}

// ========== Title Bar Icons ==========

private data class FloatingIconDef(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    /** 与顶栏同一按钮保持一致的长按二级菜单。 */
    val longPressMenu: ReadMenuLongPressMenu? = null,
)

private fun loadFloatingIcons(
    context: Context,
    state: ReadBookUiState,
    eyeProtectionActive: Boolean,
    onIntent: (ReadBookIntent) -> Unit,
): List<FloatingIconDef> {
    val defs = buildAllReadMenuActionDefs(
        context = context,
        state = state,
        eyeProtectionActive = eyeProtectionActive,
        onIntent = onIntent,
    )
    return state.menuConfig.titleBarButtons
        .asSequence()
        .filter { it.enabled }
        .mapNotNull { defs[it.id] }
        .filter { it.applicable }
        .map { def ->
            FloatingIconDef(
                id = def.id,
                icon = def.icon,
                label = def.label,
                isActive = def.isActive,
                onClick = def.onClick,
                onLongClick = def.onLongClick,
                longPressMenu = def.longPressMenu,
            )
        }
        .toList()
}
