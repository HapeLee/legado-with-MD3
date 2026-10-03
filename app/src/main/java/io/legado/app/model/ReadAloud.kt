package io.legado.app.model

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.domain.model.PlaybackTimer
import io.legado.app.domain.model.readaloud.ReadAloudEngineSelection
import io.legado.app.domain.model.readaloud.ReadAloudVoice
import io.legado.app.help.readaloud.effect.VoiceEffectStore
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.service.TTSReadAloudService
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.runBlocking
import splitties.init.appCtx

object ReadAloud {
    private val aloudSettingsGateway get() = org.koin.core.context.GlobalContext.get().get<io.legado.app.domain.gateway.ReadAloudSettingsGateway>()
    private var aloudClass: Class<*> = getReadAloudClass()
    val ttsEngine get() = ReadBook.book?.getTtsEngine() ?: aloudSettingsGateway.currentSettings.ttsEngine
    var httpTTS: HttpTTS? = null
    var coordinatorDefaultEngineType: String = ReadAloudVoice.ENGINE_SYSTEM
        private set
    var coordinatorDefaultEngineId: String = ""
        private set
    var coordinatorDefaultSpeakerId: String = ""
        private set

    /**
     * 变声器的混响 / 金属感这一层现在能不能生效。
     *
     * 它们是挂在朗读播放器**自己的音频会话**上的平台 AudioEffect：只有走文件合成 +
     * ExoPlayer 的网络/云端路径才有我们持有的播放器。系统 TTS 直读时音频是引擎自己开的
     * AudioTrack，平台不把会话号给外部应用，那一层只能整个丢掉（音高/语速照旧有效）。
     * 管理页拿它来标「当前引擎不生效」，免得用户以为预设坏了。
     */
    val supportsSessionAudioEffect: Boolean
        get() = aloudClass != TTSReadAloudService::class.java

    private fun getReadAloudClass(): Class<*> {        val ttsEngine = ttsEngine
        GSON.fromJsonObject<ReadAloudEngineSelection>(ttsEngine).getOrNull()
            ?.takeIf { it.engineType == ReadAloudVoice.ENGINE_CLOUD }
            ?.let { selection ->
                coordinatorDefaultEngineType = selection.engineType
                coordinatorDefaultEngineId = selection.engineId
                coordinatorDefaultSpeakerId = selection.speakerId
                httpTTS = HttpTTS(
                    id = Long.MIN_VALUE,
                    name = selection.displayName.ifBlank { "Cloud TTS" })
                return HttpReadAloudService::class.java
            }
        if (ttsEngine.isNullOrBlank()) {
            setSystemCoordinatorDefault(ttsEngine)
            findCoordinatorHttpSeed()?.let {
                httpTTS = it
                return HttpReadAloudService::class.java
            }
            return TTSReadAloudService::class.java
        }
        if (StringUtils.isNumeric(ttsEngine)) {
            httpTTS = appDb.httpTTSDao.get(ttsEngine.toLong())
            if (httpTTS != null) {
                coordinatorDefaultEngineType = ReadAloudVoice.ENGINE_HTTP
                coordinatorDefaultEngineId = ttsEngine
                coordinatorDefaultSpeakerId = ""
                return HttpReadAloudService::class.java
            }
        }
        setSystemCoordinatorDefault(ttsEngine)
        findCoordinatorHttpSeed()?.let {
            httpTTS = it
            return HttpReadAloudService::class.java
        }
        return TTSReadAloudService::class.java
    }

    private fun setSystemCoordinatorDefault(serializedEngine: String?) {
        coordinatorDefaultEngineType = ReadAloudVoice.ENGINE_SYSTEM
        coordinatorDefaultEngineId = GSON.fromJsonObject<SelectItem<String>>(serializedEngine)
            .getOrNull()?.value.orEmpty()
        coordinatorDefaultSpeakerId = ""
    }

