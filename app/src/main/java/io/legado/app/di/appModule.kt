package io.legado.app.di

import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.DirectLinkUploadRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.data.repository.SearchRepositoryImpl
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.help.coil.CoverFetcher
import io.legado.app.help.coil.CoverInterceptor
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.okHttpClientManga
import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.book.import.local.ImportBookViewModel
import io.legado.app.ui.book.import.remote.RemoteBookViewModel
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtTocRuleViewModel
import io.legado.app.ui.config.backupConfig.BackupConfigViewModel
import io.legado.app.ui.config.coverConfig.CoverConfigViewModel
import io.legado.app.ui.config.otherConfig.OtherConfigViewModel
import io.legado.app.ui.config.readConfig.ReadConfigViewModel
import io.legado.app.ui.config.themeConfig.ThemeConfigViewModel
import io.legado.app.ui.dict.rule.DictRuleViewModel
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.my.MyViewModel
import io.legado.app.ui.main.rss.RssViewModel
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.rss.source.manage.RssSourceViewModel
import io.legado.app.ui.rss.article.RssArticlesViewModel
import io.legado.app.ui.rss.article.RssSortViewModel
import io.legado.app.ui.rss.read.ReadRssViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {

    single { get<AppDatabase>().readRecordDao }
    single { get<AppDatabase>().bookDao }
    single { get<AppDatabase>().bookChapterDao }
    single { get<AppDatabase>().bookGroupDao }

    singleOf(::ReadRecordRepository)
    singleOf(::BookRepository)
    singleOf(::BookGroupRepository)
    singleOf(::SearchContentRepository)
    singleOf(::RemoteBookRepository)
    singleOf(::ExploreKindUiUseCase)

    single<UploadRepository> { DirectLinkUploadRepository() }
    single<ExploreRepository> { ExploreRepositoryImpl(get()) }
    single<SearchRepository> { SearchRepositoryImpl(get()) }

    single<ImageLoader> {
        ImageLoader.Builder(get())
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(CoverInterceptor())
                add(CoverFetcher.Factory(okHttpClient, okHttpClientManga))
            }
            .crossfade(true)
            .build()
    }

    viewModelOf(::DictRuleViewModel)
    viewModelOf(::RssSourceViewModel)
    viewModelOf(::RssSortViewModel)
    viewModelOf(::RssArticlesViewModel)
    viewModelOf(::ReadRssViewModel)
    viewModelOf(::ReadRecordViewModel)
    viewModelOf(::ExploreShowViewModel)
    viewModelOf(::MyViewModel)
    viewModelOf(::BookshelfViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::GroupViewModel)
    viewModelOf(::ReplaceRuleViewModel)
    viewModelOf(::AllBookmarkViewModel)
    viewModelOf(::TxtTocRuleViewModel)
    viewModelOf(::OtherConfigViewModel)
    viewModelOf(::ReadConfigViewModel)
    viewModelOf(::CoverConfigViewModel)
    viewModelOf(::ThemeConfigViewModel)
    viewModelOf(::BackupConfigViewModel)
    viewModelOf(::TocViewModel)
    viewModelOf(::ImportBookViewModel)
    viewModelOf(::RemoteBookViewModel)
    viewModelOf(::BookInfoViewModel)
    viewModelOf(::ChangeCoverViewModel)
    viewModelOf(::ChangeBookSourceComposeViewModel)
    viewModelOf(::ChangeBookSourceViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::RssViewModel)
    viewModelOf(::SearchViewModel)

    viewModel { (route: ReplaceEditRoute) ->
        ReplaceEditViewModel(
            app = get(),
            replaceRuleDao = get(),
            route = route
        )
    }

    viewModelOf(::SearchContentViewModel)
}

