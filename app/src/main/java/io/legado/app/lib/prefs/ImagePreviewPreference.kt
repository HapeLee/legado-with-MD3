package io.legado.app.lib.prefs

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import java.io.File

class ImagePreviewPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var preview: ImageView? = null

    init {
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_icon
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val v = bindView<ImageView>(
            context, holder, icon, title, summary,
            widgetLayoutResource,
            R.id.preview,
            50,
            50
        )

        preview = v
        updatePreview()
    }

    fun updatePreview() {
        val path = sharedPreferences?.getString(key, null)
        val view = preview ?: return

        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                view.setImageBitmap(bitmap)
                view.visibility = View.VISIBLE
                return
            }
        }

        view.setImageDrawable(null)
        view.visibility = View.GONE
    }
}
