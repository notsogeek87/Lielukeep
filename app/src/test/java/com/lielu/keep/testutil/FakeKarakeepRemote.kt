package com.lielu.keep.testutil

import com.lielu.keep.data.karakeep.KarakeepErrorKind
import com.lielu.keep.data.karakeep.KarakeepException
import com.lielu.keep.data.karakeep.KarakeepRemote
import com.lielu.keep.data.karakeep.RemoteBookmark
import com.lielu.keep.data.karakeep.RemoteList
import com.lielu.keep.data.karakeep.RemoteTag

/** Serveur Karakeep en mémoire, avec le journal des appels d'écriture. */
class FakeKarakeepRemote : KarakeepRemote {
    val lists = mutableListOf<RemoteList>()
    val bookmarks = linkedMapOf<String, RemoteBookmark>()

    /** Identifiant de liste → identifiants de signets. */
    val membership = mutableMapOf<String, MutableSet<String>>()
    val calls = mutableListOf<String>()
    var offline = false

    /** Appelé au milieu de `createBookmark`, pour simuler une modification locale pendant l'envoi. */
    var duringCreate: () -> Unit = {}
    private var counter = 0

    private fun guard() {
        if (offline) throw KarakeepException(KarakeepErrorKind.NETWORK, "Serveur injoignable")
    }

    fun addBookmark(
        id: String,
        url: String,
        title: String? = null,
        favourited: Boolean = false,
        archived: Boolean = false,
        tags: List<String> = emptyList(),
        createdAt: Long = 1L,
    ): RemoteBookmark =
        RemoteBookmark(
            id,
            url,
            title,
            "Titre de $url",
            null,
            null,
            null,
            favourited,
            archived,
            createdAt,
            tags.mapIndexed { i, name -> RemoteTag("tag-$id-$i", name) },
        ).also { bookmarks[id] = it }

    override suspend fun checkConnection() = guard()

    override suspend fun listLists(): List<RemoteList> {
        guard()
        return lists.toList()
    }

    override suspend fun createList(
        name: String,
        icon: String,
        parentId: String?,
    ): RemoteList {
        guard()
        calls += "createList $name"
        return RemoteList("list-${++counter}", name, icon, parentId, "manual", "owner").also { lists += it }
    }

    override suspend fun renameList(
        id: String,
        name: String,
    ) {
        guard()
        calls += "renameList $id $name"
        lists.replaceAll { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteList(id: String) {
        guard()
        calls += "deleteList $id"
        lists.removeAll { it.id == id }
        membership.remove(id)
    }

    override suspend fun listBookmarks(archived: Boolean): List<RemoteBookmark> {
        guard()
        return bookmarks.values.filter { it.archived == archived }
    }

    override suspend fun listBookmarkIdsInList(listId: String): List<String> {
        guard()
        return membership[listId].orEmpty().toList()
    }

    override suspend fun getBookmark(id: String): RemoteBookmark? {
        guard()
        return bookmarks[id]
    }

    override suspend fun createBookmark(
        url: String,
        title: String?,
        note: String?,
        favourited: Boolean,
        archived: Boolean,
    ): RemoteBookmark {
        guard()
        duringCreate()
        calls += "createBookmark $url title=$title"
        val id = "bm-${++counter}"
        return addBookmark(id, url, title, favourited, archived)
            .copy(note = note)
            .also { bookmarks[id] = it }
    }

    override suspend fun updateBookmark(
        id: String,
        title: String?,
        note: String?,
        favourited: Boolean?,
        archived: Boolean?,
    ): Boolean {
        guard()
        val current = bookmarks[id] ?: return false
        calls += "updateBookmark $id title=$title note=$note fav=$favourited archived=$archived"
        bookmarks[id] =
            current.copy(
                title = title ?: current.title,
                note = note ?: current.note,
                favourited = favourited ?: current.favourited,
                archived = archived ?: current.archived,
            )
        return true
    }

    override suspend fun deleteBookmark(id: String) {
        guard()
        calls += "deleteBookmark $id"
        bookmarks.remove(id)
        membership.values.forEach { it.remove(id) }
    }

    override suspend fun attachTag(
        bookmarkId: String,
        tagName: String,
    ) {
        guard()
        calls += "attachTag $bookmarkId $tagName"
        bookmarks[bookmarkId]?.let {
            bookmarks[bookmarkId] = it.copy(tags = it.tags + RemoteTag("tag-new-${++counter}", tagName))
        }
    }

    override suspend fun detachTag(
        bookmarkId: String,
        tagId: String,
    ) {
        guard()
        calls += "detachTag $bookmarkId $tagId"
        bookmarks[bookmarkId]?.let { bookmarks[bookmarkId] = it.copy(tags = it.tags.filter { t -> t.id != tagId }) }
    }

    override suspend fun addToList(
        listId: String,
        bookmarkId: String,
    ) {
        guard()
        calls += "addToList $listId $bookmarkId"
        membership.getOrPut(listId) { mutableSetOf() } += bookmarkId
    }

    override suspend fun removeFromList(
        listId: String,
        bookmarkId: String,
    ) {
        guard()
        calls += "removeFromList $listId $bookmarkId"
        membership[listId]?.remove(bookmarkId)
    }
}
