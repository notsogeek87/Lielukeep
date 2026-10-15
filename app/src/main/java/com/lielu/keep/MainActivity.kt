package com.lielu.keep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lielu.keep.ui.navigation.AppNavHost
import com.lielu.keep.ui.theme.SettingsAwareTheme
import com.lielu.keep.ui.update.AppUpdateViewModel
import com.lielu.keep.ui.update.UpdatePrompt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as LieluApplication).container

        setContent {
            SettingsAwareTheme(container.settingsRepository) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavHost(container = container)
                    val updateViewModel: AppUpdateViewModel =
                        viewModel(
                            factory =
                                viewModelFactory {
                                    initializer {
                                        AppUpdateViewModel(container.updateManager, container.updatesEnabled)
                                    }
                                },
                        )
                    UpdatePrompt(updateViewModel)
                }
            }
        }
    }
}
