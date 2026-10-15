package com.lielu.keep.data.karakeep

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Client de l'API REST de Karakeep (`{serveur}/api/v1`, jeton « Bearer » créé dans Réglages › Clés d'API).
 * L'adresse et la clé sont relues à chaque appel : changer de serveur dans Paramètres s'applique tout de suite.
 */
class KarakeepApi(
    private val connection: () -> KarakeepConnection?,
    private val client: OkHttpClient = defaultClient(),
) : KarakeepRemote {
    private class Reply(
        val code: Int,
        val body: String,
    )

    override suspend fun checkConnection() {
        send("GET", "/lists").orFail()
    }

    override suspend fun listLists(): List<RemoteList> {
        val json = JSONObject(send("GET", "/lists").orFail().body)
        return json.getJSONArray("lists").objects().map {
            RemoteList(
                id = it.getString("id"),
                name = it.optString("name"),
                icon = it.optString("icon"),
                parentId = it.stringOrNull("parentId"),
                type = it.optString("type", "manual"),
                userRole = it.optString("userRole", "owner"),
            )
        }
    }

    override suspend fun createList(
        name: String,
        icon: String,
        parentId: String?,
    ): RemoteList {
        val body = JSONObject().put("name", name).put("icon", icon)
        if (parentId != null) body.put("parentId", parentId)
        val json = JSONObject(send("POST", "/lists", body = body).orFail().body)
        return RemoteList(
            id = json.getString("id"),
            name = json.optString("name", name),
            icon = json.optString("icon", icon),
            parentId = json.stringOrNull("parentId"),
            type = json.optString("type", "manual"),
            userRole = json.optString("userRole", "owner"),
        )
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ) {
        send("PATCH", "/lists/$id", body = JSONObject().put("name", name)).orFail()
    }

    override suspend fun deleteList(id: String) {
        send("DELETE", "/lists/$id").orFail(allowNotFound = true)
    }

    override suspend fun listBookmarks(archived: Boolean): List<RemoteBookmark> =
        paginate("/bookmarks", mapOf("archived" to archived.toString()))

    override suspend fun listBookmarkIdsInList(listId: String): List<String> =
        paginate("/lists/$listId/bookmarks", emptyMap()).map { it.id }

    override suspend fun getBookmark(id: String): RemoteBookmark? {
        val reply = send("GET", "/bookmarks/$id")
        if (reply.code == 404) return null
        return parseBookmark(JSONObject(reply.orFail().body))
    }

    override suspend fun createBookmark(
        url: String,
        title: String?,
        note: String?,
        favourited: Boolean,
        archived: Boolean,
    ): RemoteBookmark {
        val body =
            JSONObject()
                .put("type", "link")
                .put("url", url)
                .put("source", "mobile")
                .put("favourited", favourited)
                .put("archived", archived)
        if (!title.isNullOrBlank()) body.put("title", title)
        if (!note.isNullOrBlank()) body.put("note", note)
        val json = JSONObject(send("POST", "/bookmarks", body = body).orFail().body)
        return parseBookmark(json) ?: throw KarakeepException(KarakeepErrorKind.SERVER, "Réponse du serveur inattendue")
    }

    override suspend fun updateBookmark(
        id: String,
        title: String?,
        note: String?,
        favourited: Boolean?,
        archived: Boolean?,
    ): Boolean {
        val body = JSONObject()
        if (title != null) body.put("title", title)
        if (note != null) body.put("note", note)
        if (favourited != null) body.put("favourited", favourited)
        if (archived != null) body.put("archived", archived)
        if (body.length() == 0) return true
        val reply = send("PATCH", "/bookmarks/$id", body = body)
        if (reply.code == 404) return false
        reply.orFail()
        return true
    }

    override suspend fun deleteBookmark(id: String) {
        send("DELETE", "/bookmarks/$id").orFail(allowNotFound = true)
    }

    override suspend fun attachTag(
        bookmarkId: String,
        tagName: String,
    ) {
        val tag = JSONObject().put("tagName", tagName).put("attachedBy", "human")
        send("POST", "/bookmarks/$bookmarkId/tags", body = JSONObject().put("tags", JSONArray().put(tag))).orFail()
    }

    override suspend fun detachTag(
        bookmarkId: String,
        tagId: String,
    ) {
        val tag = JSONObject().put("tagId", tagId)
        send("DELETE", "/bookmarks/$bookmarkId/tags", body = JSONObject().put("tags", JSONArray().put(tag)))
            .orFail(allowNotFound = true)
    }

    override suspend fun addToList(
        listId: String,
        bookmarkId: String,
    ) {
        send("PUT", "/lists/$listId/bookmarks/$bookmarkId").orFail()
    }

    override suspend fun removeFromList(
        listId: String,
        bookmarkId: String,
    ) {
        val reply = send("DELETE", "/lists/$listId/bookmarks/$bookmarkId")
        // 400 = « le signet n'est pas dans cette liste » : l'état voulu est déjà atteint.
        if (reply.code != 400) reply.orFail(allowNotFound = true)
    }

    private suspend fun paginate(
        path: String,
        query: Map<String, String>,
    ): List<RemoteBookmark> {
        val all = mutableListOf<RemoteBookmark>()
        var cursor: String? = null
        do {
            val params = query + mapOf("limit" to PAGE_SIZE.toString()) + listOfNotNull(cursor?.let { "cursor" to it })
            val json = JSONObject(send("GET", path, params).orFail().body)
            json.getJSONArray("bookmarks").objects().mapNotNullTo(all, ::parseBookmark)
            cursor = json.stringOrNull("nextCursor")
        } while (cursor != null)
        return all
    }

    private suspend fun send(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
    ): Reply {
        val conn =
            connection() ?: throw KarakeepException(
                KarakeepErrorKind.NOT_CONFIGURED,
                "Serveur Karakeep non configuré (Paramètres › Serveur Karakeep).",
            )
        val base =
            "${conn.baseUrl}/api/v1$path".toHttpUrlOrNull()
                ?: throw KarakeepException(KarakeepErrorKind.NOT_CONFIGURED, "Adresse du serveur invalide.")
        val url: HttpUrl = base.newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val requestBody = body?.toString()?.toRequestBody(JSON)
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer ${conn.apiKey}")
                .header("Accept", "application/json")
                .method(method, requestBody ?: if (method == "PUT" || method == "POST") "".toRequestBody() else null)
                .build()
        return try {
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { Reply(it.code, it.body?.string().orEmpty()) }
            }
        } catch (e: IOException) {
            throw KarakeepException(
                KarakeepErrorKind.NETWORK,
                "Serveur injoignable : ${e.message ?: "erreur réseau"}",
                e,
            )
        }
    }

    private fun Reply.orFail(allowNotFound: Boolean = false): Reply {
        if (code in 200..299) return this
        if (code == 404 && allowNotFound) return this
        throw when (code) {
            401, 403 -> KarakeepException(KarakeepErrorKind.UNAUTHORIZED, "Clé d'API refusée par le serveur.")
            404 -> KarakeepException(KarakeepErrorKind.NOT_FOUND, "Élément introuvable sur le serveur.")
            else -> KarakeepException(KarakeepErrorKind.SERVER, "Erreur du serveur ($code) : ${errorText(body)}")
        }
    }

    private fun errorText(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: body.take(120)

    private fun parseBookmark(json: JSONObject): RemoteBookmark? {
        val content = json.optJSONObject("content") ?: return null
        if (content.optString("type") != "link") return null
        return RemoteBookmark(
            id = json.getString("id"),
            url = content.getString("url"),
            title = json.stringOrNull("title"),
            contentTitle = content.stringOrNull("title"),
            description = content.stringOrNull("description"),
            imageUrl = content.stringOrNull("imageUrl"),
            note = json.stringOrNull("note"),
            favourited = json.optBoolean("favourited", false),
            archived = json.optBoolean("archived", false),
            createdAt =
                json.stringOrNull("createdAt")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?: 0L,
            tags =
                json.optJSONArray("tags")?.objects().orEmpty().map {
                    RemoteTag(it.getString("id"), it.optString("name"))
                },
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        private const val PAGE_SIZE = 100
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
