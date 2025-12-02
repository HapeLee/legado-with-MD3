package io.legado.app.di

import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookmarkModule = module {

    viewModel {
        AllBookmarkViewModel(
            androidApplication(),
            get()
        )
    }
}