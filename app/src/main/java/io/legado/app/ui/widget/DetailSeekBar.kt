package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.ViewCompat
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.databinding.ViewDetailSeekBarBinding
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener

@SuppressLint("ClickableViewAccessibility")
class DetailSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs),
    SeekBarChangeListener {
    private var binding: ViewDetailSeekBarBinding =
        ViewDetailSeekBarBinding.inflate(LayoutInflater.from(context), this, true)
    private val isBottomBackground: Boolean

    var valueFormat: ((progress: Int) -> String)? = null
        set(value) {
            field = value
            upValue()
        }

    var onStartTracking: (() -> Unit)? = null
    var onChanged: ((progress: Int) -> Unit)? = null
    var onStopTracking: (() -> Unit)? = null

    var progress: Int
        get() = binding.slider.value.toInt()
        set(value) {
            binding.slider.value = value.toFloat()
        }

    var max: Int
        get() = binding.slider.valueTo.toInt()
        set(value) {
            binding.slider.valueTo = value.toFloat()
        }

    var min: Int
        get() = binding.slider.valueFrom.toInt()
        set(value) {
            binding.slider.valueFrom = value.toFloat()
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailSeekBar)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.DetailSeekBar_isBottomBackground, false)
        val title = typedArray.getText(R.styleable.DetailSeekBar_title)

        binding.tvSeekTitle.apply {
            text = title
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            TooltipCompat.setTooltipText(this, title)
        }
        binding.tvSeekValue.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        binding.slider.contentDescription = title

        binding.slider.valueFrom = typedArray.getInteger(R.styleable.DetailSeekBar_min, 0).toFloat()
        binding.slider.valueTo = typedArray.getInteger(R.styleable.DetailSeekBar_max, 0).toFloat()
        typedArray.recycle()

        binding.slider.addOnChangeListener { _, value, fromUser ->
            upValue(value.toInt())

            if (fromUser) {
                onChanged?.invoke(value.toInt())
            }
        }

        binding.slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                onStartTracking?.invoke()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                onStopTracking?.invoke()
                onChanged?.invoke(slider.value.toInt())
            }
        })

        upValue()
    }

    private fun upValue(progress: Int = binding.slider.value.toInt()) {
        val displayValue = valueFormat?.invoke(progress) ?: progress.toString()
        binding.tvSeekValue.text = displayValue
        ViewCompat.setStateDescription(binding.slider, displayValue)
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        upValue(progress)
    }

    fun setTitle(title: CharSequence?) {
        binding.tvSeekTitle.text = title
        binding.slider.contentDescription = title
        TooltipCompat.setTooltipText(binding.tvSeekTitle, title)
        upValue()
    }
}
