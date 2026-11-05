package io.legado.app.feature.reader.platform.ioscurl

import android.graphics.Bitmap
import android.view.Surface
import android.opengl.*
import java.nio.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import io.legado.app.feature.reader.core.transition.ioscurl.mesh.*
import io.legado.app.feature.reader.core.transition.ioscurl.model.CurlRect

/** GLES2 raster adapter only: consumes Kotlin mesh packets with the recovered 0/3/12 programs.
 * One GL worker owns its resources; the host supplies poses without a separate animation clock.
 */
internal class IosCurlRenderer : AutoCloseable {
    private val display = sharedDisplay
    private var context = EGL14.EGL_NO_CONTEXT
    private var surface = EGL14.EGL_NO_SURFACE
    private val bitmapFramebuffer = IntArray(1)
    private val bitmapColorTexture = IntArray(1)
    private var bitmapTargetWidth = 0
    private var bitmapTargetHeight = 0
    @Volatile private var windowSurface = EGL14.EGL_NO_SURFACE
    val hasWindow: Boolean get() = windowSurface != EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private val programs = IntArray(3)
    private val textures = IntArray(2)
    private val textureSources = arrayOfNulls<WeakReference<Bitmap>>(2)
    private val textureGenerations = IntArray(2)
    private val matrices = IntArray(3)
    private val texSizes = IntArray(3)
    private var vertexBuffer =
        ByteBuffer.allocateDirect(4096 * 40).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var indexBuffer =
        ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder()).asShortBuffer()
    private var readback: ByteBuffer? = null
    private var output: Bitmap? = null
    private val matrix = FloatArray(16)
    private val queueLock = Any()
    @Volatile private var workerThread: Thread? = null
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "IosCurl").apply { isDaemon = true }.also { workerThread = it }
    }
    private var pending: PendingFrame? = null
    private var draining = false
    private val generation = AtomicLong()
    private val closed = AtomicBoolean()

    private data class PendingFrame(
        val generation: Long,
        val frame: CurlMeshFrame,
        val width: Float,
        val height: Float,
        val turningTexture: Int,
        val underTexture: Int,
        val underlays: List<Pair<Int, CurlRect>>,
        val complete: (Throwable?) -> Unit,
    )

    init { onWorker(::initialize) }

    private fun initialize() {
        try {
            val configs = arrayOfNulls<EGLConfig>(1);
            val count = IntArray(1)
            val attributes = intArrayOf(
                        EGL14.EGL_SURFACE_TYPE,
                        EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
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
                    )
            if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
                // Window output is optional; retain the bitmap path on pbuffer-only drivers.
                attributes[1] = EGL14.EGL_PBUFFER_BIT
                check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0)
            }
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

    fun upload(current: Bitmap, target: Bitmap) {
        invalidateQueuedFrames()
        onWorker {
            withCurrent {
                // Adjacent turns share a sheet. Keep only the existing two GPU textures, without
                // retaining snapshot pixels or reusing a mutable bitmap's outdated contents.
                fun matches(index: Int, bitmap: Bitmap) =
                    textureSources[index]?.get() === bitmap &&
                        textureGenerations[index] == bitmap.generationId
                if ((matches(1, current) && !matches(0, current)) ||
                    (matches(0, target) && !matches(1, target) && !matches(0, current))) {
                    val texture = textures[0]; textures[0] = textures[1]; textures[1] = texture
                    val source = textureSources[0]
                    textureSources[0] = textureSources[1]; textureSources[1] = source
                    val version = textureGenerations[0]
                    textureGenerations[0] = textureGenerations[1]; textureGenerations[1] = version
                }
                for ((index, bitmap) in arrayOf(current, target).withIndex()) {
                    check(!bitmap.isRecycled) { "Curl page bitmap was recycled" }
                    if (matches(index, bitmap)) continue
                    textureSources[index] = null
                    if (textures[index] == 0) GLES20.glGenTextures(1, textures, index)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(
                        GLES20.GL_TEXTURE_2D,
                        textures[index]
                    )
                    // Hardware snapshots need CPU-readable pixels only when their contents change.
                    val pixels = if (bitmap.config == Bitmap.Config.HARDWARE)
                        requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)) else bitmap
                    try {
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, pixels, 0)
                    } finally {
                        if (pixels !== bitmap) pixels.recycle()
                    }
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
                    checkGl("texture upload")
                    textureGenerations[index] = bitmap.generationId
                    textureSources[index] = WeakReference(bitmap)
                }
                checkGl("texture upload")
            }
        }
    }

    fun bindOutput(bitmap: Bitmap) {
        invalidateQueuedFrames()
        onWorker {
            readback = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
                .order(ByteOrder.nativeOrder())
            output = bitmap
        }
    }

    /** Borrows the host surface; detach before the host releases it. */
    fun bindWindow(window: Surface?): Boolean {
        invalidateQueuedFrames()
        return onWorker {
            if (windowSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, windowSurface)
            windowSurface = EGL14.EGL_NO_SURFACE
            if (window != null && !window.isValid) return@onWorker false
            if (window != null) {
                windowSurface = EGL14.eglCreateWindowSurface(display, config, window,
                    intArrayOf(EGL14.EGL_NONE), 0)
            }
            hasWindow
        }
    }

    /**
     * Queues window output without waiting for the GL worker on the caller thread. At most one frame
     * waits behind the current draw; later poses replace it. Replaced/invalidated frames do not
     * invoke completion. Completion runs on the worker, after restoring its previous EGL context.
     */
    fun renderAsync(
        frame: CurlMeshFrame,
        logicalWidth: Float,
        logicalHeight: Float,
        turningTexture: Int,
        underTexture: Int,
        underlays: List<Pair<Int, CurlRect>>,
        complete: (Throwable?) -> Unit,
    ) {
        val request = PendingFrame(generation.get(), frame, logicalWidth, logicalHeight,
            turningTexture, underTexture, underlays.toList(), complete)
        synchronized(queueLock) {
            if (closed.get()) return
            pending = request
            if (!draining) {
                draining = true
                worker.execute(::drainFrames)
            }
        }
    }

    private fun drainFrames() {
        try {
            while (true) {
                val request = synchronized(queueLock) { pending.also { pending = null } } ?: return
                renderPending(request)
            }
        } finally {
            synchronized(queueLock) {
                draining = false
                if (!closed.get() && pending != null) {
                    draining = true
                    worker.execute(::drainFrames)
                }
            }
        }
    }

    private fun renderPending(request: PendingFrame) {
        val failure = run {
            if (closed.get() || request.generation != generation.get() || !hasWindow) return
            try {
                render(request.frame, request.width, request.height, request.turningTexture,
                    request.underTexture, request.underlays)
                null
            } catch (error: RuntimeException) {
                error
            } catch (error: OutOfMemoryError) {
                error
            }
        }
        if (!closed.get() && request.generation == generation.get()) request.complete(failure)
    }

    /** Invalidates waiting frames before scheduling a change to textures or surface ownership. */
    private fun invalidateQueuedFrames() {
        generation.incrementAndGet()
        synchronized(queueLock) { pending = null }
    }

    fun render(
        frame: CurlMeshFrame,
        logicalWidth: Float,
        logicalHeight: Float,
        turningTexture: Int,
        underTexture: Int,
        underlays: List<Pair<Int, CurlRect>>
    ) = onWorker {
        val window = windowSurface
        val drawToWindow = window != EGL14.EGL_NO_SURFACE
        withCurrent(if (drawToWindow) window else surface) {
            val bitmap = requireNotNull(output)
            if (drawToWindow) GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            else bindBitmapFramebuffer(bitmap)
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
            if (drawToWindow) {
                checkGl("window rasterization")
                check(EGL14.eglSwapBuffers(display, window)) { "Curl window presentation failed" }
                return@withCurrent
            }
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
    }

    private fun bindBitmapFramebuffer(bitmap: Bitmap) {
        // Bitmap readback needs its own color attachment across window surface transitions.
        // The small pbuffer only provides an EGL surface on which to make the context current.
        if (bitmapFramebuffer[0] == 0) GLES20.glGenFramebuffers(1, bitmapFramebuffer, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, bitmapFramebuffer[0])
        if (bitmap.width == bitmapTargetWidth && bitmap.height == bitmapTargetHeight) return

        if (bitmapColorTexture[0] == 0) GLES20.glGenTextures(1, bitmapColorTexture, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bitmapColorTexture[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.width, bitmap.height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, bitmapColorTexture[0], 0)
        checkGl("bitmap framebuffer allocation")
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "Curl bitmap framebuffer incomplete: $status" }
        bitmapTargetWidth = bitmap.width
        bitmapTargetHeight = bitmap.height
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

    private fun <T> withCurrent(target: EGLSurface = surface, block: () -> T): T {
        val previousDisplay = EGL14.eglGetCurrentDisplay();
        val previousContext = EGL14.eglGetCurrentContext();
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        check(EGL14.eglMakeCurrent(display, target, target, context))
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
        if (!closed.compareAndSet(false, true)) return
        invalidateQueuedFrames()
        try {
            onWorker {
                bindWindow(null)
                if (context != EGL14.EGL_NO_CONTEXT && surface != EGL14.EGL_NO_SURFACE) runCatching {
                    withCurrent {
                        GLES20.glDeleteFramebuffers(1, bitmapFramebuffer, 0)
                        GLES20.glDeleteTextures(1, bitmapColorTexture, 0)
                        if (textures.any { it != 0 }) GLES20.glDeleteTextures(
                            2,
                            textures,
                            0
                        ); for (program in programs) if (program != 0) GLES20.glDeleteProgram(program)
                    }
                }
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                EGL14.eglReleaseThread()
                context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE; output = null; readback =
                    null; programs.fill(0); textures.fill(0); textureSources.fill(null)
            }
        } finally {
            worker.shutdown()
        }
    }

    /** All EGL/GLES objects stay on one thread, including initialization and synchronous fallback. */
    private fun <T> onWorker(block: () -> T): T {
        if (Thread.currentThread() === workerThread) return block()
        val task = worker.submit(Callable { block() })
        var interrupted = false
        try {
            while (true) {
                try {
                    return task.get()
                } catch (_: InterruptedException) {
                    // Borrowed bitmaps and surfaces must remain valid until the GL task finishes.
                    interrupted = true
                }
            }
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
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
