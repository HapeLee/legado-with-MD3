package io.legado.app.lib.prefs

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.preference.PreferenceViewHolder
import io.legado.app.R

class TipPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var tipText: String? = null

    init {
        layoutResource = R.layout.view_preference_tip

        // 从 attrs.xml 中读取 tipText
        context.withStyledAttributes(attrs, R.styleable.TipPreference) {
            tipText = getString(R.styleable.TipPreference_tipText)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val tipTextView = holder.findViewById(R.id.tip_text) as? TextView
        tipTextView?.text = tipText ?: ""
    }

    fun setTipText(text: String) {
        tipText = text
        notifyChanged()
    }
}