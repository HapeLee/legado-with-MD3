package io.legado.app.ui.widget.components.mochi

import android.graphics.RenderEffect
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.effect

/** Target of the version-scoped Mochi 0.2.0 AAR compatibility transform. */
object MochiBackdropCompat {
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.S)
    fun effect(scope: BackdropEffectScope, effect: RenderEffect) {
        scope.effect(effect.asComposeRenderEffect())
    }
}
