package com.galeria.util.video

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface

// Constante que o Android define nas extensões EGL (EGL_RECORDABLE_ANDROID) mas que não tem
// um equivalente exposto em android.opengl.EGL14 -- o valor é fixo e documentado, então
// declaramos aqui mesmo em vez de depender de uma constante que não existe no SDK.
private const val EGL_RECORDABLE_ANDROID = 0x3142

/**
 * Envolve um EGLSurface ligado à Surface de entrada de um MediaCodec encoder. É nela que
 * desenhamos (via OpenGL, através do OutputSurface) cada quadro decodificado antes de
 * "empurrá-lo" pro codificador -- assim o quadro nunca precisa ser copiado pra CPU entre o
 * decode e o encode, só fica passando de GPU pra GPU.
 */
class InputSurface(private val surface: Surface) {
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) throw IllegalStateException("Não foi possível obter o EGLDisplay")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw IllegalStateException("Não foi possível inicializar o EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] <= 0) {
            throw IllegalStateException("Não foi possível escolher uma configuração EGL compatível")
        }
        val config = configs[0] ?: throw IllegalStateException("EGLConfig retornado nulo pelo eglChooseConfig")

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        checkEglError("eglCreateContext")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
    }

    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw IllegalStateException("eglMakeCurrent falhou")
        }
    }

    fun setPresentationTime(nanoseconds: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nanoseconds)
    }

    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(eglDisplay, eglSurface)

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface.release()
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun checkEglError(op: String) {
        val errorCode = EGL14.eglGetError()
        if (errorCode != EGL14.EGL_SUCCESS) {
            throw IllegalStateException("$op: erro EGL 0x${Integer.toHexString(errorCode)}")
        }
    }
}
