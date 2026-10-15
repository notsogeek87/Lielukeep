package com.lielu.keep.ui.share

import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.testutil.FakeLinksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cleansUrlAndUsesSubjectAsTitle() =
        runTest {
            val vm =
                ShareViewModel(
                    FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev"))),
                    "https://exemple.fr/a?utm_source=x",
                    "Mon article",
                )

            assertEquals("https://exemple.fr/a", vm.state.value.url)
            assertEquals("Mon article", vm.state.value.title)
        }

    @Test
    fun noUrlGivesNullUrlAndNothingToSave() =
        runTest {
            val repo = FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev")))
            val vm = ShareViewModel(repo, "juste du texte", null)

            assertNull(vm.state.value.url)
            vm.onSave()
            assertTrue(repo.links.value.isEmpty())
        }

    @Test
    fun preselectsLastUsedFolder() =
        runTest {
            val repo =
                FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev"))).apply {
                    suggested =
                        2
                }
            val vm = ShareViewModel(repo, "https://exemple.fr", null)

            assertEquals(2L, vm.state.value.selectedFolderId)
            assertEquals(2, vm.state.value.folders.size)
        }

    @Test
    fun savesWithChosenFolderAndFallsBackToDomainAsTitle() =
        runTest {
            val repo = FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev")))
            val vm = ShareViewModel(repo, "https://www.exemple.fr/x", null)

            vm.onFolderSelected(1)
            vm.onSave()

            val saved = repo.links.value.single()
            assertEquals("exemple.fr", saved.title)
            assertEquals(1L, saved.folderId)
            assertTrue(vm.state.value.saved)
            assertEquals("Lecture", vm.state.value.savedFolderName)
        }

    @Test
    fun doubleTapSavesOnlyOnce() =
        runTest {
            val repo = FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev")))
            val vm = ShareViewModel(repo, "https://exemple.fr", null)

            vm.onSave()
            vm.onSave()

            assertEquals(1, repo.links.value.size)
        }

    @Test
    fun flagsDuplicateAndPreselectsItsFolder() =
        runTest {
            val repo = FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev")))
            repo.saveLink("https://exemple.fr/a", "A", 2)
            val vm = ShareViewModel(repo, "https://exemple.fr/a?fbclid=z", null)

            assertNotNull(vm.state.value.duplicate)
            assertEquals(2L, vm.state.value.selectedFolderId)
            assertFalse(vm.state.value.saved)
        }

    @Test
    fun createdFolderBecomesSelected() =
        runTest {
            val vm =
                ShareViewModel(
                    FakeLinksRepository(listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Dev"))),
                    "https://exemple.fr",
                    null,
                )

            vm.onCreateFolder("Recettes")

            val created =
                vm.state.value.folders
                    .first { it.name == "Recettes" }
            assertEquals(created.id, vm.state.value.selectedFolderId)
        }
}
