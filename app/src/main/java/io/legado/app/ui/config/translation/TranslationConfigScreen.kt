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
import io.legado.app.data.repository.TranslationCacheRepository
import io.legado.app.ui.theme.adaptiveContentPadding
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.SplicedColumnGroup
import io.legado.app.ui.widget.components.settingItem.DropdownListSettingItem
import io.legado.app.ui.widget.components.settingItem.InputSettingItem
import io.legado.app.ui.widget.components.settingItem.SliderSettingItem
import io.legado.app.ui.widget.components.settingItem.SwitchSettingItem
import io.legado.app.ui.widget.components.topbar.GlassMediumFlexibleTopAppBar
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarDefaults
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import org.koin.compose.koinInject

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationConfigScreen(
    onBackClick: () -> Unit
) {
    val scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior()
    val translationCacheRepository: TranslationCacheRepository = koinInject()

    var tempPrompt by remember { mutableStateOf(TranslationConfig.llmPrompt) }
    var cacheSize by remember { mutableStateOf(0L) }

    remember(cacheSize) {
        cacheSize = translationCacheRepository.getTranslationCacheSize()
    }

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
                SplicedColumnGroup {
                    SwitchSettingItem(
                        title = stringResource(R.string.llm_translate_enabled),
                        checked = TranslationConfig.llmTranslateEnabled,
                        onCheckedChange = { TranslationConfig.llmTranslateEnabled = it }
                    )
                }
            }

            if (TranslationConfig.llmTranslateEnabled) {
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
                        }
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
                            defaultValue = 3000f,
                            valueRange = 1000f..6000f,
                            steps = 9,
                            onValueChange = { TranslationConfig.llmMaxCharsPerChunk = it.toInt() }
                        )

                        SliderSettingItem(
                            title = stringResource(R.string.llm_concurrent_chunks),
                            value = TranslationConfig.llmConcurrentChunks.toFloat(),
                            defaultValue = 1f,
                            valueRange = 1f..4f,
                            steps = 2,
                            onValueChange = { TranslationConfig.llmConcurrentChunks = it.toInt() }
                        )

                        SliderSettingItem(
                            title = stringResource(R.string.llm_retry_count),
                            value = TranslationConfig.llmRetryCount.toFloat(),
                            defaultValue = 2f,
                            valueRange = 0f..5f,
                            steps = 4,
                            onValueChange = { TranslationConfig.llmRetryCount = it.toInt() }
                        )
                    }
                }

                item {
                    SplicedColumnGroup(title = stringResource(R.string.translation_prompt)) {
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

                item {
                    SplicedColumnGroup(title = stringResource(R.string.translation_cache)) {
                        val formattedSize = formatCacheSize(cacheSize)
                        SwitchSettingItem(
                            title = stringResource(R.string.translation_cache_size, formattedSize),
                            checked = false,
                            onCheckedChange = { }
                        )
                    }
                }

                item {
                    SplicedColumnGroup(title = stringResource(R.string.book_info_translation_section)) {
                        SwitchSettingItem(
                            title = stringResource(R.string.translate_book_info),
                            description = stringResource(R.string.translate_book_info_summary),
                            checked = TranslationConfig.translateBookInfoEnabled,
                            onCheckedChange = { TranslationConfig.translateBookInfoEnabled = it }
                        )
                    }
                }
            }
        }
    }
}

private fun formatCacheSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}