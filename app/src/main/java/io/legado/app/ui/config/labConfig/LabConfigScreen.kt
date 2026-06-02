package io.legado.app.ui.config.labConfig

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabConfigScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.lab_setting),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(R.string.lab_enabled_title),
                        description = stringResource(R.string.lab_enabled_summary),
                        checked = LabConfig.labEnabled,
                        onCheckedChange = { LabConfig.labEnabled = it }
                    )
                }

                AnimatedVisibility(visible = LabConfig.labEnabled) {
                    SplicedColumnGroup(title = stringResource(R.string.lab_display)) {
                        SwitchSettingItem(
                            title = stringResource(R.string.lab_eink_display_title),
                            description = stringResource(R.string.lab_eink_display_summary),
                            checked = LabConfig.eInkDisplay,
                            onCheckedChange = {
                                LabConfig.eInkDisplay = it
                            }
                        )

                        if (LabConfig.eInkDisplay) {
                            Text(
                                text = stringResource(R.string.lab_eink_display_hint),
                                style = LegadoTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        SwitchSettingItem(
                            title = stringResource(R.string.lab_eye_protection_title),
                            description = stringResource(R.string.lab_eye_protection_summary),
                            checked = LabConfig.eyeProtection,
                            onCheckedChange = {
                                LabConfig.eyeProtection = it
                            }
                        )

                        if (LabConfig.eyeProtection) {
                            Text(
                                text = stringResource(R.string.lab_eye_protection_hint),
                                style = LegadoTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
