package io.legado.app.data.repository.manga

import android.app.Application
import io.legado.app.domain.gateway.MangaModelGateway
import io.legado.app.domain.gateway.MangaModelItem
import io.legado.app.domain.gateway.MangaModelSource
import io.legado.app.domain.gateway.MangaModelStatus
import io.legado.app.domain.gateway.MangaTranslationGateway
import io.legado.app.domain.gateway.MangaSettingsGateway
import io.legado.app.domain.model.settings.MangaTranslationBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class MangaModelSpec(val role: String, val name: String, val url: String,
                                   val size: Long, val sha256: String)

/** Shared with inference. The catalog is pinned to the engine revision, never fetched at runtime. */
internal class MangaModelFiles(application: Application) {
    val directory = File(application.filesDir, "manga_translation/models")
    val lock = locks.getOrPut(directory.absolutePath) { Mutex() }
    val catalog: List<MangaModelSpec> by lazy {
        fun read(asset: String): List<MangaModelSpec> {
            val array = application.assets.open(asset).bufferedReader().use {
                JSONObject(it.readText()).getJSONArray("models")
            }
            return List(array.length()) { index -> array.getJSONObject(index).let {
                MangaModelSpec(it.getString("role"), it.getString("name"), it.getString("url"),
                    it.getLong("size"), it.getString("sha256"))
            } }
        }
        read("models/manga-models.json") + read("models/small100-models.json")
    }
    fun valid(spec: MangaModelSpec): Boolean {
        val file = File(directory, spec.name)
        if (!file.isFile || file.length() != spec.size) return false
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}:${spec.sha256}"
        return verified.getOrPut(key) { modelDigest(file) == spec.sha256 }
    }
    fun missing(backend: MangaTranslationBackend = MangaTranslationBackend.AI) = catalog.filter {
        (it.role != "translator" || backend == MangaTranslationBackend.LOCAL_SMALL100) && !valid(it)
    }
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val verified = ConcurrentHashMap<String, Boolean>()
    }
}

