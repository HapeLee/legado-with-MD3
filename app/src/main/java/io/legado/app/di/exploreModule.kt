package io.legado.app.di

import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.ui.book.explore.ExploreShowViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val exploreModule = module {
    single<ExploreRepository> { ExploreRepositoryImpl(get()) }
    viewModel { ExploreShowViewModel(get()) }
}