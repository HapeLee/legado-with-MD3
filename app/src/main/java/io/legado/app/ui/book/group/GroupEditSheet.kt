package io.legado.app.ui.book.group

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactSwitchSettingItem
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.launch
import io.legado.app.utils.toastOnUi
import org.koin.androidx.compose.koinViewModel
import splitties.init.appCtx
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditSheet(
    show: Boolean,
    group: BookGroup? = null,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    var coverPath by remember(group) { mutableStateOf(group?.cover) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        startAction = if (group != null && (group.groupId > 0 || group.groupId == Long.MIN_VALUE)) {
            {
                GroupDeleteAction(
                    group = group,
                    onDismissRequest = onDismissRequest,
                    viewModel = viewModel
                )
            }
        } else {
            null
        },
        endAction = {
            GroupResetCoverAction(
                group = group,
                onCoverPathChange = { coverPath = it },
                viewModel = viewModel
            )
        }
    ) {
        GroupEditContent(
            group = group,
            onDismissRequest = onDismissRequest,
            coverPath = coverPath,
            onCoverPathChange = { coverPath = it },
            viewModel = viewModel
        )
    }
}

@Composable
fun GroupEditContent(
    group: BookGroup? = null,
    onDismissRequest: () -> Unit,
    coverPath: String?,
    onCoverPathChange: (String?) -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var groupName by remember(group) { mutableStateOf(group?.groupName ?: "") }
    var enableRefresh by remember(group) { mutableStateOf(group?.enableRefresh ?: true) }
    var selectedSortIndex by remember(group) { mutableIntStateOf(group?.bookSort ?: -1) }

    val sortOptions = stringArrayResource(R.array.book_sort)
    val sortEntryValues = remember(sortOptions) {
        Array(sortOptions.size) { (it - 1).toString() }
    }

    val selectImage = rememberLauncherForActivityResult(SelectImageContract()) { result ->
        result.uri?.let { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@rememberLauncherForActivityResult
                inputStream.use { input ->
                    val fileName = MD5Utils.md5Encode(input) + ".png"
                    val file =
                        FileUtils.createFileIfNotExist(context.externalFiles, "covers", fileName)
                    FileOutputStream(file).use { output ->
                        context.contentResolver.openInputStream(uri)?.use { it.copyTo(output) }
                    }
                    onCoverPathChange(file.absolutePath)
                }
            } catch (e: Exception) {
                appCtx.toastOnUi(e.localizedMessage)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoilBookCover(
                name = null,
                author = null,
                sourceOrigin = group?.groupName,
                path = coverPath,
                modifier = Modifier
                    .width(96.dp)
                    .clickable { selectImage.launch() }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                    label = stringResource(R.string.group_name),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                GlassCard(
                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                ) {
                    CompactDropdownSettingItem(
                        title = stringResource(R.string.sort),
                        selectedValue = selectedSortIndex.toString(),
                        color = LegadoTheme.colorScheme.onSheetContent,
                        displayEntries = sortOptions,
                        entryValues = sortEntryValues,
                        onValueChange = {
                            selectedSortIndex = it.toInt()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactSwitchSettingItem(
            title = stringResource(R.string.allow_drop_down_refresh),
            checked = enableRefresh,
            onCheckedChange = { enableRefresh = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConfirmDismissButtonsRow(
            modifier = Modifier.fillMaxWidth(),
            onDismiss = onDismissRequest,
            onConfirm = {
                if (groupName.isEmpty()) {
                    appCtx.toastOnUi("分组名称不能为空")
                } else {
                    if (group != null && group.groupId != 0b1L) {
                        viewModel.upGroup(
                            group.copy(
                                groupName = groupName,
                                cover = coverPath,
                                bookSort = selectedSortIndex,
                                enableRefresh = enableRefresh
                            )
                        ) {
                            onDismissRequest()
                        }
                    } else {
                        viewModel.addGroup(
                            groupName,
                            selectedSortIndex,
                            enableRefresh,
                            coverPath
                        ) {
                            onDismissRequest()
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.ok)
        )
    }
}

@Composable
fun GroupDeleteAction(
    group: BookGroup,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    MediumIconButton(
        onClick = {
            showDeleteDialog = true
        },
        imageVector = Icons.Default.Delete
    )

    AppAlertDialog(
        show = showDeleteDialog,
        onDismissRequest = { showDeleteDialog = false },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            showDeleteDialog = false
            viewModel.delGroup(group) {
                onDismissRequest()
            }
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDeleteDialog = false }
    )
}

@Composable
fun GroupResetCoverAction(
    group: BookGroup? = null,
    onCoverPathChange: (String?) -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    MediumIconButton(
        onClick = {
            if (group != null) {
                viewModel.clearCover(group) {
                    onCoverPathChange(null)
                    appCtx.toastOnUi("封面已重置")
                }
            } else {
                onCoverPathChange(null)
            }
        },
        imageVector = Icons.Default.Restore
    )
}
