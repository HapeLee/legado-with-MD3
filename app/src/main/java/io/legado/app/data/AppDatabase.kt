package io.legado.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.data.dao.AiArtifactDao
import io.legado.app.data.dao.AiChatDao
import io.legado.app.data.dao.AiMemoryDao
import io.legado.app.data.dao.AiProfileDao
import io.legado.app.data.dao.AiPromptPresetDao
import io.legado.app.data.dao.VoiceEffectDao
import io.legado.app.data.dao.ReadAloudAudioDownloadDao
import io.legado.app.data.dao.BgmPoolDao
import io.legado.app.data.dao.BgmSceneDao
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookContentProcessDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookKnowledgeDao
import io.legado.app.data.dao.BookMarkingDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.BookCastMemoryDao
import io.legado.app.data.dao.CastCharacterDao
import io.legado.app.data.dao.VoicePoolDao
import io.legado.app.data.dao.ChapterRoleAssignmentDao
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
import io.legado.app.data.dao.RegexCastRuleDao
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
import io.legado.app.data.entities.VoiceEffectPreset
import io.legado.app.data.entities.BgmPoolEntity
import io.legado.app.data.entities.BgmPoolGroupEntity
import io.legado.app.data.entities.BgmPoolMember
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.data.entities.BgmTrackEntity
import io.legado.app.data.entities.RegexCastRule
import io.legado.app.data.entities.RegexCastGroup
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookCharacterEvent
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookCharacterRelation
import io.legado.app.data.entities.BookContentProcess
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookKnowledgeEntry
import io.legado.app.data.entities.BookMarking
import io.legado.app.data.entities.ReadAloudAudioDownload
import io.legado.app.data.entities.BookOutlineNode
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.BookVoiceBindingEntity
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.ChapterSpeechAnalysisEntity
import io.legado.app.data.entities.BookCastMemory
import io.legado.app.data.entities.CastCharacter
import io.legado.app.data.entities.VoicePoolEntity
import io.legado.app.data.entities.VoicePoolGroupEntity
import io.legado.app.data.entities.VoicePoolMember
import io.legado.app.data.entities.ChapterRoleAssignment
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
import io.legado.app.help.DefaultData
import org.intellij.lang.annotations.Language
import splitties.init.appCtx
import java.util.Locale

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}

