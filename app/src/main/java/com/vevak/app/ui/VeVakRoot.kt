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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.diagnostics.CheckState
import com.vevak.app.model.MapProvider
import com.vevak.app.ui.theme.VeVakTheme

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
                    OnboardingStep.Summary -> Summary(state, viewModel)
                    OnboardingStep.Home -> Home(state, viewModel)
                }
            }
        }
    }
}

@Composable private fun Welcome(next: () -> Unit) {
    Title("Localisation à la demande, par SMS")
    Text("VeVak répond uniquement à une phrase exacte envoyée par un contact autorisé. Aucun compte, aucun serveur et aucune permission Internet ne sont requis.")
    Info("Important", "VeVak n'est pas un service d'urgence. Les SMS, la localisation et les restrictions du constructeur peuvent échouer. Testez l'application régulièrement et ne l'utilisez jamais comme unique mesure de sécurité.")
    Info("Visibilité des SMS", "VeVak ne peut pas masquer les SMS : la demande reçue et la réponse envoyée restent visibles dans votre application de messagerie.")
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
    Title("2. Phrase de déclenchement")
    Text("La comparaison est exacte, sans tenir compte de la casse ni des espaces répétés. Choisissez une phrase discrète, mais pas une formule utilisée par hasard dans une conversation.")
    OutlinedTextField(
        value = state.settings.triggerPhrase,
        onValueChange = vm::updateTrigger,
        label = { Text("Phrase") },
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
    Info("Politique par défaut", "VeVak utilise d'abord une position en cache de moins de 2 minutes, puis demande une position unique pendant 8 secondes au maximum. Il n'effectue aucune localisation périodique.")
    Navigation(vm, canContinue = true)
}

@Composable private fun Permissions(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val mainPermissions = remember {
        arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshDiagnostics()
    }
    Title("4. Autorisations")
    Text("Les permissions SMS servent uniquement à recevoir la commande et à répondre. La localisation est sollicitée seulement après une commande autorisée.")
    Primary("Autoriser SMS et localisation") { launcher.launch(mainPermissions) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ouvrir les réglages : choisir « Toujours autoriser »") }
    }
    Info("Pourquoi l'arrière-plan ?", "La commande arrive lorsque l'application n'est généralement pas ouverte. Android exige donc une autorisation distincte pour obtenir une nouvelle position dans ce contexte.")
    state.diagnostics?.checks?.forEach { check ->
        val marker = when (check.state) { CheckState.Ok -> "OK"; CheckState.Warning -> "!"; CheckState.Error -> "À régler" }
        Text("$marker — ${check.title}: ${check.detail}")
    }
    Navigation(vm, canContinue = true)
}

@Composable private fun Summary(state: AppUiState, vm: AppViewModel) {
    Title("5. Vérification")
    Info("Contact", state.settings.contactName.ifBlank { state.settings.contactPhone } + " — " + state.settings.contactPhone)
    Info("Phrase", state.settings.triggerPhrase)
    Info("Carte", state.settings.mapProvider.label)
    Info("Comportement", "Cache ≤ ${state.settings.maxCachedLocationAgeSeconds}s, localisation ≤ ${state.settings.locationTimeoutSeconds}s, délai entre demandes ≥ ${state.settings.minRequestIntervalSeconds}s.")
    Info("À tester", "Après activation, envoyez la phrase depuis le contact autorisé, écran éteint. Recommencez après chaque mise à jour Android ou changement d'optimisation batterie.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f)) { Text("Retour") }
        Button(onClick = vm::complete, modifier = Modifier.weight(1f), enabled = state.settings.contactPhone.isNotBlank() && state.settings.triggerPhrase.isNotBlank()) { Text("Activer") }
    }
}

@Composable private fun Home(state: AppUiState, vm: AppViewModel) {
    Title(if (state.diagnostics?.ready == true) "VeVak est configuré" else "Configuration à vérifier")
    Text("VeVak attend localement la phrase exacte du contact autorisé. Aucun serveur n'est contacté.")
    Info("Contact", state.settings.contactName.ifBlank { state.settings.contactPhone })
    Info("Phrase", state.settings.triggerPhrase)
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
    TextButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Recommencer la configuration") }
    HorizontalDivider()
    Text("Les SMS restent visibles. VeVak ne garantit ni la réception du SMS ni l'obtention d'une position.", style = MaterialTheme.typography.bodySmall)
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
