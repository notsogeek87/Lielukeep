package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.links.initialFolderId
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CreateBookmarkViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDataStore = SettingsDataStore(application)
    private val repository = KarabauRepository()
    private val cacheManager = LocalCacheManager(application)

    private val _tags = MutableStateFlow<ImmutableList<TagItem>>(persistentListOf())
    val tags: StateFlow<ImmutableList<TagItem>> = _tags.asStateFlow()

    private val _lists = MutableStateFlow<ImmutableList<SavedListItem>>(persistentListOf())
    val lists: StateFlow<ImmutableList<SavedListItem>> = _lists.asStateFlow()

    /** Dossier choisi (null = aucun). Présélectionné avec le dernier utilisé une fois les dossiers chargés. */
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    /** Lien déjà enregistré pour l'URL saisie (identifiant du signet), null si inconnu. */
    private val _duplicateBookmarkId = MutableStateFlow<String?>(null)
    val duplicateBookmarkId: StateFlow<String?> = _duplicateBookmarkId.asStateFlow()

    private var hasLoadedTags = false
    private var hasLoadedFolders = false
    private var folderChosenByUser = false
    private var duplicateJob: Job? = null

    fun loadTags() {
        if (hasLoadedTags && _tags.value.isNotEmpty()) return
        viewModelScope.launch {
            if (_tags.value.isEmpty()) {
                val cached = cacheManager.loadCachedTags()
                if (!cached.isNullOrEmpty()) {
                    _tags.value = cached.toImmutableList()
                }
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (
                val result = repository.getTags(
                    limit = 50,
                    sortBy = "usage",
                    page = 0
                )
            ) {
                is ApiResult.Success -> {
                    hasLoadedTags = true
                    _tags.value = result.data.toImmutableList()
                    launch { cacheManager.saveTags(result.data) }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun loadFolders() {
        if (hasLoadedFolders) return
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val result = repository.getLists()) {
                is ApiResult.Success -> {
                    hasLoadedFolders = true
                    _lists.value = result.data.toImmutableList()
                    if (!folderChosenByUser) {
                        _selectedFolderId.value = initialFolderId(settings.lastFolderId, result.data)
                    }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    fun selectFolder(folderId: String?) {
        folderChosenByUser = true
        _selectedFolderId.value = folderId
    }

    /** Crée un dossier (ou un sous-dossier) et le sélectionne ; renvoie le résultat pour afficher une erreur. */
    suspend fun createFolder(name: String, parentId: String?): ApiResult<SavedListItem> {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val result = repository.createList(name = name, parentId = parentId)
        if (result is ApiResult.Success) {
            _lists.value = (_lists.value + result.data).toImmutableList()
            selectFolder(result.data.id)
        }
        return result
    }

    /** Variante pour l'interface : message d'erreur à afficher, ou null si le dossier est créé. */
    suspend fun createFolderOrError(name: String, parentId: String?): String? =
        when (val result = createFolder(name, parentId)) {
            is ApiResult.Success -> null
            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }

    /** Vérifie (après une courte pause de frappe) si l'URL est déjà enregistrée. */
    fun checkDuplicate(url: String) {
        duplicateJob?.cancel()
        _duplicateBookmarkId.value = null
        if (url.isBlank()) return
        duplicateJob = viewModelScope.launch {
            delay(500)
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)
            when (val result = repository.findBookmarkIdByUrl(url)) {
                is ApiResult.Success -> _duplicateBookmarkId.value = result.data
                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    suspend fun submitBookmark(
        url: String,
        title: String,
        note: String,
        tags: List<String> = emptyList()
    ): ApiResult<Unit> {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)

        val folderId = _selectedFolderId.value
        val saved = repository.saveLink(
            url = url.trim(),
            title = title.trim().takeIf { it.isNotEmpty() },
            note = note.trim().takeIf { it.isNotEmpty() },
            tags = tags
        )
        val savedLink = when (saved) {
            is ApiResult.Success -> saved.data
            is ApiResult.Error -> return saved
            is ApiResult.NetworkError -> return saved
        }

        if (folderId != null && savedLink.bookmarkId != null) {
            when (val added = repository.addBookmarkToList(folderId, savedLink.bookmarkId)) {
                is ApiResult.Success -> Unit
                // Le lien est enregistré : on le signale quand même, le dossier n'a pas pu être appliqué.
                is ApiResult.Error -> return added
                is ApiResult.NetworkError -> return added
            }
        }
        // Mémorisé même quand l'utilisateur choisit « Aucun dossier » (null).
        if (folderChosenByUser) settingsDataStore.setLastFolderId(folderId)
        return ApiResult.Success(Unit)
    }
}
