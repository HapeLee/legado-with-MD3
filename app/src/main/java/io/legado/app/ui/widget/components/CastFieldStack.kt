package io.legado.app.ui.widget.components

import android.view.View
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 下拉候选项：key 为稳定 id（角色 id / 音色 id / 池名），label 为显示文本。 */
data class CastOption(
    val key: String,
    val label: String,
)

/**
 * 角色分配悬浮窗的键盘作用域：窗口内置空 [LocalSoftwareKeyboardController]，Compose 侧的
 * hide 请求（回车 Done 等）都变成空操作；窗口关闭时再手动收起，避免键盘留在阅读页上。
 */
@Composable
fun CastImeScope(content: @Composable () -> Unit) {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    DisposableEffect(controllerOf(view, window)) {
        onDispose { controllerOf(view, window)?.hide(WindowInsetsCompat.Type.ime()) }
    }
    CompositionLocalProvider(
        LocalSoftwareKeyboardController provides null,
        content = content,
    )
}

private fun controllerOf(view: View, window: Window?): WindowInsetsControllerCompat? =
    window?.let { WindowCompat.getInsetsController(it, view) }

/**
 * 补弹节流：同一时刻只允许一发 show 在飞。
 *
 * 焦点在两个输入框之间转移的那一瞬间，窗口短暂地不再是文本编辑器，平台于是自己
 * `InsetsController.hide(ime())`（ImeTracker 里的 `HIDE_SOFT_INPUT_BY_INSETS_API`，不是
 * Compose 发的）。补弹并发时，后发的 show 会把前一次正在跑的 show 动画以
 * `onCancelled at PHASE_CLIENT_APPLY_ANIMATION` 取消掉，键盘要等 ~0.8s 才回来——
 * 这就是「切输入框又重新弹一次输入法」的表现。
 * 同一屏只有一张分配卡片，节流放全局即可。
 */
private object CastImeThrottle {
    var nextShowAt = 0L
}

/** 平台方式弹出键盘：已可见直接跳过，刚补过 [SHOW_IME_COOLDOWN_MS] 内不再补第二发。 */
@Composable
fun rememberImeShow(): () -> Unit {
    val view = LocalView.current
    val window = LocalActivity.current?.window
    // 对话框是另一个窗口：那里没有 [LocalActivity]，拿不到 Activity window，
    // 这时用 Compose 自己的控制器——它绑的就是当前 Composition 所在的那个窗口。
    val composeController = LocalSoftwareKeyboardController.current
    return remember(view, window, composeController) {
        {
            val controller = controllerOf(view, window)
            val now = System.currentTimeMillis()
            val shown = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (!shown && now >= CastImeThrottle.nextShowAt) {
                if (controller != null) {
                    CastImeThrottle.nextShowAt = now + SHOW_IME_COOLDOWN_MS
                    controller.show(WindowInsetsCompat.Type.ime())
                } else if (composeController != null) {
                    CastImeThrottle.nextShowAt = now + SHOW_IME_COOLDOWN_MS
                    composeController.show()
                }
            }
        }
    }
}

private const val SHOW_IME_COOLDOWN_MS = 400L

/**
 * 输入框获得焦点后的一小段「保护期」内盯住键盘可见性：一旦被平台收掉就补一次弹出
 * （补弹本身走 [rememberImeShow] 的节流，不会互相取消动画）。保护期过后不再干预，
 * 用户主动收起键盘依然有效。聚焦时**不**主动 show——新获得焦点的编辑器自己会走
 * `SHOW_SOFT_INPUT`，我们再补一发只会把它的动画取消掉。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun rememberImeKeepShown(focused: Boolean) {
    val showIme = rememberImeShow()
    var guarded by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(focused) {
        guarded = false
        if (!focused) return@LaunchedEffect
        guarded = true
        delay(1200L)
        guarded = false
    }
    LaunchedEffect(imeVisible, focused) {
        if (!focused || !guarded || imeVisible) return@LaunchedEffect
        // 先让平台自己那发 SHOW_SOFT_INPUT 落地：新聚焦的编辑器本来就会请求弹出，
        // 我们抢在前面补只会把它正在跑的动画取消掉。等一小段再补，showIme 内部还会
        // 复查一次真实可见性，已经弹出来就是空操作。
        delay(RESHOW_SETTLE_MS)
        showIme()
    }
}

/**
 * 补弹前的等待。新获得焦点的文本框自己会发一发 `SHOW_SOFT_INPUT`：等待不足一个动画周期时，
 * 我们的 show 会跑到它前面，把它以 `onCancelled at PHASE_CLIENT_APPLY_ANIMATION` 取消掉，
 * 然后自己再走一遍完整的收起→弹出（约 600ms）。等满一个 IME 动画周期再补，
 * 让它先弹，我们只在它没弹回来的时候兜底。
 */
