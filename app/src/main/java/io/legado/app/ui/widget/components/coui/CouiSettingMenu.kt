package io.legado.app.ui.widget.components.coui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.itos.coui.effect.CouiShadowRenderer
import com.itos.coui.menu.CouiMenuItem
import com.itos.coui.menu.Menu
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

@Composable
internal fun CouiSettingMenu(
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    enabled: Boolean = true,
    unavailableValues: Set<String> = emptySet(),
    onUnavailableSelected: ((String) -> Unit)? = null,
    onValueChange: (String) -> Unit,
    anchor: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val items = remember(displayEntries, entryValues, selectedValue, unavailableValues, onUnavailableSelected) {
        displayEntries.mapIndexed { index, text ->
            val value = entryValues[index]
            CouiMenuItem(
                value = value,
                text = text,
                selected = value == selectedValue,
                enabled = value !in unavailableValues || onUnavailableSelected != null,
            )
        }
    }
    // The Popup uses this row's bounds as its anchor, including in a separate sheet window.
    Box {
        anchor { if (enabled) expanded = true }
        Menu(
            expanded = enabled && expanded,
            onDismissRequest = { expanded = false },
            items = items,
            onItemClick = { item ->
                expanded = false
                if (item.value in unavailableValues) onUnavailableSelected?.invoke(item.value)
                else onValueChange(item.value)
            },
        )
    }
}
