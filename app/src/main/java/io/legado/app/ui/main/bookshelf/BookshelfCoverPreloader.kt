package io.legado.app.ui.main.bookshelf

import android.app.Application
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.memory.MemoryCache
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.BookshelfRepository
import io.legado.app.domain.gateway.BookshelfSettingsGateway
import io.legado.app.domain.gateway.CoverSettingsGateway
import io.legado.app.domain.gateway.PrivateContentGateway
import io.legado.app.domain.model.isPrivateBook
import io.legado.app.domain.model.settings.BookshelfSettings
import io.legado.app.ui.widget.components.image.cover.bookshelfCoverMemoryCacheKey
import io.legado.app.ui.widget.components.image.cover.buildCoverImageRequest
import io.legado.app.ui.widget.components.image.cover.isDefaultCoverPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 预热上限：再多的书也不在首屏，留着让书架自己按需加载。 */
private const val MAX_PRELOAD_BOOKS = 24

/** 列表模式一屏只能看到几本，没必要按网格的列数铺开。 */
private const val LIST_PRELOAD_BOOKS = 10

/** 按列数 × 预估可见行数收敛预热数量（按高屏手机 3 列 × 5 行估）。 */
private const val ESTIMATED_VISIBLE_ROWS = 5

/** 预热并发上限：它是启动期后台任务，不能把解码线程和启动关键路径全部占满。 */
private const val PRELOAD_CONCURRENCY = 4

/** 卡片封面四周的内边距（[io.legado.app.ui.main.bookshelf.BookItem] 里的 `padding(4.dp)`）。 */
private const val COVER_ITEM_PADDING_DP = 4

/**
 * 待预热的书：只保留书卡那种“确实有真实封面地址”的书。
 */
internal data class CoverPreloadCandidate(
    val bookUrl: String,
    val coverPath: String,
    val sourceOrigin: String?,
)

/**
 * 一次预热请求的全部参数。
 *
 * [memoryCacheKey] 必须与书架卡片请求里的 memoryCacheKey 完全一致，否则预热写进内存缓存
 * 的是另一个条目，卡片照样要重新读盘解码。
 */
internal data class CoverPreloadTarget(
    val memoryCacheKey: String,
    val bookUrl: String,
    val coverPath: String,
    val sourceOrigin: String?,
    val widthPx: Int,
    val heightPx: Int,
)

/** 卡片只加载真实封面地址；空地址与“默认封面”都不产生请求。 */
internal fun BookShelfItem.toCoverPreloadCandidate(): CoverPreloadCandidate? {
    val path = getDisplayCover()
    if (isDefaultCoverPath(path)) return null
    return CoverPreloadCandidate(
        bookUrl = bookUrl,
        coverPath = path!!,
        sourceOrigin = origin,
    )
}

/**
 * 计算首屏预热目标（纯函数，便于单测）。
 *
 * 尺寸按卡片布局反推：外层列宽 [coverWidthDp]（受书架设置控制）减去左右各 4dp 内边距，
 * 高宽比固定 5:7。尺寸准不准只影响“卡片是否要再解一次码”——内存缓存命中时 Coil 会先把
 * 缓存图交给 target，所以尺寸偏差不会让封面晚出现，只会多一次解码。
 */
