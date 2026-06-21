package io.legado.app.ui.config

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.main.ai.AiModelConfig
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.LegadoMiuixPalette
import io.legado.app.ui.widget.compose.LegadoMiuixSwitch
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette

// ── Title Bar ────────────────────────────────────────────────────────────────

@Composable
fun AppDialogTitleBar(title: String) {
    val style = rememberAppDialogStyle()
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = style.bodyFontFamily)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = style.primaryText,
                fontFamily = style.titleFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
    }
}

// ── Tab Bar ──────────────────────────────────────────────────────────────────

@Composable
fun AiProviderTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val style = rememberAppDialogStyle()
    val trackShape = RoundedCornerShape(style.panelRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(trackShape)
            .background(style.fieldSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val tabShape = RoundedCornerShape(style.actionRadius)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(tabShape)
                    .background(if (isSelected) style.surface else style.fieldSurface)
                    .clickable { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) style.accent else style.primaryText,
                    fontSize = 14.sp,
                    fontFamily = style.bodyFontFamily,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Provider Config Tab ──────────────────────────────────────────────────────

@Composable
fun AiProviderConfigTab(
    name: String,
    onNameChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    apiModeLabel: String,
    onApiModeClick: () -> Unit,
    promptCache: Boolean,
    onPromptCacheChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val style = rememberAppDialogStyle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiProviderTextField(
            value = name,
            onValueChange = onNameChange,
            label = stringResource(R.string.ai_provider_name),
            singleLine = true,
            keyboardType = KeyboardType.Text,
            style = style
        )
        AiProviderTextField(
            value = baseUrl,
            onValueChange = onBaseUrlChange,
            label = stringResource(R.string.ai_base_url),
            singleLine = true,
            keyboardType = KeyboardType.Uri,
            style = style
        )
        AiProviderTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = stringResource(R.string.ai_api_key),
            singleLine = true,
            isPassword = true,
            style = style
        )
        // API mode selector
        LegadoMiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onApiModeClick),
            color = style.fieldSurface,
            contentColor = style.primaryText,
            cornerRadius = style.actionRadius,
            insidePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = apiModeLabel,
                color = style.primaryText,
                fontSize = 14.sp,
                fontFamily = style.bodyFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // Prompt cache switch
        LegadoMiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPromptCacheChange(!promptCache) },
            color = style.fieldSurface,
            contentColor = style.primaryText,
            cornerRadius = style.actionRadius,
            insidePadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp)
        ) {
            val palette = style.toMiuixPalette()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ai_enable_prompt_cache_key),
                    modifier = Modifier.weight(1f),
                    color = if (promptCache) style.primaryText else style.secondaryText,
                    fontSize = 14.sp,
                    fontFamily = style.bodyFontFamily,
                    fontWeight = if (promptCache) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(10.dp))
                LegadoMiuixSwitch(
                    checked = promptCache,
                    onCheckedChange = onPromptCacheChange,
                    palette = palette
                )
            }
        }
        // Custom headers
        AiProviderTextField(
            value = headers,
            onValueChange = onHeadersChange,
            label = stringResource(R.string.ai_custom_headers),
            singleLine = false,
            minLines = 4,
            style = style
        )
    }
}

@Composable
private fun AiProviderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    style: AppDialogStyle,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = style.secondaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else minLines + 4,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(style.actionRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = style.primaryText,
                unfocusedTextColor = style.primaryText,
                disabledTextColor = style.secondaryText,
                focusedContainerColor = style.fieldSurface,
                unfocusedContainerColor = style.fieldSurface,
                disabledContainerColor = style.fieldSurface.copy(alpha = 0.58f),
                cursorColor = style.accent,
                focusedBorderColor = style.accent.copy(alpha = 0.55f),
                unfocusedBorderColor = style.stroke,
                disabledBorderColor = style.stroke.copy(alpha = 0.38f),
                focusedPlaceholderColor = style.secondaryText,
                unfocusedPlaceholderColor = style.secondaryText
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = style.primaryText,
                fontFamily = style.bodyFontFamily
            )
        )
    }
}

// ── Model Manage Tab ─────────────────────────────────────────────────────────

@Composable
fun AiModelManageTab(
    models: List<AiModelConfig>,
    currentModelId: String?,
    providerName: String,
    onModelClick: (AiModelConfig) -> Unit,
    onAddModel: () -> Unit,
    onFetchModels: () -> Unit
) {
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        // Summary
        Text(
            text = providerName,
            color = style.secondaryText,
            fontSize = 13.sp,
            fontFamily = style.bodyFontFamily,
            modifier = Modifier.padding(start = 18.dp, top = 10.dp, end = 18.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Model list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(models, key = { it.id }) { model ->
                AiModelItem(
                    model = model,
                    isCurrent = model.id == currentModelId,
                    style = style,
                    onClick = { onModelClick(model) }
                )
            }
        }
        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LegadoMiuixActionButton(
                text = stringResource(R.string.ai_add_model_manual),
                palette = palette,
                onClick = onAddModel,
                modifier = Modifier.weight(1f),
                cornerRadius = style.actionRadius
            )
            LegadoMiuixActionButton(
                text = stringResource(R.string.ai_fetch_models),
                palette = palette,
                onClick = onFetchModels,
                modifier = Modifier.weight(1f),
                cornerRadius = style.actionRadius
            )
        }
    }
}

