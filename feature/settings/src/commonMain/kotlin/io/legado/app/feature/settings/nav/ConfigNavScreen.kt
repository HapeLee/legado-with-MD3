package io.legado.app.feature.settings.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.legado.app.feature.settings.res.Res
import io.legado.app.feature.settings.res.ai_config
import io.legado.app.feature.settings.res.backup_restore
import io.legado.app.feature.settings.res.cover_config
import io.legado.app.feature.settings.res.download_cache_config
import io.legado.app.feature.settings.res.lab_setting
import io.legado.app.feature.settings.res.other_setting
import io.legado.app.feature.settings.res.read_config
import io.legado.app.feature.settings.res.setting
import io.legado.app.feature.settings.res.theme_setting
import io.legado.app.feature.settings.res.translation_config
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.jetbrains.compose.resources.stringResource

// M5-6b：从 `:app` 的 `io.legado.app.ui.config.ConfigNavScreen` 迁来（**结构逐字一致**，
// 只把 `R.string.*` 换成 `Res.string.*`，10 条里 7 条新增、3 条已在）。
//
// 这是 `:feature:settings` 里**第一个不是子页面迁移**的成员：它是**设置域首页**（那份
// 9 项导航列表），原来住在 `ui/config` 的根包下。它迁进来标志着模块从「子页面的集合」
// 变成「域」—— 首页归域所有。
// （迁走后 `ui/config` 的根包**不再有页面**，只剩 `ConfigTag.kt` 那 12 行常量 —— 见下。）
//
// 它是本模块里**平台依赖最少**的一页：9 个导航动作全是 `() -> Unit` 回调（宿主侧接
// `backStack.add(MainRouteSettings*)`，本片**无需改 `MainNavGraph` 的 entry**，只换 import），
// 没有 VM、没有 Effect、没有 `R.string` 以外的资源。唯一「离开」共享层的东西是
// `ConfigTag`（`ui/config/ConfigTag.kt`，12 行常量，只被 `:app` 的 `MainIntent` 用来把
// deep-link 的路由 tag 映射成 `MainRouteConst`）——那是纯 `:app` 内部的路由细节，
// 本页用不到它，**留在 `:app`**。

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigNavScreen(
    onBackClick: () -> Unit,
    onNavigateToOther: () -> Unit,
    onNavigateToRead: () -> Unit,
    onNavigateToCover: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAi: () -> Unit,
    onNavigateToDownloadCache: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToLab: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup {
                    ClickableSettingItem(
                        title = stringResource(Res.string.theme_setting),
                        onClick = onNavigateToTheme
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.other_setting),
                        onClick = onNavigateToOther
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.read_config),
                        onClick = onNavigateToRead
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.cover_config),
                        onClick = onNavigateToCover
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.download_cache_config),
                        onClick = onNavigateToDownloadCache
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.backup_restore),
                        onClick = onNavigateToBackup
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.ai_config),
                        onClick = onNavigateToAi
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.translation_config),
                        onClick = onNavigateToTranslation
                    )
                    ClickableSettingItem(
                        title = stringResource(Res.string.lab_setting),
                        onClick = onNavigateToLab
                    )
                }
            }
        }
    }
}
