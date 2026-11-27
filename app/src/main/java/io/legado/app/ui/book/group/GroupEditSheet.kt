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
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.modalBottomSheet.SheetAction
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
    var showDeleteDialog by remember(group) { mutableStateOf(false) }
    val deletableGroup = group?.takeIf { it.groupId > 0 || it.groupId == Long.MIN_VALUE }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        startSheetAction = deletableGroup?.let {
            groupDeleteAction(onRequestDelete = { showDeleteDialog = true })
        },
        endSheetAction = groupResetCoverAction(
            group = group,
            onCoverPathChange = { coverPath = it },
            viewModel = viewModel
        ),
    ) {
        GroupEditContent(
            group = group,
            onDismissRequest = onDismissRequest,
            coverPath = coverPath,
            onCoverPathChange = { coverPath = it },
            viewModel = viewModel
        )
    }

    GroupDeleteConfirmDialog(
        show = showDeleteDialog,
        group = deletableGroup,
        onDismiss = { showDeleteDialog = false },
        onDeleted = onDismissRequest,
        viewModel = viewModel,
    )
}

@Composable
fun GroupEditContent(
    group: BookGroup? = null,
    onDismissRequest: () -> Unit,
    coverPath: String?,
    onCoverPathChange: (String?) -> Unit,
    tagGroupRule: TagGroupRule? = null,
    viewModel: GroupViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var groupName by remember(group) { mutableStateOf(group?.groupName ?: "") }
    var enableRefresh by remember(group) { mutableStateOf(group?.enableRefresh ?: true) }
    var isPrivate by remember(group) { mutableStateOf(group?.isPrivate ?: false) }
    var showDisablePrivateDialog by remember(group) { mutableStateOf(false) }
    var selectedSortIndex by remember(group) { mutableIntStateOf(group?.bookSort ?: -1) }
    var pattern by remember(tagGroupRule) { mutableStateOf(tagGroupRule?.pattern ?: "") }
    var showPattern by remember(tagGroupRule) { mutableStateOf(tagGroupRule != null || pattern.isNotBlank()) }
    var isSaving by remember(group) { mutableStateOf(false) }

    val sortOptions = stringArrayResource(R.array.book_sort)
    val sortEntryValues = remember(sortOptions.size) {
        Array(sortOptions.size) { (it - 1).toString() }
    }
    val canSetPrivate = group == null || group.groupId > 0

    val selectImage = rememberLauncherForActivityResult(SelectImageContract()) { result ->
        result.uri?.let { uri ->
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@rememberLauncherForActivityResult
                inputStream.use { input ->
                    val extension = groupCoverFileExtension(context.contentResolver.getType(uri))
                    val fileName = MD5Utils.md5Encode(input) + ".$extension"
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

        if (canSetPrivate) {
            Spacer(modifier = Modifier.height(8.dp))

            CompactSwitchSettingItem(
                title = stringResource(R.string.private_group),
                description = stringResource(R.string.private_group_desc),
                checked = isPrivate,
                onCheckedChange = { checked ->
                    if (!checked && group?.isPrivate == true) {
                        showDisablePrivateDialog = true
                    } else {
                        isPrivate = checked
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        CompactSwitchSettingItem(
            title = "标签匹配规则",
            description = if (showPattern) "启用后分组将根据书籍标签自动匹配" else "关闭后分组为手动管理",
            checked = showPattern,
            onCheckedChange = { showPattern = it }
        )

        if (showPattern) {
            Spacer(modifier = Modifier.height(8.dp))

            AppTextField(
                value = pattern,
                onValueChange = { pattern = it },
                backgroundColor = LegadoTheme.colorScheme.onSheetContent,
                label = stringResource(R.string.tag_group_pattern),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConfirmDismissButtonsRow(
            modifier = Modifier.fillMaxWidth(),
            onDismiss = onDismissRequest,
            onConfirm = {
                if (groupName.isEmpty()) {
                    appCtx.toastOnUi("分组名称不能为空")
                } else {
                    isSaving = true
                    if (group != null) {
                        val ruleToSave = if (showPattern && pattern.isNotBlank()) {
                            tagGroupRule?.copy(groupName = groupName, pattern = pattern)
                                ?: TagGroupRule(groupName = groupName, pattern = pattern)
                        } else {
                            null
                        }
                        val ruleToDelete = tagGroupRule.takeIf { ruleToSave == null }
                        viewModel.saveGroup(
                            bookGroup = group.copy(
                                groupName = groupName,
                                cover = coverPath,
                                bookSort = selectedSortIndex,
                                enableRefresh = enableRefresh,
                                isPrivate = isPrivate
                            ),
                            ruleToSave = ruleToSave,
                            ruleToDelete = ruleToDelete,
                            onSuccess = onDismissRequest,
                            onError = { error ->
                                isSaving = false
                                appCtx.toastOnUi(error.localizedMessage ?: "分组保存失败")
                            }
                        )
                    } else {
                        viewModel.addGroup(
                            groupName,
                            selectedSortIndex,
                            enableRefresh,
                            isPrivate,
                            coverPath,
                            pattern = pattern.takeIf { showPattern && it.isNotBlank() },
                            onError = { error ->
                                isSaving = false
                                appCtx.toastOnUi(error.localizedMessage ?: "分组保存失败")
                            }
                        ) {
                            onDismissRequest()
                        }
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
            confirmText = stringResource(R.string.ok),
            dismissEnabled = !isSaving,
            confirmEnabled = !isSaving
        )
    }

    AppAlertDialog(
        show = showDisablePrivateDialog,
        onDismissRequest = { showDisablePrivateDialog = false },
        title = stringResource(R.string.disable_private_group),
        text = stringResource(R.string.sure_disable_private_group),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = {
            isPrivate = false
            showDisablePrivateDialog = false
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = { showDisablePrivateDialog = false }
    )
}

internal fun groupCoverFileExtension(mimeType: String?): String {
    val subtype = mimeType
        ?.substringAfter('/', missingDelimiterValue = "")
        ?.substringBefore(';')
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }
        ?: return "jpg"
    return when (subtype) {
        "jpeg", "jpg", "pjpeg" -> "jpg"
        "svg+xml" -> "svg"
        else -> subtype.substringBefore('+').takeIf {
            it.length in 1..10 && it.all(Char::isLetterOrDigit)
        } ?: "jpg"
    }
}

/**
 * 删除分组操作的声明式形态：点击只请求确认，确认弹窗由 [GroupDeleteConfirmDialog] 承载。
 * 面板头部槽位只放按钮，弹窗必须留在面板外的 composition 里。
 */
@Composable
fun groupDeleteAction(onRequestDelete: () -> Unit): SheetAction = SheetAction(
    onClick = onRequestDelete,
    icon = Icons.Default.Delete,
    contentDescription = stringResource(R.string.delete),
)

/** [groupDeleteAction] 的确认弹窗；[onDeleted] 在删除完成后回调。 */
@Composable
fun GroupDeleteConfirmDialog(
    show: Boolean,
    group: BookGroup?,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: GroupViewModel = koinViewModel()
) {
    AppAlertDialog(
        data = group.takeIf { show },
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete),
        text = stringResource(
            if (group?.isPrivate == true) R.string.sure_del_private_group else R.string.sure_del
        ),
        confirmText = stringResource(android.R.string.ok),
        onConfirm = { target ->
            viewModel.delGroup(target) {
                onDeleted()
            }
        },
        dismissText = stringResource(android.R.string.cancel),
        onDismiss = onDismiss
    )
}

/**
 * 重置封面操作的声明式形态：面板头部槽位用，COUI 引擎下由库按钮渲染。
 *
 * 清理逻辑仍落在调用处传入的 [viewModel] 上；[onCoverPathChange] 在清理完成后回调，
 * 调用方据此同步自己的封面草稿。
 */
@Composable
fun groupResetCoverAction(
    group: BookGroup? = null,
    onCoverPathChange: (String?) -> Unit,
    viewModel: GroupViewModel = koinViewModel()
): SheetAction {
    return SheetAction(
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
        icon = Icons.Default.Restore,
        contentDescription = stringResource(R.string.reset),
    )
}
