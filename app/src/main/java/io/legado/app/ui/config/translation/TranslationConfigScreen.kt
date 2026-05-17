package io.legado.app.ui.config.translation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationConfigScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()

    var tempPrompt by remember { mutableStateOf(TranslationConfig.llmPrompt) }

    AppScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = stringResource(R.string.translation_config),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarNavigationButton(onClick = onBackClick)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = adaptiveContentPadding(
                top = paddingValues.calculateTopPadding(),
                bottom = 120.dp
            )
        ) {
            item {
                SplicedColumnGroup(title = stringResource(R.string.translation_provider)) {
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_provider),
                        selectedValue = TranslationConfig.llmProvider,
                        displayEntries = TranslationConfig.providerDisplayNames.toTypedArray(),
                        entryValues = TranslationConfig.providerValues.toTypedArray(),
                        onValueChange = { TranslationConfig.llmProvider = it }
                    )
                }
            }

            item {
                SplicedColumnGroup(title = stringResource(R.string.translation_options)) {
                    val languageEntries = TranslationConfig.targetLanguages.map { it.second }.toTypedArray()
                    val languageValues = TranslationConfig.targetLanguages.map { it.first }.toTypedArray()
                    DropdownListSettingItem(
                        title = stringResource(R.string.llm_target_language),
                        selectedValue = TranslationConfig.llmTargetLanguage,
                        displayEntries = languageEntries,
                        entryValues = languageValues,
                        onValueChange = { TranslationConfig.llmTargetLanguage = it }
                    )

                    SliderSettingItem(
                        title = stringResource(R.string.llm_max_chars_per_chunk),
                        value = TranslationConfig.llmMaxCharsPerChunk.toFloat(),
                        defaultValue = 10000f,
                        valueRange = 1000f..10000f,
                        steps = 17,
                        onValueChange = { TranslationConfig.llmMaxCharsPerChunk = it.toInt() }
                    )
                }
            }

            if (TranslationConfig.llmProvider == TranslationConfig.PROVIDER_OPENAI) {
                item {
                    SplicedColumnGroup(title = stringResource(R.string.openai_config)) {
                        InputSettingItem(
                            title = stringResource(R.string.llm_base_url),
                            value = TranslationConfig.llmBaseUrl,
                            onConfirm = { TranslationConfig.llmBaseUrl = it }
                        )

                        InputSettingItem(
                            title = stringResource(R.string.llm_api_key),
                            value = TranslationConfig.llmApiKey,
                            onConfirm = { TranslationConfig.llmApiKey = it }
                        )

                        InputSettingItem(
                            title = stringResource(R.string.llm_model),
                            value = TranslationConfig.llmModel,
                            onConfirm = { TranslationConfig.llmModel = it }
                        )

                        InputSettingItem(
                            title = stringResource(R.string.llm_prompt),
                            value = tempPrompt,
                            onConfirm = {
                                tempPrompt = it
                                TranslationConfig.llmPrompt = it
                            }
                        )
                    }
                }
            }
        }
    }
}