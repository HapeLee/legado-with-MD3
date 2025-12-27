package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.DirectLinkUploadRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    viewModel { ReplaceEditViewModel(get(), get(), get()) }

    viewModel { ReplaceRuleViewModel(androidApplication()) }

    // ReadRecord
    single { get<AppDatabase>().readRecordDao }
    single { get<AppDatabase>().bookDao }
    single { get<AppDatabase>().bookChapterDao }
    single { ReadRecordRepository(get()) }
    viewModel { ReadRecordViewModel(get(), get(), get()) }

    single<UploadRepository> { DirectLinkUploadRepository() }

    // Explore
    single<ExploreRepository> { ExploreRepositoryImpl(get()) }
    viewModel { ExploreShowViewModel(get()) }

    // Bookmark
    viewModel {
        AllBookmarkViewModel(
            androidApplication(),
            get()
        )
    }

    // Search
    single { SearchContentRepository() }
    single { BookRepository() }
    viewModel { SearchContentViewModel(get(), get()) }
}
