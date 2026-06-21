@file:OptIn(ExperimentalMaterial3Api::class)

package io.legado.app.ui.widget.compose

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.ui.theme.AppTheme
import io.legado.app.utils.showDialogFragment

// ════════════════════════════════════════════════════════════════════════════
// 1. LegadoMiuix 组件系列
// ════════════════════════════════════════════════════════════════════════════

/**
 * Miuix 风格调色板，用于统一 AI 模块中卡片/按钮等组件的颜色。
 */
data class LegadoMiuixPalette(
    val accent: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val danger: Color = Color(0xFFE53935),
    val actionRadius: Dp? = null,
)

/**
 * 从当前 MaterialTheme 色板构建 [LegadoMiuixPalette]。
 */
@Composable
fun rememberLegadoMiuixPalette(): LegadoMiuixPalette {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        LegadoMiuixPalette(
            accent = cs.primary,
            surface = cs.surface,
            surfaceVariant = cs.surfaceVariant,
            primaryText = cs.onSurface,
            secondaryText = cs.onSurfaceVariant,
            danger = cs.error,
            actionRadius = 12.dp,
        )
    }
}

/**
 * 将单一强调色转换为 [LegadoMiuixPalette]（其余颜色取自 MaterialTheme）。
 */
@Composable
fun toMiuixPalette(color: Color): LegadoMiuixPalette {
    val cs = MaterialTheme.colorScheme
    return remember(color, cs) {
        LegadoMiuixPalette(
            accent = color,
            surface = cs.surface,
            surfaceVariant = cs.surfaceVariant,
            primaryText = cs.onSurface,
            secondaryText = cs.onSurfaceVariant,
            danger = cs.error,
            actionRadius = 12.dp,
        )
    }
}

/**
 * Miuix 风格卡片，使用 Material 3 [Surface] 实现。
 */
@Composable
fun LegadoMiuixCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = 12.dp,
    insidePadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(insidePadding), content = content)
    }
}

/**
 * Miuix 风格按钮。
 */
@Composable
fun LegadoMiuixActionButton(
    text: String,
    palette: LegadoMiuixPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    cornerRadius: Dp? = null,
    minWidth: Dp = 64.dp,
    minHeight: Dp = 40.dp,
    insidePadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    val radius = cornerRadius ?: palette.actionRadius ?: 12.dp
    val containerColor = if (primary) palette.accent else palette.surfaceVariant
    val contentColor = if (primary) {
        if (palette.accent == Color.Unspecified) Color.White else Color.White
    } else {
        palette.primaryText
    }
    Surface(
        modifier = modifier
            .widthIn(min = minWidth)
            .heightIn(min = minHeight)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(radius),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(insidePadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Miuix 风格可点击行。
 */
@Composable
fun LegadoMiuixActionRow(
    text: String,
    palette: LegadoMiuixPalette,
    onClick: () -> Unit,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
        contentColor = palette.primaryText,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            color = palette.primaryText,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Miuix 风格滑块。
 */
@Composable
fun LegadoMiuixSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    palette: LegadoMiuixPalette,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = palette.accent,
            activeTrackColor = palette.accent,
            inactiveTrackColor = palette.surfaceVariant,
        ),
    )
}

/**
 * Miuix 风格开关。
 */
@Composable
fun LegadoMiuixSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: LegadoMiuixPalette,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.surface,
            checkedTrackColor = palette.accent,
            uncheckedThumbColor = palette.surface,
            uncheckedTrackColor = palette.surfaceVariant,
        ),
    )
}

/**
 * Miuix 风格下拉选择框。
 */
@Composable
fun <T> LegadoMiuixSelectField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    optionDescription: (T) -> String = { "" },
    onSelected: (T) -> Unit,
    palette: LegadoMiuixPalette,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            color = palette.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(cornerRadius),
            color = palette.surfaceVariant,
            contentColor = palette.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionLabel(selected),
                    color = palette.primaryText,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = palette.secondaryText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 2. AppDialog 样式与框架
// ════════════════════════════════════════════════════════════════════════════

/**
 * 对话框/页面统一样式，提供颜色、字体与圆角。
 */
