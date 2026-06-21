package io.legado.app.ui.main.ai.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.help.ai.AiWorldBookManager
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.ai.AiWorldBookBinding
import io.legado.app.ui.main.ai.AiWorldBookConfig
import io.legado.app.ui.main.ai.AiWorldBookEntry
import io.legado.app.ui.widget.compose.LegadoMiuixActionRow
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.LegadoMiuixPalette
import io.legado.app.ui.widget.compose.LegadoMiuixSelectField
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

private data class WorldBookEditState(
    val id: String?,
    val name: String,
    val description: String,
    val maxEntries: String,
    val enabled: Boolean
)

private data class WorldBookEntryEditState(
    val worldBookId: String,
    val id: String?,
    val title: String,
    val content: String,
    val keys: String,
    val secondaryKeys: String,
    val excludeKeys: String,
    val regexEnabled: Boolean,
    val caseSensitive: Boolean,
    val enabled: Boolean,
    val constant: Boolean,
    val priority: String,
    val position: String,
    val injectDepth: String,
    val role: String,
    val scanDepth: String,
    val maxMatches: String
)

private data class WorldBookJsonState(
    val title: String,
    val text: String,
    val importMode: Boolean
)

private data class WorldBookSelectOption(
    val value: String,
    val label: String,
    val description: String = ""
)

private fun AiComposeStyle.toWorldBookMiuixPalette(): LegadoMiuixPalette {
    return LegadoMiuixPalette(
        accent = colors.accent,
        surface = colors.cardSurface,
        surfaceVariant = colors.toolSurface.copy(alpha = 0.78f),
        primaryText = colors.primaryText,
        secondaryText = colors.secondaryText,
        danger = colors.danger
    )
}

data class AiWorldBookImportPayload(
    val id: Long,
    val raw: String
)

