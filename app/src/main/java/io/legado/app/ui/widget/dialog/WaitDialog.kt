package io.legado.app.ui.widget.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.legado.app.R
import io.legado.app.databinding.DialogWaitBinding

class WaitDialog(context: Context) {

    private val binding: DialogWaitBinding =
        DialogWaitBinding.inflate(LayoutInflater.from(context))

    private val dialog: Dialog = MaterialAlertDialogBuilder(context)
        .setView(binding.root)
        .setCancelable(false)
        .create()

    init {
        binding.tvMsg.setText(R.string.loading)
    }

    fun setText(text: String): WaitDialog {
        binding.tvMsg.text = text
        return this
    }

    fun setText(resId: Int): WaitDialog {
        binding.tvMsg.setText(resId)
        return this
    }

    fun show(): WaitDialog {
        dialog.show()
        return this
    }

    fun dismiss() {
        dialog.dismiss()
    }

    fun isShowing(): Boolean {
        return dialog.isShowing
    }

    fun setOnCancelListener(listener: () -> Unit): WaitDialog {
        dialog.setOnCancelListener { listener() }
        return this
    }

    fun setCancelable(cancelable: Boolean): WaitDialog {
        dialog.setCancelable(cancelable)
        return this
    }
}
