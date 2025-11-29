package io.legado.app.ui.book.toc

//import io.legado.app.lib.theme.accentColor
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.databinding.ItemChapterListBinding
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.utils.gone
import io.legado.app.utils.themeColor
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class ChapterListAdapter(
    context: Context,
    val callback: Callback
) : DiffRecyclerAdapter<BookChapter, ItemChapterListBinding>(context) {

    val cacheFileNames = hashSetOf<String>()
    private val displayTitleMap = ConcurrentHashMap<String, String>()
    private val selectedIndices = LinkedHashSet<Int>()

    private val handler = Handler(Looper.getMainLooper())
    private var upDisplayTileJob: Job? = null
    private val collapsedVolumes = mutableSetOf<Int>() // 保存折叠的卷index
    private var visibleItems = mutableListOf<BookChapter>() // 当前显示

    override val diffItemCallback: DiffUtil.ItemCallback<BookChapter>
        get() = object : DiffUtil.ItemCallback<BookChapter>() {
            override fun areItemsTheSame(oldItem: BookChapter, newItem: BookChapter): Boolean {
                return oldItem.index == newItem.index
            }

            override fun areContentsTheSame(oldItem: BookChapter, newItem: BookChapter): Boolean {
                return oldItem.bookUrl == newItem.bookUrl
                        && oldItem.url == newItem.url
                        && oldItem.isVip == newItem.isVip
                        && oldItem.isPay == newItem.isPay
                        && oldItem.title == newItem.title
                        && oldItem.tag == newItem.tag
                        && oldItem.wordCount == newItem.wordCount
                        && oldItem.isVolume == newItem.isVolume
            }
        }

    override fun onCurrentListChanged() {
        super.onCurrentListChanged()
        rebuildVisibleItems()
        callback.onListChanged()
    }

    fun clearDisplayTitle() {
        upDisplayTileJob?.cancel()
        displayTitleMap.clear()
    }

    fun upDisplayTitles(startIndex: Int) {
        upDisplayTileJob?.cancel()
        upDisplayTileJob = callback.scope.launch(Dispatchers.Default) {
            val book = callback.book ?: return@launch
            val replaceRules = ContentProcessor.get(book.name, book.origin).getTitleReplaceRules()
            val useReplace = AppConfig.tocUiUseReplace && book.getUseReplaceRule()
            val items = getItems()
            val indices = ((startIndex until items.size) + (startIndex downTo 0))
                .filter { it in items.indices }

            for (i in indices) {
                val item = items[i]
                val key = item.url
                if (displayTitleMap[key] == null) {
                    ensureActive()
                    val displayTitle = item.getDisplayTitle(replaceRules, useReplace)
                    displayTitleMap[key] = displayTitle
                    ensureActive()
                    withContext(Dispatchers.Main) {
                        notifyItemChanged(i, true)
                    }
                }
            }
        }
    }

    fun isInSelectionMode(): Boolean = selectedIndices.isNotEmpty()

    fun toggleSelection(chapter: BookChapter, position: Int? = null) {
        val pos = position ?: getItems().indexOfFirst { it.index == chapter.index }
        if (pos == -1) return

        if (selectedIndices.contains(chapter.index)) {
            selectedIndices.remove(chapter.index)
        } else {
            selectedIndices.add(chapter.index)
        }
        notifyItemChanged(pos, true)
        callback.onSelectionModeChanged(isInSelectionMode())
    }

    fun getSelectedChapters(): List<BookChapter> =
        getItems().filter { selectedIndices.contains(it.index) }

    fun clearSelection() {
        if (selectedIndices.isNotEmpty()) {
            val prevSelected = selectedIndices.toList()
            selectedIndices.clear()
            prevSelected.forEach { index ->
                val pos = getItems().indexOfFirst { it.index == index }
                if (pos != -1) notifyItemChanged(pos, true)
            }
            callback.onSelectionModeChanged(false)
        }
    }

    fun selectAll() {
        val items = getItems()
        val changedIndices = items.map { it.index }.filter { !selectedIndices.contains(it) }
        selectedIndices.clear()
        selectedIndices.addAll(items.map { it.index })
        changedIndices.forEach { index ->
            val pos = items.indexOfFirst { it.index == index }
            if (pos != -1) notifyItemChanged(pos, true)
        }
        callback.onSelectionModeChanged(true)
    }

    fun invertSelection() {
        val items = getItems()
        val newSelected = items.map { it.index }.toSet() - selectedIndices
        val changedIndices = (selectedIndices + newSelected) // 之前选中 + 新选中 = 改变的项
        selectedIndices.clear()
        selectedIndices.addAll(newSelected)
        changedIndices.forEach { index ->
            val pos = items.indexOfFirst { it.index == index }
            if (pos != -1) notifyItemChanged(pos, true)
        }
        callback.onSelectionModeChanged(selectedIndices.isNotEmpty())
    }

    fun selectFrom() {
        val items = getItems()
        val startPos = selectedIndices.lastOrNull()?.let { lastIndex ->
            items.indexOfFirst { it.index == lastIndex }
        } ?: return

        if (startPos !in items.indices) return

        val changedIndices = mutableListOf<Int>()
        for (i in startPos until items.size) {
            val chapterIndex = items[i].index
            if (!selectedIndices.contains(chapterIndex)) {
                selectedIndices.add(chapterIndex)
                changedIndices.add(i)
            }
        }

        changedIndices.forEach { notifyItemChanged(it, true) }
        callback.onSelectionModeChanged(isInSelectionMode())
    }

    private fun rebuildVisibleItems() {
        val all = getItems()
        visibleItems.clear()
        var currentVolumeCollapsed = false
        for (item in all) {
            if (item.isVolume) {
                visibleItems.add(item)
                currentVolumeCollapsed = collapsedVolumes.contains(item.index)
            } else if (!currentVolumeCollapsed) {
                visibleItems.add(item)
            }
        }
    }

    fun toggleVolume(volume: BookChapter) {
        if (!volume.isVolume) return

        val all = getItems()
        val volumeIndex = volume.index
        val startIndex = all.indexOf(volume)
        if (startIndex == -1) return

        val visiblePos = visibleItems.indexOf(volume)
        if (visiblePos == -1) return

        if (collapsedVolumes.contains(volumeIndex)) {
            collapsedVolumes.remove(volumeIndex)

            val toAdd = mutableListOf<BookChapter>()
            for (i in startIndex + 1 until all.size) {
                val next = all[i]
                if (next.isVolume) break
                toAdd.add(next)
            }

            if (toAdd.isNotEmpty()) {
                val insertPos = visiblePos + 1
                visibleItems.addAll(insertPos, toAdd)
                notifyItemRangeInserted(insertPos, toAdd.size)
            }

            notifyItemChanged(visiblePos)

        } else {
            collapsedVolumes.add(volumeIndex)

            val toRemove = mutableListOf<BookChapter>()
            for (i in startIndex + 1 until all.size) {
                val next = all[i]
                if (next.isVolume) break
                toRemove.add(next)
            }

            if (toRemove.isNotEmpty()) {
                val removeStart = visiblePos + 1
                visibleItems.removeAll(toRemove)
                notifyItemRangeRemoved(removeStart, toRemove.size)
            }

            notifyItemChanged(visiblePos)
        }
    }

    fun expandAllVolumes() {
        val all = getItems()
        var insertedOffset = 0

        val collapsedCopy = collapsedVolumes.toList()
        collapsedVolumes.clear()

        for (volumeIndex in collapsedCopy) {
            val volume = all.firstOrNull { it.isVolume && it.index == volumeIndex } ?: continue
            val start = all.indexOf(volume)
            if (start == -1) continue

            val visiblePos = visibleItems.indexOf(volume)
            if (visiblePos == -1) continue

            val children = mutableListOf<BookChapter>()
            for (i in start + 1 until all.size) {
                val next = all[i]
                if (next.isVolume) break
                children.add(next)
            }

            if (children.isNotEmpty()) {
                val insertPos = visiblePos + 1
                visibleItems.addAll(insertPos, children)
                notifyItemRangeInserted(insertPos, children.size)
                notifyItemChanged(visiblePos)
                insertedOffset += children.size
            }
        }
    }

    fun collapseAllVolumes() {
        val all = getItems()

        for (volume in all.filter { it.isVolume }) {
            val visiblePos = visibleItems.indexOf(volume)
            if (visiblePos == -1) continue

            val volumeIndex = volume.index
            collapsedVolumes.add(volumeIndex)

            val toRemove = mutableListOf<BookChapter>()
            for (i in visiblePos + 1 until visibleItems.size) {
                val next = visibleItems[i]
                if (next.isVolume) break
                toRemove.add(next)
            }

            if (toRemove.isNotEmpty()) {
                val removeStart = visiblePos + 1
                visibleItems.removeAll(toRemove)
                notifyItemRangeRemoved(removeStart, toRemove.size)
                notifyItemChanged(visiblePos)
            }
        }
    }

    fun getAllVolumes(): List<String> {
        return getItems().filter { it.isVolume }.map { it.title }
    }

    fun getVolumeStartPosition(volumePosition: Int): Int {
        val volumes = getItems().filter { it.isVolume }
        val volume = volumes.getOrNull(volumePosition) ?: return -1
        return getItems().indexOf(volume)
    }

    fun areAllVolumesExpanded(): Boolean {
        return getItems().filter { it.isVolume }.all { !collapsedVolumes.contains(it.index) }
    }

    private fun getDisplayTitle(chapter: BookChapter): String =
        displayTitleMap[chapter.url] ?: chapter.title

    override fun getItemCount(): Int = visibleItems.size

    override fun getItem(position: Int): BookChapter? =
        visibleItems.getOrNull(position)

    override fun getViewBinding(parent: ViewGroup): ItemChapterListBinding =
        ItemChapterListBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChapterListBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        val isDur = callback.durChapterIndex() == item.index
        val cached = item.isVolume || cacheFileNames.contains(item.getFileName())
        val isSelected = selectedIndices.contains(item.index)
        val isCollapsed = collapsedVolumes.contains(item.index)

        binding.run {
            if (payloads.isEmpty()) {
                tvChapterName.text = getDisplayTitle(item)
                tvChapterItem.foreground =
                    ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)

                ivChecked.isVisible = !callback.isLocalBook

                if (!item.tag.isNullOrEmpty() && !item.isVolume) {
                    tvTag.text = item.tag
                    tvTag.visible()
                } else tvTag.gone()

                if (AppConfig.tocCountWords && !item.wordCount.isNullOrEmpty() && !item.isVolume) {
                    tvWordCount.text = item.wordCount
                    tvWordCount.visible()
                } else tvWordCount.gone()

                if (item.isVip && !item.isPay) ivLocked.visible() else ivLocked.gone()
            } else {
                tvChapterName.text = getDisplayTitle(item)
            }
            upHasCache(binding, cached, isDur)

            when {
                item.isVolume -> {
                    ivVolume.visible()
                    ivVolume.isChecked = isCollapsed
                    tvChapterName.textSize = 12f
                    tvChapterName.setTextColor(context.themeColor(com.google.android.material.R.attr.colorSecondary))
                    tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurface))
                }

                isDur -> {
                    ivVolume.gone()
                    tvChapterName.textSize = 14f
                    tvChapterName.setTextColor(context.themeColor(androidx.appcompat.R.attr.colorPrimary))
                    tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
                }

                else -> {
                    ivVolume.gone()
                    tvChapterName.textSize = 14f
                    tvChapterName.setTextColor(context.themeColor(com.google.android.material.R.attr.colorOnSurface))
                    tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurface))
                }
            }

            if (isSelected) {
                tvChapterItem.setBackgroundColor(
                    context.themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
            }
        }
    }


    override fun registerListener(holder: ItemViewHolder, binding: ItemChapterListBinding) {
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos) ?: return@setOnClickListener

            if (item.isVolume) {
                toggleVolume(item)
                return@setOnClickListener
            }

            if (isInSelectionMode()) {
                toggleSelection(item, pos)
            } else {
                callback.openChapter(item)
            }
        }

        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            val item = getItem(pos) ?: return@setOnLongClickListener true

            toggleSelection(item, pos)
            true
        }
    }

    private fun upHasCache(binding: ItemChapterListBinding, cached: Boolean, isDur: Boolean = false) = binding.apply {
        ivChecked.setImageResource(
            if (cached) R.drawable.ic_download_done
            else if (isDur) R.drawable.ic_locate
            else R.drawable.ic_outline_cloud_24)
    }

    interface Callback {
        val scope: CoroutineScope
        val book: Book?
        val isLocalBook: Boolean
        fun openChapter(bookChapter: BookChapter)
        fun durChapterIndex(): Int
        fun onListChanged()
        fun onSelectionModeChanged(enabled: Boolean)
    }
}