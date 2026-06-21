package io.legado.app.ui.widget

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A simple item selection dialog.
 * Shows a list of items and calls back with the selected item's key.
 */
object SourceSelectDialog {

    fun show(
        context: Context,
        title: String,
        items: List<String>,
        selectedKey: String? = null,
        displayName: (String) -> String = { it },
        searchTexts: (String) -> List<String> = { listOf(it) },
        itemKey: (String) -> String = { it },
        onSelected: (String) -> Unit
    ) {
        val displayItems = items.map(displayName).toTypedArray()
        val checkedItem = items.indexOfFirst { itemKey(it) == selectedKey }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(displayItems, checkedItem.coerceAtLeast(-1)) { dialog, which ->
                dialog.dismiss()
                if (which in items.indices) {
                    onSelected(itemKey(items[which]))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
