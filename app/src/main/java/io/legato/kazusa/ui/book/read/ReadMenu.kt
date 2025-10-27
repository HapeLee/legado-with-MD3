package io.legato.kazusa.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnAttach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonGroup
import com.google.android.material.overflow.OverflowLinearLayout
import com.google.android.material.slider.Slider
import io.legato.kazusa.R
import io.legato.kazusa.constant.PreferKey
import io.legato.kazusa.databinding.ViewReadMenuBinding
import io.legato.kazusa.help.config.AppConfig
import io.legato.kazusa.help.config.LocalConfig
import io.legato.kazusa.help.config.ThemeConfig
import io.legato.kazusa.help.coroutine.Coroutine
import io.legato.kazusa.help.source.getSourceType
import io.legato.kazusa.lib.dialogs.alert
import io.legato.kazusa.model.ReadBook
import io.legato.kazusa.ui.browser.WebViewActivity
import io.legato.kazusa.ui.widget.seekbar.SeekBarChangeListener
import io.legato.kazusa.utils.ConstraintModify
import io.legato.kazusa.utils.VibrationUtils
import io.legato.kazusa.utils.activity
import io.legato.kazusa.utils.applyNavigationBarPadding
import io.legato.kazusa.utils.dpToPx
import io.legato.kazusa.utils.getPrefBoolean
import io.legato.kazusa.utils.gone
import io.legato.kazusa.utils.invisible
import io.legato.kazusa.utils.loadAnimation
import io.legato.kazusa.utils.modifyBegin
import io.legato.kazusa.utils.openUrl
import io.legato.kazusa.utils.putPrefBoolean
import io.legato.kazusa.utils.startActivity
import io.legato.kazusa.utils.themeColor
import io.legato.kazusa.utils.visible
import org.checkerframework.checker.units.qual.cd
import splitties.views.onClick

/**
 * 阅读界面菜单
 */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var canShowMenu: Boolean = false
    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var confirmSkipToChapter: Boolean = false
    private var isMenuOutAnimating = false

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

    private val fadeIn = AlphaAnimation(0f, 1f).apply {
        duration = 280
        fillAfter = true
    }

    private val fadeOut = AlphaAnimation(1f, 0f).apply {
        duration = 280
        fillAfter = true
    }

//    private val immersiveMenu: Boolean
//        get() = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
//    private var bgColor: Int = if (immersiveMenu) {
//        kotlin.runCatching {
//            ReadBookConfig.durConfig.curBgStr().toColorInt()
//        }.getOrDefault(context.bottomBackground)
//    } else {
//        context.bottomBackground
//    }
//    private var textColor: Int = if (immersiveMenu) {
//        ReadBookConfig.durConfig.curTextColor()
//    } else {
//        context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
//    }

