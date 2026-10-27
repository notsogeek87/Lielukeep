package com.ruizlenato.karabau.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerErrorsTest {

    @Test
    fun `erreur tRPC groupee - code et message sans JSON brut`() {
        val body = """[{"error":{"json":{"message":"Bookmark not found","code":-32004,"data":{"code":"NOT_FOUND","httpStatus":404}}}}]"""
        assertEquals("HTTP 404 NOT_FOUND : Bookmark not found", ServerErrors.readable(404, body))
    }

    @Test
    fun `erreur tRPC simple - meme lecture`() {
        val body = """{"error":{"json":{"message":"UNAUTHORIZED","data":{"code":"UNAUTHORIZED"}}}}"""
        assertEquals("HTTP 401 UNAUTHORIZED : UNAUTHORIZED", ServerErrors.readable(401, body))
    }

    @Test
    fun `erreur de validation zod - champ et raison`() {
        val issues = """[{"code":"invalid_type","expected":"date","received":"string","path":["cursor","createdAt"],"message":"Expected date, received string"}]"""
        val body = """[{"error":{"json":{"message":${org.json.JSONObject.quote(issues)},"data":{"code":"BAD_REQUEST"}}}}]"""
        assertEquals(
            "HTTP 400 BAD_REQUEST : cursor.createdAt : Expected date, received string",
            ServerErrors.readable(400, body)
        )
    }

    @Test
    fun `page HTML de proxy - balises retirees`() {
        assertEquals("HTTP 502 : Bad Gateway nginx", ServerErrors.readable(502, "<html><h1>Bad Gateway</h1><hr>nginx</html>"))
    }

    @Test
    fun `corps vide - seulement le code`() {
        assertEquals("HTTP 500", ServerErrors.readable(500, ""))
    }
}
