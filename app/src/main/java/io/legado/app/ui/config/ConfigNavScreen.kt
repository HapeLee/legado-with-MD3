package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigNavScreen(
    onBackClick: () -> Unit,
    onNavigateToOther: () -> Unit,
    onNavigateToRead: () -> Unit,
    onNavigateToCover: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToBackup: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopbarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SplicedColumnGroup {
                ClickableSettingItem(
                    title = stringResource(R.string.theme_setting),
                    onClick = onNavigateToTheme
                )
                ClickableSettingItem(
                    title = stringResource(R.string.other_setting),
                    onClick = onNavigateToOther
                )
                ClickableSettingItem(
                    title = stringResource(R.string.read_config),
                    onClick = onNavigateToRead
                )
                ClickableSettingItem(
                    title = stringResource(R.string.cover_config),
                    onClick = onNavigateToCover
                )
                ClickableSettingItem(
                    title = stringResource(R.string.backup_restore),
                    onClick = onNavigateToBackup
                )
            }
        }
    }
}