data class AppDialogStyle(
    val bodyFontFamily: FontFamily? = null,
    val titleFontFamily: FontFamily? = null,
    val primaryText: Color,
    val secondaryText: Color,
    val accent: Color,
    val fieldSurface: Color,
    val surface: Color,
    val stroke: Color,
    val actionRadius: Dp = 12.dp,
    val panelRadius: Dp = 16.dp,
)

/**
 * 从当前 MaterialTheme 构建 [AppDialogStyle]。
 */
@Composable
fun rememberAppDialogStyle(): AppDialogStyle {
    val cs = MaterialTheme.colorScheme
    return remember(cs) {
        AppDialogStyle(
            primaryText = cs.onSurface,
            secondaryText = cs.onSurfaceVariant,
            accent = cs.primary,
            fieldSurface = cs.surfaceVariant,
            surface = cs.surface,
            stroke = cs.outline,
        )
    }
}

/**
 * 将 [AppDialogStyle] 转换为 [LegadoMiuixPalette]。
 */
fun AppDialogStyle.toMiuixPalette(): LegadoMiuixPalette {
    return LegadoMiuixPalette(
        accent = accent,
        surface = surface,
        surfaceVariant = fieldSurface,
        primaryText = primaryText,
        secondaryText = secondaryText,
        danger = Color(0xFFE53935),
        actionRadius = actionRadius,
    )
}

/**
 * 对话框框架，提供标题、可滚动内容区与底部操作栏。
 */
