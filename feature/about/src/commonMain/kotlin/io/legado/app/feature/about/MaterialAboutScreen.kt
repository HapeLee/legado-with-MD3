package io.legado.app.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.feature.about.res.Res
import io.legado.app.feature.about.res.about
import io.legado.app.feature.about.res.about_description
import io.legado.app.feature.about.res.about_disclaimer_title
import io.legado.app.feature.about.res.about_license_title
import io.legado.app.feature.about.res.about_open_github
import io.legado.app.feature.about.res.about_open_project_homepage
import io.legado.app.feature.about.res.about_privacy_policy_title
import io.legado.app.feature.about.res.app_name
import io.legado.app.feature.about.res.check_update
import io.legado.app.feature.about.res.contributors
import io.legado.app.feature.about.res.crash_log
import io.legado.app.feature.about.res.create_heap_dump
import io.legado.app.feature.about.res.disclaimer
import io.legado.app.feature.about.res.license
import io.legado.app.feature.about.res.privacy_policy
import io.legado.app.feature.about.res.save_log
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SettingItemWithDivider
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.modalBottomSheet.MarkdownSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 「关于」页的 **Material 分支**（M5-1c-2 从 `:app` 的 `ui/about/AboutScreen.kt` 拆进共享层）。
 *
 * ## 为什么这里没有 `AboutScreen` 这个「分流函数」
 *
 * 迁移前 `AboutScreen` 的形状是「按 `ThemeResolver.isMiuixEngine` 二选一 + 渲染 `AboutOverlays`」，
 * 其中 Miuix 分支（`MiuixAboutScreen`，519 行）**必须留 `:app`**——`miuix-blur` 只有
 * `miuix-blur-android`，没有 desktop 变体。于是分流点无法整体进共享层：共享层不可能 import
 * `:app` 的类（那会形成 `:app → :feature:about → :app` 的环）。
 *
 * 三种可能的分流位置，选的是第三种：
 *  1. 在本模块 `androidMain` 的 Route 里分流 ⇒ 需要把「打开链接 / Toast / 启动下载」这些
 *     **宿主**动作也搬进来，就得再抽三个平台契约；而 `:app` 侧仍然要写一个收 lambda 的回调壳。
 *  2. 在共享层分流、由宿主注入 `miuixContent: @Composable () -> Unit` ⇒ 共享层为了一个
 *     Android-only 分支多背一个参数，desktop 侧永远传 `null`。
 *  3. **分流留在 `:app`**（`MainNavGraph` 的 `entry<MainRouteAbout>` 里），本模块只导出
 *     `MaterialAboutScreen` + `AboutOverlays`。
 *
 * 选 3 的依据是 `AGENTS.md` 划的职责线：「App host 聚合导航和 DI」「导航、权限、文件选择和其他
 * 宿主动作通过回调或 Effect 处理」「新目的地优先由 `MainActivity` 的 Navigation 3 图组装」——
 * 导航入口本来就是宿主的活，分流和 Effect 收集（Toast / 打开 URL / 启动下载）都属宿主。
 * 代价是本模块的 `androidMain` **没有代码**（Route 在 `:app`，平台实现在
 * `:app/io/legado/app/platform/AndroidAboutCapabilities.kt`），这是本片唯一一处与四个规则
 * Feature 的形态差异。
 *
 * ## 两处**必做**的内容变化
 *
 * 1. `BuildConfig.VERSION_NAME` / `Build.SUPPORTED_ABIS` → 由宿主以参数注入（见 [versionName]
 *    与 [AboutOverlays] 的 `abi`）。
 * 2. `painterResource(R.drawable.*)` → `AboutIcons.*`（`ImageVector`）。Android vector XML 不能进
 *    `composeResources`（desktop 会缺图），逐字重建见 `AboutIcons.kt`。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialAboutScreen(
    state: AboutUiState,
    onIntent: (AboutIntent) -> Unit,
    onBack: () -> Unit,
    versionName: String,
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val privacyPolicyTitle = stringResource(Res.string.about_privacy_policy_title)
    val licenseTitle = stringResource(Res.string.about_license_title)
    val disclaimerTitle = stringResource(Res.string.about_disclaimer_title)

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(Res.string.about),
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                imageVector = AboutIcons.LauncherForeground,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(120.dp)
                    .width(160.dp)
                    .align(Alignment.CenterHorizontally)
            )
            AppText(
                text = stringResource(Res.string.app_name),
                style = LegadoTheme.typography.bodyLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
            TextCard(
                text = versionName,
                cornerRadius = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp)
            )
            AppText(
                text = stringResource(Res.string.about_description),
                style = LegadoTheme.typography.bodyLarge,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalIconButton(onClick = { onIntent(AboutIntent.OpenUrl("https://github.com/HapeLee/legado-with-MD3")) }) {
                    Icon(
                        imageVector = AboutIcons.WebOutline,
                        contentDescription = stringResource(Res.string.about_open_project_homepage)
                    )
                }
                FilledTonalIconButton(onClick = { onIntent(AboutIntent.OpenUrl("https://github.com/HapeLee/legado-with-MD3")) }) {
                    Icon(
                        imageVector = AboutIcons.GitHub,
                        contentDescription = stringResource(Res.string.about_open_github)
                    )
                }
                FilledTonalIconButton(onClick = { onIntent(AboutIntent.CheckUpdate) }) {
                    Icon(
                        imageVector = AboutIcons.Import,
                        contentDescription = stringResource(Res.string.check_update)
                    )
                }
            }

            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = ""
            ) {
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.contributors),
                        onClick = { onIntent(AboutIntent.OpenUrl("https://github.com/HapeLee/legado-with-MD3")) }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.privacy_policy),
                        onClick = {
                            onIntent(
                                AboutIntent.ShowMdFile(
                                    privacyPolicyTitle,
                                    "privacyPolicy.md"
                                )
                            )
                        }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.license),
                        onClick = { onIntent(AboutIntent.ShowMdFile(licenseTitle, "LICENSE.md")) }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.disclaimer),
                        onClick = { onIntent(AboutIntent.ShowMdFile(disclaimerTitle, "disclaimer.md")) }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.crash_log),
                        onClick = { onIntent(AboutIntent.ShowCrashLogs) }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.save_log),
                        onClick = { onIntent(AboutIntent.SaveLog) }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(Res.string.create_heap_dump),
                        onClick = { onIntent(AboutIntent.CreateHeapDump) }
                    )
                }
            }
        }
    }
}

