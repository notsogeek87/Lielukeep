package com.lielu.keep.data.db

import androidx.room.withTransaction
import com.lielu.keep.backup.LinksBackupStore
import com.lielu.keep.backup.LinksSnapshot

class RoomLinksBackupStore(
    private val database: AppDatabase,
) : LinksBackupStore {
    private val dao = database.linksDao()

    override suspend fun read(): LinksSnapshot =
        database.withTransaction { LinksSnapshot(dao.allFolders(), dao.allLinks(), dao.allTags()) }

    override suspend fun replaceAll(snapshot: LinksSnapshot) {
        database.withTransaction {
            dao.deleteAllTags()
            dao.deleteAllLinks()
            dao.deleteAllFolders()
            dao.insertFolders(snapshot.folders)
            dao.insertLinks(snapshot.links)
            dao.insertTags(snapshot.tags)
        }
    }
}
