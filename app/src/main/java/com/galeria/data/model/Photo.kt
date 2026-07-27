package com.galeria.data.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTakenMillis: Long,
    val dateAddedMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val bucketName: String,
    val path: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L
)
