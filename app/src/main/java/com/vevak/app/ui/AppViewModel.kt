/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.diagnostics.DiagnosticsRepository
import com.vevak.app.diagnostics.DiagnosticsSnapshot
import com.vevak.app.location.LocationRequestPolicy
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.MapProvider
import com.vevak.app.model.VeVakSettings
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
    val message: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = VeVakSettingsRepository(application)
    private val runtimeRepository = RuntimeStateRepository(application)
    private val diagnosticsRepository = DiagnosticsRepository(application)
    private val locationRepository = VeVakLocationRepository(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _state.update { current ->
                    current.copy(
                        loaded = true,
                        settings = settings,
                        step = if (settings.completedOnboarding && current.step == OnboardingStep.Welcome) OnboardingStep.Home else current.step
                    )
                }
                refreshDiagnostics()
            }
        }
    }

    fun next() = _state.update { it.copy(step = nextOf(it.step), message = null) }
    fun previous() = _state.update { it.copy(step = previousOf(it.step), message = null) }

    fun updateContact(name: String, phone: String) = updateSettings { it.copy(contactName = name, contactPhone = phone) }
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

    fun complete() {
        viewModelScope.launch {
            val final = _state.value.settings.copy(completedOnboarding = true)
            settingsRepository.save(final)
            _state.update { it.copy(settings = final, step = OnboardingStep.Home) }
            refreshDiagnostics()
        }
    }

    fun reset() {
        viewModelScope.launch {
            settingsRepository.reset()
            runtimeRepository.reset()
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
        val foreground = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
        if (!foreground) {
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

    private fun updateSettings(block: (VeVakSettings) -> VeVakSettings) {
        _state.update { it.copy(settings = block(it.settings)) }
    }

    private fun nextOf(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.Welcome -> OnboardingStep.Contact
        OnboardingStep.Contact -> OnboardingStep.Trigger
        OnboardingStep.Trigger -> OnboardingStep.Options
        OnboardingStep.Options -> OnboardingStep.Permissions
        OnboardingStep.Permissions -> OnboardingStep.Summary
        OnboardingStep.Summary, OnboardingStep.Home -> OnboardingStep.Home
    }

    private fun previousOf(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.Welcome -> OnboardingStep.Welcome
        OnboardingStep.Contact -> OnboardingStep.Welcome
        OnboardingStep.Trigger -> OnboardingStep.Contact
        OnboardingStep.Options -> OnboardingStep.Trigger
        OnboardingStep.Permissions -> OnboardingStep.Options
        OnboardingStep.Summary -> OnboardingStep.Permissions
        OnboardingStep.Home -> OnboardingStep.Home
    }
}
