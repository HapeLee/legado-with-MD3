package io.legado.app.ui.welcome

import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.databinding.FragmentBookFolderBinding
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.viewbindingdelegate.viewBinding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BookFolderFragment : BaseFragment(R.layout.fragment_book_folder) {

    private val binding by viewBinding(FragmentBookFolderBinding::bind)
    private val otherSettingsGateway by inject<OtherSettingsGateway>()

    private val selectBookFolder = registerForActivityResult(HandleFileContract()) { result ->
        result.uri?.let { treeUri ->
            lifecycleScope.launch {
                otherSettingsGateway.update { it.copy(defaultBookTreeUri = treeUri.toString()) }
                updatePathText()
            }
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
            otherSettingsGateway.currentSettings.defaultBookTreeUri
                ?: getString(R.string.welcome_book_folder_not_selected)
    }
}