internal fun buildCoverPreloadTargets(
    groupId: Long,
    candidates: List<CoverPreloadCandidate>,
    coverWidthDp: Int,
    density: Float,
    limit: Int,
): List<CoverPreloadTarget> {
    val outerPx = (coverWidthDp * density).roundToInt()
    val paddingPx = (COVER_ITEM_PADDING_DP * density).roundToInt()
    val widthPx = (outerPx - 2 * paddingPx).coerceAtLeast(1)
    val heightPx = (widthPx * 7f / 5f).roundToInt()
    return candidates.take(limit).map { candidate ->
        CoverPreloadTarget(
            memoryCacheKey = bookshelfCoverMemoryCacheKey(
                groupId = groupId,
                bookUrl = candidate.bookUrl,
                coverPath = candidate.coverPath,
            ),
            bookUrl = candidate.bookUrl,
            coverPath = candidate.coverPath,
            sourceOrigin = candidate.sourceOrigin,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }
}

/**
 * 书架首屏封面预热。
 *
 * 存在的理由：进程刚起来时 Coil 内存缓存是空的，书架卡片必须先把封面文件读出来解码完才能
 * 画第一张图，于是进入书架时先看到一片灰底、封面随后才出现。卡片请求本来就带了
 * `placeholderMemoryCacheKey`，只要内存缓存里已经有同一个键，Coil 会在发起真实加载之前
 * 就把缓存图交给 target——所以把“读盘 + 解码”提前到书架首帧之前，卡片第一帧就是封面。
 *
 * 触发点在书架组合之前（[io.legado.app.ui.main.MainActivity.onCreate]），因此这里自己按
 * 书架设置查出当前分组首屏的书；顺序复用 [BookshelfRepository.sortBooks]，与书架列表同一份
 * 排序实现，避免两套排序漂移导致预热到别的书上。
 */
class BookshelfCoverPreloader(
    private val application: Application,
    private val imageLoader: ImageLoader,
    private val bookRepository: BookRepository,
    private val bookGroupRepository: BookGroupRepository,
    private val bookshelfRepository: BookshelfRepository,
    private val bookshelfSettingsGateway: BookshelfSettingsGateway,
    private val coverSettingsGateway: CoverSettingsGateway,
    private val privateContentGateway: PrivateContentGateway,
) {

    /**
     * 本进程已经预热过的“分组 + 排序 + 封面尺寸”签名。
     *
     * Activity 因主题/语言重建会再走一遍 onCreate，没有它就会重复预热同一批封面。
     */
    @Volatile
    private var warmedSignature: String? = null

    /**
     * 预热当前书架分组首屏封面。
     *
     * @return 实际发起的预热请求数；不需要预热时返回 0
     */
    suspend fun preloadCurrentGroupFirstScreen(): Int {
        val settings = bookshelfSettingsGateway.currentSettings
        val coverSettings = coverSettingsGateway.currentSettings
        // 全局启用默认封面时卡片根本不加载真实封面，预热只会白耗一次解码与流量
        if (coverSettings.useDefaultCover) return 0
        // 文件夹模式首屏是分组卡片（每格 4 张预览封面，内存键与分组页不同），不在本次范围
        if (settings.bookGroupStyle == 2) return 0

        val groupId = settings.saveTabPosition
        val layout = resolveFirstScreenLayout(settings)
        val signature = listOf(
            groupId,
            settings.bookshelfSort,
            settings.bookshelfSortOrder,
            layout.grid,
            layout.coverWidthDp,
        ).joinToString("|")
        if (signature == warmedSignature) return 0

        return withContext(Dispatchers.IO) {
            val targets = buildTargets(groupId, settings, layout)
            if (targets.isEmpty()) return@withContext 0
            val pending = targets.filterNot { isAlreadyCached(it.memoryCacheKey) }
            if (pending.isEmpty()) {
                // 内存里已经有了，等价于预热完成
                warmedSignature = signature
                return@withContext 0
            }
            val semaphore = Semaphore(PRELOAD_CONCURRENCY)
            coroutineScope {
                pending.forEach { target ->
                    launch {
                        semaphore.withPermit {
                            try {
                                enqueueTarget(target, coverSettings.loadOnlyOnWifi)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // 预热失败与卡片无关：卡片自己的请求会照常重试并决定占位/错误态
                            }
                        }
                    }
                }
            }
            // 全部结束后才记账：中途被取消（例如 Activity 重建）不算预热过，
            // 新 Activity 的 onCreate 应该能重新发起，而不是被这个标记拦住。
            warmedSignature = signature
            pending.size
        }
    }

    private suspend fun buildTargets(
        groupId: Long,
        settings: BookshelfSettings,
        layout: FirstScreenLayout,
    ): List<CoverPreloadTarget> {
        // 排序输入与书架完全一致：分组取 flowShow（书架的 groupsFlow），
        // 私密掩码取 flowAll（书架的 allGroupsFlow），两边都用同一个纯函数判定
        val showGroups = bookGroupRepository.flowShow().first()
        val allGroups = bookGroupRepository.flowAll().first()
        val group: BookGroup? = showGroups.find { it.groupId == groupId }
        val privateBookUrls = privateContentGateway.flowPrivateBookUrls().first()
        val privateGroupMask = allGroups.fold(0L) { acc, it ->
            if (it.groupId > 0 && it.isPrivate) acc or it.groupId else acc
        }

        val candidates = bookshelfRepository.sortBooks(
            list = bookRepository.flowBookShelfByGroup(groupId).first(),
            group = group,
            sort = settings.bookshelfSort,
            sortOrder = settings.bookshelfSortOrder,
        ).asSequence()
            // 锁定态的卡片用的是另一套内存键，预热清晰封面既无用也只是白解码
            .filterNot { isPrivateBook(it.bookUrl, it.group, privateBookUrls, privateGroupMask) }
            .mapNotNull { it.toCoverPreloadCandidate() }
            .toList()

        return buildCoverPreloadTargets(
            groupId = groupId,
            candidates = candidates,
            coverWidthDp = layout.coverWidthDp,
            density = application.resources.displayMetrics.density,
            limit = layout.limit,
        )
    }

    private fun isAlreadyCached(memoryCacheKey: String): Boolean =
        imageLoader.memoryCache?.get(MemoryCache.Key(memoryCacheKey))?.image != null

    private suspend fun enqueueTarget(target: CoverPreloadTarget, loadOnlyOnWifi: Boolean) {
        val request = buildCoverImageRequest(
            context = application,
            data = target.coverPath,
            sourceOrigin = target.sourceOrigin,
            loadOnlyWifi = loadOnlyOnWifi,
            // 预热结果只进内存缓存，不经过任何 target，过渡动画没有意义
            crossfade = false,
            memoryCacheKey = target.memoryCacheKey,
            bookUrl = target.bookUrl,
            preferCache = true,
        ) {
            size(target.widthPx, target.heightPx)
        }
        imageLoader.execute(request)
    }

    private data class FirstScreenLayout(
        val grid: Boolean,
        val coverWidthDp: Int,
        val limit: Int,
    )

    private fun resolveFirstScreenLayout(settings: BookshelfSettings): FirstScreenLayout {
        val landscape = application.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
        val layoutMode = if (landscape) {
            settings.bookshelfLayoutModeLandscape
        } else {
            settings.bookshelfLayoutModePortrait
        }
        val grid = layoutMode != 0
        val columns = if (!grid) {
            1
        } else if (landscape) {
            settings.bookshelfLayoutGridLandscape
        } else {
            settings.bookshelfLayoutGridPortrait
        }
        val limit = if (grid) {
            (columns.coerceAtLeast(1) * ESTIMATED_VISIBLE_ROWS).coerceIn(1, MAX_PRELOAD_BOOKS)
        } else {
            LIST_PRELOAD_BOOKS
        }
        return FirstScreenLayout(
            grid = grid,
            coverWidthDp = if (grid) settings.bookshelfGridCoverWidth else settings.bookshelfListCoverWidth,
            limit = limit,
        )
    }
}