    private fun findCoordinatorHttpSeed(): HttpTTS? {
        val settings = aloudSettingsGateway.currentSettings
        // 「多角色朗读」开关决定用不用角色音，也就决定能不能留在系统 TTS 直读：
        // 角色音里有 HTTP/云端音色时只有文件合成那条服务能发声，系统直读会把它们过滤掉。
        // 「多角色分配」只是正文胶囊与手动分配的入口，不该改变引擎——关掉多角色朗读就必须
        // 回到用户在朗读设置里选的默认引擎。
        if (!settings.useMultiSpeaker) return null
        return runCatching {
            val bookUrl = ReadBook.book?.bookUrl ?: return@runCatching null
            val (httpVoices, systemRoutes) = runBlocking {
                val voiceIds = appDb.readAloudVoiceDao.getBindings(bookUrl)
                    .mapTo(hashSetOf()) { it.voiceId }
                // 分配表里的角色音是直接记在角色身上的（没进过配音页就没有绑定行），
                // 只看 book_voice_bindings 会漏掉它们：整章退回系统直读、角色音被过滤成旁白。
                appDb.castCharacterDao.getByBook(bookUrl).forEach {
                    if (it.voiceId.isNotBlank()) voiceIds += it.voiceId
                }
                val voices = appDb.readAloudVoiceDao.getVoices().filter {
                    it.id in voiceIds && it.enabled && it.available
                }
                voices.filter {
                    it.engineType == ReadAloudVoice.ENGINE_HTTP ||
                        it.engineType == ReadAloudVoice.ENGINE_CLOUD
                } to voices.filter { it.engineType == ReadAloudVoice.ENGINE_SYSTEM }
                    .mapTo(hashSetOf()) { it.engineId to it.speakerId }
            }
            // 全系统音色也要升级的两个理由：
            // 1) 变声器的混响/金属感挂在**我们持有播放器的**那条音频会话上，直读时音频是引擎
            //    自己的 AudioTrack，会话号不给外部应用（试听之所以有效果，就是因为它是文件合成
            //    + 我们的 ExoPlayer）。
            // 2) 一句旁白一句角色音地换音色——直读换音色要 setVoice，而这类引擎的 onDone 比耳朵
            //    提前好几秒（长句是「音频写完」就报完成，缓冲里还剩两三秒没放出去），这一次
            //    调用正好把上一句的尾巴冲掉：角色长句读到一半跳下一句就是这么来的。文件合成 +
            //    自己的播放器按真实播放结束翻页，句界不再听引擎的回调。
            val needsSessionEffect = hasSessionLayerEffect(bookUrl)
            if (httpVoices.isEmpty() && systemRoutes.size < 2 && !needsSessionEffect) {
                return@runCatching null
            }
            AppLog.put(
                "多角色朗读: 升级文件合成 系统音色 ${systemRoutes.size} 路 " +
                    "HTTP/云端音色 ${httpVoices.size} 个 会话级变声=$needsSessionEffect",
            )
            httpVoices.firstOrNull { it.engineType == ReadAloudVoice.ENGINE_HTTP }
                ?.engineId?.toLongOrNull()?.let(appDb.httpTTSDao::get)
                ?: appDb.httpTTSDao.all.firstOrNull()
                ?: HttpTTS(id = Long.MIN_VALUE, name = "TTS coordinator")
        }.getOrNull()
    }

    /** 这本书里有没有哪条音色预设需要会话级效果（角色全局、正文胶囊那一段、或正则角色那条规则自己设的）。 */
    private fun hasSessionLayerEffect(bookUrl: String): Boolean = runBlocking {
        val wanted = appDb.voiceEffectDao.getAll()
            .filter { it.enabled }
            .filter { VoiceEffectStore.needsSessionEffect(it) }
            .mapTo(hashSetOf()) { it.name }
        if (wanted.isEmpty()) return@runBlocking false
        if (appDb.castCharacterDao.getByBook(bookUrl).any { it.voiceEffect in wanted }) {
            return@runBlocking true
        }
        if (appDb.chapterRoleAssignmentDao.getForBook(bookUrl).any { it.voiceEffect in wanted }) {
            return@runBlocking true
        }
        // 正则角色那一列（regex_cast_rules.voiceEffect）的产出方是 RegexCastRuleScreen 的变声器那行，
        // 消费方是 CastSpeechOverlay → 朗读单元；混响/金属感同样只有文件合成那条路挂得上。
        appDb.regexCastRuleDao.all().any { it.enabled && it.voiceEffect in wanted }
    }

    fun upReadAloudClass() {
        stop(appCtx)
        aloudClass = getReadAloudClass()
    }

    /** Re-evaluates the configured engine after the current service has stopped. */
    fun refreshReadAloudClass() {
        aloudClass = getReadAloudClass()
    }

