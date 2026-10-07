package io.legado.app.ui.main

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal const val NAV_SLIDE_DURATION_MILLIS = 480
internal const val NAV_FADE_DURATION_MILLIS = 360

/** Keeps parent destinations composed beneath a Nav3 overlay scene. */
class ModalOverlaySceneStrategy(
    private val isTopEntry: (Any) -> Boolean = { true },
    private val predictiveBackEnabled: () -> Boolean = { true },
) : SceneStrategy<NavKey> {
    private val searchAnimations = SearchOverlayAnimations()

    // 书内子页的滑动状态另存一份：下层用 covering() 判断要不要跟随淡出，共用会误触发搜索那套。
    private val pageSlideAnimations = SearchOverlayAnimations()

    override fun SceneStrategyScope<NavKey>.calculateScene(
        entries: List<NavEntry<NavKey>>,
    ): Scene<NavKey>? {
        val entry = entries.lastOrNull() ?: return null
        entry.metadata[MetadataKey] ?: return null
        val previousEntries = entries.dropLast(1)
        if (previousEntries.isEmpty()) return null
        if (entry.metadata[SearchSlideKey] != null) {
            return SearchOverlayScene(
                entry = entry,
                previousEntries = previousEntries,
                animations = searchAnimations,
            )
        }
        val pageBack = entry.metadata[PageSlideKey] as? () -> Unit
        if (pageBack != null) {
            return PageSlideOverlayScene(
                entry = entry,
                previousEntries = previousEntries,
                animations = pageSlideAnimations,
                onBack = pageBack,
                isTopEntry = isTopEntry,
                predictiveBackEnabled = predictiveBackEnabled,
            )
        }
        return ModalOverlayScene(
            entry = entry,
            previousEntries = previousEntries,
            animations = searchAnimations,
        )
    }

    companion object {
        private object MetadataKey : NavMetadataKey<Unit>
        private object SearchSlideKey : NavMetadataKey<Unit>
        private object PageSlideKey : NavMetadataKey<() -> Unit>

        fun modalOverlay(): Map<String, Any> = metadata { put(MetadataKey, Unit) }
        fun searchSlide(): Map<String, Any> = metadata { put(SearchSlideKey, Unit) }

        /**
         * 书内子页（人物配音 / 朗读引擎与音色 / 朗读缓存 / 人物与知识库列表…）压在
         * 阅读页或详情页上面时用：下层保持组合，本层自己负责位移动画与返回键。
         *
         * 必须两样都自带，不能指望 NavDisplay：
         * 1. 覆盖层场景画在 `AnimatedContent` 之外，场景转场（`transitionSpec` 等）到不了这一层；
         * 2. 它内置的 `NavigationBackHandler` 只在**根场景**的 `previousEntries` 非空时启用，
         *    而一旦本层成为覆盖层，根场景就只剩 `SinglePane(Home)`，返回事件没人接 →
         *    直接落到 Activity 默认行为（退出应用）。
         */
        fun pageSlide(onBack: () -> Unit): Map<String, Any> =
            modalOverlay() + metadata { put(PageSlideKey, onBack) }
    }
}

/**
 * 书内子页的覆盖层：从右侧滑入盖住下层，滑出时退回下层。
 *
 * 动画状态放在 [pageSlideAnimations] 那份缓存里而不是本对象里 —— NavDisplay 每次重算场景都会
 * 新建场景对象，状态放在对象上会让已经停稳的页面突然跳回屏幕外。
 * 这份缓存与搜索那份是**两个实例**：下层用 `covering()` 判断要不要跟随淡出，
 * 混在一起会让阅读页在子页盖上来时误按搜索那套淡出。
 */
