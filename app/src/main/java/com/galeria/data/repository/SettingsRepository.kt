package com.galeria.data.repository

import com.galeria.data.db.HiddenFolderDao
import com.galeria.data.db.SafFolderDao
import com.galeria.data.model.HiddenFolderEntity
import com.galeria.data.model.SafFolderEntity
import kotlinx.coroutines.flow.Flow

class SettingsRepository(
    private val hiddenFolderDao: HiddenFolderDao,
    private val safFolderDao: SafFolderDao
) {
    fun getHiddenFolderNames(): Flow<List<String>> = hiddenFolderDao.getAll()

    suspend fun hideFolder(bucketName: String) = hiddenFolderDao.hide(HiddenFolderEntity(bucketName))

    suspend fun unhideFolder(bucketName: String) = hiddenFolderDao.unhide(bucketName)

    fun getSafFolders(): Flow<List<SafFolderEntity>> = safFolderDao.getAll()

    suspend fun addSafFolder(treeUri: String, displayName: String) =
        safFolderDao.insert(SafFolderEntity(treeUri = treeUri, displayName = displayName))

    suspend fun removeSafFolder(id: Long) = safFolderDao.delete(id)
}
