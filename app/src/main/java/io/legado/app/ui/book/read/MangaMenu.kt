package io.legado.app.ui.book.read

//import io.legado.app.lib.theme.bottomBackground
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.databinding.ViewMangaMenuBinding
import io.legado.app.help.source.getSourceType
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.domain.gateway.ReadSettingsGateway
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.openUrl
import io.legado.app.utils.startActivity
import io.legado.app.utils.visible

class MangaMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding = ViewMangaMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private val callBack: CallBack get() = activity as CallBack
    private val readSettingsGateway by lazy {
        GlobalContext.get().get<ReadSettingsGateway>()
    }
    var canShowMenu: Boolean = false

    private val sourceMenu by lazy {
        PopupMenu(context, binding.tvSourceAction).apply {
            inflate(R.menu.book_read_source)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_login -> callBack.showLogin()
                    R.id.menu_chapter_pay -> callBack.payAction()
                    R.id.menu_edit_source -> callBack.openSourceEditActivity()
                    R.id.menu_disable_source -> callBack.disableSource()
                }
                true
            }
        }
    }

    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private var isMenuOutAnimating = false
    //private var bgColor = context.bottomBackground

    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@MangaMenu.invisible()
            binding.titleBar.invisible()
            binding.bottomMenu.invisible()
            isMenuOutAnimating = false
            canShowMenu = false
            callBack.upSystemUiVisibility(false)
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.tvSourceAction.text =
                ReadManga.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
            updateSourceActionDescription()
            callBack.upSystemUiVisibility(true)
            binding.tvSourceAction.isGone = false
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.run {
                vwMenuBg.setOnClickListener { runMenuOut() }
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        initView()
        bindEvent()
    }

    private fun initView() = binding.run {
        initAnimation()
//        val brightnessBackground = GradientDrawable()
//        brightnessBackground.cornerRadius = 5F.dpToPx()
//        //brightnessBackground.setColor(ColorUtils.adjustAlpha(bgColor, 0.5f))
//        if (ReadConfig.isEInkMode) {
//            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
//            bottomMenu.setBackgroundResource(R.drawable.bg_eink_border_top)
//        } else {
//            //bottomMenu.setBackgroundColor(bgColor)
//        }
        if (ReadConfig.showReadTitleAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        /**
         * 确保视图不被导航栏遮挡
         */
        bottomView.applyNavigationBarPadding()
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun runMenuOut(anim: Boolean = !ReadConfig.isEInkMode) {
        if (isMenuOutAnimating) {
            return
        }
        if (this.isVisible) {
            if (anim) {
                binding.titleBar.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    fun runMenuIn(anim: Boolean = !ReadConfig.isEInkMode) {
        this.visible()
        binding.titleBar.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBar.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun upBookView() {
        binding.titleBar.title = " "
        binding.tvBookName.text = ReadManga.book?.name
        updateSourceActionDescription()
        ReadManga.curMangaChapter?.let {
            binding.tvChapterName.text = ReadManga.book?.durChapterTitle
            binding.tvPre.isEnabled = ReadManga.durChapterIndex != 0
            binding.tvNext.isEnabled =
                ReadManga.durChapterIndex != ReadManga.simulatedChapterSize - 1
        } ?: let {
            binding.tvChapterUrl.gone()
        }
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadConfig.readUrlInBrowser) {
                context.openUrl(tvChapterUrl.text.toString().substringBefore(",{"))
            } else {
                context.startActivity<WebViewActivity> {
                    val url = tvChapterUrl.text.toString()
                    val bookSource = ReadBook.bookSource
                    putExtra("title", tvChapterName.text)
                    putExtra("url", url)
                    putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                    putExtra("sourceName", bookSource?.bookSourceName)
                    putExtra("sourceType", bookSource?.getSourceType())
                }
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            context.alert(R.string.open_fun) {
                setMessage(R.string.use_browser_open)
                okButton {
                    updateReadUrlInBrowser(true)
                }
                noButton {
                    updateReadUrlInBrowser(false)
                }
            }
            true
        }
        tvBookName.setOnClickListener{
            callBack.openBookInfoActivity()
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)
        tvSourceAction.setOnLongClickListener {
            sourceMenu.menu.findItem(R.id.menu_chapter_pay).isVisible =
                !ReadManga.bookSource?.getContentRule()?.payAction.isNullOrBlank()
            sourceMenu.show()
            true
        }
        tvNext.setOnClickListener {
            ReadManga.moveToNextChapter(true)
        }
        tvPre.setOnClickListener {
            ReadManga.moveToPrevChapter(true)
        }

        seekReadPage.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                HapticFeedbackConstantsCompat.TEXT_HANDLE_MOVE
                updateSeekAccessibility(
                    value = value.toInt(),
                    count = seekReadPage.valueTo.toInt().coerceAtLeast(1),
                )
                callBack.skipToPage(value.toInt() - 1)
            }
        }

        seekReadPage.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                HapticFeedbackConstantsCompat.TEXT_HANDLE_MOVE
                vwMenuBg.setOnClickListener(null)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                vwMenuBg.setOnClickListener { runMenuOut() }
            }
        })

        ivCatalog.setOnClickListener {
            callBack.openCatalog()
        }

        ivSetting.setOnClickListener {
            callBack.showFooterConfig()
            runMenuOut()
        }

        btnAutoPage.setOnLongClickListener {
            callBack.onAutoPageToggle2()
            true
        }

        btnAutoPage.setOnClickListener {
            callBack.showScrollModeDialog()
        }

    }

    private fun updateReadUrlInBrowser(enabled: Boolean) {
        activity?.lifecycleScope?.launch {
            readSettingsGateway.update { it.copy(readUrlInBrowser = enabled) }
        }
    }

    fun upSeekBar(value: Int, count: Int) {
        binding.seekReadPage.apply {
            if (count <= 1) {
                isEnabled = false
                valueFrom = 1f
                valueTo = 2f
                this.value = 1f
            } else {
                isEnabled = true
                valueFrom = 1f
                valueTo = count.toFloat()
                stepSize = 1f
                this.value = value.toFloat().coerceIn(valueFrom, valueTo)
            }
            updateSeekAccessibility(
                value = this.value.toInt(),
                count = if (count <= 1) 1 else count,
            )
        }
    }

    private fun updateSeekAccessibility(value: Int, count: Int) {
        binding.seekReadPage.contentDescription = context.getString(R.string.progress)
        ViewCompat.setStateDescription(
            binding.seekReadPage,
            context.getString(
                R.string.a11y_read_progress_page,
                value.coerceAtLeast(1),
                count.coerceAtLeast(1),
            )
        )
    }

    private fun updateSourceActionDescription() {
        binding.tvSourceAction.contentDescription = context.getString(
            R.string.a11y_book_source_action,
            binding.tvSourceAction.text,
        )
    }

    interface CallBack {
        fun openBookInfoActivity()
        fun upSystemUiVisibility(menuIsVisible: Boolean)
        fun skipToPage(index: Int)
        fun openCatalog()
        fun showFooterConfig()
        fun showScrollModeDialog()
        fun onAutoPageToggle2()
        fun showLogin()
        fun payAction()
        fun openSourceEditActivity()
        fun disableSource()
    }

}
