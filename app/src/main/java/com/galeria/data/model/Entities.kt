package com.galeria.data.model

import androidx.room.ColumnInfo
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
    val biometricEnabled: Boolean = false,
    // 0 = hash legado (SHA-256 simples, de antes da versão 5 do banco); > 0 = número de
    // iterações do PBKDF2-HMAC-SHA256. Guardar isso por linha permite aumentar o custo no
    // futuro e regravar o hash de forma transparente no próximo login bem-sucedido.
    @ColumnInfo(defaultValue = "0") val iterations: Int = 0,
    // Tentativas erradas seguidas e até quando o cofre está bloqueado (epoch em ms). Ficam no
    // banco (e não em memória) para que fechar e reabrir o app não zere o limite de tentativas.
    @ColumnInfo(defaultValue = "0") val failedAttempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val lockedUntil: Long = 0L
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
