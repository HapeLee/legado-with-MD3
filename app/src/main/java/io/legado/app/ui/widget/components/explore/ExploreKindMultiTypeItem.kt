package io.legado.app.ui.widget.components.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.showDialogFragment
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
    val state = rememberExploreKindItemState(kind, sourceUrl, useCase, activity, onRefreshKinds)
    state.ResolveDisplayName(displayNameOverride)

    val trailingIcon = rememberTrailingIcon(kind.type, isSelected)

    if (onClick != null) {
        if (content != null) {
            content(state.displayName, isSelected, onClick, trailingIcon)
        } else {
            ExploreKindItem(
                kind = kind,
                isClickable = true,
                onClick = onClick,
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = state.displayName,
                isSelected = isSelected,
                trailingIcon = trailingIcon
            )
        }
        return
    }

    when (kind.type) {
        ExploreKind.Type.url -> {
            val url = kind.url?.takeIf { it.isNotBlank() }
            val internalOnClick = {
                if (!url.isNullOrBlank()) {
                    if (kind.title.startsWith("ERROR:")) {
                        activity?.showDialogFragment(TextDialog("ERROR", url))
                    } else {
                        onOpenUrl(url)
                    }
                }
            }
            if (content != null) {
                content(state.displayName, isSelected, internalOnClick, trailingIcon)
            } else {
                ExploreKindItem(
                    kind = kind,
                    isClickable = !url.isNullOrBlank(),
                    onClick = internalOnClick,
                    modifier = modifier,
                    backgroundColor = backgroundColor,
                    isMiuix = isMiuix,
                    displayText = state.displayName,
                    isSelected = isSelected
                )
            }
        }

        ExploreKind.Type.button -> {
            val internalOnClick = {
                if (onRunAction != null) onRunAction()
                else state.executeAction(kind.action)
            }
            if (content != null) {
                content(state.displayName, isSelected, internalOnClick, trailingIcon)
            } else {
                ExploreKindItem(
                    kind = kind,
                    isClickable = !kind.action.isNullOrBlank(),
                    onClick = internalOnClick,
                    modifier = modifier,
                    backgroundColor = backgroundColor,
                    isMiuix = isMiuix,
                    displayText = state.displayName,
                    isSelected = isSelected,
                    trailingIcon = trailingIcon
                )
            }
        }

        ExploreKind.Type.text -> {
            if (content != null) {
                content(state.displayName, isSelected, {}, null)
            } else {
                TextTypeItem(
                    kind,
                    sourceUrl,
                    state,
                    valueOverride,
                    onValueChange,
                    modifier,
                    backgroundColor
                )
            }
        }

        ExploreKind.Type.toggle -> {
            ToggleTypeItem(
                kind,
                sourceUrl,
                state,
                valueOverride,
                onValueChange,
                isSelected,
                modifier,
                backgroundColor,
                isMiuix,
                trailingIcon,
                content
            )
        }

        ExploreKind.Type.select -> {
            SelectTypeItem(
                kind,
                sourceUrl,
                state,
                valueOverride,
                onValueChange,
                isSelected,
                modifier,
                backgroundColor,
                isMiuix,
                trailingIcon,
                content
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

@Composable
private fun TextTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    state: ExploreKindItemState,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
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
                    state.executeAction(kind.action)
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
    isSelected: Boolean,
    modifier: Modifier,
    backgroundColor: Color,
    isMiuix: Boolean,
    trailingIcon: @Composable (() -> Unit)?,
    content: (@Composable (displayName: String, isSelected: Boolean, onClick: () -> Unit, trailingIcon: @Composable (() -> Unit)?) -> Unit)?
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
        state.executeAction(kind.action)
    }

    if (content != null) {
        content(text, isSelected, internalOnClick, trailingIcon)
    } else {
        ExploreKindItem(
            kind = kind,
            isClickable = true,
            onClick = internalOnClick,
            modifier = modifier,
            backgroundColor = backgroundColor,
            isMiuix = isMiuix,
            displayText = text,
            isSelected = isSelected,
            trailingIcon = trailingIcon
        )
    }
}

@Composable
private fun SelectTypeItem(
    kind: ExploreKind,
    sourceUrl: String?,
    state: ExploreKindItemState,
    valueOverride: String?,
    onValueChange: ((String) -> Unit)?,
    isSelected: Boolean,
    modifier: Modifier,
    backgroundColor: Color,
    isMiuix: Boolean,
    trailingIcon: @Composable (() -> Unit)?,
    content: (@Composable (displayName: String, isSelected: Boolean, onClick: () -> Unit, trailingIcon: @Composable (() -> Unit)?) -> Unit)?
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

        if (content != null) {
            content(displayText, isSelected, internalOnClick, trailingIcon)
        } else {
            ExploreKindItem(
                kind = kind,
                isClickable = chars.isNotEmpty(),
                onClick = internalOnClick,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = displayText,
                isSelected = isSelected,
                trailingIcon = trailingIcon
            )
        }

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
                            state.executeAction(kind.action)
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
