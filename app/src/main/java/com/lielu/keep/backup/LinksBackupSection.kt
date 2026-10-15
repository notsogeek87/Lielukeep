package com.lielu.keep.backup

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinkTagEntity
import org.json.JSONArray
import org.json.JSONObject

/** Tout le contenu de l'app (dossiers, liens, étiquettes), lu ou remplacé en un bloc. */
data class LinksSnapshot(
    val folders: List<FolderEntity>,
    val links: List<LinkEntity>,
    val tags: List<LinkTagEntity>,
)

/** Accès à la base pour la sauvegarde ; abstrait pour tester sans Room. */
interface LinksBackupStore {
    suspend fun read(): LinksSnapshot

    /** Remplace TOUT le contenu, dans une seule transaction. */
    suspend fun replaceAll(snapshot: LinksSnapshot)
}

/**
 * Section « links » : dossiers, liens et étiquettes. Les champs ajoutés après la première version sont lus
 * avec une valeur par défaut pour que les anciens fichiers restent restaurables.
 */
class LinksBackupSection(
    private val store: LinksBackupStore,
) : BackupSection {
    override val id: String = "links"

    override suspend fun export(): JSONObject {
        val snapshot = store.read()
        return JSONObject()
            .put("folders", JSONArray(snapshot.folders.map { it.toJson() }))
            .put("links", JSONArray(snapshot.links.map { it.toJson() }))
            .put("tags", JSONArray(snapshot.tags.map { JSONObject().put("linkId", it.linkId).put("tag", it.tag) }))
    }

    override suspend fun restore(data: JSONObject) {
        // Tout est décodé avant la première écriture : un fichier abîmé ne doit rien effacer.
        val snapshot =
            LinksSnapshot(
                folders = data.objects("folders").map { it.toFolder() },
                links = data.objects("links").map { it.toLink() },
                tags = data.objects("tags").map { LinkTagEntity(it.getLong("linkId"), it.getString("tag")) },
            )
        val linkIds = snapshot.links.map { it.id }.toSet()
        val folderIds = snapshot.folders.map { it.id }.toSet()
        // Références orphelines (fichier édité à la main) : on les écarte plutôt que d'échouer sur une clé étrangère.
        store.replaceAll(
            snapshot.copy(
                links = snapshot.links.map { if (it.folderId !in folderIds) it.copy(folderId = null) else it },
                tags = snapshot.tags.filter { it.linkId in linkIds },
            ),
        )
    }

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) &&
            !isNull(key)
        ) {
            getString(key)
        } else {
            null
        }

    private fun FolderEntity.toJson() =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("parentId", parentId ?: JSONObject.NULL)
            .put("colorIndex", colorIndex ?: JSONObject.NULL)
            .put("emoji", emoji ?: JSONObject.NULL)
            .put("position", position)
            .put("remoteId", remoteId ?: JSONObject.NULL)

    private fun JSONObject.toFolder() =
        FolderEntity(
            id = getLong("id"),
            name = getString("name"),
            parentId = optLongOrNull("parentId"),
            colorIndex = if (has("colorIndex") && !isNull("colorIndex")) getInt("colorIndex") else null,
            emoji = optStringOrNull("emoji"),
            position = optInt("position", 0),
            remoteId = optStringOrNull("remoteId"),
        )

    private fun LinkEntity.toJson() =
        JSONObject()
            .put("id", id)
            .put("url", url)
            .put("title", title)
            .put("description", description)
            .put("siteName", siteName)
            .put("imageUrl", imageUrl ?: JSONObject.NULL)
            .put("note", note)
            .put("folderId", folderId ?: JSONObject.NULL)
            .put("favorite", favorite)
            .put("readLater", readLater)
            .put("archived", archived)
            .put("createdAt", createdAt)
            .put("openedAt", openedAt ?: JSONObject.NULL)
            .put("deletedAt", deletedAt ?: JSONObject.NULL)
            .put("remoteId", remoteId ?: JSONObject.NULL)
            .put("remoteFolderId", remoteFolderId ?: JSONObject.NULL)
            .put("dirtyAt", dirtyAt ?: JSONObject.NULL)

    private fun JSONObject.toLink() =
        LinkEntity(
            id = getLong("id"),
            url = getString("url"),
            title = optString("title", ""),
            description = optString("description", ""),
            siteName = optString("siteName", ""),
            imageUrl = optStringOrNull("imageUrl"),
            note = optString("note", ""),
            folderId = optLongOrNull("folderId"),
            favorite = optBoolean("favorite", false),
            readLater = optBoolean("readLater", false),
            archived = optBoolean("archived", false),
            createdAt = optLong("createdAt", 0L),
            openedAt = optLongOrNull("openedAt"),
            deletedAt = optLongOrNull("deletedAt"),
            remoteId = optStringOrNull("remoteId"),
            remoteFolderId = optStringOrNull("remoteFolderId"),
            dirtyAt = optLongOrNull("dirtyAt"),
        )
}
