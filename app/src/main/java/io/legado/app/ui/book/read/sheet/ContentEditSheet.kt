package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.book.read.ReadBookUiState
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.SmallTonalButton
import io.legado.app.ui.widget.components.checkBox.AppCheckbox
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.progressIndicator.AppCircularProgressIndicator
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.sendToClip

@Composable
fun ContentEditSheet(
    show: Boolean,
    state: ReadBookUiState,
    onIntent: (ReadBookIntent) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val editorState = remember { TextFieldState() }
    val editorScrollState = rememberScrollState()
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var pendingLocateOffset by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        onIntent(ReadBookIntent.LoadContentEdit)
    }

    LaunchedEffect(state.contentEditText, state.contentEditCursorOffset) {
        if (editorState.text.toString() != state.contentEditText) {
            editorState.setTextAndSelectAll(state.contentEditText)
            editorState.edit {
                val offset = state.contentEditCursorOffset.coerceIn(0, length)
                selection = TextRange(offset)
                pendingLocateOffset = offset
            }
        }
    }

    LaunchedEffect(show, state.contentEditLoading, pendingLocateOffset, textLayoutResult) {
        val layoutResult = textLayoutResult
        val locateOffset = pendingLocateOffset
        if (!show || state.contentEditLoading || layoutResult == null || locateOffset == null) {
            return@LaunchedEffect
        }
        val layoutTextLength = layoutResult.layoutInput.text.length
        if (layoutTextLength != editorState.text.length) {
            return@LaunchedEffect
        }
        if (layoutTextLength == 0) {
            pendingLocateOffset = null
            return@LaunchedEffect
        }
        val offset = locateOffset.coerceIn(0, layoutTextLength - 1)
        val cursorTop = layoutResult.getCursorRect(offset).top.toInt()
        editorScrollState.scrollTo((cursorTop - 120).coerceIn(0, editorScrollState.maxValue))
        pendingLocateOffset = null
    }

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.text.toString() }
            .collect { text ->
                if (text != state.contentEditText) {
                    onIntent(ReadBookIntent.SetContentEditText(text))
                }
            }
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = state.contentEditTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SmallTonalButton(
                    onClick = {
                        onIntent(
                            ReadBookIntent.SaveContentEdit(
                                editorState.text.toString(),
                                state.contentEditSaveToSource
                            )
                        )
                        onDismissRequest()
                    },
                    text = stringResource(R.string.action_save)
                )
                SmallTonalButton(
                    onClick = {
                    onIntent(ReadBookIntent.ResetContentEdit)
                    },
                    text = stringResource(R.string.reset)
                )
                SmallTonalButton(
                    onClick = {
                    context.sendToClip("${state.contentEditTitle}\n${editorState.text}")
                    }, text = stringResource(R.string.copy_all)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.contentEditLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppCircularProgressIndicator()
                }
            } else {
                AppTextField(
                    state = editorState,
                    scrollState = editorScrollState,
                    onTextLayout = { getResult ->
                        textLayoutResult = getResult()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(400.dp),
                )
            }

            if (state.contentEditIsLocalTxt) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppCheckbox(
                        checked = state.contentEditSaveToSource,
                        onCheckedChange = { onIntent(ReadBookIntent.SetContentEditSaveToSource(it)) },
                    )
                    AppText(
                        text = stringResource(R.string.save_to_source),
                        style = LegadoTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
