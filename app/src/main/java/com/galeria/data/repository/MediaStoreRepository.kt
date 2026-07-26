package com.galeria.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.galeria.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val context: Context) {

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.DATA
    )

    suspend fun getAllPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val dateTaken = cursor.getLong(dateTakenCol).let {
                    if (it > 0L) it else cursor.getLong(dateAddedCol) * 1000L
                }
                photos.add(
                    Photo(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        dateTakenMillis = dateTaken,
                        dateAddedMillis = cursor.getLong(dateAddedCol) * 1000L,
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        bucketName = cursor.getString(bucketCol) ?: "",
                        path = cursor.getString(dataCol) ?: ""
                    )
                )
            }
        }
        photos
    }

    suspend fun getPhotosByUris(uris: List<String>): List<Photo> = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext emptyList()
        val all = getAllPhotos()
        val order = uris.withIndex().associate { (i, u) -> u to i }
        all.filter { it.uri.toString() in order }
            .sortedBy { order[it.uri.toString()] }
    }

    suspend fun getTrashedPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return@withContext emptyList()
        val photos = mutableListOf<Photo>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
        val queryArgs = android.os.Bundle().apply {
            putInt(
                MediaStore.QUERY_ARG_MATCH_TRASHED,
                MediaStore.MATCH_ONLY
            )
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
        }

        context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val dateTaken = cursor.getLong(dateTakenCol).let {
                    if (it > 0L) it else cursor.getLong(dateAddedCol) * 1000L
                }
                photos.add(
                    Photo(
                        id = id,
                        uri = uri,
                        displayName = cursor.getString(nameCol) ?: "",
                        dateTakenMillis = dateTaken,
                        dateAddedMillis = cursor.getLong(dateAddedCol) * 1000L,
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        mimeType = cursor.getString(mimeCol) ?: "image/*",
                        bucketName = cursor.getString(bucketCol) ?: "",
                        path = cursor.getString(dataCol) ?: ""
                    )
                )
            }
        }
        photos
    }

    suspend fun restoreFromTrash(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return@withContext false
        try {
            val values = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        @Volatile private var INSTANCE: MediaStoreRepository? = null
        fun getInstance(context: Context): MediaStoreRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaStoreRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
