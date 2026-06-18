package io.legado.app.ui.ai

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.legado.app.help.ai.AiProvider
import io.legado.app.help.ai.GeneratedImage
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImageScreen(
    state: AiImageUiState,
    onIntent: (AiImageIntent) -> Unit,
    effects: Flow<AiImageEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiImageEffect.ShowToast -> context.toastOnUi(eff.message ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("图像生成") },
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
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { onIntent(AiImageIntent.UpdatePrompt(it)) },
                placeholder = { Text("例如：一只橘色猫咪坐在窗台上，夕阳西下") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("dall-e-3", "dall-e-2", "stable-diffusion-xl", "imagen-3", "sdxl-turbo").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiImageIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
                var sizeExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { sizeExpanded = true }, label = { Text(state.size) })
                    DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                        listOf("1024x1024", "1792x1024", "1024x1792", "512x512").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = {
                                onIntent(AiImageIntent.UpdateSize(s))
                                sizeExpanded = false
                            })
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数量: ${state.count}", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.count.toFloat(),
                    onValueChange = { onIntent(AiImageIntent.UpdateCount(it.toInt())) },
                    valueRange = 1f..4f,
                    steps = 2,
                    modifier = Modifier.width(120.dp)
                )
                var qExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { qExpanded = true }, label = { Text(state.quality) })
                    DropdownMenu(expanded = qExpanded, onDismissRequest = { qExpanded = false }) {
                        listOf("standard", "hd").forEach { q ->
                            DropdownMenuItem(text = { Text(q) }, onClick = {
                                onIntent(AiImageIntent.UpdateQuality(q))
                                qExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row {
                TextButton(onClick = { onIntent(AiImageIntent.Generate) }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成图像")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onIntent(AiImageIntent.ClearAll) }) {
                    Text("清空")
                }
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

            if (state.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("生成结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.images, key = { it.url?.hashCode() ?: it.prompt.hashCode() }) { img ->
                        ImageResultCard(img, onSave = { onIntent(AiImageIntent.SaveImage) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageResultCard(
    img: GeneratedImage,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (img.bitmap != null) {
                Image(
                    bitmap = img.bitmap.asImageBitmap(),
                    contentDescription = img.prompt,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = img.url?.let { "图像URL: $it" } ?: "（无图像数据）",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = img.prompt.take(60) + if (img.prompt.length > 60) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            img.revisedPrompt?.let {
                Text(it.take(80), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVideoScreen(
    state: AiVideoUiState,
    onIntent: (AiVideoIntent) -> Unit,
    effects: Flow<AiVideoEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiVideoEffect.ShowToast -> context.toastOnUi(eff.message ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视频生成") },
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
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { onIntent(AiVideoIntent.UpdatePrompt(it)) },
                placeholder = { Text("描述视频画面与动作...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("sora", "runway-gen3", "vidu", "kling", "kling-1.6").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiVideoIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
                var ratioExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { ratioExpanded = true }, label = { Text(state.aspectRatio) })
                    DropdownMenu(expanded = ratioExpanded, onDismissRequest = { ratioExpanded = false }) {
                        listOf("16:9", "9:16", "1:1", "4:3", "3:4").forEach { r ->
                            DropdownMenuItem(text = { Text(r) }, onClick = {
                                onIntent(AiVideoIntent.UpdateAspectRatio(r))
                                ratioExpanded = false
                            })
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("时长: ${state.duration}秒", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = state.duration.toFloat(),
                    onValueChange = { onIntent(AiVideoIntent.UpdateDuration(it.toInt())) },
                    valueRange = 1f..60f,
                    steps = 58
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = { onIntent(AiVideoIntent.Generate) }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("生成视频")
                }
            }

            if (state.isLoading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "视频生成可能需要数分钟...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.error?.let { err ->
                Text(
                    "⚠ $err",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            state.videoUrl?.let { url ->
                Spacer(modifier = Modifier.height(14.dp))
                Text("生成完成", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = url,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiVisionScreen(
    state: AiVisionUiState,
    onIntent: (AiVisionIntent) -> Unit,
    effects: Flow<AiVisionEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { u ->
                runCatching {
                    val stream = context.contentResolver.openInputStream(u)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    stream?.close()
                    val out = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                    val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    onIntent(AiVisionIntent.UpdateImageBase64(base64))
                }
            }
        }
    )

    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiVisionEffect.ShowToast -> context.toastOnUi(eff.message ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视觉分析") },
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
                    if (state.imageBase64 != null) {
                        val b64 = state.imageBase64
                        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            "点击下方按钮选择图片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { picker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择图片")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = state.prompt,
                onValueChange = { onIntent(AiVisionIntent.UpdatePrompt(it)) },
                placeholder = { Text("描述你想让 AI 做什么，例如：\n请描述图中内容\n这张图有什么问题？\n...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("gpt-4o", "gpt-4o-mini", "claude-3-5-sonnet", "gemini-2.0-flash", "qwen-vl-max").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiVisionIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onIntent(AiVisionIntent.Analyze) }) {
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

            state.result?.let { res ->
                Spacer(modifier = Modifier.height(14.dp))
                Text("AI 分析结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = res,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTextToolsScreen(
    state: AiTextToolsUiState,
    onIntent: (AiTextToolsIntent) -> Unit,
    effects: Flow<AiTextToolsEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiTextToolsEffect.ShowToast -> context.toastOnUi(eff.message ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文本工具箱") },
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
            Text("选择工具", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.tools, key = { it.id }) { tool ->
                    FilterChip(
                        selected = state.selectedTool?.id == tool.id,
                        onClick = { onIntent(AiTextToolsIntent.SelectTool(tool)) },
                        label = { Text(tool.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("模型", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var modelExpanded by remember { mutableStateOf(false) }
                Box {
                    AssistChip(onClick = { modelExpanded = true }, label = { Text(state.model.ifEmpty { "默认模型" }) })
                    DropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        listOf("gpt-4o-mini", "gpt-4o", "claude-3-5-haiku", "claude-3-5-sonnet", "gemini-2.0-flash", "qwen2.5-72b-instruct").forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = {
                                onIntent(AiTextToolsIntent.UpdateModel(m))
                                modelExpanded = false
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = state.input,
                onValueChange = { onIntent(AiTextToolsIntent.UpdateInput(it)) },
                placeholder = { Text(state.selectedTool?.inputHint ?: "输入要处理的文本...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = { onIntent(AiTextToolsIntent.Execute) }) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(state.selectedTool?.actionName ?: "处理")
                }
                if (state.output.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onIntent(AiTextToolsIntent.CopyOutput) }) {
                        Text("复制")
                    }
                }
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

            if (state.output.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("处理结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.output,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
