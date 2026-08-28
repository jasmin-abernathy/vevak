/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.diagnostics.CheckState
import com.vevak.app.model.AuthorizationDuration
import com.vevak.app.model.MapProvider
import com.vevak.app.security.DuressPolicy
import com.vevak.app.security.RequestRatePolicy
import com.vevak.app.ui.theme.VeVakTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun VeVakRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VeVakTheme {
        if (!state.loaded) return@VeVakTheme
        BackHandler(enabled = state.step !in listOf(OnboardingStep.Welcome, OnboardingStep.Home)) { viewModel.previous() }
        Scaffold { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("VeVak", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                when (state.step) {
                    OnboardingStep.Welcome -> Welcome { viewModel.next() }
                    OnboardingStep.Contact -> Contact(state, viewModel)
                    OnboardingStep.Trigger -> Trigger(state, viewModel)
                    OnboardingStep.Options -> Options(state, viewModel)
                    OnboardingStep.Permissions -> Permissions(state, viewModel)
                    OnboardingStep.Safety -> Safety(state, viewModel)
                    OnboardingStep.Summary -> Summary(state, viewModel)
                    OnboardingStep.Home -> Home(state, viewModel)
                }
            }
        }
    }
}

@Composable private fun Welcome(next: () -> Unit) {
    Title("Localisation à la demande, par SMS")
    Text("VeVak répond uniquement aux commandes d'un contact autorisé. Aucun compte VeVak, aucun serveur VeVak et aucune permission Internet ne sont requis.")
    Info("Protection contre la surveillance", "VeVak n'a aucun mode furtif. L'autorisation du contact expire, les demandes sont limitées, chaque demande reste localement visible et l'accès peut être coupé depuis l'accueil.")
    Info("Important", "VeVak n'est pas un service d'urgence. Les SMS, la localisation et les restrictions du constructeur peuvent échouer. Testez l'application régulièrement et ne l'utilisez jamais comme unique mesure de sécurité.")
    Primary("Configurer VeVak", next)
}

