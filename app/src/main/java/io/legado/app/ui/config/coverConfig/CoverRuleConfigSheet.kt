package io.legado.app.ui.config.coverConfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.BookCover
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.MediumIconButton
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.ui.widget.components.checkBox.CheckboxItem
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import splitties.init.appCtx

private const val DEFAULT_SEARCH_URL = "data:;base64,{{java.base64Encode(key)}},{\"type\":\"lyc\"}"
private val DEFAULT_COVER_RULE = """
    @js:
    var key = java.hexDecodeToString(result);
    var url1 = `https://pre-api.tuishujun.com/api/searchBook?search_value=${'$'}{key}&page=1&pageSize=20`;
    var url2 = `http://m.ypshuo.com/api/novel/search?keyword=${'$'}{key}&searchType=1&page=1`;
    var [rr1, rr2] = java.ajaxAll([url1, url2]).map(r => r.body());
    function jjson(str, rule) {
        try {
            return com.jayway.jsonpath.JsonPath.read(str, rule);
        } catch (e) {
            return [];
        }
    }
    rr1 = jjson(rr1, '$.data.data[*]');
    rr2 = jjson(rr2, '$.data.data[*]');
    var na = String(book.name),
        au = String(book.author);
    function search() {
        for (let char of rr1) {
            //本地书名包含搜索结果书名
            if (na.includes(char.title + '')) {
                let au2 = char.author_nickname + '';
                //作者匹配
                if (au.includes(au2) || au2.includes(au)) {
                    return char.cover;
                }
            }
        }
        for (let char of rr2) {
            if (na.includes(char.novel_name + '')) {
                let au2 = char.author_name + '';
                if (au.includes(au2) || au2.includes(au)) {
                    return char.novel_img;
                }
            }
        }
        return '';
    }
    search()
""".trimIndent()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverRuleConfigSheet(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    var enable by remember { mutableStateOf(false) }
    var searchUrl by remember { mutableStateOf("") }
    var coverRule by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val rule = withContext(Dispatchers.IO) {
            BookCover.getCoverRule()
        }
        enable = rule.enable
        searchUrl = rule.searchUrl
        coverRule = rule.coverRule
    }

    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.cover_rule),
        startAction = {
            MediumIconButton(
                imageVector = Icons.Default.SettingsBackupRestore,
                onClick = {
                    searchUrl = DEFAULT_SEARCH_URL
                    coverRule = DEFAULT_COVER_RULE
                    appCtx.toastOnUi(R.string.restore_default)
                }
            )
        },
        endAction = {
            MediumIconButton(
                imageVector = Icons.Default.Save,
                onClick = {
                    if (searchUrl.isBlank() || coverRule.isBlank()) {
                        appCtx.toastOnUi("搜索url和cover规则不能为空")
                    } else {
                        BookCover.CoverRule(enable, searchUrl, coverRule).let { config ->
                            BookCover.saveCoverRule(config)
                        }
                        onDismissRequest()
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CheckboxItem(
                title = stringResource(R.string.enable),
                checked = enable,
                onCheckedChange = { enable = it }
            )

            AppTextField(
                value = searchUrl,
                onValueChange = { searchUrl = it },
                label = stringResource(R.string.search_via_url),
                modifier = Modifier.fillMaxWidth()
            )

            AppTextField(
                value = coverRule,
                onValueChange = { coverRule = it },
                label = stringResource(R.string.cover_rule_edit),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}
