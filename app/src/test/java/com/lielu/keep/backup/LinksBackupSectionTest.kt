package com.lielu.keep.backup

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.db.LinkTagEntity
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksBackupSectionTest {
    private class MemoryStore(
        var snapshot: LinksSnapshot,
    ) : LinksBackupStore {
        override suspend fun read(): LinksSnapshot = snapshot

        override suspend fun replaceAll(snapshot: LinksSnapshot) {
            this.snapshot = snapshot
        }
    }

    private val original =
        LinksSnapshot(
            folders =
                listOf(
                    FolderEntity(1, "Lecture", colorIndex = 2, emoji = "📚", position = 0),
                    FolderEntity(2, "Tech", parentId = 1, position = 1),
                ),
            links =
                listOf(
                    LinkEntity(
                        id = 10,
                        url = "https://exemple.fr/a?x=1",
                        title = "Un titre",
                        description = "Desc",
                        siteName = "exemple.fr",
                        imageUrl = "https://exemple.fr/i.png",
                        note = "à relire",
                        folderId = 2,
                        favorite = true,
                        readLater = true,
                        createdAt = 1_000L,
                        openedAt = 2_000L,
                    ),
                    LinkEntity(id = 11, url = "https://autre.org", createdAt = 3_000L, deletedAt = 4_000L),
                ),
            tags = listOf(LinkTagEntity(10, "recette"), LinkTagEntity(10, "idée")),
        )

    @Test
    fun exportThenImportRestoresEverythingAfterModification() =
        runTest {
            val store = MemoryStore(original)
            val section = LinksBackupSection(store)
            val exported = section.export()

            store.snapshot =
                LinksSnapshot(emptyList(), listOf(LinkEntity(99, "https://x.y", createdAt = 1L)), emptyList())
            section.restore(exported)

            assertEquals(original, store.snapshot)
        }

    @Test
    fun oldFileWithOnlyMandatoryFieldsGetsDefaults() =
        runTest {
            val store = MemoryStore(LinksSnapshot(emptyList(), emptyList(), emptyList()))
            val data =
                JSONObject(
                    """{"links":[{"id":5,"url":"https://a.b"}],"folders":[{"id":1,"name":"D"}]}""",
                )

            LinksBackupSection(store).restore(data)

            val link = store.snapshot.links.single()
            assertEquals("", link.title)
            assertEquals(false, link.favorite)
            assertNull(link.folderId)
            assertEquals(
                0,
                store.snapshot.folders
                    .single()
                    .position,
            )
        }

    @Test
    fun orphanReferencesAreDroppedInsteadOfFailing() =
        runTest {
            val store = MemoryStore(LinksSnapshot(emptyList(), emptyList(), emptyList()))
            val data =
                JSONObject(
                    """{"folders":[],"links":[{"id":5,"url":"https://a.b","folderId":42}],
                    |"tags":[{"linkId":5,"tag":"ok"},{"linkId":77,"tag":"orphelin"}]}
                    """.trimMargin(),
                )

            LinksBackupSection(store).restore(data)

            assertNull(
                store.snapshot.links
                    .single()
                    .folderId,
            )
            assertEquals(listOf(LinkTagEntity(5, "ok")), store.snapshot.tags)
        }

    @Test
    fun corruptSectionLeavesExistingDataUntouched() =
        runTest {
            val store = MemoryStore(original)
            val bad = JSONObject("""{"links":[{"id":1}]}""")

            val error = runCatching { LinksBackupSection(store).restore(bad) }.exceptionOrNull()

            assertTrue(error != null)
            assertEquals(original, store.snapshot)
        }
}
