package io.legado.app.ui.welcome

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentBookFolderBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.viewbindingdelegate.viewBinding

class BookFolderFragment : BaseFragment(R.layout.fragment_book_folder) {

    private val binding by viewBinding(FragmentBookFolderBinding::bind)

    private val selectBookFolder = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
            updatePathText()
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnSelectFolder.setOnClickListener {
            selectBookFolder.launch {
                title = getString(R.string.select_book_folder)
                mode = HandleFileContract.DIR_SYS
            }
        }
        updatePathText()
    }

    private fun updatePathText() {
        binding.tvFolderPath.text =
            AppConfig.defaultBookTreeUri ?: getString(R.string.welcome_book_folder_not_selected)
    }
}
