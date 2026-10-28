package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Size
import java.io.FileInputStream
import java.io.InputStream
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGExternalFileResolver
import kotlin.math.max

@Suppress("WeakerAccess", "MemberVisibilityCanBePrivate")
object SvgUtils {

    private val externalResolverLock = Any()

    /**
     * 从Svg中解码bitmap
     */
    
    fun createBitmap(filePath: String, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            createBitmap(inputStream, width, height)
        }.getOrNull()
    }

    fun createBitmap(inputStream: InputStream, width: Int, height: Int? = null): Bitmap? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            createBitmap(svg, width, height)
        }.getOrNull()
    }

    /**
     * 带外部资源解析器（气泡包内的图片/字体）的渲染入口。
     *
     * 与无解析器版本的差别：这里把 [width]/[height] 当成位图上限等比缩放（不做放大），
     * 并且整个过程串行化 —— androidsvg 的解析器注册是进程级静态状态。
     */
    fun createBitmap(
        inputStream: InputStream,
        width: Int,
        height: Int? = null,
        resolver: SVGExternalFileResolver
    ): Bitmap? {
        return kotlin.runCatching {
            synchronized(externalResolverLock) {
                SVG.registerExternalFileResolver(resolver)
                try {
                    val svg = SVG.getFromInputStream(inputStream)
                    createBitmapFitWithin(svg, width, height)
                } finally {
                    SVG.deregisterExternalFileResolver()
                }
            }
        }.getOrNull()
    }

    private fun createBitmapFitWithin(svg: SVG, width: Int?, height: Int?): Bitmap {
        val size = getSize(svg)
        val bitmapSize = requireNotNull(
            SvgBitmapSizePolicy.fitWithin(size.width, size.height, width, height)
        ) { "invalid SVG dimensions" }
        if (svg.documentViewBox == null && size.width > 0 && size.height > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val bitmap = Bitmap.createBitmap(bitmapSize.width, bitmapSize.height, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    //获取svg图片大小
    fun getSize(filePath: String): Size? {
        return kotlin.runCatching {
            val inputStream = FileInputStream(filePath)
            getSize(inputStream)
        }.getOrNull()
    }

    fun getSize(inputStream: InputStream): Size? {
        return kotlin.runCatching {
            val svg = SVG.getFromInputStream(inputStream)
            getSize(svg)
        }.getOrNull()
    }

    /////// private method
    private fun createBitmap(svg: SVG, width: Int? = null, height: Int? = null): Bitmap {
        val size = getSize(svg)
        val wRatio = width?.let { size.width / it } ?: -1
        val hRatio = height?.let { size.height / it } ?: -1
        //如果超出指定大小，则缩小相应的比例
        val ratio = when {
            wRatio > 1 && hRatio > 1 -> max(wRatio, hRatio)
            wRatio > 1 -> wRatio
            hRatio > 1 -> hRatio
            else -> 1
        }

        val viewBox: RectF? = svg.documentViewBox
        if (viewBox == null && size.width > 0 && size.height > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }

        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")

        val bitmapWidth = size.width / ratio
        val bitmapHeight = size.height / ratio
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

        svg.renderToCanvas(Canvas(bitmap))
        return bitmap
    }

    private fun getSize(svg: SVG): Size {
        val width = svg.documentWidth.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.right - svg.documentViewBox.left).toInt()
        val height = svg.documentHeight.toInt().takeIf { it > 0 }
            ?: (svg.documentViewBox.bottom - svg.documentViewBox.top).toInt()
        return Size(width, height)      
    }

}
