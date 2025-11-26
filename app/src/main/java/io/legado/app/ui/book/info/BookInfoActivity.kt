package io.legado.app.ui.book.info

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.BookCover
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.ToolbarUtils.setAllIconsColor
import io.legado.app.utils.applyNavigationBarMargin
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.openFileUri
import io.legado.app.utils.sendToClip
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookInfoActivity :
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback {

    companion object {
        private const val READ_BOOK_REQUEST_CODE = 1001
    }

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        book.durChapterIndex = it.first
                        book.durChapterPos = it.second
                        chapterChanged = it.third
                        appDb.bookDao.update(book)
                    }
                    startReadActivity(book, binding.btnRead)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook()
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_CANCELED) {
            return@registerForActivityResult
        }
        book?.let { book ->
            viewModel.bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            viewModel.refreshBook(book)
        }
    }

    private var surfaceFinalColor: Int = 0
    private var surfaceContainerFinalColor: Int = 0
    private var secondaryFinalColor: Int = 0
    private var onSurfaceFinalColor: Int = 0
    private var secondaryContainerFinalColor: Int = 0
    private var primaryFinalColor: Int = 0
    private var tertiaryFinalColor: Int = 0
    private var currentJob: Job? = null
    private var wrappedContext: Context? = null
    private var chapterChanged = false
    private val waitDialog by lazy { WaitDialog(this) }
    private var editMenuItem: MenuItem? = null
    private val book get() = viewModel.getBook(false)

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()

    @SuppressLint("PrivateResource")
    override fun onCreate(savedInstanceState: Bundle?) {
        postponeEnterTransition()
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        val transform = MaterialContainerTransform().apply {
            addTarget(binding.cdCov)
            scrimColor = Color.TRANSPARENT
        }
        window.sharedElementEnterTransition = transform
        window.sharedElementReturnTransition = transform
        window.sharedElementsUseOverlay = false
        super.onCreate(savedInstanceState)
        surfaceFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, -1)
        secondaryFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, -1)
        onSurfaceFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, -1)
        surfaceContainerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSurfaceContainer,
            -1
        )
        secondaryContainerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer,
            -1
        )
        primaryFinalColor = MaterialColors.getColor(
            this,
            androidx.appcompat.R.attr.colorPrimary,
            -1
        )
        tertiaryFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorTertiary,
            -1
        )
        binding.cdCov.transitionName = intent.getStringExtra("transitionName")
        binding.cdCov.doOnPreDraw {
            startPostponedEnterTransition()
        }
        setSupportActionBar(binding.topBar)
        
        binding.scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) binding.btnRead.shrink()
            else if (scrollY < oldScrollY) binding.btnRead.extend()
        }

        binding.btnRead.applyNavigationBarMargin(true)
        binding.btnShelf.text = getString(R.string.remove_from_bookshelf)
        binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))

        binding.tvDetail.revealOnFocusHint = false

        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) { upLoading(false, it) }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        editMenuItem = menu.findItem(R.id.menu_edit)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt ?: false
        menu.findItem(R.id.menu_upload)?.isVisible =
            viewModel.bookData.value?.isLocal ?: false
        menu.findItem(R.id.menu_delete_alert)?.isChecked =
            LocalConfig.bookInfoDeleteAlert
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    infoEditResult.launch {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_share_it -> {
                viewModel.getBook()?.let {
                    val bookJson = GSON.toJson(it)
                    val shareStr = "${it.bookUrl}#$bookJson"
                    shareWithQr(shareStr, it.name)
                }
            }

            R.id.menu_refresh -> {
                refreshBook()
            }

            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }

            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.bookUrl?.let {
                sendToClip(it)
            }

            R.id.menu_copy_toc_url -> viewModel.getBook()?.tocUrl?.let {
                sendToClip(it)
            }

            R.id.menu_can_update -> {
                viewModel.getBook()?.let {
                    it.canUpdate = !it.canUpdate
                    if (viewModel.inBookshelf) {
                        if (!it.canUpdate) {
                            it.removeType(BookType.updateError)
                        }
                        viewModel.saveBook(it)
                    }
                }
            }

            R.id.menu_clear_cache -> viewModel.clearCache()
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                viewModel.getBook()?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }

            R.id.menu_delete_alert -> LocalConfig.bookInfoDeleteAlert = !item.isChecked
            R.id.menu_upload -> {
                viewModel.getBook()?.let { book ->
                    book.getRemoteUrl()?.let {
                        alert(R.string.draw, R.string.sure_upload) {
                            okButton {
                                upLoadBook(book)
                            }
                            cancelButton()
                        }
                    } ?: upLoadBook(book)
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_BOOK_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    viewModel.inBookshelf = true
                    upTvBookshelf()
                }

                RESULT_DELETED -> {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

//    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
//        if (ev.action == MotionEvent.ACTION_DOWN) {
//            currentFocus?.let {
//                if (it === binding.tvIntro && binding.tvIntro.hasSelection()) {
//                    it.clearFocus()
//                }
//            }
//        }
//        return super.dispatchTouchEvent(ev)
//    }

    private fun refreshBook() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun upLoadBook(
        book: Book,
        bookWebDav: RemoteBookWebDav? = AppWebDav.defaultBookWebDav,
    ) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                bookWebDav
                    ?.upload(book)
                    ?: throw NoStackTraceException("未配置webDav")
                //更新书籍最后更新时间,使之比远程书籍的时间新
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun showBook(book: Book) = binding.run {
        showCover(book)
        addColorScheme(binding.ivCover.drawable)
        tvName.text = book.name
        tvAuthor.text = getString(R.string.author_show, book.getRealAuthor())
        tvOrigin.text = getString(R.string.origin_show, book.originName)
        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
        tvChapter.text = getString(R.string.read_chapter_total, book.totalChapterNum)
        if (book.durChapterIndex + 1 == book.totalChapterNum)
            tvChapterIndex.text = "已读完"
        else
            tvChapterIndex.text = getString(R.string.read_chapter_index, book.durChapterIndex + 1)
        tvDetail.text = book.getDisplayIntro()
        tvToc.visible(!book.isWebFile)
        upTvBookshelf()
        upKinds(book)
        upGroup(book.group)
    }

    private fun upKinds(book: Book) = binding.run {
        lifecycleScope.launch {
            var kinds = book.getKindList()
            if (book.isLocal) {
                withContext(IO) {
                    val size = FileDoc.fromFile(book.bookUrl).size
                    if (size > 0) {
                        kinds = kinds.toMutableList()
                        kinds.add(ConvertUtils.formatFileSize(size))
                    }
                }
            }
            if (kinds.isEmpty()) {
                lbKind.gone()
            } else {
                lbKind.visible()
                lbKind.setLabels(kinds) {}
            }
        }
    }

    private fun showCover(book: Book) {
        binding.ivCover.load(
            book.getDisplayCover(),
            book.name,
            book.author,
            false,
            book.origin,
            onLoadFinish = {
                binding.ivCover.post {
                    val drawable = binding.ivCover.drawable
                    if (drawable != null) {
                        addColorScheme(drawable)
                    }
                }
            }
        )
            if (!AppConfig.isEInkMode) {
                //高版本使用RenderEffect
                BookCover.load(this, book.getDisplayCover(), false, book.origin)
                    .into(binding.bgBook)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BookCover.load(this, book.getDisplayCover(), false, book.origin)
                        .into(binding.bgBook)
                    val blurEffect = RenderEffect.createBlurEffect(60f, 60f, Shader.TileMode.CLAMP)
                    binding.bgBook.setRenderEffect(blurEffect)
                }else{
                    BookCover.loadBlur(this, book.getDisplayCover(), false, book.origin)
                        .into(binding.bgBook)
                }
            }
        addColorScheme(binding.ivCover.drawable)

    }

    private fun addColorScheme(drawable: Drawable?) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Default).launch {
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                is TransitionDrawable -> (drawable.getDrawable(1) as? BitmapDrawable)?.bitmap
                else -> null
            } ?: return@launch

            val colorAccuracy = true
            val targetWidth = if (colorAccuracy) (bitmap.width / 4).coerceAtMost(256) else 16
            val targetHeight = if (colorAccuracy) (bitmap.height / 4).coerceAtMost(256) else 16
            val scaledBitmap = bitmap.scale(targetWidth, targetHeight, false)

            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(scaledBitmap)
                .build()

            wrappedContext = DynamicColors.wrapContextIfAvailable(
                this@BookInfoActivity,
                options
            ).apply {
                resources.configuration.uiMode =
                    this@BookInfoActivity.resources.configuration.uiMode
            }

            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    applyColorScheme()
                }
            }
        }
    }

    private suspend fun applyColorScheme() {
        val ctx = wrappedContext ?: this

        val colorPrimary =
            MaterialColors.getColor(ctx, androidx.appcompat.R.attr.colorPrimary, -1)
        val colorSecondary =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondary, -1)
        val colorTertiary =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorTertiary, -1)
        val colorOnSurface =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, -1)
        val colorSurface =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, -1)
        val colorSurfaceContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                -1
            )
        val colorSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSecondaryContainer,
                -1
            )
        val colorOnTertiary =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnTertiary, -1)

        val surfaceTransition = ValueAnimator.ofArgb(surfaceFinalColor, colorSurface).apply {
            duration = 400L
            addUpdateListener { animation ->
                val nightModeFlags =
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
                var color = animation.animatedValue as Int
                if (AppConfig.pureBlack && isDarkMode) {
                    color = Color.BLACK
                }
                binding.cdInfo?.setCardBackgroundColor(color)
                if (!AppConfig.isTransparent)
                {
                    binding.llInfo.setBackgroundColor(color)
                    (binding.llCover.background as? GradientDrawable)?.colors =
                        intArrayOf(Color.TRANSPARENT, color)
                }
            }
        }

        val surfaceContainerTransition =
            ValueAnimator.ofArgb(surfaceContainerFinalColor, colorSurfaceContainer).apply {
                duration = 400L
                addUpdateListener { animation ->
                    val color = animation.animatedValue as Int
                    binding.lbKind.applyColorScheme(color, colorOnSurface)
                    binding.collTopBar.setContentScrimColor(color)
                }
            }

        val buttonTransition = ValueAnimator.ofArgb(secondaryFinalColor, colorSecondary).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                val tint = ColorStateList.valueOf(color)
                listOf(
                    binding.btnShelf,
                    binding.tvTocView,
                    binding.tvChangeGroup,
                    binding.btnChangeSource
                ).forEach { btn ->
                    btn.setTextColor(color)
                    btn.iconTint = tint
                }
                binding.div.setTextColor(color)
                binding.tvChapterIndex.setTextColor(color)
            }
        }

        val backTransition =
            ValueAnimator.ofArgb(secondaryContainerFinalColor, colorSecondaryContainer).apply {
                duration = 400L
                addUpdateListener { animation ->
                    val color = animation.animatedValue as Int
                    if (!AppConfig.isTransparent)
                        binding.bgBookMask.setBackgroundColor(color)
                }
            }

        val textTransition = ValueAnimator.ofArgb(onSurfaceFinalColor, colorOnSurface).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                binding.tvName.setTextColor(color)
                binding.tvAuthor.setTextColor(color)
                binding.tvOrigin.setTextColor(color)
                binding.tvDetail.setTextColor(color)
                binding.tvToc.setTextColor(color)
                binding.tvLasted.setTextColor(color)
                binding.ivName.imageTintList = ColorStateList.valueOf(color)
                binding.ivWeb.imageTintList = ColorStateList.valueOf(color)
            }
        }

        val primaryTransition = ValueAnimator.ofArgb(primaryFinalColor, colorPrimary).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                binding.tvChapter.setTextColor(color)
            }
        }

        val tertiaryTransition = ValueAnimator.ofArgb(tertiaryFinalColor, colorTertiary).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                binding.btnRead.setBackgroundColor(color)
            }
        }

        withContext(Dispatchers.Main) {
            listOf(
                surfaceTransition,
                surfaceContainerTransition,
                buttonTransition,
                backTransition,
                textTransition,
                primaryTransition,
                tertiaryTransition
            ).forEach { it.start() }
        }

        surfaceFinalColor = colorSurface
        secondaryFinalColor = colorSecondary
        onSurfaceFinalColor = colorOnSurface
        surfaceContainerFinalColor = colorSurfaceContainer
        primaryFinalColor = colorPrimary
        tertiaryFinalColor = colorTertiary
        secondaryContainerFinalColor = colorSecondaryContainer

        binding.topBar.setAllIconsColor(colorOnSurface)
        binding.btnRead.setTextColor(colorOnTertiary)
        binding.btnRead.iconTint = ColorStateList.valueOf(colorOnTertiary)
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
                binding.tvTocView.text = getString(R.string.loading)
                binding.tvTocView.isEnabled = false
            }

            chapterList.isNullOrEmpty() -> {
                binding.tvToc.text = getString(
                    R.string.toc_s,
                    getString(R.string.error_load_toc)
                )
                binding.tvTocView.text = getString(R.string.error_load_toc)
                binding.tvTocView.isEnabled = false
            }

            else -> {
                book?.let {
                    binding.tvToc.text = getString(R.string.toc_s, it.durChapterTitle)
                    binding.tvLasted.text = getString(R.string.lasted_show, it.latestChapterTitle)
                    binding.tvTocView.text = getString(R.string.view_toc)
                    binding.tvTocView.isEnabled = true
                }
            }
        }
    }

    private fun upTvBookshelf() {
        binding.btnShelf.apply {
            if (viewModel.inBookshelf) {
                text = getString(R.string.remove_from_bookshelf)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_star_fill)
            } else {
                text = getString(R.string.add_to_bookshelf)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_star)
            }
        }
        editMenuItem?.isVisible = viewModel.inBookshelf
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            if (it.isNullOrEmpty()) {
                binding.tvChangeGroup.text = if (book?.isLocal == true) {
                    getString(R.string.local_no_group)
                } else {
                    getString(R.string.no_group)
                }
                binding.tvChangeGroup.setIconResource(R.drawable.ic_groups)
            } else {
                binding.tvChangeGroup.text = getString(R.string.group_s, it)
                binding.tvChangeGroup.setIconResource(R.drawable.ic_groups_fill)
            }
        }
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        ivCover.setOnLongClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path))
            }
            true
        }
        btnRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isWebFile) {
                    showWebFileDownloadAlert {
                        readBook(it)
                    }
                } else {
                    readBook(book)
                }
            }
        }
        btnShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf {
                            upTvBookshelf()
                        }
                    }
                }
            }
        }
        tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        btnChangeSource.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }
        tvTocView.setOnClickListener {
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) {
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        tvChangeGroup.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            }
        }
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                startActivity<SearchActivity> {
                    putExtra("key", book.author)
                }
            }
        }
        tvName.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                startActivity<SearchActivity> {
                    putExtra("key", book.name)
                }
            }
        }
        refreshLayout.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            refreshBook()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
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

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let {
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del
                ) {
                    var checkBox: CheckBox? = null
                    if (it.isLocal) {
                        checkBox = CheckBox(this@BookInfoActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }
                    yesButton {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    noButton()
                }
            } else {
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        selector(
            R.string.download_and_import_file,
            webFiles
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book, binding.btnRead)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book, binding.btnRead)
            }
        }
    }

    private fun startReadActivity(book: Book, sharedView: View) {
        val transitionName = "book_${book.bookUrl}_${System.currentTimeMillis()}"
        sharedView.transitionName = transitionName

        val cls = when {
            book.isAudio -> AudioPlayActivity::class.java
            !book.isLocal && book.isImage && AppConfig.showMangaUi -> ReadMangaActivity::class.java
            else -> ReadBookActivity::class.java
        }

        val intent = Intent(this, cls).apply {
            putExtra("bookUrl", book.bookUrl)
            putExtra("inBookshelf", viewModel.inBookshelf)
            putExtra("chapterChanged", chapterChanged)
            putExtra("transitionName", transitionName)
        }

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            this,
            sharedView,
            transitionName
        )
        ActivityCompat.startActivityForResult(
            this,
            intent,
            READ_BOOK_REQUEST_CODE,
            options.toBundle()
        )
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            showCover(book)
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {
                    upTvBookshelf()
                }
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        val showText = "Loading....."
        if (isShow) {
            waitDialog.run {
                setText(showText)
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

    override fun addToBookshelf(book: Book, toc: List<BookChapter>) {
        viewModel.addToBookshelf(book, toc) {
            toastOnUi("已添加到书架")
        }
    }
}