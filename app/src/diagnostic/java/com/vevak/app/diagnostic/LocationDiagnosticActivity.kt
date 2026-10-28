/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.location.VeVakPositionResolution
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.model.VeVakSettings
import kotlinx.coroutines.launch

/**
 * Separate package and launch activity for first-install GPS/cell/cache tests.
 * The diagnostic flavor manifest removes SMS permissions and every SMS entry point.
 */
class LocationDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DiagnosticsPage() } }
    }
}

@Composable
private fun DiagnosticsPage() {
    val context = LocalContext.current
    val resolver = remember { VeVakPositionResolver(context.applicationContext) }
    val memory = remember { VeVakLocationRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var cellularEnabled by rememberSaveable { mutableStateOf(false) }
    var ipEnabled by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf("Aucune vérification effectuée.") }
    var busy by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("VeVak — diagnostic", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Édition de test de localisation, sans autorisation de lire, recevoir ni envoyer de SMS. " +
                "Elle ne répond pas aux messages et ne remplace pas la vraie VeVak.",
            style = MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider()
        Text("1 · Autorisation ponctuelle", style = MaterialTheme.typography.titleMedium)
        Text(if (permissionGranted) "Localisation précise autorisée pour l’application."
             else "Localisation précise non autorisée. Elle est nécessaire pour créer un premier point précis.")
        OutlinedButton(onClick = {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }) { Text("Demander la localisation précise") }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text("Ouvrir la localisation Android") }
        HorizontalDivider()
        Text("2 · Sources facultatives", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = cellularEnabled, onCheckedChange = { cellularEnabled = it })
            Text("Estimation par antenne cellulaire (Internet + Android Localisation ON)")
        }
        Text("Si cochée, les identifiants de l’antenne actuellement utilisée et l’adresse IP sont transmis à beaconDB. Aucun historique d’antennes.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ipEnabled, onCheckedChange = { ipEnabled = it })
            Text("Estimation IP seule (Internet requis)")
        }
        Text("Si cochée, beaconDB voit l’adresse IP publique. Précision potentiellement très faible.")
        HorizontalDivider()
        Text("3 · Test de la position et de la mémoire", style = MaterialTheme.typography.titleMedium)
        Text("Active la localisation Android et acquiers un point. Coupe ensuite la localisation Android et relance : VeVak doit restituer son unique point mémorisé, avec son ancienneté.")
        Button(
            onClick = {
                if (!busy) {
                    busy = true
                    result = "Lecture en cours…"
                    scope.launch {
                        try {
                            val selected = VeVakSettings(
                                allowNetworkApproximation = ipEnabled,
                                allowCellularApproximation = cellularEnabled
                            )
                            result = when (val position = resolver.resolve(selected, includeTrustedPlace = false)) {
                                is VeVakPositionResolution.Coordinates ->
                                    "Source : " + position.location.source.label +
                                        "\nAncienneté : " + position.location.ageLabel() +
                                        "\nRayon : " + position.location.accuracyLabel() +
                                        "\nCoordonnées : " + position.location.latitude + ", " + position.location.longitude
                                is VeVakPositionResolution.KnownPlace -> "Lieu reconnu : " + position.label
                                VeVakPositionResolution.Unavailable ->
                                    "Aucune position disponible. Vérifie l’autorisation, l’état Android et la mémoire locale."
                            }
                        } catch (_: Exception) {
                            result = "Échec de lecture. Vérifie les autorisations de localisation."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Recherche en cours…" else "Tester toutes les sources autorisées") }

        OutlinedButton(
            onClick = {
                if (!busy) {
                    busy = true
                    result = "Lecture de la mémoire privée…"
                    scope.launch {
                        try {
                            val point = memory.fetchRememberedOnlyLocation()
                            result = if (point == null) {
                                "Aucun point n’a encore été enregistré dans la mémoire de cette édition."
                            } else {
                                "Mémoire locale : " + point.source.label +
                                    "\nAncienneté : " + point.ageLabel() +
                                    "\nRayon : " + point.accuracyLabel() +
                                    "\nCoordonnées : " + point.latitude + ", " + point.longitude
                            }
                        } catch (_: Exception) {
                            result = "Impossible de relire le stockage local."
                        } finally {
                            busy = false
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lire uniquement la mémoire (sans GPS ni réseau)") }
        Text(result, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Les coordonnées s’affichent uniquement sur ce téléphone ; aucun SMS n’est envoyé. " +
                "Une position en mémoire peut être ancienne : ce n’est pas une position actuelle.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
