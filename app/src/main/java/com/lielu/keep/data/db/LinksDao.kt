package com.lielu.keep.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

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
}
