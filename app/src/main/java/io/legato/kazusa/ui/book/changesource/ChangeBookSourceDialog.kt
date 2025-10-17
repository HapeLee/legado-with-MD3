package io.legato.kazusa.ui.book.changesource

//import io.legado.app.lib.theme.getPrimaryTextColor
//import io.legado.app.lib.theme.primaryColor
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legato.kazusa.R
import io.legato.kazusa.base.BaseBottomSheetDialogFragment
import io.legato.kazusa.constant.AppLog
import io.legato.kazusa.constant.BookType
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.data.entities.BookSource
import io.legato.kazusa.data.entities.SearchBook
import io.legato.kazusa.databinding.DialogBookChangeSourceBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.ui.book.source.edit.BookSourceEditActivity
import io.legato.kazusa.ui.book.source.manage.BookSourceActivity
import io.legato.kazusa.ui.widget.dialog.WaitDialog
import io.legato.kazusa.ui.widget.recycler.VerticalDivider
import io.legato.kazusa.utils.StartActivityContract
import io.legato.kazusa.utils.applyTint
import io.legato.kazusa.utils.dpToPx
import io.legato.kazusa.utils.getCompatDrawable
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.transaction
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 换源界面
 */
class ChangeBookSourceDialog() : BaseBottomSheetDialogFragment(R.layout.dialog_book_change_source),
    Toolbar.OnMenuItemClickListener,
    ChangeBookSourceAdapter.CallBack {

    constructor(name: String, author: String) : this() {
        arguments = Bundle().apply {
            putString("name", name)
            putString("author", author)
        }
    }

    private val binding by viewBinding(DialogBookChangeSourceBinding::bind)
    private val groups = linkedSetOf<String>()
    private val callBack: CallBack? get() = activity as? CallBack
    private val viewModel: ChangeBookSourceViewModel by viewModels()
    private val waitDialog by lazy { WaitDialog(requireContext()) }
    private val adapter by lazy { ChangeBookSourceAdapter(requireContext(), viewModel, this) }
    private val editSourceResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            val origin = it.data?.getStringExtra("origin") ?: return@registerForActivityResult
            viewModel.startSearch(origin)
        }
    private var currentSelectedSearchBook: SearchBook? = null
    private val searchFinishCallback: (isEmpty: Boolean) -> Unit = {
        if (it) {
            val searchGroup = AppConfig.searchGroup
            if (searchGroup.isNotEmpty()) {
                lifecycleScope.launch {
                    context?.alert("搜索结果为空") {
                        setMessage("${searchGroup}分组搜索结果为空,是否切换到全部分组")
                        cancelButton()
                        okButton {
                            AppConfig.searchGroup = ""
                            upGroupMenuName()
                            viewModel.startSearch()
                        }
                    }
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        //binding.toolBar.setBackgroundColor(primaryColor)
        viewModel.initData(arguments, callBack?.oldBook, activity is ReadBookActivity)
        showTitle()
        initMenu()
        initRecyclerView()
        initNavigationView()
        initSearchView()
        initBottomBar()
        initLiveData()
        viewModel.searchFinishCallback = searchFinishCallback
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.searchFinishCallback = null
    }

    private fun showTitle() {
        binding.toolBar.title = viewModel.name
        binding.toolBar.subtitle = viewModel.author
        binding.toolBar.navigationIcon =
            getCompatDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        //binding.toolBar.elevation = requireContext().elevation
    }

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.change_source)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.menu.findItem(R.id.menu_check_author)
            ?.isChecked = AppConfig.changeSourceCheckAuthor
        binding.toolBar.menu.findItem(R.id.menu_load_info)
            ?.isChecked = AppConfig.changeSourceLoadInfo
        binding.toolBar.menu.findItem(R.id.menu_load_toc)
            ?.isChecked = AppConfig.changeSourceLoadToc
        binding.toolBar.menu.findItem(R.id.menu_load_word_count)
            ?.isChecked = AppConfig.changeSourceLoadWordCount
    }

    private fun initRecyclerView() {
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (positionStart == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                if (toPosition == 0) {
                    binding.recyclerView.scrollToPosition(0)
                }
            }
        })
    }

    private fun initSearchView() {
        val searchView = binding.toolBar.menu.findItem(R.id.menu_screen).actionView as SearchView
        searchView.setOnCloseListener {
            showTitle()
            false
        }
        searchView.setOnSearchClickListener {
            binding.toolBar.title = ""
            binding.toolBar.subtitle = ""
            binding.toolBar.navigationIcon = null
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.screen(newText)
                return false
            }

        })
    }

    private fun initNavigationView() {
        binding.toolBar.navigationIcon =
            getCompatDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolBar.setNavigationContentDescription(
            androidx.appcompat.R.string.abc_action_bar_up_description
        )
        binding.toolBar.setNavigationOnClickListener {
            dismissAllowingStateLoss()
        }
        kotlin.runCatching {
            val mNavButtonViewField = Toolbar::class.java.getDeclaredField("mNavButtonView")
            mNavButtonViewField.isAccessible = true
            //val navigationView = mNavButtonViewField.get(binding.toolBar) as ImageButton
//            val isLight = ColorUtils.isColorLight(primaryColor)
//            val textColor = requireContext().getPrimaryTextColor(isLight)
            //navigationView.setColorFilter(textColor)
        }
    }

    private fun initBottomBar() {
        binding.tvDur.text = callBack?.oldBook?.originName
        binding.tvDur.setOnClickListener {
            scrollToDurSource()
        }
        binding.ivTop.setOnClickListener {
            binding.recyclerView.scrollToPosition(0)
        }
        binding.ivBottom.setOnClickListener {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initLiveData() {
        viewModel.searchStateData.observe(viewLifecycleOwner) {
            binding.refreshProgressBar.isVisible = it

            if (it) {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_stop_black_24dp)
                    item.setTitle(R.string.stop)
                }
            } else {
                startStopMenuItem?.let { item ->
                    item.setIcon(R.drawable.ic_refresh)
                    item.setTitle(R.string.refresh)
                }
            }
            //binding.toolBar.menu.applyTint(requireContext())
        }
        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.searchDataFlow.conflate().collect {
                    adapter.setItems(it)

                    binding.tvEmptyMsg.isVisible = it.isEmpty()
                    binding.recyclerView.isVisible = it.isNotEmpty()

                    delay(1000)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(STARTED) {
                viewModel.changeSourceProgress
                    .drop(1)
                    .collect { (count, name) ->
                        val total = viewModel.totalSourceCount
                        val progress = if (total > 0) (count * 100 / total) else 0

                        binding.refreshProgressBar.isIndeterminate = false
                        binding.refreshProgressBar.progress = progress
                        binding.llInfo.visible()

                        binding.tvProgress.text = "$count / $total"
                        binding.tvResult.text =
                            getString(R.string.change_source_progress, adapter.itemCount)
                    }
            }
        }

        lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups().conflate().collect {
                groups.clear()
                groups.addAll(it)
                upGroupMenu()
            }
        }
    }

    private val startStopMenuItem: MenuItem?
        get() = binding.toolBar.menu.findItem(R.id.menu_start_stop)

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_check_author -> {
                AppConfig.changeSourceCheckAuthor = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.refresh()
            }

            R.id.menu_load_info -> {
                AppConfig.changeSourceLoadInfo = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_toc -> {
                AppConfig.changeSourceLoadToc = !item.isChecked
                item.isChecked = !item.isChecked
            }

            R.id.menu_load_word_count -> {
                AppConfig.changeSourceLoadWordCount = !item.isChecked
                item.isChecked = !item.isChecked
                viewModel.onLoadWordCountChecked(item.isChecked)
            }

            R.id.menu_start_stop -> viewModel.startOrStopSearch()
            R.id.menu_source_manage -> startActivity<BookSourceActivity>()
            R.id.menu_close -> dismissAllowingStateLoss()
            R.id.menu_refresh_list -> viewModel.startRefreshList()
            else -> if (item?.groupId == R.id.source_group && !item.isChecked) {
                item.isChecked = true
                if (item.title.toString() == getString(R.string.all_source)) {
                    AppConfig.searchGroup = ""
                } else {
                    AppConfig.searchGroup = item.title.toString()
                }
                upGroupMenuName()
                lifecycleScope.launch(IO) {
                    viewModel.stopSearch()
                    if (viewModel.refresh()) {
                        viewModel.startSearch()
                    }
                }
            }
        }
        return false
    }

    private fun scrollToDurSource() {
        adapter.getItems().forEachIndexed { index, searchBook ->
            if (searchBook.bookUrl == oldBookUrl) {
                (binding.recyclerView.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(index, 60.dpToPx())
                return
            }
        }
    }

    override fun changeTo(searchBook: SearchBook) {
        currentSelectedSearchBook = searchBook
        showChangeSourceOptionDialog(searchBook)
    }

    private fun showChangeSourceOptionDialog(searchBook: SearchBook) {
        val oldBookType = callBack?.oldBook?.type ?: 0

        if (!searchBook.sameBookTypeLocal(oldBookType)) {
            alert(
                titleResource = R.string.book_type_different,
                messageResource = R.string.soure_change_source
            ) {
                okButton {
                    showChangeSourceActionDialog(searchBook)
                }
                cancelButton()
            }
        } else {
            showChangeSourceActionDialog(searchBook)
        }
    }

    private fun showChangeSourceActionDialog(searchBook: SearchBook) {
        context?.alert(getString(R.string.change_source_option_title)) {
            positiveButton(getString(R.string.replace_current_book)) {
                performChangeSource(searchBook, true)
            }
            negativeButton(getString(R.string.add_as_new_book)) {
                performChangeSource(searchBook, false)
            }
        }
    }

    private fun performChangeSource(searchBook: SearchBook, isReplace: Boolean) {
        val baseBook = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        val book = baseBook

        waitDialog.setText(if (isReplace) R.string.load_toc else R.string.load_info)
        waitDialog.show()

        val coroutine = viewModel.getToc(book, { toc, source ->
            waitDialog.dismiss()
            if (isReplace) {

                callBack?.changeTo(source, book, toc)
                dismissAllowingStateLoss()
            } else {
                ReadBook.book?.migrateTo(book, toc)
                callBack?.addToBookshelf(book, toc)
                context?.toastOnUi(getString(R.string.book_added_to_shelf))
            }
        }, {
            waitDialog.dismiss()
            AppLog.put("${if (isReplace) "换源" else "添加书籍"}获取目录出错\n$it", it, true)
            context?.toastOnUi("${if (isReplace) "换源" else "添加书籍"}失败")
        })

        waitDialog.setOnCancelListener {
            coroutine.cancel()
        }
    }

    override val oldBookUrl: String?
        get() = callBack?.oldBook?.bookUrl

    override fun topSource(searchBook: SearchBook) {
        viewModel.topSource(searchBook)
    }

    override fun bottomSource(searchBook: SearchBook) {
        viewModel.bottomSource(searchBook)
    }

    override fun editSource(searchBook: SearchBook) {
        editSourceResult.launch {
            putExtra("sourceUrl", searchBook.origin)
        }
    }

    override fun disableSource(searchBook: SearchBook) {
        viewModel.disableSource(searchBook)
    }

    override fun deleteSource(searchBook: SearchBook) {
        viewModel.del(searchBook)
        if (oldBookUrl == searchBook.bookUrl) {
            viewModel.autoChangeSource(callBack?.oldBook?.type) { book, toc, source ->
                callBack?.changeTo(source, book, toc)
            }
        }
    }

    override fun setBookScore(searchBook: SearchBook, score: Int) {
        viewModel.setBookScore(searchBook, score)
    }

    override fun getBookScore(searchBook: SearchBook): Int {
        return viewModel.getBookScore(searchBook)
    }

    private fun changeSource(searchBook: SearchBook, onSuccess: (() -> Unit)? = null) {
        waitDialog.setText(R.string.load_toc)
        waitDialog.show()
        val book = viewModel.bookMap[searchBook.primaryStr()] ?: searchBook.toBook()
        val coroutine = viewModel.getToc(book, { toc, source ->
            waitDialog.dismiss()
            callBack?.changeTo(source, book, toc)
            onSuccess?.invoke()
        }, {
            waitDialog.dismiss()
            AppLog.put("换源获取目录出错\n$it", it, true)
        })
        waitDialog.setOnCancelListener {
            coroutine.cancel()
        }
    }

    /**
     * 更新分组菜单
     */
    private fun upGroupMenu() {
        binding.toolBar.menu.findItem(R.id.menu_group)?.run {
            subMenu?.transaction { menu ->
                val selectedGroup = AppConfig.searchGroup
                menu.removeGroup(R.id.source_group)
                val allItem = menu.add(R.id.source_group, Menu.NONE, Menu.NONE, R.string.all_source)
                var hasSelectedGroup = false
                groups.forEach { group ->
                    menu.add(R.id.source_group, Menu.NONE, Menu.NONE, group)?.let {
                        if (group == selectedGroup) {
                            it.isChecked = true
                            hasSelectedGroup = true
                        }
                    }
                }
                menu.setGroupCheckable(R.id.source_group, true, true)
                if (hasSelectedGroup) {
                    title = getString(R.string.group) + "(" + AppConfig.searchGroup + ")"
                } else {
                    allItem.isChecked = true
                    title = getString(R.string.group)
                }
            }
        }
    }

    /**
     * 更新分组菜单名
     */
    private fun upGroupMenuName() {
        val menuGroup = binding.toolBar.menu.findItem(R.id.menu_group)
        val searchGroup = AppConfig.searchGroup
        if (searchGroup.isEmpty()) {
            menuGroup?.title = getString(R.string.group)
        } else {
            menuGroup?.title = getString(R.string.group) + "($searchGroup)"
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.SOURCE_CHANGED) {
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("upCurSource", oldBookUrl))
            )
        }
    }

    interface CallBack {
        val oldBook: Book?
        fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>)
        fun addToBookshelf(book: Book, toc: List<BookChapter>)
    }

}