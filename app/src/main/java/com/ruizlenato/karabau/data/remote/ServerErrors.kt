package com.ruizlenato.karabau.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Message d'erreur lisible à partir de la réponse du serveur : on garde le détail utile (message tRPC, champs refusés
 * par la validation) sans afficher le JSON brut. Sans détail exploitable, seul le code HTTP est donné.
 */
object ServerErrors {
    private const val MAX_LENGTH = 300

    fun readable(httpCode: Int, body: String?): String {
        val raw = body?.trim().orEmpty()
        val prefix = "HTTP $httpCode"
        if (raw.isEmpty()) return prefix

        val trpc = trpcError(raw)
        if (trpc != null) {
            val (code, message) = trpc
            val head = if (code != null) "$prefix $code" else prefix
            return if (message.isNullOrBlank()) head else "$head : ${message.take(MAX_LENGTH)}"
        }

        // Page HTML d'un proxy (502, 504…) ou texte brut : on retire les balises.
        val text = raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        return if (text.isEmpty()) prefix else "$prefix : ${text.take(MAX_LENGTH)}"
    }

    /** (code tRPC, message) pour `{"error":{"json":{…}}}` ou sa forme groupée `[{"error":…}]`, sinon null. */
    private fun trpcError(raw: String): Pair<String?, String?>? {
        val root: JSONObject = runCatching {
            if (raw.startsWith("[")) JSONArray(raw).optJSONObject(0) else JSONObject(raw)
        }.getOrNull() ?: return null
        val error = root.optJSONObject("error") ?: return null
        val json = error.optJSONObject("json") ?: error
        val code = json.optJSONObject("data")?.optString("code")?.takeIf { it.isNotBlank() }
        val message = json.optString("message").takeIf { it.isNotBlank() }?.let(::zodIssues)
        return code to message
    }

    /** Une erreur de validation (zod) arrive comme un tableau JSON dans le message : « champ : raison ; … ». */
    private fun zodIssues(message: String): String {
        if (!message.trimStart().startsWith("[")) return message
        val issues = runCatching { JSONArray(message) }.getOrNull() ?: return message
        val parts = (0 until issues.length()).mapNotNull { i ->
            val issue = issues.optJSONObject(i) ?: return@mapNotNull null
            val path = issue.optJSONArray("path")?.let { p -> (0 until p.length()).joinToString(".") { p.opt(it).toString() } }
            val reason = issue.optString("message").ifBlank { issue.optString("code") }
            if (path.isNullOrBlank()) reason else "$path : $reason"
        }
        return parts.joinToString(" ; ").ifBlank { message }
    }
}
