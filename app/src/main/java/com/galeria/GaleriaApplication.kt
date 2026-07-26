package com.galeria

import android.app.Application
import com.galeria.data.db.AppDatabase
import com.galeria.data.repository.AlbumRepository
import com.galeria.data.repository.FavoriteRepository
import com.galeria.data.repository.MediaStoreRepository
import com.galeria.data.repository.SecurityRepository
import com.galeria.data.repository.SettingsRepository

class GaleriaApplication : Application() {

    lateinit var mediaStoreRepository: MediaStoreRepository
        private set
    lateinit var albumRepository: AlbumRepository
        private set
    lateinit var favoriteRepository: FavoriteRepository
        private set
    lateinit var securityRepository: SecurityRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(this)
        mediaStoreRepository = MediaStoreRepository.getInstance(this)
        albumRepository = AlbumRepository(db.albumDao())
        favoriteRepository = FavoriteRepository(db.favoriteDao())
        securityRepository = SecurityRepository(db.securityDao())
        settingsRepository = SettingsRepository(db.hiddenFolderDao(), db.safFolderDao())
    }
}
