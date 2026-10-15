package com.lielu.keep.data.links

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinkTagEntity
import com.lielu.keep.data.db.LinksDao
import com.lielu.keep.data.karakeep.KarakeepRemote
import com.lielu.keep.data.karakeep.READ_LATER_TAG
import com.lielu.keep.data.sync.SyncRequester
import kotlinx.coroutines.flow.Flow

interface LinksRepository {
    fun observeFolders(): Flow<List<FolderEntity>>

    /** Liens visibles sur l'accueil (ni archivés ni dans la corbeille), du plus récent au plus ancien. */
    fun observeActiveLinks(): Flow<List<LinkEntity>>

    /** Lien déjà enregistré (hors corbeille) pour exactement cette URL, ou null. */
    suspend fun findActiveLink(url: String): LinkEntity?

    /** Dossier du dernier lien enregistré, proposé par défaut à l'ajout. */
    suspend fun suggestedFolderId(): Long?

    /**
     * Crée un dossier (une liste Karakeep) et renvoie son identifiant. Nécessite le serveur : lève
     * [com.lielu.keep.data.karakeep.KarakeepException] hors ligne.
     */
    suspend fun createFolder(
        name: String,
        parentId: Long? = null,
    ): Long

    suspend fun renameFolder(
        id: Long,
        name: String,
    )

    /** Supprime la liste sur le serveur ; ses liens sont conservés, simplement sans dossier. */
    suspend fun deleteFolder(id: Long)

    suspend fun saveLink(
        url: String,
        title: String,
        folderId: Long?,
    ): Long

    suspend fun setFavorite(
        id: Long,
        value: Boolean,
    )

    suspend fun setReadLater(
        id: Long,
        value: Boolean,
    )

    suspend fun setArchived(
        id: Long,
        value: Boolean,
    )

    /** `true` envoie à la corbeille (suppression douce), `false` restaure. */
    suspend fun setDeleted(
        id: Long,
        deleted: Boolean,
    )

    suspend fun markOpened(id: Long)
}

class RoomLinksRepository(
    private val dao: LinksDao,
    private val remote: KarakeepRemote,
    private val sync: SyncRequester,
    private val clock: () -> Long = System::currentTimeMillis,
) : LinksRepository {
    override fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    override fun observeActiveLinks(): Flow<List<LinkEntity>> = dao.observeActiveLinks()

    override suspend fun findActiveLink(url: String): LinkEntity? = dao.findActiveLink(url)

    override suspend fun suggestedFolderId(): Long? = dao.lastUsedFolderId()

    override suspend fun createFolder(
        name: String,
        parentId: Long?,
    ): Long {
        val parent = parentId?.let { dao.folderById(it) }
        val list = remote.createList(name.trim(), DEFAULT_FOLDER_ICON, parent?.remoteId)
        return dao.insertFolder(
            FolderEntity(
                name = list.name,
                parentId = parent?.id,
                emoji = list.icon.ifBlank { null },
                position = dao.nextRootFolderPosition(),
                remoteId = list.id,
            ),
        )
    }

    override suspend fun renameFolder(
        id: Long,
        name: String,
    ) {
        val folder = dao.folderById(id) ?: return
        folder.remoteId?.let { remote.renameList(it, name.trim()) }
        dao.renameFolder(id, name.trim())
    }

    override suspend fun deleteFolder(id: Long) {
        val folder = dao.folderById(id) ?: return
        folder.remoteId?.let { remote.deleteList(it) }
        dao.deleteFolder(id)
        // Les sous-listes de la liste supprimée sont réorganisées par le serveur : on relit son état.
        sync.requestSync()
    }

    override suspend fun saveLink(
        url: String,
        title: String,
        folderId: Long?,
    ): Long {
        val now = clock()
        val id =
            dao.insertLink(
                LinkEntity(
                    url = url,
                    title = title,
                    siteName = UrlTools.domainOf(url),
                    folderId = folderId,
                    createdAt = now,
                    dirtyAt = now,
                ),
            )
        sync.requestSync()
        return id
    }

    override suspend fun setFavorite(
        id: Long,
        value: Boolean,
    ) {
        dao.setFavorite(id, value, clock())
        sync.requestSync()
    }

    override suspend fun setReadLater(
        id: Long,
        value: Boolean,
    ) {
        dao.setReadLater(id, value, clock())
        if (value) dao.insertTag(LinkTagEntity(id, READ_LATER_TAG)) else dao.deleteTag(id, READ_LATER_TAG)
        sync.requestSync()
    }

    override suspend fun setArchived(
        id: Long,
        value: Boolean,
    ) {
        dao.setArchived(id, value, clock())
        sync.requestSync()
    }

    override suspend fun setDeleted(
        id: Long,
        deleted: Boolean,
    ) {
        val now = clock()
        dao.setDeletedAt(id, if (deleted) now else null, now)
        sync.requestSync()
    }

    // Local uniquement : Karakeep n'a pas de notion de « dernière ouverture ».
    override suspend fun markOpened(id: Long) = dao.setOpenedAt(id, clock())

    private companion object {
        const val DEFAULT_FOLDER_ICON = "📁"
    }
}
