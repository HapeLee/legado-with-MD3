package io.legado.app.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import io.legado.app.ui.widget.components.card.TextCard
import io.legado.app.ui.widget.components.settingItem.SettingItem
import io.legado.app.ui.widget.components.SettingItemWithDivider
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    versionName: String = appInfo.versionName,
    onBack: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onShowMdFile: (String, String) -> Unit = { _, _ -> },
    onSaveLog: () -> Unit = {},
    onCreateHeapDump: () -> Unit = {},
    onShowCrashLogs: () -> Unit = {}
) {
    LocalContext.current

    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.about),
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
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(120.dp)
                    .width(160.dp)
                    .align(Alignment.CenterHorizontally)
            )
            AppText(
                text = stringResource(R.string.app_name),
                style = LegadoTheme.typography.bodyLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally))
            TextCard(
                text = versionName,
                cornerRadius = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp))
            AppText(
                text = stringResource(R.string.about_description),
                style = LegadoTheme.typography.bodyLarge,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(bottom = 4.dp))
            Row (modifier = Modifier
                .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center){
                FilledTonalIconButton ( onClick = { onOpenUrl("https://example.com") } ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_web_outline),
                        contentDescription = stringResource(R.string.back)
                    )
                }

                FilledTonalIconButton (onClick = { onOpenUrl("https://github.com/HapeLee/legado-with-MD3") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = stringResource(R.string.back)
                    )
                }

                FilledTonalIconButton(onClick = onCheckUpdate) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }

            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = ""
            ){
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.contributors),
                        onClick = {
                            onOpenUrl("https://github.com/gedoor/legado/graphs/contributors")
                        }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.privacy_policy),
                        onClick = {
                            onShowMdFile("隐私政策", "privacyPolicy.md")
                        }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.license),
                        onClick = {
                            onShowMdFile("许可证", "LICENSE.md")
                        }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.disclaimer),
                        onClick = {
                            onShowMdFile("免责声明", "disclaimer.md")
                        }
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.crash_log),
                        onClick = onShowCrashLogs
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.save_log),
                        onClick = onSaveLog
                    )
                }
                SettingItemWithDivider {
                    SettingItem(
                        title = stringResource(R.string.create_heap_dump),
                        onClick = onCreateHeapDump
                    )
                }
            }
        }
    }
}
