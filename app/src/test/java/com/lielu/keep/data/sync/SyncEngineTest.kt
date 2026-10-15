package com.lielu.keep.data.sync

import com.lielu.keep.backup.LinksSnapshot
import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinkTagEntity
import com.lielu.keep.data.karakeep.KarakeepErrorKind
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.karakeep.RemoteList
import com.lielu.keep.testutil.FakeKarakeepRemote
import com.lielu.keep.testutil.InMemorySyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncEngineTest {
    private val now = 1_000_000L
    private val remote = FakeKarakeepRemote()
    private val store = InMemorySyncStore()

    private fun engine() = SyncEngine(remote, store, clock = { now })

    private fun local(
        folders: List<FolderEntity> = emptyList(),
        links: List<LinkEntity> = emptyList(),
        tags: List<LinkTagEntity> = emptyList(),
    ) {
        store.snapshot = LinksSnapshot(folders, links, tags)
    }

    private val lectureList = RemoteList("L1", "Lecture", "📚", null, "manual", "owner")

    @Test
    fun pullBuildsFoldersLinksMembershipAndReadLater() =
        runTest {
            remote.lists += lectureList
            remote.lists += RemoteList("L2", "Tech", "💻", "L1", "manual", "owner")
            remote.lists += RemoteList("S1", "Smart", "✨", null, "smart", "owner")
            remote.lists += RemoteList("X1", "Partagée", "👥", null, "manual", "viewer")
            remote.addBookmark(
                "b1",
                "https://exemple.fr/a",
                title = "Perso",
                favourited = true,
                tags = listOf("à lire", "kotlin"),
            )
            remote.addBookmark("b2", "https://exemple.fr/b", archived = true)
            remote.membership["L2"] = mutableSetOf("b1")

            engine().sync()

            val snapshot = store.snapshot
            assertEquals(listOf("Lecture", "Tech"), snapshot.folders.map { it.name })
            val tech = snapshot.folders.first { it.name == "Tech" }
            assertEquals(snapshot.folders.first { it.name == "Lecture" }.id, tech.parentId)
            val b1 = snapshot.links.first { it.remoteId == "b1" }
            assertEquals("Perso", b1.title)
            assertEquals(tech.id, b1.folderId)
            assertTrue(b1.favorite)
            assertTrue(b1.readLater)
            assertEquals(
                setOf("à lire", "kotlin"),
                snapshot.tags
                    .filter { it.linkId == b1.id }
                    .map { it.tag }
                    .toSet(),
            )
            val b2 = snapshot.links.first { it.remoteId == "b2" }
            assertEquals("Titre de https://exemple.fr/b", b2.title)
            assertTrue(b2.archived)
        }

    @Test
    fun newLocalLinkIsCreatedOnServerWithoutBlankTitleAndFolderMembership() =
        runTest {
            remote.lists += lectureList
            local(
                folders = listOf(FolderEntity(1, "Lecture", remoteId = "L1")),
                links = listOf(LinkEntity(1, "https://exemple.fr/a", folderId = 1, createdAt = 5, dirtyAt = 5)),
            )

            engine().sync()

            assertEquals("createBookmark https://exemple.fr/a title=null", remote.calls.first())
            val link = store.snapshot.links.single()
            val remoteId = link.remoteId
            assertNotNull(remoteId)
            assertNull(link.dirtyAt)
            assertEquals("L1", link.remoteFolderId)
            assertEquals(setOf(remoteId), remote.membership["L1"])
        }

    @Test
    fun localFavoriteIsPushedAsPatchWithOnlyChangedFields() =
        runTest {
            remote.addBookmark("b1", "https://exemple.fr/a")
            local(
                links =
                    listOf(
                        LinkEntity(
                            1,
                            "https://exemple.fr/a",
                            "Titre de https://exemple.fr/a",
                            favorite = true,
                            createdAt = 1,
                            remoteId = "b1",
                            dirtyAt = 7,
                        ),
                    ),
            )

            engine().sync()

            assertEquals("updateBookmark b1 title=null note=null fav=true archived=null", remote.calls.single())
            assertNull(
                store.snapshot.links
                    .single()
                    .dirtyAt,
            )
            assertTrue(
                store.snapshot.links
                    .single()
                    .favorite,
            )
        }

    @Test
    fun readLaterIsMappedToATagBothWays() =
        runTest {
            remote.addBookmark("b1", "https://a.fr", tags = listOf("à lire"))
            remote.addBookmark("b2", "https://b.fr")
            local(
                links =
                    listOf(
                        LinkEntity(
                            1,
                            "https://a.fr",
                            "Titre de https://a.fr",
                            readLater = false,
                            createdAt = 1,
                            remoteId = "b1",
                            dirtyAt = 3,
                        ),
                        LinkEntity(
                            2,
                            "https://b.fr",
                            "Titre de https://b.fr",
                            readLater = true,
                            createdAt = 2,
                            remoteId = "b2",
                            dirtyAt = 3,
                        ),
                    ),
            )

            engine().sync()

            assertTrue(remote.calls.any { it.startsWith("detachTag b1") })
            assertTrue(remote.calls.contains("attachTag b2 à lire"))
        }

    @Test
    fun serverWinsOnCleanLinksAndDeletesAreMirrored() =
        runTest {
            remote.addBookmark("b1", "https://a.fr", title = "Nouveau titre côté serveur")
            local(
                links =
                    listOf(
                        LinkEntity(1, "https://a.fr", "Ancien", createdAt = 1, remoteId = "b1", openedAt = 99),
                        LinkEntity(2, "https://gone.fr", "Supprimé ailleurs", createdAt = 2, remoteId = "b-gone"),
                        LinkEntity(
                            3,
                            "https://dirty.fr",
                            "Modifié ici",
                            createdAt = 3,
                            remoteId = "b-gone2",
                            dirtyAt = 50,
                        ),
                    ),
            )
            remote.addBookmark("b-gone2", "https://dirty.fr") // existe, donc le lien sale part en PATCH
            remote.bookmarks.remove("b-gone2")

            engine().sync()

            val links = store.snapshot.links
            assertEquals("Nouveau titre côté serveur", links.first { it.remoteId == "b1" }.title)
            assertEquals(99L, links.first { it.remoteId == "b1" }.openedAt)
            assertNull(links.firstOrNull { it.remoteId == "b-gone" })
            // Signet modifié localement mais supprimé sur le serveur : le serveur fait foi.
            assertNull(links.firstOrNull { it.remoteId == "b-gone2" })
        }

    @Test
    fun deletionWaitsForUndoWindowThenRemovesOnServer() =
        runTest {
            remote.addBookmark("b1", "https://a.fr")
            local(
                links =
                    listOf(
                        LinkEntity(
                            1,
                            "https://a.fr",
                            createdAt = 1,
                            remoteId = "b1",
                            deletedAt = now - 5_000,
                            dirtyAt =
                                now - 5_000,
                        ),
                    ),
            )

            engine().sync()
            assertTrue(remote.bookmarks.containsKey("b1"))
            assertEquals(1, store.snapshot.links.size)

            local(
                links =
                    listOf(
                        LinkEntity(
                            1,
                            "https://a.fr",
                            createdAt = 1,
                            remoteId = "b1",
                            deletedAt = now - 120_000,
                            dirtyAt =
                                now - 120_000,
                        ),
                    ),
            )
            engine().sync()
            assertFalse(remote.bookmarks.containsKey("b1"))
            assertTrue(store.snapshot.links.isEmpty())
        }

    @Test
    fun editMadeDuringPushIsNotLost() =
        runTest {
            local(links = listOf(LinkEntity(1, "https://a.fr", createdAt = 1, dirtyAt = 10)))
            remote.duringCreate = {
                store.snapshot =
                    store.snapshot.copy(links = store.snapshot.links.map { it.copy(favorite = true, dirtyAt = 20) })
            }

            engine().sync()

            val link = store.snapshot.links.single()
            assertEquals(20L, link.dirtyAt)
            assertTrue(link.favorite)
            assertNotNull(link.remoteId)
        }

    @Test
    fun offlineFailsWithoutTouchingLocalData() =
        runTest {
            val original = LinkEntity(1, "https://a.fr", createdAt = 1, dirtyAt = 10)
            local(links = listOf(original))
            remote.offline = true

            try {
                engine().sync()
                fail("KarakeepException attendue")
            } catch (e: KarakeepException) {
                assertEquals(KarakeepErrorKind.NETWORK, e.kind)
            }

            assertEquals(listOf(original), store.snapshot.links)
        }

    @Test
    fun folderDeletedOnServerDetachesLocalLinks() =
        runTest {
            local(
                folders = listOf(FolderEntity(1, "Vieux", remoteId = "L-old")),
                links = listOf(LinkEntity(1, "https://a.fr", createdAt = 1, folderId = 1, dirtyAt = 3)),
            )
            remote.addBookmark("b1", "https://autre.fr")

            engine().sync()

            assertTrue(store.snapshot.folders.none { it.remoteId == "L-old" })
            assertNull(
                store.snapshot.links
                    .first { it.url == "https://a.fr" }
                    .folderId,
            )
        }
}
