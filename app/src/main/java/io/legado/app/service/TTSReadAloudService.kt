@file:Suppress("DEPRECATION")
package io.legado.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.IntentAction
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.domain.model.readaloud.SpeechVoiceRouter
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.readaloud.isBlankReadAloudUnit
import io.legado.app.help.MediaHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.ui.config.readConfig.ReadConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 本地朗读
 */
class TTSReadAloudService : BaseReadAloudService(), KoinComponent {

    override val useSpeechPlaybackQueue: Boolean = true

    /**
     * 引擎侧偏移还原：utteranceId → 送进引擎的那一句。
     *
     * 标记是整段删掉的，引擎回来的下标比正文靠前若干字符；不换算回去，朗读进度、翻页判定
     * 和「从半句接着念」的偏移就全都错位。
     */
    private val pendingUtterances = mutableMapOf<String, CastMarkers.Spoken>()

    protected override val currentSpeechRate: Float
        get() = if (ReadConfig.ttsFollowSys) 1f else (speechRateSetting + 5) / 10f

    private val readAloudSettingsGateway: ReadAloudSettingsGateway by inject()
    @Volatile
    private var speechRateSetting: Int = 5

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private var playAfterTtsInit = false
    private val ttsCallbackHandler = Handler(Looper.getMainLooper())
    private val ttsUtteranceListener = TTSUtteranceListener()
    private var speakJob: Coroutine<*>? = null
    private var serialPlayback = false
    private var retriedSubmission: ReadAloudPlaybackCursor? = null
    private var utteranceStartPos = 0
    private var utteranceStartReadAloudNumber = 0

    /**
     * 每句各自的起点与「引擎报过的最远下标」：判断「是不是念到一半就收了尾」用。
     *
     * 预取窗口会让两句同时在引擎里，单例字段被后一句的 onRangeStart 冲掉后，
     * 前一句收尾时算出来的尾巴就是整句，于是每段都要整句重念。
     */
    private val speechProgress = mutableMapOf<String, SpeechProgress>()

    /** 已送进引擎、还没回调完的句子（含正在播的这一句）。 */
    private val speechWindow = SystemTtsSpeechWindow(SPEECH_WINDOW_DEPTH)

    /** 引擎已经念完、但游标还没走到的句子：预取会让它们比游标先结束。 */
    private val completedAhead = mutableSetOf<Int>()

    /** 同一句提前收尾后补念的次数上限：引擎真的念不动时不能无限重试。 */
    private var respeakTries = 0
    private var needParagraphInterval = false // 是否需要进行段落间隔延迟
    private var activeEngine = ""
    private var activeVoiceName = ""

    /** 当前生效的是引擎自带默认音色（true = 没有沿用任何角色的声音） */
    private var voiceIsDefault = true
    private var initGeneration = 0

    /**
     * 引擎音色表：一次枚举、整个会话复用。
     *
     * 逐句 speak 前都读 `tts.voices` 是换音色停顿的主因——那是一次同步 binder 全量枚举，
     * 引擎音色多时要几百毫秒，回包超过 binder 上限的引擎（MultiTTS）还会直接回空。
     * 只在引擎实例变化后重取；空表不缓存，免得引擎尚未就绪就把整个会话锁死在默认音色。
     */
    private var voiceCatalogCache: Set<Voice>? = null

    /** 上一次发给引擎的语速/音高：相同就不再发，省下切换边界上的两次 binder 调用 */
    private var appliedSpeechRate = -1f
    private var appliedPitch = -1f

    /** 上一句自然播完、由回调直接接下一句：只有这时才允许让引擎自己接力（QUEUE_ADD） */
    private var advancedFromUtteranceDone = false

    /** 播放会话号: 每次真正发声、停止、暂停、清理或整体换章时递增, 用于拒收旧会话的回调 */
    private var speakSession = 0

    /** 最近一次已推进的 utteranceId：同一句的重复回调只推进一次（见 advanceOnce）。 */
    private var advancedUtteranceId: String? = null
    private val TAG = "TTSReadAloudService"

    override fun onCreate() {
        super.onCreate()
        speechRateSetting = readAloudSettingsGateway.currentSettings.ttsSpeechRate
        lifecycleScope.launch {
            readAloudSettingsGateway.settings.collect {
                speechRateSetting = it.ttsSpeechRate
            }
        }
        initTts()
    }

    override fun onDestroy() {
        super.onDestroy()
        clearTTS()
    }

