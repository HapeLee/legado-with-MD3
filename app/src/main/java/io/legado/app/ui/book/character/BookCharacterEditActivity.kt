package io.legado.app.ui.book.character

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.data.entities.AiImageGroup
import io.legado.app.data.entities.BookCharacter
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ItemAiGeneratedImageBinding
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.ai.AiImageGalleryManager.GalleryFilter
import io.legado.app.help.ai.AiImageService
import io.legado.app.help.character.BookCharacterIdentityMigrator
import io.legado.app.help.character.BookCharacterProfileMeta
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.readaloud.ReadAloudConfigChangeNotifier
import io.legado.app.help.readaloud.speech.SpeechVoiceCatalogRepository
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.book.character.compose.CharacterEditDraft
import io.legado.app.ui.book.character.compose.CharacterEditScreen
import io.legado.app.ui.book.character.compose.CharacterSpeechEngineUi
import io.legado.app.ui.book.character.compose.toDraft
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.externalFiles
import io.legado.app.utils.inputStream
import io.legado.app.utils.readUri
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

class BookCharacterEditActivity : BaseActivity<ViewBinding>(
    fullScreen = false,
    imageBg = false
) {

    private lateinit var composeView: ComposeView
    override val binding: ViewBinding by lazy {
        composeView = ComposeView(this)
        SimpleViewBinding(composeView)
    }

    private var bookUrl: String = ""
    private var characterBookKey: String = ""
    private var characterId: Long = 0L
    private var character = BookCharacter()
    private var draft by mutableStateOf(CharacterEditDraft())
    private var speechEngines by mutableStateOf<List<CharacterSpeechEngineUi>>(emptyList())
    private val waitDialog by lazy { WaitDialog(this) }

    private val selectAvatar = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let(::copyAvatar)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookUrl = intent.getStringExtra(BookCharacterManageActivity.EXTRA_BOOK_URL).orEmpty()
        characterBookKey = intent.getStringExtra(BookCharacterManageActivity.EXTRA_CHARACTER_BOOK_KEY).orEmpty()
        characterId = intent.getLongExtra(BookCharacterManageActivity.EXTRA_CHARACTER_ID, 0L)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            CharacterEditScreen(
                title = if (characterId > 0L) "编辑角色" else "添加角色",
                draft = draft,
                speechEngines = speechEngines,
                onDraftChange = { draft = it },
                onBack = ::finish,
                onSave = ::save,
                onPickLocalAvatar = {
                    selectAvatar.launch {
                        mode = HandleFileContract.IMAGE
                    }
                },
                onPickOnlineAvatar = ::showOnlineAvatarDialog,
                onPickGalleryAvatar = ::showGalleryAvatarSelector,
                onRegenerateAvatar = ::showRegenerateAvatarDialog,
                onClearAvatar = { draft = draft.copy(avatar = "") }
            )
        }
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            character = withContext(IO) {
                val loaded = appDb.bookCharacterDao.getCharacter(characterId)
                val key = characterBookKey.ifBlank {
                    loaded?.bookUrl ?: BookCharacterIdentityMigrator.migrate(appDb.bookDao.getBook(bookUrl))
                }
                characterBookKey = key
                loaded ?: BookCharacter(bookUrl = key)
            }
            speechEngines = withContext(IO) {
                SpeechVoiceCatalogRepository
                    .allGroups(applicationContext, appDb.httpTTSDao.all)
                    .map { CharacterSpeechEngineUi(it) }
            }
            draft = character.toDraft()
        }
    }

    private fun save() {
        val name = draft.name.trim()
        if (characterBookKey.isBlank()) {
            toastOnUi("当前书籍不存在")
            return
        }
        if (name.isBlank()) {
            toastOnUi("角色名称不能为空")
            return
        }
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val result = withContext(IO) {
                val duplicated = appDb.bookCharacterDao.getCharacter(characterBookKey, name)
                    ?.takeIf { it.id != character.id }
                if (duplicated != null) {
                    return@withContext false
                }
                val saving = character.copy(
                    bookUrl = characterBookKey,
                    name = name,
                    avatar = draft.avatar.trim(),
                    gender = BookCharacter.normalizeGender(draft.gender),
                    roleLevel = draft.roleLevel.coerceIn(
                        BookCharacter.ROLE_NORMAL,
                        BookCharacter.ROLE_MAIN
                    ),
                    identity = draft.identity.trim(),
                    skills = draft.skills.trim(),
                    attributes = BookCharacterProfileMeta.mergeAgeIntoAttributes(
                        draft.age,
                        draft.attributes
                    ),
                    appearance = draft.appearance.trim(),
                    personality = draft.personality.trim(),
                    biography = draft.biography.trim(),
                    speechRouteJson = draft.speechRouteJson.trim(),
                    sortOrder = character.sortOrder.takeIf { it > 0 }
                        ?: ((appDb.bookCharacterDao.maxCharacterOrder(characterBookKey) ?: -1) + 1),
                    createdAt = character.createdAt.takeIf { it > 0 } ?: now,
                    updatedAt = now
                )
                if (saving.id > 0) {
                    appDb.bookCharacterDao.updateCharacter(saving)
                } else {
                    appDb.bookCharacterDao.insertCharacter(saving)
                }
                true
            }
            if (!result) {
                toastOnUi("已存在同名角色")
                return@launch
            }
            ReadAloudConfigChangeNotifier.notifySpeech()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun showOnlineAvatarDialog() {
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = "输入在线图片链接"
            editView.setText(draft.avatar.takeIf { it.startsWith("http", ignoreCase = true) }.orEmpty())
        }
        alert("在线头像") {
            customView { dialogBinding.root }
            okButton {
                val value = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                if (value.isNotBlank()) {
                    draft = draft.copy(avatar = value)
                }
            }
            cancelButton()
        }
    }

    private fun showGalleryAvatarSelector() {
        lifecycleScope.launch {
            val data = withContext(IO) {
                AiImageGalleryManager.listGroups() to AiImageGalleryManager.listImages(GalleryFilter.ALL)
            }
            val groups = data.first
            val images = data.second
            showGalleryAvatarPicker(groups, images)
        }
    }

    private fun showGalleryAvatarPicker(groups: List<AiImageGroup>, images: List<AiGeneratedImage>) {
        if (images.isEmpty()) {
            toastOnUi("AI 图库暂无图片")
            return
        }
        var currentGroupId: String? = null
        var query = ""
        var dialog: AlertDialog? = null
        val adapter = GalleryAvatarAdapter { image ->
            setGalleryAvatar(image)
            dialog?.dismiss()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 8.dpToPx())
        }
        val search = EditText(this).apply {
            hint = getString(R.string.search)
            setSingleLine(true)
            textSize = 14f
            setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
            background = UiCorner.panelRounded(
                this@BookCharacterEditActivity,
                ContextCompat.getColor(this@BookCharacterEditActivity, R.color.background_card),
                UiCorner.actionRadius(this@BookCharacterEditActivity)
            )
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 42.dpToPx()))
        val chipContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipContainer)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx()))
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@BookCharacterEditActivity, 3)
            this.adapter = adapter
            overScrollMode = RecyclerView.OVER_SCROLL_NEVER
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        }
        root.addView(recyclerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420.dpToPx()))

        fun applyFilter() {
            adapter.setItems(images.filter { image ->
                val matchGroup = currentGroupId == null || image.groupId == currentGroupId
                val text = "${image.name}\n${image.prompt}\n${image.bookName}\n${image.chapterTitle}\n${image.characterName}"
                val matchQuery = query.isBlank() || text.contains(query, ignoreCase = true)
                matchGroup && matchQuery
            })
        }

        fun renderChips() {
            chipContainer.removeAllViews()
            fun addChip(text: String, groupId: String?) {
                val selected = currentGroupId == groupId
                val chip = TextView(this).apply {
                    this.text = text
                    gravity = Gravity.CENTER
                    minWidth = 62.dpToPx()
                    setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
                    setTextColor(if (selected) accentColor else secondaryTextColor)
                    background = UiCorner.actionSelector(
                        ContextCompat.getColor(this@BookCharacterEditActivity, if (selected) R.color.background_card else R.color.background_menu),
                        ContextCompat.getColor(this@BookCharacterEditActivity, R.color.background_card),
                        UiCorner.actionRadius(this@BookCharacterEditActivity)
                    )
                    setOnClickListener {
                        currentGroupId = groupId
                        renderChips()
                        applyFilter()
                    }
                }
                chipContainer.addView(chip, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    34.dpToPx()
                ).apply {
                    marginEnd = 8.dpToPx()
                })
            }
            addChip("全部", null)
            groups.forEach { addChip(it.name, it.id) }
        }

        renderChips()
        search.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            applyFilter()
        }
        applyFilter()
        dialog = alert("选择 AI 图库头像") {
            customView { root }
            cancelButton()
        }
    }

    private fun setGalleryAvatar(image: AiGeneratedImage) {
        lifecycleScope.launch {
            withContext(IO) {
                AiImageGalleryManager.setFavorite(image.id, true, null)
            }
            draft = draft.copy(avatar = AiImageGalleryManager.imageUri(image.id))
            toastOnUi("已设置角色头像")
        }
    }

    private fun showRegenerateAvatarDialog() {
        if (AppConfig.aiEnabledImageProviders.isEmpty()) {
            toastOnUi("未配置生图供应商")
            return
        }
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = "输入角色头像提示词"
            editView.minLines = 5
            editView.setText(buildAvatarPrompt())
        }
        alert("生成角色头像") {
            customView { dialogBinding.root }
            okButton {
                val prompt = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                if (prompt.isNotBlank()) generateAvatar(prompt)
            }
            cancelButton()
        }
    }

    private fun buildAvatarPrompt(): String {
        return buildList {
            add("为小说角色生成一张角色头像，头像构图，清晰，适合角色资料卡。")
            add("角色名：${draft.name.ifBlank { "未命名角色" }}")
            draft.identity.takeIf { it.isNotBlank() }?.let { add("身份：$it") }
            draft.skills.takeIf { it.isNotBlank() }?.let { add("技能：$it") }
            draft.attributes.takeIf { it.isNotBlank() }?.let { add("属性：$it") }
            draft.appearance.takeIf { it.isNotBlank() }?.let { add("形象：$it") }
            draft.personality.takeIf { it.isNotBlank() }?.let { add("性格：$it") }
            draft.biography.takeIf { it.isNotBlank() }?.let { add("生平：$it") }
        }.joinToString("\n")
    }

    private fun generateAvatar(prompt: String) {
        waitDialog.setText("正在生成角色头像...")
        waitDialog.show()
        lifecycleScope.launch {
            val result = withContext(IO) {
                runCatching {
                    val book = appDb.bookDao.getBook(bookUrl)
                    AiImageService.generateAndStore(
                        prompt,
                        metadata = AiImageGalleryManager.ImageMetadata(
                            bookName = book?.name.orEmpty(),
                            bookAuthor = book?.author.orEmpty(),
                            characterId = character.id,
                            characterName = draft.name,
                            sourceType = AiImageGalleryManager.SOURCE_TYPE_CHARACTER_AVATAR,
                            sourceText = prompt
                        )
                    ).also { image ->
                        AiImageGalleryManager.setFavorite(image.id, true, null)
                    }
                }
            }
            waitDialog.dismiss()
            result.onSuccess { image ->
                draft = draft.copy(avatar = AiImageGalleryManager.imageUri(image.id))
                toastOnUi("已生成并收藏角色头像")
            }.onFailure {
                toastOnUi("生成角色头像失败：${it.localizedMessage ?: it.javaClass.simpleName}")
            }
        }
    }

    private fun copyAvatar(uri: Uri) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            draft = draft.copy(avatar = uri.toString())
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            runCatching {
                inputStream.use {
                    val suffix = "." + fileDoc.name.substringAfterLast(".", "png")
                    val fileName = uri.inputStream(this).getOrThrow().use { stream ->
                        MD5Utils.md5Encode(stream) + suffix
                    }
                    val file = FileUtils.createFileIfNotExist(
                        externalFiles,
                        "bookCharacters",
                        "avatars",
                        fileName
                    )
                    FileOutputStream(file).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    draft = draft.copy(avatar = file.absolutePath)
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: "头像导入失败")
            }
        }
    }

    private inner class GalleryAvatarAdapter(
        private val onPick: (AiGeneratedImage) -> Unit
    ) : RecyclerAdapter<AiGeneratedImage, ItemAiGeneratedImageBinding>(this@BookCharacterEditActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemAiGeneratedImageBinding {
            return ItemAiGeneratedImageBinding.inflate(inflater, parent, false).apply {
                root.radius = UiCorner.scaledDp(12f)
                root.cardElevation = 0f
                root.setCardBackgroundColor(ContextCompat.getColor(root.context, R.color.background_card))
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiGeneratedImageBinding,
            item: AiGeneratedImage,
            payloads: MutableList<Any>
        ) = binding.run {
            ImageLoader.load(this@BookCharacterEditActivity, item.localPath)
                .error(R.drawable.image_loading_error)
                .into(ivImage)
            tvName.text = item.name
            tvPrompt.text = buildList {
                item.bookName.takeIf { it.isNotBlank() }?.let(::add)
                item.chapterTitle.takeIf { it.isNotBlank() }?.let(::add)
                item.characterName.takeIf { it.isNotBlank() }?.let(::add)
                add(item.prompt.replace(Regex("\\s+"), " ").take(48))
            }.joinToString(" · ")
            tvState.text = if (item.favorite) getString(R.string.in_favorites) else getString(R.string.ai_image_gallery_temporary)
            tvSelected.visibility = android.view.View.GONE
            tvName.applyUiSectionTitleStyle(this@BookCharacterEditActivity)
            tvPrompt.applyUiLabelStyle(this@BookCharacterEditActivity)
            tvPrompt.setTextColor(secondaryTextColor)
            tvState.applyUiLabelStyle(this@BookCharacterEditActivity)
            tvState.setTextColor(if (item.favorite) accentColor else ContextCompat.getColor(this@BookCharacterEditActivity, R.color.primaryText))
            tvState.background = UiCorner.actionSelector(
                ContextCompat.getColor(this@BookCharacterEditActivity, R.color.background_card),
                ContextCompat.getColor(this@BookCharacterEditActivity, R.color.background_menu),
                UiCorner.actionRadius(this@BookCharacterEditActivity)
            )
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiGeneratedImageBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let(onPick)
            }
        }
    }
}
