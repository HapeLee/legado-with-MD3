package io.legado.app.ui.book.import.remote

import android.app.Application
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.Server
import io.legado.app.data.repository.RemoteBookRepository

class ServersViewModel(
    application: Application,
    private val remoteBookRepository: RemoteBookRepository
) : BaseViewModel(application) {

    fun flowServers() = remoteBookRepository.flowServers()

    fun delete(server: Server) {
        execute {
            remoteBookRepository.deleteServer(server)
        }
    }

}
