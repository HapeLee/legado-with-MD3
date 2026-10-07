package io.legado.app.ui.book.readaloud.cast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.feature.reader.core.cast.CastSyntax
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.androidx.compose.koinViewModel

/**
 * 多角色识别页（多角色规则 → 多角色识别）。
 *
 * 四个符号位 + 实时预览 + 恢复默认。正文里的标记、阅读器胶囊与朗读注入全部
 * 按这里的配置生成/解析（见 CastSyntax）；引号对不参与配置，改了会让
 * 已存的锚点序号错位。
 */
@Composable
fun MultiRoleRecognitionRouteScreen(
    onBackClick: () -> Unit,
    viewModel: MultiRoleRecognitionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MultiRoleRecognitionScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBackClick = onBackClick,
    )
}

@Composable
fun MultiRoleRecognitionScreen(
    state: MultiRoleRecognitionUiState,
    onIntent: (MultiRoleRecognitionIntent) -> Unit,
    onBackClick: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.multi_role_recognition),
                scrollBehavior = scrollBehavior,
                navigationIcon = { TopBarNavigationButton(onClick = onBackClick) },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp,
            ),
        ) {
            item {
                SymbolHint(stringResource(R.string.multi_role_recognition_hint))
            }
            item {
                SplicedColumnGroup {
                    symbolItem(
                        title = stringResource(R.string.cast_mark_start),
                        field = CastSymbolField.MARK_START,
                        value = state.config.markStart,
                        defaultValue = CastSyntax.DEFAULT_MARK_START,
                        onIntent = onIntent,
                    )
                    symbolItem(
                        title = stringResource(R.string.cast_mark_end),
                        field = CastSymbolField.MARK_END,
                        value = state.config.markEnd,
                        defaultValue = CastSyntax.DEFAULT_MARK_END,
                        onIntent = onIntent,
                    )
                    symbolItem(
                        title = stringResource(R.string.cast_pool_start),
                        field = CastSymbolField.POOL_START,
                        value = state.config.poolStart,
                        defaultValue = CastSyntax.DEFAULT_POOL_START,
                        onIntent = onIntent,
                    )
                    symbolItem(
                        title = stringResource(R.string.cast_pool_end),
                        field = CastSymbolField.POOL_END,
                        value = state.config.poolEnd,
                        defaultValue = CastSyntax.DEFAULT_POOL_END,
                        onIntent = onIntent,
                    )
                }
            }
            item {
                SymbolHint(
                    text = if (state.errorRes != 0) {
                        stringResource(state.errorRes)
                    } else {
                        stringResource(R.string.cast_syntax_preview, state.preview)
                    },
                    error = state.errorRes != 0,
                )
            }
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.restore_default),
                        onClick = { onIntent(MultiRoleRecognitionIntent.Reset) },
                    )
                }
            }
            item {
                SymbolHint(stringResource(R.string.cast_syntax_applies_next_chapter))
            }
        }
    }
}

@Composable
private fun symbolItem(
    title: String,
    field: CastSymbolField,
    value: String,
    defaultValue: String,
    onIntent: (MultiRoleRecognitionIntent) -> Unit,
) {
    InputSettingItem(
        title = title,
        value = value,
        defaultValue = defaultValue,
        onConfirm = { onIntent(MultiRoleRecognitionIntent.SetSymbol(field, it)) },
    )
}

@Composable
private fun SymbolHint(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) LegadoTheme.colorScheme.error
        else LegadoTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
