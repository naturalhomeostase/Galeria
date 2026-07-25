package com.galeria.util

import android.graphics.ColorMatrix

enum class FilterPreset(val label: String) {
    NORMAL("Normal") {
        override fun matrix() = ColorMatrix()
    },
    PRETO_BRANCO("P&B") {
        override fun matrix() = ColorMatrix().apply { setSaturation(0f) }
    },
    SEPIA("Sépia") {
        override fun matrix() = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    },
    VIVIDO("Vívido") {
        override fun matrix() = ColorMatrix().apply { setSaturation(1.5f) }
    },
    FRIO("Frio") {
        override fun matrix() = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, -5f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    },
    QUENTE("Quente") {
        override fun matrix() = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 20f,
                0f, 1f, 0f, 0f, 5f,
                0f, 0f, 1f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    },
    VINTAGE("Vintage") {
        override fun matrix() = ColorMatrix(
            floatArrayOf(
                0.9f, 0.1f, 0f, 0f, 10f,
                0.05f, 0.85f, 0.1f, 0f, 5f,
                0.05f, 0.1f, 0.7f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    };

    abstract fun matrix(): ColorMatrix
}
