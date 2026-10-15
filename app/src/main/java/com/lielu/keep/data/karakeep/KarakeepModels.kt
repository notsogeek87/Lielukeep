package com.lielu.keep.data.karakeep

/** Nom de l'étiquette Karakeep qui porte « À lire plus tard » (Karakeep n'a pas cette notion). */
const val READ_LATER_TAG = "à lire"

data class KarakeepConnection(
    /** Adresse du serveur, sans slash final ni `/api/v1` (voir [normalizeServerUrl]). */
    val baseUrl: String,
    val apiKey: String,
)

enum class KarakeepErrorKind { NOT_CONFIGURED, UNAUTHORIZED, NETWORK, NOT_FOUND, SERVER }

/** Échec d'un appel au serveur ; [message] est affichable tel quel à l'utilisateur. */
class KarakeepException(
    val kind: KarakeepErrorKind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class RemoteList(
    val id: String,
    val name: String,
    val icon: String,
    val parentId: String?,
    /** `manual` ou `smart` ; seules les listes manuelles servent de dossiers. */
    val type: String,
    /** `owner`, `editor`, `viewer` ou `public`. */
    val userRole: String,
)

data class RemoteTag(
    val id: String,
    val name: String,
)

/** Signet de type « lien » (les notes texte et fichiers de Karakeep sont ignorés par l'app). */
data class RemoteBookmark(
    val id: String,
    val url: String,
    val title: String?,
    val contentTitle: String?,
    val description: String?,
    val imageUrl: String?,
    val note: String?,
    val favourited: Boolean,
    val archived: Boolean,
    val createdAt: Long,
    val tags: List<RemoteTag>,
)

/**
 * Normalise ce que l'utilisateur tape : ajoute `https://`, retire le slash final et un éventuel `/api/v1`.
 * Renvoie null si ce n'est pas une adresse https valide (la clé d'API ne doit jamais circuler en clair).
 */
fun normalizeServerUrl(input: String): String? {
    var text = input.trim()
    if (text.isEmpty()) return null
    if (!text.contains("://")) text = "https://$text"
    if (!text.startsWith("https://", ignoreCase = true)) return null
    text = text.trimEnd('/').removeSuffix("/api/v1").trimEnd('/')
    val host = text.substringAfter("://").substringBefore('/')
    return text.takeIf { host.isNotEmpty() && host.none { it.isWhitespace() } && '.' in host.substringBefore(':') }
}
