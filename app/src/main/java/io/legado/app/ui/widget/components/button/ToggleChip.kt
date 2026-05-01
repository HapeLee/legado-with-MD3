package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.LegadoTheme.composeEngine
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ToggleChip(
    label: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    checkedContentDescription: String = "已选择",
    uncheckedContentDescription: String = "未选择"
) {
    val isSelected = selected
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        MiuixIconButton(
            onClick = onToggle,
            modifier = modifier.semantics {
                role = Role.Checkbox
                toggleableState = if (isSelected) {
                    ToggleableState.On
                } else {
                    ToggleableState.Off
                }
                stateDescription = if (isSelected) {
                    checkedContentDescription
                } else {
                    uncheckedContentDescription
                }
            },
            backgroundColor = if (selected) {
                MiuixTheme.colorScheme.primaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                AnimatedVisibility(visible = selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MiuixIcon(
                            imageVector = Icons.Default.Check,
                            contentDescription = checkedContentDescription,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }

                MiuixText(
                    text = label,
                    style = LegadoTheme.typography.labelMediumEmphasized,
                    maxLines = 1,
                    softWrap = false,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface
                )
            }
        }
    } else {
        FilterChip(
            selected = selected,
            onClick = onToggle,
            modifier = modifier,
            label = { Text(label) },
            leadingIcon = if (selected) {
                {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = checkedContentDescription,
                        Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            } else null
        )
    }
}
