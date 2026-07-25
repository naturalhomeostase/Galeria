package com.galeria.data.repository

import com.galeria.data.db.AlbumDao
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.AlbumPhotoCrossRef
import kotlinx.coroutines.flow.Flow

class AlbumRepository(private val dao: AlbumDao) {
    fun getAlbums(): Flow<List<AlbumEntity>> = dao.getAlbums()

    suspend fun getAlbum(id: Long) = dao.getAlbum(id)

    suspend fun createAlbum(name: String, isSecret: Boolean): Long =
        dao.insertAlbum(AlbumEntity(name = name, isSecret = isSecret))

    suspend fun renameAlbum(album: AlbumEntity, newName: String) =
        dao.updateAlbum(album.copy(name = newName))

    suspend fun setCover(album: AlbumEntity, coverUri: String) =
        dao.updateAlbum(album.copy(coverUri = coverUri))

    suspend fun deleteAlbum(albumId: Long) {
        dao.clearAlbumPhotos(albumId)
        dao.deleteAlbum(albumId)
    }

    suspend fun addPhoto(albumId: Long, photoUri: String) =
        dao.addPhotoToAlbum(AlbumPhotoCrossRef(albumId, photoUri))

    suspend fun removePhoto(albumId: Long, photoUri: String) =
        dao.removePhotoFromAlbum(albumId, photoUri)

    fun getPhotoUris(albumId: Long): Flow<List<String>> = dao.getPhotoUrisForAlbum(albumId)

    suspend fun countPhotos(albumId: Long): Int = dao.countPhotosInAlbum(albumId)
}
