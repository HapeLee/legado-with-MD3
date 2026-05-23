package io.legado.app.ui.main.homepage

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.manage.AddCustomModuleDialog
import io.legado.app.ui.main.homepage.manage.AddDialogPrefill
import io.legado.app.ui.main.homepage.manage.BrowseSourcesPage
import io.legado.app.ui.main.homepage.manage.CustomSetAddModulesPage
import io.legado.app.ui.main.homepage.manage.SetDetailPage
import io.legado.app.ui.main.homepage.manage.SetListPage
import io.legado.app.ui.main.homepage.manage.SourceBrowseDetailPage
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HomepageModuleManageSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    state: HomepageManageUiState,
    actions: HomepageManageActions,
) {
    var selectingSetUrl by remember(data != null) { mutableStateOf<String?>(null) }
    var browsingSourceUrl by remember(data != null) { mutableStateOf<String?>(null) }
    var showSourceBrowser by remember(data != null) { mutableStateOf(false) }
    var renameSetId by remember(data != null) { mutableStateOf<String?>(null) }
    var showCreateSetDialog by remember(data != null) { mutableStateOf(false) }
    var addDialogPrefill by remember(data != null) { mutableStateOf<AddDialogPrefill?>(null) }
    var editingModule by remember(data != null) { mutableStateOf<HomepageModuleManageUi?>(null) }
    var deleteConfirmId by remember(data != null) { mutableStateOf<String?>(null) }
    var deleteSetConfirmId by remember(data != null) { mutableStateOf<String?>(null) }
    var customSetTitleEdit: Pair<String, String>? by remember(data != null) { mutableStateOf(null) }

    var groupFilter by remember(data != null) { mutableStateOf<String?>(null) }
    var browsingDetail by remember(data != null) { mutableStateOf(false) }
    var browseTab by remember(data != null) { mutableIntStateOf(0) }
    var browseModuleType by remember(data != null) { mutableStateOf("card") }
    var selectedKindTitles by remember(data != null) { mutableStateOf<Set<String>>(emptySet()) }
    var showCustomSetAddModules by remember(data != null) { mutableStateOf(false) }
    var showAddButtonGroupDialog by remember(data != null) { mutableStateOf(false) }
    val defaultQuickActionsTitle = stringResource(R.string.homepage_quick_actions)
    var tempButtonGroupTitle by remember(data != null) { mutableStateOf(defaultQuickActionsTitle) }

    val effectiveTargetSetId = remember(selectingSetUrl, browsingSourceUrl) {
        val url =
            selectingSetUrl ?: browsingSourceUrl?.let { HomepageViewModel.customSetUrl("src_$it") }
        url?.let { HomepageViewModel.customSetIdFromUrl(it) }
    }

    val infiniteModuleIdInTargetSet = remember(state.allJoinedModules, effectiveTargetSetId) {
        state.allJoinedModules.find {
            it.customSetId == effectiveTargetSetId && HomepageViewModel.isInfinite(
                it.type,
                it.layoutConfig
            )
        }?.id
    }

    val canSelectInfiniteGlobal = infiniteModuleIdInTargetSet == null

    val allGroups = remember(state.browseSources) {
        state.browseSources.flatMap { it.sourceGroup?.split(",") ?: emptyList() }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredBrowseSources = remember(state.browseSources, groupFilter) {
        val list = if (groupFilter == null) state.browseSources
        else state.browseSources.filter {
            it.sourceGroup?.split(",")?.contains(groupFilter) == true
        }
        list.toImmutableList()
    }

    AppModalBottomSheet(
        data = data,
        onDismissRequest = {
            onDismissRequest()
            selectingSetUrl = null
            browsingSourceUrl = null
            showSourceBrowser = false
            groupFilter = null
        },
        title = when {
            showCustomSetAddModules -> stringResource(R.string.homepage_add_module)
            browsingSourceUrl != null && browsingDetail ->
                state.browseSources.find { it.sourceUrl == browsingSourceUrl }?.sourceName
                    ?: stringResource(R.string.homepage_module_list)

            showSourceBrowser || browsingSourceUrl != null -> stringResource(R.string.homepage_browse_source_modules)
            selectingSetUrl != null && HomepageViewModel.isCustomSetUrl(selectingSetUrl!!) ->
                (state.sets.find { it.sourceUrl == selectingSetUrl }?.sourceName
                    ?: stringResource(R.string.homepage_set_detail))

            else -> stringResource(R.string.homepage_module_manage)
        },
        startAction = {
            if (showCustomSetAddModules) {
                SmallIconButton(
                    onClick = { showCustomSetAddModules = false },
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack
                )
            } else if (browsingSourceUrl != null || showSourceBrowser) {
                SmallIconButton(
                    onClick = {
                        if (browsingDetail) browsingDetail = false
                        else if (showSourceBrowser) showSourceBrowser = false
                        else browsingSourceUrl = null
                    },
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack
                )
            } else if (selectingSetUrl != null) {
                SmallIconButton(
                    onClick = { selectingSetUrl = null },
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack
                )
            }
        },
        endAction = {
            if (browsingDetail && browseTab == 2 && browseModuleType == "buttonGroup" && selectedKindTitles.isNotEmpty()) {
                SmallIconButton(
                    onClick = { showAddButtonGroupDialog = true },
                    imageVector = Icons.Default.Check
                )
            } else if ((showSourceBrowser || browsingSourceUrl != null) && !browsingDetail) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    SmallIconButton(
                        onClick = { expanded = true },
                        imageVector = Icons.Default.FilterList
                    )
                    RoundDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }) {
                        RoundDropdownMenuItem(
                            text = stringResource(R.string.homepage_all_groups),
                            onClick = { groupFilter = null; expanded = false },
                            trailingIcon = if (groupFilter == null) {
                                { AppIcon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                            } else null
                        )
                        allGroups.forEach { group ->
                            RoundDropdownMenuItem(
                                text = group,
                                onClick = { groupFilter = group; expanded = false },
                                trailingIcon = if (groupFilter == group) {
                                    { AppIcon(Icons.Default.Check, null, Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    ) {
        var backProgress by remember { mutableFloatStateOf(0f) }
        PredictiveBackHandler(
            enabled = showCustomSetAddModules || browsingDetail || showSourceBrowser || browsingSourceUrl != null || selectingSetUrl != null
        ) { progress ->
            try {
                progress.collect { event ->
                    backProgress = event.progress
                }
                when {
                    showCustomSetAddModules -> showCustomSetAddModules = false
                    browsingDetail -> browsingDetail = false
                    showSourceBrowser -> showSourceBrowser = false
                    browsingSourceUrl != null -> browsingSourceUrl = null
                    selectingSetUrl != null -> selectingSetUrl = null
                }
            } finally {
                backProgress = 0f
            }
        }

        val setUrl = selectingSetUrl
        val browseUrl = browsingSourceUrl
        val isBrowsing = showSourceBrowser || browseUrl != null

        LaunchedEffect(browseUrl) {
            browseUrl?.let { actions.onSyncSourceModules(it) }
        }

        val currentLevel = when {
            showCustomSetAddModules || (browseUrl != null && browsingDetail) -> 2
            isBrowsing || (setUrl != null && HomepageViewModel.isCustomSetUrl(setUrl)) -> 1
            else -> 0
        }

        AnimatedContent(
            targetState = currentLevel to (showCustomSetAddModules to browsingDetail),
            transitionSpec = {
                if (targetState.first > initialState.first) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 3 } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            modifier = Modifier.graphicsLayer {
                val scale = 1f - (backProgress * 0.08f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (backProgress * 0.1f)
            },
            label = "PageTransition"
        ) { _ ->
            when {
                browseUrl != null && browsingDetail -> {
                    SourceBrowseDetailPage(
                        browseUrl = browseUrl,
                        selectingSetUrl = selectingSetUrl,
                        allJoinedModules = state.allJoinedModules,
                        canSelectInfiniteGlobal = canSelectInfiniteGlobal,
                        onGetSourceModules = actions.onGetSourceModules,
                        onGetExploreKinds = actions.onGetExploreKinds,
                        onToggleModule = actions.onToggleModule,
                        onJoinModule = actions.onJoinModule,
                        onReorderModules = actions.onReorderModules,
                        onEditModule = { editingModule = it },
                        onRequestDeleteModule = { deleteConfirmId = it },
                        onAddDialogPrefill = { addDialogPrefill = it },
                        onShowAddButtonGroupDialog = { showAddButtonGroupDialog = true },
                        browseTab = browseTab,
                        onBrowseTabChange = { browseTab = it },
                        browseModuleType = browseModuleType,
                        onBrowseModuleTypeChange = { browseModuleType = it },
                        selectedKindTitles = selectedKindTitles,
                        onSelectedKindTitlesChange = { selectedKindTitles = it },
                    )
                }

                showCustomSetAddModules -> {
                    CustomSetAddModulesPage(
                        setUrl = selectingSetUrl!!,
                        allJoinedModules = state.allJoinedModules,
                        sourceNames = state.sourceNames,
                        onToggleModuleToSet = { module, inCurrentSet, isBlocked ->
                            if (inCurrentSet) {
                                val instanceId = state.allJoinedModules
                                    .find {
                                        it.customSetId == HomepageViewModel.customSetIdFromUrl(
                                            selectingSetUrl!!
                                        ) && it.moduleKey == module.moduleKey
                                    }?.id
                                if (instanceId != null) {
                                    actions.onDeleteModule(instanceId)
                                }
                            } else if (!isBlocked) {
                                actions.onAssignModuleToCustomSet(
                                    module.id,
                                    HomepageViewModel.customSetIdFromUrl(selectingSetUrl!!)
                                )
                            }
                        },
                    )
                }

                isBrowsing -> {
                    BrowseSourcesPage(
                        browseSources = filteredBrowseSources,
                        getSourceModules = actions.onGetSourceModules,
                        onSelectSource = {
                            browsingSourceUrl = it
                            browsingDetail = true
                        },
                    )
                }

                setUrl != null && HomepageViewModel.isCustomSetUrl(setUrl) -> {
                    SetDetailPage(
                        setUrl = setUrl,
                        allJoinedModules = state.allJoinedModules,
                        onToggleModule = actions.onToggleModule,
                        onReorderModules = actions.onReorderModules,
                        onEditModule = { editingModule = it },
                        onRequestDeleteModule = { deleteConfirmId = it },
                        onBrowseSourceModules = {
                            val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
                            browsingSourceUrl = setId.removePrefix("src_")
                            browsingDetail = true
                        },
                        onAddModules = {
                            showCustomSetAddModules = true
                        },
                    )
                }

                else -> {
                    SetListPage(
                        sets = state.sets,
                        onToggleSet = actions.onToggleSet,
                        onReorderSets = actions.onReorderSets,
                        onSelectSet = { selectingSetUrl = it },
                        onRenameSet = { renameSetId = it },
                        onDeleteSet = { deleteSetConfirmId = it },
                        onCreateSet = { showCreateSetDialog = true },
                        onBrowseSources = { showSourceBrowser = true },
                    )
                }
            }
        }

        var tempName by remember { mutableStateOf("") }
        AppAlertDialog(
            data = renameSetId,
            onDismissRequest = { renameSetId = null },
            title = stringResource(R.string.homepage_rename_custom_set),
            content = { setId ->
                val currentName =
                    remember(setId) { state.sets.find { it.sourceUrl == setId }?.sourceName ?: "" }
                LaunchedEffect(setId) { tempName = currentName }
                AppTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = stringResource(R.string.homepage_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onConfirm = { setId ->
                if (tempName.isNotBlank()) actions.onRenameCustomSet(
                    HomepageViewModel.customSetIdFromUrl(setId),
                    tempName
                )
                renameSetId = null
            },
            confirmText = stringResource(R.string.dialog_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { renameSetId = null }
        )

        AppAlertDialog(
            data = if (showCreateSetDialog) Unit else null,
            onDismissRequest = { showCreateSetDialog = false },
            title = stringResource(R.string.homepage_new_custom_set_title),
            content = {
                LaunchedEffect(Unit) { tempName = "" }
                AppTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = stringResource(R.string.homepage_name_label),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onConfirm = {
                if (tempName.isNotBlank()) actions.onCreateCustomSet(tempName)
                showCreateSetDialog = false
            },
            confirmText = stringResource(R.string.dialog_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { showCreateSetDialog = false }
        )

        AppAlertDialog(
            data = deleteSetConfirmId,
            onDismissRequest = { deleteSetConfirmId = null },
            title = stringResource(R.string.homepage_delete_custom_set),
            text = stringResource(R.string.homepage_delete_custom_set_confirm),
            onConfirm = { setId ->
                actions.onDeleteCustomSet(HomepageViewModel.customSetIdFromUrl(setId))
                deleteSetConfirmId = null
            },
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { deleteSetConfirmId = null }
        )

        AppAlertDialog(
            data = deleteConfirmId,
            onDismissRequest = { deleteConfirmId = null },
            title = stringResource(R.string.homepage_remove_module),
            text = stringResource(R.string.homepage_remove_module_confirm),
            onConfirm = { id ->
                actions.onDeleteModule(id)
                deleteConfirmId = null
            },
            confirmText = stringResource(R.string.remove),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { deleteConfirmId = null }
        )

        AddCustomModuleDialog(
            data = addDialogPrefill,
            sourceUrl = browsingSourceUrl ?: "",
            targetSetId = effectiveTargetSetId ?: "",
            prefillTitle = addDialogPrefill?.title ?: "",
            prefillUrl = addDialogPrefill?.url ?: "",
            prefillType = addDialogPrefill?.type ?: "card",
            canSelectInfinite = canSelectInfiniteGlobal,
            onDismissRequest = { addDialogPrefill = null },
            onConfirm = { def ->
                actions.onAddCustomModule(browsingSourceUrl!!, effectiveTargetSetId, def)
                addDialogPrefill = null
            }
        )

        AddCustomModuleDialog(
            data = editingModule,
            sourceUrl = editingModule?.sourceUrl ?: "",
            targetSetId = editingModule?.customSetId ?: "",
            prefillTitle = editingModule?.title ?: "",
            prefillUrl = editingModule?.url ?: "",
            prefillType = editingModule?.type ?: "card",
            prefillArgs = editingModule?.args ?: "",
            prefillLayoutConfig = editingModule?.layoutConfig ?: "",
            canSelectInfinite = canSelectInfiniteGlobal || (editingModule?.id != null && editingModule?.id == infiniteModuleIdInTargetSet),
            onDismissRequest = { editingModule = null },
            onConfirm = { def ->
                actions.onUpdateModule(editingModule!!.id, def)
                editingModule = null
            }
        )

        var titleState by remember { mutableStateOf("") }
        AppAlertDialog(
            data = if (showAddButtonGroupDialog) Unit else null,
            onDismissRequest = { showAddButtonGroupDialog = false },
            title = stringResource(R.string.homepage_add_button_group),
            content = {
                val quickActionsLabel = stringResource(R.string.homepage_quick_actions)
                LaunchedEffect(Unit) { tempButtonGroupTitle = quickActionsLabel }
                AppTextField(
                    value = tempButtonGroupTitle,
                    onValueChange = { tempButtonGroupTitle = it },
                    label = stringResource(R.string.homepage_module_title_label),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onConfirm = {
                actions.onAddButtonGroupFromKinds(
                    browsingSourceUrl!!,
                    effectiveTargetSetId,
                    tempButtonGroupTitle,
                    selectedKindTitles.toList()
                )
                selectedKindTitles = emptySet()
                showAddButtonGroupDialog = false
            },
            confirmText = stringResource(R.string.dialog_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { showAddButtonGroupDialog = false }
        )

        AppAlertDialog(
            data = customSetTitleEdit,
            onDismissRequest = { customSetTitleEdit = null },
            title = stringResource(R.string.homepage_custom_title),
            content = { (_, title) ->
                LaunchedEffect(title) { titleState = title }
                AppTextField(
                    value = titleState,
                    onValueChange = { titleState = it },
                    label = stringResource(R.string.homepage_title_label),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            onConfirm = { (id, _) ->
                actions.onSetCustomSetTitle(id, titleState.takeIf { it.isNotBlank() })
                customSetTitleEdit = null
            },
            confirmText = stringResource(R.string.dialog_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { customSetTitleEdit = null }
        )
    }
}
