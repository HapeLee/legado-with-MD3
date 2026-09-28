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
internal fun rememberPlayerThemeOverride(state: ReadAloudPlayerUiState) =
    rememberPlayerThemeOverride(state.bookName, state.author, state.coverPath, state.sourceOrigin)

@Composable
internal fun rememberPlayerThemeOverride(
    bookName: String,
    author: String,
    coverPath: String?,
    sourceOrigin: String?,
) = run {
    val imageLoader: ImageLoader = koinInject()
    val coverSettings = koinInject<CoverSettingsGateway>().currentSettings
    val isNight = LegadoTheme.isDark
    val useDefaultCover = usesDefaultBookCover(coverPath)
    val defaultCoverPaths =
        if (isNight) coverSettings.defaultCoverDark else coverSettings.defaultCover
    val resolvedCoverPath = remember(
        bookName,
        author,
        coverPath,
        useDefaultCover,
        isNight,
        defaultCoverPaths,
    ) {
        if (useDefaultCover) {
            BookCoverModel.getRandomDefaultPath(seed = bookName, isNight = isNight)
        } else {
            coverPath
        }
    }
    val resolvedSourceOrigin = if (useDefaultCover) null else sourceOrigin
    val loadOnlyWifi = !useDefaultCover && coverSettings.loadOnlyOnWifi
    val requestKey = remember(resolvedCoverPath, resolvedSourceOrigin, loadOnlyWifi) {
        listOf(resolvedCoverPath, resolvedSourceOrigin, loadOnlyWifi)
    }
    val seedColor = rememberImageSeedColor(
        imageLoader = imageLoader,
        data = resolvedCoverPath,
        requestKey = requestKey,
    ) {
        extras[CoverExtras.SourceOrigin] = resolvedSourceOrigin
        extras[CoverExtras.LoadOnlyWifi] = loadOnlyWifi
    }
    rememberThemeOverride(seedColor)
}