private const val RESHOW_SETTLE_MS = 320L

/**
 * 悬浮卡片里用的文本框：官方 [AppTextField] 一行都不改，只在调用侧接上焦点守卫。
 *
 * 卡片内多个输入框之间转移焦点时平台会自己收键盘（见 [rememberImeKeepShown]），
 * 所以本浮层内**每个**输入框都要走这里，不能只保下拉行。
 */
@Composable
fun CastImeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    var focused by remember { mutableStateOf(false) }
    rememberImeKeepShown(focused)
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusEvent { focused = it.isFocused },
        label = label,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
    )
}

/**
 * 悬浮卡片高度上限 = 窗口高 × [fraction]。父级 `safeDrawingPadding` 已经把状态栏与键盘占掉的
 * 空间扣掉，`heightIn` 取两者较小值 → 卡片再长也不会顶到状态栏/挖孔。
 */
@Composable
fun castCardMaxHeight(fraction: Float): Dp {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    return with(LocalDensity.current) { windowHeight.toDp() * fraction }
}

/**
 * 一行输入框的规格。整屏只渲染**一个**真输入框（见 [CastFieldStack]），
 * 这些规格决定它当前绑定到哪一行、以及那一行的外壳画什么。
 *
 * [options] 为空 = 这一行只是普通文本框（不画三角、不弹候选列表）；
 * 行数配置（[singleLine] / [minLines] / [maxLines]）由外壳和共享框共用，
 * 保证两者高度一致、叠在一起不重影。
 */
class CastFieldSpec(
    val id: String,
    val label: String,
    val value: String,
    val options: List<CastOption> = emptyList(),
    val expanded: Boolean = false,
    val singleLine: Boolean = true,
    val minLines: Int = 1,
    val maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    val onValueChange: (String) -> Unit,
    val onSelected: (CastOption) -> Unit = {},
    val onExpand: (Boolean) -> Unit = {},
) {
    val hasDropdown: Boolean get() = options.isNotEmpty()
}

/**
 * 三行下拉共用同一个输入框。
 *
 * 为什么非共用不可：焦点从一个文本框挪到另一个时，Compose 的输入服务会自己发
 * `InsetsController.hide(ime())`，客户端补 show 拦不住——hide 的 token 即使被 cancelled，
 * system_server 仍走完 `onHidden`，键盘肉眼可见地掉下去约 650ms 再滑回来。
 * 只能让焦点压根不换：三行都画成"外壳"（不可聚焦，
 * 点击被盖板吃掉），真正的 OutlinedTextField 只有一个、永远待在 Box 的同一个子槽位里，
 * 切行只是把它的绑定和 offset 挪过去。焦点节点自始至终没变 → 平台不发 hide →
 * 三个字段照样都能打字。
 */
@Composable
fun CastFieldStack(
    specs: List<CastFieldSpec>,
    modifier: Modifier = Modifier,
) {
    var activeId by remember { mutableStateOf(specs.firstOrNull()?.id) }
    // 点三角=浏览全部，打字=按输入筛选。记行 id，因为输入框是共享的、不能记在框身上。
    var browseAllId by remember { mutableStateOf<String?>(null) }
    val lineTop = remember { mutableStateMapOf<String, Float>() }
    var stackTop by remember { mutableFloatStateOf(0f) }
    val active = specs.indexOfFirst { it.id == activeId }.let { if (it >= 0) it else 0 }
    val activeSpec = specs.getOrNull(active)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { stackTop = it.localToRoot(Offset.Zero).y },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            specs.forEach { spec ->
                val query = spec.value.trim()
                val visible = remember(spec.options, browseAllId, query, spec.id) {
                    if (!spec.hasDropdown || browseAllId == spec.id || query.isEmpty()) {
                        spec.options
                    } else {
                        spec.options.filter { it.label.contains(query, ignoreCase = true) }
                    }
                }
                CastFieldShell(
                    spec = spec,
                    options = visible,
                    hidden = spec === activeSpec,
                    onActivate = {
                        activeId = spec.id
                        if (spec.hasDropdown) {
                            browseAllId = spec.id
                            spec.onExpand(true)
                        }
                    },
                    modifier = Modifier.onGloballyPositioned {
                        lineTop[spec.id] = it.localToRoot(Offset.Zero).y
                    },
                )
            }
        }
        activeSpec?.let { spec ->
            CastFieldEditor(
                spec = spec,
                onBrowseAll = { browseAllId = if (it) spec.id else null },
                modifier = Modifier.offset {
                    IntOffset(0, ((lineTop[spec.id] ?: 0f) - stackTop).roundToInt())
                },
            )
        }
    }
}

