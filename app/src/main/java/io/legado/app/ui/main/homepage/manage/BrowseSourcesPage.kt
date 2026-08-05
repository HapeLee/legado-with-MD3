package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageSourceManageUi
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.SelectionItemCard
import kotlinx.collections.immutable.ImmutableList

@Composable
fun BrowseSourcesPage(
    browseSources: ImmutableList<HomepageSourceManageUi>,
    getSourceModules: (String, String?) -> List<HomepageModuleManageUi>,
    onSelectSource: (String) -> Unit,
) {
    val sources = remember(browseSources) {
        browseSources.distinctBy { it.sourceUrl }
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(sources, key = { it.sourceUrl }) { source ->
            val moduleCount = getSourceModules(source.sourceUrl, null).size
            SelectionItemCard(
                title = source.sourceName,
                subtitle = stringResource(R.string.homepage_n_modules, moduleCount),
                containerColor = LegadoTheme.colorScheme.onSheetContent,
                onToggleSelection = {
                    onSelectSource(source.sourceUrl)
                }
            )
        }
    }
}
