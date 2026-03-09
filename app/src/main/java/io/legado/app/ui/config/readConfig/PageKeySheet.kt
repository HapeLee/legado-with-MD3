package io.legado.app.ui.config.readConfig

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.widget.components.modalBottomSheet.GlassModalBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageKeySheet(
    onDismissRequest: () -> Unit
) {
    var prevKeys by remember { mutableStateOf(ReadConfig.prevKeys) }
    var nextKeys by remember { mutableStateOf(ReadConfig.nextKeys) }

    GlassModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.custom_page_key),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Companion.Bold
            )

            OutlinedTextField(
                value = prevKeys,
                onValueChange = { prevKeys = it },
                label = { Text(stringResource(R.string.prev_page_key)) },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            val keyCode = event.nativeKeyEvent.keyCode
                            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
                                prevKeys = if (prevKeys.isEmpty() || prevKeys.endsWith(",")) {
                                    prevKeys + keyCode.toString()
                                } else {
                                    "$prevKeys,$keyCode"
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    },
                singleLine = true
            )

            OutlinedTextField(
                value = nextKeys,
                onValueChange = { nextKeys = it },
                label = { Text(stringResource(R.string.next_page_key)) },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            val keyCode = event.nativeKeyEvent.keyCode
                            if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_DEL) {
                                nextKeys = if (nextKeys.isEmpty() || nextKeys.endsWith(",")) {
                                    nextKeys + keyCode.toString()
                                } else {
                                    "$nextKeys,$keyCode"
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    },
                singleLine = true
            )

            Text(
                text = stringResource(R.string.page_key_set_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        prevKeys = ""
                        nextKeys = ""
                    },
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text(stringResource(R.string.reset))
                }

                Button(
                    onClick = {
                        ReadConfig.prevKeys = prevKeys
                        ReadConfig.nextKeys = nextKeys
                        onDismissRequest()
                    },
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    }
}