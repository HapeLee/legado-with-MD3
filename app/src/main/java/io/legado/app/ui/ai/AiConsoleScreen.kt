package io.legado.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class AiConsoleEntry(
    val title: String,
    val description: String,
    val tag: String,
    val color: Long,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConsoleScreen(
    onOpenChat: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenVision: () -> Unit,
    onOpenTextTools: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenSourceAdvanced: () -> Unit,
    onOpenShelfAnalyze: () -> Unit,
    onOpenRecommend: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenContentTools: () -> Unit,
    onOpenArt: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit
) {
    val entries = listOf(
        AiConsoleEntry("AI 对话", "与大模型自由对话", "聊天", 0xFF3B82F6, onOpenChat),
        AiConsoleEntry("AI 图像生成", "封面 / 场景 / 海报", "图像", 0xFF8B5CF6, onOpenImage),
        AiConsoleEntry("AI 内容工具", "翻译、摘要、改写、检索", "文本", 0xFF10B981, onOpenContentTools),
        AiConsoleEntry("AI 封面 & 角色卡", "书籍封面 + 角色设定", "艺术", 0xFFF59E0B, onOpenArt),
        AiConsoleEntry("书源生成", "根据网站生成书源 JSON", "书源", 0xFFEC4899, onOpenSource),
        AiConsoleEntry("书源进阶", "搜索 / 评分 / 自动修源", "书源", 0xFF06B6D4, onOpenSourceAdvanced),
        AiConsoleEntry("书架分析", "分析你的书架数据 & 偏好", "书架", 0xFFEF4444, onOpenShelfAnalyze),
        AiConsoleEntry("书单 & 阅读教练", "个性化书单与节奏建议", "推荐", 0xFF84CC16, onOpenRecommend),
        AiConsoleEntry("归档 & 替换规则", "本地书归档、自动重命名", "工具", 0xFF6366F1, onOpenArchive),
        AiConsoleEntry("AI 设置", "API Key / 模型 / 供应商", "设置", 0xFF334155, onOpenSettings)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 能力中心") },
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
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "欢迎使用 AI 能力中心",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "集成聊天、图像生成、书源生成、书架分析等多种能力",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries) { entry ->
                    AiConsoleCard(entry = entry)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiConsoleCard(entry: AiConsoleEntry) {
    Card(
        onClick = entry.onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "#${entry.tag}",
                color = Color(entry.color.toInt()),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