@Composable
fun AiWorldBookManageRoute(
    initialTargetType: String,
    initialTargetKey: String,
    importPayload: AiWorldBookImportPayload? = null,
    onImportConsumed: () -> Unit = {},
    onRequestImportLocal: () -> Unit = {},
    onRequestImportNetwork: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val style = aiComposeStyle(context)
    var books by remember { mutableStateOf(AppConfig.aiWorldBookList) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedBookId by rememberSaveable { mutableStateOf("") }
    var editingBook by remember { mutableStateOf<WorldBookEditState?>(null) }
    var editingEntry by remember { mutableStateOf<WorldBookEntryEditState?>(null) }
    var jsonEditor by remember { mutableStateOf<WorldBookJsonState?>(null) }

    fun persist(updated: List<AiWorldBookConfig>) {
        AppConfig.aiWorldBookList = updated
        books = AppConfig.aiWorldBookList
        postEvent(EventBus.AI_CONFIG_CHANGED, true)
    }

    fun reload() {
        books = AppConfig.aiWorldBookList
    }

    fun importWorldBookRaw(raw: String) {
        runCatching {
            val imported = AiWorldBookManager.parseStandardWorldBook(raw, books.size)
            val existingIds = books.map { it.id }.toSet()
            if (imported.id in existingIds) {
                imported.copy(
                    id = AiWorldBookConfig(name = imported.name).id,
                    name = "${imported.name} 副本"
                )
            } else {
                imported
            }
        }.onSuccess { saving ->
            persist(books + saving)
            expandedBookId = saving.id
            jsonEditor = null
            context.toastOnUi("世界书已导入")
        }.onFailure {
            context.toastOnUi(it.localizedMessage ?: "世界书 JSON 解析失败")
        }
    }

    LaunchedEffect(importPayload?.id) {
        val payload = importPayload ?: return@LaunchedEffect
        importWorldBookRaw(payload.raw)
        onImportConsumed()
    }

    val editEntryState = editingEntry
    val editBookState = editingBook
    val jsonState = jsonEditor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(style.colors.pageBackground)
    ) {
        when {
            jsonState != null -> WorldBookJsonEditor(
                state = jsonState,
                style = style,
                onBack = { jsonEditor = null },
                onImport = ::importWorldBookRaw
            )
            editEntryState != null -> WorldBookEntryEditor(
                state = editEntryState,
                style = style,
                onBack = { editingEntry = null },
                onSave = { state ->
                    val target = books.firstOrNull { it.id == state.worldBookId } ?: return@WorldBookEntryEditor
                    val updatedEntry = state.toEntry(target)
                    persist(books.map { book ->
                        if (book.id == target.id) {
                            book.copy(entries = book.entries.filterNot { it.id == updatedEntry.id } + updatedEntry)
                        } else {
                            book
                        }
                    })
                    editingEntry = null
                    expandedBookId = target.id
                }
            )
            editBookState != null -> WorldBookEditor(
                state = editBookState,
                style = style,
                onBack = { editingBook = null },
                onSave = { state ->
                    val old = state.id?.let { id -> books.firstOrNull { it.id == id } }
                    val updated = state.toBook(old, books.size)
                    persist(books.filterNot { it.id == updated.id } + updated)
                    editingBook = null
                    expandedBookId = updated.id
                }
            )
            else -> WorldBookMainScreen(
                books = books,
                query = query,
                expandedBookId = expandedBookId,
                style = style,
                onBack = onBack,
                onQueryChange = { query = it },
                onExpandedChange = { expandedBookId = it },
                onRefresh = ::reload,
                onImportLocal = onRequestImportLocal,
                onImportNetwork = onRequestImportNetwork,
                onAddBook = {
                    editingBook = WorldBookEditState(
                        id = null,
                        name = "",
                        description = "",
                        maxEntries = "12",
                        enabled = true
                    )
                },
                onEditBook = { book -> editingBook = book.toEditState() },
                onImportPaste = {
                    jsonEditor = WorldBookJsonState(
                        title = "导入世界书 JSON",
                        text = "",
                        importMode = true
                    )
                },
                onCopyBook = { book ->
                    val copy = book.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        name = "${book.name} 副本",
                        bindings = emptyList(),
                        entries = book.entries.map { it.copy(id = java.util.UUID.randomUUID().toString()) }
                    )
                    persist(books + copy)
                    expandedBookId = copy.id
                },
                onDeleteBook = { book -> persist(books.filterNot { it.id == book.id }) },
                onExportBook = { book ->
                    jsonEditor = WorldBookJsonState(
                        title = "${book.name} · 导出 JSON",
                        text = AiWorldBookManager.exportStandardWorldBook(book).toString(2),
                        importMode = false
                    )
                },
                onToggleBook = { book ->
                    persist(books.map { if (it.id == book.id) it.toggleGlobalBinding() else it })
                },
                onAddEntry = { book ->
                    editingEntry = WorldBookEntryEditState(
                        worldBookId = book.id,
                        id = null,
                        title = "",
                        content = "",
                        keys = "",
                        secondaryKeys = "",
                        excludeKeys = "",
                        regexEnabled = false,
                        caseSensitive = false,
                        enabled = true,
                        constant = false,
                        priority = "50",
                        position = AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT,
                        injectDepth = "4",
                        role = AiWorldBookEntry.ROLE_USER,
                        scanDepth = "8",
                        maxMatches = "1"
                    )
                },
                onEditEntry = { book, entry -> editingEntry = entry.toEditState(book.id) },
                onDeleteEntry = { book, entry ->
                    persist(books.map {
                        if (it.id == book.id) it.copy(entries = it.entries.filterNot { item -> item.id == entry.id })
                        else it
                    })
                },
                onToggleEntry = { book, entry ->
                    persist(books.map {
                        if (it.id == book.id) {
                            it.copy(entries = it.entries.map { item ->
                                if (item.id == entry.id) item.copy(enabled = !item.enabled) else item
                            })
                        } else {
                            it
                        }
                    })
                }
            )
        }
    }
}

