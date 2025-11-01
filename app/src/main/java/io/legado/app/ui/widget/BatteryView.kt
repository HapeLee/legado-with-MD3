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

    enum class BatteryMode { OUTER, INNER, ICON, ARROW }

    private val batteryTypeface by lazy {
        Typeface.createFromAsset(context.assets, "font/number.ttf")
    }

    private var battery: Int = 0

    init {
        setPadding(4.dpToPx(), 3.dpToPx(), 6.dpToPx(), 3.dpToPx())
        binding.batteryText.typeface = batteryTypeface
        binding.batteryTextInner.typeface = batteryTypeface
    }

    var isBattery = false
        set(value) {
            field = value
            binding.arrowIcon.visibility = if (value) VISIBLE else GONE
            binding.batteryTextInner.visibility = if (value) VISIBLE else GONE
            binding.batteryTextInner.typeface = if (value) Typeface.DEFAULT else batteryTypeface
            binding.batteryText.typeface = if (value) Typeface.DEFAULT else batteryTypeface
            binding.batteryIcon.visibility = if (value) VISIBLE else GONE
            binding.batteryFill.visibility = if (value) VISIBLE else GONE
        }

    var text: CharSequence?
        get() = binding.batteryText.text
        set(value) {
            binding.batteryText.text = value
            binding.batteryTextInner.text = value
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
        binding.batteryText.text = text?.let { "$it  $battery" } ?: battery.toString()
        binding.batteryTextInner.text = text?.let { "$it  $battery" } ?: battery.toString()
        updateFill()
    }

    fun setColor(@ColorInt color: Int) {
        binding.batteryText.setTextColor(color)
        binding.batteryFill.setCardBackgroundColor(color)
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
                binding.arrowIcon.visibility = GONE
                binding.batteryText.visibility = VISIBLE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = VISIBLE
            }
            BatteryMode.INNER -> {
                binding.arrowIcon.visibility = GONE
                binding.batteryText.visibility = GONE
                binding.batteryTextInner.visibility = VISIBLE
                binding.batteryFill.visibility = GONE
            }
            BatteryMode.ICON -> {
                binding.arrowIcon.visibility = GONE
                binding.batteryText.visibility = GONE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = VISIBLE
            }
            BatteryMode.ARROW -> {
                binding.batteryText.visibility = VISIBLE
                binding.batteryTextInner.visibility = GONE
                binding.batteryFill.visibility = GONE
                binding.arrowIcon.visibility = VISIBLE
            }
        }
        updateFill()
    }

    private fun updateFill() {
        post {
            val maxWidth = 14.dpToPx()
            val params = binding.batteryFill.layoutParams
            params.width = (maxWidth * battery / 100f).toInt()
            binding.batteryFill.layoutParams = params
        }
    }

}