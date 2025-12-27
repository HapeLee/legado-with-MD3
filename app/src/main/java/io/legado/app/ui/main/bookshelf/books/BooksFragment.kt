package io.legado.app.ui.main.bookshelf.books

//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter.StateRestorationPolicy
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBooksBinding
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BaseBooksAdapter
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BooksAdapterGrid
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BooksAdapterGridCompact
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BooksAdapterGridCover
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BooksAdapterList
import io.legado.app.ui.main.bookshelf.books.styleDefalut.BooksAdapterListCompact
import io.legado.app.utils.bookshelfLayoutGrid
import io.legado.app.utils.bookshelfLayoutMode
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面
 */
class BooksFragment() : BaseFragment(R.layout.fragment_books),
    BaseBooksAdapter.CallBack {

    constructor(position: Int, group: BookGroup) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        bundle.putLong("groupId", group.groupId)
        bundle.putInt("bookSort", group.getRealBookSort())
        bundle.putBoolean("enableRefresh", group.enableRefresh)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBooksBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()

    private val bookshelfLayoutMode by lazy { requireContext().bookshelfLayoutMode }

    private val bookshelfLayoutGrid by lazy { requireContext().bookshelfLayoutGrid }

    private val booksAdapter: BaseBooksAdapter<*> by lazy {
        when (bookshelfLayoutMode) {
            0 -> {
                BooksAdapterList(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            }

            1 -> {
                BooksAdapterGrid(requireContext(), this)
            }

            2 -> {
                BooksAdapterGridCompact(requireContext(), this)
            }

            3 -> {
                BooksAdapterGridCover(requireContext(), this)
            }

            else -> {
                BooksAdapterListCompact(requireContext(), this, this, viewLifecycleOwner.lifecycle)
            }
        }
    }
    private var booksFlowJob: Job? = null
    var position = 0
        private set
    var groupId = -1L
        private set
    var bookSort = 0
        private set
    private var upLastUpdateTimeJob: Job? = null
    private var enableRefresh = true

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let {
            position = it.getInt("position", 0)
            groupId = it.getLong("groupId", -1)
            bookSort = it.getInt("bookSort", 0)
            enableRefresh = it.getBoolean("enableRefresh", true)
            binding.refreshLayout.isEnabled = enableRefresh
        }
        initRecyclerView()
        upRecyclerData()
    }

    private fun initRecyclerView() {
        //binding.rvBookshelf.setEdgeEffectColor(primaryColor)
        upFastScrollerBar()
        //binding.refreshLayout.setColorSchemeColors(accentColor)
        binding.refreshLayout.setOnRefreshListener {
            val books = booksAdapter.getItems()
            val refreshList = if (AppConfig.bookshelfRefreshingLimit > 0) {
                books.take(AppConfig.bookshelfRefreshingLimit)
            } else {
                books
            }
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(refreshList)
        }
        binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayoutGrid)
        binding.rvBookshelf.setRecycledViewPool(activityViewModel.booksGridRecycledViewPool)
        booksAdapter.stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
        binding.rvBookshelf.adapter = booksAdapter
        booksAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (positionStart == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (toPosition == 0 && itemCount == 1 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }
        })
        ViewCompat.setOnApplyWindowInsetsListener(binding.rvBookshelf) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBars.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
        startLastUpdateTimeJob()
    }

    private fun upFastScrollerBar() {
        val showBookshelfFastScroller = AppConfig.showBookshelfFastScroller
        binding.rvBookshelf.setFastScrollEnabled(showBookshelfFastScroller)
        if (showBookshelfFastScroller) {
            binding.rvBookshelf.scrollBarSize = 0
        } else {
            binding.rvBookshelf.scrollBarSize =
                ViewConfiguration.get(requireContext()).scaledScrollBarSize
        }
    }

    fun upBookSort(sort: Int) {
        binding.root.post {
            if (!isAdded || view == null) return@post
            arguments?.putInt("bookSort", sort)
            bookSort = sort
            upRecyclerData()
        }
    }

    fun setEnableRefresh(enable: Boolean) {
        enableRefresh = enable
        binding.refreshLayout.isEnabled = enable
    }

    /**
     * 更新书籍列表信息
     */
    private fun upRecyclerData() {
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序

                val isDescending = AppConfig.bookshelfSortOrder == 1

                when (bookSort) {
                    1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
                    else list.sortedBy { it.latestChapterTime }

                    2 -> if (isDescending)
                        list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    else
                        list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

                    3 -> if (isDescending) list.sortedByDescending { it.order }
                    else list.sortedBy { it.order }

                    4 -> if (isDescending) list.sortedByDescending {
                        max(
                            it.latestChapterTime,
                            it.durChapterTime
                        )
                    }
                    else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

                    5 -> if (isDescending)
                        list.sortedWith { o1, o2 -> o2.author.cnCompare(o1.author) }
                    else
                        list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }

                    else -> if (isDescending) list.sortedByDescending { it.durChapterTime }
                    else list.sortedBy { it.durChapterTime }
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                if (view == null) return@collect
                binding.emptyView.isGone = list.isNotEmpty()
                binding.refreshLayout.isEnabled = enableRefresh && list.isNotEmpty()
                booksAdapter.setItems(list)
                delay(500)
            }
        }
    }

    private fun startLastUpdateTimeJob() {
        upLastUpdateTimeJob?.cancel()
        if (!AppConfig.showLastUpdateTime || (bookshelfLayoutMode != 0 && bookshelfLayoutMode != 4)) {
            return
        }
        upLastUpdateTimeJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    booksAdapter.upLastUpdateTime()
                    delay(30 * 1000)
                }
            }
        }
    }

    fun getBooks(): List<Book> {
        return booksAdapter.getItems()
    }

    fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    fun getBooksCount(): Int {
        return booksAdapter.itemCount
    }

    override fun onDestroyView() {
        super.onDestroyView()
        /**
         * 将 RecyclerView 中的视图全部回收到 RecycledViewPool 中
         */
        upLastUpdateTimeJob?.cancel()
        booksFlowJob?.cancel()
        binding.rvBookshelf.setItemViewCacheSize(0)
        binding.rvBookshelf.adapter = null
    }

    override fun open(book: Book, sharedView: View) {
        if (AppConfig.sharedElementEnterTransitionEnable){
            val transitionName = "book_${book.bookUrl}"
            sharedView.transitionName = transitionName

            val cls = when {
                book.isAudio -> AudioPlayActivity::class.java
                book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
                else -> ReadBookActivity::class.java
            }

            val intent = Intent(requireContext(), cls).apply {
                putExtra("bookUrl", book.bookUrl)
                putExtra("transitionName", transitionName)
            }

            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                sharedView,
                transitionName
            )

            startActivity(intent, options.toBundle())
        } else {
            startActivityForBook(book)
        }
    }

    override fun openBookInfo(book: Book, sharedView: View) {
        if (AppConfig.sharedElementEnterTransitionEnable){
            val intent = Intent(requireContext(), BookInfoActivity::class.java).apply {
                putExtra("name", book.name)
                putExtra("author", book.author)
                putExtra("bookUrl", book.bookUrl)
                putExtra("transitionName", "book_${book.bookUrl}")
            }

            sharedView.transitionName = "book_${book.bookUrl}"

            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                requireActivity(),
                sharedView,
                sharedView.transitionName
            )

            startActivity(intent, options.toBundle())
        } else {
            startActivity<BookInfoActivity> {
                putExtra("name", book.name)
                putExtra("author", book.author)
                putExtra("bookUrl", book.bookUrl)
            }

        }

    }


    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
            startLastUpdateTimeJob()
            upFastScrollerBar()
        }
    }
}
