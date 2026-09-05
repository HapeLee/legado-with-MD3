package io.legado.app.domain.model.settings

data class ImportBookSettings(
    val importBookPath: String? = null,
    val bookImportFileName: String? = null,
    val localBookImportSort: Int = 0,
    val remoteServerId: Long = 0L,
)
