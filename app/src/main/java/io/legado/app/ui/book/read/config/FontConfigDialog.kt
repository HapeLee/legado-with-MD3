package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.help.config.ReadBookConfig
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogFontConfigBinding
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig.dottedLine
import io.legado.app.help.config.ReadBookConfig.underline
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.cnCompare
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.invisible
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

/**
 * 字体选择对话框
 */
class FontConfigDialog : BaseBottomSheetDialogFragment(R.layout.dialog_font_config) {

    companion object {
        const val S_COLOR = 123
        const val TEXT_COLOR = 121
    }
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
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
            }
        }
        initView()
        upView()
        initViewEvent()
    }

    private fun initView() = binding.run {
        binding.btnTextColor.color = ReadBookConfig.durConfig.curTextColor()
        binding.swUnderline.isChecked = underline
        binding.swDottedline.isChecked = dottedLine
        binding.swDottedline.isEnabled = underline
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
            dismissAllowingStateLoss()
        }

        binding.btnSelectFonts.setOnClickListener {
            callBack2?.showFontSelect()
            dismissAllowingStateLoss()
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
        binding.btnDefaultFonts.setOnClickListener {
            val requireContext = requireContext()
            alert(titleResource = R.string.system_typeface) {
                items(
                    requireContext.resources.getStringArray(R.array.system_typefaces).toList()
                ) { _, i ->
                    AppConfig.systemTypefaces = i
                    onDefaultFontChange()
                    dismissAllowingStateLoss()
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