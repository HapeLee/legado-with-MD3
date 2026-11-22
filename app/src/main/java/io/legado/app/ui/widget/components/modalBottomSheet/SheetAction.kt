package io.legado.app.ui.widget.components.modalBottomSheet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * 面板顶部两侧的声明式操作。
 *
 * 只描述「一颗按钮」：图标/文字、可用、选中与点击。之所以不放开任意 Composable，
 * 是为了让每个引擎把同一个操作渲染成自己的按钮：COUI 引擎映射为库的 `CouiSheetButton`
 * （胶囊材质、按压动效、按下防误触由库提供），其余引擎仍用 `MediumTonalButton`。
 *
 * 同侧需要多颗操作时用 `AppModalBottomSheet` 的 `startSheetActions` / `endSheetActions`，
 * 头部改由 App 自绘、按钮仍按引擎渲染。
 *
 * 已知差异：COUI 按钮只有 label 或 icon 二选一（文字优先），也没有选中态与小号尺寸，
 * 因此 [text] 与 [icon] 同时给出、[selected] 为 true 或 [compact] 为 true 时，
 * COUI 下只保留文字与可用态。
 */
@Immutable
data class SheetAction(
    /** 菜单按钮（[menu] 非空）不需要点击回调：点击只负责展开菜单。 */
    val onClick: (() -> Unit)? = null,
    val icon: ImageVector? = null,
    val text: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean = false,
    val contentDescription: String? = null,
    val modifier: Modifier = Modifier,
    /** 非 COUI 引擎用小号 Tonal 按钮；COUI 按钮只有一种尺寸。 */
    val compact: Boolean = false,
    /** 非空时这颗按钮变成菜单锚点：点击展开菜单，[onClick] 不再执行。 */
    val menu: ImmutableList<SheetMenuAction> = persistentListOf(),
)

/**
 * 挂在 [SheetAction] 上的菜单项。
 *
 * 菜单是「一颗按钮 + 一个弹层」，所以它属于具体按钮而不是面板头部；这样同一套数据在
 * 四套引擎下都能落到各自的原生弹层（COUI `Menu`、Miuix 下拉、Material 圆角菜单）。
 */
@Immutable
data class SheetMenuAction(
    val id: String,
    val label: String,
    val onClick: () -> Unit,
    /** null = 普通命令项；非 null 时渲染独立勾选态（开关类命令）。 */
    val checked: Boolean? = null,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
)
