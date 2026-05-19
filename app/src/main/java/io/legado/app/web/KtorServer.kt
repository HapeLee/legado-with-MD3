package io.legado.app.web

import android.graphics.Bitmap
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import io.legado.app.api.ReturnData
import io.legado.app.api.controller.BookController
import io.legado.app.api.controller.BookSourceController
import io.legado.app.api.controller.ReplaceRuleController
import io.legado.app.api.controller.RssSourceController
import io.legado.app.model.localBook.LocalBook
import io.legado.app.service.WebService
import io.legado.app.utils.LogUtils
import io.legado.app.utils.stackTraceStr
import io.legado.app.web.socket.BookSearchWebSocket
import io.legado.app.web.socket.BookSourceDebugWebSocket
import io.legado.app.web.socket.RssSourceDebugWebSocket
import io.legado.app.web.utils.AssetsWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class KtorServer(private val port: Int) {
    private var server: ApplicationEngine? = null
    private var wsServer: ApplicationEngine? = null
    private val assetsWeb = AssetsWeb("web")

    fun start() {
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                gson {
                    setLenient()
                }
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }

            routing {
                post("/saveBookSource") { handlePost { BookSourceController.saveSource(it) } }
                post("/saveBookSources") { handlePost { BookSourceController.saveSources(it) } }
                post("/deleteBookSources") { handlePost { BookSourceController.deleteSources(it) } }
                post("/saveBook") { handlePost { BookController.saveBook(it) } }
                post("/deleteBook") { handlePost { BookController.deleteBook(it) } }
                post("/saveBookProgress") { handlePost { BookController.saveBookProgress(it) } }
                post("/addLocalBook") {
                    WebService.serve()
                    val multipart = call.receiveMultipart()
                    var fileName: String? = null
                    var fileBytes: ByteArray? = null
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "fileName") fileName = part.value
                            }
                            is PartData.FileItem -> {
                                fileBytes = part.streamProvider().readBytes()
                            }
                            else -> {}
                        }
                        part.dispose()
                    }
                    if (fileName != null && fileBytes != null) {
                        val returnData = withContext(Dispatchers.IO) {
                            kotlin.runCatching {
                                val uri = LocalBook.saveBookFile(fileBytes!!.inputStream(), fileName!!)
                                LocalBook.importFile(uri)
                                ReturnData().setData(true)
                            }.getOrElse {
                                ReturnData().setErrorMsg(it.localizedMessage ?: "Save book error")
                            }
                        }
                        respondReturnData(returnData)
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Missing fileName or fileData")
                    }
                }
                post("/saveReadConfig") { handlePost { BookController.saveWebReadConfig(it) } }
                post("/saveRssSource") { handlePost { RssSourceController.saveSource(it) } }
                post("/saveRssSources") { handlePost { RssSourceController.saveSources(it) } }
                post("/deleteRssSources") { handlePost { RssSourceController.deleteSources(it) } }
                post("/saveReplaceRule") { handlePost { ReplaceRuleController.saveRule(it) } }
                post("/deleteReplaceRule") { handlePost { ReplaceRuleController.delete(it) } }
                post("/testReplaceRule") { handlePost { ReplaceRuleController.testRule(it) } }

                get("/getBookSource") { handleGet { BookSourceController.getSource(it) } }
                get("/getBookSources") { handleGet { BookSourceController.sources } }
                get("/getBookshelf") { handleGet { BookController.bookshelf } }
                get("/getChapterList") { handleGet { BookController.getChapterList(it) } }
                get("/refreshToc") { handleGet { BookController.refreshToc(it) } }
                get("/getBookContent") { handleGet { BookController.getBookContent(it) } }
                get("/cover") { handleGet { BookController.getCover(it) } }
                get("/image") { handleGet { BookController.getImg(it) } }
                get("/getReadConfig") { handleGet { BookController.getWebReadConfig() } }
                get("/getRssSource") { handleGet { RssSourceController.getSource(it) } }
                get("/getRssSources") { handleGet { RssSourceController.sources } }
                get("/getReplaceRules") { handleGet { ReplaceRuleController.allRules } }

                get("{...}") {
                    WebService.serve()
                    var uri = call.request.uri.substringBefore("?")
                    if (uri.endsWith("/")) uri += "index.html"
                    val inputStream = assetsWeb.getInputStream(uri)
                    if (inputStream != null) {
                        call.respondOutputStream(ContentType.parse(assetsWeb.getMimeType(uri))) {
                            inputStream.copyTo(this)
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
    }

    fun startWebSocket(wsPort: Int) {
        wsServer = embeddedServer(CIO, port = wsPort) {
            install(WebSockets)
            routing {
                webSocket("/bookSourceDebug") {
                    BookSourceDebugWebSocket(this).handle()
                }
                webSocket("/rssSourceDebug") {
                    RssSourceDebugWebSocket(this).handle()
                }
                webSocket("/searchBook") {
                    BookSearchWebSocket(this).handle()
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 1000)
        wsServer?.stop(1000, 1000)
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handlePost(
        block: suspend (String?) -> ReturnData
    ) {
        WebService.serve()
        try {
            val postData = call.receiveText()
            val returnData = block(postData)
            respondReturnData(returnData)
        } catch (e: Exception) {
            LogUtils.e(TAG, e.stackTraceStr)
            call.respondText(e.message ?: "Unknown error")
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.handleGet(
        block: (Map<String, List<String>>) -> ReturnData?
    ) {
        WebService.serve()
        try {
            val parameters = call.request.queryParameters.toMap()
            val returnData = block(parameters)
            if (returnData != null) {
                respondReturnData(returnData)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, e.stackTraceStr)
            call.respondText(e.message ?: "Unknown error")
        }
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.respondReturnData(returnData: ReturnData) {
        if (returnData.data is Bitmap) {
            val bitmap = returnData.data as Bitmap
            val outputStream = ByteArrayOutputStream()
            withContext(Dispatchers.IO) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            call.respondBytes(outputStream.toByteArray(), ContentType.Image.PNG)
        } else {
            call.respond(returnData)
        }
    }

    private fun Parameters.toMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        this.forEach { s, list -> map[s] = list }
        return map
    }

    companion object {
        private const val TAG = "KtorServer"
    }
}
