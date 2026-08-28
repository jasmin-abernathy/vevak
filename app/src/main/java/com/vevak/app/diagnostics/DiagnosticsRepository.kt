/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vevak.app.BuildConfig
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.system.RequestVisibilityNotifier

class DiagnosticsRepository(private val context: Context) {
    private val locationRepository = VeVakLocationRepository(context)
    private val notifier = RequestVisibilityNotifier(context)

    fun snapshot(settings: VeVakSettings): DiagnosticsSnapshot {
        val receive = granted(Manifest.permission.RECEIVE_SMS)
        val send = granted(Manifest.permission.SEND_SMS)
        val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val locationManager = context.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            runCatching {
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            }.getOrDefault(false)
        }
        val telephony = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        } else {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }
        val backend = locationRepository.backendStatus()
        val configuredContacts = settings.trustedContacts()
        val activeContacts = settings.activeTrustedContacts()
        val authorization = activeContacts.isNotEmpty()
        val visibility = notifier.notificationsAllowedForRequests(settings.isDiscreetModeActive())
        val duressValid = DuressPolicy.configurationIsValid(settings)

        val checks = listOf(
            check(configuredContacts.isNotEmpty(), "Contacts autorisés", "${configuredContacts.size} contact(s) configuré(s).", "Ajoutez au moins un numéro pouvant interroger VeVak."),
            check(configuredContacts.all { it.triggerPhrase.isNotBlank() }, "Phrases de déclenchement", "Toutes les phrases sont configurées.", "Chaque contact doit avoir une phrase non vide."),
            check(authorization, "Autorisations locales", "${activeContacts.size} autorisation(s) active(s) et limitée(s) dans le temps.", "Réactivez explicitement au moins un contact."),
            check(duressValid, "Protection sous contrainte", "Configuration cohérente.", "La phrase de sécurité doit être distincte de toutes les phrases normales et une position de repli doit être enregistrée."),
            check(visibility, "Visibilité des demandes", "Notifications disponibles.", "Activez les notifications VeVak : aucune position ne sera envoyée sans notification visible."),
            check(telephony, "Téléphonie SMS", "Appareil compatible.", "Cet appareil ne déclare pas la fonction SMS."),
            check(receive, "Réception des SMS", "Autorisation accordée.", "Autorisation RECEIVE_SMS manquante."),
            check(send, "Envoi des SMS", "Autorisation accordée.", "Autorisation SEND_SMS manquante."),
            check(foreground, "Localisation", "Accès accordé.", "Autorisez la localisation."),
            check(background, "Localisation en arrière-plan", "Accès permanent accordé.", "Choisissez « Toujours autoriser » dans les réglages Android."),
            check(locationEnabled, "Services de localisation", "Services activés.", "Activez la localisation Android."),
            check(backend.available, "Moteur de localisation", backend.detail, backend.detail)
        )

        val report = buildString {
            appendLine("VeVak diagnostic — redacted")
            appendLine("version=${BuildConfig.VERSION_NAME}")
            appendLine("flavor=${BuildConfig.FLAVOR}")
            appendLine("androidApi=${Build.VERSION.SDK_INT}")
            appendLine("locationBackend=${backend.name}")
            appendLine("usesGooglePlayServices=${BuildConfig.USES_GOOGLE_PLAY_SERVICES}")
            appendLine("trustedContactCount=${configuredContacts.size}")
            appendLine("activeTrustedContactCount=${activeContacts.size}")
            appendLine("trustedWifiConfigured=${settings.hasTrustedWifiConfiguration()}")
            appendLine("discreetModeActive=${settings.isDiscreetModeActive()}")
            checks.forEachIndexed { index, value ->
                appendLine("check.$index=${value.state}:${value.title}")
            }
            append("Phone numbers, SMS bodies, trigger phrases, Wi-Fi identifiers, fallback mode and coordinates are excluded.")
        }
        return DiagnosticsSnapshot(checks, backend.name, report)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun check(ok: Boolean, title: String, okDetail: String, badDetail: String) =
        ReadinessCheck(title, if (ok) okDetail else badDetail, if (ok) CheckState.Ok else CheckState.Error)
}
