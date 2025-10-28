package io.legado.app.ui.book.audio

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.scale
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.TransitionManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.slider.Slider
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Status
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityAudioPlayBinding
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCover
import io.legado.app.service.AudioPlayService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.ToolbarUtils.setAllIconsColor
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.startAnimation
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.onLongClick
import java.util.Locale

/**
 * 音频播放
 */
@SuppressLint("ObsoleteSdkInt")
class AudioPlayActivity :
    VMBaseActivity<ActivityAudioPlayBinding, AudioPlayViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    AudioPlay.CallBack {

    override val binding by viewBinding(ActivityAudioPlayBinding::inflate)
    override val viewModel by viewModels<AudioPlayViewModel>()
    private var adjustProgress = false
    private var playMode = AudioPlay.PlayMode.LIST_END_STOP
    private var playSpeed = 1f
    private var primaryFinalColor: Int = 0
    private var surfaceFinalColor: Int = 0
    private var secondaryFinalColor: Int = 0
    private var onSurfaceFinalColor: Int = 0
    private var secondaryContainerFinalColor: Int = 0
    private var currentJob: Job? = null
    private var wrappedContext: Context? = null

    private val progressTimeFormat by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("mm:ss", Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("mm:ss", Locale.getDefault())
        }
    }
    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            if (it.first != AudioPlay.book?.durChapterIndex
                || it.second == 0
            ) {
                AudioPlay.skipTo(it.first)
            }
        }
    }
    private val sourceEditResult =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upSource()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        val transform = MaterialContainerTransform().apply {
            addTarget(binding.root)
            scrimColor = Color.TRANSPARENT
        }
        window.sharedElementEnterTransition = transform
        window.sharedElementReturnTransition = transform
        super.onCreate(savedInstanceState)
        binding.root.transitionName = intent.getStringExtra("transitionName")
        primaryFinalColor =
            MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, -1)
        surfaceFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, -1)
        secondaryFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondary, -1)
        onSurfaceFinalColor =
            MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, -1)
        secondaryContainerFinalColor = MaterialColors.getColor(
            this,
            com.google.android.material.R.attr.colorSecondaryContainer,
            -1
        )
        setSupportActionBar(binding.toolBar)
        binding.toolBar.setTitle(" ")
        binding.titleBar.setBackgroundResource(R.color.transparent)
        AudioPlay.register(this)
        viewModel.titleData.observe(this) {
            binding.tvTitle.text = it
        }
        viewModel.coverData.observe(this) {
            upCover(it)
        }
        viewModel.initData(intent)
        initView()
        onBackPressedDispatcher.addCallback(this){
            if (savedInstanceState != null || !AudioPlay.inBookshelf) {
                finish()
            } else {
                supportFinishAfterTransition()
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.audio_play, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible = !AudioPlay.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_wake_lock)?.isChecked = AppConfig.audioPlayUseWakeLock
        menu.findItem(R.id.menu_media_control)?.isChecked = AppConfig.systemMediaControlCompatibilityChange
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source -> AudioPlay.book?.let {
                showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
            }

            R.id.menu_login -> AudioPlay.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                }
            }
            R.id.menu_media_control -> AppConfig.systemMediaControlCompatibilityChange = !AppConfig.systemMediaControlCompatibilityChange
            R.id.menu_wake_lock -> AppConfig.audioPlayUseWakeLock = !AppConfig.audioPlayUseWakeLock
            R.id.menu_copy_audio_url -> sendToClip(AudioPlayService.url)
            R.id.menu_edit_source -> AudioPlay.bookSource?.let {
                sourceEditResult.launch {
                    putExtra("sourceUrl", it.bookSourceUrl)
                }
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initView() {
        binding.ivTimer.isEnabled = false
        binding.ivFastForward.isEnabled = false

        binding.ivPlayMode.setOnClickListener {
            AudioPlay.changePlayMode()
        }

        observeEventSticky<AudioPlay.PlayMode>(EventBus.PLAY_MODE_CHANGED) {
            playMode = it
            updatePlayModeIcon()
        }
        binding.fabPlayStop.setOnClickListener {
            playButton()
        }
        binding.fabPlayStop.onLongClick {
            AudioPlay.stop()
        }
        binding.ivSkipNext.setOnClickListener {
            AudioPlay.next()
        }
        binding.ivSkipPrevious.setOnClickListener {
            AudioPlay.prev()
        }

//        binding.playerProgress.setOnSeekBarChangeListener(object : SeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
//                binding.tvDurTime.text = progressTimeFormat.format(progress.toLong())
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar) {
//                adjustProgress = true
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar) {
//                adjustProgress = false
//                AudioPlay.adjustProgress(seekBar.progress)
//            }
//        })

        binding.playerProgress.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.tvDurTime.text = progressTimeFormat.format(value.toLong())
            }
        }

        binding.playerProgress.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                adjustProgress = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                adjustProgress = false
                AudioPlay.adjustProgress(slider.value.toInt())
            }
        })

        binding.playerProgress.setLabelFormatter { value ->
            progressTimeFormat.format(value.toLong())
        }

        binding.ivChapter.setOnClickListener {
            AudioPlay.book?.let {
                tocActivityResult.launch(it.bookUrl)
            }
        }

        binding.ivFastForward.setOnClickListener { toggleFastForward() }
        binding.ivTimer.setOnClickListener { toggleTimer() }

        binding.llSubMenu.applyNavigationBarPadding()
    }


    private fun toggleFastForward() {
        TransitionManager.beginDelayedTransition(binding.root)
        if (binding.llSet.isVisible) {
            if (binding.ivTimer.isChecked) {
                initSlider(true)
            } else {
                binding.llSet.gone()
            }
            binding.ivTimer.isChecked = false
        } else {
            initSlider(true)
            binding.llSet.visible()
        }
    }

    private fun toggleTimer() {
        TransitionManager.beginDelayedTransition(binding.root)
        if (binding.llSet.isVisible) {
            if (binding.ivFastForward.isChecked) {
                initSlider(false)
            } else {
                binding.llSet.gone()
            }
            binding.ivFastForward.isChecked = false
        } else {
            initSlider(false)
            binding.llSet.visible()
        }
    }

    private fun initSlider(isAudioPlay: Boolean) {
        binding.settingSlider.clearOnChangeListeners()
        binding.settingSlider.clearOnSliderTouchListeners()
        binding.btnReset.clearOnCheckedChangeListeners()

        if (isAudioPlay) {
            binding.settingSlider.isEnabled = AudioPlay.status != Status.STOP
            binding.btnReset.setOnClickListener {
                binding.settingSlider.value = 1f
                AudioPlay.adjustSpeed(1f)
            }
            binding.settingSlider.apply {
                valueFrom = 0.5f
                valueTo = 5.0f
                stepSize = 0.1f
                value = playSpeed

                addOnChangeListener { _, newValue, fromUser ->
                    if (fromUser) {
                        AudioPlay.adjustSpeed(newValue)
                    }
                }

                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }

                    override fun onStopTrackingTouch(slider: Slider) {}
                })

                setLabelFormatter { value -> String.format(Locale.ROOT, "%.1fX", value) }
            }
        } else {
            binding.settingSlider.isEnabled = true
            binding.btnReset.setOnClickListener {
                binding.settingSlider.value = 0f
                AudioPlay.setTimer(0)
            }
            binding.settingSlider.apply {
                valueFrom = 0f
                valueTo = 180f
                stepSize = 1f
                value = AudioPlayService.timeMinute.toFloat()

                addOnChangeListener { _, newValue, fromUser ->
                    if (fromUser) AudioPlay.setTimer(newValue.toInt())
                }

                addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                    override fun onStartTrackingTouch(slider: Slider) {
                        slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }

                    override fun onStopTrackingTouch(slider: Slider) {}
                })

                setLabelFormatter { v -> "${v.toInt()}m" }
            }
        }
    }

    private fun updatePlayModeIcon() {
        binding.ivPlayMode.setIconResource(playMode.iconRes)
    }

    private fun upCover(path: String?) {
        BookCover.load(
            this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl,
            onLoadFinish = {
                binding.ivCover.post {
                    val drawable = binding.ivCover.drawable
                    if (drawable != null) {
                        addColorScheme(drawable)
                    }
                }
            }).into(binding.ivCover)
        if (!AppConfig.isEInkMode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BookCover.load(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl)
                    .into(binding.ivBg)
                val blurEffect = RenderEffect.createBlurEffect(120f, 120f, Shader.TileMode.CLAMP)
                binding.ivBg.setRenderEffect(blurEffect)
            } else {
                // 低版本直接加载模糊图
                BookCover.loadBlur(this, path, sourceOrigin = AudioPlay.bookSource?.bookSourceUrl)
                    .into(binding.ivBg)
            }
        }
        addColorScheme(binding.ivCover.drawable)
    }

    private fun playButton() {
        when (AudioPlay.status) {
            Status.PLAY -> AudioPlay.pause(this)
            Status.PAUSE -> AudioPlay.resume(this)
            else -> AudioPlay.loadOrUpPlayUrl()
        }
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
                this@AudioPlayActivity,
                options
            ).apply {
                resources.configuration.uiMode =
                    this@AudioPlayActivity.resources.configuration.uiMode
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

        val colorPrimary = MaterialColors.getColor(ctx, androidx.appcompat.R.attr.colorPrimary, -1)
        val colorSecondary =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSecondary, -1)
        val colorOnSurface =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, -1)
        val colorSurface =
            MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurface, -1)
        val colorSurfaceContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSurfaceContainer,
                -1
            )
        val colorSecondaryContainer =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorSecondaryContainer,
                -1
            )
        val colorOnSurfaceVariant =
            MaterialColors.getColor(
                ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                -1
            )

        val primaryTransition = ValueAnimator.ofArgb(primaryFinalColor, colorPrimary).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                binding.settingSlider.thumbTintList = ColorStateList.valueOf(color)
                binding.settingSlider.trackActiveTintList = ColorStateList.valueOf(color)
                binding.playerProgress.thumbTintList = ColorStateList.valueOf(color)
                binding.playerProgress.trackActiveTintList = ColorStateList.valueOf(color)
                binding.tvDurTime.setTextColor(color)
                binding.tvAllTime.setTextColor(color)
            }
        }

        val secondaryContainerTransition =
            ValueAnimator.ofArgb(secondaryContainerFinalColor, colorSecondaryContainer).apply {
                duration = 400L
                addUpdateListener { animation ->
                    val color = animation.animatedValue as Int
                    binding.fabPlayStop.backgroundTintList = ColorStateList.valueOf(color)
                    binding.btnReset.backgroundTintList = ColorStateList.valueOf(color)
                    binding.settingSlider.trackInactiveTintList = ColorStateList.valueOf(color)
                    binding.playerProgress.trackInactiveTintList = ColorStateList.valueOf(color)
                }
            }

        val surfaceTransition =
            ValueAnimator.ofArgb(surfaceFinalColor, colorSurfaceContainer).apply {
                duration = 400L
                addUpdateListener { animation ->
                    val color = animation.animatedValue as Int
                    binding.vwBg2.setBackgroundColor(color)
                }
            }

        val textTransition = ValueAnimator.ofArgb(onSurfaceFinalColor, colorOnSurface).apply {
            duration = 400L
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                binding.tvTitle.setTextColor(color)
            }
        }

        withContext(Dispatchers.Main) {
            surfaceTransition.start()
            textTransition.start()
            primaryTransition.start()
            secondaryContainerTransition.start()
        }

        surfaceFinalColor = colorSurface
        secondaryFinalColor = colorSecondary
        onSurfaceFinalColor = colorOnSurface
        primaryFinalColor = colorPrimary
        secondaryContainerFinalColor = colorSecondaryContainer

        binding.toolBar.setAllIconsColor(colorOnSurface)
        binding.progressLoading.setIndicatorColor(colorPrimary)
        binding.settingSlider.tickActiveTintList = ColorStateList.valueOf(colorSurface)
        binding.settingSlider.tickInactiveTintList = ColorStateList.valueOf(colorPrimary)

        binding.tvSubTitle.setTextColor(colorOnSurfaceVariant)
        binding.btnTimer.imageTintList = ColorStateList.valueOf(colorOnSurface)
        binding.tvSpeed.setTextColor(colorOnSurface)
        binding.tvTimer.setTextColor(colorOnSurface)

        binding.cdTimer.setCardBackgroundColor(colorSurfaceContainer)
        binding.cdSpeed.setCardBackgroundColor(colorSurfaceContainer)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked),
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_enabled)
        )

        val colors = intArrayOf(
            colorSurface,
            colorOnSurface,
            colorOnSurface.copy(alpha = 0.3f)
        )

        val stateList = ColorStateList(states, colors)
        listOf(
            binding.ivSkipNext, binding.ivSkipPrevious, binding.ivPlayMode, binding.ivTimer,
            binding.ivChapter, binding.ivFastForward, binding.btnReset, binding.fabPlayStop
        ).forEach { btn ->
            btn.setTextColor(stateList)
            btn.iconTint = stateList
        }
    }

    override val oldBook: Book?
        get() = AudioPlay.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isAudio) {
            viewModel.changeTo(source, book, toc)
        } else {
            AudioPlay.stop()
            lifecycleScope.launch {
                withContext(IO) {
                    AudioPlay.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    AudioPlay.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun finish() {
        val book = AudioPlay.book ?: return super.finish()

        if (AudioPlay.inBookshelf) {
            return super.finish()
        }

        if (!AppConfig.showAddToShelfAlert) {
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    AudioPlay.book?.removeType(BookType.notShelf)
                    AudioPlay.book?.save()
                    AudioPlay.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton { viewModel.removeFromBookshelf { super.finish() } }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (AudioPlay.status != Status.PLAY) {
            AudioPlay.stop()
        }
        AudioPlay.unregister(this)
    }

    @SuppressLint("SetTextI18n")
    override fun observeLiveBus() {
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                playButton()
            }
        }
        observeEventSticky<Int>(EventBus.AUDIO_STATE) { state ->
            AudioPlay.status = state

            val iconDrawable = if (state == Status.PLAY) {
                binding.ivTimer.isEnabled = true
                binding.ivFastForward.isEnabled = true
                AppCompatResources.getDrawable(this, R.drawable.play_anim)
            } else {
                AppCompatResources.getDrawable(this, R.drawable.pause_anim)
            }

            val bgDrawable = if (state == Status.PLAY) {
                AppCompatResources.getDrawable(this, R.drawable.bg_play_anim)
            } else {
                AppCompatResources.getDrawable(this, R.drawable.bg_pause_anim)
            }

            binding.fabPlayStop.icon = iconDrawable
            binding.fabPlayStop.background = bgDrawable

            iconDrawable?.startAnimation()
            bgDrawable?.startAnimation()
        }

        observeEventSticky<String>(EventBus.AUDIO_SUB_TITLE) {
            binding.tvSubTitle.text = it
            binding.ivSkipPrevious.isEnabled = AudioPlay.durChapterIndex > 0
            binding.ivSkipNext.isEnabled =
                AudioPlay.durChapterIndex < AudioPlay.simulatedChapterSize - 1
        }
//        observeEventSticky<Int>(EventBus.AUDIO_SIZE) {
//            binding.playerProgress.max = it
//            binding.tvAllTime.text = progressTimeFormat.format(it.toLong())
//        }
//        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) {
//            if (!adjustProgress) binding.playerProgress.progress = it
//            binding.tvDurTime.text = progressTimeFormat.format(it.toLong())
//        }
//        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
//            binding.playerProgress.secondaryProgress = it
//
//        }
        observeEventSticky<Int>(EventBus.AUDIO_SIZE) { size ->
            binding.playerProgress.valueTo = maxOf(1f, size.toFloat())
            binding.tvAllTime.text = progressTimeFormat.format(size.toLong())
        }

        observeEventSticky<Int>(EventBus.AUDIO_PROGRESS) { progress ->
            val slider = binding.playerProgress
            val safeValue = progress.toFloat().coerceIn(slider.valueFrom, slider.valueTo)
            if (!adjustProgress) slider.value = safeValue
            binding.tvDurTime.text = progressTimeFormat.format(progress.toLong())
        }

        observeEventSticky<Int>(EventBus.AUDIO_BUFFER_PROGRESS) {
            //updateBufferProgress(it)
        }

        observeEventSticky<Float>(EventBus.AUDIO_SPEED) {
            TransitionManager.beginDelayedTransition(binding.root)
            playSpeed = it
            binding.tvSpeed.text = String.format(Locale.ROOT, "%.1fX", it)
            if (it != 1f) {
                binding.cdSpeed.visible()
            } else
                binding.cdSpeed.gone()
        }
        observeEventSticky<Int>(EventBus.AUDIO_DS) {
            TransitionManager.beginDelayedTransition(binding.root)
            binding.tvTimer.text = "${it}m"
            if (it > 0) {
                binding.cdTimer.visible()
            } else
                binding.cdTimer.gone()
        }
    }

    override fun upLoading(loading: Boolean) {
        runOnUiThread {
            binding.progressLoading.visible(loading)
        }
    }

    override fun addToBookshelf(book: Book, toc: List<BookChapter>) {
        viewModel.addToBookshelf(book, toc) {
            toastOnUi("已添加到书架")
        }
    }

}

private fun Int.copy(alpha: Float): Int {
    val alpha = (alpha * 255).toInt()
    return (this and 0x00FFFFFF) or (alpha shl 24)
}

