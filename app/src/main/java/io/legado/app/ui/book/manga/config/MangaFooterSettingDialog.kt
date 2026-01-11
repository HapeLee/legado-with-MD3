package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
import androidx.transition.TransitionManager
import com.google.android.material.chip.Chip
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogMangaFooterSettingBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.manga.entities.MangaFooterConfig
import io.legado.app.ui.widget.ReaderInfoBarView
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class MangaFooterSettingDialog :
    BaseBottomSheetDialogFragment(R.layout.dialog_manga_footer_setting) {

    companion object {
        const val MANGA_B = 1919
    }

    val config = GSON.fromJsonObject<MangaFooterConfig>(AppConfig.mangaFooterConfig).getOrNull()
        ?: MangaFooterConfig()

    var initialWebtoonSidePadding: Int = 0
    var initialScrollMode: Int = MangaScrollMode.WEBTOON

    var callback: Callback? = null

    private val binding by viewBinding(DialogMangaFooterSettingBinding::bind)

    override fun onStart() {
        super.onStart()

    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {

        binding.chipGroupScrollMode.removeAllViews()

        MangaScrollMode.ALL.forEach { mode ->
            binding.chipGroupScrollMode.addView(Chip(requireContext()).apply {
                text = MangaScrollMode.labelOf(mode)
                isCheckable = true
                isChecked = (mode == initialScrollMode)

                setOnClickListener {
                    callback?.onScrollModeChanged(mode)
                    TransitionManager.beginDelayedTransition(binding.rootView)
                    binding.llWebtoon.isVisible = mode == MangaScrollMode.WEBTOON ||
                            mode == MangaScrollMode.WEBTOON_WITH_GAP
                }
                setOnLongClickListener {
                    AppConfig.mangaScrollMode = mode
                    toastOnUi("已设置为全局默认模式")
                    true
                }
            })
        }

        binding.btnBackgroundColor.color = AppConfig.mangaBackground
        binding.btnBackgroundColor.setOnClickListener {
            dismiss()
            ColorPickerDialog.newBuilder()
                .setColor(AppConfig.mangaBackground)
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(MANGA_B)
                .show(requireActivity())
        }

        binding.llWebtoon.isVisible = initialScrollMode == MangaScrollMode.WEBTOON ||
                initialScrollMode == MangaScrollMode.WEBTOON_WITH_GAP

        binding.scvPadding.apply {
            valueFormat = { "$it %" }
            progress = initialWebtoonSidePadding
            onChanged = { newValue ->
                callback?.upSidePadding(newValue)
            }
        }

        binding.checkboxDisableClickScroll.apply {
            isChecked = AppConfig.disableClickScroll
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onClickScrollDisabledChanged(isChecked)
            }
        }

        binding.checkboxScrollAnimation.apply {
            isChecked = AppConfig.disableMangaScrollAnimation
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onScrollAniDisabledChanged(isChecked)
            }
        }

        binding.checkboxMangaCrossFade.apply {
            isChecked = AppConfig.disableMangaCrossFade
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onCrossFadeDisabledChanged(isChecked)
            }
        }

        binding.checkboxDisableMangaScale.apply {
            isChecked = AppConfig.disableMangaScale
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onMangaScaleDisabledChanged(isChecked)
            }
        }

        binding.checkboxHideMangaTitle.apply {
            isChecked = AppConfig.hideMangaTitle
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onHideMangaTitleChanged(isChecked)
            }
        }

        binding.checkboxVolumeKeyPage.apply {
            isChecked = AppConfig.MangaVolumeKeyPage
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onVolumeKeyPageChanged(isChecked)
            }
        }

        binding.reverseVolumeKeyPage.apply {
            isChecked = AppConfig.reverseVolumeKeyPage
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onReverseVolumeKeyPageChanged(isChecked)
            }
        }

        binding.checkboxMangaLongClick.apply {
            isChecked = AppConfig.mangaLongClick
            setOnCheckedChangeListener { _, isChecked ->
                callback?.onMangaLongClickChanged(isChecked)
            }
        }

        binding.cbChapterLabel.run {
            isChecked = !config.hideChapterLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapterLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbChapter.isChecked = true
            }
        }

        binding.cbChapter.run {
            isChecked = !config.hideChapter
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapter = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbChapterLabel.isChecked = false
            }
        }

        binding.cbChapterName.run {
            isChecked = !config.hideChapterName
            setOnCheckedChangeListener { _, isChecked ->
                config.hideChapterName = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
            }
        }

        binding.cbPageNumberLabel.run {
            isChecked = !config.hidePageNumberLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hidePageNumberLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbPageNumber.isChecked = true
            }
        }

        binding.cbPageNumber.run {
            isChecked = !config.hidePageNumber
            setOnCheckedChangeListener { _, isChecked ->
                config.hidePageNumber = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbPageNumberLabel.isChecked = false
            }
        }

        binding.cbProgressRatioLabel.run {
            isChecked = !config.hideProgressRatioLabel
            setOnCheckedChangeListener { _, isChecked ->
                config.hideProgressRatioLabel = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (isChecked) binding.cbProgressRatio.isChecked = true
            }
        }

        binding.cbProgressRatio.run {
            isChecked = !config.hideProgressRatio
            setOnCheckedChangeListener { _, isChecked ->
                config.hideProgressRatio = !isChecked
                postEvent(EventBus.UP_MANGA_CONFIG, config)
                updateChapterText()
                if (!isChecked) binding.cbProgressRatioLabel.isChecked = false
            }
        }

        binding.rgFooterOrientation.check(
            when {
                config.hideFooter -> R.id.rb_hide
                config.footerOrientation == ReaderInfoBarView.ALIGN_CENTER -> R.id.rb_center
                else -> R.id.rb_left
            }
        )

        binding.rgFooterOrientation.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.rb_left -> {
                    config.hideFooter = false
                    config.footerOrientation = ReaderInfoBarView.ALIGN_LEFT
                }
                R.id.rb_center -> {
                    config.hideFooter = false
                    config.footerOrientation = ReaderInfoBarView.ALIGN_CENTER
                }
                R.id.rb_hide -> {
                    config.hideFooter = true
                }
            }
            updateChapterText()
            postEvent(EventBus.UP_MANGA_CONFIG, config)
        }

        binding.btnColorFilter.setOnClickListener {
            callback?.showColorFilterConfig()
            dismiss()
        }

        binding.btnClickSet.setOnClickListener {
            callback?.showClickConfig()
            dismiss()
        }

        updateChapterText()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaFooterConfig = GSON.toJson(config)
    }

    private fun updateChapterText() {
        if (config.hideFooter) {
            binding.tvChapter.visibility = View.INVISIBLE
            return
        } else {
            binding.tvChapter.visibility = View.VISIBLE
        }

        binding.tvChapter.gravity = when (config.footerOrientation) {
            ReaderInfoBarView.ALIGN_LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            ReaderInfoBarView.ALIGN_CENTER -> Gravity.CENTER
            else -> Gravity.START
        }

        val label = if (!config.hideChapterLabel) "章节" else ""
        val chapter = if (!config.hideChapter) "1/45 " else ""
        val name = if (!config.hideChapterName) "第三话 " else ""

        val progressLabel = if (!config.hideProgressRatioLabel) "总进度" else ""
        val progress = if (!config.hideProgressRatio) "2.1%" else ""

        val pageLabel = if (!config.hidePageNumberLabel) "页数" else ""
        val page = if (!config.hidePageNumber) "4/30 " else ""

        val parts = listOf(
            name,
            pageLabel,
            page,
            label,
            chapter,
            progressLabel,
            progress
        ).filter { it.isNotEmpty() }
        binding.tvChapter.text = parts.joinToString(" ")
    }

    interface Callback {
        fun onAutoPageToggle(enable: Boolean)
        fun onAutoPageSpeedChanged(speed: Int)
        fun showColorFilterConfig()
        fun showClickConfig()
        fun onScrollModeChanged(mode: Int)
        fun upSidePadding(padding: Int)
        fun onClickScrollDisabledChanged(disabled: Boolean)
        fun onScrollAniDisabledChanged(disabled: Boolean)
        fun onCrossFadeDisabledChanged(disabled: Boolean)
        fun onMangaScaleDisabledChanged(disabled: Boolean)
        fun onHideMangaTitleChanged(hide: Boolean)
        fun onVolumeKeyPageChanged(enable: Boolean)
        fun onReverseVolumeKeyPageChanged(enable: Boolean)
        fun onMangaLongClickChanged(checked: Boolean)
    }

}