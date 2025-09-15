package io.legato.kazusa.ui.book.toc

import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.platform.MaterialFadeThrough
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseFragment
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.databinding.FragmentChapterListBinding
import io.legato.kazusa.help.book.BookHelp
import io.legato.kazusa.help.book.isLocal
import io.legato.kazusa.help.book.simulatedTotalChapterNum
import io.legato.kazusa.model.CacheBook
import io.legato.kazusa.ui.widget.recycler.UpLinearLayoutManager
import io.legato.kazusa.utils.VibrationUtils
import io.legato.kazusa.utils.applyNavigationBarPadding
import io.legato.kazusa.utils.dpToPx
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.themeColor
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

class ChapterListFragment : VMBaseFragment<TocViewModel>(R.layout.fragment_chapter_list),
    ChapterListAdapter.Callback,
    TocViewModel.ChapterListCallBack {
    override val viewModel by activityViewModels<TocViewModel>()
    private val binding by viewBinding(FragmentChapterListBinding::bind)
    private val mLayoutManager by lazy { UpLinearLayoutManager(requireContext()) }
    private val adapter by lazy { ChapterListAdapter(requireContext(), this) }
    private var durChapterIndex = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        viewModel.chapterListCallBack = this@ChapterListFragment
        initRecyclerView()
        initView()
        viewModel.bookData.observe(this@ChapterListFragment) {
            initBook(it)
        }
    }

    private fun initRecyclerView() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val chapter = adapter.getItem(position) ?: return
                val book = book ?: return

                toastOnUi("开始下载: ${adapter.getItem(position)?.title}")
                CacheBook.start(requireContext(), book, listOf(chapter.index))
                VibrationUtils.vibratePattern(requireContext(), longArrayOf(0, 50, 30, 50), -1)
                adapter.notifyItemChanged(position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    val itemView = viewHolder.itemView

                    val interpolator = FastOutSlowInInterpolator()
                    val dampedDx =
                        interpolator.getInterpolation(min(1f, dX / itemView.width)) * itemView.width

                    // 背景
                    val background =
                        requireContext().themeColor(com.google.android.material.R.attr.colorSecondary)
                            .toDrawable()
                    background.setBounds(
                        itemView.left,
                        itemView.top,
                        itemView.left + dampedDx.toInt(),
                        itemView.bottom
                    )
                    background.draw(c)

                    // 图标
                    val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_download)!!
                    val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + icon.intrinsicHeight
                    val iconLeft = 12.dpToPx()
                    val iconRight = iconLeft + icon.intrinsicWidth
                    icon.setTint(requireContext().themeColor(com.google.android.material.R.attr.colorOnSecondary))
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    icon.draw(c)

                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dampedDx,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                } else {
                    super.onChildDraw(
                        c,
                        recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        binding.recyclerView.layoutManager = mLayoutManager

        binding.recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
                    outRect.bottom = 80.dpToPx()
                }
            }
        })

        binding.recyclerView.adapter = adapter
    }

    private fun initView() = binding.run {
        llBase.applyNavigationBarPadding()
        btnChapterTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        btnChapterBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        btnLocate.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(durChapterIndex, 0)
        }
        btnSelectAll.setOnClickListener {
            adapter.selectAll()
            toastOnUi("已全选 ${adapter.itemCount} 个章节")
        }
        btnInvertSelection.setOnClickListener {
            adapter.invertSelection()
            toastOnUi("已反选")
        }
        btnFromSelection.setOnClickListener {
            adapter.selectFrom()
            toastOnUi("已选择之后的章节")
        }

        btnDownloadSelected.setOnClickListener {
            val chapters = adapter.getSelectedChapters()
            if (chapters.isNotEmpty()) {
                val book = book ?: return@setOnClickListener
                val indices = chapters.map { it.index }
                CacheBook.start(requireContext(), book, indices)
                toastOnUi("开始下载 ${chapters.size} 个章节")
                adapter.clearSelection()
            } else {
                toastOnUi("未选择章节")
            }
        }
        btnExit.setOnClickListener {
            adapter.clearSelection()
        }
        btnBookmarkSelection.setOnClickListener {
            addSelectedChaptersBookmarkDirectly()
            toastOnUi("已添加书签")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(book: Book) {
        lifecycleScope.launch {
            upChapterList(null)
            durChapterIndex = book.durChapterIndex
            binding.tvCurrentChapterInfo.text =
                "${book.durChapterTitle}"
            binding.tvCurrentChapterAll.text =
                "${book.durChapterIndex + 1}/${book.simulatedTotalChapterNum()}"
            initCacheFileNames(book)
        }
    }

    private fun initCacheFileNames(book: Book) {
        lifecycleScope.launch(IO) {
            adapter.cacheFileNames.addAll(BookHelp.getChapterFiles(book))
            withContext(Main) {
                adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
            }
        }
    }

    fun addSelectedChaptersBookmarkDirectly() {
        val book = viewModel.bookData.value ?: return
        val selectedChapters = adapter.getSelectedChapters()
        if (selectedChapters.isEmpty()) return

        lifecycleScope.launch {
            withContext(IO) {
                selectedChapters.forEach { chapter ->
                    val page = chapter.title
                    val bookmark = book.createBookMark().apply {
                        chapterIndex = chapter.index
                        chapterPos = 0
                        chapterName = page
                        // 不设置 content 或 bookText，或者只存标题
                    }
                    appDb.bookmarkDao.insert(bookmark)
                }
            }

            // 可选：清空选择状态并提示
            adapter.clearSelection()
        }
    }


    override fun onSelectionModeChanged(enabled: Boolean) {
        TransitionManager.beginDelayedTransition(binding.coordinatorLayout, MaterialFadeThrough())
        if (enabled) {
            binding.llBase.gone()
            binding.floatingToolbarBottom.visible()
        } else {
            binding.llBase.visible()
            binding.floatingToolbarBottom.gone()
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.bookData.value?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == viewModel.bookData.value?.bookUrl) {
                    adapter.cacheFileNames.add(chapter.getFileName())
                    val index = adapter.getItems().indexOfFirst { it.index == chapter.index }
                    if (index >= 0) {
                        adapter.notifyItemChanged(index, true)
                    }
                }
            }
        }
    }

    override fun upChapterList(searchKey: String?) {
        lifecycleScope.launch {
            withContext(IO) {
                val end = (book?.simulatedTotalChapterNum() ?: Int.MAX_VALUE) - 1
                when {
                    searchKey.isNullOrBlank() ->
                        appDb.bookChapterDao.getChapterList(viewModel.bookUrl, 0, end)

                    else -> appDb.bookChapterDao.search(viewModel.bookUrl, searchKey, 0, end)
                }
            }.let {
                adapter.setItems(it)
            }
        }
    }

    override fun onListChanged() {
        lifecycleScope.launch {
            val scrollPos = adapter.getItems()
                .indexOfLast { it.index < durChapterIndex }
                .coerceAtLeast(0)
            mLayoutManager.scrollToPositionWithOffset(scrollPos, 0)
            adapter.upDisplayTitles(scrollPos)
        }
    }

    override fun clearDisplayTitle() {
        adapter.clearDisplayTitle()
        adapter.upDisplayTitles(mLayoutManager.findFirstVisibleItemPosition())
    }

    override fun upAdapter() {
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    override val scope: CoroutineScope
        get() = lifecycleScope

    override val book: Book?
        get() = viewModel.bookData.value

    override val isLocalBook: Boolean
        get() = viewModel.bookData.value?.isLocal == true

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

    override fun openChapter(bookChapter: BookChapter) {
        activity?.run {
            setResult(
                RESULT_OK, Intent()
                    .putExtra("index", bookChapter.index)
                    .putExtra("chapterChanged", bookChapter.index != durChapterIndex)
            )
            finish()
        }
    }

}