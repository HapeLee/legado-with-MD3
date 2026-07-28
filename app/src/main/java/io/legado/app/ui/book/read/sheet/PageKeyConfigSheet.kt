package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.custom_page_key)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = prevKeys,
                    onValueChange = { prevKeys = it },
                    label = { Text(stringResource(R.string.prev_page_key)) },
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
                OutlinedTextField(
                    value = nextKeys,
                    onValueChange = { nextKeys = it },
                    label = { Text(stringResource(R.string.next_page_key)) },
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
                Text(
                    text = stringResource(R.string.page_key_set_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prevKeys = ""
                    nextKeys = ""
                },
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        readSettingsRepository.setPageKeys(prevKeys, nextKeys)
                        onDismissRequest()
                    }
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
    )
}
