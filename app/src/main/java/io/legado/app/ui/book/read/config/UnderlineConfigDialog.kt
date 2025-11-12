package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import io.legado.app.help.config.ReadBookConfig
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogUnderlineConfigBinding
import io.legado.app.help.config.ReadBookConfig.dottedLine
import io.legado.app.help.config.ReadBookConfig.underline
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 字体选择对话框
 */
class UnderlineConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_underline_config) {

    companion object {
        const val U_COLOR = 810
    }

    private val binding by viewBinding(DialogUnderlineConfigBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) { list ->
            if (list.contains(2)) {
                binding.btnUnderlineColor.color = ReadBookConfig.durConfig.curUnderlineColor()
            }
        }
        initView()
    }

    private fun initView() = binding.run {
        binding.btnUnderlineColor.color = ReadBookConfig.durConfig.curTextColor()
        binding.swUnderline.isChecked = underline
        binding.swDottedline.isChecked = dottedLine
        binding.swDottedline.isEnabled = underline

        binding.swUnderline.addOnCheckedChangeListener { _, isChecked ->
            underline = isChecked
            binding.swDottedline.isEnabled = isChecked
            if (!isChecked) {
                dottedLine = false
                binding.swDottedline.isChecked = false
            }
            postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
        }

        binding.swDottedline.addOnCheckedChangeListener { _, isChecked ->
            dottedLine = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(6, 9, 11))
        }

        binding.btnDottedLineBlack.apply {
            progress = ReadBookConfig.durConfig.dottedBase.toInt()
            onChanged = {
                ReadBookConfig.durConfig.dottedBase = it.toFloat()
                postEvent(EventBus.UP_CONFIG, arrayListOf(6, 8, 10))
            }
        }

        binding.btnDottedLineWhile.apply {
            progress = ReadBookConfig.durConfig.dottedRatio.toInt()
            onChanged = {
                ReadBookConfig.durConfig.dottedRatio = it.toFloat()
                postEvent(EventBus.UP_CONFIG, arrayListOf(6, 8, 10))
            }
        }

        binding.btnUnderlineColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curUnderlineColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(U_COLOR)
                .show(requireActivity())
        }

        binding.btnUnderlineHeight.apply {
            progress = ReadBookConfig.underlineHeight
            onChanged = {
                ReadBookConfig.underlineHeight = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            }
        }
    }

}