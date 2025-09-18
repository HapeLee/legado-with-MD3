@file:Suppress("DEPRECATION")

package io.legato.kazusa.ui.book.toc

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.google.android.material.tabs.TabLayout
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.databinding.ActivityChapterListBinding
import io.legato.kazusa.databinding.DialogDownloadChoiceBinding
import io.legato.kazusa.help.book.isLocalTxt
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.model.CacheBook
//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.primaryTextColor
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.ui.about.AppLogDialog
import io.legato.kazusa.ui.book.toc.rule.TxtTocRuleDialog
import io.legato.kazusa.ui.file.HandleFileContract
import io.legato.kazusa.ui.widget.dialog.WaitDialog
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.hideSoftInput
import io.legato.kazusa.utils.shouldHideSoftInput
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible

/**
 * 目录
 */
class TocActivity : VMBaseActivity<ActivityChapterListBinding, TocViewModel>(),
    TxtTocRuleDialog.CallBack {

    override val binding by viewBinding(ActivityChapterListBinding::inflate)
    override val viewModel by viewModels<TocViewModel>()

    private lateinit var tabLayout: TabLayout
    private var menu: Menu? = null
    private var searchView: SearchView? = null
    private val waitDialog by lazy { WaitDialog(this) }
    private val exportDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                1 -> viewModel.saveBookmark(uri)
                2 -> viewModel.saveBookmarkMd(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.topBar)
        tabLayout = binding.tabLayout
        binding.viewPager.adapter = TabFragmentPageAdapter()
        tabLayout.setupWithViewPager(binding.viewPager)
        viewModel.bookData.observe(this) { book ->
            menu?.setGroupVisible(R.id.menu_group_text, book.isLocalTxt)
            supportActionBar?.title = book.name
        }
        intent.getStringExtra("bookUrl")?.let {
            viewModel.initBook(it)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it.shouldHideSoftInput(ev)) {
                    it.hideSoftInput()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_toc, menu)
        this.menu = menu
        val search = menu.findItem(R.id.menu_search)
        searchView = (search.actionView as SearchView).apply {
            //applyTint(primaryTextColor)
            maxWidth = resources.displayMetrics.widthPixels
            onActionViewCollapsed()
            setOnCloseListener {
                tabLayout.visible()
                false
            }
            setOnSearchClickListener { tabLayout.gone() }
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    viewModel.searchKey = query
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    viewModel.searchKey = newText
                    if (tabLayout.selectedTabPosition == 1) {
                        viewModel.startBookmarkSearch(newText)
                    } else {
                        viewModel.startChapterListSearch(newText)
                    }
                    return false
                }
            })
            setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    searchView?.isIconified = true
                }
            }
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        if (tabLayout.selectedTabPosition == 1) {
            menu.setGroupVisible(R.id.menu_group_bookmark, true)
            menu.setGroupVisible(R.id.menu_group_toc, false)
            menu.setGroupVisible(R.id.menu_group_text, false)
        } else {
            menu.setGroupVisible(R.id.menu_group_bookmark, false)
            menu.setGroupVisible(R.id.menu_group_toc, true)
            menu.setGroupVisible(R.id.menu_group_text, viewModel.bookData.value?.isLocalTxt == true)
        }
        menu.findItem(R.id.menu_use_replace)?.isChecked =
            AppConfig.tocUiUseReplace
        menu.findItem(R.id.menu_load_word_count)?.isChecked =
            AppConfig.tocCountWords
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() == true
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_toc_regex -> showDialogFragment(
                TxtTocRuleDialog(viewModel.bookData.value?.tocUrl)
            )

            R.id.menu_download -> showDownloadDialog()

            R.id.menu_split_long_chapter -> {
                viewModel.bookData.value?.let { book ->
                    item.isChecked = !item.isChecked
                    book.setSplitLongChapter(item.isChecked)
                    upBookAndToc(book)
                }
            }

            R.id.menu_reverse_toc -> viewModel.reverseToc {
                viewModel.chapterListCallBack?.upChapterList(searchView?.query?.toString())
                setResult(RESULT_OK, Intent().apply {
                    putExtra("index", it.durChapterIndex)
                    putExtra("chapterPos", 0)
                })
            }

            R.id.menu_use_replace -> {
                AppConfig.tocUiUseReplace = !item.isChecked
                viewModel.chapterListCallBack?.clearDisplayTitle()
                viewModel.chapterListCallBack?.upChapterList(searchView?.query?.toString())
            }

            R.id.menu_load_word_count -> {
                AppConfig.tocCountWords = !item.isChecked
                viewModel.upChapterListAdapter()
            }

            R.id.menu_export_bookmark -> exportDir.launch {
                requestCode = 1
            }

            R.id.menu_export_md -> exportDir.launch {
                requestCode = 2
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onTocRegexDialogResult(tocRegex: String) {
        viewModel.bookData.value?.let { book ->
            book.tocUrl = tocRegex
            upBookAndToc(book)
        }
    }

    private fun upBookAndToc(book: Book) {
        waitDialog.show()
        viewModel.upBookTocRule(book) {
            waitDialog.dismiss()
            if (ReadBook.book == book) {
                if (it == null) {
                    ReadBook.upMsg(null)
                } else {
                    ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                }
            }
        }
    }

    fun locateToChapter(index: Int) {
        val tag = "android:switcher:${binding.viewPager.id}:0"
        val chapterFragment = supportFragmentManager.findFragmentByTag(tag) as? ChapterListFragment
        chapterFragment?.scrollToChapter(index)

        binding.viewPager.currentItem = 0
    }


    @SuppressLint("SetTextI18n")
    fun showDownloadDialog() {
        ReadBook.book?.let { book ->
            alert(titleResource = R.string.offline_cache) {
                val alertBinding = DialogDownloadChoiceBinding.inflate(layoutInflater).apply {
                    editStart.setText((book.durChapterIndex + 1).toString())
                    editEnd.setText(book.totalChapterNum.toString())
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.run {
                        val start = editStart.text!!.toString().let {
                            if (it.isEmpty()) 0 else it.toInt()
                        }
                        val end = editEnd.text!!.toString().let {
                            if (it.isEmpty()) book.totalChapterNum else it.toInt()
                        }
                        val indices = (start - 1..end - 1).toList()
                        CacheBook.start(this@TocActivity, book, indices)
                    }
                }
                cancelButton()
            }
        }
    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter :
        FragmentPagerAdapter(supportFragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            return when (position) {
                1 -> BookmarkFragment()
                else -> ChapterListFragment()
            }
        }

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(position: Int): CharSequence {
            return when (position) {
                1 -> getString(R.string.bookmark)
                else -> getString(R.string.chapter_list)
            }
        }

    }

}