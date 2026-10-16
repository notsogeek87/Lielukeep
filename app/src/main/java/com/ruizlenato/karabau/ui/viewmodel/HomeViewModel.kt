package com.ruizlenato.karabau.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruizlenato.karabau.data.links.LinkFilter
import com.ruizlenato.karabau.data.links.filterLinks
import com.ruizlenato.karabau.data.links.folderAndChildrenIds
import com.ruizlenato.karabau.data.links.foldersOf
import com.ruizlenato.karabau.data.links.isReadLater
import com.ruizlenato.karabau.data.links.isReadLaterTag
import com.ruizlenato.karabau.data.links.READ_LATER_TAG
import com.ruizlenato.karabau.data.links.UrlTools
import com.ruizlenato.karabau.data.links.normalizeForSearch
import com.ruizlenato.karabau.data.local.LocalCacheManager
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.ArchiveDisplayBehaviour
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isTagsLoading: Boolean = false,
    val isTagsRefreshing: Boolean = false,
    val isListsLoading: Boolean = false,
    val isListsRefreshing: Boolean = false,
    val bookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val tags: ImmutableList<TagItem> = persistentListOf(),
    val tagsErrorMessage: String? = null,
    val lists: ImmutableList<SavedListItem> = persistentListOf(),
    val listsErrorMessage: String? = null,
    val selectedTag: TagItem? = null,
    val selectedTagDetails: TagItem? = null,
    val selectedList: SavedListItem? = null,
    val selectedListDetails: SavedListItem? = null,
    val isTagBookmarksLoading: Boolean = false,
    val isListBookmarksLoading: Boolean = false,
    val tagBookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val listBookmarks: ImmutableList<BookmarkItem> = persistentListOf(),
    val tagBookmarksErrorMessage: String? = null,
    val listBookmarksErrorMessage: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val homeFilter: LinkFilter = LinkFilter.All,
    /** Liens de chaque dossier (identifiant de liste → identifiants de signets), pour filtrer l'accueil. */
    val folderMembership: Map<String, Set<String>> = emptyMap(),
    val profileName: String? = null,
    val profileImage: String? = null,
    val profileImageHeaders: ImmutableMap<String, String> = persistentMapOf(),
    val hasCompletedInitialBookmarksLoad: Boolean = false,
    val errorMessage: String? = null,
    /** Vrai quand le message d'erreur vient d'un serveur injoignable (réseau), faux pour une réponse d'erreur HTTP. */
    val errorIsNetwork: Boolean = false,
    /** Dernière synchronisation en échec alors qu'une copie locale est affichée (serveur KO : on garde la liste). */
    val syncFailure: SyncFailure? = null
)

enum class SyncFailure { Network, Server }

/** Rappel d'une action en échec : `isNetwork` = serveur injoignable, sinon le serveur a répondu par une erreur. */
typealias ActionFailure = (isNetwork: Boolean, message: String) -> Unit

