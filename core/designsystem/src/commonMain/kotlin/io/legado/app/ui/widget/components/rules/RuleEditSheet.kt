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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.core.designsystem.res.Res
import io.legado.app.core.designsystem.res.action_save
import io.legado.app.core.designsystem.res.close
import io.legado.app.core.designsystem.res.copy_rule
import io.legado.app.core.designsystem.res.example_is_empty
import io.legado.app.core.designsystem.res.invalid_regex
import io.legado.app.core.designsystem.res.more_menu
import io.legado.app.core.designsystem.res.name
import io.legado.app.core.designsystem.res.paste_rule
import io.legado.app.core.designsystem.res.regex_is_empty
import io.legado.app.core.designsystem.res.test
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenu
import io.legado.app.ui.widget.components.menuItem.RoundDropdownMenuItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通用编辑数据包装，用于适配不同的规则
 */
data class RuleEditFields(
    val name: String = "",
    val rule1: String = "",
    val rule2: String = "",
    val rule3: String = "",
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

/**
 * M1-3x-pre 从 `:core:ui` 搬进 `:core:designsystem/commonMain`（包名不变 ⇒ `:app` 与
 * `feature/{txttocrules,dict}` 的 import 零改动）。搬迁原因：后两个 Feature 要转 CMP，
 * 而它们的规则编辑弹层（本文件的 `RuleEditSheet`）还住在 Android 专用的 `:core:ui` 里。
 *
 * 10 条 `R.string.*` 换成 `Res.string.*`（M1-3j 的 CMP 资源配方）：`more_menu` designsystem
 * 已有，其余 9 条本次新增；11 条全部与 `:core:ui` 的 Android 同名条目逐字一致（搬前脚本比对，
 * 四语言）。其余依赖全是 designsystem 自己的组件与 Compose 标准库。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RuleEditSheet(
    show: Boolean,
    rule: T?,
    title: String,
    label1: String,
    label2: String,
    label3: String? = null,
    onDismissRequest: () -> Unit,
    onSave: (T) -> Unit,
    onCopy: (T) -> Unit,
    onPaste: () -> T?,
    toFields: (T?) -> RuleEditFields,
    fromFields: (RuleEditFields, T?) -> T,
    showTestButton: Boolean = false,
    onTest: (suspend (rule: String, example: String) -> List<TestLineResult>?)? = null,
) {
    val scope = rememberCoroutineScope()

    val initialFields = remember(show, rule) { toFields(rule) }
    var name by remember(show, rule) { mutableStateOf(initialFields.name) }
    var rule1 by remember(show, rule) { mutableStateOf(initialFields.rule1) }
    var rule2 by remember(show, rule) { mutableStateOf(initialFields.rule2) }
    var rule3 by remember(show, rule) { mutableStateOf(initialFields.rule3) }

    var showMenu by remember(show, rule) { mutableStateOf(false) }

    // Test results state
    var testResults by remember(show, rule) { mutableStateOf<List<TestLineResult>?>(null) }
    var testError by remember(show, rule) { mutableStateOf<String?>(null) }
    var testRunning by remember(show, rule) { mutableStateOf(false) }

    // Pre-resolve string resources at composable level
    val regexIsEmptyStr = stringResource(Res.string.regex_is_empty)
    val exampleIsEmptyStr = stringResource(Res.string.example_is_empty)
    val invalidRegexStr = stringResource(Res.string.invalid_regex)

    fun getCurrentEntity() = fromFields(RuleEditFields(name, rule1, rule2, rule3), rule)

    fun runTest() {
        if (testRunning) return
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

        val testCallback = onTest
        if (testCallback == null) {
            testError = invalidRegexStr
            testResults = null
            return
        }

        // Capture inputs for background processing
        val capturedRule1 = rule1
        val capturedRule2 = rule2
        testRunning = true
        testError = null
        testResults = null

        scope.launch(Dispatchers.Default) {
            try {
                val results = testCallback(capturedRule1, capturedRule2)
                testResults = results
            } catch (_: Exception) {
                testError = invalidRegexStr
                testResults = null
            } finally {
                testRunning = false
            }
        }
    }

    AppModalBottomSheet(
        title = title,
        startAction = {
            MediumTonalButton(
                onClick = onDismissRequest,
                icon = Icons.Default.Close,
                contentDescription = stringResource(Res.string.close),
            )
        },
        endAction = {
            Box{
                MediumTonalButton(
                    onClick = { showMenu = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(Res.string.more_menu)
                )
                RoundDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    RoundDropdownMenuItem(
                        text = stringResource(Res.string.copy_rule),
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, null) },
                        onClick = {
                            onCopy(getCurrentEntity())
                            showMenu = false
                        }
                    )
                    RoundDropdownMenuItem(
                        text = stringResource(Res.string.paste_rule),
                        leadingIcon = { Icon(Icons.Default.ContentPaste, null) },
                        onClick = {
                            scope.launch {
                                onPaste()?.let { pasted ->
                                    val fields = toFields(pasted)
                                    name = fields.name
                                    rule1 = fields.rule1
                                    rule2 = fields.rule2
                                    rule3 = fields.rule3
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
                    label = stringResource(Res.string.name),
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
                label3?.let { label ->
                    AppTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = rule3,
                        onValueChange = { rule3 = it },
                        backgroundColor = LegadoTheme.colorScheme.surface,
                        label = label
                    )
                }

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
                        tooltipText = stringResource(Res.string.test),
                        icon = Icons.Default.RunningWithErrors,
                        containerColor = LegadoTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppFloatingActionButton(
                    onClick = { onSave(getCurrentEntity()) },
                    tooltipText = stringResource(Res.string.action_save),
                    icon = Icons.Default.Save
                )
            }
        }
    }
}
