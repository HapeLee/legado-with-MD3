package io.legado.app.ui.main.explore

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy {
        ExploreAdapter(requireContext(), this)
    }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView by lazy { binding.searchBar }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.topBar)
        initSearchView()
        initRecyclerView()
        initGroupData()
        upExploreData()
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S)
            binding.appBar.fitsSystemWindows = true
    }

    override fun onPause() {
        super.onPause()
        searchView.clearFocus()
    }

    private fun initSearchView() {
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                upExploreData(query)
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                upExploreData(newText)
                return true
            }
        })

    }

    private fun initRecyclerView() {
        //binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                }
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            val query = searchKey ?: searchView.query?.toString() ?: ""

            when {
                query.isBlank() -> {
                    appDb.bookSourceDao.flowExplore()
                }
                query.startsWith("group:") -> {
                    val key = query.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupExplore(key)
                }
                else -> {
                    appDb.bookSourceDao.flowExplore(query)
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                binding.emptyView.isGone = it.isNotEmpty() || query.isNotEmpty()
                adapter.setItems(it, diffItemCallBack)
            }
        }
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, getString(R.string.all))
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }


    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        if (item.groupId == R.id.menu_group_text) {
            val title = item.title.toString()
            if (title == getString(R.string.all)) {
                searchView.setQuery("", false)
                upExploreData("")
            } else {
                searchView.setQuery(title, false)
            }
        } else if (item.itemId == R.id.menu_exp_search) {
            TransitionManager.beginDelayedTransition(binding.rootView)
            if (searchView.isVisible) {
                searchView.isVisible = false
                searchView.clearFocus()
            } else {
                searchView.isVisible = true
                searchView.requestFocus()
            }
        }
    }


    override fun scrollTo(pos: Int) {
        (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        startActivity<SearchActivity> {
            putExtra("searchScope", SearchScope(bookSource).toString())
        }
    }

    fun compressExplore() {
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) {
                binding.rvFind.scrollToPosition(0)
            } else {
                binding.rvFind.smoothScrollToPosition(0)
            }
        }
    }

}
