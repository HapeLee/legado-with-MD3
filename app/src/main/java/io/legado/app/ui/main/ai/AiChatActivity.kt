package io.legado.app.ui.main.ai

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.widget.compose.releaseComposeImage
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookCharacter
import io.legado.app.databinding.ActivityAiChatBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.book.characterBookKey
import io.legado.app.help.character.BookCharacterProfileMeta
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.config.AiWorldBookManageActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.book.character.BookCharacterEditActivity
import io.legado.app.ui.book.character.BookCharacterManageActivity
import io.legado.app.ui.main.ai.compose.AiChatRoute
import io.legado.app.ui.main.ai.compose.AiChatScreenActions
import io.legado.app.ui.main.ai.compose.aiComposeStyle
import io.legado.app.ui.book.character.compose.CharacterAvatar
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiChatActivity : BaseActivity<ActivityAiChatBinding>(
    fullScreen = false,
    imageBg = false
) {

    override val binding by viewBinding(ActivityAiChatBinding::inflate)

    private val viewModel by viewModels<AiChatViewModel>()
    private val historyTimeFormat by lazy { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    private val refreshToken = mutableIntStateOf(0)
    private var characterPickerGroups by mutableStateOf<List<CharacterPickGroup>>(emptyList())
    private var characterPickerVisible by mutableStateOf(false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeRoot.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeRoot.setContent {
            AiChatRoute(
                viewModel = viewModel,
                lifecycleOwner = this,
                compactHeader = false,
                refreshToken = refreshToken.intValue,
                actions = AiChatScreenActions(
                    onSend = ::dispatchSend,
                    onStop = ::cancelCurrentRequest,
                    onOpenSettings = ::openAiSettings,
                    onNewChat = ::startNewChatFromMenu,
                    onOpenHistory = ::openHistoryFromMenu,
                    onSelectModel = ::showModelSelectorDialog,
                    onOpenImageGallery = ::openImageGallery,
                    onOpenWindowAbilities = ::showWindowAbilityDialog,
                    onOpenWorldBooks = { showCompanionWorldBookDialog() },
                    onToggleAutoSpeak = ::toggleAutoSpeak,
                    onSpeakMessage = { text, companion, playbackKey ->
                        speakCompanionMessage(text, companion, playbackKey)
                    },
                    onAddCompanion = ::showAddCompanionDialog,
                    onSelectCompanion = ::selectCompanion,
                    onSelectSession = ::loadSessionFromDrawer,
                    onSelectCompanionSession = ::loadCompanionSessionFromDrawer,
                    onNewCompanionChat = ::startNewChatFromDrawer,
                    onDeleteSession = ::confirmDeleteHistorySession,
                    onCompanionLongPress = ::showCompanionActions
                )
            )
            if (characterPickerVisible) {
                AiCharacterCompanionPickerDialogV2(
                    groups = characterPickerGroups,
                    onDismiss = { characterPickerVisible = false },
                    onCharacterSelected = { group, character ->
                        characterPickerVisible = false
                        addCharacterCompanion(group, character)
                    }
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            AiImageGalleryManager.cleanupExpiredTemporary()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshToken.intValue += 1
    }

    override fun onDestroy() {
        AiChatSpeechPlayer.stop()
        super.onDestroy()
    }

    private fun dispatchSend(content: String): Boolean {
        if (content.isBlank() || viewModel.isRequesting) return false
        val provider = AppConfig.aiCurrentProvider
        if (provider?.baseUrl.isNullOrBlank() || AppConfig.aiCurrentModelConfig == null) {
            toastOnUi(R.string.ai_missing_config)
            return false
        }
        viewModel.startRequest(
            userContent = content.trim(),
            thinkingText = getString(R.string.ai_chat_thinking),
            cancelledText = getString(R.string.ai_chat_cancelled),
            failureMessage = { getString(R.string.ai_request_failed, it) }
        )
        return true
    }

    private fun cancelCurrentRequest() {
        viewModel.stopRequest(getString(R.string.ai_chat_cancelled))
    }

    private fun openHistoryFromMenu() {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        showHistoryDialog()
    }

    private fun startNewChatFromMenu() {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        viewModel.startNewSession()
        refreshToken.intValue += 1
    }

    private fun openAiSettings() {
        android.content.Intent(this, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
        }.also(::startActivity)
    }

    private fun openImageGallery() {
        startActivity(android.content.Intent(this, AiImageGalleryActivity::class.java))
    }

    private fun selectCompanion(companionId: String) {
        if (!viewModel.switchCompanion(companionId)) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        refreshToken.intValue += 1
    }

    private fun startNewChatFromDrawer(companionId: String) {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        viewModel.startNewSession(companionId)
        refreshToken.intValue += 1
    }

    private fun loadSessionFromDrawer(sessionId: String) {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        viewModel.loadSession(sessionId)
        refreshToken.intValue += 1
    }

    private fun loadCompanionSessionFromDrawer(companionId: String, sessionId: String) {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        if (!viewModel.loadSession(companionId, sessionId)) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        refreshToken.intValue += 1
    }

    private fun toggleAutoSpeak() {
        AppConfig.aiChatAutoSpeakEnabled = !AppConfig.aiChatAutoSpeakEnabled
        refreshToken.intValue += 1
    }

    private fun speakCompanionMessage(
        text: String,
        companion: AiChatCompanionConfig,
        playbackKey: String
    ) {
        if (AiChatSpeechPlayer.stopIfActive(playbackKey)) {
            return
        }
        lifecycleScope.launch {
            val routeJson = withContext(Dispatchers.IO) {
                resolveCompanionSpeechRouteJson(companion)
            }
            AiChatSpeechPlayer.speak(text, routeJson, playbackKey)
        }
    }

    private fun resolveCompanionSpeechRouteJson(companion: AiChatCompanionConfig): String {
        if (companion.type != AiChatCompanionConfig.TYPE_CHARACTER) {
            return companion.ttsRouteJson
        }
        val characterId = companion.characterId.toLongOrNull() ?: return companion.ttsRouteJson
        val character = appDb.bookCharacterDao.getCharacter(characterId) ?: return companion.ttsRouteJson
        if (companion.bookKey.isNotBlank() && character.bookUrl != companion.bookKey) {
            return companion.ttsRouteJson
        }
        return character.speechRouteJson.ifBlank { companion.ttsRouteJson }
    }

    private fun showAddCompanionDialog() {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) {
                val books = appDb.bookDao.all
                val booksByKey = books.associateBy { it.characterBookKey() }
                appDb.bookCharacterDao.allCharacters()
                    .filter { it.name.isNotBlank() }
                    .groupBy { character -> character.bookUrl }
                    .mapNotNull { (bookKey, characters) ->
                        val book = booksByKey[bookKey] ?: return@mapNotNull null
                        CharacterPickGroup(
                            bookKey = bookKey,
                            bookName = book.name,
                            author = book.author,
                            coverUrl = book.getDisplayCover().orEmpty(),
                            label = "${book.name} · ${book.author.ifBlank { "未知作者" }}",
                            characters = characters.sortedWith(
                                compareByDescending<BookCharacter> { it.roleLevel }
                                    .thenBy { it.sortOrder }
                                    .thenBy { it.id }
                            )
                        )
                    }
                    .filter { it.characters.isNotEmpty() }
                    .sortedBy { it.label }
            }
            if (groups.isEmpty()) {
                toastOnUi("没有可添加的角色卡")
                return@launch
            }
            characterPickerGroups = groups
            characterPickerVisible = true
        }
    }

    private fun addCharacterCompanion(group: CharacterPickGroup, character: BookCharacter) {
        val companionId = characterCompanionId(character)
        val existing = AppConfig.aiChatCompanionList.firstOrNull { it.id == companionId }
        if (existing != null) {
            viewModel.switchCompanion(existing.id)
            refreshToken.intValue += 1
            return
        }
        val companion = AiChatCompanionConfig(
            id = companionId,
            type = AiChatCompanionConfig.TYPE_CHARACTER,
            name = character.displayName(),
            avatar = character.avatar,
            bookKey = group.bookKey,
            characterId = character.id.toString(),
            prompt = "",
            ttsRouteJson = character.speechRouteJson,
            order = AppConfig.aiChatCompanionList.size
        )
        AppConfig.upsertAiChatCompanion(companion)
        viewModel.switchCompanion(companion.id)
        refreshToken.intValue += 1
    }

    private fun showCompanionActions(companion: AiChatCompanionConfig) {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        val isDefault = companion.id == AiChatCompanionConfig.DEFAULT_COMPANION_ID
        val items = if (isDefault) {
            listOf("新建对话", "编辑默认助手人格", "世界书")
        } else {
            listOf("编辑角色卡", "新建对话", "世界书", "移除角色助手")
        }
        selector(companion.name.ifBlank { "助手" }, items) { _, _, index ->
            if (isDefault) {
                when (index) {
                    0 -> startNewChatForCompanion(companion.id)
                    1 -> showDefaultCompanionPromptDialog(companion)
                    2 -> showCompanionWorldBookDialog(companion)
                }
            } else {
                when (index) {
                    0 -> openCharacterEditor(companion)
                    1 -> startNewChatForCompanion(companion.id)
                    2 -> showCompanionWorldBookDialog(companion)
                    3 -> confirmDeleteCompanion(companion)
                }
            }
        }
    }

    private fun startNewChatForCompanion(companionId: String) {
        if (!viewModel.switchCompanion(companionId)) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        viewModel.startNewSession()
        refreshToken.intValue += 1
    }

    private fun openCharacterEditor(companion: AiChatCompanionConfig) {
        val characterId = companion.characterId.toLongOrNull()
        if (characterId == null || characterId <= 0L) {
            toastOnUi("角色卡不存在")
            return
        }
        lifecycleScope.launch {
            val bookUrl = withContext(Dispatchers.IO) {
                appDb.bookDao.all.firstOrNull { book ->
                    book.characterBookKey() == companion.bookKey
                }?.bookUrl.orEmpty()
            }
            startActivity(
                Intent(this@AiChatActivity, BookCharacterEditActivity::class.java).apply {
                    putExtra(BookCharacterManageActivity.EXTRA_BOOK_URL, bookUrl)
                    putExtra(BookCharacterManageActivity.EXTRA_CHARACTER_BOOK_KEY, companion.bookKey)
                    putExtra(BookCharacterManageActivity.EXTRA_CHARACTER_ID, characterId)
                }
            )
        }
    }

    private fun showDefaultCompanionPromptDialog(companion: AiChatCompanionConfig) {
        val binding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.setSingleLine(false)
            editView.minLines = 8
            editView.inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            editView.setText(companion.prompt)
        }
        alert(title = "${companion.name} · 人格") {
            customView { binding.root }
            okButton {
                val prompt = binding.editView.text?.toString().orEmpty().trim()
                if (prompt.isBlank()) {
                    toastOnUi("提示词不能为空")
                } else {
                    AppConfig.upsertAiChatCompanion(companion.copy(prompt = prompt))
                    refreshToken.intValue += 1
                }
            }
            cancelButton()
        }
    }

    private fun confirmDeleteCompanion(companion: AiChatCompanionConfig) {
        if (companion.id == AiChatCompanionConfig.DEFAULT_COMPANION_ID) {
            toastOnUi("默认助手不能删除")
            return
        }
        alert(
            title = "删除角色助手",
            message = "确定删除「${companion.name}」？它的聊天历史也会删除。"
        ) {
            okButton {
                AppConfig.removeAiChatCompanion(companion.id)
                viewModel.switchCompanion(AiChatCompanionConfig.DEFAULT_COMPANION_ID)
                refreshToken.intValue += 1
            }
            cancelButton()
        }
    }

    private fun showCompanionWorldBookDialog(companion: AiChatCompanionConfig = viewModel.currentCompanion()) {
        val worldBooks = AppConfig.aiWorldBookList
        if (worldBooks.isEmpty()) {
            selector("世界书", listOf("打开世界书管理")) { _, _, _ ->
                openWorldBookManage()
            }
            return
        }
        val visibleWorldBooks = worldBooks.filter { it.enabled && !it.isGlobalWorldBookEnabled() }
        if (visibleWorldBooks.isEmpty()) {
            selector("世界书", listOf("没有可单独绑定到角色的世界书", "打开世界书管理")) { _, _, which ->
                if (which == 1) openWorldBookManage()
            }
            return
        }
        val visibleIds = visibleWorldBooks.map { it.id }.toSet()
        val selected = companion.worldBookIds.filterTo(mutableSetOf()) { it in visibleIds }
        alert(title = "${companion.name} · 世界书") {
            multiChoiceItems(
                items = visibleWorldBooks.map { book ->
                    buildString {
                        append(book.name)
                        if (book.bindings.any { it.enabled && it.targetType == AiWorldBookBinding.TARGET_GLOBAL }) {
                            append("（全局）")
                        }
                        if (!book.enabled) {
                            append("（资料库停用）")
                        }
                    }
                }.toTypedArray(),
                checkedItems = BooleanArray(visibleWorldBooks.size) { index ->
                    visibleWorldBooks[index].id in selected
                }
            ) { _, which, isChecked ->
                if (isChecked) selected += visibleWorldBooks[which].id else selected -= visibleWorldBooks[which].id
            }
            okButton {
                AppConfig.upsertAiChatCompanion(
                    companion.copy(worldBookIds = selected.filter { id -> id in visibleIds })
                )
                refreshToken.intValue += 1
            }
            neutralButton("管理") { openWorldBookManage() }
            cancelButton()
        }
    }

    private fun showWindowAbilityDialog() {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        selector(
            "当前窗口能力",
            listOf(
                "Skill：${viewModel.activeWindowSkillIds().size} 个",
                "MCP：${viewModel.activeWindowMcpServerIds().size} 个",
                "世界书：${activeCompanionWorldBookCount()} 个",
                "清空 Skill/MCP"
            )
        ) { _, _, index ->
            when (index) {
                0 -> showWindowSkillDialog()
                1 -> showWindowMcpDialog()
                2 -> showCompanionWorldBookDialog()
                3 -> {
                    viewModel.setActiveWindowSkillIds(emptySet())
                    viewModel.setActiveWindowMcpServerIds(emptySet())
                    refreshToken.intValue += 1
                }
            }
        }
    }

    private fun openWorldBookManage() {
        startActivity(Intent(this, AiWorldBookManageActivity::class.java))
    }

    private fun activeCompanionWorldBookCount(): Int {
        val companion = viewModel.currentCompanion()
        return companion.worldBookIds.count { worldBookId ->
            AppConfig.aiWorldBookList.any {
                it.id == worldBookId && it.enabled && !it.isGlobalWorldBookEnabled()
            }
        }
    }

    private fun showWindowSkillDialog() {
        val skills = AppConfig.aiSkillList
        if (skills.isEmpty()) {
            toastOnUi("没有可用 Skill")
            return
        }
        val selected = viewModel.activeWindowSkillIds().toMutableSet()
        alert(title = "当前窗口 Skill") {
            multiChoiceItems(
                items = skills.map { skill -> skill.name.ifBlank { "Skill" } }.toTypedArray(),
                checkedItems = BooleanArray(skills.size) { index -> skills[index].id in selected }
            ) { _, which, isChecked ->
                if (isChecked) selected += skills[which].id else selected -= skills[which].id
            }
            okButton {
                viewModel.setActiveWindowSkillIds(selected)
                refreshToken.intValue += 1
            }
            neutralButton("清空") {
                viewModel.setActiveWindowSkillIds(emptySet())
                refreshToken.intValue += 1
            }
            cancelButton()
        }
    }

    private fun showWindowMcpDialog() {
        val servers = AppConfig.aiMcpServerList.filter { it.enabled }
        if (servers.isEmpty()) {
            toastOnUi("没有已启用 MCP")
            return
        }
        val selected = viewModel.activeWindowMcpServerIds().toMutableSet()
        alert(title = "当前窗口 MCP") {
            multiChoiceItems(
                items = servers.map { server -> server.name.ifBlank { "MCP" } }.toTypedArray(),
                checkedItems = BooleanArray(servers.size) { index -> servers[index].id in selected }
            ) { _, which, isChecked ->
                if (isChecked) selected += servers[which].id else selected -= servers[which].id
            }
            okButton {
                viewModel.setActiveWindowMcpServerIds(selected)
                refreshToken.intValue += 1
            }
            neutralButton("清空") {
                viewModel.setActiveWindowMcpServerIds(emptySet())
                refreshToken.intValue += 1
            }
            cancelButton()
        }
    }

    private fun showHistoryDialog() {
        val sessions = viewModel.historySessions()
        if (sessions.isEmpty()) {
            toastOnUi(R.string.ai_history_empty)
            return
        }
        val items = mutableListOf(getString(R.string.ai_history_clear_all))
        items += sessions.map { session ->
            "${session.title}\n${historyTimeFormat.format(Date(session.updatedAt))}"
        }
        selector(getString(R.string.ai_chat_history), items) { _, _, index ->
            if (index == 0) {
                confirmClearAllHistory()
            } else {
                showHistorySessionActions(sessions[index - 1])
            }
        }
    }

    private fun showHistorySessionActions(session: AiChatSession) {
        selector(
            session.title,
            listOf(
                getString(R.string.ai_history_open),
                getString(R.string.ai_history_delete)
            )
        ) { _, _, index ->
            when (index) {
                0 -> {
                    viewModel.loadSession(session.id)
                    refreshToken.intValue += 1
                }
                1 -> confirmDeleteHistorySession(session)
            }
        }
    }

    private fun confirmDeleteHistorySession(session: AiChatSession) {
        alert(
            title = getString(R.string.ai_history_delete),
            message = getString(R.string.ai_history_delete_confirm, session.title)
        ) {
            okButton {
                viewModel.deleteSession(session.id)
                refreshToken.intValue += 1
            }
            cancelButton()
        }
    }

    private fun confirmClearAllHistory() {
        alert(
            title = getString(R.string.ai_history_clear_all),
            message = getString(R.string.ai_history_clear_all_confirm)
        ) {
            okButton {
                viewModel.clearAllSessions()
                refreshToken.intValue += 1
            }
            cancelButton()
        }
    }

    private fun showModelSelectorDialog() {
        if (viewModel.isRequesting) {
            toastOnUi(R.string.ai_chat_wait_current)
            return
        }
        val models = AppConfig.aiModelConfigList
        if (models.isEmpty()) {
            toastOnUi(R.string.ai_no_models)
            return
        }
        val providerNameMap = AppConfig.aiProviderList.associateBy({ it.id }, { it.name })
        selector(
            getString(R.string.ai_current_model),
            models.map { model ->
                providerNameMap[model.providerId]?.takeIf { it.isNotBlank() }
                    ?.let { "${model.modelId} · $it" }
                    ?: model.modelId
            }
        ) { _, _, index ->
            AppConfig.aiCurrentModelId = models[index].id
            refreshToken.intValue += 1
        }
    }

    private fun characterCompanionId(character: BookCharacter): String {
        return "character_${character.id}"
    }

    @Composable
    private fun AiCharacterCompanionPickerDialogV2(
        groups: List<CharacterPickGroup>,
        onDismiss: () -> Unit,
        onCharacterSelected: (CharacterPickGroup, BookCharacter) -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        var query by remember(groups) { mutableStateOf("") }
        var selectedGroup by remember(groups) { mutableStateOf<CharacterPickGroup?>(null) }
        var selectedRole by remember(groups) { mutableIntStateOf(BookCharacter.ROLE_MAIN) }
        val filteredGroups = remember(groups, query) {
            val keyword = query.trim()
            if (keyword.isBlank()) {
                groups
            } else {
                groups.filter { group ->
                    group.bookName.contains(keyword, ignoreCase = true) ||
                        group.author.contains(keyword, ignoreCase = true) ||
                        group.label.contains(keyword, ignoreCase = true) ||
                        group.characters.any { character ->
                            character.displayName().contains(keyword, ignoreCase = true)
                        }
                }
            }
        }
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(style.metrics.cardRadius),
                color = style.colors.composerSurface,
                tonalElevation = 0.dp,
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
                    .heightIn(max = 720.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "添加角色助手",
                                color = style.colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = selectedGroup?.let { "从《${it.bookName.ifBlank { it.label }}》选择角色" }
                                    ?: "先选择书籍，再选择要添加到侧边栏的角色",
                                color = style.colors.secondaryText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "关闭",
                            color = style.colors.accent,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(style.metrics.chipRadius))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    CharacterPickerSearchFieldV2(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedGroup = null
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    val current = selectedGroup
                    if (current == null) {
                        Text(
                            text = "选择书籍",
                            color = style.colors.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (filteredGroups.isEmpty()) {
                            CharacterPickerEmptyV2("没有匹配的角色书籍")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 460.dp),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                gridItems(filteredGroups, key = { it.bookKey }) { group ->
                                    CharacterBookGridCardV2(
                                        group = group,
                                        onClick = {
                                            selectedGroup = group
                                            selectedRole = bestRoleFor(group)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        CharacterSelectedBookCardV2(
                            group = current,
                            onChangeBook = { selectedGroup = null }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val addedCharacterIds = remember(current.bookKey, current.characters.size) {
                            AppConfig.aiChatCompanionList
                                .mapNotNull { it.characterId.toLongOrNull() }
                                .toSet()
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            items(characterRoleFilters(current), key = { it.roleLevel }) { filter ->
                                CharacterRoleFilterChipV2(
                                    filter = filter,
                                    selected = filter.roleLevel == selectedRole,
                                    onClick = { selectedRole = filter.roleLevel }
                                )
                            }
                        }
                        val filteredCharacters = current.characters.filter { character ->
                            characterMatchesRole(character, selectedRole)
                        }
                        Text(
                            text = "选择角色",
                            color = style.colors.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCharacters, key = { it.id }) { character ->
                                CharacterPickRowV2(
                                    character = character,
                                    added = character.id in addedCharacterIds,
                                    onClick = {
                                        onCharacterSelected(current, character)
                                        onDismiss()
                                    }
                                )
                            }
                            if (filteredCharacters.isEmpty()) {
                                item {
                                    CharacterPickerEmptyV2("这一组还没有角色")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CharacterPickerSearchFieldV2(
        value: String,
        onValueChange: (String) -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isBlank()) {
                    Text(
                        text = "搜索书名、作者或角色",
                        color = style.colors.secondaryText.copy(alpha = 0.72f),
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = style.colors.primaryText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    @Composable
    private fun CharacterPickerEmptyV2(text: String) {
        val style = aiComposeStyle(this@AiChatActivity)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, color = style.colors.secondaryText, fontSize = 14.sp)
        }
    }

    @Composable
    private fun CharacterBookGridCardV2(
        group: CharacterPickGroup,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(style.metrics.chipRadius)),
                    factory = {
                        CoverImageView(it).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = {
                        it.load(
                            path = group.coverUrl,
                            name = group.bookName.ifBlank { group.label },
                            author = group.author,
                            loadOnlyWifi = false,
                            preferThumb = true
                        )
                    },
                    onRelease = { it.releaseComposeImage() }
                )
                Text(
                    text = group.bookName.ifBlank { group.label },
                    color = style.colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    text = "${group.characters.size} 个角色",
                    color = style.colors.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun CharacterSelectedBookCardV2(
        group: CharacterPickGroup,
        onChangeBook: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    modifier = Modifier
                        .width(52.dp)
                        .height(72.dp)
                        .clip(RoundedCornerShape(style.metrics.chipRadius)),
                    factory = {
                        CoverImageView(it).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = {
                        it.load(
                            path = group.coverUrl,
                            name = group.bookName.ifBlank { group.label },
                            author = group.author,
                            loadOnlyWifi = false,
                            preferThumb = true
                        )
                    },
                    onRelease = { it.releaseComposeImage() }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = group.bookName.ifBlank { group.label },
                        color = style.colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.author.ifBlank { "未知作者" }} · ${group.characters.size} 个角色",
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Text(
                    text = "换书",
                    color = style.colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(style.metrics.chipRadius))
                        .clickable(onClick = onChangeBook)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }
    }

    @Composable
    private fun CharacterRoleFilterChipV2(
        filter: CharacterRoleFilter,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.chipRadius),
            color = if (selected) style.colors.accent.copy(alpha = 0.14f) else style.colors.cardSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = "${filter.label} ${filter.count}",
                color = if (selected) style.colors.accent else style.colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }

    @Composable
    private fun CharacterPickRowV2(
        character: BookCharacter,
        added: Boolean,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CharacterAvatar(
                    path = character.avatar,
                    contentDescription = character.displayName(),
                    sizeDp = 42,
                    modifier = Modifier.clip(CircleShape)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = character.displayName(),
                        color = style.colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(character.roleLabel(), character.genderLabel(), BookCharacterProfileMeta.ageOf(character))
                            .filter { it.isNotBlank() && it != "未知" }
                            .joinToString(" · ")
                            .ifBlank { "角色卡" },
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val summary = characterCardSummary(character)
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            color = style.colors.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Text(
                    text = if (added) "打开" else "添加",
                    color = if (added) style.colors.secondaryText else style.colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }

    @Composable
    private fun AiCharacterCompanionPickerDialog(
        groups: List<CharacterPickGroup>,
        onDismiss: () -> Unit,
        onCharacterSelected: (CharacterPickGroup, BookCharacter) -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        var selectedGroup by remember(groups) { mutableStateOf(groups.firstOrNull()) }
        var selectedRole by remember(groups) {
            mutableIntStateOf(bestRoleFor(groups.firstOrNull()))
        }
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(style.metrics.cardRadius),
                color = style.colors.pageBackground,
                border = BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "添加角色助手",
                                color = style.colors.primaryText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "从角色卡创建聊天对象，长按侧边栏角色可编辑角色卡",
                                color = style.colors.secondaryText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "关闭",
                            color = style.colors.accent,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(style.metrics.chipRadius))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(groups, key = { it.bookKey }) { group ->
                            CharacterBookPickCard(
                                group = group,
                                selected = group.bookKey == selectedGroup?.bookKey,
                                onClick = {
                                    selectedGroup = group
                                    selectedRole = bestRoleFor(group)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val current = selectedGroup
                    if (current == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有可添加的角色卡", color = style.colors.secondaryText, fontSize = 14.sp)
                        }
                    } else {
                        val addedCharacterIds = remember(current.bookKey, current.characters.size) {
                            AppConfig.aiChatCompanionList
                                .mapNotNull { it.characterId.toLongOrNull() }
                                .toSet()
                        }
                        val roleFilters = characterRoleFilters(current)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            items(roleFilters, key = { it.roleLevel }) { filter ->
                                CharacterRoleFilterChip(
                                    filter = filter,
                                    selected = filter.roleLevel == selectedRole,
                                    onClick = { selectedRole = filter.roleLevel }
                                )
                            }
                        }
                        val filteredCharacters = current.characters.filter { character ->
                            characterMatchesRole(character, selectedRole)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredCharacters, key = { it.id }) { character ->
                                CharacterPickRow(
                                    character = character,
                                    added = character.id in addedCharacterIds,
                                    onClick = { onCharacterSelected(current, character) }
                                )
                            }
                            if (filteredCharacters.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("这一组还没有角色", color = style.colors.secondaryText, fontSize = 13.sp)
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
    private fun CharacterBookPickCard(
        group: CharacterPickGroup,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.chipRadius),
            color = if (selected) style.colors.accent.copy(alpha = 0.12f) else style.colors.cardSurface,
            border = BorderStroke(
                style.metrics.strokeWidth,
                if (selected) style.colors.accent.copy(alpha = 0.42f) else style.colors.stroke
            ),
            modifier = Modifier
                .width(132.dp)
                .clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(style.metrics.chipRadius)),
                    factory = {
                        CoverImageView(it).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                    update = {
                        it.load(
                            path = group.coverUrl,
                            name = group.bookName.ifBlank { group.label },
                            author = group.author,
                            loadOnlyWifi = false,
                            preferThumb = true
                        )
                    },
                    onRelease = { it.releaseComposeImage() }
                )
                Text(
                    text = group.bookName.ifBlank { group.label },
                    color = style.colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp)
                )
                Text(
                    text = "${group.characters.size} 个角色",
                    color = if (selected) style.colors.accent else style.colors.secondaryText,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun CharacterRoleFilterChip(
        filter: CharacterRoleFilter,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.chipRadius),
            color = if (selected) style.colors.accent.copy(alpha = 0.14f) else style.colors.cardSurface,
            border = BorderStroke(
                style.metrics.strokeWidth,
                if (selected) style.colors.accent.copy(alpha = 0.42f) else style.colors.stroke
            ),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Text(
                text = "${filter.label} ${filter.count}",
                color = if (selected) style.colors.accent else style.colors.secondaryText,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }

    @Composable
    private fun CharacterPickRow(
        character: BookCharacter,
        added: Boolean,
        onClick: () -> Unit
    ) {
        val style = aiComposeStyle(this@AiChatActivity)
        Surface(
            shape = RoundedCornerShape(style.metrics.cardRadius),
            color = style.colors.cardSurface,
            border = BorderStroke(style.metrics.strokeWidth, style.colors.stroke),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CharacterAvatar(
                    path = character.avatar,
                    contentDescription = character.displayName(),
                    sizeDp = 42,
                    modifier = Modifier.clip(CircleShape)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                ) {
                    Text(
                        text = character.displayName(),
                        color = style.colors.primaryText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOf(character.roleLabel(), character.genderLabel(), BookCharacterProfileMeta.ageOf(character))
                            .filter { it.isNotBlank() && it != "未知" }
                            .joinToString(" · ")
                            .ifBlank { "角色卡" },
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val summary = characterCardSummary(character)
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            color = style.colors.secondaryText,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Text(
                    text = if (added) "打开" else "添加",
                    color = if (added) style.colors.secondaryText else style.colors.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }

    private fun bestRoleFor(group: CharacterPickGroup?): Int {
        val characters = group?.characters.orEmpty()
        return when {
            characters.any { it.roleLevel == BookCharacter.ROLE_MAIN } -> BookCharacter.ROLE_MAIN
            characters.any { it.roleLevel == BookCharacter.ROLE_IMPORTANT } -> BookCharacter.ROLE_IMPORTANT
            else -> BookCharacter.ROLE_NORMAL
        }
    }

    private fun characterRoleFilters(group: CharacterPickGroup): List<CharacterRoleFilter> {
        return listOf(
            CharacterRoleFilter(
                roleLevel = BookCharacter.ROLE_MAIN,
                label = "主角",
                count = group.characters.count { it.roleLevel == BookCharacter.ROLE_MAIN }
            ),
            CharacterRoleFilter(
                roleLevel = BookCharacter.ROLE_IMPORTANT,
                label = "重要",
                count = group.characters.count { it.roleLevel == BookCharacter.ROLE_IMPORTANT }
            ),
            CharacterRoleFilter(
                roleLevel = BookCharacter.ROLE_NORMAL,
                label = "普通",
                count = group.characters.count { characterMatchesRole(it, BookCharacter.ROLE_NORMAL) }
            )
        )
    }

    private fun characterMatchesRole(character: BookCharacter, roleLevel: Int): Boolean {
        return when (roleLevel) {
            BookCharacter.ROLE_MAIN -> character.roleLevel == BookCharacter.ROLE_MAIN
            BookCharacter.ROLE_IMPORTANT -> character.roleLevel == BookCharacter.ROLE_IMPORTANT
            else -> character.roleLevel != BookCharacter.ROLE_MAIN &&
                    character.roleLevel != BookCharacter.ROLE_IMPORTANT
        }
    }

    private fun characterCardSummary(character: BookCharacter): String {
        return listOf(
            character.identity,
            character.appearance,
            character.personality,
            character.biography
        )
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun AiWorldBookConfig.isGlobalWorldBookEnabled(): Boolean {
        return enabled && bindings.any { binding ->
            binding.enabled && binding.targetType == AiWorldBookBinding.TARGET_GLOBAL
        }
    }

    private data class CharacterRoleFilter(
        val roleLevel: Int,
        val label: String,
        val count: Int
    )

    private data class CharacterPickGroup(
        val bookKey: String,
        val bookName: String,
        val author: String,
        val coverUrl: String,
        val label: String,
        val characters: List<BookCharacter>
    )
}
