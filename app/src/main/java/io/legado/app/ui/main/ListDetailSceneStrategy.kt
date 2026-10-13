@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package io.legado.app.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.ui.NavDisplay
import io.legado.app.ui.theme.LegadoTheme

/** 两栏之间的沟槽宽度，与 Material 列表-详情规范一致。 */
private val PaneGap = 24.dp

/** 左栏是首页这类「容器」时的宽度：它里面还有导航栏，比普通列表更需要空间。 */
private val ContainerPaneWidth = 420.dp

/** 左栏是普通列表时的宽度。 */
private val ListPaneWidth = 360.dp

private val MinDetailPaneWidth = 360.dp

/**
 * 把栈顶的详情目的地与它下面的列表目的地合成一个列表-详情双栏场景。
 *
 * 适用面由 entry metadata 决定，不限于某一对目的地：
 * - `listPane()` + `detailPane()` —— 探索书单 ↔ 书籍详情；
 * - 首页容器（`containerPane()`）+ 从它打开的次级页面（`detailPane()`）—— 书源管理、替换净化、
 *   设置、缓存管理、阅读记录、关于等；
 * - 两者都标的页面（设置、书源管理、替换净化、阅读记录）可以在栈里继续嵌套并排，
 *   例如「设置 ↔ 主题设置」「书源管理 ↔ 书源编辑」。
 *
 * 导航仍完全由 Navigation 3 的 backStack 拥有：这里只在「够宽 + 这两个目的地相邻」时改变
 * **呈现方式**，返回键依旧是 NavDisplay 的 pop（关掉右栏，左栏留在屏上）。窄窗口时
 * `calculateScene` 返回 null，交给链上的 [androidx.navigation3.scene.SinglePaneSceneStrategy]，
 * 行为与本次改动前完全一致。
 *
 * 布局是手写的而不是 `ListDetailPaneScaffold`：静态窗格值的脚手架只在**同一个**脚手架实例内
 * 响应窗格值变化，而这里每次导航都会新建场景对象，拿不到「单栏 → 双栏」的过渡，左栏也就没有
 * 展开动画。进入判据由 [MainAdaptiveLayoutEnvironment] 的可用区域、窗格最低尺寸、
 * 导航侧栏占用和用户模式共同决定；分隔铰链使用静态区域，避免展开动画跨越遮挡。
 *
 * 动画只在**从单栏进入双栏**时播放左栏展开（见 [PaneArrival]）：在左栏上换一个条目、或者
 * 下钻到下一级（上一级挪到左栏）都只是换窗格内容，不该把整个双栏重新展开一遍。
 */
