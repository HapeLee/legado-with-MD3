package io.legado.app.ui.rss.source.debug

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivitySourceDebugBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.gone
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch
import splitties.views.onClick
import splitties.views.onLongClick


class RssSourceDebugActivity : VMBaseActivity<ActivitySourceDebugBinding, RssSourceDebugModel>() {

    override val binding by viewBinding(ActivitySourceDebugBinding::inflate)
    override val viewModel by viewModels<RssSourceDebugModel>()

    private val adapter by lazy { RssSourceDebugAdapter(this) }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initRecyclerView()
        initSearchView()
        initHelpView()
        viewModel.observe { state, msg ->
            lifecycleScope.launch {
                adapter.addItem(msg)
                if (state == -1 || state == 1000) {
                    binding.rotateLoading.gone()
                }
            }
        }
        viewModel.initData(intent.getStringExtra("key")) {
            // initData 完成后的回调
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_source_debug, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_list_src -> showDialogFragment(TextDialog("Html", viewModel.listSrc))
            R.id.menu_content_src -> showDialogFragment(TextDialog("Html", viewModel.contentSrc))
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.adapter = adapter
        binding.recyclerView.applyNavigationBarPadding()
    }

    private fun initSearchView() {
        val searchView = binding.titleBar.findViewById<SearchView>(R.id.search_view)
        searchView.visible()
        searchView.isSubmitButtonEnabled = true
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                startDebug(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    @SuppressLint("SetTextI18n")
    private fun initHelpView() {
        binding.help.visible()
        binding.textMy.onClick {
            binding.titleBar.findViewById<SearchView>(R.id.search_view).setQuery(binding.textMy.text, true)
        }
        binding.textXt.onClick {
            binding.titleBar.findViewById<SearchView>(R.id.search_view).setQuery(binding.textXt.text, true)
        }
        binding.textFx.onClick {
            if (!binding.textFx.text.startsWith("ERROR:")) {
                binding.titleBar.findViewById<SearchView>(R.id.search_view).setQuery(binding.textFx.text, true)
            }
        }
        binding.textContent.onClick {
            val query = binding.titleBar.findViewById<SearchView>(R.id.search_view).query
            if (!query.isNullOrBlank()) {
                binding.titleBar.findViewById<SearchView>(R.id.search_view).setQuery(query, true)
            }
        }
        initSortKinds()
    }

    private fun initSortKinds() {
        lifecycleScope.launch {
            val sortKinds = viewModel.rssSource?.sortUrls()?.filter {
                it.second.isNotBlank()
            }
            sortKinds?.firstOrNull()?.let {
                binding.textFx.text = "${it.first}::${it.second}"
                if (it.first.startsWith("ERROR:")) {
                    adapter.addItem("获取发现出错\n${it.second}")
                    binding.titleBar.findViewById<SearchView>(R.id.search_view).clearFocus()
                    return@launch
                }
            }
        }
    }

    private fun startDebug(key: String) {
        adapter.clearItems()
        binding.help.gone() // 关闭调试选项面板
        viewModel.rssSource?.let {
            binding.rotateLoading.visible()
            viewModel.startDebug(it, key)
        } ?: toastOnUi(R.string.error_no_source)
    }
}