package io.legado.app.constant

@Suppress("ConstPropertyName")
object IntentAction {
    const val start = "start"
    const val play = "play"
    const val playNew = "playNew"
    const val stop = "stop"
    const val resume = "resume"
    /** 仅清除全局暂停并继续调度，不解冻各书已单章暂停的章节 */
    const val continueDownload = "continueDownload"
    const val pause = "pause"
    const val addTimer = "addTimer"
    const val setTimer = "setTimer"

    /** 章节定时剩余章数（不是分钟）；翻页/换章都不应清除它。 */
    const val setTimerChapters = "setTimerChapters"
    const val prevParagraph = "prevParagraph"
    const val nextParagraph = "nextParagraph"
    const val upTtsSpeechRate = "upTtsSpeechRate"
    const val syncReadAloudLayout = "syncReadAloudLayout"
    /** 多角色分配变了：按当前朗读位置重新准备本章队列，正在听的那句不重写、之后的句子换新音色。 */
    const val refreshReadAloudCast = "refreshReadAloudCast"
    const val upTtsProgress = "upTtsProgress"
    const val adjustProgress = "adjustProgress"
    const val adjustSpeed = "adjustSpeed"
    const val adjustGain = "adjustGain"
    const val prev = "prev"
    const val next = "next"
    const val moveTo = "moveTo"
    const val init = "init"
    const val remove = "remove"
    const val stopPlay = "stopPlay"

    /** 听书音频下载：把指定章节区间的朗读音频合成到下载区，之后朗读直接播本地文件。 */
    const val downloadReadAloudAudio = "downloadReadAloudAudio"

    /** 取消正在跑的听书音频下载。 */
    const val cancelDownloadReadAloudAudio = "cancelDownloadReadAloudAudio"
}
