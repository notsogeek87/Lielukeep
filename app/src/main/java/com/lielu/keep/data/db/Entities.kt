package com.lielu.keep.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dossier de liens. [parentId] vaut null à la racine ; l'app limite l'arborescence à deux niveaux. Pas de clé
 * étrangère sur [parentId] : la restauration insère les dossiers dans l'ordre du fichier, sans tri parents d'abord.
 */
@Entity(tableName = "folders", indices = [Index(value = ["remoteId"], unique = true)])
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    /** Index dans la palette de `ui/theme/Theme.kt` (jamais une couleur en dur), null = couleur par défaut. */
    val colorIndex: Int? = null,
    val emoji: String? = null,
    /** Ordre manuel parmi les dossiers de même parent. */
    val position: Int = 0,
    /** Identifiant de la liste Karakeep correspondante (un dossier = une liste manuelle du serveur). */
    val remoteId: String? = null,
)

/**
 * Copie locale d'un signet Karakeep (le serveur fait foi). Supprimer = renseigner [deletedAt] : l'envoi de la
 * suppression au serveur n'a lieu qu'après un court délai, ce qui laisse le temps d'annuler.
 */
@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId"), Index("url"), Index("createdAt"), Index(value = ["remoteId"], unique = true)],
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val description: String = "",
    val siteName: String = "",
    val imageUrl: String? = null,
    val note: String = "",
    val folderId: Long? = null,
    val favorite: Boolean = false,
    val readLater: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long,
    val openedAt: Long? = null,
    val deletedAt: Long? = null,
    /** Identifiant du signet Karakeep ; null tant que le lien n'a pas été envoyé au serveur. */
    val remoteId: String? = null,
    /** Liste Karakeep qui contenait le signet à la dernière synchronisation (pour savoir quoi retirer). */
    val remoteFolderId: String? = null,
    /** Date de la dernière modification locale non encore envoyée ; null = synchronisé. */
    val dirtyAt: Long? = null,
)

/** Étiquette posée sur un lien (transversale aux dossiers). */
@Entity(
    tableName = "link_tags",
    primaryKeys = ["linkId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = LinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag")],
)
data class LinkTagEntity(
    val linkId: Long,
    val tag: String,
)
