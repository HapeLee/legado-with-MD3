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
    const val prevParagraph = "prevParagraph"
    const val nextParagraph = "nextParagraph"
    const val upTtsSpeechRate = "upTtsSpeechRate"
    const val syncReadAloudLayout = "syncReadAloudLayout"
    const val upTtsProgress = "upTtsProgress"
    const val adjustProgress = "adjustProgress"
    const val adjustSpeed = "adjustSpeed"
    const val prev = "prev"
    const val next = "next"
    const val moveTo = "moveTo"
    const val init = "init"
    const val remove = "remove"
    const val stopPlay = "stopPlay"
}
