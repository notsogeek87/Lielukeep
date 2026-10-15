package com.lielu.keep.data.links

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinksDao
import kotlinx.coroutines.flow.Flow

interface LinksRepository {
    fun observeFolders(): Flow<List<FolderEntity>>

    /** Liens visibles sur l'accueil (ni archivés ni dans la corbeille), du plus récent au plus ancien. */
    fun observeActiveLinks(): Flow<List<LinkEntity>>

    /** Lien déjà enregistré (hors corbeille) pour exactement cette URL, ou null. */
    suspend fun findActiveLink(url: String): LinkEntity?

    /** Dossier du dernier lien enregistré, proposé par défaut à l'ajout. */
    suspend fun suggestedFolderId(): Long?

    /** Crée un dossier à la racine et renvoie son identifiant. */
    suspend fun createFolder(name: String): Long

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
    private val clock: () -> Long = System::currentTimeMillis,
) : LinksRepository {
    override fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    override fun observeActiveLinks(): Flow<List<LinkEntity>> = dao.observeActiveLinks()

    override suspend fun findActiveLink(url: String): LinkEntity? = dao.findActiveLink(url)

    override suspend fun suggestedFolderId(): Long? = dao.lastUsedFolderId()

    override suspend fun createFolder(name: String): Long =
        dao.insertFolder(FolderEntity(name = name.trim(), position = dao.nextRootFolderPosition()))

    override suspend fun saveLink(
        url: String,
        title: String,
        folderId: Long?,
    ): Long =
        dao.insertLink(
            LinkEntity(
                url = url,
                title = title,
                siteName = UrlTools.domainOf(url),
                folderId = folderId,
                createdAt = clock(),
            ),
        )

    override suspend fun setFavorite(
        id: Long,
        value: Boolean,
    ) = dao.setFavorite(id, value)

    override suspend fun setReadLater(
        id: Long,
        value: Boolean,
    ) = dao.setReadLater(id, value)

    override suspend fun setArchived(
        id: Long,
        value: Boolean,
    ) = dao.setArchived(id, value)

    override suspend fun setDeleted(
        id: Long,
        deleted: Boolean,
    ) = dao.setDeletedAt(id, if (deleted) clock() else null)

    override suspend fun markOpened(id: Long) = dao.setOpenedAt(id, clock())
}
