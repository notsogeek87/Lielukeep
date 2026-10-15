package com.lielu.keep.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lielu.keep.LieluApplication
import com.lielu.keep.ui.theme.SettingsAwareTheme

/**
 * Cible du menu Partager d'Android (texte brut) : n'ouvre que la feuille d'enregistrement par-dessus l'app
 * d'origine, dans sa propre tâche, sans lancer l'écran principal.
 */
class ShareActivity : ComponentActivity() {
    private val viewModel: ShareViewModel by viewModels {
        val container = (application as LieluApplication).container
        viewModelFactory {
            initializer {
                ShareViewModel(
                    container.linksRepository,
                    sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
                    sharedSubject =
                        intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LieluApplication).container

        setContent {
            SettingsAwareTheme(container.settingsRepository) {
                val state by viewModel.state.collectAsState()

                LaunchedEffect(state.saved) {
                    if (!state.saved) return@LaunchedEffect
                    val where = state.savedFolderName?.let { " dans « $it »" }.orEmpty()
                    Toast.makeText(this@ShareActivity, "Lien enregistré$where", Toast.LENGTH_SHORT).show()
                    finish()
                }

                ShareSheet(
                    state = state,
                    onTitleChange = viewModel::onTitleChange,
                    onFolderSelected = viewModel::onFolderSelected,
                    onCreateFolder = viewModel::onCreateFolder,
                    onSave = viewModel::onSave,
                    onDismiss = ::finish,
                )
            }
        }
    }
}
