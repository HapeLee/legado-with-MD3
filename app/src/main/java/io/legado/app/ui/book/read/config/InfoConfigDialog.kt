package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadInfoBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding

class InfoConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_read_info) {

    private val binding by viewBinding(DialogReadInfoBinding::bind)
    private val callBack get() = activity as? ReadBookActivity

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) { list ->
            if (list.contains(2)) {
                upBtnHeaderMode()
                upBtnFooterMode()
            }
        }
    }

    private fun initView() {
        ReadTipConfig.run {
            tipNames.let { tipNames ->
                binding.tvHeaderLeft.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderLeft)) { tipNames[none] }
                binding.tvHeaderMiddle.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderMiddle)) { tipNames[none] }
                binding.tvHeaderRight.text =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderRight)) { tipNames[none] }
                binding.tvFooterLeft.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterLeft)) { tipNames[none] }
                binding.tvFooterMiddle.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterMiddle)) { tipNames[none] }
                binding.tvFooterRight.text =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterRight)) { tipNames[none] }
            }
        }
        binding.btnPaddingSetting.setOnClickListener {
            callBack?.showPaddingConfig()
        }
        upTvTipColor()
        upTvTipDividerColor()
    }

    private fun upTvTipColor() {
        val tipColor = if (ReadTipConfig.tipColor == 0) {
            ReadBookConfig.textColor
        } else {
            ReadTipConfig.tipColor
        }
        binding.btnTipColor.color = tipColor
    }

    private fun upTvTipDividerColor() {
        val tipDividerColor = when (ReadTipConfig.tipDividerColor) {
            -1 -> getCompatColor(R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> ReadTipConfig.tipDividerColor
        }
        binding.btnDividerColor.color = tipDividerColor
    }

    private fun upBtnHeaderMode() {
        val headerModes = ReadTipConfig.getHeaderModes(requireContext())
        binding.btnHeaderMode.text = headerModes[ReadTipConfig.headerMode] ?: getString(R.string.header)
    }

    private fun upBtnFooterMode() {
        val footerModes = ReadTipConfig.getFooterModes(requireContext())
        binding.btnFooterMode.text = footerModes[ReadTipConfig.footerMode] ?: getString(R.string.footer)
    }

    private fun initEvent() = binding.run {

        val headerModes = ReadTipConfig.getHeaderModes(requireContext())
        binding.btnHeaderMode.text = headerModes[ReadTipConfig.headerMode] ?: getString(R.string.header)
        binding.btnHeaderMode.setOnClickListener {
            val items = headerModes.values.toList()
            context?.selector(items = items) { _, index ->
                val selectedKey = headerModes.keys.toList()[index]
                ReadTipConfig.headerMode = selectedKey
                binding.btnHeaderMode.text = headerModes[selectedKey]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }

        val footerModes = ReadTipConfig.getFooterModes(requireContext())
        binding.btnFooterMode.text = footerModes[ReadTipConfig.footerMode] ?: getString(R.string.footer)
        binding.btnFooterMode.setOnClickListener {
            val items = footerModes.values.toList()
            context?.selector(items = items) { _, index ->
                val selectedKey = footerModes.keys.toList()[index]
                ReadTipConfig.footerMode = selectedKey
                binding.btnFooterMode.text = footerModes[selectedKey]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }

        llHeaderLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderLeft = tipValue
                tvHeaderLeft.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderMiddle = tipValue
                tvHeaderMiddle.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderRight = tipValue
                tvHeaderRight.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterLeft = tipValue
                tvFooterLeft.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterMiddle = tipValue
                tvFooterMiddle.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterRight = tipValue
                tvFooterRight.text = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        btnTipColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipColorNames) { _, i ->
                when (i) {
                    0 -> {
                        ReadTipConfig.tipColor = 0
                        upTvTipColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    1 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_COLOR)
                        .show(requireActivity())
                }
            }
        }
        btnDividerColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipDividerColorNames) { _, i ->
                when (i) {
                    0, 1 -> {
                        ReadTipConfig.tipDividerColor = i - 1
                        upTvTipDividerColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    2 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_DIVIDER_COLOR)
                        .show(requireActivity())
                }
            }
        }
    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) {
                tipHeaderLeft = none
                binding.tvHeaderLeft.text = tipNames[none]
            }
            if (tipHeaderMiddle == repeat) {
                tipHeaderMiddle = none
                binding.tvHeaderMiddle.text = tipNames[none]
            }
            if (tipHeaderRight == repeat) {
                tipHeaderRight = none
                binding.tvHeaderRight.text = tipNames[none]
            }
            if (tipFooterLeft == repeat) {
                tipFooterLeft = none
                binding.tvFooterLeft.text = tipNames[none]
            }
            if (tipFooterMiddle == repeat) {
                tipFooterMiddle = none
                binding.tvFooterMiddle.text = tipNames[none]
            }
            if (tipFooterRight == repeat) {
                tipFooterRight = none
                binding.tvFooterRight.text = tipNames[none]
            }
        }
    }

}
