package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import androidx.core.content.withStyledAttributes

//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor


class NameListPreference(context: Context, attrs: AttributeSet) : ListPreference(context, attrs) {

    private var isBottomBackground: Boolean = false

    init {
        layoutResource = R.layout.view_preference
        context.withStyledAttributes(attrs, R.styleable.Preference) {
            isBottomBackground = getBoolean(R.styleable.Preference_isBottomBackground, false)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val v = Preference.bindView<TextView>(
            context, holder, icon, title, entry,
            R.id.text_view, isBottomBackground = isBottomBackground
        )
        if (v is TextView) {
            v.text = entry
//            if (isBottomBackground) {
//                val bgColor = context.bottomBackground
//               val pTextColor = context.getPrimaryTextColor(ColorUtils.isColorLight(bgColor))
//                //v.setTextColor(pTextColor)
//            }
        }
        super.onBindViewHolder(holder)
    }

}