@Composable
fun AppDialogFrame(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    title: String = "",
    scrollContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
    footer: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val style = rememberAppDialogStyle()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(style.panelRadius),
        color = style.surface,
        contentColor = style.primaryText,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = style.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = style.titleFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            val contentModifier = if (scrollContent) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
            Column(modifier = contentModifier, content = content)
            actions?.let { actionsContent ->
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actionsContent,
                )
            }
            footer?.let { footerContent ->
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = footerContent,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 3. AppManagement 组件系列
// ════════════════════════════════════════════════════════════════════════════

/**
 * 管理卡片菜单项。
 */
data class AppManagementMenuAction(
    val text: String,
    val danger: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 管理页面调色板，包含 [AppDialogStyle] 与 [LegadoMiuixPalette]。
 */
data class AppManagementPalette(
    val settings: AppDialogStyle,
    val miuix: LegadoMiuixPalette,
)

/**
 * 从当前 MaterialTheme 构建 [AppManagementPalette]。
 */
@Composable
fun rememberAppManagementPalette(): AppManagementPalette {
    val style = rememberAppDialogStyle()
    return remember(style) {
        AppManagementPalette(
            settings = style,
            miuix = style.toMiuixPalette(),
        )
    }
}

/**
 * 管理卡片，使用 Material 3 [Surface] 实现。
 */
@Composable
fun AppManagementCard(
    palette: AppManagementPalette,
    modifier: Modifier = Modifier,
    title: String = "",
    summary: String = "",
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    menuActions: List<AppManagementMenuAction> = emptyList(),
    insidePadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(palette.settings.panelRadius),
        color = palette.settings.surface,
        contentColor = palette.settings.primaryText,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(insidePadding)) {
            if (title.isNotBlank() || icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = palette.settings.accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            color = palette.settings.primaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (summary.isNotBlank()) {
                if (title.isNotBlank() || icon != null) Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary,
                    color = palette.settings.secondaryText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content?.invoke(this)
        }
    }
}

/**
 * 管理卡片"更多"按钮，点击后弹出菜单。
 */
@Composable
fun AppManagementMoreActionButton(
    actionsProvider: () -> List<AppManagementMenuAction>,
    palette: AppManagementPalette,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    val actions = actionsProvider()
    Box {
        Surface(
            modifier = modifier
                .size(36.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(palette.miuix.actionRadius ?: 12.dp),
            color = Color.Transparent,
            contentColor = palette.settings.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = contentDescription,
                    tint = palette.settings.secondaryText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.text,
                            color = if (action.danger) palette.settings.accent else palette.settings.primaryText,
                        )
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 4. Compose 对话框系列（DialogFragment 实现）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 确认对话框（DialogFragment），通过 [create] 工厂方法创建。
 */
class ComposeConfirmDialog : DialogFragment() {

    private var title: String = ""
    private var message: String = ""
    private var positiveText: String = "确定"
    private var negativeText: String = "取消"
    private var dangerPositive: Boolean = false
    private var onPositive: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    ComposeConfirmDialogContent(
                        title = title,
                        message = message,
                        positiveText = positiveText,
                        negativeText = negativeText,
                        dangerPositive = dangerPositive,
                        onPositive = {
                            onPositive?.invoke()
                            dismissAllowingStateLoss()
                        },
                        onDismiss = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            message: String,
            positiveText: String = "确定",
            negativeText: String = "取消",
            dangerPositive: Boolean = false,
            onPositive: () -> Unit,
        ): ComposeConfirmDialog {
            return ComposeConfirmDialog().apply {
                this.title = title
                this.message = message
                this.positiveText = positiveText
                this.negativeText = negativeText
                this.dangerPositive = dangerPositive
                this.onPositive = onPositive
            }
        }
    }
}

@Composable
private fun ComposeConfirmDialogContent(
    title: String,
    message: String,
    positiveText: String,
    negativeText: String,
    dangerPositive: Boolean,
    onPositive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val style = rememberAppDialogStyle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = style.surface,
        titleContentColor = style.primaryText,
        textContentColor = style.secondaryText,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onPositive) {
                Text(
                    text = positiveText,
                    color = if (dangerPositive) style.accent else style.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = negativeText, color = style.secondaryText)
            }
        },
    )
}

/**
 * 操作列表对话框（DialogFragment），通过 [create] 工厂方法创建。
 */
class ComposeActionListDialog : DialogFragment() {

    private var title: String = ""
    private var labels: List<String> = emptyList()
    private var dangerIndices: Set<Int> = emptySet()
    private var negativeText: String = "取消"
    private var onSelected: ((Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    ComposeActionListDialogContent(
                        title = title,
                        labels = labels,
                        dangerIndices = dangerIndices,
                        negativeText = negativeText,
                        onSelected = { index ->
                            onSelected?.invoke(index)
                            dismissAllowingStateLoss()
                        },
                        onDismiss = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            labels: List<String>,
            dangerIndices: Set<Int> = emptySet(),
            negativeText: String = "取消",
            onSelected: (Int) -> Unit,
        ): ComposeActionListDialog {
            return ComposeActionListDialog().apply {
                this.title = title
                this.labels = labels
                this.dangerIndices = dangerIndices
                this.negativeText = negativeText
                this.onSelected = onSelected
            }
        }
    }
}

@Composable
private fun ComposeActionListDialogContent(
    title: String,
    labels: List<String>,
    dangerIndices: Set<Int>,
    negativeText: String,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val style = rememberAppDialogStyle()
    Surface(
        shape = RoundedCornerShape(style.panelRadius),
        color = style.surface,
        contentColor = style.primaryText,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                color = style.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            labels.forEachIndexed { index, label ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(index) },
                    color = Color.Transparent,
                    contentColor = style.primaryText,
                ) {
                    Text(
                        text = label,
                        color = if (index in dangerIndices) style.accent else style.primaryText,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss),
                color = Color.Transparent,
                contentColor = style.secondaryText,
            ) {
                Text(
                    text = negativeText,
                    color = style.secondaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * 获取模型对话框（DialogFragment），通过 [create] 工厂方法创建。
 */
class ComposeFetchedModelDialog : DialogFragment() {

    private var modelIds: List<String> = emptyList()
    private var existingIds: Set<String> = emptySet()
    private var onAddSingle: ((String) -> Unit)? = null
    private var onAddSelected: ((List<String>) -> Unit)? = null
    private var onAddAll: ((List<String>) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    ComposeFetchedModelDialogContent(
                        modelIds = modelIds,
                        existingIds = existingIds,
                        onAddSingle = { id ->
                            onAddSingle?.invoke(id)
                            dismissAllowingStateLoss()
                        },
                        onAddSelected = { ids ->
                            onAddSelected?.invoke(ids)
                            dismissAllowingStateLoss()
                        },
                        onAddAll = { ids ->
                            onAddAll?.invoke(ids)
                            dismissAllowingStateLoss()
                        },
                        onDismiss = { dismissAllowingStateLoss() },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt(),
        )
    }

    companion object {
        fun create(
            modelIds: List<String>,
            existingIds: Set<String>,
            onAddSingle: (String) -> Unit,
            onAddSelected: (List<String>) -> Unit,
            onAddAll: (List<String>) -> Unit,
        ): ComposeFetchedModelDialog {
            return ComposeFetchedModelDialog().apply {
                this.modelIds = modelIds
                this.existingIds = existingIds
                this.onAddSingle = onAddSingle
                this.onAddSelected = onAddSelected
                this.onAddAll = onAddAll
            }
        }
    }
}

@Composable
private fun ComposeFetchedModelDialogContent(
    modelIds: List<String>,
    existingIds: Set<String>,
    onAddSingle: (String) -> Unit,
    onAddSelected: (List<String>) -> Unit,
    onAddAll: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()

    val filteredModels = remember(modelIds, searchQuery) {
        if (searchQuery.isBlank()) modelIds
        else modelIds.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    AppDialogFrame(
        title = "选择模型",
        scrollContent = false,
        onDismissRequest = onDismiss,
        content = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索", color = style.secondaryText) },
                shape = RoundedCornerShape(style.actionRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = style.primaryText,
                    unfocusedTextColor = style.primaryText,
                    focusedContainerColor = style.fieldSurface,
                    unfocusedContainerColor = style.fieldSurface,
                    cursorColor = style.accent,
                    focusedBorderColor = style.accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = style.stroke,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredModels, key = { it }) { modelId ->
                    val isExisting = modelId in existingIds
                    val isSelected = modelId in selectedIds
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) {
                                    selectedIds = selectedIds - modelId
                                } else {
                                    selectedIds = selectedIds + modelId
                                }
                            },
                        shape = RoundedCornerShape(style.actionRadius),
                        color = if (isSelected) style.fieldSurface else style.surface,
                        contentColor = style.primaryText,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) selectedIds = selectedIds + modelId
                                    else selectedIds = selectedIds - modelId
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = style.accent,
                                    uncheckedColor = style.secondaryText,
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = modelId,
                                color = if (isExisting) style.accent else style.primaryText,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            LegadoMiuixActionButton(
                text = "全部添加",
                palette = palette,
                onClick = {
                    onAddAll(modelIds)
                },
                cornerRadius = style.actionRadius,
            )
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = if (selectedIds.isEmpty()) "添加选中" else "添加选中(${selectedIds.size})",
                palette = palette,
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onAddSelected(selectedIds.toList())
                    }
                },
                primary = selectedIds.isNotEmpty(),
                cornerRadius = style.actionRadius,
            )
        },
    )
}

