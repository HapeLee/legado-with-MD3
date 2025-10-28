package io.legado.app.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import io.legado.app.constant.AppConst
import java.util.Date

object ImageSaveUtils {

    /**
     * 使用 MediaStore 保存图片到系统相册
     * @param context Context
     * @param byteArray 图片数据（JPG/PNG 的 byte[]）
     * @param prefix 文件名前缀，默认 "IMG_"
     * @param folderName 相册下的子文件夹，默认 "Legado"
     */
    fun saveImageToGallery(
        context: Context,
        byteArray: ByteArray,
        prefix: String = "IMG_",
        folderName: String = "Legado"
    ): Boolean {
        return try {
            val fileName = prefix + AppConst.fileNameFormat.format(Date()) + ".jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android10以上指定相对路径
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(byteArray)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
