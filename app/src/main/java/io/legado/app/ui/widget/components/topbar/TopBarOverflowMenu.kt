package io.legado.app.ui.widget.components.topbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.LocalAppGlassOverlay
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.menuItem.GlassMenuAnchor
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassTopAppBarDefaults as NativeGlassTopAppBarDefaults

@Composable
internal fun TopBarOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val anchor = if (LocalMiuixGlassTopBar.current && LocalAppGlassOverlay.current != null) {
        rememberGlassPopupAnchor()
    } else null
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box {
        TopBarActionButton(
            onClick = { onExpandedChange(true) },
            imageVector = AppIcons.MoreVert,
            contentDescription = stringResource(R.string.more_menu),
            modifier = if (anchor != null) {
                Modifier
                    .glassPopupAnchor(anchor, cornerRadius = NativeGlassTopAppBarDefaults.ButtonSize / 2, floating = true)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
            } else Modifier,
        )
        RoundDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            glassAnchor = anchor?.let {
                GlassMenuAnchor(it, bounds) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(AppIcons.MoreVert, contentDescription = null)
                    }
                }
            },
            content = content,
        )
    }
}
