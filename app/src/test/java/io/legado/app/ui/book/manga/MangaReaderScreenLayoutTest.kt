package io.legado.app.ui.book.manga

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ColorImage
import coil3.Image
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.intercept.Interceptor
import io.legado.app.R
import io.legado.app.help.coil.CoverExtras
import io.legado.app.ui.book.manga.config.MangaScrollMode
import kotlinx.collections.immutable.toImmutableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MangaReaderScreenLayoutTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var imageLoader: ImageLoader
    private val currentPages = pages(1, 57)
    private val reader = mutableStateOf(
        MangaReaderUiState(
            bookUrl = "layout-book", pages = currentPages.toImmutableList(), chapterIndex = 1,
            chapterCount = 3, pageCount = 57, isLoading = false, navigationId = 1,
            settings = MangaReaderSettings(
                preDownloadCount = 0, hideMangaTitle = true, hideFooter = true,
                disableCrossFade = true, disableScale = false,
            ),
        )
    )
    private val visible = mutableStateOf(true)
    private val reports = mutableListOf<MangaReaderIntent.VisibleItemChanged>()
    private val intents = mutableListOf<MangaReaderIntent>()

    /** 按 imageUrl 覆盖原图尺寸，预览可以使用不同的采样比例。 */
    private val imageSizes = mutableMapOf<String, Pair<Int, Int>>()
    private val images = mutableMapOf<String, Image>()

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        application.injectAsAppCtx()
        imageLoader = ImageLoader.Builder(application).components {
            // Match CoverInterceptor: original dimensions also reach owners on memory-cache hits.
            add(Interceptor { chain ->
                imageSizes[chain.request.data.toString()]?.let { (width, height) ->
                    val ratio = width.toFloat() / height
                    chain.request.extras[CoverExtras.MangaFileOwner]?.setAspectRatio(ratio)
                    chain.request.extras[CoverExtras.MangaAspectRatio]?.invoke(ratio)
                }
                chain.proceed()
            })
            // Render the actual reader without source rules, storage or network variability.
            add(Fetcher.Factory<Any> { data, _, _ ->
                // coil3 会把 String 规范化为 coil3.Uri，按 toString 匹配。
                val originalSize = imageSizes[data.toString()]
                val size = originalSize ?: (80 to 800)
                Fetcher {
                    ImageFetchResult(
                        images[data.toString()] ?: ColorImage(
                            color = android.graphics.Color.CYAN,
                            width = size.first, height = size.second,
                        ),
                        false,
                        DataSource.MEMORY
                    )
                }
            })
        }.build()
        compose.setContent {
            val savedState = rememberSaveableStateHolder()
            MaterialTheme {
                if (visible.value) savedState.SaveableStateProvider("reader") {
                    MangaReaderScreen(
                        reader.value,
                        ::onIntent,
                        imageLoader = imageLoader,
                        canHandleBack = false
                    )
                }
            }
        }
        try {
            compose.waitUntil(5_000) {
                val bounds = page(1, 0).getUnclippedBoundsInRoot()
                bounds.bottom - bounds.top > 1_000.dp
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "Image bounds: ${
                    page(
                        1,
                        0
                    ).getUnclippedBoundsInRoot()
                }; events: $intents", error
            )
        }
    }

    @After
    fun tearDown() {
        imageLoader.shutdown()
        images.values.filterIsInstance<BitmapImage>().forEach { it.bitmap.recycle() }
    }

    @Test
    fun `inserting and removing a 300 page prefix keeps the measured image offset`() {
        scrollInsideFirstPage()
        val before = page(1, 0).getUnclippedBoundsInRoot().top.value
        assertTrue(before < -10f)
        compose.runOnIdle {
            reports.clear()
            reader.value = reader.value.copy(
                pages = (pages(0, 300) + currentPages).toImmutableList(),
                currentItemIndex = 300, navigationId = 2,
            )
        }
        compose.waitForIdle()
        assertEquals(before, page(1, 0).getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.runOnIdle {
            assertTrue(reports.all {
                reader.value.pages[it.itemIndex] is MangaReaderItemUi.Page &&
                        (reader.value.pages[it.itemIndex] as MangaReaderItemUi.Page).chapterIndex == 1
            })
            reader.value = reader.value.copy(
                pages = currentPages.toImmutableList(),
                currentItemIndex = 0,
                navigationId = 3
            )
        }
        compose.waitForIdle()
        assertEquals(before, page(1, 0).getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun `returning after the chapter window changes restores image identity and pixel offset`() {
        scrollInsideFirstPage()
        val before = page(1, 0).getUnclippedBoundsInRoot().top.value
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = (pages(0, 300) + currentPages).toImmutableList(),
                currentItemIndex = 300, navigationId = 2,
            )
            visible.value = true
        }
        compose.waitForIdle()
        assertEquals(before, page(1, 0).getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun `explicit seek during a prefix replacement reaches the target image`() {
        scrollInsideFirstPage()
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = (pages(0, 300) + currentPages).toImmutableList(), currentItemIndex = 300,
                navigationId = 2, scrollRequest = MangaScrollRequest(10, 305, false),
            )
        }
        compose.waitForIdle()
        try {
            compose.waitUntil(5_000) { reader.value.scrollRequest == null }
        } catch (error: Throwable) {
            throw AssertionError(
                "Pending seek: ${reader.value.scrollRequest}; events: $intents",
                error
            )
        }
        assertEquals(5, reader.value.currentPage)
        assertEquals(0f, page(1, 5).getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun `double tap keeps the image point under the gesture`() {
        scrollInsideFirstPage()
        val before = page(1, 0).getUnclippedBoundsInRoot()
        var focalY = 0f
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            focalY = centerY / compose.density.density
            doubleClick(Offset(centerX, centerY))
        }
        val after = page(1, 0).getUnclippedBoundsInRoot()
        assertEquals(
            2.5f,
            (after.right - after.left).value / (before.right - before.left).value,
            0.02f
        )
        assertEquals(
            (focalY - before.top.value) / (before.bottom - before.top).value,
            (focalY - after.top.value) / (after.bottom - after.top).value,
            0.001f,
        )
    }

    @Test
    fun `zoom and pan survive leaving while a chapter prefix is inserted`() {
        scrollInsideFirstPage()
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            doubleClick(Offset(width * 0.75f, centerY))
        }
        val before = page(1, 0).getUnclippedBoundsInRoot()
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = (pages(0, 300) + currentPages).toImmutableList(),
                currentItemIndex = 300,
                navigationId = 2,
            )
            visible.value = true
        }
        compose.waitForIdle()
        val after = page(1, 0).getUnclippedBoundsInRoot()
        assertEquals(before.top.value, after.top.value, 0.5f)
        assertEquals(before.left.value, after.left.value, 0.5f)
        assertEquals(before.right.value, after.right.value, 0.5f)
        assertEquals(before.bottom.value, after.bottom.value, 0.5f)
    }

    @Test
    fun `replacing a visible chapter prompt anchors the image below it`() {
        val waiting = MangaReaderItemUi.ChapterTransition(
            key = "previous:waiting",
            direction = MangaChapterTransitionDirection.PREVIOUS,
            targetChapterIndex = 0,
            currentChapterName = "chapter-1",
            targetChapterName = "chapter-0",
            targetStatus = MangaChapterTransitionStatus.WAITING,
        )
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = (listOf(waiting) + currentPages).toImmutableList(),
                currentItemIndex = 1,
                navigationId = 2,
            )
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        val before = page(1, 0).getUnclippedBoundsInRoot().top.value
        assertTrue(before > 0)
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = (pages(0, 300) + waiting.copy(
                    key = "previous:ready",
                    targetStatus = MangaChapterTransitionStatus.READY
                ) + currentPages)
                    .toImmutableList(),
                currentItemIndex = 301, navigationId = 3,
            )
        }
        compose.waitForIdle()
        assertEquals(before, page(1, 0).getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun `leaving during a drag clears the transient scrolling flag`() {
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            down(Offset(centerX, centerY))
            moveBy(Offset(0f, -100f))
            moveBy(Offset(0f, -100f))
        }
        compose.runOnIdle {
            assertTrue(
                intents.filterIsInstance<MangaReaderIntent.PagerScrollChanged>().last().inProgress
            )
            visible.value = false
        }
        compose.runOnIdle {
            assertEquals(
                false,
                intents.filterIsInstance<MangaReaderIntent.PagerScrollChanged>().last().inProgress
            )
        }
    }

    @Test
    fun `an ultra tall stitched strip renders in segments without constraints crash`() {
        // 400px 宽、比例 0.001：显示高度 400000px，超过 Constraints 可表示上限（#2475，
        // 修复前 Constraints.fixed(400, 400001) 在测量期直接抛异常）。应拆成多段行渲染。
        imageSizes["manga://1/long/0"] = 80 to 80_000
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = longStripPages().toImmutableList(),
                currentItemIndex = 0,
                navigationId = 2,
            )
        }
        compose.waitForIdle()
        // 比例异步到达：首行高度从兜底整屏（800dp）切到约 80000dp 的分段即说明已拆分。
        try {
            compose.waitUntil(5_000) {
                val bounds = page(1, 0).getUnclippedBoundsInRoot()
                bounds.bottom - bounds.top > 10_000.dp
            }
        } catch (error: Throwable) {
            throw AssertionError(
                "bounds: ${page(1, 0).getUnclippedBoundsInRoot()}; events: $intents", error,
            )
        }
        // ceil(400000 / 87380) = 5 段；瞬时跳到第 4 段（列表下标 3）不会产生 scroll-idle，
        // 追加一次小滑动驱动可见页上报，断言仍按页下标 0 上报。
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        page(1, 0).assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.6f), 300)
        }
        try {
            compose.waitUntil(5_000) {
                intents.filterIsInstance<MangaReaderIntent.VisibleItemChanged>()
                    .any { it.navigationId == 2L }
            }
        } catch (error: Throwable) {
            throw AssertionError("events: $intents", error)
        }
        val report = intents.filterIsInstance<MangaReaderIntent.VisibleItemChanged>()
            .last { it.navigationId == 2L }
        assertEquals(0, report.itemIndex)
        assertTrue(reader.value.pages[report.itemIndex] is MangaReaderItemUi.Page)
    }

    @Test
    fun `each segment shows its own preview region`() {
        showStripWithColoredSegments()
        val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta)
        colors.forEachIndexed { index, expected ->
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            page(1, 0).assertIsDisplayed()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            assertEquals("segment $index", expected, pixels[pixels.width / 2, pixels.height / 2])
        }
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            doubleClick(Offset(centerX, centerY))
        }
        page(1, 0).assertIsDisplayed()
        val zoomed = compose.onRoot().captureToImage().toPixelMap()
        assertEquals(Color.Magenta, zoomed[zoomed.width / 2, zoomed.height / 2])
    }

    @Test
    fun `gap mode joins segments seamlessly and keeps gaps between pages`() {
        showStripWithColoredSegments(withGap = true)
        fun boundaryColors(index: Int): List<Color> {
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            compose.onNode(hasScrollToIndexAction()).performTouchInput {
                swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.8f), 1_000)
            }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            return (0 until pixels.height).map { pixels[pixels.width / 2, it] }
        }

        val segmentJoin = boundaryColors(1)
        assertTrue(
            "both segments must be visible", Color.Red in segmentJoin && Color.Green in segmentJoin,
        )
        assertEquals("no gap inside an image", 0, segmentJoin.count { it == Color.Black })

        val pageJoin = boundaryColors(5)
        assertTrue(
            "both pages must be visible", Color.Magenta in pageJoin && Color.Cyan in pageJoin,
        )
        assertEquals(
            "one page gap", with(compose.density) { 8.dp.roundToPx() },
            pageJoin.count { it == Color.Black },
        )
    }

    private fun showStripWithColoredSegments(withGap: Boolean = false) {
        // The original is 1:1000; integer downsampling changes this preview to 1:500.
        // Each band must still cover exactly one segment of the 400000px virtual image.
        imageSizes["manga://1/long/0"] = 80 to 80_000
        val bitmap = Bitmap.createBitmap(2, 1_000, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(
            android.graphics.Color.RED, android.graphics.Color.GREEN, android.graphics.Color.BLUE,
            android.graphics.Color.YELLOW, android.graphics.Color.MAGENTA,
        )
        colors.forEachIndexed { band, color ->
            repeat(200) { y -> repeat(2) { x -> bitmap.setPixel(x, band * 200 + y, color) } }
        }
        images["manga://1/long/0"] = bitmap.asImage()
        compose.runOnIdle {
            reader.value = reader.value.copy(
                pages = longStripPages().toImmutableList(), currentItemIndex = 0, navigationId = 2,
                settings = reader.value.settings.copy(
                    scrollMode = if (withGap) MangaScrollMode.WEBTOON_WITH_GAP else MangaScrollMode.WEBTOON,
                ),
            )
        }
        compose.waitUntil(5_000) {
            val bounds = page(1, 0).getUnclippedBoundsInRoot()
            bounds.bottom - bounds.top > 10_000.dp
        }
    }

    private fun longStripPages() = currentPages.mapIndexed { index, page ->
        if (index == 0) {
            page.copy(imageUrl = "manga://1/long/0", key = "page:1:0:manga://1/long/0")
        } else {
            page
        }
    }

    private fun scrollInsideFirstPage() {
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            swipe(Offset(centerX, height * 0.7f), Offset(centerX, height * 0.4f), 600)
        }
        compose.waitForIdle()
    }

    private fun page(chapter: Int, index: Int) = compose.onNodeWithContentDescription(
        RuntimeEnvironment.getApplication()
            .getString(R.string.manga_reader_page_description, "chapter-$chapter", index + 1),
    )

    private fun onIntent(intent: MangaReaderIntent) {
        intents += intent
        if (intent is MangaReaderIntent.VisibleItemChanged) {
            reports += intent
            val state = reader.value
            val item = state.pages.getOrNull(intent.itemIndex) as? MangaReaderItemUi.Page ?: return
            if (intent.navigationId == state.navigationId &&
                acceptsMangaVisibleItem(state.scrollRequest?.itemIndex, intent.itemIndex)
            ) reader.value = state.copy(
                currentItemIndex = intent.itemIndex, currentPage = item.pageIndex,
                scrollRequest = null,
            )
        }
    }

    private fun pages(chapter: Int, count: Int) = (0 until count).map {
        MangaReaderItemUi.Page(
            key = "page:$chapter:$it", imageUrl = "manga://$chapter/$it", bookUrl = "layout-book",
            chapterIndex = chapter, chapterCount = 3, pageIndex = it, pageCount = count,
            chapterName = "chapter-$chapter", chapterSubject = "chapter-$chapter",
            loadState = MangaPageLoadState.Ready,
        )
    }
}
