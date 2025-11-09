package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.text.InputType
import android.view.View
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding


class TipConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898
        const val B_COLOR = 114
        const val A_COLOR = 514
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            binding.abtnBackgroundColor.color = ReadBookConfig.durConfig.curMenuBg()
            binding.abtnAccentColor.color = ReadBookConfig.durConfig.curMenuAc()
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
        val weightValues = listOf(0, 1, 2)
        binding.textFontWeightConverter.setOnClickListener {
            context?.alert(titleResource = R.string.text_font_weight_converter) {
                weightOptions?.let { options ->
                    items(options.toList()) { _, i ->
                        ReadBookConfig.titleBold = weightValues[i]
                        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
                    }
                }
            }
        }
        binding.abtnBackgroundColor.color = ReadBookConfig.durConfig.curMenuBg()
        binding.abtnAccentColor.color = ReadBookConfig.durConfig.curMenuAc()
        binding.abtnBackgroundColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curMenuBg())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(B_COLOR)
                .show(requireActivity())
        }

        binding.abtnAccentColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curMenuAc())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(A_COLOR)
                .show(requireActivity())
        }

        binding.bottomMode.check(
            when (AppConfig.readBarStyle) {
                0 -> R.id.bottom_mode1
                1 -> R.id.bottom_mode2
                else -> R.id.bottom_mode3
            }
        )
        binding.bottomMode.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            AppConfig.readBarStyle = when (checkedId) {
                R.id.bottom_mode1 -> 0
                R.id.bottom_mode2 -> 1
                R.id.bottom_mode3 -> 2
                else -> 0
            }
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
        }

        binding.btnTitleSegType.setOnClickListener {
            val types = arrayOf("不分段", "按字符数分段", "按标志字符串分段")
            val current = ReadBookConfig.titleSegType

            alert(title = "选择标题分段模式") {
                singleChoiceItems(types, current) { _, which ->
                    ReadBookConfig.titleSegType = which
                }
                positiveButton("确定") {
                    toastOnUi("分段模式已设置为：${types[ReadBookConfig.titleSegType]}")
                    postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                }
                negativeButton("取消")
            }.show()
        }

        binding.btnTitleSegConfig.setOnClickListener {
            when (ReadBookConfig.titleSegType) {
                1 -> { // 按字符数分段
                    alert(title = "设置分段字符数") {
                        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                            editView.inputType = InputType.TYPE_CLASS_NUMBER
                            editView.setText(ReadBookConfig.titleSegDistance.toString())
                            editView.hint = "输入分段字符数"
                        }
                        customView { alertBinding.root }

                        okButton {
                            val value = alertBinding.editView.text?.toString()?.toIntOrNull()
                            if (value != null && value > 0) {
                                ReadBookConfig.titleSegDistance = value
                                toastOnUi("分段字符数设置为 $value")
                                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                            } else {
                                toastOnUi("请输入有效数字")
                            }
                        }
                        cancelButton()
                    }.requestInputMethod()
                }

                2 -> { // 按标志字符串分段
                    alert(title = "设置分段标志") {
                        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                            editView.inputType = InputType.TYPE_CLASS_TEXT
                            editView.setText(ReadBookConfig.titleSegFlag)
                            editLayout.hint = "输入多个标志，用英文逗号分隔，例如：章,回,篇"
                        }
                        customView { alertBinding.root }

                        okButton {
                            val value = alertBinding.editView.text?.toString()?.trim()
                            if (!value.isNullOrEmpty()) {
                                ReadBookConfig.titleSegFlag = value
                                toastOnUi("分段标志设置为 \"$value\"")
                                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
                            } else {
                                toastOnUi("标志不能为空")
                            }
                        }
                        cancelButton()
                    }.requestInputMethod()
                }

                else -> {
                    toastOnUi("当前分段模式无需配置参数")
                }
            }
        }

        binding.dsbTitleSegScaling.progress = ReadBookConfig.titleSegScaling.toInt() * 10
        binding.dsbTitleLineSpacingExtra.progress = ReadBookConfig.titleLineSpacingExtra
        binding.dsbTitleSize.progress = ReadBookConfig.titleSize
        binding.dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        binding.dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing
    }

    private fun initEvent() = binding.run {
        binding.rgTitleMode.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                ReadBookConfig.titleMode = group.indexOfChild(group.findViewById(checkedIds.first()))
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }
        }
        binding.dsbTitleSegScaling.onChanged = {
            ReadBookConfig.titleSegScaling = it / 10f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbTitleLineSpacingExtra.onChanged = {
            ReadBookConfig.titleLineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
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
    }

}