package io.legado.app.ui.widget.components.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.legado.app.ui.widget.components.card.NormalCard
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
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        NormalCard (
            modifier = modifier
                .padding(vertical = 2.dp)
                .semantics {
                    toggleableState = if (selected) {
                        ToggleableState.On
                    } else {
                        ToggleableState.Off
                    }
                    stateDescription = if (selected) {
                        checkedContentDescription
                    } else {
                        uncheckedContentDescription
                    }
                },
            cornerRadius = 12.dp,
            onClick = onToggle,
            containerColor = if (selected) {
                MiuixTheme.colorScheme.secondaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AnimatedVisibility(
                    visible = selected
                ) {
                    MiuixIcon(
                        imageVector = Icons.Default.Check,
                        contentDescription = checkedContentDescription,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(16.dp),
                        tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                    )
                }

                MiuixText(
                    modifier = Modifier
                        .padding(vertical = 8.dp),
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