private fun ApiResult<*>.reportFailure(onFailed: ActionFailure) {
    when (this) {
        is ApiResult.Success -> Unit
        is ApiResult.Error -> onFailed(false, message)
        is ApiResult.NetworkError -> onFailed(true, message)
    }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val FAVORITES_LIST_ID = "__favorites__"
        private const val CACHE_SAVE_DEBOUNCE_MS = 500L
    }

    private val settingsDataStore = SettingsDataStore(application)
    private val cacheManager = LocalCacheManager(application)
    private val repository = KarabauRepository()

    private var cachedProfileHeadersKey: Triple<String, String?, String>? = null
    private var cachedProfileHeadersMap: Map<String, String> = emptyMap()

    private var searchDebounceJob: Job? = null
    private var tagDetailJob: Job? = null
    private var listDetailJob: Job? = null
    private var cacheSaveJob: Job? = null
    private var hasLoadedItems = false
    private var hasLoadedTags = false
    private var hasLoadedLists = false
    private val pendingDeleteJobs = mutableMapOf<String, Job>()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _displayedBookmarks = MutableStateFlow<ImmutableList<BookmarkItem>>(persistentListOf())
    val displayedBookmarks: StateFlow<ImmutableList<BookmarkItem>> = _displayedBookmarks.asStateFlow()

    fun loadSavedItems() {
        if (hasLoadedItems && _uiState.value.bookmarks.isNotEmpty()) return
        viewModelScope.launch {
            val settingsDeferred = async { settingsDataStore.settingsFlow.first() }

            if (_uiState.value.bookmarks.isEmpty()) {
                val cached = cacheManager.loadCachedBookmarks()
                if (!cached.isNullOrEmpty()) {
                    val displayed = computeDisplayedBookmarks(
                        bookmarks = cached,
                        query = _uiState.value.searchQuery,
                        isSearchActive = _uiState.value.isSearchActive
                    )
                    _uiState.update {
                        it.copy(
                            bookmarks = cached.toImmutableList()
                        )
                    }
                    _displayedBookmarks.value = displayed.toImmutableList()
                }
            }

            val settings = settingsDeferred.await()

            cacheManager.loadCachedProfile(profileCacheKey(settings))?.let { cachedProfile ->
                val profileImage = resolveProfileImageUrl(
                    serverAddress = settings.address,
                    image = cachedProfile.profileImage
                )
                val profileImageHeaders = buildProfileImageHeaders(
                    settings = settings,
                    image = cachedProfile.profileImage
                )

                _uiState.update {
                    it.copy(
                        profileName = cachedProfile.profileName,
                        profileImage = profileImage,
                        profileImageHeaders = profileImageHeaders.toImmutableMap()
                    )
                }
            }

            loadSavedItemsInternal(isRefresh = false, preloadedSettings = settings)
        }
    }

    fun refreshSavedItems() {
        viewModelScope.launch {
            loadSavedItemsInternal(isRefresh = true)
        }
    }

    fun syncArchiveDisplayBehaviourAfterInitialLoad() {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val remote = repository.getArchiveDisplayBehaviour()) {
                is ApiResult.Success -> {
                    if (settings.archiveDisplayBehaviour != remote.data) {
                        settingsDataStore.updateSettings(
                            settings.copy(archiveDisplayBehaviour = remote.data)
                        )
                    }
                }

                is ApiResult.Error -> Unit
                is ApiResult.NetworkError -> Unit
            }
        }
    }

    private suspend fun loadSavedItemsInternal(isRefresh: Boolean, preloadedSettings: Settings? = null) {
        val hasExistingData = _uiState.value.bookmarks.isNotEmpty()
        _uiState.update {
            it.copy(
                isLoading = if (isRefresh || hasExistingData) it.isLoading else true,
                isRefreshing = isRefresh,
                errorMessage = null
            )
        }

        val settings = preloadedSettings ?: settingsDataStore.settingsFlow.first()
        repository.configure(settings)

        val (userResult, bookmarksResult) = coroutineScope {
            val userDeferred = async { repository.whoAmI() }
            val bookmarksDeferred = async { repository.getAllBookmarks(archived = false) }
            userDeferred.await() to bookmarksDeferred.await()
        }

        when (userResult) {
            is ApiResult.Success -> {
                val profileImage = resolveProfileImageUrl(
                    serverAddress = settings.address,
                    image = userResult.data.image
                )
                val profileImageHeaders = buildProfileImageHeaders(
                    settings = settings,
                    image = userResult.data.image
                )

                _uiState.update {
                    it.copy(
                        profileName = userResult.data.name,
                        profileImage = profileImage,
                        profileImageHeaders = profileImageHeaders.toImmutableMap()
                    )
                }

                viewModelScope.launch {
                    cacheManager.saveProfile(
                        cacheKey = profileCacheKey(settings),
                        profileName = userResult.data.name,
                        profileImage = userResult.data.image
                    )
                }
            }

            is ApiResult.Error -> {
                // no-op
            }

            is ApiResult.NetworkError -> {
                // no-op
            }
        }

        when (bookmarksResult) {
            is ApiResult.Success -> {
                hasLoadedItems = true
                val bookmarksData = bookmarksResult.data
                val displayed = computeDisplayedBookmarks(
                    bookmarks = bookmarksData,
                    query = _uiState.value.searchQuery,
                    isSearchActive = _uiState.value.isSearchActive
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        bookmarks = bookmarksData.toImmutableList(),
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = null,
                        errorIsNetwork = false,
                        syncFailure = null
                    )
                }
                _displayedBookmarks.value = displayed.toImmutableList()
                viewModelScope.launch { cacheManager.saveBookmarks(bookmarksData) }
            }

            is ApiResult.Error -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = if ((isRefresh || hasExistingData) && it.bookmarks.isNotEmpty()) null else bookmarksResult.message,
                        errorIsNetwork = false,
                        syncFailure = if (it.bookmarks.isNotEmpty()) SyncFailure.Server else null
                    )
                }
            }

            is ApiResult.NetworkError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        hasCompletedInitialBookmarksLoad = true,
                        errorMessage = if ((isRefresh || hasExistingData) && it.bookmarks.isNotEmpty()) null else bookmarksResult.message,
                        errorIsNetwork = true,
                        syncFailure = if (it.bookmarks.isNotEmpty()) SyncFailure.Network else null
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query)
        }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(300)
            val bookmarks = _uiState.value.bookmarks
            val isSearchActive = _uiState.value.isSearchActive
            val displayed = withContext(Dispatchers.Default) {
                computeDisplayedBookmarks(
                    bookmarks = bookmarks,
                    query = query,
                    isSearchActive = isSearchActive
                )
            }
            _displayedBookmarks.value = displayed.toImmutableList()
        }
    }

    fun onSearchActiveChange(active: Boolean) {
        _uiState.update { state ->
            state.copy(isSearchActive = active)
        }
        viewModelScope.launch {
            val bookmarks = _uiState.value.bookmarks
            val query = _uiState.value.searchQuery
            val displayed = withContext(Dispatchers.Default) {
                computeDisplayedBookmarks(
                    bookmarks = bookmarks,
                    query = query,
                    isSearchActive = active
                )
            }
            _displayedBookmarks.value = displayed.toImmutableList()
        }
    }

    fun onFilterChange(filter: LinkFilter) {
        _uiState.update { it.copy(homeFilter = filter) }
        refreshDisplayed()
    }

    private fun refreshDisplayed() {
        val state = _uiState.value
        _displayedBookmarks.value = computeDisplayedBookmarks(
            bookmarks = state.bookmarks,
            query = state.searchQuery,
            isSearchActive = state.isSearchActive
        ).toImmutableList()
    }

    /** Charge le contenu de chaque dossier : Karakeep ne dit pas, sur un lien, dans quelles listes il se trouve. */
    private fun loadFolderMembership(lists: List<SavedListItem>) {
        viewModelScope.launch {
            val folders = foldersOf(lists)
            if (folders.isEmpty()) {
                _uiState.update { it.copy(folderMembership = emptyMap()) }
                refreshDisplayed()
                return@launch
            }
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val loaded = coroutineScope {
                folders.map { folder ->
                    async {
                        when (val result = repository.getAllBookmarksByList(listId = folder.id, archived = false, limit = 100)) {
                            is ApiResult.Success -> folder.id to result.data.mapTo(mutableSetOf()) { it.id }
                            is ApiResult.Error -> null
                            is ApiResult.NetworkError -> null
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
            // Un dossier dont le chargement a échoué garde son contenu précédent plutôt que de s'afficher vide.
            _uiState.update { it.copy(folderMembership = it.folderMembership.filterKeys { id -> folders.any { f -> f.id == id } } + loaded) }
            refreshDisplayed()
        }
    }

    fun toggleBookmarkReadLater(
        bookmark: BookmarkItem,
        onFailed: ActionFailure = { _, _ -> },
        onUpdated: (BookmarkItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val enable = !bookmark.isReadLater()
            when (val result = repository.setBookmarkReadLater(bookmark.id, bookmark.tags, enable)) {
                is ApiResult.Success -> {
                    val newTags = if (enable) {
                        bookmark.tags + READ_LATER_TAG
                    } else {
                        bookmark.tags.filterNot(::isReadLaterTag)
                    }
                    val updated = bookmark.copy(tags = newTags.toImmutableList())
                    applyBookmarkUpdate(updated)
                    onUpdated(updated)
                }

                is ApiResult.Error, is ApiResult.NetworkError -> result.reportFailure(onFailed)
            }
        }
    }

    /** Dossier(s) où se trouve un lien d'après le contenu chargé des dossiers. */
    fun folderIdsOf(bookmarkId: String): Set<String> =
        _uiState.value.folderMembership.filterValues { bookmarkId in it }.keys

    /**
     * Applique les modifications d'un lien (titre, URL, note, étiquettes, dossier), seulement celles qui changent.
     * Renvoie le lien à jour, ou un message d'erreur ; en cas d'échec en cours de route, ce qui a déjà été appliqué
     * côté serveur est quand même reflété dans la liste.
     */
    suspend fun editBookmark(
        original: BookmarkItem,
        title: String,
        url: String,
        note: String,
        tags: List<String>,
        folderId: String?
    ): Result<BookmarkItem> {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)

        var current = original
        fun failure(message: String): Result<BookmarkItem> {
            if (current != original) applyBookmarkUpdate(current)
            return Result.failure(IllegalStateException(message))
        }

        val newTitle = title.trim()
        val newNote = note.trim()
        val newUrl = url.trim()
        val titleChanged = newTitle != original.title.orEmpty()
        val noteChanged = newNote != original.note.orEmpty()
        val urlChanged = original.linkUrl != null && newUrl.isNotEmpty() && newUrl != original.linkUrl
        if (titleChanged || noteChanged || urlChanged) {
            val result = repository.updateBookmarkDetails(
                bookmarkId = original.id,
                title = newTitle.takeIf { titleChanged },
                note = newNote.takeIf { noteChanged },
                url = newUrl.takeIf { urlChanged }
            )
            when (result) {
                is ApiResult.Success -> current = current.copy(
                    title = newTitle.ifEmpty { null },
                    note = newNote.ifEmpty { null },
                    linkUrl = if (urlChanged) newUrl else current.linkUrl,
                    subtitle = if (urlChanged) UrlTools.domainOf(newUrl).ifEmpty { current.subtitle } else current.subtitle
                )

                is ApiResult.Error -> return failure(result.message)
                is ApiResult.NetworkError -> return failure(result.message)
            }
        }

        val wanted = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy(::normalizeForSearch)
        val have = original.tags
        val attach = wanted.filter { w -> have.none { normalizeForSearch(it) == normalizeForSearch(w) } }
        val detach = have.filter { h -> wanted.none { normalizeForSearch(it) == normalizeForSearch(h) } }
        if (attach.isNotEmpty() || detach.isNotEmpty()) {
            when (val result = repository.updateBookmarkTagNames(original.id, attach, detach)) {
                is ApiResult.Success -> {
                    val kept = have.filter { it !in detach }
                    current = current.copy(tags = (kept + attach).toImmutableList())
                }

                is ApiResult.Error -> return failure(result.message)
                is ApiResult.NetworkError -> return failure(result.message)
            }
        }

        val currentFolders = folderIdsOf(original.id)
        if (currentFolders != setOfNotNull(folderId)) {
            for (id in currentFolders - setOfNotNull(folderId)) {
                when (val result = repository.removeBookmarkFromList(id, original.id)) {
                    is ApiResult.Success -> setMembership(id, original.id, member = false)
                    is ApiResult.Error -> return failure(result.message)
                    is ApiResult.NetworkError -> return failure(result.message)
                }
            }
            if (folderId != null && folderId !in currentFolders) {
                when (val result = repository.addBookmarkToList(folderId, original.id)) {
                    is ApiResult.Success -> setMembership(folderId, original.id, member = true)
                    is ApiResult.Error -> return failure(result.message)
                    is ApiResult.NetworkError -> return failure(result.message)
                }
            }
        }

        applyBookmarkUpdate(current)
        return Result.success(current)
    }

    private fun setMembership(folderId: String, bookmarkId: String, member: Boolean) {
        _uiState.update { state ->
            val members = state.folderMembership[folderId].orEmpty()
            state.copy(folderMembership = state.folderMembership + (folderId to if (member) members + bookmarkId else members - bookmarkId))
        }
        refreshDisplayed()
    }

    /** Annule un archivage : le lien est désarchivé côté Karakeep puis remis dans la liste. */
    fun unarchiveBookmark(bookmark: BookmarkItem, onFailed: ActionFailure = { _, _ -> }) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val result = repository.setBookmarkArchived(bookmark.id, false)) {
                is ApiResult.Success -> applyBookmarkRestore(bookmark.copy(archived = false))
                is ApiResult.Error, is ApiResult.NetworkError -> result.reportFailure(onFailed)
            }
        }
    }

    /** Les trois opérations de dossier renvoient un message d'erreur à afficher, ou null en cas de succès. */
    suspend fun createFolder(name: String, parentId: String?): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return when (val result = repository.createList(name = name, parentId = parentId)) {
            is ApiResult.Success -> {
                _uiState.update {
                    it.copy(
                        lists = (it.lists + result.data).toImmutableList(),
                        folderMembership = it.folderMembership + (result.data.id to emptySet())
                    )
                }
                null
            }

            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }
    }

    /** Renomme ou supprime une étiquette ; renvoie un message d'erreur à afficher, ou null en cas de succès. */
    suspend fun renameTag(tag: TagItem, name: String): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return when (val result = repository.renameTag(tag.id, name)) {
            is ApiResult.Success -> {
                _uiState.update { state ->
                    fun TagItem.renamed() = if (id == tag.id) copy(name = name) else this
                    state.copy(
                        tags = state.tags.map { it.renamed() }.toImmutableList(),
                        selectedTag = state.selectedTag?.renamed(),
                        selectedTagDetails = state.selectedTagDetails?.renamed(),
                        // Les liens portent le nom de l'étiquette : on le met à jour pour rester cohérent.
                        tagBookmarks = state.tagBookmarks.map { b ->
                            b.copy(tags = b.tags.map { if (it == tag.name) name else it }.toImmutableList())
                        }.toImmutableList()
                    )
                }
                viewModelScope.launch { cacheManager.saveTags(_uiState.value.tags) }
                null
            }

            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }
    }

    /**
     * Applique [op] à chaque élément, 4 à la fois pour ne pas saturer le serveur de David sur les gros volumes.
     * Renvoie les éléments réussis et un message d'erreur (nombre d'échecs + premier détail du serveur) ou null.
     */
    private suspend fun <T> runBatch(items: List<T>, op: suspend (T) -> ApiResult<Unit>): Pair<List<T>, String?> {
        val done = mutableListOf<T>()
        var failed = 0
        var firstError: String? = null
        for (chunk in items.chunked(4)) {
            val results = coroutineScope { chunk.map { item -> async { item to op(item) } }.awaitAll() }
            for ((item, result) in results) {
                when (result) {
                    is ApiResult.Success -> done += item
                    is ApiResult.Error -> { failed++; if (firstError == null) firstError = result.message }
                    is ApiResult.NetworkError -> { failed++; if (firstError == null) firstError = result.message }
                }
            }
        }
        val error = firstError?.let { "$failed/${items.size} : ${it.take(160)}" }
        return done to error
    }

    /** Multisélection : met les liens dans [folderId] (et les retire de leurs autres dossiers), ou hors dossier si null. */
    suspend fun applyFolderToBookmarks(ids: Collection<String>, folderId: String?): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val (done, error) = runBatch(ids.toList()) { id ->
            val current = folderIdsOf(id)
            var outcome: ApiResult<Unit> = ApiResult.Success(Unit)
            for (other in current - setOfNotNull(folderId)) {
                outcome = repository.removeBookmarkFromList(other, id)
                if (outcome !is ApiResult.Success) return@runBatch outcome
            }
            if (folderId != null && folderId !in current) outcome = repository.addBookmarkToList(folderId, id)
            outcome
        }
        _uiState.update { state ->
            val doneSet = done.toSet()
            val membership = state.folderMembership.mapValues { (fid, members) ->
                if (fid == folderId) members + doneSet else members - doneSet
            }
            state.copy(folderMembership = membership)
        }
        refreshDisplayed()
        return error
    }

    /** Multisélection : ajoute l'étiquette [tagName] (créée si besoin) à chaque lien. */
    suspend fun addTagToBookmarks(ids: Collection<String>, tagName: String): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val name = tagName.trim()
        if (name.isEmpty()) return null
        val byId = _uiState.value.bookmarks.associateBy { it.id }
        val targets = ids.distinct().filter { id ->
            byId[id]?.tags?.none { normalizeForSearch(it) == normalizeForSearch(name) } != false
        }
        val (done, error) = runBatch(targets) { id -> repository.updateBookmarkTagNames(id, listOf(name), emptyList()) }
        done.mapNotNull { byId[it] }.forEach { applyBookmarkUpdate(it.copy(tags = (it.tags + name).toImmutableList())) }
        if (done.isNotEmpty()) loadTagsInternal(isRefresh = true)
        return error
    }

    /**
     * Copie (ou transfère si [move]) tous les liens de [source] vers l'étiquette [targetName]. La liste complète est lue
     * par curseur avant toute modification, pour qu'un transfert ne fausse pas la pagination.
     */
    suspend fun transferTagBookmarks(source: TagItem, targetName: String, move: Boolean): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val name = targetName.trim()
        if (name.isEmpty() || normalizeForSearch(name) == normalizeForSearch(source.name)) return null
        val linked = when (val result = repository.getAllBookmarksByTag(source.id, limit = 100, maxTotal = 100_000)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return result.message
            is ApiResult.NetworkError -> return result.message
        }
        val (_, error) = runBatch(linked) { bookmark ->
            repository.attachAndDetachTag(bookmark.id, name, source.takeIf { move })
        }
        loadTagsInternal(isRefresh = true)
        refreshSavedItems()
        if (_uiState.value.selectedTag?.id == source.id) loadSelectedTagContent()
        return error
    }

    /**
     * Ajoute tous les liens de [folder] (archivés compris) à [targetId]. Avec [move], ils sont aussi retirés de [folder]
     * (le dossier lui-même reste) ; sans, ils y restent, ce qui sert avant une suppression du dossier.
     */
    suspend fun transferFolderBookmarks(folder: SavedListItem, targetId: String, move: Boolean = false): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val linked = when (val result = repository.getAllBookmarksByList(folder.id, limit = 100, maxTotal = 100_000)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return result.message
            is ApiResult.NetworkError -> return result.message
        }
        val already = _uiState.value.folderMembership[targetId].orEmpty()
        val (done, error) = runBatch(linked) { bookmark ->
            var outcome: ApiResult<Unit> = ApiResult.Success(Unit)
            if (bookmark.id !in already) outcome = repository.addBookmarkToList(targetId, bookmark.id)
            if (outcome is ApiResult.Success && move) outcome = repository.removeBookmarkFromList(folder.id, bookmark.id)
            outcome
        }
        val doneIds = done.map { it.id }.toSet()
        _uiState.update { state ->
            val membership = state.folderMembership + (targetId to state.folderMembership[targetId].orEmpty() + doneIds)
            state.copy(
                folderMembership = if (move) membership + (folder.id to membership[folder.id].orEmpty() - doneIds) else membership
            )
        }
        refreshDisplayed()
        if (move && _uiState.value.selectedList?.id == folder.id) refreshListBookmarks()
        return error
    }

    /** Ajoute l'étiquette [tagName] à tous les liens de [folder], archivés compris. */
    suspend fun addTagToFolderBookmarks(folder: SavedListItem, tagName: String): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        val linked = when (val result = repository.getAllBookmarksByList(folder.id, limit = 100, maxTotal = 100_000)) {
            is ApiResult.Success -> result.data
            is ApiResult.Error -> return result.message
            is ApiResult.NetworkError -> return result.message
        }
        return addTagToBookmarks(linked.map { it.id }, tagName)
    }

    suspend fun deleteTag(tag: TagItem): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return when (val result = repository.deleteTag(tag.id)) {
            is ApiResult.Success -> {
                tagDetailJob?.cancel()
                _uiState.update { state ->
                    val wasOpen = state.selectedTag?.id == tag.id
                    state.copy(
                        tags = state.tags.filterNot { it.id == tag.id }.toImmutableList(),
                        selectedTag = if (wasOpen) null else state.selectedTag,
                        selectedTagDetails = if (wasOpen) null else state.selectedTagDetails,
                        tagBookmarks = if (wasOpen) persistentListOf() else state.tagBookmarks,
                        isTagBookmarksLoading = if (wasOpen) false else state.isTagBookmarksLoading,
                        tagBookmarksErrorMessage = if (wasOpen) null else state.tagBookmarksErrorMessage
                    )
                }
                viewModelScope.launch { cacheManager.saveTags(_uiState.value.tags) }
                null
            }

            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }
    }

    suspend fun renameFolder(folder: SavedListItem, name: String): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return when (val result = repository.renameList(folder.id, name)) {
            is ApiResult.Success -> {
                _uiState.update { state ->
                    state.copy(
                        lists = state.lists.map { if (it.id == folder.id) it.copy(name = name) else it }.toImmutableList()
                    )
                }
                null
            }

            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }
    }

    suspend fun deleteFolder(folder: SavedListItem): String? {
        val settings = settingsDataStore.settingsFlow.first()
        repository.configure(settings)
        return when (val result = repository.deleteList(folder.id)) {
            is ApiResult.Success -> {
                val removed = folderAndChildrenIds(folder.id, _uiState.value.lists)
                _uiState.update { state ->
                    val filter = state.homeFilter
                    state.copy(
                        homeFilter = if (filter is LinkFilter.Folder && filter.id in removed) LinkFilter.All else filter,
                        selectedList = state.selectedList?.takeUnless { it.id == folder.id },
                        folderMembership = state.folderMembership - removed
                    )
                }
                // Le serveur décide du sort des sous-dossiers : on relit les listes plutôt que de le deviner.
                loadListsInternal(isRefresh = true)
                null
            }

            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> result.message
        }
    }

    fun loadTags() {
        if (hasLoadedTags && _uiState.value.tags.isNotEmpty()) return
        viewModelScope.launch {
            if (_uiState.value.tags.isEmpty()) {
                val cached = cacheManager.loadCachedTags()
                if (!cached.isNullOrEmpty()) {
                    _uiState.update { it.copy(tags = cached.toImmutableList()) }
                }
            }
            loadTagsInternal(isRefresh = false)
        }
    }

    fun refreshTags() {
        loadTagsInternal(isRefresh = true)
    }

    fun loadLists() {
        if (hasLoadedLists && _uiState.value.lists.isNotEmpty()) return
        loadListsInternal(isRefresh = false)
    }

    fun refreshLists() {
        loadListsInternal(isRefresh = true)
    }

    private fun loadListsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            val hasExistingLists = _uiState.value.lists.isNotEmpty()
            _uiState.update {
                it.copy(
                    isListsLoading = if (isRefresh || hasExistingLists) it.isListsLoading else true,
                    isListsRefreshing = isRefresh,
                    listsErrorMessage = null
                )
            }

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            when (val result = repository.getLists()) {
                is ApiResult.Success -> {
                    hasLoadedLists = true
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            lists = result.data.toImmutableList(),
                            listsErrorMessage = null
                        )
                    }
                    loadFolderMembership(result.data)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            listsErrorMessage = if ((isRefresh || hasExistingLists) && it.lists.isNotEmpty()) null else result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListsLoading = false,
                            isListsRefreshing = false,
                            listsErrorMessage = if ((isRefresh || hasExistingLists) && it.lists.isNotEmpty()) null else result.message
                        )
                    }
                }
            }
        }
    }

    private fun loadTagsInternal(isRefresh: Boolean) {
        viewModelScope.launch {
            val hasExistingTags = _uiState.value.tags.isNotEmpty()
            _uiState.update {
                it.copy(
                    isTagsLoading = if (isRefresh || hasExistingTags) it.isTagsLoading else true,
                    isTagsRefreshing = isRefresh,
                    tagsErrorMessage = null
                )
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
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tags = result.data.toImmutableList(),
                            tagsErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveTags(result.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if ((isRefresh || hasExistingTags) && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagsLoading = false,
                            isTagsRefreshing = false,
                            tagsErrorMessage = if ((isRefresh || hasExistingTags) && it.tags.isNotEmpty()) null else result.message
                        )
                    }
                }
            }
        }
    }

    fun openTag(tag: TagItem) {
        tagDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTag = tag,
                selectedTagDetails = null,
                isTagBookmarksLoading = true,
                tagBookmarks = persistentListOf(),
                tagBookmarksErrorMessage = null
            )
        }
        loadSelectedTagContent()
    }

    fun closeTagDetail() {
        tagDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTag = null,
                selectedTagDetails = null,
                isTagBookmarksLoading = false,
                tagBookmarks = persistentListOf(),
                tagBookmarksErrorMessage = null
            )
        }
    }

    fun refreshTagBookmarks() {
        if (_uiState.value.selectedTag != null) {
            loadSelectedTagContent()
        }
    }

    fun openList(list: SavedListItem) {
        listDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedList = list,
                selectedListDetails = null,
                isListBookmarksLoading = true,
                listBookmarks = persistentListOf(),
                listBookmarksErrorMessage = null
            )
        }
        loadSelectedListContent()
    }

    fun openFavoritesList() {
        openList(
            SavedListItem(
                id = FAVORITES_LIST_ID,
                name = "Favorites",
                description = "Your favourited bookmarks",
                icon = "⭐",
                parentId = null,
                type = "smart",
                query = "favourited:true",
                isPublic = false,
                hasCollaborators = false,
                userRole = "owner"
            )
        )
    }

    fun toggleBookmarkFavourite(
        bookmark: BookmarkItem,
        onFailed: ActionFailure = { _, _ -> },
        onUpdated: (BookmarkItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val updatedBookmark = bookmark.copy(favourited = !bookmark.favourited)
            when (val result = repository.setBookmarkFavourited(bookmark.id, updatedBookmark.favourited)) {
                is ApiResult.Success -> {
                    applyBookmarkUpdate(updatedBookmark)
                    onUpdated(updatedBookmark)
                }

                is ApiResult.Error, is ApiResult.NetworkError -> result.reportFailure(onFailed)
            }
        }
    }

    fun toggleBookmarkArchived(
        bookmark: BookmarkItem,
        onFailed: ActionFailure = { _, _ -> },
        onUpdated: (BookmarkItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)

            val updatedBookmark = bookmark.copy(archived = !bookmark.archived)
            when (val result = repository.setBookmarkArchived(bookmark.id, updatedBookmark.archived)) {
                is ApiResult.Success -> {
                    applyBookmarkUpdate(updatedBookmark)
                    onUpdated(updatedBookmark)
                }

                is ApiResult.Error, is ApiResult.NetworkError -> result.reportFailure(onFailed)
            }
        }
    }

    fun deleteBookmarkWithUndo(bookmark: BookmarkItem, onFailed: ActionFailure = { _, _ -> }) {
        pendingDeleteJobs[bookmark.id]?.cancel()
        applyBookmarkRemoval(bookmark.id)

        pendingDeleteJobs[bookmark.id] = viewModelScope.launch {
            delay(4500)

            val settings = settingsDataStore.settingsFlow.first()
            repository.configure(settings)
            val result = repository.deleteBookmark(bookmark.id)

            pendingDeleteJobs.remove(bookmark.id)
            // Serveur KO : le lien n'a pas été supprimé, on le remet dans la liste plutôt que de le laisser disparaître
            // jusqu'à la prochaine synchronisation.
            if (result !is ApiResult.Success) {
                applyBookmarkRestore(bookmark)
                result.reportFailure(onFailed)
            }
        }
    }

    fun undoDeleteBookmark(bookmark: BookmarkItem) {
        pendingDeleteJobs.remove(bookmark.id)?.cancel()
        applyBookmarkRestore(bookmark)
    }

    fun closeListDetail() {
        listDetailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedList = null,
                selectedListDetails = null,
                isListBookmarksLoading = false,
                listBookmarks = persistentListOf(),
                listBookmarksErrorMessage = null
            )
        }
    }

    fun refreshListBookmarks() {
        if (_uiState.value.selectedList != null) {
            loadSelectedListContent()
        }
    }

    private fun loadSelectedListContent() {
        val selectedList = _uiState.value.selectedList ?: return

        listDetailJob = viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val archivedFilter = archivedFilterFor(settings)

            if (_uiState.value.listBookmarks.isEmpty()) {
                cacheManager.loadCachedListBookmarks(selectedList.id)?.takeIf { it.isNotEmpty() }?.let { cached ->
                    if (_uiState.value.selectedList?.id == selectedList.id) {
                        _uiState.update { it.copy(listBookmarks = applyArchiveFilter(cached, archivedFilter).toImmutableList()) }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isListBookmarksLoading = true,
                    listBookmarksErrorMessage = null
                )
            }

            repository.configure(settings)

            val (listResult, bookmarksResult) = if (selectedList.id == FAVORITES_LIST_ID) {
                ApiResult.Success(selectedList) to repository.getAllFavouritedBookmarks(
                    archived = archivedFilter,
                    limit = 20
                )
            } else {
                coroutineScope {
                    val listDeferred = async { repository.getList(selectedList.id) }
                    val bookmarksDeferred = async {
                        repository.getAllBookmarksByList(
                            archived = archivedFilter,
                            listId = selectedList.id,
                            limit = 20
                        )
                    }
                    listDeferred.await() to bookmarksDeferred.await()
                }
            }

            if (_uiState.value.selectedList?.id != selectedList.id) return@launch

            when (listResult) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(selectedListDetails = listResult.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = listResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = listResult.message
                        )
                    }
                }
            }

            when (bookmarksResult) {
                is ApiResult.Success -> {
                    val filteredBookmarks = applyArchiveFilter(bookmarksResult.data, archivedFilter)
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarks = filteredBookmarks.toImmutableList(),
                            listBookmarksErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveListBookmarks(selectedList.id, bookmarksResult.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isListBookmarksLoading = false,
                            listBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }
            }
        }
    }

    private fun scheduleCacheSave() {
        // Capture a consistent snapshot at schedule time: reading selectedTag/List
        // after the debounce could pair a newly opened tag/list with stale bookmarks.
        val state = _uiState.value
        val bookmarks = state.bookmarks
        val tagId = state.selectedTag?.id
        val tagBookmarks = state.tagBookmarks
        val listId = state.selectedList?.id
        val listBookmarks = state.listBookmarks
        cacheSaveJob?.cancel()
        cacheSaveJob = viewModelScope.launch {
            delay(CACHE_SAVE_DEBOUNCE_MS)
            cacheManager.saveBookmarks(bookmarks)
            if (tagId != null) {
                cacheManager.saveTagBookmarks(tagId, tagBookmarks)
            }
            if (listId != null) {
                cacheManager.saveListBookmarks(listId, listBookmarks)
            }
        }
    }

    private fun applyBookmarkUpdate(updatedBookmark: BookmarkItem) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val isFavoritesListOpen = state.selectedList?.id == FAVORITES_LIST_ID

            val bookmarksUpdated = state.bookmarks.map { current ->
                if (current.id == updatedBookmark.id) updatedBookmark else current
            }.let { items ->
                if (updatedBookmark.archived) items.filterNot { it.id == updatedBookmark.id } else items
            }

            val tagBookmarksUpdated = state.tagBookmarks.map { current ->
                if (current.id == updatedBookmark.id) updatedBookmark else current
            }

            val listBookmarksUpdated = state.listBookmarks
                .map { current -> if (current.id == updatedBookmark.id) updatedBookmark else current }
                .let { items ->
                    if (isFavoritesListOpen && !updatedBookmark.favourited) {
                        items.filterNot { it.id == updatedBookmark.id }
                    } else {
                        items
                    }
                }

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun applyBookmarkRemoval(bookmarkId: String) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val bookmarksUpdated = state.bookmarks.filterNot { it.id == bookmarkId }
            val tagBookmarksUpdated = state.tagBookmarks.filterNot { it.id == bookmarkId }
            val listBookmarksUpdated = state.listBookmarks.filterNot { it.id == bookmarkId }

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun applyBookmarkRestore(bookmark: BookmarkItem) {
        var newDisplayed: List<BookmarkItem> = _displayedBookmarks.value
        _uiState.update { state ->
            val bookmarksUpdated = if (state.bookmarks.any { it.id == bookmark.id }) {
                state.bookmarks
            } else {
                listOf(bookmark) + state.bookmarks
            }

            val tagBookmarksUpdated = if (state.selectedTag != null && state.tagBookmarks.none { it.id == bookmark.id }) {
                listOf(bookmark) + state.tagBookmarks
            } else {
                state.tagBookmarks
            }

            val listBookmarksUpdated = if (state.selectedList != null && state.listBookmarks.none { it.id == bookmark.id }) {
                listOf(bookmark) + state.listBookmarks
            } else {
                state.listBookmarks
            }

            newDisplayed = computeDisplayedBookmarks(
                bookmarks = bookmarksUpdated,
                query = state.searchQuery,
                isSearchActive = state.isSearchActive
            )

            state.copy(
                bookmarks = bookmarksUpdated.toImmutableList(),
                tagBookmarks = tagBookmarksUpdated.toImmutableList(),
                listBookmarks = listBookmarksUpdated.toImmutableList()
            )
        }
        _displayedBookmarks.value = newDisplayed.toImmutableList()

        scheduleCacheSave()
    }

    private fun loadSelectedTagContent() {
        val selectedTag = _uiState.value.selectedTag ?: return

        tagDetailJob = viewModelScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            val archivedFilter = archivedFilterFor(settings)

            if (_uiState.value.tagBookmarks.isEmpty()) {
                cacheManager.loadCachedTagBookmarks(selectedTag.id)?.takeIf { it.isNotEmpty() }?.let { cached ->
                    if (_uiState.value.selectedTag?.id == selectedTag.id) {
                        _uiState.update { it.copy(tagBookmarks = applyArchiveFilter(cached, archivedFilter).toImmutableList()) }
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isTagBookmarksLoading = true,
                    tagBookmarksErrorMessage = null
                )
            }

            repository.configure(settings)

            val (tagResult, bookmarksResult) = coroutineScope {
                val tagDeferred = async { repository.getTag(selectedTag.id) }
                val bookmarksDeferred = async {
                    repository.getAllBookmarksByTag(
                        archived = archivedFilter,
                        tagId = selectedTag.id,
                        limit = 20
                    )
                }
                tagDeferred.await() to bookmarksDeferred.await()
            }

            if (_uiState.value.selectedTag?.id != selectedTag.id) return@launch

            when (tagResult) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(selectedTagDetails = tagResult.data)
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = tagResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = tagResult.message
                        )
                    }
                }
            }

            when (bookmarksResult) {
                is ApiResult.Success -> {
                    val filteredBookmarks = applyArchiveFilter(bookmarksResult.data, archivedFilter)
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarks = filteredBookmarks.toImmutableList(),
                            tagBookmarksErrorMessage = null
                        )
                    }
                    launch { cacheManager.saveTagBookmarks(selectedTag.id, bookmarksResult.data) }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }

                is ApiResult.NetworkError -> {
                    _uiState.update {
                        it.copy(
                            isTagBookmarksLoading = false,
                            tagBookmarksErrorMessage = bookmarksResult.message
                        )
                    }
                }
            }
        }
    }

    private fun computeDisplayedBookmarks(
        bookmarks: List<BookmarkItem>,
        query: String,
        isSearchActive: Boolean
    ): List<BookmarkItem> {
        val state = _uiState.value
        return filterLinks(
            bookmarks = bookmarks,
            lists = state.lists,
            membership = state.folderMembership,
            filter = state.homeFilter,
            query = if (isSearchActive || query.isNotBlank()) query else ""
        )
    }

    private fun resolveProfileImageUrl(serverAddress: String, image: String?): String? {
        if (image.isNullOrBlank()) return null
        if (isRemoteImage(image)) return image
        val normalizedBase = serverAddress.trimEnd('/')
        return "$normalizedBase/api/assets/$image"
    }

    private fun buildProfileImageHeaders(settings: Settings, image: String?): Map<String, String> {
        if (isRemoteImage(image)) return emptyMap()

        val cacheKey = Triple(settings.address, settings.apiKey, image ?: "")
        if (cachedProfileHeadersKey == cacheKey) return cachedProfileHeadersMap

        val headers = settings.customHeaders.toMutableMap()
        settings.apiKey?.takeIf { it.isNotBlank() }?.let { apiKey ->
            headers["Authorization"] = "Bearer $apiKey"
        }
        cachedProfileHeadersKey = cacheKey
        cachedProfileHeadersMap = headers
        return headers
    }

    private fun profileCacheKey(settings: Settings): String {
        return "${settings.address.trimEnd('/')}|${settings.apiKeyId.orEmpty()}"
    }

    private fun archivedFilterFor(settings: Settings): Boolean? {
        return when (settings.archiveDisplayBehaviour) {
            ArchiveDisplayBehaviour.HIDE -> false
            ArchiveDisplayBehaviour.SHOW -> null
        }
    }

    private fun applyArchiveFilter(bookmarks: List<BookmarkItem>, archivedFilter: Boolean?): List<BookmarkItem> {
        return when (archivedFilter) {
            false -> bookmarks.filterNot { it.archived }
            true -> bookmarks.filter { it.archived }
            null -> bookmarks
        }
    }

    private fun isRemoteImage(image: String?): Boolean {
        if (image.isNullOrBlank()) return true
        return image.startsWith("http://") || image.startsWith("https://")
    }

    private fun filterBookmarks(bookmarks: List<BookmarkItem>, query: String): List<BookmarkItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return bookmarks

        return bookmarks.filter { bookmark ->
            val title = bookmark.title.orEmpty()
            val subtitle = bookmark.subtitle.orEmpty()
            val link = bookmark.linkUrl.orEmpty()
            title.contains(normalized, ignoreCase = true) ||
                    subtitle.contains(normalized, ignoreCase = true) ||
                    link.contains(normalized, ignoreCase = true)
        }
    }
}
