package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.ExchangeKeyRequest
import com.ruizlenato.karabau.data.model.ExchangeKeyResponse
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.BookmarkCursor
import com.ruizlenato.karabau.data.model.CreateBookmarkRequest
import com.ruizlenato.karabau.data.model.CreateListRequest
import com.ruizlenato.karabau.data.model.EditListRequest
import com.ruizlenato.karabau.data.model.ListBookmarkRequest
import com.ruizlenato.karabau.data.model.ListIdRequest
import com.ruizlenato.karabau.data.model.TagRef
import com.ruizlenato.karabau.data.model.UpdateTagsRequest
import com.ruizlenato.karabau.data.links.isReadLaterTag
import com.ruizlenato.karabau.data.links.READ_LATER_TAG
import com.ruizlenato.karabau.data.model.DeleteBookmarkRequest
import com.ruizlenato.karabau.data.model.GetBookmarksResponse
import com.ruizlenato.karabau.data.model.SavedListItem
import com.ruizlenato.karabau.data.model.RevokeKeyRequest
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.model.ArchiveDisplayBehaviour
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.data.model.UpdateBookmarkRequest
import com.ruizlenato.karabau.data.model.ValidateKeyRequest
import com.ruizlenato.karabau.data.model.ValidateKeyResponse
import com.ruizlenato.karabau.data.model.WhoAmIResponse
import com.ruizlenato.karabau.data.model.isLoggedIn
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Résultat d'un enregistrement : `alreadyExists` quand Karakeep a reconnu une URL déjà enregistrée. */
data class SavedLink(val bookmarkId: String?, val alreadyExists: Boolean)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: String, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val message: String) : ApiResult<Nothing>()
}

const val DEFAULT_FOLDER_ICON = "📁"

class KarabauRepository {

    private val _settingsFlow = MutableStateFlow<Settings?>(null)

    private data class ServiceCacheKey(
        val address: String,
        val customHeaders: Map<String, String>
    )

    private val _apiServiceCache = ConcurrentHashMap<ServiceCacheKey, KarabauApiService>()

    private val currentSettings: Settings?
        get() = _settingsFlow.value

    private val apiService: KarabauApiService
        get() {
            val settings = currentSettings
                ?: throw IllegalStateException("KarabauRepository not configured. Call configure() first.")
            val key = ServiceCacheKey(settings.address, settings.customHeaders)
            return _apiServiceCache.getOrPut(key) {
                RetrofitClient.getOrCreate(settings.address, settings.customHeaders)
            }
        }

    fun configure(settings: Settings) {
        _settingsFlow.value = settings
    }

    private fun requireSettings(): Settings =
        currentSettings ?: throw IllegalStateException("KarabauRepository not configured. Call configure() first.")

