package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import io.legado.app.R
import io.legado.app.databinding.ViewSimpleSliderBinding
import io.legado.app.lib.dialogs.alert

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class SimpleSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = ViewSimpleSliderBinding.inflate(LayoutInflater.from(context), this, true)
    private var _orientation: Int = 1
    private var _progress = 0
    private var _max = 100
    private var _min = 0
    private val isBottomBackground: Boolean

    var valueFormat: ((Int) -> String)? = null
    var onChanged: ((Int) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())

    private val repeatInterval = 320L

    private val tvTit: TextView

    var progress: Int
        get() = _progress
        set(value) {
            _progress = value.coerceIn(_min, _max)
            updateValue()
        }

    var max: Int
        get() = _max
        set(value) {
            _max = value
            _progress = _progress.coerceAtMost(_max)
            updateValue()
        }

    var min: Int
        get() = _min
        set(value) {
            _min = value
            _progress = _progress.coerceAtLeast(_min)
            updateValue()
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailSeekBar)
        isBottomBackground = typedArray.getBoolean(R.styleable.DetailSeekBar_isBottomBackground, false)
        val title = typedArray.getText(R.styleable.DetailSeekBar_title)
        _max = typedArray.getInt(R.styleable.DetailSeekBar_max, 100)
        _min = typedArray.getInt(R.styleable.DetailSeekBar_min, 0)
        _progress = _progress.coerceIn(_min, _max)

        val ta = context.obtainStyledAttributes(attrs, R.styleable.SimpleCounterView)
        _orientation = ta.getInt(R.styleable.SimpleCounterView_orientation, 1)
        ta.recycle()

        val slider = binding.slider
        slider.valueFrom = _min.toFloat()
        slider.valueTo = _max.toFloat()
        slider.value = _progress.toFloat()

        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                _progress = value.toInt()
                updateValue()
                onChanged?.invoke(_progress)
            }
        }

        tvTit = binding.tvSeekTitleVertical
        typedArray.recycle()

        tvTit.text = title
        TooltipCompat.setTooltipText(tvTit, title)

        binding.tvSeekValue.setOnClickListener {
            showSeekBarDialog()
        }

        updateValue()
    }

    private fun showSeekBarDialog() {
        context.alert(title = null, message = null) {
            customView {
                val detailSeekBar = SimpleCounterView(ctx).apply {
                    max = _max
                    min = _min
                    progress = _progress
                    valueFormat = this@SimpleSliderView.valueFormat
                    setTitle(tvTit.text)
                    onChanged = {
                        _progress = it
                        updateValue()
                        this@SimpleSliderView.onChanged?.invoke(it)
                    }
                }

                FrameLayout(ctx).apply {
                    setPadding(64, 0, 64, 0)
                    addView(detailSeekBar, LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.WRAP_CONTENT
                    ))
                }
            }

            okButton()
            cancelButton()
        }
    }

    private fun updateValue() {
        binding.tvSeekValue.text = valueFormat?.invoke(_progress) ?: _progress.toString()
        if (binding.slider.value.toInt() != _progress) {
            binding.slider.value = _progress.toFloat()
        }
    }


    private val incrementRunnable = object : Runnable {
        override fun run() {
            if (_progress < _max) {
                _progress++
                updateValue()
                onChanged?.invoke(_progress)
                handler.postDelayed(this, repeatInterval)
            }
        }
    }

    private val decrementRunnable = object : Runnable {
        override fun run() {
            if (_progress > _min) {
                _progress--
                updateValue()
                onChanged?.invoke(_progress)
                handler.postDelayed(this, repeatInterval)
            }
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(incrementRunnable)
        handler.removeCallbacks(decrementRunnable)
        super.onDetachedFromWindow()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        tvTit.isEnabled = enabled
        binding.tvSeekValue.isEnabled = enabled
    }

}