internal fun modelDigest(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** Validation and publication are separate: an invalid/cancelled import never replaces a good model. */
internal suspend fun installMangaModel(directory: File, spec: MangaModelSpec,
                                       source: MangaModelSource, progress: (Int) -> Unit) {
    directory.mkdirs()
    val temporary = File.createTempFile("model-", ".part", directory)
    try {
        val context = currentCoroutineContext()
        var bytes = 0L
        temporary.outputStream().use { output ->
            source.transfer { buffer, count ->
                context.ensureActive()
                require(count in 0..buffer.size && bytes + count <= spec.size) { "模型大小不匹配" }
                output.write(buffer, 0, count)
                bytes += count
                progress((bytes * 100 / spec.size).toInt())
            }
            output.fd.sync()
        }
        context.ensureActive()
        require(bytes == spec.size && modelDigest(temporary) == spec.sha256) { "模型大小或 SHA-256 不匹配" }
        context.ensureActive()
        Files.move(temporary.toPath(), File(directory, spec.name).toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { temporary.delete() }
}

class MangaModelRepository(application: Application, private val translation: MangaTranslationGateway,
                           private val settings: MangaSettingsGateway) : MangaModelGateway {
    private val files = MangaModelFiles(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()
    private val _models = MutableStateFlow(files.catalog.map { MangaModelItem(it.name, it.role, it.size) })
    override val models = _models.asStateFlow()
    private val _busy = MutableStateFlow(false)
    override val busy = _busy.asStateFlow()
    private val _importErrors = MutableStateFlow<List<String>>(emptyList())
    override val importErrors = _importErrors.asStateFlow()
    private var operation: Job? = null
    init { refresh() }

    private fun update(name: String, transform: (MangaModelItem) -> MangaModelItem) {
        _models.update { items -> items.map { if (it.name == name) transform(it) else it } }
    }
    private fun scan() {
        files.catalog.forEach { spec ->
            val valid = files.valid(spec)
            update(spec.name) { it.copy(installed = valid, progress = null,
                status = if (it.error != null) MangaModelStatus.FAILED
                    else if (valid) MangaModelStatus.READY else MangaModelStatus.MISSING) }
        }
    }
    override fun refresh() {
        scope.launch { files.lock.withLock { if (!_busy.value) scan() } }
    }
    @Synchronized
    private fun launchOperation(action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _importErrors.value = emptyList()
        operation = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _importErrors.update { it + (error.localizedMessage ?: "模型操作失败") } }
            finally {
                // Non-suspending scan is safe: all mutation blocks have released the files lock.
                scan()
                _busy.value = false
            }
        }
    }
    private suspend fun install(spec: MangaModelSpec, source: MangaModelSource, status: MangaModelStatus) {
        update(spec.name) { it.copy(status = status, progress = 0, error = null) }
        try {
            installMangaModel(files.directory, spec, source) { percent ->
                update(spec.name) { it.copy(progress = percent, status = if (percent == 100) MangaModelStatus.VERIFYING else status) }
            }
            update(spec.name) { it.copy(installed = true, progress = null, status = MangaModelStatus.READY) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            update(spec.name) { it.copy(error = error.localizedMessage ?: "模型操作失败") }
            _importErrors.update { it + "${spec.name}: ${error.localizedMessage}" }
        }
    }
    override fun download(name: String?) = launchOperation {
        translation.withModelsPaused {
            files.lock.withLock {
                files.catalog.filter { spec ->
                    val selected = when (name) {
                        SMALL100_GROUP -> spec.role == "translator"
                        null -> spec.role != "translator" || settings.currentSettings.translationBackend == MangaTranslationBackend.LOCAL_SMALL100
                        else -> spec.name == name
                    }
                    selected && !files.valid(spec)
                }.forEach { spec ->
                    install(spec, object : MangaModelSource {
                        override val name = spec.name
                        override suspend fun transfer(write: (ByteArray, Int) -> Unit) {
                            val call = client.newCall(Request.Builder().url(spec.url).build())
                            coroutineScope {
                                // A child observes cancellation while execute/read are blocking on IO.
                                val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                                    try { awaitCancellation() } finally { call.cancel() }
                                }
                                try {
                                    call.execute().use { response ->
                                        check(response.isSuccessful) { "HTTP ${response.code}" }
                                        response.body.byteStream().use { input ->
                                            val buffer = ByteArray(65536)
                                            while (true) {
                                                currentCoroutineContext().ensureActive()
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                write(buffer, count)
                                            }
                                        }
                                    }
                                } finally { cancellation.cancel() }
                            }
                        }
                    }, MangaModelStatus.DOWNLOADING)
                }
            }
        }
    }
    override fun importModels(sources: List<MangaModelSource>) = launchOperation {
        translation.withModelsPaused {
            files.lock.withLock {
                sources.forEach { source ->
                    val spec = files.catalog.firstOrNull { it.name == source.name }
                    if (spec == null) _importErrors.update { it + "${source.name}: 不在官方兼容清单中" }
                    else install(spec, source, MangaModelStatus.IMPORTING)
                }
            }
        }
    }
    override fun delete(name: String) = launchOperation {
        val specs = files.catalog.filter { it.name == name || (name == SMALL100_GROUP && it.role == "translator") }
        specs.forEach { spec -> update(spec.name) { it.copy(status = MangaModelStatus.DELETING, error = null) } }
        translation.withModelsPaused {
            files.lock.withLock {
                specs.forEach { spec ->
                    val target = File(files.directory, spec.name)
                    check(!target.exists() || target.delete()) { "无法删除模型：${spec.name}" }
                }
            }
        }
    }
    override fun cancel() { operation?.cancel() }
    companion object { const val SMALL100_GROUP = "SMaLL-100" }
}