// ════════════════════════════════════════════════════════════════════════════
// 5. 对话框显示函数（Activity / Fragment 扩展）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 显示确认对话框。
 */
fun AppCompatActivity.showComposeConfirmDialog(
    title: String,
    message: String,
    positiveText: String = "确定",
    negativeText: String = "取消",
    dangerPositive: Boolean = false,
    onPositive: () -> Unit,
) {
    showDialogFragment(
        ComposeConfirmDialog.create(
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            dangerPositive = dangerPositive,
            onPositive = onPositive,
        )
    )
}

/**
 * 显示确认对话框。
 */
fun Fragment.showComposeConfirmDialog(
    title: String,
    message: String,
    positiveText: String = "确定",
    negativeText: String = "取消",
    dangerPositive: Boolean = false,
    onPositive: () -> Unit,
) {
    showDialogFragment(
        ComposeConfirmDialog.create(
            title = title,
            message = message,
            positiveText = positiveText,
            negativeText = negativeText,
            dangerPositive = dangerPositive,
            onPositive = onPositive,
        )
    )
}

/**
 * 显示操作列表对话框。
 */
fun AppCompatActivity.showComposeActionListDialog(
    title: String,
    labels: List<String>,
    dangerIndices: Set<Int> = emptySet(),
    negativeText: String = "取消",
    onSelected: (Int) -> Unit,
) {
    showDialogFragment(
        ComposeActionListDialog.create(
            title = title,
            labels = labels,
            dangerIndices = dangerIndices,
            negativeText = negativeText,
            onSelected = onSelected,
        )
    )
}

/**
 * 显示操作列表对话框。
 */
fun Fragment.showComposeActionListDialog(
    title: String,
    labels: List<String>,
    dangerIndices: Set<Int> = emptySet(),
    negativeText: String = "取消",
    onSelected: (Int) -> Unit,
) {
    showDialogFragment(
        ComposeActionListDialog.create(
            title = title,
            labels = labels,
            dangerIndices = dangerIndices,
            negativeText = negativeText,
            onSelected = onSelected,
        )
    )
}

