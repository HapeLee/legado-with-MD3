package io.legato.kazusa.ui.book.read

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnDetach
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import io.legato.kazusa.R
import io.legato.kazusa.base.VMBaseActivity
import io.legato.kazusa.constant.AppConst.charsets
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.databinding.ActivityBookReadBinding
import io.legato.kazusa.databinding.DialogDownloadChoiceBinding
import io.legato.kazusa.databinding.DialogEditTextBinding
import io.legato.kazusa.databinding.DialogSimulatedReadingBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.LocalConfig
import io.legato.kazusa.help.config.ReadBookConfig
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.lib.dialogs.selector
//import io.legado.app.lib.theme.bottomBackground
import io.legato.kazusa.model.CacheBook
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.ui.book.read.config.BgTextConfigDialog
import io.legato.kazusa.ui.book.read.config.ClickActionConfigDialog
import io.legato.kazusa.ui.book.read.config.PaddingConfigDialog
import io.legato.kazusa.ui.book.read.config.PageKeyDialog
import io.legato.kazusa.ui.file.HandleFileContract
import io.legato.kazusa.utils.ColorUtils
import io.legato.kazusa.utils.FileDoc
import io.legato.kazusa.utils.find
import io.legato.kazusa.utils.getPrefString
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.isTv
import io.legato.kazusa.utils.setLightStatusBar
import io.legato.kazusa.utils.setNavigationBarColorAuto
import io.legato.kazusa.utils.setOnApplyWindowInsetsListenerCompat
import io.legato.kazusa.utils.showDialogFragment
import io.legato.kazusa.utils.themeColor
import io.legato.kazusa.utils.viewbindingdelegate.viewBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 阅读界面
 */
