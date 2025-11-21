package io.legado.app.ui.about

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.AppTheme
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.ui.widget.compose.SettingItem
import io.legado.app.ui.widget.compose.SplicedColumnGroup
import io.legado.app.ui.widget.compose.TextCard

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun PreviewAboutScreen() {
    AppTheme {
        AboutScreen(
            versionName = "1.0",
            onCheckUpdate = {},
            onOpenUrl = {},
            onShowMdFile = { _, _ -> },
            onSaveLog = {},
            onCreateHeapDump = {},
            onShowCrashLogs = {}
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ButtonGroup(
    onOpenUrl: (String) -> Unit,
    onCheckUpdate: () -> Unit
) {

    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        clickableItem(
            onClick = { onOpenUrl("https://example.com") },
            weight = 1f,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_web_outline),
                    contentDescription = stringResource(R.string.back),
                    tint = colorScheme.onSurface
                )
            },
            label = ""
        )

        clickableItem(
            onClick = { onOpenUrl("https://github.com/HapeLee/legado-with-MD3") },
            weight = 1f,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.back),
                    tint = colorScheme.onSurface
                )
            },
            label = ""
        )

        clickableItem(
            onClick = { onCheckUpdate() },
            weight = 1f,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_import),
                    contentDescription = stringResource(R.string.back),
                    tint = colorScheme.onSurface
                )
            },
            label = ""
        )
    }
}

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
            Text(
                text = stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge,
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
            )
            Text(
                text = stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally))
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

                FilledTonalIconButton (onClick = { onCheckUpdate }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_import),
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }

            SplicedColumnGroup(
                title = stringResource(R.string.about),
                content = listOf(
                    {
                        SettingItem(
                            modifier = Modifier.background(colorScheme.surfaceBright),
                            title = stringResource(R.string.contributors),
                            description = "",
                            onClick = {
                                onOpenUrl("https://github.com/gedoor/legado/graphs/contributors")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.privacy_policy),
                            description = "",
                            onClick = {
                                onShowMdFile("隐私政策", "privacyPolicy.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.license),
                            description = "",
                            onClick = {
                                onShowMdFile("许可证", "LICENSE.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.disclaimer),
                            description = "",
                            onClick = {
                                onShowMdFile("免责声明", "disclaimer.md")
                            }
                        )
                        SettingItem(
                            title = stringResource(R.string.crash_log),
                            description = "",
                            onClick = onShowCrashLogs
                        )
                        SettingItem(
                            title = stringResource(R.string.save_log),
                            description = "",
                            onClick = onSaveLog
                        )
                        SettingItem(
                            title = stringResource(R.string.create_heap_dump),
                            description = "",
                            onClick = onCreateHeapDump
                        )
                    }
                )
            )
        }
    }
}