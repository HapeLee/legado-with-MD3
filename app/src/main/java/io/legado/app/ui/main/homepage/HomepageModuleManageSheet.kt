package io.legado.app.ui.main.homepage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.gson.JsonParser
import io.legado.app.domain.model.HomepageModuleType
import io.legado.app.domain.model.ModuleDef
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.JsonConfigEditor
import io.legado.app.ui.widget.components.JsonKeyEditorConfig
import io.legado.app.ui.widget.components.JsonRawEditor
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.button.SecondaryButton
import io.legado.app.ui.widget.components.button.SmallIconButton
import io.legado.app.ui.widget.components.card.ReorderableSelectionItem
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.divider.PillDivider
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.CompactClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.CompactDropdownSettingItem
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.tabRow.AppTabRow
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.move
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HomepageModuleManageSheet(
    data: T?,
    onDismissRequest: () -> Unit,
    sets: List<HomepageSourceManageUi>,
    browseSources: List<HomepageSourceManageUi>,
    onToggleSet: (String, Boolean) -> Unit,
    onGetModulesInSet: (String) -> List<HomepageModuleManageUi>,
    onGetSourceModules: (String, String?) -> List<HomepageModuleManageUi>,
    onToggleModule: (String, Boolean) -> Unit,
    onJoinModule: (String, String?, ModuleDef) -> Unit,
    onAddCustomModule: (String, String?, ModuleDef) -> Unit,
    onAddButtonGroupFromKinds: (String, String?, String, List<String>) -> Unit,
    onGetExploreKinds: (String) -> List<Pair<String, String>>,
    onUpdateModule: (String, ModuleDef) -> Unit,
    onDeleteModule: (String) -> Unit,
    onReorderModules: (List<String>) -> Unit,
    onReorderSets: (List<String>) -> Unit = {},
    onSetCustomSetTitle: (String, String?) -> Unit,
    onCreateCustomSet: (String) -> Unit,
    onRenameCustomSet: (String, String) -> Unit,
    onDeleteCustomSet: (String) -> Unit,
    onGetAllModulesGroupedBySource: () -> Map<String, List<HomepageModuleManageUi>> = { emptyMap() },
    onGetSourceName: (String) -> String = { it },
    onAssignModuleToCustomSet: (String, String?) -> Unit = { _, _ -> },
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
    var tempButtonGroupTitle by remember(data != null) { mutableStateOf("快捷操作") }

    val currentTargetSetId = remember(selectingSetUrl) {
        selectingSetUrl?.let { HomepageViewModel.customSetIdFromUrl(it) }
    }

    val allGroups = remember(browseSources) {
        browseSources.flatMap { it.sourceGroup?.split(",") ?: emptyList() }
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredBrowseSources = remember(browseSources, groupFilter) {
        if (groupFilter == null) browseSources
        else browseSources.filter { it.sourceGroup?.split(",")?.contains(groupFilter) == true }
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
            showCustomSetAddModules -> "添加模块"
            browsingSourceUrl != null && browsingDetail ->
                browseSources.find { it.sourceUrl == browsingSourceUrl }?.sourceName ?: "模块列表"

            showSourceBrowser || browsingSourceUrl != null -> "浏览书源模块"
            selectingSetUrl != null && HomepageViewModel.isCustomSetUrl(selectingSetUrl!!) ->
                (sets.find { it.sourceUrl == selectingSetUrl }?.sourceName ?: "集详情")

            else -> "首页模块管理"
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
                            text = "全部分组",
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
        val setUrl = selectingSetUrl
        val browseUrl = browsingSourceUrl
        val isBrowsing = showSourceBrowser || browseUrl != null
        when {
            browseUrl != null && browsingDetail -> {
                // 三级：浏览书源的模块列表（已加入 / 书源模块 / 发现）
                val displaySetUrl =
                    selectingSetUrl ?: HomepageViewModel.customSetUrl("src_$browseUrl")
                val currentSetId = HomepageViewModel.customSetIdFromUrl(displaySetUrl)
                val joinedModules = onGetModulesInSet(displaySetUrl)

                val standardModules =
                    joinedModules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
                val infiniteModules =
                    joinedModules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }

                val joinedKeys = joinedModules.map { it.moduleKey }.toSet()
                val sourceModules = onGetSourceModules(browseUrl, currentSetId)
                val exploreKinds = remember(browseUrl) { onGetExploreKinds(browseUrl) }

                Column {
                    AppTabRow(
                        tabTitles = listOf("已加入", "书源模块", "发现"),
                        selectedTabIndex = browseTab,
                        onTabSelected = { browseTab = it }
                    )
                    when (browseTab) {
                        0 -> {
                            if (joinedModules.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText("暂无已加入的模块")
                                }
                            } else {
                                var listData by remember(displaySetUrl) {
                                    mutableStateOf(
                                        standardModules
                                    )
                                }
                                val listState = rememberLazyListState()
                                val reorderableState =
                                    rememberReorderableLazyListState(listState) { from, to ->
                                        listData = listData.toMutableList().apply {
                                            val fromIndex = (from.index - 1).coerceIn(0, lastIndex)
                                            val toIndex = (to.index - 1).coerceIn(0, lastIndex)
                                            move(fromIndex, toIndex)
                                        }
                                    }
                                LaunchedEffect(standardModules) {
                                    if (!reorderableState.isAnyItemDragging) listData =
                                        standardModules.distinctBy { it.id }
                                }
                                LaunchedEffect(reorderableState.isAnyItemDragging) {
                                    if (!reorderableState.isAnyItemDragging) {
                                        val orderedIds =
                                            listData.map { it.id } + infiniteModules.map { it.id }
                                        if (orderedIds != joinedModules.map { it.id }) {
                                            onReorderModules(orderedIds)
                                        }
                                    }
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    item(key = "header_standard") {
                                        AppText(
                                            text = "标准模块 (可拖拽排序)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(
                                                horizontal = 16.dp,
                                                vertical = 4.dp
                                            )
                                        )
                                    }

                                    items(listData, key = { it.id }) { module ->
                                        ReorderableSelectionItem(
                                            state = reorderableState,
                                            key = module.id,
                                            title = module.title,
                                            subtitle = HomepageModuleType.fromKey(module.type).title,
                                            isEnabled = module.isVisible,
                                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                                            onEnabledChange = { enabled ->
                                                onToggleModule(module.id, enabled)
                                                listData = listData.map {
                                                    if (it.id == module.id) it.copy(isVisible = enabled) else it
                                                }
                                            },
                                            trailingAction = {
                                                SmallIconButton(
                                                    onClick = { editingModule = module },
                                                    imageVector = Icons.Default.Edit
                                                )
                                                SmallIconButton(
                                                    onClick = { deleteConfirmId = module.id },
                                                    imageVector = Icons.Default.Delete
                                                )
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }

                                    if (infiniteModules.isNotEmpty()) {
                                        item(key = "header_infinite") {
                                            PillDivider(
                                                modifier = Modifier.padding(
                                                    vertical = 8.dp,
                                                    horizontal = 16.dp
                                                )
                                            )
                                            AppText(
                                                text = "底栏无限模块",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.padding(
                                                    horizontal = 16.dp,
                                                    vertical = 4.dp
                                                )
                                            )
                                        }

                                        items(infiniteModules, key = { it.id }) { module ->
                                            val isEffective =
                                                infiniteModules.firstOrNull() == module
                                            SelectionItemCard(
                                                title = module.title,
                                                subtitle = HomepageModuleType.fromKey(module.type).title + if (isEffective) " · 当前生效" else " · 已被屏蔽",
                                                isEnabled = module.isVisible,
                                                containerColor = if (isEffective) LegadoTheme.colorScheme.surfaceContainerHigh else LegadoTheme.colorScheme.onSheetContent,
                                                onEnabledChange = { onToggleModule(module.id, it) },
                                                trailingAction = {
                                                    SmallIconButton(
                                                        onClick = { editingModule = module },
                                                        imageVector = Icons.Default.Edit
                                                    )
                                                    SmallIconButton(
                                                        onClick = { deleteConfirmId = module.id },
                                                        imageVector = Icons.Default.Delete
                                                    )
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            if (sourceModules.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppText("该书源的 homepageModules JSON 为空")
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        sourceModules.distinctBy { it.id },
                                        key = { it.id }) { module ->
                                        val isJoined = joinedKeys.contains(module.moduleKey)
                                        SelectionItemCard(
                                            title = module.title,
                                            subtitle = module.moduleKey + if (isJoined) " · 已加入" else "",
                                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                                            isSelected = isJoined,
                                            inSelectionMode = true,
                                            onToggleSelection = {
                                                if (!isJoined) onJoinModule(
                                                    browseUrl, currentTargetSetId, ModuleDef(
                                                        key = module.moduleKey,
                                                        type = module.type,
                                                        title = module.title,
                                                        sourceUrl = browseUrl,
                                                    )
                                                )
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        2 -> {
                            val isButtonGroup = browseModuleType == "buttonGroup"
                            val selectableKinds = exploreKinds
                            Column {
                                val typeList = remember {
                                    HomepageModuleType.entries.filter { it != HomepageModuleType.Unknown }
                                }
                                CompactDropdownSettingItem(
                                    title = "模块类型",
                                    selectedValue = browseModuleType,
                                    displayEntries = typeList.map { it.title }.toTypedArray(),
                                    entryValues = typeList.map { it.key }.toTypedArray(),
                                    onValueChange = {
                                        browseModuleType = it; selectedKindTitles = emptySet()
                                    }
                                )
                                if (selectableKinds.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppText(
                                            "该书源暂无发现项",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    AppText(
                                        "选择项",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(
                                            selectableKinds.distinctBy { it.first + it.second },
                                            key = { it.first + it.second }) { (kindTitle, kindUrl) ->
                                            if (isButtonGroup) {
                                                val isSelected = kindTitle in selectedKindTitles
                                                SelectionItemCard(
                                                    title = kindTitle,
                                                    subtitle = kindUrl.take(60),
                                                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                                                    isSelected = isSelected,
                                                    inSelectionMode = true,
                                                    onToggleSelection = {
                                                        selectedKindTitles =
                                                            if (isSelected) selectedKindTitles - kindTitle
                                                            else selectedKindTitles + kindTitle
                                                    },
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            } else {
                                                val isJoined = joinedKeys.contains(kindTitle)
                                                SelectionItemCard(
                                                    title = kindTitle,
                                                    subtitle = kindUrl.take(60) + if (isJoined) " · 已加入" else "",
                                                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                                                    isSelected = isJoined,
                                                    inSelectionMode = true,
                                                    onToggleSelection = {
                                                        if (!isJoined) addDialogPrefill =
                                                            AddDialogPrefill(
                                                                kindTitle,
                                                                kindUrl,
                                                                browseModuleType
                                                            )
                                                    },
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                SecondaryButton(
                                    text = "+ 手动添加",
                                    onClick = {
                                        addDialogPrefill = AddDialogPrefill(type = browseModuleType)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            showCustomSetAddModules -> {
                // 自定义集添加模块
                val currentSetUrl = selectingSetUrl!!
                val currentSetId = HomepageViewModel.customSetIdFromUrl(currentSetUrl)
                val initialJoined = onGetModulesInSet(currentSetUrl)
                    .associateBy({ it.moduleKey }, { it.id })
                var joinedInCurrent by remember(initialJoined) { mutableStateOf(initialJoined) }

                val grouped = onGetAllModulesGroupedBySource()
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (sourceUrl, modules) ->
                        item(key = "header_$sourceUrl") {
                            AppText(
                                text = onGetSourceName(sourceUrl),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(modules, key = { it.sourceUrl + it.moduleKey }) { module ->
                            val instanceIdInCurrentSet = joinedInCurrent[module.moduleKey]
                            val inCurrentSet = instanceIdInCurrentSet != null
                            SelectionItemCard(
                                title = module.title,
                                subtitle = module.moduleKey,
                                containerColor = LegadoTheme.colorScheme.onSheetContent,
                                isSelected = inCurrentSet,
                                inSelectionMode = true,
                                onToggleSelection = {
                                    if (inCurrentSet) {
                                        onDeleteModule(instanceIdInCurrentSet!!)
                                        joinedInCurrent = joinedInCurrent - module.moduleKey
                                    } else {
                                        onAssignModuleToCustomSet(module.id, currentSetId)
                                        joinedInCurrent =
                                            joinedInCurrent + (module.moduleKey to "temp_${module.id}")
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            isBrowsing -> {
                // 二级：浏览书源列表
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredBrowseSources, key = { it.sourceUrl }) { source ->
                        val moduleCount = onGetSourceModules(source.sourceUrl, null).size
                        SelectionItemCard(
                            title = source.sourceName,
                            subtitle = "$moduleCount 个模块",
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                            onToggleSelection = {
                                browsingSourceUrl = source.sourceUrl
                                browsingDetail = true
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            setUrl != null && HomepageViewModel.isCustomSetUrl(setUrl) -> {
                // 二级：集详情
                val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
                val modules = onGetModulesInSet(setUrl)

                val standardModules =
                    modules.filter { !HomepageViewModel.isInfinite(it.type, it.layoutConfig) }
                val infiniteModules =
                    modules.filter { HomepageViewModel.isInfinite(it.type, it.layoutConfig) }

                if (modules.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppText("暂无模块")
                        SecondaryButton(
                            text = "浏览书源模块添加",
                            onClick = {
                                if (setId.startsWith("src_")) {
                                    browsingSourceUrl = setId.removePrefix("src_")
                                    browsingDetail = true
                                } else {
                                    showCustomSetAddModules = true
                                }
                            }
                        )
                    }
                } else {
                    var listData by remember(setUrl) { mutableStateOf(standardModules) }
                    val listState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                        listData = listData.toMutableList().apply {
                            val fromIndex = (from.index - 1).coerceIn(0, lastIndex)
                            val toIndex = (to.index - 1).coerceIn(0, lastIndex)
                            move(fromIndex, toIndex)
                        }
                    }

                    LaunchedEffect(standardModules) {
                        if (!reorderableState.isAnyItemDragging) listData =
                            standardModules.distinctBy { it.id }
                    }
                    LaunchedEffect(reorderableState.isAnyItemDragging) {
                        if (!reorderableState.isAnyItemDragging) {
                            val orderedIds = listData.map { it.id } + infiniteModules.map { it.id }
                            if (orderedIds != modules.map { it.id }) onReorderModules(orderedIds)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (listData.isNotEmpty()) {
                            item(key = "header_std_detail") {
                                AppText(
                                    text = "标准模块",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(listData, key = { it.id }) { module ->
                                ReorderableSelectionItem(
                                    state = reorderableState,
                                    key = module.id,
                                    title = module.title,
                                    subtitle = HomepageModuleType.fromKey(module.type).title,
                                    isEnabled = module.isVisible,
                                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                                    onEnabledChange = { enabled ->
                                        onToggleModule(module.id, enabled)
                                        listData = listData.map {
                                            if (it.id == module.id) it.copy(isVisible = enabled) else it
                                        }
                                    },
                                    trailingAction = {
                                        SmallIconButton(
                                            onClick = { editingModule = module },
                                            imageVector = Icons.Default.Edit
                                        )
                                        SmallIconButton(
                                            onClick = { deleteConfirmId = module.id },
                                            imageVector = Icons.Default.Delete
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        if (infiniteModules.isNotEmpty()) {
                            item(key = "header_inf_detail") {
                                HorizontalDivider(
                                    modifier = Modifier.padding(
                                        vertical = 8.dp,
                                        horizontal = 16.dp
                                    )
                                )
                                AppText(
                                    text = "无限模块槽位",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(infiniteModules, key = { it.id }) { module ->
                                val isEffective = infiniteModules.firstOrNull() == module
                                SelectionItemCard(
                                    title = module.title,
                                    subtitle = HomepageModuleType.fromKey(module.type).title,
                                    isEnabled = module.isVisible,
                                    containerColor = if (isEffective) LegadoTheme.colorScheme.surfaceContainerHigh else LegadoTheme.colorScheme.onSheetContent,
                                    onEnabledChange = { onToggleModule(module.id, it) },
                                    trailingAction = {
                                        SmallIconButton(
                                            onClick = { editingModule = module },
                                            imageVector = Icons.Default.Edit
                                        )
                                        SmallIconButton(
                                            onClick = { deleteConfirmId = module.id },
                                            imageVector = Icons.Default.Delete
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        item(key = "browse_from_set") {
                            SecondaryButton(
                                text = "浏览书源模块",
                                onClick = {
                                    if (setId.startsWith("src_")) {
                                        browsingSourceUrl = setId.removePrefix("src_")
                                        browsingDetail = true
                                    } else {
                                        showCustomSetAddModules = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            else -> {
                // 一级：集列表
                var localSets by remember(data != null) { mutableStateOf(sets) }
                val setsListState = rememberLazyListState()
                val setsReorderableState =
                    rememberReorderableLazyListState(setsListState) { from, to ->
                        localSets = localSets.toMutableList().apply {
                            val fromIndex = from.index.coerceIn(0, lastIndex)
                            val toIndex = to.index.coerceIn(0, lastIndex)
                            move(fromIndex, toIndex)
                        }
                    }

                LaunchedEffect(sets) {
                    if (!setsReorderableState.isAnyItemDragging) localSets = sets
                }
                LaunchedEffect(setsReorderableState.isAnyItemDragging) {
                    if (!setsReorderableState.isAnyItemDragging) {
                        val orderedUrls = localSets.map { it.sourceUrl }
                        if (orderedUrls != sets.map { it.sourceUrl }) onReorderSets(orderedUrls)
                    }
                }

                LazyColumn(
                    state = setsListState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(localSets, key = { it.sourceUrl }) { set ->
                        ReorderableSelectionItem(
                            state = setsReorderableState,
                            key = set.sourceUrl,
                            title = set.sourceName,
                            subtitle = "${set.moduleCount} 个模块",
                            containerColor = LegadoTheme.colorScheme.onSheetContent,
                            isEnabled = set.isSelected,
                            onToggleSelection = { selectingSetUrl = set.sourceUrl },
                            onEnabledChange = { enabled ->
                                onToggleSet(set.sourceUrl, enabled)
                                localSets = localSets.map {
                                    if (it.sourceUrl == set.sourceUrl) it.copy(isSelected = enabled) else it
                                }
                            },
                            trailingAction = {
                                SmallIconButton(
                                    onClick = { renameSetId = set.sourceUrl },
                                    imageVector = Icons.Default.DriveFileRenameOutline
                                )
                                SmallIconButton(
                                    onClick = { deleteSetConfirmId = set.sourceUrl },
                                    imageVector = Icons.Default.Delete
                                )
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    item(key = "create_set") {
                        SecondaryButton(
                            text = "+ 新建自定义集",
                            onClick = { showCreateSetDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item(key = "browse_sources") {
                        SecondaryButton(
                            text = "浏览书源模块",
                            onClick = { showSourceBrowser = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    var tempName by remember { mutableStateOf("") }
    AppAlertDialog(
        data = renameSetId,
        onDismissRequest = { renameSetId = null },
        title = "重命名自定义集",
        content = { setId ->
            val currentName =
                remember(setId) { sets.find { it.sourceUrl == setId }?.sourceName ?: "" }
            LaunchedEffect(setId) { tempName = currentName }
            AppTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = "名称",
                modifier = Modifier.fillMaxWidth()
            )
        },
        onConfirm = { setId ->
            if (tempName.isNotBlank()) onRenameCustomSet(
                HomepageViewModel.customSetIdFromUrl(setId),
                tempName
            )
            renameSetId = null
        },
        confirmText = "确定",
        dismissText = "取消",
        onDismiss = { renameSetId = null }
    )

    AppAlertDialog(
        data = if (showCreateSetDialog) Unit else null,
        onDismissRequest = { showCreateSetDialog = false },
        title = "新建自定义集",
        content = {
            LaunchedEffect(Unit) { tempName = "" }
            AppTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = "名称",
                modifier = Modifier.fillMaxWidth()
            )
        },
        onConfirm = {
            if (tempName.isNotBlank()) onCreateCustomSet(tempName)
            showCreateSetDialog = false
        },
        confirmText = "确定",
        dismissText = "取消",
        onDismiss = { showCreateSetDialog = false }
    )

    AppAlertDialog(
        data = deleteSetConfirmId,
        onDismissRequest = { deleteSetConfirmId = null },
        title = "删除自定义集",
        text = "确定要删除该集及其包含的所有模块副本吗？",
        onConfirm = { setId ->
            onDeleteCustomSet(HomepageViewModel.customSetIdFromUrl(setId))
            deleteSetConfirmId = null
        },
        confirmText = "删除",
        dismissText = "取消",
        onDismiss = { deleteSetConfirmId = null }
    )

    AppAlertDialog(
        data = deleteConfirmId,
        onDismissRequest = { deleteConfirmId = null },
        title = "移除模块",
        text = "确定要从当前集中移除该模块吗？",
        onConfirm = { id ->
            onDeleteModule(id)
            deleteConfirmId = null
        },
        confirmText = "移除",
        dismissText = "取消",
        onDismiss = { deleteConfirmId = null }
    )

    AddCustomModuleDialog(
        data = addDialogPrefill,
        sourceUrl = browsingSourceUrl ?: "",
        targetSetId = currentTargetSetId ?: "",
        prefillTitle = addDialogPrefill?.title ?: "",
        prefillUrl = addDialogPrefill?.url ?: "",
        prefillType = addDialogPrefill?.type ?: "card",
        onDismissRequest = { addDialogPrefill = null },
        onConfirm = { def ->
            onAddCustomModule(browsingSourceUrl!!, currentTargetSetId, def)
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
        onDismissRequest = { editingModule = null },
        onConfirm = { def ->
            onUpdateModule(editingModule!!.id, def)
            editingModule = null
        }
    )

    var titleState by remember { mutableStateOf("") }
    AppAlertDialog(
        data = if (showAddButtonGroupDialog) Unit else null,
        onDismissRequest = { showAddButtonGroupDialog = false },
        title = "添加按钮组",
        content = {
            LaunchedEffect(Unit) { tempButtonGroupTitle = "快捷操作" }
            AppTextField(
                value = tempButtonGroupTitle,
                onValueChange = { tempButtonGroupTitle = it },
                label = "模块标题",
                modifier = Modifier.fillMaxWidth()
            )
        },
        onConfirm = {
            onAddButtonGroupFromKinds(
                browsingSourceUrl!!,
                currentTargetSetId,
                tempButtonGroupTitle,
                selectedKindTitles.toList()
            )
            selectedKindTitles = emptySet()
            showAddButtonGroupDialog = false
        },
        confirmText = "确定",
        dismissText = "取消",
        onDismiss = { showAddButtonGroupDialog = false }
    )

    AppAlertDialog(
        data = customSetTitleEdit,
        onDismissRequest = { customSetTitleEdit = null },
        title = "自定义标题",
        content = { (_, title) ->
            LaunchedEffect(title) { titleState = title }
            AppTextField(
                value = titleState,
                onValueChange = { titleState = it },
                label = "标题",
                modifier = Modifier.fillMaxWidth()
            )
        },
        onConfirm = { (id, _) ->
            onSetCustomSetTitle(id, titleState.takeIf { it.isNotBlank() })
            customSetTitleEdit = null
        },
        confirmText = "确定",
        dismissText = "取消",
        onDismiss = { customSetTitleEdit = null }
    )
}

data class AddDialogPrefill(
    val title: String = "",
    val url: String = "",
    val type: String = "card"
)

@Composable
fun <T> AddCustomModuleDialog(
    data: T?,
    sourceUrl: String = "",
    targetSetId: String = "",
    prefillTitle: String = "",
    prefillUrl: String = "",
    prefillType: String = "card",
    prefillArgs: String = "",
    prefillLayoutConfig: String = "",
    onDismissRequest: () -> Unit,
    onConfirm: (ModuleDef) -> Unit,
) {
    var title by remember(data) { mutableStateOf(prefillTitle) }
    var url by remember(data) { mutableStateOf(prefillUrl) }
    var type by remember(data) { mutableStateOf(prefillType) }
    var args by remember(data) { mutableStateOf(prefillArgs) }
    var layoutConfig by remember(data) { mutableStateOf(prefillLayoutConfig) }
    var showRawLayoutConfig by remember(data) { mutableStateOf(false) }

    val layoutKeyConfigs = remember {
        mapOf(
            "fullWidth" to JsonKeyEditorConfig.Switch,
            "showTitle" to JsonKeyEditorConfig.Switch,
            "showMore" to JsonKeyEditorConfig.Switch,
            "isInfinite" to JsonKeyEditorConfig.Switch,
            "aspectRatio" to JsonKeyEditorConfig.Dropdown(
                displayEntries = arrayOf("默认", "1:1", "3:4", "2:3", "16:9"),
                entryValues = arrayOf("", "1:1", "3:4", "2:3", "16:9")
            )
        )
    }

    val hasVisualizableKeys = remember(layoutConfig) {
        runCatching {
            val jsonObject = JsonParser.parseString(layoutConfig).asJsonObject
            jsonObject.keySet().any { key ->
                key == "columns" || key == "rows" || layoutKeyConfigs.containsKey(key)
            }
        }.getOrElse { false }
    }

    AppAlertDialog(
        data = data,
        onDismissRequest = onDismissRequest,
        title = if (prefillTitle.isEmpty()) "添加模块" else "编辑模块",
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth()
                )
                AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = "URL",
                    modifier = Modifier.fillMaxWidth()
                )
                val typeList = remember {
                    HomepageModuleType.entries.filter { it != HomepageModuleType.Unknown }
                }
                DropdownListSettingItem(
                    title = "类型",
                    selectedValue = type,
                    displayEntries = typeList.map { it.title }.toTypedArray(),
                    entryValues = typeList.map { it.key }.toTypedArray(),
                    onValueChange = { type = it }
                )
                AppTextField(
                    value = args,
                    onValueChange = { args = it },
                    label = "Args (JSON)",
                    modifier = Modifier.fillMaxWidth()
                )
                AppText(
                    text = "布局配置",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                if (hasVisualizableKeys) {
                    JsonConfigEditor(
                        jsonString = layoutConfig,
                        onJsonStringChange = { layoutConfig = it },
                        keyConfigs = layoutKeyConfigs,
                        modifier = Modifier.fillMaxWidth()
                    )
                    CompactClickableSettingItem(
                        title = "编辑原始 JSON (LayoutConfig)",
                        onClick = { showRawLayoutConfig = !showRawLayoutConfig }
                    )
                    if (showRawLayoutConfig) {
                        JsonRawEditor(
                            value = layoutConfig,
                            onValueChange = { layoutConfig = it },
                            label = "LayoutConfig (JSON) RAW",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    JsonRawEditor(
                        value = layoutConfig,
                        onValueChange = { layoutConfig = it },
                        label = "LayoutConfig (JSON)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        onConfirm = {
            onConfirm(
                ModuleDef(
                    title = title,
                    url = url,
                    type = type,
                    args = args,
                    layoutConfig = layoutConfig
                )
            )
        },
        confirmText = "确定",
        dismissText = "取消",
        onDismiss = onDismissRequest
    )
}
