package io.legado.app.ui.widget.components.image.cover

import io.legado.app.ui.main.bookCoverSharedElementKey

/**
 * 封面在 Coil 内存缓存中的键的唯一拼装入口。
 *
 * 键必须由卡片与预热共用同一个函数：书架卡片的请求一旦带上 [sharedCoverKey]，缓存键就
 * 变成「共享元素键:cover:封面地址」。预热若按别的公式拼键，写进内存缓存的就是另一个
 * 条目，卡片依旧要重新读盘解码——表现就是进入书架时先闪一下灰底。
 */
internal fun coverMemoryCacheKey(
    sharedCoverKey: String?,
    explicitKey: String?,
    path: String?,
): String? = sharedCoverKey?.let { "$it:cover:${explicitKey ?: path}" } ?: explicitKey ?: path

/**
 * 书架分组页共享元素用的来源标识。
 *
 * 卡片（[io.legado.app.ui.main.bookshelf.BookshelfScreen]）与封面预热共用，避免同一段
 * 字面量在两处各写一遍后漂移——漂移的结果是预热全部落空且很难发现。
 */
internal fun bookshelfSharedCoverSourceId(groupId: Long): String = "bookshelf:$groupId"

/** 书架分组页卡片的封面内存缓存键（与卡片请求里的 memoryCacheKey 必须完全一致）。 */
internal fun bookshelfCoverMemoryCacheKey(
    groupId: Long,
    bookUrl: String,
    coverPath: String,
): String = requireNotNull(
    coverMemoryCacheKey(
        sharedCoverKey = bookCoverSharedElementKey(bookUrl, bookshelfSharedCoverSourceId(groupId)),
        explicitKey = null,
        path = coverPath,
    )
)
