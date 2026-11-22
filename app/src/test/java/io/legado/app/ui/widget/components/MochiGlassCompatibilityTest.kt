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
import ui.feice.mochi.theme.MochiGlass

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [31, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MochiGlassCompatibilityTest {
    /**
     * 发布出来的 mochi AAR 里，玻璃仍然能把面色矩阵作为 RenderEffect 交给 Backdrop：
     * 0.3.x 把这段搬进了 engine/GlassBodyKt 的 legacyGlass（Android 12 的模糊 + 面色矩阵），
     * 这条调用同时也是 app/mochi-compat.gradle 重定向到 [io.legado.app.ui.widget.components.mochi.MochiBackdropCompat]
     * 的那一处，所以这里能过就说明补丁在运行时也接上了。
     */
    @Test
    fun publishedGlassCanApplyItsColorFilterToBackdrop() {
        var result: RenderEffect? = null
        val scope = Proxy.newProxyInstance(
            BackdropEffectScope::class.java.classLoader,
            arrayOf(BackdropEffectScope::class.java),
        ) { _, method, arguments ->
            when (method.name) {
                "getRenderEffect" -> result
                "setRenderEffect" -> {
                    result = arguments!![0] as RenderEffect?
                    null
                }
                "getDensity", "getFontScale", "toPx-0680j_4" -> 1f
                else -> error("Unexpected effect-scope access: ${method.name}")
            }
        } as BackdropEffectScope
        val glassBody = Class.forName("ui.feice.mochi.effect.glass.engine.GlassBodyKt")
        val legacyGlass = glassBody.getDeclaredMethod(
            "legacyGlass",
            BackdropEffectScope::class.java,
            MochiGlass::class.java,
            Boolean::class.javaPrimitiveType,
        )
        legacyGlass.invoke(null, scope, MochiGlass(), false)
        assertNotNull(result)
    }
}
