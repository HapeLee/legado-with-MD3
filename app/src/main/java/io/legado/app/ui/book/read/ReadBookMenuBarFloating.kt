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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.backdrop.Backdrop
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
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
                        modifier = Modifier.matchParentSize(),
                        onDismissRequest = { expandedMenu = false },
                        onIntent = onIntent,
                    )
                }
            }
        }
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
