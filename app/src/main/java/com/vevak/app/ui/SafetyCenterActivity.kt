/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vevak.app.data.EmergencyRecipientStore
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.model.VeVakSettings
import com.vevak.app.system.TrustedNetworkReader
import com.vevak.app.ui.theme.VeVakTheme
import kotlinx.coroutines.launch

class SafetyCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = VeVakSettingsRepository(applicationContext)
        val recipients = EmergencyRecipientStore(applicationContext)
        val networkReader = TrustedNetworkReader(applicationContext)

        setContent {
            VeVakTheme {
                SafetyCenter(
                    settingsRepository = settingsRepository,
                    recipientStore = recipients,
                    networkReader = networkReader,
                    close = ::finish
                )
            }
        }
    }
}

@Composable
private fun SafetyCenter(
    settingsRepository: VeVakSettingsRepository,
    recipientStore: EmergencyRecipientStore,
    networkReader: TrustedNetworkReader,
    close: () -> Unit
) {
    var settings by remember { mutableStateOf<VeVakSettings?>(null) }
    var allRecipients by remember { mutableStateOf(recipientStore.usesAllActiveContacts()) }
    var selectedIds by remember { mutableStateOf(recipientStore.selectedContactIds()) }
    var replacementArmed by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { settings = settingsRepository.current() }
    val current = settings
    val activeContacts = current?.activeTrustedContacts().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Sécurité VeVak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Protection anti-suivi abusif", fontWeight = FontWeight.Bold)
                Text("Les réponses automatiques sont limitées à une toutes les 15 minutes et à 4 maximum sur 24 heures.")
                Text("La limite est globale à tous les contacts : ajouter plusieurs personnes ne multiplie pas la capacité de suivi.")
                Text("Une alerte d'urgence déclenchée volontairement depuis la notification n'est pas soumise à cette limite.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        Text("Destinataires de l'urgence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Un appui sur « URGENCE » dans la notification envoie immédiatement la dernière position connue et son ancienneté, sans confirmation supplémentaire.")

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = allRecipients,
                onClick = {
                    allRecipients = true
                    recipientStore.setUseAllActiveContacts(true)
                    message = null
                }
            )
            Text("Tous les contacts actuellement autorisés")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = !allRecipients,
                onClick = {
                    val initial = if (selectedIds.isEmpty()) {
                        activeContacts.firstOrNull()?.id?.let { setOf(it) }.orEmpty()
                    } else {
                        selectedIds
                    }
                    if (initial.isEmpty()) {
                        message = "Autorisez d'abord au moins un contact avant de créer une liste d'urgence prédéfinie."
                    } else {
                        allRecipients = false
                        selectedIds = initial
                        recipientStore.setSelectedContactIds(initial)
                        message = null
                    }
                }
            )
            Text("Seulement des contacts prédéfinis")
        }

        if (!allRecipients) {
            activeContacts.forEach { contact ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = contact.id in selectedIds,
                        onCheckedChange = { checked ->
                            val updated = if (checked) selectedIds + contact.id else selectedIds - contact.id
                            if (updated.isEmpty()) {
                                message = "Gardez au moins un destinataire ou choisissez « Tous les contacts ».")
                            } else {
                                selectedIds = updated
                                recipientStore.setSelectedContactIds(updated)
                                message = null
                            }
                        }
                    )
                    Text(contact.displayLabel())
                }
            }
        }

        HorizontalDivider()
        Text("Réseau Maison", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (current != null) {
            val recognized = current.hasTrustedWifiConfiguration() && networkReader.matches(current)
            Text(
                when {
                    !current.hasTrustedWifiConfiguration() -> "Aucun réseau Maison n'est enregistré."
                    recognized -> "Le réseau Maison enregistré est reconnu actuellement."
                    else -> "Un réseau Maison est enregistré, mais le téléphone n'est pas dessus actuellement."
                }
            )
        }

        if (!replacementArmed) {
            OutlinedButton(
                onClick = {
                    replacementArmed = true
                    message = "Changer Maison remplace un repère de sécurité. Vérifiez que vous êtes bien connecté au nouveau Wi-Fi avant de confirmer."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Changer le réseau Maison") }
        } else {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Confirmer le changement de Maison", fontWeight = FontWeight.Bold)
                    Text("Cette action remplace le réseau actuellement enregistré. Elle n'est jamais déclenchée automatiquement.")
                    Button(
                        enabled = current != null,
                        onClick = {
                            val base = current ?: return@Button
                            val capture = networkReader.captureCurrentNetwork()
                            if (capture == null) {
                                message = "Impossible d'identifier le Wi-Fi actuel. Connectez-vous au nouveau réseau Maison puis réessayez."
                                return@Button
                            }
                            scope.launch {
                                val updated = base.copy(
                                    trustedWifiEnabled = true,
                                    trustedWifiHash = capture.storedHash,
                                    trustedPlaceLabel = base.trustedPlaceLabel.trim().ifBlank { "Maison" }
                                )
                                settingsRepository.save(updated)
                                settings = updated
                                replacementArmed = false
                                message = "Nouveau réseau Maison enregistré."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Confirmer et utiliser le Wi-Fi actuel") }
                    OutlinedButton(
                        onClick = { replacementArmed = false; message = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Annuler") }
                }
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        OutlinedButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
    }
}
