package io.legado.app.data.repository

import io.legado.app.data.entities.BookProgress
import io.legado.app.domain.gateway.ReadingProgressGateway
import io.legado.app.domain.model.ReadingProgress
import io.legado.app.help.AppWebDav
import io.legado.app.help.WebDavConfigState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WebDavReadingProgressRepository : ReadingProgressGateway {

    /**
     * 菜单可见性只看「本地是否配了 WebDAV 账号」。
     *
     * 此前返回 `AppWebDav.isOk`（本次进程内网络初始化成功），会把「服务暂时连不上」
     * 和「没配置」混为一谈，导致进度同步入口在初始化失败时永久消失。
     */
    override val isConfigured: Boolean
        get() = AppWebDav.isConfigured

    override val isConfiguredFlow: Flow<Boolean> = AppWebDav.configState
        .map { it == WebDavConfigState.Ready }

    override suspend fun ensureConfigured() {
        AppWebDav.ensureConfigured()
    }

    override suspend fun getProgress(name: String, author: String): ReadingProgress? {
        AppWebDav.ensureConfigured()
        return AppWebDav.getBookProgress(name, author)?.let {
            ReadingProgress(
                name = it.name,
                author = it.author,
                durChapterIndex = it.durChapterIndex,
                durChapterPos = it.durChapterPos,
                durChapterTime = it.durChapterTime,
                durChapterTitle = it.durChapterTitle
            )
        }
    }

    override suspend fun uploadProgress(progress: ReadingProgress): Long? {
        // 与 getProgress 同理：菜单可见性只证明「配了账号」，真正读写前补一次初始化。
        AppWebDav.ensureConfigured()
        val uploadTime = System.currentTimeMillis()
        val uploaded = AppWebDav.uploadBookProgress(
            BookProgress(
                name = progress.name,
                author = progress.author,
                durChapterIndex = progress.durChapterIndex,
                durChapterPos = progress.durChapterPos,
                durChapterTime = progress.durChapterTime,
                durChapterTitle = progress.durChapterTitle
            )
        )
        return if (uploaded) uploadTime else null
    }
}
