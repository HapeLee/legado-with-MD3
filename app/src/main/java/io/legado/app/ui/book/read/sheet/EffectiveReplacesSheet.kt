package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.ReadBook
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet

@Composable
fun EffectiveReplacesSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onOpenReplaceEditor: (id: Long, pattern: String?) -> Unit,
    onReplaceRuleChanged: () -> Unit,
) {
    val effectiveRules = remember {
        ReadBook.curTextChapter?.effectiveReplaceRules ?: emptyList()
    }

    var isEdited by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = {
            if (isEdited) onReplaceRuleChanged()
            onDismissRequest()
        },
        title = stringResource(R.string.effective_replaces),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(effectiveRules, key = { it.id }) { rule ->
                Text(
                    text = rule.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onOpenReplaceEditor(rule.id, rule.pattern)
                        }
                        .padding(vertical = 12.dp),
                )
                HorizontalDivider()
            }
        }
    }
}
