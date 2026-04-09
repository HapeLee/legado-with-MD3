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


class PageAnimSpeedDialog(private val animType: String, private val area: String) : BaseBottomSheetDialogFragment(R.layout.dialog_page_anim_speed) {

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
        // 显示方位信息
        val areaName = when(area) {
            "tl" -> getString(R.string.left_top)
            "ml" -> getString(R.string.left_middle)
            "bl" -> getString(R.string.left_bottom)
            "tr" -> getString(R.string.right_top)
            "mr" -> getString(R.string.right_middle)
            "br" -> getString(R.string.right_bottom)
            else -> area
        }
        binding.tvArea.text = areaName
        initOnChange()
        initData()
    }

    private fun initData() {
        val speed = getCurrentSpeed()
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
                setCurrentSpeed(slider.value.toInt())
            }
        })
    }

    private fun getCurrentSpeed(): Int {
        return when(animType) {
            "cover" -> AppConfig.getCoverPageAnimSpeed(area)
            "slide" -> AppConfig.getSlidePageAnimSpeed(area)
            "simulation" -> AppConfig.getSimulationPageAnimSpeed(area)
            "simulationV2" -> AppConfig.getSimulationPageAnimV2Speed(area)
            "scroll" -> AppConfig.getScrollPageAnimSpeed(area)
            "gradient" -> AppConfig.getGradientPageAnimSpeed(area)
            else -> 200
        }
    }

    private fun setCurrentSpeed(speed: Int) {
        when(animType) {
            "cover" -> AppConfig.setCoverPageAnimSpeed(area, speed)
            "slide" -> AppConfig.setSlidePageAnimSpeed(area, speed)
            "simulation" -> AppConfig.setSimulationPageAnimSpeed(area, speed)
            "simulationV2" -> AppConfig.setSimulationPageAnimV2Speed(area, speed)
            "scroll" -> AppConfig.setScrollPageAnimSpeed(area, speed)
            "gradient" -> AppConfig.setGradientPageAnimSpeed(area, speed)
        }
    }
}
