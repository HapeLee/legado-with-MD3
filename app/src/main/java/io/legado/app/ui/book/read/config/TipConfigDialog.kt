package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import com.google.android.material.chip.Chip
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding


class TipConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
    }

    private fun initView() {

        when (ReadBookConfig.titleMode) {
            0 -> binding.rgTitleMode.check(R.id.rb_title_mode1)
            1 -> binding.rgTitleMode.check(R.id.rb_title_mode2)
            2 -> binding.rgTitleMode.check(R.id.rb_title_mode3)
            else -> {  }
        }
        val weightOptions = context?.resources?.getStringArray(R.array.text_font_weight)
        val weightValues = listOf(2, 1, 0)
        val initialIndex = weightValues.indexOf(ReadBookConfig.titleBold)
        binding.textFontWeightConverter.text = weightOptions?.getOrNull(initialIndex) ?: "自定义"
        binding.textFontWeightConverter.setOnClickListener {
            context?.alert(titleResource = R.string.text_font_weight_converter) {
                weightOptions?.let { options ->
                    items(options.toList()) { _, i ->
                        ReadBookConfig.titleBold = weightValues[i]
                        binding.sliderFontWeight.value =
                            ReadBookConfig.titleBold.toFloat().coerceAtLeast(100f)
                        binding.textFontWeightConverter.text = options.getOrNull(i) ?: ""
                        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
                    }
                }
            }
        }

        binding.sliderFontWeight.apply {
            valueFrom = 100f
            valueTo = 900f
            stepSize = 1f
            value = ReadBookConfig.titleBold.toFloat().coerceAtLeast(100f)
            addOnChangeListener { _, newValue, _ ->
                binding.textFontWeightConverter.text = "自定义"
                ReadBookConfig.titleBold = newValue.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            }
        }

        binding.dsbTitleSize.progress = ReadBookConfig.titleSize
        binding.dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        binding.dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing

        val headerModes = ReadTipConfig.getHeaderModes(requireContext())
        binding.chipHeaderMode.removeAllViews()

        headerModes.forEach { (key, value) ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = value
                isCheckable = true
                isClickable = true
                tag = key
            }

            binding.chipHeaderMode.addView(chip)

            if (key == ReadTipConfig.headerMode) {
                chip.isChecked = true
            }
        }

        val footerModes = ReadTipConfig.getFooterModes(requireContext())
        binding.chipFooterMode.removeAllViews()

        footerModes.forEach { (key, value) ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = value
                isCheckable = true
                isClickable = true
                tag = key
            }

            binding.chipFooterMode.addView(chip)

            if (key == ReadTipConfig.footerMode) {
                chip.isChecked = true
            }
        }

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
        upTvTipColor()
        upTvTipDividerColor()
    }

    private fun upTvTipColor() {
        val tipColorNames = ReadTipConfig.tipColorNames
        val tipColor = ReadTipConfig.tipColor
        binding.tvTipColor.text = if (tipColor == 0) {
            tipColorNames.first()
        } else {
            "#${tipColor.hexString}"
        }
    }

    private fun upTvTipDividerColor() {
        val tipDividerColorNames = ReadTipConfig.tipDividerColorNames
        val tipDividerColor = ReadTipConfig.tipDividerColor
        binding.tvTipDividerColor.text = when (tipDividerColor) {
            -1, 0 -> tipDividerColorNames[tipDividerColor + 1]
            else -> "#${tipDividerColor.hexString}"
        }
    }

    private fun initEvent() = binding.run {
        binding.rgTitleMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                ReadBookConfig.titleMode = group.indexOfChild(group.findViewById(checkedIds.first()))
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }
        }
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        chipHeaderMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                val mode = selectedChip.tag as Int
                ReadTipConfig.headerMode = mode
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        chipFooterMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                val mode = selectedChip.tag as Int
                ReadTipConfig.footerMode = mode
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
        llTipColor.setOnClickListener {
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
        llTipDividerColor.setOnClickListener {
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