@Composable
private fun AiModelItem(
    model: AiModelConfig,
    isCurrent: Boolean,
    style: AppDialogStyle,
    onClick: () -> Unit
) {
    LegadoMiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = style.fieldSurface,
        contentColor = style.primaryText,
        cornerRadius = style.actionRadius,
        insidePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.modelId,
                    color = style.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = style.titleFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.ai_current_model),
                        color = style.accent,
                        fontSize = 12.sp,
                        fontFamily = style.bodyFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            LegadoMiuixActionButton(
                text = stringResource(R.string.more),
                palette = style.toMiuixPalette(),
                onClick = onClick,
                cornerRadius = style.actionRadius,
                minWidth = 56.dp,
                minHeight = 34.dp,
                insidePadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ── Fetched Model Selector Dialog (Compose) ──────────────────────────────────

@Composable
fun FetchedModelSelectorContent(
    modelIds: List<String>,
    existingIds: Set<String>,
    onAddSingle: (String) -> Unit,
    onAddSelected: (List<String>) -> Unit,
    onAddAll: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val filteredModels = remember(modelIds, searchQuery) {
        if (searchQuery.isBlank()) modelIds
        else modelIds.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    fun toggleSelection(id: String) {
        selectionMode = true
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    val summaryText = if (selectionMode) {
        stringResource(R.string.ai_fetch_models_selected_hint, selectedIds.size)
    } else {
        stringResource(R.string.ai_fetch_models_long_press_hint)
    }

    AppDialogFrame(
        title = stringResource(R.string.ai_add_model_from_list),
        scrollContent = false,
        content = {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.screen_find),
                        color = style.secondaryText,
                        fontSize = 14.sp
                    )
                },
                shape = RoundedCornerShape(style.actionRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = style.primaryText,
                    unfocusedTextColor = style.primaryText,
                    focusedContainerColor = style.fieldSurface,
                    unfocusedContainerColor = style.fieldSurface,
                    cursorColor = style.accent,
                    focusedBorderColor = style.accent.copy(alpha = 0.55f),
                    unfocusedBorderColor = style.stroke
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = style.primaryText,
                    fontFamily = style.bodyFontFamily
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Summary
            Text(
                text = summaryText,
                color = style.secondaryText,
                fontSize = 13.sp,
                fontFamily = style.bodyFontFamily,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Model list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredModels, key = { it }) { modelId ->
                    val isExisting = modelId in existingIds
                    val isSelected = modelId in selectedIds
                    FetchedModelRow(
                        modelId = modelId,
                        existing = isExisting,
                        selected = isSelected,
                        selectionMode = selectionMode,
                        style = style,
                        onClick = {
                            if (selectionMode) {
                                toggleSelection(modelId)
                            } else {
                                onDismiss()
                                onAddSingle(modelId)
                            }
                        },
                        onLongClick = { toggleSelection(modelId) }
                    )
                }
            }
        },
        actions = {
            // Add All button
            LegadoMiuixActionButton(
                text = stringResource(R.string.ai_add_all_models),
                palette = palette,
                onClick = {
                    onDismiss()
                    onAddAll(modelIds)
                },
                cornerRadius = style.actionRadius
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Add Selected button
            LegadoMiuixActionButton(
                text = if (selectedIds.isEmpty()) {
                    stringResource(R.string.ai_fetch_models_add_selected_empty)
                } else {
                    stringResource(R.string.ai_fetch_models_add_selected, selectedIds.size)
                },
                palette = palette,
                onClick = {
                    if (selectedIds.isNotEmpty()) {
                        onDismiss()
                        onAddSelected(selectedIds.toList())
                    }
                },
                primary = selectedIds.isNotEmpty(),
                cornerRadius = style.actionRadius
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FetchedModelRow(
    modelId: String,
    existing: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    style: AppDialogStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val rowColor = if (selected) style.fieldSurface else style.surface
    LegadoMiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = rowColor,
        contentColor = style.primaryText,
        cornerRadius = style.actionRadius,
        insidePadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = style.accent,
                        uncheckedColor = style.secondaryText,
                        checkmarkColor = style.surface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelId,
                    color = style.primaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = style.bodyFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        existing -> stringResource(R.string.ai_fetch_models_existing)
                        selectionMode -> stringResource(R.string.ai_fetch_models_click_toggle)
                        else -> stringResource(R.string.ai_fetch_models_click_add_long_select)
                    },
                    color = if (existing) style.accent else style.secondaryText,
                    fontSize = 12.sp,
                    fontFamily = style.bodyFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
