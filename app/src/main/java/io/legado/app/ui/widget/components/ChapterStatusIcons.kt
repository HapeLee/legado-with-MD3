package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.legado.app.R
import io.legado.app.ui.book.toc.TocItemUi
import io.legado.app.ui.theme.LegadoTheme

/**
 * 目录行右侧的状态图标：本章分配过角色、本章下载过听书音频。
 *
 * 放在字数徽标的左边，自己只占内容宽度，字数徽标那侧是包裹内容的，所以「12131字」变宽也不会挤。
 */
@Composable
fun ChapterStatusIcons(
    item: TocItemUi,
    iconSize: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
) {
    if (!item.hasCastAssignment && !item.hasAudioDownload) return
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (item.hasCastAssignment) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = stringResource(R.string.cast_assignment_state),
                modifier = Modifier.size(iconSize),
                tint = LegadoTheme.colorScheme.primary,
            )
        }
        if (item.hasAudioDownload) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.read_aloud_audio_download_state),
                modifier = Modifier
                    .padding(start = spacing)
                    .size(iconSize),
                tint = LegadoTheme.colorScheme.secondary,
            )
        }
    }
}
