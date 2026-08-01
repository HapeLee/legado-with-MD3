package io.legado.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.about.MarkdownSheet
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.ThemeResolver
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.explore.ExploreKindMultiTypeItem
import io.legado.app.ui.widget.components.explore.FlexItemLayout
import io.legado.app.ui.widget.components.explore.calculateFlexRows
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.log.AppLogSheet
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun SourceLoginSheetHost(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    when {
        state.loading -> AppModalBottomSheet(
            show = true,
            onDismissRequest = { onIntent(SourceLoginIntent.Back) },
            title = stringResource(R.string.login),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        state.mode == SourceLoginMode.Web -> SourceLoginWebSheet(
            state = state,
            onIntent = onIntent,
            onOpenExternalUrl = onOpenExternalUrl,
        )

        else -> SourceLoginFormSheet(state, onIntent)
    }

    val header = state.activeSheet as? SourceLoginSheet.LoginHeader
    MarkdownSheet(
        show = header != null,
        title = stringResource(R.string.login_header),
        content = header?.content.orEmpty(),
        onDismissRequest = { onIntent(SourceLoginIntent.DismissSheet) },
        endAction = {
            MediumTonalButton(
                icon = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.copy_text),
                onClick = { header?.content?.let { onIntent(SourceLoginIntent.CopyLoginHeader(it)) } },
            )
        },
    )
    AppLogSheet(
        show = state.activeSheet == SourceLoginSheet.Log,
        onDismissRequest = { onIntent(SourceLoginIntent.DismissSheet) },
    )
}

