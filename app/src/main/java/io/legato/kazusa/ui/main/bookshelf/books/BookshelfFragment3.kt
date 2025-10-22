@file:Suppress("DEPRECATION")

package io.legato.kazusa.ui.main.bookshelf.books

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import io.legato.kazusa.R
import io.legato.kazusa.data.appDb
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookGroup
import io.legato.kazusa.databinding.FragmentBookshelf3Binding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.ui.book.search.SearchActivity
import io.legato.kazusa.ui.main.bookshelf.BaseBookshelfFragment
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set

/**
 * 书架界面
 */
class BookshelfFragment3() : BaseBookshelfFragment(R.layout.fragment_bookshelf3),
    TabLayout.OnTabSelectedListener,
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf3Binding::bind)

    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    private lateinit var adapter: TabFragmentPageAdapter

    override val books: List<Book>
        get() {
            val fragment = fragmentMap[groupId]
            return fragment?.getBooks() ?: emptyList()
        }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.topBar)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(binding.viewPagerBookshelf.currentItem)

    private fun initView() {
        adapter = TabFragmentPageAdapter(this)
        binding.viewPagerBookshelf.adapter = adapter
        binding.viewPagerBookshelf.offscreenPageLimit = 1

        binding.viewPagerBookshelf.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateToolbarTitle(position)
            }
        })
    }

    private fun updateToolbarTitle(position: Int) {
        if (bookGroups.isNotEmpty() && position in 0 until bookGroups.size) {
            binding.collTopBar.title = bookGroups[position].groupName
        }
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    @SuppressLint("NotifyDataSetChanged")
    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
        } else {
            if (data != bookGroups) {
                bookGroups.clear()
                bookGroups.addAll(data)
                adapter.notifyDataSetChanged()
                adapter = TabFragmentPageAdapter(this)
                binding.viewPagerBookshelf.adapter = adapter

                updateToolbarTitle(binding.viewPagerBookshelf.currentItem)
            }
        }
    }

    override fun upSort() {
        childFragmentManager.fragments.forEach {
            if (it is BooksFragment) {
                val position = it.position
                val group = bookGroups.getOrNull(position) ?: return@forEach
                it.setEnableRefresh(group.enableRefresh)
                it.upBookSort(group.getRealBookSort())
            }
        }
    }

    override fun onTabReselected(tab: TabLayout.Tab) {
    }

    override fun onTabUnselected(tab: TabLayout.Tab) = Unit

    override fun onTabSelected(tab: TabLayout.Tab) {
        AppConfig.saveTabPosition = tab.position
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    private inner class TabFragmentPageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = bookGroups.size

        override fun createFragment(position: Int): Fragment {
            val group = bookGroups[position]
            val fragment = BooksFragment(position, group)
            fragmentMap[group.groupId] = fragment
            return fragment
        }
    }
}