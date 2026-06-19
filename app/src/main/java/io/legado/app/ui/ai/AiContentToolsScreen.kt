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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiContentToolsScreen(
    state: AiContentToolsUiState,
    onIntent: (AiContentToolsIntent) -> Unit,
    effects: Flow<AiContentToolsEffect>,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        effects.collect { eff ->
            when (eff) {
                is AiContentToolsEffect.ShowToast -> context.toastOnUi(eff.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 内容工具") },
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
                        onClick = { onIntent(AiContentToolsIntent.ChangeTab(idx)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 工具特定输入
            when (state.tab) {
                0 -> {
                    Row {
                        listOf("中文", "英文", "日文", "繁体").forEach { lang ->
                            FilterChip(
                                selected = state.targetLanguage == lang,
                                onClick = { onIntent(AiContentToolsIntent.UpdateTargetLanguage(lang)) },
                                label = { Text(lang) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
                1 -> {
                    Row {
                        listOf("简洁", "标准", "详细").forEach { level ->
                            FilterChip(
                                selected = state.summaryLevel == level,
                                onClick = { onIntent(AiContentToolsIntent.UpdateSummaryLevel(level)) },
                                label = { Text(level) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
                3 -> {
                    Row {
                        listOf("文学化", "口语化", "正式化", "古风").forEach { style ->
                            FilterChip(
                                selected = state.rewriteStyle == style,
                                onClick = { onIntent(AiContentToolsIntent.UpdateRewriteStyle(style)) },
                                label = { Text(style) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.sourceBookName,
                onValueChange = { onIntent(AiContentToolsIntent.UpdateBookName(it)) },
                placeholder = { Text("书名（可选）") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.sourceChapter,
                onValueChange = { onIntent(AiContentToolsIntent.UpdateChapter(it)) },
                placeholder = { Text("章节标题（可选）") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.input,
                onValueChange = { onIntent(AiContentToolsIntent.UpdateInput(it)) },
                placeholder = { Text("在此粘贴要处理的文本……") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row {
                TextButton(onClick = { onIntent(AiContentToolsIntent.Execute) }) {
                    Text("执行")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                if (state.output.isNotBlank()) {
                    TextButton(onClick = { onIntent(AiContentToolsIntent.CopyOutput) }) {
                        Text("复制结果")
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
                Text("处理结果", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.output,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
