package io.legado.app.ui.book.read

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookAiChapterSummary
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.ai.AiChapterSummaryService
import io.legado.app.help.ai.AiTaskKeepAlive
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.ui.main.ai.AiMarkdownRender
import io.legado.app.ui.main.ai.compose.AiComposeStyle
import io.legado.app.ui.main.ai.compose.aiComposeStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.toastOnUi
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class ReadAiSummaryPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    data class SummaryUiState(
        val title: String = "",
        val modelLabel: String = "",
        val summary: String = "",
        val statusLines: List<String> = emptyList(),
        val requesting: Boolean = false,
        val cached: Boolean = false
    )

    private val composeView = ComposeView(context)
    private val markwon: Markwon by lazy { AiMarkdownRender.createMarkwon(context) }
    private var input: AiChapterSummaryService.SummaryInput? = null
    private var job: Job? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f

    private var uiState by mutableStateOf(SummaryUiState())

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        composeView.setContent {
            ReadAiSummaryContent(
                state = uiState,
                markwon = markwon,
                onTopDrag = ::handleDrag,
                onSelectModel = ::selectModel,
                onRefresh = { loadSummary(forceRefresh = true) },
                onStop = ::stop,
                onClose = ::close
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun attach(lifecycleOwner: LifecycleOwner) {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun open(book: Book, chapter: BookChapter, content: String, anchor: ReadAiFloatingPanel.Anchor? = null) {
        input = AiChapterSummaryService.SummaryInput(book, chapter, content)
        uiState = SummaryUiState(
            title = "${book.name.ifBlank { "当前书籍" }} - ${chapter.title.ifBlank { "当前章节" }}",
            modelLabel = currentModelLabel(),
            summary = "正在读取缓存...",
            requesting = false
        )
        animate().cancel()
        translationY = 0f
        if (visibility != VISIBLE) {
            alpha = 0f
            visibility = VISIBLE
        } else {
            visibility = VISIBLE
        }
        bringToFront()
        doOnLayoutCompat {
            if (anchor != null) placeNearAnchor(anchor)
            ensureInsideParent()
            if (alpha < 1f) {
                animate().alpha(1f).setDuration(160L).start()
            }
        }
        loadSummary(forceRefresh = false)
    }

    fun close() {
        visibility = GONE
    }

    private fun stop() {
        job?.cancel()
        job = null
        uiState = uiState.copy(requesting = false)
    }

    private fun loadSummary(forceRefresh: Boolean) {
        val currentInput = input ?: return
        if (job?.isActive == true) {
            context.toastOnUi("请等待当前总结完成")
            return
        }
        val keepAliveId = AiTaskKeepAlive.retain(
            title = "AI章节总结",
            content = currentInput.chapter.title,
            kind = AiTaskKeepAlive.KIND_SUMMARY
        )
        job = requestScope.launch {
            try {
                post {
                    uiState = uiState.copy(
                        requesting = true,
                        cached = false,
                        statusLines = if (forceRefresh) listOf("正在重新总结") else listOf("正在检查缓存"),
                        summary = if (forceRefresh) "正在请求 AI 总结..." else uiState.summary
                    )
                }
                val result = runCatching {
                    if (!forceRefresh) {
                        AiChapterSummaryService.cached(currentInput)?.let { cached ->
                            return@runCatching cached to true
                        }
                    }
                    withContext(IO) {
                        AiChapterSummaryService.summarize(
                            input = currentInput,
                            forceRefresh = forceRefresh,
                            onPartial = { partial ->
                                if (partial.isNotBlank()) {
                                    AiTaskKeepAlive.update(keepAliveId, content = partial)
                                    post { uiState = uiState.copy(summary = partial, cached = false) }
                                }
                            },
                            onStatus = { status ->
                                AiTaskKeepAlive.update(
                                    keepAliveId,
                                    progressText = status.optString("label")
                                        .ifBlank { status.optString("name") }
                                )
                                post { appendStatus(status) }
                            }
                        )
                    } to false
                }
                post {
                    val (summary, fromCache) = result.getOrElse { throwable ->
                        val text = if (throwable is CancellationException) {
                            "已停止总结"
                        } else {
                            "总结失败：${throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName}"
                        }
                        uiState = uiState.copy(
                            summary = text,
                            requesting = false,
                            cached = false
                        )
                        job = null
                        return@post
                    }
                    showSummary(summary, fromCache)
                    job = null
                }
            } finally {
                AiTaskKeepAlive.release(keepAliveId)
            }
        }
    }

    private fun appendStatus(status: JSONObject) {
        val label = status.optString("label").ifBlank {
            when (status.optString("kind")) {
                "tool" -> status.optString("name")
                "thinking" -> "思考中"
                else -> ""
            }
        }
        if (label.isBlank()) return
        val lines = (uiState.statusLines + label).takeLast(5).distinct()
        uiState = uiState.copy(statusLines = lines)
    }

    private fun showSummary(summary: BookAiChapterSummary, fromCache: Boolean) {
        uiState = uiState.copy(
            summary = summary.summary,
            requesting = false,
            cached = fromCache,
            modelLabel = currentModelLabel(),
            statusLines = if (fromCache) listOf("已读取缓存") else listOf("总结完成")
        )
    }

    private fun selectModel() {
        if (job?.isActive == true) {
            context.toastOnUi("请等待当前总结完成")
            return
        }
        val models = AppConfig.aiModelConfigList
        if (models.isEmpty()) {
            context.toastOnUi(R.string.ai_no_models)
            return
        }
        val providerNameMap = AppConfig.aiProviderList.associateBy({ it.id }, { it.name })
        context.selector(
            context.getString(R.string.ai_current_model),
            models.map { model ->
                providerNameMap[model.providerId]?.takeIf { it.isNotBlank() }
                    ?.let { "${model.modelId} - $it" }
                    ?: model.modelId
            }
        ) { _, _, index ->
            AppConfig.aiSummaryModelId = models[index].id
            uiState = uiState.copy(modelLabel = currentModelLabel())
        }
    }

    private fun currentModelLabel(): String {
        val model = AppConfig.aiSummaryModelConfig ?: return ""
        val providerName = AppConfig.aiProviderList.firstOrNull { it.id == model.providerId }
            ?.name
            ?.takeIf { it.isNotBlank() }
        return providerName?.let { "${model.modelId} - $it" } ?: model.modelId
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        val parentView = parent as? ViewGroup ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = x
                startY = y
                parentView.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                x = (startX + event.rawX - downRawX)
                    .coerceIn(0f, max(0, parentView.width - width).toFloat())
                y = (startY + event.rawY - downRawY)
                    .coerceIn(0f, max(0, parentView.height - height).toFloat())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                ensureInsideParent()
                parentView.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun ensureInsideParent() {
        val parentView = parent as? ViewGroup ?: return
        if (width <= 0 || height <= 0 || parentView.width <= 0 || parentView.height <= 0) return
        x = min(max(0f, x), max(0, parentView.width - width).toFloat())
        y = min(max(0f, y), max(0, parentView.height - height).toFloat())
    }

    private fun placeNearAnchor(anchor: ReadAiFloatingPanel.Anchor) {
        val parentView = parent as? ViewGroup ?: return
        if (width <= 0 || height <= 0 || parentView.width <= 0 || parentView.height <= 0) return
        val margin = 10.dpToPx()
        val preferredX = anchor.centerX - width / 2
        val maxX = (parentView.width - width - margin).coerceAtLeast(margin)
        x = preferredX.toFloat().coerceIn(margin.toFloat(), maxX.toFloat())
        val spaceAbove = anchor.topY - margin
        val spaceBelow = parentView.height - anchor.bottomY - margin
        y = if (spaceBelow >= height || spaceBelow >= spaceAbove) {
            (anchor.bottomY + margin).toFloat()
                .coerceAtMost((parentView.height - height - margin).toFloat())
        } else {
            (anchor.topY - height - margin).toFloat()
                .coerceAtLeast(margin.toFloat())
        }
    }

    private fun doOnLayoutCompat(action: () -> Unit) {
        if (isLaidOut) {
            action()
        } else {
            addOnLayoutChangeListener(object : OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    removeOnLayoutChangeListener(this)
                    action()
                }
            })
        }
    }

    companion object {
        private val requestScope = CoroutineScope(SupervisorJob() + IO)
    }
}

@Composable
private fun ReadAiSummaryContent(
    state: ReadAiSummaryPanel.SummaryUiState,
    markwon: Markwon,
    onTopDrag: (MotionEvent) -> Boolean,
    onSelectModel: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val style = aiComposeStyle(context)
    val panelShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, panelShape, clip = false)
            .clip(panelShape),
        shape = panelShape,
        color = style.colors.background,
        shadowElevation = 0.dp,
        border = BorderStroke(style.metrics.strokeWidth, style.colors.stroke)
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onSelectModel,
                    enabled = !state.requesting,
                    shape = RoundedCornerShape(style.metrics.chipRadius),
                    color = style.colors.accent.copy(alpha = if (state.requesting) 0.06f else 0.10f)
                ) {
                    Text(
                        text = state.modelLabel.ifBlank { "选择模型" },
                        color = if (state.requesting) style.colors.secondaryText else style.colors.accent,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 150.dp)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pointerInteropFilter(onTouchEvent = onTopDrag)
                )
                SummaryIconButton(R.drawable.ic_refresh_black_24dp, "重新总结", style, onRefresh)
                if (state.requesting) {
                    SummaryIconButton(R.drawable.ic_stop_black_24dp, "停止", style, onStop)
                }
                SummaryIconButton(R.drawable.ic_close_x, "关闭", style, onClose)
            }
            Text(
                text = state.title,
                color = style.colors.secondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SummaryBody(state, markwon, style)
        }
    }
}

