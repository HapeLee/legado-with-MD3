package io.legado.app.ui.main.homepage.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.main.homepage.HomepageModuleManageUi
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.collections.immutable.ImmutableList

@Composable
fun CustomSetAddModulesPage(
    setUrl: String,
    allJoinedModules: ImmutableList<HomepageModuleManageUi>,
    sourceNames: Map<String, String>,
    onToggleModuleToSet: (HomepageModuleManageUi, inCurrentSet: Boolean, isBlocked: Boolean) -> Unit,
) {
    val setId = HomepageViewModel.customSetIdFromUrl(setUrl)
    val initialJoined = allJoinedModules
        .filter { it.customSetId == setId }
        .associateBy({ it.moduleKey }, { it.id })
    var joinedInCurrent by remember(initialJoined) { mutableStateOf(initialJoined) }

    val hasInfiniteInCurrentSet = remember(allJoinedModules) {
        allJoinedModules.any {
            it.customSetId == setId && HomepageViewModel.isInfinite(
                it.type,
                it.layoutConfig
            )
        }
    }

    val grouped = remember(allJoinedModules) {
        allJoinedModules
            .distinctBy { it.sourceUrl to it.moduleKey }
            .groupBy { it.sourceUrl }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grouped.forEach { (sourceUrl, modules) ->
            item(key = "header_$sourceUrl") {
                AppText(
                    text = sourceNames[sourceUrl] ?: sourceUrl,
                    style = LegadoTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(modules, key = { it.id }) { module ->
                val instanceIdInCurrentSet = joinedInCurrent[module.moduleKey]
                val inCurrentSet = instanceIdInCurrentSet != null
                val isInfinite = HomepageViewModel.isInfinite(module.type, module.layoutConfig)
                val isBlocked = !inCurrentSet && isInfinite && hasInfiniteInCurrentSet

                SelectionItemCard(
                    title = module.title,
                    subtitle = module.moduleKey + if (isBlocked) " (${stringResource(R.string.homepage_module_duplicate_infinite)})" else "",
                    containerColor = LegadoTheme.colorScheme.onSheetContent,
                    isSelected = inCurrentSet,
                    inSelectionMode = true,
                    isEnabled = !isBlocked,
                    onToggleSelection = {
                        onToggleModuleToSet(module, inCurrentSet, isBlocked)
                        if (inCurrentSet) {
                            joinedInCurrent = joinedInCurrent - module.moduleKey
                        } else if (!isBlocked) {
                            joinedInCurrent =
                                joinedInCurrent + (module.moduleKey to "temp_${module.id}")
                        }
                    }
                )
            }
        }
    }
}
