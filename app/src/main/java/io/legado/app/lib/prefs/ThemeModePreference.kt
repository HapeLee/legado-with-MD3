package io.legado.app.lib.prefs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.core.view.ViewCompat
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import io.legado.app.R
import io.legado.app.help.config.ThemeConfigStore


class ThemeModePreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var currentValue: String = "0"

    init {
        layoutResource = R.layout.view_pref
        widgetLayoutResource = R.layout.view_theme_mode
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val toggleGroup =
            holder.itemView.findViewById<MaterialButtonToggleGroup>(R.id.theme_toggle_group)
                ?: return

        // 根据 currentValue 设置选中
        when (currentValue) {
            "0" -> toggleGroup.check(R.id.btn_system)
            "1" -> toggleGroup.check(R.id.btn_light)
            "2" -> toggleGroup.check(R.id.btn_dark)
        }

        setupToggleGroup(toggleGroup)
        updateButtonStateDescriptions(toggleGroup)
    }


    private fun setupToggleGroup(toggleGroup: MaterialButtonToggleGroup) {

        when (currentValue) {
            "0" -> toggleGroup.check(R.id.btn_system)
            "1" -> toggleGroup.check(R.id.btn_light)
            "2" -> toggleGroup.check(R.id.btn_dark)
        }

        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                val newValue = when (checkedId) {
                    R.id.btn_system -> "0"
                    R.id.btn_light -> "1"
                    R.id.btn_dark -> "2"
                    else -> null
                }

                if (newValue != null && callChangeListener(newValue)) {
                    currentValue = newValue
                    persistString(newValue)
                    callChangeListener(newValue)
                    updateButtonStateDescriptions(group)
                    Handler(Looper.getMainLooper()).postDelayed({
                        ThemeConfigStore.applyDayNight(context)
                    }, 300)
                }
            }
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        currentValue = getPersistedString(defaultValue as? String ?: "0")
    }

    private fun updateButtonStateDescriptions(group: MaterialButtonToggleGroup) {
        for (index in 0 until group.childCount) {
            val child = group.getChildAt(index)
            val selected = child.id == group.checkedButtonId
            ViewCompat.setStateDescription(
                child,
                context.getString(if (selected) R.string.a11y_selected else R.string.a11y_not_selected)
            )
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
    }

}
