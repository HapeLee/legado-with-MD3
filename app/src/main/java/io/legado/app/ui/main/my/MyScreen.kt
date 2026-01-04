package io.legado.app.ui.main.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.readRecord.ReadRecordActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.widget.components.SettingItem
import io.legado.app.ui.widget.components.SplicedColumnGroup


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen(
    viewModel: MyViewModel,
    onNavigate: (PrefClickEvent) -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.my)
                    )
                },
                actions = {
                    IconButton(
                        onClick = { onNavigate(PrefClickEvent.ShowMd("appHelp", "xxx")) }
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = ""
            ) {
                SettingItem(
                    title = stringResource(R.string.book_source_manage),
                    description = stringResource(R.string.book_source_manage_desc),
                    imageVector = Icons.Default.Source,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(BookSourceActivity::class.java)
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.web_service),
                    description = if (uiState.isWebServiceRun)
                        uiState.webServiceAddress
                    else
                        stringResource(R.string.web_service_desc),
                    imageVector = Icons.Default.Web,
                    trailingContent = {
                        Switch(
                            checked = uiState.isWebServiceRun,
                            onCheckedChange = {
                                viewModel.onEvent(PrefClickEvent.ToggleWebService)
                            }
                        )
                    },
                    dropdownMenu = if (uiState.isWebServiceRun) {
                        { onDismiss ->
                            DropdownMenuItem(
                                text = { Text("复制地址") },
                                onClick = {
                                    onNavigate(PrefClickEvent.CopyUrl(uiState.webServiceAddress))
                                    onDismiss()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("浏览器打开") },
                                onClick = {
                                    onNavigate(PrefClickEvent.OpenUrl(uiState.webServiceAddress))
                                    onDismiss()
                                }
                            )
                        }
                    } else null,
                    onClick = { }
                )
            }

            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(R.string.setting)) {
                SettingItem(
                    title = stringResource(R.string.backup_restore),
                    description = stringResource(R.string.web_dav_set_import_old),
                    imageVector = Icons.Default.Backup,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(
                                ConfigActivity::class.java,
                                ConfigTag.BACKUP_CONFIG
                            )
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.theme_setting),
                    description = stringResource(R.string.theme_setting_s),
                    imageVector = Icons.Default.Palette,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(
                                ConfigActivity::class.java,
                                ConfigTag.THEME_CONFIG
                            )
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.read),
                    description = stringResource(R.string.global_read_setting),
                    imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(
                                ConfigActivity::class.java,
                                ConfigTag.READ_CONFIG
                            )
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.other_setting),
                    description = stringResource(R.string.other_setting_s),
                    imageVector = Icons.Default.Settings,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(
                                ConfigActivity::class.java,
                                ConfigTag.OTHER_CONFIG
                            )
                        )
                    }
                )
            }
            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "规则"
            ) {
                SettingItem(
                    title = stringResource(R.string.replace_purify),
                    imageVector = Icons.Default.FindReplace,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(ReplaceRuleActivity::class.java)
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.txt_toc_rule),
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(TxtTocRuleActivity::class.java)
                        )
                    }
                )
                SettingItem(
                    title = stringResource(R.string.dict_rule),
                    imageVector = Icons.Default.Translate,
                    onClick = {
                        onNavigate(
                            PrefClickEvent.StartActivity(DictRuleActivity::class.java)
                        )
                    }
                )
            }
            SplicedColumnGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = stringResource(R.string.other)) {
                SettingItem(
                    title = stringResource(R.string.bookmark),
                    imageVector = Icons.Default.Bookmark,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AllBookmarkActivity::class.java))
                    }
                )
                SettingItem(
                    title = stringResource(R.string.read_record),
                    imageVector = Icons.Default.History,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(ReadRecordActivity::class.java))
                    }
                )
                SettingItem(
                    title = stringResource(R.string.file_manage),
                    imageVector = Icons.Default.Folder,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(FileManageActivity::class.java))
                    }
                )
                SettingItem(
                    title = stringResource(R.string.about),
                    imageVector = Icons.Default.Info,
                    onClick = {
                        onNavigate(PrefClickEvent.StartActivity(AboutActivity::class.java))
                    }
                )
                SettingItem(
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
