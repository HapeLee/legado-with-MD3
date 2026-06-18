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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiArtScreen(
    viewModel: AiArtViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { eff ->
            when (eff) {
                is AiArtEffect.ShowToast -> toast(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 封面 & 角色卡") },
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.tabs.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = state.tab == idx,
                        onClick = { viewModel.onIntent(AiArtIntent.ChangeTab(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (state.tab) {
                0 -> {
                    OutlinedTextField(
                        value = state.bookName,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateBookName(it)) },
                        placeholder = { Text("书名") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.author,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateAuthor(it)) },
                        placeholder = { Text("作者（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.intro,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateIntro(it)) },
                        placeholder = { Text("简介 / 氛围描述") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                1 -> {
                    OutlinedTextField(
                        value = state.characterName,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateCharacterName(it)) },
                        placeholder = { Text("角色名") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.characterDesc,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateCharacterDesc(it)) },
                        placeholder = { Text("角色描述（外貌、性格、背景……）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                2, 3 -> {
                    OutlinedTextField(
                        value = state.sceneDesc,
                        onValueChange = { viewModel.onIntent(AiArtIntent.UpdateSceneDesc(it)) },
                        placeholder = { Text("场景描述 / 章节内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("512x512", "1024x1024", "1024x1792").forEach { s ->
                    FilterChip(
                        selected = state.size == s,
                        onClick = { viewModel.onIntent(AiArtIntent.UpdateSize(s)) },
                        label = { Text(s) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("中式古风", "赛博朋克", "奇幻", "写实", "水墨", "现代都市").forEach { s ->
                    FilterChip(
                        selected = state.styleHint == s,
                        onClick = { viewModel.onIntent(AiArtIntent.UpdateStyleHint(s)) },
                        label = { Text(s) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row {
                TextButton(onClick = { viewModel.onIntent(AiArtIntent.GenerateImage) }) {
                    Text("生成图像")
                }
                Spacer(modifier = Modifier.width(6.dp))
                if (state.tab == 1) {
                    TextButton(onClick = { viewModel.onIntent(AiArtIntent.GenerateCharacterCard) }) {
                        Text("生成角色卡片文本")
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

            if (state.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("生成结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                state.images.forEach { img ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (img.url != null) {
                                Text(
                                    text = "URL: ${img.url}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (img.revisedPrompt != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "提示词: ${img.revisedPrompt}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (img.base64 != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("(已返回 base64 图像数据)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            if (state.characterText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("角色卡片", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.characterText,
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
fun AiSourceAdvancedScreen(
    viewModel: AiSourceAdvancedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { eff ->
            when (eff) {
                is AiSourceAdvancedEffect.ShowToast -> toast(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 书源进阶") },
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
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.tabs.forEachIndexed { idx, label ->
                    FilterChip(
                        selected = state.tab == idx,
                        onClick = { viewModel.onIntent(AiSourceAdvancedIntent.ChangeTab(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (state.tab) {
                0 -> {
                    OutlinedTextField(
                        value = state.queryBookName,
                        onValueChange = { viewModel.onIntent(AiSourceAdvancedIntent.UpdateQueryBookName(it)) },
                        placeholder = { Text("书名") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.queryAuthor,
                        onValueChange = { viewModel.onIntent(AiSourceAdvancedIntent.UpdateQueryAuthor(it)) },
                        placeholder = { Text("作者（可选）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onIntent(AiSourceAdvancedIntent.SearchSources) }) {
                        Text("AI 搜索候选书源")
                    }
                    if (state.candidates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("候选列表", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        state.candidates.forEach { line ->
                            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                Text(
                                    text = line,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
                1 -> {
                    OutlinedTextField(
                        value = state.selectedSourceToValidate,
                        onValueChange = { viewModel.onIntent(AiSourceAdvancedIntent.UpdateSelectedSource(it)) },
                        placeholder = { Text("粘贴要校验的书源 JSON 或描述……") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onIntent(AiSourceAdvancedIntent.EvaluateQuality) }) {
                        Text("AI 质量评估")
                    }
                    if (state.qualityReport.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("评估结果", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.qualityReport,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = state.sourceUrlToFix,
                        onValueChange = { viewModel.onIntent(AiSourceAdvancedIntent.UpdateSourceUrlToFix(it)) },
                        placeholder = { Text("粘贴失效的书源 URL / JSON") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.onIntent(AiSourceAdvancedIntent.AutoFix) }) {
                        Text("AI 自动修源建议")
                    }
                    if (state.fixReport.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("修复建议", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.fixReport,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
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
        }
    }
}
