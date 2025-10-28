package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import io.legado.app.R
import androidx.core.content.withStyledAttributes

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
        Preference.bindView<SwitchCompat>(
            context, holder, icon, title, summary,
            widgetLayoutResource,
            androidx.preference.R.id.switchWidget,
            isBottomBackground = isBottomBackground
        )

        super.onBindViewHolder(holder)
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
