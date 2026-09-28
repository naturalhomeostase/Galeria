package com.galeria.util.video

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val VERTEX_SHADER = """
    uniform mat4 uSTMatrix;
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = aPosition;
        vTextureCoord = (uSTMatrix * aTextureCoord).xy;
    }
"""

private const val FRAGMENT_SHADER = """
    #extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTextureCoord;
    uniform samplerExternalOES sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
"""

private val FULL_RECTANGLE_COORDS = floatArrayOf(
    -1f, -1f, 0f,
    1f, -1f, 0f,
    -1f, 1f, 0f,
    1f, 1f, 0f
)
private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
    0f, 0f,
    1f, 0f,
    0f, 1f,
    1f, 1f
)

/**
 * Recebe os quadros decodificados (a [surface] exposta aqui é passada como saída do
 * decoder) e sabe desenhá-los, via OpenGL, num quad de tela cheia -- desde que o contexto
 * EGL do InputSurface (a entrada do encoder) já esteja "current" na hora de chamar
 * [drawImage]. É esse desenho que faz o quadro decodificado virar entrada do encoder sem
 * nunca passar pela CPU.
 */
class OutputSurface : SurfaceTexture.OnFrameAvailableListener {
    private var textureId = -1
    val surface: Surface
    private val surfaceTexture: SurfaceTexture

    private val vertexBuffer: FloatBuffer = toFloatBuffer(FULL_RECTANGLE_COORDS)
    private val texBuffer: FloatBuffer = toFloatBuffer(FULL_RECTANGLE_TEX_COORDS)
    private val stMatrix = FloatArray(16)
    private var program = 0
    private var aPositionHandle = 0
    private var aTexCoordHandle = 0
    private var uSTMatrixHandle = 0

    private val lock = Object()
    private var frameAvailable = false

    init {
        createProgram()

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setOnFrameAvailableListener(this)
        surface = Surface(surfaceTexture)
    }

    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(lock) {
            frameAvailable = true
            lock.notifyAll()
        }
    }

    /** Bloqueia até o decoder entregar um novo quadro, com um limite de segurança de 3s. */
    fun awaitNewImage() {
        synchronized(lock) {
            var waitedMs = 0L
            while (!frameAvailable) {
                lock.wait(500)
                waitedMs += 500
                if (waitedMs >= 3000) {
                    throw IllegalStateException("Tempo esgotado esperando um quadro do decodificador")
                }
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)
    }

    /** Desenha o quadro atual num quad de tela cheia. Chame com o EGL do InputSurface já current. */
    fun drawImage() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        texBuffer.position(0)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoordHandle)

        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexCoordHandle)
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
        if (program != 0) GLES20.glDeleteProgram(program)
    }

    private fun createProgram() {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            program = 0
            throw IllegalStateException("Falha ao linkar o programa GL: $log")
        }
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Falha ao compilar shader: $log")
        }
        return shader
    }
}

private fun toFloatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data)
        position(0)
    }
