/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.R
import com.vevak.app.model.MapProvider
import com.vevak.app.ui.theme.VeVakTheme

/**
 * Small outer root used to keep the initial setup choices explicit.
 *
 * VeVakBetaRoot remains responsible for the rest of the application. The Options step is rendered
 * here so the initial setup cannot accidentally lose the map-link provider or the opt-in network
 * approximation choice during future home-screen refactors.
 */
@Composable
fun VeVakAppRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    if (state.step != OnboardingStep.Options) {
        VeVakBetaRoot(viewModel)
        return
    }

    VeVakTheme {
        BackHandler { viewModel.previous() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = "Logo VeVak",
                    modifier = Modifier.size(76.dp)
                )
                Column {
                    Text(
                        "VeVak",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Localisation à la demande, par SMS", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text("Étape 3 sur 5", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("Réponse et localisation", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            CheckRow("Ajouter l'état ou le niveau de batterie", state.settings.includeBattery) {
                viewModel.updateOptions(battery = it)
            }
            CheckRow("Utiliser une ancienne position si aucune nouvelle n'est disponible", state.settings.allowStaleFallback) {
                viewModel.updateOptions(staleFallback = it)
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Localisation alternative", fontWeight = FontWeight.Bold)
                    Text(
                        "Si Android ne peut pas fournir une position récente, VeVak peut en dernier recours estimer votre zone via la connexion Internet. Cette estimation peut être large et n'est jamais présentée comme du GPS."
                    )
                    CheckRow(
                        "Activer l'estimation réseau/IP",
                        state.settings.allowNetworkApproximation
                    ) { viewModel.updateOptions(networkApproximation = it) }
                    if (state.settings.allowNetworkApproximation) {
                        Text(
                            "Activée : VeVak pourra contacter beaconDB uniquement lorsque les sources locales ne suffisent pas. Le service voit nécessairement l'adresse IP publique de la connexion.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text("Lien de localisation envoyé par SMS", fontWeight = FontWeight.SemiBold)
            Text(
                "Choisissez le service utilisé pour fabriquer le lien qui accompagne une position connue.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                            .padding(10.dp),
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

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Protection contre les demandes répétées", fontWeight = FontWeight.Bold)
                    Text("La version publique limite les réponses rapprochées. Les builds debug utilisent un délai court pour permettre les tests sur téléphone réel.")
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
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}
