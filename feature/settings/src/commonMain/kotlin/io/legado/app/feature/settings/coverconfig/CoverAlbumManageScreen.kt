package io.legado.app.feature.settings.coverconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.cancel
import io.legado.app.feature.settings.res.cover_album_add_images
import io.legado.app.feature.settings.res.cover_album_create
import io.legado.app.feature.settings.res.cover_album_day_night_count
import io.legado.app.feature.settings.res.cover_album_delete
import io.legado.app.feature.settings.res.cover_album_delete_confirmation
import io.legado.app.feature.settings.res.cover_album_empty
import io.legado.app.feature.settings.res.cover_album_name
import io.legado.app.feature.settings.res.cover_album_rename
import io.legado.app.feature.settings.res.cover_album_selected
import io.legado.app.feature.settings.res.cover_albums
import io.legado.app.feature.settings.res.day
import io.legado.app.feature.settings.res.delete
import io.legado.app.feature.settings.res.night
import io.legado.app.feature.settings.res.ok
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.AppIconButton
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.button.series.SmallPlainButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.card.NormalCard
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarActionButton
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

/**
 * M5-14b：从 `:app` 的 `ui/config/coverConfig/CoverAlbumManageScreen.kt` 迁来
 * **只有页面本体这一半**（含 4 个 `private` 子组件）。那个文件里同时放着
 * `CoverAlbumManageRouteScreen`（宿主壳：`GetMultipleContents` 选择器 + `toastOnUi`）——
 * 本片按职责拆开，壳留在 `:app`（现住 `CoverAlbumManageRouteScreen.kt`）。
 *
 * **唯一改动**是资源访问（CMP 的 `stringResource`、`R.string.*` → `Res.string.*`，15 条）。
 * 无数组，也没有 JVM 专用调用 —— 是本批**最干净的搬运**之一（对照 M5-13c 里藏着的
 * `Integer.toHexString`）。
 *
 * ⚠️ 这里用到 `coil3.compose.AsyncImage` 渲图库缩略图（两处），依赖由 M5-13b 加进
 * `build.gradle.kts`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverAlbumManageScreen(
    state: CoverAlbumManageUiState,
    onIntent: (CoverAlbumIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.cover_albums),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                },
                actions = {
                    TopBarActionButton(
                        onClick = { onIntent(CoverAlbumIntent.CreateClick) },
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(Res.string.cover_album_create),
                    )
                },
            )
        },
    ) { paddingValues ->
        if (state.albums.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Collections,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                    AppText(
                        text = stringResource(Res.string.cover_album_empty),
                        color = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = adaptiveContentPadding(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = state.albums,
                    key = { it.id },
                    contentType = { "coverAlbum" },
                ) { album ->
                    CoverAlbumCard(
                        album = album,
                        selected = state.selectedAlbumId == album.id,
                        onClick = { onIntent(CoverAlbumIntent.EditClick(album.id)) },
                        onRename = { onIntent(CoverAlbumIntent.RenameClick(album.id)) },
                        onDelete = { onIntent(CoverAlbumIntent.DeleteClick(album.id)) },
                    )
                }
            }
        }
    }

    val editingAlbum = state.albums.firstOrNull { it.id == state.editingAlbumId }
    CoverAlbumEditorSheet(
        album = editingAlbum,
        onAddImages = { isDark ->
            editingAlbum?.let {
                onIntent(CoverAlbumIntent.AddImagesClick(it.id, isDark))
            }
        },
        onRemoveImage = { isDark, imageId ->
            editingAlbum?.let {
                onIntent(CoverAlbumIntent.RemoveImage(it.id, isDark, imageId))
            }
        },
        onDismissRequest = { onIntent(CoverAlbumIntent.DismissEditor) },
    )

    CoverAlbumDialogs(
        dialog = state.dialog,
        onIntent = onIntent,
    )
}

@Composable
private fun CoverAlbumCard(
    album: CoverAlbumItemUi,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val preview = album.lightImages.firstOrNull()?.path
                    ?: album.darkImages.firstOrNull()?.path
                if (preview != null) {
                    AsyncImage(
                        model = preview,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AppText(
                    text = album.name,
                    style = LegadoTheme.typography.titleSmall,
                    maxLines = 1,
                )
                AppText(
                    text = stringResource(
                        Res.string.cover_album_day_night_count,
                        album.lightImages.size,
                        album.darkImages.size,
                    ),
                    style = LegadoTheme.typography.bodySmall,
                    color = LegadoTheme.colorScheme.onSurfaceVariant,
                )
                if (selected) {
                    AppText(
                        text = stringResource(Res.string.cover_album_selected),
                        style = LegadoTheme.typography.labelSmall,
                        color = LegadoTheme.colorScheme.primary,
                    )
                }
            }
            Column {
                SmallPlainButton(
                    onClick = onRename,
                    icon = Icons.Default.Edit,
                    contentDescription = stringResource(Res.string.cover_album_rename),
                )
                SmallPlainButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.cover_album_delete),
                )
            }
        }
    }
}

@Composable
private fun CoverAlbumEditorSheet(
    album: CoverAlbumItemUi?,
    onAddImages: (Boolean) -> Unit,
    onRemoveImage: (Boolean, String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selectedTab by remember(album?.id) { mutableIntStateOf(0) }
    val isDark = selectedTab == 1
    AppModalBottomSheet(
        show = album != null,
        onDismissRequest = onDismissRequest,
        title = album?.name,
        endAction = {
            MediumTonalButton(
                onClick = { onAddImages(isDark) },
                icon = Icons.Default.Add,
                contentDescription = stringResource(Res.string.cover_album_add_images),
            )
        },
    ) {
        CardTabRow(
            tabTitles = listOf(
                stringResource(Res.string.day),
                stringResource(Res.string.night),
            ),
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        val images = if (isDark) album?.darkImages.orEmpty() else album?.lightImages.orEmpty()
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = images,
                key = { it.id },
                contentType = { "coverAlbumImage" },
            ) { image ->
                Box {
                    NormalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(5f / 7f),
                        cornerRadius = 12.dp,
                        containerColor = Color.Transparent,
                    ) {
                        AsyncImage(
                            model = image.path,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    AppIconButton(
                        onClick = { onRemoveImage(isDark, image.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = LegadoTheme.colorScheme.surfaceContainer.copy(
                                alpha = 0.82f
                            )
                        ),
                        shape = CircleShape,
                    ) {
                        AppIcon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.delete),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverAlbumDialogs(
    dialog: CoverAlbumDialog?,
    onIntent: (CoverAlbumIntent) -> Unit,
) {
    when (dialog) {
        CoverAlbumDialog.Create -> CoverAlbumNameDialog(
            show = true,
            title = stringResource(Res.string.cover_album_create),
            initialName = "",
            onSave = { onIntent(CoverAlbumIntent.SaveName(it)) },
            onDismiss = { onIntent(CoverAlbumIntent.DismissDialog) },
        )

        is CoverAlbumDialog.Rename -> CoverAlbumNameDialog(
            show = true,
            title = stringResource(Res.string.cover_album_rename),
            initialName = dialog.currentName,
            onSave = { onIntent(CoverAlbumIntent.SaveName(it)) },
            onDismiss = { onIntent(CoverAlbumIntent.DismissDialog) },
        )

        is CoverAlbumDialog.Delete -> AppAlertDialog(
            data = dialog,
            onDismissRequest = { onIntent(CoverAlbumIntent.DismissDialog) },
            title = stringResource(Res.string.cover_album_delete),
            text = stringResource(Res.string.cover_album_delete_confirmation, dialog.name),
            confirmText = stringResource(Res.string.delete),
            onConfirm = { onIntent(CoverAlbumIntent.ConfirmDelete) },
            dismissText = stringResource(Res.string.cancel),
            onDismiss = { onIntent(CoverAlbumIntent.DismissDialog) },
        )

        null -> Unit
    }
}

@Composable
private fun CoverAlbumNameDialog(
    show: Boolean,
    title: String,
    initialName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initialName, show) { mutableStateOf(initialName) }
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismiss,
        title = title,
        confirmText = stringResource(Res.string.ok),
        onConfirm = {
            if (name.isNotBlank()) onSave(name)
        },
        dismissText = stringResource(Res.string.cancel),
        onDismiss = onDismiss,
        content = {
            AppTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { AppText(stringResource(Res.string.cover_album_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
