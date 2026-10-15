package com.ruizlenato.karabau.data.links

/** Extraction, nettoyage et lecture d'URL, sans dépendance Android (testable en JVM). */
object UrlTools {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Paramètres de suivi retirés avant l'enregistrement (en plus de tout `utm_*`). */
    private val trackingParams = setOf(
        "fbclid", "gclid", "dclid", "msclkid", "yclid", "igshid", "mc_eid", "mc_cid", "_hsenc", "_hsmi"
    )

    /** Première URL http(s) d'un texte partagé (« Regarde ça : https://… »), sans ponctuation de fin de phrase. */
    fun extractUrl(text: String?): String? {
        val raw = text?.let { urlRegex.find(it)?.value } ?: return null
        var url = raw.trimEnd('.', ',', ';', '!', '?', ':')
        if (url.endsWith(")") && '(' !in url) url = url.dropLast(1)
        return url.takeIf { domainOf(it).isNotEmpty() }
    }

    /** Retire les paramètres de suivi ; le reste de l'URL (ordre, encodage, ancre) est conservé tel quel. */
    fun clean(url: String): String {
        val hashAt = url.indexOf('#').let { if (it < 0) url.length else it }
        val fragment = url.substring(hashAt)
        val beforeFragment = url.substring(0, hashAt)
        val queryAt = beforeFragment.indexOf('?')
        if (queryAt < 0) return url
        val base = beforeFragment.substring(0, queryAt)
        val kept = beforeFragment
            .substring(queryAt + 1)
            .split('&')
            .filter { it.isNotEmpty() && !isTracking(it.substringBefore('=')) }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    /** Nom d'hôte en minuscules, sans « www. » ni port ; vide si l'URL n'en a pas. */
    fun domainOf(url: String): String =
        url
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
            .removePrefix("www.")

    private fun isTracking(key: String): Boolean {
        val lower = key.lowercase()
        return lower.startsWith("utm_") || lower in trackingParams
    }
}
