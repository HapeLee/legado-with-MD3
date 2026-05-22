package io.legado.app.ui.main.homepage.modules

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.image.sourceIcon.SourceIcon
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.GSON
import org.koin.compose.koinInject

@Composable
fun ButtonGroupModule(
    kinds: List<ExploreKind>,
    sourceUrl: String,
    globalId: String,
    viewModel: HomepageViewModel,
    modifier: Modifier = Modifier,
    icon: String? = null,
    layoutConfig: String? = null,
) {
    if (kinds.isEmpty()) return

    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val useCase: ExploreKindUiUseCase = koinInject()
    val isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)

    // 解析图标映射表和默认图标
    val (iconMap, defaultIcon) = remember(layoutConfig) {
        layoutConfig?.let {
            try {
                val json = GSON.fromJson(it, Map::class.java)

                @Suppress("UNCHECKED_CAST")
                val icons = json["icons"] as? Map<String, String>
                val singleIcon = json["icon"] as? String
                (icons ?: emptyMap()) to (singleIcon ?: icon)
            } catch (_: Exception) {
                emptyMap<String, String>() to icon
            }
        } ?: (emptyMap<String, String>() to icon)
    }

    // --- 动态布局计算逻辑 ---
    val maxColumns = 5
    val total = kinds.size
    val numRows = (total + maxColumns - 1) / maxColumns
    val actualColumns = (total + numRows - 1) / numRows
    // -----------------------

    Column(
        modifier = modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.chunked(actualColumns).forEach { rowKinds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowKinds.forEach { kind ->
                    val buttonIcon = iconMap[kind.title] ?: defaultIcon
                    val hasIcon = !buttonIcon.isNullOrBlank()

                    ExploreKindMultiTypeItem(
                        kind = kind,
                        sourceUrl = sourceUrl,
                        activity = activity,
                        onOpenUrl = { url ->
                            viewModel.onKindUrlClick(sourceUrl, url, kind.title)
                        },
                        onRefreshKinds = {
                            viewModel.refreshButtonGroup(globalId)
                        },
                        useCase = useCase,
                        isMiuix = isMiuix,
                        modifier = Modifier.weight(1f),
                        content = { displayName, isSelected, onClick, trailingIcon ->
                            GlassCard(
                                onClick = onClick,
                                cornerRadius = 8.dp,
                                containerColor = if (isSelected) LegadoTheme.colorScheme.primaryContainer else LegadoTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    if (hasIcon) {
                                        SourceIcon(
                                            path = buttonIcon!!,
                                            modifier = Modifier.size(20.dp),
                                            placeholderIcon = {

                                            }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }

                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AppText(
                                            text = displayName,
                                            style = LegadoTheme.typography.labelSmallEmphasized,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp)
                                                .basicMarquee()
                                        )

                                        if (trailingIcon != null) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(end = 2.dp)
                                            ) {
                                                trailingIcon()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                if (rowKinds.size < actualColumns) {
                    repeat(actualColumns - rowKinds.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
