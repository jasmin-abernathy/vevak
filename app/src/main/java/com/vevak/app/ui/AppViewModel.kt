/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vevak.app.data.RequestAuditEvent
import com.vevak.app.data.RequestAuditRepository
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.diagnostics.DiagnosticsRepository
import com.vevak.app.diagnostics.DiagnosticsSnapshot
import com.vevak.app.location.LocationRequestPolicy
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.AuthorizationDuration
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.sms.SmsReplyFormatter
import com.vevak.app.sms.SmsReplySender
import com.vevak.app.system.BatteryReader
import com.vevak.app.system.RequestVisibilityNotifier
import com.vevak.app.system.TrustedNetworkReader
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
    val testLocationLoading: Boolean = false,
    val fallbackLocationLoading: Boolean = false,
    val manualShareConfirmationPending: Boolean = false,
    val manualShareLoading: Boolean = false,
    val auditEvents: List<RequestAuditEvent> = emptyList(),
    val authorizationDuration: AuthorizationDuration = AuthorizationDuration.ThirtyDays,
    val consentChecked: Boolean = false,
    val message: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = VeVakSettingsRepository(application)
    private val runtimeRepository = RuntimeStateRepository(application)
    private val auditRepository = RequestAuditRepository(application)
    private val diagnosticsRepository = DiagnosticsRepository(application)
    private val locationRepository = VeVakLocationRepository(application)
    private val smsSender = SmsReplySender(application)
    private val batteryReader = BatteryReader(application)
    private val notifier = RequestVisibilityNotifier(application)
    private val trustedNetworkReader = TrustedNetworkReader(application)
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
        staleFallback: Boolean? = null
    ) = updateSettings {
        it.copy(
            includeBattery = battery ?: it.includeBattery,
            includeAccuracy = accuracy ?: it.includeAccuracy,
            mapProvider = provider ?: it.mapProvider,
            allowStaleFallback = staleFallback ?: it.allowStaleFallback
        )
    }

    fun setDuressEnabled(enabled: Boolean) = updateSettings {
        it.copy(duressEnabled = enabled)
    }

    fun updateDuressPhrase(value: String) = updateSettings {
        it.copy(duressPhrase = value)
    }

    fun updateTrustedPlaceLabel(value: String) {
        val updated = _state.value.settings.copy(trustedPlaceLabel = value.take(40))
        persistSettings(updated)
    }

    fun captureTrustedWifi() {
        val app = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(message = "Autorisez la localisation précise avant d'enregistrer le Wi-Fi du domicile.") }
            return
        }
        val hash = trustedNetworkReader.currentSsidHash()
        if (hash == null) {
            _state.update {
                it.copy(message = "Impossible de lire le Wi-Fi actuel. Vérifiez que le téléphone est connecté en Wi-Fi et que la localisation Android est activée.")
            }
            return
        }
        val updated = _state.value.settings.copy(
            trustedWifiEnabled = true,
            trustedWifiHash = hash,
            trustedPlaceLabel = _state.value.settings.trustedPlaceLabel.trim().ifBlank { "Maison" }
        )
        persistSettings(updated, "Wi-Fi de confiance enregistré localement. Son nom n'est pas conservé en clair.")
    }

    fun clearTrustedWifi() {
        val updated = _state.value.settings.copy(
            trustedWifiEnabled = false,
            trustedWifiHash = ""
        )
        persistSettings(updated, "Lieu de confiance Wi-Fi désactivé.")
    }

    fun setDiscreetMode(hours: Int) {
        if (hours !in setOf(1, 8, 24)) return
        val settings = _state.value.settings
        if (!settings.hasActiveAuthorization()) {
            _state.update { it.copy(message = "Réactivez d'abord l'autorisation VeVak.") }
            return
        }
        val now = System.currentTimeMillis()
        val requestedUntil = now + hours * HOUR_MILLIS
        val until = minOf(requestedUntil, settings.authorizationExpiresAtEpochMs)
        val updated = settings.copy(discreetModeUntilEpochMs = until)
        persistSettings(updated, "Mode discret activé jusqu'au ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(until))}. Les demandes restent visibles dans Android, mais sans son ni vibration.")
    }

    fun disableDiscreetMode() {
        val updated = _state.value.settings.copy(discreetModeUntilEpochMs = 0L)
        persistSettings(updated, "Mode discret désactivé.")
    }

    fun requestManualPositionShare() {
        val app = getApplication<Application>()
        val settings = _state.value.settings
        when {
            settings.contactPhone.isBlank() ->
                _state.update { it.copy(message = "Configurez d'abord un contact de confiance.") }
            ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ->
                _state.update { it.copy(message = "Autorisez d'abord l'envoi de SMS.") }
            !hasForegroundLocationPermission(app) ->
                _state.update { it.copy(message = "Autorisez d'abord la localisation.") }
            defaultSmsSubscriptionId() == null ->
                _state.update { it.copy(message = "Choisissez une SIM par défaut pour les SMS dans Android avant l'envoi. VeVak n'en choisit jamais une au hasard.") }
            else ->
                _state.update { it.copy(manualShareConfirmationPending = true, message = null) }
        }
    }

    fun cancelManualPositionShare() {
        _state.update { it.copy(manualShareConfirmationPending = false, message = "Envoi annulé.") }
    }

    fun confirmManualPositionShare() {
        val app = getApplication<Application>()
        val current = _state.value
        if (!current.manualShareConfirmationPending || current.manualShareLoading) return
        val settings = current.settings
        val subscriptionId = defaultSmsSubscriptionId()
        when {
            settings.contactPhone.isBlank() -> {
                _state.update { it.copy(manualShareConfirmationPending = false, message = "Aucun contact n'est configuré.") }
                return
            }
            ContextCompat.checkSelfPermission(app, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED -> {
                _state.update { it.copy(manualShareConfirmationPending = false, message = "Autorisation d'envoi de SMS absente.") }
                return
            }
            !hasForegroundLocationPermission(app) -> {
                _state.update { it.copy(manualShareConfirmationPending = false, message = "Autorisation de localisation absente.") }
                return
            }
            subscriptionId == null -> {
                _state.update { it.copy(manualShareConfirmationPending = false, message = "Aucune SIM SMS par défaut n'est définie dans Android.") }
                return
            }
        }

        _state.update {
            it.copy(
                manualShareConfirmationPending = false,
                manualShareLoading = true,
                message = null
            )
        }
        viewModelScope.launch {
            val location = runCatching {
                locationRepository.fetchBestLocation(
                    LocationRequestPolicy(
                        maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                        currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                        allowStaleFallback = settings.allowStaleFallback
                    )
                )
            }.getOrNull()

            if (location == null) {
                _state.update {
                    it.copy(
                        manualShareLoading = false,
                        message = "Aucune position obtenue : aucun SMS n'a été envoyé."
                    )
                }
                return@launch
            }

            val body = SmsReplyFormatter.formatManualShare(settings, location, batteryReader.percentage())
            val acceptedByAndroid = runCatching {
                smsSender.send(settings.contactPhone, body, subscriptionId)
            }.isSuccess

            // Deliberately no RequestVisibilityNotifier call here. A manual share is an explicit,
            // foreground action and must not create an extra Android notification in discreet mode.
            _state.update {
                it.copy(
                    manualShareLoading = false,
                    message = if (acceptedByAndroid) {
                        "SMS transmis à Android pour envoi à votre contact. La livraison n'est pas garantie."
                    } else {
                        "Échec de l'envoi du SMS. Rien ne permet de confirmer sa livraison."
                    }
                )
            }
        }
    }

    fun setConsentChecked(value: Boolean) = _state.update { it.copy(consentChecked = value) }

    fun setAuthorizationDuration(value: AuthorizationDuration) =
        _state.update { it.copy(authorizationDuration = value) }

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
                _state.update {
                    it.copy(message = "Activez les notifications VeVak : une réponse automatique n'est jamais autorisée sans visibilité locale.")
                }
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
        _state.update {
            it.copy(
                step = OnboardingStep.Summary,
                consentChecked = false,
                message = null
            )
        }
    }

    fun revokeAuthorization() {
        viewModelScope.launch {
            val revoked = _state.value.settings.copy(
                authorizationGrantedAtEpochMs = 0L,
                authorizationExpiresAtEpochMs = 0L,
                discreetModeUntilEpochMs = 0L
            )
            settingsRepository.save(revoked)
            runtimeRepository.reset()
            notifier.cancelActiveStatus()
            _state.update {
                it.copy(
                    settings = revoked,
                    manualShareConfirmationPending = false,
                    message = "Accès du contact coupé immédiatement."
                )
            }
            refreshDiagnostics()
        }
    }

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

    fun refreshDiagnostics() {
        _state.update { it.copy(diagnostics = diagnosticsRepository.snapshot(it.settings)) }
    }

    fun testLocation() {
        val app = getApplication<Application>()
        if (!hasForegroundLocationPermission(app)) {
            _state.update { it.copy(message = "Autorisez d'abord la localisation.") }
            return
        }
        _state.update { it.copy(testLocationLoading = true, message = null) }
        viewModelScope.launch {
            val settings = _state.value.settings
            val result = runCatching {
                locationRepository.fetchBestLocation(
                    LocationRequestPolicy(
                        maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                        currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                        allowStaleFallback = settings.allowStaleFallback
                    )
                )
            }.getOrNull()
            _state.update {
                it.copy(
                    testLocationLoading = false,
                    testLocation = result,
                    message = if (result == null) "Aucune position obtenue." else "Test terminé."
                )
            }
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

            if (result == null) {
                _state.update {
                    it.copy(fallbackLocationLoading = false, message = "Impossible d'enregistrer une position de repli maintenant.")
                }
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

    private fun hasForegroundLocationPermission(app: Application): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }

    private fun defaultSmsSubscriptionId(): Int? {
        val subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()
        return subscriptionId.takeIf { SubscriptionManager.isValidSubscriptionId(it) }
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