package com.lielu.keep.data.sync

import com.lielu.keep.backup.LinksSnapshot
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.karakeep.KarakeepErrorKind
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.karakeep.KarakeepRemote
import com.lielu.keep.data.karakeep.READ_LATER_TAG
import com.lielu.keep.data.karakeep.RemoteTag

data class SyncReport(
    /** Liens locaux qui n'ont pas pu être envoyés (réessayés à la prochaine synchronisation). */
    val failedLinks: Int,
)

/**
 * Synchronisation avec Karakeep, en deux temps :
 * 1. envoi des modifications locales (nouveaux liens, favoris, archivage, « à lire », dossier, notes,
 *    suppressions après le délai d'annulation) ;
 * 2. lecture de l'état du serveur, qui fait foi, appliquée à la copie locale ([mergeRemote]).
 *
 * Les dossiers (listes) sont créés/renommés/supprimés en ligne par le dépôt, pas par ici.
 */
class SyncEngine(
    private val remote: KarakeepRemote,
    private val store: SyncStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sync(): SyncReport {
        val failed = pushLocalChanges()
        pull()
        return SyncReport(failed)
    }

    private suspend fun pushLocalChanges(): Int {
        val snapshot = store.read()
        var failed = 0
        for (link in snapshot.links) {
            try {
                push(link, snapshot)
            } catch (e: KarakeepException) {
                // Serveur injoignable, clé refusée : inutile d'insister lien par lien.
                if (e.kind != KarakeepErrorKind.SERVER && e.kind != KarakeepErrorKind.NOT_FOUND) throw e
                failed++
            }
        }
        return failed
    }

    private suspend fun push(
        link: LinkEntity,
        snapshot: LinksSnapshot,
    ) {
        val deletedAt = link.deletedAt
        if (deletedAt != null) {
            if (clock() - deletedAt < DELETE_GRACE_MS) return
            link.remoteId?.let { remote.deleteBookmark(it) }
            store.merge { it.withoutLink(link.id) }
            return
        }
        if (link.remoteId != null && link.dirtyAt == null) return

        val wantedList = snapshot.folders.firstOrNull { it.id == link.folderId }?.remoteId
        val remoteId: String
        val serverTags: List<RemoteTag>
        if (link.remoteId == null) {
            val created =
                remote.createBookmark(
                    link.url,
                    link.title.takeIf { it.isNotBlank() },
                    link.note,
                    link.favorite,
                    link.archived,
                )
            remoteId = created.id
            serverTags = created.tags
        } else {
            remoteId = link.remoteId
            val current = remote.getBookmark(remoteId)
            if (current == null) {
                // Supprimé sur le serveur entre-temps : le serveur fait foi.
                store.merge { it.withoutLink(link.id) }
                return
            }
            serverTags = current.tags
            remote.updateBookmark(
                remoteId,
                title =
                    link.title.takeIf {
                        it != (current.title ?: current.contentTitle.orEmpty()) && it.isNotBlank()
                    },
                note = link.note.takeIf { it != current.note.orEmpty() },
                favourited = link.favorite.takeIf { it != current.favourited },
                archived = link.archived.takeIf { it != current.archived },
            )
        }

        syncReadLater(remoteId, serverTags, link.readLater)

        var memberOf = link.remoteFolderId
        if (wantedList != memberOf) {
            memberOf?.let { remote.removeFromList(it, remoteId) }
            wantedList?.let { remote.addToList(it, remoteId) }
            memberOf = wantedList
        }

        val pushedDirtyAt = link.dirtyAt
        store.merge { it.withLinkPushed(link.id, remoteId, memberOf, pushedDirtyAt) }
    }

    private suspend fun syncReadLater(
        remoteId: String,
        serverTags: List<RemoteTag>,
        wanted: Boolean,
    ) {
        val onServer = serverTags.firstOrNull { it.name.equals(READ_LATER_TAG, ignoreCase = true) }
        when {
            wanted && onServer == null -> remote.attachTag(remoteId, READ_LATER_TAG)
            !wanted && onServer != null -> remote.detachTag(remoteId, onServer.id)
        }
    }

    private suspend fun pull() {
        val lists = remote.listLists().filter { it.type == "manual" && it.userRole == "owner" }
        val bookmarks =
            (
                remote.listBookmarks(
                    archived = false,
                ) + remote.listBookmarks(archived = true)
            ).distinctBy { it.id }
        val membership = mutableMapOf<String, String>()
        for (list in lists) {
            for (id in remote.listBookmarkIdsInList(list.id)) membership.putIfAbsent(id, list.id)
        }
        val state = RemoteState(lists, bookmarks, membership)
        store.merge { mergeRemote(it, state) }
    }

    private fun LinksSnapshot.withoutLink(id: Long) =
        copy(
            links = links.filter { it.id != id },
            tags =
                tags.filter {
                    it.linkId !=
                        id
                },
        )

    private fun LinksSnapshot.withLinkPushed(
        id: Long,
        remoteId: String,
        remoteFolderId: String?,
        pushedDirtyAt: Long?,
    ) = copy(
        links =
            links.map {
                if (it.id != id) {
                    it
                } else {
                    // Une modification faite pendant l'envoi garde son dirtyAt plus récent : elle repartira.
                    it.copy(
                        remoteId = remoteId,
                        remoteFolderId = remoteFolderId,
                        dirtyAt = if (it.dirtyAt == pushedDirtyAt) null else it.dirtyAt,
                    )
                }
            },
    )

    companion object {
        /** Délai pendant lequel « Annuler » restaure encore un lien supprimé avant l'effacement sur le serveur. */
        const val DELETE_GRACE_MS = 60_000L
    }
}