internal class ListDetailSceneStrategy(
    private val isEnabled: () -> Boolean,
    /** 被浮层覆盖仍然保留展开，只有目的地真正出栈才考虑收拢。 */
    private val isEntryRetained: (Any) -> Boolean = { true },
    /**
     * 当前完整导航栈的项数。
     *
     * 用来区分「为栈顶区域算场景」和「为覆盖层下面的剩余项递归算场景」：只有前者才能代表
     * 用户此刻看到的形态，拿它更新 [lastTopPair]；递归求解下层时更新会把状态写坏。
     */
    private val stackSize: () -> Int = { Int.MAX_VALUE },
    private val environment: () -> MainAdaptiveLayoutEnvironment? = { null },
    private val canCollapse: () -> Boolean = { true },
) : SceneStrategy<NavKey> {

    private val expansions = PaneExpansionAnimations()

    /** 上一次为栈顶区域算出的双栏组合；null 表示那一刻栈顶是单栏。 */
    private var topPairKey: Pair<Any, Any>? = null

    /** [topPairKey] 对应的到达方式。对同一组合的重复求解要沿用首次判定，不能当成「已停稳」。 */
    private var topPairArrival: PaneArrival = PaneArrival.Expand

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val isTopRegion = entries.size == stackSize()
        if (!isEnabled()) {
            if (isTopRegion) topPairKey = null
            return null
        }
        val pair = listDetailEntries(entries)
        if (pair == null) {
            if (isTopRegion) topPairKey = null
            return null
        }
        val pairKey = pair.first.contentKey to pair.second.contentKey
        val adaptiveEnvironment = environment()
        val layout = adaptiveEnvironment?.paneLayout(
            pair.first.metadata[ContainerPaneKey] != null,
        )
        if (adaptiveEnvironment != null && layout == null) return null
        val arrival = if (!isTopRegion) {
            PaneArrival.Settled
        } else if (pairKey == topPairKey) {
            topPairArrival
        } else {
            paneArrival(previous = topPairKey, next = pairKey).also {
                topPairKey = pairKey
                topPairArrival = it
            }
        }
        return ListDetailScene(
            listEntry = pair.first,
            detailEntry = pair.second,
            // 返回只退出右栏；保留左栏才能解析出「上一对窗格」并启用预测式返回。
            previousEntries = entries.dropLast(1),
            expansion = expansions.state(
                key = pairKey,
                initialValue = if (arrival == PaneArrival.Expand) 0f else 1f,
            ),
            arrival = arrival,
            isEntryRetained = isEntryRetained,
            layout = layout,
            canCollapse = canCollapse,
        )
    }

    companion object {
        internal val ListPaneKey = object : NavMetadataKey<Unit> {}
        internal val DetailPaneKey = object : NavMetadataKey<Unit> {}
        internal val ContainerPaneKey = object : NavMetadataKey<Unit> {}

        /** 列表栏候选 —— 「点一项进详情」的目的地，例如探索书单。 */
        internal fun listPane(): Map<String, Any> = metadata { put(ListPaneKey, Unit) }

        /** 详情栏候选 —— 承接列表选中项的目的地，例如书籍详情、设置子页。 */
        internal fun detailPane(): Map<String, Any> = metadata { put(DetailPaneKey, Unit) }

        /**
         * 既可当左栏又可当右栏的目的地。
         *
         * 这类页面在导航栈里既是上一级的目的地、又是下一级的容器（设置 → 设置子页、
         * 书源管理 → 书源编辑、阅读记录 → 总览），标双份后它们在上层相随时也能继续并排。
         */
        internal fun listAndDetailPane(): Map<String, Any> =
            metadata {
                put(ListPaneKey, Unit)
                put(DetailPaneKey, Unit)
            }

        /** 首页容器：可以当左栏，且左栏要按容器的宽度（内含导航栏）留空间。 */
        internal fun containerPane(): Map<String, Any> =
            metadata {
                put(ListPaneKey, Unit)
                put(ContainerPaneKey, Unit)
            }
    }
}

/**
 * 找出可以双栏并排的相邻目的地：栈顶是候选详情、紧邻其下是候选列表。
 *
 * 识别靠 [ListDetailSceneStrategy.listPane] / [ListDetailSceneStrategy.detailPane] 这两个
 * entry metadata 标记，而不是 `contentKey` 的类型判断：`contentKey` 的默认实现是
 * `Pair(key.toString(), key::class)`（`NavEntryKt.defaultContentKey`），拿不到原始目的地对象。
 *
 * 只有标记过的这两个目的地相邻才升成双栏，其余组合返回 null，让下一个策略保持单栏全屏。
 */
internal fun listDetailEntries(
    entries: List<NavEntry<NavKey>>,
): Pair<NavEntry<NavKey>, NavEntry<NavKey>>? {
    if (entries.size < 2) return null
    val detail = entries[entries.size - 1]
    val list = entries[entries.size - 2]
    return if (
        list.metadata[ListDetailSceneStrategy.ListPaneKey] != null &&
        detail.metadata[ListDetailSceneStrategy.DetailPaneKey] != null
    ) {
        list to detail
    } else {
        null
    }
}

/**
 * 左栏宽度：容器比列表宽，且要给右栏留下可用宽度。
 *
 * 抽成纯函数是为了能在单测里钉住宽度策略——真机上「左栏够不够放导航栏」只靠肉眼确认。
 */