@Composable
private fun SummaryIconButton(
    iconRes: Int,
    contentDescription: String,
    style: AiComposeStyle,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = style.colors.primaryText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SummaryBody(
    state: ReadAiSummaryPanel.SummaryUiState,
    markwon: Markwon,
    style: AiComposeStyle
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp, max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.requesting) {
            item("loading") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = style.colors.accent
                    )
                    Text(
                        text = "正在总结",
                        color = style.colors.secondaryText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        items(state.statusLines, key = { it }) { line ->
            Text(
                text = line,
                color = if (state.cached) style.colors.accent else style.colors.secondaryText,
                fontSize = 12.sp,
                fontWeight = if (state.cached) FontWeight.Medium else FontWeight.Normal
            )
        }
        item("summary") {
            SummaryMarkdown(
                content = state.summary,
                markwon = markwon,
                style = style
            )
        }
    }
}

@Composable
private fun SummaryMarkdown(
    content: String,
    markwon: Markwon,
    style: AiComposeStyle
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = {
            TextView(it).apply {
                textSize = 14f
                typeface = it.uiTypeface()
                setLineSpacing(0f, 1.18f)
                AiMarkdownRender.setNativeSelectionWithLinkTap(this)
            }
        },
        update = { textView ->
            textView.setTextColor(style.colors.primaryText.toArgb())
            val key = AiMarkdownRender.renderKey(
                messageId = "chapter-summary",
                content = content,
                pending = false,
                textView = textView,
                context = context
            )
            if (textView.tag != key) {
                markwon.setMarkdown(textView, content.ifBlank { "暂无总结" })
                textView.tag = key
            }
        }
    )
}
