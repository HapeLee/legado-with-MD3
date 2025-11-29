package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val readRecordModule = module {
    single { get<AppDatabase>().readRecordDao }
    single { get<AppDatabase>().bookDao }
    single { get<AppDatabase>().bookChapterDao }
    single { ReadRecordRepository(get()) }

    viewModel { ReadRecordViewModel(get(), get(), get()) }
}