package io.legado.app.ui.book.read.sheet

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.help.GlideImageGetter
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.utils.setHtml
import org.koin.androidx.compose.koinViewModel

@Composable
fun DictSheet(
    show: Boolean,
    word: String,
    onDismissRequest: () -> Unit,
    viewModel: DictViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    var dictRules by remember { mutableStateOf<List<DictRule>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var htmlContent by remember { mutableStateOf("") }
    var emptyMessage by remember { mutableStateOf<String?>(null) }
    var glideImageGetter by remember { mutableStateOf<GlideImageGetter?>(null) }

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        viewModel.initData { rules ->
            dictRules = rules
            if (rules.isEmpty()) {
                emptyMessage = context.getString(R.string.empty)
                isLoading = false
            } else {
                // Auto-select first tab
                viewModel.dict(rules[0], word) { result ->
                    isLoading = false
                    if (result.isBlank()) {
                        emptyMessage = "没有查询到结果"
                    } else {
                        htmlContent = result
                        emptyMessage = null
                    }
                }
            }
        }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            glideImageGetter?.clear()
            glideImageGetter = null
            onDismissRequest()
        },
        title = word,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            // Tab row
            if (dictRules.size > 1) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceIn(
                        0,
                        (dictRules.size - 1).coerceAtLeast(0)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    edgePadding = 0.dp,
                ) {
                    dictRules.forEachIndexed { index, rule ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                isLoading = true
                                emptyMessage = null
                                viewModel.dict(rule, word) { result ->
                                    isLoading = false
                                    if (result.isBlank()) {
                                        emptyMessage = "没有查询到结果"
                                    } else {
                                        htmlContent = result
                                        emptyMessage = null
                                    }
                                }
                            },
                            text = { Text(rule.name) },
                        )
                    }
                }
            }

            // Content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                emptyMessage != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                movementMethod = LinkMovementMethod()
                                setPadding(32, 16, 32, 16)
                            }
                        },
                        update = { textView ->
                            textView.setHtml(htmlContent)
                            glideImageGetter?.clear()
                            glideImageGetter =
                                GlideImageGetter.create(context, textView, htmlContent)
                            textView.setHtml(htmlContent, glideImageGetter)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
,
                    )
                }
            }
        }
    }
}
