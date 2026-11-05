package io.legado.app.service

import kotlin.math.abs

/**
 * 引擎里同时排队的句子上限（含正在播的那一句）。
 *
 * 声音引擎按句缓冲：队列里有下一句，段与段之间才不会出现「等合成」的空档。
 * 深度不必很大——只要下一句的合成比当前句的播放先完成，段间就是无缝的。
 */
internal const val SPEECH_WINDOW_DEPTH = 3

/** 语速/音高由 Float 运算得出，相等比较必须留余量。 */
private const val SPEECH_STATE_EPSILON = 0.001f

/**
 * 一句送进引擎时需要的引擎级状态。
 *
 * `setVoice` / `setSpeechRate` / `setPitch` 改的都是引擎级状态，改一次就影响之后所有的合成，
 * 所以只有「下一句要的状态与当前一致」时才允许提前把句子排进引擎。
 */
internal data class CueSpeechState(
    val engineId: String,
    val voiceName: String,
    val rate: Float,
    val pitch: Float,
)

/** 下一句能不能在当前引擎状态下直接排队：换引擎、换音色、改语速或音高都不行。 */
internal fun canSpeakInSameEngineState(applied: CueSpeechState, next: CueSpeechState): Boolean =
    applied.engineId == next.engineId &&
        applied.voiceName == next.voiceName &&
        abs(applied.rate - next.rate) < SPEECH_STATE_EPSILON &&
        abs(applied.pitch - next.pitch) < SPEECH_STATE_EPSILON

/**
 * 已经送进引擎、还没回调完的句子。
 *
 * 只记下标，不记文本：文本与偏移另有 pendingUtterances / speechProgress 按 utteranceId 存。
 * 填充在 IO 上进行、消费在引擎回调线程上进行，所以每个方法自带锁。
 */
internal class SystemTtsSpeechWindow(private val capacity: Int = SPEECH_WINDOW_DEPTH) {

    private val queued = LinkedHashSet<Int>()

    /** 归属的播放会话号：换会话后旧窗口里的句子一律作废。 */
    var session: Int = Int.MIN_VALUE
        private set

    @get:Synchronized
    val size: Int
        get() = queued.size

    @get:Synchronized
    val isFull: Boolean
        get() = queued.size >= capacity

    @get:Synchronized
    val hasPending: Boolean
        get() = queued.isNotEmpty()

    @Synchronized
    fun reset(session: Int) {
        this.session = session
        queued.clear()
    }

    @Synchronized
    fun clear() {
        session = Int.MIN_VALUE
        queued.clear()
    }

    @Synchronized
    fun contains(cueIndex: Int): Boolean = queued.contains(cueIndex)

    @Synchronized
    fun add(cueIndex: Int) {
        queued.add(cueIndex)
    }

    /** 这句是否原本就在窗口里；已经消费过（重复回调）返回 false。 */
    @Synchronized
    fun complete(cueIndex: Int): Boolean = queued.remove(cueIndex)
}
