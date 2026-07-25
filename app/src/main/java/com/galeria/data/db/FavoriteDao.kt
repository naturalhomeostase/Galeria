package com.galeria.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.galeria.data.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT photoUri FROM favorites ORDER BY addedAt DESC")
    fun getFavoriteUris(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE photoUri = :photoUri")
    suspend fun remove(photoUri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE photoUri = :photoUri)")
    suspend fun isFavorite(photoUri: String): Boolean
}
