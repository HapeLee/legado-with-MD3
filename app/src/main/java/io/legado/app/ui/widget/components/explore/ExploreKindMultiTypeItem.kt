package io.legado.app.ui.widget.components.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.legado.app.App
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.widget.components.explore.ExploreKindUiUseCase
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown

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
    onValueChange: ((String) -> Unit)? = null,
    onRunAction: (() -> Unit)? = null,
    useCase: ExploreKindUiUseCase? = null
) {
    val scope = rememberCoroutineScope()
    val infoMap = remember(sourceUrl, useCase) {
        if (useCase == null) null else sourceUrl?.takeIf { it.isNotBlank() }?.let(::getExploreInfoMap)
    }
    var displayName by remember(sourceUrl, kind.title, kind.viewName) { mutableStateOf(kind.title) }

    LaunchedEffect(displayNameOverride, sourceUrl, kind.title, kind.viewName, useCase) {
        displayName = displayNameOverride
            ?: useCase?.resolveDisplayName(kind, sourceUrl, infoMap)
                    ?: kind.title
    }

    fun runAction(action: String?) {
        if (action.isNullOrBlank()) return
        if (onRunAction != null) {
            onRunAction()
        } else {
            val useCase = useCase ?: return
            scope.launch(IO) {
                useCase.executeAction(
                    action = action,
                    title = kind.title,
                    sourceUrl = sourceUrl,
                    infoMap = infoMap,
                    activity = activity,
                    onRefreshKinds = onRefreshKinds
                )
            }
        }
    }

    fun updateValue(value: String) {
        if (onValueChange != null) {
            onValueChange(value)
        } else {
            infoMap?.let {
                it[kind.title] = value
                it.saveNow()
            }
        }
    }

    when (kind.type) {
        ExploreKind.Type.url -> {
            val url = kind.url?.takeIf { it.isNotBlank() }
            ExploreKindItem(
                kind = kind,
                isClickable = !url.isNullOrBlank(),
                onClick = {
                    if (url.isNullOrBlank()) return@ExploreKindItem
                    if (kind.title.startsWith("ERROR:")) {
                        activity?.showDialogFragment(TextDialog("ERROR", url))
                    } else {
                        onOpenUrl(url)
                    }
                },
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = displayName
            )
        }

        ExploreKind.Type.button -> {
            ExploreKindItem(
                kind = kind,
                isClickable = !kind.action.isNullOrBlank(),
                onClick = { runAction(kind.action) },
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = displayName,
                trailingIcon = {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        AppIcon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = LegadoTheme.colorScheme.outlineVariant
                        )
                    }
                }
            )
        }

        ExploreKind.Type.text -> {
            var value by remember(sourceUrl, kind.title) {
                mutableStateOf(valueOverride ?: infoMap?.get(kind.title).orEmpty())
            }
            LaunchedEffect(valueOverride) {
                if (valueOverride != null) {
                    value = valueOverride
                }
            }
            var actionJob by remember(sourceUrl, kind.title) { mutableStateOf<Job?>(null) }
            ExploreKindCompactTextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    updateValue(newValue)
                    if (!kind.action.isNullOrBlank()) {
                        actionJob?.cancel()
                        actionJob = scope.launch {
                            delay(600)
                            runAction(kind.action)
                        }
                    }
                },
                placeholder = displayName,
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix
            )
        }

        ExploreKind.Type.toggle -> {
            val chars = remember(kind.chars) {
                kind.chars?.filterNotNull().takeUnless { it.isNullOrEmpty() } ?: listOf("chars", "is null")
            }
            val left = kind.style().layout_justifySelf != "right"
            var char by remember(sourceUrl, kind.title, kind.default, kind.chars) {
                mutableStateOf(
                    valueOverride
                        ?: infoMap?.get(kind.title)
                        ?.takeUnless { it.isEmpty() }
                        ?: (kind.default ?: chars.first()).also {
                            infoMap?.let { map ->
                                map[kind.title] = it
                                map.saveNow()
                            }
                        }
                )
            }
            LaunchedEffect(valueOverride) {
                if (valueOverride != null) {
                    char = valueOverride
                }
            }
            val text = if (left) "$char$displayName" else "$displayName$char"
            ExploreKindItem(
                kind = kind,
                isClickable = true,
                onClick = {
                    val currentIndex = chars.indexOf(char)
                    val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % chars.size
                    char = chars.getOrElse(nextIndex) { "" }
                    updateValue(char)
                    runAction(kind.action)
                },
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = text,
                trailingIcon = {
                    AppIcon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = LegadoTheme.colorScheme.outlineVariant
                    )
                }
            )
        }

        ExploreKind.Type.select -> {
            val chars = remember(kind.chars) {
                kind.chars?.filterNotNull().takeUnless { it.isNullOrEmpty() } ?: listOf("chars", "is null")
            }
            var selected by remember(sourceUrl, kind.title, kind.default, kind.chars) {
                mutableStateOf(
                    valueOverride
                        ?: infoMap?.get(kind.title)
                        ?.takeUnless { it.isEmpty() }
                        ?: (kind.default ?: chars.first()).also {
                            infoMap?.let { map ->
                                map[kind.title] = it
                                map.saveNow()
                            }
                        }
                )
            }
            LaunchedEffect(valueOverride) {
                if (valueOverride != null) {
                    selected = valueOverride
                }
            }
            var showSelector by remember(sourceUrl, kind.title) { mutableStateOf(false) }
            Box(modifier = modifier) {
                ExploreKindItem(
                    kind = kind,
                    isClickable = chars.isNotEmpty(),
                    onClick = { showSelector = true },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = backgroundColor,
                    isMiuix = isMiuix,
                    displayText = "$displayName $selected",
                    trailingIcon = {
                        AppIcon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = LegadoTheme.colorScheme.outlineVariant
                        )
                    }
                )
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
                                    updateValue(option)
                                    runAction(kind.action)
                                }
                            }
                        )
                    }
                }
            }
        }

        else -> {
            ExploreKindItem(
                kind = kind,
                isClickable = false,
                onClick = {},
                modifier = modifier,
                backgroundColor = backgroundColor,
                isMiuix = isMiuix,
                displayText = displayName
            )
        }
    }
}

@Composable
private fun ExploreKindCompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = LegadoTheme.colorScheme.surfaceContainer,
    isMiuix: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (isFocused) {
        LegadoTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = LegadoTheme.typography.bodySmall.copy(color = LegadoTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(LegadoTheme.colorScheme.primary),
        interactionSource = interactionSource,
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = androidx.compose.ui.Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    AppText(
                        text = placeholder,
                        color = LegadoTheme.colorScheme.outline,
                        style = LegadoTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    innerTextField()
                }
            }
        }
    )
}
