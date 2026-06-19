package io.legado.app.ui.main.ai

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.entities.AiGeneratedImage
import io.legado.app.databinding.DialogAiImagePreviewBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.ai.AiImageGalleryManager
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiImagePreviewDialog() : BaseDialogFragment(R.layout.dialog_ai_image_preview) {

    constructor(imageId: String) : this() {
        arguments = Bundle().apply {
            putString(EXTRA_IMAGE_ID, imageId)
        }
    }

    private val binding by viewBinding(DialogAiImagePreviewBinding::bind)
    private var imageId: String = ""
    private var image: AiGeneratedImage? = null

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        imageId = arguments?.getString(EXTRA_IMAGE_ID).orEmpty()
        val actionBackground = UiCorner.actionSelector(
            ContextCompat.getColor(requireContext(), R.color.background_card),
            ContextCompat.getColor(requireContext(), R.color.background_menu),
            UiCorner.actionRadius(requireContext())
        )
        listOf(binding.btnFavorite, binding.btnGroup, binding.btnRename, binding.btnDelete).forEach {
            it.background = actionBackground
        }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }
        binding.btnGroup.setOnClickListener { selectGroup() }
        binding.btnRename.setOnClickListener { renameImage() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        reload()
    }

    private fun reload() {
        image = AiImageGalleryManager.getImage(imageId)
        val target = image
        if (target == null) {
            dismissAllowingStateLoss()
            return
        }
        ImageLoader.load(requireContext(), target.localPath)
            .error(R.drawable.image_loading_error)
            .into(binding.photoView)
        binding.tvTitle.text = target.name
        binding.btnFavorite.text = getString(
            if (target.favorite) R.string.ai_image_cancel_favorite else R.string.favorite
        )
        binding.btnGroup.isVisible = target.favorite
        binding.btnRename.isEnabled = target.favorite
        binding.btnRename.alpha = if (target.favorite) 1f else 0.45f
        binding.btnGroup.setTextColor(secondaryTextColor)
        binding.btnRename.setTextColor(secondaryTextColor)
    }

    private fun toggleFavorite() {
        val target = image ?: return
        if (target.favorite) {
            AiImageGalleryManager.setFavorite(target.id, false, null)
            toastOnUi(R.string.out_favorites)
            reload()
        } else {
            showGroupSelector(getString(R.string.ai_image_favorite_to)) { groupId ->
                AiImageGalleryManager.setFavorite(target.id, true, groupId)
                toastOnUi(R.string.in_favorites)
                reload()
            }
        }
    }

    private fun selectGroup() {
        val target = image ?: return
        if (!target.favorite) return
        showGroupSelector(getString(R.string.ai_image_group)) { groupId ->
            AiImageGalleryManager.setFavorite(target.id, true, groupId)
            reload()
        }
    }

    private fun showGroupSelector(title: String, onSelected: (String) -> Unit) {
        val groups = AiImageGalleryManager.listGroups()
        val labels = groups.map { it.name } + getString(R.string.ai_image_new_group)
        requireContext().selector(title, labels) { _, index ->
            if (index == groups.size) {
                createGroup(onSelected)
            } else {
                onSelected(groups[index].id)
            }
        }
    }

    private fun createGroup(onCreated: (String) -> Unit) {
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.ai_image_new_group)
        }
        alert(titleResource = R.string.ai_image_new_group) {
            customView { dialogBinding.root }
            okButton {
                val name = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) {
                    onCreated(AiImageGalleryManager.createGroup(name).id)
                }
            }
            cancelButton()
        }
    }

    private fun renameImage() {
        val target = image ?: return
        if (!target.favorite) return
        val dialogBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.setText(target.name)
            editView.setSelection(editView.text?.length ?: 0)
        }
        alert(titleResource = R.string.ai_image_rename) {
            customView { dialogBinding.root }
            okButton {
                val name = dialogBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotBlank()) {
                    AiImageGalleryManager.renameImage(target.id, name)
                    reload()
                }
            }
            cancelButton()
        }
    }

    private fun confirmDelete() {
        val target = image ?: return
        alert(titleResource = R.string.delete, messageResource = R.string.ai_image_delete_confirm) {
            okButton {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AiImageGalleryManager.deleteImage(target.id)
                    }
                    dismissAllowingStateLoss()
                }
            }
            cancelButton()
        }
    }

    companion object {
        private const val EXTRA_IMAGE_ID = "imageId"
    }
}
