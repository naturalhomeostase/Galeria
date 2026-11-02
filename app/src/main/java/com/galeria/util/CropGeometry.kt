package com.galeria.util

import kotlin.math.max
import kotlin.math.min

/**
 * Moldura do corte, em frações (0f..1f) da área onde a imagem é exibida na tela.
 */
data class CropFrame(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * Zoom/posição da imagem por baixo da moldura. [tx] e [ty] são a posição do canto
 * superior esquerdo da imagem, também em frações da área de exibição. Com escala 1 a imagem
 * ocupa exatamente a área toda (tx = ty = 0); com escala maior ela "sobra" pra fora da área,
 * e tx/ty ficam negativos (a imagem ocupa de tx até tx + scale).
 */
data class ImageTransform(val scale: Float = 1f, val tx: Float = 0f, val ty: Float = 0f)

enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

object CropGeometry {
    const val MIN_FRAME_SIZE = 0.15f
    const val MAX_SCALE = 6f
    val DEFAULT_FRAME = CropFrame(0.08f, 0.08f, 0.92f, 0.92f)

    // Igual a coerceIn, mas sem lançar exceção quando erros de arredondamento de ponto
    // flutuante deixam o intervalo "vazio" por ~1e-8 (ex.: moldura exatamente no tamanho
    // mínimo). Nesse caso o limite inferior vence.
    private fun clamp(value: Float, low: Float, high: Float): Float = max(low, min(value, high))

    // A imagem sempre cobre a área inteira (nunca aparece faixa preta ao arrastar), então
    // a posição só pode variar entre "alinhada no canto direito/inferior" (1 - scale) e
    // "alinhada no canto esquerdo/superior" (0).
    fun clampTransform(t: ImageTransform): ImageTransform {
        val s = t.scale.coerceIn(1f, MAX_SCALE)
        return ImageTransform(s, t.tx.coerceIn(1f - s, 0f), t.ty.coerceIn(1f - s, 0f))
    }

    // Zoom em torno de um ponto (o centro dos dedos): o pedaço da imagem que está sob esse
    // ponto continua sob ele depois do zoom.
    fun zoomAbout(t: ImageTransform, focalX: Float, focalY: Float, zoom: Float): ImageTransform {
        val newScale = (t.scale * zoom).coerceIn(1f, MAX_SCALE)
        val k = newScale / t.scale
        return clampTransform(
            ImageTransform(
                scale = newScale,
                tx = focalX - (focalX - t.tx) * k,
                ty = focalY - (focalY - t.ty) * k
            )
        )
    }

    fun panBy(t: ImageTransform, dx: Float, dy: Float): ImageTransform =
        clampTransform(t.copy(tx = t.tx + dx, ty = t.ty + dy))

    // Move a moldura inteira, mantendo o tamanho e sem deixá-la sair da área.
    fun moveFrame(f: CropFrame, dx: Float, dy: Float): CropFrame {
        val newLeft = clamp(f.left + dx, 0f, 1f - f.width)
        val newTop = clamp(f.top + dy, 0f, 1f - f.height)
        return CropFrame(newLeft, newTop, newLeft + f.width, newTop + f.height)
    }

    fun dragCorner(f: CropFrame, corner: CropCorner, dx: Float, dy: Float): CropFrame = when (corner) {
        CropCorner.TOP_LEFT -> f.copy(
            left = clamp(f.left + dx, 0f, f.right - MIN_FRAME_SIZE),
            top = clamp(f.top + dy, 0f, f.bottom - MIN_FRAME_SIZE)
        )
        CropCorner.TOP_RIGHT -> f.copy(
            right = clamp(f.right + dx, f.left + MIN_FRAME_SIZE, 1f),
            top = clamp(f.top + dy, 0f, f.bottom - MIN_FRAME_SIZE)
        )
        CropCorner.BOTTOM_LEFT -> f.copy(
            left = clamp(f.left + dx, 0f, f.right - MIN_FRAME_SIZE),
            bottom = clamp(f.bottom + dy, f.top + MIN_FRAME_SIZE, 1f)
        )
        CropCorner.BOTTOM_RIGHT -> f.copy(
            right = clamp(f.right + dx, f.left + MIN_FRAME_SIZE, 1f),
            bottom = clamp(f.bottom + dy, f.top + MIN_FRAME_SIZE, 1f)
        )
    }

    // Converte a moldura (posição na tela) na região correspondente da imagem original,
    // levando em conta o zoom/posição atuais. É isso que ImageEditUtils.cropBitmap espera.
    fun toImageFractions(f: CropFrame, t: ImageTransform): CropFrame = CropFrame(
        left = ((f.left - t.tx) / t.scale).coerceIn(0f, 1f),
        top = ((f.top - t.ty) / t.scale).coerceIn(0f, 1f),
        right = ((f.right - t.tx) / t.scale).coerceIn(0f, 1f),
        bottom = ((f.bottom - t.ty) / t.scale).coerceIn(0f, 1f)
    )
}
