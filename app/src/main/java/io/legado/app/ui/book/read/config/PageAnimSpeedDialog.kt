package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogPageAnimSpeedBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.Locale


class PageAnimSpeedDialog : BaseBottomSheetDialogFragment(R.layout.dialog_page_anim_speed) {

    private val binding by viewBinding(DialogPageAnimSpeedBinding::bind)

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as ReadBookActivity).bottomDialog--
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        val bottomDialog = (activity as ReadBookActivity).bottomDialog++
        if (bottomDialog > 0) {
            dismiss()
            return@run
        }
        initOnChange()
        initData()
    }

    private fun initData() {
        val speed = AppConfig.simulationPageAnimV2Speed
        binding.tvPageAnimSpeed.text = String.format(Locale.ROOT, "%dms", speed)
        binding.seekPageAnimSpeed.value = speed.toFloat()
    }

    private fun initOnChange() {
        binding.seekPageAnimSpeed.addOnChangeListener { slider, value, fromUser ->
            val speed = value.toInt()
            binding.tvPageAnimSpeed.text = String.format(Locale.ROOT, "%dms", speed)
        }

        binding.seekPageAnimSpeed.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {

            }

            override fun onStopTrackingTouch(slider: Slider) {
                AppConfig.simulationPageAnimV2Speed = slider.value.toInt()
            }
        })
    }
}