@Composable private fun Contact(state: AppUiState, vm: AppViewModel) {
    Title("1. Contact autorisé")
    Text("Saisissez le numéro qui sera le seul à pouvoir déclencher une réponse. Le choix manuel évite la permission d'accès à tout le carnet d'adresses.")
    OutlinedTextField(
        value = state.settings.contactName,
        onValueChange = { vm.updateContact(it, state.settings.contactPhone) },
        label = { Text("Nom (facultatif)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    OutlinedTextField(
        value = state.settings.contactPhone,
        onValueChange = { vm.updateContact(state.settings.contactName, it) },
        label = { Text("Numéro de téléphone") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Navigation(vm, canContinue = state.settings.contactPhone.isNotBlank())
}

@Composable private fun Trigger(state: AppUiState, vm: AppViewModel) {
    Title("2. Phrase normale")
    Text("La comparaison est exacte, sans tenir compte de la casse ni des espaces répétés. Choisissez une phrase qui ne risque pas d'apparaître par hasard dans une conversation.")
    OutlinedTextField(
        value = state.settings.triggerPhrase,
        onValueChange = vm::updateTrigger,
        label = { Text("Phrase normale") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Navigation(vm, canContinue = state.settings.triggerPhrase.isNotBlank())
}

@Composable private fun Options(state: AppUiState, vm: AppViewModel) {
    Title("3. Réponse et sobriété")
    CheckRow("Inclure la batterie", state.settings.includeBattery) { vm.updateOptions(battery = it) }
    CheckRow("Inclure la précision", state.settings.includeAccuracy) { vm.updateOptions(accuracy = it) }
    CheckRow("Utiliser une position ancienne si aucune nouvelle position n'arrive", state.settings.allowStaleFallback) { vm.updateOptions(staleFallback = it) }
    Text("Lien cartographique", fontWeight = FontWeight.SemiBold)
    MapProvider.entries.forEach { provider ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = provider == state.settings.mapProvider, onClick = { vm.updateOptions(provider = provider) })
            Text(provider.label)
        }
    }
    Info("Limite anti-suivi", "Même si le contact connaît la phrase, VeVak impose au minimum 15 minutes entre deux réponses automatiques et au maximum ${RequestRatePolicy.MAX_REQUESTS_PER_WINDOW} réponses sur 24 heures. Ces limites ne peuvent pas être augmentées depuis l'interface.")
    Navigation(vm, canContinue = true)
}

@Composable private fun Permissions(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val mainPermissions = remember {
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshDiagnostics()
    }
    Title("4. Autorisations")
    Text("Les permissions SMS servent uniquement à recevoir la commande et à répondre. La localisation est sollicitée seulement après une commande normale autorisée. Les notifications sont obligatoires : sans visibilité locale, VeVak refuse d'envoyer une position automatiquement.")
    Primary("Autoriser SMS, localisation et notifications", onClick = { launcher.launch(mainPermissions) })
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ouvrir les réglages : choisir « Toujours autoriser »") }
    }
    Info("Pourquoi l'arrière-plan ?", "Une commande normale peut arriver lorsque l'application n'est pas ouverte. Android exige alors une autorisation distincte pour demander une nouvelle position. La commande sous contrainte, elle, n'accède jamais à la position réelle ni au Wi-Fi actuel.")
    state.diagnostics?.checks?.forEach { check ->
        val marker = when (check.state) { CheckState.Ok -> "OK"; CheckState.Warning -> "!"; CheckState.Error -> "À régler" }
        Text("$marker — ${check.title}: ${check.detail}")
    }
    Navigation(vm, canContinue = true)
}

@Composable private fun Safety(state: AppUiState, vm: AppViewModel) {
    Title("5. Protection sous contrainte")
    Text("Option facultative : vous pouvez définir une seconde phrase. Si elle est reçue depuis le contact autorisé, VeVak répond avec un lieu de repli choisi à l'avance au lieu de votre position réelle.")
    CheckRow("Activer la phrase sous contrainte", state.settings.duressEnabled, vm::setDuressEnabled)

    if (state.settings.duressEnabled) {
        OutlinedTextField(
            value = state.settings.duressPhrase,
            onValueChange = vm::updateDuressPhrase,
            label = { Text("Phrase sous contrainte") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        val distinct = DuressPolicy.phrasesAreDistinctEnough(state.settings.triggerPhrase, state.settings.duressPhrase)
        if (!distinct && state.settings.duressPhrase.isNotBlank()) {
            Info("Phrase trop proche", "La phrase de sécurité doit être nettement différente de la phrase normale afin d'éviter tout déclenchement accidentel.")
        }

        Primary(
            if (state.fallbackLocationLoading) "Enregistrement en cours…" else "Enregistrer ma position actuelle comme lieu de repli",
            vm::captureFallbackLocation,
            enabled = !state.fallbackLocationLoading
        )
        if (state.settings.hasFallbackCoordinates()) {
            Info("Lieu de repli enregistré", "Les coordonnées restent uniquement sur cet appareil et ne sont pas affichées sur l'accueil. Réappuyez sur le bouton pour remplacer ce lieu.")
        }
        Info("Règle de sécurité", "La phrase sous contrainte ne lance jamais le GPS, ne lit jamais le Wi-Fi actuel et ne consulte jamais la position réelle. Sa réponse utilise le même format que la réponse normale. L'historique local ne révèle pas quel mode a été utilisé.")
    } else {
        Info("Désactivé par défaut", "Sans cette option, seule la phrase normale peut produire une réponse de localisation.")
    }
    state.message?.let { Text(it) }
    val valid = !state.settings.duressEnabled ||
        (DuressPolicy.configurationIsValid(state.settings) && state.settings.hasFallbackCoordinates())
    Navigation(vm, canContinue = valid)
}

@Composable private fun Summary(state: AppUiState, vm: AppViewModel) {
    Title("6. Consentement et activation")
    Info("Contact", state.settings.contactName.ifBlank { state.settings.contactPhone } + " — " + state.settings.contactPhone)
    Info("Comportement", "Cache ≤ ${state.settings.maxCachedLocationAgeSeconds}s, localisation normale ≤ ${state.settings.locationTimeoutSeconds}s, délai automatique ≥ 15 min, maximum ${RequestRatePolicy.MAX_REQUESTS_PER_WINDOW} réponses / 24 h.")
    if (state.settings.duressEnabled) {
        Info("Protection sous contrainte", "Configurée. La phrase et les coordonnées de repli ne seront pas affichées sur l'écran d'accueil.")
    }

    Text("Durée de l'autorisation", fontWeight = FontWeight.SemiBold)
    AuthorizationDuration.entries.forEach { duration ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = duration == state.authorizationDuration,
                onClick = { vm.setAuthorizationDuration(duration) }
            )
            Text(duration.label)
        }
    }
    Info("Pas d'autorisation permanente", "À l'expiration, VeVak conserve la configuration mais cesse de répondre jusqu'à une nouvelle validation locale sur ce téléphone.")
    CheckRow(
        "Je comprends que ce contact pourra demander ma position pendant la durée choisie et que je peux couper cet accès à tout moment.",
        state.consentChecked,
        vm::setConsentChecked
    )
    state.message?.let { Text(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f)) { Text("Retour") }
        Button(
            onClick = vm::complete,
            modifier = Modifier.weight(1f),
            enabled = state.consentChecked && state.settings.contactPhone.isNotBlank() && state.settings.triggerPhrase.isNotBlank()
        ) { Text("Activer") }
    }
}

@Composable private fun Home(state: AppUiState, vm: AppViewModel) {
    val active = state.settings.hasActiveAuthorization()
    val discreet = state.settings.isDiscreetModeActive()
    Title(if (active) "VeVak est actif" else "VeVak est en pause")

    if (active) {
        val contact = state.settings.contactName.ifBlank { state.settings.contactPhone }
        Info("Accès actuellement autorisé", "$contact peut demander une position jusqu'au ${formatDate(state.settings.authorizationExpiresAtEpochMs)}.")
        OutlinedButton(onClick = vm::revokeAuthorization, modifier = Modifier.fillMaxWidth()) {
            Text("Couper immédiatement l'accès du contact")
        }
    } else {
        Info("Aucune réponse automatique", "L'autorisation a expiré ou a été révoquée. Les commandes reçues ne donnent aucune position tant que vous ne réactivez pas l'accès localement.")
        Primary("Réactiver l'autorisation", vm::beginReauthorization)
    }

    HorizontalDivider()
    Text("Lieu de confiance", fontWeight = FontWeight.SemiBold)
    Text("Vous pouvez enregistrer le Wi-Fi du domicile. Quand une commande normale arrive sur ce réseau, VeVak répond avec un libellé comme « Maison » sans réveiller le GPS.")
    OutlinedTextField(
        value = state.settings.trustedPlaceLabel,
        onValueChange = vm::updateTrustedPlaceLabel,
        label = { Text("Libellé du lieu") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Primary("Enregistrer le Wi-Fi actuel comme lieu de confiance", vm::captureTrustedWifi)
    if (state.settings.hasTrustedWifiConfiguration()) {
        Info("Lieu de confiance actif", "Si le téléphone est connecté à ce Wi-Fi, la réponse indique « ${state.settings.trustedPlaceLabel} » au lieu d'envoyer des coordonnées. Le nom du réseau est conservé uniquement sous forme d'empreinte locale.")
        OutlinedButton(onClick = vm::clearTrustedWifi, modifier = Modifier.fillMaxWidth()) {
            Text("Supprimer ce lieu de confiance")
        }
    }
    Info("Limite", "Le Wi-Fi sert de raccourci pratique, pas de preuve absolue de présence. Si Android ne permet pas d'identifier le réseau actuel, VeVak revient simplement à la localisation normale. Une phrase sous contrainte ne consulte jamais le Wi-Fi.")

    HorizontalDivider()
    Text("Mode discret temporaire", fontWeight = FontWeight.SemiBold)
    if (discreet) {
        Info("Mode discret actif", "Jusqu'au ${formatDateTime(state.settings.discreetModeUntilEpochMs)}. Les demandes sont silencieuses et sans vibration, mais restent visibles dans le volet Android. La notification permanente « VeVak est actif » reste affichée.")
    } else {
        Text("Réduit temporairement les alertes de demande sans transformer VeVak en mode furtif.")
    }
    if (active) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.setDiscreetMode(1) }, modifier = Modifier.weight(1f)) { Text("1 h") }
            OutlinedButton(onClick = { vm.setDiscreetMode(8) }, modifier = Modifier.weight(1f)) { Text("8 h") }
            OutlinedButton(onClick = { vm.setDiscreetMode(24) }, modifier = Modifier.weight(1f)) { Text("24 h") }
        }
    }
    if (discreet) {
        OutlinedButton(onClick = vm::disableDiscreetMode, modifier = Modifier.fillMaxWidth()) {
            Text("Désactiver le mode discret")
        }
    }
    Info("Pas de mode invisible", "Désactiver complètement les notifications Android bloque toujours les réponses automatiques. Le mode discret agit seulement sur le son, la vibration et le niveau d'alerte, pour une durée limitée choisie localement.")

    state.diagnostics?.checks?.forEach { check ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(check.title, fontWeight = FontWeight.SemiBold)
                Text(check.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Primary(if (state.testLocationLoading) "Test en cours…" else "Tester une position unique", vm::testLocation, enabled = !state.testLocationLoading)
    state.testLocation?.let { Info("Résultat du test", "${it.ageLabel()} — précision ${it.accuracyLabel()} — ${it.source.label}") }
    state.message?.let { Text(it) }
    OutlinedButton(onClick = vm::refreshDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Actualiser le diagnostic") }

    HorizontalDivider()
    Text("Historique local des demandes", fontWeight = FontWeight.SemiBold)
    if (state.auditEvents.isEmpty()) {
        Text("Aucune demande enregistrée.", style = MaterialTheme.typography.bodySmall)
    } else {
        state.auditEvents.take(10).forEach { event ->
            Text("${formatDateTime(event.timestampMillis)} — ${event.outcome.label}", style = MaterialTheme.typography.bodySmall)
        }
        Text("L'historique ne conserve ni coordonnées, ni contenu SMS, ni Wi-Fi, ni indication permettant de distinguer une réponse normale d'une réponse de sécurité.", style = MaterialTheme.typography.bodySmall)
    }

    TextButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Effacer les données locales et recommencer") }
    HorizontalDivider()
    Text("VeVak ne garantit ni la réception du SMS ni l'obtention d'une position. Une réponse automatique n'est jamais envoyée si les notifications VeVak ne peuvent pas rester localement visibles.", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun Title(value: String) = Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable private fun Info(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body)
        }
    }
}

@Composable private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable private fun Primary(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) { Text(label) }
}

@Composable private fun Navigation(vm: AppViewModel, canContinue: Boolean) {
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f)) { Text("Retour") }
        Button(onClick = { vm.persistDraft(); vm.next() }, enabled = canContinue, modifier = Modifier.weight(1f)) { Text("Continuer") }
    }
}

private fun formatDate(timestampMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampMillis))

private fun formatDateTime(timestampMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestampMillis))
