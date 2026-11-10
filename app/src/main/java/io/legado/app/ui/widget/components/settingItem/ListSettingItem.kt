package io.legado.app.ui.widget.components.settingItem

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.ui.theme.LocalHasMiuixPopupHost
import io.legado.app.ui.theme.LocalMiuixBarBackdrop
import io.legado.app.ui.widget.components.SplicedColumnDivider
import io.legado.app.ui.widget.components.coui.CouiSettingMenu
import io.legado.app.ui.widget.components.coui.canUseCouiSettingMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

@Composable
fun DropdownListSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    enabled: Boolean = true,
    /**
     * 当前设备或产物不提供的档位：仍然列出，但不可选中。
     * 调用方负责在显示名里标出来源（例如"（漫画增强版）"）。
     */
    unavailableValues: Set<String> = emptySet(),
    /** 点到本产物不提供的档位时的反馈；不提供则静默忽略。 */
    onUnavailableSelected: ((String) -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine

    // window 级 Miuix 弹层没有 popup host，host-based preference 弹不出；tiny 系列自带
    // Miuix 的 WindowListPopup，与宿主弹层同属 window 级，不退回 M3。
    if (composeEngine == ComposeEngine.Miuix && !LocalHasMiuixPopupHost.current) {
        TinyDropdownSettingItem(
            title = title,
            selectedValue = selectedValue,
            displayEntries = displayEntries,
            entryValues = entryValues,
            description = description,
            imageVector = imageVector,
            enabled = enabled,
            unavailableValues = unavailableValues,
            onUnavailableSelected = onUnavailableSelected,
            onValueChange = onValueChange,
        )
        return
    }
    SplicedColumnDivider()

    if (canUseCouiSettingMenu()) {
        CouiSettingMenu(
            selectedValue = selectedValue,
            displayEntries = displayEntries,
            entryValues = entryValues,
            enabled = enabled,
            unavailableValues = unavailableValues,
            onUnavailableSelected = onUnavailableSelected,
            onValueChange = onValueChange,
        ) { openMenu ->
            SettingItem(
                title = title,
                description = description,
                option = displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue,
                imageVector = imageVector,
                enabled = enabled,
                onClick = openMenu,
                onLongClick = openMenu,
            )
        }
        return
    }

    when (composeEngine) {
        ComposeEngine.Miuix -> {
            val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
            val spinnerItems = displayEntries.toList()
            val hasDisabledOptions = onUnavailableSelected == null && unavailableValues.isNotEmpty()
            val dropdownItems = displayEntries.mapIndexed { index, display ->
                DropdownItem(
                    text = display,
                    enabled = !hasDisabledOptions || entryValues[index] !in unavailableValues,
                )
            }
            val startAction: (@Composable () -> Unit)? = if (imageVector != null) {
                { Icon(imageVector = imageVector, contentDescription = null) }
            } else null
            val onSelectedIndexChange: (Int) -> Unit = { index ->
                // miuix 的 spinner 没有单项禁用态：靠提示说明这一档本产物跑不了。
                val value = entryValues[index]
                when {
                    !enabled -> Unit
                    value in unavailableValues -> onUnavailableSelected?.invoke(value)
                    else -> onValueChange(value)
                }
            }
            // 能走到这里说明已在 Scaffold 内，具备 MiuixPopupHost。
            val useGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                LocalAppUiConfiguration.current.theme.useHyperOs4Components

            // miuix 的 spinner 没有独立的禁用态：置灰并吞掉选择，不再触发设置变更。
            Box(
                Modifier
                    .alpha(if (enabled) 1f else 0.45f)
                    .semantics { if (!enabled) disabled() }
            ) {
                if (useGlass && !hasDisabledOptions) {
                    GlassDropdownPreference(
                        title = title,
                        summary = description,
                        items = spinnerItems,
                        selectedIndex = selectedIndex,
                        backdrop = LocalMiuixBarBackdrop.current,
                        enabled = enabled,
                        startAction = startAction,
                        onSelectedIndexChange = onSelectedIndexChange,
                    )
                } else {
                    OverlaySpinnerPreference(
                        title = title,
                        summary = description,
                        items = dropdownItems,
                        selectedIndex = selectedIndex,
                        startAction = startAction,
                        onSelectedIndexChange = onSelectedIndexChange,
                    )
                }
            }
        }
        ComposeEngine.Material, ComposeEngine.Mochi, ComposeEngine.Coui -> {

            val currentEntry =
                displayEntries.getOrNull(entryValues.indexOf(selectedValue)) ?: selectedValue

            SettingItem(
                title = title,
                description = description,
                option = currentEntry,
                imageVector = imageVector,
                enabled = enabled,
                onClick = { },
                dropdownMenu = if (!enabled) null else { onDismiss ->
                    displayEntries.forEachIndexed { index, display ->
                        val value = entryValues[index]
                        val unavailable = value in unavailableValues
                        RoundDropdownMenuItem(
                            text = display,
                            enabled = !unavailable || onUnavailableSelected != null,
                            // 视觉上灰掉，但仍可点击：点了给出"为什么用不了"，比无声无息更好。
                            color = if (unavailable) {
                                LegadoTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            } else Color.Unspecified,
                            onClick = {
                                onDismiss()
                                if (unavailable) onUnavailableSelected?.invoke(value) else onValueChange(value)
                            },
                            trailingIcon = if (selectedValue == value) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            )
        }
    }
}