@Composable
private fun SourceLoginFormSheet(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    AppModalBottomSheet(
        show = state.activeSheet == SourceLoginSheet.Form,
        onDismissRequest = { onIntent(SourceLoginIntent.Back) },
        title = state.title,
        startAction = {
            Box {
                MediumTonalButton(
                    icon = AppIcons.MoreVert,
                    contentDescription = stringResource(R.string.menu),
                    onClick = { menuExpanded = true },
                )
                RoundDropdownMenu(menuExpanded, { menuExpanded = false }) { dismiss ->
                    RoundDropdownMenuItem(stringResource(R.string.show_login_header), onClick = {
                        dismiss(); onIntent(SourceLoginIntent.ShowLoginHeader)
                    })
                    RoundDropdownMenuItem(stringResource(R.string.del_login_header), onClick = {
                        dismiss(); onIntent(SourceLoginIntent.DeleteLoginHeader)
                    })
                    RoundDropdownMenuItem(stringResource(R.string.log), onClick = {
                        dismiss(); onIntent(SourceLoginIntent.ShowLog)
                    })
                }
            }
        },
        endAction = {
            MediumTonalButton(
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.ok),
                onClick = { onIntent(SourceLoginIntent.Confirm) },
            )
        },
    ) {
        val rows = remember(state.rows) {
            calculateFlexRows(state.rows, maxSpan = 6) { row ->
                if (row is LoginRowUi.Text) {
                    FlexItemLayout(
                        basisPercent = 1f,
                        wrapBefore = true,
                    )
                } else {
                    FlexItemLayout(
                        flexGrow = row.layout.flexGrow,
                        basisPercent = row.layout.basisPercent,
                        wrapBefore = row.layout.wrapBefore,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowItems.forEach { (row, span) ->
                        SourceLoginRow(
                            row = row,
                            modifier = Modifier.weight(span.toFloat()),
                            value = state.values[row.key].orEmpty(),
                            onValueChange = {
                                onIntent(SourceLoginIntent.ValueChanged(row.key, it))
                            },
                            onCommit = { onIntent(SourceLoginIntent.ValueCommitted(row.key)) },
                            onRunAction = { longClick ->
                                onIntent(SourceLoginIntent.RunAction(row.key, longClick))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLoginRow(
    row: LoginRowUi,
    modifier: Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onRunAction: (Boolean) -> Unit,
) {
    when (row) {
        is LoginRowUi.Text -> {
            var hadFocus by remember(row.key) { mutableStateOf(false) }
            AppTextField(
                value = value,
                onValueChange = onValueChange,
                label = row.title,
                modifier = modifier.onFocusChanged { focus ->
                    if (hadFocus && !focus.isFocused) onCommit()
                    hadFocus = focus.isFocused
                },
                singleLine = true,
                visualTransformation = if (row.password) PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onCommit() }
                ),
            )
        }

        is LoginRowUi.Button,
        is LoginRowUi.Select,
        is LoginRowUi.Toggle -> {
            val kind = row.toExploreKind()
            ExploreKindMultiTypeItem(
                kind = kind,
                sourceUrl = null,
                onOpenUrl = {},
                modifier = modifier,
                backgroundColor = LegadoTheme.colorScheme.surface,
                minHeight = 44.dp,
                isMiuix = ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine),
                displayNameOverride = row.title,
                valueOverride = value,
                onValueChange = onValueChange,
                onRunAction = { onRunAction(false) },
                onLongClick = { onRunAction(true) },
                textAlign = row.layout.textAlign(),
            )
        }
    }
}

private fun LoginRowLayoutUi.textAlign(): TextAlign = when (justify) {
    "flex_start" -> TextAlign.Start
    "flex_end", "right" -> TextAlign.End
    else -> TextAlign.Center
}

private fun LoginRowUi.toExploreKind() = ExploreKind(
    title = title,
    type = when (this) {
        is LoginRowUi.Button -> ExploreKind.Type.button
        is LoginRowUi.Select -> ExploreKind.Type.select
        is LoginRowUi.Toggle -> ExploreKind.Type.toggle
        is LoginRowUi.Text -> ExploreKind.Type.text
    },
    action = action,
    chars = when (this) {
        is LoginRowUi.Select -> options.toTypedArray()
        is LoginRowUi.Toggle -> options.toTypedArray()
        else -> null
    },
    viewName = title,
)

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
private fun SourceLoginWebSheet(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = { onIntent(SourceLoginIntent.Back) },
        title = state.title,
        endAction = {
            MediumTonalButton(
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.ok),
                onClick = { onIntent(SourceLoginIntent.Confirm) },
            )
        },
        contentPaddingEnabled = false,
    ) {
        SourceLoginWebView(state, onIntent, onOpenExternalUrl)
    }
}

@SuppressLint("SetJavaScriptEnabled", "WebViewClientOnReceivedSslError")
@Composable
private fun SourceLoginWebView(
    state: SourceLoginUiState,
    onIntent: (SourceLoginIntent) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    val currentIntent by rememberUpdatedState(onIntent)
    val currentOpenExternalUrl by rememberUpdatedState(onOpenExternalUrl)
    var webView by remember { mutableStateOf<WebView?>(null) }
    Box(Modifier
        .fillMaxWidth()
        .heightIn(min = 520.dp)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        javaScriptEnabled = true
                        displayZoomControls = false
                        state.headers[AppConst.UA_NAME]?.let { userAgentString = it }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            url?.let { currentIntent(SourceLoginIntent.WebPageStarted(it)) }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            url?.let { currentIntent(SourceLoginIntent.WebPageFinished(it)) }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ) =
                            handleUrl(request.url)

                        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                            handleUrl(Uri.parse(url))

                        private fun handleUrl(uri: Uri): Boolean {
                            if (uri.scheme == "http" || uri.scheme == "https") return false
                            currentOpenExternalUrl(uri.toString())
                            return true
                        }

                        override fun onReceivedSslError(
                            view: WebView?, handler: SslErrorHandler?, error: SslError?
                        ) {
                            handler?.proceed()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, progress: Int) {
                            currentIntent(SourceLoginIntent.WebProgressChanged(progress))
                        }
                    }
                    state.webUrl?.let { loadUrl(it, state.headers) }
                    webView = this
                }
            },
            update = {},
        )
        if (state.webProgress in 0..99) {
            LinearProgressIndicator(
                progress = { state.webProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView?.destroy() }
    }
    LaunchedEffect(state.checkingCookie) {
        if (state.checkingCookie) {
            state.webUrl?.let { webView?.loadUrl(it, state.headers) }
        }
    }
}
