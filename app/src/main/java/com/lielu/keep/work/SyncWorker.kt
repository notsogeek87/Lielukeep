package com.lielu.keep.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.lielu.keep.data.sync.SyncManager
import com.lielu.keep.data.sync.SyncRequester

/** Envoie les modifications locales à Karakeep puis relit le serveur, dès que le réseau est disponible. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val manager: SyncManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        manager.syncNow()
        // L'échec est visible dans l'app (état de synchronisation) ; le prochain changement ou lancement retente.
        return Result.success()
    }

    class Factory(
        private val manager: SyncManager,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            if (workerClassName ==
                SyncWorker::class.java.name
            ) {
                SyncWorker(appContext, workerParameters, manager)
            } else {
                null
            }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "karakeep_sync"
    }
}

class WorkManagerSyncRequester(
    private val context: Context,
) : SyncRequester {
    override fun requestSync() {
        val request =
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        // KEEP : une synchronisation déjà en attente embarquera aussi ce changement.
        WorkManager
            .getInstance(
                context,
            ).enqueueUniqueWork(SyncWorker.UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
