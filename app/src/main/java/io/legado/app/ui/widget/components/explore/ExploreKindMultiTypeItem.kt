package io.legado.app.ui.widget.components.explore

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ExploreKindMultiTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    activity: AppCompatActivity? = null,
    onOpenUrl: (String) -> Unit,
    onRefreshKinds: () -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = LegadoTheme.colorScheme.surfaceContainer,
    isMiuix: Boolean,
    displayNameOverride: String? = null,
    valueOverride: String? = null,
    isSelected: Boolean = false,
    onValueChange: ((String) -> Unit)? = null,
    onRunAction: (() -> Unit)? = null,
    useCase: ExploreKindUiUseCase? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable (displayName: String, isSelected: Boolean, onClick: () -> Unit, trailingIcon: @Composable (() -> Unit)?) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentActivity = remember(context, activity) { activity ?: context.findActivity() }
    val state =
        rememberExploreKindItemState(kind, sourceUrl, useCase, currentActivity, onRefreshKinds)
    state.ResolveDisplayName(displayNameOverride)

    var showFullError by remember { mutableStateOf<String?>(null) }
    val errorMsg = remember(kind) { kind.errorMsg }
    val trailingIcon = rememberTrailingIcon(kind.type, isSelected)

    val BaseItem = @Composable { text: String, click: () -> Unit ->
        if (content != null) {
            content(text, isSelected, click, trailingIcon)
        } else {
            ExploreKindItem(
                kind = kind,
                isClickable = true,
                onClick = click,
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = text,
                isSelected = isSelected,
                trailingIcon = trailingIcon
            )
        }
    }

    when {
        errorMsg != null -> BaseItem(state.displayName) { showFullError = errorMsg }

        onClick != null -> BaseItem(state.displayName, onClick)

        else -> when (kind.type) {
            ExploreKind.Type.url -> {
                val url = kind.url?.takeIf { it.isNotBlank() }
                BaseItem(state.displayName) {
                    if (!url.isNullOrBlank()) onOpenUrl(url)
                }
            }

            ExploreKind.Type.button -> {
                BaseItem(state.displayName) {
                    if (onRunAction != null) onRunAction()
                    else state.executeAction(kind.action)
                }
            }

            ExploreKind.Type.text -> {
                TextTypeItem(
                    kind,
                    sourceUrl,
                    state,
                    valueOverride,
                    onValueChange,
                    onRunAction,
                    modifier,
                    backgroundColor
                )
            }

            ExploreKind.Type.toggle -> {
                ToggleTypeItem(
                    kind,
                    sourceUrl,
                    state,
                    valueOverride,
                    onValueChange,
                    onRunAction,
                    BaseItem
                )
            }

            ExploreKind.Type.select -> {
                SelectTypeItem(
                    kind,
                    sourceUrl,
                    state,
                    valueOverride,
                    onValueChange,
                    onRunAction,
                    modifier,
                    BaseItem
                )
            }

            else -> {
                if (content != null) {
                    content(state.displayName, isSelected, {}, null)
                } else {
                    ExploreKindItem(
                        kind = kind,
                        isClickable = false,
                        onClick = {},
                        modifier = modifier,
                        backgroundColor = backgroundColor,
                        isMiuix = isMiuix,
                        displayText = state.displayName
                    )
                }
            }
        }
    }

    AppAlertDialog(
        data = showFullError,
        onDismissRequest = { showFullError = null },
        title = "错误详情",
        confirmText = "复制",
        onConfirm = { error ->
            context.sendToClip(error)
            showFullError = null
        },
        dismissText = "关闭",
        onDismiss = { showFullError = null },
        content = { error ->
            SelectionContainer {
                AppText(
                    text = error,
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    )
}

private val ExploreKind.errorMsg: String?
    get() = when {
        title.startsWith("ERROR:", ignoreCase = true) -> url ?: title
        url?.contains("Exception") == true -> url
        title.contains("Exception") -> title
        else -> null
    }

private fun Context.findActivity(): AppCompatActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is AppCompatActivity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun TextTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    state: ExploreKindItemState,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
    onRunAction: (() -> Unit)?,
    modifier: Modifier,
    backgroundColor: Color
) {
    val scope = rememberCoroutineScope()
    var value by remember(sourceUrl, kind.title) {
        mutableStateOf(valueOverride ?: state.infoMap?.get(kind.title).orEmpty())
    }
    LaunchedEffect(valueOverride) {
        if (valueOverride != null) value = valueOverride
    }
    var actionJob by remember(sourceUrl, kind.title) { mutableStateOf<Job?>(null) }
    ExploreKindCompactTextField(
        value = value,
        onValueChange = { newValue ->
            value = newValue
            state.updateValue(newValue, onValueChange)
            if (!kind.action.isNullOrBlank()) {
                actionJob?.cancel()
                actionJob = scope.launch {
                    delay(600)
                    if (onRunAction != null) onRunAction() else state.executeAction(kind.action)
                }
            }
        },
        placeholder = state.displayName,
        modifier = modifier,
        backgroundColor = backgroundColor
    )
}

@Composable
private fun ToggleTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    state: ExploreKindItemState,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
    onRunAction: (() -> Unit)?,
    BaseItem: @Composable (String, () -> Unit) -> Unit
) {
    val chars = remember(kind.chars) {
        kind.chars?.filterNotNull().takeUnless { it.isNullOrEmpty() } ?: listOf("chars", "is null")
    }
    val left = kind.style().layout_justifySelf != "right"
    var char by remember(sourceUrl, kind.title, kind.default, kind.chars) {
        mutableStateOf(
            valueOverride
                ?: state.infoMap?.get(kind.title)
                    ?.takeUnless { it.isEmpty() }
                ?: (kind.default ?: chars.first()).also {
                    state.updateValue(it, onValueChange)
                }
        )
    }
    LaunchedEffect(valueOverride) {
        if (valueOverride != null) char = valueOverride
    }
    val text = if (left) "$char${state.displayName}" else "${state.displayName}$char"
    val internalOnClick = {
        val currentIndex = chars.indexOf(char)
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % chars.size
        char = chars.getOrElse(nextIndex) { "" }
        state.updateValue(char, onValueChange)
        if (onRunAction != null) onRunAction() else state.executeAction(kind.action)
    }

    BaseItem(text, internalOnClick)
}