/**
 * 显示文本输入对话框。
 */
fun AppCompatActivity.showComposeTextInputDialog(
    title: String,
    hint: String = "",
    initialValue: String = "",
    minLines: Int = 1,
    maxLines: Int = 1,
    neutralText: String? = null,
    validateInput: ((String) -> Boolean)? = null,
    onPositive: (String) -> Unit,
    onNeutral: (() -> Unit)? = null,
) {
    showDialogFragment(
        ComposeTextInputDialog.create(
            title = title,
            hint = hint,
            initialValue = initialValue,
            minLines = minLines,
            maxLines = maxLines,
            neutralText = neutralText,
            validateInput = validateInput,
            onPositive = onPositive,
            onNeutral = onNeutral,
        )
    )
}

/**
 * 显示文本输入对话框。
 */
fun Fragment.showComposeTextInputDialog(
    title: String,
    hint: String = "",
    initialValue: String = "",
    minLines: Int = 1,
    maxLines: Int = 1,
    neutralText: String? = null,
    validateInput: ((String) -> Boolean)? = null,
    onPositive: (String) -> Unit,
    onNeutral: (() -> Unit)? = null,
) {
    showDialogFragment(
        ComposeTextInputDialog.create(
            title = title,
            hint = hint,
            initialValue = initialValue,
            minLines = minLines,
            maxLines = maxLines,
            neutralText = neutralText,
            validateInput = validateInput,
            onPositive = onPositive,
            onNeutral = onNeutral,
        )
    )
}

/**
 * 显示多选对话框。
 */
fun AppCompatActivity.showComposeMultiChoiceDialog(
    title: String,
    labels: List<String>,
    checkedIndices: Set<Int> = emptySet(),
    positiveText: String = "确定",
    negativeText: String = "取消",
    onPositive: (BooleanArray) -> Unit,
) {
    showDialogFragment(
        ComposeMultiChoiceDialog.create(
            title = title,
            labels = labels,
            checkedIndices = checkedIndices,
            positiveText = positiveText,
            negativeText = negativeText,
            onPositive = onPositive,
        )
    )
}

/**
 * 显示多选对话框。
 */
fun Fragment.showComposeMultiChoiceDialog(
    title: String,
    labels: List<String>,
    checkedIndices: Set<Int> = emptySet(),
    positiveText: String = "确定",
    negativeText: String = "取消",
    onPositive: (BooleanArray) -> Unit,
) {
    showDialogFragment(
        ComposeMultiChoiceDialog.create(
            title = title,
            labels = labels,
            checkedIndices = checkedIndices,
            positiveText = positiveText,
            negativeText = negativeText,
            onPositive = onPositive,
        )
    )
}

/**
 * 显示数字选择对话框。
 */
fun AppCompatActivity.showComposeNumberPickerDialog(
    title: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    positiveText: String = "确定",
    negativeText: String = "取消",
    onValue: (Int) -> Unit,
) {
    showDialogFragment(
        ComposeNumberPickerDialog.create(
            title = title,
            value = value,
            minValue = minValue,
            maxValue = maxValue,
            positiveText = positiveText,
            negativeText = negativeText,
            onValue = onValue,
        )
    )
}

/**
 * 显示数字选择对话框。
 */
fun Fragment.showComposeNumberPickerDialog(
    title: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    positiveText: String = "确定",
    negativeText: String = "取消",
    onValue: (Int) -> Unit,
) {
    showDialogFragment(
        ComposeNumberPickerDialog.create(
            title = title,
            value = value,
            minValue = minValue,
            maxValue = maxValue,
            positiveText = positiveText,
            negativeText = negativeText,
            onValue = onValue,
        )
    )
}

/**
 * 显示带复选框的文本表单对话框。
 */
