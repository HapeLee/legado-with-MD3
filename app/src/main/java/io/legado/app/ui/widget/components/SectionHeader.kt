package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SectionHeader(
    // 左侧主标题内容
    titleContent: @Composable () -> Unit,
    // 右侧或副标题内容
    detailContent: @Composable (() -> Unit)? = null,
    // 主要内容的排列方式
    verticalArrangement: Arrangement.Vertical = Arrangement.Center,
    // 定义水平对齐方式
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    // 根容器的 Padding
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    Surface(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = horizontalArrangement
        ) {
            titleContent()
            detailContent?.let {
                it()
            }
        }
    }
}