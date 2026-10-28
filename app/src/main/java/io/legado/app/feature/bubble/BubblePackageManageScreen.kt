package io.legado.app.feature.bubble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BubblePackageManageScreen(
    state: BubblePackageManageUiState,
    onIntent: (BubblePackageManageIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.bubble_package_manage),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.bubble_package_new),
                        onClick = { onIntent(BubblePackageManageIntent.Create) }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.bubble_package_import_zip),
                        onClick = { onIntent(BubblePackageManageIntent.RequestImport) }
                    )
                }
            }
            items(state.packages, key = { it.dirName }) { item ->
                val applied = state.activeDirName == item.dirName
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = item.name,
                        description = bubblePackageSummary(item),
                        option = if (applied) {
                            stringResource(R.string.bubble_package_applied)
                        } else null,
                        onClick = {
                            if (!applied) onIntent(BubblePackageManageIntent.Apply(item.dirName))
                        }
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.bubble_package_edit),
                        onClick = { onIntent(BubblePackageManageIntent.Edit(item.dirName)) }
                    )
                    if (!item.builtin) {
                        ClickableSettingItem(
                            title = stringResource(R.string.bubble_package_export),
                            onClick = { onIntent(BubblePackageManageIntent.Export(item.dirName)) }
                        )
                        ClickableSettingItem(
                            title = stringResource(R.string.bubble_package_delete),
                            onClick = {
                                onIntent(BubblePackageManageIntent.RequestDelete(item.dirName))
                            }
                        )
                    }
                }
            }
        }
    }

    state.draft?.let { draft ->
        BubblePackageEditSheet(draft = draft, onIntent = onIntent)
    }

    state.pendingDelete?.let { item ->
        AppAlertDialog(
            show = true,
            onDismissRequest = { onIntent(BubblePackageManageIntent.DismissDelete) },
            title = stringResource(R.string.bubble_package_delete),
            text = stringResource(R.string.bubble_package_delete_message, item.name),
            confirmText = stringResource(R.string.bubble_package_delete),
            onConfirm = { onIntent(BubblePackageManageIntent.ConfirmDelete) },
            onDismiss = { onIntent(BubblePackageManageIntent.DismissDelete) },
        )
    }
}

@Composable
private fun bubblePackageSummary(item: BubblePackageItem): String {
    val source = if (item.builtin) {
        stringResource(R.string.review_bubble_package_builtin_tag)
    } else {
        stringResource(R.string.bubble_package_local_tag)
    }
    val resources = if (item.resourceCount > 0) {
        " · ${stringResource(R.string.bubble_package_resource_count, item.resourceCount)}"
    } else {
        ""
    }
    return "$source · ${stringResource(R.string.bubble_package_size_scale, item.sizeScale)}$resources"
}