//    private var bottomBackgroundList: ColorStateList = Selector.colorBuild()
//        .setDefaultColor(bgColor)
//        .setPressedColor(ColorUtils.darkenColor(bgColor))
//        .create()
    private var onMenuOutEnd: (() -> Unit)? = null

    private val showBrightnessView
        get() = context.getPrefBoolean(
            PreferKey.showBrightnessView,
            true
        )

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

    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.tvSourceAction.text =
                ReadBook.bookSource?.bookSourceName ?: context.getString(R.string.book_source)
            binding.tvSourceAction.isGone = ReadBook.isLocalBook
            callBack.upSystemUiVisibility()
            binding.llBrightness.visible(showBrightnessView)
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.vwMenuBg.setOnClickListener { runMenuOut() }
            callBack.upSystemUiVisibility()
            if (!LocalConfig.readMenuHelpVersionIsLast) {
                callBack.showHelp()
            }
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@ReadMenu.invisible()
            binding.titleBar.invisible()
            binding.bottomMenu.invisible()
            canShowMenu = false
            isMenuOutAnimating = false
            onMenuOutEnd?.invoke()
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        doOnAttach {
            initView()
            upBrightnessState()
            bindEvent()
        }
    }

    private fun initView() = binding.run {
        initAnimation()
        updateSliderVisibility()
        val brightnessBackground = GradientDrawable()
        brightnessBackground.cornerRadius = 5F.dpToPx()
        llBrightness.background = brightnessBackground
        if (AppConfig.isEInkMode) {
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
        } else {
            //llBottomBg.setBackgroundColor(bgColor)
        }

        llBrightness.setOnClickListener(null)
        seekBrightness.post {
            seekBrightness.progress = AppConfig.readBrightness
        }
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        binding.bottomView.post {
            val allButtons = getUserButtons()
            renderButtons(binding.bottomView, allButtons)
        }
        val alpha = (AppConfig.menuAlpha / 100f * 255).toInt()
        val color = context.themeColor(com.google.android.material.R.attr.colorSurfaceContainer)
        titleBar.setBackgroundColor(ColorUtils.setAlphaComponent(color, alpha))
        cdSlider.setCardBackgroundColor(ColorUtils.setAlphaComponent(color, alpha))
        tvPre.alpha = AppConfig.menuAlpha / 100f * 255
        tvNext.alpha = AppConfig.menuAlpha / 100f * 255
        upBrightnessVwPos()
        /**
         * 确保视图不被导航栏遮挡
         */
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.bottomMenu.applyNavigationBarPadding()
        } else {
            bottomView.setBackgroundColor(ColorUtils.setAlphaComponent(color, alpha))
            binding.bottomView.applyNavigationBarPadding()
        }
    }

    fun reset() {
        upColorConfig()
        initView()
    }

    fun refreshMenuColorFilter() {
//        if (immersiveMenu) {
//            //binding.titleBar.setColorFilter(textColor)
//        }
    }

    private fun upColorConfig() {
//        bgColor = if (immersiveMenu) {
//            kotlin.runCatching {
//                Color.parseColor(ReadBookConfig.durConfig.curBgStr())
//            }.getOrDefault(context.bottomBackground)
//        } else {
//            context.bottomBackground
//        }
//        textColor = if (immersiveMenu) {
//            ReadBookConfig.durConfig.curTextColor()
//        } else {
//            context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
//        }
//        bottomBackgroundList = Selector.colorBuild()
//            .setDefaultColor(bgColor)
//            .setPressedColor(ColorUtils.darkenColor(bgColor))
//            .create()
    }

    fun upBrightnessState() {
        if (brightnessAuto()) {
            binding.ivBrightnessAuto.setColorFilter(context.themeColor(androidx.appcompat.R.attr.colorPrimary))
            binding.seekBrightness.isEnabled = false
        } else {
            binding.ivBrightnessAuto.setColorFilter(context.themeColor(com.google.android.material.R.attr.colorOnSurface))
            binding.seekBrightness.isEnabled = true
        }
        setScreenBrightness(AppConfig.readBrightness.toFloat())
    }

    /**
     * 设置屏幕亮度
     */
    fun setScreenBrightness(value: Float) {
        activity?.run {
            var brightness = BRIGHTNESS_OVERRIDE_NONE
            if (!brightnessAuto() && value != BRIGHTNESS_OVERRIDE_NONE) {
                brightness = value
                if (brightness < 1f) brightness = 1f
                brightness /= 255f
            }
            val params = window.attributes
            params.screenBrightness = brightness
            window.attributes = params
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        callBack.onMenuShow()
        this.visible()
        binding.titleBar.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBar.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
            updateBrightnessVisibility(true)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        callBack.onMenuHide()
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            if (anim) {
                binding.titleBar.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
                updateBrightnessVisibility(false)

            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    fun updateBrightnessVisibility(boolean: Boolean) {
        if (showBrightnessView) {
            if(boolean){
                binding.llBrightness.startAnimation(fadeIn)
            }else{
                binding.llBrightness.startAnimation(fadeOut)
            }
        }
    }

    private fun brightnessAuto(): Boolean {
        return context.getPrefBoolean("brightnessAuto", true) || !showBrightnessView
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        titleBar.toolbar.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        tvBookName.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadBook.isLocalBook) {
                return@OnClickListener
            }
            if (AppConfig.readUrlInBrowser) {
                context.openUrl(tvChapterUrl.text.toString().substringBefore(",{"))
            } else {
                Coroutine.async {
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
        }
        val chapterViewLongClickListener = OnLongClickListener {
            if (ReadBook.isLocalBook) {
                return@OnLongClickListener true
            }
            context.alert(R.string.open_fun) {
                setMessage(R.string.use_browser_open)
                okButton {
                    AppConfig.readUrlInBrowser = true
                }
                noButton {
                    AppConfig.readUrlInBrowser = false
                }
            }
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        //书源操作
        tvSourceAction.onClick {
            sourceMenu.menu.findItem(R.id.menu_login).isVisible =
                !ReadBook.bookSource?.loginUrl.isNullOrEmpty()
            sourceMenu.menu.findItem(R.id.menu_chapter_pay).isVisible =
                !ReadBook.bookSource?.loginUrl.isNullOrEmpty()
                        && ReadBook.curTextChapter?.isVip == true
                        && ReadBook.curTextChapter?.isPay != true
            sourceMenu.show()
        }
        //亮度跟随
        ivBrightnessAuto.setOnClickListener {
            context.putPrefBoolean("brightnessAuto", !brightnessAuto())
            upBrightnessState()
        }
        //亮度调节
        seekBrightness.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setScreenBrightness(progress.toFloat())
                }
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                AppConfig.readBrightness = seekBar.progress
            }

        })

        vwBrightnessPosAdjust.setOnClickListener {
            AppConfig.brightnessVwPos = !AppConfig.brightnessVwPos
            upBrightnessVwPos()
        }

        seekReadPage.addOnChangeListener { slider, value, fromUser ->
            if (fromUser) {
                if (AppConfig.progressBarBehavior == "page")
                    ReadBook.skipToPage(value.toInt() - 1)
                VibrationUtils.vibrate(context, 12)
            }
        }

        seekReadPage.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                vwMenuBg.setOnClickListener(null)
                VibrationUtils.vibrate(context, 16)
            }

            override fun onStopTrackingTouch(slider: Slider) {
                vwMenuBg.setOnClickListener { runMenuOut() }
                val progress = slider.value.toInt()

                when (AppConfig.progressBarBehavior) {
                    "page" -> ReadBook.skipToPage(progress - 1)
                    "chapter" -> {
                        if (confirmSkipToChapter) {
                            callBack.skipToChapter(progress - 1)
                        } else {
                            context.alert("章节跳转确认", "确定要跳转章节吗？") {
                                yesButton {
                                    confirmSkipToChapter = true
                                    callBack.skipToChapter(progress - 1)
                                }
                                noButton { upSeekBar() }
                                onCancelled { upSeekBar() }
                            }
                        }
                    }
                }
            }
        })

        //上一章
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }

        //下一章
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }
    }

    private fun updateSliderVisibility() {
        when (AppConfig.readSliderMode) {
            "0" -> {
                binding.llSlider.gravity = Gravity.CENTER
                binding.llSlider.isVisible = true
                binding.cdSlider.isVisible = true
                binding.tvPre.isVisible = true
                binding.tvNext.isVisible = true
            }

            "1" -> {
                binding.llSlider.gravity = Gravity.CENTER
                binding.llSlider.isVisible = false
                binding.cdSlider.isVisible = false
                binding.tvPre.isVisible = false
                binding.tvNext.isVisible = false
            }

            "2" -> {
                binding.llSlider.gravity = Gravity.START
                binding.llSlider.isVisible = true
                binding.cdSlider.isVisible = false
                binding.tvPre.isVisible = true
                binding.tvNext.isVisible = true
            }

            "3" -> {
                binding.llSlider.gravity = Gravity.END
                binding.llSlider.isVisible = true
                binding.cdSlider.isVisible = false
                binding.tvPre.isVisible = true
                binding.tvNext.isVisible = true
            }

            "4" -> {
                binding.llSlider.gravity = Gravity.CENTER
                binding.llSlider.isVisible = true
                binding.cdSlider.isVisible = true
                binding.tvPre.isVisible = false
                binding.tvNext.isVisible = false
            }

            else -> {
                binding.llSlider.isVisible = true
                binding.cdSlider.isVisible = true
                binding.tvPre.isVisible = true
                binding.tvNext.isVisible = true
            }
        }
    }

    private val buttonMap = mutableMapOf<String, MaterialButton>()

    fun renderButtons(group: MaterialButtonGroup, buttons: List<ToolButton>) {
        group.removeAllViews()
        buttonMap.clear()

        buttons.forEach { btn ->
            val style = com.google.android.material.R.attr.materialIconButtonOutlinedStyle
            val button = MaterialButton(group.context, null, style).apply {
                id = btn.id.hashCode()
                setIconResource(btn.iconRes)
                contentDescription = btn.description
                tooltipText = btn.description
                strokeWidth = 0
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                maxLines = 1
                iconTint = ColorStateList.valueOf(context.themeColor(androidx.appcompat.R.attr.colorPrimary))
                setOnClickListener { btn.onClick() }
                btn.onLongClick?.let { longAction ->
                    setOnLongClickListener {
                        longAction()
                        true
                    }
                }
            }
            group.addView(
                button,
                OverflowLinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f)
            )
            val lp =
                button.layoutParams as MaterialButtonGroup.LayoutParams
            lp.overflowText = btn.description
            buttonMap[btn.id] = button
        }
    }

    private fun getAllButtons(): List<ToolButton> {
        return listOf(
            ToolButton(
                id = "search",
                iconRes = R.drawable.ic_search,
                description = context.getString(R.string.search_content),
                onClick = { runMenuOut{ callBack.openSearchActivity(null) } }
            ),
            ToolButton(
                id = "auto_page",
                iconRes = R.drawable.ic_auto_page,
                description = context.getString(R.string.auto_next_page),
                onClick = { runMenuOut { callBack.autoPage() } }
            ),
            ToolButton(
                id = "catalog",
                iconRes = R.drawable.ic_toc,
                description = context.getString(R.string.chapter_list),
                onClick = { runMenuOut { callBack.openChapterList() } }
            ),
            ToolButton(
                id = "read_aloud",
                iconRes = R.drawable.ic_read_aloud,
                description = context.getString(R.string.read_aloud),
                onClick = { runMenuOut { callBack.onClickReadAloud() } },
                onLongClick = { runMenuOut { callBack.onClickReadAloud() } }
            ),
            ToolButton(
                id = "setting",
                iconRes = R.drawable.ic_settings,
                description = context.getString(R.string.setting),
                onClick = { runMenuOut { callBack.showReadStyle() } }
            ),
            ToolButton(
                id = "addBookmark",
                iconRes = R.drawable.ic_bookmark,
                description = context.getString(R.string.bookmark),
                onClick = { runMenuOut { callBack.addBookmark() } }
            ),
            ToolButton(
                id = "theme",
                iconRes = if (AppConfig.isNightTheme) R.drawable.ic_daytime else R.drawable.ic_brightness,
                description = context.getString(R.string.day_night_switch),
                onClick = {
                    AppConfig.isNightTheme = !AppConfig.isNightTheme
                    ThemeConfig.applyDayNight(context)
                    buttonMap["theme"]?.setIconResource(
                        if (AppConfig.isNightTheme) R.drawable.ic_daytime else R.drawable.ic_brightness
                    )
                }
            ),
            ToolButton(
                id = "prev_chapter",
                iconRes = R.drawable.ic_previous,
                description = context.getString(R.string.previous_chapter),
                onClick = { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }
            ),
            ToolButton(
                id = "next_chapter",
                iconRes = R.drawable.ic_next,
                description = context.getString(R.string.next_chapter),
                onClick = { ReadBook.moveToNextChapter(true) }
            )
        )
    }

    private fun getUserButtons(): List<ToolButton> {
        val prefs by lazy {
            context.getSharedPreferences("tool_button_config", Context.MODE_PRIVATE)
        }
        val allButtons = getAllButtons().associateBy { it.id }

        val str = prefs.getString("tool_buttons", null)
        val savedList = str?.split(";")?.mapNotNull {
            val parts = it.split(",")
            if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
        } ?: emptyList()

        val result = mutableListOf<ToolButton>()

        if (savedList.isNotEmpty()) {
            savedList.forEach { (id, enabled) ->
                if (enabled) allButtons[id]?.let { result.add(it) }
            }

            getAllButtons().forEach { btn ->
                if (savedList.none { it.first == btn.id }) {
                    result.add(btn)
                }
            }
        } else {
            result.addAll(getAllButtons().take(5))
        }


        return result
    }

    fun setAutoPage(autoPage: Boolean) {
        buttonMap["auto_page"]?.apply {
            val icon = if (autoPage) R.drawable.ic_auto_page_stop else R.drawable.ic_auto_page
            val desc =
                context.getString(if (autoPage) R.string.auto_next_page_stop else R.string.auto_next_page)
            setIconResource(icon)
            contentDescription = desc
            tooltipText = desc
        }
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun upBookView() {
        val bookName = ReadBook.book?.name ?: ""

        val isTablet = (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE

        if (isTablet || resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.titleBar.title = bookName
            binding.tvBookName.gone()
        } else {
            binding.titleBar.title = " "
            binding.tvBookName.text = bookName
        }

        ReadBook.curTextChapter?.let {
            binding.tvChapterName.text = it.title
            if (!ReadBook.isLocalBook) {
                binding.tvChapterUrl.text = it.chapter.getAbsoluteURL()
                //binding.tvChapterUrl.visible()
            } else {
                binding.tvChapterUrl.gone()
            }
            upSeekBar()
            binding.tvPre.isEnabled = ReadBook.durChapterIndex != 0
            binding.tvNext.isEnabled = ReadBook.durChapterIndex != ReadBook.simulatedChapterSize - 1
        } ?: let {
            binding.tvChapterUrl.gone()
        }
    }

    fun upSeekBar() {
        //孩子们 土方法丑陋但有效
        binding.seekReadPage.apply {
            when (AppConfig.progressBarBehavior) {
                "page" -> {
                    ReadBook.curTextChapter?.let { chapter ->
                        if (chapter.pageSize >= 0 && ReadBook.durPageIndex >= 0) {
                            valueFrom = 1f
                            valueTo = chapter.pageSize.toFloat().coerceAtLeast(2f)
                            stepSize = 1f
                            value = (ReadBook.durPageIndex).coerceIn(1, chapter.pageSize).toFloat()
                        } else {
                            valueFrom = 0f
                            valueTo = 100000f
                            stepSize = 0f
                            value = 0f
                        }
                    }
                }

                "chapter" -> {
                    if (ReadBook.simulatedChapterSize > 0)
                    {
                        valueFrom = 1f
                        valueTo = ReadBook.simulatedChapterSize.toFloat().coerceAtLeast(2f)
                        stepSize = 1f
                        value = (ReadBook.durChapterIndex).coerceIn(1, ReadBook.simulatedChapterSize).toFloat()
                    } else {
                        valueFrom = 0f
                        valueTo = 100000f
                        stepSize = 0f
                        value = 0f
                    }
                }
            }
        }
    }

//    fun upSeekBar() {
//        binding.seekReadPage.apply {
//            when (AppConfig.progressBarBehavior) {
//                "page" -> {
//                    ReadBook.curTextChapter?.let {
//                        max = it.pageSize.minus(1)
//                        progress = ReadBook.durPageIndex
//                    }
//                }
//
//                "chapter" -> {
//                    max = ReadBook.simulatedChapterSize - 1
//                    progress = ReadBook.durChapterIndex
//                }
//            }
//        }
//    }

    fun setSeekPage(seek: Int) {
        binding.seekReadPage.value = seek.toFloat() + 1
    }

    private fun upBrightnessVwPos() {
        if (AppConfig.brightnessVwPos) {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.LEFT)
                .rightToRightOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        } else {
            binding.root.modifyBegin()
                .clear(R.id.ll_brightness, ConstraintModify.Anchor.RIGHT)
                .leftToLeftOf(R.id.ll_brightness, R.id.vw_menu_root)
                .commit()
        }
    }

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchActivity(searchWord: String?)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showReadStyle()
        fun addBookmark()
        fun showReadAloudDialog()
        fun upSystemUiVisibility()
        fun onClickReadAloud()
        fun showHelp()
        fun showLogin()
        fun payAction()
        fun disableSource()
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()
    }

    data class ToolButton(
        val id: String,             // 唯一标识
        val iconRes: Int,           // 图标资源
        val description: String,    // contentDescription / tooltipText
        val onClick: () -> Unit,    // 点击事件
        val onLongClick: (() -> Unit)? = null, // 可选长按
        var state: Boolean = false // 动态
    )
}