@Composable
private fun WorldBookMainScreen(
    books: List<AiWorldBookConfig>,
    query: String,
    expandedBookId: String,
    style: AiComposeStyle,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddBook: () -> Unit,
    onImportLocal: () -> Unit,
    onImportNetwork: () -> Unit,
    onImportPaste: () -> Unit,
    onEditBook: (AiWorldBookConfig) -> Unit,
    onCopyBook: (AiWorldBookConfig) -> Unit,
    onDeleteBook: (AiWorldBookConfig) -> Unit,
    onExportBook: (AiWorldBookConfig) -> Unit,
    onToggleBook: (AiWorldBookConfig) -> Unit,
    onAddEntry: (AiWorldBookConfig) -> Unit,
    onEditEntry: (AiWorldBookConfig, AiWorldBookEntry) -> Unit,
    onDeleteEntry: (AiWorldBookConfig, AiWorldBookEntry) -> Unit,
    onToggleEntry: (AiWorldBookConfig, AiWorldBookEntry) -> Unit
) {
    val filtered = remember(books, query) {
        if (query.isBlank()) {
            books
        } else {
            books.filter { book ->
                book.name.contains(query, ignoreCase = true) ||
                        book.description.contains(query, ignoreCase = true) ||
                        book.entries.any {
                            it.title.contains(query, ignoreCase = true) ||
                                    it.content.contains(query, ignoreCase = true) ||
                                    it.keys.any { key -> key.contains(query, ignoreCase = true) }
                        }
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp)
    ) {
        WorldBookTopBar(
            title = "世界书",
            subtitle = "${books.count { it.isGloballyEnabled() }} 个全局启用 · ${books.sumOf { it.entries.size }} 条条目",
            style = style,
            onBack = onBack,
            onAdd = onAddBook,
            onImportLocal = onImportLocal,
            onImportNetwork = onImportNetwork,
            onImportPaste = onImportPaste,
            onRefresh = onRefresh
        )
        SearchField(query, style, onQueryChange)
        Spacer(modifier = Modifier.height(10.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { book ->
                WorldBookCard(
                    book = book,
                    expanded = expandedBookId == book.id,
                    style = style,
                    onExpand = {
                        onExpandedChange(if (expandedBookId == book.id) "" else book.id)
                    },
                    onEdit = { onEditBook(book) },
                    onCopy = { onCopyBook(book) },
                    onDelete = { onDeleteBook(book) },
                    onExport = { onExportBook(book) },
                    onToggle = { onToggleBook(book) },
                    onAddEntry = { onAddEntry(book) },
                    onEditEntry = { onEditEntry(book, it) },
                    onDeleteEntry = { onDeleteEntry(book, it) },
                    onToggleEntry = { onToggleEntry(book, it) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun WorldBookTopBar(
    title: String,
    subtitle: String,
    style: AiComposeStyle,
    onBack: () -> Unit,
    onAdd: (() -> Unit)? = null,
    onImportLocal: (() -> Unit)? = null,
    onImportNetwork: (() -> Unit)? = null,
    onImportPaste: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "返回",
                tint = style.colors.primaryText
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = style.colors.primaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = style.colors.secondaryText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        onRefresh?.let {
            IconButton(onClick = it) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_black_24dp),
                    contentDescription = "刷新",
                    tint = style.colors.secondaryText
                )
            }
        }
        onAdd?.let { addAction ->
            val palette = style.toWorldBookMiuixPalette()
            Box {
                IconButton(onClick = { addMenuExpanded = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = "新增",
                        tint = style.colors.accent
                    )
                }
                if (addMenuExpanded) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        onDismissRequest = { addMenuExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        LegadoMiuixCard(
                            modifier = Modifier
                                .width(188.dp)
                                .padding(top = 46.dp),
                            color = palette.surface,
                            contentColor = palette.primaryText,
                            cornerRadius = style.metrics.chipRadius,
                            insidePadding = PaddingValues(vertical = 6.dp)
                        ) {
                            WorldBookAddMenuItem("新增世界书", palette, style) {
                                addMenuExpanded = false
                                addAction()
                            }
                            onImportLocal?.let { action ->
                                WorldBookAddMenuItem("本地导入", palette, style) {
                                    addMenuExpanded = false
                                    action()
                                }
                            }
                            onImportNetwork?.let { action ->
                                WorldBookAddMenuItem("网络导入", palette, style) {
                                    addMenuExpanded = false
                                    action()
                                }
                            }
                            onImportPaste?.let { action ->
                                WorldBookAddMenuItem("粘贴导入", palette, style) {
                                    addMenuExpanded = false
                                    action()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldBookAddMenuItem(
    text: String,
    palette: LegadoMiuixPalette,
    style: AiComposeStyle,
    onClick: () -> Unit
) {
    LegadoMiuixActionRow(
        text = text,
        palette = palette,
        onClick = onClick,
        cornerRadius = style.metrics.chipRadius,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun SearchField(
    query: String,
    style: AiComposeStyle,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("搜索世界书、条目、关键词") },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = style.colors.secondaryText
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    )
}

@Composable
private fun WorldBookCard(
    book: AiWorldBookConfig,
    expanded: Boolean,
    style: AiComposeStyle,
    onExpand: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onToggle: () -> Unit,
    onAddEntry: () -> Unit,
    onEditEntry: (AiWorldBookEntry) -> Unit,
    onDeleteEntry: (AiWorldBookEntry) -> Unit,
    onToggleEntry: (AiWorldBookEntry) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.metrics.cardRadius))
            .background(style.colors.cardSurface)
            .border(style.metrics.strokeWidth, style.colors.stroke, RoundedCornerShape(style.metrics.cardRadius))
            .clickable { onExpand() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    color = style.colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.description.ifBlank { "无描述" },
                    color = style.colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = book.isGloballyEnabled(), onCheckedChange = { onToggle() })
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoChip(if (book.isGloballyEnabled()) "全局启用" else "可角色绑定", style, selected = book.isGloballyEnabled())
            if (!book.enabled) InfoChip("资料库停用", style)
            InfoChip("${book.entries.size} 条目", style)
            InfoChip("${book.activeBindingCount()} 个绑定", style)
            InfoChip("最多 ${book.maxEntries} 条", style)
        }
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallAction("编辑", style, onClick = onEdit)
            SmallAction("复制", style, onClick = onCopy)
            SmallAction("导出", style, onClick = onExport)
            SmallAction("新增条目", style, onClick = onAddEntry)
            SmallAction("删除", style, danger = true, onClick = onDelete)
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(10.dp))
            if (book.entries.isEmpty()) {
                Text("暂无条目", color = style.colors.secondaryText, fontSize = 13.sp)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.entries.forEach { entry ->
                        EntryRow(
                            entry = entry,
                            style = style,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onDeleteEntry(entry) },
                            onToggle = { onToggleEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: AiWorldBookEntry,
    style: AiComposeStyle,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(style.metrics.chipRadius))
            .background(style.colors.assistantBubble)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = style.colors.primaryText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.content.replace(Regex("\\s+"), " ").trim(),
                    color = style.colors.secondaryText,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = entry.enabled, onCheckedChange = { onToggle() })
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (entry.constant) InfoChip("常驻", style, selected = true)
            if (entry.regexEnabled) InfoChip("正则", style)
            InfoChip("P${entry.priority}", style)
            InfoChip("扫 ${entry.scanDepth}", style)
            entry.keys.take(4).forEach { InfoChip(it, style) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            SmallAction("编辑", style, onClick = onEdit)
            SmallAction("删除", style, danger = true, onClick = onDelete)
        }
    }
}

@Composable
private fun WorldBookJsonEditor(
    state: WorldBookJsonState,
    style: AiComposeStyle,
    onBack: () -> Unit,
    onImport: (String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(state.text) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp)
    ) {
        WorldBookTopBar(
            title = state.title,
            subtitle = if (state.importMode) "粘贴 RikkaHub lorebook JSON" else "标准 lorebook JSON",
            style = style,
            onBack = onBack
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LabeledField(
                    label = "JSON",
                    value = text,
                    style = style,
                    minLines = 16,
                    onValueChange = { text = it }
                )
                SaveBar(
                    enabled = !state.importMode || text.trim().isNotBlank(),
                    style = style,
                    onCancel = onBack,
                    onSave = {
                        if (state.importMode) onImport(text) else onBack()
                    }
                )
            }
        }
    }
}

@Composable
private fun WorldBookEditor(
    state: WorldBookEditState,
    style: AiComposeStyle,
    onBack: () -> Unit,
    onSave: (WorldBookEditState) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var description by rememberSaveable { mutableStateOf(state.description) }
    var maxEntries by rememberSaveable { mutableStateOf(state.maxEntries) }
    var enabled by rememberSaveable { mutableStateOf(state.enabled) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp)
    ) {
        WorldBookTopBar(
            title = if (state.id == null) "新增世界书" else "编辑世界书",
            subtitle = "资料库设置",
            style = style,
            onBack = onBack
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LabeledSwitch("资料库可用", enabled, style) { enabled = it }
                LabeledField("名称", name, style, onValueChange = { name = it })
                LabeledField("描述", description, style, minLines = 3, onValueChange = { description = it })
                LabeledField(
                    label = "每本世界书最大命中条数",
                    value = maxEntries,
                    style = style,
                    keyboardType = KeyboardType.Number,
                    onValueChange = { maxEntries = it.filter(Char::isDigit).take(2) }
                )
                SaveBar(
                    enabled = name.trim().isNotBlank(),
                    style = style,
                    onCancel = onBack,
                    onSave = {
                        onSave(
                            state.copy(
                                name = name.trim(),
                                description = description.trim(),
                                maxEntries = maxEntries.ifBlank { "12" },
                                enabled = enabled
                            )
                        )
                    }
                )
            }
        }
    }
}

private val worldBookPositionOptions = listOf(
    WorldBookSelectOption(
        AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT,
        "系统提示词之后",
        "适合长期设定和角色资料"
    ),
    WorldBookSelectOption(
        AiWorldBookEntry.POSITION_BEFORE_PROMPT,
        "对话正文之前",
        "适合本轮前置补充信息"
    ),
    WorldBookSelectOption(
        AiWorldBookEntry.POSITION_BEFORE_LAST_USER,
        "最后一条用户消息之前",
        "适合贴近当前问题的补充"
    ),
    WorldBookSelectOption(
        AiWorldBookEntry.POSITION_INJECT_DEPTH,
        "按深度插入",
        "按下方插入深度放入历史消息附近"
    )
)

private val worldBookRoleOptions = listOf(
    WorldBookSelectOption(AiWorldBookEntry.ROLE_SYSTEM, "系统消息", "最高优先级设定"),
    WorldBookSelectOption(AiWorldBookEntry.ROLE_USER, "用户消息", "模拟用户侧补充"),
    WorldBookSelectOption(AiWorldBookEntry.ROLE_ASSISTANT, "助手消息", "模拟助手侧记忆")
)

@Composable
private fun WorldBookEntryEditor(
    state: WorldBookEntryEditState,
    style: AiComposeStyle,
    onBack: () -> Unit,
    onSave: (WorldBookEntryEditState) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(state.title) }
    var content by rememberSaveable { mutableStateOf(state.content) }
    var keys by rememberSaveable { mutableStateOf(state.keys) }
    var secondaryKeys by rememberSaveable { mutableStateOf(state.secondaryKeys) }
    var excludeKeys by rememberSaveable { mutableStateOf(state.excludeKeys) }
    var regexEnabled by rememberSaveable { mutableStateOf(state.regexEnabled) }
    var caseSensitive by rememberSaveable { mutableStateOf(state.caseSensitive) }
    var enabled by rememberSaveable { mutableStateOf(state.enabled) }
    var constant by rememberSaveable { mutableStateOf(state.constant) }
    var priority by rememberSaveable { mutableStateOf(state.priority) }
    var position by rememberSaveable { mutableStateOf(state.position) }
    var injectDepth by rememberSaveable { mutableStateOf(state.injectDepth) }
    var role by rememberSaveable { mutableStateOf(state.role) }
    var scanDepth by rememberSaveable { mutableStateOf(state.scanDepth) }
    var maxMatches by rememberSaveable { mutableStateOf(state.maxMatches) }
    val regexOk = !regexEnabled || splitKeys(keys)
        .plus(splitKeys(secondaryKeys))
        .plus(splitKeys(excludeKeys))
        .all { runCatching { Regex(it) }.isSuccess }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 14.dp)
    ) {
        WorldBookTopBar(
            title = if (state.id == null) "新增条目" else "编辑条目",
            subtitle = "关键词命中后注入内容",
            style = style,
            onBack = onBack
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LabeledSwitch("启用条目", enabled, style) { enabled = it }
                LabeledSwitch("常驻注入", constant, style) { constant = it }
                LabeledSwitch("关键词按正则匹配", regexEnabled, style) { regexEnabled = it }
                LabeledSwitch("区分大小写", caseSensitive, style) { caseSensitive = it }
                LabeledField("条目名称", title, style, onValueChange = { title = it })
                LabeledField("内容", content, style, minLines = 6, onValueChange = { content = it })
                LabeledField("关键词，逗号或换行分隔", keys, style, minLines = 2, onValueChange = { keys = it })
                LabeledField("二级关键词", secondaryKeys, style, minLines = 2, onValueChange = { secondaryKeys = it })
                LabeledField("排除关键词", excludeKeys, style, minLines = 2, onValueChange = { excludeKeys = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledSelect(
                        label = "插入位置",
                        value = position,
                        options = worldBookPositionOptions,
                        style = style,
                        modifier = Modifier.weight(1f),
                        onValueChange = { position = it }
                    )
                    LabeledSelect(
                        label = "消息角色",
                        value = role,
                        options = worldBookRoleOptions,
                        style = style,
                        modifier = Modifier.weight(1f),
                        onValueChange = { role = it }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField(
                        label = "优先级",
                        value = priority,
                        style = style,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        onValueChange = { priority = it.filter { ch -> ch.isDigit() || ch == '-' }.take(5) }
                    )
                    if (position == AiWorldBookEntry.POSITION_INJECT_DEPTH) {
                        LabeledField(
                            label = "插入深度",
                            value = injectDepth,
                            style = style,
                            modifier = Modifier.weight(1f),
                            keyboardType = KeyboardType.Number,
                            onValueChange = { injectDepth = it.filter(Char::isDigit).take(2) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField(
                        label = "扫描深度",
                        value = scanDepth,
                        style = style,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        onValueChange = { scanDepth = it.filter(Char::isDigit).take(2) }
                    )
                    LabeledField(
                        label = "最大命中数",
                        value = maxMatches,
                        style = style,
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        onValueChange = { maxMatches = it.filter(Char::isDigit).take(2) }
                    )
                }
                if (!regexOk) {
                    Text("正则表达式有错误，不能保存。", color = style.colors.danger, fontSize = 13.sp)
                }
                SaveBar(
                    enabled = title.trim().isNotBlank() && content.trim().isNotBlank() && regexOk,
                    style = style,
                    onCancel = onBack,
                    onSave = {
                        onSave(
                            state.copy(
                                title = title.trim(),
                                content = content.trim(),
                                keys = keys,
                                secondaryKeys = secondaryKeys,
                                excludeKeys = excludeKeys,
                                regexEnabled = regexEnabled,
                                caseSensitive = caseSensitive,
                                enabled = enabled,
                                constant = constant,
                                priority = priority.ifBlank { "50" },
                                position = position.ifBlank { AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT },
                                injectDepth = injectDepth.ifBlank { "4" },
                                role = role.ifBlank { AiWorldBookEntry.ROLE_USER },
                                scanDepth = scanDepth.ifBlank { "8" },
                                maxMatches = maxMatches.ifBlank { "1" }
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    style: AiComposeStyle,
    modifier: Modifier = Modifier.fillMaxWidth(),
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier
            .padding(top = 10.dp)
            .heightIn(min = if (minLines > 1) 96.dp else 56.dp)
    )
}

@Composable
private fun LabeledSelect(
    label: String,
    value: String,
    options: List<WorldBookSelectOption>,
    style: AiComposeStyle,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChange: (String) -> Unit
) {
    val selected = options.firstOrNull { it.value == value } ?: options.first()
    LegadoMiuixSelectField(
        label = label,
        options = options,
        selected = selected,
        optionLabel = { it.label },
        optionDescription = { it.description },
        onSelected = { onValueChange(it.value) },
        palette = style.toWorldBookMiuixPalette(),
        modifier = modifier.padding(top = 10.dp),
        cornerRadius = style.metrics.chipRadius
    )
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    style: AiComposeStyle,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = style.colors.primaryText, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SaveBar(
    enabled: Boolean,
    style: AiComposeStyle,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onCancel) {
            Text("取消", color = style.colors.secondaryText)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            enabled = enabled,
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = style.colors.accent)
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    style: AiComposeStyle,
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(style.metrics.chipRadius))
            .background(if (selected) style.colors.toolSurface else style.colors.processSurface)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = if (selected) style.colors.accent else style.colors.secondaryText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallAction(
    text: String,
    style: AiComposeStyle,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = if (danger) style.colors.danger else style.colors.accent,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(style.metrics.chipRadius))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 4.dp)
    )
}

private fun AiWorldBookConfig.globalBinding(): AiWorldBookBinding? {
    return bindings.firstOrNull { it.targetType == AiWorldBookBinding.TARGET_GLOBAL }
}

private fun AiWorldBookConfig.isGloballyEnabled(): Boolean {
    return enabled && globalBinding()?.enabled == true
}

private fun AiWorldBookConfig.activeBindingCount(): Int {
    return bindings.count { it.enabled }
}

private fun AiWorldBookConfig.toggleGlobalBinding(): AiWorldBookConfig {
    val oldBinding = globalBinding()
    val updatedBindings = if (oldBinding == null) {
        bindings + AiWorldBookBinding(
            targetType = AiWorldBookBinding.TARGET_GLOBAL,
            targetKey = "",
            enabled = true,
            order = bindings.size
        )
    } else {
        bindings.map { binding ->
            if (binding.id == oldBinding.id) {
                binding.copy(enabled = !oldBinding.enabled)
            } else {
                binding
            }
        }
    }
    return copy(
        enabled = true,
        bindingVersion = 1,
        bindings = updatedBindings
    )
}

private fun WorldBookEditState.toBook(
    old: AiWorldBookConfig?,
    order: Int
): AiWorldBookConfig {
    return (old ?: AiWorldBookConfig(name = name, order = order)).copy(
        name = name,
        description = description,
        enabled = enabled,
        bindingVersion = 1,
        maxEntries = maxEntries.toIntOrNull()?.coerceIn(1, 40) ?: 12
    )
}

private fun AiWorldBookConfig.toEditState(): WorldBookEditState {
    return WorldBookEditState(
        id = id,
        name = name,
        description = description,
        maxEntries = maxEntries.toString(),
        enabled = enabled
    )
}

private fun WorldBookEntryEditState.toEntry(book: AiWorldBookConfig): AiWorldBookEntry {
    val old = id?.let { targetId -> book.entries.firstOrNull { it.id == targetId } }
    val keywordList = splitKeys(keys)
    val safePosition = when (position) {
        AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT,
        AiWorldBookEntry.POSITION_BEFORE_PROMPT,
        AiWorldBookEntry.POSITION_INJECT_DEPTH,
        AiWorldBookEntry.POSITION_BEFORE_LAST_USER -> position
        else -> AiWorldBookEntry.POSITION_AFTER_SYSTEM_PROMPT
    }
    val safeRole = when (role) {
        AiWorldBookEntry.ROLE_SYSTEM,
        AiWorldBookEntry.ROLE_USER,
        AiWorldBookEntry.ROLE_ASSISTANT -> role
        else -> AiWorldBookEntry.ROLE_USER
    }
    return (old ?: AiWorldBookEntry(title = title, content = content, order = book.entries.size)).copy(
        title = title,
        name = title,
        content = content,
        keys = keywordList,
        keywords = keywordList,
        secondaryKeys = splitKeys(secondaryKeys),
        excludeKeys = splitKeys(excludeKeys),
        regexEnabled = regexEnabled,
        useRegex = regexEnabled,
        caseSensitive = caseSensitive,
        enabled = enabled,
        constant = constant,
        constantActive = constant,
        priority = priority.toIntOrNull()?.coerceIn(-9999, 9999) ?: 50,
        position = safePosition,
        injectDepth = injectDepth.toIntOrNull()?.coerceIn(0, 64) ?: 4,
        role = safeRole,
        scanDepth = scanDepth.toIntOrNull()?.coerceIn(1, 64) ?: 8,
        maxMatches = maxMatches.toIntOrNull()?.coerceIn(1, 20) ?: 1
    )
}

private fun AiWorldBookEntry.toEditState(worldBookId: String): WorldBookEntryEditState {
    return WorldBookEntryEditState(
        worldBookId = worldBookId,
        id = id,
        title = name.ifBlank { title },
        content = content,
        keys = keywords.ifEmpty { keys }.joinToString("\n"),
        secondaryKeys = secondaryKeys.joinToString("\n"),
        excludeKeys = excludeKeys.joinToString("\n"),
        regexEnabled = useRegex || regexEnabled,
        caseSensitive = caseSensitive,
        enabled = enabled,
        constant = constantActive || constant,
        priority = priority.toString(),
        position = position,
        injectDepth = injectDepth.toString(),
        role = role,
        scanDepth = scanDepth.toString(),
        maxMatches = maxMatches.toString()
    )
}

private fun splitKeys(value: String): List<String> {
    return value.split(',', '，', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(40)
}
