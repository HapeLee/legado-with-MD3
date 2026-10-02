package io.legado.app.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModalOverlaySceneStrategyTest {
    private val strategy = ModalOverlaySceneStrategy()
    private val scope = SceneStrategyScope<NavKey>()

    @Test
    fun `search predictive back updates both overlay and reader from one progress`() = runBlocking {
        val animation = SearchOverlayAnimation(underlayKey = "reader")
        animation.progress.snapTo(1f)
        animation.opacity.snapTo(1f)
        animation.underlayOpacity.snapTo(0f)

        animation.previewBack(0.4f)

        assertEquals(true, animation.removing)
        assertEquals(1f - FastOutSlowInEasing.transform(0.4f), animation.progress.value, 0.0001f)
        assertEquals(animation.progress.value, animation.opacity.value, 0.0001f)
        assertEquals(
            LinearOutSlowInEasing.transform(0.4f),
            animation.underlayOpacity.value,
            0.0001f
        )
    }

    @Test
    fun `reader keeps the same book info overlay and home parent`() {
        val home = entry(MainRouteHome)
        val info = entry(MainRouteBookInfo("Book", "Author", "book-url"), overlay = true)
        val reader = entry(MainRouteReadBook(bookUrl = "book-url"), overlay = true)
        val infoBeforeReading = calculate(listOf(home, info)) as OverlayScene<NavKey>
        val readerScene = calculate(listOf(home, info, reader)) as OverlayScene<NavKey>

        // NavDisplay recursively calculates the background from overlaidEntries.
        // Keeping only info turns it into a SinglePane root and loses its overlay owner.
        val infoUnderReader = calculate(readerScene.overlaidEntries)
        assertNotNull("Book info must remain an overlay while reading", infoUnderReader)
        assertEquals(infoBeforeReading, infoUnderReader)
        assertEquals(listOf(home), (infoUnderReader as OverlayScene<NavKey>).previousEntries)
        assertEquals(reader.metadata, readerScene.metadata)

        val infoAfterReading = calculate(readerScene.previousEntries)
        assertEquals(infoBeforeReading, infoAfterReading)
        assertEquals(listOf(home), infoAfterReading!!.previousEntries)
    }

    @Test
    fun `root destination is never an overlay even with overlay metadata`() {
        val info = entry(MainRouteBookInfo("Book", "Author", "book-url"), overlay = true)
        assertNull(calculate(listOf(info)))
        assertNull(calculate(listOf(entry(MainRouteHome))))
    }

    /**
     * 压在阅读界面之上的目的地（全文搜索正文）必须自己登记成叠层。
     *
     * 只要栈顶没有叠层元数据，本策略就返回 null，`NavDisplay` 会退回到
     * `SinglePaneSceneStrategy`：阅读界面所在叠层被整帧丢弃、`overlayScenes` 清空，
     * `previousScenes` 也不再有可回退的上一站。表现就是返回阅读界面时重播入场动画，
     * 且返回手势不再被阅读界面或导航层接管，直接结束 Activity。
     */
    @Test
    fun `destination above a reader overlay must itself declare overlay metadata`() {
        val home = entry(MainRouteHome)
        val reader = entry(MainRouteReadBook(bookUrl = "book-url"), overlay = true)
        val searchOverlay = entry(
            MainRouteSearchContent(bookUrl = "book-url"), overlay = true, searchSlide = true,
        )
        val searchPlain = entry(MainRouteSearchContent(bookUrl = "book-url"))

        val searchScene = calculate(listOf(home, reader, searchOverlay)) as OverlayScene<NavKey>
        val plainScene = calculate(listOf(home, reader, searchPlain))
        // 搜索正文这一叠层之下必须仍然是 [主页, 阅读界面]：阅读界面保持组合，
        // 返回时 `previousScenes` 也有可回退的一站。
        assertEquals(
            searchScene.overlaidEntries.map { it.contentKey },
            listOf(home, reader).map { it.contentKey },
        )
        assertEquals(searchScene, calculate(listOf(home, reader, searchOverlay)))
        assertEquals(searchOverlay.metadata, searchScene.metadata)

        // 反例：普通单栏目的地会让阅读界面这一层没有场景承载者。
        assertNull(
            "普通单栏目的地压在阅读界面上会丢弃阅读界面叠层",
            plainScene,
        )
    }

    private fun calculate(entries: List<NavEntry<NavKey>>): Scene<NavKey>? =
        with(strategy) { scope.calculateScene(entries) }

    private fun entry(
        key: NavKey,
        overlay: Boolean = false,
        searchSlide: Boolean = false,
    ): NavEntry<NavKey> =
        NavEntry(
            key = key,
            metadata = (if (overlay) ModalOverlaySceneStrategy.modalOverlay() else emptyMap()) +
                    (if (searchSlide) ModalOverlaySceneStrategy.searchSlide() else emptyMap()),
        ) {}
}
