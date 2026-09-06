package io.legado.app.data.entities

import android.content.Context
import io.legado.app.R

/**
 * [BookGroup] 的 Android 特有扩展：把分组的「后缀徽标」解析成显示文案。
 *
 * 实体本体已下沉 :core:data，但 `R.string.*` 与 `Context` 都是 Android 专属，
 * 且这段逻辑纯粹是展示层文案映射，故作为 Android 侧扩展保留（与
 * `Server.getConfigJsonObject` 同一模式）。
 */
fun BookGroup.getManageName(context: Context): BookGroup.GroupNameInfo {
    return when (groupId) {
        BookGroup.IdAll -> BookGroup.GroupNameInfo(groupName, context.getString(R.string.all))
        BookGroup.IdAudio -> BookGroup.GroupNameInfo(groupName, context.getString(R.string.audio))
        BookGroup.IdLocal -> BookGroup.GroupNameInfo(groupName, context.getString(R.string.local))
        BookGroup.IdNetNone -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.net_no_group)
        )

        BookGroup.IdLocalNone -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.local_no_group)
        )

        BookGroup.IdManga -> BookGroup.GroupNameInfo(groupName, context.getString(R.string.manga))
        BookGroup.IdText -> BookGroup.GroupNameInfo(groupName, context.getString(R.string.noval))
        BookGroup.IdError -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.update_book_fail)
        )

        BookGroup.IdReading -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.is_reading)
        )

        BookGroup.IdUnread -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.is_unread)
        )

        BookGroup.IdReadFinished -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.is_read_finished)
        )

        BookGroup.IdReadFinishedUpdate -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.is_read_finished_update)
        )

        BookGroup.IdReadFinishedComplete -> BookGroup.GroupNameInfo(
            groupName,
            context.getString(R.string.is_read_finished_complete)
        )

        else -> BookGroup.GroupNameInfo(groupName)
    }
}
