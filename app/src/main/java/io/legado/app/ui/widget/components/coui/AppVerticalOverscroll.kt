package io.legado.app.ui.widget.components.coui

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.itos.coui.scroll.couiVerticalScroll
import com.itos.coui.scroll.rememberCouiOverscrollEffect
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme

@Composable
private fun useCouiVerticalOverscroll(): Boolean {
    if (LegadoTheme.composeEngine != ComposeEngine.Coui) return false
    val refreshRate = LocalView.current.display?.refreshRate ?: return false
    return refreshRate.isFinite() && refreshRate > 0f
}

@Composable
internal fun rememberAppVerticalOverscrollEffect(): OverscrollEffect? =
    if (useCouiVerticalOverscroll()) rememberCouiOverscrollEffect() else rememberOverscrollEffect()

@Composable
internal fun Modifier.appVerticalScroll(state: ScrollState, enabled: Boolean = true): Modifier =
    if (useCouiVerticalOverscroll()) couiVerticalScroll(state, enabled = enabled)
    else verticalScroll(state, enabled = enabled)
