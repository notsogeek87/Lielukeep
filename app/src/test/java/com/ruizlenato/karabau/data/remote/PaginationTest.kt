package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.Settings
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.Collections

/**
 * Gros comptes : faux serveur Karakeep qui pagine comme le vrai (curseur createdAt + id, étiquettes par page) et
 * refuse un curseur dont la date n'est pas annotée pour superjson.
 */
class PaginationTest {

    private var server: HttpServer? = null
    private val requests = Collections.synchronizedList(mutableListOf<String>())

    @After
    fun tearDown() {
        server?.stop(0)
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private fun HttpExchange.input(): JSONObject {
        val query = requestURI.rawQuery.orEmpty().split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        return JSONObject(query["input"] ?: "{}").getJSONObject("0")
    }

    private fun ok(json: JSONObject) = JSONArray().put(JSONObject().put("result", JSONObject().put("data", JSONObject().put("json", json)))).toString()

    private fun start(totalBookmarks: Int, totalTags: Int, tagsPageSize: Int = 50): KarabauRepository {
        // Du plus récent au plus ancien, comme Karakeep.
        val bookmarks = (0 until totalBookmarks).map { i ->
            JSONObject()
                .put("id", "b$i")
                .put("createdAt", java.time.Instant.parse("2026-01-01T00:00:00Z").minusSeconds(i.toLong()).toString())
                .put("title", "Lien $i")
                .put("tags", JSONArray().put(JSONObject().put("id", "t-lire").put("name", "à lire")))
                .put("content", JSONObject().put("type", "link").put("url", "https://exemple.fr/$i"))
        }
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/api/trpc/bookmarks.getBookmarks") { ex ->
            val input = ex.input()
            val json = input.getJSONObject("json")
            requests += json.toString()
            val limit = json.getInt("limit")
            if (limit > 100) return@createContext ex.reply(400, """[{"error":{"json":{"message":"limit too big"}}}]""")
            val start = if (json.has("cursor")) {
                // Le vrai serveur exige l'annotation superjson de la date du curseur.
                val dateMeta = input.optJSONObject("meta")?.optJSONObject("values")?.optJSONArray("cursor.createdAt")?.optString(0)
                if (dateMeta != "Date") return@createContext ex.reply(400, """[{"error":{"json":{"message":"Expected date, received string"}}}]""")
                bookmarks.indexOfFirst { it.getString("id") == json.getJSONObject("cursor").getString("id") } + 1
            } else 0
            val page = bookmarks.drop(start).take(limit)
            val result = JSONObject().put("bookmarks", JSONArray(page))
            val last = page.lastOrNull()
            if (last != null && start + page.size < bookmarks.size) {
                result.put("nextCursor", JSONObject().put("id", last.getString("id")).put("createdAt", last.getString("createdAt")))
            } else {
                result.put("nextCursor", JSONObject.NULL)
            }
            ex.reply(200, ok(result))
        }
        http.createContext("/api/trpc/tags.list") { ex ->
            val json = ex.input().getJSONObject("json")
            val page = json.getJSONObject("cursor").getInt("page")
            val nameContains = json.optString("nameContains")
            val all = (0 until totalTags).map { "étiquette $it" }.filter { nameContains.isEmpty() || it.contains(nameContains) }
            val items = all.drop(page * tagsPageSize).take(tagsPageSize).map { name ->
                JSONObject().put("id", "id-$name").put("name", name).put("numBookmarks", 1)
            }
            ex.reply(200, ok(JSONObject().put("tags", JSONArray(items))))
        }
        http.createContext("/api/trpc/bookmarks.updateTags") { ex ->
            requests += ex.requestBody.readBytes().decodeToString()
            ex.reply(200, """{"result":{"data":{"json":{}}}}""")
        }
        http.start()
        server = http
        return KarabauRepository().apply {
            configure(Settings(apiKey = "k", address = "http://127.0.0.1:${http.address.port}"))
        }
    }

    @Test
    fun `plus de 500 liens - tout est charge par curseur avec la date annotee`() = runBlocking {
        val repo = start(totalBookmarks = 1234, totalTags = 0)
        val pages = mutableListOf<Int>()
        val result = repo.getAllBookmarks(archived = false, onPage = { pages += it.size })
        assertTrue("attendu Success, obtenu $result", result is ApiResult.Success)
        val items = (result as ApiResult.Success).data
        assertEquals(1234, items.size)
        assertEquals(1234, items.map { it.id }.toSet().size)
        assertEquals(listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200, 1234), pages)
    }

    @Test
    fun `detail d un dossier - pages de 100 sans limite a 200`() = runBlocking {
        val repo = start(totalBookmarks = 450, totalTags = 0)
        val result = repo.getAllBookmarksByList(listId = "l1")
        assertEquals(450, (result as ApiResult.Success).data.size)
        assertTrue(requests.all { JSONObject(it).getInt("limit") <= 100 })
    }

    @Test
    fun `plus de 50 etiquettes - toutes les pages sont lues`() = runBlocking {
        val repo = start(totalBookmarks = 0, totalTags = 137)
        val result = repo.getAllTags()
        assertEquals(137, (result as ApiResult.Success).data.size)
    }

    @Test
    fun `serveur qui ignore la pagination des etiquettes - pas de boucle infinie`() = runBlocking {
        // Toujours la même page complète : la lecture s'arrête dès qu'aucune étiquette nouvelle n'arrive.
        val repo = start(totalBookmarks = 0, totalTags = 50, tagsPageSize = 50)
        server!!.removeContext("/api/trpc/tags.list")
        server!!.createContext("/api/trpc/tags.list") { ex ->
            val items = (0 until 50).map { JSONObject().put("id", "id$it").put("name", "t$it").put("numBookmarks", 1) }
            ex.reply(200, ok(JSONObject().put("tags", JSONArray(items))))
        }
        assertEquals(50, (repo.getAllTags() as ApiResult.Success).data.size)
    }

    @Test
    fun `retirer une etiquette - identifiant repris des liens charges, sans recherche`() = runBlocking {
        val repo = start(totalBookmarks = 3, totalTags = 0)
        repo.getAllBookmarks()
        requests.clear()
        assertTrue(repo.updateBookmarkTagNames("b1", attach = emptyList(), detach = listOf("à lire")) is ApiResult.Success)
        val detach = JSONObject(requests.single()).getJSONObject("json").getJSONArray("detach").getJSONObject(0)
        assertEquals("t-lire", detach.getString("tagId"))
    }

    @Test
    fun `retirer une etiquette inconnue - recherche par nom puis comparaison exacte`() = runBlocking {
        val repo = start(totalBookmarks = 0, totalTags = 45)
        assertTrue(repo.updateBookmarkTagNames("b1", attach = emptyList(), detach = listOf("étiquette 4")) is ApiResult.Success)
        val detach = JSONObject(requests.last()).getJSONObject("json").getJSONArray("detach").getJSONObject(0)
        assertEquals("id-étiquette 4", detach.getString("tagId"))
        assertNull(detach.optString("attachedBy").ifEmpty { null })
    }
}
