package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.data.repository.ReadPreferences
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun PageKeyConfigSheet(
    onDismissRequest: () -> Unit,
) {
    val readSettingsRepository: ReadSettingsRepository = koinInject()
    val preferences by readSettingsRepository.preferences.collectAsStateWithLifecycle(
        initialValue = ReadPreferences()
    )
    val scope = rememberCoroutineScope()
    var prevKeys by remember { mutableStateOf(preferences.prevKeys) }
    var nextKeys by remember { mutableStateOf(preferences.nextKeys) }

    LaunchedEffect(preferences.prevKeys, preferences.nextKeys) {
        prevKeys = preferences.prevKeys
        nextKeys = preferences.nextKeys
    }

    AppAlertDialog(
        show = true,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.custom_page_key),
        content = {
            AppTextField(
                value = prevKeys,
                onValueChange = { prevKeys = it },
                label = stringResource(R.string.prev_page_key),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (keyCode != android.view.KeyEvent.KEYCODE_BACK &&
                            keyCode != android.view.KeyEvent.KEYCODE_DEL
                        ) {
                            prevKeys = if (prevKeys.isEmpty() || prevKeys.endsWith(",")) {
                                "$prevKeys$keyCode"
                            } else {
                                "$prevKeys,$keyCode"
                            }
                            true
                        } else {
                            false
                        }
                    },
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppTextField(
                value = nextKeys,
                onValueChange = { nextKeys = it },
                label = stringResource(R.string.next_page_key),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .onKeyEvent { event ->
                        val keyCode = event.nativeKeyEvent.keyCode
                        if (keyCode != android.view.KeyEvent.KEYCODE_BACK &&
                            keyCode != android.view.KeyEvent.KEYCODE_DEL
                        ) {
                            nextKeys = if (nextKeys.isEmpty() || nextKeys.endsWith(",")) {
                                "$nextKeys$keyCode"
                            } else {
                                "$nextKeys,$keyCode"
                            }
                            true
                        } else {
                            false
                        }
                    },
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppText(
                text = stringResource(R.string.page_key_set_help),
                style = LegadoTheme.typography.bodySmall,
                color = LegadoTheme.colorScheme.onSurfaceVariant,
            )
        },
        dismissText = stringResource(R.string.reset),
        onDismiss = {
            prevKeys = ""
            nextKeys = ""
        },
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            scope.launch {
                readSettingsRepository.setPageKeys(prevKeys, nextKeys)
                onDismissRequest()
            }
        },
    )
}
