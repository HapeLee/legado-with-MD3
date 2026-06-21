package io.legado.app.ui.main.ai

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.data.entities.AiImageGroup
import io.legado.app.databinding.ActivityAiImageGalleryBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.ItemAiGeneratedImageBinding
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.ai.AiImageGalleryManager.GalleryFilter
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiImageGalleryActivity : BaseActivity<ActivityAiImageGalleryBinding>() {

    override val binding by viewBinding(ActivityAiImageGalleryBinding::inflate)
    private val adapter by lazy { Adapter() }
    private val selectedIds = linkedSetOf<String>()
    private var currentFilter: GalleryFilter = GalleryFilter.ALL
    private var fixedBookKey: String = ""
    private var fixedTitle: String = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        fixedBookKey = intent.getStringExtra(EXTRA_BOOK_KEY).orEmpty()
        fixedTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (fixedBookKey.isNotBlank()) {
            currentFilter = GalleryFilter.BOOK(fixedBookKey)
        }
        binding.titleBar.title = fixedTitle.ifBlank { getString(R.string.ai_image_gallery) }
        binding.etSearch.background = UiCorner.panelRounded(
            this,
            ContextCompat.getColor(this, R.color.background_card),
            UiCorner.actionRadius(this)
        )
        binding.etSearch.doAfterTextChanged { reload() }
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = adapter
        (binding.recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recyclerView.setPadding(10.dpToPx(), 0, 10.dpToPx(), 16.dpToPx())
        val actionBackground = UiCorner.actionSelector(
            ContextCompat.getColor(this, R.color.background_card),
            ContextCompat.getColor(this, R.color.background_menu),
            UiCorner.actionRadius(this)
        )
        listOf(
            binding.btnBatchSelectAll,
            binding.btnBatchGroup,
            binding.btnBatchDelete,
            binding.btnBatchCancel
        ).forEach {
            it.background = actionBackground
        }
        binding.btnBatchSelectAll.setOnClickListener { selectAllVisibleImages() }
        binding.btnBatchGroup.setOnClickListener { showBatchGroupDialog() }
        binding.btnBatchDelete.setOnClickListener { confirmBatchDelete() }
        binding.btnBatchCancel.setOnClickListener { clearSelection() }
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val query = binding.etSearch.text?.toString().orEmpty().trim()
            val data = withContext(Dispatchers.IO) {
                AiImageGalleryManager.cleanupExpiredTemporary()
                val groups = AiImageGalleryManager.listGroups()
                val images = when {
                    query.isNotBlank() -> AiImageGalleryManager.listImages(GalleryFilter.SEARCH(query))
                        .let { list -> if (fixedBookKey.isBlank()) list else list.filter { it.bookKey == fixedBookKey } }
                    else -> AiImageGalleryManager.listImages(currentFilter)
                }
                groups to images
            }
            selectedIds.retainAll(data.second.map { it.id }.toSet())
            renderFilters(data.first)
            adapter.setItems(data.second)
            binding.recyclerView.isVisible = data.second.isNotEmpty()
            binding.tvEmpty.isVisible = data.second.isEmpty()
            updateBatchBar()
        }
    }

    private fun renderFilters(groups: List<AiImageGroup>) {
        binding.filterContainer.removeAllViews()
        addFilterChip(getString(R.string.ai_image_gallery_all), currentFilter == GalleryFilter.ALL) {
            currentFilter = GalleryFilter.ALL
            reload()
        }
        addFilterChip(getString(R.string.ai_image_gallery_temporary), currentFilter == GalleryFilter.TEMPORARY) {
            currentFilter = GalleryFilter.TEMPORARY
            reload()
        }
        addFilterChip(getString(R.string.favorites), currentFilter == GalleryFilter.FAVORITE) {
            currentFilter = GalleryFilter.FAVORITE
            reload()
        }
        if (fixedBookKey.isNotBlank()) {
            addFilterChip("本书", currentFilter == GalleryFilter.BOOK(fixedBookKey)) {
                currentFilter = GalleryFilter.BOOK(fixedBookKey)
                reload()
            }
        }
        addFilterChip("角色图", currentFilter == GalleryFilter.SOURCE_TYPE(AiImageGalleryManager.SOURCE_TYPE_CHARACTER_AVATAR)) {
            currentFilter = GalleryFilter.SOURCE_TYPE(AiImageGalleryManager.SOURCE_TYPE_CHARACTER_AVATAR)
            reload()
        }
        groups.forEach { group ->
            addFilterChip(group.name, currentFilter == GalleryFilter.GROUP(group.id)) {
                currentFilter = GalleryFilter.GROUP(group.id)
                reload()
            }
        }
    }

    private fun addFilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
        val chip = TextView(this).apply {
            this.text = text
            minHeight = 34.dpToPx()
            minWidth = 64.dpToPx()
            gravity = android.view.Gravity.CENTER
            setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
            setTextColor(if (selected) accentColor else secondaryTextColor)
            background = UiCorner.actionSelector(
                ContextCompat.getColor(this@AiImageGalleryActivity, if (selected) R.color.background_card else R.color.background_menu),
                ContextCompat.getColor(this@AiImageGalleryActivity, R.color.background_card),
                UiCorner.actionRadius(this@AiImageGalleryActivity)
            )
            setOnClickListener { onClick() }
        }
        binding.filterContainer.addView(chip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = 8.dpToPx()
        })
    }

    private fun toggleSelection(image: AiGeneratedImage) {
        if (!selectedIds.add(image.id)) {
            selectedIds.remove(image.id)
        }
        adapter.notifyDataSetChanged()
        updateBatchBar()
    }

    private fun clearSelection() {
        selectedIds.clear()
        adapter.notifyDataSetChanged()
        updateBatchBar()
    }

    private fun selectAllVisibleImages() {
        val ids = adapter.getItems().map { it.id }
        if (ids.isEmpty()) return
        selectedIds.addAll(ids)
        adapter.notifyDataSetChanged()
        updateBatchBar()
    }

    private fun updateBatchBar() {
        binding.batchBar.isVisible = selectedIds.isNotEmpty()
        binding.tvBatchCount.text = "已选择 ${selectedIds.size} 张"
    }

    private fun showBatchGroupDialog() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        lifecycleScope.launch {
            val groups = withContext(Dispatchers.IO) { AiImageGalleryManager.listGroups() }
            val labels: List<CharSequence> = groups.map { it.name } + getString(R.string.ai_image_new_group)
            selector(getString(R.string.ai_image_favorite_to), labels) { _, index ->
                val group = groups.getOrNull(index)
                if (group != null) {
                    moveSelectedToGroup(ids, group.id)
                } else {
                    showCreateGroupDialog(ids)
                }
            }
        }
    }

    private fun showCreateGroupDialog(ids: List<String>) {
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_image_new_group)
        }
        alert(getString(R.string.ai_image_new_group)) {
            customView { dialogBinding.root }
            okButton {
                val name = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) {
                    val groupId = AiImageGalleryManager.createGroup(name).id
                    moveSelectedToGroup(ids, groupId)
                }
            }
            cancelButton()
        }
    }

    private fun moveSelectedToGroup(ids: List<String>, groupId: String?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AiImageGalleryManager.moveImagesToGroup(ids, groupId)
            }
            toastOnUi("已移动分组")
            clearSelection()
            reload()
        }
    }

    private fun confirmBatchDelete() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        alert(title = getString(R.string.delete), message = "删除选中的 ${ids.size} 张图片？") {
            okButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AiImageGalleryManager.deleteImages(ids)
                    }
                    clearSelection()
                    reload()
                }
            }
            cancelButton()
        }
    }

    private inner class Adapter :
        RecyclerAdapter<AiGeneratedImage, ItemAiGeneratedImageBinding>(this@AiImageGalleryActivity) {

        override fun getViewBinding(parent: ViewGroup): ItemAiGeneratedImageBinding {
            return ItemAiGeneratedImageBinding.inflate(inflater, parent, false).apply {
                root.radius = UiCorner.scaledDp(14f)
                root.cardElevation = 0f
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAiGeneratedImageBinding,
            item: AiGeneratedImage,
            payloads: MutableList<Any>
        ) = binding.run {
            val selected = item.id in selectedIds
            root.setCardBackgroundColor(
                ContextCompat.getColor(root.context, if (selected) R.color.background_menu else R.color.background_card)
            )
            ImageLoader.load(this@AiImageGalleryActivity, item.localPath)
                .error(R.drawable.image_loading_error)
                .into(ivImage)
            tvName.text = item.name
            tvPrompt.text = buildImageSubtitle(item)
            tvState.text = if (item.favorite) getString(R.string.in_favorites) else getString(R.string.ai_image_gallery_temporary)
            tvSelected.isVisible = selected
            tvSelected.setTextColor(accentColor)
            tvSelected.background = UiCorner.actionSelector(
                ContextCompat.getColor(this@AiImageGalleryActivity, R.color.background_card),
                ContextCompat.getColor(this@AiImageGalleryActivity, R.color.background_menu),
                UiCorner.actionRadius(this@AiImageGalleryActivity)
            )
            tvName.applyUiSectionTitleStyle(this@AiImageGalleryActivity)
            tvPrompt.applyUiLabelStyle(this@AiImageGalleryActivity)
            tvState.applyUiLabelStyle(this@AiImageGalleryActivity)
            tvPrompt.setTextColor(secondaryTextColor)
            tvState.setTextColor(if (item.favorite) accentColor else ContextCompat.getColor(this@AiImageGalleryActivity, R.color.primaryText))
            tvState.background = UiCorner.actionSelector(
                ContextCompat.getColor(this@AiImageGalleryActivity, R.color.background_card),
                ContextCompat.getColor(this@AiImageGalleryActivity, R.color.background_menu),
                UiCorner.actionRadius(this@AiImageGalleryActivity)
            )
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAiGeneratedImageBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let { image ->
                    if (selectedIds.isNotEmpty()) {
                        toggleSelection(image)
                    } else {
                        val dialog = AiImagePreviewDialog(image.id).apply {
                            setOnDismissListener { reload() }
                        }
                        showDialogFragment(dialog)
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                getItem(holder.bindingAdapterPosition - getHeaderCount())?.let { image ->
                    toggleSelection(image)
                }
                true
            }
        }
    }

    private fun buildImageSubtitle(item: AiGeneratedImage): String {
        return buildList {
            if (item.bookName.isNotBlank()) {
                add(item.bookName)
            }
            if (item.chapterTitle.isNotBlank()) {
                add(item.chapterTitle)
            }
            if (item.characterName.isNotBlank()) {
                add(item.characterName)
            }
            add(item.prompt.replace(Regex("\\s+"), " ").take(72))
        }.joinToString(" · ")
    }

    companion object {
        const val EXTRA_BOOK_KEY = "bookKey"
        const val EXTRA_TITLE = "title"
    }
}
