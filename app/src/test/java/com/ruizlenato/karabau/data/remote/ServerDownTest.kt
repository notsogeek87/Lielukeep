package com.ruizlenato.karabau.data.remote

import com.ruizlenato.karabau.data.model.Settings
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Serveur Karakeep KO : aucune requête ne doit planter l'app. Chaque appel doit rendre un [ApiResult] d'échec
 * lisible (réseau coupé = NetworkError, serveur en erreur = Error), que l'interface sait afficher.
 */
class ServerDownTest {

    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
    }

    /** Un port sur lequel plus rien n'écoute : le cas « serveur éteint ». */
    private fun closedPortAddress(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port"
    }

    /** Un serveur qui répond toujours [status] : le cas « serveur planté / proxy en erreur ». */
    private fun failingServerAddress(status: Int, body: String): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    private fun repository(address: String) = KarabauRepository().apply {
        configure(Settings(apiKey = "test-key", address = address))
    }

    @Test
    fun `serveur eteint - le chargement de la liste rend une erreur reseau`() = runBlocking {
        val result = repository(closedPortAddress()).getAllBookmarks(archived = false)
        assertTrue("attendu NetworkError, obtenu $result", result is ApiResult.NetworkError)
        assertTrue((result as ApiResult.NetworkError).message.isNotBlank())
    }

    @Test
    fun `serveur eteint - favori archivage et suppression rendent une erreur reseau`() = runBlocking {
        val repo = repository(closedPortAddress())
        assertTrue(repo.setBookmarkFavourited("b1", true) is ApiResult.NetworkError)
        assertTrue(repo.setBookmarkArchived("b1", true) is ApiResult.NetworkError)
        assertTrue(repo.deleteBookmark("b1") is ApiResult.NetworkError)
    }

    @Test
    fun `serveur eteint - le profil rend une erreur reseau sans planter`() = runBlocking {
        assertTrue(repository(closedPortAddress()).whoAmI() is ApiResult.NetworkError)
    }

    @Test
    fun `serveur en erreur 500 - chaque appel rend une erreur avec le detail du serveur`() = runBlocking {
        val repo = repository(failingServerAddress(500, "boom interne"))

        val list = repo.getAllBookmarks(archived = false)
        assertTrue("attendu Error, obtenu $list", list is ApiResult.Error)

        val delete = repo.deleteBookmark("b1")
        assertTrue("attendu Error, obtenu $delete", delete is ApiResult.Error)
        assertEquals("boom interne", (delete as ApiResult.Error).message)

        assertTrue(repo.setBookmarkFavourited("b1", true) is ApiResult.Error)
        assertTrue(repo.setBookmarkArchived("b1", true) is ApiResult.Error)
    }

    @Test
    fun `serveur en erreur 502 HTML - pas d exception de lecture JSON`() = runBlocking {
        val repo = repository(failingServerAddress(502, "<html>Bad Gateway</html>"))
        assertTrue(repo.getAllBookmarks(archived = false) is ApiResult.Error)
        assertTrue(repo.whoAmI() is ApiResult.Error)
    }
}
