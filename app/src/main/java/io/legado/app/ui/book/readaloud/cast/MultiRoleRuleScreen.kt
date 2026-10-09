package io.legado.app.ui.book.readaloud.cast

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

/**
 * 朗读设置 hub（设置 → 朗读设置）。
 *
 * 只做菜单跳转：声音池、背景音乐池、变声器、胶囊外观、引擎、角色识别各自是独立页面。
 * 角色声音池 / 背景音乐池 / 朗读胶囊外观在阅读界面的「朗读设置」卡片里也各有一个入口
 * （角色与配乐 / 常规 tab）；这里是设置侧的完整入口，两处指向同一批页面。
 */
@Composable
fun MultiRoleRuleRouteScreen(
    onBackClick: () -> Unit,
    onNavigateToVoicePool: () -> Unit,
    onNavigateToRecognition: () -> Unit,
    onNavigateToEngines: () -> Unit,
    onNavigateToBgmPool: () -> Unit,
    onNavigateToVoiceEffect: () -> Unit,
    onNavigateToCapsuleStyle: () -> Unit,
    onNavigateToRegexCast: () -> Unit,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Miuix 引擎分支不套 contentColor，隐式 LocalContentColor 会落到 Unspecified → 深色下文字发黑
        contentColor = LegadoTheme.colorScheme.onSurface,
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.aloud_config),
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
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(R.string.cast_voice_pool),
                        description = stringResource(R.string.cast_voice_pool_summary),
                        onClick = onNavigateToVoicePool,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.cast_bgm_pool),
                        description = stringResource(R.string.cast_bgm_pool_summary),
                        onClick = onNavigateToBgmPool,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.voice_effect),
                        description = stringResource(R.string.voice_effect_hub_summary),
                        onClick = onNavigateToVoiceEffect,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.capsule_style_title),
                        description = stringResource(R.string.capsule_style_hub_summary),
                        onClick = onNavigateToCapsuleStyle,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.read_aloud_engines_and_voices),
                        description = stringResource(R.string.cast_tts_engines_summary),
                        onClick = onNavigateToEngines,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.multi_role_recognition),
                        description = stringResource(R.string.multi_role_recognition_summary),
                        onClick = onNavigateToRecognition,
                    )
                    ClickableSettingItem(
                        title = stringResource(R.string.regex_cast_rule),
                        description = stringResource(R.string.regex_cast_rule_summary),
                        onClick = onNavigateToRegexCast,
                    )
                }
            }
        }
    }
}
