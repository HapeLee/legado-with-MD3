package io.legado.app.ui.widget

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import io.legado.app.databinding.ViewBatteryBinding
import io.legado.app.utils.dpToPx

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private val binding: ViewBatteryBinding =
        ViewBatteryBinding.inflate(LayoutInflater.from(context), this, true)

    enum class BatteryMode { OUTER, INNER, ICON, ARROW, TIME, CLASSIC, NO_BATTERY, EMPTY }

    private var battery: Int = 0

    var text: CharSequence?
        get() = binding.batteryText.text
        set(value) {
            binding.batteryText.text = value
            binding.batteryTextInner.text = value
            binding.batteryTextEnd.text = value
        }

    var batteryMode: BatteryMode = BatteryMode.OUTER
        set(value) {
            field = value
            updateMode()
        }

    var typeface: Typeface?
        get() = binding.batteryText.typeface
        set(value) {
            binding.batteryText.typeface = value ?: Typeface.DEFAULT
            binding.batteryTextInner.typeface = value ?: Typeface.DEFAULT
            binding.batteryTextEnd.typeface = value ?: Typeface.DEFAULT
        }

    var textSize: Float
        get() = binding.batteryText.textSize / binding.batteryText.resources.displayMetrics.density
        set(value) {
            binding.batteryText.setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
        }

    fun setTextColor(@ColorInt color: Int) {
        binding.batteryText.setTextColor(color)
        binding.batteryTextInner.setTextColor(color)
    }

    fun setBattery(battery: Int, text: String? = null) {
        this.battery = battery.coerceIn(0, 100)

        when (batteryMode) {
            BatteryMode.TIME -> {
                val timePart = text ?: ""
                val batteryPart = "$battery"
                binding.batteryText.text = timePart
                binding.batteryTextInner.text = batteryPart
            }

            BatteryMode.CLASSIC -> {
                binding.batteryClassic.isBattery = true
                binding.batteryClassic.textSize = 11f
                binding.batteryClassic.setBattery(battery, text)
            }

            else -> {
                val displayText = text?.let { "$it  $battery" } ?: battery.toString()
                binding.batteryText.text = displayText
                binding.batteryTextEnd.text = displayText
                binding.batteryTextInner.text = displayText
            }
        }

        updateFill()
    }

    fun setColor(@ColorInt color: Int) {
        binding.batteryText.setTextColor(color)
        binding.batteryTextEnd.setTextColor(color)
        binding.batteryTextInner.setTextColor(color)
        binding.batteryFill.setCardBackgroundColor(color)
        binding.batteryClassic.setColor(color)
        binding.arrowIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        binding.batteryIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        binding.batteryIcon.alpha = 0.76f
        binding.arrowIcon.alpha = 0.76f
    }

    fun setTextIfNotEqual(newText: String?) {
        if (binding.batteryText.text?.toString() != newText) {
            binding.batteryText.text = newText
        }
    }


    private fun updateMode() {
        when (batteryMode) {
            BatteryMode.OUTER -> {
                binding.batteryText.visibility = GONE
                binding.batteryTextEnd.visibility = VISIBLE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = VISIBLE
                binding.batteryIcon.visibility = VISIBLE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.INNER -> {
                binding.batteryText.visibility = GONE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = VISIBLE
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = VISIBLE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.ICON -> {
                binding.batteryText.visibility = GONE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = VISIBLE
                binding.batteryIcon.visibility = VISIBLE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.ARROW -> {
                binding.batteryText.visibility = VISIBLE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = GONE
                binding.arrowIcon.visibility = VISIBLE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.TIME -> {
                binding.batteryText.visibility = VISIBLE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = VISIBLE
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = VISIBLE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.CLASSIC -> {
                binding.batteryText.visibility = GONE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = GONE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = VISIBLE
            }
            BatteryMode.NO_BATTERY -> {
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = GONE
                binding.batteryText.visibility = VISIBLE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.arrowIcon.visibility = GONE
                binding.batteryClassic.visibility = GONE
            }
            BatteryMode.EMPTY -> {
                binding.batteryFill.visibility = GONE
                binding.batteryIcon.visibility = GONE
                binding.batteryText.visibility = GONE
                binding.batteryTextEnd.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.arrowIcon.visibility = INVISIBLE
                binding.batteryClassic.visibility = GONE
            }
        }
        updateFill()
    }

    private fun updateFill() {
        post {
            val maxWidth = 17.dpToPx()
            val params = binding.batteryFill.layoutParams
            params.width = (maxWidth * battery / 100f).toInt()
            binding.batteryFill.layoutParams = params
        }
    }

}