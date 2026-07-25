package com.galeria.data.db

import androidx.room.*
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.AlbumPhotoCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun getAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    suspend fun getAlbum(albumId: Long): AlbumEntity?

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Query("DELETE FROM album_photos WHERE albumId = :albumId")
    suspend fun clearAlbumPhotos(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addPhotoToAlbum(ref: AlbumPhotoCrossRef)

    @Query("DELETE FROM album_photos WHERE albumId = :albumId AND photoUri = :photoUri")
    suspend fun removePhotoFromAlbum(albumId: Long, photoUri: String)

    @Query("SELECT photoUri FROM album_photos WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun getPhotoUrisForAlbum(albumId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM album_photos WHERE albumId = :albumId")
    suspend fun countPhotosInAlbum(albumId: Long): Int
}
