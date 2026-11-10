package io.legado.app.ui.widget.components.mochi

import android.view.View
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import io.legado.app.R
import java.util.WeakHashMap

// Mochi's in-window overlay adds a sibling ComposeView rather than a modal Window.
// Hide the covered host from accessibility until every overlay using it has closed.
private class CoveredHost(val original: Int, var count: Int)
private val coveredHosts = WeakHashMap<View, CoveredHost>()

@Composable
internal fun MochiOverlayAccessibility(visible: Boolean) {
    val host = LocalView.current
    if (visible) DisposableEffect(host) {
        val lease = coveredHosts.getOrPut(host) { CoveredHost(host.importantForAccessibility, 0) }
        lease.count++
        host.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        onDispose {
            lease.count--
            if (lease.count == 0) {
                host.importantForAccessibility = lease.original
                coveredHosts.remove(host)
            }
        }
    }
}

@Composable
internal fun mochiOverlayContentModifier(onDismiss: () -> Unit, title: String? = null): Modifier {
    val close = stringResource(R.string.close)
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    return Modifier
        .semantics {
            isTraversalGroup = true
            if (!title.isNullOrBlank()) paneTitle = title
            dismiss(label = close) { onDismiss(); true }
        }
        .focusRequester(focus)
        .onPreviewKeyEvent {
            if (it.key == Key.Escape) {
                if (it.type == KeyEventType.KeyUp) onDismiss()
                true
            } else false
        }
        .focusable()
}
