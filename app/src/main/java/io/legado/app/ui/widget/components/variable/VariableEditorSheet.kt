package io.legado.app.ui.widget.components.variable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@Immutable
data class VariableEditorUiState(
    val title: String,
    val key: String,
    val value: String,
    val comment: String,
)

@Composable
fun VariableEditorSheet(
    state: VariableEditorUiState?,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AppModalBottomSheet(
        show = state != null,
        onDismissRequest = onDismissRequest,
        title = state?.title,
        endAction = {
            MediumTonalButton(
                icon = AppIcons.Check,
                contentDescription = stringResource(R.string.action_save),
                onClick = onSave,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            AppTextField(
                value = state?.value.orEmpty(),
                onValueChange = onValueChange,
                label = "variable",
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            AppText(
                text = stringResource(R.string.variable_comment),
                style = LegadoTheme.typography.labelMediumEmphasized,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
            )
            SelectionContainer {
                AppText(
                    text = state?.comment.orEmpty(),
                    style = LegadoTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                )
            }
        }
    }
}
