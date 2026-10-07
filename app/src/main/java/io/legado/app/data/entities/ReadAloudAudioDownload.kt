package io.legado.app.data.entities

import androidx.room.Entity

/**
 * 一章的听书音频下载记录：音频文件本身落在 `filesDir/readAloudAudio/<书的 md5>/` 下，
 * 文件名的算法与朗读缓存完全一致（引擎 + 语速 + 句子文本的 md5），所以朗读时只要在这两个
 * 目录里找到同名文件就直接播，不再联网合成。
 *
 * [files] 是这一章用到的文件名清单（逗号分隔）。文件名是内容哈希，反推不出「哪一章产出了
 * 哪些文件」，不记下来就没法把一章干净地删掉；顺带它就是「这一章下载了几句」。
 */
@Entity(
    tableName = "read_aloud_audio_downloads",
    primaryKeys = ["bookUrl", "chapterIndex"],
)
data class ReadAloudAudioDownload(
    var bookUrl: String = "",
    var chapterIndex: Int = 0,
    /** 章节标题，列表里显示用。 */
    var title: String = "",
    /** 这一章下载到的音频文件名，逗号分隔。 */
    var files: String = "",
    var createTime: Long = 0,
) {
    val sentenceCount: Int get() = if (files.isBlank()) 0 else files.split(',').size

    /** 这一章下载到的音频文件名（不含扩展名）。 */
    val fileNames: List<String> get() = files.split(',').filter { it.isNotBlank() }
}
