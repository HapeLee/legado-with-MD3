@file:Suppress("DEPRECATION")

package io.legato.kazusa.ui.main

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomnavigation.LabelVisibilityMode
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.legato.kazusa.BuildConfig
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.constant.AppConst.appInfo
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.databinding.ActivityMainBinding
import io.legato.kazusa.help.AppWebDav
import io.legato.kazusa.help.book.BookHelp
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.LocalConfig
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.storage.Backup
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.service.BaseReadAloudService
import io.legato.kazusa.ui.about.CrashLogsDialog
import io.legato.kazusa.ui.book.read.ReadBookActivity
import io.legato.kazusa.ui.book.search.SearchActivity
import io.legato.kazusa.ui.main.bookshelf.BaseBookshelfFragment
import io.legato.kazusa.ui.main.bookshelf.books.BookshelfFragment1
import io.legato.kazusa.ui.main.bookshelf.books.BookshelfFragment2
import io.legato.kazusa.ui.main.bookshelf.books.BookshelfFragment3
import io.legato.kazusa.ui.main.explore.ExploreFragment
import io.legato.kazusa.ui.main.my.MyFragment
import io.legato.kazusa.ui.main.rss.RssFragment
import io.legato.kazusa.ui.welcome.WelcomeActivity
import io.legato.kazusa.ui.widget.dialog.TextDialog
import io.legato.kazusa.utils.getPrefBoolean
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.hideSoftInput
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.setNavigationBarColorAuto
import io.legato.kazusa.utils.shouldHideSoftInput
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.themeColor
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 主界面
 */
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idBookshelf3 = 13
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 4
    private val realPositions = arrayOf(idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(this)
    }
    private lateinit var backCallback: OnBackPressedCallback
    private val badge by lazy {
        getNavigationBarView().getOrCreateBadge(R.id.menu_bookshelf)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    private fun setupBackCallback() {
        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (pagePosition != 0) {
                    binding.viewPagerMain.currentItem = 0
                    return
                }

                val fragment = fragmentMap[getFragmentId(0)] as? BookshelfFragment2
                if (fragment?.back() == true) {
                    return
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (checkStartupRoute()) return
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())

        if (savedInstanceState != null) {
            pagePosition = savedInstanceState.getInt("currentPagePosition", 0)
        }

        // 其他初始化逻辑
        setupBackCallback()
        upBottomMenu()
        initView()
        upHomePage()
        bindNavigationListener()
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

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //版本更新
            upVersion()
            //设置本地密码
            notifyAppCrash()
            //备份同步
            backupSync()
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                binding.viewPagerMain.postDelayed(1000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        val index = when (item.itemId) {
            R.id.menu_bookshelf -> 0
            R.id.menu_discovery -> realPositions.indexOf(idExplore)
            R.id.menu_rss -> realPositions.indexOf(idRss)
            R.id.menu_my_config -> realPositions.indexOf(idMy)
            else -> 0
        }
        binding.viewPagerMain.setCurrentItem(index, true)
        return true
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[1] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun checkStartupRoute(): Boolean {
        return when {
            LocalConfig.isFirstOpenApp -> {
                startActivity<WelcomeActivity>()
                finish()
                true
            }
            getPrefBoolean(PreferKey.defaultToRead) -> {
                startActivity<ReadBookActivity>()
                false
            }
            else -> false
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.registerOnPageChangeCallback(PageChangeCallback())

        window.setNavigationBarColorAuto(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCoroutine { block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            block.resume(null)
            return@suspendCoroutine
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentPagePosition", pagePosition)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        (fragmentMap[getFragmentId(0)] as? BaseBookshelfFragment)?.run {
            upSort()
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        viewModel.onUpBooksLiveData.observe(this) {
            badge.isVisible = it > 0
            badge.number = it
        }

        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                upBottomMenu()
                if (it) {
                    viewPagerMain.setCurrentItem(bottomMenuCount - 1, false)
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
    }

    private fun getNavigationBarView(): NavigationBarView {
        val tabletInterface = AppConfig.tabletInterface
        val orientation = resources.configuration.orientation
        val smallestWidthDp = resources.configuration.smallestScreenWidthDp

        val useRail = when (tabletInterface) {
            "always" -> true
            "landscape" -> orientation == Configuration.ORIENTATION_LANDSCAPE
            "off" -> false
            "auto" -> smallestWidthDp >= 600
            else -> false
        }

        return if (useRail) binding.navigationRailView else binding.bottomNavigationView
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun upBottomMenu() {
        val navView = getNavigationBarView()
        val menu = navView.menu

        menu.findItem(R.id.menu_discovery).isVisible = AppConfig.showDiscovery
        menu.findItem(R.id.menu_rss).isVisible = AppConfig.showRSS

        var index = 0
        if (AppConfig.showDiscovery) {
            index++
            realPositions[index] = idExplore
        }
        if (AppConfig.showRSS) {
            index++
            realPositions[index] = idRss
        }
        index++
        realPositions[index] = idMy
        bottomMenuCount = index + 1

        adapter.notifyDataSetChanged()

        if (AppConfig.showBottomView) {
            val navView = getNavigationBarView()
            navView.visible()
            if (navView === binding.bottomNavigationView) {
                binding.navigationRailView.gone()
            } else {
                binding.bottomNavigationView.gone()
            }

            navView.labelVisibilityMode = when (AppConfig.labelVisibilityMode) {
                "auto" -> LabelVisibilityMode.LABEL_VISIBILITY_AUTO
                "selected" -> LabelVisibilityMode.LABEL_VISIBILITY_SELECTED
                "labeled" -> LabelVisibilityMode.LABEL_VISIBILITY_LABELED
                "unlabeled" -> LabelVisibilityMode.LABEL_VISIBILITY_UNLABELED
                else -> LabelVisibilityMode.LABEL_VISIBILITY_AUTO
            }

        } else {
            binding.bottomNavigationView.gone()
            binding.navigationRailView.gone()
        }

        val lp =
            binding.navigationRailView.headerView!!.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.START

        binding.navigationRailView.headerView!!
            .setPadding(
                binding.navigationRailView.itemActiveIndicatorExpandedMarginHorizontal,
                0,
                binding.navigationRailView.itemActiveIndicatorExpandedMarginHorizontal,
                0)

        val efab =
            binding.navigationRailView.headerView!!.findViewById<ExtendedFloatingActionButton>(R.id.nav_fab)
        val button =
            binding.navigationRailView.headerView!!.findViewById<ImageView>(R.id.nav_botton)

        efab.let {
            if (LocalConfig.navExtended) {
                it.isExtended = true
                binding.navigationRailView.expand()
                button.setImageResource(R.drawable.ic_menu_open)
            } else {
                it.isExtended = false
                binding.navigationRailView.collapse()
                button.setImageResource(R.drawable.ic_menu)
            }
        }

        efab.setOnClickListener {
                startActivity<SearchActivity>()
            }

        button.setOnClickListener {
            efab.let { it1 ->
                if (it1.isExtended) {
                    efab.isExtended = false
                    binding.navigationRailView.collapse()
                    button.setImageResource(R.drawable.ic_menu)
                } else {
                    efab.isExtended = true
                    binding.navigationRailView.expand()
                    button.setImageResource(R.drawable.ic_menu_open)
                }
                LocalConfig.navExtended = it1.isExtended
            }
        }

    }

    private fun bindNavigationListener() {
        val navView = getNavigationBarView()
        navView.setOnItemSelectedListener { onNavigationItemSelected(it) }
        navView.setOnItemReselectedListener { onNavigationItemReselected(it) }
        binding.viewPagerMain.post {
            binding.viewPagerMain.setCurrentItem(pagePosition, false)
            getNavigationBarView().menu[pagePosition].isChecked = true
        }
    }

    private fun upHomePage() {
        when (AppConfig.defaultHomePage) {
            "bookshelf" -> {}
            "explore" -> if (AppConfig.showDiscovery && AppConfig.showBottomView) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)
            }

            "rss" -> if (AppConfig.showRSS && AppConfig.showBottomView) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)
            }

            "my" -> if (AppConfig.showBottomView) {
                binding.viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
            }
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return when (AppConfig.bookGroupStyle) {
                0 -> idBookshelf1
                1 -> idBookshelf2
                2 -> idBookshelf3
                else -> idBookshelf1
            }
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            pagePosition = position
            getNavigationBarView().selectedItemId = when (realPositions[position]) {
                idBookshelf -> R.id.menu_bookshelf
                idExplore -> R.id.menu_discovery
                idRss -> R.id.menu_rss
                idMy -> R.id.menu_my_config
                else -> R.id.menu_bookshelf
            }
            updateBackCallbackState()
        }
    }

    private fun updateBackCallbackState() {
        val fragment = fragmentMap[getFragmentId(0)] as? BookshelfFragment2
        backCallback.isEnabled = (pagePosition != 0) || (fragment?.canHandleBack() == true)
    }

    private inner class TabFragmentPageAdapter(
        activity: FragmentActivity
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = bottomMenuCount

        override fun createFragment(position: Int): Fragment {
            val fragment = when (getFragmentId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> {
                    val bookshelfFragment = BookshelfFragment2(position)
                    bookshelfFragment.setGroupIdChangeListener {
                        updateBackCallbackState()
                    }
                    bookshelfFragment
                }
                idBookshelf3 -> BookshelfFragment3(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }

            fragmentMap[getFragmentId(position)] = fragment
            return fragment
        }

        override fun getItemId(position: Int): Long {
            return getFragmentId(position).toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return realPositions.map { getFragmentId(it).toLong() }.contains(itemId)
        }
    }



}


