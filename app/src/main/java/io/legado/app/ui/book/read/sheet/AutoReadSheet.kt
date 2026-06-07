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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.TinySliderSettingItem
import org.koin.compose.koinInject
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AutoReadSheet(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenChapterList: () -> Unit,
    onStopAutoPage: () -> Unit,
    onShowPageAnimConfig: () -> Unit,
) {
    AppModalBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.auto_page_speed),
    ) {
        AutoReadContent(
            onDismissRequest = onDismissRequest,
            onIntent = onIntent,
            onOpenChapterList = onOpenChapterList,
            onStopAutoPage = onStopAutoPage,
            onShowPageAnimConfig = onShowPageAnimConfig,
            modifier = Modifier
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun AutoReadContent(
    onDismissRequest: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
    onOpenChapterList: () -> Unit,
    onStopAutoPage: () -> Unit,
    onShowPageAnimConfig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val initialSpeed = remember { preferences.autoReadSpeed.coerceIn(1, 120).toFloat() }
    var speed by remember { mutableFloatStateOf(initialSpeed) }

    LaunchedEffect(preferences.autoReadSpeed) {
        speed = preferences.autoReadSpeed.coerceIn(1, 120).toFloat()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
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
                onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.AutoReadSpeed(intSpeed)))
            },
        )

        Spacer(Modifier.height(12.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionButton(
                icon = R.drawable.ic_toc,
                label = stringResource(R.string.chapter_list),
                onClick = {
                    onDismissRequest()
                    onOpenChapterList()
                },
            )
            ActionButton(
                icon = R.drawable.ic_auto_page_stop,
                label = stringResource(R.string.stop),
                onClick = {
                    onStopAutoPage()
                    onDismissRequest()
                },
            )
            ActionButton(
                icon = R.drawable.ic_settings,
                label = stringResource(R.string.setting),
                onClick = {
                    onDismissRequest()
                    onShowPageAnimConfig()
                },
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.FilledTonalIconButton(onClick = onClick) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