fun AppCompatActivity.showComposeTextFormDialogWithChecks(
    title: String,
    labels: List<String>,
    initialValues: List<String>,
    passwordFields: Set<Int> = emptySet(),
    checkboxLabels: List<String> = emptyList(),
    checkedIndices: Set<Int> = emptySet(),
    positiveText: String = "确定",
    negativeText: String = "取消",
    validateInput: ((List<String>) -> Boolean)? = null,
    onPositive: (List<String>, Set<Int>) -> Unit,
) {
    showDialogFragment(
        ComposeTextFormDialogWithChecks.create(
            title = title,
            labels = labels,
            initialValues = initialValues,
            passwordFields = passwordFields,
            checkboxLabels = checkboxLabels,
            checkedIndices = checkedIndices,
            positiveText = positiveText,
            negativeText = negativeText,
            validateInput = validateInput,
            onPositive = onPositive,
        )
    )
}

/**
 * 显示带复选框的文本表单对话框。
 */
fun Fragment.showComposeTextFormDialogWithChecks(
    title: String,
    labels: List<String>,
    initialValues: List<String>,
    passwordFields: Set<Int> = emptySet(),
    checkboxLabels: List<String> = emptyList(),
    checkedIndices: Set<Int> = emptySet(),
    positiveText: String = "确定",
    negativeText: String = "取消",
    validateInput: ((List<String>) -> Boolean)? = null,
    onPositive: (List<String>, Set<Int>) -> Unit,
) {
    showDialogFragment(
        ComposeTextFormDialogWithChecks.create(
            title = title,
            labels = labels,
            initialValues = initialValues,
            passwordFields = passwordFields,
            checkboxLabels = checkboxLabels,
            checkedIndices = checkedIndices,
            positiveText = positiveText,
            negativeText = negativeText,
            validateInput = validateInput,
            onPositive = onPositive,
        )
    )
}

// ════════════════════════════════════════════════════════════════════════════
// 6. 内部 DialogFragment 实现（文本输入 / 多选 / 数字选择 / 表单）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 文本输入对话框。
 */
class ComposeTextInputDialog : DialogFragment() {

    private var title: String = ""
    private var hint: String = ""
    private var initialValue: String = ""
    private var minLines: Int = 1
    private var maxLines: Int = 1
    private var neutralText: String? = null
    private var validateInput: ((String) -> Boolean)? = null
    private var onPositive: ((String) -> Unit)? = null
    private var onNeutral: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    var text by remember { mutableStateOf(initialValue) }
                    val style = rememberAppDialogStyle()
                    AlertDialog(
                        onDismissRequest = { dismissAllowingStateLoss() },
                        containerColor = style.surface,
                        titleContentColor = style.primaryText,
                        textContentColor = style.secondaryText,
                        title = { Text(title) },
                        text = {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = maxLines == 1,
                                minLines = minLines,
                                maxLines = if (maxLines == 1) 1 else maxLines + 4,
                                placeholder = if (hint.isNotBlank()) {
                                    { Text(hint, color = style.secondaryText) }
                                } else null,
                                visualTransformation = if (false) PasswordVisualTransformation()
                                else VisualTransformation.None,
                                shape = RoundedCornerShape(style.actionRadius),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = style.primaryText,
                                    unfocusedTextColor = style.primaryText,
                                    focusedContainerColor = style.fieldSurface,
                                    unfocusedContainerColor = style.fieldSurface,
                                    cursorColor = style.accent,
                                    focusedBorderColor = style.accent.copy(alpha = 0.55f),
                                    unfocusedBorderColor = style.stroke,
                                ),
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val valid = validateInput?.invoke(text) ?: true
                                if (valid) {
                                    onPositive?.invoke(text)
                                    dismissAllowingStateLoss()
                                }
                            }) {
                                Text("确定", color = style.accent)
                            }
                        },
                        dismissButton = {
                            Row {
                                if (neutralText != null) {
                                    TextButton(onClick = {
                                        onNeutral?.invoke()
                                    }) {
                                        Text(neutralText!!, color = style.secondaryText)
                                    }
                                }
                                TextButton(onClick = { dismissAllowingStateLoss() }) {
                                    Text("取消", color = style.secondaryText)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            hint: String = "",
            initialValue: String = "",
            minLines: Int = 1,
            maxLines: Int = 1,
            neutralText: String? = null,
            validateInput: ((String) -> Boolean)? = null,
            onPositive: (String) -> Unit,
            onNeutral: (() -> Unit)? = null,
        ): ComposeTextInputDialog {
            return ComposeTextInputDialog().apply {
                this.title = title
                this.hint = hint
                this.initialValue = initialValue
                this.minLines = minLines
                this.maxLines = maxLines
                this.neutralText = neutralText
                this.validateInput = validateInput
                this.onPositive = onPositive
                this.onNeutral = onNeutral
            }
        }
    }
}

