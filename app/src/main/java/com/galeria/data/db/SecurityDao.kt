package com.galeria.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.galeria.data.model.SecurityEntity

@Dao
interface SecurityDao {
    @Query("SELECT * FROM security WHERE id = 0")
    suspend fun get(): SecurityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: SecurityEntity)
}
