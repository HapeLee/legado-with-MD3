package io.legado.app.platform

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.feature.settings.themeconfig.LauncherIconOption
import io.legado.app.utils.getCompatDrawable

/**
 * M5-19e：`LauncherIconPickerSheet` 迁进共享层后，**它原先内联在同一文件里的图标表与渲染方式
 * 留在宿主**（`:app`）—— `R.mipmap.*` 资源 id、`getCompatDrawable`、`ImageView` / `AndroidView`
 * 都是平台 API，共享层只能拿到一个渲染槽。
 *
 * ⚠️ **顺手收窄了两个从未被读过的字段**（整理时量过，不是猜的）：
 *
 * - 原 `LauncherIconItem(label, component)` 与 `LauncherIcons.list` 里那 9 个
 *   `ComponentName(appCtx, LauncherX::class.java)`：全仓**零读取**（两个字段都只在构造时赋值）。
 *   换图标走的是 `LauncherIconHelp.changeIcon(String)`，它按 **value 字符串**与
 *   `className.substringAfterLast(".")` 比较，跟 `ComponentName` 无关。
 * - 于是这里只保留真正被用的两列：`value` 与 `resId`。
 *   **这不是审美问题**：把带 `appCtx` 的表原样搬到 `platform/` 会让该目录**首次出现
 *   「全局 Context 直连」**，而 G4 对"新区域"要求为零 —— 与其登记一笔**只为构造死字段而存在**
 *   的债，不如把死字段删掉。（`ui/config/themeConfig` 侧计数随本片 2 → 1，按棘轮下调基线。）
 *
 * ⚠️ **取 drawable 用的是 `LocalContext.current`，不是 splitties 的 `appCtx`**：这里本就在
 * composable 内，作用域化的 Context 才是正确写法（原 sheet 也正是 `LocalContext.current` +
 * `getCompatDrawable`），且不会给新目录引入全局 Context 直连。
 *
 * 渲染与迁移前那段 `AndroidView` **逐字等价**（`FIT_CENTER` + `setImageDrawable`，
 * `remember(resId)` 缓存 drawable）。
 */
@Composable
fun rememberLauncherIconOptions(): List<LauncherIconOption> {
    val context = LocalContext.current
    return LauncherIcons.list.map { item ->
        LauncherIconOption(
            value = item.value,
            image = { modifier ->
                val drawable = remember(item.resId) {
                    context.getCompatDrawable(item.resId)
                }
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        imageView.setImageDrawable(drawable)
                    },
                    modifier = modifier,
                )
            },
        )
    }
}

/** 一个启动图标位：`value` 是对外存的值（也是网格 key），`resId` 是宿主的 mipmap。 */
private data class LauncherIconItem(
    val value: String,
    val resId: Int,
)

private object LauncherIcons {

    val list = listOf(
        LauncherIconItem("ic_launcher", R.mipmap.ic_launcher),
        LauncherIconItem("launcherw", R.mipmap.launcherw),
        LauncherIconItem("launcher0", R.mipmap.launcher0),
        LauncherIconItem("launcher1", R.mipmap.launcher1),
        LauncherIconItem("launcher2", R.mipmap.launcher2),
        LauncherIconItem("launcher3", R.mipmap.launcher3),
        LauncherIconItem("launcher4", R.mipmap.launcher4),
        LauncherIconItem("launcher5", R.mipmap.launcher5),
        LauncherIconItem("launcher6", R.mipmap.launcher6),
    )
}