/**
 * 多选对话框。
 */
class ComposeMultiChoiceDialog : DialogFragment() {

    private var title: String = ""
    private var labels: List<String> = emptyList()
    private var checkedIndices: Set<Int> = emptySet()
    private var positiveText: String = "确定"
    private var negativeText: String = "取消"
    private var onPositive: ((BooleanArray) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    val style = rememberAppDialogStyle()
                    val checkedState = remember {
                        mutableStateOf(BooleanArray(labels.size) { it in checkedIndices })
                    }
                    AlertDialog(
                        onDismissRequest = { dismissAllowingStateLoss() },
                        containerColor = style.surface,
                        titleContentColor = style.primaryText,
                        textContentColor = style.secondaryText,
                        title = { Text(title) },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                labels.forEachIndexed { index, label ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val current = checkedState.value
                                                current[index] = !current[index]
                                                checkedState.value = current.copyOf()
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checkedState.value[index],
                                            onCheckedChange = {
                                                val current = checkedState.value
                                                current[index] = it
                                                checkedState.value = current.copyOf()
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = style.accent,
                                                uncheckedColor = style.secondaryText,
                                            ),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = label,
                                            color = style.primaryText,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onPositive?.invoke(checkedState.value)
                                dismissAllowingStateLoss()
                            }) {
                                Text(positiveText, color = style.accent)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissAllowingStateLoss() }) {
                                Text(negativeText, color = style.secondaryText)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            labels: List<String>,
            checkedIndices: Set<Int> = emptySet(),
            positiveText: String = "确定",
            negativeText: String = "取消",
            onPositive: (BooleanArray) -> Unit,
        ): ComposeMultiChoiceDialog {
            return ComposeMultiChoiceDialog().apply {
                this.title = title
                this.labels = labels
                this.checkedIndices = checkedIndices
                this.positiveText = positiveText
                this.negativeText = negativeText
                this.onPositive = onPositive
            }
        }
    }
}

/**
 * 数字选择对话框。
 */
class ComposeNumberPickerDialog : DialogFragment() {

    private var title: String = ""
    private var value: Int = 0
    private var minValue: Int = 0
    private var maxValue: Int = 100
    private var positiveText: String = "确定"
    private var negativeText: String = "取消"
    private var onValue: ((Int) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    val style = rememberAppDialogStyle()
                    var currentValue by remember { mutableStateOf(value.coerceIn(minValue, maxValue)) }
                    AlertDialog(
                        onDismissRequest = { dismissAllowingStateLoss() },
                        containerColor = style.surface,
                        titleContentColor = style.primaryText,
                        textContentColor = style.secondaryText,
                        title = { Text(title) },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = currentValue.toString(),
                                    color = style.accent,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = currentValue.toFloat(),
                                    onValueChange = {
                                        currentValue = it.toInt().coerceIn(minValue, maxValue)
                                    },
                                    valueRange = minValue.toFloat()..maxValue.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = style.accent,
                                        activeTrackColor = style.accent,
                                    ),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("$minValue", color = style.secondaryText, fontSize = 12.sp)
                                    Text("$maxValue", color = style.secondaryText, fontSize = 12.sp)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                onValue?.invoke(currentValue)
                                dismissAllowingStateLoss()
                            }) {
                                Text(positiveText, color = style.accent)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissAllowingStateLoss() }) {
                                Text(negativeText, color = style.secondaryText)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            value: Int,
            minValue: Int,
            maxValue: Int,
            positiveText: String = "确定",
            negativeText: String = "取消",
            onValue: (Int) -> Unit,
        ): ComposeNumberPickerDialog {
            return ComposeNumberPickerDialog().apply {
                this.title = title
                this.value = value
                this.minValue = minValue
                this.maxValue = maxValue
                this.positiveText = positiveText
                this.negativeText = negativeText
                this.onValue = onValue
            }
        }
    }
}

/**
 * 带复选框的文本表单对话框。
 */
class ComposeTextFormDialogWithChecks : DialogFragment() {

