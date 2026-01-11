package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogFontConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadBookConfig.underline
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 字体选择对话框
 */
class FontConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_font_config) {

    companion object {
        const val S_COLOR = 123
        const val TEXT_COLOR = 121
        const val TEXT_ACCENT_COLOR = 122
    }
    private val binding by viewBinding(DialogFontConfigBinding::bind)

    private val callBack2 get() = activity as? ReadBookActivity
    private val weightIconMap = mapOf(
        0 to R.drawable.ic_text_weight_0,
        1 to R.drawable.ic_text_weight_1,
        2 to R.drawable.ic_text_weight_2,
    )

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) { list ->
            if (list.contains(2)) {
                binding.btnTextColor.color = ReadBookConfig.durConfig.curTextColor()
                binding.btnShadowColor.color = ReadBookConfig.durConfig.curTextShadowColor()
                binding.btnTextAccentColor.color = ReadBookConfig.durConfig.curTextAccentColor()
            }
        }
        initView()
        upView()
        initViewEvent()
    }

    private fun initView() = binding.run {
        binding.btnTextColor.color = ReadBookConfig.durConfig.curTextColor()
        binding.btnShadowColor.color = ReadBookConfig.durConfig.curTextShadowColor()
        binding.btnTextAccentColor.color = ReadBookConfig.durConfig.curTextAccentColor()
        binding.swUnderline.isChecked = underline
        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        binding.dsbParagraphSpacing.valueFormat = { value ->
            (value / 10f).toString()
        }
        binding.btnIndentLayout.apply {
            valueFormat = { value ->
                value.toString()
            }
            onChanged = { value ->
                val indentCount = value.coerceIn(0, 4)
                ReadBookConfig.paragraphIndent = "　".repeat(indentCount)
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            }
            progress = ReadBookConfig.paragraphIndent.length
        }

        val weightOptions = context?.resources?.getStringArray(R.array.text_font_weight)
        val weightValues = listOf(0, 1, 2)
        val initialIndex = weightValues.indexOf(ReadBookConfig.textBold)
        val initialIconRes = weightIconMap[initialIndex] ?: R.drawable.ic_custom_text
        binding.textFontWeightConverter.setIconResource(initialIconRes)
        binding.textFontWeightConverter.setOnClickListener {
            context?.alert(titleResource = R.string.text_font_weight_converter) {
                weightOptions?.let { options ->
                    items(options.toList()) { _, i ->
                        ReadBookConfig.textBold = weightValues[i]
                        binding.sliderFontWeight.progress =
                            ReadBookConfig.textBold.coerceAtLeast(100)
                        val iconRes = weightIconMap[i] ?: R.drawable.ic_custom_text
                        binding.textFontWeightConverter.setIconResource(iconRes)
                        postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
                    }
                }
            }
        }

        binding.sliderFontWeight.apply {
            min = 100
            max = 900
            progress = ReadBookConfig.textBold.coerceAtLeast(100)
            onChanged = {
                binding.textFontWeightConverter.setIconResource(R.drawable.ic_custom_text)
                ReadBookConfig.textBold = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            }
        }

        binding.btnShadowSet.setOnClickListener {
            callBack2?.showShadowSet()
        }

        binding.btnSelectFonts.setOnClickListener {
            callBack2?.showFontSelect()
        }
        binding.btnTextItalic.isChecked = ReadBookConfig.textItalic
        binding.btnTextShadow.isChecked = ReadBookConfig.textShadow
        binding.btnShadowColor.color = ReadBookConfig.textShadowColor

    }

    private fun initViewEvent() = binding.run {
        dsbTextLetterSpacing.onChanged = {
            ReadBookConfig.letterSpacing = (it - 50) / 100f
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbLineSize.onChanged = {
            ReadBookConfig.lineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.btnTextColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curTextColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_COLOR)
                .show(requireActivity())
        }
        binding.btnTextAccentColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.durConfig.curTextAccentColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(TEXT_ACCENT_COLOR)
                .show(requireActivity())
        }
        binding.swUnderline.addOnCheckedChangeListener { _, isChecked ->
            callBack2?.showUnderlineConfig()
        }

        binding.btnDefaultFonts.setOnClickListener {
            val requireContext = requireContext()
            alert(titleResource = R.string.system_typeface) {
                items(
                    requireContext.resources.getStringArray(R.array.system_typefaces).toList()
                ) { _, i ->
                    AppConfig.systemTypefaces = i
                    onDefaultFontChange()
                }
            }
        }
        binding.dsbParagraphSpacing.onChanged = { value ->
            ReadBookConfig.paragraphSpacing = value
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }

        binding.btnTextItalic.addOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.textItalic = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.btnTextShadow.addOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.textShadow = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.btnShadowColor.setOnClickListener {
            ColorPickerDialog.newBuilder()
                .setColor(ReadBookConfig.config.curTextShadowColor())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(S_COLOR)
                .show(requireActivity())
            //postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
    }

    private fun upView() = binding.run {
        ReadBookConfig.let {
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
        }
    }

    private fun onDefaultFontChange() {
        callBack?.selectFont("")
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}