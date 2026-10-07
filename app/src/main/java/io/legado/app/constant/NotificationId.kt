package io.legado.app.constant

/**
 * 通知ID不能重复,统一规划通知ID
 */
@Suppress("ConstPropertyName")
object NotificationId {

    const val ReadAloudService = 101
    const val AudioPlayService = 102
    const val CacheBookService = 103
    const val ExportBookService = 104
    const val WebService = 105
    const val DownloadService = 106
    const val BookSourceCheckService = 107
    /** 听书音频下载：章节 + 句子两条进度都发在这条通知上 */
    const val ReadAloudAudioDownload = 108
    const val Download = 10000
    const val ExportBook = 201

}
