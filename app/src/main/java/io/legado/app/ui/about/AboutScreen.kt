package io.legado.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.constant.AppConst.appInfo


@Composable
fun AboutScreen(
    onCheckUpdate: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onShowMdFile: (String, String) -> Unit = { _, _ -> },
    onSaveLog: () -> Unit = {},
    onCreateHeapDump: () -> Unit = {},
    onShowCrashLogs: () -> Unit = {},
    versionName: String = "1.0.0"
) {
    val context = LocalContext.current
    val version = remember { appInfo.versionName }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "版本：$version", style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))
            Button(onClick = onCheckUpdate) {
                Text(stringResource(R.string.check_update))
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { onOpenUrl(context.getString(R.string.github_url)) }) {
                Text(stringResource(R.string.github_url))
            }

            Button(onClick = { onOpenUrl(context.getString(R.string.legado_url)) }) {
                Text(stringResource(R.string.web_service))
            }

            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )

            Text(text = "文档与政策", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { onShowMdFile(context.getString(R.string.privacy_policy), "privacyPolicy.md") }) {
                Text(stringResource(R.string.privacy_policy))
            }
            TextButton(onClick = { onShowMdFile(context.getString(R.string.license), "LICENSE.md") }) {
                Text(stringResource(R.string.license))
            }
            TextButton(onClick = { onShowMdFile(context.getString(R.string.disclaimer), "disclaimer.md") }) {
                Text(stringResource(R.string.disclaimer))
            }

            HorizontalDivider(
                Modifier.padding(vertical = 8.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )

            Text(text = "开发工具", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onShowCrashLogs) {
                Text(stringResource(R.string.crash_log))
            }
            TextButton(onClick = onSaveLog) {
                Text(stringResource(R.string.save_log))
            }
            TextButton(onClick = onCreateHeapDump) {
                Text(stringResource(R.string.create_heap_dump))
            }
        }
    }
}