    private var title: String = ""
    private var labels: List<String> = emptyList()
    private var initialValues: List<String> = emptyList()
    private var passwordFields: Set<Int> = emptySet()
    private var checkboxLabels: List<String> = emptyList()
    private var checkedIndices: Set<Int> = emptySet()
    private var positiveText: String = "确定"
    private var negativeText: String = "取消"
    private var validateInput: ((List<String>) -> Boolean)? = null
    private var onPositive: ((List<String>, Set<Int>) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as Dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppTheme {
                    val style = rememberAppDialogStyle()
                    val fieldValues = remember {
                        labels.mapIndexed { index, _ ->
                            mutableStateOf(initialValues.getOrElse(index) { "" })
                        }
                    }
                    val checkboxStates = remember {
                        checkboxLabels.mapIndexed { index, _ ->
                            mutableStateOf(index in checkedIndices)
                        }
                    }
                    AlertDialog(
                        onDismissRequest = { dismissAllowingStateLoss() },
                        containerColor = style.surface,
                        titleContentColor = style.primaryText,
                        textContentColor = style.secondaryText,
                        title = { Text(title) },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                labels.forEachIndexed { index, label ->
                                    val isPassword = index in passwordFields
                                    OutlinedTextField(
                                        value = fieldValues[index].value,
                                        onValueChange = { fieldValues[index].value = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        label = { Text(label) },
                                        visualTransformation = if (isPassword)
                                            PasswordVisualTransformation()
                                        else VisualTransformation.None,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = if (isPassword) KeyboardType.Password
                                            else KeyboardType.Text
                                        ),
                                        shape = RoundedCornerShape(style.actionRadius),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = style.primaryText,
                                            unfocusedTextColor = style.primaryText,
                                            focusedContainerColor = style.fieldSurface,
                                            unfocusedContainerColor = style.fieldSurface,
                                            cursorColor = style.accent,
                                            focusedBorderColor = style.accent.copy(alpha = 0.55f),
                                            unfocusedBorderColor = style.stroke,
                                        ),
                                    )
                                }
                                if (checkboxLabels.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    checkboxLabels.forEachIndexed { index, label ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    checkboxStates[index].value =
                                                        !checkboxStates[index].value
                                                }
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Checkbox(
                                                checked = checkboxStates[index].value,
                                                onCheckedChange = {
                                                    checkboxStates[index].value = it
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = style.accent,
                                                    uncheckedColor = style.secondaryText,
                                                ),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = label,
                                                color = style.primaryText,
                                                fontSize = 14.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val values = fieldValues.map { it.value }
                                val valid = validateInput?.invoke(values) ?: true
                                if (valid) {
                                    val checked = checkboxLabels.indices
                                        .filter { checkboxStates[it].value }
                                        .toSet()
                                    onPositive?.invoke(values, checked)
                                    dismissAllowingStateLoss()
                                }
                            }) {
                                Text(positiveText, color = style.accent)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { dismissAllowingStateLoss() }) {
                                Text(negativeText, color = style.secondaryText)
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    companion object {
        fun create(
            title: String,
            labels: List<String>,
            initialValues: List<String>,
            passwordFields: Set<Int> = emptySet(),
            checkboxLabels: List<String> = emptyList(),
            checkedIndices: Set<Int> = emptySet(),
            positiveText: String = "确定",
            negativeText: String = "取消",
            validateInput: ((List<String>) -> Boolean)? = null,
            onPositive: (List<String>, Set<Int>) -> Unit,
        ): ComposeTextFormDialogWithChecks {
            return ComposeTextFormDialogWithChecks().apply {
                this.title = title
                this.labels = labels
                this.initialValues = initialValues
                this.passwordFields = passwordFields
                this.checkboxLabels = checkboxLabels
                this.checkedIndices = checkedIndices
                this.positiveText = positiveText
                this.negativeText = negativeText
                this.validateInput = validateInput
                this.onPositive = onPositive
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// 7. 工具函数
// ════════════════════════════════════════════════════════════════════════════

/**
 * 释放 Compose 中 AndroidView 持有的图片资源。
 * 空实现，仅满足 AI 代码中的 onRelease 回调签名。
 */
fun Any?.releaseComposeImage() {
    // 空实现：Glide 会自动回收 ImageView 关联的图片资源
}

/**
 * 返回强调色（基于 MaterialTheme.primary）。
 */
@Composable
fun accent(color: Color = MaterialTheme.colorScheme.primary): Color {
    return color
}
