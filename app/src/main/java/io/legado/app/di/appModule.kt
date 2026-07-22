package io.legado.app.di

import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import io.legado.app.data.AppDatabase
import io.legado.app.data.repository.AiArtifactRepository
import io.legado.app.data.repository.AiChatRepository
import io.legado.app.data.repository.AiMemoryRepository
import io.legado.app.data.repository.AiProfileRepository
import io.legado.app.data.repository.AiPromptPresetRepository
import io.legado.app.data.repository.AiTextRepositoryImpl
import io.legado.app.data.repository.AiToolRepository
import io.legado.app.data.repository.AppLocaleRepository
import io.legado.app.data.repository.AppShellSettingsRepository
import io.legado.app.data.repository.AppStartupRepository
import io.legado.app.data.repository.AppUiConfigurationRepository
import io.legado.app.data.repository.BackupRestoreRepository
import io.legado.app.data.repository.BackupSettingsRepository
import io.legado.app.data.repository.BookCacheCleanupRepository
import io.legado.app.data.repository.BookContentProcessRepository
import io.legado.app.data.repository.BookDomainRepositoryImpl
import io.legado.app.data.repository.BookExportSettingsRepository
import io.legado.app.data.repository.BookGroupMutationRepository
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookKnowledgeRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookSourceCallbackRepository
import io.legado.app.data.repository.BookSourceRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.data.repository.BookshelfSettingsRepository
import io.legado.app.data.repository.CacheBookDownloadRepository
import io.legado.app.data.repository.ChangeSourceSettingsRepository
import io.legado.app.data.repository.ChapterSpeechRepository
import io.legado.app.data.repository.CheckSourceSettingsRepository
import io.legado.app.data.repository.CloudTtsEngineRepository
import io.legado.app.data.repository.CoverAlbumRepository
import io.legado.app.data.repository.CoverSettingsRepository
import io.legado.app.data.repository.DatabaseMaintenanceRepository
import io.legado.app.data.repository.DictRuleRepository
import io.legado.app.data.repository.DictionaryRepositoryImpl
import io.legado.app.data.repository.DirectLinkSettingsRepository
import io.legado.app.data.repository.DirectLinkUploadRepository
import io.legado.app.data.repository.DownloadCacheSettingsRepository
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ExploreRepositoryImpl
import io.legado.app.data.repository.HighlightRuleRepository
import io.legado.app.data.repository.HomeDashboardRepository
import io.legado.app.data.repository.HomepageModulesRepository
import io.legado.app.data.repository.HomepageSettingsRepository
import io.legado.app.data.repository.HttpTtsEngineRepository
import io.legado.app.data.repository.ImportBookSettingsRepository
import io.legado.app.data.repository.LabSettingsRepository
import io.legado.app.data.repository.LocalBookRepository
import io.legado.app.data.repository.LocalPasswordRepository
import io.legado.app.data.repository.MangaSettingsRepository
import io.legado.app.data.repository.OtherConfigSystemRepository
import io.legado.app.data.repository.OtherSettingsRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.ReadAloudVoiceRepository
import io.legado.app.data.repository.ReadBookStyleConfigRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.ReadSettingsRepository
import io.legado.app.data.repository.ReadStyleRepository
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.data.repository.ReplaceRuleRepository
import io.legado.app.data.repository.RssFavoriteRepository
import io.legado.app.data.repository.RssRepository
import io.legado.app.data.repository.RuleSubscriptionRepository
import io.legado.app.data.repository.SearchContentRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.data.repository.SearchRepositoryImpl
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.data.repository.TagGroupRuleApplier
import io.legado.app.data.repository.ThemePackageSettingsRepository
import io.legado.app.data.repository.ThemeSettingsRepository
import io.legado.app.data.repository.TranslationCacheRepositoryImpl
import io.legado.app.data.repository.TranslationSettingsRepository
import io.legado.app.data.repository.TxtTocRuleRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.data.repository.WebDavBackupRepository
import io.legado.app.data.repository.WebDavReadingProgressRepository
import io.legado.app.data.security.CloudTtsCredentialCipher
import io.legado.app.domain.gateway.AiArtifactGateway
import io.legado.app.domain.gateway.AiChatGateway
import io.legado.app.domain.gateway.AiMemoryGateway
import io.legado.app.domain.gateway.AiProfileGateway
import io.legado.app.domain.gateway.AiPromptPresetGateway
import io.legado.app.domain.gateway.AiTextGateway
import io.legado.app.domain.gateway.AiToolGateway
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.AppShellSettingsGateway
import io.legado.app.domain.gateway.AppStartupGateway
import io.legado.app.domain.gateway.AppUiConfigurationGateway
import io.legado.app.domain.gateway.BackupRestoreGateway
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.domain.gateway.BookCacheCleanupGateway
import io.legado.app.domain.gateway.BookCacheDownloadGateway
import io.legado.app.domain.gateway.BookContentProcessGateway
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.BookSourceCallbackGateway
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.ChangeSourceSettingsGateway
import io.legado.app.domain.gateway.ChapterSpeechGateway
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.domain.gateway.CloudTtsEngineGateway
import io.legado.app.domain.gateway.CoverAlbumGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.DatabaseMaintenanceGateway
import io.legado.app.domain.gateway.DictionaryGateway
import io.legado.app.domain.gateway.DirectLinkSettingsGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.ExploreBooksGateway
import io.legado.app.domain.gateway.HomeDashboardGateway
import io.legado.app.domain.gateway.HomepageModulesGateway
import io.legado.app.domain.gateway.HomepageSettingsGateway
import io.legado.app.domain.gateway.HttpTtsEngineGateway
import io.legado.app.domain.gateway.ImportBookSettingsGateway
import io.legado.app.domain.gateway.LabSettingsGateway
import io.legado.app.domain.gateway.LocalBookGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.gateway.ReadAloudVoiceGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.gateway.ReadStyleGateway
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.gateway.ThemePackageSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.gateway.WebDavBackupGateway
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.domain.usecase.AddBookUseCase
import io.legado.app.domain.usecase.AddToBookshelfUseCase
import io.legado.app.domain.usecase.AiChatGenerationUseCase
import io.legado.app.domain.usecase.AiTaskManager
import io.legado.app.domain.usecase.AiTextFactoryUseCase
import io.legado.app.domain.usecase.AiToolAwareGenerationUseCase
import io.legado.app.domain.usecase.AnalyzeChapterSpeechUseCase
import io.legado.app.domain.usecase.AppStartupMaintenanceUseCase
import io.legado.app.domain.usecase.BackupRestoreUseCase
import io.legado.app.domain.usecase.BatchCacheDownloadUseCase
import io.legado.app.domain.usecase.BuildSpeechPlanUseCase
import io.legado.app.domain.usecase.CacheBookChaptersUseCase
import io.legado.app.domain.usecase.ChangeBookSourceUseCase
import io.legado.app.domain.usecase.ChangeSourceSearchUseCase
import io.legado.app.domain.usecase.CleanSelectedTextUseCase
import io.legado.app.domain.usecase.ClearBookCacheUseCase
import io.legado.app.domain.usecase.CoverAlbumUseCase
import io.legado.app.domain.usecase.DeleteBooksUseCase
import io.legado.app.domain.usecase.ExploreBooksUseCase
import io.legado.app.domain.usecase.ExploreKindUiUseCase
import io.legado.app.domain.usecase.ExportBookshelfUseCase
import io.legado.app.domain.usecase.GenerateChapterSummaryUseCase
import io.legado.app.domain.usecase.GetChapterContentUseCase
import io.legado.app.domain.usecase.GetReadingProgressUseCase
import io.legado.app.domain.usecase.HomeDashboardUseCase
import io.legado.app.domain.usecase.IdentifyBookCharactersUseCase
import io.legado.app.domain.usecase.ImportBookshelfUseCase
import io.legado.app.domain.usecase.PrepareChapterSpeechPlanUseCase
import io.legado.app.domain.usecase.RefineSpeechWithAiUseCase
import io.legado.app.domain.usecase.RefreshTocUseCase
import io.legado.app.domain.usecase.RemoveBookGroupAssignmentUseCase
import io.legado.app.domain.usecase.ResolveBookShelfStateUseCase
import io.legado.app.domain.usecase.ResolveLocalSpeakersUseCase
import io.legado.app.domain.usecase.SaveBookContentProcessUseCase
import io.legado.app.domain.usecase.SaveSearchBooksUseCase
import io.legado.app.domain.usecase.SearchBooksUseCase
import io.legado.app.domain.usecase.ShrinkDatabaseUseCase
import io.legado.app.domain.usecase.SyncReadAloudVoicesUseCase
import io.legado.app.domain.usecase.TranslateChapterUseCase
import io.legado.app.domain.usecase.UpdateBooksGroupUseCase
import io.legado.app.domain.usecase.UploadReadingProgressUseCase
import io.legado.app.domain.usecase.WebDavBackupUseCase
import io.legado.app.domain.usecase.readRecord.GetReadRecordOverviewUseCase
import io.legado.app.help.coil.CoverFetcher
import io.legado.app.help.coil.CoverInterceptor
import io.legado.app.help.config.ThemePackageManager
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.okHttpClientManga
import io.legado.app.model.ReadAloudSessionStore
import io.legado.app.ui.about.AboutViewModel
import io.legado.app.ui.ai.chat.AiChatViewModel
import io.legado.app.ui.book.bookmark.AllBookmarkViewModel
import io.legado.app.ui.book.cache.manage.BookCacheManageViewModel
import io.legado.app.ui.book.changecover.ChangeCoverViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceComposeViewModel
import io.legado.app.ui.book.changesource.ChangeBookSourceViewModel
import io.legado.app.ui.book.changesource.ChangeChapterSourceViewModel
import io.legado.app.ui.book.explore.ExploreShowViewModel
import io.legado.app.ui.book.group.GroupViewModel
import io.legado.app.ui.book.import.local.ImportBookViewModel
import io.legado.app.ui.book.import.remote.RemoteBookViewModel
import io.legado.app.ui.book.import.remote.ServerConfigViewModel
import io.legado.app.ui.book.import.remote.ServersViewModel
import io.legado.app.ui.book.info.BookInfoViewModel
import io.legado.app.ui.book.knowledge.BookCharacterDetailViewModel
import io.legado.app.ui.book.knowledge.BookCharacterListViewModel
import io.legado.app.ui.book.knowledge.BookCharacterNetworkViewModel
import io.legado.app.ui.book.knowledge.BookEventDetailViewModel
import io.legado.app.ui.book.knowledge.BookEventListViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeDetailViewModel
import io.legado.app.ui.book.knowledge.BookKnowledgeListViewModel
import io.legado.app.ui.book.manage.BookshelfManageScreenViewModel
import io.legado.app.ui.book.manga.ReadMangaViewModel
import io.legado.app.ui.book.read.ReadBookViewModel
import io.legado.app.ui.book.readRecord.ReadRecordOverviewViewModel
import io.legado.app.ui.book.readRecord.ReadRecordViewModel
import io.legado.app.ui.book.readaloud.cache.TtsCacheViewModel
import io.legado.app.ui.book.readaloud.casting.BookVoiceCastingViewModel
import io.legado.app.ui.book.readaloud.cloudtts.CloudTtsViewModel
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerCoordinator
import io.legado.app.ui.book.readaloud.player.ReadAloudPlayerViewModel
import io.legado.app.ui.book.search.SearchViewModel
import io.legado.app.ui.book.searchContent.SearchContentViewModel
import io.legado.app.ui.book.toc.TocViewModel
import io.legado.app.ui.book.toc.rule.TxtTocRuleViewModel
import io.legado.app.ui.book.toc.rule.preview.TxtTocRulePreviewViewModel
import io.legado.app.ui.config.ai.AiConfigViewModel
import io.legado.app.ui.config.ai.AiModelEditViewModel
import io.legado.app.ui.config.ai.AiProviderEditViewModel
import io.legado.app.ui.config.ai.prompt.AiPromptConfigViewModel
import io.legado.app.ui.config.ai.summary.AiSummaryConfigViewModel
import io.legado.app.ui.config.backupConfig.BackupConfigViewModel
import io.legado.app.ui.config.bookshelfConfig.BookshelfManageScreenConfig
import io.legado.app.ui.config.coverConfig.CoverAlbumManageViewModel
import io.legado.app.ui.config.coverConfig.CoverConfigViewModel
import io.legado.app.ui.config.customTheme.CustomThemeViewModel
import io.legado.app.ui.config.downloadCacheConfig.DownloadCacheConfigViewModel
import io.legado.app.ui.config.labConfig.LabConfigViewModel
import io.legado.app.ui.config.otherConfig.OtherConfigViewModel
import io.legado.app.ui.config.readConfig.ApplyReadSettingUseCase
import io.legado.app.ui.config.readConfig.ReadConfigViewModel
import io.legado.app.ui.config.themeConfig.ThemeConfigViewModel
import io.legado.app.ui.config.themeManage.ThemeManageViewModel
import io.legado.app.ui.config.translation.TranslationConfigViewModel
import io.legado.app.ui.dict.DictViewModel
import io.legado.app.ui.dict.rule.DictRuleViewModel
import io.legado.app.ui.highlightTagRule.HighlightTagRuleViewModel
import io.legado.app.ui.main.MainRouteSearchContent
import io.legado.app.ui.main.MainViewModel
import io.legado.app.ui.main.bookshelf.BookshelfViewModel
import io.legado.app.ui.main.explore.ExploreViewModel
import io.legado.app.ui.main.home.HomeViewModel
import io.legado.app.ui.main.homepage.HomepageViewModel
import io.legado.app.ui.main.my.MyViewModel
import io.legado.app.ui.main.rss.RssViewModel
import io.legado.app.ui.replace.ReplaceEditRoute
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.replace.edit.ReplaceEditViewModel
import io.legado.app.ui.rss.article.RssArticlesViewModel
import io.legado.app.ui.rss.article.RssSortViewModel
import io.legado.app.ui.rss.favorites.RssFavoritesViewModel
import io.legado.app.ui.rss.read.ReadRssViewModel
import io.legado.app.ui.rss.source.manage.RssSourceViewModel
import io.legado.app.ui.rss.subscription.RuleSubViewModel
import io.legado.app.ui.tagGroupRule.TagGroupRuleViewModel
import io.legado.app.utils.isNightMode
import io.legado.app.utils.sysConfiguration
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.time.Clock

