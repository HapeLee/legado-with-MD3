package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.legado.app.utils.themeColor

@Suppress("unused", "MemberVisibilityCanBePrivate")
class LabelsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ChipGroup(context, attrs) {

    private val unUsedChips = arrayListOf<Chip>()
    private val usedChips = arrayListOf<Chip>()

    fun clear() {
        unUsedChips.addAll(usedChips)
        usedChips.clear()
        removeAllViews()
    }

    fun setLabels(labels: List<String>, style: (Chip) -> Unit) {
        clear()
        labels.forEach {
            addLabel(it, style)
        }
    }

    fun addLabel(label: String, style: (Chip) -> Unit) {
        val chip = if (unUsedChips.isEmpty()) {
            Chip(context).apply {
                isClickable = false
                isCheckable = false
                chipStartPadding = 8f
                chipEndPadding = 8f
                chipStrokeWidth = 0f
                chipBackgroundColor = ColorStateList.valueOf(
                    context.themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest)
                )
                usedChips.add(this)
            }
        } else {
            unUsedChips.removeAt(unUsedChips.lastIndex).also {
                usedChips.add(it)
            }
        }
        style(chip)
        chip.text = label
        addView(chip)
    }

    fun applyColorScheme(backgroundColor: Int, textColor: Int) {
        usedChips.forEach { chip ->
            chip.chipBackgroundColor = ColorStateList.valueOf(backgroundColor)
            chip.setTextColor(textColor)
        }
        unUsedChips.forEach { chip ->
            chip.chipBackgroundColor = ColorStateList.valueOf(backgroundColor)
            chip.setTextColor(textColor)
        }
    }

}
