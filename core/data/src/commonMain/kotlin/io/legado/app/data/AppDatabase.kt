package io.legado.app.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.dao.AiPromptPresetDao
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookKnowledgeDao
import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.ChapterSpeechDao
import io.legado.app.data.dao.CloudTtsEngineDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.DictRuleDao
import io.legado.app.data.dao.ExactChapterPageCountDao
import io.legado.app.data.dao.HighlightRuleDao
import io.legado.app.data.dao.HighlightTagRuleDao
import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.dao.KeyboardAssistsDao
import io.legado.app.data.dao.ReadAloudVoiceDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.RssArticleDao
import io.legado.app.data.dao.RssReadRecordDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.dao.RssStarDao
import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.dao.SearchContentHistoryDao
import io.legado.app.data.dao.SearchKeywordDao
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.dao.TagGroupRuleDao
import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.entities.AiArtifact
import io.legado.app.data.entities.AiChatConversation
import io.legado.app.data.entities.AiChatMessage
import io.legado.app.data.entities.AiMemory
import io.legado.app.data.entities.AiModelProfile
import io.legado.app.data.entities.AiPromptPreset
import io.legado.app.data.entities.AiProviderProfile
import io.legado.app.data.entities.AiTaskPreset
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.BookOutlineNode
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourceConverters
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.ChapterSpeechSegmentEntity
import io.legado.app.data.entities.CloudTtsEngineEntity
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.ExactChapterPageCountEntity
import io.legado.app.data.entities.HighlightRule
import io.legado.app.data.entities.HighlightTagRule
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.data.entities.HomepageModule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReadAloudVoiceEntity
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchContentHistory
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.TagGroupRule
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession

/**
 * AppDatabase 主体下沉 commonMain（P3）。
 *
 * - `@Database` 注解 + abstract DAO 属性 + companion 常量 平台无关，放 commonMain
 * - `appDb` 单例 + `dbCallback`（依赖 appCtx + AndroidSQLiteConnection + Locale.CHINESE +
 *   DefaultData.keyboardAssists）留 app 端
 * - `DatabaseMigrations` 已下沉 commonMain（`androidx.sqlite.SQLiteConnection` 是 KMP 库，
 *   唯一平台依赖 AppConst.androidId 已用 SourceRuntime 契约替代）
 * - `@ConstructedBy` + `expect object AppDatabaseConstructor`：非 Android 平台（desktop）
 *   Room 要求显式构造工厂；KSP 为 Android/Desktop 双 target 生成 actual 与 `_Impl`。
 */
