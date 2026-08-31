/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vevak.app.backup.SettingsBackupRepository
import com.vevak.app.data.RequestAuditEvent
import com.vevak.app.data.RequestAuditRepository
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.diagnostics.DiagnosticsRepository
import com.vevak.app.diagnostics.DiagnosticsSnapshot
import com.vevak.app.location.LocationRequestPolicy
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.location.VeVakPositionResolution
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.model.AuthorizationDuration
import com.vevak.app.model.MapProvider
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.sms.PhoneNumberMatcher
import com.vevak.app.sms.SmsReplyFormatter
import com.vevak.app.sms.SmsReplySender
import com.vevak.app.system.BatteryReader
import com.vevak.app.system.RequestVisibilityNotifier
import com.vevak.app.system.TrustedNetworkReader
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class AppUiState(
    val loaded: Boolean = false,
    val step: OnboardingStep = OnboardingStep.Welcome,
    val settings: VeVakSettings = VeVakSettings(),
    val diagnostics: DiagnosticsSnapshot? = null,
    val testLocation: VeVakLocationSnapshot? = null,
    val testPositionSummary: String? = null,
    val testLocationLoading: Boolean = false,
    val fallbackLocationLoading: Boolean = false,
    val manualShareConfirmationPending: Boolean = false,
    val manualShareTargetContactId: String? = null,
    val manualShareLoading: Boolean = false,
    val auditEvents: List<RequestAuditEvent> = emptyList(),
    val authorizationDuration: AuthorizationDuration = AuthorizationDuration.ThirtyDays,
    val consentChecked: Boolean = false,
    val newContactName: String = "",
    val newContactPhone: String = "",
    val newContactTriggerPhrase: String = "",
    val newContactAuthorizationDuration: AuthorizationDuration = AuthorizationDuration.ThirtyDays,
    val newContactConsentChecked: Boolean = false,
    val backupPassword: String = "",
    val backupBusy: Boolean = false,
    val message: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = VeVakSettingsRepository(application)
    private val runtimeRepository = RuntimeStateRepository(application)
    private val auditRepository = RequestAuditRepository(application)
    private val diagnosticsRepository = DiagnosticsRepository(application)
    private val locationRepository = VeVakLocationRepository(application)
    private val positionResolver = VeVakPositionResolver(application)
    private val smsSender = SmsReplySender(application)
    private val batteryReader = BatteryReader(application)
    private val notifier = RequestVisibilityNotifier(application)
    private val trustedNetworkReader = TrustedNetworkReader(application)
    private val phoneMatcher = PhoneNumberMatcher(application)
    private val backupRepository = SettingsBackupRepository(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.update { current ->
                    current.copy(
                        loaded = true,
                        settings = settings,
                        step = if (settings.completedOnboarding && current.step == OnboardingStep.Welcome) {
                            OnboardingStep.Home
                        } else {
                            current.step
                        }
                    )
                }
                notifier.syncActiveStatus(settings)
                refreshDiagnostics()
            }
        }
        viewModelScope.launch {
            auditRepository.eventsFlow.collect { events ->
                _state.update { it.copy(auditEvents = events) }
            }
        }
    }

    fun next() = _state.update { it.copy(step = nextOf(it.step), message = null) }
    fun previous() = _state.update { it.copy(step = previousOf(it.step), message = null) }

    fun updateContact(name: String, phone: String) =
        updateSettings { it.copy(contactName = name, contactPhone = phone) }

    fun updateTrigger(value: String) = updateSettings { it.copy(triggerPhrase = value) }

    fun updateOptions(
        battery: Boolean? = null,
        accuracy: Boolean? = null,
        provider: MapProvider? = null,
        staleFallback: Boolean? = null,
        networkApproximation: Boolean? = null
    ) = updateSettings {
        it.copy(
            includeBattery = battery ?: it.includeBattery,
            includeAccuracy = accuracy ?: it.includeAccuracy,
            mapProvider = provider ?: it.mapProvider,
            allowStaleFallback = staleFallback ?: it.allowStaleFallback,
            allowNetworkApproximation = networkApproximation ?: it.allowNetworkApproximation
        )
    }

    fun setNetworkApproximation(enabled: Boolean) {
        val updated = _state.value.settings.copy(allowNetworkApproximation = enabled)
        persistSettings(
            updated,
            if (enabled) {
                "Estimation réseau activée. En dernier recours, VeVak pourra contacter beaconDB : votre adresse IP sera visible par ce service et la position obtenue restera approximative."
            } else {
                "Estimation réseau désactivée. VeVak n'utilisera plus Internet pour chercher votre position."
            }
        )
    }

    fun setDuressEnabled(enabled: Boolean) = updateSettings { it.copy(duressEnabled = enabled) }
    fun updateDuressPhrase(value: String) = updateSettings { it.copy(duressPhrase = value) }

    fun updateTrustedPlaceLabel(value: String) {
        persistSettings(_state.value.settings.copy(trustedPlaceLabel = value.take(40)))
    }

    fun captureTrustedWifi() {
        val settings = _state.value.settings
        if (settings.hasTrustedWifiConfiguration() && !trustedNetworkReader.matches(settings)) {
            _state.update {
                it.copy(message = "Le réseau Maison est verrouillé contre les remplacements accidentels. Ouvrez « Sécurité » depuis la notification VeVak pour changer de réseau avec une double confirmation locale.")
            }
            return
        }

        val capture = trustedNetworkReader.captureCurrentNetwork()
        if (capture == null) {
            _state.update { it.copy(message = "Impossible d'identifier une connexion Wi-Fi active. Connectez d'abord le téléphone au Wi-Fi de la maison.") }
            return
        }

        val message = if (capture.durable) {
            "Wi-Fi maison mémorisé. VeVak conserve seulement une empreinte de son nom, jamais le nom en clair."
        } else {
            "Maison enregistrée sans localisation pour cette connexion Wi-Fi. Cela fonctionne tant que cette connexion reste la même. Optionnel : autoriser la localisation précise et activer temporairement la localisation Android permet à VeVak de reconnaître ce Wi-Fi après une reconnexion ou un redémarrage ; le nom du réseau n'est jamais conservé en clair."
        }
        persistSettings(
            settings.copy(
                trustedWifiEnabled = true,
                trustedWifiHash = capture.storedHash,
                trustedPlaceLabel = settings.trustedPlaceLabel.trim().ifBlank { "Maison" }
            ),
            message
        )
    }

    fun clearTrustedWifi() {
        _state.update {
            it.copy(message = "Le réseau Maison ne peut plus être supprimé ou remplacé depuis cet écran. Utilisez « Sécurité » dans la notification VeVak afin que le changement soit explicitement confirmé.")
        }
    }

    fun setDiscreetMode(hours: Int) {
        if (hours !in setOf(1, 8, 24)) return
        val settings = _state.value.settings
        val now = System.currentTimeMillis()
        val latestExpiry = settings.latestActiveAuthorizationExpiry(now)
        if (latestExpiry == null) {
            _state.update { it.copy(message = "Réactivez d'abord au moins un contact VeVak.") }
            return
        }
        val until = minOf(now + hours * HOUR_MILLIS, latestExpiry)
        persistSettings(
            settings.copy(discreetModeUntilEpochMs = until),
            "Mode discret activé jusqu'au ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until))}. Les demandes restent visibles dans Android, mais sans son ni vibration."
        )
    }

    fun disableDiscreetMode() {
        persistSettings(_state.value.settings.copy(discreetModeUntilEpochMs = 0L), "Mode discret désactivé.")
    }

    fun updateNewContactName(value: String) = _state.update { it.copy(newContactName = value.take(80), message = null) }
    fun updateNewContactPhone(value: String) = _state.update { it.copy(newContactPhone = value.take(40), message = null) }
    fun updateNewContactTrigger(value: String) = _state.update { it.copy(newContactTriggerPhrase = value.take(120), message = null) }
    fun setNewContactAuthorizationDuration(value: AuthorizationDuration) =
        _state.update { it.copy(newContactAuthorizationDuration = value, message = null) }
    fun setNewContactConsentChecked(value: Boolean) =
        _state.update { it.copy(newContactConsentChecked = value, message = null) }

    fun addTrustedContact() {
        val current = _state.value
        val settings = current.settings
        val name = current.newContactName.trim()
        val phone = current.newContactPhone.trim()
        val trigger = current.newContactTriggerPhrase.trim()
        when {
            settings.trustedContacts().size >= VeVakSettings.MAX_TRUSTED_CONTACTS -> {
                _state.update { it.copy(message = "VeVak limite actuellement les contacts de confiance à ${VeVakSettings.MAX_TRUSTED_CONTACTS} afin de garder le modèle d'accès simple et vérifiable.") }
                return
            }
            phone.isBlank() || trigger.isBlank() -> {
                _state.update { it.copy(message = "Le numéro et la phrase du nouveau contact sont obligatoires.") }
                return
            }
            !current.newContactConsentChecked -> {
                _state.update { it.copy(message = "Confirmez explicitement que ce nouveau contact est autorisé à demander votre position.") }
                return
            }
            !notifier.notificationsAllowedForRequests() -> {
                _state.update { it.copy(message = "Activez les notifications VeVak avant d'autoriser un nouveau contact : aucune réponse automatique n'est permise sans visibilité locale.") }
                return
            }
            settings.trustedContacts().any { phoneMatcher.matches(phone, it.phone) } -> {
                _state.update { it.copy(message = "Ce numéro correspond déjà à un contact VeVak.") }
                return
            }
            settings.duressEnabled && !DuressPolicy.phrasesAreDistinctEnough(trigger, settings.duressPhrase) -> {
                _state.update { it.copy(message = "La phrase normale de ce contact est trop proche de la phrase sous contrainte.") }
                return
            }
        }

        val now = System.currentTimeMillis()
        val contact = TrustedContact(
            id = UUID.randomUUID().toString(),
            name = name,
            phone = phone,
            triggerPhrase = trigger,
            authorizationGrantedAtEpochMs = now,
            authorizationExpiresAtEpochMs = current.newContactAuthorizationDuration.expiresAt(now)
        )
        persistSettings(
            settings.copy(additionalTrustedContacts = settings.additionalTrustedContacts + contact),
            "Contact ajouté et autorisé pour ${current.newContactAuthorizationDuration.label}."
        )
        _state.update {
            it.copy(
                newContactName = "",
                newContactPhone = "",
                newContactTriggerPhrase = "",
                newContactAuthorizationDuration = AuthorizationDuration.ThirtyDays,
                newContactConsentChecked = false
            )
        }
    }

    fun revokeContact(contactId: String) {
        val settings = _state.value.settings
        val contact = settings.contactById(contactId) ?: return
        persistSettings(
            settings.withUpdatedContact(contact.revoke()).copy(discreetModeUntilEpochMs = 0L),
            "Accès de ${contact.displayLabel()} coupé immédiatement."
        )
    }

    fun reauthorizeContact(contactId: String, duration: AuthorizationDuration) {
        if (!notifier.notificationsAllowedForRequests()) {
            _state.update { it.copy(message = "Activez les notifications VeVak avant de réautoriser ce contact : aucune réponse automatique n'est permise sans visibilité locale.") }
            return
        }
        val settings = _state.value.settings
        val contact = settings.contactById(contactId) ?: return
        val now = System.currentTimeMillis()
        val updatedContact = contact.copy(
            authorizationGrantedAtEpochMs = now,
            authorizationExpiresAtEpochMs = duration.expiresAt(now)
        )
        persistSettings(
            settings.withUpdatedContact(updatedContact),
            "${contact.displayLabel()} est de nouveau autorisé pour ${duration.label}."
        )
    }

    fun removeAdditionalContact(contactId: String) {
        if (contactId == VeVakSettings.PRIMARY_CONTACT_ID) return
        val settings = _state.value.settings
        val contact = settings.contactById(contactId) ?: return
        persistSettings(settings.withoutContact(contactId), "${contact.displayLabel()} a été supprimé de VeVak.")
    }

    fun requestManualPositionShare(contactId: String = VeVakSettings.PRIMARY_CONTACT_ID) {
        val app = getApplication<Application>()
        val contact = _state.value.settings.contactById(contactId)
        when {
            contact == null || contact.phone.isBlank() ->
                _state.update { it.copy(message = "Choisissez d'abord un contact de confiance configuré.") }
            ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ->
                _state.update { it.copy(message = "Autorisez d'abord l'envoi de SMS.") }
            defaultSmsSubscriptionId() == null ->
                _state.update { it.copy(message = "Choisissez une SIM par défaut pour les SMS dans Android avant l'envoi. VeVak n'en choisit jamais une au hasard.") }
            else -> _state.update {
                it.copy(
                    manualShareConfirmationPending = true,
                    manualShareTargetContactId = contact.id,
                    message = null
                )
            }
        }
    }

    fun cancelManualPositionShare() {
        _state.update {
            it.copy(
                manualShareConfirmationPending = false,
                manualShareTargetContactId = null,
                message = "Envoi annulé."
            )
        }
    }

    fun confirmManualPositionShare() {
        val app = getApplication<Application>()
        val current = _state.value
        if (!current.manualShareConfirmationPending || current.manualShareLoading) return
        val settings = current.settings
        val contact = current.manualShareTargetContactId?.let(settings::contactById)
        val subscriptionId = defaultSmsSubscriptionId()
        when {
            contact == null || contact.phone.isBlank() -> {
                _state.update { it.copy(manualShareConfirmationPending = false, manualShareTargetContactId = null, message = "Le contact sélectionné n'est plus configuré.") }
                return
            }
            ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED -> {
                _state.update { it.copy(manualShareConfirmationPending = false, manualShareTargetContactId = null, message = "Autorisation d'envoi de SMS absente.") }
                return
            }
            subscriptionId == null -> {
                _state.update { it.copy(manualShareConfirmationPending = false, manualShareTargetContactId = null, message = "Aucune SIM SMS par défaut n'est définie dans Android.") }
                return
            }
        }

        _state.update { it.copy(manualShareConfirmationPending = false, manualShareLoading = true, message = null) }
        viewModelScope.launch {
            val lastKnown = runCatching { locationRepository.fetchLastKnownLocation() }.getOrNull()
            if (lastKnown == null) {
                _state.update {
                    it.copy(
                        manualShareLoading = false,
                        manualShareTargetContactId = null,
                        message = "Aucune dernière position connue : aucun SMS n'a été envoyé."
                    )
                }
                return@launch
            }

            val body = SmsReplyFormatter.formatManualShareWithBatteryLabel(
                settings,
                lastKnown,
                batteryReader.label()
            )
            val acceptedByAndroid = runCatching { smsSender.send(contact.phone, body, subscriptionId) }.isSuccess
            _state.update {
                it.copy(
                    manualShareLoading = false,
                    manualShareTargetContactId = null,
                    message = if (acceptedByAndroid) {
                        "Dernière position connue (${lastKnown.ageLabel()}) transmise à Android pour envoi à ${contact.displayLabel()}. La livraison n'est pas garantie."
                    } else {
                        "Échec de l'envoi du SMS. Rien ne permet de confirmer sa livraison."
                    }
                )
            }
        }
    }

    fun setConsentChecked(value: Boolean) = _state.update { it.copy(consentChecked = value) }
    fun setAuthorizationDuration(value: AuthorizationDuration) = _state.update { it.copy(authorizationDuration = value) }

    fun complete() {
        viewModelScope.launch {
            val current = _state.value
            val settings = current.settings
            if (!current.consentChecked) {
                _state.update { it.copy(message = "Confirmez explicitement l'autorisation avant d'activer VeVak.") }
                return@launch
            }
            if (settings.contactPhone.isBlank() || settings.triggerPhrase.isBlank()) {
                _state.update { it.copy(message = "Le contact et la phrase normale doivent être configurés.") }
                return@launch
            }
            if (!DuressPolicy.configurationIsValid(settings)) {
                _state.update { it.copy(message = "La protection sous contrainte n'est pas correctement configurée.") }
                return@launch
            }
            if (!notifier.notificationsAllowedForRequests()) {
                _state.update { it.copy(message = "Activez les notifications VeVak : une réponse automatique n'est jamais autorisée sans visibilité locale.") }
                return@launch
            }

            val now = System.currentTimeMillis()
            val final = settings.copy(
                completedOnboarding = true,
                authorizationGrantedAtEpochMs = now,
                authorizationExpiresAtEpochMs = current.authorizationDuration.expiresAt(now),
                discreetModeUntilEpochMs = 0L
            )
            settingsRepository.save(final)
            runtimeRepository.reset()
            notifier.syncActiveStatus(final)
            _state.update {
                it.copy(
                    settings = final,
                    step = OnboardingStep.Home,
                    consentChecked = false,
                    message = "VeVak est activé pour une durée limitée."
                )
            }
            refreshDiagnostics()
        }
    }

    fun beginReauthorization() {
        _state.update { it.copy(step = OnboardingStep.Summary, consentChecked = false, message = null) }
    }

    fun revokeAuthorization() = revokeContact(VeVakSettings.PRIMARY_CONTACT_ID)

    fun reset() {
        viewModelScope.launch {
            notifier.cancelActiveStatus()
            settingsRepository.reset()
            runtimeRepository.reset()
            auditRepository.clear()
            _state.value = AppUiState(loaded = true)
        }
    }

    fun persistDraft() {
        viewModelScope.launch { settingsRepository.save(_state.value.settings) }
    }

    fun updateBackupPassword(value: String) = _state.update { it.copy(backupPassword = value.take(256), message = null) }

    fun exportEncryptedBackup(uri: Uri) {
        val password = _state.value.backupPassword
        if (password.length < 8) {
            _state.update { it.copy(message = "Choisissez un mot de passe de sauvegarde d'au moins 8 caractères.") }
            return
        }
        val settings = _state.value.settings
        _state.update { it.copy(backupBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { backupRepository.export(uri, settings, password) }
            _state.update {
                it.copy(
                    backupBusy = false,
                    backupPassword = "",
                    message = if (result.isSuccess) {
                        "Sauvegarde chiffrée créée. Conservez son mot de passe séparément : VeVak ne peut pas le récupérer."
                    } else {
                        "Impossible de créer la sauvegarde chiffrée. Aucun mot de passe n'a été enregistré par VeVak."
                    }
                )
            }
        }
    }

    fun importEncryptedBackup(uri: Uri) {
        val password = _state.value.backupPassword
        if (password.length < 8) {
            _state.update { it.copy(message = "Saisissez le mot de passe de cette sauvegarde (8 caractères minimum).") }
            return
        }
        _state.update { it.copy(backupBusy = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val restored = runCatching { backupRepository.import(uri, password) }.getOrNull()
            val valid = restored != null &&
                restored.trustedContacts().isNotEmpty() &&
                !hasDuplicateContactPhones(restored) &&
                DuressPolicy.configurationIsValid(restored)
            if (!valid) {
                _state.update {
                    it.copy(
                        backupBusy = false,
                        backupPassword = "",
                        message = "Sauvegarde illisible, mot de passe incorrect ou configuration invalide. Rien n'a été modifié."
                    )
                }
                return@launch
            }

            val safe = restored!!.withAllAuthorizationsRevoked().copy(
                completedOnboarding = true,
                discreetModeUntilEpochMs = 0L
            )
            settingsRepository.save(safe)
            runtimeRepository.reset()
            notifier.cancelActiveStatus()
            _state.update {
                it.copy(
                    backupBusy = false,
                    backupPassword = "",
                    settings = safe,
                    step = OnboardingStep.Home,
                    manualShareConfirmationPending = false,
                    manualShareTargetContactId = null,
                    message = "Configuration restaurée. Par sécurité, tous les contacts sont désautorisés : réactivez localement uniquement ceux que vous souhaitez autoriser."
                )
            }
            refreshDiagnostics()
        }
    }

    fun refreshDiagnostics() {
        _state.update { it.copy(diagnostics = diagnosticsRepository.snapshot(it.settings)) }
    }

    fun testLocation() {
        _state.update { it.copy(testLocationLoading = true, testLocation = null, testPositionSummary = null, message = null) }
        viewModelScope.launch {
            val settings = _state.value.settings
            val result = runCatching { positionResolver.resolve(settings) }
                .getOrDefault(VeVakPositionResolution.Unavailable)
            val coordinate = (result as? VeVakPositionResolution.Coordinates)?.location
            val summary = when (result) {
                is VeVakPositionResolution.KnownPlace -> "Lieu reconnu : ${result.label}. Aucune géolocalisation n'a été lancée."
                is VeVakPositionResolution.Coordinates -> if (result.location.isApproximateNetworkEstimate()) {
                    "Estimation réseau obtenue via l'adresse IP (${result.location.accuracyLabel()}). Ce n'est pas une position GPS."
                } else {
                    "Position obtenue via ${result.location.source.label}, ${result.location.ageLabel()}, précision ${result.location.accuracyLabel()}."
                }
                VeVakPositionResolution.Unavailable -> "Aucune source n'a pu fournir de position ou de lieu reconnu."
            }
            _state.update {
                it.copy(
                    testLocationLoading = false,
                    testLocation = coordinate,
                    testPositionSummary = summary,
                    message = "Test terminé."
                )
            }
            refreshDiagnostics()
        }
    }

    fun captureFallbackLocation() {
        val app = getApplication<Application>()
        if (!hasForegroundLocationPermission(app)) {
            _state.update { it.copy(message = "Autorisez d'abord la localisation pour enregistrer le lieu de repli.") }
            return
        }
        _state.update { it.copy(fallbackLocationLoading = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                locationRepository.fetchBestLocation(
                    LocationRequestPolicy(
                        maxAcceptedCacheAgeMillis = 30_000L,
                        currentLocationTimeoutMillis = 8_000L,
                        allowStaleFallback = false
                    )
                )
            }.getOrNull()
            if (result == null || result.isApproximateNetworkEstimate()) {
                _state.update { it.copy(fallbackLocationLoading = false, message = "Impossible d'enregistrer une position de repli précise maintenant.") }
                return@launch
            }
            updateSettings {
                it.copy(
                    fallbackLatitude = result.latitude,
                    fallbackLongitude = result.longitude,
                    fallbackAccuracyMeters = result.accuracyMeters
                )
            }
            _state.update {
                it.copy(
                    fallbackLocationLoading = false,
                    message = "Position de repli enregistrée localement. Ses coordonnées ne seront pas affichées sur l'accueil."
                )
            }
        }
    }

    fun duressConfigurationValid(): Boolean = DuressPolicy.configurationIsValid(_state.value.settings)

    private fun hasDuplicateContactPhones(settings: VeVakSettings): Boolean {
        val contacts = settings.trustedContacts()
        return contacts.indices.any { left ->
            (left + 1 until contacts.size).any { right ->
                phoneMatcher.matches(contacts[left].phone, contacts[right].phone)
            }
        }
    }

    private fun hasForegroundLocationPermission(app: Application): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

    private fun defaultSmsSubscriptionId(): Int? {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        return subscriptionId.takeIf { it >= 0 }
    }

    private fun updateSettings(block: (VeVakSettings) -> VeVakSettings) {
        _state.update { it.copy(settings = block(it.settings)) }
    }

    private fun persistSettings(settings: VeVakSettings, message: String? = null) {
        _state.update { it.copy(settings = settings, message = message ?: it.message) }
        viewModelScope.launch {
            settingsRepository.save(settings)
            notifier.syncActiveStatus(settings)
            refreshDiagnostics()
        }
    }

    private fun nextOf(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.Welcome -> OnboardingStep.Contact
        OnboardingStep.Contact -> OnboardingStep.Trigger
        OnboardingStep.Trigger -> OnboardingStep.Options
        OnboardingStep.Options -> OnboardingStep.Permissions
        OnboardingStep.Permissions -> OnboardingStep.Safety
        OnboardingStep.Safety -> OnboardingStep.Summary
        OnboardingStep.Summary, OnboardingStep.Home -> OnboardingStep.Home
    }

    private fun previousOf(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.Welcome -> OnboardingStep.Welcome
        OnboardingStep.Contact -> OnboardingStep.Welcome
        OnboardingStep.Trigger -> OnboardingStep.Contact
        OnboardingStep.Options -> OnboardingStep.Trigger
        OnboardingStep.Permissions -> OnboardingStep.Options
        OnboardingStep.Safety -> OnboardingStep.Permissions
        OnboardingStep.Summary -> OnboardingStep.Safety
        OnboardingStep.Home -> OnboardingStep.Home
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1_000L
    }
}
