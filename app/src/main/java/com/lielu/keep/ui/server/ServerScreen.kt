package com.lielu.keep.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lielu.keep.ui.formatDateTime

/** Connexion au serveur Karakeep : adresse, clé d'API, test, synchronisation manuelle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(
    viewModel: ServerViewModel,
    onBackClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sync by viewModel.syncStatus.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serveur Karakeep") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        paddingValues,
                    ).padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Tes liens et tes dossiers sont ceux de ton serveur Karakeep. Crée une clé d'API dans Karakeep " +
                    "(Réglages › Clés d'API), puis colle-la ici. Elle reste sur cet appareil et n'est jamais incluse " +
                    "dans les sauvegardes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                label = { Text("Adresse du serveur") },
                placeholder = { Text("https://keep.exemple.fr") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text("Clé d'API") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            state.message?.let {
                val colors = MaterialTheme.colorScheme
                val tone = if (state.messageIsError) colors.error else colors.primary
                Text(it, style = MaterialTheme.typography.bodyMedium, color = tone)
            }
            Button(onClick = viewModel::onSaveAndTest, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Test en cours…" else "Enregistrer et tester")
            }

            if (state.connected) {
                Text("Synchronisation", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        sync.syncing -> "Synchronisation en cours…"
                        sync.lastSyncAt != null -> "Dernière synchronisation : ${formatDateTime(sync.lastSyncAt!!)}"
                        else -> "Pas encore synchronisé."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                sync.lastError?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = viewModel::onSyncNow,
                    enabled = !sync.syncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Synchroniser maintenant")
                }
                TextButton(onClick = viewModel::onDisconnect) { Text("Se déconnecter du serveur") }
            }
        }
    }
}
