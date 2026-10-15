package com.lielu.keep.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LinksDao {
    @Query("SELECT * FROM folders ORDER BY id")
    suspend fun allFolders(): List<FolderEntity>

    @Query("SELECT * FROM links ORDER BY id")
    suspend fun allLinks(): List<LinkEntity>

    @Query("SELECT * FROM link_tags ORDER BY linkId, tag")
    suspend fun allTags(): List<LinkTagEntity>

    @Query("DELETE FROM link_tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM links")
    suspend fun deleteAllLinks()

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()

    @Insert
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert
    suspend fun insertLinks(links: List<LinkEntity>)

    @Insert
    suspend fun insertTags(tags: List<LinkTagEntity>)

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Insert
    suspend fun insertLink(link: LinkEntity): Long

    @Query("SELECT * FROM folders ORDER BY position, name COLLATE NOCASE")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders WHERE parentId IS NULL")
    suspend fun nextRootFolderPosition(): Int

    @Query("SELECT * FROM links WHERE url = :url AND deletedAt IS NULL LIMIT 1")
    suspend fun findActiveLink(url: String): LinkEntity?

    /** Dossier du lien enregistré le plus récemment (null = sans dossier, ou aucun lien). */
    @Query("SELECT folderId FROM links WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun lastUsedFolderId(): Long?

    @Query("SELECT * FROM links WHERE deletedAt IS NULL AND archived = 0 ORDER BY createdAt DESC, id DESC")
    fun observeActiveLinks(): Flow<List<LinkEntity>>

    @Query("UPDATE links SET favorite = :value, dirtyAt = :now WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        value: Boolean,
        now: Long,
    )

    @Query("UPDATE links SET readLater = :value, dirtyAt = :now WHERE id = :id")
    suspend fun setReadLater(
        id: Long,
        value: Boolean,
        now: Long,
    )

    @Query("UPDATE links SET archived = :value, dirtyAt = :now WHERE id = :id")
    suspend fun setArchived(
        id: Long,
        value: Boolean,
        now: Long,
    )

    @Query("UPDATE links SET deletedAt = :deletedAt, dirtyAt = :now WHERE id = :id")
    suspend fun setDeletedAt(
        id: Long,
        deletedAt: Long?,
        now: Long,
    )

    @Query("UPDATE links SET openedAt = :openedAt WHERE id = :id")
    suspend fun setOpenedAt(
        id: Long,
        openedAt: Long,
    )

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: LinkTagEntity)

    @Query("DELETE FROM link_tags WHERE linkId = :linkId AND tag = :tag COLLATE NOCASE")
    suspend fun deleteTag(
        linkId: Long,
        tag: String,
    )

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun folderById(id: Long): FolderEntity?

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(
        id: Long,
        name: String,
    )

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)
}
