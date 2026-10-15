package com.lielu.keep.data.karakeep

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class KarakeepApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: KarakeepApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val base = server.url("/").toString().trimEnd('/')
        api = KarakeepApi(connection = { KarakeepConnection(base, "secret-key") })
    }

    @After
    fun tearDown() = server.shutdown()

    private fun reply(
        body: String,
        code: Int = 200,
    ) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private val bookmarkJson =
        """{"id":"b1","createdAt":"2024-05-01T10:00:00.000Z","title":null,"archived":false,"favourited":true,
        |"note":"n","tags":[{"id":"t1","name":"à lire","attachedBy":"human"}],
        |"content":{"type":"link","url":"https://exemple.fr/a","title":"Titre page","imageUrl":"https://exemple.fr/i.png"}}
        """.trimMargin()

    @Test
    fun sendsBearerKeyAndApiV1Path() =
        runTest {
            reply("""{"lists":[]}""")

            api.checkConnection()

            val request = server.takeRequest()
            assertEquals("/api/v1/lists", request.path)
            assertEquals("Bearer secret-key", request.getHeader("Authorization"))
        }

    @Test
    fun parsesLists() =
        runTest {
            reply(
                """{"lists":[{"id":"l1","name":"Lecture","icon":"📚","parentId":null,"type":"manual","userRole":"owner"},
                |{"id":"l2","name":"Tech","icon":"💻","parentId":"l1","type":"smart","userRole":"viewer"}]}
                """.trimMargin(),
            )

            val lists = api.listLists()

            assertEquals(listOf("l1", "l2"), lists.map { it.id })
            assertNull(lists[0].parentId)
            assertEquals("l1", lists[1].parentId)
            assertEquals("smart", lists[1].type)
        }

    @Test
    fun paginatesBookmarksAndSkipsNonLinks() =
        runTest {
            reply("""{"bookmarks":[$bookmarkJson],"nextCursor":"c2"}""")
            reply(
                """{"bookmarks":[{"id":"b2","createdAt":"2024-05-02T10:00:00.000Z","archived":false,"favourited":false,
                |"tags":[],"content":{"type":"text","text":"note"}}],"nextCursor":null}
                """.trimMargin(),
            )

            val bookmarks = api.listBookmarks(archived = false)

            assertEquals(listOf("b1"), bookmarks.map { it.id })
            val first = bookmarks.single()
            assertEquals("Titre page", first.contentTitle)
            assertNull(first.title)
            assertTrue(first.favourited)
            assertEquals(listOf("à lire"), first.tags.map { it.name })
            assertEquals(1_714_557_600_000L, first.createdAt)
            val firstRequest = server.takeRequest()
            assertEquals("false", firstRequest.requestUrl?.queryParameter("archived"))
            assertEquals("c2", server.takeRequest().requestUrl?.queryParameter("cursor"))
        }

    @Test
    fun createBookmarkSendsLinkBody() =
        runTest {
            reply(bookmarkJson, 201)

            val created =
                api.createBookmark(
                    "https://exemple.fr/a",
                    "Mon titre",
                    null,
                    favourited = true,
                    archived = false,
                )

            assertEquals("b1", created.id)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("link", body.getString("type"))
            assertEquals("https://exemple.fr/a", body.getString("url"))
            assertEquals("Mon titre", body.getString("title"))
            assertEquals(true, body.getBoolean("favourited"))
            assertFalse(body.has("note"))
        }

    @Test
    fun updateUsesPatchWithOnlyGivenFieldsAndReportsMissingBookmark() =
        runTest {
            reply("{}")
            assertTrue(api.updateBookmark("b1", favourited = true))
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("""{"favourited":true}""", request.body.readUtf8())

            reply("""{"code":"NOT_FOUND","message":"x"}""", 404)
            assertFalse(api.updateBookmark("gone", archived = true))
        }

    @Test
    fun tagsAndListMembershipCalls() =
        runTest {
            reply("""{"attached":["t1"]}""")
            reply("""{"detached":["t1"]}""")
            reply("", 204)
            reply("""{"message":"not in list"}""", 400)

            api.attachTag("b1", "à lire")
            api.detachTag("b1", "t1")
            api.addToList("l1", "b1")
            api.removeFromList("l1", "b1")

            val attach = server.takeRequest()
            assertEquals("POST", attach.method)
            assertEquals(
                "à lire",
                JSONObject(attach.body.readUtf8()).getJSONArray("tags").getJSONObject(0).getString("tagName"),
            )
            val detach = server.takeRequest()
            assertEquals("DELETE", detach.method)
            assertEquals(
                "t1",
                JSONObject(detach.body.readUtf8()).getJSONArray("tags").getJSONObject(0).getString("tagId"),
            )
            assertEquals("PUT /api/v1/lists/l1/bookmarks/b1", server.takeRequest().let { "${it.method} ${it.path}" })
            assertEquals("DELETE", server.takeRequest().method)
        }

    @Test
    fun deletingMissingBookmarkIsNotAnError() =
        runTest {
            reply("", 404)

            api.deleteBookmark("gone")
        }

    @Test
    fun getBookmarkReturnsNullWhenMissing() =
        runTest {
            reply("", 404)

            assertNull(api.getBookmark("gone"))
        }

    @Test
    fun mapsHttpFailuresToExplicitKinds() =
        runTest {
            reply("Unauthorized", 401)
            assertEquals(KarakeepErrorKind.UNAUTHORIZED, failureKind { api.checkConnection() })

            reply("""{"message":"boom"}""", 500)
            val kind = failureKind { api.listLists() }
            assertEquals(KarakeepErrorKind.SERVER, kind)
        }

    @Test
    fun unreachableServerIsANetworkError() =
        runTest {
            server.shutdown()

            assertEquals(KarakeepErrorKind.NETWORK, failureKind { api.checkConnection() })
        }

    @Test
    fun missingConnectionIsNotConfigured() =
        runTest {
            val unconfigured = KarakeepApi(connection = { null })

            assertEquals(KarakeepErrorKind.NOT_CONFIGURED, failureKind { unconfigured.checkConnection() })
        }

    private suspend fun failureKind(block: suspend () -> Unit): KarakeepErrorKind {
        try {
            block()
        } catch (e: KarakeepException) {
            return e.kind
        }
        fail("KarakeepException attendue")
        error("inatteignable")
    }
}