    private suspend inline fun <T> safeApiCall(
        crossinline block: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                ApiResult.Error("HTTP_ERROR", e.message ?: "HTTP error")
            } catch (e: IOException) {
                ApiResult.NetworkError(e.message ?: "Network error")
            } catch (e: Exception) {
                ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
            }
        }
    }

    private inline fun <T> handleBatchGetResponse(
        response: Response<ResponseBody>,
        errorContext: String,
        mapper: (JSONObject) -> T?
    ): ApiResult<T> {
        if (response.isSuccessful) {
            val rawBody = response.body()?.string().orEmpty()
            val json = parseBatchJson(rawBody)
                ?: return ApiResult.Error("UNKNOWN", "Empty response")
            val result = mapper(json)
                ?: return ApiResult.Error("UNKNOWN", "Empty response")
            return ApiResult.Success(result)
        } else {
            val errorBody = response.errorBody()?.string().orEmpty()
            return ApiResult.Error(
                "FAILED",
                errorBody.ifBlank { "Failed to $errorContext" }
            )
        }
    }


    private inline fun <T> handleTrpcPostResponse(
        response: Response<TrpcResponse<T>>,
        errorMapCode: (Int) -> String = { "UNKNOWN" }
    ): ApiResult<T> {
        if (response.isSuccessful) {
            val data = response.body()?.result?.data?.json
            return if (data != null) {
                ApiResult.Success(data)
            } else {
                ApiResult.Error("UNKNOWN", "Empty response")
            }
        } else {
            val errorBody = response.errorBody()?.string().orEmpty()
            val errorCode = errorMapCode(response.code())
            return ApiResult.Error(
                errorCode,
                errorBody.ifBlank { "Request failed: ${response.code()}" }
            )
        }
    }

    private fun buildTrpcInput(block: JSONObject.() -> Unit): String {
        return JSONObject().apply {
            put("0", JSONObject().apply {
                put("json", JSONObject().apply(block))
            })
        }.toString()
    }

    private fun Settings.authHeader(): String = "Bearer $apiKey"


    suspend fun exchangeKey(email: String, password: String): ApiResult<ExchangeKeyResponse> {
        if (currentSettings == null) return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")

        return safeApiCall {
            val randStr = UUID.randomUUID().toString().take(8)
            val request = ExchangeKeyRequest(
                email = email.trim(),
                password = password,
                keyName = "Mobile App: ($randStr)"
            )
            val response = apiService.exchangeKey(TrpcInput(json = request))
            handleTrpcPostResponse(response) { code ->
                when (code) {
                    401 -> "UNAUTHORIZED"
                    403 -> "FORBIDDEN"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    suspend fun validateKey(apiKey: String): ApiResult<ValidateKeyResponse> {
        if (currentSettings == null) return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")

        return safeApiCall {
            val request = ValidateKeyRequest(apiKey = apiKey)
            val response = apiService.validateKey(TrpcInput(json = request))
            handleTrpcPostResponse(response) { code ->
                when (code) {
                    401 -> "UNAUTHORIZED"
                    429 -> "RATE_LIMIT"
                    else -> "UNKNOWN"
                }
            }
        }
    }

    suspend fun getBookmarks(
        archived: Boolean? = false,
        tagId: String? = null,
        listId: String? = null,
        favourited: Boolean? = null,
        cursor: BookmarkCursor? = null,
        limit: Int = 20
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                archived?.let { put("archived", it) }
                put("includeContent", false)
                put("useCursorV2", true)
                put("limit", limit)
                tagId?.takeIf { it.isNotBlank() }?.let { put("tagId", it) }
                listId?.takeIf { it.isNotBlank() }?.let { put("listId", it) }
                favourited?.let { put("favourited", it) }
                cursor?.let {
                    put("cursor", JSONObject().apply {
                        put("createdAt", it.createdAt)
                        put("id", it.id)
                    })
                }
            }

            val response = apiService.getBookmarks(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load bookmarks") { json ->
                parseBookmarksPageFromBatchJson(json).bookmarks
            }
        }
    }

    suspend fun createLinkBookmark(
        url: String,
        title: String? = null,
        note: String? = null,
        tags: List<String> = emptyList()
    ): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.createBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = CreateBookmarkRequest(
                        url = url.trim(),
                        title = title,
                        note = note,
                        tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().ifEmpty { null }
                    )
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to create bookmark" })
            }
        }
    }

    /** Parcourt toutes les pages (jusqu'à [maxTotal] liens) : l'accueil filtre et recherche côté appareil. */
    suspend fun getAllBookmarks(
        archived: Boolean? = false,
        limit: Int = 100,
        maxTotal: Int = 500
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            while (allItems.size < maxTotal) {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = null,
                    listId = null,
                    favourited = null,
                    cursor = cursor,
                    limit = minOf(limit, maxTotal - allItems.size)
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        if (pageResult.data.bookmarks.isEmpty()) break
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                        if (cursor == null) break
                    }

                    is ApiResult.Error -> return@safeApiCall pageResult
                    is ApiResult.NetworkError -> return@safeApiCall pageResult
                }
            }

            ApiResult.Success(allItems)
        }
    }

    /** Comme [createLinkBookmark], mais renvoie l'identifiant (pour l'ajouter à un dossier) et le doublon éventuel. */
    suspend fun saveLink(
        url: String,
        title: String? = null,
        note: String? = null,
        tags: List<String> = emptyList()
    ): ApiResult<SavedLink> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.createBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = CreateBookmarkRequest(
                        url = url.trim(),
                        title = title,
                        note = note,
                        tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().ifEmpty { null }
                    )
                )
            )

            if (response.isSuccessful) {
                val json = runCatching {
                    JSONObject(response.body()?.string().orEmpty())
                        .getJSONObject("result").getJSONObject("data").getJSONObject("json")
                }.getOrNull()
                ApiResult.Success(
                    SavedLink(
                        bookmarkId = json?.optStringOrNull("id"),
                        alreadyExists = json?.optBoolean("alreadyExists", false) ?: false
                    )
                )
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to create bookmark" })
            }
        }
    }

    /** Identifiant du lien déjà enregistré pour cette URL, ou null (Karakeep compare l'URL telle quelle). */
    suspend fun findBookmarkIdByUrl(url: String): ApiResult<String?> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.checkUrl(
                auth = settings.authHeader(),
                batch = "1",
                input = buildTrpcInput { put("url", url.trim()) }
            )
            if (!response.isSuccessful) {
                return@safeApiCall ApiResult.Error("FAILED", "Failed to check url")
            }
            val rawBody = response.body()?.string().orEmpty()
            val data = runCatching {
                org.json.JSONArray(rawBody).getJSONObject(0).getJSONObject("result").getJSONObject("data")
            }.getOrNull()
            // La réponse est `{ json: { bookmarkId: "…" | null } }` selon la version du serveur.
            val json = data?.optJSONObject("json")
            ApiResult.Success(json?.optStringOrNull("bookmarkId"))
        }
    }

    suspend fun createList(name: String, parentId: String? = null, icon: String = DEFAULT_FOLDER_ICON): ApiResult<SavedListItem> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.createList(
                auth = settings.authHeader(),
                request = TrpcInput(CreateListRequest(name = name.trim(), icon = icon, parentId = parentId))
            )
            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string().orEmpty()
                return@safeApiCall ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to create list" })
            }
            val json = runCatching {
                JSONObject(response.body()?.string().orEmpty())
                    .getJSONObject("result").getJSONObject("data").getJSONObject("json")
            }.getOrNull()
            val created = json?.let(::parseListItem)
                ?: return@safeApiCall ApiResult.Error("UNKNOWN", "Empty response")
            ApiResult.Success(created)
        }
    }

    suspend fun renameList(listId: String, name: String): ApiResult<Unit> =
        unitCall("update list") { settings ->
            apiService.editList(settings.authHeader(), TrpcInput(EditListRequest(listId = listId, name = name.trim())))
        }

    suspend fun deleteList(listId: String): ApiResult<Unit> =
        unitCall("delete list") { settings ->
            apiService.deleteList(settings.authHeader(), TrpcInput(ListIdRequest(listId = listId)))
        }

    suspend fun addBookmarkToList(listId: String, bookmarkId: String): ApiResult<Unit> =
        unitCall("add to list") { settings ->
            apiService.addToList(settings.authHeader(), TrpcInput(ListBookmarkRequest(listId, bookmarkId)))
        }

    suspend fun removeBookmarkFromList(listId: String, bookmarkId: String): ApiResult<Unit> =
        unitCall("remove from list") { settings ->
            apiService.removeFromList(settings.authHeader(), TrpcInput(ListBookmarkRequest(listId, bookmarkId)))
        }

    /**
     * « À lire plus tard » = étiquette Karakeep [READ_LATER_TAG]. [tagNames] : étiquettes actuelles du lien, pour
     * retirer exactement celles qui désignent « à lire » (casse/accents différents).
     */
    suspend fun setBookmarkReadLater(
        bookmarkId: String,
        tagNames: List<String>,
        readLater: Boolean
    ): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val request = if (readLater) {
                UpdateTagsRequest(
                    bookmarkId = bookmarkId,
                    attach = listOf(TagRef(tagName = READ_LATER_TAG, attachedBy = "human"))
                )
            } else {
                // Les versions récentes de Karakeep exigent l'identifiant d'étiquette pour détacher.
                val known = (getTags(nameContains = READ_LATER_TAG, limit = 20) as? ApiResult.Success)?.data.orEmpty()
                val detach = tagNames.filter(::isReadLaterTag).distinct().map { name ->
                    TagRef(tagId = known.firstOrNull { it.name == name }?.id, tagName = name)
                }
                UpdateTagsRequest(bookmarkId = bookmarkId, detach = detach)
            }
            val response = apiService.updateBookmarkTags(settings.authHeader(), TrpcInput(request))
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to update tags" })
            }
        }
    }

    /** Modifie titre, note et/ou URL ; un champ à null n'est pas touché, une chaîne vide efface titre/note. */
    suspend fun updateBookmarkDetails(
        bookmarkId: String,
        title: String? = null,
        note: String? = null,
        url: String? = null
    ): ApiResult<Unit> =
        unitCall("update bookmark") { settings ->
            apiService.updateBookmark(
                settings.authHeader(),
                TrpcInput(UpdateBookmarkRequest(bookmarkId = bookmarkId, title = title, note = note, url = url))
            )
        }

    /** Ajoute et retire des étiquettes par nom ; le détachement exige l'identifiant, retrouvé par le nom. */
    suspend fun updateBookmarkTagNames(
        bookmarkId: String,
        attach: List<String>,
        detach: List<String>
    ): ApiResult<Unit> {
        if (attach.isEmpty() && detach.isEmpty()) return ApiResult.Success(Unit)
        val detachRefs = detach.map { name ->
            val known = (getTags(nameContains = name, limit = 20) as? ApiResult.Success)?.data.orEmpty()
            TagRef(tagId = known.firstOrNull { it.name == name }?.id, tagName = name)
        }
        val request = UpdateTagsRequest(
            bookmarkId = bookmarkId,
            attach = attach.map { TagRef(tagName = it, attachedBy = "human") },
            detach = detachRefs
        )
        return unitCall("update tags") { settings -> apiService.updateBookmarkTags(settings.authHeader(), TrpcInput(request)) }
    }

    private suspend fun unitCall(
        context: String,
        call: suspend (Settings) -> Response<ResponseBody>
    ): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = call(settings)
            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to $context" })
            }
        }
    }

    suspend fun getAllBookmarksByTag(
        tagId: String,
        archived: Boolean? = null,
        limit: Int = 20,
        maxTotal: Int = 200
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            while (allItems.size < maxTotal) {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = tagId,
                    listId = null,
                    favourited = null,
                    cursor = cursor,
                    limit = minOf(limit, maxTotal - allItems.size)
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        if (pageResult.data.bookmarks.isEmpty()) break
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                        if (cursor == null) break
                    }

                    is ApiResult.Error -> return@safeApiCall pageResult
                    is ApiResult.NetworkError -> return@safeApiCall pageResult
                }
            }

            ApiResult.Success(allItems)
        }
    }

    suspend fun setBookmarkFavourited(bookmarkId: String, favourited: Boolean): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.updateBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = UpdateBookmarkRequest(
                        bookmarkId = bookmarkId,
                        favourited = favourited
                    )
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to update bookmark" })
            }
        }
    }

    suspend fun setBookmarkArchived(bookmarkId: String, archived: Boolean): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.updateBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = UpdateBookmarkRequest(
                        bookmarkId = bookmarkId,
                        archived = archived
                    )
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to update bookmark" })
            }
        }
    }

    suspend fun deleteBookmark(bookmarkId: String): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val response = apiService.deleteBookmark(
                auth = settings.authHeader(),
                request = TrpcInput(
                    json = DeleteBookmarkRequest(bookmarkId = bookmarkId)
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to delete bookmark" })
            }
        }
    }

    suspend fun updateArchiveDisplayBehaviour(behaviour: ArchiveDisplayBehaviour): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val payload = mapOf(
                "archiveDisplayBehaviour" to behaviour.name.lowercase()
            )

            val response = apiService.updateUserSettings(
                auth = settings.authHeader(),
                batch = "1",
                request = TrpcBatchInput(
                    zero = TrpcInput(
                        json = payload
                    )
                )
            )

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string().orEmpty()
                ApiResult.Error("FAILED", errorBody.ifBlank { "Failed to update settings" })
            }
        }
    }

    suspend fun getArchiveDisplayBehaviour(): ApiResult<ArchiveDisplayBehaviour> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = JSONObject().apply {
                put("0", JSONObject().apply {
                    put("json", JSONObject.NULL)
                    put("meta", JSONObject().apply {
                        put("values", org.json.JSONArray().apply { put("undefined") })
                    })
                })
            }.toString()

            val response = apiService.getUserSettings(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load user settings") { json ->
                when (json.optStringOrNull("archiveDisplayBehaviour")?.lowercase()) {
                    "show" -> ArchiveDisplayBehaviour.SHOW
                    else -> ArchiveDisplayBehaviour.HIDE
                }
            }
        }
    }

    suspend fun getAllBookmarksByList(
        listId: String,
        archived: Boolean? = null,
        limit: Int = 20,
        maxTotal: Int = 200
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            while (allItems.size < maxTotal) {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = null,
                    listId = listId,
                    favourited = null,
                    cursor = cursor,
                    limit = minOf(limit, maxTotal - allItems.size)
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        if (pageResult.data.bookmarks.isEmpty()) break
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                        if (cursor == null) break
                    }

                    is ApiResult.Error -> return@safeApiCall pageResult
                    is ApiResult.NetworkError -> return@safeApiCall pageResult
                }
            }

            ApiResult.Success(allItems)
        }
    }

    suspend fun getAllFavouritedBookmarks(
        archived: Boolean? = false,
        limit: Int = 20,
        maxTotal: Int = 200
    ): ApiResult<List<BookmarkItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val allItems = mutableListOf<BookmarkItem>()
            var cursor: BookmarkCursor? = null

            while (allItems.size < maxTotal) {
                val pageResult = getBookmarksPage(
                    archived = archived,
                    tagId = null,
                    listId = null,
                    favourited = true,
                    cursor = cursor,
                    limit = minOf(limit, maxTotal - allItems.size)
                )

                when (pageResult) {
                    is ApiResult.Success -> {
                        if (pageResult.data.bookmarks.isEmpty()) break
                        allItems += pageResult.data.bookmarks
                        cursor = pageResult.data.nextCursor
                        if (cursor == null) break
                    }

                    is ApiResult.Error -> return@safeApiCall pageResult
                    is ApiResult.NetworkError -> return@safeApiCall pageResult
                }
            }

            ApiResult.Success(allItems)
        }
    }

    suspend fun whoAmI(): ApiResult<WhoAmIResponse> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput { }

            val response = apiService.whoAmI(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load profile") { json ->
                WhoAmIResponse(
                    id = json.optString("id"),
                    name = json.optStringOrNull("name"),
                    email = json.optStringOrNull("email"),
                    image = json.optStringOrNull("image")
                )
            }
        }
    }

    suspend fun getTags(
        nameContains: String? = null,
        limit: Int = 50,
        sortBy: String = if (nameContains.isNullOrBlank()) "usage" else "relevance",
        page: Int = 0
    ): ApiResult<List<TagItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                put("limit", limit)
                put("sortBy", sortBy)
                put("cursor", JSONObject().apply { put("page", page) })
                nameContains?.takeIf { it.isNotBlank() }?.let { put("nameContains", it) }
            }

            val response = apiService.listTags(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load tags") { json ->
                parseTagsListFromBatchJson(json)
            }
        }
    }

    suspend fun getTag(tagId: String): ApiResult<TagItem> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                put("tagId", tagId)
            }

            val response = apiService.getTag(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load tag") { json ->
                val id = json.optStringOrNull("id") ?: return@handleBatchGetResponse null
                val name = json.optStringOrNull("name") ?: return@handleBatchGetResponse null
                TagItem(
                    id = id,
                    name = name,
                    numBookmarks = json.optInt("numBookmarks", 0)
                )
            }
        }
    }

    suspend fun getLists(): ApiResult<List<SavedListItem>> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput { }

            val response = apiService.listLists(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load lists") { json ->
                parseListsFromBatchJson(json)
            }
        }
    }

    suspend fun getList(listId: String): ApiResult<SavedListItem> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val inputJson = buildTrpcInput {
                put("listId", listId)
            }

            val response = apiService.getList(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load list") { json ->
                parseListItem(json)
            }
        }
    }

    suspend fun revokeKey(apiKeyId: String): ApiResult<Unit> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")

        return safeApiCall {
            val auth = settings.authHeader()
            val request = RevokeKeyRequest(id = apiKeyId)
            val response = apiService.revokeKey(auth, TrpcInput(json = request))

            if (response.isSuccessful) {
                ApiResult.Success(Unit)
            } else {
                ApiResult.Error("FAILED", "Failed to revoke key")
            }
        }
    }

    private suspend fun getBookmarksPage(
        archived: Boolean?,
        tagId: String?,
        listId: String?,
        favourited: Boolean?,
        cursor: BookmarkCursor?,
        limit: Int
    ): ApiResult<GetBookmarksResponse> {
        val settings = requireSettings()

        return safeApiCall {
            val inputJson = buildTrpcInput {
                archived?.let { put("archived", it) }
                put("includeContent", false)
                put("useCursorV2", true)
                put("limit", limit)
                tagId?.takeIf { it.isNotBlank() }?.let { put("tagId", it) }
                listId?.takeIf { it.isNotBlank() }?.let { put("listId", it) }
                favourited?.let { put("favourited", it) }
                cursor?.let {
                    put("cursor", JSONObject().apply {
                        put("createdAt", it.createdAt)
                        put("id", it.id)
                    })
                }
            }

            val response = apiService.getBookmarks(
                auth = settings.authHeader(),
                batch = "1",
                input = inputJson
            )

            handleBatchGetResponse(response, "load bookmarks") { json ->
                parseBookmarksPageFromBatchJson(json)
            }
        }
    }

    private fun parseBookmarksPageFromBatchJson(json: JSONObject): GetBookmarksResponse {
        val bookmarksArray = json.optJSONArray("bookmarks")
            ?: return GetBookmarksResponse(bookmarks = emptyList(), nextCursor = null)

        val items = mutableListOf<BookmarkItem>()
        for (i in 0 until bookmarksArray.length()) {
            val obj = bookmarksArray.optJSONObject(i) ?: continue
            val content = obj.optJSONObject("content")
            val imageUrl = content?.optStringOrNull("imageUrl")
            val faviconUrl = content?.optStringOrNull("favicon")
            val linkUrl = content?.optStringOrNull("url")
                ?: content?.optStringOrNull("sourceUrl")
            val subtitle = content?.optStringOrNull("publisher")
                ?: content?.optStringOrNull("author")
                ?: domainFromUrl(linkUrl)
            val tags = parseTags(obj.optJSONArray("tags"))

            items += BookmarkItem(
                id = obj.optString("id"),
                title = if (obj.isNull("title")) null else obj.optString("title"),
                tags = tags,
                imageUrl = imageUrl,
                faviconUrl = faviconUrl,
                subtitle = subtitle,
                linkUrl = linkUrl,
                archived = obj.optBoolean("archived", false),
                favourited = obj.optBoolean("favourited", false),
                createdAt = obj.optString("createdAt", ""),
                note = obj.optStringOrNull("note")
            )
        }

        val nextCursor = json.optJSONObject("nextCursor")?.let {
            val id = it.optStringOrNull("id") ?: return@let null
            val createdAt = it.optStringOrNull("createdAt") ?: return@let null
            BookmarkCursor(createdAt = createdAt, id = id)
        }

        return GetBookmarksResponse(
            bookmarks = items,
            nextCursor = nextCursor
        )
    }

    private fun parseTagsListFromBatchJson(json: JSONObject): List<TagItem> {
        val tagsArray = json.optJSONArray("tags") ?: return emptyList()

        val tags = mutableListOf<TagItem>()
        for (i in 0 until tagsArray.length()) {
            val obj = tagsArray.optJSONObject(i) ?: continue
            val id = obj.optStringOrNull("id") ?: continue
            val name = obj.optStringOrNull("name") ?: continue
            val numBookmarks = obj.optInt("numBookmarks", 0)
            tags += TagItem(
                id = id,
                name = name,
                numBookmarks = numBookmarks
            )
        }

        return tags
    }

    private fun parseListsFromBatchJson(json: JSONObject): List<SavedListItem> {
        val listsArray = json.optJSONArray("lists") ?: return emptyList()

        val lists = mutableListOf<SavedListItem>()
        for (i in 0 until listsArray.length()) {
            val obj = listsArray.optJSONObject(i) ?: continue
            parseListItem(obj)?.let(lists::add)
        }

        return lists
    }

    private fun parseListItem(obj: JSONObject): SavedListItem? {
        val id = obj.optStringOrNull("id") ?: return null
        val name = obj.optStringOrNull("name") ?: return null

        return SavedListItem(
            id = id,
            name = name,
            description = obj.optStringOrNull("description"),
            icon = obj.optStringOrNull("icon") ?: "📚",
            parentId = obj.optStringOrNull("parentId"),
            type = obj.optStringOrNull("type") ?: "manual",
            query = obj.optStringOrNull("query"),
            isPublic = obj.optBoolean("public", false),
            hasCollaborators = obj.optBoolean("hasCollaborators", false),
            userRole = obj.optStringOrNull("userRole") ?: "owner"
        )
    }

    private fun parseBatchJson(raw: String): JSONObject? {
        val rootArray = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return null
        if (rootArray.length() == 0) return null

        return rootArray
            .optJSONObject(0)
            ?.optJSONObject("result")
            ?.optJSONObject("data")
            ?.optJSONObject("json")
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        val element = opt(name) ?: return null
        if (element == JSONObject.NULL) return null
        val value = element.toString().trim()
        return value.takeIf { it.isNotBlank() }
    }

    private fun domainFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url).host?.removePrefix("www.") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseTags(tagsArray: org.json.JSONArray?): ImmutableList<String> {
        if (tagsArray == null || tagsArray.length() == 0) return persistentListOf()

        val tags = mutableListOf<String>()
        for (i in 0 until tagsArray.length()) {
            val item = tagsArray.opt(i) ?: continue
            when (item) {
                is String -> item.trim().takeIf { it.isNotBlank() }?.let(tags::add)
                is JSONObject -> item.optStringOrNull("name")?.let(tags::add)
            }
        }
        return tags.toImmutableList()
    }
}
