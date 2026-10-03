package io.legado.app.ui.book.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextFieldSurface
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

/**
 * 人物头像的来源：本地导入要过一遍裁剪，链接那一份直接存下来用。
 *
 * 链接入口与书籍详情页封面的填链接方式是同一套口径。
 *
 * 「编辑头像」用的是已经存下的那一份：只有走选图器才会重新弹裁剪，换过一次头像后
 * 想挪一下取景框就只能重新导入原图，这里把它做成同一份菜单里的一项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterAvatarSourceSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onPickLocal: () -> Unit,
    onUrl: (String) -> Unit,
    hasAvatar: Boolean = false,
    onEditAvatar: () -> Unit = {},
) {
    var editUrl by remember(show) { mutableStateOf(false) }
    var url by remember(show) { mutableStateOf("") }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(
            if (editUrl) R.string.character_avatar_url else R.string.character_avatar_source,
        ),
        endAction = {
            if (editUrl) {
                MediumTonalButton(
                    onClick = { if (url.isNotBlank()) onUrl(url.trim()) },
                    icon = AppIcons.Check,
                    contentDescription = stringResource(R.string.ok),
                )
            }
        },
    ) {
        if (editUrl) {
            AppTextFieldSurface(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.character_avatar_url),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AvatarSourceRow(
                    icon = Icons.Default.Image,
                    text = stringResource(R.string.select_image),
                    onClick = onPickLocal,
                )
                AvatarSourceRow(
                    icon = Icons.Default.Link,
                    text = stringResource(R.string.character_avatar_url),
                    onClick = { editUrl = true },
                )
                if (hasAvatar) {
                    AvatarSourceRow(
                        icon = AppIcons.Edit,
                        text = stringResource(R.string.character_avatar_edit),
                        onClick = onEditAvatar,
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarSourceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(24.dp),
            tint = LegadoTheme.colorScheme.onSurfaceVariant,
        )
        AppText(
            text = text,
            style = LegadoTheme.typography.bodyLarge,
            color = LegadoTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
