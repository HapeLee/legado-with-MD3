package io.legado.app.ui.book.read

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogTranslationActionsBinding
import io.legado.app.model.translation.TranslationDisplayState
import io.legado.app.utils.viewbindingdelegate.viewBinding

class TranslationActionDialog : BaseBottomSheetDialogFragment(R.layout.dialog_translation_actions) {

    private val binding by viewBinding(DialogTranslationActionsBinding::bind)
    private val callBack: CallBack? get() = activity as? CallBack

    var displayState: TranslationDisplayState = TranslationDisplayState.Original
    var currentChunk: Int = 0
    var totalChunks: Int = 0

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        updateUI()

        btnReturnOriginal.setOnClickListener {
            callBack?.onTranslationSwitchToOriginal()
            dismiss()
        }

        btnRetranslate.setOnClickListener {
            callBack?.onTranslationRetranslate()
            dismiss()
        }
    }

    private fun updateUI() {
        binding.tvProgress.visibility = if (displayState == TranslationDisplayState.Translating) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.tvProgress.text = getString(R.string.translation_progress, currentChunk, totalChunks)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }

    interface CallBack {
        fun onTranslationSwitchToOriginal()
        fun onTranslationRetranslate()
    }
}