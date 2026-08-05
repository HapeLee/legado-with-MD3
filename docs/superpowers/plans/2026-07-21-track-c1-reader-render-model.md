# Track C1 ReaderRenderModel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变旧 `ReadView` 渲染行为的前提下，暴露可供后续 Compose 阅读表面消费的只读结构渲染流与高频 viewport 流。

**Architecture:** `ReaderRenderStateStore` 维护两个彼此独立的 `StateFlow`：低频 `ReaderRenderModel` 聚合 `LegacyReaderSnapshot`、三页绘制数据、页索引、选择、朗读高亮与 loading；高频 `ReaderRenderViewport` 只承载尺寸、触点、方向和归一化动画进度。`ReadBookController` 负责从现有 `ReadBook`/`ReadView` 真源取样，旧 View 只在已有状态变化点通知控制器，不转移状态所有权，也不改变任何绘制调用顺序。

**Tech Stack:** Kotlin、StateFlow、kotlinx.collections.immutable、Android View、Robolectric/JUnit 4。

---

### Task 1: 定义并测试只读渲染状态容器

**Files:**
- Create: `app/src/main/java/io/legado/app/ui/book/read/ReaderRenderModel.kt`
- Create: `app/src/test/java/io/legado/app/ui/book/read/ReaderRenderStateStoreTest.kt`

- [ ] **Step 1: 写低频结构态与高频 viewport 相互独立的失败测试**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ReaderRenderStateStoreTest {
    @Test
    fun `viewport updates do not replace structural model`() {
        val store = ReaderRenderStateStore(LegacyReaderSnapshot(bookUrl = "book"))
        val structural = store.model.value

        store.publishViewport(
            width = 100,
            height = 200,
            startX = 0f,
            startY = 0f,
            touchX = 50f,
            touchY = 0f,
            direction = PageDirection.NEXT,
            isAnimationRunning = true,
        )

        assertSame(structural, store.model.value)
        assertEquals(0.5f, store.viewport.value.animationProgress, 0f)
    }
}
```

- [ ] **Step 2: 运行测试并确认因 `ReaderRenderStateStore` 尚不存在而失败**

Run: `./gradlew --no-daemon :app:testAppDebugUnitTest --tests 'io.legado.app.ui.book.read.ReaderRenderStateStoreTest'`

Expected: Kotlin 编译失败，提示 `Unresolved reference 'ReaderRenderStateStore'`。

- [ ] **Step 3: 写三页、选择、朗读高亮、loading 与会话更新的失败测试**

```kotlin
@Test
fun `structure publish snapshots render inputs`() {
    val current = TextPage(index = 4, text = "current", chapterIndex = 2)
    val line = TextLine().apply { isReadAloud = true }
    current.addLine(line)
    val mutableStart = TextPos(0, 1, 2)
    val mutableEnd = TextPos(0, 1, 5)
    val store = ReaderRenderStateStore()

    store.publishStructure(
        previousPage = TextPage(index = 3),
        currentPage = current,
        nextPage = TextPage(index = 5),
        durPageIndex = 4,
        selectionStart = mutableStart,
        selectionEnd = mutableEnd,
        message = "Loading…",
        isLoading = true,
    )
    mutableStart.lineIndex = 9

    val model = store.model.value
    assertEquals(4, model.durPageIndex)
    assertEquals(1, model.selection?.start?.lineIndex)
    assertEquals(0, model.readAloudHighlights.single().firstLineIndex)
    assertTrue(model.isLoading)
}

@Test
fun `session updates preserve render pages`() {
    val store = ReaderRenderStateStore()
    val current = TextPage(index = 1)
    store.publishStructure(currentPage = current, durPageIndex = 1)

    store.publishSession(LegacyReaderSnapshot(bookUrl = "updated", chapterIndex = 3))

    assertEquals("updated", store.model.value.session.bookUrl)
    assertSame(current, store.model.value.currentPage?.page)
}
```

- [ ] **Step 4: 实现最小只读模型与双 StateFlow 容器**

```kotlin
@Stable
class ReaderRenderPage internal constructor(
    val page: TextPage,
    val revision: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is ReaderRenderPage && page === other.page && revision == other.revision

    override fun hashCode(): Int = 31 * System.identityHashCode(page) + revision.hashCode()
}

@Stable
data class ReaderTextPosition(
    val relativePage: Int,
    val lineIndex: Int,
    val columnIndex: Int,
)

@Stable
data class ReaderSelection(
    val start: ReaderTextPosition,
    val end: ReaderTextPosition,
)

@Stable
data class ReaderReadAloudHighlight(
    val relativePage: Int,
    val firstLineIndex: Int,
    val lastLineIndex: Int,
)

