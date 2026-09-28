package com.galeria.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max

object ImageEditUtils {

    private const val MAX_DIMENSION = 2200

    fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        val (w, h) = bounds.outWidth to bounds.outHeight
        while ((w / sampleSize) > MAX_DIMENSION || (h / sampleSize) > MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        var bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        bitmap = applyExifRotation(context, uri, bitmap)
        return bitmap
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) rotateBitmap(bitmap, degrees) else bitmap
            } ?: bitmap
        } catch (_: IOException) {
            bitmap
        }
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun flipBitmap(bitmap: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun cropBitmap(bitmap: Bitmap, leftPct: Float, topPct: Float, rightPct: Float, bottomPct: Float): Bitmap {
        val left = (leftPct * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (topPct * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (rightPct * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bottomPct * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    // Reduz a imagem proporcionalmente até que a maior dimensão fique dentro de
    // maxDimension. Devolve o próprio bitmap sem mudanças se ele já for menor que isso --
    // comprimir "pra cima" (aumentar) não faz sentido aqui.
    fun resizeToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = max(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // Comprime em memória sem gravar nada em disco -- usado só pra estimar o tamanho final
    // enquanto a pessoa ainda está ajustando o slider de qualidade, antes de confirmar.
    fun jpegSizeBytes(bitmap: Bitmap, quality: Int): Long {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.size().toLong()
    }

    // Tamanho do arquivo original (antes de qualquer edição), usado só pra mostrar o
    // "antes/depois" na aba de compressão. Tenta a coluna SIZE do MediaStore primeiro (mais
    // barato) e cai pro tamanho real do descritor do arquivo se a coluna vier vazia.
    fun getFileSizeBytes(context: Context, uri: Uri): Long? {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    val size = cursor.getLong(sizeIndex)
                    if (size > 0) return size
                }
            }
        } catch (_: Exception) {
        }
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length.takeIf { len -> len >= 0 } }
        } catch (_: IOException) {
            null
        }
    }

    fun buildColorMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
        val satMatrix = ColorMatrix().apply { setSaturation(saturation) }

        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        contrastMatrix.postConcat(satMatrix)
        return contrastMatrix
    }

    fun saveToGallery(context: Context, bitmap: Bitmap, displayName: String, quality: Int = 92): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Galeria")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