    @Synchronized
    private fun initTts(engineOverride: String? = null) {
        ttsInitFinish = false
        voiceCatalogCache = null
        appliedSpeechRate = -1f
        appliedPitch = -1f
        val engine = engineOverride
            ?: GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine).getOrNull()?.value
        activeEngine = engine.orEmpty()
        val generation = ++initGeneration
        LogUtils.d(TAG, "initTts engine:$engine")
        val listener = TextToSpeech.OnInitListener { status ->
            postTtsCallback { onTtsInitialized(status, generation) }
        }
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this, listener)
        } else {
            TextToSpeech(this, listener, engine)
        }
    }

    @Synchronized
    fun clearTTS() {
        speakSession++
        speakJob?.cancel()
        resetSpeechTracking()
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
        playAfterTtsInit = false
        activeVoiceName = ""
        voiceIsDefault = true
        voiceCatalogCache = null
        appliedSpeechRate = -1f
        appliedPitch = -1f
        initGeneration++
    }

    /** Binder 回调先返回，随后再与入队、停止等操作串行处理。 */
    private fun postTtsCallback(callback: () -> Unit) {
        ttsCallbackHandler.post {
            synchronized(this) { callback() }
        }
    }

    /** 换会话时整组作废：窗口、已完成集合和每句的进度表都只对所属会话有效。 */
    private fun resetSpeechTracking() {
        speechWindow.clear()
        completedAhead.clear()
        speechProgress.clear()
        pendingUtterances.clear()
    }

    private fun onTtsInitialized(status: Int, generation: Int) {
        if (generation != initGeneration) return
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let {
                it.setOnUtteranceProgressListener(ttsUtteranceListener)
                // 新初始化的引擎就停在它自己的默认音色上
                activeVoiceName = ""
                voiceIsDefault = true
                voiceCatalogCache = null
                appliedSpeechRate = -1f
                appliedPitch = -1f
                ttsInitFinish = true
                upSpeechRate(reset = false)
                // 先把音色表取回来：否则第一处「旁白→角色」的边界要现场等一次全量枚举
                execute { voiceCatalog() }
                if (playAfterTtsInit) play()
            }
        } else {
            toastOnUi(R.string.tts_init_failed)
            if (playAfterTtsInit) pauseReadAloud()
            clearTTS()
        }
    }

    @Synchronized
    override fun play() {
        if (!canStartPreparedPlayback) return
        // 上一句自然播完、这里接下一句 —— 只有这一种情况可以让引擎自己接力
        val continuesCurrentSpeech = advancedFromUtteranceDone
        advancedFromUtteranceDone = false
        var nextRoute: ReadAloudVoice? = null
        if (hasSpeechPlaybackQueue) {
            val route = systemVoiceForCurrentCue()
            val requiredEngine = route.engineId
            if (requiredEngine != activeEngine || textToSpeech == null) {
                clearTTS()
                playAfterTtsInit = true
                initTts(requiredEngine)
                return
            }
            // 换音色与改语速挪到 speakJob 里：那里先等引擎把上一句缓冲的音频放完，
            // 否则这两次调用打在还在播的那一句上，听着就是突然加速然后跳下一句
            nextRoute = route
        }
        if (!ttsInitFinish) {
            playAfterTtsInit = true
            if (textToSpeech == null) initTts()
            return
        }
        playAfterTtsInit = false
        val tts = textToSpeech ?: return
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            preparePlayback()
            return
        }
        super.play()
        if (!continuesCurrentSpeech) {
            // 起播、暂停后恢复、换章这几路要放这声无声垫音（服务在 onCreate 里就把 isRun
            // 置真，不能按「是否已在播放」判断）；逐句接力不每句新建 MediaPlayer 抢音频轨，
            // 免得抢轨的停顿加在切换边界上
            MediaHelp.playSilentSound(this@TTSReadAloudService)
        }
        
        // 捕获本次是否需要进行段落延迟，并将标志位复位（防多次触发）
        val isDelay = needParagraphInterval
        needParagraphInterval = false
        
        val session = ++speakSession
        speakJob?.cancel()
        resetSpeechTracking()
        val interval = ReadConfig.ttsParagraphInterval.toLong()
        // 章节批量入队无法在同一段内等上一片完成，超长段留给逐段路径处理。
        serialPlayback = interval > 0 ||
            (!hasSpeechPlaybackQueue && contentList.any { it.length > TextToSpeech.getMaxSpeechInputLength() })
        speakJob = execute {
            AppLog.putDebug("TTS_PLAY: nowSpeak=$nowSpeak, isDelay=$isDelay, interval=$interval")
            
            if (hasSpeechPlaybackQueue || serialPlayback) {
                // 段落间隔模式：单段播放
                if (isDelay) {
                    AppLog.putDebug("TTS开始延迟: $interval 毫秒")
                    delay(interval)
                    AppLog.putDebug("TTS延迟结束，准备播放")
                }
                ensureActive()

                if (nextRoute != null) awaitEngineIdle(tts, continuesCurrentSpeech)
                speakCurrentCue(tts, session, nextRoute, continuesCurrentSpeech, isDelay)
            } else {
                enqueueChapter(tts, session)
            }
        }.onError {
            AppLog.putDebug("TTS协程异常: ${it.localizedMessage}")
        }
    }

    @Synchronized
    private fun speakCurrentCue(
        tts: TextToSpeech,
        session: Int,
        route: ReadAloudVoice?,
        continuesCurrentSpeech: Boolean,
        isDelay: Boolean,
    ) {
        if (session != speakSession || pause) return
        val voiceChanged = if (route != null) {
            applyVoice(route.speakerId).also { applyPreset(route) }
        } else {
            applyDefaultPreset()
            false
        }
        val storedStart = paragraphStartPos
        val storedTail = if (storedStart > 0) {
            contentList[nowSpeak].substring(storedStart)
        } else {
            contentList[nowSpeak]
        }
        // 偏移用完才去掉标记：这一句要么干净正文，要么带着 <<角色（池）>> 交给认标签的引擎
        val utteranceId = ttsUtteranceId(AppConst.APP_TAG, session, nowSpeak)
        val spoken = speakable(storedTail, utteranceId, storedStart, readAloudNumber, limitLength = true)
        if (spoken.text.isBlank()) {
            AppLog.putDebug("TTS段落全标点跳过: nowSpeak=$nowSpeak")
            pendingUtterances.remove(utteranceId)
            val nextChunkOffset = speechProgress.remove(utteranceId)?.nextChunkOffset
            postTtsCallback {
                if (session != speakSession || pause) return@postTtsCallback
                if (nextChunkOffset == null) {
                    skipCurrentCue()
                } else {
                    paragraphStartPos = nextChunkOffset
                    play()
                }
            }
            return
        }
        // 第三条音轨：这一单元身上挂了音效（正则角色「命中不念改放音频」）就一起响
        playCueSounds(nowSpeak)
        AppLog.putDebug("TTS开始Speak: ${spoken.text}")
        val queueMode = if (continuesCurrentSpeech && !isDelay) {
            // 接上一句一律排队，不冲刷。引擎是「整句合成好才报 onDone」的，回调比耳朵
            // 听到的出声早好几秒（约 5 秒），这时 QUEUE_FLUSH 会把还没播出去的尾巴直接
            // 丢掉，角色长句会读到一半跳下一句。换音色不需要冲刷：音色是送给引擎合成
            // 这一步的，排进来的这一句本来就用新音色。
            TextToSpeech.QUEUE_ADD
        } else {
            // 起播、暂停后恢复、手动上下段、半句续念：这些要冲掉引擎里剩下的东西
            TextToSpeech.QUEUE_FLUSH
        }
        LogUtils.d(TAG, "queueMode=${if (queueMode == TextToSpeech.QUEUE_ADD) "ADD" else "FLUSH"} nowSpeak=$nowSpeak voiceChanged=$voiceChanged")
        // 先登记再开口：引擎可能立刻回调，窗口里没有这一句就会被当成过期消息丢掉
        speechWindow.reset(session)
        speechWindow.add(nowSpeak)
        val result = tts.runCatching {
            speak(spoken.text, queueMode, null, utteranceId)
        }.getOrElse {
            AppLog.put("tts出错\n${it.localizedMessage}", it, true)
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) {
            retrySubmission()
            return
        }
        retriedSubmission = null
        // 这一句已经在引擎里，接着把后面「音色语速都没变」的句子预取进去：
        // 光等 onDone 再送下一句，每段都要先等引擎放空再冷启动合成，听起来就是段间一顿
        if (speechProgress[utteranceId]?.nextChunkOffset == null) fillSpeechWindow(session)
        LogUtils.d(TAG, "朗读内容添加完成")
    }

    /** 无间隔的旧路径一次入队整章，每次提交仍可被暂停或换章打断。 */
    private suspend fun enqueueChapter(tts: TextToSpeech, session: Int) {
        val paragraphs = synchronized(this) {
            if (session != speakSession || pause) return
            applyDefaultPreset()
            contentList
        }
        var isAddedText = false
        for (i in nowSpeak until paragraphs.size) {
            synchronized(this) {
                if (session != speakSession || pause) return
                val storedStart = if (i == nowSpeak) paragraphStartPos else 0
                val storedTail = paragraphs[i].substring(storedStart)
                val utteranceId = ttsUtteranceId(AppConst.APP_TAG, session, i)
                val text = speakable(storedTail, utteranceId, storedStart).text
                if (text.isBlank()) {
                    pendingUtterances.remove(utteranceId)
                    speechProgress.remove(utteranceId)
                    return@synchronized
                }
                val queueMode = if (isAddedText) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
                val result = tts.runCatching {
                    speak(text, queueMode, null, utteranceId)
                }.getOrElse {
                    AppLog.put("tts出错\n${it.localizedMessage}", it, true)
                    TextToSpeech.ERROR
                }
                if (result == TextToSpeech.ERROR) {
                    if (!isAddedText) {
                        retrySubmission()
                        return
                    }
                    AppLog.put("tts朗读出错:$text")
                } else {
                    retriedSubmission = null
                }
                isAddedText = true
            }
        }
        LogUtils.d(TAG, "朗读内容添加完成")
        if (isAddedText) return
        synchronized(this) {
            if (session != speakSession || pause) return
            needParagraphInterval = false
            tts.runCatching { stop() }
        }
        delay(1000)
        postTtsCallback {
            if (session == speakSession && !pause) completeCurrentChapter()
        }
    }

    private fun retrySubmission() {
        val cursor = ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
        if (retriedSubmission == cursor) {
            val session = ++speakSession
            resetSpeechTracking()
            postTtsCallback {
                if (session == speakSession && !pause) {
                    AppLog.put("系统朗读重新初始化后仍无法提交当前文本，已暂停朗读", toast = true)
                    pauseReadAloud()
                }
            }
            return
        }
        retriedSubmission = cursor
        val engine = activeEngine
        clearTTS()
        playAfterTtsInit = true
        initTts(engine)
    }

    /**
     * 记下这一句要送给引擎的文本（标记按开关处理）与其下标还原表。
     *
     * [base] 是这一句在段落里的起点：引擎报回来的下标加上它才是正文偏移，长句被引擎提前
     * 收尾时靠它才能从半句处接着念（见 [TTSUtteranceListener.onDone]）。
     */
    private fun speakable(
        storedTail: String,
        utteranceId: String,
        base: Int,
        startReadAloudNumber: Int = 0,
        limitLength: Boolean = false,
    ): CastMarkers.Spoken {
        // 一次入队整章时表会跟着涨，引擎不再回调的条目没人清理，换会话就整体作废
        if (pendingUtterances.size > MAX_PENDING_UTTERANCES) {
            pendingUtterances.clear()
            speechProgress.clear()
        }
        val full = spokenFor(storedTail, base)
        val limit = TextToSpeech.getMaxSpeechInputLength()
        var nextChunkOffset: Int? = null
        val spoken = if (limitLength && full.text.length > limit) {
            // 先转换为实际发声文本再切片，切点避开代理对，并映射回原段坐标。
            val end = if (full.text[limit].isLowSurrogate() && full.text[limit - 1].isHighSurrogate()) {
                limit - 1
            } else {
                limit
            }
            val storedEnd = full.storedOf(end)
            nextChunkOffset = storedEnd
            CastMarkers.Spoken(full.text.substring(0, end), storedEnd, full::storedOf)
        } else {
            full
        }
        pendingUtterances[utteranceId] = spoken
        speechProgress[utteranceId] = SpeechProgress(base, startReadAloudNumber, nextChunkOffset)
        return spoken
    }

    /**
     * 这一句全是标点/标记，引擎不会出声：直接按「念完」推进，不进引擎。
     *
     * 不能伪造一次 onDone 回调：窗口里没有这一句，回调会被当成过期消息丢掉，朗读就停在这里。
     */
    private fun skipCurrentCue() {
        val advanced = nextParagraph(naturalCompletion = true)
        respeakTries = 0
        if (advanced &&
            !pause &&
            (hasSpeechPlaybackQueue || serialPlayback)
        ) {
            needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
            advancedFromUtteranceDone = true
            play()
        }
    }

    /**
     * 等引擎把已经缓冲的音频真放完，再动音色和语速。
     *
     * MultiTTS / TTS Server 这类转发引擎连 `isSpeaking` 一起报假：onDone 之后还有 5 秒
     * 音频在播，它这里已经回 false，所以这一层守不住它们，真正的兜底是接下一句不冲刷
     * （见 [play] 里的 queueMode）。本层覆盖的是老实报 isSpeaking 的引擎，代价一次 binder 查询。
     * 引擎真放完时 isSpeaking 已经是 false，这里只多问一次。
     */
    private suspend fun awaitEngineIdle(tts: TextToSpeech, continuesCurrentSpeech: Boolean) {
        if (!continuesCurrentSpeech || pause) return
        var waited = 0L
        while (waited < ENGINE_IDLE_WAIT_MAX_MS) {
            if (!runCatching { tts.isSpeaking }.getOrDefault(false)) {
                if (waited > 0) LogUtils.d(TAG, "引擎收尾等待 ${waited}ms 后放完")
                return
            }
            delay(ENGINE_IDLE_POLL_MS)
            waited += ENGINE_IDLE_POLL_MS
        }
        AppLog.put("朗读：引擎超过 ${ENGINE_IDLE_WAIT_MAX_MS / 1000} 秒仍在发声，按当前进度继续下一句")
    }

    private fun systemVoiceForCurrentCue(): ReadAloudVoice = systemVoiceForCue(nowSpeak)

    /** 指定朗读单元该用哪个系统音色；路由不到就退回「当前配置的引擎 + 默认音色」。 */
    private fun systemVoiceForCue(index: Int): ReadAloudVoice {
        val configured = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine)
            .getOrNull()?.value.orEmpty()
        val fallback = ReadAloudVoice(
            id = "runtime-system:$configured",
            engineType = ReadAloudVoice.ENGINE_SYSTEM,
            engineId = configured,
            speakerId = "",
            displayName = configured,
        )
        val cue = playbackQueue.cues.getOrNull(index) ?: return fallback
        return SpeechVoiceRouter.route(
            cue = cue,
            supportedEngineTypes = setOf(ReadAloudVoice.ENGINE_SYSTEM),
            defaultRoute = SpeechEngineRoute(ReadAloudVoice.ENGINE_SYSTEM, configured),
        ).voice ?: fallback
    }

    /** 引擎此刻实际挂着的状态：预取只在这个状态与下一句要求的一致时继续。 */
    private fun appliedSpeechState(): CueSpeechState = CueSpeechState(
        engineId = activeEngine,
        voiceName = if (voiceIsDefault) "" else activeVoiceName,
        rate = appliedSpeechRate,
        pitch = appliedPitch,
    )

    private fun desiredSpeechState(index: Int): CueSpeechState? {
        if (index !in contentList.indices) return null
        val voice = systemVoiceForCue(index)
        val (rate, pitch) = presetForCue(index, voice)
        return CueSpeechState(
            engineId = voice.engineId,
            voiceName = voice.speakerId,
            rate = rate,
            pitch = pitch,
        )
    }

    /**
     * 把后面「音色、语速、音高都没变」的句子提前排进引擎。
     *
     * 换引擎、换音色、改语速/音高都是引擎级状态，改一次会影响引擎里排着的所有句子，
     * 所以这类边界一律到此为止，留给 [play] 的串行路径（先等引擎放完再改）。
     */
    @Synchronized
    private fun fillSpeechWindow(session: Int) {
        if (!hasSpeechPlaybackQueue || ReadConfig.ttsParagraphInterval > 0 || pause) return
        if (session != speakSession || speechWindow.session != session) return
        val tts = textToSpeech ?: return
        var cursor = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
        while (!speechWindow.isFull) {
            val next = playbackQueue.next(cursor) ?: break
            cursor = next
            val cue = next.cueIndex
            if (speechWindow.contains(cue) || cue in completedAhead) continue
            val state = desiredSpeechState(cue) ?: break
            if (!canSpeakInSameEngineState(appliedSpeechState(), state)) break
            val text = contentList.getOrNull(cue) ?: break
            val utteranceId = ttsUtteranceId(AppConst.APP_TAG, session, cue)
            val spoken = speakable(
                text,
                utteranceId,
                base = 0,
                startReadAloudNumber = playbackQueue.cues[cue].chapterStart,
            )
            if (spoken.text.isBlank() || spoken.text.length > TextToSpeech.getMaxSpeechInputLength()) {
                pendingUtterances.remove(utteranceId)
                speechProgress.remove(utteranceId)
                // 空句和需要切片的长句留给串行路径，预取不能越过它们。
                break
            }
            speechWindow.add(cue)
            val result = tts.runCatching {
                speak(spoken.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }.getOrElse {
                AppLog.put("tts预取出错\n${it.localizedMessage}", it)
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                speechWindow.complete(cue)
                pendingUtterances.remove(utteranceId)
                speechProgress.remove(utteranceId)
                break
            }
        }
    }

    /** 补队尾；引擎调用一律放到 IO 上，不占回调线程。 */
    private fun refillSpeechWindow() {
        if (!hasSpeechPlaybackQueue || pause) return
        if (ReadConfig.ttsParagraphInterval > 0) return
        val session = speakSession
        execute { fillSpeechWindow(session) }
    }

    /**
     * 把游标推到「还没念完的那一句」。
     *
     * 预取会让后面的句子比游标先念完（转发引擎合成完就回 onDone），这些句子既不能让游标
     * 停在它们身上，也不能漏掉它们身上的音效。
     * 返回 false 表示本章已收尾，返回 true 表示游标指向仍需播放的句子。
     */
    private fun advancePastCompleted(): Boolean {
        while (true) {
            val current = playbackCursor ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            if (!completedAhead.remove(current.cueIndex)) return true
            if (!nextParagraph(naturalCompletion = true)) return false
            // 到了新的一句，补念的预算跟着重置（老路径靠 advanceOnce 做同一件事）
            respeakTries = 0
            if (speechWindow.contains(nowSpeak) || nowSpeak in completedAhead) {
                playCueSounds(nowSpeak)
            }
        }
    }

    /**
     * 切换系统 TTS 音色，返回本句是否真的换了声音。
     *
     * 只按名字在 tts.voices 里找、找不到就返回是不行的：引擎报不出默认音色名
     * （`defaultVoice` 为 null 时名字是空串）或被分配的音色已被改名/删除时，切换会静默
     * 失败，上一个角色的音色一直挂着，之后的旁白也用角色的声音读。所以这些情况必须
     * 走 [restoreDefaultVoice] 退回引擎默认。
     *
     * 音色表经 [voiceCatalog] 缓存：逐句各做一次全量枚举会在换音色处产生可感知的停顿。
     */
    private fun applyVoice(voiceName: String): Boolean {
        val tts = textToSpeech ?: return false
        if (voiceName.isBlank()) {
            return restoreDefaultVoice(tts)
        }
        if (!voiceIsDefault && voiceName == activeVoiceName) return false
        val voice = voiceCatalog().firstOrNull { it.name == voiceName }
        if (voice == null) {
            // 引擎报不出这个音色名（MultiTTS 那类 getVoices 回包超限的引擎会整表为空）时，
            // 换音静默失败，之后的旁白都还是默认音色
            AppLog.put("系统 TTS 找不到音色 $voiceName，改用默认音色")
            return restoreDefaultVoice(tts)
        }
        if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
            voiceIsDefault = false
            activeVoiceName = voiceName
            return true
        }
        AppLog.putDebug("系统 TTS 音色切换失败: $voiceName")
        return false
    }

    /** 退回引擎默认音色（setVoice(null) 就是「用引擎自己的默认」，不依赖它报不报得出名字）。 */
    private fun restoreDefaultVoice(tts: TextToSpeech): Boolean {
        if (voiceIsDefault && activeVoiceName.isEmpty()) return false
        if (tts.setVoice(null) == TextToSpeech.SUCCESS) {
            voiceIsDefault = true
            activeVoiceName = ""
            return true
        }
        AppLog.putDebug("系统 TTS 恢复默认音色失败")
        return false
    }

    /** 引擎音色表：只在引擎实例变化后枚举一次。 */
    private fun voiceCatalog(): Set<Voice> {
        voiceCatalogCache?.let { return it }
        val tts = textToSpeech ?: return emptySet()
        val catalog = runCatching { tts.voices }.getOrNull().orEmpty()
        if (catalog.isEmpty()) return emptySet()
        voiceCatalogCache = catalog
        return catalog
    }

    /** 这一句要的语速/音高：角色自带预设优先，其余落到全局语速与变声器。 */
    private fun presetForCue(index: Int, voice: ReadAloudVoice): Pair<Float, Float> {
        val config = runCatching {
            GSON.fromJson(voice.traitsJson, SystemTtsVoiceConfig::class.java)
        }.getOrNull() ?: SystemTtsVoiceConfig()
        val globalRate = if (ReadConfig.ttsFollowSys) {
            1f
        } else {
            (speechRateSetting + 5) / 10f
        }
        // 角色的变声器：直读路径没有自己的播放器，只能用引擎的音高/语速这一层
        val cue = playbackQueue.cues.getOrNull(index)
        val effect = VoiceEffectStore.ofSpeech(cue?.voiceEffect, cue?.characterId)
        return VoiceEffectAudio.speedOf(effect, config.speechRate ?: globalRate) to
            VoiceEffectAudio.pitchOf(effect, config.pitch ?: 1f)
    }

    private fun applyPreset(voice: ReadAloudVoice) {
        val cue = playbackQueue.cues.getOrNull(nowSpeak)
        val effect = VoiceEffectStore.ofSpeech(cue?.voiceEffect, cue?.characterId)
        if (VoiceEffectStore.needsSessionEffect(effect)) {
            // 混响/金属感是挂在音频会话上的平台音效，TextToSpeech 不给会话号 → 这一层挂不上。
            // 需要它时朗读该走文件合成那条服务（见 ReadAloud.findCoordinatorHttpSeed 的升级），
            // 真落到这里只剩音高/语速，写进日志让用户知道少了哪一层。
            AppLog.put(
                "变声器「${effect?.name}」的混响/金属感在系统 TTS 直读下挂不上，只有音高/语速生效",
            )
        }
        val (rate, pitch) = presetForCue(nowSpeak, voice)
        val tts = textToSpeech ?: return
        // 同一角色的连续两句算出的是同一个值，就别再发两次 binder 调用堆在切换边界上
        if (rate != appliedSpeechRate) {
            appliedSpeechRate = rate
            tts.setSpeechRate(rate)
        }
        if (pitch != appliedPitch) {
            appliedPitch = pitch
            tts.setPitch(pitch)
        }
    }

    private fun applyDefaultPreset() {
        val tts = textToSpeech ?: return
        val rate = if (ReadConfig.ttsFollowSys) 1f else (speechRateSetting + 5) / 10f
        if (rate != appliedSpeechRate) {
            appliedSpeechRate = rate
            tts.setSpeechRate(rate)
        }
        if (appliedPitch != 1f) {
            appliedPitch = 1f
            tts.setPitch(1f)
        }
    }

    @Synchronized
    override fun playStop() {
        speakSession++
        speakJob?.cancel()
        needParagraphInterval = false
        playAfterTtsInit = false
        retriedSubmission = null
        resetSpeechTracking()
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 更新朗读速度
     */
    @Synchronized
    override fun upSpeechRate(reset: Boolean) {
        if (ReadConfig.ttsFollowSys) {
            if (reset) {
                val shouldPlay = !pause && canStartPreparedPlayback
                clearTTS()
                playAfterTtsInit = shouldPlay
                initTts()
            }
        } else {
            val speechRate = (speechRateSetting + 5) / 10f
            appliedSpeechRate = speechRate
            textToSpeech?.setSpeechRate(speechRate)
            upMediaMetadata()
            if (reset && !pause) {
                play()
            }
        }
    }

    /**
     * 暂停朗读
     */
    @Synchronized
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        playStop()
    }

    /**
     * 恢复朗读
     */
    @Synchronized
    override fun resumeReadAloud() {
        if (resumeAfterPreparation()) return
        super.resumeReadAloud()
        play()
    }

    @Synchronized
    override fun onPlaybackStateReplaced() {
        // 换章/重新定位后, 旧章节发言的迟到回调不得再改新队列状态
        speakSession++
        speakJob?.cancel()
        resetSpeechTracking()
    }

    /**
     * 朗读监听
     */
    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        private val TAG = "TTSUtteranceListener"

        override fun onStart(s: String) = postTtsCallback {
            val cue = liveCueOf(s) ?: return@postTtsCallback
            // 预取会把后面几句一起排进引擎，它们的回调可能先到：只有轮到这一句才动进度
            if (cue != nowSpeak) return@postTtsCallback
            LogUtils.d(TAG, "onStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$s")
            utteranceStartPos = paragraphStartPos
            utteranceStartReadAloudNumber = readAloudNumber
            if (isChapterTitleAt(nowSpeak)) {
                upMediaMetadata(showContent = true)
                return@postTtsCallback
            }
            readerReadAloudChapter?.let {
                val position = currentRangePosition(
                    utteranceStartReadAloudNumber, 0, utteranceStartPos
                )
                if (contentList[nowSpeak].isBlankReadAloudUnit()) {
                    nextParagraph(naturalCompletion = true)
                    // 游标已经挪到下一句了，再拿这一句算页码和进度就是把两句的位置混在一起
                    return@let
                }
                if (pageIndex + 1 < it.pageCount
                    && position + 1 > it.pageStart(pageIndex + 1)
                ) {
                    pageIndex++
                    // 这是引擎自己越过页界续读，不是用户翻页：标记后 ReadBook 既不会脱离
                    // 会话，也不会把 TTS 在第二页重启
                    syncReaderToPlaybackPage()
                }
                upTtsProgress(position + 1)
                upMediaMetadata(showContent = true)
            }
        }

        override fun onDone(s: String) = postTtsCallback {
            val cue = liveCueOf(s) ?: return@postTtsCallback
            LogUtils.d(TAG, "onDone utteranceId:$s")
            val spoken = pendingUtterances.remove(s)
            val nextChunkOffset = speechProgress[s]?.nextChunkOffset
            if (resumeIfCutShort(cue, s, spoken, "引擎提前收尾")) return@postTtsCallback
            if (nextChunkOffset != null) {
                paragraphStartPos = nextChunkOffset
                respeakTries = 0
                advancedFromUtteranceDone = true
                play()
                return@postTtsCallback
            }
            finishCue(cue, s, "念完")
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) = postTtsCallback {
            val cue = liveCueOf(utteranceId) ?: return@postTtsCallback
            if (cue != nowSpeak) return@postTtsCallback
            val spoken = utteranceId?.let(pendingUtterances::get)
            // 引擎报的是「送进去的那一份文本」的下标：删过标记就得先换算回正文，否则
            // 每一句角色台词的进度、翻页判定和续念偏移都差着标记那段长度
            val storedStart = spoken?.storedOf(start) ?: (utteranceStartPos + start)
            val progress = utteranceId?.let(speechProgress::get)
            if (spoken != null && progress != null) {
                progress.sawRange = true
                progress.rangeEnd = maxOf(progress.rangeEnd, end.coerceIn(0, spoken.text.length))
            }
            paragraphStartPos = storedStart
            if (isChapterTitleAt(nowSpeak)) return@postTtsCallback
            // 正在朗读的精确章内位置（段起点 + 段内偏移），保持 readAloudNumber 的"段起点"语义不被污染
            // storedStart 已经把引擎下标换算回正文（含 utteranceStartPos 与被删掉的标记），
            // 上游的 utteranceStartOffset 参数是我们这里的子集，不用再传一遍。
            val startNumber = if (hasSpeechPlaybackQueue) {
                progress?.startReadAloudNumber ?: utteranceStartReadAloudNumber
            } else {
                utteranceStartReadAloudNumber
            }
            val position = currentRangePosition(startNumber, storedStart)
            updateReadAloudProgressSnapshot(position)
            val msg =
                "onRangeStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId start:$start end:$end frame:$frame"
            LogUtils.d(TAG, msg)
            if (moveToReadAloudPage(position)) {
                upTtsProgress(position)
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) = postTtsCallback {
            val cue = liveCueOf(utteranceId) ?: return@postTtsCallback
            val id = utteranceId ?: return@postTtsCallback
            LogUtils.d(
                TAG,
                "onError nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$id errorCode:$errorCode"
            )
            val spoken = pendingUtterances.remove(id)
            if (resumeIfCutShort(cue, id, spoken, "引擎报错")) return@postTtsCallback
            finishCue(cue, id, "引擎报错($errorCode)")
        }

        @Deprecated("Deprecated in Java")
        override fun onError(s: String) = postTtsCallback {
            val cue = liveCueOf(s) ?: return@postTtsCallback
            LogUtils.d(TAG, "onError nowSpeak:$nowSpeak pageIndex:$pageIndex s:$s")
            val spoken = pendingUtterances.remove(s)
            if (resumeIfCutShort(cue, s, spoken, "引擎报错")) return@postTtsCallback
            finishCue(cue, s, "未带错误码")
        }

        /**
         * 一句收尾后的唯一出口：推进游标、补队尾，或按串行路径起播下一句。
         *
         * 预取窗口里有下一句时**不能**重新 play()：那会用 QUEUE_FLUSH 把已经排好的句子冲掉。
         */
        private fun finishCue(cue: Int, utteranceId: String, reason: String) {
            if (hasSpeechPlaybackQueue) {
                if (!speechWindow.complete(cue)) return
                speechProgress.remove(utteranceId)
                completedAhead.add(cue)
                // 预取会让后面的句子比游标先念完，游标要一路推到还没念完的那一句
                if (!advancePastCompleted()) return
                if (speechWindow.hasPending) {
                    refillSpeechWindow()
                    return
                }
                if (pause) return
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                advancedFromUtteranceDone = true
                play()
                return
            }
            speechProgress.remove(utteranceId)
            // 章节自然完结时本章播放状态已作废, 恢复播放由换章后的 newReadAloud 负责;
            // 若此处仍用旧队列 play(), 会把本章最后一段反复重新入队
            if (!advanceOnce(utteranceId, reason)) return
            val paragraphAdvanced = nextParagraph(naturalCompletion = true)
            if (paragraphAdvanced && !pause && serialPlayback) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                advancedFromUtteranceDone = true
                play()
            }
        }

        /**
         * 引擎念到一半就收了尾时从半句处接着念，返回是否接管了这一句。
         *
         * 判据只能是引擎自己报的范围：它报过进度、离句尾还差一截，才是被切断而不是念完。
         * 预取里早就被引擎判完的句子不补念——那已经不是「正在念的这一句」了。
         */
        private fun resumeIfCutShort(
            cue: Int,
            utteranceId: String,
            spoken: CastMarkers.Spoken?,
            reason: String,
        ): Boolean {
            if (spoken == null) return false
            val progress = speechProgress.remove(utteranceId) ?: return false
            if (cue != nowSpeak) return false
            if (!progress.sawRange || respeakTries >= MAX_RESPEAK_TRIES) return false
            if (spoken.text.length - progress.rangeEnd < MIN_RESPEAK_TAIL) return false
            val resumeFrom = spoken.storedOf(progress.rangeEnd)
            if (resumeFrom <= progress.startPos) return false
            respeakTries++
            AppLog.put(
                "朗读$reason：这一句差 ${spoken.text.length - progress.rangeEnd} 字没收尾，" +
                    "从第 $resumeFrom 字接着念（第 $respeakTries 次）",
            )
            paragraphStartPos = resumeFrom
            if (!pause) play()
            return true
        }

        /**
         * 一句只推进一次。
         *
         * `onError(id, code)` 的默认实现会再回调一次已废弃的 `onError(id)`，两处各自推进
         * 就是白丢一句；长句更容易被引擎中途放弃，丢句的表现更明显。
         */
        private fun advanceOnce(utteranceId: String, reason: String): Boolean {
            if (utteranceId == advancedUtteranceId) {
                AppLog.put("朗读重复回调已忽略: $reason $utteranceId")
                return false
            }
            advancedUtteranceId = utteranceId
            // 推进到新句了，补念的预算跟着重置
            respeakTries = 0
            return true
        }

    }

    /**
     * 把游标推进到下一句；没有下一句就按 [naturalCompletion] 收尾本章或直接换章。
     *
     * 说话体和填充窗口都要用它，所以放在服务层而不是回调监听里。
     */
    private fun nextParagraph(naturalCompletion: Boolean = false): Boolean {
        if (hasSpeechPlaybackQueue) {
            val current = playbackCursor
                ?: ReadAloudPlaybackCursor(nowSpeak, paragraphStartPos)
            val next = playbackQueue.next(current)
            if (next != null) {
                moveToPlaybackCursor(next)
                return true
            }
            if (naturalCompletion) completeCurrentChapter() else nextChapter()
            return false
        }
        //跳过全标点段落
        do {
            readAloudNumber = nextParagraphPosition(
                currentPosition = readAloudNumber,
                paragraphLength = contentList[nowSpeak].length,
                paragraphStartPosition = paragraphStartPos,
            )
            paragraphStartPos = 0
            nowSpeak++
            if (nowSpeak >= contentList.size) {
                if (naturalCompletion) completeCurrentChapter() else nextChapter()
                return false
            }
        } while (contentList[nowSpeak].isBlankReadAloudUnit())
        // 页内切段不引入换行符，累加会漂移，用段落绝对位置重算
        readAloudNumber = paragraphChapterPositionAt(nowSpeak) ?: 0
        return true
    }

    /**
     * 回调属于这一次播放会话的哪一句。
     *
     * 预取窗口是新的合法来源：窗口里排着的后续句子同样要认。老路径（整章一次入队）没有预取，
     * 只认「就是 nowSpeak 的那一句」，领先的回调只可能来自上一次会话。
     */
    private fun liveCueOf(utteranceId: String?): Int? {
        val cue = cueOf(utteranceId) ?: return null
        return if (hasSpeechPlaybackQueue) {
            cue.takeIf { speechWindow.contains(it) }
        } else {
            cue.takeIf { it == nowSpeak }
        }
    }

    /** 解析 utteranceId 里的会话号与段落下标；会话对不上一律当过期回调。 */
    private fun cueOf(utteranceId: String?): Int? {
        if (utteranceId == null) return null
        val tag = AppConst.APP_TAG
        if (!utteranceId.startsWith(tag)) return null
        val rest = utteranceId.substring(tag.length)
        val separator = rest.indexOf(':')
        if (separator <= 0) return null
        if (rest.substring(0, separator).toIntOrNull() != speakSession) return null
        return rest.substring(separator + 1).toIntOrNull()?.takeIf { it in contentList.indices }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == IntentAction.play) {
            speechRateSetting = readAloudSettingsGateway.currentSettings.ttsSpeechRate
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<TTSReadAloudService>(actionStr)
    }

}

