package io.legado.app.ui.main.my

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.readRecord.ReadRecordActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.SmallTextButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import org.koin.androidx.compose.koinViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen(
    viewModel: MyViewModel = koinViewModel(),
    onOpenSettings: () -> Unit,
    onNavigate: (PrefClickEvent) -> Unit
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.my),
                actions = {
                    IconButton(
                        onClick = {
                            onNavigate(
                                PrefClickEvent.ShowMd(
                                    title = "",
                                    path = "appHelp"
                                )
                            )
                        }
                    ) {Icon(
                        Icons.AutoMirrored.Filled.HelpOutline, null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    adaptiveContentPadding(
                        top = padding.calculateTopPadding(),
                        bottom = 120.dp
                    )
                )
        ) {
            SplicedColumnGroup(
                title = ""
            ) {
                WebServiceSettingBlock(
                    uiState = uiState,
                    onToggleWebService = {
                        viewModel.onEvent(PrefClickEvent.ToggleWebService)
                    },
                    onNavigate = onNavigate
                )
            }

            SplicedColumnGroup(
                title = stringResource(R.string.rule_segment),
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.book_source_manage),
                    description = stringResource(R.string.book_source_manage_desc),
                    imageVector = Icons.Default.Source,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(BookSourceActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.replace_purify),
                    imageVector = Icons.Default.FindReplace,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(ReplaceRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.txt_toc_rule),
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(TxtTocRuleActivity::class.java)
                        )
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.dict_rule),
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(DictRuleActivity::class.java)
                        )
                    }
                )
            }

            SplicedColumnGroup(
                title = stringResource(R.string.other)
            ) {
                ClickableSettingItem(
                    title = stringResource(R.string.setting),
                    imageVector = Icons.Default.Settings,
                    onClick = {
                        onOpenSettings()
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.bookmark),
                    imageVector = Icons.Default.Bookmark,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AllBookmarkActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.read_record),
                    imageVector = Icons.Default.History,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(ReadRecordActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.cache_management),
                    imageVector = Icons.Default.Download,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenBookCacheManage)
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.file_manage),
                    imageVector = Icons.Default.Folder,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(FileManageActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.about),
                    imageVector = Icons.Default.Info,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AboutActivity::class.java))
                    }
                )
                ClickableSettingItem(
                    title = stringResource(R.string.exit),
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    onClick = {
                        onNavigate(PrefClickEvent.ExitApp)
                    }
                )
            }
        }
    }
}


@Composable
fun WebServiceSettingBlock(
    uiState: MyUiState,
    onToggleWebService: () -> Unit,
    onNavigate: (PrefClickEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SwitchSettingItem(
            title = stringResource(R.string.web_service),
            description = if (uiState.isWebServiceRun) {
                uiState.webServiceAddress
            } else {
                stringResource(R.string.web_service_desc)
            },
            imageVector = Icons.Default.Web,
            checked = uiState.isWebServiceRun,
            onCheckedChange = { onToggleWebService() }
        )

        AnimatedVisibility(
            visible = uiState.isWebServiceRun,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                SmallTextButton(
                    text = stringResource(R.string.copy_url),
                    imageVector = Icons.Default.ContentCopy,
                    onClick = {
                        onNavigate(PrefClickEvent.CopyUrl(uiState.webServiceAddress))
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                SmallTextButton(
                    text = stringResource(R.string.open_in_browser),
                    imageVector = Icons.Default.OpenInBrowser,
                    onClick = {
                        onNavigate(PrefClickEvent.OpenUrl(uiState.webServiceAddress))
                    }
                )
            }
        }
    }
}

