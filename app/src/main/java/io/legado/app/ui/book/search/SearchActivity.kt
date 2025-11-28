package io.legado.app.ui.book.search

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.material.search.SearchBar
import com.google.android.material.search.SearchView
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.databinding.ActivityBookSearchBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.BookShelfState
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.utils.applyNavigationBarMargin
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import kotlin.math.abs

class SearchActivity : VMBaseActivity<ActivityBookSearchBinding, SearchViewModel>(),
    BookAdapter.CallBack,
    HistoryKeyAdapter.CallBack,
    SearchScopeDialog.Callback,
    SearchAdapter.CallBack {

    override val binding by viewBinding(ActivityBookSearchBinding::inflate)
    override val viewModel by viewModels<SearchViewModel>()

    private val adapter by lazy { SearchAdapter(this, this) }
    private val bookAdapter by lazy {
        BookAdapter(this, this).apply {
            setHasStableIds(true)
        }
    }
    private val historyKeyAdapter by lazy {
        HistoryKeyAdapter(this, this).apply {
            setHasStableIds(true)
        }
    }

    private val searchBar: SearchBar by lazy { binding.searchBar }
    private val searchView: SearchView by lazy { binding.searchView }

    private var menu: Menu? = null
    private var groups: List<String>? = null
    private var historyFlowJob: Job? = null
    private var booksFlowJob: Job? = null
    private var precisionSearchMenuItem: MenuItem? = null
    private var isManualStopSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.searchBar)
        initRecyclerView()
        initMaterialSearch()
        initOtherView()
        initData()
        updateSelectedGroup()
        receiptIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiptIntent(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_search, menu)
        this.menu = menu
        precisionSearchMenuItem = menu.findItem(R.id.menu_precision_search)
        precisionSearchMenuItem?.isChecked = getPrefBoolean(PreferKey.precisionSearch)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        buildSearchScopeMenu(menu)
        updateSearchScopeChip()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_precision_search -> {
                val newValue = !getPrefBoolean(PreferKey.precisionSearch)
                putPrefBoolean(PreferKey.precisionSearch, newValue)
                precisionSearchMenuItem?.isChecked = newValue
                binding.chipPrecisionSearch.isChecked = newValue
                binding.searchView.text.toString().trim().let {
                    binding.searchView.setText(it)
                }
            }

            R.id.menu_search_scope -> alertSearchScope()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            R.id.menu_log -> showDialogFragment(AppLogDialog())
            R.id.menu_1 -> {
                viewModel.searchScope.update("")
                updateSelectedGroup()
            }
            else -> {
                if (item.groupId == R.id.menu_group_1) {
                    viewModel.searchScope.remove(item.title.toString())
                } else if (item.groupId == R.id.menu_group_2) {
                    viewModel.searchScope.update(item.title.toString())
                }
                updateSelectedGroup()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initMaterialSearch() {
        binding.chipPrecisionSearch.isChecked = getPrefBoolean(PreferKey.precisionSearch)
        binding.chipPrecisionSearch.setOnCheckedChangeListener { chip, isChecked ->
            putPrefBoolean(PreferKey.precisionSearch, isChecked)
            precisionSearchMenuItem?.isChecked = isChecked
            binding.searchView.text.toString().trim().let {
                binding.searchView.setText(it)
            }
        }
        binding.chipSearchScope.setOnClickListener {
            alertSearchScope()
        }
        binding.chipGroup.setOnClickListener { view ->
            binding.chipGroup.isChecked = true
            val popup = PopupMenu(this, view)
            val menu = popup.menu
            buildSearchScopeMenu(menu)
            updateSearchScopeChip()
            var fromGroup1 = false
            popup.setOnMenuItemClickListener { item ->
                when (item.groupId) {
                    R.id.menu_group_1 -> {
                        viewModel.searchScope.remove(item.title.toString())
                        fromGroup1 = true
                    }
                    R.id.menu_group_2 -> {
                        if (item.itemId == R.id.menu_1) {
                            viewModel.searchScope.update("")
                            fromGroup1 = true
                        } else {
                            viewModel.searchScope.update(item.title.toString())
                            fromGroup1 = false
                        }
                    }
                    else -> {
                        viewModel.searchScope.update(item.title.toString())
                        fromGroup1 = false
                    }
                }

                updateSelectedGroup()
                true
            }
            popup.setOnDismissListener {
                binding.chipGroup.isChecked = !fromGroup1
                updateSearchScopeChip()
            }
            popup.show()
        }

        searchBar.setOnMenuItemClickListener { item ->
            onCompatOptionsItemSelected(item)
        }

        searchBar.setNavigationOnClickListener {
            finish()
        }

        searchView.setupWithSearchBar(searchBar)

        binding.searchView.show()
        binding.searchView.editText.requestFocus()

        searchView.editText.hint = getString(R.string.search_book_key)
        searchView.editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }

        searchView.editText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                doSearch()
                true
            } else {
                false
            }
        }

        searchView.editText.doAfterTextChanged {
            viewModel.stop()
            binding.fbStartStop.invisible()
            upHistory(it?.toString()?.trim().orEmpty())
        }

        searchView.addTransitionListener { _, _, newState ->
            if (newState == SearchView.TransitionState.HIDDEN) {
                searchBar.setText(searchView.text)
            }
        }

        searchView.addTransitionListener { _, oldState, newState ->
            if (oldState == SearchView.TransitionState.HIDDEN && newState == SearchView.TransitionState.SHOWN) {
                searchView.editText.requestFocus()
            }
        }

        visibleInputHelp()
    }

    private fun doSearch() {
        val key = searchView.text.toString().trim()
        if (key.isNotEmpty()) {
            isManualStopSearch = false
            viewModel.saveSearchKey(key)
            viewModel.searchKey = ""
            viewModel.search(key)
            visibleInputHelp()
            searchView.hide()
        }
    }

    private fun updateSelectedGroup() {
        val displayNames = viewModel.searchScope.displayNames
        val groupName = displayNames.firstOrNull().orEmpty()
        binding.chipGroup.text = groupName.ifEmpty { getString(R.string.search_select_group) }
        binding.chipGroup.isChecked = groupName.isNotEmpty()
    }

    private fun buildSearchScopeMenu(menu: Menu) {
        menu.transaction {
            menu.removeGroup(R.id.menu_group_1)
            menu.removeGroup(R.id.menu_group_2)
            var hasChecked = false
            val searchScopeNames = viewModel.searchScope.displayNames

            if (viewModel.searchScope.isSource()) {
                menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, searchScopeNames.first()).apply {
                    isChecked = true
                    hasChecked = true
                }
            }

            val allSourceMenu =
                menu.add(R.id.menu_group_2, R.id.menu_1, Menu.NONE, getString(R.string.all_source))
                    .apply {
                        if (searchScopeNames.isEmpty()) {
                            isChecked = true
                            hasChecked = true
                        }
                    }

            groups?.forEach { groupName ->
                if (searchScopeNames.contains(groupName)) {
                    menu.add(R.id.menu_group_1, Menu.NONE, Menu.NONE, groupName).apply {
                        isChecked = true
                        hasChecked = true
                    }
                } else {
                    menu.add(R.id.menu_group_2, Menu.NONE, Menu.NONE, groupName)
                }
            }

            if (!hasChecked) {
                viewModel.searchScope.update("")
                allSourceMenu.isChecked = true
            }

            menu.setGroupCheckable(R.id.menu_group_1, true, false)
            menu.setGroupCheckable(R.id.menu_group_2, true, true)
        }
    }


    private fun initRecyclerView() {
        binding.rvBookshelfSearch.layoutManager = FlexboxLayoutManager(this)
        binding.rvBookshelfSearch.adapter = bookAdapter
        binding.rvBookshelfSearch.itemAnimator = DefaultItemAnimator()

        binding.rvHistoryKey.layoutManager = FlexboxLayoutManager(this)
        binding.rvHistoryKey.adapter = historyKeyAdapter
        binding.rvHistoryKey.itemAnimator = DefaultItemAnimator()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = DefaultItemAnimator()

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                if (toPosition == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!recyclerView.canScrollVertically(1)) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    if (lastPosition == RecyclerView.NO_POSITION) {
                        return
                    }
                    val lastView = layoutManager.findViewByPosition(lastPosition)
                    if (lastView == null) {
                        scrollToBottom()
                        return
                    }
                    val bottom =
                        abs(lastView.bottom - recyclerView.height) - recyclerView.paddingBottom
                    if (bottom <= 1) {
                        scrollToBottom()
                    }
                }
            }
        })
    }

    private fun initOtherView() {
        binding.llStop.applyNavigationBarMargin()
        binding.fbStartStop.setOnClickListener {
            if (viewModel.isSearchLiveData.value == true) {
                isManualStopSearch = true
                viewModel.stop()
            } else {
                viewModel.search("")
            }
        }
        binding.tvClearHistory.setOnClickListener { alertClearHistory() }
    }

    private fun initData() {
        viewModel.searchScope.stateLiveData.observe(this) {
            if (!binding.llInputHelp.isVisible) {
                searchView.text.toString().trim().let {
                    searchView.setText(it)
                }
            }
        }
        viewModel.isSearchLiveData.observe(this) {
            if (it) {
                startSearch()
            } else {
                searchFinally()
            }
        }
        viewModel.searchBookLiveData.observe(this) {
            adapter.setItems(it)
            if (it.isNullOrEmpty()) {
                binding.tvEmptyMsg.visible()
            } else {
                binding.tvEmptyMsg.gone()
            }
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().collect {
                groups = it
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.resume()
                try {
                    awaitCancellation()
                } finally {
                    viewModel.pause()
                }
            }
        }
    }

    /**
     * 处理传入数据
     */
    private fun receiptIntent(intent: Intent? = null) {
        val searchScope = intent?.getStringExtra("searchScope")
        searchScope?.let {
            viewModel.searchScope.update(searchScope, false)
            updateSearchScopeChip()
        }
        val key = intent?.getStringExtra("key")
        if (key.isNullOrBlank()) {
            searchView.editText.requestFocus()
        } else {
            searchView.setText(key)
            searchView.postDelayed({doSearch()},500)
        }
    }

    /**
     * 滚动到底部事件
     */
    private fun scrollToBottom() {
        if (isManualStopSearch) {
            return
        }
        if (viewModel.isSearchLiveData.value == false
            && viewModel.searchKey.isNotEmpty()
            && viewModel.hasMore
        ) {
            viewModel.search("")
        }
    }

    /**
     * 打开关闭输入帮助
     */
    private fun visibleInputHelp() {
        upHistory(searchView.text.toString())
        binding.llInputHelp.visibility = VISIBLE
    }

    /**
     * 更新搜索历史
     */
    private fun upHistory(key: String? = null) {
        booksFlowJob?.cancel()
        booksFlowJob = lifecycleScope.launch {
            if (key.isNullOrBlank()) {
                binding.tvBookShow.gone()
                binding.rvBookshelfSearch.gone()
            } else {
                appDb.bookDao.flowSearch(key).conflate().collect {
                    if (it.isEmpty()) {
                        binding.tvBookShow.gone()
                        binding.rvBookshelfSearch.gone()
                    } else {
                        binding.tvBookShow.visible()
                        binding.rvBookshelfSearch.visible()
                    }
                    bookAdapter.setItems(it)
                }
            }
        }
        historyFlowJob?.cancel()
        historyFlowJob = lifecycleScope.launch {
            when {
                key.isNullOrBlank() -> appDb.searchKeywordDao.flowByTime()
                else -> appDb.searchKeywordDao.flowSearch(key)
            }.catch {
                AppLog.put("搜索界面获取搜索历史数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                historyKeyAdapter.setItems(it)
                if (it.isEmpty()) {
                    binding.tvClearHistory.invisible()
                } else {
                    binding.tvClearHistory.visible()
                }
            }
        }
    }

    /**
     * 开始搜索
     */
    private fun startSearch() {
        binding.refreshProgressBar.visible()
        binding.cdProgress.visible()
        //binding.refreshProgressBar.isAutoLoading = true
        binding.fbStartStop.setImageResource(R.drawable.ic_stop_black_24dp)
        binding.fbStartStop.visible()
    }

    /**
     * 搜索结束
     */
    private fun searchFinally() {
        TransitionManager.beginDelayedTransition(binding.root)
        binding.refreshProgressBar.gone()
        binding.cdProgress.gone()
        if (!isManualStopSearch && viewModel.hasMore) {
            binding.fbStartStop.setImageResource(R.drawable.ic_play)
        } else {
            binding.fbStartStop.invisible()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun observeLiveBus() {
        viewModel.upAdapterLiveData.observe(this) {
            adapter.notifyItemRangeChanged(0, adapter.itemCount, bundleOf(it to null))
        }
        viewModel.searchProgressLiveData.observe(this) { (processed, total) ->
            val progress = (processed * 100 / total).coerceAtMost(100)
            binding.refreshProgressBar.setProgress(progress, true)
            binding.tvProgress.text = "$processed / $total"
        }
        viewModel.searchFinishLiveData.observe(this) { isEmpty ->
            if (!isEmpty || viewModel.searchScope.isAll()) return@observe
            alert("搜索结果为空") {
                val precisionSearch = appCtx.getPrefBoolean(PreferKey.precisionSearch)
                val displayScope = viewModel.searchScope.display
                if (precisionSearch) {
                    setMessage("${displayScope}分组搜索结果为空，是否关闭精准搜索？")
                    yesButton {
                        appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
                        precisionSearchMenuItem?.isChecked = false
                        viewModel.searchKey = ""
                        viewModel.search(searchView.text.toString())
                        doSearch()
                    }
                } else {
                    setMessage("${displayScope}分组搜索结果为空，是否切换到全部分组？")
                    yesButton {
                        viewModel.searchScope.update("")
                        doSearch()
                    }
                }
                noButton()
            }
        }
    }

    /**
     * 显示书籍详情
     */
    override fun showBookInfo(name: String, author: String, bookUrl: String) {
        startActivity<BookInfoActivity> {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
        }
    }

    /**
     * 是否已经加入书架
     */
    override fun getBookShelfState(name: String, author: String, url: String?): BookShelfState {
        return viewModel.isInBookShelf(name, author, url)
    }

    /**
     * 显示书籍详情
     */
    override fun showBookInfo(book: Book) {
        showBookInfo(book.name, book.author, book.bookUrl)
    }

    /**
     * 点击历史关键字
     */
    override fun searchHistory(key: String) {
        lifecycleScope.launch {
            when {
                searchView.text.toString() == key -> {
                    searchView.setText(key)
                }

                withContext(IO) { appDb.bookDao.findByName(key).isEmpty() } -> {
                    searchView.setText(key)
                }

                else -> {
                    searchView.setText(key)
                }
            }
        }
    }

    /**
     * 删除搜索记录
     */
    override fun deleteHistory(searchKeyword: SearchKeyword) {
        viewModel.deleteHistory(searchKeyword)
    }


    override fun onSearchScopeOk(searchScope: SearchScope) {
        viewModel.searchScope.update(searchScope.toString())
        updateSearchScopeChip()
    }

    private fun updateSearchScopeChip() {
        binding.chipSearchScope.isCheckable = true
        val scopeNames = viewModel.searchScope.displayNames
        if (scopeNames.isEmpty() || viewModel.searchScope.isAll()) {
            binding.chipSearchScope.apply {
                isChecked = false
                text = getString(R.string.all_source)
            }
        } else {
            binding.chipSearchScope.apply {
                isChecked = true
                text = scopeNames.joinToString(", ")
            }
        }
        binding.chipSearchScope.isCheckable = false
    }

    private fun alertSearchScope() {
        showDialogFragment<SearchScopeDialog>()
    }

    private fun alertClearHistory() {
        alert(R.string.draw) {
            setMessage(R.string.sure_clear_search_history)
            yesButton {
                viewModel.clearHistory()
            }
            noButton()
        }
    }

    override fun finish() {
        if (searchView.hasFocus()) {
            searchView.clearFocus()
        }
        super.finish()
    }

    companion object {

        fun start(context: Context, key: String?) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
            }
        }

    }
}