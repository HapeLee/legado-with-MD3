@file:Suppress("DEPRECATION")

package io.legado.app.ui.rss.article

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssArtivlesBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.read.ReadRssActivity.RedirectPolicy
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssSortActivity : VMBaseActivity<ActivityRssArtivlesBinding, RssSortViewModel>(),
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssArtivlesBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()

    private var adapter: TabFragmentPageAdapter? = null
    private val sortList = mutableListOf<Pair<String, String>>()
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.initData(intent) {
                upFragments()
            }
        }
    }

    var redirectPolicy: RedirectPolicy = RedirectPolicy.ALLOW_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.topBar)

        viewModel.titleLiveData.observe(this) {
            binding.ctlBar.title = it
        }

        viewModel.initData(intent) {
            upFragments()
            redirectPolicy = RedirectPolicy.fromString(viewModel.rssSource?.redirectPolicy)
        }

        binding.titleBar.addOnOffsetChangedListener({ appBarLayout, verticalOffset ->
            if (-verticalOffset >= appBarLayout.totalScrollRange) {
                val drawable = binding.tabRightFade.background as? GradientDrawable
                drawable?.colors = intArrayOf(Color.TRANSPARENT,
                    themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
            } else {
                val drawable = binding.tabRightFade.background as? GradientDrawable
                drawable?.colors = intArrayOf(Color.TRANSPARENT,
                    themeColor(com.google.android.material.R.attr.colorSurface))
            }
        })

    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_articles, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.rssSource?.loginUrl.isNullOrBlank()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }

            R.id.menu_refresh_sort -> viewModel.clearSortCache { upFragments() }

            R.id.menu_set_source_variable -> setSourceVariable()

            R.id.menu_edit_source -> viewModel.rssSource?.sourceUrl?.let {
                editSourceResult.launch {
                    putExtra("sourceUrl", it)
                }
            }
            R.id.menu_redirect_policy ->
                showRedirectPolicySubMenu()
            R.id.menu_clear -> {
                viewModel.url?.let {
                    viewModel.clearArticles()
                }
            }

            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                upFragments()
            }

            R.id.menu_read_record -> {
                showDialogFragment<ReadRecordDialog>()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun showRedirectPolicySubMenu() {
        val popup = PopupMenu(this, binding.titleBar, Gravity.END)
        popup.menuInflater.inflate(R.menu.menu_redirect_policy_submenu, popup.menu)
        setCurrentRedirectPolicyChecked(popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            handleRedirectPolicySelection(menuItem)
            true
        }
        popup.show()
    }

    private fun setCurrentRedirectPolicyChecked(menu: Menu) {
        val currentPolicy = redirectPolicy
        val menuItemId = when (currentPolicy) {
            RedirectPolicy.ALLOW_ALL -> R.id.menu_redirect_allow_all
            RedirectPolicy.ASK_ALWAYS -> R.id.menu_redirect_ask_always
            RedirectPolicy.ASK_CROSS_ORIGIN -> R.id.menu_redirect_ask_cross_origin
            RedirectPolicy.BLOCK_CROSS_ORIGIN -> R.id.menu_redirect_block_cross_origin
            RedirectPolicy.BLOCK_ALL -> R.id.menu_redirect_block_all
            RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS -> R.id.menu_redirect_ask_same_domain_block_cross
        }
        menu.findItem(menuItemId)?.isChecked = true
    }

    private fun handleRedirectPolicySelection(menuItem: MenuItem) {
        val selectedPolicy = when (menuItem.itemId) {
            R.id.menu_redirect_allow_all -> RedirectPolicy.ALLOW_ALL
            R.id.menu_redirect_ask_always -> RedirectPolicy.ASK_ALWAYS
            R.id.menu_redirect_ask_cross_origin -> RedirectPolicy.ASK_CROSS_ORIGIN
            R.id.menu_redirect_block_cross_origin -> RedirectPolicy.BLOCK_CROSS_ORIGIN
            R.id.menu_redirect_block_all -> RedirectPolicy.BLOCK_ALL
            R.id.menu_redirect_ask_same_domain_block_cross -> RedirectPolicy.ASK_SAME_DOMAIN_BLOCK_CROSS
            else -> RedirectPolicy.ALLOW_ALL
        }

        updateRedirectPolicy(selectedPolicy)
        toastOnUi("重定向策略已更新")
    }

    fun updateRedirectPolicy(policy: RedirectPolicy) {
        viewModel.rssSource?.let { source ->
            viewModel.updateRssSourceRedirectPolicy(source.sourceUrl, policy.name)
            redirectPolicy = policy
        }
    }

    private fun upFragments() {
        lifecycleScope.launch {
            val sorts = viewModel.rssSource?.sortUrls().orEmpty()

            sortList.clear()
            sortList.addAll(sorts)

            if (sortList.size <= 1) {
                binding.llTab.gone()
            } else {
                binding.llTab.visible()
            }

            adapter = TabFragmentPageAdapter(this@RssSortActivity, sortList)
            binding.viewPager.adapter = adapter

            // 重新绑定 TabLayout
            TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
                tab.text = sortList[position].first
            }.attach()
        }

        binding.btnExpandTabs.setOnClickListener {
            val popup = PopupMenu(this, it)
            sortList.forEachIndexed { index, pair ->
                popup.menu.add(0, index, index, pair.first)
            }
            popup.setOnMenuItemClickListener { item ->
                binding.viewPager.setCurrentItem(item.itemId, true)
                true
            }
            popup.setOnDismissListener {
                binding.btnExpandTabs.isChecked = false
            }
            popup.show()
        }

    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.rssSource
            if (source == null) {
                toastOnUi("源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    private class TabFragmentPageAdapter(
        activity: FragmentActivity,
        private val items: List<Pair<String, String>>
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = items.size

        override fun createFragment(position: Int): Fragment {
            val sort = items[position]
            return RssArticlesFragment(sort.first, sort.second)
        }
    }
}
