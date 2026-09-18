package io.legado.app.ui.book.readaloud.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.help.coil.CoverExtras
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.theme.rememberImageSeedColor
import io.legado.app.ui.theme.rememberThemeOverride
import io.legado.app.ui.widget.components.image.cover.usesDefaultBookCover
import org.koin.compose.koinInject
import io.legado.app.model.BookCover as BookCoverModel

/**
 * 听书播放界面的封面取色主题。
 *
 * 与阅读器内播放页同源：按显示封面（含默认封面日夜选择）取种子色，再派生主题覆盖。
 */
@Composable
internal fun rememberPlayerThemeOverride(state: ReadAloudPlayerUiState) = run {
    val imageLoader: ImageLoader = koinInject()
    val coverSettings = koinInject<CoverSettingsGateway>().currentSettings
    val isNight = LegadoTheme.isDark
    val useDefaultCover = usesDefaultBookCover(state.coverPath)
    val defaultCoverPaths =
        if (isNight) coverSettings.defaultCoverDark else coverSettings.defaultCover
    val coverPath = remember(
        state.bookName,
        state.author,
        state.coverPath,
        useDefaultCover,
        isNight,
        defaultCoverPaths,
    ) {
        if (useDefaultCover) {
            BookCoverModel.getRandomDefaultPath(seed = state.bookName, isNight = isNight)
        } else {
            state.coverPath
        }
    }
    val sourceOrigin = if (useDefaultCover) null else state.sourceOrigin
    val loadOnlyWifi = !useDefaultCover && coverSettings.loadOnlyOnWifi
    val requestKey = remember(coverPath, sourceOrigin, loadOnlyWifi) {
        listOf(coverPath, sourceOrigin, loadOnlyWifi)
    }
    val seedColor = rememberImageSeedColor(
        imageLoader = imageLoader,
        data = coverPath,
        requestKey = requestKey,
    ) {
        extras[CoverExtras.SourceOrigin] = sourceOrigin
        extras[CoverExtras.LoadOnlyWifi] = loadOnlyWifi
    }
    rememberThemeOverride(seedColor)
}
