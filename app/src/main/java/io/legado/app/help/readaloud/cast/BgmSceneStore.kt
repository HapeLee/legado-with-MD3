package io.legado.app.help.readaloud.cast

import io.legado.app.data.appDb
import io.legado.app.data.entities.BgmSceneMark
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** 总览行里的正文提示长度：够认出一段，又不会把卡片撑满。 */
private const val PREVIEW_CHARS = 48

/**
 * 正文背景音乐场景标记的读写出口（界面/朗读服务都只经这里，不直连 DAO）。
 *
 * 除 CRUD 外还负责「这段该放哪首」：指定了单首就用它，否则在池内启用的配乐里随机取
 * 一首（副本文件已被删的跳过），这样朗读时只需问一个问题就能开播。
 */
object BgmSceneStore {

    /**
     * 正文段落序号 = 非空段落的编号，从 0 开始（-1 = 空白行，不算一段）。
     *
     * 胶囊渲染、AI 分配场景、朗读取乐三处必须用同一个编号，否则点开的段和实际播的不是
     * 同一段。标题段由 `getContent(includeTitle = false)` 事先排除。
     */
    fun ordinals(paragraphs: List<String>): List<Int> {
        var count = 0
        return paragraphs.map { if (it.isBlank()) -1 else count++ }
    }

    /**
     * 语义文本 → 每个正文段的 (起始位置, 段序号)。
     *
     * 朗读服务手里只有章节字符坐标，胶囊与 AI 手里只有段落序号，两边靠这张表对上；
     * 空白段不编号，规则与 [ordinals] 一致。
     */
    fun positionsToOrdinals(semanticContent: String): List<Pair<Int, Int>> {
        var position = 0
        var count = 0
        val result = ArrayList<Pair<Int, Int>>()
        for (line in semanticContent.split('\n')) {
            if (line.isNotBlank()) {
                result += position to count
                count++
            }
            position += line.length + 1
        }
        return result
    }

    /** 本章全部标记（按段落序号升序）。 */
    suspend fun marks(bookUrl: String, chapterIndex: Int): List<BgmSceneMark> =
        appDb.bgmSceneDao.getChapter(bookUrl, chapterIndex)

    /**
     * 渲染侧整章读取：段落序号 → (池名, 指定曲目)。
     *
     * 池名为空的行不产出胶囊（没有池就没有可播的东西，留着只会让人点了又改）。
     */
    suspend fun labelsForChapter(bookUrl: String, chapterIndex: Int): Map<Int, Pair<String, String>> =
        marks(bookUrl, chapterIndex)
            .filter { it.enabled && it.poolName.isNotBlank() }
            .associate { it.paragraphOrdinal to (it.poolName to it.trackName) }

    /** 段前胶囊要显示的当前值（没有标记给 null）。 */
    suspend fun markAt(bookUrl: String, chapterIndex: Int, ordinal: Int): BgmSceneMark? =
        appDb.bgmSceneDao.getChapter(bookUrl, chapterIndex).firstOrNull { it.paragraphOrdinal == ordinal }

    /**
     * 本章配乐总览：一行 = 一个配乐区间（从这条标记起，到下一条标记前）。
     *
     * 朗读时区间内一直播同一首，所以只报起点没意义，必须连区间一起给，否则看不出
     * 「这段音乐铺了多远」。正文只用来认段落（取前几十字），章节内容读不到时 preview
     * 为空，池/曲目/音量照样能看能改。
     */
    suspend fun overview(book: Book, chapterIndex: Int): List<OverviewRow> = withContext(Dispatchers.IO) {
        val marked = marks(book.bookUrl, chapterIndex)
            .filter { it.enabled && (it.poolName.isNotBlank() || it.trackName.isNotBlank()) }
        if (marked.isEmpty()) return@withContext emptyList()
        val previews = paragraphPreviews(book, chapterIndex)
        // 区间终点：下一条标记的起点；最后一条铺到本章段尾（正文读不到就至少铺一段）
        val chapterTail = (previews.keys.maxOrNull() ?: marked.last().paragraphOrdinal) + 1
        marked.mapIndexed { index, mark ->
            val next = marked.getOrNull(index + 1)?.paragraphOrdinal
            OverviewRow(
                ordinal = mark.paragraphOrdinal,
                endOrdinal = maxOf(mark.paragraphOrdinal + 1, next ?: chapterTail),
                poolName = mark.poolName,
                trackName = mark.trackName,
                volume = mark.volume.coerceIn(0f, 1f),
                preview = previews[mark.paragraphOrdinal].orEmpty().trim().take(PREVIEW_CHARS),
            )
        }
    }

    /** 总览的一行。 */
    data class OverviewRow(
        val ordinal: Int,
        /** 本区间结束于哪一段（不含），即下一条标记的起点。 */
        val endOrdinal: Int,
        val poolName: String,
        val trackName: String,
        val volume: Float,
        /** 起点段落正文前几十字，用来认这是哪一段。 */
        val preview: String,
    ) {
        /** 列表右侧显示：指定了单首就报曲名，留空报池（朗读时从池里随机取）。 */
        val musicLabel: String
            get() = trackName.ifBlank { poolName }
    }

    /** 段落序号 → 正文（空白段不编号，与 [ordinals] 同一套计数）。 */
    private fun paragraphPreviews(book: Book, chapterIndex: Int): Map<Int, String> {
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: return emptyMap()
        val content = BookHelp.getContent(book, chapter) ?: return emptyMap()
        val paragraphs = ContentProcessor.get(book)
            .getContent(book, chapter, content, includeTitle = false)
            .textList
        val ordinals = ordinals(paragraphs)
        return paragraphs.mapIndexed { index, text -> ordinals[index] to text }
            .filter { it.first >= 0 }
            .associate { it.first to it.second }
    }

