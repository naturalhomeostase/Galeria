package com.galeria.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isSecret: Boolean = false,
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isHidden: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null
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

@Entity(tableName = "hidden_folders")
data class HiddenFolderEntity(
    @PrimaryKey val bucketName: String,
    val hiddenAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saf_folders")
data class SafFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treeUri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis()
)
