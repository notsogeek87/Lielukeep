package com.lielu.keep.data.db

import androidx.room.withTransaction
import com.lielu.keep.backup.LinksBackupStore
import com.lielu.keep.backup.LinksSnapshot
import com.lielu.keep.data.sync.SyncStore

/** Lecture/remplacement atomiques de toute la copie locale : sauvegarde et synchronisation y passent. */
class RoomLinksBackupStore(
    private val database: AppDatabase,
) : LinksBackupStore,
    SyncStore {
    private val dao = database.linksDao()

    override suspend fun read(): LinksSnapshot = database.withTransaction { readInTransaction() }

    override suspend fun replaceAll(snapshot: LinksSnapshot) {
        database.withTransaction { writeInTransaction(snapshot) }
    }

    override suspend fun merge(transform: (LinksSnapshot) -> LinksSnapshot) {
        database.withTransaction { writeInTransaction(transform(readInTransaction())) }
    }

    private suspend fun readInTransaction() = LinksSnapshot(dao.allFolders(), dao.allLinks(), dao.allTags())

    private suspend fun writeInTransaction(snapshot: LinksSnapshot) {
        dao.deleteAllTags()
        dao.deleteAllLinks()
        dao.deleteAllFolders()
        dao.insertFolders(snapshot.folders)
        dao.insertLinks(snapshot.links)
        dao.insertTags(snapshot.tags)
    }
}
