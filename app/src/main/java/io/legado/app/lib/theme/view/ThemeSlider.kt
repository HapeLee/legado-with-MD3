package io.legado.app.lib.theme.view

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.slider.Slider

//import io.legado.app.lib.theme.accentColor

/**
 * @author Aidan Follestad (afollestad)
 */


class ThemeSlider(context: Context, attrs: AttributeSet) : Slider(context, attrs) {

//    private var isDragging = false

//    @SuppressLint("ClickableViewAccessibility")
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        when (event.action) {
//            MotionEvent.ACTION_DOWN -> {
//                // 检查是否点击在滑块上
//                isDragging = isPointOnThumb(event.x, event.y)
//                if (!isDragging) {
//                    // 如果点击不在滑块上，不处理事件
//                    return false
//                }
//            }
//            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                isDragging = false
//            }
//        }
//
//        // 只有在拖动状态下或者在滑块上点击时才处理事件
//        return (isDragging || isPointOnThumb(event.x, event.y)) && super.onTouchEvent(event)
//    }
//
//    // 检查触摸点是否在滑块上
//    private fun isPointOnThumb(x: Float, y: Float): Boolean {
//        // 计算滑块位置
//        val valueRange = valueTo - valueFrom
//        val proportion = if (valueRange != 0f) (value - valueFrom) / valueRange else 0f
//
//        // 计算滑块中心X坐标
//        val sliderWidth = width
//        val sliderLeft = paddingLeft.toFloat()
//        val thumbCenterX = sliderLeft + proportion * sliderWidth
//
//
//        // 检查触摸点是否在滑块区域内
//        return x >= thumbCenterX - thumbRadius && x <= thumbCenterX + thumbRadius
//    }
}

