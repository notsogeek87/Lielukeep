package com.lielu.keep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lielu.keep.AppContainer
import com.lielu.keep.ui.home.HomeScreen
import com.lielu.keep.ui.home.HomeViewModel
import com.lielu.keep.ui.server.ServerScreen
import com.lielu.keep.ui.server.ServerViewModel
import com.lielu.keep.ui.settings.SettingsScreen
import com.lielu.keep.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.map

object AppDestinations {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SERVER = "server"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppDestinations.HOME) {
        composable(AppDestinations.HOME) { entry ->
            val viewModel: HomeViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                HomeViewModel(
                                    container.settingsRepository,
                                    container.backupManager,
                                    container.linksRepository,
                                    syncStatus = container.syncManager.status,
                                    isConfigured = container.connectionStore.connection.map { it != null },
                                    refresh = container.syncManager::syncNow,
                                )
                            }
                        },
                )
            HomeScreen(
                viewModel = viewModel,
                linksRepository = container.linksRepository,
                onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                onServerClick = { navController.navigate(AppDestinations.SERVER) },
            )
        }

        composable(AppDestinations.SETTINGS) { entry ->
            val viewModel: SettingsViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                SettingsViewModel(
                                    container.settingsRepository,
                                    container.backupManager,
                                    container.autoBackup,
                                    container.autoBackupScheduler,
                                    container.updateManager,
                                    container.updatesEnabled,
                                )
                            }
                        },
                )
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onServerClick = { navController.navigate(AppDestinations.SERVER) },
            )
        }

        composable(AppDestinations.SERVER) { entry ->
            val viewModel: ServerViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                ServerViewModel(
                                    container.connectionStore,
                                    container.karakeep,
                                    container.syncManager::syncNow,
                                    container.syncManager.status,
                                )
                            }
                        },
                )
            ServerScreen(viewModel = viewModel, onBackClick = { navController.popBackStack() })
        }
    }
}
