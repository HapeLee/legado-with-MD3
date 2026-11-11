package io.legado.app.ui.widget.components.menu

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** 选项菜单的声明式条目；[id] 是跨四套主题库唯一的业务标识。 */
@Stable
data class AppChoiceOption(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    /**
     * 在此项前插入一条纯分段线；Material / Mochi / COUI / Miuix compact 生效，
     * Miuix 默认设置行形态复用 `DropdownListSettingItem`，库不支持故忽略。
     */
    val dividerBefore: Boolean = false,
)

/** 命令菜单的声明式条目。 */
@Stable
data class AppAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    /** null = 普通命令；非 null = 独立开关勾选态，与 [selected] 的单选高亮互不干扰。 */
    val checked: Boolean? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    /** 在此项前插入一条纯分段线，四套引擎都生效。 */
    val dividerBefore: Boolean = false,
    /**
     * 分组标题文案。产品口径允许分段线不保留文字，因此该值只作为「此处需要一条线」的信号，
     * 四套引擎都不渲染文本；调用方不应依赖它出现在界面上。
     */
    val groupTitleBefore: String? = null,
    val icon: ImageVector? = null,
    /** 一层子菜单；Material 无级联能力，降级为平铺项但不丢子项 ID。 */
    val children: ImmutableList<AppAction> = persistentListOf(),
)

@Stable
data class AppActionGroup(
    val id: String,
    /** 见 [AppAction.groupTitleBefore]：仅触发组首分段线，文本不渲染。 */
    val title: String? = null,
    val actions: ImmutableList<AppAction>,
)

/**
 * 分段线的唯一判定口径（四套引擎共用）：显式 [AppAction.dividerBefore]、标题信号
 * [AppAction.groupTitleBefore] / [AppActionGroup.title]，以及第 2 组起的组首自动补线。
 *
 * 标题文字按产品口径降级为纯分段线，避免各引擎「有的显示文本、有的丢文本、有的只认第一组」
 * 造成同一份数据在四套主题下分组形态不一致。
 */
internal fun AppActionGroup.dividerBeforeAt(actionIndex: Int, groupIndex: Int): Boolean {
    val action = actions[actionIndex]
    return action.dividerBefore ||
        action.groupTitleBefore != null ||
        (actionIndex == 0 && (groupIndex > 0 || title != null))
}

enum class AppMenuAnchor { Row, Icon }

/**
 * 主题分支把各自需要的锚点 [modifier] 与打开动作 [open] 注入触发器。
 *
 * `trigger` 槽约定为**纯展示内容**，不得自带 `clickable` / `onClick` 语义：由主题分支
 * 统一包装成唯一的可点控件（COUI 锚点、Miuix 玻璃锚点、Mochi 库按钮、M3 `clickable`）。
 * 这样语义树上只有一个可点节点，Mochi 的 Press 手势也不会与内容自己的点击处理相争。
 *
 * COUI 用 `couiMenuAnchor` 注入 [modifier] 保留触点；Miuix 玻璃的 `glassPopupAnchor`
 * 挂在分支渲染的按钮最外层（库靠 modifier.foldIn 取按钮材质，注入 trigger 的图标会重复
 * 上报 bounds），故该分支 [modifier] 为空；Mochi 由库自己接管按钮，M3 两者皆空。
 * Miuix 常规菜单的 `expanded` 由库自持，[open] 不保证能从外部展开。
 */
@Stable
interface AppMenuTriggerScope {
    val modifier: Modifier
    fun open()
}

@Stable
internal class AppMenuTriggerScopeImpl(
    override val modifier: Modifier,
    private val onOpen: () -> Unit,
) : AppMenuTriggerScope {
    override fun open() = onOpen()
}
