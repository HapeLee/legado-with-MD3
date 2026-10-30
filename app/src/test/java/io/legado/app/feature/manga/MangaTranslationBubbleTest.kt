package io.legado.app.feature.manga

import android.app.Application
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import io.legado.app.ui.theme.LocalAppUiConfiguration
import io.legado.app.domain.model.settings.AppUiConfiguration
import io.legado.app.domain.model.settings.ThemeSettings
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.chrisbanes.haze.HazeState
import io.legado.app.ui.theme.responsiveHazeSource
import org.junit.Assert.*
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
@Config(application = Application::class, sdk = [35], qualifiers = "zh-rCN-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MangaTranslationBubbleTest {
    @get:Rule val compose = createComposeRule()
    private var automaticClicks = 0
    private var clearClicks = 0
    private var lastMove: Pair<Boolean, Int>? = null
    private val controls = mutableStateOf(MangaTranslationControls())
    @Before fun setup() { RuntimeEnvironment.getApplication().injectAsAppCtx() }
    private fun content(dark: Boolean = false, eInk: Boolean = false, blurEnabled: Boolean = true, patterned: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalAppUiConfiguration provides AppUiConfiguration(theme = ThemeSettings(enableBlur = blurEnabled))) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val haze = remember { HazeState() }
                val dockRight = remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().responsiveHazeSource(haze)
                        .background(if (dark) Color(0xFF222629) else Color(0xFFE7E9EB))
                        .drawBehind {
                            if (patterned) {
                                drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(size.width, size.height / 2f))
                                drawRect(Color.White, topLeft = Offset(0f, size.height / 2f), size = androidx.compose.ui.geometry.Size(size.width, size.height / 2f))
                            }
                        })
                    MangaTranslationBubble(controls.value, dockRight.value, 500, eInk, haze, false,
                        { edge, height -> lastMove = edge to height; dockRight.value = edge },
                        { automaticClicks++; controls.value = controls.value.copy(automatic = !controls.value.automatic) },
                        { controls.value = controls.value.copy(showTranslation = !controls.value.showTranslation) }, { clearClicks++ })
                }
            }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.waitForIdle()
    }
    private fun open() {
        compose.onNodeWithContentDescription("漫画翻译").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(600)
    }
    @Test fun `idle retracts and can be reopened`() {
        content(); open()
        compose.onNodeWithContentDescription("自动翻译").assertExists()
        compose.mainClock.advanceTimeBy(5500)
        compose.onNodeWithContentDescription("自动翻译").assertDoesNotExist()
        open()
        compose.onNodeWithContentDescription("自动翻译").assertExists()
    }
    @Test fun `action resets five second timer`() {
        content(); open()
        compose.mainClock.advanceTimeBy(3500)
        compose.onNodeWithContentDescription("自动翻译").performClick()
        compose.mainClock.advanceTimeBy(3500)
        compose.onNodeWithContentDescription("自动翻译").assertExists()
        assertEquals(1, automaticClicks)
        compose.mainClock.advanceTimeBy(2100)
        compose.onNodeWithContentDescription("自动翻译").assertDoesNotExist()
    }
    @Test fun `progress follows chapter counts while running paused and completed`() {
        content(); open()
        compose.onNodeWithText("0/0").assertExists()
        compose.runOnIdle { controls.value = controls.value.copy(automatic = true, completed = 12, total = 36, processing = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("12/36").assertExists()
        compose.onNodeWithContentDescription("自动翻译").assertIsSelected()
        compose.onNodeWithContentDescription("自动翻译").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "已启用，12/36"))
        compose.runOnIdle { controls.value = controls.value.copy(automatic = false, processing = false) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("12/36").assertExists()
        compose.onNodeWithContentDescription("自动翻译").assertIsNotSelected()
        compose.runOnIdle { controls.value = controls.value.copy(completed = 36) }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("36/36").assertExists()
    }
    @Test fun `display label and selection follow the current display mode`() {
        content(); open()
        compose.onNodeWithText("译文").assertExists()
        compose.onNodeWithContentDescription("译文/原图").assertIsSelected().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("原图").assertExists()
        compose.onNodeWithContentDescription("译文/原图").assertIsNotSelected()
        compose.onNodeWithContentDescription("译文/原图").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("译文/原图").assertIsSelected()
        compose.onNodeWithText("译文").assertExists()
    }
    @Test fun `long progress is displayed completely on one line`() {
        controls.value = MangaTranslationControls(completed = 12345, total = 67890)
        content(); open()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("12345/67890", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
        assertEquals(1, results.single().lineCount)
        assertFalse("Progress must shrink rather than clip", results.single().hasVisualOverflow)
    }
    @Test fun `all actions show press feedback and clear dispatches without delay`() {
        content(); open()
        listOf("自动翻译", "译文/原图", "清除").forEachIndexed { index, description ->
            val node = compose.onNodeWithContentDescription(description)
            val before = node.captureToImage().toPixelMap()
            node.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(160)
            val after = node.captureToImage().toPixelMap()
            var difference = 0f
            for (y in 0 until minOf(before.height, after.height)) for (x in 0 until minOf(before.width, after.width)) {
                difference += kotlin.math.abs(before[x, y].red - after[x, y].red)
            }
            assertTrue("$description must visibly react to press", difference / (before.width * before.height) > .005f)
            node.performTouchInput { up() }
            if (index == 2) compose.runOnIdle { assertEquals("Clear must dispatch on release", 1, clearClicks) }
            compose.mainClock.advanceTimeBy(160)
        }
        compose.runOnIdle { assertEquals(1, clearClicks) }
        compose.onNodeWithContentDescription("清除").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("清除").assertDoesNotExist()
    }
    @Test fun `dragging still snaps the enlarged main bubble to the opposite dock`() {
        content(); open()
        compose.onNodeWithContentDescription("漫画翻译").performTouchInput {
            down(center)
            moveBy(Offset(260f, 60f))
            up()
        }
        compose.runOnIdle {
            assertTrue("Drag must persist the new dock", requireNotNull(lastMove).first)
            assertTrue(requireNotNull(lastMove).second in 0..1000)
        }
        compose.mainClock.advanceTimeBy(700)
        open()
        val bounds = compose.onNodeWithContentDescription("漫画翻译").getUnclippedBoundsInRoot()
        assertTrue("Main bubble must reopen at the right edge", bounds.left.value > 300f)
    }
    @Test fun `enlarged bubbles keep their touch bounds`() {
        content(); open()
        listOf("漫画翻译", "自动翻译", "译文/原图", "清除").forEach { description ->
            compose.onNodeWithContentDescription(description).assertWidthIsEqualTo(48.dp)
                .assertHeightIsEqualTo(48.dp)
        }
    }
    @Test fun `translation settings expose independent bubble control and model subpage`() {
        val state = androidx.compose.runtime.mutableStateOf(io.legado.app.ui.book.manga.MangaReaderUiState(
            translationPage = MangaTranslationSettingsPage.SETTINGS))
        val intents = mutableListOf<io.legado.app.ui.book.manga.MangaReaderIntent>()
        compose.setContent {
            io.legado.app.ui.theme.MaterialThemeWrapper(
                io.legado.app.ui.theme.LocalLegadoThemeColors.current, null, applyBackground = false
            ) {
                MangaTranslationSettingsContent(state.value) { intent ->
                    intents += intent
                    when (intent) {
                        io.legado.app.ui.book.manga.MangaReaderIntent.OpenTranslationModels -> state.value = state.value.copy(translationPage = MangaTranslationSettingsPage.MODELS)
                        else -> Unit
                    }
                }
            }
        }
        compose.onNodeWithText("显示悬浮气泡").performClick()
        assertTrue(intents.contains(io.legado.app.ui.book.manga.MangaReaderIntent.UpdateSetting(
            io.legado.app.ui.book.manga.MangaReaderSettingKey.TRANSLATION_BUBBLE_VISIBLE, 0)))
        compose.onNodeWithText("模型管理").performClick()
        compose.onNodeWithText("补齐缺失").assertExists()
        compose.onNodeWithText("导入模型").assertExists()
    }

    @Test fun `main bubble has no rectangular dark compositing surface`() = checkMainBubbleSurface()
    @Test fun `dark main bubble retains transparent corners`() = checkMainBubbleSurface(dark = true)
    @Test fun `eink main bubble retains transparent corners`() = checkMainBubbleSurface(eInk = true)

    private fun checkMainBubbleSurface(dark: Boolean = false, eInk: Boolean = false) {
        content(dark = dark, eInk = eInk); open()
        compose.mainClock.advanceTimeBy(150)
        val image = compose.onRoot().captureToImage().toPixelMap()
        val bounds = compose.onNodeWithContentDescription("漫画翻译").getUnclippedBoundsInRoot()
        val rootBounds = compose.onRoot().getUnclippedBoundsInRoot()
        val scale = image.width / (rootBounds.right.value - rootBounds.left.value)
        val x = ((bounds.left.value + 1f) * scale).toInt().coerceIn(0, image.width - 1)
        val y = ((bounds.top.value + 1f) * scale).toInt().coerceIn(0, image.height - 1)
        val background = image[image.width / 2, y]
        assertTrue("Main bubble corner must remain transparent", image[x, y].red >= background.red - .025f)
    }

    @Test fun `fan spans 110 degrees and mirrors at either edge`() {
        val left = translationBubbleLayout(400f, 800f, Offset(0f, 376f), false)
        val right = translationBubbleLayout(400f, 800f, Offset(352f, 376f), true)
        left.actions.forEachIndexed { index, point ->
            assertEquals(84f, (point - left.main).getDistance(), .01f)
            assertEquals(352f - point.x, right.actions[index].x, .01f)
            assertEquals(point.y, right.actions[index].y, .01f)
        }
        val top = left.actions.first() - left.main
        val bottom = left.actions.last() - left.main
        val arc = kotlin.math.acos((top.x * bottom.x + top.y * bottom.y) / (84f * 84f)) * 180f / kotlin.math.PI.toFloat()
        assertEquals(110f, arc, .01f)
    }
    @Test fun `liquid outline contains all cells and every bridge`() {
        val root = Offset(40f, 120f)
        val children = listOf(Offset(88f, 51f), Offset(124f, 120f), Offset(88f, 189f))
        val path = translationMembranePath(root, children)
        val region = android.graphics.Region().apply {
            setPath(path.asAndroidPath(), android.graphics.Region(-100, -100, 300, 400))
        }
        children.forEach { child ->
            repeat(21) { step ->
                val point = root + (child - root) * (step / 20f)
                assertTrue(region.contains(point.x.toInt(), point.y.toInt()))
            }
        }
    }
    @Test fun `fan contains the convex space between bubbles without branching`() {
        val root = Offset(40f, 120f)
        val children = listOf(Offset(88f, 51f), Offset(124f, 120f), Offset(88f, 189f))
        val region = android.graphics.Region().apply {
            setPath(translationMembranePath(root, children).asAndroidPath(), android.graphics.Region(-100, -100, 300, 400))
        }
        val points = (listOf(root) + children).flatMap { center ->
            listOf(Offset(-23f, 0f), Offset(23f, 0f), Offset(0f, -23f), Offset(0f, 23f)).map { center + it }
        }
        points.forEach { a -> points.forEach { b ->
            repeat(11) { step ->
                val point = a + (b - a) * (step / 10f)
                assertTrue("Fan must not have inward notches", region.contains(point.x.toInt(), point.y.toInt()))
            }
        } }
    }
    @Test fun `outer fan follows a round arc instead of straight diamond chords`() {
        val layout = translationBubbleLayout(400f, 800f, Offset(0f, 376f), false)
        val root = layout.main + Offset(24f, 24f)
        val region = android.graphics.Region().apply {
            setPath(translationMembranePath(root, layout.actions.map { it + Offset(24f, 24f) }).asAndroidPath(),
                android.graphics.Region(-100, -100, 500, 900))
        }
        listOf(-27.5f, 27.5f).forEach { degrees ->
            val angle = degrees * kotlin.math.PI.toFloat() / 180f
            val point = root + Offset(kotlin.math.cos(angle), kotlin.math.sin(angle)) * 108f
            assertTrue("Outer arc must stay rounded between controls", region.contains(point.x.toInt(), point.y.toInt()))
        }
    }

    private fun checkBackdropVisibility(blurEnabled: Boolean) {
        content(blurEnabled = blurEnabled, patterned = true); open()
        compose.mainClock.advanceTimeBy(150)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val scale = pixels.width / 400f
        val upper = pixels[(65 * scale).toInt(), (384 * scale).toInt()]
        val lower = pixels[(65 * scale).toInt(), (416 * scale).toInt()]
        assertTrue("Underlying black/white page must remain visible through the fan", lower.red - upper.red > .25f)
    }
    @Test fun `fan remains translucent when global blur is disabled`() = checkBackdropVisibility(false)
    @Test fun `blur uses transparent backdrop rather than opaque surface`() = checkBackdropVisibility(true)

    @Test fun `reveal begins inside main bubble and finishes on the fan`() {
        val main = Offset(8f, 200f)
        val target = Offset(92f, 131f)
        assertEquals(main, translationBubbleRevealPoint(main, target, 0f, 0))
        assertEquals(target, translationBubbleRevealPoint(main, target, 1f, 0))
        assertTrue((translationBubbleRevealPoint(main, target, 1.08f, 0) - main).getDistance() > (target - main).getDistance())
    }
    @Test fun `layout stays in bounds in portrait landscape and small windows`() {
        listOf(400f to 800f, 800f to 400f, 240f to 140f, 140f to 140f).forEach { (width, height) ->
            listOf(false, true).forEach { right ->
                val layout = translationBubbleLayout(width, height, Offset(width, height), right)
                (layout.actions + layout.main).forEach {
                    assertTrue(it.x >= 0 && it.x + 48 <= width)
                    assertTrue(it.y >= 0 && it.y + 48 <= height)
                }
            }
        }
        assertEquals(-24f, translationBubbleHiddenX(400f, false))
        assertEquals(376f, translationBubbleHiddenX(400f, true))
    }
}
