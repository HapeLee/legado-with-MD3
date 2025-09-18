package io.legato.kazusa.ui.book.toc

//import io.legado.app.lib.theme.accentColor
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legato.kazusa.R
import io.legato.kazusa.base.adapter.DiffRecyclerAdapter
import io.legato.kazusa.base.adapter.ItemViewHolder
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.databinding.ItemChapterListBinding
import io.legato.kazusa.help.book.ContentProcessor
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.lib.theme.ThemeUtils
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.themeColor
import io.legato.kazusa.utils.visible
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

    private fun getDisplayTitle(chapter: BookChapter): String =
        displayTitleMap[chapter.title] ?: chapter.title

    override fun getViewBinding(parent: ViewGroup): ItemChapterListBinding =
        ItemChapterListBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemChapterListBinding,
        item: BookChapter,
        payloads: MutableList<Any>
    ) {
        val isDur = callback.durChapterIndex() == item.index
        val cached =
            callback.isLocalBook || item.isVolume || cacheFileNames.contains(item.getFileName())

        binding.run {
            if (payloads.isEmpty()) {
                tvChapterName.text = getDisplayTitle(item)
                tvChapterItem.foreground =
                    ThemeUtils.resolveDrawable(context, android.R.attr.selectableItemBackground)

                if (!item.tag.isNullOrEmpty() && !item.isVolume) {
                    tvTag.text = item.tag
                    tvTag.visible()
                } else tvTag.gone()

                if (AppConfig.tocCountWords && !item.wordCount.isNullOrEmpty() && !item.isVolume) {
                    tvWordCount.text = item.wordCount
                    tvWordCount.visible()
                } else tvWordCount.gone()

                if (item.isVip && !item.isPay) ivLocked.visible() else ivLocked.gone()

                upHasCache(binding, cached)

            } else {
                tvChapterName.text = getDisplayTitle(item)
                upHasCache(binding, cached)
            }

            if (selectedIndices.contains(item.index)) {
                ivVolume.gone()
                tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest))
            } else {
                if (item.isVolume) {
                    ivVolume.visible()
                    tvChapterName.textSize = 12f
                    tvChapterName.setTextColor(context.themeColor(com.google.android.material.R.attr.colorTertiary))
                    tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurface))
                } else {
                    ivVolume.gone()
                    if (isDur) {
                        tvChapterName.setTextColor(context.themeColor(androidx.appcompat.R.attr.colorPrimary))
                        tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
                    } else {
                        tvChapterName.setTextColor(context.themeColor(com.google.android.material.R.attr.colorOnSurface))
                        tvChapterItem.setBackgroundColor(context.themeColor(com.google.android.material.R.attr.colorSurface))
                    }
                }
            }
        }
    }


    override fun registerListener(holder: ItemViewHolder, binding: ItemChapterListBinding) {
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val item = getItem(pos) ?: return@setOnClickListener

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

    private fun upHasCache(binding: ItemChapterListBinding, cached: Boolean) = binding.apply {
        ivChecked.setImageResource(if (cached) R.drawable.ic_download_done else R.drawable.ic_outline_cloud_24)
        ivChecked.visible()
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