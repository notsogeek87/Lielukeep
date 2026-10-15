package com.lielu.keep.ui.home

import com.lielu.keep.backup.BackupManager
import com.lielu.keep.data.db.FolderEntity
import com.lielu.keep.data.db.LinkEntity
import com.lielu.keep.data.links.LinkFilter
import com.lielu.keep.testutil.FakeLinksRepository
import com.lielu.keep.testutil.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val repo =
        FakeLinksRepository(
            initialFolders = listOf(FolderEntity(1, "Lecture"), FolderEntity(2, "Tech", parentId = 1)),
            initialLinks =
                listOf(
                    LinkEntity(1, "https://a.fr", "Kotlin", folderId = 2, favorite = true, createdAt = 1),
                    LinkEntity(2, "https://b.fr", "Gâteau", readLater = true, createdAt = 2),
                    LinkEntity(3, "https://c.fr", "Vieux", createdAt = 3, archived = true),
                ),
        )

    private fun viewModel() = HomeViewModel(FakeSettingsRepository(), BackupManager(emptyList(), "test"), repo)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun listsActiveLinksNewestFirstWithCounts() =
        runTest {
            val state = viewModel().state.value

            assertTrue(state.loaded)
            assertEquals(listOf(2L, 1L), state.links.map { it.id })
            assertEquals(2, state.totalCount)
            assertEquals(1, state.favoriteCount)
            assertEquals(1, state.readLaterCount)
        }

    @Test
    fun folderOptionsShowParentAndCountSubfolders() =
        runTest {
            val options = viewModel().state.value.folderOptions

            assertEquals(listOf("Lecture", "Lecture › Tech"), options.map { it.label })
            assertEquals(listOf(1, 1), options.map { it.count })
        }

    @Test
    fun queryAndFilterNarrowTheList() =
        runTest {
            val vm = viewModel()

            vm.onFilterSelected(LinkFilter.Folder(1))
            assertEquals(
                listOf(1L),
                vm.state.value.links
                    .map { it.id },
            )

            vm.onFilterSelected(LinkFilter.All)
            vm.onQueryChange("gateau")
            assertEquals(
                listOf(2L),
                vm.state.value.links
                    .map { it.id },
            )
        }

    @Test
    fun toggleFavoriteUsesCurrentValue() =
        runTest {
            val vm = viewModel()

            vm.toggleFavorite(2)
            assertTrue(
                repo.links.value
                    .first { it.id == 2L }
                    .favorite,
            )
            vm.toggleFavorite(2)
            assertFalse(
                repo.links.value
                    .first { it.id == 2L }
                    .favorite,
            )
        }

    @Test
    fun archiveAndTrashRemoveFromListAndCanBeUndone() =
        runTest {
            val vm = viewModel()

            vm.archive(1)
            vm.trash(2)
            assertEquals(
                emptyList<Long>(),
                vm.state.value.links
                    .map { it.id },
            )

            vm.unarchive(1)
            vm.restoreFromTrash(2)
            assertEquals(
                listOf(2L, 1L),
                vm.state.value.links
                    .map { it.id },
            )
        }

    @Test
    fun openingMarksOpenedAndClearsReadLater() =
        runTest {
            val vm = viewModel()

            vm.onOpened(2)

            val link = repo.links.value.first { it.id == 2L }
            assertEquals(1L, link.openedAt)
            assertFalse(link.readLater)
        }
}
