package io.legado.app.di

import android.app.Application
import io.legado.app.help.config.AppConfigStore
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.error.NoDefinitionFoundException
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import coil3.ImageLoader
import io.legado.app.core.platform.Clipboard
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.Toaster
import io.legado.app.core.rules.BuiltInRulesImporter
import io.legado.app.core.rules.RuleTransferPlatform
import io.legado.app.data.repository.ExploreRepository
import io.legado.app.data.repository.ReadAloudSettingsRepository
import io.legado.app.data.repository.SearchRepository
import io.legado.app.data.repository.UploadRepository
import io.legado.app.domain.ai.AiArtifactGateway
import io.legado.app.domain.ai.AiChatGateway
import io.legado.app.domain.ai.AiMemoryGateway
import io.legado.app.domain.ai.AiProfileGateway
import io.legado.app.domain.ai.AiPromptPresetGateway
import io.legado.app.domain.contentprocess.BookContentProcessGateway
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
import io.legado.app.domain.gateway.BookExportSettingsGateway
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.gateway.BookKnowledgeGateway
import io.legado.app.domain.gateway.BookSearchGateway
import io.legado.app.domain.gateway.BookSourceCallbackGateway
import io.legado.app.domain.gateway.BookSourceCheckGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupGateway
import io.legado.app.domain.gateway.BookshelfAutoGroupPromptGateway
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
import io.legado.app.domain.gateway.ReplaceRuleChangeNotifier
import io.legado.app.domain.gateway.ReplaceRuleSettingsGateway
import io.legado.app.domain.gateway.ThemePackageSettingsGateway
import io.legado.app.domain.gateway.ThemeSettingsGateway
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.gateway.WebDavBackupGateway
import io.legado.app.domain.homepage.HomepageModulesGateway
import io.legado.app.domain.marking.BookMarkingGateway
import io.legado.app.domain.repository.BookDomainRepository
import io.legado.app.domain.rules.DictRuleRepository
import io.legado.app.domain.rules.HighlightTagRuleRepository
import io.legado.app.domain.rules.ReadBookReplaceSessionGateway
import io.legado.app.domain.rules.ReplaceRuleRepository
import io.legado.app.domain.rules.RuleSubRepository
import io.legado.app.domain.rules.TagGroupRuleRepository
import io.legado.app.domain.rules.TxtTocRuleRepository
import io.legado.app.feature.about.AboutDiagnostics
import io.legado.app.feature.about.AppUpdateChecker
import io.legado.app.feature.about.BundledTextReader
import io.legado.app.feature.replacerules.ReplaceRuleImportCompat
import io.legado.app.feature.txttocrules.TxtTocRuleImportCompat

/**
 * `:app` 的 **Koin graph creation test**（M2-8）。
 *
 * ## 它存在的理由：一个被实测出来的缺口
 *
 * M5-1c-3 的变异验证里，我**删掉了 `single<BundledTextReader> { … }` 这一行**，
 * 然后 `:app:compileAppDebugKotlin` 照样 **BUILD SUCCESSFUL** —— Koin 的
 * `viewModelOf(::AboutViewModel)` 是在**运行期**按类型解析构造参数的，漏绑没有任何
 * 编译期信号，只会在用户第一次进到那个页面时抛 `NoDefinitionFoundException`。
 * 本仓当时没有任何宿主 graph 测试（`grep checkModules` 只命中 `App.kt` 的 `startKoin`
 * 生产代码），所以这个洞一直开着。本测试把它堵上。
 *
 * ## 为什么是手写清单，而不是 `checkModules()`
 *
 * `checkModules()`（koin-test）会**自动**遍历所有定义，新增绑定天然被覆盖 —— 那更好；
 * 但本环境解析不到 `io.insert-koin:koin-test:4.2.2`（Koin 4 的 KMP 模块，离线/坐标问题），
 * 所以退到零新依赖的方案：**每条 `single<接口> { … }` 显式解析一次**。
 *
 * ⚠️ 代价与纪律：新增 `single<接口> { … }` 绑定时**必须**往下面的清单补一行，否则这条
 * 绑定不被覆盖。清单由 `appModule` 里的 `single<` 绑定派生（83 条），不含
 * `singleOf(::X)`（那些是具体类型，缺失会在引用它的定义处连带暴露）。
 *
 * ## 环境
 *
 * `androidContext(...)` 需要 Android `Context` ⇒ Robolectric（`:app` 已开
 * `testOptions.unitTests.isIncludeAndroidResources`，否则取 `R.string` 的定义会
 * `Resources$NotFoundException`）。加载的 module 与 `App.onCreate` 一致：
 * `appDatabaseModule` + `appModule`。
 *
 * ⚠️ 只保证**图能建起来**，不保证实例可用：Room 是内存库、网络客户端不会真的发请求。
 */
