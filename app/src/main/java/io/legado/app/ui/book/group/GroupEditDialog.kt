package io.legado.app.ui.book.group

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.DialogBookGroupEditBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.gone
import io.legado.app.utils.inputStream
import io.legado.app.utils.launch
import io.legado.app.utils.readUri
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.init.appCtx
import splitties.views.onClick
import java.io.FileOutputStream

class GroupEditDialog() : BaseBottomSheetDialogFragment(R.layout.dialog_book_group_edit) {

    constructor(bookGroup: BookGroup? = null) : this() {
        arguments = Bundle().apply {
            putParcelable("group", bookGroup?.copy())
        }
    }

    private var selectedSortIndex = -1
    private lateinit var sortOptions: List<String>

    private val binding by viewBinding(DialogBookGroupEditBinding::bind)
    private val viewModel by viewModels<GroupViewModel>()
    private var bookGroup: BookGroup? = null
    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri ?: return@registerForActivityResult
        readUri(it.uri) { fileDoc, inputStream ->
            try {
                var file = requireContext().externalFiles
                val suffix = fileDoc.name.substringAfterLast(".")
                val fileName = it.uri.inputStream(requireContext()).getOrThrow().use { tmp ->
                    MD5Utils.md5Encode(tmp) + ".$suffix"
                }
                file = FileUtils.createFileIfNotExist(file, "covers", fileName)
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                binding.ivCover.load(file.absolutePath)
            } catch (e: Exception) {
                appCtx.toastOnUi(e.localizedMessage)
            }
        }
    }

    override fun onStart() {
        super.onStart()

    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        sortOptions = resources.getStringArray(R.array.book_sort).toList()

        @Suppress("DEPRECATION")
        bookGroup = arguments?.getParcelable("group")

        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            sortOptions
        )
        binding.actvSortMenu.setAdapter(sortAdapter)

        bookGroup?.let { group ->
            binding.btnDelete.isEnabled = (group.groupId > 0 || group.groupId == Long.MIN_VALUE)
            binding.tieGroupName.setText(group.groupName)
            if (group.cover != null) {
                binding.ivCover.load(group.cover)
            }
            binding.cbEnableRefresh.isChecked = group.enableRefresh

            selectedSortIndex = group.bookSort
            val displayIndex = selectedSortIndex + 1
            if (displayIndex in sortOptions.indices) {
                binding.actvSortMenu.setText(sortOptions[displayIndex], false)
            } else {
                binding.actvSortMenu.setText(sortOptions[0], false)
                selectedSortIndex = -1
            }

        } ?: run {
            binding.btnDelete.gone()
            binding.ivCover.load()
            selectedSortIndex = -1
            binding.actvSortMenu.setText(sortOptions[0], false)
        }

        binding.actvSortMenu.setOnItemClickListener { _, _, position, _ ->
            selectedSortIndex = position - 1
        }

        binding.run {
            ivCover.onClick {
                selectImage.launch()
            }
            btnReset.onClick {
                bookGroup?.let {
                    viewModel.clearCover(it) {
                        toastOnUi("封面已重置")
                        dismiss()
                    }
                }
            }

            btnCancel.onClick {
                dismiss()
            }

            btnOk.onClick {
                val groupName = tieGroupName.text?.toString()
                if (groupName.isNullOrEmpty()) {
                    toastOnUi("分组名称不能为空")
                } else {
                    val bookSort = selectedSortIndex
                    val coverPath = binding.ivCover.bitmapPath
                    val enableRefresh = binding.cbEnableRefresh.isChecked
                    bookGroup?.let {
                        it.groupName = groupName
                        it.cover = coverPath
                        it.bookSort = bookSort
                        it.enableRefresh = enableRefresh
                        viewModel.upGroup(it) {
                            dismiss()
                        }
                    } ?: let {
                        viewModel.addGroup(
                            groupName,
                            bookSort,
                            enableRefresh,
                            coverPath
                        ) {
                            dismiss()
                        }
                    }
                }
            }

            btnDelete.onClick {
                deleteGroup {
                    bookGroup?.let {
                        viewModel.delGroup(it) {
                            dismiss()
                        }
                    }
                }
            }
        }
    }


    private fun deleteGroup(ok: () -> Unit) {
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                ok.invoke()
            }
            noButton()
        }
    }

}