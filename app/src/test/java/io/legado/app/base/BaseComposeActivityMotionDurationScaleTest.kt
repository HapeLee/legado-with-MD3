package io.legado.app.base

import android.animation.ValueAnimator
import android.app.Application
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import kotlin.coroutines.coroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26, 33, 35])
class BaseComposeActivityMotionDurationScaleTest {

    @Test
    fun `注入到 decorView Recomposer 的 MotionDurationScale 不会被覆盖且开启时动画立即完成`() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()

        Settings.Global.putFloat(
            activity.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.5f,
        )
        val durationScale = BaseComposeActivity.DynamicMotionDurationScale(
            contentResolver = activity.contentResolver,
            initialDisabled = true,
        )
        activity.window.decorView.compositionContext =
            activity.window.decorView.createLifecycleAwareWindowRecomposer(
                coroutineContext = durationScale,
                lifecycle = activity.lifecycle,
            )

        var observedScale: MotionDurationScale? = null
        var observedFactorWhenDisabled: Float? = null
        var animationFinished = false
        var finalValue = 0f

        activity.setContent {
            LaunchedEffect(Unit) {
                observedScale = coroutineContext[MotionDurationScale]
                observedFactorWhenDisabled = observedScale?.scaleFactor
                val animatable = Animatable(0f)
                animatable.animateTo(1f, animationSpec = tween(durationMillis = 500))
                finalValue = animatable.value
                animationFinished = true
            }
        }

        // 仅推进极短帧时间（远小于 500ms），验证 scaleFactor == 0f 时动画直接完成。
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))

        assertSame(durationScale, observedScale)
        assertEquals(0f, observedFactorWhenDisabled)
        assertTrue("scaleFactor=0f 时 500ms 动画应立即完成", animationFinished)
        assertEquals(1f, finalValue, 0.0001f)

        // 关闭禁用后，逐次动态读取系统缩放值（API 33+ 走 ValueAnimator，API < 33 走 Settings.Global）。
        durationScale.disableAnimations = false
        val expectedEnabledScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ValueAnimator.getDurationScale()
        } else {
            1.5f
        }
        assertEquals(expectedEnabledScale, durationScale.scaleFactor, 0.0001f)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Settings.Global.putFloat(
                activity.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f,
            )
            assertEquals(0f, durationScale.scaleFactor, 0.0001f)
        }

        controller.destroy()
    }
}
