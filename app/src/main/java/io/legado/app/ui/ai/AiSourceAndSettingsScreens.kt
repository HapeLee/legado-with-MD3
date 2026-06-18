package io.legado.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSourceScreen(
    state: AiSourceUiState,
    onIntent: (AiSourceIntent) -> Unit,
    effects: Flow<AiSourceEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiSourceEffect.ShowToast -> context.toastOnUi(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 书源助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.tab == 0,
                    onClick = { onIntent(AiSourceIntent.ChangeTab(0)) },
                    label = { Text("生成书源") }
                )
                FilterChip(
                    selected = state.tab == 1,
                    onClick = { onIntent(AiSourceIntent.ChangeTab(1)) },
                    label = { Text("校验书源") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-sonnet", "gemini-2.0-flash", "qwen2.5-72b-instruct").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiSourceIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (state.tab == 0) {
                OutlinedTextField(
                    value = state.siteUrl,
                    onValueChange = { onIntent(AiSourceIntent.UpdateSiteUrl(it)) },
                    placeholder = { Text("目标网站 URL（如 https://www.example.com）") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.description,
                    onValueChange = { onIntent(AiSourceIntent.UpdateDescription(it)) },
                    placeholder = { Text("描述信息：目标是小说/漫画/音频？书源主要特色？") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = { onIntent(AiSourceIntent.GenerateSource) }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成书源")
                }

                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.error?.let { err ->
                    Text(
                        "⚠ $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.generatedJson.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row {
                        Text("生成结果（请人工检查后导入）", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onIntent(AiSourceIntent.CopyGenerated) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.generatedJson,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { onIntent(AiSourceIntent.SaveGeneratedSource) }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入到书源列表")
                    }
                }
            } else {
                OutlinedTextField(
                    value = state.sourceToValidateJson,
                    onValueChange = { onIntent(AiSourceIntent.UpdateValidationJson(it)) },
                    placeholder = { Text("粘贴要校验的书源 JSON...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onIntent(AiSourceIntent.ValidateSource) }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("校验分析")
                }

                if (state.isLoading) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                state.error?.let { err ->
                    Text(
                        "⚠ $err",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (state.validationResult.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("AI 校验建议", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.validationResult,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiBookshelfScreen(
    state: AiBookshelfUiState,
    onIntent: (AiBookshelfIntent) -> Unit,
    effects: Flow<AiBookshelfEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiBookshelfEffect.ShowToast -> context.toastOnUi(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("书架总数：${state.bookCount} 本", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("书源分布（Top 10）:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    state.sourceStats.forEach { (k, v) ->
                        Text("  · $k: $v 本", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "阅读画像",
                    "阅读习惯",
                    "阅读风格",
                    "自定义"
                ).forEachIndexed { idx, label ->
                    FilterChip(
                        selected = state.mode == idx,
                        onClick = { onIntent(AiBookshelfIntent.ChangeMode(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (state.mode == 3) {
                OutlinedTextField(
                    value = state.customPrompt,
                    onValueChange = { onIntent(AiBookshelfIntent.UpdateCustomPrompt(it)) },
                    placeholder = { Text("自定义分析指令，例如：找出我最喜欢的主题并推荐同类型书籍...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-haiku", "gemini-2.0-flash", "qwen2.5-32b-instruct").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiBookshelfIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = { onIntent(AiBookshelfIntent.Analyze) }) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("开始分析")
            }

            if (state.isLoading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { err ->
                Text(
                    "⚠ $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (state.analysis.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("AI 分析结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.analysis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    state: AiSettingsUiState,
    onIntent: (AiSettingsIntent) -> Unit,
    effects: Flow<AiSettingsEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiSettingsEffect.ShowToast -> context.toastOnUi(eff.message ?: "")
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text("AI 提供商", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var providerExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(
                        onClick = { providerExpanded = true },
                        label = { Text(state.activeProvider.displayName) }
                    )
                    DropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                        io.legado.app.help.ai.AiProvider.values().forEach { p ->
                            DropdownMenuItem(text = { Text(p.displayName) }, onClick = {
                                onIntent(AiSettingsIntent.ChangeProvider(p))
                                providerExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("基础配置", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentEndpoint,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateEndpoint(it)) },
                        label = { Text("API 端点 (Endpoint)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentApiKey,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateApiKey(it)) },
                        label = { Text("API Key") },
                        trailingIcon = {
                            IconButton(onClick = {
                                onIntent(AiSettingsIntent.ToggleApiKeyVisibility(!state.showApiKey))
                            }) {
                                Text(if (state.showApiKey) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentChatModel,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateChatModel(it)) },
                        label = { Text("聊天模型") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentImageModel,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateImageModel(it)) },
                        label = { Text("图像生成模型") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentVideoModel,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateVideoModel(it)) },
                        label = { Text("视频生成模型") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentVisionModel,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateVisionModel(it)) },
                        label = { Text("视觉分析模型") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentTtsModel,
                        onValueChange = { onIntent(AiSettingsIntent.UpdateTtsModel(it)) },
                        label = { Text("TTS 模型") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Temperature: ${state.currentTemperature}", modifier = Modifier.width(180.dp))
                        Slider(
                            value = state.currentTemperature,
                            onValueChange = { onIntent(AiSettingsIntent.UpdateTemperature(it)) },
                            valueRange = 0f..2f
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.currentTimeout.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { sec ->
                                onIntent(AiSettingsIntent.UpdateTimeout(sec))
                            }
                        },
                        label = { Text("超时时间（秒）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row {
                TextButton(onClick = { onIntent(AiSettingsIntent.Save) }) {
                    Text("保存配置")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onIntent(AiSettingsIntent.ResetToDefaults) }) {
                    Text("重置为默认值")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onIntent(AiSettingsIntent.ExportConfig) }) {
                    Text("导出到剪贴板")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "提示：切换提供商时会自动保存当前提供商的配置。\n" +
                    "如遇自签名证书 / 内网 API，可在系统 CA 中添加相应证书，或使用兼容 OpenAI 协议的代理服务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
