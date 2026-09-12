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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * silently drop reply choices or make background location mandatory again. Optional periodic
 * last-position refresh remains a separate, explicit setting after onboarding.
 */
@Composable
fun VeVakAppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(state.settings.completedOnboarding) {
        if (state.settings.completedOnboarding) {
            // One opportunistic refresh when VeVak becomes operational (or is opened again). This
            // particular refresh is foreground work. includeTrustedPlace is false because the goal
            // here is to populate coordinate memory when possible.
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
                step = "Étape 3 sur 6",
                title = "Ce que VeVak répond",
                subtitle = "La position reste la fonction centrale. Vous choisissez les informations qui l'accompagnent."
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dernière position toujours disponible", fontWeight = FontWeight.Bold)
                    Text(
                        "Dès qu'une source fournit une position, VeVak en garde localement la dernière copie. Si Android ne peut plus actualiser la localisation au moment d'une demande, cette dernière position est utilisée et son ancienneté est indiquée. Aucun historique de déplacement n'est construit.",
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

            SetupNavigationButtons(
                onBack = viewModel::previous,
                onContinue = viewModel::next
            )
        }
    }
}

@Composable
private fun VeVakPermissionsStep(state: AppUiState, viewModel: AppViewModel) {
    VeVakTheme {
        BackHandler { viewModel.previous() }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var showSettingsHelp by remember { mutableStateOf(false) }
        var permissionRequestAttempted by remember { mutableStateOf(false) }

        val smsPermissions = remember {
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.SEND_SMS
            )
        }
        val locationPermissions = remember {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val smsPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionRequestAttempted = true
            viewModel.refreshDiagnostics()
        }
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionRequestAttempted = true
            viewModel.refreshDiagnostics()
        }

        RefreshDiagnosticsOnResume(lifecycleOwner, viewModel)

        val receiveSms = hasPermission(context, Manifest.permission.RECEIVE_SMS)
        val sendSms = hasPermission(context, Manifest.permission.SEND_SMS)
        val foregroundLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val smsReady = receiveSms && sendSms
        val allNeededGranted = smsReady && foregroundLocation

        LaunchedEffect(allNeededGranted) {
            if (allNeededGranted) viewModel.next()
        }

        SetupColumn {
            SetupHeader(
                step = "Étape 4 sur 6",
                title = "Autorisations",
                subtitle = "VeVak vous demande les accès dans l'ordre, un bloc à la fois. Dès que tout est prêt, l'étape suivante s'ouvre automatiquement."
            )

            PermissionStatusCard(
                title = "1 · SMS",
                ready = smsReady,
                detail = "Recevoir la phrase-clé uniquement lorsqu'un SMS arrive, puis envoyer la réponse au contact autorisé. VeVak ne parcourt pas votre historique de messages."
            )
            PermissionStatusCard(
                title = "2 · Localisation ponctuelle",
                ready = foregroundLocation,
                detail = "Mettre à jour la dernière position quand Android permet une acquisition. La localisation permanente n'est pas nécessaire pour terminer la configuration."
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pourquoi deux demandes ?", fontWeight = FontWeight.Bold)
                    Text(
                        "Android affiche mieux ce que vous autorisez quand chaque besoin est demandé au bon moment. VeVak commence donc par les SMS, puis demande la localisation seulement après."
                    )
                }
            }

            PermissionStatusCard(
                title = "Notifications",
                ready = true,
                detail = "Aucune autorisation nécessaire : VeVak répond sans notification de demande ni notification permanente."
            )

            when {
                !smsReady -> {
                    Button(
                        onClick = {
                            val missing = smsPermissions.filterNot { hasPermission(context, it) }.toTypedArray()
                            smsPermissionLauncher.launch(missing)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 · Autoriser les SMS")
                    }
                }

                !foregroundLocation -> {
                    Button(
                        onClick = {
                            val missing = locationPermissions.filterNot { hasPermission(context, it) }.toTypedArray()
                            locationPermissionLauncher.launch(missing)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2 · Autoriser la localisation")
                    }
                }

                else -> {
                    Text("Tout est prêt ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            if (!allNeededGranted && permissionRequestAttempted) {
                OutlinedButton(
                    onClick = { showSettingsHelp = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Une autorisation reste bloquée ?")
                }
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

        if (showSettingsHelp) {
            AlertDialog(
                onDismissRequest = { showSettingsHelp = false },
                title = { Text("Vérifier les autorisations Android") },
                text = {
                    Text(
                        "Ouvrez la fiche Android de VeVak puis vérifiez ses autorisations. Sur certains téléphones et pour certaines installations manuelles, Android peut aussi afficher dans le menu ⋮ l'option « Autoriser les paramètres restreints ». N'utilisez cette option que si vous avez installé VeVak depuis une source en laquelle vous avez confiance. Revenez ensuite dans VeVak : l'application revérifiera automatiquement les accès."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSettingsHelp = false
                            openAppSettings(context)
                        }
                    ) { Text("Ouvrir les paramètres") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsHelp = false }) { Text("Annuler") }
                }
            )
        }
    }
}

@Composable
private fun SetupColumn(content: @Composable () -> Unit) {
    val compactWidth = LocalConfiguration.current.screenWidthDp < 360
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compactWidth) 12.dp else 18.dp,
                    vertical = if (compactWidth) 12.dp else 18.dp
                ),
            verticalArrangement = Arrangement.spacedBy(if (compactWidth) 11.dp else 14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SetupHeader(step: String, title: String, subtitle: String) {
    val compactWidth = LocalConfiguration.current.screenWidthDp < 360
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.large
    ) {
        if (compactWidth) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Logo VeVak",
                        modifier = Modifier.size(46.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(step, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
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
}

@Composable
private fun SetupNavigationButtons(onBack: () -> Unit, onContinue: () -> Unit) {
    val compactWidth = LocalConfiguration.current.screenWidthDp < 360
    if (compactWidth) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continuer")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Retour")
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Retour")
            }
            Button(onClick = onContinue, modifier = Modifier.weight(1f)) {
                Text("Continuer")
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
