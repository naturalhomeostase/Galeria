package com.galeria.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSecret: Boolean = false,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "album_photos", primaryKeys = ["albumId", "photoUri"])
data class AlbumPhotoCrossRef(
    val albumId: Long,
    val photoUri: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val photoUri: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "security")
data class SecurityEntity(
    @PrimaryKey val id: Int = 0,
    val passwordHash: String? = null,
    val salt: String? = null,
    val biometricEnabled: Boolean = false
)
