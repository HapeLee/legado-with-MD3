package io.legado.app.ui.widget.components.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RunningWithErrors
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.button.series.MediumPlainButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 通用编辑数据包装，用于适配不同的规则
 */
data class RuleEditFields(
    val name: String = "",
    val rule1: String = "",
    val rule2: String = "",
    val extra: String = ""
)

/**
 * 测试结果：每一行的匹配状态
 */
data class TestLineResult(
    val line: String,
    val matched: Boolean,
    val matchResult: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RuleEditSheet(
    show: Boolean,
    rule: T?,
    title: String,
    label1: String,
    label2: String,
    onDismissRequest: () -> Unit,
    onSave: (T) -> Unit,
    onCopy: (T) -> Unit,
    onPaste: () -> T?,
    toFields: (T?) -> RuleEditFields,
    fromFields: (RuleEditFields, T?) -> T,
    showTestButton: Boolean = false,
) {
    val scope = rememberCoroutineScope()

    val initialFields = remember(show, rule) { toFields(rule) }
    var name by remember(show, rule) { mutableStateOf(initialFields.name) }
    var rule1 by remember(show, rule) { mutableStateOf(initialFields.rule1) }
    var rule2 by remember(show, rule) { mutableStateOf(initialFields.rule2) }

    var showMenu by remember(show, rule) { mutableStateOf(false) }

    // Test results state
    var testResults by remember(show, rule) { mutableStateOf<List<TestLineResult>?>(null) }
    var testError by remember(show, rule) { mutableStateOf<String?>(null) }

    // Pre-resolve string resources at composable level
    val regexIsEmptyStr = stringResource(R.string.regex_is_empty)
    val exampleIsEmptyStr = stringResource(R.string.example_is_empty)
    val invalidRegexStr = stringResource(R.string.invalid_regex)

    fun getCurrentEntity() = fromFields(RuleEditFields(name, rule1, rule2), rule)

    fun runTest() {
        if (rule1.isBlank()) {
            testError = regexIsEmptyStr
            testResults = null
            return
        }
        if (rule2.isBlank()) {
            testError = exampleIsEmptyStr
            testResults = null
            return
        }

        val pattern: Pattern
        try {
            pattern = Pattern.compile(rule1, Pattern.MULTILINE)
        } catch (e: PatternSyntaxException) {
            testError = e.message ?: invalidRegexStr
            testResults = null
            return
        }

        testError = null
        val lines = rule2.lines()
        val fullText = "\n${rule2}"
        val matcher = pattern.matcher(fullText)

        // Collect all match ranges (character offsets in fullText)
        val matchRanges = mutableListOf<IntRange>()
        while (matcher.find()) {
            matchRanges.add(matcher.start()..matcher.end())
        }

        // Map each line: check if any match overlaps with this line's range in fullText
        var offset = 1 // first \n
        testResults = lines.map { line ->
            val lineStart = offset
            val lineEnd = offset + line.length
            val matched = matchRanges.any { it.first < lineEnd && it.last > lineStart }
            offset = lineEnd + 1 // +1 for the \n that was between lines
            TestLineResult(line = line, matched = matched, matchResult = if (matched) line else null)
        }
    }

    AppModalBottomSheet(
        title = title,
        startAction = {
            MediumPlainButton(
                onClick = onDismissRequest,
                icon = Icons.Default.Close,
                contentDescription = stringResource(R.string.close),
            )
        },
        endAction = {
            Box{
                MediumPlainButton(
                    onClick = { showMenu = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_menu)
                )
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.copy_rule),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = {
                            onCopy(getCurrentEntity())
                            showMenu = false
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(R.string.paste_rule),
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = {
                            scope.launch {
                                onPaste()?.let { pasted ->
                                    val fields = toFields(pasted)
                                    name = fields.name
                                    rule1 = fields.rule1
                                    rule2 = fields.rule2
                                }
                            }
                            showMenu = false
                        }
                    )
                }
            }
        },
        show = show,
        onDismissRequest = onDismissRequest
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 120.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = { name = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = stringResource(R.string.name),
                    singleLine = true
                )
                AppTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = rule1,
                    onValueChange = { rule1 = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = label1
                )
                AppTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = rule2,
                    onValueChange = { rule2 = it },
                    backgroundColor = LegadoTheme.colorScheme.surface,
                    label = label2,
                    minLines = 3
                )

                if (showTestButton) {
                    // Test error
                    testError?.let { error ->
                        AppText(
                            text = error,
                            color = LegadoTheme.colorScheme.error,
                            style = LegadoTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Test results
                    testResults?.let { results ->
                        val matchedCount = results.count { it.matched }
                        val totalCount = results.count { it.line.isNotBlank() }
                        AppText(
                            text = "$matchedCount / $totalCount",
                            style = LegadoTheme.typography.labelMedium,
                            color = LegadoTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        results.forEach { result ->
                            if (result.line.isBlank()) return@forEach
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = if (result.matched) Icons.Default.Check else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (result.matched) {
                                        LegadoTheme.colorScheme.primary
                                    } else {
                                        LegadoTheme.colorScheme.error
                                    },
                                    modifier = Modifier.size(18.dp),
                                )
                                AppText(
                                    text = result.line,
                                    style = LegadoTheme.typography.bodySmall,
                                    color = if (result.matched) {
                                        LegadoTheme.colorScheme.onSurface
                                    } else {
                                        LegadoTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showTestButton) {
                    AppFloatingActionButton(
                        onClick = { runTest() },
                        tooltipText = stringResource(R.string.test),
                        icon = Icons.Default.RunningWithErrors,
                        containerColor = LegadoTheme.colorScheme.secondaryContainer,
                    )
                }
                AppFloatingActionButton(
                    onClick = { onSave(getCurrentEntity()) },
                    tooltipText = stringResource(R.string.action_save),
                    icon = Icons.Default.Save
                )
            }
        }
    }
}
