package io.legado.app.ui.config.coverConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun CoverAlbumSelectSheet(
    show: Boolean,
    state: CoverAlbumSelectionUiState,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val selectedId = state.selectedAlbumId
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.select_cover_album),
        endAction = {
            SmallPlainButton(
                onClick = onManage,
                icon = Icons.Default.Settings,
                contentDescription = stringResource(R.string.manage_cover_albums),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CoverAlbumSelectionItem(
                name = stringResource(R.string.cover_album_none),
                imagePath = null,
                lightImageCount = null,
                darkImageCount = null,
                selected = selectedId == null,
                onClick = {
                    onSelect(null)
                    onDismissRequest()
                },
            )
            state.albums.forEach { album ->
                CoverAlbumSelectionItem(
                    name = album.name,
                    imagePath = album.lightImages.firstOrNull()?.path
                        ?: album.darkImages.firstOrNull()?.path,
                    lightImageCount = album.lightImages.size,
                    darkImageCount = album.darkImages.size,
                    selected = selectedId == album.id,
                    onClick = {
                        onSelect(album.id)
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun CoverAlbumSelectionItem(
    name: String,
    imagePath: String?,
    lightImageCount: Int?,
    darkImageCount: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NormalCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        containerColor = if (selected) {
            LegadoTheme.colorScheme.secondaryContainer
        } else {
            LegadoTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (imagePath != null) {
                    AsyncImage(
                        model = imagePath,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    AppIcon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = null,
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = name,
                    style = LegadoTheme.typography.titleSmall,
                    maxLines = 1,
                )
                if (lightImageCount != null && darkImageCount != null) {
                    AppText(
                        text = stringResource(
                            R.string.cover_album_day_night_count,
                            lightImageCount,
                            darkImageCount,
                        ),
                        style = LegadoTheme.typography.bodySmall,
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (selected) {
                AppIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = LegadoTheme.colorScheme.primary,
                )
            }
        }
    }
}
