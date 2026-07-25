package com.galeria.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.AlbumPhotoCrossRef
import com.galeria.data.model.FavoriteEntity
import com.galeria.data.model.SecurityEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumPhotoCrossRef::class,
        FavoriteEntity::class,
        SecurityEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun securityDao(): SecurityDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "galeria.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