abstract class BaseReadBookActivity :
    VMBaseActivity<ActivityBookReadBinding, ReadBookViewModel>(imageBg = false) {

    override val binding by viewBinding(ActivityBookReadBinding::inflate)
    override val viewModel by viewModels<ReadBookViewModel>()
    protected val menuLayoutIsVisible
        get() = bottomDialog > 0 || binding.readMenu.isVisible || binding.searchMenu.bottomMenuVisible

    var bottomDialog = 0
        set(value) {
            if (field != value) {
                field = value
                onBottomDialogChange()
            }
        }
    private val selectBookFolderResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ReadBook.book?.let { book ->
                FileDoc.fromUri(uri, true).find(book.originName)?.let { doc ->
                    book.bookUrl = doc.uri.toString()
                    book.save()
                    viewModel.loadChapterList(book)
                } ?: ReadBook.upMsg("找不到文件")
            }
        } ?: ReadBook.upMsg("没有权限访问")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ReadBook.msg = null
        setOrientation()
        upLayoutInDisplayCutoutMode()
        super.onCreate(savedInstanceState)
        binding.navigationBar.doOnDetach {
            binding.navigationBar.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updateLayoutParams {
                    height = insets.bottom
                }
                windowInsets
            }
        }
        viewModel.permissionDenialLiveData.observe(this) {
            selectBookFolderResult.launch {
                mode = HandleFileContract.DIR_SYS
                title = "选择书籍所在文件夹"
            }
        }
        if (!LocalConfig.readHelpVersionIsLast) {
            if (isTv) {
                showCustomPageKeyConfig()
            } else {
                showClickRegionalConfig()
            }
        }
    }

    private fun onBottomDialogChange() {
        when (bottomDialog) {
            0 -> onMenuHide()
            1 -> onMenuShow()
        }
    }

    open fun onMenuShow() {

    }

    open fun onMenuHide() {

    }

    fun showPaddingConfig() {
        showDialogFragment<PaddingConfigDialog>()
    }

    fun showBgTextConfig() {
        showDialogFragment<BgTextConfigDialog>()
    }

    fun showClickRegionalConfig() {
        showDialogFragment<ClickActionConfigDialog>()
    }

    private fun showCustomPageKeyConfig() {
        PageKeyDialog(this).show()
    }

    /**
     * 屏幕方向
     */
    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientation() {
        when (AppConfig.screenOrientation) {
            "0" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            "1" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "2" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "3" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            "4" -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
    }

    /**
     * 更新状态栏,导航栏
     */
    fun upSystemUiVisibility(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean = true,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.run {
                if (toolBarHide && ReadBookConfig.hideNavigationBar) {
                    hide(WindowInsets.Type.navigationBars())
                } else {
                    show(WindowInsets.Type.navigationBars())
                }
                if (toolBarHide && ReadBookConfig.hideStatusBar) {
                    hide(WindowInsets.Type.statusBars())
                } else {
                    show(WindowInsets.Type.statusBars())
                }
            }
        }
        upSystemUiVisibilityO(isInMultiWindow, toolBarHide)
        if (toolBarHide) {
            setLightStatusBar(ReadBookConfig.durConfig.curStatusIconDark())
        } else {
            val statusBarColor =
                if (AppConfig.readBarStyleFollowPage
                    && ReadBookConfig.durConfig.curBgType() == 0
                ) {
                    ReadBookConfig.bgMeanColor
                } else {
                    ReadBookConfig.bgMeanColor
                }
            setLightStatusBar(ColorUtils.isColorLight(statusBarColor))
        }
    }

    @Suppress("DEPRECATION")
    private fun upSystemUiVisibilityO(
        isInMultiWindow: Boolean,
        toolBarHide: Boolean = true
    ) {
        var flag = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        if (!isInMultiWindow) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
        if (ReadBookConfig.hideNavigationBar) {
            flag = flag or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (toolBarHide) {
                flag = flag or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            }
        }
        if (ReadBookConfig.hideStatusBar && toolBarHide) {
            flag = flag or View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        window.decorView.systemUiVisibility = flag
    }

    fun upNavigationBarColor() {
        upNavigationBar()
        when {
            binding.readMenu.isVisible -> window.setNavigationBarColorAuto(themeColor(com.google.android.material.R.attr.colorSurfaceContainer))
            binding.searchMenu.bottomMenuVisible -> window.setNavigationBarColorAuto(themeColor(com.google.android.material.R.attr.colorSurface))
            bottomDialog > 0 -> window.setNavigationBarColorAuto(themeColor(com.google.android.material.R.attr.colorSurface))
            //!AppConfig.immNavigationBar -> super.upNavigationBarColor()
            else -> window.setNavigationBarColorAuto(ReadBookConfig.bgMeanColor)
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun upNavigationBar() {
        binding.navigationBar.gone(!menuLayoutIsVisible)
    }

    /**
     * 保持亮屏
     */
    fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * 适配刘海
     */
    private fun upLayoutInDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (ReadBookConfig.readBodyToLh) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
        }
    }

    @SuppressLint("InflateParams", "SetTextI18n")
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
                        CacheBook.start(this@BaseReadBookActivity, book, indices)
                    }
                }
                cancelButton()
            }
        }
    }

    fun showSimulatedReading() {
        val book = ReadBook.book ?: return
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val alertBinding = DialogSimulatedReadingBinding.inflate(layoutInflater).apply {
            srEnabled.isChecked = book.getReadSimulating()
            editStart.setText(book.getStartChapter().toString())
            editNum.setText(book.getDailyChapters().toString())
            startDate.setText(book.getStartDate()?.format(dateFormatter))
            startDate.isFocusable = false // 设置为false，不允许获得焦点
            startDate.isCursorVisible = false // 不显示光标
            startDate.setOnClickListener {
                // 获取当前日期
                val localStartDate = LocalDate.parse(startDate.text)
                // 创建 DatePickerDialog
                val datePickerDialog = DatePickerDialog(
                    root.context,
                    { _, yy, mm, dayOfMonth ->
                        // 使用Java 8的日期和时间API来格式化日期
                        val date = LocalDate.of(yy, mm + 1, dayOfMonth) // Java 8的LocalDate，月份从1开始
                        val formattedDate = date.format(dateFormatter)
                        startDate.setText(formattedDate)
                    }, localStartDate.year,
                    localStartDate.monthValue - 1,
                    localStartDate.dayOfMonth
                )
                datePickerDialog.show()
            }
        }
        alert(titleResource = R.string.simulated_reading) {
            customView { alertBinding.root }
            okButton {
                alertBinding.run {
                    val start = editStart.text!!.toString().let {
                        if (it.isEmpty()) 0 else it.toInt()
                    }
                    val num = editNum.text!!.toString().let {
                        if (it.isEmpty()) book.totalChapterNum else it.toInt()
                    }
                    val enabled = srEnabled.isChecked
                    val date = startDate.text!!.toString().let {
                        if (it.isEmpty()) LocalDate.now()
                        else LocalDate.parse(it, dateFormatter)
                    }
                    book.setStartDate(date)
                    book.setDailyChapters(num)
                    book.setStartChapter(start)
                    book.setReadSimulating(enabled)
                    book.save()
                    ReadBook.clearTextChapter()
                    viewModel.initData(intent)
                }
            }
            cancelButton()
        }
    }

    fun showCharsetConfig() {
        alert(R.string.set_charset) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "charset"
                editView.setFilterValues(charsets)
                editView.setText(ReadBook.book?.charset)
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    ReadBook.setCharset(it)
                }
            }
            cancelButton()
        }
    }

    fun showPageAnimConfig(success: () -> Unit) {
        val items = arrayListOf<String>()
        items.add(getString(R.string.btn_default_s))
        items.add(getString(R.string.page_anim_cover))
        items.add(getString(R.string.page_anim_slide))
        items.add(getString(R.string.page_anim_simulation))
        items.add(getString(R.string.page_anim_scroll))
        items.add(getString(R.string.page_anim_none))
        selector(R.string.page_anim, items) { _, i ->
            ReadBook.book?.setPageAnim(i - 1)
            success()
        }
    }

    fun isPrevKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val prevKeysStr = getPrefString(PreferKey.prevKeys)
        return prevKeysStr?.split(",")?.contains(keyCode.toString()) ?: false
    }

    fun isNextKey(keyCode: Int): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }
        val nextKeysStr = getPrefString(PreferKey.nextKeys)
        return nextKeysStr?.split(",")?.contains(keyCode.toString()) ?: false
    }
}