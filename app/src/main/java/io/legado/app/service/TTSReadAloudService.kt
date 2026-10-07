@file:Suppress("DEPRECATION")
package io.legado.app.service

import android.app.PendingIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.domain.model.readaloud.ReadAloudPlaybackCursor
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.domain.model.readaloud.SpeechEngineRoute
import io.legado.app.domain.model.readaloud.SpeechVoiceRouter
import io.legado.app.domain.model.readaloud.SystemTtsVoiceConfig
import io.legado.app.exception.NoStackTraceException
import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.help.readaloud.effect.VoiceEffectAudio
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.help.MediaHelp
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
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
import java.util.concurrent.ConcurrentHashMap

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
    private val pendingUtterances = ConcurrentHashMap<String, CastMarkers.Spoken>()

    protected override val currentSpeechRate: Float
        get() = if (ReadConfig.ttsFollowSys) 1f else (speechRateSetting + 5) / 10f

    private val readAloudSettingsGateway: ReadAloudSettingsGateway by inject()
    @Volatile
    private var speechRateSetting: Int = 5

    private var textToSpeech: TextToSpeech? = null
    private var ttsInitFinish = false
    private val ttsUtteranceListener = TTSUtteranceListener()
    private var speakJob: Coroutine<*>? = null
    private var utteranceStartPos = 0
    private var utteranceStartReadAloudNumber = 0

    /** 本句引擎报过的最远下标（引擎坐标系）与是否报过范围：判断「是不是念到一半就收了尾」用。 */
    private var lastRangeEnd = 0
    private var sawRangeProgress = false

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
    @Volatile
    private var advancedFromUtteranceDone = false

    /** 播放会话号: 每次真正发声、停止、暂停、清理或整体换章时递增, 用于拒收旧会话的回调 */
    @Volatile
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
        textToSpeech = if (engine.isNullOrBlank()) {
            TextToSpeech(this) { status -> onTtsInitialized(status, generation) }
        } else {
            TextToSpeech(this, { status -> onTtsInitialized(status, generation) }, engine)
        }
        upSpeechRate()
    }

    @Synchronized
    fun clearTTS() {
        textToSpeech?.runCatching {
            stop()
            shutdown()
        }
        textToSpeech = null
        ttsInitFinish = false
        activeVoiceName = ""
        voiceIsDefault = true
        voiceCatalogCache = null
        appliedSpeechRate = -1f
        appliedPitch = -1f
        initGeneration++
        speakSession++
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
                // 先把音色表取回来：否则第一处「旁白→角色」的边界要现场等一次全量枚举
                execute { voiceCatalog() }
                play()
            }
        } else {
            toastOnUi(R.string.tts_init_failed)
        }
    }

    @Synchronized
    override fun play() {
        // 上一句自然播完、这里接下一句 —— 只有这一种情况可以让引擎自己接力
        val continuesCurrentSpeech = advancedFromUtteranceDone
        advancedFromUtteranceDone = false
        // 本句是否真的换了声音：换了就必须 QUEUE_FLUSH，音色是引擎级状态，不清空就会用上一个角色的声音念
        var voiceChanged = true
        var nextRoute: ReadAloudVoice? = null
        if (hasSpeechPlaybackQueue) {
            val route = systemVoiceForCurrentCue()
            val requiredEngine = route.engineId
            if (requiredEngine != activeEngine || textToSpeech == null) {
                clearTTS()
                initTts(requiredEngine)
                return
            }
            // 换音色与改语速挪到 speakJob 里：那里先等引擎把上一句缓冲的音频放完，
            // 否则这两次调用打在还在播的那一句上，听着就是突然加速然后跳下一句
            nextRoute = route
        }
        if (!ttsInitFinish) return
        if (!requestFocus()) return
        if (contentList.isEmpty()) {
            AppLog.putDebug("朗读列表为空")
            ReadBook.readAloud()
            return
        }
        super.play()
        if (!hasSpeechPlaybackQueue || !continuesCurrentSpeech) {
            // 起播、暂停后恢复、换章这几路要放这声无声垫音（服务在 onCreate 里就把 isRun
            // 置真，不能按「是否已在播放」判断）；逐句接力不每句新建 MediaPlayer 抢音频轨，
            // 免得抢轨的停顿加在切换边界上
            MediaHelp.playSilentSound(this@TTSReadAloudService)
        }
        
        // 捕获本次是否需要进行段落延迟，并将标志位复位（防多次触发）
        val isDelay = needParagraphInterval
        needParagraphInterval = false
        
        speakJob?.cancel()
        speakJob = execute {
            val interval = ReadConfig.ttsParagraphInterval.toLong()
            AppLog.putDebug("TTS_PLAY: nowSpeak=$nowSpeak, isDelay=$isDelay, interval=$interval")
            
            if (hasSpeechPlaybackQueue || interval > 0) {
                // 段落间隔模式：单段播放
                if (isDelay) {
                    AppLog.putDebug("TTS开始延迟: $interval 毫秒")
                    delay(interval)
                    AppLog.putDebug("TTS延迟结束，准备播放")
                }
                ensureActive()

                LogUtils.d(TAG, "朗读列表大小 ${contentList.size}")
                val session = ++speakSession
                val tts = textToSpeech ?: throw NoStackTraceException("tts is null")
                nextRoute?.let {
                    awaitEngineIdle(tts, continuesCurrentSpeech)
                    voiceChanged = applyVoice(it.speakerId)
                    applyPreset(it)
                }
                val storedStart = paragraphStartPos
                val storedTail = if (storedStart > 0) {
                    contentList[nowSpeak].substring(storedStart)
                } else {
                    contentList[nowSpeak]
                }
                // 偏移用完才去掉标记：这一句要么干净正文，要么带着 <<角色（池）>> 交给认标签的引擎
                val utteranceId = ttsUtteranceId(AppConst.APP_TAG, session, nowSpeak)
                val spoken = speakable(storedTail, utteranceId, storedStart)
                if (spoken.text.isEmpty() || spoken.text.matches(AppPattern.notReadAloudRegex)) {
                    AppLog.putDebug("TTS段落全标点跳过: nowSpeak=$nowSpeak")
                    pendingUtterances.remove(utteranceId)
                    ttsUtteranceListener.onDone(utteranceId)
                    return@execute
                }
                // 第三条音轨：这一单元身上挂了音效（正则角色「命中不念改放音频」）就一起响
                playCueSounds(nowSpeak)
                AppLog.putDebug("TTS开始Speak: ${spoken.text}")
                val queueMode = if (hasSpeechPlaybackQueue
                    && continuesCurrentSpeech && paragraphStartPos == 0 && !isDelay
                ) {
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
                val result = tts.runCatching {
                    speak(spoken.text, queueMode, null, utteranceId)
                }.getOrElse {
                    AppLog.put("tts出错\n${it.localizedMessage}", it, true)
                    TextToSpeech.ERROR
                }
                if (result == TextToSpeech.ERROR) {
                    AppLog.put("tts出错 尝试重新初始化")
                    clearTTS()
                    initTts()
                    return@execute
                }
                LogUtils.d(TAG, "朗读内容添加完成")
            } else {
                // 无间隔模式：保持原有的队列式连续播放，确保无缝衔接
                LogUtils.d(TAG, "朗读列表大小 ${contentList.size}")
                LogUtils.d(TAG, "朗读页数 ${readerReadAloudChapter?.pageCount}")
                val session = ++speakSession
                val tts = textToSpeech ?: throw NoStackTraceException("tts is null")
                val contentList = contentList
                var isAddedText = false
                for (i in nowSpeak until contentList.size) {
                    ensureActive()
                    val storedStart = if (i == nowSpeak) paragraphStartPos else 0
                    val storedTail = if (storedStart > 0) {
                        contentList[i].substring(storedStart)
                    } else {
                        contentList[i]
                    }
                    val utteranceId = ttsUtteranceId(AppConst.APP_TAG, session, i)
                    val text = speakable(storedTail, utteranceId, storedStart).text
                    if (text.isEmpty() || text.matches(AppPattern.notReadAloudRegex)) {
                        pendingUtterances.remove(utteranceId)
                        continue
                    }
                    if (!isAddedText) {
                        val result = tts.runCatching {
                            speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                        }.getOrElse {
                            AppLog.put("tts出错\n${it.localizedMessage}", it, true)
                            TextToSpeech.ERROR
                        }
                        if (result == TextToSpeech.ERROR) {
                            AppLog.put("tts出错 尝试重新初始化")
                            clearTTS()
                            initTts()
                            return@execute
                        }
                    } else {
                        val result = tts.runCatching {
                            speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                        }.getOrElse {
                            AppLog.put("tts出错\n${it.localizedMessage}", it, true)
                            TextToSpeech.ERROR
                        }
                        if (result == TextToSpeech.ERROR) {
                            AppLog.put("tts朗读出错:$text")
                        }
                    }
                    isAddedText = true
                }
                LogUtils.d(TAG, "朗读内容添加完成")
                if (!isAddedText) {
                    // 本协程仍需执行延迟后的章末处理，不能通过 playStop 取消自身。
                    speakSession++
                    needParagraphInterval = false
                    tts.runCatching { stop() }
                    delay(1000)
                    completeCurrentChapter()
                }
            }
        }.onError {
            AppLog.putDebug("TTS协程异常: ${it.localizedMessage}")
        }
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
    ): CastMarkers.Spoken {
        // 一次入队整章时表会跟着涨，引擎不再回调的条目没人清理，换会话就整体作废
        if (pendingUtterances.size > MAX_PENDING_UTTERANCES) pendingUtterances.clear()
        val spoken = spokenFor(storedTail, base)
        pendingUtterances[utteranceId] = spoken
        lastRangeEnd = 0
        sawRangeProgress = false
        return spoken
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

    private fun systemVoiceForCurrentCue(): ReadAloudVoice {
        val configured = GSON.fromJsonObject<SelectItem<String>>(ReadAloud.ttsEngine)
            .getOrNull()?.value.orEmpty()
        val fallback = ReadAloudVoice(
            id = "runtime-system:$configured",
            engineType = ReadAloudVoice.ENGINE_SYSTEM,
            engineId = configured,
            speakerId = "",
            displayName = configured,
        )
        val cue = playbackQueue.cues.getOrNull(nowSpeak) ?: return fallback
        return SpeechVoiceRouter.route(
            cue = cue,
            supportedEngineTypes = setOf(ReadAloudVoice.ENGINE_SYSTEM),
            defaultRoute = SpeechEngineRoute(ReadAloudVoice.ENGINE_SYSTEM, configured),
        ).voice ?: fallback
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

    private fun applyPreset(voice: ReadAloudVoice) {
        val config = runCatching {
            GSON.fromJson(voice.traitsJson, SystemTtsVoiceConfig::class.java)
        }.getOrNull() ?: SystemTtsVoiceConfig()
        val globalRate = if (ReadConfig.ttsFollowSys) {
            1f
        } else {
            (ReadConfig.ttsSpeechRate + 5) / 10f
        }
        // 角色的变声器：直读路径没有自己的播放器，只能用引擎的音高/语速这一层
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
        val rate = VoiceEffectAudio.speedOf(effect, config.speechRate ?: globalRate)
        val pitch = VoiceEffectAudio.pitchOf(effect, config.pitch ?: 1f)
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

    override fun playStop() {
        speakSession++
        speakJob?.cancel()
        needParagraphInterval = false
        pendingUtterances.clear()
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 更新朗读速度
     */
    override fun upSpeechRate(reset: Boolean) {
        if (ReadConfig.ttsFollowSys) {
            if (reset) {
                clearTTS()
                initTts()
            }
        } else {
            val speechRate = (speechRateSetting + 5) / 10f
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
    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        speakSession++
        speakJob?.cancel()
        textToSpeech?.runCatching {
            stop()
        }
    }

    /**
     * 恢复朗读
     */
    override fun resumeReadAloud() {
        super.resumeReadAloud()
        play()
    }

    override fun onPlaybackStateReplaced() {
        // 换章/重新定位后, 旧章节发言的迟到回调不得再改新队列状态
        speakSession++
        pendingUtterances.clear()
    }

    /**
     * 朗读监听
     */
    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        private val TAG = "TTSUtteranceListener"

        override fun onStart(s: String) {
            if (!isCurrentUtterance(s)) return
            LogUtils.d(TAG, "onStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$s")
            utteranceStartPos = paragraphStartPos
            utteranceStartReadAloudNumber = readAloudNumber
            lastRangeEnd = 0
            sawRangeProgress = false
            if (isChapterTitleAt(nowSpeak)) {
                upMediaMetadata(showContent = true)
                return
            }
            readerReadAloudChapter?.let {
                val position = currentRangePosition(
                    utteranceStartReadAloudNumber, 0, utteranceStartPos
                )
                if (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex)) {
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
                    withSpeechNavigation { ReadBook.moveToNextPage() }
                }
                upTtsProgress(position + 1)
                upMediaMetadata(showContent = true)
            }
        }

        override fun onDone(s: String) {
            if (!isCurrentUtterance(s)) return
            LogUtils.d(TAG, "onDone utteranceId:$s")
            val spoken = pendingUtterances.remove(s)
            if (resumeIfCutShort(spoken, "引擎提前收尾")) return
            // 章节自然完结时本章播放状态已作废, 恢复播放由换章后的 newReadAloud 负责;
            // 若此处仍用旧队列 play(), 会把本章最后一段反复重新入队
            if (!advanceOnce(s, "念完")) return
            val paragraphAdvanced = nextParagraph(naturalCompletion = true)
            if (paragraphAdvanced &&
                !pause &&
                (hasSpeechPlaybackQueue || ReadConfig.ttsParagraphInterval > 0)
            ) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                advancedFromUtteranceDone = true
                play()
            }
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)
            if (!isCurrentUtterance(utteranceId)) return
            val spoken = utteranceId?.let(pendingUtterances::get)
            // 引擎报的是「送进去的那一份文本」的下标：删过标记就得先换算回正文，否则
            // 每一句角色台词的进度、翻页判定和续念偏移都差着标记那段长度
            val storedStart = spoken?.storedOf(start) ?: (utteranceStartPos + start)
            if (spoken != null) {
                sawRangeProgress = true
                lastRangeEnd = maxOf(lastRangeEnd, end.coerceIn(0, spoken.text.length))
            }
            paragraphStartPos = storedStart
            if (isChapterTitleAt(nowSpeak)) return
            // 正在朗读的精确章内位置（段起点 + 段内偏移），保持 readAloudNumber 的"段起点"语义不被污染
            // storedStart 已经把引擎下标换算回正文（含 utteranceStartPos 与被删掉的标记），
            // 上游的 utteranceStartOffset 参数是我们这里的子集，不用再传一遍。
            val position = currentRangePosition(utteranceStartReadAloudNumber, storedStart)
            updateReadAloudProgressSnapshot(position)
            val msg =
                "onRangeStart nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId start:$start end:$end frame:$frame"
            LogUtils.d(TAG, msg)
            if (moveToReadAloudPage(position)) {
                upTtsProgress(position)
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (!isCurrentUtterance(utteranceId)) return
            LogUtils.d(
                TAG,
                "onError nowSpeak:$nowSpeak pageIndex:$pageIndex utteranceId:$utteranceId errorCode:$errorCode"
            )
            handleUtteranceError(utteranceId, "错误码 $errorCode")
        }

        /**
         * 引擎念到一半就收了尾时从半句处接着念，返回是否接管了这一句。
         *
         * 判据只能是引擎自己报的范围：它报过进度、离句尾还差一截，才是被切断而不是念完。
         */
        private fun resumeIfCutShort(spoken: CastMarkers.Spoken?, reason: String): Boolean {
            if (spoken == null) return false
            if (!sawRangeProgress || respeakTries >= MAX_RESPEAK_TRIES) return false
            if (spoken.text.length - lastRangeEnd < MIN_RESPEAK_TAIL) return false
            val resumeFrom = spoken.storedOf(lastRangeEnd)
            if (resumeFrom <= utteranceStartPos) return false
            respeakTries++
            AppLog.put(
                "朗读$reason：这一句差 ${spoken.text.length - lastRangeEnd} 字没收尾，" +
                    "从第 $resumeFrom 字接着念（第 $respeakTries 次）",
            )
            paragraphStartPos = resumeFrom
            if (!pause) play()
            return true
        }

        private fun handleUtteranceError(utteranceId: String?, reason: String) {
            val spoken = utteranceId?.let(pendingUtterances::remove)
            if (resumeIfCutShort(spoken, "引擎报错")) return
            if (!advanceOnce(utteranceId.orEmpty(), "引擎报错($reason)")) return
            val paragraphAdvanced = nextParagraph(naturalCompletion = true)
            if (paragraphAdvanced &&
                !pause &&
                (hasSpeechPlaybackQueue || ReadConfig.ttsParagraphInterval > 0)
            ) {
                needParagraphInterval = ReadConfig.ttsParagraphInterval > 0
                play()
            }
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
            } while (contentList[nowSpeak].matches(AppPattern.notReadAloudRegex))
            // 页内切段不引入换行符，累加会漂移，用段落绝对位置重算
            readAloudNumber = paragraphChapterPositionAt(nowSpeak) ?: 0
            return true
        }

        @Deprecated("Deprecated in Java")
        override fun onError(s: String) {
            if (!isCurrentUtterance(s)) return
            LogUtils.d(TAG, "onError nowSpeak:$nowSpeak pageIndex:$pageIndex s:$s")
            handleUtteranceError(s, "未带错误码")
        }

        private fun isCurrentUtterance(utteranceId: String?): Boolean =
            utteranceId != null &&
                utteranceId == ttsUtteranceId(AppConst.APP_TAG, speakSession, nowSpeak)

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
