package io.legado.app.help

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.legado.app.constant.AppLog
import io.legado.app.utils.FileDoc
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.treeChildren
import java.io.File

private val fontFileRegex = Regex("(?i).*\\.[ot]tf")

/** 是否是受支持的字体文件名。 */
fun isFontFileName(name: String): Boolean = name.matches(fontFileRegex)

/**
 * 纯文件目录扫描：按 [fontFileRegex] 过滤目录下的字体文件。
 *
 * 不依赖 Android 类型，便于在 JVM 单测里验证 file:// 目录这条路径；
 * 路径不存在、不是目录或没有读取权限时返回 null，调用方据此标记为不可访问。
 */
internal fun listFontFilesInDir(dir: File): List<File>? =
    if (dir.isDirectory) {
        dir.listFiles()?.filter { it.isFile && isFontFileName(it.name) }
    } else {
        null
    }

/**
 * 应用私有字体目录 `Android/data/{package}/files/font`。
 *
 * 「导入字体」与排版/主题包导入的字体都放在这里，读取不需要任何存储授权，
 * 因此在任何 ROM 上都可用。
 */
fun fontDir(context: Context): File? = context.getExternalFilesDir(null)?.let { File(it, "font") }

/**
 * 已配置的字体文件夹的读取结果。UI 依赖它区分“空目录”和“读不了”，
 * 避免用户什么都看不到却没有任何解释。
 */
sealed interface FontFolderRead {

    /** 没有配置字体文件夹。 */
    data object NotConfigured : FontFolderRead

    /**
     * 读到了目录内容。
     * @param fonts 其中的字体文件（目录里可能只有非字体文件，这时为空）
     */
    data class Loaded(val fonts: List<FileDoc>) : FontFolderRead

    /**
     * 目录能访问，但一个条目都没读出来。
     * 部分国产 ROM 的文件选择器返回的 tree URI 就是这种情况：查询不报错也没有结果。
     */
    data object Empty : FontFolderRead

    /** 目录读不了：授权失效、不是目录，或者查询直接抛异常。 */
    data object Unreadable : FontFolderRead
}

/**
 * 字体扫描结果。
 *
 * @param fontFiles 可用字体：配置的字体文件夹 + 应用私有字体目录，按文件名去重
 * @param folder 配置的字体文件夹的读取结果
 */
data class FontScanResult(
    val fontFiles: List<FileDoc>,
    val folder: FontFolderRead,
)

/**
 * 扫描可用字体。
 *
 * 应用私有字体目录始终参与列表，因为「导入字体」的结果就放在那里，
 * 否则用户配置了字体文件夹之后就再也看不到导入的字体。
 */
fun scanFontFiles(context: Context, folderUri: Uri?): FontScanResult {
    val privateFonts = fontDir(context)?.listFileDocs { isFontFileName(it.name) }.orEmpty()
    if (folderUri == null) {
        return FontScanResult(privateFonts, FontFolderRead.NotConfigured)
    }
    val folder = readFontFolder(context, folderUri)
    val folderFonts = (folder as? FontFolderRead.Loaded)?.fonts.orEmpty()
    if (folderFonts.isEmpty()) {
        return FontScanResult(privateFonts, folder)
    }
    // 同名时以配置的字体文件夹优先：用户显式选过的那一份应该生效
    val merged = LinkedHashMap<String, FileDoc>(folderFonts.size + privateFonts.size)
    folderFonts.forEach { merged[it.name] = it }
    privateFonts.forEach { merged.putIfAbsent(it.name, it) }
    return FontScanResult(merged.values.toList(), folder)
}

/**
 * 把用户选中的字体文件复制进应用私有字体目录。
 *
 * 这是“系统文件夹选择器读不出内容”的兜底路径：`ACTION_OPEN_DOCUMENT` 给的是单个文件的
 * 授权，不需要遍历目录、不需要「所有文件访问」，任何 ROM 都能用；复制完成后也不再需要
 * 保留任何授权。
 *
 * @return 实际导入的字体数量
 */
fun importFontFiles(context: Context, uris: List<Uri>): Int {
    val dir = fontDir(context) ?: return 0
    if (!dir.isDirectory && !dir.mkdirs()) {
        AppLog.put("创建字体目录失败: $dir")
        return 0
    }
    var imported = 0
    uris.forEach { uri ->
        val name = runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name ?: uri.lastPathSegment.orEmpty()
        }.getOrDefault("")
        if (!isFontFileName(name)) {
            AppLog.put("忽略非字体文件: $uri")
            return@forEach
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 只取文件名，避免 uri 里带出目录分隔符
                File(dir, File(name).name).outputStream().use(input::copyTo)
            } ?: error("打开输入流失败")
        }.onSuccess {
            imported++
        }.onFailure {
            AppLog.put("导入字体失败: $uri", it)
        }
    }
    return imported
}

private fun readFontFolder(context: Context, folderUri: Uri): FontFolderRead {
    if (!folderUri.isContentScheme()) {
        val dir = File(folderUri.path ?: folderUri.toString())
        val fonts = listFontFilesInDir(dir)
        if (fonts == null) {
            AppLog.put("字体文件夹不可访问: $folderUri")
            return FontFolderRead.Unreadable
        }
        return FontFolderRead.Loaded(fonts.map(FileDoc::fromFile))
    }
    return readTreeFontFolder(context, folderUri)
}

/**
 * 读取 SAF tree URI。
 *
 * 部分国产 ROM（ColorOS 等）的文件选择器返回的 tree URI 用常规方式查不到任何子条目，
 * 这里依次尝试三种取法，任意一种拿到条目就用它：
 * 1. `getDocumentId` —— 与 `FileDoc.list()` 一致，绝大多数设备走这条；
 * 2. `getTreeDocumentId` —— 处理 `tree/{root}/document/{doc}` 形式的 URI；
 * 3. `DocumentFile` 自己的实现 —— 对 provider 的 projection 兼容性更好。
 */
private fun readTreeFontFolder(context: Context, folderUri: Uri): FontFolderRead {
    var lastError: Throwable? = null
    var queried = false
    val readers = listOf<Pair<String, () -> List<FileDoc>>>(
        "documentId" to {
            treeChildren(context, folderUri, DocumentsContract.getDocumentId(folderUri))
        },
        "treeDocumentId" to {
            treeChildren(context, folderUri, DocumentsContract.getTreeDocumentId(folderUri))
        },
        "documentFile" to {
            DocumentFile.fromTreeUri(context, folderUri)?.listFiles()
                ?.map(FileDoc::fromDocumentFile)
                .orEmpty()
        },
    )
    for ((label, read) in readers) {
        val entries = try {
            read()
        } catch (e: Exception) {
            lastError = e
            AppLog.put("读取字体文件夹失败[$label]: $folderUri", e)
            continue
        }
        queried = true
        if (entries.isEmpty()) continue
        return FontFolderRead.Loaded(
            fonts = entries.filter { !it.isDir && isFontFileName(it.name) },
        )
    }
    return if (queried) {
        AppLog.put("字体文件夹查不到任何条目, 可能是系统文件选择器不支持: $folderUri")
        FontFolderRead.Empty
    } else {
        AppLog.put("字体文件夹不可访问: $folderUri", lastError)
        FontFolderRead.Unreadable
    }
}
