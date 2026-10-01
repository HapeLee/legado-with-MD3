package io.legado.app.core.ui.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import io.legado.app.ui.book.readaloud.morph.PlayerPanelCornerRadii
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookMorphTest {

    @Before
    fun setUp() {
        BookCoverMorphAnchors.remove("test-key")
    }

    @Test
    fun bookCoverMorphAnchorsStoresAndRetrieves() {
        val bounds = Rect(100f, 200f, 300f, 500f)
        BookCoverMorphAnchors.report(
            key = "book-cover:http://book.test/1",
            bounds = bounds,
            cornerRadiusPx = 16f,
            bookName = "测试书名",
            author = "测试作者",
            coverPath = "/path/to/cover.jpg",
            badgeText = "12",
            showBadgeDot = true,
            leftBottomText = "小说",
        )

        // 精确匹配
        val exact = BookCoverMorphAnchors.get("book-cover:http://book.test/1")
        assertNotNull(exact)
        assertEquals(bounds, exact?.bounds)
        assertEquals(16f, exact?.cornerRadiusPx ?: 0f, 0.001f)
        assertEquals("测试书名", exact?.bookName)
        assertEquals("12", exact?.badgeText)
        assertTrue(exact?.showBadgeDot == true)
        assertEquals("小说", exact?.leftBottomText)

        // 包含关系兜底匹配（传入 url 也能找到包含此 url 的 anchor）
        val fuzzy = BookCoverMorphAnchors.get("http://book.test/1")
        assertNotNull(fuzzy)
        assertEquals("测试书名", fuzzy?.bookName)
        assertEquals("12", fuzzy?.badgeText)
    }

    @Test
    fun bookCoverMorphAnchorsTracksActiveMorphKey() {
        assertFalse(BookCoverMorphAnchors.isMorphing("book-cover:http://book.test/1"))

        BookCoverMorphAnchors.activeMorphKey = "book-cover:http://book.test/1"
        assertTrue(BookCoverMorphAnchors.isMorphing("book-cover:http://book.test/1"))
        // 书架带 sourceId 的 key 也能正确识别匹配
        assertTrue(BookCoverMorphAnchors.isMorphing("book-cover:bookshelf:0:http://book.test/1"))
        // 其他书籍不被隐藏
        assertFalse(BookCoverMorphAnchors.isMorphing("book-cover:http://book.test/2"))
        assertFalse(BookCoverMorphAnchors.isMorphing(null))

        BookCoverMorphAnchors.clearActiveMorphKey("book-cover:http://book.test/1")
        assertFalse(BookCoverMorphAnchors.isMorphing("book-cover:http://book.test/1"))
    }

    @Test
    fun bookCoverMorphAnchorsDynamicOriginHiding() = runTest {
        val key = "book-cover:http://book.test/dynamic"
        val state = BookMorphState(Animatable(0f), Density(1f))

        // 未激活时，源封面不隐藏
        assertFalse(BookCoverMorphAnchors.isOriginCoverHidden(key))

        // 绑定活跃形变，初始 progress = 0 时源封面不隐藏
        BookCoverMorphAnchors.setActiveMorph(key, state)
        assertTrue(BookCoverMorphAnchors.isMorphing(key))
        assertFalse(BookCoverMorphAnchors.isOriginCoverHidden(key))

        // 动画进行中 progress > 0，源封面隐藏（让位给飞行封面）
        state.progress.snapTo(0.01f)
        assertTrue(BookCoverMorphAnchors.isOriginCoverHidden(key))
        state.progress.snapTo(0.5f)
        assertTrue(BookCoverMorphAnchors.isOriginCoverHidden(key))
        state.progress.snapTo(1f)
        assertTrue(BookCoverMorphAnchors.isOriginCoverHidden(key))

        // 返回动画完成归零 progress = 0，源封面零延迟瞬间显现
        state.progress.snapTo(0f)
        assertFalse(BookCoverMorphAnchors.isOriginCoverHidden(key))

        // 清理活跃状态
        BookCoverMorphAnchors.clearActiveMorph(key)
        assertFalse(BookCoverMorphAnchors.isMorphing(key))
        assertFalse(BookCoverMorphAnchors.isOriginCoverHidden(key))
    }

    @Test
    fun bookMorphStateCalculatesFramesWhenAnchorPresent() = runTest {
        val state = BookMorphState(Animatable(0f), Density(1f))
        state.reportScreenBounds(Rect(0f, 0f, 1080f, 2400f))
        state.reportScreenCorners(PlayerPanelCornerRadii.all(48f))

        val anchor = BookCoverMorphAnchor(
            bounds = Rect(100f, 200f, 300f, 500f),
            cornerRadiusPx = 16f,
            bookName = "书名",
            author = "作者",
            coverPath = "path",
        )
        state.setAnchor(anchor)
        assertFalse(state.fadeOnlyOpening)

        // 初始状态 progress = 0
        val frame0 = state.panelFrame()
        assertNotNull(frame0)
        assertEquals(100f, frame0?.bounds?.left ?: 0f, 0.01f)
        assertEquals(200f, frame0?.bounds?.top ?: 0f, 0.01f)

        // 展开到中间 progress = 0.5
        state.progress.snapTo(0.5f)
        val frameHalf = state.panelFrame()
        assertNotNull(frameHalf)
        assertTrue((frameHalf?.bounds?.width ?: 0f) > 200f)
        assertTrue((frameHalf?.bounds?.width ?: 0f) < 1080f)
        assertFalse(state.expanded)

        // 完全展开 progress = 1.0
        state.progress.snapTo(1f)
        assertTrue(state.expanded)
        assertEquals(1f, state.veil, 0.001f)
        val frameEnd = state.panelFrame()
        assertNotNull(frameEnd)
        assertEquals(1080f, frameEnd?.bounds?.width ?: 0f, 0.01f)
        assertEquals(2400f, frameEnd?.bounds?.height ?: 0f, 0.01f)
    }

    @Test
    fun bookMorphStateFallsBackToFadeOnlyWithoutAnchor() = runTest {
        val state = BookMorphState(Animatable(0f), Density(1f))
        state.reportScreenBounds(Rect(0f, 0f, 1080f, 2400f))
        state.setAnchor(null)

        assertTrue(state.fadeOnlyOpening)
        assertNull(state.panelFrame())

        state.progress.snapTo(0.5f)
        assertEquals(0.5f, state.veil, 0.001f)
    }

    @Test
    fun bookCoverAlphaTransitionsEarlyInAnimation() {
        // 打开时：在几何飞行中段（0.10 -> 0.50）平滑淡出，避免封面在展开中途与内容双重叠加
        assertEquals(1f, computeBookCoverAlpha(0f, isCollapsing = false), 0.001f)
        assertEquals(1f, computeBookCoverAlpha(0.10f, isCollapsing = false), 0.001f)
        assertEquals(0.5f, computeBookCoverAlpha(0.30f, isCollapsing = false), 0.001f)
        assertEquals(0f, computeBookCoverAlpha(0.50f, isCollapsing = false), 0.001f)
        assertEquals(0f, computeBookCoverAlpha(1f, isCollapsing = false), 0.001f)

        // 收起时：在动画中段（0.50 -> 0.10）渐显回到书架卡片
        assertEquals(0f, computeBookCoverAlpha(1f, isCollapsing = true), 0.001f)
        assertEquals(0f, computeBookCoverAlpha(0.50f, isCollapsing = true), 0.001f)
        assertEquals(0.5f, computeBookCoverAlpha(0.30f, isCollapsing = true), 0.001f)
        assertEquals(1f, computeBookCoverAlpha(0.10f, isCollapsing = true), 0.001f)
        assertEquals(1f, computeBookCoverAlpha(0f, isCollapsing = true), 0.001f)
    }

    @Test
    fun pendingCollapseVelocityIsInheritedOnceWhileCollapsing() = runTest {
        val state = BookMorphState(Animatable(1f), Density(1f))

        state.progress.snapTo(0.73f)
        state.recordCollapseVelocity(-2.5f)
        assertEquals(-2.5f, state.consumeCollapseVelocity(), 0.001f)
        // 只生效一次：重复收起不会再吃到上一次手势的动量
        assertEquals(0f, state.consumeCollapseVelocity(), 0.001f)
    }

    @Test
    fun pendingCollapseVelocityExpiresWhenGestureWasRolledBack() = runTest {
        val state = BookMorphState(Animatable(1f), Density(1f))

        // 手势结束但业务未授权关闭（例：弹出加入书架确认框），进度被恢复回 1
        state.progress.snapTo(0.73f)
        state.recordCollapseVelocity(-2.5f)
        state.progress.snapTo(1f)
        assertEquals(0f, state.consumeCollapseVelocity(), 0.001f)
    }

    @Test
    fun bookBadgeAlphaTransitionsGradually() {
        // 打开时：在极前期（0.0 -> 0.15）平滑淡出，交接给阅读/详情页
        assertEquals(1f, computeBookBadgeAlpha(0f, isCollapsing = false), 0.001f)
        assertEquals(0.5f, computeBookBadgeAlpha(0.075f, isCollapsing = false), 0.001f)
        assertEquals(0f, computeBookBadgeAlpha(0.15f, isCollapsing = false), 0.001f)
        assertEquals(0f, computeBookBadgeAlpha(0.5f, isCollapsing = false), 0.001f)
        assertEquals(0f, computeBookBadgeAlpha(1f, isCollapsing = false), 0.001f)

        // 收起时：在动画末段（0.20 -> 0.0）平滑渐显，与书架原生角标零时差无缝衔接
        assertEquals(0f, computeBookBadgeAlpha(1f, isCollapsing = true), 0.001f)
        assertEquals(0f, computeBookBadgeAlpha(0.50f, isCollapsing = true), 0.001f)
        assertEquals(0f, computeBookBadgeAlpha(0.20f, isCollapsing = true), 0.001f)
        assertEquals(0.5f, computeBookBadgeAlpha(0.10f, isCollapsing = true), 0.001f)
        assertEquals(0.75f, computeBookBadgeAlpha(0.05f, isCollapsing = true), 0.001f)
        assertEquals(1f, computeBookBadgeAlpha(0f, isCollapsing = true), 0.001f)
    }

    @Test
    fun bookMorphStateTracksUpdatedAnchorWhenBookshelfReorders() = runTest {
        val state = BookMorphState(Animatable(1f), Density(1f))
        state.reportScreenBounds(Rect(0f, 0f, 1080f, 2400f))
        state.reportScreenCorners(PlayerPanelCornerRadii.all(48f))

        val key = "book-cover:http://book.test/reorder"
        // 初始位置在第 2 个位置 (400, 300)
        BookCoverMorphAnchors.report(
            key = key,
            bounds = Rect(400f, 300f, 600f, 600f),
            cornerRadiusPx = 16f,
            bookName = "书名",
        )
        state.setAnchor(BookCoverMorphAnchors.get(key), key)

        // 阅读后书架重新排序，移动到第 1 个位置 (50, 100)
        BookCoverMorphAnchors.report(
            key = key,
            bounds = Rect(50f, 100f, 250f, 400f),
            cornerRadiusPx = 16f,
            bookName = "书名",
        )

        // 开始收起
        state.onPredictiveBackStart()
        assertTrue(state.isCollapsing)
        state.progress.snapTo(0f)

        val frame = state.panelFrame()
        assertNotNull(frame)
        // 终点必须精准追随到重排后的第 1 个位置 (50, 100)，而不是旧位置 (400, 300)
        assertEquals(50f, frame?.bounds?.left ?: 0f, 0.01f)
        assertEquals(100f, frame?.bounds?.top ?: 0f, 0.01f)
    }

    @Test
    fun isAnchorVisibleInScreenCorrectlyIdentifiesVisibility() {
        val screen = Rect(0f, 0f, 1080f, 2400f)

        // 完全在屏幕内
        assertTrue(isAnchorVisibleInScreen(Rect(50f, 100f, 250f, 400f), screen))

        // 完全在屏幕上方之外
        assertFalse(isAnchorVisibleInScreen(Rect(50f, -500f, 250f, -200f), screen))

        // 完全在屏幕下方之外
        assertFalse(isAnchorVisibleInScreen(Rect(50f, 2500f, 250f, 2800f), screen))

        // 完全在屏幕左侧之外
        assertFalse(isAnchorVisibleInScreen(Rect(-300f, 100f, -50f, 400f), screen))

        // 大部分在屏幕内（底部露出一半以上）
        assertTrue(isAnchorVisibleInScreen(Rect(50f, 2300f, 250f, 2500f), screen))

        // 仅擦边小于 25% 面积可见
        assertFalse(isAnchorVisibleInScreen(Rect(50f, 2380f, 250f, 2680f), screen))

        // 空矩形
        assertFalse(isAnchorVisibleInScreen(Rect.Zero, screen))
    }

    @Test
    fun bookMorphStateNeverFliesOffScreenWhenAnchorIsOffScreen() = runTest {
        val state = BookMorphState(Animatable(1f), Density(1f))
        val screen = Rect(0f, 0f, 1080f, 2400f)
        state.reportScreenBounds(screen)
        state.reportScreenCorners(PlayerPanelCornerRadii.all(48f))

        val key = "book-cover:http://book.test/offscreen"
        // 初始位置在屏幕下方或外面
        val initialOffscreen = Rect(50f, -800f, 250f, -500f)
        BookCoverMorphAnchors.report(
            key = key,
            bounds = initialOffscreen,
            cornerRadiusPx = 16f,
            bookName = "屏幕外书名",
        )
        state.setAnchor(BookCoverMorphAnchors.get(key), key)

        // 收起时，如果锚点在屏幕外（例如重排到列表顶部但当前滚动在下方）
        state.onPredictiveBackStart()
        assertTrue(state.isCollapsing)
        state.progress.snapTo(0f)

        val frame = state.panelFrame()
        assertNotNull(frame)
        // 验证收敛矩形绝对不飞出屏幕（top >= 0 且 bottom <= 2400）
        val frameBounds = frame?.bounds ?: Rect.Zero
        assertTrue("Frame bounds left should be inside screen", frameBounds.left >= screen.left)
        assertTrue("Frame bounds top should be inside screen", frameBounds.top >= screen.top)
        assertTrue("Frame bounds right should be inside screen", frameBounds.right <= screen.right)
        assertTrue(
            "Frame bounds bottom should be inside screen",
            frameBounds.bottom <= screen.bottom
        )
    }

    @Test
    fun bookMorphStateContinuousCoverTransitionWhenTargetCoverPresent() = runTest {
        val state = BookMorphState(Animatable(0f), Density(1f))
        val screen = Rect(0f, 0f, 1080f, 2400f)
        state.reportScreenBounds(screen)
        state.reportScreenCorners(PlayerPanelCornerRadii.all(48f))

        val start = Rect(100f, 300f, 300f, 580f)
        val end = Rect(50f, 100f, 350f, 520f)
        val anchor = BookCoverMorphAnchor(
            bounds = start,
            cornerRadiusPx = 16f,
            bookName = "测试书名",
            author = "测试作者",
            coverPath = "/path/cover.jpg",
        )
        state.setAnchor(anchor)
        state.reportCoverEnd(end, cornerRadiusPx = 12f)

        assertTrue(state.hasCoverEnd)

        // 初始状态 progress = 0: coverFrame 起点
        val frame0 = state.coverFrame()
        assertNotNull(frame0)
        assertEquals(start.left, frame0?.bounds?.left ?: 0f, 0.01f)
        assertEquals(start.top, frame0?.bounds?.top ?: 0f, 0.01f)
        assertEquals(0f, state.coverAlpha, 0.001f) // 端点由页面/书架自身显示

        // 动画进行中 progress = 0.5: 封面保持连续全程可见 (alpha = 1f)
        state.progress.snapTo(0.5f)
        val frameHalf = state.coverFrame()
        assertNotNull(frameHalf)
        assertTrue((frameHalf?.bounds?.left ?: 0f) in 50f..100f)
        assertEquals(1f, state.coverAlpha, 0.001f)

        // 完全展开 progress = 1: coverFrame 到达目标位置
        state.progress.snapTo(1f)
        val frameEnd = state.coverFrame()
        assertNotNull(frameEnd)
        assertEquals(end.left, frameEnd?.bounds?.left ?: 0f, 0.01f)
        assertEquals(end.top, frameEnd?.bounds?.top ?: 0f, 0.01f)
        assertEquals(0f, state.coverAlpha, 0.001f) // 展开完成交接给详情页封面
    }

    @Test
    fun bookMorphStateHasTargetCoverKeepsAlphaAndTargetStartBeforeCoverEndReported() = runTest {
        val state = BookMorphState(Animatable(0f), Density(1f), hasTargetCover = true)
        val screen = Rect(0f, 0f, 1080f, 2400f)
        state.reportScreenBounds(screen)

        val start = Rect(100f, 300f, 300f, 580f)
        val anchor = BookCoverMorphAnchor(
            bounds = start,
            cornerRadiusPx = 16f,
            bookName = "测试书名",
            author = "测试作者",
            coverPath = "/path/cover.jpg",
            bookUrl = "http://book.test/1",
        )
        state.setAnchor(anchor)

        // 目标封面尚未上报终点（第 0 帧）
        assertFalse(state.hasCoverEnd)
        assertEquals("http://book.test/1", state.bookUrl)

        // targetStart 稳妥锚定在起点
        val (targetStartBounds, targetStartRadius) = state.targetStart()
        assertEquals(start, targetStartBounds)
        assertEquals(16f, targetStartRadius, 0.001f)

        // progress = 0 时为 0f，progress > 0 即连续全程可见，不随漫画/小说渐隐
        assertEquals(0f, state.coverAlpha, 0.001f)
        state.progress.snapTo(0.05f)
        assertEquals(1f, state.coverAlpha, 0.001f)
        state.progress.snapTo(0.5f)
        assertEquals(1f, state.coverAlpha, 0.001f)
    }
}
