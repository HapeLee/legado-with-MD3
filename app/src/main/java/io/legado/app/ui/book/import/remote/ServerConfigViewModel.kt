package io.legado.app.ui.book.import.remote

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Server
import io.legado.app.data.repository.RemoteBookRepository
import io.legado.app.utils.toastOnUi

class ServerConfigViewModel(
    application: Application,
    private val remoteBookRepository: RemoteBookRepository
) : BaseViewModel(application) {

    var mServer: Server? = null

    fun init(id: Long?, onSuccess: () -> Unit) {
        //mServer不为空可能是旋转屏幕界面重新创建,不用更新数据
        if (mServer != null) return
        execute {
            mServer = if (id != null) {
                remoteBookRepository.getServer(id)
            } else {
                Server()
            }
        }.onSuccess {
            onSuccess.invoke()
        }
    }

    fun save(server: Server, onSuccess: () -> Unit) {
        execute {
            mServer?.let {
                remoteBookRepository.deleteServer(it)
            }
            mServer = server
            remoteBookRepository.saveServer(server)
        }.onSuccess {
            onSuccess.invoke()
        }.onError {
            context.toastOnUi("保存出错\n${it.localizedMessage}")
        }
    }

}
