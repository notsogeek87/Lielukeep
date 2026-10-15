package com.lielu.keep.ui.home

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lielu.keep.backup.BackupManager
import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.links.LinkFilter
import com.lielu.keep.data.links.LinksRepository
import com.lielu.keep.data.links.filterLinks
import com.lielu.keep.data.settings.SettingsRepository
import com.lielu.keep.ui.describeImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Puce de filtre « dossier » : libellé (« Parent › Enfant ») et nombre de liens, sous-dossiers compris. */
data class FolderOption(
    val folder: FolderEntity,
    val label: String,
    val count: Int,
)

data class HomeUiState(
    val query: String = "",
    val filter: LinkFilter = LinkFilter.All,
    /** Liens après filtre et recherche. */
    val links: List<LinkEntity> = emptyList(),
    val totalCount: Int = 0,
    val favoriteCount: Int = 0,
    val readLaterCount: Int = 0,
    val folderOptions: List<FolderOption> = emptyList(),
    val folderNames: Map<Long, String> = emptyMap(),
    /** Faux tant que la base n'a pas répondu : évite de montrer l'état vide une fraction de seconde. */
    val loaded: Boolean = false,
)

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
    private val linksRepository: LinksRepository,
) : ViewModel() {
    /**
     * Vrai tant que l'invitation de bienvenue n'a pas été traitée. Initialement faux : on n'affiche rien
     * avant que le DataStore ait répondu, pour ne pas faire clignoter l'invitation à chaque lancement.
     */
    val showWelcome: StateFlow<Boolean> =
        settingsRepository.welcomeShown
            .map { !it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<LinkFilter>(LinkFilter.All)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /** Tous les liens actifs, avant filtre : les actions (basculer un favori…) lisent la valeur courante ici. */
    private var current: List<LinkEntity> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                linksRepository.observeActiveLinks(),
                linksRepository.observeFolders(),
                query,
                filter,
            ) { links, folders, q, f ->
                current = links
                buildState(links, folders, q, f)
            }.collect { _state.value = it }
        }
    }

    private fun buildState(
        links: List<LinkEntity>,
        folders: List<FolderEntity>,
        query: String,
        filter: LinkFilter,
    ): HomeUiState {
        val names = folders.associate { it.id to it.name }
        val options =
            folders
                .sortedWith(
                    compareBy({ (it.parentId ?: it.id) }, { it.parentId != null }, { it.position }, { it.name }),
                ).map { folder ->
                    val label = folder.parentId?.let { names[it] }?.let { "$it › ${folder.name}" } ?: folder.name
                    FolderOption(folder, label, filterLinks(links, folders, LinkFilter.Folder(folder.id), "").size)
                }
        return HomeUiState(
            query = query,
            filter = filter,
            links = filterLinks(links, folders, filter, query),
            totalCount = links.size,
            favoriteCount = links.count { it.favorite },
            readLaterCount = links.count { it.readLater },
            folderOptions = options,
            folderNames = names,
            loaded = true,
        )
    }

    fun onQueryChange(value: String) = query.update { value }

    fun onFilterSelected(value: LinkFilter) = filter.update { value }

    fun toggleFavorite(id: Long) {
        val link = current.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { linksRepository.setFavorite(id, !link.favorite) }
    }

    fun toggleReadLater(id: Long) {
        val link = current.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { linksRepository.setReadLater(id, !link.readLater) }
    }

    fun archive(id: Long) = viewModelScope.launch { linksRepository.setArchived(id, true) }

    fun unarchive(id: Long) = viewModelScope.launch { linksRepository.setArchived(id, false) }

    fun trash(id: Long) = viewModelScope.launch { linksRepository.setDeleted(id, true) }

    fun restoreFromTrash(id: Long) = viewModelScope.launch { linksRepository.setDeleted(id, false) }

    /** Ouvrir un lien le retire de « À lire plus tard » : la liste ne garde que ce qui reste à lire. */
    fun onOpened(id: Long) {
        val link = current.firstOrNull { it.id == id }
        viewModelScope.launch {
            linksRepository.markOpened(id)
            if (link?.readLater == true) linksRepository.setReadLater(id, false)
        }
    }

    fun onWelcomeDismissed() {
        viewModelScope.launch { settingsRepository.setWelcomeShown() }
    }

    /** Restauration proposée au premier lancement (réinstallation, nouvel appareil) depuis un fichier choisi. */
    fun onImportRequested(
        contentResolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            _message.value =
                try {
                    val stream =
                        contentResolver.openInputStream(uri) ?: error("Impossible de lire le fichier sélectionné")
                    val result = stream.use { backupManager.importFrom(it) }
                    settingsRepository.setWelcomeShown()
                    describeImport(result)
                } catch (e: Exception) {
                    "Échec de la restauration : ${e.message}"
                }
        }
    }

    fun onMessageShown() {
        _message.value = null
    }
}
