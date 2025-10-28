package io.legado.app.lib.prefs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig


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
                    Handler(Looper.getMainLooper()).postDelayed({
                        ThemeConfig.applyDayNight(context)
                    }, 300)
                }
            }
        }
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        currentValue = getPersistedString(defaultValue as? String ?: "0")
    }

}