package com.lielu.keep.testutil

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.links.LinksRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Remplaçant en mémoire de [LinksRepository]. */
class FakeLinksRepository(
    initialFolders: List<FolderEntity> = emptyList(),
    initialLinks: List<LinkEntity> = emptyList(),
) : LinksRepository {
    val folders = MutableStateFlow(initialFolders)
    val links = MutableStateFlow(initialLinks)
    var suggested: Long? = null

    override fun observeFolders(): Flow<List<FolderEntity>> = folders

    override fun observeActiveLinks(): Flow<List<LinkEntity>> =
        links.map { all -> all.filter { it.deletedAt == null && !it.archived }.sortedByDescending { it.createdAt } }

    override suspend fun findActiveLink(url: String) = links.value.firstOrNull { it.url == url && it.deletedAt == null }

    override suspend fun suggestedFolderId() = suggested

    var folderError: KarakeepException? = null

    override suspend fun createFolder(
        name: String,
        parentId: Long?,
    ): Long {
        folderError?.let { throw it }
        val id = (folders.value.maxOfOrNull { it.id } ?: 0) + 1
        folders.value = folders.value + FolderEntity(id, name, parentId = parentId)
        return id
    }

    override suspend fun renameFolder(
        id: Long,
        name: String,
    ) {
        folderError?.let { throw it }
        folders.value = folders.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteFolder(id: Long) {
        folderError?.let { throw it }
        folders.value = folders.value.filter { it.id != id }
        links.value = links.value.map { if (it.folderId == id) it.copy(folderId = null) else it }
    }

    override suspend fun saveLink(
        url: String,
        title: String,
        folderId: Long?,
    ): Long {
        val id = (links.value.maxOfOrNull { it.id } ?: 0) + 1
        links.value = links.value + LinkEntity(id, url, title, folderId = folderId, createdAt = id)
        return id
    }

    private fun update(
        id: Long,
        change: (LinkEntity) -> LinkEntity,
    ) {
        links.value = links.value.map { if (it.id == id) change(it) else it }
    }

    override suspend fun setFavorite(
        id: Long,
        value: Boolean,
    ) = update(id) { it.copy(favorite = value) }

    override suspend fun setReadLater(
        id: Long,
        value: Boolean,
    ) = update(id) { it.copy(readLater = value) }

    override suspend fun setArchived(
        id: Long,
        value: Boolean,
    ) = update(id) { it.copy(archived = value) }

    override suspend fun setDeleted(
        id: Long,
        deleted: Boolean,
    ) = update(id) { it.copy(deletedAt = if (deleted) 1L else null) }

    override suspend fun markOpened(id: Long) = update(id) { it.copy(openedAt = 1L) }
}
