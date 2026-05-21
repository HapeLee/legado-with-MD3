package io.legado.app.di

import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.DictRuleDao
import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.dao.KeyboardAssistsDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.RssArticleDao
import io.legado.app.data.dao.RssReadRecordDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.dao.RssStarDao
import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.dao.SearchKeywordDao
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.dao.TxtTocRuleDao
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
    factory<HomepageModuleDao> { get<AppDatabase>().homepageModuleDao }
    factory<HomepageCustomSetDao> { get<AppDatabase>().homepageCustomSetDao }
}