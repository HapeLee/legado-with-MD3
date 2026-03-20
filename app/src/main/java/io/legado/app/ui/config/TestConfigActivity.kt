package io.legado.app.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.legado.app.R
import io.legado.app.base.BaseComposeActivity
import io.legado.app.ui.config.coverConfig.CoverConfigScreen
import io.legado.app.ui.config.otherConfig.OtherConfigScreen
import io.legado.app.ui.config.readConfig.ReadConfigScreen
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.ui.widget.components.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.button.TopbarNavigationButton
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import kotlinx.serialization.Serializable

class TestConfigActivity : BaseComposeActivity() {

    @Serializable
    object ConfigNavRoute

    @Serializable
    object OtherConfigRoute

    @Serializable
    object ReadConfigRoute

    @Serializable
    object CoverConfigRoute

    @Composable
    override fun Content() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = ConfigNavRoute
        ) {
            composable<ConfigNavRoute> {
                ConfigNavScreen(
                    onBackClick = { finish() },
                    onNavigateToOther = { navController.navigate(OtherConfigRoute) },
                    onNavigateToRead = { navController.navigate(ReadConfigRoute) },
                    onNavigateToCover = { navController.navigate(CoverConfigRoute) }
                )
            }

            composable<OtherConfigRoute> {
                OtherConfigScreen(onBackClick = { navController.popBackStack() })
            }

            composable<ReadConfigRoute> {
                ReadConfigScreen(onBackClick = { navController.popBackStack() })
            }

            composable<CoverConfigRoute> {
                CoverConfigScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ConfigNavScreen(
        onBackClick: () -> Unit,
        onNavigateToOther: () -> Unit,
        onNavigateToRead: () -> Unit,
        onNavigateToCover: () -> Unit
    ) {
        val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                GlassMediumFlexibleTopAppBar(
                    title = { Text("设置") },
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
                    SwitchSettingItem(
                        title = "使用折叠应用栏",
                        checked = ThemeConfig.useFlexibleTopAppBar,
                        onCheckedChange = { ThemeConfig.useFlexibleTopAppBar = it }
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
                }
            }
        }
    }

}