@Composable
private fun SelectTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    state: ExploreKindItemState,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
    onRunAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    BaseItem: @Composable (String, () -> Unit) -> Unit
) {
    val chars = remember(kind.chars) {
        kind.chars?.filterNotNull().takeUnless { it.isNullOrEmpty() } ?: listOf("chars", "is null")
    }
    var selected by remember(sourceUrl, kind.title, kind.default, kind.chars) {
        mutableStateOf(
            valueOverride
                ?: state.infoMap?.get(kind.title)
                    ?.takeUnless { it.isEmpty() }
                ?: (kind.default ?: chars.first()).also {
                    state.updateValue(it, onValueChange)
                }
        )
    }
    LaunchedEffect(valueOverride) {
        if (valueOverride != null) selected = valueOverride
    }
    var showSelector by remember(sourceUrl, kind.title) { mutableStateOf(false) }

    Box(modifier = modifier) {
        val internalOnClick = { showSelector = true }
        val displayText = "${state.displayName} $selected"

        BaseItem(displayText, internalOnClick)

        RoundDropdownMenu(
            expanded = showSelector,
            onDismissRequest = { showSelector = false }
        ) {
            chars.forEach { option ->
                RoundDropdownMenuItem(
                    text = option,
                    onClick = {
                        showSelector = false
                        if (selected != option) {
                            selected = option
                            state.updateValue(option, onValueChange)
                            if (onRunAction != null) onRunAction() else state.executeAction(kind.action)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun rememberTrailingIcon(type: String, isSelected: Boolean): @Composable (() -> Unit)? {
    return remember(type, isSelected) {
        when (type) {
            ExploreKind.Type.button -> {
                {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = if (isSelected) LegadoTheme.colorScheme.onPrimaryContainer.copy(
                                alpha = 0.7f
                            ) else LegadoTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }

            ExploreKind.Type.toggle -> {
                {
                    AppIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = if (isSelected) LegadoTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else LegadoTheme.colorScheme.outlineVariant
                    )
                }
            }

            ExploreKind.Type.select -> {
                {
                    AppIcon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = if (isSelected) LegadoTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else LegadoTheme.colorScheme.outlineVariant
                    )
                }
            }

            else -> null
        }
    }
}
