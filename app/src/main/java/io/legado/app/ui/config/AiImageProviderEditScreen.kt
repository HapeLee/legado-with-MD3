package io.legado.app.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.main.ai.AiImageProviderConfig
import io.legado.app.ui.widget.compose.LegadoMiuixActionButton
import io.legado.app.ui.widget.compose.LegadoMiuixCard
import io.legado.app.ui.widget.compose.rememberAppDialogStyle
import io.legado.app.ui.widget.compose.toMiuixPalette

@Composable
internal fun AiImageProviderEditScreen(
    name: String,
    onNameChange: (String) -> Unit,
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    model: String,
    onModelChange: (String) -> Unit,
    headers: String,
    onHeadersChange: (String) -> Unit,
    timeout: String,
    onTimeoutChange: (String) -> Unit,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    providerType: String,
    isOpenAi: Boolean,
    onTypeClick: () -> Unit,
    stylePromptSummary: String,
    onStylePromptClick: () -> Unit,
    paramsSummary: String,
    onParamsClick: () -> Unit,
    scriptSummary: String,
    onScriptClick: () -> Unit,
    jsLibSummary: String,
    onJsLibClick: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val style = rememberAppDialogStyle()
    val palette = style.toMiuixPalette()
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = style.bodyFontFamily)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(context.backgroundColor),
            contentColor = style.primaryText
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                AiImageProviderEditTopBar(onBack = onBack)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Type selector
                    LegadoMiuixCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onTypeClick),
                        color = style.fieldSurface,
                        contentColor = style.primaryText,
                        cornerRadius = style.actionRadius,
                        insidePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.ai_image_provider_type)}: " +
                                stringResource(
                                    if (isOpenAi) R.string.ai_image_provider_openai
                                    else R.string.ai_image_provider_js
                                ),
                            color = style.primaryText,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Name
                    EditTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = stringResource(R.string.name)
                    )

                    // OpenAI group
                    if (isOpenAi) {
                        EditTextField(
                            value = baseUrl,
                            onValueChange = onBaseUrlChange,
                            label = stringResource(R.string.ai_base_url),
                            keyboardType = KeyboardType.Uri
                        )
                        EditTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                            label = stringResource(R.string.ai_api_key),
                            isPassword = true
                        )
                        EditTextField(
                            value = model,
                            onValueChange = onModelChange,
                            label = stringResource(R.string.ai_model)
                        )
                        EditTextField(
                            value = headers,
                            onValueChange = onHeadersChange,
                            label = stringResource(R.string.ai_custom_headers),
                            minLines = 3,
                            maxLines = 5
                        )
                    }

                    // Timeout
                    EditTextField(
                        value = timeout,
                        onValueChange = onTimeoutChange,
                        label = stringResource(R.string.timeout_millisecond),
                        keyboardType = KeyboardType.Number
                    )

                    // Enabled checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEnabledChange(!enabled) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enabled,
                            onCheckedChange = onEnabledChange,
                            colors = CheckboxDefaults.colors(
                                checkedColor = palette.accent,
                                uncheckedColor = palette.secondaryText,
                                checkmarkColor = palette.surface
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.enable),
                            color = style.primaryText,
                            fontSize = 14.sp
                        )
                    }

                    // Style prompt button
                    EditCodeButton(
                        label = stylePromptSummary,
                        onClick = onStylePromptClick
                    )

                    // Params button (OpenAI only)
                    if (isOpenAi) {
                        EditCodeButton(
                            label = paramsSummary,
                            onClick = onParamsClick
                        )
                    }

                    // Script button (JS only)
                    if (!isOpenAi) {
                        EditCodeButton(
                            label = scriptSummary,
                            onClick = onScriptClick
                        )
                    }

                    // jsLib button (JS only)
                    if (!isOpenAi) {
                        EditCodeButton(
                            label = jsLibSummary,
                            onClick = onJsLibClick
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Save button
                LegadoMiuixActionButton(
                    text = stringResource(R.string.action_save),
                    palette = palette,
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    primary = true,
                    cornerRadius = style.actionRadius,
                    minHeight = 46.dp
                )
            }
        }
    }
}

@Composable
private fun AiImageProviderEditTopBar(onBack: () -> Unit) {
    val style = rememberAppDialogStyle()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onBack),
            shape = RoundedCornerShape(style.actionRadius),
            color = Color.Transparent,
            contentColor = style.primaryText,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = style.primaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.ai_image_provider_manage),
            color = style.primaryText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = style.titleFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(42.dp))
    }
}

@Composable
private fun EditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    val style = rememberAppDialogStyle()
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
            singleLine = maxLines == 1,
            minLines = minLines,
            maxLines = maxLines,
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
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                color = style.primaryText,
                fontFamily = style.bodyFontFamily
            )
        )
    }
}

@Composable
private fun EditCodeButton(
    label: String,
    onClick: () -> Unit
) {
    val style = rememberAppDialogStyle()
    LegadoMiuixCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = style.fieldSurface,
        contentColor = style.primaryText,
        cornerRadius = style.actionRadius,
        insidePadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            color = style.primaryText,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
