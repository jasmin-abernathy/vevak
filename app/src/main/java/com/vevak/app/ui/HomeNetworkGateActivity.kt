/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vevak.app.MainActivity
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.model.VeVakSettings
import com.vevak.app.system.TrustedNetworkReader
import com.vevak.app.ui.theme.VeVakTheme
import kotlinx.coroutines.launch

/**
 * Launcher gate: a home Wi-Fi identity is part of every new VeVak configuration.
 * Existing beta installations are not locked out during migration if they predate this requirement.
 *
 * Android can hide the SSID while system Location is disabled. In that case VeVak never guesses
 * Maison from weak IPv4 traits: a voluntary app opening can instead confirm the current opaque
 * Wi-Fi session locally, without requesting an extra permission.
 */
class HomeNetworkGateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = VeVakSettingsRepository(applicationContext)
        val networkReader = TrustedNetworkReader(applicationContext)

        setContent {
            VeVakTheme {
                HomeNetworkGate(
                    repository = repository,
                    networkReader = networkReader,
                    continueToApp = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeNetworkGate(
    repository: VeVakSettingsRepository,
    networkReader: TrustedNetworkReader,
    continueToApp: () -> Unit
) {
    var settings by remember { mutableStateOf<VeVakSettings?>(null) }
    var confirmExistingHomeSession by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val current = repository.current()
        when {
            current.hasTrustedWifiConfiguration() && networkReader.shouldOfferTrustedSessionConfirmation(current) -> {
                settings = current
                confirmExistingHomeSession = true
            }
            current.hasTrustedWifiConfiguration() || current.completedOnboarding -> continueToApp()
            else -> settings = current
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (confirmExistingHomeSession) {
            val current = settings
            Text("VeVak", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Confirmer Maison pour cette connexion", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Android masque parfois le nom du Wi-Fi lorsque la localisation système est désactivée. VeVak voit bien une connexion Wi-Fi, mais refuse de deviner qu'il s'agit de Maison."
            )
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Confirmation locale", fontWeight = FontWeight.Bold)
                    Text(
                        "Si vous êtes actuellement connecté à votre Wi-Fi Maison, vous pouvez confirmer uniquement cette session. VeVak conserve une empreinte opaque de la session, pas le nom du réseau. Cette confirmation peut être redemandée après une reconnexion ou un redémarrage si Android ne fournit toujours pas d'identifiant durable."
                    )
                }
            }
            Button(
                enabled = current != null,
                onClick = {
                    val base = current ?: return@Button
                    if (networkReader.confirmCurrentSessionAsTrusted(base)) {
                        continueToApp()
                    } else {
                        message = "Impossible de confirmer cette session Wi-Fi. Vérifiez que le téléphone est bien connecté en Wi-Fi."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Oui, ce Wi-Fi est Maison")
            }
            OutlinedButton(
                onClick = {
                    networkReader.dismissCurrentSessionConfirmation()
                    continueToApp()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continuer sans le marquer Maison")
            }
            Text(
                "Aucune permission supplémentaire n'est demandée. VeVak ne considère jamais automatiquement un réseau inconnu comme Maison.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } else {
            Text("VeVak", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Configurer le réseau Maison", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Avant de poursuivre, connectez ce téléphone au Wi-Fi de votre domicile. VeVak enregistre uniquement une empreinte locale du réseau quand Android le permet, jamais son nom en clair."
            )
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pourquoi maintenant ?", fontWeight = FontWeight.Bold)
                    Text("Maison fait partie de la configuration de sécurité initiale. Une fois enregistrée, son remplacement sera volontairement encadré dans l'écran Sécurité.")
                }
            }
            Button(
                enabled = settings != null && !saving,
                onClick = {
                    val current = settings ?: return@Button
                    val capture = networkReader.captureCurrentNetwork()
                    if (capture == null) {
                        message = "Aucun Wi-Fi exploitable n'est détecté. Connectez le téléphone au réseau Maison puis réessayez."
                        return@Button
                    }
                    saving = true
                    scope.launch {
                        repository.save(
                            current.copy(
                                trustedWifiEnabled = true,
                                trustedWifiHash = capture.storedHash,
                                trustedPlaceLabel = current.trustedPlaceLabel.trim().ifBlank { "Maison" }
                            )
                        )
                        continueToApp()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (saving) "Enregistrement…" else "Utiliser ce Wi-Fi comme Maison")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(
                "Il n'y a pas de bouton « ignorer » pour une nouvelle configuration : VeVak doit savoir quel réseau vous avez choisi comme Maison avant la suite.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
