package io.legado.app.lib.prefs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.restart
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

@SuppressLint("ResourceType")
class ThemeCardPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var entries: Array<CharSequence> = context.resources.getTextArray(R.array.themes_item)
    private var entryValues: Array<CharSequence> = context.resources.getTextArray(R.array.themes_value).takeIf { it.isNotEmpty() }
        ?: arrayOf("0")
    private var currentValue = "0"

    init {
        layoutResource = R.layout.preference_theme_card
        widgetLayoutResource = R.layout.item_theme_card
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        currentValue = getPersistedString(defaultValue as? String ?: entryValues.getOrNull(0)?.toString())
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val recyclerView = holder.findViewById(R.id.recyclerView) as RecyclerView

        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.adapter = ThemeAdapter()
    }

    private inner class ThemeAdapter : RecyclerView.Adapter<ThemeViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
            return ThemeViewHolder(view)
        }

        @SuppressLint("NotifyDataSetChanged")
        override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
            val label = entries.getOrNull(position)?.toString() ?: return
            val value = entryValues.getOrNull(position)?.toString() ?: return

            holder.label.text = label
            holder.card.isChecked = (value == currentValue)

            val colors = getThemeColors(value)
            holder.colorTop.setCardBackgroundColor(colors[0])
            holder.colorBook.setCardBackgroundColor(colors[1])
            holder.colorPin.setCardBackgroundColor(colors[2])
            holder.colorBottom.setCardBackgroundColor(colors[3])
            holder.colorBottomLeft.setCardBackgroundColor(colors[4])
            holder.colorBottomRight.setCardBackgroundColor(colors[5])
            holder.background.setCardBackgroundColor(colors[6])
            val isSelected = (value == currentValue)
            holder.background.strokeColor = if (isSelected) colors[4] else colors[7]
            holder.card.checkedIconTint = ColorStateList.valueOf(colors[2])


            holder.card.setOnClickListener {
                if (value != currentValue) {
                    if (value == "13") {
                        val hasLightBg = !appCtx.getPrefString(PreferKey.bgImage).isNullOrEmpty()
                        val hasDarkBg = !appCtx.getPrefString(PreferKey.bgImageN).isNullOrEmpty()
                        if (!hasLightBg || !hasDarkBg) {
                            context.toastOnUi(R.string.transparent_theme_alarm)
                            return@setOnClickListener
                        } else {
                            AppConfig.containerOpacity = 0
                        }
                    }
                    val oldValue = currentValue
                    currentValue = value
                    persistString(value)
                    callChangeListener(value)
                    ThemeConfig.appTheme = value
                    notifyDataSetChanged()
                    val isDynamicSwitch = (oldValue == "12" || value == "12")
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isDynamicSwitch) {
                            context.alert(context.getString(R.string.restart_required_message)) {
                                okButton {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        context.restart()
                                    }, 100)
                                }
                                cancelButton {
                                    context.toastOnUi(R.string.restart_later_message)
                                }
                            }
                        } else {
                            postEvent(EventBus.RECREATE, "")
                        }
                    }, 100)
                }
            }
        }


        override fun getItemCount(): Int = entries.size
    }

    private val themeResIdMap = mapOf(
        "0" to R.style.Theme_Base_Dycolors,
        "1" to R.style.Theme_Base_GR,
        "2" to R.style.Theme_Base_Lemon,
        "3" to R.style.Theme_Base_WH,
        "4" to R.style.Theme_Base_Elink,
        "5" to R.style.Theme_Base_Sora,
        "6" to R.style.Theme_Base_August,
        "7" to R.style.Theme_Base_Carlotta,
        "8" to R.style.Theme_Base_Koharu,
        "9" to R.style.Theme_Base_Yuuka,
        "10" to R.style.Theme_Base_Phoebe,
        "11" to R.style.Theme_Base_Mujika,
        "12" to R.style.ThemeOverlay_WhiteBackground,
        "13" to R.style.AppTheme_Transparent)

    private fun getThemeColors(value: String): List<Int> {
        val themeResId = themeResIdMap[value] ?: return listOf(Color.GRAY, Color.GRAY, Color.GRAY, Color.GRAY)

        val themedContext = ContextThemeWrapper(context, themeResId)
        val attrs = intArrayOf(
            com.google.android.material.R.attr.colorOnSurface,
            com.google.android.material.R.attr.colorSecondaryContainer,
            com.google.android.material.R.attr.colorSecondaryVariant,
            com.google.android.material.R.attr.colorSurfaceContainer,
            androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            com.google.android.material.R.attr.colorSurface,
            com.google.android.material.R.attr.colorSecondaryContainer,
        )

        val ta = themedContext.obtainStyledAttributes(attrs)
        return List(attrs.size) { ta.getColor(it, Color.GRAY) }.also { ta.recycle() }
    }



    class ThemeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardView)
        val label: TextView = view.findViewById(R.id.themeLabel)
        val colorTop: MaterialCardView = view.findViewById(R.id.cv_title)
        val colorBook: MaterialCardView = view.findViewById(R.id.cv_book)
        val colorPin: MaterialCardView = view.findViewById(R.id.cv_pin)
        val colorBottom: MaterialCardView = view.findViewById(R.id.cv_bottom)
        val colorBottomRight: MaterialCardView = view.findViewById(R.id.right_rect)
        val colorBottomLeft : MaterialCardView = view.findViewById(R.id.left_circle)
        val background : MaterialCardView = view.findViewById(R.id.cardView)
    }
}


