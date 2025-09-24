package io.legato.kazusa.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.Menu
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import io.legato.kazusa.R
import io.legato.kazusa.databinding.ViewSelectActionBarBinding
//import io.legado.app.lib.theme.accentColor
//import io.legado.app.lib.theme.bottomBackground
//import io.legado.app.lib.theme.getPrimaryTextColor
//import io.legado.app.lib.theme.getSecondaryDisabledTextColor
import io.legato.kazusa.utils.applyNavigationBarPadding
import io.legato.kazusa.utils.visible


@Suppress("unused")
class SelectActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

//    private val bgIsLight = ColorUtils.isColorLight(context.bottomBackground)
//    private val primaryTextColor = context.getPrimaryTextColor(bgIsLight)
//    private val disabledColor = context.getSecondaryDisabledTextColor(bgIsLight)

    private var callBack: CallBack? = null
    private var selMenu: PopupMenu? = null
    private val binding = ViewSelectActionBarBinding
        .inflate(LayoutInflater.from(context), this, true)

    init {
        if (!isInEditMode) {
//            setBackgroundColor(context.bottomBackground)
//            //elevation = context.elevation
//            binding.cbSelectedAll.setTextColor(primaryTextColor)
//            TintHelper.setTint(binding.cbSelectedAll, context.accentColor, !bgIsLight)
//            binding.ivMenuMore.setColorFilter(disabledColor, PorterDuff.Mode.SRC_IN)
            binding.cbSelectedAll.setOnCheckedChangeListener { buttonView, isChecked ->
                if (buttonView.isPressed) {
                    callBack?.selectAll(isChecked)
                }
            }
            binding.btnRevertSelection.setOnClickListener { callBack?.revertSelection() }
            binding.btnSelectActionMain.setOnClickListener { callBack?.onClickSelectBarMainAction() }
            binding.ivMenuMore.addOnCheckedChangeListener { button, isChecked ->
                if (isChecked) {
                    selMenu?.show()
                }
            }
            applyNavigationBarPadding()
        }
    }

    fun setMainActionText(text: String) = binding.run {
        btnSelectActionMain.text = text
        btnSplit.visible()
    }

    fun setMainActionText(@StringRes id: Int) = binding.run {
        btnSelectActionMain.setText(id)
        btnSplit.visible()
    }

    fun inflateMenu(@MenuRes resId: Int): Menu? {
        selMenu = PopupMenu(context, binding.ivMenuMore).apply {
            inflate(resId)

            setOnDismissListener {
                binding.ivMenuMore.isChecked = false
            }
        }
        return selMenu?.menu
    }


    fun setCallBack(callBack: CallBack) {
        this.callBack = callBack
    }

    fun setOnMenuItemClickListener(listener: PopupMenu.OnMenuItemClickListener) {
        selMenu?.setOnMenuItemClickListener(listener)
    }

    fun upCountView(selectCount: Int, allCount: Int) = binding.run {
        if (selectCount == 0) {
            cbSelectedAll.isChecked = false
        } else {
            cbSelectedAll.isChecked = selectCount >= allCount
        }

        //重置全选的文字
        if (cbSelectedAll.isChecked) {
            cbSelectedAll.text = context.getString(
                R.string.select_cancel_count,
                selectCount,
                allCount
            )
        } else {
            cbSelectedAll.text = context.getString(
                R.string.select_all_count,
                selectCount,
                allCount
            )
        }
        setMenuClickable(selectCount > 0)
    }

    private fun setMenuClickable(isClickable: Boolean) = binding.run {
        btnRevertSelection.isEnabled = isClickable
        btnRevertSelection.isClickable = isClickable
        btnSplit.isEnabled = isClickable
        btnSplit.isClickable = isClickable
    }

    interface CallBack {

        fun selectAll(selectAll: Boolean)

        fun revertSelection()

        fun onClickSelectBarMainAction() {}
    }
}