val appModule = module {

    single { get<AppDatabase>().readRecordDao }
    single { get<AppDatabase>().bookDao }
    single { get<AppDatabase>().bookChapterDao }
    single { get<AppDatabase>().bookGroupDao }
    single { get<AppDatabase>().bookSourceDao }
    single { get<AppDatabase>().searchContentHistoryDao }
    single { get<AppDatabase>().rssStarDao }
    single { get<AppDatabase>().ruleSubDao }

    singleOf(::ReadRecordRepository)
    single<HomeDashboardGateway> { HomeDashboardRepository(get(), get()) }
    singleOf(::BookRepository)
    singleOf(::BookGroupRepository)
    singleOf(::TagGroupRuleApplier)
    single<BookGroupMutationGateway> { BookGroupMutationRepository(get(), get()) }
    singleOf(::BookSourceRepository)
    singleOf(::BookshelfRepository)
    singleOf(::DictRuleRepository)
    singleOf(::TxtTocRuleRepository)
    single {
        SearchContentRepository(
            titleModeProvider = { io.legado.app.help.config.ReadBookConfig.titleMode },
            historyDao = get(),
            readSettingsGateway = get(),
            otherSettingsGateway = get(),
        )
    }
    singleOf(::RemoteBookRepository)
    singleOf(::SettingsRepository)
    single<AppLocaleGateway> { AppLocaleRepository() }
    single<AppShellSettingsGateway> { AppShellSettingsRepository() }
    single<ThemeSettingsGateway> { ThemeSettingsRepository() }
    single<ThemePackageSettingsGateway> { ThemePackageSettingsRepository() }
    single<AppUiConfigurationGateway> {
        AppUiConfigurationRepository(
            appLocaleGateway = get(),
            initialSystemDarkTheme = sysConfiguration.isNightMode,
        )
    }
    single<OtherSettingsGateway> { OtherSettingsRepository() }
    single<CheckSourceSettingsGateway> { CheckSourceSettingsRepository(get()) }
    single<DirectLinkSettingsGateway> { DirectLinkSettingsRepository() }
    single<LocalPasswordGateway> { LocalPasswordRepository() }
    single<OtherConfigSystemGateway> { OtherConfigSystemRepository(get()) }
    single<DownloadCacheSettingsGateway> { DownloadCacheSettingsRepository() }
    single<CoverSettingsGateway> { CoverSettingsRepository() }
    single<BackupSettingsGateway> { BackupSettingsRepository() }
    single<LabSettingsGateway> { LabSettingsRepository() }
    single<MangaSettingsGateway> { MangaSettingsRepository() }
    single<ChangeSourceSettingsGateway> { ChangeSourceSettingsRepository() }
    single<ImportBookSettingsGateway> { ImportBookSettingsRepository() }
    single<TranslationSettingsGateway> { TranslationSettingsRepository() }
    single<BookshelfSettingsGateway> { BookshelfSettingsRepository() }
    single { ReadSettingsRepository(settingsRepository = get()) }
    single<ReadSettingsGateway> { get<ReadSettingsRepository>() }
    singleOf(::ReadAloudSettingsRepository)
    singleOf(::ReadAloudSessionStore)
    single<HttpTtsEngineGateway> { HttpTtsEngineRepository(get()) }
    single<ReadAloudSettingsGateway> { get<ReadAloudSettingsRepository>() }
    singleOf(::ApplyReadSettingUseCase)
    singleOf(::HighlightRuleRepository)
    singleOf(::ReadStyleRepository)
    singleOf(::ReadBookStyleConfigRepository)
    single<ReadStyleGateway> { get<ReadBookStyleConfigRepository>() }
    singleOf(::ExploreBooksUseCase)
    singleOf(::ExploreKindUiUseCase)
    singleOf(::SaveSearchBooksUseCase)
    singleOf(::AppStartupMaintenanceUseCase)
    singleOf(::BackupRestoreUseCase)
    singleOf(::BatchCacheDownloadUseCase)
    singleOf(::BuildSpeechPlanUseCase)
    singleOf(::AnalyzeChapterSpeechUseCase)
    singleOf(::ResolveLocalSpeakersUseCase)
    singleOf(::PrepareChapterSpeechPlanUseCase)
    singleOf(::RefineSpeechWithAiUseCase)
    singleOf(::SyncReadAloudVoicesUseCase)
    singleOf(::CacheBookChaptersUseCase)
    singleOf(::ChangeBookSourceUseCase)
    singleOf(::ClearBookCacheUseCase)
    singleOf(::CoverAlbumUseCase)
    singleOf(::DeleteBooksUseCase)
    singleOf(::GetReadingProgressUseCase)
    single { HomeDashboardUseCase(get(), Clock.systemDefaultZone()) }
    singleOf(::RemoveBookGroupAssignmentUseCase)
    singleOf(::UpdateBooksGroupUseCase)
    singleOf(::UploadReadingProgressUseCase)
    singleOf(::ResolveBookShelfStateUseCase)
    singleOf(::RefreshTocUseCase)
    singleOf(::AddBookUseCase)
    singleOf(::AddToBookshelfUseCase)
    singleOf(::ImportBookshelfUseCase)
    singleOf(::ExportBookshelfUseCase)
    factory { GetReadRecordOverviewUseCase() }
    singleOf(::ShrinkDatabaseUseCase)
    singleOf(::WebDavBackupUseCase)
    singleOf(::BookshelfManageScreenConfig)
    singleOf(::ThemePackageManager)

    single<UploadRepository> { DirectLinkUploadRepository() }
    single<TranslationCacheGateway> { TranslationCacheRepositoryImpl() }
    single<AiProfileGateway> { AiProfileRepository(get()) }
    single<AiArtifactGateway> { AiArtifactRepository(get()) }
    single<AiChatGateway> { AiChatRepository(get()) }
    single<AiMemoryGateway> { AiMemoryRepository(get()) }
    single<AiPromptPresetGateway> { AiPromptPresetRepository(get()) }
    single<AiTextGateway> { AiTextRepositoryImpl() }
    single<AiToolGateway> { AiToolRepository(get(), get(), get(), get(), get(), get(), get()) }
    single<AppStartupGateway> { AppStartupRepository(get()) }
    single<BackupRestoreGateway> { BackupRestoreRepository() }
    single<BookCacheDownloadGateway> { CacheBookDownloadRepository(get()) }
    single<BookCacheCleanupGateway> { BookCacheCleanupRepository(get()) }
    single<BookExportSettingsGateway> { BookExportSettingsRepository() }
    single<HomepageSettingsGateway> { HomepageSettingsRepository() }
    single<CoverAlbumGateway> { CoverAlbumRepository(get(), get()) }
    single<BookSourceCallbackGateway> { BookSourceCallbackRepository(get(), get()) }
    single<LocalBookGateway> { LocalBookRepository(get()) }
    single<DatabaseMaintenanceGateway> { DatabaseMaintenanceRepository(get()) }
    single<WebDavBackupGateway> { WebDavBackupRepository() }
    single<ReadingProgressGateway> { WebDavReadingProgressRepository() }
    single<HomepageModulesGateway> { HomepageModulesRepository(get(), get()) }
    single<BookDomainRepository> { BookDomainRepositoryImpl(get(), get()) }
    single<BookContentProcessGateway> { BookContentProcessRepository(get()) }
    single<BookKnowledgeGateway> { BookKnowledgeRepository(get()) }
    single<ReadAloudVoiceGateway> { ReadAloudVoiceRepository(get()) }
    singleOf(::CloudTtsCredentialCipher)
    single<CloudTtsEngineGateway> { CloudTtsEngineRepository(get(), get()) }
    single<ChapterSpeechGateway> { ChapterSpeechRepository(get()) }
    single { ExploreRepositoryImpl(get()) }
    single<ExploreRepository> { get<ExploreRepositoryImpl>() }
    single<ExploreBooksGateway> { get<ExploreRepositoryImpl>() }
    singleOf(::RssRepository)
    singleOf(::RssFavoriteRepository)
    singleOf(::RuleSubscriptionRepository)
    single {
        SearchRepositoryImpl(get())
    }
    single<SearchRepository> { get<SearchRepositoryImpl>() }
    single<BookSearchGateway> { get<SearchRepositoryImpl>() }
    singleOf(::SearchBooksUseCase)
    singleOf(::ChangeSourceSearchUseCase)
    singleOf(::GetChapterContentUseCase)
    singleOf(::AiToolAwareGenerationUseCase)
    singleOf(::AiTaskManager)
    singleOf(::IdentifyBookCharactersUseCase)
    singleOf(::GenerateChapterSummaryUseCase)
    singleOf(::AiTextFactoryUseCase)
    singleOf(::CleanSelectedTextUseCase)
    singleOf(::SaveBookContentProcessUseCase)
    singleOf(::ReplaceRuleRepository)
    single<DictionaryGateway> { DictionaryRepositoryImpl() }
    singleOf(::TranslateChapterUseCase)
    singleOf(::AiChatGenerationUseCase)

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
    viewModelOf(::HighlightTagRuleViewModel)
    viewModelOf(::TagGroupRuleViewModel)
    viewModelOf(::DictViewModel)
    viewModelOf(::RssSourceViewModel)
    viewModelOf(::RssSortViewModel)
    viewModelOf(::RssArticlesViewModel)
    viewModelOf(::ReadRssViewModel)
    viewModelOf(::RssFavoritesViewModel)
    viewModelOf(::RuleSubViewModel)
    viewModelOf(::ReadRecordViewModel)
    viewModelOf(::ReadRecordOverviewViewModel)
    viewModelOf(::ExploreShowViewModel)
    viewModelOf(::MyViewModel)
    viewModelOf(::BookshelfViewModel)
    viewModelOf(::MainViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::HomepageViewModel)
    viewModelOf(::AboutViewModel)
    viewModelOf(::GroupViewModel)
    viewModelOf(::ReplaceRuleViewModel)
    viewModelOf(::AllBookmarkViewModel)
    viewModelOf(::TxtTocRuleViewModel)
    viewModel { TxtTocRulePreviewViewModel(app = get(), repository = get()) }
    viewModel {
        OtherConfigViewModel(
            appLocaleGateway = get(),
            readAloudSettingsGateway = get(),
            otherSettingsGateway = get(),
            downloadCacheSettingsGateway = get(),
            checkSourceSettingsGateway = get(),
            directLinkSettingsGateway = get(),
            localPasswordGateway = get(),
            systemGateway = get(),
        )
    }
    viewModelOf(::CustomThemeViewModel)
    viewModelOf(::ReadConfigViewModel)
    viewModelOf(::CoverConfigViewModel)
    viewModelOf(::CoverAlbumManageViewModel)
    viewModelOf(::DownloadCacheConfigViewModel)
    viewModelOf(::ThemeConfigViewModel)
    viewModelOf(::ThemeManageViewModel)
    viewModelOf(::BackupConfigViewModel)
    viewModelOf(::LabConfigViewModel)
    viewModelOf(::TranslationConfigViewModel)
    viewModelOf(::AiConfigViewModel)
    viewModelOf(::AiSummaryConfigViewModel)
    viewModelOf(::AiPromptConfigViewModel)
    viewModelOf(::AiChatViewModel)
    viewModel { (providerId: String?) ->
        AiProviderEditViewModel(
            initialProviderId = providerId,
            aiProfileGateway = get(),
            aiTextGateway = get()
        )
    }
    viewModel { (providerId: String?, modelProfileId: String?) ->
        AiModelEditViewModel(
            initialProviderId = providerId,
            initialModelProfileId = modelProfileId,
            aiProfileGateway = get(),
            aiTextGateway = get()
        )
    }
    viewModelOf(::TocViewModel)
    viewModelOf(::ImportBookViewModel)
    viewModelOf(::RemoteBookViewModel)
    viewModelOf(::ServerConfigViewModel)
    viewModelOf(::ServersViewModel)
    viewModelOf(::BookInfoViewModel)
    viewModel { (bookUrl: String, characterId: String?) ->
        BookCharacterDetailViewModel(
            bookUrl = bookUrl,
            characterId = characterId,
            bookKnowledgeGateway = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        BookCharacterNetworkViewModel(
            bookUrl = bookUrl,
            bookKnowledgeGateway = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        BookKnowledgeListViewModel(
            bookUrl = bookUrl,
            bookKnowledgeGateway = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        BookCharacterListViewModel(
            bookUrl = bookUrl,
            bookKnowledgeGateway = get(),
            identifyBookCharacters = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        BookVoiceCastingViewModel(
            bookUrl = bookUrl,
            bookKnowledgeGateway = get(),
            voiceGateway = get(),
        )
    }
    viewModelOf(::CloudTtsViewModel)
    viewModelOf(::TtsCacheViewModel)
    singleOf(::ReadAloudPlayerCoordinator)
    viewModelOf(::ReadAloudPlayerViewModel)
    viewModel { (bookUrl: String, entryId: String?) ->
        BookKnowledgeDetailViewModel(
            bookUrl = bookUrl,
            entryId = entryId,
            bookKnowledgeGateway = get(),
        )
    }
    viewModel { (bookUrl: String) ->
        BookEventListViewModel(
            bookUrl = bookUrl,
            bookKnowledgeGateway = get(),
        )
    }
    viewModel { (bookUrl: String, eventId: String?) ->
        BookEventDetailViewModel(
            bookUrl = bookUrl,
            eventId = eventId,
            bookKnowledgeGateway = get(),
        )
    }
    viewModelOf(::ReadMangaViewModel)
    viewModel {
        ReadBookViewModel(
            application = get(),
            getReadingProgressUseCase = get(),
            uploadReadingProgressUseCase = get(),
            translateChapterUseCase = get(),
            readSettingsRepository = get(),
            readBookStyleConfigRepository = get(),
            readAloudSettingsRepository = get(),
            localPreferencesRepository = get(),
            highlightRuleRepository = get(),
            uploadRepository = get(),
            changeBookSourceUseCase = get(),
            generateChapterSummaryUseCase = get(),
            cleanSelectedTextUseCase = get(),
            aiTextFactoryUseCase = get(),
            saveBookContentProcessUseCase = get(),
            bookContentProcessGateway = get(),
            aiArtifactGateway = get(),
            aiPromptPresetGateway = get(),
            aiProfileGateway = get(),
            syncReadAloudVoicesUseCase = get(),
            readAloudSessionStore = get(),
            replaceRuleRepository = get(),
            changeSourceSettingsGateway = get(),
            appShellSettingsGateway = get(),
            appUiConfigurationGateway = get(),
            otherSettingsGateway = get(),
            downloadCacheSettingsGateway = get(),
            backupSettingsGateway = get(),
        )
    }
    viewModelOf(::ChangeCoverViewModel)
    viewModelOf(::ChangeBookSourceComposeViewModel)
    viewModelOf(::ChangeBookSourceViewModel)
    viewModelOf(::ChangeChapterSourceViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::RssViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::BookCacheManageViewModel)
    viewModel {
        BookshelfManageScreenViewModel(
            application = get(),
            bookRepository = get(),
            bookSourceRepository = get(),
            bookGroupRepository = get(),
            searchRepository = get(),
            bookshelfManageScreenConfig = get(),
            bookExportSettingsGateway = get(),
            batchCacheDownloadUseCase = get(),
            cacheBookChaptersUseCase = get(),
            changeBookSourceUseCase = get(),
            clearBookCacheUseCase = get(),
            deleteBooksUseCase = get(),
            updateBooksGroupUseCase = get()
        )
    }

    viewModel { (route: ReplaceEditRoute) ->
        ReplaceEditViewModel(
            app = get(),
            replaceRuleDao = get(),
            route = route
        )
    }

    viewModel { (route: MainRouteSearchContent) ->
        SearchContentViewModel(
            bookUrl = route.bookUrl,
            initialSearchWord = route.searchWord,
            searchResultIndex = route.searchResultIndex,
            bookRepository = get(),
            searchContentRepository = get()
        )
    }
}
