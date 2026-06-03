package io.legado.app.ui.book.read.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.book.read.ConfigUpdate
import io.legado.app.ui.book.read.ReadBookIntent
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.tabRow.CardTabRow
import kotlinx.coroutines.launch

@Composable
fun ReadStyleSheet(
    onDismissRequest: () -> Unit,
    onOpenPaddingConfig: () -> Unit,
    onOpenMoreConfig: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenShadowSet: () -> Unit,
    onOpenUnderlineConfig: () -> Unit,
    onOpenRegexColor: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onToggleDayNight: () -> Unit,
    onIntent: (ReadBookIntent) -> Unit,
) {
    var showTextTitle by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        show = true,
        onDismissRequest = {
            onIntent(ReadBookIntent.SaveReadStyleConfig)
            onDismissRequest()
        },
        title = stringResource(R.string.read_config),
    ) {
        ReadStyleContent(
            onOpenPaddingConfig = onOpenPaddingConfig,
            onOpenMoreConfig = onOpenMoreConfig,
            onOpenBgTextConfig = onOpenBgTextConfig,
            onOpenTextTitle = { showTextTitle = true },
            onOpenFontSelect = onOpenFontSelect,
            onToggleDayNight = onToggleDayNight,
            onIntent = onIntent,
        )

        TextTitlePage(
            show = showTextTitle,
            onDismissRequest = { showTextTitle = false },
            onOpenShadowSet = onOpenShadowSet,
            onOpenUnderlineConfig = onOpenUnderlineConfig,
            onOpenRegexColor = onOpenRegexColor,
            onOpenFontSelect = onOpenFontSelect,
            onIntent = onIntent,
        )
    }
}

@Composable
fun ReadStyleContent(
    onOpenPaddingConfig: () -> Unit,
    onOpenMoreConfig: () -> Unit,
    onOpenBgTextConfig: (Int) -> Unit,
    onOpenTextTitle: () -> Unit,
    onOpenFontSelect: () -> Unit,
    onToggleDayNight: () -> Unit,
    modifier: Modifier = Modifier,
    onIntent: (ReadBookIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            currentPage = page
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f, fill = false),
        ) { page ->
            when (page) {
                0 -> GlobalThemePage(
                    onToggleDayNight = onToggleDayNight,
                    onOpenBgTextConfig = onOpenBgTextConfig,
                    onOpenTextTitle = onOpenTextTitle,
                    onOpenPaddingConfig = onOpenPaddingConfig,
                    onShareLayoutChange = { shareLayout ->
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.ShareLayout(shareLayout)))
                    },
                    onStyleSelect = { index ->
                        onIntent(ReadBookIntent.UpdateConfig(ConfigUpdate.StyleSelect(index)))
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onIntent = onIntent,
                )

                1 -> SystemMenuPage(
                    onIntent = onIntent,
                )
                2 -> HeaderFooterPage(
                    onOpenFontSelect = onOpenFontSelect,
                    onIntent = onIntent,
                )
            }
        }

        val tabTitles = listOf(
            stringResource(R.string.read_config_global_theme),
            stringResource(R.string.read_config_menu_system),
            stringResource(R.string.header_footer),
            stringResource(R.string.more_setting),
        )
        CardTabRow(
            tabTitles = tabTitles,
            selectedTabIndex = currentPage,
            onTabSelected = { index ->
                if (index < 3) {
                    scope.launch { pagerState.animateScrollToPage(index) }
                } else {
                    onOpenMoreConfig()
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
