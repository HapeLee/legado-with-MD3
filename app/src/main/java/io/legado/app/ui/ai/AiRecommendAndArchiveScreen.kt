package io.legado.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.utils.toastOnUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiRecommendScreen(
    viewModel: AiRecommendViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { eff ->
            when (eff) {
                is AiRecommendEffect.ShowToast -> context.toastOnUi(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 推荐 & 书单") },
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
                        onClick = { viewModel.onIntent(AiRecommendIntent.ChangeTab(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val placeholder = when (state.tab) {
                0 -> "我最近喜欢《三体》《基地》，推荐类似科幻小说"
                1 -> "描述你的阅读习惯，或直接生成目标"
                2 -> "例如：下雨天想读的治愈系小说"
                3 -> "例如：深夜读科幻想配的 BGM 歌单"
                else -> "在此输入"
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = { viewModel.onIntent(AiRecommendIntent.UpdateQuery(it)) },
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = {
                when (state.tab) {
                    1 -> viewModel.onIntent(AiRecommendIntent.CoachReport)
                    3 -> viewModel.onIntent(AiRecommendIntent.VibeReport)
                    else -> viewModel.onIntent(AiRecommendIntent.Generate)
                }
            }) {
                Text("生成")
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

            if (state.report.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("AI 结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.report,
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
fun AiArchiveScreen(
    viewModel: AiArchiveViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { eff ->
            when (eff) {
                is AiArchiveEffect.ShowToast -> context.toastOnUi(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 归档工具") },
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
                        onClick = { viewModel.onIntent(AiArchiveIntent.ChangeTab(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (state.tab == 0) {
                OutlinedTextField(
                    value = state.inputDesc,
                    onValueChange = { viewModel.onIntent(AiArchiveIntent.UpdateInputDesc(it)) },
                    placeholder = { Text("目标：想要替换的文本模式（例如：去除章节中的广告）") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.sampleText,
                    onValueChange = { viewModel.onIntent(AiArchiveIntent.UpdateSampleText(it)) },
                    placeholder = { Text("样本文本（可选）") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.onIntent(AiArchiveIntent.GenerateReplaceRule) }) {
                    Text("生成替换规则")
                }
            } else {
                OutlinedTextField(
                    value = state.inputDesc,
                    onValueChange = { viewModel.onIntent(AiArchiveIntent.UpdateInputDesc(it)) },
                    placeholder = { Text("描述你的目标（例如：把我的 epub 按分类归档）") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.filePathPattern,
                    onValueChange = { viewModel.onIntent(AiArchiveIntent.UpdateFilePathPattern(it)) },
                    placeholder = { Text("样例路径 / 文件名列表") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.tab == 1) {
                    TextButton(onClick = { viewModel.onIntent(AiArchiveIntent.GenerateArchivePlan) }) {
                        Text("生成归档方案")
                    }
                } else {
                    TextButton(onClick = { viewModel.onIntent(AiArchiveIntent.GenerateRenamePlan) }) {
                        Text("生成重命名方案")
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

            if (state.output.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("AI 方案", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.output,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { viewModel.onIntent(AiArchiveIntent.CopyOutput) }) {
                    Text("复制")
                }
            }
        }
    }
}