@RunWith(RobolectricTestRunner::class)
// 与 `:app` 其余 22 个 Robolectric 测试一致：`sdk = [35]`（Robolectric 尚未支持项目的
// `targetSdk = 37`，不指定会报 `targetSdkVersion=37 > maxSdkVersion=36`）。
//
// ⚠️ 宿主用**干净的 `Application`** + 测试自己 `startKoin`，而**不是**项目的 `App`。
// 两条路都试过，记录一下为什么选前者：
//  - 用 `App::class` 作宿主（让 `App.onCreate` 自己装配）更"真实"，但它跑到
//    `App.kt:121` 的 `LocalConfig.appLocaleMigrated` 就 `ExceptionInInitializerError`
//    —— `App.onCreate` 的副作用链在 Robolectric 下并不完整，测试会红在一处与
//    **绑定正确性无关**的地方。
//  - 用 `Application::class` 时 `App.onCreate` 不跑，于是缺两步初始化；实测 83 条绑定
//    里 61 条红，且根因**只有两条**（`AppConfigStore 未初始化` / `appCtx has not been
//    initialized`），两条都可在测试里显式补上（见用例开头），补完即绿。
// 所以本测试刻意**不**依赖 `App.onCreate` 的其余副作用 —— 它只关心"图里的绑定齐不齐"。
@Config(application = Application::class, sdk = [35])
class AppModuleGraphTest {

