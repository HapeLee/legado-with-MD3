package io.legato.kazusa.ui.book.searchContent

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.constant.AppLog
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.databinding.ActivitySearchContentBinding
import io.legato.kazusa.help.IntentData
import io.legato.kazusa.help.book.BookHelp
import io.legato.kazusa.help.book.isLocal
//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor
//import io.legado.app.lib.theme.primaryTextColor
import io.legato.kazusa.ui.widget.recycler.UpLinearLayoutManager
import io.legato.kazusa.ui.widget.recycler.VerticalDivider
import io.legato.kazusa.utils.applyNavigationBarMargin
import io.legato.kazusa.utils.hideSoftInput
import io.legato.kazusa.utils.invisible
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.postEvent
import io.legato.kazusa.utils.shouldHideSoftInput
import io.legato.kazusa.utils.showSoftInput
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class SearchContentActivity :
    VMBaseActivity<ActivitySearchContentBinding, SearchContentViewModel>(),
    SearchContentAdapter.Callback {

    override val binding by viewBinding(ActivitySearchContentBinding::inflate)
    override val viewModel by viewModels<SearchContentViewModel>()
    private val adapter by lazy { SearchContentAdapter(this, this) }
    private val mLayoutManager by lazy { UpLinearLayoutManager(this) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var durChapterIndex = 0
    private var searchJob: Job? = null
    private var initJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val bbg = bottomBackground
//        val btc = getPrimaryTextColor(ColorUtils.isColorLight(bbg))
//        binding.llSearchBaseInfo.setBackgroundColor(bbg)
        binding.llSearchBaseInfo.applyNavigationBarMargin()
//        binding.tvCurrentSearchInfo.setTextColor(btc)
//        binding.ivSearchContentTop.setColorFilter(btc)
//        binding.ivSearchContentBottom.setColorFilter(btc)
        val searchResultList = IntentData.get<List<SearchResult>>("searchResultList")
        val position = intent.getIntExtra("searchResultIndex", 0)
        val noSearchResult = searchResultList == null
        initSearchView(noSearchResult)
        initRecyclerView()
        initView()
        val bookUrl = intent.getStringExtra("bookUrl") ?: return
        viewModel.initBook(bookUrl) {
            initSearchResultList(searchResultList, position)
            initBook(noSearchResult)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.content_search, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_enable_replace)?.isChecked = viewModel.replaceEnabled
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_enable_replace -> {
                viewModel.replaceEnabled = !viewModel.replaceEnabled
                item.isChecked = viewModel.replaceEnabled
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it.shouldHideSoftInput(ev)) {
                    it.post {
                        it.clearFocus()
                        it.hideSoftInput()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun initSearchResultList(list: List<SearchResult>?, position: Int) {
        list ?: return
        viewModel.searchResultList.addAll(list)
        viewModel.searchResultCounts = list.size
        adapter.setItems(list)
        binding.recyclerView.scrollToPosition(position)
    }

    private fun initSearchView(requestFocus: Boolean) {
        //searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.search)
        if (requestFocus) searchView.isIconified = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                startContentSearch(query.trim())
                searchView.clearFocus()
                return false
            }

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = mLayoutManager
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
    }

    private fun initView() {
        binding.ivSearchContentTop.setOnClickListener {
            mLayoutManager.scrollToPositionWithOffset(0, 0)
        }
        binding.ivSearchContentBottom.setOnClickListener {
            if (adapter.itemCount > 0) {
                mLayoutManager.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
            }
        }
        binding.tvCurrentSearchInfo.setOnClickListener {
            searchView.allViews.forEach { view ->
                if (view is EditText) {
                    view.showSoftInput()
                    return@setOnClickListener
                }
            }
        }
        binding.fbStop.setOnClickListener {
            searchJob?.cancel()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initBook(submit: Boolean = true) {
        binding.tvCurrentSearchInfo.text =
            this.getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
        viewModel.book?.let {
            initCacheFileNames(it)
            durChapterIndex = it.durChapterIndex
            intent.getStringExtra("searchWord")?.let { searchWord ->
                searchView.setQuery(searchWord, submit)
            }
        }
    }

    private fun initCacheFileNames(book: Book) {
        initJob = lifecycleScope.launch {
            withContext(IO) {
                viewModel.cacheChapterNames.addAll(BookHelp.getChapterFiles(book))
            }
            adapter.notifyItemRangeChanged(0, adapter.itemCount, true)
        }
    }

    override fun observeLiveBus() {
        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (book, chapter) ->
            viewModel.book?.bookUrl?.let { bookUrl ->
                if (book.bookUrl == bookUrl) {
                    viewModel.cacheChapterNames.add(chapter.getFileName())
                    adapter.notifyItemChanged(chapter.index, true)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun startContentSearch(query: String) {
        // 按章节搜索内容
        if (query.isBlank()) return
        searchJob?.cancel()
        adapter.clearItems()
        viewModel.searchResultList.clear()
        viewModel.searchResultCounts = 0
        viewModel.lastQuery = query
        binding.refreshProgressBar.isVisible = true
        binding.fbStop.visible()
        searchJob = lifecycleScope.launch(IO) {
            initJob?.join()
            kotlin.runCatching {
                appDb.bookChapterDao.getChapterList(viewModel.bookUrl).forEach { bookChapter ->
                    ensureActive()
                    val searchResults = if (isLocalBook
                        || viewModel.cacheChapterNames.contains(bookChapter.getFileName())
                    ) {
                        viewModel.searchChapter(query, bookChapter)
                    } else {
                        return@forEach
                    }
                    ensureActive()
                    if (searchResults.isNotEmpty()) {
                        viewModel.searchResultList.addAll(searchResults)
                        binding.tvCurrentSearchInfo.post {
                            binding.tvCurrentSearchInfo.text =
                                this@SearchContentActivity.getString(R.string.search_content_size) + ": ${viewModel.searchResultCounts}"
                            adapter.addItems(searchResults)
                        }
                    }
                }
                if (viewModel.searchResultCounts == 0) {
                    val noSearchResult =
                        SearchResult(resultText = getString(R.string.search_content_empty))
                    binding.tvCurrentSearchInfo.post {
                        adapter.addItem(noSearchResult)
                    }
                }
            }.onFailure {
                AppLog.put("全文搜索出错\n${it.localizedMessage}", it)
            }
            binding.tvCurrentSearchInfo.post {
                binding.fbStop.invisible()
                binding.refreshProgressBar.isVisible = false
            }
        }
    }

    private val isLocalBook: Boolean
        get() = viewModel.book?.isLocal == true

    override fun openSearchResult(searchResult: SearchResult, index: Int) {
        searchJob?.cancel()
        postEvent(EventBus.SEARCH_RESULT, viewModel.searchResultList as List<SearchResult>)
        val searchData = Intent()
        val key = System.currentTimeMillis()
        IntentData.put("searchResult$key", searchResult)
        IntentData.put("searchResultList$key", viewModel.searchResultList)
        searchData.putExtra("key", key)
        searchData.putExtra("index", index)
        setResult(RESULT_OK, searchData)
        finish()
    }

    override fun durChapterIndex(): Int {
        return durChapterIndex
    }

}