/** 一行的外壳：长得和输入框一样的展示框（永不聚焦）+ 内嵌候选列表。 */
@Composable
private fun CastFieldShell(
    spec: CastFieldSpec,
    options: List<CastOption>,
    hidden: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = spec.value,
                onValueChange = {},
                label = { Text(spec.label) },
                singleLine = spec.singleLine,
                minLines = spec.minLines,
                maxLines = spec.maxLines,
                trailingIcon = if (spec.hasDropdown) {
                    { CastArrow(expanded = spec.expanded, onClick = onActivate) }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // 当前行由盖在上面的共享输入框负责画，外壳藏掉避免文字/边框重影
                    .graphicsLayer { alpha = if (hidden) 0f else 1f },
            )
            // 盖板吃掉点击：外壳一旦拿到焦点，共享输入框的编辑会话就结束了→键盘收起
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(onActivate) {
                        detectTapGestures { onActivate() }
                    },
            )
        }
        // 候选列表就地展开，不加高度动画：这几行下拉出现在 AlertDialog、配音卡片和悬浮窗里，
        // 宿主都是「按内容高 + 居中」或 LazyColumn 里的行，高度一边动宿主一边重新量自己，
        // 结果就是整窗上下跳、行与行叠在一起。
        if (spec.hasDropdown && spec.expanded) {
            CastOptionList(
                options = options,
                onSelected = {
                    spec.onSelected(it)
                    spec.onExpand(false)
                },
            )
        }
    }
}

/** 整屏唯一的那个真输入框。 */
@Composable
private fun CastFieldEditor(
    spec: CastFieldSpec,
    onBrowseAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val showIme = rememberImeShow()
    rememberImeKeepShown(focused)
    OutlinedTextField(
        value = spec.value,
        onValueChange = {
            onBrowseAll(false)
            spec.onValueChange(it)
            // 输入即筛选并自动弹出列表；纯文本框没有列表可弹
            if (spec.hasDropdown) spec.onExpand(true)
        },
        label = { Text(spec.label) },
        singleLine = spec.singleLine,
        minLines = spec.minLines,
        maxLines = spec.maxLines,
        trailingIcon = if (spec.hasDropdown) {
            {
                CastArrow(expanded = spec.expanded) {
                    onBrowseAll(true)
                    spec.onExpand(!spec.expanded)
                }
            }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onPointerUpObserved { showIme() }
            .onFocusEvent { focused = it.isFocused },
    )
}

@Composable
private fun CastArrow(expanded: Boolean, onClick: () -> Unit) {
    IconButton(
        // 三角必须不可聚焦：点到它会把焦点从输入框抢走，于是平台收键盘 → 「点一下就弹一次键盘」
        modifier = Modifier.focusProperties { canFocus = false },
        onClick = onClick,
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Default.ArrowDropUp
            } else {
                Icons.Default.ArrowDropDown
            },
            contentDescription = null,
        )
    }
}

/** 内嵌在卡片里的候选列表（不用 Popup → 无窗口层级/焦点互扰）。 */
@Composable
private fun CastOptionList(
    options: List<CastOption>,
    onSelected: (CastOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                RoundedCornerShape(16.dp),
            )
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
    ) {
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.cast_no_match),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        options.forEach { option ->
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                color = LegadoTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    // 同三角：选项可点击但绝不可聚焦，否则点中项就抢走输入框焦点→键盘收起
                    .focusProperties { canFocus = false }
                    .clickable { onSelected(option) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 在 Initial pass 上旁观一次「按下→抬起」，不消费事件，输入框照常收到点击、照常放光标。
 *
 * 用途：用输入法自带的收起键把键盘关掉之后，输入框的焦点还在，再点它不会产生焦点变化，
 * Compose 就不会重新 show → 键盘再也叫不回来（[rememberImeKeepShown] 只在刚拿到焦点的
 * 保护期内盯，也救不了）。这里在每次抬手时补一发，[rememberImeShow] 内部先查真实可见性，
 * 键盘在着就是空操作。
 */
private fun Modifier.onPointerUpObserved(onUp: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var event: PointerEvent
        do {
            event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        onUp()
    }
}
