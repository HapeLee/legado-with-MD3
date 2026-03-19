package io.legado.app.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.help.AppWebDav
import io.legado.app.help.DefaultData
import io.legado.app.ui.main.my.PrefClickEvent
import io.legado.app.utils.eventBus.FlowEventBus
import io.legado.app.utils.sendToClip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val upBooksCount: Int = 0
)

class MainViewModel(application: Application) : BaseViewModel(application) {

    private val upBooksCountFlow = MutableStateFlow(0)

    val state: StateFlow<MainUiState> = upBooksCountFlow
        .map { count -> MainUiState(count) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainUiState())

    init {
        viewModelScope.launch {
            FlowEventBus.with<Int>(EventBus.UP_BOOKSHELF_COUNT).collect {
                upBooksCountFlow.value = it
            }
        }
        deleteNotShelfBook()
    }

    fun upAllBookToc() {
        FlowEventBus.post(EventBus.UP_ALL_BOOK_TOC, Unit)
    }

    fun postLoad() {
        execute {
            if (appDb.httpTTSDao.count == 0) {
                DefaultData.httpTTS.let {
                    appDb.httpTTSDao.insert(*it.toTypedArray())
                }
            }
        }
    }

    fun restoreWebDav(name: String) {
        execute {
            AppWebDav.restoreWebDav(name)
        }
    }

    private fun deleteNotShelfBook() {
        execute {
            appDb.bookDao.deleteNotShelfBook()
        }
    }

    fun onPrefClickEvent(context: Context, event: PrefClickEvent) {
        when (event) {
            is PrefClickEvent.OpenUrl -> context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(event.url)
                )
            )

            is PrefClickEvent.CopyUrl -> context.sendToClip(event.url)
            is PrefClickEvent.ShowMd -> {
                // Handle showing MD dialog
            }

            is PrefClickEvent.StartActivity -> {
                context.startActivity(Intent(context, event.destination).apply {
                    event.configTag?.let { putExtra("configTag", it) }
                })
            }

            PrefClickEvent.ExitApp -> {
                if (context is androidx.activity.ComponentActivity) {
                    context.finish()
                }
            }

            else -> Unit
        }
    }

}
