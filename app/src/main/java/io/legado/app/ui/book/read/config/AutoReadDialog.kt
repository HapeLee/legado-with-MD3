package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogAutoReadBinding
import io.legado.app.help.config.ReadBookConfig
//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.BaseReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.util.Locale


class AutoReadDialog : BaseBottomSheetDialogFragment(R.layout.dialog_auto_read) {

    private val binding by viewBinding(DialogAutoReadBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack

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
        initEvent()
    }

    private fun initData() {
        val speed = if (ReadBookConfig.autoReadSpeed < 1) 1 else ReadBookConfig.autoReadSpeed
        binding.tvReadSpeed.text = String.format(Locale.ROOT, "%ds", speed)
        binding.seekAutoRead.value = speed.toFloat()
    }

    private fun initOnChange() {
        binding.seekAutoRead.addOnChangeListener { slider, value, fromUser ->
            val speed = if (value < 1) 1 else value.toInt()
            binding.tvReadSpeed.text = String.format(Locale.ROOT, "%ds", speed)
        }

        binding.seekAutoRead.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {

            }

            override fun onStopTrackingTouch(slider: Slider) {
                ReadBookConfig.autoReadSpeed = if (slider.value < 1) 1 else slider.value.toInt()
                upTtsSpeechRate()
            }
        })
    }

    private fun initEvent() {
        binding.btnMainMenu.setOnClickListener {
            callBack?.showMenuBar()
            dismissAllowingStateLoss()
        }
        binding.btnSetting.setOnClickListener {
            (activity as BaseReadBookActivity).showPageAnimConfig {
                (activity as ReadBookActivity).upPageAnim()
                ReadBook.loadContent(false)
            }
        }
        binding.btnCatalog.setOnClickListener { callBack?.openChapterList() }
        binding.btnAutoPageStop.setOnClickListener {
            callBack?.autoPageStop()
            binding.btnAutoPageStop.post {
                dismissAllowingStateLoss()
            }
        }
    }

    private fun upTtsSpeechRate() {
        ReadAloud.upTtsSpeechRate(requireContext())
        if (!BaseReadAloudService.pause) {
            ReadAloud.pause(requireContext())
            ReadAloud.resume(requireContext())
        }
    }

    interface CallBack {
        fun showMenuBar()
        fun openChapterList()
        fun autoPageStop()
    }
}