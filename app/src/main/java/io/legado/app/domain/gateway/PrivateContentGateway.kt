package io.legado.app.domain.gateway

import io.legado.app.domain.model.PrivateBookFacts
import kotlinx.coroutines.flow.Flow

/**
 * 私密内容标记。
 *
 * 私密判定取并集：书籍自身被标记私密，或书籍属于任一私密分组。
 * 判定所需的数据源（books.isPrivate 列、私密分组掩码）都在数据层组装，UI 只消费结果。
 */
interface PrivateContentGateway {

    /** 被单独标记为私密的书籍 url，供书架在内存侧与分组掩码求并集 */
    fun flowPrivateBookUrls(): Flow<Set<String>>

    /**
     * 私密判定所需的全部事实。
     *
     * 中心 gate 只有 bookUrl，判定"能不能看"还需要分组掩码才能复用
     * [io.legado.app.domain.model.PrivateAccessState.isTargetGranted] 的分组授权，
     * 所以两项一次取回，而不是分两次查同一行。
     */
    suspend fun factsOf(bookUrl: String): PrivateBookFacts

    /**
     * 只有书名 + 作者时的判定入口。
     *
     * 阅读记录这类表里根本没存 bookUrl（书签也是），只能按名+作者反查一次；
     * 反查不到返回 null，调用方按"与私密无关"处理——书都没了，也就没有可泄露的内容。
     */
    suspend fun factsOfByName(name: String, author: String): PrivateBookFacts?

    /** 单本判定；实现方只需实现 [factsOf] */
    suspend fun isBookPrivate(bookUrl: String): Boolean = factsOf(bookUrl).isPrivate

    /** 批量设置/取消书籍的私密标记 */
    suspend fun setBooksPrivate(bookUrls: Set<String>, isPrivate: Boolean)
}
