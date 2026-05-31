package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AutoReadSheet(
    onDismissRequest: () -> Unit,
    onOpenChapterList: () -> Unit,
    onShowMainMenu: () -> Unit,
    onStopAutoPage: () -> Unit,
    onShowPageAnimConfig: () -> Unit,
) {
    val initialSpeed = remember {
        if (ReadBookConfig.autoReadSpeed < 1) 1f else ReadBookConfig.autoReadSpeed.toFloat()
    }
    var speed by remember { mutableFloatStateOf(initialSpeed) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.auto_page_speed),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            // Speed display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.auto_page_speed),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = String.format(Locale.ROOT, "%ds", speed.roundToInt()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            TinySliderSettingItem(
                title = stringResource(R.string.auto_page_speed),
                description = String.format(Locale.ROOT, "%ds", speed.roundToInt()),
                value = speed,
                valueRange = 1f..120f,
                steps = 118,
                onValueChange = {
                    speed = it
                    val intSpeed = it.roundToInt().coerceIn(1, 120)
                    ReadBookConfig.autoReadSpeed = intSpeed
                },
            )

            Spacer(Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                // Catalog
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = {
                        onDismissRequest()
                        onOpenChapterList()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_toc),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.chapter_list),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Main Menu
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = {
                        onDismissRequest()
                        onShowMainMenu()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.main_menu),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Stop
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = {
                        onStopAutoPage()
                        onDismissRequest()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_auto_page_stop),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.stop),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                // Settings
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = {
                        onDismissRequest()
                        onShowPageAnimConfig()
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(R.string.setting),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
