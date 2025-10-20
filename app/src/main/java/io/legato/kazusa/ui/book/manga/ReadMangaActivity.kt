package io.legato.kazusa.ui.book.manga

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.request.target.Target.SIZE_ORIGINAL
import com.bumptech.glide.util.FixedPreloadSizeProvider
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.legato.kazusa.BuildConfig
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.constant.BookType
import io.legato.kazusa.constant.EventBus
import io.legato.kazusa.data.entities.Book
import io.legato.kazusa.data.entities.BookChapter
import io.legato.kazusa.data.entities.BookProgress
import io.legato.kazusa.data.entities.BookSource
import io.legato.kazusa.databinding.ActivityMangaBinding
import io.legato.kazusa.databinding.ViewLoadMoreBinding
import io.legato.kazusa.help.book.isImage
import io.legato.kazusa.help.book.removeType
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.storage.Backup
import io.legato.kazusa.lib.dialogs.SelectItem
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.lib.dialogs.selector
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.model.ReadManga
import io.legato.kazusa.receiver.NetworkChangedListener
import io.legato.kazusa.ui.book.changesource.ChangeBookSourceDialog
import io.legato.kazusa.ui.book.info.BookInfoActivity
import io.legato.kazusa.ui.book.manga.config.MangaAutoReadDialog
import io.legato.kazusa.ui.book.manga.config.MangaClickActionConfigDialog
import io.legato.kazusa.ui.book.manga.config.MangaColorFilterConfig
import io.legato.kazusa.ui.book.manga.config.MangaColorFilterDialog
import io.legato.kazusa.ui.book.manga.config.MangaFooterSettingDialog
import io.legato.kazusa.ui.book.manga.config.MangaScrollMode
import io.legato.kazusa.ui.book.manga.entities.BaseMangaPage
import io.legato.kazusa.ui.book.manga.entities.MangaFooterConfig
import io.legato.kazusa.ui.book.manga.entities.MangaPage
import io.legato.kazusa.ui.book.manga.recyclerview.MangaAdapter
import io.legato.kazusa.ui.book.manga.recyclerview.MangaLayoutManager
import io.legato.kazusa.ui.book.manga.recyclerview.ScrollTimer
import io.legato.kazusa.ui.book.manga.recyclerview.WebtoonFrame
import io.legato.kazusa.ui.book.read.MangaMenu
import io.legato.kazusa.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legato.kazusa.ui.book.toc.TocActivityResult
import io.legato.kazusa.ui.widget.number.NumberPickerDialog
import io.legato.kazusa.ui.widget.recycler.LoadMoreView
import io.legato.kazusa.utils.GSON
import io.legato.kazusa.utils.NetworkUtils
import io.legato.kazusa.utils.StartActivityContract
import io.legato.kazusa.utils.canScroll
import io.legato.kazusa.utils.fastBinarySearch
import io.legato.kazusa.utils.findCenterViewPosition
import io.legato.kazusa.utils.fromJsonObject
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.observeEvent
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.toastOnUi
import io.legato.kazusa.utils.toggleSystemBar
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import io.legato.kazusa.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import kotlin.math.ceil

