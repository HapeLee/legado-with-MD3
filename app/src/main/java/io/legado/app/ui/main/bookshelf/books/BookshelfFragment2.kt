package io.legado.app.ui.main.bookshelf.books

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewConfiguration
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf2Binding
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.books.styleFold.BaseBooksAdapter
import io.legado.app.ui.main.bookshelf.books.styleFold.BooksAdapterGrid
import io.legado.app.ui.main.bookshelf.books.styleFold.BooksAdapterGridCompact
import io.legado.app.ui.main.bookshelf.books.styleFold.BooksAdapterGridCover
import io.legado.app.ui.main.bookshelf.books.styleFold.BooksAdapterList
import io.legado.app.ui.main.bookshelf.books.styleFold.BooksAdapterListCompact
import io.legado.app.utils.bookshelfLayoutGrid
import io.legado.app.utils.bookshelfLayoutMode
import io.legado.app.utils.cnCompare
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
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
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 书架界面
 */
class BookshelfFragment2() : BaseBookshelfFragment(R.layout.fragment_bookshelf2),
    SearchView.OnQueryTextListener,
    BaseBooksAdapter.CallBack {

    fun interface OnGroupIdChangeListener {
        fun onGroupIdChanged()
    }

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf2Binding::bind)

    private val bookshelfLayoutMode by lazy { requireContext().bookshelfLayoutMode }

    private val bookshelfLayoutGrid by lazy { requireContext().bookshelfLayoutGrid }

    private var groupIdChangeListener: OnGroupIdChangeListener? = null

    private val booksAdapter: BaseBooksAdapter<*> by lazy {
        when (bookshelfLayoutMode) {
            0 -> {
                BooksAdapterList(requireContext(), this)
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
                BooksAdapterListCompact(requireContext(), this)
            }
        }
    }

    private var bookGroups: List<BookGroup> = emptyList()
    private var booksFlowJob: Job? = null
    override var groupId = BookGroup.Companion.IdRoot
    private var allBooks: List<Book> = emptyList()
    override var books: List<Book> = emptyList()
    private var enableRefresh = true

    private lateinit var backCallback: OnBackPressedCallback

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.topBar)
        initRecyclerView()
        initBookGroupData()
        initAllBooksData()
        initBooksData()
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S)
            binding.titleBar.fitsSystemWindows = true
        backCallback = object : OnBackPressedCallback(false) { // 初始禁用
            override fun handleOnBackPressed() {
                if (back()) {
                    updateBackCallbackState()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
    }

    private fun updateBackCallbackState() {
        //每次进出书架时，判断是否启用回调
        backCallback.isEnabled = groupId != BookGroup.IdRoot
    }

    @Suppress("UNCHECKED_CAST")
    fun BaseBooksAdapter<*>.getBookItems(): List<Book> {
        return getItems() as List<Book>
    }

    private fun initRecyclerView() {
        upFastScrollerBar()
        binding.refreshLayout.setOnRefreshListener {
            val books = booksAdapter.getBookItems()
            val refreshList = if (AppConfig.bookshelfRefreshingLimit > 0) {
                books.take(AppConfig.bookshelfRefreshingLimit)
            } else {
                books
            }
            binding.refreshLayout.isRefreshing = false
            activityViewModel.upToc(refreshList)
        }

        binding.rvBookshelf.layoutManager = GridLayoutManager(context, bookshelfLayoutGrid)
        binding.rvBookshelf.itemAnimator = null
        binding.rvBookshelf.adapter = booksAdapter
        booksAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (positionStart == 0 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                val layoutManager = binding.rvBookshelf.layoutManager
                if (toPosition == 0 && layoutManager is LinearLayoutManager) {
                    val scrollTo = layoutManager.findFirstVisibleItemPosition() - itemCount
                    binding.rvBookshelf.scrollToPosition(max(0, scrollTo))
                }
            }
        })
        binding.rvBookshelf.itemAnimator = DefaultItemAnimator()
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

    override fun upGroup(data: List<BookGroup>) {
        if (data != bookGroups) {
            bookGroups = data
            booksAdapter.updateItems()
            binding.emptyView.isGone = getItemCount() > 0
            binding.refreshLayout.isEnabled = enableRefresh && getItemCount() > 0
        }
    }

    override fun upSort() {
        initAllBooksData()
        initBooksData()
    }

    private var allBooksFlowJob: Job? = null

    private fun initAllBooksData() {
        allBooksFlowJob?.cancel()
        allBooksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowAll().map { list ->
                val sortType = BookGroup(BookGroup.Companion.IdRoot, "").getRealBookSort()
                when (sortType) {
                    1 -> list.sortedByDescending { it.latestChapterTime }
                    2 -> list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }
                    3 -> list.sortedBy { it.order }
                    4 -> list.sortedByDescending { max(it.latestChapterTime, it.durChapterTime) }
                    5 -> list.sortedWith { o1, o2 -> o1.author.cnCompare(o2.author) }
                    else -> list.sortedByDescending { it.durChapterTime }
                }
            }.flowWithLifecycleAndDatabaseChangeFirst(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.Companion.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("所有书籍更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                allBooks = list
                booksAdapter.setAllBooks(allBooks)
                booksAdapter.updateItems()
            }
        }
    }

    private fun initBooksData() {
        if (groupId == BookGroup.Companion.IdRoot) {
            if (isAdded) {
                binding.collTopBar.title = getString(R.string.bookshelf)
                binding.refreshLayout.isEnabled = true
                enableRefresh = true
            }
        } else {
            bookGroups.firstOrNull {
                groupId == it.groupId
            }?.let {
                binding.collTopBar.title = it.groupName
                binding.refreshLayout.isEnabled = it.enableRefresh
                enableRefresh = it.enableRefresh
            }
        }
        booksFlowJob?.cancel()
        booksFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookDao.flowByGroup(groupId).map { list ->
                //排序
                val isDescending = AppConfig.bookshelfSortOrder == 1

                val sortType = AppConfig.getBookSortByGroupId(groupId)
                when (sortType) {
                    1 -> if (isDescending) list.sortedByDescending { it.latestChapterTime }
                    else list.sortedBy { it.latestChapterTime }

                    2 -> if (isDescending)
                        list.sortedWith { o1, o2 -> o2.name.cnCompare(o1.name) }
                    else
                        list.sortedWith { o1, o2 -> o1.name.cnCompare(o2.name) }

                    3 -> if (isDescending) list.sortedByDescending { it.order }
                    else list.sortedBy { it.order }

                    4 -> if (isDescending) list.sortedByDescending {
                        max(it.latestChapterTime, it.durChapterTime)
                    } else list.sortedBy { max(it.latestChapterTime, it.durChapterTime) }

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
                AppDatabase.Companion.BOOK_TABLE_NAME
            ).catch {
                AppLog.put("书架更新出错", it)
            }.conflate().flowOn(Dispatchers.Default).collect { list ->
                if (view == null) return@collect
                binding.emptyView.isGone = list.isNotEmpty()
                binding.refreshLayout.isEnabled = enableRefresh && list.isNotEmpty()
                booksAdapter.updateItems()
                delay(500)
            }
        }
    }

    fun back(): Boolean {
        if (groupId != BookGroup.Companion.IdRoot) {
            groupId = BookGroup.Companion.IdRoot
            initBooksData()
            groupIdChangeListener?.onGroupIdChanged()
            updateBackCallbackState()
            return false
        }
        return true
        //需要提前设置，因为在返回时触发
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.Companion.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    override fun gotoTop() {
        if (AppConfig.isEInkMode) {
            binding.rvBookshelf.scrollToPosition(0)
        } else {
            binding.rvBookshelf.smoothScrollToPosition(0)
        }
    }

    override fun onItemClick(item: Any, sharedView: View) {
        if (AppConfig.sharedElementEnterTransitionEnable){
            when (item) {
                is Book -> {
                    val transitionName = "book_${item.bookUrl}"
                    sharedView.transitionName = transitionName

                    val cls = when {
                        item.isAudio -> AudioPlayActivity::class.java
                        item.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
                        else -> ReadBookActivity::class.java
                    }

                    val intent = Intent(requireContext(), cls).apply {
                        putExtra("bookUrl", item.bookUrl)
                        putExtra("transitionName", transitionName)
                    }

                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        sharedView,
                        transitionName
                    )

                    startActivity(intent, options.toBundle())
                }

                is BookGroup -> {
                    groupId = item.groupId
                    initBooksData()
                    groupIdChangeListener?.onGroupIdChanged()
                    updateBackCallbackState()
                }
            }
        } else {
            when (item) {
                is Book -> startActivityForBook(item)

                is BookGroup -> {
                    groupId = item.groupId
                    initBooksData()
                }
            }
        }

    }

    override fun onItemLongClick(item: Any, sharedView: View) {
        if (AppConfig.sharedElementEnterTransitionEnable){
            when (item) {
                is Book -> {
                    val intent = Intent(requireContext(), BookInfoActivity::class.java).apply {
                        putExtra("name", item.name)
                        putExtra("author", item.author)
                        putExtra("bookUrl", item.bookUrl)
                        putExtra("transitionName", "book_${item.bookUrl}") // 给共享元素唯一标识
                    }

                    sharedView.transitionName = "book_${item.bookUrl}"

                    val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                        requireActivity(),
                        sharedView,
                        sharedView.transitionName
                    )

                    startActivity(intent, options.toBundle())
                }

                is BookGroup -> showDialogFragment(GroupEditDialog(item))
            }
        } else {
            when (item) {
                is Book -> startActivity<BookInfoActivity> {
                    putExtra("name", item.name)
                    putExtra("author", item.author)
                    putExtra("bookUrl", item.bookUrl)
                }

                is BookGroup -> showDialogFragment(GroupEditDialog(item))
            }
        }
    }

    override fun isUpdate(bookUrl: String): Boolean {
        return activityViewModel.isUpdate(bookUrl)
    }

    fun getItemCount(): Int {
        return if (groupId == BookGroup.Companion.IdRoot) {
            bookGroups.size + books.size
        } else {
            books.size
        }
    }

    override fun getItems(): List<Any> {
        return if (groupId == BookGroup.Companion.IdRoot) {
            bookGroups + books
        } else {
            books
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.UP_BOOKSHELF) {
            booksAdapter.notification(it)
        }
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) {
            booksAdapter.notifyDataSetChanged()
        }
    }
}