package com.galeria.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galeria.GaleriaApplication
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.Photo
import com.galeria.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthGroup(val key: String, val label: String, val photos: List<Photo>)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as GaleriaApplication

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    val allPhotos: StateFlow<List<Photo>> = _allPhotos

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    val favoriteUris: StateFlow<Set<String>> = app.favoriteRepository.getFavoriteUris()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val albums: StateFlow<List<AlbumEntity>> = app.albumRepository.getAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthGroups: StateFlow<List<MonthGroup>> = _allPhotos
        .map { photos -> groupPhotosByMonth(photos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun groupPhotosByMonth(photos: List<Photo>): List<MonthGroup> {
        return photos.groupBy { DateUtils.monthYearKey(it.dateTakenMillis) }
            .map { (key, list) ->
                MonthGroup(key, DateUtils.monthYearLabel(list.first().dateTakenMillis), list)
            }
            .sortedByDescending { it.photos.first().dateTakenMillis }
    }

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadPhotos()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _allPhotos.value = app.mediaStoreRepository.getAllPhotos()
        }
    }

    fun toggleFavorite(photoUri: String) {
        viewModelScope.launch {
            app.favoriteRepository.toggle(photoUri)
        }
    }

    fun createAlbum(name: String, isSecret: Boolean, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = app.albumRepository.createAlbum(name, isSecret)
            onCreated(id)
        }
    }

    fun addPhotoToAlbum(albumId: Long, photoUri: String) {
        viewModelScope.launch { app.albumRepository.addPhoto(albumId, photoUri) }
    }

    fun removePhotoFromAlbum(albumId: Long, photoUri: String) {
        viewModelScope.launch { app.albumRepository.removePhoto(albumId, photoUri) }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { app.albumRepository.deleteAlbum(albumId) }
    }

    fun getPhotoByUri(uriString: String): Photo? =
        _allPhotos.value.firstOrNull { it.uri.toString() == uriString }

    fun resolvePhotos(uris: List<String>): List<Photo> {
        val byUri = _allPhotos.value.associateBy { it.uri.toString() }
        return uris.mapNotNull { byUri[it] }
    }

    suspend fun hasSecretPassword(): Boolean = app.securityRepository.hasPassword()

    suspend fun setSecretPassword(password: String) = app.securityRepository.setPassword(password)

    suspend fun verifySecretPassword(password: String): Boolean =
        app.securityRepository.verifyPassword(password)

    suspend fun isBiometricEnabled(): Boolean = app.securityRepository.isBiometricEnabled()

    suspend fun setBiometricEnabled(enabled: Boolean) =
        app.securityRepository.setBiometricEnabled(enabled)
}
