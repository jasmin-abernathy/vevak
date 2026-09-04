/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vevak.app.R
import com.vevak.app.data.ProtectionPromptRepository
import com.vevak.app.data.RequestAuditOutcome
import com.vevak.app.model.AuthorizationDuration
import com.vevak.app.model.MapProvider
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.system.TrustedNetworkReader
import com.vevak.app.ui.theme.VeVakTheme
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class HomeTab(val label: String, val glyph: String) {
    Home("Accueil", "⌂"),
    Contacts("Contacts", "◎"),
    Places("Lieux", "⌖"),
    Settings("Réglages", "⚙")
}

@Composable
fun VeVakBetaRoot(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VeVakTheme {
        if (!state.loaded) return@VeVakTheme

        var homeTabName by rememberSaveable { mutableStateOf(HomeTab.Home.name) }
        val homeTab = runCatching { HomeTab.valueOf(homeTabName) }.getOrDefault(HomeTab.Home)
        var openProtectionSetup by rememberSaveable { mutableStateOf(false) }

        BackHandler(enabled = state.step !in listOf(OnboardingStep.Welcome, OnboardingStep.Home)) {
            viewModel.previous()
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (state.step == OnboardingStep.Home) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        HomeTab.entries.forEach { tab ->
                            val selected = homeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { homeTabName = tab.name },
                                icon = {
                                    Text(
                                        tab.glyph,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                        lineHeight = 34.sp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BrandHeader(compact = state.step == OnboardingStep.Home)
                when (state.step) {
                    OnboardingStep.Welcome -> WelcomeScreen { viewModel.next() }
                    OnboardingStep.Contact -> ContactScreen(state, viewModel)
                    OnboardingStep.Trigger -> TriggerScreen(state, viewModel)
                    OnboardingStep.Options -> OptionsScreen(state, viewModel)
                    OnboardingStep.Permissions -> PermissionsScreen(state, viewModel)
                    OnboardingStep.Safety, OnboardingStep.Summary -> ConsentScreen(state, viewModel)
                    OnboardingStep.Home -> HomeShell(
                        state = state,
                        vm = viewModel,
                        tab = homeTab,
                        selectTab = { homeTabName = it.name },
                        openProtectionSetup = openProtectionSetup,
                        setOpenProtectionSetup = { openProtectionSetup = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader(compact: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (compact) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = if (compact) 9.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "Logo VeVak",
                modifier = Modifier.size(if (compact) 44.dp else 76.dp)
            )
            Column {
                Text(
                    "VeVak",
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (compact) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                )
                Text(
                    if (compact) "Local • privé • à la demande" else "Localisation à la demande, par SMS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun WelcomeScreen(next: () -> Unit) {
    Title("Vous gardez le contrôle")
    Text("VeVak permet à des personnes que vous choisissez de demander ponctuellement votre position par SMS.")
    SimpleInfo("Local", "Pas de compte VeVak, pas de serveur obligatoire et pas de suivi continu.")
    SimpleInfo("Révocable", "Chaque accès expire et peut être coupé immédiatement depuis votre téléphone.")
    SimpleInfo("À savoir", "VeVak n'est pas un service d'urgence. Le réseau SMS, Android ou la localisation peuvent parfois être indisponibles.")
    Primary("Configurer VeVak", next)
}

@Composable
private fun ContactScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    var pickerMessage by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            readPickedPhone(context, uri)?.let { (name, phone) ->
                vm.updateContact(name, phone)
                pickerMessage = null
            } ?: run { pickerMessage = "Impossible de lire ce numéro. Vous pouvez le saisir manuellement." }
        }
    }

    StepLabel("Étape 1 sur 5")
    Title("Qui pourra vous demander votre position ?")
    Text("Choisissez une personne dans votre répertoire. VeVak ne demande pas l'accès à tout votre carnet d'adresses.")
    Primary("Choisir dans mes contacts") {
        picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }
    Text("ou saisir manuellement", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    pickerMessage?.let { InlineMessage(it) }
    NavigationButtons(vm, canContinue = state.settings.contactPhone.isNotBlank())
}

@Composable
private fun TriggerScreen(state: AppUiState, vm: AppViewModel) {
    StepLabel("Étape 2 sur 5")
    Title("Choisissez une phrase-clé")
    Text("Votre contact devra envoyer cette phrase par SMS pour demander votre position. Majuscules, minuscules, espaces insécables et apostrophes typographiques courantes sont normalisés.")
    OutlinedTextField(
        value = state.settings.triggerPhrase,
        onValueChange = vm::updateTrigger,
        label = { Text("Phrase-clé") },
        placeholder = { Text("Ex. : Où es-tu ?") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    NavigationButtons(vm, canContinue = state.settings.triggerPhrase.trim().isNotBlank())
}

@Composable
private fun OptionsScreen(state: AppUiState, vm: AppViewModel) {
    StepLabel("Étape 3 sur 5")
    Title("Que doit contenir la réponse ?")
    CheckRow("Ajouter le niveau de batterie", state.settings.includeBattery) { vm.updateOptions(battery = it) }
    CheckRow("Ajouter la précision de la position", state.settings.includeAccuracy) { vm.updateOptions(accuracy = it) }
    SimpleInfo(
        "Dernière position toujours disponible",
        "Si aucune position plus récente ne peut être obtenue, VeVak renvoie la dernière position mémorisée et indique depuis combien de temps elle date. Ce comportement fait partie du fonctionnement de base et n'a plus besoin d'une option séparée."
    )
    CheckRow(
        "Autoriser la localisation alternative réseau/IP en dernier recours",
        state.settings.allowNetworkApproximation
    ) { vm.updateOptions(networkApproximation = it) }
    SimpleInfo(
        "Localisation alternative",
        "Facultative. Si vous l'activez et qu'aucune source locale exploitable n'est disponible, VeVak peut demander une zone approximative à beaconDB via votre adresse IP. Le SMS la présente comme une estimation, jamais comme un GPS."
    )
    Text("Moteur du lien cartographique", fontWeight = FontWeight.SemiBold)
    MapProvider.entries.forEach { provider ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = provider == state.settings.mapProvider,
                onClick = { vm.updateOptions(provider = provider) }
            )
            Text(provider.label)
        }
    }
    SimpleInfo("Protection anti-suivi abusif", "Les réponses automatiques sont limitées à une toutes les 15 minutes et à 4 maximum sur 24 heures, pour l'ensemble des contacts. Le bouton d'urgence déclenché localement n'est pas soumis à cette limite.")
    NavigationButtons(vm, canContinue = true)
}

@Composable
private fun PermissionsScreen(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
                vm.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mainPermissions = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshTick++
        vm.refreshDiagnostics()
    }

    @Suppress("UNUSED_VARIABLE") val refresh = refreshTick
    val receiveSms = hasPermission(context, Manifest.permission.RECEIVE_SMS)
    val sendSms = hasPermission(context, Manifest.permission.SEND_SMS)
    val foregroundLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    val notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    val allGranted = receiveSms && sendSms && foregroundLocation && notifications
    val locationEnabled = systemLocationEnabled(context)

    LaunchedEffect(allGranted) {
        if (allGranted) vm.next()
    }

    StepLabel("Étape 4 sur 5")
    Title("Autorisations nécessaires")
    Text("VeVak vous explique chaque accès avant de le demander. Dès que tout ce qui est réellement nécessaire est autorisé, cette étape se valide toute seule.")

    PermissionCard("SMS", receiveSms && sendSms, "Reconnaître la phrase-clé d'un contact autorisé et lui répondre.")
    PermissionCard("Localisation ponctuelle", foregroundLocation, "Mémoriser une position lorsque VeVak peut y accéder. Elle n'a pas besoin de rester disponible en permanence.")
    PermissionCard("Notifications", notifications, "Rendre les demandes automatiques visibles sur votre propre téléphone.")

    if (!allGranted) {
        Primary("Autoriser ce qui manque") { permissionLauncher.launch(mainPermissions) }
    }

    OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
        Text("Ouvrir les paramètres Android de VeVak")
    }

    if (!(receiveSms && sendSms)) {
        SimpleInfo("Version de test installée manuellement", "Android peut protéger l'accès aux SMS avec une confirmation supplémentaire. Si le bouton SMS reste bloqué : ouvrez les réglages de VeVak, utilisez le menu ⋮ puis « Autoriser les paramètres restreints » lorsqu'Android propose cette option.")
    }

    if (!locationEnabled) {
        SimpleInfo(
            "Localisation précise actuellement désactivée",
            "Android ne peut pas produire un nouveau point précis pour le moment. VeVak peut toutefois utiliser un lieu reconnu, la dernière position mémorisée ou une estimation réseau approximative si vous l'avez activée."
        )
    }

    if (!allGranted) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
    } else {
        Text("Tout est prêt ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    state.message?.let { InlineMessage(it) }
}

@Composable
private fun ConsentScreen(state: AppUiState, vm: AppViewModel) {
    val contact = state.settings.primaryTrustedContact()
    val duration = state.authorizationDuration
    val expiry = duration.expiresAt(System.currentTimeMillis())

    StepLabel("Étape 5 sur 5")
    Title("Autoriser ce contact")
    Text("Vous choisissez pendant combien de temps cette personne pourra demander votre position.")

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(contact.displayLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(contact.phone)
        }
    }

    Text("Ce que cela permet", fontWeight = FontWeight.SemiBold)
    Text("• Ce contact pourra demander votre position pendant la durée choisie.\n• Vous pourrez retirer cet accès à tout moment.\n• VeVak limite automatiquement les demandes répétées.")

    Text("Durée de l'autorisation", fontWeight = FontWeight.SemiBold)
    AuthorizationDuration.entries.forEach { candidate ->
        DurationCard(candidate, selected = candidate == duration) { vm.setAuthorizationDuration(candidate) }
    }

    SimpleInfo("Résumé", "${contact.displayLabel()} pourra demander votre position jusqu'au ${formatDate(expiry)}. Vous pourrez couper cet accès à tout moment depuis VeVak.")
    CheckRow("J'autorise ce contact pendant la durée choisie", state.consentChecked, vm::setConsentChecked)
    state.message?.let { InlineMessage(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f)) { Text("Retour") }
        Button(
            onClick = vm::complete,
            modifier = Modifier.weight(1f),
            enabled = state.consentChecked && contact.phone.isNotBlank() && contact.triggerPhrase.isNotBlank()
        ) { Text("Autoriser ${duration.label}") }
    }
}

@Composable
private fun HomeShell(
    state: AppUiState,
    vm: AppViewModel,
    tab: HomeTab,
    selectTab: (HomeTab) -> Unit,
    openProtectionSetup: Boolean,
    setOpenProtectionSetup: (Boolean) -> Unit
) {
    when (tab) {
        HomeTab.Home -> HomeTabContent(state, vm, selectTab, setOpenProtectionSetup)
        HomeTab.Contacts -> ContactsTabContent(state, vm)
        HomeTab.Places -> PlacesTabContent(state, vm)
        HomeTab.Settings -> SettingsTabContent(state, vm, openProtectionSetup, setOpenProtectionSetup)
    }
}

@Composable
private fun HomeTabContent(
    state: AppUiState,
    vm: AppViewModel,
    selectTab: (HomeTab) -> Unit,
    setOpenProtectionSetup: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val promptRepository = remember { ProtectionPromptRepository(context.applicationContext) }
    val trustedNetworkReader = remember { TrustedNetworkReader(context.applicationContext) }
    var pendingProtectionContactId by rememberSaveable { mutableStateOf<String?>(null) }
    var shareChooser by rememberSaveable { mutableStateOf(false) }
    var locationRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) locationRefresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_VARIABLE") val refresh = locationRefresh

    LaunchedEffect(Unit) {
        if (!state.settings.duressEnabled) {
            pendingProtectionContactId = promptRepository.firstEligibleContactId()
        }
    }

    val contacts = state.settings.trustedContacts()
    val active = state.settings.activeTrustedContacts()
    val hasSuccessfulRequest = state.auditEvents.any { it.outcome == RequestAuditOutcome.Replied }
    val trustedPlaceRecognized = state.settings.hasTrustedWifiConfiguration() && trustedNetworkReader.matches(state.settings)

    MonitoringHeroCard(state, active, context, trustedPlaceRecognized)

    if (!systemLocationEnabled(context)) {
        when {
            trustedPlaceRecognized -> StatusCard(
                "Lieu reconnu : ${state.settings.trustedPlaceLabel}",
                "La localisation précise Android est coupée, mais VeVak reconnaît ce lieu sans lancer de GPS."
            )
            state.settings.allowNetworkApproximation -> SimpleInfo(
                "Localisation précise Android désactivée",
                "La dernière position mémorisée reste disponible. VeVak peut aussi demander une nouvelle zone réseau approximative si cette option est activée."
            )
            else -> SimpleInfo(
                "Localisation précise Android désactivée",
                "La dernière position mémorisée reste disponible avec son ancienneté. L'estimation réseau approximative est actuellement désactivée."
            )
        }
    }

    if (contacts.isNotEmpty()) {
        ActionCard(
            title = "Partager volontairement ma position",
            detail = "Envoie uniquement la dernière position réelle déjà connue. VeVak ne lance pas un suivi pour cette action.",
            actionLabel = if (state.manualShareLoading) "Lecture en cours…" else "Choisir un destinataire"
        ) {
            if (!state.manualShareLoading) {
                if (contacts.size == 1) vm.requestManualPositionShare(contacts.first().id) else shareChooser = true
            }
        }
    }

    if (shareChooser) {
        SimpleInfo("Choisir le destinataire", "VeVak enverra uniquement la dernière position réelle déjà connue, avec son ancienneté, après votre confirmation.")
        contacts.forEach { contact ->
            OutlinedButton(
                onClick = { shareChooser = false; vm.requestManualPositionShare(contact.id) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(contact.displayLabel()) }
        }
        TextButton(onClick = { shareChooser = false }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
    }

    if (state.manualShareConfirmationPending) {
        val target = state.manualShareTargetContactId?.let(state.settings::contactById)
        if (target != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Envoyer votre dernière position connue à ${target.displayLabel()} ?", fontWeight = FontWeight.Bold)
                    Text("VeVak n'essaiera pas de produire un nouveau point : il enverra uniquement la dernière position réelle déjà connue et indiquera depuis combien de temps elle date. La livraison du SMS n'est pas garantie.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = vm::cancelManualPositionShare, modifier = Modifier.weight(1f)) { Text("Annuler") }
                        Button(onClick = vm::confirmManualPositionShare, modifier = Modifier.weight(1f)) { Text("Envoyer") }
                    }
                }
            }
        }
    }

    if (contacts.isNotEmpty()) {
        Text("Contacts autorisés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        active.take(3).forEach { contact ->
            CompactTrustedContactCard(contact)
        }
        if (active.isEmpty()) {
            SimpleInfo("Aucun accès actif", "Vos contacts restent configurés, mais leurs autorisations sont expirées ou révoquées.")
        }
        OutlinedButton(onClick = { selectTab(HomeTab.Contacts) }, modifier = Modifier.fillMaxWidth()) {
            Text("Gérer les contacts")
        }
    }

    if (!hasSuccessfulRequest && contacts.isNotEmpty()) {
        val first = contacts.first()
        SimpleInfo("Premier test recommandé", "Depuis le téléphone de ${first.displayLabel()}, envoyez : « ${first.triggerPhrase} ». La casse n'a pas d'importance. Une réponse réussie confirme que SMS + autorisation + résolution de position fonctionnent ensemble.")
    } else if (hasSuccessfulRequest && !state.settings.hasTrustedWifiConfiguration()) {
        ActionCard("Réseau Maison manquant", "Le réseau Maison fait désormais partie de la configuration de sécurité initiale. Fermez puis rouvrez VeVak pour compléter cette étape.", "Compris") { }
    }

    val pendingContact = pendingProtectionContactId?.let(state.settings::contactById)
    if (pendingContact != null && !state.settings.duressEnabled) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Une option de protection peut être utile", fontWeight = FontWeight.Bold)
                Text("Avez-vous peur que ${pendingContact.displayLabel()} puisse utiliser votre phrase-clé pour savoir où vous êtes sans que vous le souhaitiez ?")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { promptRepository.dismiss(pendingContact.id) }
                            pendingProtectionContactId = null
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pas maintenant") }
                    Button(
                        onClick = {
                            vm.setProtectedContact(pendingContact.id)
                            pendingProtectionContactId = null
                            setOpenProtectionSetup(true)
                            selectTab(HomeTab.Settings)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Me protéger") }
                }
            }
        }
    }

    state.message?.let { InlineMessage(it) }
}

@Composable
private fun MonitoringHeroCard(
    state: AppUiState,
    active: List<TrustedContact>,
    context: Context,
    trustedPlaceRecognized: Boolean
) {
    val smsReady = hasPermission(context, Manifest.permission.RECEIVE_SMS) && hasPermission(context, Manifest.permission.SEND_SMS)
    val notificationsReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    val enabled = active.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("ACCÈS DE CONFIANCE", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (enabled) "VeVak actif" else "VeVak en pause",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (enabled) {
                            "${active.size} contact${if (active.size > 1) "s" else ""} autorisé${if (active.size > 1) "s" else ""}"
                        } else {
                            "Aucun contact ne peut recevoir de réponse automatique"
                        },
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    if (enabled) "●" else "○",
                    fontSize = 38.sp,
                    color = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatus("SMS", smsReady, Modifier.weight(1f))
                MiniStatus("Visible", notificationsReady, Modifier.weight(1f))
                MiniStatus("Anti-suivi", true, Modifier.weight(1f))
            }

            Text(
                when {
                    trustedPlaceRecognized -> "Lieu reconnu : ${state.settings.trustedPlaceLabel}. Aucun GPS permanent n'est nécessaire."
                    systemLocationEnabled(context) -> "La localisation Android est disponible : VeVak peut actualiser sa mémoire ponctuellement."
                    else -> "La localisation Android est coupée : VeVak conserve et renvoie la dernière position mémorisée avec son ancienneté."
                },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MiniStatus(label: String, ok: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
    ) {
        Text(
            text = if (ok) "✓ $label" else "○ $label",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun CompactTrustedContactCard(contact: TrustedContact) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(contact.displayLabel(), fontWeight = FontWeight.Bold)
                Text(
                    "Autorisé jusqu'au ${formatDate(contact.authorizationExpiresAtEpochMs)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("✓", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ContactsTabContent(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    var adding by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            readPickedPhone(context, uri)?.let { (name, phone) ->
                vm.updateNewContactName(name)
                vm.updateNewContactPhone(phone)
            }
        }
    }

    Title("Contacts de confiance")
    Text("Chaque personne a sa propre phrase-clé et sa propre date d'expiration. La protection anti-suivi reste globale : 15 min minimum entre réponses automatiques et 4 maximum sur 24 h pour tout le téléphone.")
    state.settings.trustedContacts().forEach { contact -> ContactCard(contact, state, vm) }

    if (state.settings.trustedContacts().size < VeVakSettings.MAX_TRUSTED_CONTACTS) {
        if (!adding) {
            Primary("Ajouter un contact") { adding = true }
        } else {
            HorizontalDivider()
            Text("Nouveau contact", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choisir dans mes contacts") }
            OutlinedTextField(value = state.newContactName, onValueChange = vm::updateNewContactName, label = { Text("Nom") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = state.newContactPhone, onValueChange = vm::updateNewContactPhone, label = { Text("Numéro") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = state.newContactTriggerPhrase, onValueChange = vm::updateNewContactTrigger, label = { Text("Phrase-clé") }, placeholder = { Text("Ex. : Où es-tu ?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Durée", fontWeight = FontWeight.SemiBold)
            AuthorizationDuration.entries.forEach { duration ->
                DurationCard(duration, duration == state.newContactAuthorizationDuration) { vm.setNewContactAuthorizationDuration(duration) }
            }
            CheckRow("J'autorise ce contact pendant la durée choisie", state.newContactConsentChecked, vm::setNewContactConsentChecked)
            Button(
                onClick = { vm.addTrustedContact(); adding = false },
                enabled = state.newContactPhone.isNotBlank() && state.newContactTriggerPhrase.isNotBlank() && state.newContactConsentChecked,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Ajouter et autoriser") }
            TextButton(onClick = { adding = false }, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
        }
    }
    state.message?.let { InlineMessage(it) }
}

@Composable
private fun ContactCard(contact: TrustedContact, state: AppUiState, vm: AppViewModel) {
    val active = contact.hasActiveAuthorization()
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(contact.displayLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(contact.phone, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (active) "Autorisé jusqu'au ${formatDate(contact.authorizationExpiresAtEpochMs)}" else "Accès expiré ou révoqué", color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { vm.requestManualPositionShare(contact.id) }, modifier = Modifier.fillMaxWidth()) { Text("Envoyer ma dernière position connue") }
            if (active) {
                OutlinedButton(onClick = { vm.revokeContact(contact.id) }, modifier = Modifier.fillMaxWidth()) { Text("Révoquer l'accès") }
            } else {
                Text("Réautoriser", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AuthorizationDuration.entries.forEach { duration ->
                        TextButton(onClick = { vm.reauthorizeContact(contact.id, duration) }, modifier = Modifier.weight(1f)) { Text(duration.label) }
                    }
                }
            }
            if (contact.id != VeVakSettings.PRIMARY_CONTACT_ID) {
                TextButton(onClick = { vm.removeAdditionalContact(contact.id) }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer ce contact") }
            }
        }
    }
}

@Composable
private fun PlacesTabContent(state: AppUiState, vm: AppViewModel) {
    val context = LocalContext.current
    val preciseLocationAllowed = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val locationEnabled = systemLocationEnabled(context)
    val sessionOnly = state.settings.trustedWifiHash == TrustedNetworkReader.SESSION_ONLY_MARKER

    Title("Lieux")
    Text("Le réseau Maison est défini pendant la configuration initiale. Son remplacement est volontairement protégé contre les changements accidentels.")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Maison", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("VeVak conserve une empreinte locale du Wi-Fi quand Android le permet, jamais son nom en clair.")
            OutlinedTextField(value = state.settings.trustedPlaceLabel, onValueChange = vm::updateTrustedPlaceLabel, label = { Text("Libellé") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = vm::captureTrustedWifi, modifier = Modifier.fillMaxWidth()) { Text(if (state.settings.hasTrustedWifiConfiguration()) "Vérifier / renforcer ce Wi-Fi" else "Utiliser le Wi-Fi actuel") }
            if (state.settings.hasTrustedWifiConfiguration()) {
                Text(
                    if (sessionOnly) "Configuré pour cette connexion ✓" else "Configuré durablement ✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { context.startActivity(Intent(context, SafetyCenterActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Changer le réseau Maison") }
            }
        }
    }

    if (!state.settings.hasTrustedWifiConfiguration() || sessionOnly) {
        SimpleInfo(
            "Optionnel : reconnaître Maison plus longtemps",
            "Android protège le nom du Wi-Fi comme une information liée à la localisation. Si vous autorisez la localisation précise et activez temporairement la localisation Android, VeVak peut mémoriser seulement une empreinte du nom du réseau et le reconnaître après une reconnexion ou un redémarrage. Sans cela, la connexion Wi-Fi actuelle suffit jusqu'à sa prochaine reconnexion."
        )
        if (sessionOnly) {
            when {
                !preciseLocationAllowed -> OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Autoriser la localisation (facultatif)")
                }
                !locationEnabled -> OutlinedButton(onClick = { openLocationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Activer temporairement la localisation")
                }
                else -> OutlinedButton(onClick = vm::captureTrustedWifi, modifier = Modifier.fillMaxWidth()) {
                    Text("Mémoriser ce Wi-Fi durablement")
                }
            }
        }
    }

    SimpleInfo(
        "Comment VeVak répond à une phrase-clé",
        "VeVak cherche d'abord un point Android récent s'il est accessible, puis un lieu de confiance reconnu, puis une estimation réseau fraîche si vous l'avez activée. À défaut, il renvoie la dernière coordonnée mémorisée quelle que soit sa source et indique toujours son ancienneté. Le partage manuel et l'urgence conservent séparément le dernier point réel."
    )
    state.message?.let { InlineMessage(it) }
}

@Composable
private fun SettingsTabContent(
    state: AppUiState,
    vm: AppViewModel,
    openProtectionSetup: Boolean,
    setOpenProtectionSetup: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> if (uri != null) vm.exportEncryptedBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importEncryptedBackup(uri) }
    var responseOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var diagnosticOpen by rememberSaveable { mutableStateOf(false) }

    Title("Réglages")
    OutlinedButton(
        onClick = { context.startActivity(Intent(context, SafetyCenterActivity::class.java)) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Sécurité, urgence et réseau Maison") }

    SectionToggle("Réponse et carte", responseOpen) { responseOpen = !responseOpen }
    if (responseOpen) {
        CheckRow("Inclure la batterie", state.settings.includeBattery) { vm.updateOptions(battery = it); vm.persistDraft() }
        CheckRow("Inclure la précision", state.settings.includeAccuracy) { vm.updateOptions(accuracy = it); vm.persistDraft() }
        SimpleInfo(
            "Dernière position",
            "La dernière position connue est toujours utilisée lorsque VeVak ne peut pas en obtenir une plus récente. Son ancienneté est indiquée : ce comportement de sécurité n'est plus désactivable par erreur."
        )
        CheckRow("Autoriser une estimation réseau approximative en dernier recours", state.settings.allowNetworkApproximation, vm::setNetworkApproximation)
        SimpleInfo(
            "Estimation réseau : facultative",
            "Désactivée par défaut. Si vous l'activez, VeVak peut contacter beaconDB lorsqu'aucune source Android ou lieu reconnu ne fournit une information plus utile. Le service voit alors votre adresse IP ; le SMS indique clairement qu'il s'agit d'une estimation et non d'un GPS."
        )
        Text("Lien cartographique", fontWeight = FontWeight.SemiBold)
        MapProvider.entries.forEach { provider ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = provider == state.settings.mapProvider, onClick = { vm.updateOptions(provider = provider); vm.persistDraft() })
                Text(provider.label)
            }
        }
    }

    SectionToggle("Notifications discrètes", state.settings.isDiscreetModeActive()) { if (state.settings.isDiscreetModeActive()) vm.disableDiscreetMode() else vm.setDiscreetMode(1) }
    Text("Le mode discret réduit temporairement le bruit des notifications mais ne rend jamais VeVak invisible.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 8, 24).forEach { hours -> TextButton(onClick = { vm.setDiscreetMode(hours) }, modifier = Modifier.weight(1f)) { Text("${hours} h") } }
    }

    SectionToggle("Sauvegarde chiffrée", backupOpen) { backupOpen = !backupOpen }
    if (backupOpen) {
        Text("La sauvegarde contient votre configuration, jamais l'historique des demandes. Après restauration, tous les accès sont révoqués par sécurité.")
        OutlinedTextField(value = state.backupPassword, onValueChange = vm::updateBackupPassword, label = { Text("Mot de passe") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = { exportLauncher.launch("VeVak-config.vvk") }, modifier = Modifier.fillMaxWidth(), enabled = !state.backupBusy) { Text("Créer une sauvegarde") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !state.backupBusy) { Text("Restaurer une sauvegarde") }
    }

    SectionToggle("Diagnostic", diagnosticOpen) { diagnosticOpen = !diagnosticOpen }
    if (diagnosticOpen) {
        DiagnosticRow("SMS", hasPermission(context, Manifest.permission.RECEIVE_SMS) && hasPermission(context, Manifest.permission.SEND_SMS))
        DiagnosticRow("Permission de localisation ponctuelle", hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
        DiagnosticRow("Notifications", Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(context, Manifest.permission.POST_NOTIFICATIONS))

        state.diagnostics?.locationCapabilities?.let { lab ->
            SimpleInfo(
                "Laboratoire localisation — aucune donnée sensible affichée",
                "Localisation Android : ${if (lab.locationEnabled) "ON" else "OFF"}\n" +
                    "Providers Android : ${lab.enabledProviderCount}/${lab.knownProviderCount} actifs\n" +
                    "Caches providers disponibles : ${lab.cachedProviderFixCount}\n" +
                    "Enregistrements cellulaires visibles : ${lab.visibleCellRecordCount}\n" +
                    "Identité Wi-Fi lisible : ${if (lab.wifiIdentityReadable) "oui" else "non/masquée"}\n" +
                    "Connexion : ${lab.activeTransport}"
            )
        }

        if (!systemLocationEnabled(context)) {
            OutlinedButton(onClick = { openLocationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir la localisation précise Android (facultatif)")
            }
        }
        Button(onClick = vm::testLocation, modifier = Modifier.fillMaxWidth(), enabled = !state.testLocationLoading) {
            Text(if (state.testLocationLoading) "Test du resolver en cours…" else "Tester toutes les sources")
        }
        state.testPositionSummary?.let { SimpleInfo("Résultat du resolver", it) }
        SimpleInfo(
            "Test ON/OFF conseillé",
            "Notez les compteurs ci-dessus avec la localisation Android activée, puis désactivez-la, revenez dans VeVak et relancez le test. Cela montre exactement ce que votre modèle de téléphone laisse accessible dans les deux états."
        )
    }

    val protectionExpanded = openProtectionSetup || state.settings.duressEnabled
    SectionToggle("Protection avancée", protectionExpanded) { setOpenProtectionSetup(!protectionExpanded) }
    if (protectionExpanded) {
        Text("Cette option sert si vous craignez qu'une personne déjà autorisée utilise sa propre phrase-clé pour connaître votre vraie position contre votre volonté.")

        if (state.settings.usesLegacyProtectionPhrase() && state.settings.protectedContactId.isBlank()) {
            SimpleInfo(
                "Ancienne configuration détectée",
                "Votre ancienne seconde phrase reste compatible en interne. Pour utiliser le fonctionnement plus simple ci-dessous, sélectionnez le contact concerné : sa phrase habituelle restera inchangée."
            )
        }

        Text("Quel contact voulez-vous protéger ?", fontWeight = FontWeight.SemiBold)
        state.settings.trustedContacts().forEach { contact ->
            ProtectionContactCard(
                contact = contact,
                selected = state.settings.protectedContactId == contact.id,
                onClick = { vm.setProtectedContact(contact.id) }
            )
        }

        val protectedContact = state.settings.protectedContact()
        if (protectedContact != null) {
            SimpleInfo(
                "Phrase utilisée",
                "${protectedContact.displayLabel()} continuera d'envoyer exactement sa phrase-clé habituelle : « ${protectedContact.triggerPhrase} ». Vous n'avez rien d'autre à lui communiquer."
            )
            CheckRow(
                "Activer la protection pour ${protectedContact.displayLabel()}",
                state.settings.duressEnabled,
                vm::setDuressEnabled
            )
            Button(onClick = vm::captureFallbackLocation, modifier = Modifier.fillMaxWidth(), enabled = !state.fallbackLocationLoading) {
                Text(if (state.fallbackLocationLoading) "Enregistrement…" else "Enregistrer le lieu de repli")
            }
            if (state.settings.hasFallbackCoordinates()) {
                Text("Lieu de repli enregistré ✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            SimpleInfo(
                "Ce qui se passera",
                "Si ${protectedContact.displayLabel()} envoie sa phrase-clé habituelle, VeVak n'ira pas lire votre position réelle : la réponse utilisera uniquement le lieu de repli enregistré. Les autres contacts gardent leur fonctionnement normal."
            )
            SimpleInfo("Discrétion", "L'accueil, le diagnostic standard et l'historique visible n'indiquent pas que cette protection existe ou qu'elle a été utilisée.")
            OutlinedButton(onClick = vm::persistDraft, modifier = Modifier.fillMaxWidth(), enabled = vm.duressConfigurationValid()) {
                Text("Enregistrer la protection")
            }
        } else {
            InlineMessage("Sélectionnez d'abord le contact concerné. Sa phrase-clé existante sera utilisée automatiquement.")
        }
    }

    HorizontalDivider()
    Text("À propos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("VeVak fonctionne localement par défaut et n'est pas un service d'urgence. Si vous activez volontairement l'estimation réseau, un appel à beaconDB peut être utilisé uniquement comme repli approximatif.")
    SimpleInfo("Projet libre et gratuit", "Si VeVak vous est utile, vous pouvez soutenir volontairement son développement. Un don ne débloque aucune fonction et n'est jamais nécessaire pour utiliser le socle de sécurité.")
    OutlinedButton(onClick = { openSupportPage(context) }, modifier = Modifier.fillMaxWidth()) { Text("Soutenir VeVak 🌱") }
    TextButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) { Text("Réinitialiser VeVak") }
    state.message?.let { InlineMessage(it) }
}

@Composable
private fun PermissionCard(title: String, ready: Boolean, detail: String) {
    Card(colors = CardDefaults.cardColors(containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text(if (ready) "✓" else "○", fontWeight = FontWeight.Bold, color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column { Text(title, fontWeight = FontWeight.Bold); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DurationCard(duration: AuthorizationDuration, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(duration.label, fontWeight = FontWeight.Bold)
                Text(
                    when (duration) {
                        AuthorizationDuration.OneDay -> "Pour un besoin ponctuel"
                        AuthorizationDuration.SevenDays -> "Pour une courte période"
                        AuthorizationDuration.ThirtyDays -> "Pour un usage régulier"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProtectionContactCard(contact: TrustedContact, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayLabel(), fontWeight = FontWeight.Bold)
                Text(
                    "Phrase-clé : « ${contact.triggerPhrase} »",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StatusCard(title: String, detail: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail) }
}

@Composable
private fun WarningCard(title: String, detail: String, action: () -> Unit) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail)
        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir les réglages") }
    }
}

@Composable
private fun ActionCard(title: String, detail: String, actionLabel: String, action: () -> Unit) = Card {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail)
        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
    }
}

@Composable
private fun SimpleInfo(title: String, detail: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail) }
}

@Composable
private fun SectionToggle(title: String, expanded: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(if (expanded) "−" else "+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(if (ok) "Prêt ✓" else "À régler", color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
@Composable private fun StepLabel(text: String) = Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
@Composable private fun InlineMessage(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Primary(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun NavigationButtons(vm: AppViewModel, canContinue: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = vm::previous, modifier = Modifier.weight(1f)) { Text("Retour") }
        Button(onClick = vm::next, enabled = canContinue, modifier = Modifier.weight(1f)) { Text("Continuer") }
    }
}

private fun readPickedPhone(context: Context, uri: Uri): Pair<String, String>? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)).orEmpty()
            val phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)).orEmpty()
            if (phone.isBlank()) null else name to phone
        }
    }.getOrNull()
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun systemLocationEnabled(context: Context): Boolean {
    val manager = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        manager.isLocationEnabled
    } else {
        @Suppress("DEPRECATION")
        (runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ||
            runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false))
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

private fun openLocationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

private fun openSupportPage(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://vevak.lepotager.org/soutenir/")))
    }
}

private fun formatDate(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMillis))
