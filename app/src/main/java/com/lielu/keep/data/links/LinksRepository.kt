package com.lielu.keep.data.links

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinksDao
import kotlinx.coroutines.flow.Flow

interface LinksRepository {
    fun observeFolders(): Flow<List<FolderEntity>>

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
}

class RoomLinksRepository(
    private val dao: LinksDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : LinksRepository {
    override fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

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
}