/** 一句被引擎提前收尾后最多补念几次：真的念不动的引擎不能陪它转圈。 */
private const val MAX_RESPEAK_TRIES = 2

/** 等引擎收尾的轮询间隔与上限：上限内没放完就认了，不能把朗读卡死。 */
private const val ENGINE_IDLE_POLL_MS = 100L
private const val ENGINE_IDLE_WAIT_MAX_MS = 8000L

/** 尾巴短于这么多字不值得单独补念：句末标点和收尾静音本来不报进范围。 */
private const val MIN_RESPEAK_TAIL = 8

/** 一次性入队整章时的上限保护：条目只在回调里消费，没人回收就整体作废。 */
private const val MAX_PENDING_UTTERANCES = 256

/** 一句的基准位置与引擎报过的范围：起点在入队时定，范围随回调更新。 */
private class SpeechProgress(
    val startPos: Int,
    val startReadAloudNumber: Int,
    val nextChunkOffset: Int?,
) {
    var sawRange = false

    var rangeEnd = 0
}

internal fun nextParagraphPosition(
    currentPosition: Int,
    paragraphLength: Int,
    paragraphStartPosition: Int,
): Int = currentPosition + paragraphLength + 1 - paragraphStartPosition

internal fun currentRangePosition(
    utteranceStartPosition: Int,
    rangeStart: Int,
    utteranceStartOffset: Int = 0,
): Int = utteranceStartPosition + utteranceStartOffset + rangeStart

/**
 * TTS 回调只回传 utteranceId, 播放会话号必须编码进 id,
 * 否则旧会话(暂停/停止/换章后迟到)的回调无法与当前队列区分
 */
internal fun ttsUtteranceId(appTag: String, session: Int, index: Int): String =
    "$appTag$session:$index"