class ReadMangaActivity : VMBaseActivity<ActivityMangaBinding, ReadMangaViewModel>(),
    ReadManga.Callback, ChangeBookSourceDialog.CallBack, MangaMenu.CallBack,
    MangaColorFilterDialog.Callback, ScrollTimer.ScrollCallback, MangaFooterSettingDialog.Callback,
    MangaAutoReadDialog.Callback {

    private val mLayoutManager by lazy {
        MangaLayoutManager(this)
    }

    private val mAdapter: MangaAdapter by lazy {
        MangaAdapter(this)
    }

    private val mSizeProvider by lazy {
        FixedPreloadSizeProvider<Any>(resources.displayMetrics.widthPixels, SIZE_ORIGINAL)
    }

    private val mPagerSnapHelper: PagerSnapHelper by lazy {
        PagerSnapHelper()
    }

    private lateinit var mMangaFooterConfig: MangaFooterConfig
    private val mLabelBuilder by lazy { StringBuilder() }

    private var mMenu: Menu? = null

    private var scrollMode: Int = MangaScrollMode.PAGE_LEFT_TO_RIGHT

    private var mRecyclerViewPreloader: RecyclerViewPreloader<Any>? = null

    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }

    private var justInitData: Boolean = false
    private var syncDialog: AlertDialog? = null
    private val mScrollTimer by lazy {
        ScrollTimer(this, binding.recyclerView, lifecycleScope).apply {
            setSpeed(AppConfig.mangaAutoPageSpeed)
        }
    }
    private var enableAutoScrollPage = false
    private var enableAutoScroll = false
    private var enableScroll = false
    private val mLinearInterpolator by lazy {
        LinearInterpolator()
    }

    private val loadMoreView by lazy {
        LoadMoreView(this).apply {
//            setBackgroundColor(getCompatColor(R.color.book_ant_10))
//            setLoadingColor(R.color.white)
//            setLoadingTextColor(R.color.white)
        }
    }

    //打开目录返回选择章节返回结果
    private val tocActivity = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.openChapter(it.first, it.second)
        }
    }
    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadManga.loadOrUpContent()
            }
        }
    override val binding by viewBinding(ActivityMangaBinding::inflate)
    override val viewModel by viewModels<ReadMangaViewModel>()
    private val loadingViewVisible get() = binding.flLoading.isVisible
    private val df by lazy {
        DecimalFormat("0.0%")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        val transform = MaterialContainerTransform().apply {
            addTarget(binding.rootView)
            scrimColor = Color.TRANSPARENT
            duration = 500
        }
        window.sharedElementEnterTransition = transform
        window.sharedElementReturnTransition = transform
        upLayoutInDisplayCutoutMode()
        super.onCreate(savedInstanceState)
        binding.rootView.transitionName = intent.getStringExtra("transitionName")
        ReadManga.register(this)
        upSystemUiVisibility(false)
        initRecyclerView()
        binding.tvRetry.setOnClickListener {
            binding.llLoading.isVisible = true
            binding.llRetry.isGone = true
            ReadManga.loadOrUpContent()
        }
        mAdapter.addFooterView {
            ViewLoadMoreBinding.bind(loadMoreView)
        }
        loadMoreView.setOnClickListener {
            if (!loadMoreView.isLoading && ReadManga.hasNextChapter) {
                loadMoreView.startLoad()
                ReadManga.loadOrUpContent()
            }
        }
        loadMoreView.gone()
        mMangaFooterConfig =
            GSON.fromJsonObject<MangaFooterConfig>(AppConfig.mangaFooterConfig).getOrNull()
                ?: MangaFooterConfig()

        onBackPressedDispatcher.addCallback(this){
            if (savedInstanceState != null || !ReadBook.inBookshelf) {
                finish()
            } else {
                supportFinishAfterTransition()
            }
        }
    }

    override fun observeLiveBus() {
        observeEvent<MangaFooterConfig>(EventBus.UP_MANGA_CONFIG) {
            mMangaFooterConfig = it
            val item = mAdapter.getItem(binding.recyclerView.findCenterViewPosition())
            upInfoBar(item)
        }
    }

    private fun initRecyclerView() {
        val mangaColorFilter =
            GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
                ?: MangaColorFilterConfig()
        mAdapter.run {
            setMangaImageColorFilter(mangaColorFilter)
            enableMangaEInk(AppConfig.enableMangaEInk, AppConfig.mangaEInkThreshold)
            enableGray(AppConfig.enableMangaGray)
        }

        viewModel.initData(intent) {
            setScrollMode(viewModel.getEffectiveScrollMode())
            updateWebtoonSidePadding(viewModel.getEffectiveWebtoonSidePadding())
        }

        binding.recyclerView.run {
            adapter = mAdapter
            itemAnimator = null
            layoutManager = mLayoutManager
            setHasFixedSize(true)
            setDisableMangaScale(AppConfig.disableMangaScale)
            setRecyclerViewPreloader(AppConfig.mangaPreDownloadNum)
            setPreScrollListener { _, _, _, position ->
                if (mAdapter.isNotEmpty()) {
                    val item = mAdapter.getItem(position)
                    if (item is BaseMangaPage) {
                        if (ReadManga.durChapterIndex < item.chapterIndex) {
                            ReadManga.moveToNextChapter()
                        } else if (ReadManga.durChapterIndex > item.chapterIndex) {
                            ReadManga.moveToPrevChapter()
                        } else {
                            ReadManga.durChapterPos = item.index
                            ReadManga.curPageChanged()
                        }
                        if (item is MangaPage) {
                            binding.mangaMenu.upSeekBar(item.index, item.imageCount)
                            upInfoBar(item)
                        }
                    }
                }
            }
        }

        binding.webtoonFrame.actionHandler = object : WebtoonFrame.ClickActionHandler {
            override fun showMenu() {
                if (!binding.mangaMenu.isVisible && !loadingViewVisible) {
                    binding.mangaMenu.runMenuIn()
                }
            }

            override fun nextPage() {
                scrollToNext()
            }

            override fun prevPage() {
                scrollToPrev()
            }

            override fun nextChapter() {
                ReadManga.moveToNextChapter()
            }

            override fun prevChapter() {
                ReadManga.moveToPrevChapter()
            }
        }

        binding.webtoonFrame.longPressListener = {
            val centerPos = binding.recyclerView.findCenterViewPosition()
            val currentItem = mAdapter.getItem(centerPos)
            val page = currentItem as? MangaPage
            page?.let { item ->
                selector(
                    arrayListOf(
                        SelectItem("保存", "save"),
                        SelectItem("分享", "share")
                    )
                ) { _, option, _ ->
                    when (option.value) {
                        "save" -> viewModel.saveImageToGallery(item.mImageUrl)
                        // "share" -> shareImage(item.mImageUrl)
                    }
                }
            }
        }


    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.initData(intent)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
//        Looper.myQueue().addIdleHandler {
//            viewModel.initData(intent)
//            false
//        }
        justInitData = true
    }

    override fun upContent() {
        lifecycleScope.launch {
            binding.mangaMenu.upBookView()
            val data = withContext(IO) { ReadManga.mangaContents }
            val pos = data.pos
            val list = data.items
            val curFinish = data.curFinish
            val nextFinish = data.nextFinish
            mAdapter.submitList(list) {
                if (loadingViewVisible && curFinish) {
                    binding.infobar.isVisible = true
                    upInfoBar(list[pos])
                    mLayoutManager.scrollToPositionWithOffset(pos, 0)
                    binding.flLoading.isGone = true
                    loadMoreView.visible()
                    binding.mangaMenu.upSeekBar(
                        ReadManga.durChapterPos, ReadManga.curMangaChapter!!.imageCount
                    )
                }

                if (curFinish) {
                    if (!ReadManga.hasNextChapter) {
                        loadMoreView.noMore("暂无章节了！")
                    } else if (nextFinish) {
                        loadMoreView.stopLoad()
                    } else {
                        loadMoreView.startLoad()
                    }
                }
            }
        }
    }

    private fun upInfoBar(page: Any?) {
        if (page !is MangaPage) {
            return
        }
        val chapterIndex = page.chapterIndex
        val chapterSize = page.chapterSize
        val chapterPos = page.index
        val imageCount = page.imageCount
        val chapterName = page.mChapterName
        mMangaFooterConfig.run {
            mLabelBuilder.clear()
            binding.infobar.isGone = hideFooter
            binding.infobar.textInfoAlignment = footerOrientation

            if (!hideChapterName) {
                mLabelBuilder.append(chapterName).append(" ")
            }

            if (!hidePageNumber) {
                if (!hidePageNumberLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_page_number))
                }
                mLabelBuilder.append("${chapterPos + 1}/${imageCount}").append(" ")
            }

            if (!hideChapter) {
                if (!hideChapterLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_chapter))
                }
                mLabelBuilder.append("${chapterIndex + 1}/${chapterSize}").append(" ")
            }

            if (!hideProgressRatio) {
                if (!hideProgressRatioLabel) {
                    mLabelBuilder.append(getString(R.string.manga_check_progress))
                }
                val percent = if (chapterSize == 0 || imageCount == 0 && chapterIndex == 0) {
                    "0.0%"
                } else if (imageCount == 0) {
                    df.format((chapterIndex + 1.0f) / chapterSize.toDouble())
                } else {
                    var percent =
                        df.format(
                            chapterIndex * 1.0f / chapterSize + 1.0f /
                                    chapterSize * (chapterPos + 1) / imageCount.toDouble()
                        )
                    if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || chapterPos + 1 != imageCount)) {
                        percent = "99.9%"
                    }
                    percent
                }
                mLabelBuilder.append(percent)
            }
        }
        binding.infobar.update(
            if (mLabelBuilder.isEmpty()) "" else mLabelBuilder.toString()
        )
    }

    override fun onResume() {
        super.onResume()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 当网络是可用状态且无需初始化时同步进度（初始化中已有同步进度逻辑）
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData) {
                ReadManga.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
        if (enableAutoScrollPage) {
            mScrollTimer.isEnabledPage = true
        }
        if (enableAutoScroll) {
            mScrollTimer.isEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (ReadManga.inBookshelf) {
            ReadManga.saveRead()
            if (!BuildConfig.DEBUG) {
                if (AppConfig.syncBookProgressPlus) {
                    ReadManga.syncProgress()
                } else {
                    ReadManga.uploadProgress()
                }
                Backup.autoBack(this)
            }
        }
        ReadManga.cancelPreDownloadTask()
        networkChangedListener.unRegister()
        mScrollTimer.isEnabledPage = false
        mScrollTimer.isEnabled = false
    }

    override fun loadFail(msg: String) {
        lifecycleScope.launch {
            if (loadingViewVisible) {
                binding.llLoading.isGone = true
                binding.llRetry.isVisible = true
                binding.tvMsg2.setMessage(msg)
            } else {
                loadMoreView.error(null, "加载失败，点击重试")
            }
        }
    }

    override fun onDestroy() {
        ReadManga.unregister(this)
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Glide.get(this).clearMemory()
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadManga.setProgress(progress)
            }
            noButton()
        }
    }

    override fun showLoading() {
        lifecycleScope.launch {
            binding.flLoading.isVisible = true
        }
    }

    override fun startLoad() {
        lifecycleScope.launch {
            loadMoreView.startLoad()
        }
    }

    override fun scrollBy(distance: Int) {
        if (!binding.recyclerView.canScroll(1)) {
            return
        }
        val time = ceil(16f / distance * 10000).toInt()
        binding.recyclerView.smoothScrollBy(10000, 10000, mLinearInterpolator, time)
    }

    override fun scrollPage(direction: Int) {
        if (scrollMode == MangaScrollMode.WEBTOON || scrollMode == MangaScrollMode.WEBTOON_WITH_GAP) {
            // 条漫平滑滚动距离
            val dy =
                (binding.recyclerView.height - binding.recyclerView.paddingTop - binding.recyclerView.paddingBottom) * direction
            binding.recyclerView.smoothScrollBy(0, dy)
        } else {
            // 分页按 position 翻页
            val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager ?: return
            val currentPosition = layoutManager.findFirstVisibleItemPosition()
            val nextPosition = currentPosition + direction
            val itemCount = binding.recyclerView.adapter?.itemCount ?: return

            if (nextPosition in 0 until itemCount) {
                binding.recyclerView.smoothScrollToPosition(nextPosition)
            }
        }
    }


    override val oldBook: Book?
        get() = ReadManga.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isImage) {
            binding.flLoading.isVisible = true
            viewModel.changeTo(book, toc)
        } else {
            toastOnUi("所选择的源不是漫画源")
        }
    }

    override fun updateColorFilter(config: MangaColorFilterConfig) {
        mAdapter.setMangaImageColorFilter(config)
        if (config.autoBrightness) {
            resetWindowToSystemBrightness()
        } else {
            updateWindowBrightness(config.l)
        }
    }

    @SuppressLint("StringFormatMatches")
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_manga, menu)
        upMenu(menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    /**
     * 菜单
     */
    @SuppressLint("StringFormatMatches", "NotifyDataSetChanged")
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source -> {
                binding.mangaMenu.runMenuOut()
                ReadManga.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_refresh -> {
                binding.flLoading.isVisible = true
                ReadManga.book?.let {
                    viewModel.refreshContentDur(it)
                }
            }

            R.id.menu_pre_manga_number -> {
                showNumberPickerDialog(
                    0,
                    getString(R.string.pre_download),
                    AppConfig.mangaPreDownloadNum
                ) {
                    AppConfig.mangaPreDownloadNum = it
                    item.title = getString(R.string.pre_download_m, it)
                    setRecyclerViewPreloader(it)
                }
            }

        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun openCatalog() {
        ReadManga.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    override fun showFooterConfig() {
        showDialogFragment(MangaFooterSettingDialog().apply {
            initialScrollMode = viewModel.getEffectiveScrollMode()
            initialWebtoonSidePadding = viewModel.getEffectiveWebtoonSidePadding()
            callback = this@ReadMangaActivity
        })
    }

    override fun showColorFilterConfig() {
        binding.mangaMenu.runMenuOut()
        showDialogFragment(MangaColorFilterDialog())
    }

    override fun showClickConfig() {
        binding.mangaMenu.runMenuOut()
        showDialogFragment(MangaClickActionConfigDialog())
    }

    override fun showScrollModeDialog() {
        showDialogFragment(MangaAutoReadDialog().apply {
            initialAutoPageEnabled = enableScroll
            callback = this@ReadMangaActivity
        })
    }

    //漫画模式
    override fun onScrollModeChanged(mode: Int) {
        onAutoPageToggle(false)
        viewModel.updateReadConfig {
            mangaScrollMode = mode
        }
        setScrollMode(mode)
        updateWebtoonSidePadding(AppConfig.webtoonSidePaddingDp)
        setAutoReadEnabled(false)
    }

    //点击滑动
    override fun onClickScrollDisabledChanged(disabled: Boolean) {
        AppConfig.disableClickScroll = disabled
    }

    //双击缩放
    override fun onMangaScaleDisabledChanged(disabled: Boolean) {
        AppConfig.disableMangaScale = disabled
        setDisableMangaScale(disabled)
    }

    //侧边留白
    override fun upSidePadding(padding: Int) {
        viewModel.updateReadConfig {
            webtoonSidePaddingDp = padding
        }
        updateWebtoonSidePadding(padding)
    }

    //墨水屏
    override fun updateEpaperMode(enabled: Boolean, threshold: Int) {
        AppConfig.enableMangaEInk = enabled
        AppConfig.enableMangaGray = false
        AppConfig.mangaEInkThreshold = threshold
        mAdapter.enableMangaEInk(enabled, threshold)
    }

    //灰度
    override fun updateGrayMode(enabled: Boolean) {
        AppConfig.enableMangaGray = enabled
        AppConfig.enableMangaEInk = false
        mAdapter.enableGray(enabled)
    }

    //自动翻页
    override fun onAutoPageToggle(enable: Boolean) {
        enableScroll = enable
        setAutoReadEnabled(enable)
    }

    //自动翻页速度
    override fun onAutoPageSpeedChanged(speed: Int) {
        setAutoReadEnabled(false)
        AppConfig.mangaAutoPageSpeed = speed
        mScrollTimer.setSpeed(speed)
        setAutoReadEnabled(enableScroll)
//        if (enableAutoScrollPage) {
//            mScrollTimer.isEnabledPage = true
//        }
    }

    override fun onMangaLongClickChanged(checked: Boolean) {
        AppConfig.mangaLongClick = checked
    }

    override fun onVolumeKeyPageChanged(enable: Boolean) {
        AppConfig.MangaVolumeKeyPage = enable
    }

    override fun onHideMangaTitleChanged(hide: Boolean) {
        AppConfig.hideMangaTitle = hide
        ReadManga.loadContent()
    }

    override fun openBookInfoActivity() {
        ReadManga.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
                putExtra("bookUrl", it.bookUrl)
            }
        }
    }

    //1 自动翻页 2 自动滚动 0 关闭
    fun setAutoReadEnabled(enabled: Boolean) {
        enableScroll = enabled
        val isWebtoonMode =
            scrollMode == MangaScrollMode.WEBTOON || scrollMode == MangaScrollMode.WEBTOON_WITH_GAP
        if (enabled) {
            if (isWebtoonMode) {
                mScrollTimer.isEnabled = true
                enableAutoScroll = true
                enableAutoScrollPage = false
                mScrollTimer.isEnabledPage = false
            } else {
                mScrollTimer.isEnabledPage = true
                enableAutoScrollPage = true
                enableAutoScroll = false
                mScrollTimer.isEnabled = false
            }
        } else {
            enableAutoScroll = false
            enableAutoScrollPage = false
            mScrollTimer.isEnabled = false
            mScrollTimer.isEnabledPage = false
        }
    }

    override fun upSystemUiVisibility(menuIsVisible: Boolean) {
        toggleSystemBar(menuIsVisible)
        if (enableAutoScroll) {
            mScrollTimer.isEnabled = !menuIsVisible
        }
        if (enableAutoScrollPage) {
            mScrollTimer.isEnabledPage = !menuIsVisible
        }
    }

    /**
     * 调整漫画类型
     * @param mode 漫画类型
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun setScrollMode(mode: Int) {
        scrollMode = mode

        val isHorizontalPage =
            mode == MangaScrollMode.PAGE_LEFT_TO_RIGHT || mode == MangaScrollMode.PAGE_RIGHT_TO_LEFT

        val isWebtoon = mode == MangaScrollMode.WEBTOON || mode == MangaScrollMode.WEBTOON_WITH_GAP

        when (mode) {
            MangaScrollMode.PAGE_LEFT_TO_RIGHT -> {
                mLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
                mLayoutManager.reverseLayout = false
            }

            MangaScrollMode.PAGE_RIGHT_TO_LEFT -> {
                mLayoutManager.orientation = LinearLayoutManager.HORIZONTAL
                mLayoutManager.reverseLayout = true
            }

            MangaScrollMode.PAGE_TOP_TO_BOTTOM,
            MangaScrollMode.WEBTOON,
            MangaScrollMode.WEBTOON_WITH_GAP -> {
                mLayoutManager.orientation = LinearLayoutManager.VERTICAL
                mLayoutManager.reverseLayout = false
            }
        }

        mAdapter.isHorizontal = isHorizontalPage || mode == MangaScrollMode.PAGE_TOP_TO_BOTTOM

        if (!isWebtoon) {
            mPagerSnapHelper.attachToRecyclerView(binding.recyclerView)
        } else {
            mPagerSnapHelper.attachToRecyclerView(null)
        }

        mAdapter.notifyItemRangeChanged(0, mAdapter.itemCount)
    }

    /**
     * 调整条漫侧边留白设置
     * @param paddingDp 侧边留白距离，单位dp
     */
    private fun updateWebtoonSidePadding(paddingDp: Int) {
        if (scrollMode != MangaScrollMode.WEBTOON && scrollMode != MangaScrollMode.WEBTOON_WITH_GAP) {
            // 非条漫模式，取消留白
            binding.recyclerView.setPadding(0, 0, 0, 0)
            binding.recyclerView.clipToPadding = true
            return
        }

        binding.recyclerView.doOnLayout { recyclerView ->
            val width = recyclerView.width
            if (paddingDp == 0) {
                recyclerView.setPadding(0, 0, 0, 0)
            } else if (width > 0) {
                val paddingPx = (width * paddingDp / 100)
                recyclerView.setPadding(paddingPx, 0, paddingPx, 0)
            }
        }

    }


    private fun setRecyclerViewPreloader(maxPreload: Int) {
        if (mRecyclerViewPreloader != null) {
            binding.recyclerView.removeOnScrollListener(mRecyclerViewPreloader!!)
        }
        mRecyclerViewPreloader = RecyclerViewPreloader(
            Glide.with(this), mAdapter, mSizeProvider, maxPreload
        )
        binding.recyclerView.addOnScrollListener(mRecyclerViewPreloader!!)
    }

    @SuppressLint("StringFormatMatches")
    private fun upMenu(menu: Menu) {
        this.mMenu = menu
        menu.findItem(R.id.menu_pre_manga_number).title =
            getString(R.string.pre_download_m, AppConfig.mangaPreDownloadNum)
    }

    private fun setDisableMangaScale(disable: Boolean) {
        binding.webtoonFrame.disableMangaScale = disable
        binding.recyclerView.disableMangaScale = disable
        if (disable) {
            binding.recyclerView.resetZoom()
        }
    }

    private fun upLayoutInDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun scrollToNext() {
        if (!AppConfig.disableClickScroll)
            scrollPageTo(1)
    }

    private fun scrollToPrev() {
        if (!AppConfig.disableClickScroll)
            scrollPageTo(-1)
    }

    private fun isHorizontalScroll(): Boolean {
        return scrollMode == MangaScrollMode.PAGE_LEFT_TO_RIGHT ||
                scrollMode == MangaScrollMode.PAGE_RIGHT_TO_LEFT
    }

    private fun scrollPageTo(direction: Int) {
        if (!binding.recyclerView.canScroll(direction)) {
            return
        }
        var dx = 0
        var dy = 0
        if (isHorizontalScroll()) {
            dx = binding.recyclerView.run {
                width - paddingStart - paddingEnd
            }
        } else {
            dy = binding.recyclerView.run {
                height - paddingTop - paddingBottom
            }
        }
        dx *= direction
        dy *= direction
        binding.recyclerView.smoothScrollBy(dx, dy)
    }

    private fun showNumberPickerDialog(
        min: Int,
        title: String,
        initValue: Int,
        callback: (Int) -> Unit,
    ) {
        NumberPickerDialog(this)
            .setTitle(title)
            .setMaxValue(9999)
            .setMinValue(min)
            .setValue(initValue)
            .show {
                callback.invoke(it)
            }
    }

    override fun finish() {
        val book = ReadManga.book ?: return super.finish()

        if (ReadManga.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadManga.book?.removeType(BookType.notShelf)
                    ReadManga.book?.save()
                    ReadManga.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }
    fun resetWindowToSystemBrightness() {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
    }

    fun updateWindowBrightness(brightness: Int) {
        val layoutParams = window.attributes
        val normalizedBrightness = brightness.toFloat() / 255.0f
        layoutParams.screenBrightness = normalizedBrightness.coerceIn(0f, 1f)
        window.attributes = layoutParams
        // 强制刷新屏幕
        window.decorView.postInvalidate()
    }

    override fun skipToPage(index: Int) {
        val durChapterIndex = ReadManga.durChapterIndex
        val itemPos = mAdapter.getItems().fastBinarySearch {
            val chapterIndex: Int
            val pageIndex: Int
            if (it is BaseMangaPage) {
                chapterIndex = it.chapterIndex
                pageIndex = it.index
            } else {
                error("unknown item type")
            }
            val delta = chapterIndex - durChapterIndex
            if (delta != 0) {
                delta
            } else {
                pageIndex - index
            }
        }
        if (itemPos > -1) {
            mLayoutManager.scrollToPositionWithOffset(itemPos, 0)
            upInfoBar(mAdapter.getItem(itemPos))
            ReadManga.durChapterPos = index
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (AppConfig.MangaVolumeKeyPage) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    scrollToPrev()
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    scrollToNext()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun addToBookshelf(book: Book, toc: List<BookChapter>) {
        viewModel.addToBookshelf(book, toc) {
            toastOnUi("已添加到书架")
        }
    }

}