package io.legado.app.feature.reader.platform.ioscurl

import android.graphics.Bitmap
import android.opengl.*
import java.nio.*
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.*
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/** GLES2 raster adapter only: consumes Kotlin mesh packets with the recovered 0/3/12 programs.
 * One owner/thread, no SurfaceView, JNI, independent clock or navigation state.
 */
internal class IosCurlRenderer : AutoCloseable {
    private val display = sharedDisplay
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private val programs = IntArray(3)
    private val textures = IntArray(2)
    private val matrices = IntArray(3)
    private val texSizes = IntArray(3)
    private var vertexBuffer =
        ByteBuffer.allocateDirect(4096 * 40).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var indexBuffer =
        ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder()).asShortBuffer()
    private var readback: ByteBuffer? = null
    private var output: Bitmap? = null
    private val matrix = FloatArray(16)

    init {
        try {
            val configs = arrayOfNulls<EGLConfig>(1);
            val count = IntArray(1)
            check(
                EGL14.eglChooseConfig(
                    display, intArrayOf(
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_RED_SIZE,
                        8,
                        EGL14.EGL_GREEN_SIZE,
                        8,
                        EGL14.EGL_BLUE_SIZE,
                        8,
                        EGL14.EGL_ALPHA_SIZE,
                        8,
                        EGL14.EGL_NONE
                    ), 0, configs, 0, 1, count, 0
                ) && count[0] > 0
            )
            config = requireNotNull(configs[0])
            context = EGL14.eglCreateContext(
                display,
                config,
                EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                0
            )
            surface = EGL14.eglCreatePbufferSurface(
                display,
                config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE),
                0
            )
            check(context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE)
            withCurrent {
                for (index in programs.indices) {
                    val mode = intArrayOf(0, 3, 12)[index]
                    val vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
                    val fs = compile(
                        GLES20.GL_FRAGMENT_SHADER,
                        "precision mediump float;uniform lowp sampler2D page;varying highp vec2 uv;varying lowp vec4 tint;void main(){" +
                                (if (mode == 0) "gl_FragColor=tint;" else "lowp vec4 s0=texture2D(page,uv);gl_FragColor=" + if (mode == 3) "tint*s0;" else "tint*s0.a;") + "}"
                    )
                    try {
                        val program = GLES20.glCreateProgram(); programs[index] = program
                        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs)
                        GLES20.glBindAttribLocation(
                            program,
                            0,
                            "position"
                        ); GLES20.glBindAttribLocation(
                            program,
                            1,
                            "texcoord"
                        ); GLES20.glBindAttribLocation(program, 2, "color")
                        GLES20.glLinkProgram(program);
                        val status = IntArray(1); GLES20.glGetProgramiv(
                            program,
                            GLES20.GL_LINK_STATUS,
                            status,
                            0
                        )
                        check(status[0] != 0) { GLES20.glGetProgramInfoLog(program) }
                        matrices[index] =
                            GLES20.glGetUniformLocation(program, "vertex_matrix"); texSizes[index] =
                            GLES20.glGetUniformLocation(program, "texmat0")
                        GLES20.glUseProgram(program); GLES20.glUniform1i(
                            GLES20.glGetUniformLocation(
                                program,
                                "page"
                            ), 0
                        )
                    } finally {
                        GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs)
                    }
                }
                GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glDisable(GLES20.GL_CULL_FACE); GLES20.glDisable(
                GLES20.GL_DITHER
            )
            }
        } catch (e: Throwable) {
            close(); throw e
        }
    }

    fun upload(current: Bitmap, target: Bitmap) = withCurrent {
        GLES20.glGenTextures(2, textures, 0)
        for ((index, bitmap) in arrayOf(current, target).withIndex()) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                textures[index]
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )
        }
        checkGl("texture upload")
    }

    fun bindOutput(bitmap: Bitmap) {
        val next = EGL14.eglCreatePbufferSurface(
            display,
            config,
            intArrayOf(
                EGL14.EGL_WIDTH,
                bitmap.width,
                EGL14.EGL_HEIGHT,
                bitmap.height,
                EGL14.EGL_NONE
            ),
            0
        )
        check(next != EGL14.EGL_NO_SURFACE)
        EGL14.eglDestroySurface(display, surface); surface = next; output = bitmap
        readback = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            .order(ByteOrder.nativeOrder())
    }

    fun render(
        frame: CurlMeshFrame,
        logicalWidth: Float,
        logicalHeight: Float,
        turningTexture: Int,
        underTexture: Int,
        underlays: List<Pair<Int, CurlRect>>
    ) = withCurrent {
        val bitmap = requireNotNull(output)
        GLES20.glViewport(0, 0, bitmap.width, bitmap.height); GLES20.glClearColor(
        0f,
        0f,
        0f,
        0f
    ); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        matrix.fill(0f); matrix[0] = 2f / logicalWidth; matrix[5] =
        -2f / logicalHeight; matrix[10] = 1f; matrix[12] = -1f; matrix[13] = 1f; matrix[15] = 1f
        if (underlays.isEmpty()) flat(
            underTexture,
            CurlRect(0.0, 0.0, logicalWidth.toDouble(), logicalHeight.toDouble()),
            frame.width.toFloat(),
            frame.height.toFloat()
        )
        else for ((texture, rect) in underlays) flat(
            texture,
            rect,
            frame.width.toFloat(),
            frame.height.toFloat()
        )
        for (batch in frame.batches) draw(
            batch,
            turningTexture,
            frame.width.toFloat(),
            frame.height.toFloat()
        )
        val buffer = requireNotNull(readback); buffer.clear()
        GLES20.glReadPixels(
            0,
            0,
            bitmap.width,
            bitmap.height,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            buffer
        )
        checkGl("page rasterization"); buffer.position(0); bitmap.copyPixelsFromBuffer(buffer)
    }

    private fun flat(texture: Int, r: CurlRect, tw: Float, th: Float) {
        val x = r.x.toFloat();
        val y = r.y.toFloat();
        val w = r.width.toFloat();
        val h = r.height.toFloat()
        vertexBuffer.clear(); for (p in arrayOf(
            floatArrayOf(x, y, 0f, 0f),
            floatArrayOf(x, y + h, 0f, th),
            floatArrayOf(x + w, y, tw, 0f),
            floatArrayOf(x + w, y + h, tw, th)
        ))
            vertexBuffer.put(floatArrayOf(p[0], p[1], 0f, 1f, p[2], p[3], 1f, 1f, 1f, 1f))
        bind(1, texture, tw, th, false); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun draw(batch: CurlDrawBatch, texture: Int, tw: Float, th: Float) {
        if (vertexBuffer.capacity() < batch.vertexCount * 10) vertexBuffer =
            ByteBuffer.allocateDirect(batch.vertexCount * 40).order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        vertexBuffer.clear();
        val input = ByteBuffer.wrap(batch.vertices).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until batch.vertexCount) {
            val at = i * 64
            for (k in 0..5) vertexBuffer.put(input.getFloat(at + k * 4))
            for (k in 0..3) vertexBuffer.put(
                half(
                    input.getShort(at + 40 + k * 2).toInt() and 65535
                )
            )
        }
        check(batch.blendFunction == 3)
        bind(
            when (batch.mode) {
                0 -> 0; 3 -> 1; 12 -> 2; else -> error("Unknown curl shader ${batch.mode}")
            }, texture, tw, th, batch.blendEnabled
        )
        val primitive = when (batch.primitive) {
            0 -> GLES20.GL_POINTS; 1 -> GLES20.GL_LINES; 2 -> GLES20.GL_TRIANGLE_STRIP; 3 -> GLES20.GL_TRIANGLES; 4 -> GLES20.GL_TRIANGLE_FAN; else -> error(
                "Unknown curl primitive"
            )
        }
        val indices = batch.indices
        if (indices == null) GLES20.glDrawArrays(primitive, 0, batch.count)
        else {
            if (indexBuffer.capacity() < indices.size) indexBuffer =
                ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
                    .asShortBuffer()
            indexBuffer.clear(); indexBuffer.put(indices).position(0); GLES20.glDrawElements(
                primitive,
                batch.count,
                GLES20.GL_UNSIGNED_SHORT,
                indexBuffer
            )
        }
    }

    private fun bind(program: Int, texture: Int, tw: Float, th: Float, blend: Boolean) {
        GLES20.glUseProgram(programs[program]); GLES20.glUniformMatrix4fv(
            matrices[program],
            1,
            false,
            matrix,
            0
        ); GLES20.glUniform2f(texSizes[program], 1f / tw, 1f / th)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(
            GLES20.GL_TEXTURE_2D,
            textures[texture]
        )
        if (blend) {
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendEquation(GLES20.GL_FUNC_ADD); GLES20.glBlendFunc(
                GLES20.GL_ONE,
                GLES20.GL_ONE_MINUS_SRC_ALPHA
            )
        } else GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glBindBuffer(
            GLES20.GL_ARRAY_BUFFER,
            0
        ); GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        for ((slot, size, offset) in arrayOf(
            intArrayOf(0, 4, 0),
            intArrayOf(1, 2, 4),
            intArrayOf(2, 4, 6)
        )) {
            vertexBuffer.position(offset); GLES20.glEnableVertexAttribArray(slot); GLES20.glVertexAttribPointer(
                slot,
                size,
                GLES20.GL_FLOAT,
                false,
                40,
                vertexBuffer
            )
        }
    }

    private fun half(value: Int): Float {
        val sign = (value and 0x8000) shl 16;
        val exponent = value ushr 10 and 31;
        var m = value and 1023
        val bits = if (exponent == 0) {
            if (m == 0) sign else {
                var k = 0; while (m and 1024 == 0) {
                    m = m shl 1; k++
                }; sign or ((113 - k) shl 23) or ((m and 1023) shl 13)
            }
        } else sign or ((if (exponent == 31) 255 else exponent + 112) shl 23) or (m shl 13)
        return Float.fromBits(bits)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type); GLES20.glShaderSource(
            shader,
            source
        ); GLES20.glCompileShader(shader)
        val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader); GLES20.glDeleteShader(shader); error(
                error
            )
        }; return shader
    }

    private fun checkGl(operation: String) {
        val code =
            GLES20.glGetError(); check(code == GLES20.GL_NO_ERROR) { "Curl $operation GL error $code" }
    }

    private fun <T> withCurrent(block: () -> T): T {
        val previousDisplay = EGL14.eglGetCurrentDisplay();
        val previousContext = EGL14.eglGetCurrentContext();
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        check(EGL14.eglMakeCurrent(display, surface, surface, context))
        try {
            return block()
        } finally {
            if (previousDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglMakeCurrent(
                previousDisplay,
                draw,
                read,
                previousContext
            )
            else EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
        }
    }

    override fun close() {
        if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) runCatching {
            withCurrent {
                if (textures.any { it != 0 }) GLES20.glDeleteTextures(
                    2,
                    textures,
                    0
                ); for (program in programs) if (program != 0) GLES20.glDeleteProgram(program)
            }
        }
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE; output = null; readback =
            null; programs.fill(0); textures.fill(0)
    }

    private companion object {
        val sharedDisplay by lazy {
            EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                .also { check(EGL14.eglInitialize(it, IntArray(2), 0, IntArray(2), 0)) }
        }
        const val VERTEX =
            "attribute highp vec4 position;attribute highp vec2 texcoord;attribute lowp vec4 color;uniform highp mat4 vertex_matrix;uniform highp vec2 texmat0;varying highp vec2 uv;varying lowp vec4 tint;void main(){gl_Position=vertex_matrix*position;uv=texcoord*texmat0;tint=color;}"
    }
}
