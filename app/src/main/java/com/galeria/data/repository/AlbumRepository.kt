package com.galeria.data.repository

import com.galeria.data.db.AlbumDao
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.AlbumPhotoCrossRef
import kotlinx.coroutines.flow.Flow

class AlbumRepository(private val dao: AlbumDao) {
    fun getAlbums(): Flow<List<AlbumEntity>> = dao.getAlbums()

    fun getTrashedAlbums(): Flow<List<AlbumEntity>> = dao.getTrashedAlbums()

    fun getAllCrossRefs(): Flow<List<AlbumPhotoCrossRef>> = dao.getAllCrossRefs()

    suspend fun getAlbum(id: Long) = dao.getAlbum(id)

    suspend fun createAlbum(name: String, isSecret: Boolean): Long =
        dao.insertAlbum(AlbumEntity(name = name, isSecret = isSecret))

    suspend fun renameAlbum(album: AlbumEntity, newName: String) =
        dao.updateAlbum(album.copy(name = newName, lastModifiedAt = System.currentTimeMillis()))

    // Exclusão definitiva (sem volta) -- usada só a partir da lixeira de álbuns.
    suspend fun deleteAlbum(albumId: Long) {
        dao.clearAlbumPhotos(albumId)
        dao.deleteAlbum(albumId)
    }

    // "Excluir" um álbum no dia a dia manda pra lixeira (reversível), igual já acontece com
    // fotos -- em vez de apagar de vez na hora, o que era arriscado sem nenhuma confirmação.
    suspend fun moveToTrash(albumId: Long) = dao.moveToTrash(albumId, System.currentTimeMillis())

    suspend fun restoreFromTrash(albumId: Long) = dao.restoreFromTrash(albumId)

    suspend fun setHidden(albumId: Long, hidden: Boolean) = dao.setHidden(albumId, hidden)

    suspend fun addPhoto(albumId: Long, photoUri: String) {
        dao.addPhotoToAlbum(AlbumPhotoCrossRef(albumId, photoUri))
        dao.touchAlbum(albumId, System.currentTimeMillis())
    }

    suspend fun removePhoto(albumId: Long, photoUri: String) {
        dao.removePhotoFromAlbum(albumId, photoUri)
        dao.touchAlbum(albumId, System.currentTimeMillis())
    }

    fun getPhotoUris(albumId: Long): Flow<List<String>> = dao.getPhotoUrisForAlbum(albumId)

    suspend fun countPhotos(albumId: Long): Int = dao.countPhotosInAlbum(albumId)
}
