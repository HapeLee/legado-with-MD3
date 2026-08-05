package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import io.legado.app.R

//import io.legado.app.lib.theme.accentColor

class SwitchPreference(context: Context, attrs: AttributeSet) :
    SwitchPreferenceCompat(context, attrs) {

    private var isBottomBackground: Boolean = false
    private var onLongClick: ((preference: SwitchPreference) -> Boolean)? = null

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.preference_widget_material_switch
        context.withStyledAttributes(attrs, R.styleable.Preference) {
            isBottomBackground = getBoolean(R.styleable.Preference_isBottomBackground, false)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val switchView = Preference.bindView<SwitchCompat>(
            context, holder, icon, title, summary,
            widgetLayoutResource,
            androidx.preference.R.id.switchWidget,
            isBottomBackground = isBottomBackground
        )

        super.onBindViewHolder(holder)
        val stateDescription = context.getString(
            if (isChecked) R.string.a11y_on else R.string.a11y_off
        )
        ViewCompat.setScreenReaderFocusable(holder.itemView, true)
        ViewCompat.setStateDescription(holder.itemView, stateDescription)
        switchView?.let {
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        onLongClick?.let { listener ->
            holder.itemView.setOnLongClickListener {
                listener.invoke(this)
            }
        }
    }

    fun onLongClick(listener: (preference: SwitchPreference) -> Boolean) {
        onLongClick = listener
    }

}
