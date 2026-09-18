package io.legado.app.domain.model.settings

import io.legado.app.domain.model.AiReasoningLevel

/**
 * 听书内容划分方式：决定一段正文被切成多少个朗读单元。
 *
 * [Default] 跟随多角色朗读开关：开启时按句末标点切分（多角色需要句级片段才能分角色），
 * 未开启时按整段切分。其余取值是显式的固定粒度，不受多角色开关影响。
 */
enum class ReadAloudContentSplitMode(val storageValue: String) {
    Default("default"),
    Paragraph("paragraph"),
    Page("page"),
    Symbols("symbols");

    /** 按符号划分时是否允许单元内再按引号/冒号切成多个角色片段。 */
    val allowsRoleSplits: Boolean
        get() = when (this) {
            Default -> true
            Symbols -> true
            Paragraph, Page -> false
        }

    companion object {
        fun fromStorage(value: String): ReadAloudContentSplitMode =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

data class ReadAloudSettings(
    val ttsEngine: String? = null,
    val ttsParagraphInterval: Int = 0,
    val audioCacheCleanTime: Int = 10,
    val ignoreAudioFocus: Boolean = false,
    val mediaButtonOnExit: Boolean = true,
    val readAloudByMediaButton: Boolean = false,
    val pauseReadAloudWhilePhoneCalls: Boolean = false,
    val readAloudWakeLock: Boolean = false,
    val showReadAloudCapsule: Boolean = true,
    val capsuleAutoCollapse: Boolean = true,
    val capsuleOffsetX: Float = 0f,
    val capsuleOffsetY: Float = 0f,
    val mediaButtonPerNext: Boolean = false,
    val readAloudByPage: Boolean = false,
    val contentSplitMode: String = ReadAloudContentSplitMode.Default.storageValue,
    /** 「按符号」划分方式选中的标点；空集合表示使用默认句末标点。 */
    val contentSplitSymbols: Set<String> = emptySet(),
    val androidMediaControlEnabled: Boolean = false,
    val systemMediaControlCompatibilityChange: Boolean = true,
    val streamReadAloudAudio: Boolean = false,
    val ttsTimer: Int = 0,
    val finishCurrentChapterAfterTimer: Boolean = false,
    val ttsFollowSys: Boolean = true,
    val ttsSpeechRate: Int = 5,
    val speechAnalysisMode: String = "rule",
    /**
     * Reasoning level for AI speech analysis (dialogue attribution / AI understanding).
     * OFF by default: models that think by default (Zhipu GLM, …) return the JSON only in
     * `reasoning_content` and the analysis fails, so thinking must be opted into explicitly.
     */
    val speechAnalysisReasoningLevel: String = AiReasoningLevel.OFF.storageValue,
    val useMultiSpeaker: Boolean = true,
    val defaultInterface: String = "classic",
    val contentSelectSpeakMode: Int = 0,
    val audioPreDownloadNum: Int = 10,
    val ttsPreSynthesisConcurrency: Int = 3,
)
