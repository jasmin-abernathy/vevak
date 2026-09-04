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
import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.BuildConfig
import com.vevak.app.R
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.model.MapProvider
import com.vevak.app.ui.theme.VeVakTheme

/**
 * Stable outer setup root.
 *
 * The options and permission steps are intentionally kept here so later home-screen refactors cannot
 * silently drop reply choices or make background location mandatory again. All other screens remain
 * implemented by VeVakBetaRoot.
 */
@Composable
fun VeVakAppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(state.settings.completedOnboarding) {
        if (state.settings.completedOnboarding) {
            // One opportunistic refresh when VeVak becomes operational (or is opened again). This
            // is foreground work only: no periodic/background tracker is started. includeTrustedPlace
            // is false because the goal here is to populate coordinate memory when possible.
            runCatching {
                VeVakPositionResolver(appContext).resolve(
                    settings = state.settings,
                    includeTrustedPlace = false
                )
            }
        }
    }

    when (state.step) {
        OnboardingStep.Options -> VeVakOptionsStep(state, viewModel)
        OnboardingStep.Permissions -> VeVakPermissionsStep(state, viewModel)
        else -> VeVakBetaRoot(viewModel)
    }
}

@Composable
private fun VeVakOptionsStep(state: AppUiState, viewModel: AppViewModel) {
    VeVakTheme {
        BackHandler { viewModel.previous() }
        SetupColumn {
            SetupHeader(
                step = "Étape 3 sur 5",
                title = "Ce que VeVak répond",
                subtitle = "La position reste la fonction centrale. Vous choisissez les informations qui l'accompagnent."
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dernière position toujours disponible", fontWeight = FontWeight.Bold)
                    Text(
                        "Dès qu'une source fournit une position, VeVak en garde localement la dernière copie. Si Android ne peut plus actualiser la localisation au moment d'une demande, cette dernière position est utilisée et son ancienneté est indiquée. Aucun suivi continu n'est lancé.",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text("Informations ajoutées au SMS", fontWeight = FontWeight.SemiBold)
            OptionCard {
                CheckRow("État / niveau de batterie", state.settings.includeBattery) {
                    viewModel.updateOptions(battery = it)
                }
                CheckRow("Précision ou rayon approximatif", state.settings.includeAccuracy) {
                    viewModel.updateOptions(accuracy = it)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Localisation alternative", fontWeight = FontWeight.Bold)
                    Text(
                        "Facultatif : si Android ne fournit pas de point exploitable, VeVak peut demander à beaconDB une zone approximative via l'adresse IP. Cette source reste explicitement marquée comme estimation, y compris si elle devient la dernière position mémorisée."
                    )
                    CheckRow(
                        "Activer l'estimation réseau/IP",
                        state.settings.allowNetworkApproximation
                    ) { viewModel.updateOptions(networkApproximation = it) }
                }
            }

            Text("Lien cartographique envoyé", fontWeight = FontWeight.SemiBold)
            MapProvider.entries.forEach { provider ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (provider == state.settings.mapProvider) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == state.settings.mapProvider,
                            onClick = { viewModel.updateOptions(provider = provider) }
                        )
                        Text(provider.label)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Protection anti-suivi abusif", fontWeight = FontWeight.Bold)
                    Text("En production : au moins 15 minutes entre deux réponses automatiques et 4 réponses maximum sur 24 heures, globalement pour tous les contacts.")
                    if (BuildConfig.DEBUG) {
                        Text(
                            "Build de test : seul l'intervalle est raccourci pour faciliter les essais ; le plafond de 4 demandes sur 24 h reste actif.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = viewModel::previous, modifier = Modifier.weight(1f)) {
                    Text("Retour")
                }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f)) {
                    Text("Continuer")
                }
            }
        }
    }
}

@Composable
private fun VeVakPermissionsStep(state: AppUiState, viewModel: AppViewModel) {
    VeVakTheme {
        BackHandler { viewModel.previous() }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val permissions = remember {
            buildList {
                add(Manifest.permission.RECEIVE_SMS)
                add(Manifest.permission.SEND_SMS)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            viewModel.refreshDiagnostics()
        }

        RefreshDiagnosticsOnResume(lifecycleOwner, viewModel)

        val receiveSms = hasPermission(context, Manifest.permission.RECEIVE_SMS)
        val sendSms = hasPermission(context, Manifest.permission.SEND_SMS)
        val foregroundLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        val allNeededGranted = receiveSms && sendSms && foregroundLocation && notifications

        LaunchedEffect(allNeededGranted) {
            if (allNeededGranted) viewModel.next()
        }

        SetupColumn {
            SetupHeader(
                step = "Étape 4 sur 5",
                title = "Autorisations",
                subtitle = "Une fois les accès réellement nécessaires accordés, cette étape se valide automatiquement."
            )

            PermissionStatusCard(
                title = "SMS",
                ready = receiveSms && sendSms,
                detail = "Lire uniquement les SMS entrants nécessaires à la phrase-clé et envoyer la réponse au contact autorisé."
            )
            PermissionStatusCard(
                title = "Localisation quand VeVak peut y accéder",
                ready = foregroundLocation,
                detail = "Permet de mettre à jour la dernière position lorsque VeVak est au premier plan ou qu'Android autorise une acquisition ponctuelle."
            )
            PermissionStatusCard(
                title = "Notifications",
                ready = notifications,
                detail = "Rendre visibles sur votre téléphone les demandes automatiques et l'état de VeVak."
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pas de localisation permanente", fontWeight = FontWeight.Bold)
                    Text(
                        "VeVak ne demande plus la localisation en arrière-plan comme condition de fonctionnement. Une position obtenue lorsque l'application est ouverte est mémorisée localement et peut être renvoyée plus tard si la localisation Android est coupée ou inaccessible."
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
                    onClick = { openAppSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ouvrir les paramètres Android de VeVak")
                }
            } else {
                Text("Tout est prêt ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!allNeededGranted) {
                OutlinedButton(onClick = viewModel::previous, modifier = Modifier.fillMaxWidth()) {
                    Text("Retour")
                }
            }
        }
    }
}

@Composable
private fun SetupColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        content()
    }
}

@Composable
private fun SetupHeader(step: String, title: String, subtitle: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Logo VeVak",
                modifier = Modifier.size(58.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(step, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun OptionCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun PermissionStatusCard(title: String, ready: Boolean, detail: String) {
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

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RefreshDiagnosticsOnResume(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    viewModel: AppViewModel
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDiagnostics()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
}
