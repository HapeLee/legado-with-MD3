package io.legado.app.help

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.legado.app.utils.FileDoc
import io.legado.app.utils.listFileDocs
import java.io.File

fun loadFontFiles(context: Context, folderUri: Uri?): List<FileDoc> {
    val fontRegex = Regex("(?i).*\\.[ot]tf")
    if (folderUri != null) {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
        return documentFile?.let { docFile ->
            val fontFiles = mutableListOf<FileDoc>()
            docFile.listFiles()?.forEach { file ->
                if (file.isFile && file.name?.matches(fontRegex) == true) {
                    fontFiles.add(
                        FileDoc(
                            name = file.name ?: "",
                            isDir = false,
                            size = file.length(),
                            lastModified = file.lastModified(),
                            uri = file.uri
                        )
                    )
                }
            }
            fontFiles
        } ?: emptyList()
    } else {
        val fontPath = "${context.getExternalFilesDir(null)?.absolutePath}/font"
        return File(fontPath).listFileDocs { it.name.matches(fontRegex) }
    }
}
