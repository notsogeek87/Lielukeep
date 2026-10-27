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
import com.ruizlenato.karabau.data.model.UpdateTagRequest
import com.ruizlenato.karabau.data.model.TagIdRequest
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

/** Taille de page maximale acceptée par `bookmarks.getBookmarks`. */
const val BOOKMARKS_PAGE_SIZE = 100

/** Garde-fou contre une pagination qui ne s'arrêterait pas : bien au-delà d'un usage réel. */
const val MAX_BOOKMARKS = 50_000

private const val TAGS_PAGE_SIZE = 50
private const val MAX_TAG_PAGES = 400

class KarabauRepository {

    @Volatile
    private var currentSettings: Settings? = null

    /** Identifiant d'étiquette par nom, appris des réponses du serveur : Karakeep exige l'identifiant pour détacher. */
    private val tagIdsByName = ConcurrentHashMap<String, String>()

    private val apiService: KarabauApiService
        get() {
            val settings = requireSettings()
            return RetrofitClient.getOrCreate(settings.address, settings.customHeaders)
        }

    fun configure(settings: Settings) {
        currentSettings = settings
    }

    private fun requireSettings(): Settings =
        currentSettings ?: throw IllegalStateException("KarabauRepository not configured. Call configure() first.")

    private fun Settings.authHeader(): String = "Bearer $apiKey"