    /** 断言每一条「接口 → 实现」绑定都能解析出来；一次收集全部失败，不 fail-fast。 */
    @Test
    fun everyInterfaceBindingResolves() {
        val app = RuntimeEnvironment.getApplication() as Application
        // 下面两行是在补 `App.onCreate` 的前两步初始化。**它们不是可选的**：
        // 缺第一行 ⇒ `AppConfigStore 未初始化，应在 App.onCreate 首行调用 init()`；
        // 缺第二行 ⇒ splitties 的 `appCtx has not been initialized!`（它在生产里由
        // AndroidX Startup 的 `InitializationProvider` 注入，Robolectric 下没跑到）。
        // 实测：不补这两行时 83 条绑定里 61 条红，且根因只有这两条。
        AppConfigStore.init(app)
        app.injectAsAppCtx()

        val koin = startKoin {
            androidContext(app)
            modules(appDatabaseModule, appModule)
        }.koin

        try {
            val bindings = listOf<Pair<String, () -> Any?>>(
                "AboutDiagnostics" to { koin.get<AboutDiagnostics>() },
                "AiArtifactGateway" to { koin.get<AiArtifactGateway>() },
                "AiChatGateway" to { koin.get<AiChatGateway>() },
                "AiMemoryGateway" to { koin.get<AiMemoryGateway>() },
                "AiProfileGateway" to { koin.get<AiProfileGateway>() },
                "AiPromptPresetGateway" to { koin.get<AiPromptPresetGateway>() },
                "AiTextGateway" to { koin.get<AiTextGateway>() },
                "AiToolGateway" to { koin.get<AiToolGateway>() },
                "AppLocaleGateway" to { koin.get<AppLocaleGateway>() },
                "AppShellSettingsGateway" to { koin.get<AppShellSettingsGateway>() },
                "AppStartupGateway" to { koin.get<AppStartupGateway>() },
                "AppUiConfigurationGateway" to { koin.get<AppUiConfigurationGateway>() },
                "AppUpdateChecker" to { koin.get<AppUpdateChecker>() },
                "BackupRestoreGateway" to { koin.get<BackupRestoreGateway>() },
                "BackupSettingsGateway" to { koin.get<BackupSettingsGateway>() },
                "BookCacheCleanupGateway" to { koin.get<BookCacheCleanupGateway>() },
                "BookCacheDownloadGateway" to { koin.get<BookCacheDownloadGateway>() },
                "BookContentProcessGateway" to { koin.get<BookContentProcessGateway>() },
                "BookDomainRepository" to { koin.get<BookDomainRepository>() },
                "BookExportSettingsGateway" to { koin.get<BookExportSettingsGateway>() },
                "BookGroupMutationGateway" to { koin.get<BookGroupMutationGateway>() },
                "BookKnowledgeGateway" to { koin.get<BookKnowledgeGateway>() },
                "BookMarkingGateway" to { koin.get<BookMarkingGateway>() },
                "BookSearchGateway" to { koin.get<BookSearchGateway>() },
                "BookSourceCallbackGateway" to { koin.get<BookSourceCallbackGateway>() },
                "BookSourceCheckGateway" to { koin.get<BookSourceCheckGateway>() },
                "BookshelfAutoGroupGateway" to { koin.get<BookshelfAutoGroupGateway>() },
                "BookshelfAutoGroupPromptGateway" to { koin.get<BookshelfAutoGroupPromptGateway>() },
                "BookshelfSettingsGateway" to { koin.get<BookshelfSettingsGateway>() },
                "BuiltInRulesImporter" to { koin.get<BuiltInRulesImporter>() },
                "BundledTextReader" to { koin.get<BundledTextReader>() },
                "ChangeSourceSettingsGateway" to { koin.get<ChangeSourceSettingsGateway>() },
                "ChapterSpeechGateway" to { koin.get<ChapterSpeechGateway>() },
                "CheckSourceSettingsGateway" to { koin.get<CheckSourceSettingsGateway>() },
                "Clipboard" to { koin.get<Clipboard>() },
                "CloudTtsEngineGateway" to { koin.get<CloudTtsEngineGateway>() },
                "CoverAlbumGateway" to { koin.get<CoverAlbumGateway>() },
                "CoverSettingsGateway" to { koin.get<CoverSettingsGateway>() },
                "DatabaseMaintenanceGateway" to { koin.get<DatabaseMaintenanceGateway>() },
                "DictRuleRepository" to { koin.get<DictRuleRepository>() },
                "DictionaryGateway" to { koin.get<DictionaryGateway>() },
                "DirectLinkSettingsGateway" to { koin.get<DirectLinkSettingsGateway>() },
                "DownloadCacheSettingsGateway" to { koin.get<DownloadCacheSettingsGateway>() },
                "ExploreBooksGateway" to { koin.get<ExploreBooksGateway>() },
                "ExploreRepository" to { koin.get<ExploreRepository>() },
                "HighlightTagRuleRepository" to { koin.get<HighlightTagRuleRepository>() },
                "HomeDashboardGateway" to { koin.get<HomeDashboardGateway>() },
                "HomepageModulesGateway" to { koin.get<HomepageModulesGateway>() },
                "HomepageSettingsGateway" to { koin.get<HomepageSettingsGateway>() },
                "HttpTtsEngineGateway" to { koin.get<HttpTtsEngineGateway>() },
                "ImageLoader" to { koin.get<ImageLoader>() },
                "ImportBookSettingsGateway" to { koin.get<ImportBookSettingsGateway>() },
                "ImportJsonEditor" to { koin.get<ImportJsonEditor>() },
                "LabSettingsGateway" to { koin.get<LabSettingsGateway>() },
                "LocalBookGateway" to { koin.get<LocalBookGateway>() },
                "LocalPasswordGateway" to { koin.get<LocalPasswordGateway>() },
                "MangaSettingsGateway" to { koin.get<MangaSettingsGateway>() },
                "OtherConfigSystemGateway" to { koin.get<OtherConfigSystemGateway>() },
                "OtherSettingsGateway" to { koin.get<OtherSettingsGateway>() },
                "ReadAloudSettingsGateway" to { koin.get<ReadAloudSettingsGateway>() },
                "ReadAloudSettingsRepository" to { koin.get<ReadAloudSettingsRepository>() },
                "ReadAloudVoiceGateway" to { koin.get<ReadAloudVoiceGateway>() },
                "ReadBookReplaceSessionGateway" to { koin.get<ReadBookReplaceSessionGateway>() },
                "ReadSettingsGateway" to { koin.get<ReadSettingsGateway>() },
                "ReadStyleGateway" to { koin.get<ReadStyleGateway>() },
                "ReadingProgressGateway" to { koin.get<ReadingProgressGateway>() },
                "ReplaceRuleChangeNotifier" to { koin.get<ReplaceRuleChangeNotifier>() },
                "ReplaceRuleImportCompat" to { koin.get<ReplaceRuleImportCompat>() },
                "ReplaceRuleRepository" to { koin.get<ReplaceRuleRepository>() },
                "ReplaceRuleSettingsGateway" to { koin.get<ReplaceRuleSettingsGateway>() },
                "RuleSubRepository" to { koin.get<RuleSubRepository>() },
                "RuleTransferPlatform" to { koin.get<RuleTransferPlatform>() },
                "SearchRepository" to { koin.get<SearchRepository>() },
                "TagGroupRuleRepository" to { koin.get<TagGroupRuleRepository>() },
                "ThemePackageSettingsGateway" to { koin.get<ThemePackageSettingsGateway>() },
                "ThemeSettingsGateway" to { koin.get<ThemeSettingsGateway>() },
                "Toaster" to { koin.get<Toaster>() },
                "TranslationCacheGateway" to { koin.get<TranslationCacheGateway>() },
                "TranslationSettingsGateway" to { koin.get<TranslationSettingsGateway>() },
                "TxtTocRuleImportCompat" to { koin.get<TxtTocRuleImportCompat>() },
                "TxtTocRuleRepository" to { koin.get<TxtTocRuleRepository>() },
                "UploadRepository" to { koin.get<UploadRepository>() },
                "WebDavBackupGateway" to { koin.get<WebDavBackupGateway>() },
            )
            // ⚠️ 判据是「**没有定义**」，不是「实例化没抛异常」。二者必须分开，理由是实测的：
            // 补完 `AppConfigStore.init` / `injectAsAppCtx` 后还剩 1 条红 —— `ReadStyleGateway`
            // 炸在 `ReadBookConfig` 的 `lateinit property readSettingsGateway has not been
            // initialized`，那个字段由 `AppConfig.initialize(...)` 赋值（生产里在 `startKoin`
            // **之后**调）。也就是说**定义是存在的**，只是这台宿主没跑完生产初始化链的第三步。
            // 把这类环境性失败当失败，测试就会长期红在一处与"绑定正确性"无关的地方。
            //
            // 要防的失效模式（M5-1c-3 删 `single<BundledTextReader>` 后编译仍绿）抛的是
            // `NoDefinitionFoundException`；**依赖链上任一环节缺定义**也都会以它出现
            // （外层包成 `InstanceCreationException`，cause 链里能找到它）⇒ 判据取
            // "异常链上任一是 `NoDefinitionFoundException`"，既精准又不受宿主健全度影响。
            val missing = mutableListOf<String>()
            val environment = mutableListOf<String>()
            bindings.forEach { (name, resolve) ->
                val error = runCatching { resolve() }.exceptionOrNull() ?: return@forEach
                val chain = generateSequence(error) { it.cause }.toList()
                val root = chain.last()
                val text = "$name: ${root::class.java.simpleName}: ${root.message}"
                if (chain.any { it is NoDefinitionFoundException }) {
                    missing += text
                } else {
                    environment += text
                }
            }
            if (environment.isNotEmpty()) {
                // 环境性失败不用断言拦住，但**必须打印**：新增噪音时能立刻看见，
                // 免得它悄悄掩盖后面真正的漏绑。
                println("[graph] 环境性失败 ${environment.size}/${bindings.size} 条（非漏绑）：")
                environment.distinctBy { it.substringAfter(": ") }
                    .forEach { println("   [env] $it") }
            }
            assertTrue(
                "以下 Koin 绑定**缺失定义**（共 ${missing.size} 条）—— 这类漏绑不会让编译失败，" +
                    "只会在用户首次进入该对应页面时抛异常：\n" + missing.joinToString("\n"),
                missing.isEmpty(),
            )
        } finally {
            stopKoin()
        }
    }
}
