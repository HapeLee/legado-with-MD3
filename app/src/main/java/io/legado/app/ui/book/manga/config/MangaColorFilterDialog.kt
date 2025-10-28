package io.legado.app.ui.book.manga.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogMangaColorFilterBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.invisible
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

class MangaColorFilterDialog : BaseBottomSheetDialogFragment(R.layout.dialog_manga_color_filter) {
    private val binding by viewBinding(DialogMangaColorFilterBinding::bind)
    private val mConfig =
        GSON.fromJsonObject<MangaColorFilterConfig>(AppConfig.mangaColorFilter).getOrNull()
            ?: MangaColorFilterConfig()
    private val callback: Callback? get() = activity as? Callback

    private var mMangaEInkThreshold = AppConfig.mangaEInkThreshold


    override fun onStart() {
        super.onStart()
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initData()
        initView()
    }

    private fun initData() {
        binding.run {
            chipAutoBrightness.isChecked = mConfig.autoBrightness

            if (AppConfig.enableMangaEInk) dsbEpaper.visible()
            else dsbEpaper.invisible()

            dsbEpaper.progress = mMangaEInkThreshold

            cpEpaper.isChecked = AppConfig.enableMangaEInk
            cpEnableGray.isChecked = AppConfig.enableMangaGray

            dsbBrightness.isEnabled = !mConfig.autoBrightness
            dsbBrightness.progress = mConfig.l
            dsbR.progress = mConfig.r
            dsbG.progress = mConfig.g
            dsbB.progress = mConfig.b
            dsbA.progress = mConfig.a
        }
    }

    private fun initView() {
        binding.run {
            chipAutoBrightness.setOnCheckedChangeListener { _, isChecked ->
                mConfig.autoBrightness = isChecked
                dsbBrightness.isEnabled = !isChecked
                callback?.updateColorFilter(mConfig)
            }

            cpEpaper.setOnCheckedChangeListener { _, isChecked ->
                binding.dsbEpaper.isVisible = isChecked
                if (isChecked) {
                    cpEnableGray.isChecked = false
                    callback?.updateGrayMode(false)
                    dsbEpaper.visible()
                } else dsbEpaper.invisible()
                callback?.updateEpaperMode(isChecked, mMangaEInkThreshold)
            }

            cpEnableGray.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    binding.dsbEpaper.isVisible = false
                    cpEpaper.isChecked = false
                    callback?.updateEpaperMode(false, mMangaEInkThreshold)
                }
                dsbEpaper.invisible()
                callback?.updateGrayMode(isChecked)
            }

            dsbEpaper.onChanged = {
                mMangaEInkThreshold = it
                callback?.updateEpaperMode(true, it)
            }

            dsbBrightness.onChanged = {
                mConfig.l = it
                callback?.updateColorFilter(mConfig)
            }
            dsbR.onChanged = {
                mConfig.r = it
                callback?.updateColorFilter(mConfig)
            }
            dsbG.onChanged = {
                mConfig.g = it
                callback?.updateColorFilter(mConfig)
            }
            dsbB.onChanged = {
                mConfig.b = it
                callback?.updateColorFilter(mConfig)
            }
            dsbA.onChanged = {
                mConfig.a = it
                callback?.updateColorFilter(mConfig)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AppConfig.mangaColorFilter = mConfig.toJson()
        AppConfig.mangaEInkThreshold = mMangaEInkThreshold
    }

    interface Callback {
        fun updateColorFilter(config: MangaColorFilterConfig)
        fun updateEpaperMode(enabled: Boolean, threshold: Int)
        fun updateGrayMode(enabled: Boolean)
    }

}