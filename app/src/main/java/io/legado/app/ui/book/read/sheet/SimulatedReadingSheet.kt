package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.settingItem.TinySwitchSettingItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatedReadingSheet(
    onDismissRequest: () -> Unit,
    onApply: () -> Unit,
) {
    val book = ReadBook.book ?: return
    var enabled by remember { mutableStateOf(book.getReadSimulating()) }
    var startChapter by remember { mutableStateOf(book.getStartChapter().toString()) }
    var dailyChapters by remember { mutableStateOf(book.getDailyChapters().toString()) }
    var startDate by remember { mutableStateOf(book.getStartDate() ?: LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = LegadoTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.simulated_reading)) },
        text = {
            Column {
                OutlinedTextField(
                    value = startDate.format(dateFormatter),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.start_from)) },
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TinySwitchSettingItem(
                    title = stringResource(R.string.simulated_reading),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Start chapter + daily chapters
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startChapter,
                        onValueChange = { startChapter = it },
                        label = { Text(stringResource(R.string.start_chapter)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = dailyChapters,
                        onValueChange = { dailyChapters = it },
                        label = { Text(stringResource(R.string.daily_chapters)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    book.setStartDate(startDate)
                    book.setDailyChapters(dailyChapters.toIntOrNull() ?: 0)
                    book.setStartChapter(startChapter.toIntOrNull() ?: 0)
                    book.setReadSimulating(enabled)
                    book.save()
                    onApply()
                    onDismissRequest()
                },
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        },
    )

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = LegadoTheme.colorScheme.surfaceContainer,
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            startDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
