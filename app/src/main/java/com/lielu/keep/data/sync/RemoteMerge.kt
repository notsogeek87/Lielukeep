package com.lielu.keep.data.sync

import com.lielu.keep.backup.LinksSnapshot
import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinkTagEntity
import com.lielu.keep.data.karakeep.READ_LATER_TAG
import com.lielu.keep.data.karakeep.RemoteBookmark
import com.lielu.keep.data.karakeep.RemoteList
import com.lielu.keep.data.links.UrlTools

/** Ce que le serveur contient, tel que lu pendant une synchronisation. */
data class RemoteState(
    /** Listes manuelles dont l'utilisateur est propriétaire, dans l'ordre du serveur. */
    val lists: List<RemoteList>,
    val bookmarks: List<RemoteBookmark>,
    /** Identifiant de signet → identifiant de la (première) liste qui le contient. */
    val membership: Map<String, String>,
)

/**
 * Applique l'état du serveur à la copie locale (le serveur fait foi) :
 * - dossiers = listes du serveur (créés, renommés, supprimés comme elles) ;
 * - liens = signets du serveur ; un lien supprimé là-bas disparaît ici ;
 * - sauf les liens modifiés localement et pas encore envoyés (`dirtyAt`) ou en cours de suppression : on les
 *   garde tels quels, ils partiront au serveur à la prochaine passe ;
 * - données purement locales conservées (`openedAt`).
 */
fun mergeRemote(
    local: LinksSnapshot,
    remote: RemoteState,
): LinksSnapshot {
    var nextFolderId = (local.folders.maxOfOrNull { it.id } ?: 0) + 1
    var nextLinkId = (local.links.maxOfOrNull { it.id } ?: 0) + 1

    // --- Dossiers -------------------------------------------------------------------------------------------
    val localFoldersByRemote = local.folders.filter { it.remoteId != null }.associateBy { it.remoteId }
    val folderIdByRemote = mutableMapOf<String, Long>()
    remote.lists.forEach { list -> folderIdByRemote[list.id] = localFoldersByRemote[list.id]?.id ?: nextFolderId++ }
    val siblingsSeen = mutableMapOf<String?, Int>()
    val remoteFolders =
        remote.lists.map { list ->
            val position = siblingsSeen.merge(list.parentId, 1, Int::plus)!! - 1
            FolderEntity(
                id = folderIdByRemote.getValue(list.id),
                name = list.name,
                parentId = list.parentId?.let { folderIdByRemote[it] },
                colorIndex = localFoldersByRemote[list.id]?.colorIndex,
                emoji = list.icon.ifBlank { null },
                position = position,
                remoteId = list.id,
            )
        }
    val folders = local.folders.filter { it.remoteId == null } + remoteFolders
    val validFolderIds = folders.map { it.id }.toSet()

    // --- Liens ----------------------------------------------------------------------------------------------
    val remoteById = remote.bookmarks.associateBy { it.id }
    val tagsByLink = local.tags.groupBy { it.linkId }
    val links = mutableListOf<LinkEntity>()
    val tags = mutableListOf<LinkTagEntity>()

    fun keep(link: LinkEntity) {
        // Son dossier a pu disparaître du serveur entre-temps.
        links += if (link.folderId != null && link.folderId !in validFolderIds) link.copy(folderId = null) else link
        tags += tagsByLink[link.id].orEmpty()
    }

    fun rebuild(
        bookmark: RemoteBookmark,
        id: Long,
        openedAt: Long?,
    ) {
        val listId = remote.membership[bookmark.id]
        links +=
            LinkEntity(
                id = id,
                url = bookmark.url,
                title = bookmark.title?.takeIf { it.isNotBlank() } ?: bookmark.contentTitle.orEmpty(),
                description = bookmark.description.orEmpty(),
                siteName = UrlTools.domainOf(bookmark.url),
                imageUrl = bookmark.imageUrl,
                note = bookmark.note.orEmpty(),
                folderId = listId?.let { folderIdByRemote[it] },
                favorite = bookmark.favourited,
                readLater = bookmark.tags.any { it.name.equals(READ_LATER_TAG, ignoreCase = true) },
                archived = bookmark.archived,
                createdAt = bookmark.createdAt,
                openedAt = openedAt,
                remoteId = bookmark.id,
                remoteFolderId = listId,
            )
        bookmark.tags.forEach { tags += LinkTagEntity(id, it.name) }
    }

    for (link in local.links) {
        val bookmark = link.remoteId?.let { remoteById[it] }
        when {
            link.remoteId == null -> keep(link)
            link.dirtyAt != null || link.deletedAt != null -> keep(link)
            bookmark == null -> Unit // supprimé sur le serveur
            else -> rebuild(bookmark, link.id, link.openedAt)
        }
    }
    val known = local.links.mapNotNull { it.remoteId }.toSet()
    remote.bookmarks
        .filter { it.id !in known }
        .distinctBy { it.id }
        .forEach { rebuild(it, nextLinkId++, null) }

    return LinksSnapshot(folders = folders, links = links, tags = tags)
}