/**
 * 三张底部弹层 + 一个「检查更新中」对话框。
 *
 * 与 Material / Miuix 两个分支**共用**（迁移前它就在 `AboutScreen` 的 if/else 之外），所以是
 * public 的：`:app` 的 Miuix 分支需要和 [MaterialAboutScreen] 渲染同一批 overlays。
 *
 * `renderedSheet` 那段 `delay(300)` 是弹层关闭动画期间「保留上一条内容」的既有做法，逐字照抄。
 */
@Composable
fun AboutOverlays(
    state: AboutUiState,
    onIntent: (AboutIntent) -> Unit,
    versionName: String,
    abi: String,
) {
    val currentSheet = state.sheet
    var renderedSheet by remember { mutableStateOf<AboutSheet>(AboutSheet.None) }
    LaunchedEffect(currentSheet) {
        if (currentSheet is AboutSheet.None) {
            delay(300)
            renderedSheet = AboutSheet.None
        } else {
            renderedSheet = currentSheet
        }
    }

    when (val sheet = renderedSheet) {
        is AboutSheet.None -> Unit
        is AboutSheet.Markdown -> MarkdownSheet(
            show = currentSheet is AboutSheet.Markdown,
            title = sheet.title,
            content = sheet.content,
            onDismissRequest = { onIntent(AboutIntent.DismissSheet) },
        )

        is AboutSheet.CrashLogs -> CrashLogSheet(
            show = currentSheet is AboutSheet.CrashLogs,
            logFiles = state.crashLogFiles,
            onDismissRequest = { onIntent(AboutIntent.DismissSheet) },
            onReadFile = { onIntent(AboutIntent.ReadCrashFile(it)) },
            onClear = { onIntent(AboutIntent.ClearCrashLogs) },
        )

        is AboutSheet.Update -> UpdateSheet(
            show = currentSheet is AboutSheet.Update,
            updateInfo = sheet.updateInfo,
            updateToVariant = state.updateToVariant,
            mode = sheet.mode,
            versionName = versionName,
            abi = abi,
            onDismissRequest = { onIntent(AboutIntent.DismissSheet) },
            onStartDownload = { onIntent(AboutIntent.StartDownload) },
        )
    }

    AppAlertDialog(
        show = state.dialog is AboutDialog.CheckingUpdate,
        onDismissRequest = {},
        content = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AppCircularProgressIndicator()
            }
        }
    )
}