    /** 分配表版本号：每次写入自增。朗读中的配乐轨靠它发现「用户刚改过这一段」。 */
    @Volatile
    var version = 0
        private set

    /**
     * 保存/更新一段的分配；池名与曲名都为空等于「这段起不要背景音乐」。
     *
     * [volume] 传 null = 保留这一段原有的音量（AI 重新分配场景时不该把用户手调过的音量抹掉）。
     */
    suspend fun put(
        bookUrl: String,
        chapterIndex: Int,
        ordinal: Int,
        poolName: String,
        trackName: String,
        volume: Float? = null,
    ) {
        appDb.bgmSceneDao.put(
            BgmSceneMark(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                paragraphOrdinal = ordinal,
                poolName = poolName.trim(),
                trackName = trackName.trim(),
                volume = (volume ?: markAt(bookUrl, chapterIndex, ordinal)?.volume ?: 1f).coerceIn(0f, 1f),
            ),
        )
        version++
    }

    suspend fun clear(bookUrl: String, chapterIndex: Int, ordinal: Int) {
        appDb.bgmSceneDao.delete(bookUrl, chapterIndex, ordinal)
        version++
    }

    /**
     * 清空整章的场景标记。
     *
     * 必须走这里而不是直接 `bgmSceneDao.deleteChapter`：正在朗读的配乐轨和总览弹层都靠
     * [version] 判断「手上的还是不是新的」，直连 DAO 删完版本号不变，看起来就像
     * 「删除分配 / 重新分配对场景分配没作用」。
     */
    suspend fun clearChapter(bookUrl: String, chapterIndex: Int) {
        appDb.bgmSceneDao.deleteChapter(bookUrl, chapterIndex)
        version++
    }

    /** 悬浮窗一次性读取：本段当前值 + 两个候选列表（曲名按已选池筛，未选池给整库）。 */
    suspend fun sheetData(bookUrl: String, chapterIndex: Int, ordinal: Int): SheetData {
        val mark = markAt(bookUrl, chapterIndex, ordinal)
        val pools = BgmPoolStore.enabledPoolNames()
        val pool = mark?.poolName.orEmpty()
        val pinned = mark?.trackName.orEmpty()
        val tracks = (
            BgmPoolStore.playableTracksOfPool(pool).map { it.name } +
                (if (pinned.isBlank() || pinned in pools) emptyList() else listOf(pinned))
            ).distinct()
        return SheetData(pools, tracks, pool, pinned, mark?.volume ?: 1f, mark != null)
    }

    /** 弹层初始值。 */
    data class SheetData(
        val pools: List<String>,
        val tracks: List<String>,
        val pool: String,
        val track: String,
        /** 本段音量（0f–1f），没有标记时 1f。 */
        val volume: Float,
        val assigned: Boolean,
    )

    suspend fun deleteForBook(bookUrl: String) = appDb.bgmSceneDao.deleteForBook(bookUrl)

    suspend fun countForBook(bookUrl: String): Int = appDb.bgmSceneDao.countForBook(bookUrl)

    /** 悬浮窗的两个候选列表：池名 + 配乐库里的曲名。 */
    suspend fun pickerData(): Pair<List<String>, List<String>> =
        BgmPoolStore.enabledPoolNames() to BgmPoolStore.list().map { it.name }

    /**
     * 这段实际要播的文件与**曲目自身音量**。
     *
     * 顺序：指定单首 → 池内随机。两处都要「副本文件还在」，否则返回 null（不播，
     * 而不是播一条静音/失败的路径）。随机每次问都重掷，同一章反复听不会总是同一首。
     *
     * 这里只给曲目那一半音量，段内音量由调用方（[BgmSceneMark.volume]）再乘一次——
     * 播放中用户拖本段滑杆时，播放器要就地改音量而不重新起播，所以两个因子得分开拿。
     */
    suspend fun resolve(mark: BgmSceneMark): Resolved? {
        if (!mark.enabled) return null
        mark.trackName.trim().takeIf { it.isNotEmpty() }?.let { name ->
            val track = BgmPoolStore.list().firstOrNull { it.name == name && it.enabled }
                ?.takeIf { File(it.path).exists() }
            if (track != null) return Resolved(track.path, track.volume.coerceIn(0f, 1f))
        }
        mark.poolName.trim().takeIf { it.isNotEmpty() }?.let { pool ->
            val playable = BgmPoolStore.playableTracksOfPool(pool)
            if (playable.isNotEmpty()) {
                val track = playable[Random.nextInt(playable.size)]
                return Resolved(track.path, track.volume.coerceIn(0f, 1f))
            }
        }
        return null
    }

    /** 播哪个文件 + 这条配乐自己的音量（0f–1f），实际播放音量 = 它 × 本段音量。 */
    data class Resolved(val path: String, val trackVolume: Float)

    /** 朗读时用当前段落序号找「最近一个不晚于它的标记」，跨章延续由调用方负责。 */
    suspend fun activeMark(bookUrl: String, chapterIndex: Int, ordinal: Int): BgmSceneMark? =
        marks(bookUrl, chapterIndex).lastOrNull { it.paragraphOrdinal <= ordinal }

    /** 试听：不落库，直接问「这一组池/曲/音量会播什么」。 */
    suspend fun resolve(
        bookUrl: String,
        chapterIndex: Int,
        ordinal: Int,
        poolName: String,
        trackName: String,
        volume: Float = 1f,
    ): Resolved? = resolve(
        BgmSceneMark(
            bookUrl = bookUrl,
            chapterIndex = chapterIndex,
            paragraphOrdinal = ordinal,
            poolName = poolName,
            trackName = trackName,
            volume = volume,
        ),
    )
}
