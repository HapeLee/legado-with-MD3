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
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.ConfirmDismissButtonsRow
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageKeySheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    var prevKeys by remember { mutableStateOf(ReadConfig.prevKeys) }
    var nextKeys by remember { mutableStateOf(ReadConfig.nextKeys) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.custom_page_key)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppTextField(
                value = prevKeys,
                onValueChange = { prevKeys = it },
                label = stringResource(R.string.prev_page_key),
                modifier = Modifier
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

            AppTextField(
                value = nextKeys,
                onValueChange = { nextKeys = it },
                label = stringResource(R.string.next_page_key),
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

            AppText(
                text = stringResource(R.string.page_key_set_help),
                style = LegadoTheme.typography.bodyMedium
            )

            ConfirmDismissButtonsRow(
                modifier = Modifier.fillMaxWidth(),
                onDismiss = {
                    prevKeys = ""
                    nextKeys = ""
                },
                onConfirm = {
                    ReadConfig.prevKeys = prevKeys
                    ReadConfig.nextKeys = nextKeys
                    onDismissRequest()
                },
                dismissText = stringResource(R.string.reset),
                confirmText = stringResource(R.string.ok)
            )
        }
    }
}