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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.components.cover.Cover
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet
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
    group: BookGroup? = null,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    GlassModalBottomSheet(onDismissRequest = onDismissRequest) {
        GroupEditContent(
            group = group,
            onDismissRequest = onDismissRequest,
            viewModel = viewModel
        )
    }
}

@Composable
fun GroupEditContent(
    group: BookGroup? = null,
    onDismissRequest: () -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var groupName by remember(group) { mutableStateOf(group?.groupName ?: "") }
    var coverPath by remember(group) { mutableStateOf(group?.cover) }
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
                    coverPath = file.absolutePath
                }
            } catch (e: Exception) {
                appCtx.toastOnUi(e.localizedMessage)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.group_edit),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Cover(
                path = coverPath,
                modifier = Modifier
                    .width(96.dp)
                    .clickable { selectImage.launch() }
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                CompactDropdownSettingItem(
                    title = stringResource(R.string.sort),
                    selectedValue = selectedSortIndex.toString(),
                    displayEntries = sortOptions,
                    entryValues = sortEntryValues,
                    onValueChange = {
                        selectedSortIndex = it.toInt()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactSwitchSettingItem(
            title = stringResource(R.string.allow_drop_down_refresh),
            checked = enableRefresh,
            onCheckedChange = { enableRefresh = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group != null && (group.groupId > 0 || group.groupId == Long.MIN_VALUE)) {
                    OutlinedIconButton(onClick = {
                        context.alert(R.string.delete, R.string.sure_del) {
                            yesButton {
                                viewModel.delGroup(group) {
                                    onDismissRequest()
                                }
                            }
                            noButton()
                        }
                    }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                }

                OutlinedIconButton(onClick = {
                    if (group != null) {
                        viewModel.clearCover(group) {
                            coverPath = null
                            appCtx.toastOnUi("封面已重置")
                        }
                    } else {
                        coverPath = null
                    }
                }) {
                    Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.reset))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel))
                }
                Button(onClick = {
                    if (groupName.isEmpty()) {
                        appCtx.toastOnUi("分组名称不能为空")
                    } else {
                        if (group != null) {
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
                }) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}
