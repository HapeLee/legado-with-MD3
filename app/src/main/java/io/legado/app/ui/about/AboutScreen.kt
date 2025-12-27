package io.legado.app.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.base.AppTheme
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.ui.widget.components.SettingItem
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.TextCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    versionName: String = appInfo.versionName,
    onNavigateBack: () -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onShowMdFile: (String, String) -> Unit = { _, _ -> },
    onSaveLog: () -> Unit = {},
    onCreateHeapDump: () -> Unit = {},
    onShowCrashLogs: () -> Unit = {}
) {
    LocalContext.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
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
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally))
            TextCard(
                text = versionName,
                backgroundColor = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer,
                cornerRadius = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp))
            Text(
                text = stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyLarge,
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
                title = "",
                content = listOf(
                    {
                        SettingItem(
                            modifier = Modifier.background(colorScheme.surfaceBright),
                            title = stringResource(R.string.contributors),
                            description = null,
                            option = null,
                            onClick = {
                                onOpenUrl("https://github.com/gedoor/legado/graphs/contributors")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.privacy_policy),
                            description = null,
                            option = null,
                            onClick = {
                                onShowMdFile("隐私政策", "privacyPolicy.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.license),
                            description = null,
                            option = null,
                            onClick = {
                                onShowMdFile("许可证", "LICENSE.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.disclaimer),
                            description = null,
                            option = null,
                            onClick = {
                                onShowMdFile("免责声明", "disclaimer.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.crash_log),
                            description = null,
                            option = null,
                            onClick = onShowCrashLogs
                        )
                        SettingItem(
                            title = stringResource(R.string.save_log),
                            description = null,
                            option = null,
                            onClick = onSaveLog
                        )
                        SettingItem(
                            title = stringResource(R.string.create_heap_dump),
                            description = null,
                            option = null,
                            onClick = onCreateHeapDump
                        )
                    }
                )
            )
        }
    }
}