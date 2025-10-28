package io.legado.app.utils

import android.annotation.SuppressLint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.core.view.size
import com.google.android.material.appbar.MaterialToolbar
import io.legado.app.R

object ToolbarUtils {
    @JvmStatic
    fun MaterialToolbar.setAllIconsColor(@ColorInt color: Int) {
        // 导航图标
        navigationIcon?.setTint(color)

        // 溢出图标
        overflowIcon = overflowIcon?.apply { setTint(color) }

        // 菜单项图标
        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(color)
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    fun MaterialToolbar.setMoreIconColor(color: Int) {
        val moreIcon = ContextCompat.getDrawable(context, R.drawable.ic_more)
        if (moreIcon != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            moreIcon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            overflowIcon = moreIcon
        }
    }
}