private data class PageSlideOverlayScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val animations: SearchOverlayAnimations,
    private val onBack: () -> Unit,
    private val isTopEntry: (Any) -> Boolean,
    private val predictiveBackEnabled: () -> Boolean,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
    override val metadata: Map<String, Any> = entry.metadata
    private val animation = animations.state(key, previousEntries.last().contentKey)

    override val content: @Composable () -> Unit = {
        val backScope = rememberCoroutineScope()
        PredictiveBackHandler(enabled = isTopEntry(entry.contentKey)) { events ->
            try {
                events.collect { event ->
                    if (predictiveBackEnabled()) animation.previewBack(event.progress)
                }
                onBack()
            } catch (cancelled: CancellationException) {
                backScope.launch { animation.cancelPreview() }
                throw cancelled
            }
        }
        LaunchedEffect(key) {
            coroutineScope {
                launch {
                    animation.progress.animateTo(
                        1f,
                        tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animation.opacity.animateTo(
                        1f,
                        tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(previousEntries.size.toFloat())
                .graphicsLayer {
                    translationX =
                        if (animation.removing) 0f else size.width * (1f - animation.progress.value)
                    scaleX = if (animation.removing) 0.8f + 0.2f * animation.progress.value else 1f
                    scaleY = scaleX
                    alpha = animation.opacity.value
                }
        ) {
            entry.Content()
        }
    }

    override suspend fun onRemove() {
        try {
            animation.removing = true
            coroutineScope {
                launch {
                    animation.progress.animateTo(
                        0f,
                        tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
                launch { animation.opacity.animateTo(0f, tween(NAV_FADE_DURATION_MILLIS)) }
            }
        } finally {
            animations.release(key, animation)
        }
    }
}

private data class ModalOverlayScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val animations: SearchOverlayAnimations,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val metadata: Map<String, Any> = entry.metadata

    // NavDisplay recursively resolves the scene underneath an overlay. Passing only the
    // last entry turns a nested overlay (book info under a reader) into a SinglePane root,
    // changing its composition/lifecycle owner and discarding its parent scene.
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
    override val content: @Composable () -> Unit = {
        // NavDisplay retains existing overlays and appends newly opened ones. Their
        // composition order can therefore differ from stack order for nested overlays.
        // Draw and hit-test by stack depth so a reader always sits above book info.
        val search = animations.covering(key)
        Box(
            Modifier
                .zIndex(previousEntries.size.toFloat())
                // 搜索覆盖阅读页时，阅读页淡出到主题背景，而不是露出更底层的主页。
                .then(
                    if (search != null) {
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    } else Modifier
                )
        ) {
            Box(Modifier.graphicsLayer {
                translationX = -size.width / 4f * (search?.progress?.value ?: 0f)
                alpha = search?.underlayOpacity?.value ?: 1f
            }) {
                entry.Content()
            }
        }
    }
}

/** Keeps the reader composed while Nav3 owns the search panel's entry and removal lifetime. */
private data class SearchOverlayScene(
    private val entry: NavEntry<NavKey>,
    override val previousEntries: List<NavEntry<NavKey>>,
    private val animations: SearchOverlayAnimations,
) : OverlayScene<NavKey> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<NavKey>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<NavKey>> = previousEntries
    override val metadata: Map<String, Any> = entry.metadata
    private val animation = animations.state(key, previousEntries.last().contentKey)

    override val content: @Composable () -> Unit = {
        LaunchedEffect(key) {
            coroutineScope {
                launch {
                    animation.progress.animateTo(
                        1f,
                        tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    animation.opacity.animateTo(
                        1f,
                        tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)
                    )
                }
                launch {
                    animation.underlayOpacity.animateTo(
                        0f,
                        tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)
                    )
                }
            }
        }
        CompositionLocalProvider(LocalSearchOverlayAnimation provides animation) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(previousEntries.size.toFloat())
                    .graphicsLayer {
                        translationX =
                            if (animation.removing) 0f else size.width * (1f - animation.progress.value)
                        scaleX =
                            if (animation.removing) 0.8f + 0.2f * animation.progress.value else 1f
                        scaleY = scaleX
                        alpha = animation.opacity.value
                    }
            ) {
                entry.Content()
            }
        }
    }

    override suspend fun onRemove() {
        try {
            animation.removing = true
            coroutineScope {
                launch {
                    animation.progress.animateTo(
                        0f,
                        tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
                launch { animation.opacity.animateTo(0f, tween(NAV_FADE_DURATION_MILLIS)) }
                launch {
                    animation.underlayOpacity.animateTo(
                        1f,
                        tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)
                    )
                }
            }
        } finally {
            animations.release(key, animation)
        }
    }
}

internal class SearchOverlayAnimation(val underlayKey: Any) {
    val progress = Animatable(0f)
    val opacity = Animatable(0f)
    val underlayOpacity = Animatable(1f)
    var removing by mutableStateOf(false)

    suspend fun previewBack(progress: Float) {
        removing = true
        // 与 NavDisplay 的 predictivePopTransitionSpec 使用同一 easing。
        val fraction = progress.coerceIn(0f, 1f)
        val remaining = 1f - FastOutSlowInEasing.transform(fraction)
        this.progress.snapTo(remaining)
        opacity.snapTo(remaining)
        underlayOpacity.snapTo(LinearOutSlowInEasing.transform(fraction))
    }

    suspend fun cancelPreview() {
        coroutineScope {
            launch {
                progress.animateTo(
                    1f,
                    tween(NAV_SLIDE_DURATION_MILLIS, easing = FastOutSlowInEasing)
                )
            }
            launch { opacity.animateTo(1f, tween(NAV_FADE_DURATION_MILLIS)) }
            launch {
                underlayOpacity.animateTo(
                    0f,
                    tween(NAV_FADE_DURATION_MILLIS, easing = LinearOutSlowInEasing)
                )
            }
        }
        removing = false
    }
}

internal val LocalSearchOverlayAnimation =
    staticCompositionLocalOf<SearchOverlayAnimation?> { null }

private class SearchOverlayAnimations {
    // 旧阅读场景可能被 NavDisplay 复用；搜索入栈时必须使它重新读取覆盖动画。
    private val byKey = mutableStateMapOf<Any, SearchOverlayAnimation>()

    fun state(key: Any, underlayKey: Any): SearchOverlayAnimation =
        byKey.getOrPut(key) { SearchOverlayAnimation(underlayKey) }

    fun covering(key: Any): SearchOverlayAnimation? =
        byKey.values.lastOrNull { it.underlayKey == key }

    fun release(key: Any, animation: SearchOverlayAnimation) {
        if (byKey[key] === animation) byKey.remove(key)
    }
}
