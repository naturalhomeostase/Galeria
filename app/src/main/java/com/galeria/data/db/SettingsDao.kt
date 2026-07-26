package com.galeria.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.galeria.data.model.HiddenFolderEntity
import com.galeria.data.model.SafFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenFolderDao {
    @Query("SELECT bucketName FROM hidden_folders")
    fun getAll(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entity: HiddenFolderEntity)

    @Query("DELETE FROM hidden_folders WHERE bucketName = :bucketName")
    suspend fun unhide(bucketName: String)
}

@Dao
interface SafFolderDao {
    @Query("SELECT * FROM saf_folders ORDER BY addedAt DESC")
    fun getAll(): Flow<List<SafFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: SafFolderEntity)

    @Query("DELETE FROM saf_folders WHERE id = :id")
    suspend fun delete(id: Long)
}
