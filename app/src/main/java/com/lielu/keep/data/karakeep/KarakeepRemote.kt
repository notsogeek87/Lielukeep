package com.lielu.keep.data.karakeep

/** Ce que l'app utilise de l'API Karakeep ; abstrait pour tester la synchronisation sans réseau. */
interface KarakeepRemote {
    /** Vérifie l'adresse et la clé (appel authentifié léger). Lève [KarakeepException] sinon. */
    suspend fun checkConnection()

    suspend fun listLists(): List<RemoteList>

    suspend fun createList(
        name: String,
        icon: String,
        parentId: String?,
    ): RemoteList

    suspend fun renameList(
        id: String,
        name: String,
    )

    suspend fun deleteList(id: String)

    /** Tous les signets de type lien, archivés ou non selon [archived] (pagination gérée). */
    suspend fun listBookmarks(archived: Boolean): List<RemoteBookmark>

    suspend fun listBookmarkIdsInList(listId: String): List<String>

    /** Null si le signet n'existe plus sur le serveur. */
    suspend fun getBookmark(id: String): RemoteBookmark?

    /** Crée le signet ; si l'URL existe déjà, le serveur renvoie l'existant. */
    suspend fun createBookmark(
        url: String,
        title: String?,
        note: String?,
        favourited: Boolean,
        archived: Boolean,
    ): RemoteBookmark

    /** Champs null = inchangés. Faux si le signet n'existe plus. */
    suspend fun updateBookmark(
        id: String,
        title: String? = null,
        note: String? = null,
        favourited: Boolean? = null,
        archived: Boolean? = null,
    ): Boolean

    /** Sans erreur si le signet est déjà absent. */
    suspend fun deleteBookmark(id: String)

    suspend fun attachTag(
        bookmarkId: String,
        tagName: String,
    )

    suspend fun detachTag(
        bookmarkId: String,
        tagId: String,
    )

    suspend fun addToList(
        listId: String,
        bookmarkId: String,
    )

    /** Sans erreur si le signet n'était pas dans la liste. */
    suspend fun removeFromList(
        listId: String,
        bookmarkId: String,
    )
}
