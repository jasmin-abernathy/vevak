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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val current = repository.current()
        if (current.hasTrustedWifiConfiguration() || current.completedOnboarding) {
            continueToApp()
        } else {
            settings = current
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
