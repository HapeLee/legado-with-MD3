package io.legado.app.ui.widget.components.explore

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.source.getExploreInfoMap
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch

/**
 * 封装 ExploreKind 的业务状态与交互逻辑
 */
@Stable
class ExploreKindItemState(
    val kind: ExploreKind,
    val sourceUrl: String?,
    private val useCase: ExploreKindUiUseCase?,
    private val scope: CoroutineScope,
    private val activity: AppCompatActivity?,
    private val onRefreshKinds: () -> Unit
) {
    val infoMap: InfoMap? = if (useCase == null) null else sourceUrl?.takeIf { it.isNotBlank() }
        ?.let(::getExploreInfoMap)
    var displayName by mutableStateOf(kind.title)
        internal set

    fun executeAction(action: String?) {
        if (action.isNullOrBlank()) return
        val useCase = useCase ?: return
        scope.launch(IO) {
            useCase.executeAction(
                action = action,
                title = kind.title,
                sourceUrl = sourceUrl,
                infoMap = infoMap,
                activity = activity,
                onRefreshKinds = onRefreshKinds
            )
        }
    }

    fun updateValue(value: String, onValueChange: ((String) -> Unit)?) {
        if (onValueChange != null) {
            onValueChange(value)
        } else {
            infoMap?.let {
                it[kind.title] = value
                it.saveNow()
            }
        }
    }

    @Composable
    fun ResolveDisplayName(override: String?) {
        LaunchedEffect(override, sourceUrl, kind.title, kind.viewName, useCase) {
            displayName = override
                ?: useCase?.resolveDisplayName(kind, sourceUrl, infoMap)
                        ?: kind.title
        }
    }
}

@Composable
fun rememberExploreKindItemState(
    kind: ExploreKind,
    sourceUrl: String?,
    useCase: ExploreKindUiUseCase?,
    activity: AppCompatActivity?,
    onRefreshKinds: () -> Unit
): ExploreKindItemState {
    val scope = rememberCoroutineScope()
    return remember(kind, sourceUrl, useCase, activity) {
        ExploreKindItemState(kind, sourceUrl, useCase, scope, activity, onRefreshKinds)
    }
}
