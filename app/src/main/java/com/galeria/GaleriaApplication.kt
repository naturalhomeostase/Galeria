package com.galeria

import android.app.Application
import android.graphics.Bitmap
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import com.galeria.data.coil.MediaStoreThumbnailFetcher
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

        val imageLoader = ImageLoader.Builder(this)
            .components {
                // Miniaturas do MediaStore (rápido, via cache de thumbnails do sistema) antes
                // de decodificar frames de vídeo ou o arquivo original inteiro — é o principal
                // ganho de fluidez ao rolar a grade rapidamente.
                add(MediaStoreThumbnailFetcher.Factory(applicationContext))
                add(VideoFrameDecoder.Factory())
            }
            // RGB_565 usa metade da memória por pixel do padrão (ARGB_8888), o que reduz bastante
            // o trabalho de decodificação/composição ao rolar uma grade com muitas miniaturas.
            // A perda de precisão de cor é imperceptível em thumbnails pequenos.
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.3)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