@Stable
data class ReaderRenderModel(
    val session: LegacyReaderSnapshot = LegacyReaderSnapshot(),
    val previousPage: ReaderRenderPage? = null,
    val currentPage: ReaderRenderPage? = null,
    val nextPage: ReaderRenderPage? = null,
    val durPageIndex: Int = 0,
    val selection: ReaderSelection? = null,
    val readAloudHighlights: ImmutableList<ReaderReadAloudHighlight> = persistentListOf(),
    val message: String? = null,
    val isLoading: Boolean = false,
)

@Stable
data class ReaderRenderViewport(
    val width: Int = 0,
    val height: Int = 0,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val touchX: Float = 0f,
    val touchY: Float = 0f,
    val direction: PageDirection = PageDirection.NONE,
    val isAnimationRunning: Boolean = false,
    val animationProgress: Float = 0f,
)

internal class ReaderRenderStateStore(
    initialSession: LegacyReaderSnapshot = LegacyReaderSnapshot(),
) {
    private val _model = MutableStateFlow(ReaderRenderModel(session = initialSession))
    val model = _model.asStateFlow()
    private val _viewport = MutableStateFlow(ReaderRenderViewport())
    val viewport = _viewport.asStateFlow()

    fun publishSession(session: LegacyReaderSnapshot) {
        _model.update { it.copy(session = session) }
    }

    private var revision = 0L

    fun publishStructure(
        previousPage: TextPage? = null,
        currentPage: TextPage? = null,
        nextPage: TextPage? = null,
        durPageIndex: Int = 0,
        selectionStart: TextPos? = null,
        selectionEnd: TextPos? = null,
        message: String? = null,
        isLoading: Boolean = false,
    ) {
        revision++
        val pages = listOf(-1 to previousPage, 0 to currentPage, 1 to nextPage)
        _model.update { current ->
            current.copy(
                previousPage = previousPage?.let { ReaderRenderPage(it, revision) },
                currentPage = currentPage?.let { ReaderRenderPage(it, revision) },
                nextPage = nextPage?.let { ReaderRenderPage(it, revision) },
                durPageIndex = durPageIndex,
                selection = if (
                    selectionStart?.isSelected() == true && selectionEnd?.isSelected() == true
                ) {
                    ReaderSelection(
                        start = selectionStart.toReaderTextPosition(),
                        end = selectionEnd.toReaderTextPosition(),
                    )
                } else {
                    null
                },
                readAloudHighlights = pages.mapNotNull { (relativePage, page) ->
                    val indices = page?.lines?.indices?.filter { page.lines[it].isReadAloud }
                        .orEmpty()
                    if (indices.isEmpty()) null else ReaderReadAloudHighlight(
                        relativePage = relativePage,
                        firstLineIndex = indices.first(),
                        lastLineIndex = indices.last(),
                    )
                }.toImmutableList(),
                message = message,
                isLoading = isLoading,
            )
        }
    }

    fun publishViewport(
        width: Int,
        height: Int,
        startX: Float,
        startY: Float,
        touchX: Float,
        touchY: Float,
        direction: PageDirection,
        isAnimationRunning: Boolean,
    ) {
        val xProgress = if (width > 0) abs(touchX - startX) / width else 0f
        val yProgress = if (height > 0) abs(touchY - startY) / height else 0f
        _viewport.value = ReaderRenderViewport(
            width = width,
            height = height,
            startX = startX,
            startY = startY,
            touchX = touchX,
            touchY = touchY,
            direction = direction,
            isAnimationRunning = isAnimationRunning,
            animationProgress = max(xProgress, yProgress).coerceIn(0f, 1f),
        )
    }
}
```

- [ ] **Step 5: 运行聚焦测试，确认全部通过**

Run: `./gradlew --no-daemon :app:testAppDebugUnitTest --tests 'io.legado.app.ui.book.read.ReaderRenderStateStoreTest'`

Expected: `BUILD SUCCESSFUL`，所有 `ReaderRenderStateStoreTest` 用例通过。

- [ ] **Step 6: 提交状态模型任务**

```bash
git add app/src/main/java/io/legado/app/ui/book/read/ReaderRenderModel.kt app/src/test/java/io/legado/app/ui/book/read/ReaderRenderStateStoreTest.kt
git commit -m "feat(Track C): 抽取阅读器只读渲染状态"
```

### Task 2: 从旧 ReadView 真源并行发布渲染状态

**Files:**
- Modify: `app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/PageView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt`
- Modify: `app/src/main/java/io/legado/app/ui/book/read/page/delegate/PageDelegate.kt`
- Modify: `docs/dev/track-c-compose-reader-plan.md`
- Test: `app/src/test/java/io/legado/app/ui/book/read/ReaderRenderStateStoreTest.kt`

- [ ] **Step 1: 补充相同 `TextPage` 重发时 revision 递增的回归测试**

```kotlin
@Test
fun `republishing same mutable page advances render revision`() {
    val store = ReaderRenderStateStore()
    val page = TextPage(index = 1)
    store.publishStructure(currentPage = page, durPageIndex = 1)
    val first = store.model.value.currentPage!!

    page.hasReadAloudSpan = true
    store.publishStructure(currentPage = page, durPageIndex = 1)

    val second = store.model.value.currentPage!!
    assertSame(page, second.page)
    assertTrue(second.revision > first.revision)
    assertNotEquals(first, second)
}
```

- [ ] **Step 2: 运行测试确认 identity + revision 契约继续成立**

Run: `./gradlew --no-daemon :app:testAppDebugUnitTest --tests 'io.legado.app.ui.book.read.ReaderRenderStateStoreTest.republishing same mutable page advances render revision'`

Expected: `BUILD SUCCESSFUL`；相同可变页的后续发布具有更大的 revision，且不触发 `TextPage` 深层 equality。

- [ ] **Step 3: 保持页面包装的 identity + revision 相等语义，不在接线层复制 TextPage**

```kotlin
@Stable
class ReaderRenderPage internal constructor(
    val page: TextPage,
    val revision: Long,
) {
    override fun equals(other: Any?): Boolean =
        other is ReaderRenderPage && page === other.page && revision == other.revision

    override fun hashCode(): Int = 31 * System.identityHashCode(page) + revision.hashCode()
}
```

- [ ] **Step 4: 在旧 View 的既有变更点仅增加取样通知**

```kotlin
// ReadView.CallBack
fun onRenderStructureChanged()
fun onRenderViewportChanged()

