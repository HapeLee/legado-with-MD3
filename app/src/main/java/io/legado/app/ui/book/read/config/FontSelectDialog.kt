package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.Toolbar
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
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.invisible
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
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
class FontSelectDialog : BaseBottomSheetDialogFragment(R.layout.dialog_font_select),
    Toolbar.OnMenuItemClickListener {

    companion object {
        const val S_COLOR = 123
    }
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val binding by viewBinding(DialogFontSelectBinding::bind)
    private val adapter by lazy {
        val curFontPath = callBack?.curFontPath ?: ""
        FontAdapter(requireContext(), curFontPath)
    }
    private val selectFontDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                putPrefString(PreferKey.fontFolder, uri.toString())
                val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                if (doc != null) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                        loadFontFilesByPermission(path)
                    }
                }
            } else {
                uri.path?.let { path ->
                    putPrefString(PreferKey.fontFolder, path)
                    loadFontFilesByPermission(path)
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        //binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.select_font)
        binding.toolBar.inflateMenu(R.menu.font_select)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter


        initView()
        upView()
        initViewEvent()
    }

    private fun initView() = binding.run {

        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            openFolder()
        } else {
            if (fontPath.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(requireContext(), fontPath.toUri())
                if (doc?.canRead() == true) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    openFolder()
                }
            } else {
                loadFontFilesByPermission(fontPath)
            }
        }

        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        binding.dsbShadowRadius.valueFormat = { "$it px" }
        binding.dsbShadowDx.valueFormat = { "$it px" }
        binding.dsbShadowDy.valueFormat = { "$it px" }

        binding.textIndentDropdown.apply {
            val items = listOf("0", "1", "2", "3", "4")
            val adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)
            setAdapter(adapter)
            val currentIndex = ReadBookConfig.paragraphIndent.length.coerceIn(0, items.lastIndex)
            setText(items[currentIndex], false)
            setOnItemClickListener { _, _, position, _ ->
                ReadBookConfig.paragraphIndent = "　".repeat(position)
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
            }
        }

        val weightOptions = context?.resources?.getStringArray(R.array.text_font_weight)
        val weightValues = listOf(0, 1, 2)
        val initialIndex = weightValues.indexOf(ReadBookConfig.textBold)

        binding.textFontWeightConverter.text = weightOptions?.getOrNull(initialIndex) ?: "自定义"
        binding.textFontWeightConverter.setOnClickListener {
            context?.alert(titleResource = R.string.text_font_weight_converter) {
                weightOptions?.let { options ->
                    items(options.toList()) { _, i ->
                        ReadBookConfig.textBold = weightValues[i]
                        binding.sliderFontWeight.value =
                            ReadBookConfig.textBold.toFloat().coerceAtLeast(100f)
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
            value = ReadBookConfig.textBold.toFloat().coerceAtLeast(100f)
            addOnChangeListener { _, newValue, _ ->
                binding.textFontWeightConverter.text = "自定义"
                ReadBookConfig.textBold = newValue.toInt()
                postEvent(EventBus.UP_CONFIG, arrayListOf(8, 9, 6))
            }
        }

        binding.btnTextItalic.isChecked = ReadBookConfig.textItalic
        binding.btnTextShadow.isChecked = ReadBookConfig.textShadow
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
        binding.dsbParagraphSpacing.apply {
            valueFrom = 0f
            valueTo = 20f
            stepSize = 1f
            value = ReadBookConfig.paragraphSpacing.toFloat()
            textParagraphSpacing.text = (ReadBookConfig.paragraphSpacing.toFloat() / 10f).toString()
            addOnChangeListener { slider, newValue, fromUser ->
                binding.textParagraphSpacing.text = (newValue / 10f).toString()

                if (fromUser) {
                    ReadBookConfig.paragraphSpacing = newValue.toInt()
                    postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
                }
            }
        }
        binding.btnTextItalic.addOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.textItalic = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.btnTextShadow.addOnCheckedChangeListener { _, isChecked ->
            ReadBookConfig.textShadow = isChecked
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbShadowRadius.onChanged = {
            ReadBookConfig.shadowRadius = it.toFloat()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbShadowDx.onChanged = {
            ReadBookConfig.shadowDx = it.toFloat()
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        binding.dsbShadowDy.onChanged = {
            ReadBookConfig.shadowDy = it.toFloat()
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
            dsbParagraphSpacing.value = it.paragraphSpacing.toFloat()
            binding.dsbShadowRadius.progress = it.shadowRadius.toInt()
            binding.dsbShadowDx.progress = it.shadowDx.toInt()
            binding.dsbShadowDy.progress = it.shadowDy.toInt()
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_default -> {
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
            R.id.menu_other -> {
                openFolder()
            }
        }
        return true
    }

    private fun openFolder() {
        lifecycleScope.launch {
            val defaultPath = "SD${File.separator}Fonts"
            selectFontDir.launch {
                otherActions = arrayListOf(SelectItem(defaultPath, -1))
            }
        }
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return File(path).listFileDocs {
            it.name.matches(fontRegex)
        }
    }

    private fun loadFontFilesByPermission(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                loadFontFiles(
                    FileDoc.fromFile(File(path))
                )
            }
            .request()
    }

    private fun loadFontFiles(fileDoc: FileDoc) {
        execute {
            val fontItems = fileDoc.list {
                it.name.matches(fontRegex)
            } ?: ArrayList()
            mergeFontItems(fontItems, getLocalFonts())
        }.onSuccess {
            adapter.setItems(it)
        }.onError {
            AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            var isInFirst = false
            items1.forEach for1@{ item1 ->
                if (item2.name == item1.name) {
                    isInFirst = true
                    return@for1
                }
            }
            if (!isInFirst) {
                items.add(item2)
            }
        }
        return items.sortedWith { o1, o2 ->
            o1.name.cnCompare(o2.name)
        }
    }

    fun onFontSelect(docItem: FileDoc) {
        execute {
            callBack?.selectFont(docItem.toString())
        }.onSuccess {
            dismissAllowingStateLoss()
        }
    }

    private fun onDefaultFontChange() {
        callBack?.selectFont("")
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    inner class FontAdapter(context: Context, curFilePath: String) :
        RecyclerAdapter<FileDoc, ItemFontBinding>(context) {

        private val curName = runCatching {
            URLDecoder.decode(curFilePath, "utf-8")
        }.getOrNull()?.substringAfterLast(File.separator)

        override fun getViewBinding(parent: ViewGroup): ItemFontBinding {
            return ItemFontBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFontBinding,
            item: FileDoc,
            payloads: MutableList<Any>
        ) {
            binding.run {
                runCatching {
                    val typeface: Typeface? = if (item.isContentScheme) {
                        context.contentResolver
                            .openFileDescriptor(item.uri, "r")?.use {
                                Typeface.Builder(it.fileDescriptor).build()
                            }
                    } else {
                        Typeface.createFromFile(item.uri.path!!)
                    }
                    tvFont.typeface = typeface
                }.onFailure {
                    it.printOnDebug()
                    AppLog.put("读取字体 ${item.name} 出错\n${it.localizedMessage}", it, true)
                }
                tvFont.text = item.name
                root.setOnClickListener { onFontSelect(item) }
                if (item.name == curName) {
                    ivChecked.visible()
                } else {
                    ivChecked.invisible()
                }
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFontBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    onFontSelect(it)
                }
            }
        }
    }

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}