    private suspend inline fun <T> safeApiCall(
        crossinline block: suspend () -> ApiResult<T>
    ): ApiResult<T> {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                ApiResult.Error("HTTP_ERROR", ServerErrors.readable(e.code(), e.response()?.errorBody()?.string()))
            } catch (e: IOException) {
                ApiResult.NetworkError(e.message ?: "Network error")
            } catch (e: Exception) {
                ApiResult.Error("EXCEPTION", e.message ?: "Unknown error")
            }
        }
    }

    /** Appel authentifié : vérifie la configuration et la connexion, puis exécute [block] avec les réglages. */
    private suspend inline fun <T> authed(
        crossinline block: suspend (Settings) -> ApiResult<T>
    ): ApiResult<T> {
        val settings = currentSettings ?: return ApiResult.Error("NOT_CONFIGURED", "Repository not configured")
        if (!settings.isLoggedIn()) return ApiResult.Error("NOT_LOGGED_IN", "Not logged in")
        return safeApiCall { block(settings) }
    }

    private fun <T> Response<*>.failure(errorCode: String = "FAILED"): ApiResult<T> =
        ApiResult.Error(errorCode, ServerErrors.readable(code(), errorBody()?.string()))

    private inline fun <T> handleBatchGetResponse(
        response: Response<ResponseBody>,
        mapper: (JSONObject) -> T?
    ): ApiResult<T> {
        if (!response.isSuccessful) return response.failure()
        val json = parseBatchJson(response.body()?.string().orEmpty())
            ?: return ApiResult.Error("UNKNOWN", "Empty response")
        val result = mapper(json) ?: return ApiResult.Error("UNKNOWN", "Empty response")
        return ApiResult.Success(result)
    }

    private inline fun <T> handleTrpcPostResponse(
        response: Response<TrpcResponse<T>>,
        errorMapCode: (Int) -> String = { "UNKNOWN" }
    ): ApiResult<T> {
        if (!response.isSuccessful) return response.failure(errorMapCode(response.code()))
        val data = response.body()?.result?.data?.json
        return if (data != null) ApiResult.Success(data) else ApiResult.Error("UNKNOWN", "Empty response")
    }

    /** Corps JSON `result.data.json` d'une mutation tRPC (non groupée), ou null. */
    private fun Response<ResponseBody>.mutationJson(): JSONObject? = runCatching {
        JSONObject(body()?.string().orEmpty()).getJSONObject("result").getJSONObject("data").getJSONObject("json")
    }.getOrNull()

    /**
     * [dateFields] : chemins (« cursor.createdAt ») des champs à lire comme des dates par le serveur (superjson) ;
     * sans cette annotation, Karakeep refuse une date envoyée en simple chaîne.
     */
    private fun buildTrpcInput(dateFields: List<String> = emptyList(), block: JSONObject.() -> Unit): String {
        return JSONObject().apply {
            put("0", JSONObject().apply {
                put("json", JSONObject().apply(block))
                if (dateFields.isNotEmpty()) {
                    put("meta", JSONObject().apply {
                        put("values", JSONObject().apply {
                            dateFields.forEach { put(it, org.json.JSONArray().put("Date")) }
                        })
                    })
                }
            })
        }.toString()
    }

    private suspend fun unitCall(call: suspend (Settings) -> Response<ResponseBody>): ApiResult<Unit> =
        authed { settings ->
            val response = call(settings)
            if (response.isSuccessful) ApiResult.Success(Unit) else response.failure()
        }

    // ---------------------------------------------------------------------------------------------------------------
    // Authentification

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

    suspend fun revokeKey(apiKeyId: String): ApiResult<Unit> = authed { settings ->
        val response = apiService.revokeKey(settings.authHeader(), TrpcInput(json = RevokeKeyRequest(id = apiKeyId)))
        if (response.isSuccessful) ApiResult.Success(Unit) else response.failure()
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Liens

    /**
     * Parcourt toutes les pages (curseur) jusqu'à [maxTotal] liens. [onPage] reçoit la liste accumulée après chaque
     * page, pour afficher les premiers liens sans attendre la fin d'un gros compte.
     */
    private suspend fun paginateBookmarks(
        archived: Boolean?,
        tagId: String? = null,
        listId: String? = null,
        favourited: Boolean? = null,
        limit: Int,
        maxTotal: Int,
        onPage: ((List<BookmarkItem>) -> Unit)? = null
    ): ApiResult<List<BookmarkItem>> = authed {
        val allItems = mutableListOf<BookmarkItem>()
        val seen = HashSet<String>()
        var cursor: BookmarkCursor? = null

        while (allItems.size < maxTotal) {
            val pageResult = getBookmarksPage(
                archived = archived,
                tagId = tagId,
                listId = listId,
                favourited = favourited,
                cursor = cursor,
                limit = minOf(limit, BOOKMARKS_PAGE_SIZE, maxTotal - allItems.size)
            )
            when (pageResult) {
                is ApiResult.Success -> {
                    val page = pageResult.data
                    val fresh = page.bookmarks.filter { seen.add(it.id) }
                    // Page vide, ou serveur qui renverrait toujours la même page : on s'arrête.
                    if (fresh.isEmpty()) break
                    allItems += fresh
                    onPage?.invoke(allItems.toList())
                    cursor = page.nextCursor ?: break
                }

                is ApiResult.Error -> return@authed pageResult
                is ApiResult.NetworkError -> return@authed pageResult
            }
        }

        ApiResult.Success(allItems)
    }

    suspend fun getAllBookmarks(
        archived: Boolean? = false,
        limit: Int = BOOKMARKS_PAGE_SIZE,
        maxTotal: Int = MAX_BOOKMARKS,
        onPage: ((List<BookmarkItem>) -> Unit)? = null
    ): ApiResult<List<BookmarkItem>> =
        paginateBookmarks(archived = archived, limit = limit, maxTotal = maxTotal, onPage = onPage)

    suspend fun getAllBookmarksByTag(
        tagId: String,
        archived: Boolean? = null,
        limit: Int = BOOKMARKS_PAGE_SIZE,
        maxTotal: Int = MAX_BOOKMARKS
    ): ApiResult<List<BookmarkItem>> =
        paginateBookmarks(archived = archived, tagId = tagId, limit = limit, maxTotal = maxTotal)

    suspend fun getAllBookmarksByList(
        listId: String,
        archived: Boolean? = null,
        limit: Int = BOOKMARKS_PAGE_SIZE,
        maxTotal: Int = MAX_BOOKMARKS
    ): ApiResult<List<BookmarkItem>> =
        paginateBookmarks(archived = archived, listId = listId, limit = limit, maxTotal = maxTotal)

    suspend fun getAllFavouritedBookmarks(
        archived: Boolean? = false,
        limit: Int = BOOKMARKS_PAGE_SIZE,
        maxTotal: Int = MAX_BOOKMARKS
    ): ApiResult<List<BookmarkItem>> =
        paginateBookmarks(archived = archived, favourited = true, limit = limit, maxTotal = maxTotal)

    private suspend fun getBookmarksPage(
        archived: Boolean?,
        tagId: String?,
        listId: String?,
        favourited: Boolean?,
        cursor: BookmarkCursor?,
        limit: Int
    ): ApiResult<GetBookmarksResponse> = authed { settings ->
        val inputJson = buildTrpcInput(dateFields = if (cursor != null) listOf("cursor.createdAt") else emptyList()) {
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

        val response = apiService.getBookmarks(auth = settings.authHeader(), batch = "1", input = inputJson)
        handleBatchGetResponse(response) { json -> parseBookmarksPageFromBatchJson(json) }
    }

    /** Enregistre un lien ; renvoie l'identifiant (pour l'ajouter à un dossier) et le doublon éventuel. */
    suspend fun saveLink(
        url: String,
        title: String? = null,
        note: String? = null,
        tags: List<String> = emptyList()
    ): ApiResult<SavedLink> = authed { settings ->
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
        if (!response.isSuccessful) return@authed response.failure()
        val json = response.mutationJson()
        ApiResult.Success(
            SavedLink(
                bookmarkId = json?.optStringOrNull("id"),
                alreadyExists = json?.optBoolean("alreadyExists", false) ?: false
            )
        )
    }

    /** Identifiant du lien déjà enregistré pour cette URL, ou null (Karakeep compare l'URL telle quelle). */
    suspend fun findBookmarkIdByUrl(url: String): ApiResult<String?> = authed { settings ->
        val response = apiService.checkUrl(
            auth = settings.authHeader(),
            batch = "1",
            input = buildTrpcInput { put("url", url.trim()) }
        )
        if (!response.isSuccessful) return@authed response.failure()
        val rawBody = response.body()?.string().orEmpty()
        val data = runCatching {
            org.json.JSONArray(rawBody).getJSONObject(0).getJSONObject("result").getJSONObject("data")
        }.getOrNull()
        // La réponse est `{ json: { bookmarkId: "…" | null } }` selon la version du serveur.
        ApiResult.Success(data?.optJSONObject("json")?.optStringOrNull("bookmarkId"))
    }

    /** Modifie titre, note et/ou URL ; un champ à null n'est pas touché, une chaîne vide efface titre/note. */
    suspend fun updateBookmarkDetails(
        bookmarkId: String,
        title: String? = null,
        note: String? = null,
        url: String? = null
    ): ApiResult<Unit> = unitCall { settings ->
        apiService.updateBookmark(
            settings.authHeader(),
            TrpcInput(UpdateBookmarkRequest(bookmarkId = bookmarkId, title = title, note = note, url = url))
        )
    }

    suspend fun setBookmarkFavourited(bookmarkId: String, favourited: Boolean): ApiResult<Unit> = unitCall { settings ->
        apiService.updateBookmark(settings.authHeader(), TrpcInput(UpdateBookmarkRequest(bookmarkId, favourited = favourited)))
    }

    suspend fun setBookmarkArchived(bookmarkId: String, archived: Boolean): ApiResult<Unit> = unitCall { settings ->
        apiService.updateBookmark(settings.authHeader(), TrpcInput(UpdateBookmarkRequest(bookmarkId, archived = archived)))
    }

    suspend fun deleteBookmark(bookmarkId: String): ApiResult<Unit> = unitCall { settings ->
        apiService.deleteBookmark(settings.authHeader(), TrpcInput(DeleteBookmarkRequest(bookmarkId = bookmarkId)))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Étiquettes

    /**
     * Identifiant de l'étiquette [name] : d'abord ceux déjà vus dans les réponses du serveur, sinon une recherche
     * par nom (comparaison exacte). Null si introuvable.
     */
    private suspend fun resolveTagId(name: String): String? {
        tagIdsByName[name]?.let { return it }
        val found = (getTags(nameContains = name, limit = TAGS_PAGE_SIZE) as? ApiResult.Success)?.data.orEmpty()
        return found.firstOrNull { it.name == name }?.id
    }

    private suspend fun detachRefs(names: List<String>): List<TagRef> =
        names.distinct().map { name -> TagRef(tagId = resolveTagId(name), tagName = name) }

    /**
     * « À lire plus tard » = étiquette Karakeep [READ_LATER_TAG]. [tagNames] : étiquettes actuelles du lien, pour
     * retirer exactement celles qui désignent « à lire » (casse/accents différents).
     */
    suspend fun setBookmarkReadLater(
        bookmarkId: String,
        tagNames: List<String>,
        readLater: Boolean
    ): ApiResult<Unit> {
        val request = if (readLater) {
            UpdateTagsRequest(bookmarkId = bookmarkId, attach = listOf(TagRef(tagName = READ_LATER_TAG, attachedBy = "human")))
        } else {
            UpdateTagsRequest(bookmarkId = bookmarkId, detach = detachRefs(tagNames.filter(::isReadLaterTag)))
        }
        return unitCall { settings -> apiService.updateBookmarkTags(settings.authHeader(), TrpcInput(request)) }
    }

    /** Ajoute et retire des étiquettes par nom ; le détachement exige l'identifiant, retrouvé par le nom. */
    suspend fun updateBookmarkTagNames(
        bookmarkId: String,
        attach: List<String>,
        detach: List<String>
    ): ApiResult<Unit> {
        if (attach.isEmpty() && detach.isEmpty()) return ApiResult.Success(Unit)
        val request = UpdateTagsRequest(
            bookmarkId = bookmarkId,
            attach = attach.map { TagRef(tagName = it, attachedBy = "human") },
            detach = detachRefs(detach)
        )
        return unitCall { settings -> apiService.updateBookmarkTags(settings.authHeader(), TrpcInput(request)) }
    }

    /** Ajoute [attachName] à un lien et, pour un transfert, retire [detach] (le détachement exige l'identifiant). */
    suspend fun attachAndDetachTag(bookmarkId: String, attachName: String, detach: TagItem?): ApiResult<Unit> {
        val request = UpdateTagsRequest(
            bookmarkId = bookmarkId,
            attach = listOf(TagRef(tagName = attachName, attachedBy = "human")),
            detach = detach?.let { listOf(TagRef(tagId = it.id, tagName = it.name)) }.orEmpty()
        )
        return unitCall { settings -> apiService.updateBookmarkTags(settings.authHeader(), TrpcInput(request)) }
    }

    /** Une page d'étiquettes (`cursor.page`, à partir de 0). */
    suspend fun getTags(
        nameContains: String? = null,
        limit: Int = TAGS_PAGE_SIZE,
        sortBy: String = if (nameContains.isNullOrBlank()) "usage" else "relevance",
        page: Int = 0
    ): ApiResult<List<TagItem>> = authed { settings ->
        val inputJson = buildTrpcInput {
            put("limit", limit)
            put("sortBy", sortBy)
            put("cursor", JSONObject().apply { put("page", page) })
            nameContains?.takeIf { it.isNotBlank() }?.let { put("nameContains", it) }
        }
        val response = apiService.listTags(auth = settings.authHeader(), batch = "1", input = inputJson)
        handleBatchGetResponse(response) { json -> parseTagsListFromBatchJson(json) }
    }

    /**
     * Toutes les étiquettes, page par page. S'arrête sur une page incomplète, vide, ou qui ne contient que des
     * étiquettes déjà reçues (serveur qui ignorerait la pagination).
     */
    suspend fun getAllTags(sortBy: String = "usage"): ApiResult<List<TagItem>> {
        val all = mutableListOf<TagItem>()
        val seen = HashSet<String>()
        for (page in 0 until MAX_TAG_PAGES) {
            when (val result = getTags(limit = TAGS_PAGE_SIZE, sortBy = sortBy, page = page)) {
                is ApiResult.Success -> {
                    val fresh = result.data.filter { seen.add(it.id) }
                    all += fresh
                    if (fresh.isEmpty() || result.data.size < TAGS_PAGE_SIZE) break
                }

                is ApiResult.Error -> return if (all.isEmpty()) result else ApiResult.Success(all)
                is ApiResult.NetworkError -> return if (all.isEmpty()) result else ApiResult.Success(all)
            }
        }
        return ApiResult.Success(all)
    }

    suspend fun getTag(tagId: String): ApiResult<TagItem> = authed { settings ->
        val response = apiService.getTag(
            auth = settings.authHeader(),
            batch = "1",
            input = buildTrpcInput { put("tagId", tagId) }
        )
        handleBatchGetResponse(response) { json ->
            val id = json.optStringOrNull("id") ?: return@handleBatchGetResponse null
            val name = json.optStringOrNull("name") ?: return@handleBatchGetResponse null
            tagIdsByName[name] = id
            TagItem(id = id, name = name, numBookmarks = json.optInt("numBookmarks", 0))
        }
    }

    suspend fun renameTag(tagId: String, name: String): ApiResult<Unit> {
        val result = unitCall { settings ->
            apiService.updateTag(settings.authHeader(), TrpcInput(UpdateTagRequest(tagId = tagId, name = name.trim())))
        }
        if (result is ApiResult.Success) {
            tagIdsByName.entries.removeIf { it.value == tagId }
            tagIdsByName[name.trim()] = tagId
        }
        return result
    }

    suspend fun deleteTag(tagId: String): ApiResult<Unit> {
        val result = unitCall { settings ->
            apiService.deleteTag(settings.authHeader(), TrpcInput(TagIdRequest(tagId = tagId)))
        }
        if (result is ApiResult.Success) tagIdsByName.entries.removeIf { it.value == tagId }
        return result
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dossiers (listes Karakeep)

    suspend fun getLists(): ApiResult<List<SavedListItem>> = authed { settings ->
        val response = apiService.listLists(auth = settings.authHeader(), batch = "1", input = buildTrpcInput { })
        handleBatchGetResponse(response) { json -> parseListsFromBatchJson(json) }
    }

    suspend fun getList(listId: String): ApiResult<SavedListItem> = authed { settings ->
        val response = apiService.getList(
            auth = settings.authHeader(),
            batch = "1",
            input = buildTrpcInput { put("listId", listId) }
        )
        handleBatchGetResponse(response) { json -> parseListItem(json) }
    }

    suspend fun createList(name: String, parentId: String? = null, icon: String = DEFAULT_FOLDER_ICON): ApiResult<SavedListItem> =
        authed { settings ->
            val response = apiService.createList(
                auth = settings.authHeader(),
                request = TrpcInput(CreateListRequest(name = name.trim(), icon = icon, parentId = parentId))
            )
            if (!response.isSuccessful) return@authed response.failure()
            val created = response.mutationJson()?.let(::parseListItem)
                ?: return@authed ApiResult.Error("UNKNOWN", "Empty response")
            ApiResult.Success(created)
        }

    suspend fun renameList(listId: String, name: String): ApiResult<Unit> = unitCall { settings ->
        apiService.editList(settings.authHeader(), TrpcInput(EditListRequest(listId = listId, name = name.trim())))
    }

    suspend fun deleteList(listId: String): ApiResult<Unit> = unitCall { settings ->
        apiService.deleteList(settings.authHeader(), TrpcInput(ListIdRequest(listId = listId)))
    }

    suspend fun addBookmarkToList(listId: String, bookmarkId: String): ApiResult<Unit> = unitCall { settings ->
        apiService.addToList(settings.authHeader(), TrpcInput(ListBookmarkRequest(listId, bookmarkId)))
    }

    suspend fun removeBookmarkFromList(listId: String, bookmarkId: String): ApiResult<Unit> = unitCall { settings ->
        apiService.removeFromList(settings.authHeader(), TrpcInput(ListBookmarkRequest(listId, bookmarkId)))
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Compte

    suspend fun whoAmI(): ApiResult<WhoAmIResponse> = authed { settings ->
        val response = apiService.whoAmI(auth = settings.authHeader(), batch = "1", input = buildTrpcInput { })
        handleBatchGetResponse(response) { json ->
            WhoAmIResponse(
                id = json.optString("id"),
                name = json.optStringOrNull("name"),
                email = json.optStringOrNull("email"),
                image = json.optStringOrNull("image")
            )
        }
    }

    suspend fun updateArchiveDisplayBehaviour(behaviour: ArchiveDisplayBehaviour): ApiResult<Unit> = authed { settings ->
        val response = apiService.updateUserSettings(
            auth = settings.authHeader(),
            batch = "1",
            request = TrpcBatchInput(zero = TrpcInput(json = mapOf("archiveDisplayBehaviour" to behaviour.name.lowercase())))
        )
        if (response.isSuccessful) ApiResult.Success(Unit) else response.failure()
    }

    suspend fun getArchiveDisplayBehaviour(): ApiResult<ArchiveDisplayBehaviour> = authed { settings ->
        val inputJson = JSONObject().apply {
            put("0", JSONObject().apply {
                put("json", JSONObject.NULL)
                put("meta", JSONObject().apply {
                    put("values", org.json.JSONArray().apply { put("undefined") })
                })
            })
        }.toString()

        val response = apiService.getUserSettings(auth = settings.authHeader(), batch = "1", input = inputJson)
        handleBatchGetResponse(response) { json ->
            when (json.optStringOrNull("archiveDisplayBehaviour")?.lowercase()) {
                "show" -> ArchiveDisplayBehaviour.SHOW
                else -> ArchiveDisplayBehaviour.HIDE
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lecture des réponses

    private fun parseBookmarksPageFromBatchJson(json: JSONObject): GetBookmarksResponse {
        val bookmarksArray = json.optJSONArray("bookmarks")
            ?: return GetBookmarksResponse(bookmarks = emptyList(), nextCursor = null)

        val items = mutableListOf<BookmarkItem>()
        for (i in 0 until bookmarksArray.length()) {
            val obj = bookmarksArray.optJSONObject(i) ?: continue
            val content = obj.optJSONObject("content")
            val linkUrl = content?.optStringOrNull("url")
                ?: content?.optStringOrNull("sourceUrl")
            val subtitle = content?.optStringOrNull("publisher")
                ?: content?.optStringOrNull("author")
                ?: domainFromUrl(linkUrl)

            items += BookmarkItem(
                id = obj.optString("id"),
                title = if (obj.isNull("title")) null else obj.optString("title"),
                tags = parseTags(obj.optJSONArray("tags")),
                imageUrl = content?.optStringOrNull("imageUrl"),
                faviconUrl = content?.optStringOrNull("favicon"),
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

        return GetBookmarksResponse(bookmarks = items, nextCursor = nextCursor)
    }

    private fun parseTagsListFromBatchJson(json: JSONObject): List<TagItem> {
        val tagsArray = json.optJSONArray("tags") ?: return emptyList()

        val tags = mutableListOf<TagItem>()
        for (i in 0 until tagsArray.length()) {
            val obj = tagsArray.optJSONObject(i) ?: continue
            val id = obj.optStringOrNull("id") ?: continue
            val name = obj.optStringOrNull("name") ?: continue
            tagIdsByName[name] = id
            tags += TagItem(id = id, name = name, numBookmarks = obj.optInt("numBookmarks", 0))
        }
        return tags
    }

    private fun parseListsFromBatchJson(json: JSONObject): List<SavedListItem> {
        val listsArray = json.optJSONArray("lists") ?: return emptyList()
        return (0 until listsArray.length()).mapNotNull { i -> listsArray.optJSONObject(i)?.let(::parseListItem) }
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
            when (val item = tagsArray.opt(i) ?: continue) {
                is String -> item.trim().takeIf { it.isNotBlank() }?.let(tags::add)
                is JSONObject -> item.optStringOrNull("name")?.let { name ->
                    tags += name
                    item.optStringOrNull("id")?.let { tagIdsByName[name] = it }
                }
            }
        }
        return tags.toImmutableList()
    }
}
