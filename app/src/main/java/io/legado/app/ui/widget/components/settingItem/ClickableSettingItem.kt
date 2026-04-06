package io.legado.app.ui.widget.components.settingItem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
fun ClickableSettingItem(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    option: String? = null,
    imageVector: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val composeEngine = LegadoTheme.composeEngine
    if (ThemeResolver.isMiuixEngine(composeEngine)) {
        ArrowPreference(
            title = title,
            summary = description,
            insideMargin = BasicComponentDefaults.InsideMargin,
            onClick = onClick
        )
    } else {
        SettingItem(
            modifier = modifier,
            title = title,
            description = description,
            option = option,
            imageVector = imageVector,
            trailingContent = trailingContent ?: {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}
