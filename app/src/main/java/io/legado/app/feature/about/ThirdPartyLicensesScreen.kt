package io.legado.app.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import com.mikepenz.aboutlibraries.util.withJson
import io.legado.app.R
import io.legado.app.ui.theme.ComposeEngine
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.AppScaffold
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.settingItem.ClickableSettingItem
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassSmallTopAppBar
import io.legado.app.ui.widget.components.topbar.TopBarNavigationButton
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@Stable
private data class LicenseLibrary(
    val id: String,
    val name: String,
    val version: String?,
    val website: String?,
    val licenses: ImmutableList<License>,
) {
    val summary = (listOfNotNull(version?.takeIf(String::isNotBlank)) +
        licenses.map(License::name).filter(String::isNotBlank))
        .joinToString(" · ").takeIf(String::isNotBlank)
}

@Composable
internal fun ThirdPartyLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val libraries by produceState<Result<ImmutableList<LicenseLibrary>>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                Libs.Builder().withJson(context, R.raw.aboutlibraries).build().libraries
                    .map { library ->
                        LicenseLibrary(
                            id = library.artifactId,
                            name = library.name.ifBlank { library.uniqueId },
                            version = library.artifactVersion,
                            website = library.website,
                            licenses = library.licenses.toImmutableList(),
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toImmutableList()
            }.onFailure { Timber.w(it, "Unable to load third-party licenses") }
        }
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    AppScaffold(
        topBar = {
            GlassSmallTopAppBar(
                title = stringResource(R.string.about_third_party_licenses),
                navigationIcon = { TopBarNavigationButton(onClick = onBack) },
            )
        },
    ) { padding ->
        val loaded = libraries
        val entries = loaded?.getOrNull()
        if (!entries.isNullOrEmpty()) {
            LibrariesContainer(entries, padding, onSelect = { selectedId = it })
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    stringResource(
                        when {
                            loaded == null -> R.string.loading
                            loaded.isFailure -> R.string.load_failed
                            else -> R.string.empty
                        }
                    )
                )
            }
        }
    }
    libraries?.getOrNull()?.firstOrNull { it.id == selectedId }?.let { library ->
        LibraryDetails(library, onDismiss = { selectedId = null })
    }
}

@Composable
private fun LibrariesContainer(
    libraries: ImmutableList<LicenseLibrary>,
    padding: PaddingValues,
    onSelect: (String) -> Unit,
) {
    val useMiuix = LegadoTheme.composeEngine == ComposeEngine.Miuix
    LazyColumn(
        modifier = Modifier.fillMaxSize()
            .consumeWindowInsets(padding)
            .padding(horizontal = if (useMiuix) 0.dp else 16.dp),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(if (useMiuix) 0.dp else 4.dp),
    ) {
        items(libraries, key = LicenseLibrary::id) { library ->
            ClickableSettingItem(
                title = library.name,
                description = library.summary,
                onClick = { onSelect(library.id) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryDetails(library: LicenseLibrary, onDismiss: () -> Unit) {
    AppModalBottomSheet(show = true, title = library.name, onDismissRequest = onDismiss) {
        SelectionContainer {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                library.version?.takeIf(String::isNotBlank)?.let { AppText(it) }
                LicenseLink(library.website)
                library.licenses.forEach { license ->
                    AppText(
                        text = license.name.ifBlank { stringResource(R.string.license) },
                        modifier = Modifier.semantics { heading() },
                        style = LegadoTheme.typography.titleSmall,
                    )
                    LicenseLink(license.url)
                    license.licenseContent?.takeIf(String::isNotBlank)?.let { content ->
                        AppText(content.replace("<br />", "\n").replace("<br/>", "\n"))
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseLink(url: String?) {
    if (url == null || (!url.startsWith("https://") && !url.startsWith("http://"))) return
    AppText(
        text = buildAnnotatedString { withLink(LinkAnnotation.Url(url)) { append(url) } },
        color = LegadoTheme.colorScheme.primary,
    )
}
