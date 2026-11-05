package io.legado.app.help.coil

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.legado.app.utils.SvgUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/** 原图头部尺寸独立于预览降采样；可重建的索引不影响图片有效性和下载完成判定。 */
internal object MangaImageDimensions {
    fun read(file: File): Float? {
        val length = file.length()
        val modified = file.lastModified()
        val index = File(File(file.parentFile, ".dimensions"), file.name)
        try {
            val saved = Properties().apply { index.inputStream().use(::load) }
            if (saved.getProperty("version") == "1" && saved.getProperty("length") == length.toString() &&
                saved.getProperty("modified") == modified.toString()
            ) {
                saved.getProperty("ratio")?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0 }
                    ?.let { return it }
            }
        } catch (_: IOException) { /* 未建立索引或缓存已被清理。 */
        } catch (_: IllegalArgumentException) { /* 损坏索引重新建立。 */
        }
        val ratio = readRatio(file::inputStream) { ExifInterface(file.path) } ?: return null
        try {
            index.parentFile!!.mkdirs()
            val temp = File.createTempFile("dimensions", ".tmp", index.parentFile)
            try {
                Properties().apply {
                    setProperty("version", "1")
                    setProperty("length", length.toString()); setProperty(
                    "modified",
                    modified.toString()
                )
                    setProperty("ratio", ratio.toString())
                }.let { props -> temp.outputStream().use { props.store(it, null) } }
                Files.move(temp.toPath(), index.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
        } catch (_: IOException) { /* 尺寸缓存写入失败仍可正常显示原图。 */
        }
        return ratio
    }

    fun read(resolver: ContentResolver, uri: Uri): Float? =
        readRatio({ resolver.openInputStream(uri) }) {
            resolver.openInputStream(uri)?.use(::ExifInterface)
        }

    private fun readRatio(open: () -> InputStream?, readExif: () -> ExifInterface?): Float? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val ratio = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            bounds.outWidth.toFloat() / bounds.outHeight
        } else open()?.use { input ->
            SvgUtils.getSize(input)?.takeIf { it.width > 0 && it.height > 0 }
                ?.let { (it.width / it.height).toFloat() }
        } ?: return null
        val orientation = try {
            readExif()?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        } catch (_: IOException) {
            null
        }
        val corrected = when (orientation) {
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270 -> 1f / ratio
            else -> ratio
        }
        return corrected.takeIf { it.isFinite() && it > 0 }
    }
}
