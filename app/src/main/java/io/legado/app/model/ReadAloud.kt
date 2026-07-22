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
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.service.TTSReadAloudService
import io.legado.app.ui.config.readConfig.ReadConfig
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
    private var aloudClass: Class<*> = getReadAloudClass()
    val ttsEngine get() = ReadBook.book?.getTtsEngine() ?: ReadConfig.ttsEngine
    var httpTTS: HttpTTS? = null
    var coordinatorDefaultEngineType: String = ReadAloudVoice.ENGINE_SYSTEM
        private set
    var coordinatorDefaultEngineId: String = ""
        private set
    var coordinatorDefaultSpeakerId: String = ""
        private set

    private fun getReadAloudClass(): Class<*> {
        val ttsEngine = ttsEngine
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
        if (!ReadConfig.useMultiSpeaker) return null
        return runCatching {
            val bookUrl = ReadBook.book?.bookUrl ?: return@runCatching null
            val boundVoices = runBlocking {
                val voiceIds = appDb.readAloudVoiceDao.getBindings(bookUrl)
                    .mapTo(hashSetOf()) { it.voiceId }
                appDb.readAloudVoiceDao.getVoices().filter {
                    it.id in voiceIds && it.engineType in setOf(
                        ReadAloudVoice.ENGINE_HTTP,
                        ReadAloudVoice.ENGINE_CLOUD,
                    ) &&
                        it.enabled && it.available
                }
            }
            if (boundVoices.isEmpty()) return@runCatching null
            boundVoices.firstOrNull { it.engineType == ReadAloudVoice.ENGINE_HTTP }
                ?.engineId?.toLongOrNull()?.let(appDb.httpTTSDao::get)
                ?: appDb.httpTTSDao.all.firstOrNull()
                ?: HttpTTS(id = Long.MIN_VALUE, name = "TTS coordinator")
        }.getOrNull()
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
        startPos: Int = 0
    ) {
        if (!BaseReadAloudService.isRun) {
            aloudClass = getReadAloudClass()
        }
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        intent.putExtra("play", play)
        intent.putExtra("pageIndex", pageIndex)
        intent.putExtra("startPos", startPos)
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
        startPos: Int = 0
    ) {
        val bundle = Bundle().apply {
            putBoolean("play", play)
            putInt("pageIndex", pageIndex)
            putInt("startPos", startPos)
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
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.stop
            context.startForegroundServiceCompat(intent)
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

    fun setTimer(context: Context, minute: Int) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", PlaybackTimer.normalize(minute))
            context.startForegroundServiceCompat(intent)
        }
    }

}
