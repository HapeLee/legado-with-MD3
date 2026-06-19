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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

data class AiConsoleItem(
    val title: String,
    val subtitle: String,
    val icon: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConsoleScreen(
    onBack: () -> Unit,
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
    onOpenSettings: () -> Unit
) {
    val items = listOf(
        AiConsoleItem("AI 聊天", "智能对话、工具调用", "💬") { onOpenChat() },
        AiConsoleItem("图片生成", "AI 生成图片/封面/头像", "🎨") { onOpenImage() },
        AiConsoleItem("视频生成", "AI 生成视频内容", "🎬") { onOpenVideo() },
        AiConsoleItem("图像理解", "图片内容识别和分析", "👁️") { onOpenVision() },
        AiConsoleItem("文本工具", "润色、翻译、总结、校对", "📝") { onOpenTextTools() },
        AiConsoleItem("书源生成", "AI 辅助生成书源规则", "📚") { onOpenSource() },
        AiConsoleItem("高级书源", "书源调试、规则优化", "🔧") { onOpenSourceAdvanced() },
        AiConsoleItem("书架分析", "阅读记录、分类统计", "📊") { onOpenShelfAnalyze() },
        AiConsoleItem("书籍推荐", "根据阅读偏好推荐", "💡") { onOpenRecommend() },
        AiConsoleItem("内容工具箱", "内容处理工具集", "🧰") { onOpenContentTools() },
        AiConsoleItem("AI 画廊", "生成的图片作品集", "🖼️") { onOpenArt() },
        AiConsoleItem("对话归档", "历史对话记录", "📋") { onOpenArchive() },
        AiConsoleItem("AI 设置", "供应商、模型、Key 配置", "⚙️") { onOpenSettings() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 控制台") },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "智能阅读助手",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "集聊天、书源、图片、书架分析于一体的 AI 助手。配置 API Key 后即可使用全部功能。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items) { item ->
                    FeatureCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(item: AiConsoleItem) {
    Card(
        onClick = item.onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(verticalArrangement = Arrangement.SpaceBetween) {
                Text(text = item.icon, fontSize = 28.sp)
                Column {
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
