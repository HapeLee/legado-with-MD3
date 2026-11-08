package io.legado.app.ui.widget.components

import android.app.Application
import androidx.compose.ui.graphics.RenderEffect
import com.kyant.backdrop.BackdropEffectScope
import java.lang.reflect.Proxy
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [31, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MochiGlassCompatibilityTest {
    @Test
    fun publishedGlassSelectionCanApplyItsColorFilterToBackdrop2() {
        var result: RenderEffect? = null
        val scope = Proxy.newProxyInstance(
            BackdropEffectScope::class.java.classLoader,
            arrayOf(BackdropEffectScope::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getRenderEffect" -> result
                "setRenderEffect" -> { result = arguments!![0] as RenderEffect?; null }
                "getDensity", "getFontScale" -> 1f
                else -> error("Unexpected effect-scope access: ${method.name}")
            }
        } as BackdropEffectScope
        val pipeline = Class.forName("ui.feice.mochi.effect.glass.engine.GlassPipelineKt")
        val selection = pipeline.getDeclaredMethod(
            "selection", BackdropEffectScope::class.java, List::class.java,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
        )
        selection.invoke(null, scope, listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f), 1f, 0f)
        assertNotNull(result)
    }
}
