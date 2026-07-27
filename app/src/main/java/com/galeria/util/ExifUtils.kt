package com.galeria.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExifInfo(
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val flash: String? = null,
    val gpsLatLong: String? = null
) {
    val hasCameraInfo: Boolean
        get() = cameraMake != null || cameraModel != null || fNumber != null ||
            exposureTime != null || iso != null || focalLength != null
}

object ExifUtils {
    suspend fun readExif(context: Context, uri: Uri): ExifInfo = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)

                val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    .takeIf { it > 0 }?.let { "f/%.1f".format(it) }

                val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    .takeIf { it > 0 }?.let { formatExposureTime(it) }

                val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    .takeIf { it > 0 }?.toString()
                    ?: exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED, 0).takeIf { it > 0 }?.toString()

                val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    .takeIf { it > 0 }?.let { "%.1f mm".format(it) }

                val flashValue = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
                val flash = if (flashValue >= 0) {
                    if (flashValue and 0x1 != 0) "Disparado" else "Não disparado"
                } else null

                val latLong = exif.latLong
                val gps = latLong?.let { "%.5f, %.5f".format(it[0], it[1]) }

                ExifInfo(
                    cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()?.takeIf { it.isNotBlank() },
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotBlank() },
                    fNumber = fNumber,
                    exposureTime = exposure,
                    iso = iso?.let { "ISO $it" },
                    focalLength = focalLength,
                    flash = flash,
                    gpsLatLong = gps
                )
            } ?: ExifInfo()
        } catch (_: Exception) {
            ExifInfo()
        }
    }

    private fun formatExposureTime(seconds: Double): String {
        return if (seconds < 1.0) {
            val denominator = (1.0 / seconds).let { if (it >= 10) it.toInt() else "%.1f".format(it) }
            "1/$denominator s"
        } else {
            "%.1f s".format(seconds)
        }
    }
}