@TypeConverters(BookSourceConverters::class)
@Database(
    version = 104,
    exportSchema = true,
    entities = [Book::class, BookGroup::class, BookSource::class, BookChapter::class,
        ReplaceRule::class, SearchBook::class, SearchKeyword::class, Cookie::class,
        RssSource::class, Bookmark::class, RssArticle::class,
        RssReadRecord::class, ReadRecordDetail::class, ReadRecordSession::class,
        RssStar::class, TxtTocRule::class, ReadRecord::class, HttpTTS::class, Cache::class,
        RuleSub::class, DictRule::class, KeyboardAssist::class, Server::class,
        SearchContentHistory::class, HomepageModule::class, HomepageCustomSet::class,
        HighlightRule::class, AiProviderProfile::class, AiModelProfile::class,
        AiTaskPreset::class, AiArtifact::class, AiChatConversation::class,
        AiChatMessage::class, AiMemory::class, HighlightTagRule::class, TagGroupRule::class,
        BookContentProcess::class, AiPromptPreset::class, BookCharacterProfile::class,
        BookCharacterEvent::class, BookCharacterRelation::class, BookKnowledgeEntry::class,
        BookOutlineNode::class, ReadAloudVoiceEntity::class, BookVoiceBindingEntity::class,
        ChapterSpeechAnalysisEntity::class, ChapterSpeechSegmentEntity::class,
        CloudTtsEngineEntity::class, ExactChapterPageCountEntity::class,
        BookMarking::class],
    views = [BookSourcePart::class],
    autoMigrations = [
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50),
        AutoMigration(from = 50, to = 51),
        AutoMigration(from = 51, to = 52),
        AutoMigration(from = 52, to = 53),
        AutoMigration(from = 53, to = 54),
        AutoMigration(from = 54, to = 55, spec = DatabaseMigrations.Migration_54_55::class),
        AutoMigration(from = 55, to = 56),
        AutoMigration(from = 56, to = 57),
        AutoMigration(from = 57, to = 58),
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60),
        AutoMigration(from = 60, to = 61),
        AutoMigration(from = 61, to = 62),
        AutoMigration(from = 62, to = 63),
        AutoMigration(from = 63, to = 64),
        AutoMigration(from = 64, to = 65, spec = DatabaseMigrations.Migration_64_65::class),
        AutoMigration(from = 65, to = 66),
        AutoMigration(from = 66, to = 67),
        AutoMigration(from = 67, to = 68),
        AutoMigration(from = 68, to = 69),
        AutoMigration(from = 69, to = 70),
        AutoMigration(from = 70, to = 71),
        AutoMigration(from = 71, to = 72),
        AutoMigration(from = 72, to = 73),
        AutoMigration(from = 73, to = 74),
        AutoMigration(from = 74, to = 75),
        AutoMigration(from = 75, to = 76),
        AutoMigration(from = 76, to = 77),
        AutoMigration(from = 77, to = 78),
        AutoMigration(from = 78, to = 79),
        AutoMigration(from = 79, to = 80),
        AutoMigration(from = 80, to = 81),
        AutoMigration(from = 81, to = 82),
        AutoMigration(from = 82, to = 83),
        AutoMigration(from = 83, to = 84),
        AutoMigration(from = 84, to = 85),
        AutoMigration(from = 85, to = 86),
        AutoMigration(from = 86, to = 87),
        AutoMigration(from = 87, to = 88),
        AutoMigration(from = 88, to = 89),
        AutoMigration(from = 89, to = 90),
        AutoMigration(from = 90, to = 91),
        AutoMigration(from = 91, to = 92),
        AutoMigration(from = 92, to = 93),
        AutoMigration(from = 93, to = 94),
        AutoMigration(from = 94, to = 95),
        AutoMigration(from = 95, to = 96),
        AutoMigration(from = 96, to = 97),
        AutoMigration(from = 97, to = 98),
        AutoMigration(from = 100, to = 101, spec = DatabaseMigrations.Migration_100_101::class),
        AutoMigration(from = 101, to = 102),
        AutoMigration(from = 103, to = 104)
    ]
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookChapterDao: BookChapterDao
    abstract val bookContentProcessDao: BookContentProcessDao
    abstract val bookKnowledgeDao: BookKnowledgeDao
    abstract val readAloudVoiceDao: ReadAloudVoiceDao
    abstract val chapterSpeechDao: ChapterSpeechDao
    abstract val cloudTtsEngineDao: CloudTtsEngineDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val searchBookDao: SearchBookDao
    abstract val searchKeywordDao: SearchKeywordDao
    abstract val rssSourceDao: RssSourceDao
    abstract val bookmarkDao: BookmarkDao
    abstract val bookMarkingDao: BookMarkingDao
    abstract val rssArticleDao: RssArticleDao
    abstract val rssStarDao: RssStarDao
    abstract val rssReadRecordDao: RssReadRecordDao
    abstract val cookieDao: CookieDao
    abstract val txtTocRuleDao: TxtTocRuleDao
    abstract val readRecordDao: ReadRecordDao
    abstract val httpTTSDao: HttpTTSDao
    abstract val cacheDao: CacheDao
    abstract val ruleSubDao: RuleSubDao
    abstract val dictRuleDao: DictRuleDao
    abstract val exactChapterPageCountDao: ExactChapterPageCountDao
    abstract val keyboardAssistsDao: KeyboardAssistsDao
    abstract val serverDao: ServerDao
    abstract val searchContentHistoryDao: SearchContentHistoryDao
    abstract val homepageModuleDao: HomepageModuleDao
    abstract val homepageCustomSetDao: HomepageCustomSetDao
    abstract val highlightRuleDao: HighlightRuleDao
    abstract val highlightTagRuleDao: HighlightTagRuleDao
    abstract val tagGroupRuleDao: TagGroupRuleDao
    abstract val aiProfileDao: AiProfileDao
    abstract val aiArtifactDao: AiArtifactDao
    abstract val aiChatDao: AiChatDao
    abstract val aiMemoryDao: AiMemoryDao
    abstract val aiPromptPresetDao: AiPromptPresetDao

    companion object {

        const val DATABASE_NAME = "legado.db"

        const val BOOK_TABLE_NAME = "books"
        const val BOOK_SOURCE_TABLE_NAME = "book_sources"
        const val RSS_SOURCE_TABLE_NAME = "rssSources"
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
