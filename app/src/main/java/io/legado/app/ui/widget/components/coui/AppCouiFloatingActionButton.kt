package io.legado.app.ui.widget.components.coui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.itos.coui.effect.LocalCouiContourGlowEnabled
import com.itos.coui.floatingactionbutton.CouiFabDefaults
import com.itos.coui.floatingactionbutton.Fab
import com.itos.coui.theme.CouiTheme

/**
 * COUI 主题下的浮动操作按钮。
 *
 * 有页面采样源时走原厂 [Fab]：56dp 圆形、有色材质、`CouiFabDefaults` 的投影/焦散预设。
 * 缺少采样源（无顶栏、未启用模糊、渲染器创建失败）时降级为同配色的实色 FAB，
 * 保持相同尺寸、点击区域与无障碍语义，只是没有材质采样。
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
internal fun AppCouiFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalCouiPageBackdrop.current
    // 与标签行共用同一个渲染器工厂，材质不可用时返回 null 而不是抛给页面。
    val renderer = if (backdrop == null) null else rememberCouiCapsuleRenderer()
    if (backdrop != null && renderer != null) {
        val dark = CouiTheme.isDark
        val density = LocalDensity.current.density
        val contourGlow = LocalCouiContourGlowEnabled.current
        val material = remember(containerColor, dark) {
            CouiFabDefaults.material(containerColor, dark)
        }
        val shadow = remember(containerColor, density, contourGlow) {
            CouiFabDefaults.shadow(containerColor, density, contourGlow)
        }
        Fab(
            modifier = modifier,
            backdrop = backdrop,
            renderer = renderer,
            onClick = onClick,
            dark = dark,
            shape = CouiFabDefaults.circleShape,
            material = material,
            shadow = shadow,
            contentColor = contentColor,
            content = content,
        )
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = modifier,
            shape = CouiFabDefaults.circleShape,
            containerColor = containerColor,
            contentColor = contentColor,
            content = content,
        )
    }
}
