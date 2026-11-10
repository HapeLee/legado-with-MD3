package io.legado.app.feature.settings.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.flow.first

private val LocalSearchItem = staticCompositionLocalOf<SettingsSearchItem?> { null }
private val LocalSearchFocus = staticCompositionLocalOf<SearchFocus?> { null }

@Stable
private class SearchFocus(
    val requested: SettingsSearchItem,
    val targetId: String?,
    val locate: Boolean,
    val topInset: Dp,
    val anchors: MutableMap<String, Unit>,
    val onLocated: () -> Unit,
)

/** Host supplies a destination only; this scope cannot mutate settings or navigation. */
@Composable
fun SettingsSearchTargetScope(item: SettingsSearchItem, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSearchItem provides item, content = content)
}

/** Keeps ordinary list behavior, and resolves a searched conditional item to its visible prerequisite. */
@Composable
fun SearchableSettingsList(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val requested = LocalSearchItem.current?.takeUnless { it.isCategory }
    val anchors = remember(requested?.id) { mutableStateMapOf<String, Unit>() }
    var handled by rememberSaveable(requested?.id) { mutableStateOf(false) }
    var targetId by rememberSaveable(requested?.id) { mutableStateOf<String?>(null) }
    var unavailable by rememberSaveable(requested?.id) { mutableStateOf(false) }

    LaunchedEffect(requested?.id) {
        if (requested == null || handled) return@LaunchedEffect
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
        var candidate: SettingsSearchItem? = requested
        val visited = mutableSetOf<String>()
        while (candidate != null && visited.add(candidate.id)) {
            val count = state.layoutInfo.totalItemsCount
            // Search status is an extra item only on targeted visits. Negative indexes count from the end.
            val index = if (candidate.groupIndex < 0) count + candidate.groupIndex else candidate.groupIndex + 1
            state.scrollToItem(index.coerceIn(0, count - 1))
            // Let the lazy group compose and place its anchors before resolving a hidden child.
            withFrameNanos { }
            withFrameNanos { }
            if (anchors.containsKey(candidate.id)) {
                targetId = candidate.id
                break
            }
            candidate = candidate.parentId?.let(SettingsSearchCatalog::find)
        }
        if (targetId == null) {
            unavailable = true
            handled = true
            state.scrollToItem(0)
        }
    }
    val focus = requested?.let {
        SearchFocus(it, targetId, !handled, contentPadding.calculateTopPadding(), anchors) { handled = true }
    }
    CompositionLocalProvider(LocalSearchFocus provides focus) {
        LazyColumn(modifier, state, contentPadding, verticalArrangement = verticalArrangement) {
            if (requested != null) item("settings-search-status") {
                if (unavailable) AppText(
                    stringResource(R.string.settings_search_unavailable, stringResource(requested.titleRes)),
                    modifier = Modifier.padding(16.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun SearchableSetting(id: String, content: @Composable () -> Unit) {
    val focus = LocalSearchFocus.current
    val item = requireNotNull(SettingsSearchCatalog.find(id)) { "Unknown setting target: $id" }
    if (focus != null) DisposableEffect(id, focus.anchors) {
        focus.anchors[id] = Unit
        onDispose { focus.anchors.remove(id) }
    }
    SettingSearchTarget(
        settingId = id,
        locate = focus != null && focus.locate && focus.targetId == id,
        topInset = focus?.topInset ?: 0.dp,
        onLocated = { focus?.onLocated?.invoke() },
    ) {
        content()
        if (focus?.targetId == id && focus.requested.id != id) {
            AppText(
                stringResource(R.string.settings_search_prerequisite,
                    stringResource(focus.requested.titleRes), stringResource(item.titleRes)),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