@Database(
    version = 130,
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
        ChapterRoleAssignment::class, CastCharacter::class, BookCastMemory::class,
        VoicePoolEntity::class, VoicePoolMember::class, VoicePoolGroupEntity::class,
        CloudTtsEngineEntity::class, ExactChapterPageCountEntity::class,
        BgmTrackEntity::class,
        BgmPoolEntity::class, BgmPoolMember::class, BgmPoolGroupEntity::class,
        BgmSceneMark::class, VoiceEffectPreset::class,
        BookMarking::class, ReadAloudAudioDownload::class, RegexCastRule::class, RegexCastGroup::class],
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
        // book_marks 新表：Room AutoMigration 支持新增表，自动 CREATE TABLE
        AutoMigration(from = 101, to = 102),
        // httpTTS 新增可空列 speed（源级语速）
        AutoMigration(from = 103, to = 104),
        AutoMigration(from = 104, to = 105),
        // readRecordSession 新增 bookUrl 归属列：同名作者作品共存时按书籍副本分别计时
        AutoMigration(from = 105, to = 106),
        // books 新增 isPrivate 列：单本私密标记，与所属私密分组共同决定书籍是否私密
        AutoMigration(from = 106, to = 107),
        // chapter_role_assignments 新表：多角色分配（对话→角色）
        AutoMigration(from = 107, to = 108),
        // cast_characters 新表：配音角色档案（身份=书+名字+声音池，同名可多版本并存）
        AutoMigration(from = 108, to = 109),
        // voice_pools + voice_pool_members 新表：声音池可管理（建/删/分组/成员）
        AutoMigration(from = 109, to = 110),
        // voice_pool_members 新增 enabled 列：池内音色启用开关
        AutoMigration(from = 110, to = 111),
        // book_cast_memory 新表：AI 分配角色的书级人物档案
        AutoMigration(from = 111, to = 112),
        // voice_pool_groups 新表 + voice_pools 新增 groupId/order：分组升级为可嵌套实体
        AutoMigration(from = 112, to = 113),
        // voice_pool_groups 新增 enabled：分组可整体停用，停用的组不作为新建分配的候选
        AutoMigration(from = 113, to = 114),
        // bgm_tracks 新表：背景音乐池，导入的配乐文件副本 + 顺序/开关
        AutoMigration(from = 114, to = 115),
        // bgm_pools + bgm_pool_members + bgm_pool_groups 新表：背景音乐池也分组、可拖动排序，与角色声音池同构
        AutoMigration(from = 115, to = 116),
        // bgm_scene_marks 新表：正文段起的背景音乐场景（池/单首），只作视觉胶囊与朗读取乐
        AutoMigration(from = 116, to = 117),
        // voice_effect_presets 新表 + cast_characters 新增 voiceEffect：变声器预设与角色绑定
        AutoMigration(from = 117, to = 118),
        // bgm_tracks.volume + bgm_scene_marks.volume：配乐自身音量与段内音量，实际播放取乘积
        AutoMigration(from = 118, to = 119),
        // chapter_role_assignments.voiceEffect：正文胶囊那一栏的段级变声器
        AutoMigration(from = 119, to = 120),
        // cast_characters.sortOrder：配音页人物拖动排序，没拖过的仍按男女主/男女配置顶
        AutoMigration(from = 120, to = 121),
        // read_aloud_audio_downloads：听书音频按章下载的记录（文件名清单 + 句数）
        AutoMigration(from = 121, to = 122),
        // highlight_rules 新增命中排版四列 + 九宫格长度偏移：全部默认 0，老规则各列取值不变、读回来逐字节等价
        AutoMigration(from = 122, to = 123),
        // 版本号 123 对外发布过两套列定义，identityHash 对不上会让覆盖安装被 Room 拒绝开库；
        // 124 用手工迁移按列名补齐，见 DatabaseMigrations。
        // 125：九宫格长度偏移拆成左/右两列（DROP COLUMN 要 SQLite 3.35，minSdk 26 没有），
        // 同样手工整表重建，老值按左右各一半落进新列。
        // cast_characters.bubbleRuleJson：这个角色自己的气泡（只填气泡那几栏的高亮规则 JSON），
        // 默认空串 = 不设气泡，老角色该字段保持默认、读回来逐字节等价。
        AutoMigration(from = 125, to = 126),
        // 127：新表 regex_cast_rules（正则角色：命中文字换音色 / 换音效）。
        AutoMigration(from = 126, to = 127),
        // 128 是手工迁移：正则角色的分组从「一个文本列」升级成真正的可嵌套分组树
        // （新表 regex_cast_groups + rules.groupId），要搬数据，AutoMigration 做不了。
        // regex_cast_rules 新增 useRegex 列：一条规则自己决定按正则还是按字面量匹配
        AutoMigration(from = 128, to = 129),
        // regex_cast_rules 新增 voiceEffect 列：命中的那段文字用哪个变声器预设念，默认空 = 不变声
        AutoMigration(from = 129, to = 130),
    ]
)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookChapterDao: BookChapterDao
    abstract val bookContentProcessDao: BookContentProcessDao
    abstract val bookKnowledgeDao: BookKnowledgeDao
    abstract val readAloudVoiceDao: ReadAloudVoiceDao
    abstract val chapterSpeechDao: ChapterSpeechDao
    abstract val chapterRoleAssignmentDao: ChapterRoleAssignmentDao
    abstract val castCharacterDao: CastCharacterDao
    abstract val bookCastMemoryDao: BookCastMemoryDao
    abstract val voicePoolDao: VoicePoolDao
    abstract val bgmPoolDao: BgmPoolDao
    abstract val bgmSceneDao: BgmSceneDao
    abstract val regexCastRuleDao: RegexCastRuleDao
    abstract val voiceEffectDao: VoiceEffectDao
    abstract val readAloudAudioDownloadDao: ReadAloudAudioDownloadDao
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

        val dbCallback = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                db.setLocale(Locale.CHINESE)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                // 伴生分组默认隐藏，仅「全部」默认显示；已有数据库不受影响，用户仍可在分组管理中手动开启
                @Language("sql")
                val insertBookGroupAllSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdAll}, '全部', -10, 1
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdAll})
                """.trimIndent()
                db.execSQL(insertBookGroupAllSql)
                @Language("sql")
                val insertBookGroupLocalSql = """
                    insert into book_groups(groupId, groupName, 'order', enableRefresh, show) 
                    select ${BookGroup.IdLocal}, '本地', -9, 0, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdLocal})
                """.trimIndent()
                db.execSQL(insertBookGroupLocalSql)
                @Language("sql")
                val insertBookGroupTextSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdText}, '小说', -26, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdText})
                """.trimIndent()
                db.execSQL(insertBookGroupTextSql)
                @Language("sql")
                val insertBookGroupMangaSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdManga}, '漫画', -25, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdManga})
                """.trimIndent()
                db.execSQL(insertBookGroupMangaSql)
                @Language("sql")
                val insertBookGroupMusicSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdAudio}, '音频', -8, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdAudio})
                """.trimIndent()
                db.execSQL(insertBookGroupMusicSql)
                Language("sql")
                val insertGroupReading = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdReading}, '在读', -30, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdReading})
                """.trimIndent()
                db.execSQL(insertGroupReading)
                @Language("sql")
                val insertGroupUnread = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdUnread}, '未读', -29, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdUnread})
                """.trimIndent()
                db.execSQL(insertGroupUnread)
                @Language("sql")
                val insertGroupReadFinished = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdReadFinished}, '已读', -28, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinished})
                """.trimIndent()
                db.execSQL(insertGroupReadFinished)
                @Language("sql")
                val insertGroupReadFinishedUpdate = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdReadFinishedUpdate}, '连载已读', -27, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinishedUpdate})
                """.trimIndent()
                db.execSQL(insertGroupReadFinishedUpdate)
                @Language("sql")
                val insertGroupReadFinishedComplete = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdReadFinishedComplete}, '完本已读', -26, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdReadFinishedComplete})
                """.trimIndent()
                db.execSQL(insertGroupReadFinishedComplete)
                @Language("sql")
                val insertBookGroupNetNoneGroupSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdNetNone}, '网络未分组', -7, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdNetNone})
                """.trimIndent()
                db.execSQL(insertBookGroupNetNoneGroupSql)
                @Language("sql")
                val insertBookGroupLocalNoneGroupSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdLocalNone}, '本地未分组', -6, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdLocalNone})
                """.trimIndent()
                db.execSQL(insertBookGroupLocalNoneGroupSql)
                @Language("sql")
                val insertBookGroupErrorSql = """
                    insert into book_groups(groupId, groupName, 'order', show) 
                    select ${BookGroup.IdError}, '更新失败', -1, 0
                    where not exists (select * from book_groups where groupId = ${BookGroup.IdError})
                """.trimIndent()
                db.execSQL(insertBookGroupErrorSql)
                @Language("sql")
                val upBookSourceLoginUiSql =
                    "update book_sources set loginUi = null where loginUi = 'null'"
                db.execSQL(upBookSourceLoginUiSql)
                @Language("sql")
                val upRssSourceLoginUiSql =
                    "update rssSources set loginUi = null where loginUi = 'null'"
                db.execSQL(upRssSourceLoginUiSql)
                @Language("sql")
                val upHttpTtsLoginUiSql =
                    "update httpTTS set loginUi = null where loginUi = 'null'"
                db.execSQL(upHttpTtsLoginUiSql)
                @Language("sql")
                val upHttpTtsConcurrentRateSql =
                    "update httpTTS set concurrentRate = '0' where concurrentRate is null"
                db.execSQL(upHttpTtsConcurrentRateSql)
                db.query("select * from keyboardAssists order by serialNo").use {
                    if (it.count == 0) {
                        DefaultData.keyboardAssists.forEach { keyboardAssist ->
                            val contentValues = ContentValues().apply {
                                put("type", keyboardAssist.type)
                                put("key", keyboardAssist.key)
                                put("value", keyboardAssist.value)
                                put("serialNo", keyboardAssist.serialNo)
                            }
                            db.insert(
                                "keyboardAssists",
                                SQLiteDatabase.CONFLICT_REPLACE,
                                contentValues
                            )
                        }
                    }
                }
            }
        }

    }

}