internal fun listPaneWidthOf(totalWidth: Dp, isContainer: Boolean): Dp {
    val preferred = if (isContainer) ContainerPaneWidth else ListPaneWidth
    val upperBound = (totalWidth - PaneGap - MinDetailPaneWidth).coerceAtLeast(ListPaneWidth)
    return preferred.coerceAtMost(upperBound)
}

/** 双栏相对上一次栈顶形态的到达方式，决定要不要播左栏展开动画。 */
internal enum class PaneArrival {
    /** 从单栏进来：左栏从占满窗口收到窗格宽度，右栏从右侧滑入。 */
    Expand,

    /** 左栏没变、只换了右栏：直接在原位替换右栏内容（「我的」里连着点不同条目就是这种）。 */
    Swap,

    /** 上一层的右栏成了这一层的左栏：左栏内容下推，右栏换成下一级（下钻二级页面）。 */
    Promote,

    /** 组合与上一次无关：直接落到目标形态，不做形变。 */
    Replace,

    /** 与上一次是同一组合：维持现状，不重播动画。 */
    Settled,
}

/**
 * 纯函数版本的位置判定，方便在单测里穷举这几种关系。
 *
 * [previous] 为 null 表示上一次栈顶是单栏（或者刚进进程）。
 */
internal fun paneArrival(
    previous: Pair<Any, Any>?,
    next: Pair<Any, Any>,
): PaneArrival = when {
    previous == next -> PaneArrival.Settled
    previous == null -> PaneArrival.Expand
    previous.first == next.first -> PaneArrival.Swap
    previous.second == next.first -> PaneArrival.Promote
    else -> PaneArrival.Replace
}

internal data class ListDetailScene(
    private val listEntry: NavEntry<NavKey>,
    private val detailEntry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val expansion: Animatable<Float, AnimationVector1D>,
    /** 到达方式；对导航测试可见，动画本身只读它。 */
    internal val arrival: PaneArrival,
    private val isEntryRetained: (Any) -> Boolean,
    internal val layout: MainPaneLayout? = null,
    private val canCollapse: () -> Boolean = { true },
) : Scene<NavKey> {

    // 同级选择只更新右栏，保持 Scene 和左栏的组合身份；下钻才更换左栏身份。
    override val key: Any = listEntry.contentKey

    /**
     * 两个目的地都由本场景消费：NavDisplay 据此给它们各自的条目生命周期与 ViewModelStore，
     * 因此列表和详情的 ViewModel 互不干扰（详情仍使用 `"BookInfo:<bookUrl>"` 那份 key）。
     */
    override val entries: List<NavEntry<NavKey>> = listOf(listEntry, detailEntry)

    // Ordinary single-page overrides (e.g. book info) cannot animate both panes across a hinge.
    override val metadata: Map<String, Any> = PaneSceneMetadata

    override val content: @Composable () -> Unit = {
        val isContainer = listEntry.metadata[ListDetailSceneStrategy.ContainerPaneKey] != null
        val expanded = isEntryRetained(detailEntry.contentKey) || !canCollapse()
        LaunchedEffect(expanded, arrival, expansion, layout?.usesHinge) {
            when {
                // 只有从单栏进来才播展开：换右栏内容、下钻一级都保持已展开的左栏。
                expanded && arrival == PaneArrival.Expand && layout?.usesHinge != true -> {
                    expansion.snapTo(0f)
                    expansion.animateTo(
                        1f,
                        tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    )
                }

                expanded -> expansion.snapTo(1f)

                // 返回时收回：右栏滑出、左栏重新占满，下层已恢复成单栏的同一页面。
                else -> expansion.animateTo(
                    0f,
                    tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing),
                )
            }
        }
        val progress = expansion.value.coerceIn(0f, 1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(LegadoTheme.colorScheme.background)
        ) {
            val totalWidth = maxWidth
            val listWidth = listPaneWidthOf(totalWidth, isContainer)
            val detailWidth = (totalWidth - listWidth - PaneGap).coerceAtLeast(0.dp)
            val paneLayout = layout ?: MainPaneLayout(
                Rect(0f, 0f, listWidth.value, maxHeight.value),
                Rect(totalWidth.value - detailWidth.value, 0f, totalWidth.value, maxHeight.value),
                usesHinge = false,
            )
            val contentWidth =
                layout?.let { it.detailBounds.right - it.listBounds.left } ?: totalWidth.value
            val visibleProgress = if (paneLayout.usesHinge) 1f else progress
            Box(
                modifier = Modifier
                    .absoluteOffset(paneLayout.listBounds.left.dp, paneLayout.listBounds.top.dp)
                    .width((contentWidth + (paneLayout.listBounds.width - contentWidth) * visibleProgress).dp)
                    .height(paneLayout.listBounds.height.dp)
                    .clipToBounds()
                    .consumeWindowInsets(mainAdaptiveInsets)
            ) {
                // 换左栏内容（下钻时上一级挪到左边）用一次淡入淡出过渡，而不是整屏重播展开。
                AnimatedContent(
                    targetState = listEntry,
                    contentKey = { it.contentKey },
                    transitionSpec = { PaneContentTransition },
                    label = "listPaneContent",
                ) { entry -> entry.Content() }
            }
            // 右栏按最终宽度布局后整体平移进来：动画过程中它内部不会跟着换行重排。
            Box(
                modifier = Modifier
                    .absoluteOffset(paneLayout.detailBounds.left.dp, paneLayout.detailBounds.top.dp)
                    .width(paneLayout.detailBounds.width.dp)
                    .height(paneLayout.detailBounds.height.dp)
                    .clipToBounds()
                    .consumeWindowInsets(mainAdaptiveInsets)
                    .graphicsLayer { translationX = size.width * (1f - visibleProgress) }
            ) {
                AnimatedContent(
                    targetState = detailEntry,
                    contentKey = { it.contentKey },
                    transitionSpec = { PaneContentTransition },
                    label = "detailPaneContent",
                ) { entry -> entry.Content() }
            }
        }
    }
}

