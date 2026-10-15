package com.lielu.keep

import android.content.Context
import androidx.work.DelegatingWorkerFactory
import com.lielu.githubupdater.UpdateConfig
import com.lielu.githubupdater.UpdateManager
import com.lielu.keep.backup.AutoBackupRunner
import com.lielu.keep.backup.BackupManager
import com.lielu.keep.backup.BackupSection
import com.lielu.keep.backup.LinksBackupSection
import com.lielu.keep.backup.SafBackupFolder
import com.lielu.keep.backup.SettingsBackupSection
import com.lielu.keep.data.db.AppDatabase
import com.lielu.keep.data.db.RoomLinksBackupStore
import com.lielu.keep.data.karakeep.ConnectionStore
import com.lielu.keep.data.karakeep.KarakeepApi
import com.lielu.keep.data.karakeep.SharedPrefsConnectionStore
import com.lielu.keep.data.links.LinksRepository
import com.lielu.keep.data.links.RoomLinksRepository
import com.lielu.keep.data.settings.SettingsRepository
import com.lielu.keep.data.settings.SettingsRepositoryImpl
import com.lielu.keep.data.settings.settingsDataStore
import com.lielu.keep.data.sync.SyncEngine
import com.lielu.keep.data.sync.SyncManager
import com.lielu.keep.work.AutoBackupScheduler
import com.lielu.keep.work.AutoBackupWorker
import com.lielu.keep.work.SyncWorker
import com.lielu.keep.work.WorkManagerSyncRequester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Conteneur de dépendances écrit à la main : une app de ce modèle est petite et mono-module, un framework
 * d'injection ajouterait de la complexité de build (traitement d'annotations, graphes générés) sans bénéfice.
 *
 * Pour brancher les données de l'app dans la sauvegarde, ajouter une [BackupSection] à [backupSections].
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }

    /** Adresse du serveur Karakeep et clé d'API (jamais dans les sauvegardes, voir [ConnectionStore]). */
    val connectionStore: ConnectionStore = SharedPrefsConnectionStore(appContext)

    private val karakeepApi = KarakeepApi(connection = { connectionStore.current() })

    private val roomStore by lazy { RoomLinksBackupStore(database) }

    val syncManager by lazy { SyncManager(SyncEngine(karakeepApi, roomStore), connectionStore) }

    val karakeep: KarakeepApi get() = karakeepApi

    val linksRepository: LinksRepository by lazy {
        RoomLinksRepository(database.linksDao(), karakeepApi, WorkManagerSyncRequester(appContext))
    }

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext.settingsDataStore)

    /**
     * Données incluses dans toute sauvegarde (manuelle ou automatique) et restaurées à l'import.
     * Chaque section a un identifiant stable : le renommer ou le retirer rend les anciennes sauvegardes
     * illisibles pour cette section.
     */
    private val backupSections: List<BackupSection> =
        listOf(
            SettingsBackupSection(settingsRepository),
            LinksBackupSection(roomStore),
        )

    val backupManager = BackupManager(backupSections, appId = appContext.packageName)

    private val autoBackupRunner =
        AutoBackupRunner(
            settings = settingsRepository,
            backupManager = backupManager,
            openFolder = { uri -> SafBackupFolder.open(appContext, uri) },
            fileNamePrefix = BackupFileNames.prefix(appContext),
        )

    val autoBackupScheduler = AutoBackupScheduler(appContext)

    val workerFactory =
        DelegatingWorkerFactory().apply {
            addFactory(AutoBackupWorker.Factory(autoBackupRunner))
            addFactory(SyncWorker.Factory(syncManager))
        }

    /** Utilisé par « Sauvegarder maintenant » et « Restaurer la dernière sauvegarde » (voir SettingsViewModel). */
    val autoBackup: AutoBackupRunner = autoBackupRunner

    /** Mise à jour de l'app depuis les releases GitHub de CE dépôt (pas celui de la bibliothèque). */
    val updateManager =
        UpdateManager(
            appContext,
            UpdateConfig(
                githubOwner = BuildConfig.GITHUB_REPOSITORY.substringBefore('/'),
                githubRepository = BuildConfig.GITHUB_REPOSITORY.substringAfter('/'),
                checkIntervalHours = 1,
            ),
        )

    /**
     * Les mises à jour viennent de la dernière release `main` : l'APK release du variant production. Un build
     * debug ou staging a un autre applicationId, Android refuserait d'installer cet APK par-dessus : on ne leur
     * propose donc rien.
     */
    val updatesEnabled: Boolean = !BuildConfig.DEBUG && BuildConfig.FLAVOR == "production"

    /**
     * Réapplique les réglages persistés au cas où le travail périodique n'aurait jamais été planifié.
     *
     * Appelée par [LieluApplication] une fois le conteneur assigné, pas depuis un bloc `init` : cela atteint
     * WorkManager, qui redemande à l'application sa Configuration (donc ce conteneur) pendant son
     * initialisation à la demande.
     */
    fun scheduleAutoBackup() {
        applicationScope.launch {
            autoBackupScheduler.apply(settingsRepository.autoBackup.first())
        }
    }
}
