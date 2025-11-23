package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.*
import org.koin.dsl.module

/**
 * 应用程序的数据库和 DAO 模块
 */
val appDatabaseModule = module {

    // 注册 AppDatabase 实例
    // 使用 lazy 属性 appDb，该属性在 AppDatabase.kt 中定义并已初始化
    single<AppDatabase> { appDb }

    // 注册所有的 DAO 接口，通过 AppDatabase 实例获取
    factory<BookDao> { get<AppDatabase>().bookDao }
    factory<BookGroupDao> { get<AppDatabase>().bookGroupDao }
    factory<BookSourceDao> { get<AppDatabase>().bookSourceDao }
    factory<BookChapterDao> { get<AppDatabase>().bookChapterDao }
    factory<ReplaceRuleDao> { get<AppDatabase>().replaceRuleDao }
    factory<SearchBookDao> { get<AppDatabase>().searchBookDao }
    factory<SearchKeywordDao> { get<AppDatabase>().searchKeywordDao }
    factory<RssSourceDao> { get<AppDatabase>().rssSourceDao }
    factory<BookmarkDao> { get<AppDatabase>().bookmarkDao }
    factory<RssArticleDao> { get<AppDatabase>().rssArticleDao }
    factory<RssStarDao> { get<AppDatabase>().rssStarDao }
    factory<RssReadRecordDao> { get<AppDatabase>().rssReadRecordDao }
    factory<CookieDao> { get<AppDatabase>().cookieDao }
    factory<TxtTocRuleDao> { get<AppDatabase>().txtTocRuleDao }
    factory<ReadRecordDao> { get<AppDatabase>().readRecordDao }
    factory<HttpTTSDao> { get<AppDatabase>().httpTTSDao }
    factory<CacheDao> { get<AppDatabase>().cacheDao }
    factory<RuleSubDao> { get<AppDatabase>().ruleSubDao }
    factory<DictRuleDao> { get<AppDatabase>().dictRuleDao }
    factory<KeyboardAssistsDao> { get<AppDatabase>().keyboardAssistsDao }
    factory<ServerDao> { get<AppDatabase>().serverDao }
}