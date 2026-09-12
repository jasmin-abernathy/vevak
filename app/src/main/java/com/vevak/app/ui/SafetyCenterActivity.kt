/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vevak.app.background.PositionRefreshScheduler
import com.vevak.app.background.BackgroundLocationAccess
import com.vevak.app.data.EmergencyRecipientStore
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.emergency.EmergencyShortcutManager
import com.vevak.app.emergency.EmergencyShortcutPreset
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
    val context = LocalContext.current
    val shortcutManager = remember { EmergencyShortcutManager(context.applicationContext) }
    val refreshScheduler = remember { PositionRefreshScheduler(context.applicationContext) }
    var settings by remember { mutableStateOf<VeVakSettings?>(null) }
    var emergencyConfigured by remember { mutableStateOf(recipientStore.isConfigured()) }
    var allRecipients by remember { mutableStateOf(recipientStore.usesAllActiveContacts()) }
    var selectedIds by remember { mutableStateOf(recipientStore.selectedContactIds()) }
    var selectedShortcutPreset by remember { mutableStateOf(shortcutManager.selectedPreset()) }
    var replacementArmed by remember { mutableStateOf(false) }
    var showBackgroundDisclosure by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        settings?.let(refreshScheduler::sync)
        message = if (granted) {
            "Localisation en arrière-plan autorisée. Le rafraîchissement périodique peut maintenant fonctionner."
        } else {
            "Autorisation refusée : Android empêchera la mise à jour périodique lorsque VeVak n'est pas affiché."
        }
    }
    val appSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        settings?.let(refreshScheduler::sync)
        message = if (BackgroundLocationAccess.isGranted(context)) {
            "Localisation en arrière-plan autorisée. Le rafraîchissement périodique peut maintenant fonctionner."
        } else {
            "L'accès « Toujours autoriser » n'est pas actif : la mise à jour périodique reste suspendue."
        }
    }

    fun saveRefreshSettings(updated: VeVakSettings, confirmation: String) {
        scope.launch {
            settingsRepository.save(updated)
            settings = updated
            refreshScheduler.sync(updated)
            message = confirmation
        }
    }

    LaunchedEffect(Unit) { settings = settingsRepository.current() }
    val current = settings
    val activeContacts = current?.activeTrustedContacts().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
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
                Text("Une alerte d'urgence déclenchée volontairement depuis le téléphone n'est pas soumise à cette limite.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()
        Text("Destinataires de l'urgence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Choisissez maintenant qui recevra le SMS. Le déclenchement d'urgence n'affichera ensuite aucun choix de destinataire ni écran de confirmation.")
        if (!emergencyConfigured) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Envoi d'urgence non configuré", fontWeight = FontWeight.Bold)
                    Text("Aucun contact ne recevra d'alerte tant que vous n'aurez pas fait un choix explicite ci-dessous.")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = emergencyConfigured && allRecipients,
                onClick = {
                    emergencyConfigured = true
                    allRecipients = true
                    recipientStore.setUseAllActiveContacts(true)
                    message = null
                }
            )
            Text("Tous les contacts actuellement autorisés")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = emergencyConfigured && !allRecipients,
                onClick = {
                    emergencyConfigured = true
                    allRecipients = false
                    recipientStore.setSelectedContactIds(selectedIds)
                    message = if (activeContacts.isEmpty()) {
                        "Autorisez d'abord au moins un contact avant de choisir les destinataires."
                    } else null
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
                                message = "Gardez au moins un destinataire ou choisissez « Tous les contacts »."
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
        Text("Raccourci discret d'envoi d'urgence", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("VeVak peut ajouter sur l'écran d'accueil une icône qui ressemble à un petit utilitaire banal. Les noms et logos proposés sont génériques et ne copient aucune application existante.")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Protection contre l'appui accidentel", fontWeight = FontWeight.Bold)
                Text("Premier appui : l'envoi est armé pendant 4 secondes. Un deuxième appui sur le même raccourci pendant ce délai annule l'envoi. Sans deuxième appui, le SMS d'urgence part automatiquement aux destinataires choisis ci-dessus.")
            }
        }

        Text("Nom et icône du raccourci", fontWeight = FontWeight.SemiBold)
        EmergencyShortcutPreset.entries.forEach { preset ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = preset == selectedShortcutPreset,
                    onClick = { selectedShortcutPreset = preset }
                )
                androidx.compose.foundation.Image(
                    painter = painterResource(preset.iconRes),
                    contentDescription = "Aperçu ${preset.label}",
                    modifier = Modifier.padding(2.dp)
                )
                Column {
                    Text(preset.label, fontWeight = FontWeight.SemiBold)
                    Text(preset.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Button(
            enabled = emergencyConfigured &&
                activeContacts.isNotEmpty() &&
                (allRecipients || selectedIds.any { id -> activeContacts.any { it.id == id } }) &&
                shortcutManager.isSupported(),
            onClick = {
                message = if (shortcutManager.requestPin(selectedShortcutPreset)) {
                    "Android va vous proposer d'ajouter « ${selectedShortcutPreset.label} » à l'écran d'accueil. Une fois ajouté, ses destinataires d'urgence resteront ceux définis ci-dessus."
                } else {
                    "Ce lanceur Android ne permet pas à VeVak d'ajouter automatiquement ce raccourci."
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Créer ce raccourci") }

        if (emergencyConfigured) {
            OutlinedButton(
                onClick = {
                    recipientStore.clear()
                    emergencyConfigured = false
                    allRecipients = false
                    selectedIds = emptySet()
                    message = "Envoi d'urgence désactivé. Les raccourcis déjà placés ne pourront plus envoyer de SMS."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Désactiver l'envoi d'urgence") }
        }

        if (activeContacts.isEmpty()) {
            Text("Autorisez au moins un contact avant de créer le raccourci d'urgence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider()
        Text("Mémoire de position", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("VeVak peut essayer périodiquement de rafraîchir une seule dernière position locale. Chaque nouveau point remplace le précédent : aucun trajet ni historique de positions n'est conservé.")
        if (current != null) {
            val backgroundLocationGranted = BackgroundLocationAccess.isGranted(context)
            val backgroundRefreshCanRun = backgroundLocationGranted || current.allowNetworkApproximation
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = current.backgroundRefreshEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showBackgroundDisclosure = true
                        } else {
                            saveRefreshSettings(
                                current.copy(backgroundRefreshEnabled = false, startOnBoot = false),
                                "Rafraîchissement périodique désactivé. La dernière position déjà mémorisée reste disponible."
                            )
                        }
                    }
                )
                Text("Essayer de garder une dernière position récente")
            }

            if (current.backgroundRefreshEnabled) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            when {
                                backgroundLocationGranted -> "Mise à jour réelle hors écran autorisée ✓"
                                current.allowNetworkApproximation -> "Mise à jour approximative disponible"
                                else -> "Mise à jour périodique suspendue"
                            },
                            fontWeight = FontWeight.Bold,
                            color = if (backgroundRefreshCanRun) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            if (backgroundRefreshCanRun && !backgroundLocationGranted) {
                                "Sans accès Android « Toujours autoriser », VeVak peut seulement renouveler la zone réseau/IP si cette option est active. Pour obtenir un nouveau point Android hors écran, accordez l'autorisation ci-dessous."
                            } else {
                                "Pour obtenir un nouveau point Android quand VeVak n'est pas affiché, Android exige l'accès à la localisation en arrière-plan. VeVak l'utilise seulement aux passages que vous activez ici et ne conserve qu'un point."
                            }
                        )
                        if (!backgroundLocationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    } else {
                                        appSettingsLauncher.launch(
                                            Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) "Autoriser en arrière-plan" else "Choisir « Toujours autoriser » dans Android")
                            }
                        }
                    }
                }

                Text("Fréquence cible", fontWeight = FontWeight.SemiBold)
                VeVakSettings.BACKGROUND_REFRESH_INTERVAL_CHOICES_MINUTES.forEach { minutes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = current.normalizedBackgroundRefreshIntervalMinutes() == minutes,
                            onClick = {
                                saveRefreshSettings(
                                    current.copy(backgroundRefreshIntervalMinutes = minutes),
                                    "Fréquence cible réglée sur environ $minutes minutes."
                                )
                            }
                        )
                        Text("Environ $minutes min")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = current.startOnBoot,
                        onCheckedChange = { enabled ->
                            saveRefreshSettings(
                                current.copy(startOnBoot = enabled),
                                if (enabled) {
                                    "VeVak reprogrammera cette mémoire de position après le redémarrage du téléphone."
                                } else {
                                    "Relance automatique au démarrage désactivée."
                                }
                            )
                        }
                    )
                    Text("Relancer VeVak au démarrage du téléphone")
                }

                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Fonctionnement économe", fontWeight = FontWeight.Bold)
                        Text("La fréquence est une cible, pas une horloge exacte : Android peut espacer les mises à jour en arrière-plan et retarder un passage en veille profonde. VeVak n'utilise ni alarme répétitive exacte, ni historique de déplacement, ni notification permanente pour forcer le téléphone à rester éveillé.")
                    }
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
                                settingsRepository.saveWithTrustedNetworkReplacement(updated)
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

    if (showBackgroundDisclosure && current != null) {
        AlertDialog(
            onDismissRequest = { showBackgroundDisclosure = false },
            title = { Text("Localisation lorsque VeVak est fermé") },
            text = {
                Text(
                    "Si vous activez cette option, VeVak pourra accéder ponctuellement à la localisation en arrière-plan, même lorsque l'application n'est pas affichée, afin de remplacer sa seule dernière position mémorisée. VeVak ne crée aucun trajet, ne conserve aucun historique de positions et ne transmet pas ces données à un serveur VeVak. Android peut retarder les passages."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBackgroundDisclosure = false
                    saveRefreshSettings(
                        current.copy(backgroundRefreshEnabled = true),
                        "Rafraîchissement périodique activé. Android peut décaler certains passages pour économiser la batterie."
                    )
                }) { Text("J'ai compris — activer") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showBackgroundDisclosure = false }
                ) { Text("Annuler") }
            }
        )
    }
}
