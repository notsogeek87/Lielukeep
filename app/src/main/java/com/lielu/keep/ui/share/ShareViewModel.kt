package com.lielu.keep.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.links.LinksRepository
import com.lielu.keep.data.links.UrlTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareUiState(
    /** URL nettoyée, ou null si le texte partagé n'en contient pas. */
    val url: String? = null,
    val title: String = "",
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolderId: Long? = null,
    /** Lien identique déjà présent : l'enregistrement demande alors une confirmation explicite. */
    val duplicate: LinkEntity? = null,
    val saving: Boolean = false,
    /** Renseigné une fois le lien écrit : l'écran se ferme et affiche le nom du dossier. */
    val savedFolderName: String? = null,
    val saved: Boolean = false,
    /** Échec affichable (création de dossier hors ligne…), à montrer puis effacer. */
    val error: String? = null,
)

/**
 * Logique de la feuille « Enregistrer le lien » ouverte depuis le menu Partager. L'enregistrement est local et
 * immédiat : titre et image sont récupérés plus tard, en arrière-plan, pour ne jamais bloquer ni perdre un lien.
 */
class ShareViewModel(
    private val repository: LinksRepository,
    sharedText: String?,
    sharedSubject: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state

    init {
        val rawUrl = UrlTools.extractUrl(sharedText) ?: UrlTools.extractUrl(sharedSubject)
        val url = rawUrl?.let(UrlTools::clean)
        val subject = sharedSubject?.trim().orEmpty()
        val title = if (subject.isNotEmpty() && UrlTools.extractUrl(subject) == null) subject else ""
        _state.value = ShareUiState(url = url, title = title)
        if (url != null) {
            viewModelScope.launch {
                val duplicate = repository.findActiveLink(url)
                val suggested = repository.suggestedFolderId()
                _state.update { it.copy(duplicate = duplicate, selectedFolderId = duplicate?.folderId ?: suggested) }
            }
            viewModelScope.launch {
                repository.observeFolders().collect { folders -> _state.update { it.copy(folders = folders) } }
            }
        }
    }

    fun onTitleChange(title: String) = _state.update { it.copy(title = title) }

    fun onFolderSelected(folderId: Long?) = _state.update { it.copy(selectedFolderId = folderId) }

    fun onCreateFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val id = repository.createFolder(name)
                _state.update { it.copy(selectedFolderId = id, error = null) }
            } catch (e: KarakeepException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun onSave() {
        val current = _state.value
        val url = current.url ?: return
        if (current.saving || current.saved) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            // Titre vide = laisser Karakeep récupérer celui de la page ; l'écran affiche le site en attendant.
            repository.saveLink(url, current.title.trim(), current.selectedFolderId)
            val folderName = current.folders.firstOrNull { it.id == current.selectedFolderId }?.name
            _state.update { it.copy(saving = false, saved = true, savedFolderName = folderName) }
        }
    }
}