/** 窗格内容互换时的过渡：只做淡入淡出，几何形态由展开进度单独负责。 */
private val PaneContentTransition =
    fadeIn(tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)) togetherWith
            fadeOut(tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing))

private val PaneSceneMetadata = metadata {
    put(NavDisplay.TransitionKey) { EnterTransition.None togetherWith ExitTransition.None }
    put(NavDisplay.PopTransitionKey) {
        if (targetState is ListDetailScene || (initialState as? ListDetailScene)?.layout?.usesHinge == true) {
            EnterTransition.None togetherWith ExitTransition.None
        } else EnterTransition.None togetherWith fadeOut(tween(NAV_SLIDE_DURATION_MILLIS))
    }
    put(NavDisplay.PredictivePopTransitionKey) { _ ->
        // Fade in place instead of moving or scaling content into the excluded hinge region.
        EnterTransition.None togetherWith fadeOut(tween(NAV_SLIDE_DURATION_MILLIS))
    }
}

/**
 * 每对目的地的展开进度。
 *
 * 状态必须挂在策略上、按场景复用：NavDisplay 每次重算场景都会新建场景对象，把
 * [Animatable] 放进场景对象会让已经停稳的双栏在每次重组时跳回全屏再展开一次。
 *
 * [initialValue] 让「换右栏内容 / 下钻一级」直接以已展开状态开始，不重播展开。
 */
private class PaneExpansionAnimations {
    private val byKey = LinkedHashMap<Any, Animatable<Float, AnimationVector1D>>()

    fun state(key: Any, initialValue: Float): Animatable<Float, AnimationVector1D> {
        byKey.remove(key)?.let {
            byKey[key] = it
            return it
        }
        val animation = Animatable(initialValue)
        byKey[key] = animation
        // 转场期间最多同时存在两个双栏场景，留几份足够，避免长会话里无限增长。
        while (byKey.size > 4) {
            byKey.remove(byKey.keys.first())
        }
        return animation
    }
}
