package io.legado.app.ui.widget.components.coui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import com.itos.coui.effect.CouiShadowRenderer
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private object CouiMenuSupport {
    // Menu constructs this renderer during composition and has no shadow override.
    val supported = try {
        CouiShadowRenderer()
        true
    } catch (error: IllegalArgumentException) {
        Timber.w(error, "COUI menu shadow is unavailable")
        false
    } catch (error: UnsupportedOperationException) {
        Timber.w(error, "COUI menu shadow is unavailable")
        false
    }
}

@Composable
internal fun canUseCouiSettingMenu(): Boolean =
    LegadoTheme.composeEngine == ComposeEngine.Coui &&
        ThemeResolver.isCouiSupported() &&
        CouiMenuSupport.supported