    fun play(
        context: Context,
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0,
        /** Absolute position in the processed chapter; preferred by the Canvas reader. */
        chapterPosition: Int? = null,
    ) {
        if (!BaseReadAloudService.isRun) {
            aloudClass = getReadAloudClass()
        }
        // 角色音听不见的第一个分岔口就在这里：系统直读那条服务只认自己这类音色。
        run {
            val settings = aloudSettingsGateway.currentSettings
            AppLog.put(
                "多角色朗读: 走${aloudClass.simpleName} 多角色朗读=${settings.useMultiSpeaker} " +
                    "多角色分配=${settings.multiRoleCast} 合成服务=${httpTTS?.name ?: "无"}",
            )
        }
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        intent.putExtra("play", play)
        intent.putExtra("pageIndex", pageIndex)
        intent.putExtra("startPos", startPos)
        chapterPosition?.let { intent.putExtra("chapterPosition", it) }
        LogUtils.d("ReadAloud", intent.toString())
        try {
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            val msg = "启动朗读服务出错\n${e.localizedMessage}"
            AppLog.put(msg, e)
            context.toastOnUi(msg)
        }
    }

    fun playByEventBus(
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0,
        chapterPosition: Int? = null,
    ) {
        val bundle = Bundle().apply {
            putBoolean("play", play)
            putInt("pageIndex", pageIndex)
            putInt("startPos", startPos)
            chapterPosition?.let { putInt("chapterPosition", it) }
        }
        postEvent(EventBus.READ_ALOUD_PLAY, bundle)
    }

    fun pause(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.pause
            context.startForegroundServiceCompat(intent)
        }
    }

    fun resume(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.resume
            context.startForegroundServiceCompat(intent)
        }
    }

    fun stop(context: Context) {
        if (BaseReadAloudService.requestStop()) {
            // Stopping an already-running service must not issue a new foreground-start request.
            // If its last start is still pending, the stop action could otherwise finish the
            // service without ever calling startForeground(), crashing the process on timeout.
            context.stopService(Intent(context, aloudClass))
        }
    }

    fun prevParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prevParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun nextParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.nextParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun upTtsSpeechRate(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.upTtsSpeechRate
            context.startForegroundServiceCompat(intent)
        }
    }

    fun syncLayout(context: Context = appCtx) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.syncReadAloudLayout
            context.startForegroundServiceCompat(intent)
        }
    }

    /**
     * 分配/变声器改动后让朗读听见：只重排队列，不重开服务。
     *
     * 只发一种 action：这里绝不能顺手 `refreshReadAloudClass()`——那会把 aloudClass 换成
     * 当前设置对应的服务类，而停止旧服务时用的正是 aloudClass。一旦这两者不同，
     * stop 意图发给一个根本没在跑的类，朗读就永远停不下来（配乐清掉的正是这条链路）。
     */
    fun refreshCastQueue(context: Context = appCtx) {
        if (!BaseReadAloudService.isRun) return
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.refreshReadAloudCast
        context.startForegroundServiceCompat(intent)
    }

    /**
     * 听书音频下载：只走文件合成那条服务，因为只有它能把每句落成文件。
     *
     * 下载和朗读共用同一个服务实例时才不冲突；正在用系统直读朗读时另起一个
     * HttpReadAloudService 会和服务通知号撞车，所以那种情况直接拒绝，不做绕行。
     */
    fun downloadAudio(context: Context, bookUrl: String, startChapter: Int, endChapter: Int) {
        if (BaseReadAloudService.isRun && aloudClass != HttpReadAloudService::class.java) {
            context.toastOnUi("正在用系统直读朗读，请先停止朗读再下载音频")
            return
        }
        val intent = Intent(context, HttpReadAloudService::class.java)
        intent.action = IntentAction.downloadReadAloudAudio
        intent.putExtra("bookUrl", bookUrl)
        intent.putExtra("startChapter", startChapter)
        intent.putExtra("endChapter", endChapter)
        startServiceIntent(context, intent, "启动听书下载")
    }

    fun cancelDownloadAudio(context: Context) {
        if (!BaseReadAloudService.isRun) return
        val intent = Intent(context, HttpReadAloudService::class.java)
        intent.action = IntentAction.cancelDownloadReadAloudAudio
        startServiceIntent(context, intent, "取消听书下载")
    }

    private fun startServiceIntent(context: Context, intent: Intent, label: String) {
        try {
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            val msg = "${label}出错\n${e.localizedMessage}"
            AppLog.put(msg, e)
            context.toastOnUi(msg)
        }
    }

    fun setTimer(context: Context, minute: Int) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", PlaybackTimer.normalize(minute))
            context.startForegroundServiceCompat(intent)
        }
    }

    /** 章节定时剩余章数；0 关闭。走独立 action，避免清掉分钟倒计时。 */
    fun setTimerChapters(context: Context, chapters: Int) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimerChapters
            intent.putExtra("chapters", PlaybackTimer.normalizeChapters(chapters))
            context.startForegroundServiceCompat(intent)
        }
    }

}
