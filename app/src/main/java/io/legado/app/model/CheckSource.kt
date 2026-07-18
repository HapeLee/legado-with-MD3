package io.legado.app.model

import android.content.Context
import io.legado.app.R
import io.legado.app.constant.IntentAction
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.domain.gateway.CheckSourceSettingsGateway
import io.legado.app.help.IntentData
import io.legado.app.service.CheckSourceService
import io.legado.app.utils.startService
import org.koin.core.context.GlobalContext
import splitties.init.appCtx

object CheckSource {
    var keyword = "我的"

    private val settingsGateway: CheckSourceSettingsGateway
        get() = GlobalContext.get().get()
    private val settings get() = settingsGateway.currentSettings

    // Legacy services keep synchronous reads; writes are owned by CheckSourceSettingsGateway.
    val timeout get() = settings.timeoutMillis
    val checkSearch get() = settings.checkSearch
    val checkDiscovery get() = settings.checkDiscovery
    val checkInfo get() = settings.checkInfo
    val checkCategory get() = settings.checkCategory
    val checkContent get() = settings.checkContent
    val summary get() = upSummary()

    fun start(context: Context, sources: List<BookSourcePart>) {
        val selectedIds = sources.map {
            it.bookSourceUrl
        }
        IntentData.put("checkSourceSelectedIds", selectedIds)
        context.startService<CheckSourceService> {
            action = IntentAction.start
        }
    }

    fun stop(context: Context) {
        context.startService<CheckSourceService> {
            action = IntentAction.stop
        }
    }

    fun resume(context: Context) {
        context.startService<CheckSourceService> {
            action = IntentAction.resume
        }
    }

    private fun upSummary(): String {
        var checkItem = ""
        if (checkSearch) checkItem = "$checkItem ${appCtx.getString(R.string.search)}"
        if (checkDiscovery) checkItem = "$checkItem ${appCtx.getString(R.string.discovery)}"
        if (checkInfo) checkItem = "$checkItem ${appCtx.getString(R.string.source_tab_info)}"
        if (checkCategory) checkItem = "$checkItem ${appCtx.getString(R.string.chapter_list)}"
        if (checkContent) checkItem = "$checkItem ${appCtx.getString(R.string.main_body)}"
        return appCtx.getString(
            R.string.check_source_config_summary,
            (timeout / 1000).toString(),
            checkItem
        )
    }
}
