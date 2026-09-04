/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.ui.theme.VeVakTheme

/**
 * Final hardening wrapper.
 *
 * The existing VeVakAppRoot remains intact for every previously validated screen. Only the
 * permission step is intercepted here so the 0.3.11 behaviour cannot be accidentally coupled back
 * to POST_NOTIFICATIONS or to the former request/status notification model.
 */
@Composable
fun VeVakFinalRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    if (state.step == OnboardingStep.Permissions) {
        FinalPermissionsStep(state, viewModel)
    } else {
        VeVakAppRoot(viewModel)
    }
}

@Composable
private fun FinalPermissionsStep(state: AppUiState, viewModel: AppViewModel) {
    VeVakTheme {
        BackHandler { viewModel.previous() }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var showSettingsHelp by remember { mutableStateOf(false) }

        val permissions = remember {
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            viewModel.refreshDiagnostics()
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDiagnostics()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val receiveSms = hasPermissionFinal(context, Manifest.permission.RECEIVE_SMS)
        val sendSms = hasPermissionFinal(context, Manifest.permission.SEND_SMS)
        val foregroundLocation = hasPermissionFinal(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermissionFinal(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val allNeededGranted = receiveSms && sendSms && foregroundLocation

        LaunchedEffect(allNeededGranted) {
            if (allNeededGranted) viewModel.next()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Étape 4 sur 5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Autorisations nécessaires", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "VeVak a besoin des SMS et d'un accès de localisation ponctuel. Les notifications ne sont ni demandées ni nécessaires pour répondre à une phrase-clé."
            )

            FinalPermissionCard(
                title = "SMS",
                ready = receiveSms && sendSms,
                detail = "Reconnaître une phrase-clé provenant d'un contact autorisé puis envoyer la réponse avec la SIM du SMS reçu."
            )
            FinalPermissionCard(
                title = "Localisation ponctuelle",
                ready = foregroundLocation,
                detail = "Obtenir ou mémoriser un point lorsque Android le permet. VeVak ne demande pas la localisation permanente en arrière-plan."
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Réponses silencieuses", fontWeight = FontWeight.Bold)
                    Text(
                        "Une demande reçue ne crée pas de notification et l'absence d'autorisation de notification ne bloque jamais le SMS de réponse. Après deux réponses réussies du même contact, VeVak pourra simplement proposer la protection lors d'une prochaine ouverture de l'application."
                    )
                }
            }

            if (!allNeededGranted) {
                Button(
                    onClick = { permissionLauncher.launch(permissions) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Autoriser ce qui manque")
                }

                OutlinedButton(
                    onClick = { showSettingsHelp = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ouvrir les paramètres Android de VeVak")
                }
            } else {
                Text("Tout est prêt ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            state.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (!allNeededGranted) {
                OutlinedButton(onClick = viewModel::previous, modifier = Modifier.fillMaxWidth()) {
                    Text("Retour")
                }
            }
        }

        if (showSettingsHelp) {
            AlertDialog(
                onDismissRequest = { showSettingsHelp = false },
                title = { Text("Avant d'ouvrir les paramètres") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sur certaines installations manuelles, Android protège les autorisations SMS avec les « paramètres restreints ».")
                        Text("1. Ouvrez la fiche Android de VeVak.")
                        Text("2. Si l'option existe, ouvrez le menu ⋮ puis choisissez « Autoriser les paramètres restreints ».")
                        Text("3. Accordez ensuite les autorisations nécessaires.")
                        Text("4. Revenez simplement dans VeVak : l'application revérifiera automatiquement les autorisations et validera cette étape dès qu'elles sont accordées.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showSettingsHelp = false
                        openFinalAppSettings(context)
                    }) {
                        Text("Ouvrir les paramètres")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsHelp = false }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
private fun FinalPermissionCard(title: String, ready: Boolean, detail: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (ready) "✓" else "○",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun hasPermissionFinal(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openFinalAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
}
