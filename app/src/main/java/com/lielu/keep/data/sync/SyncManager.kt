package com.lielu.keep.data.sync

import com.lielu.keep.data.karakeep.ConnectionStore
import com.lielu.keep.data.karakeep.KarakeepException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncStatus(
    val syncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
)

/** Demande de synchronisation en arrière-plan (WorkManager en production). */
fun interface SyncRequester {
    fun requestSync()
}

/** Une seule synchronisation à la fois, état observable par l'interface. */
class SyncManager(
    private val engine: SyncEngine,
    private val connections: ConnectionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(SyncStatus(lastSyncAt = connections.lastSyncAt))
    val status: StateFlow<SyncStatus> = _status

    /** Faux si aucun serveur n'est configuré (rien à faire) ; l'échec éventuel est dans [status]. */
    suspend fun syncNow(): Boolean {
        if (connections.current() == null) return false
        mutex.withLock {
            _status.value = _status.value.copy(syncing = true)
            try {
                val report = engine.sync()
                val now = clock()
                connections.lastSyncAt = now
                _status.value =
                    SyncStatus(
                        lastSyncAt = now,
                        lastError =
                            if (report.failedLinks >
                                0
                            ) {
                                "${report.failedLinks} lien(s) n'ont pas pu être envoyés."
                            } else {
                                null
                            },
                    )
            } catch (e: KarakeepException) {
                _status.value = _status.value.copy(syncing = false, lastError = e.message)
            }
        }
        return true
    }
}
