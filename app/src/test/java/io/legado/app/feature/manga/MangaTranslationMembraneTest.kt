package io.legado.app.feature.manga

import android.app.Application
import android.graphics.PathMeasure
import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidPath
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MangaTranslationMembraneTest {
    @Test fun `curvature changes gradually around the entire expanded outline including seam`() {
        val layout = translationBubbleLayout(400f, 800f, Offset(0f, 376f), false)
        val measure = PathMeasure(translationMembranePath(layout.main, layout.actions).asAndroidPath(), false)
        val tangent = FloatArray(2)
        fun angle(distance: Float): Float {
            measure.getPosTan((distance % measure.length + measure.length) % measure.length, null, tangent)
            return atan2(tangent[1], tangent[0])
        }
        fun curvature(distance: Float): Float {
            val delta = angle(distance + .5f) - angle(distance - .5f)
            return atan2(sin(delta), cos(delta))
        }
        var largestJump = 0f
        repeat(ceil(measure.length * 2).toInt()) { index ->
            val distance = index * .5f
            largestJump = max(largestJump, abs(curvature(distance + .5f) - curvature(distance)))
        }
        assertTrue("Curvature must not jump between straight edges and circular corners: $largestJump", largestJump < .015f)
    }

    @Test fun `membrane encloses button circles throughout reveal in mirrored and compact layouts`() {
        listOf(400f to 800f, 800f to 400f, 240f to 140f, 140f to 140f).forEach { (width, height) ->
            listOf(false, true).forEach { right ->
                val layout = translationBubbleLayout(width, height, Offset(0f, height / 2), right)
                listOf(0f, .01f, .1f, .3f, .6f, .9f, 1f, 1.08f, 1.12f).forEach { reveal ->
                    val children = layout.actions.mapIndexed { index, target ->
                        val amount = ((reveal - index * .055f) / (1f - index * .055f)).coerceIn(0f, 1.12f)
                        translationBubbleRevealPoint(layout.main, target, amount, index)
                    }
                    val region = Region().apply {
                        setPath(translationMembranePath(layout.main, children).asAndroidPath(), Region(-500, -500, 1500, 1500))
                    }
                    (children + layout.main).forEach { center ->
                        repeat(72) { index ->
                            val angle = index * PI.toFloat() / 36f
                            val point = center + Offset(cos(angle), sin(angle)) * 24f
                            assertTrue("Button clipped at ${width}x$height right=$right reveal=$reveal", region.contains(point.x.roundToInt(), point.y.roundToInt()))
                        }
                    }
                }
            }
        }
    }
}
