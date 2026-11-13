package io.legado.app.ui.widget.components.settingItem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itos.coui.effect.couiButtonFeedback
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Item as CouiItem
import com.itos.coui.text.CouiTextKind
import com.itos.coui.text.rememberCouiTextStyle
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.widget.components.LocalMochiSettingsGroup
import io.legado.app.ui.widget.components.LocalCouiSettingsGroup
import io.legado.app.ui.widget.components.card.SettingCard
import io.legado.app.ui.widget.components.menu.AppChoiceDropdown
import io.legado.app.ui.widget.components.menu.AppChoiceOption
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList
import ui.feice.mochi.list.mochiListDivider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    imageVector: ImageVector? = null,
    color: Color? = null,
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    title: String,
    description: String? = null,
    option: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    /**
     * 单选下拉的选项。与 [onChoiceSelect] 一起使用时，本行点击会打开四引擎原生的单选弹层，
     * 锚点落在右侧 `trailing` 取值区（与用户已经看惯的「值旁边弹出」一致）。
     * 旧的自定义内容槽 `dropdownMenu` 已由数据描述取代：内容槽无法映射到
     * `AppChoiceDropdown` 的 `selectedId` 语义，也无法在四引擎下保持同一形态。
     */
    choiceOptions: ImmutableList<AppChoiceOption>? = null,
    choiceSelectedId: String? = null,
    onChoiceSelect: ((String) -> Unit)? = null,
    /** 当前设备/产物不提供的档位：仍列出但不可选中。 */
    choiceUnavailableIds: Set<String> = emptySet(),
    /** 点击不可用档位时的提示回调；为 `null` 时该档位真正禁用。 */
    onChoiceUnavailableSelected: ((String) -> Unit)? = null,
    /**
     * 选中后等弹层收起再回调。切换根主题这类会立刻重建 composition 的操作必须开启：
     * Mochi 的浮层挂在 DecorView 上，测量期间摘除子视图会出问题。默认 false 保持既有调用方。
     */
    choiceCommitAfterDismiss: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    semanticRole: Role? = null,
    semanticStateDescription: String? = null,
    semanticToggleState: Boolean? = null,
    expanded: Boolean = false,
    onExpandChange: ((Boolean) -> Unit)? = null,
    expandContent: (@Composable ColumnScope.() -> Unit)? = null,
    /**
     * false 表示这一行是纯展示内容，不挂 `combinedClickable`。
     * 统一菜单入口用它作为 trigger，由主题分支包装出唯一的可点控件，
     * 避免与库的 Press 手势/点击语义重复。默认 true 保持既有调用方行为。
     */
    interactive: Boolean = true,
) {

    var showMenu by remember { mutableStateOf(false) }
    val menuOptions = choiceOptions
    val menuSelect = onChoiceSelect
    val hasChoiceMenu = menuOptions != null && menuSelect != null
    val isExpandable = expandContent != null && onExpandChange != null
    val mochiGrouped = LocalMochiSettingsGroup.current
    val isCoui = LegadoTheme.composeEngine == ComposeEngine.Coui
    val grouped = mochiGrouped || (isCoui && LocalCouiSettingsGroup.current != null)
    val couiColors = if (isCoui) CouiPageDefaults.themeColors() else null
    val trailingOption = option.takeIf { isCoui && trailingContent == null && !isExpandable }
    val useOptionAnchor = LegadoTheme.composeEngine == ComposeEngine.Mochi &&
        hasChoiceMenu && trailingContent == null && option != null
    // 受控弹层：本行负责开合与锚点位置，四引擎入口只渲染弹层，不包可点节点。
    // 撑满取值区是必需的：Miuix 的 popup 要按调用点 bounds 定位，0 尺寸容器量不到锚点，
    // 弹层不会出现。
    val menu: @Composable BoxScope.() -> Unit = {
        if (menuOptions != null && menuSelect != null) {
            AppChoiceDropdown(
                options = menuOptions,
                selectedId = choiceSelectedId,
                onSelect = menuSelect,
                title = title,
                modifier = Modifier.matchParentSize(),
                unavailableIds = choiceUnavailableIds,
                onUnavailableSelected = onChoiceUnavailableSelected,
                commitAfterDismiss = choiceCommitAfterDismiss,
                embeddedExpanded = showMenu,
                onDismissRequest = { showMenu = false },
            )
        }
    }
    val hasAction = onClick != null || onLongClick != null || hasChoiceMenu || isExpandable
    val interactionSource = if (isCoui && hasAction) remember { MutableInteractionSource() } else null
    val rowModifier = Modifier
        .semantics(mergeDescendants = !isCoui || hasAction) {
            semanticRole?.let { role = it }
            semanticStateDescription?.let { stateDescription = it }
            semanticToggleState?.let {
                toggleableState = if (it) ToggleableState.On else ToggleableState.Off
            }
            if (!enabled) disabled()
        }
        .then(if (interactionSource != null) {
            Modifier.couiButtonFeedback(interactionSource = interactionSource, enabled = enabled, list = true)
        } else Modifier)
        .then(if (interactive && (!isCoui || hasAction)) {
            Modifier.combinedClickable(
                interactionSource = interactionSource,
                indication = if (isCoui) null else LocalIndication.current,
                enabled = enabled,
                role = semanticRole,
                onClick = {
                    when {
                        hasChoiceMenu -> showMenu = true
                        isExpandable -> onExpandChange.invoke(!expanded)
                        else -> onClick?.invoke()
                    }
                },
                onLongClick = if (!isCoui || hasChoiceMenu || onLongClick != null) {
                    {
                        if (hasChoiceMenu) showMenu = true
                        onLongClick?.invoke()
                    }
                } else null,
            )
        } else Modifier)
    val leading: (@Composable () -> Unit)? = if (painter != null || imageVector != null) {
        {
            val tint = if (couiColors != null) {
                if (enabled) couiColors.summary else couiColors.disabled
            } else LegadoTheme.colorScheme.onSurfaceVariant
            if (painter != null) {
                Icon(painter = painter, contentDescription = null, tint = tint)
            } else if (imageVector != null) {
                Icon(imageVector = imageVector, contentDescription = null, tint = tint)
            }
        }
    } else null
    val supporting: (@Composable () -> Unit)? = if (description != null || option != null && trailingOption == null) {
        {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                description?.let {
                    AppText(
                        it,
                        style = if (isCoui) rememberCouiTextStyle(CouiTextKind.ItemSummary)
                        else LegadoTheme.typography.bodySmallEmphasized,
                        fontFamily = if (isCoui) LegadoTheme.typography.bodySmallEmphasized.fontFamily else null,
                        color = if (couiColors != null) {
                            if (enabled) couiColors.summary else couiColors.disabled
                        } else LegadoTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                if (option != null && trailingOption == null) {
                    Box {
                        AppText(
                            option,
                            style = LegadoTheme.typography.labelMediumEmphasized,
                            color = LegadoTheme.colorScheme.primary,
                        )
                        if (useOptionAnchor) menu()
                    }
                }
            }
        }
    } else null
    val trailing: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            if (isExpandable && trailingContent == null) {
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                )
            } else if (trailingOption != null && couiColors != null) {
                AppText(
                    text = trailingOption,
                    modifier = Modifier.widthIn(max = 162.dp),
                    style = rememberCouiTextStyle(CouiTextKind.ItemValue),
                    fontFamily = LegadoTheme.typography.labelMediumEmphasized.fontFamily,
                    color = if (enabled) couiColors.summary else couiColors.disabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                trailingContent?.invoke()
            }
            if (!useOptionAnchor) menu()
        }
    }
    val titleContent: @Composable () -> Unit = {
        AppText(
            text = title,
            style = if (isCoui) rememberCouiTextStyle(CouiTextKind.ItemTitle)
            else LegadoTheme.typography.titleMedium,
            fontFamily = if (isCoui) LegadoTheme.typography.titleMedium.fontFamily else null,
            color = if (couiColors != null) {
                if (enabled) couiColors.title else couiColors.disabled
            } else Color.Unspecified,
        )
    }

    SettingCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (mochiGrouped) Modifier.mochiListDivider(if (painter != null || imageVector != null) 56.dp else 16.dp)
                else Modifier
            ),
        cornerRadius = cornerRadius,
        colors = CardDefaults.cardColors(
            containerColor = color ?: if (grouped) Color.Transparent else LegadoTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column {
            if (isCoui) {
                CouiItem(
                    modifier = rowModifier,
                    icon = leading,
                    summary = supporting,
                    trailing = trailing,
                    enabled = enabled,
                    title = titleContent,
                )
            } else {
                ListItem(
                    modifier = rowModifier,
                    leadingContent = leading,
                    supportingContent = supporting,
                    trailingContent = trailing,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    content = titleContent,
                )
            }

            if (isExpandable) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 8.dp)
                    ) {
                        expandContent.invoke(this)
                    }
                }
            }
        }
    }
}
