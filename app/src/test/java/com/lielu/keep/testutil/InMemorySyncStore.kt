package com.lielu.keep.testutil

import com.lielu.keep.backup.LinksSnapshot
import com.lielu.keep.data.sync.SyncStore

class InMemorySyncStore(
    var snapshot: LinksSnapshot = LinksSnapshot(emptyList(), emptyList(), emptyList()),
) : SyncStore {
    override suspend fun read(): LinksSnapshot = snapshot

    override suspend fun merge(transform: (LinksSnapshot) -> LinksSnapshot) {
        snapshot = transform(snapshot)
    }
}