// ReadView
internal fun notifyRenderViewportChanged() = callBack.onRenderViewportChanged()

// Existing methods keep their original behavior/order, then notify:
// onSizeChanged / setStartPoint / setTouchPoint / upPageAnim -> viewport
// upContent / onLayoutPageCompleted -> structure
```

`ContentTextView`/`PageView` 只增加返回 `TextPos.copy()` 的选择快照 getter；不得把内部可变 `TextPos` 直接放进状态。`PageDelegate.isRunning`、`isStarted` 和 `setDirection` 的 setter 在原赋值后通知 viewport，使动画停止也能发出最终状态。

- [ ] **Step 5: 在 ReadBookController 暴露双只读 Flow 并从现有真源取样**

```kotlin
private val renderStateStore = ReaderRenderStateStore(ReadBook.snapshot.value)
val readerRenderModel = renderStateStore.model
val readerRenderViewport = renderStateStore.viewport

init {
    activity.lifecycleScope.launch {
        ReadBook.snapshot.collect(renderStateStore::publishSession)
    }
}

override fun onRenderStructureChanged() = publishRenderStructure()
override fun onRenderViewportChanged() = publishRenderViewport()
```

`publishRenderStructure()` 读取 `readView.prevPage/curPage/nextPage.textPage`、`ReadBook.durPageIndex`、选择副本、`ReadBook.msg` 与当前 loading 判定；`publishRenderViewport()` 读取 View 尺寸、触点、delegate 方向/运行状态。`onRefsReady` 首次发布两条流，`upSelectedStart`、`upSelectedEnd`、`onCancelSelect` 在原 UI 更新后重发结构态。

- [ ] **Step 6: 更新 C1 文档状态并运行测试与编译**

Run: `./gradlew --no-daemon :app:testAppDebugUnitTest --tests 'io.legado.app.ui.book.read.ReaderRenderStateStoreTest'`

Run: `./gradlew --no-daemon :app:compileAppDebugKotlin`

Expected: 两条命令均 `BUILD SUCCESSFUL`；旧 `ReadView` 仍是唯一绘制者，代码中没有新增 Compose Canvas、pointerInput 或 feature flag。

- [ ] **Step 7: 停止编译进程并提交接线任务**

```bash
./gradlew --stop
git add app/src/main/java/io/legado/app/ui/book/read/ReadBookController.kt app/src/main/java/io/legado/app/ui/book/read/page/ReadView.kt app/src/main/java/io/legado/app/ui/book/read/page/PageView.kt app/src/main/java/io/legado/app/ui/book/read/page/ContentTextView.kt app/src/main/java/io/legado/app/ui/book/read/page/delegate/PageDelegate.kt docs/dev/track-c-compose-reader-plan.md app/src/test/java/io/legado/app/ui/book/read/ReaderRenderStateStoreTest.kt
git commit -m "refactor(Track C): 并行发布旧阅读器渲染状态"
```
