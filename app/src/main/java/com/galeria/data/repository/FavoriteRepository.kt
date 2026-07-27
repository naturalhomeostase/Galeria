package com.galeria.data.repository

import com.galeria.data.db.FavoriteDao
import com.galeria.data.model.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val dao: FavoriteDao) {
    fun getFavoriteUris(): Flow<List<String>> = dao.getFavoriteUris()

    suspend fun toggle(photoUri: String) {
        if (dao.isFavorite(photoUri)) {
            dao.remove(photoUri)
        } else {
            dao.add(FavoriteEntity(photoUri))
        }
    }

    suspend fun setFavorite(photoUri: String, value: Boolean) {
        if (value) dao.add(FavoriteEntity(photoUri)) else dao.remove(photoUri)
    }

    suspend fun isFavorite(photoUri: String): Boolean = dao.isFavorite(photoUri)
}
