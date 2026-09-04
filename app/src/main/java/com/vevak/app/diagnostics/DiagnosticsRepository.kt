/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.diagnostics

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vevak.app.BuildConfig
import com.vevak.app.location.OnlineApproximateLocationProvider
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.DuressPolicy
import com.vevak.app.system.RequestVisibilityNotifier

class DiagnosticsRepository(private val context: Context) {
    private val locationRepository = VeVakLocationRepository(context)
    private val capabilityProbe = LocationCapabilityProbe(context)
    private val notifier = RequestVisibilityNotifier(context)

    fun snapshot(settings: VeVakSettings): DiagnosticsSnapshot {
        val receive = granted(Manifest.permission.RECEIVE_SMS)
        val send = granted(Manifest.permission.SEND_SMS)
        val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val telephony = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_MESSAGING)
        } else {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        }
        val backend = locationRepository.backendStatus()
        val capabilities = capabilityProbe.snapshot()
        val configuredContacts = settings.trustedContacts()
        val activeContacts = settings.activeTrustedContacts()
        val authorization = activeContacts.isNotEmpty()
        val visibility = notifier.notificationsAllowedForRequests(settings.isDiscreetModeActive())
        val duressValid = DuressPolicy.configurationIsValid(settings)

        val locationServiceCheck = if (capabilities.locationEnabled) {
            ReadinessCheck(
                "Localisation précise Android",
                "Activée : VeVak peut demander ponctuellement un nouveau point aux sources Android.",
                CheckState.Ok
            )
        } else {
            val extra = if (settings.allowNetworkApproximation) {
                " L'estimation réseau via ${OnlineApproximateLocationProvider.SERVICE_NAME} est activée en dernier recours."
            } else {
                " L'estimation réseau est désactivée."
            }
            ReadinessCheck(
                "Localisation précise Android",
                "Désactivée : Android ne peut pas produire un nouveau point précis. VeVak peut encore utiliser un lieu reconnu ou sa dernière position mémorisée localement, avec son ancienneté.$extra",
                CheckState.Warning
            )
        }

        val backendCheck = when {
            backend.available -> ReadinessCheck("Moteur Android", backend.detail, CheckState.Ok)
            foreground || settings.allowNetworkApproximation -> ReadinessCheck(
                "Moteur Android",
                "Aucune source Android active pour un nouveau point précis. Les autres étages du resolver VeVak restent disponibles selon la configuration.",
                CheckState.Warning
            )
            else -> ReadinessCheck("Moteur Android", backend.detail, CheckState.Error)
        }

        val homeFingerprintCheck = when {
            capabilities.localNetworkFingerprintAvailable -> ReadinessCheck(
                "Empreinte locale Maison",
                "Disponible sur cette connexion Wi-Fi sans lire le nom du réseau. Une reconnexion peut être reconnue tant que l'empreinte réseau reste stable.",
                CheckState.Ok
            )
            capabilities.activeTransport == "wifi" -> ReadinessCheck(
                "Empreinte locale Maison",
                "Non disponible sur cette connexion. Sans SSID lisible, VeVak doit rester sur une reconnaissance limitée à la session réseau.",
                CheckState.Warning
            )
            else -> ReadinessCheck(
                "Empreinte locale Maison",
                "Aucune connexion Wi-Fi active pour tester cette capacité.",
                CheckState.Warning
            )
        }

        val checks = listOf(
            check(configuredContacts.isNotEmpty(), "Contacts autorisés", "${configuredContacts.size} contact(s) configuré(s).", "Ajoutez au moins un numéro pouvant interroger VeVak."),
            check(configuredContacts.all { it.triggerPhrase.isNotBlank() }, "Phrases de déclenchement", "Toutes les phrases sont configurées.", "Chaque contact doit avoir une phrase non vide."),
            check(authorization, "Autorisations locales", "${activeContacts.size} autorisation(s) active(s) et limitée(s) dans le temps.", "Réactivez explicitement au moins un contact."),
            check(duressValid, "Protection sous contrainte", "Configuration cohérente.", "La phrase de sécurité doit être distincte de toutes les phrases normales et une position de repli doit être enregistrée."),
            check(visibility, "Visibilité des demandes", "Notifications disponibles.", "Activez les notifications VeVak : aucune position ne sera envoyée sans notification visible."),
            check(telephony, "Téléphonie SMS", "Appareil compatible.", "Cet appareil ne déclare pas la fonction SMS."),
            check(receive, "Réception des SMS", "Autorisation accordée.", "Autorisation RECEIVE_SMS manquante."),
            check(send, "Envoi des SMS", "Autorisation accordée.", "Autorisation SEND_SMS manquante."),
            check(foreground, "Permission de localisation ponctuelle", "Accès Android accordé. Aucune permission de localisation permanente n'est requise par VeVak.", "Autorisez la localisation lorsque l'application peut l'utiliser afin qu'elle puisse mémoriser un point réel."),
            homeFingerprintCheck,
            locationServiceCheck,
            backendCheck
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
            appendLine("networkApproximationEnabled=${settings.allowNetworkApproximation}")
            appendLine("locationServicesEnabled=${capabilities.locationEnabled}")
            appendLine("locationLab.knownProviders=${capabilities.knownProviderCount}")
            appendLine("locationLab.enabledProviders=${capabilities.enabledProviderCount}")
            appendLine("locationLab.cachedProviderFixes=${capabilities.cachedProviderFixCount}")
            appendLine("locationLab.visibleCellRecords=${capabilities.visibleCellRecordCount}")
            appendLine("locationLab.wifiIdentityReadable=${capabilities.wifiIdentityReadable}")
            appendLine("locationLab.localNetworkFingerprintAvailable=${capabilities.localNetworkFingerprintAvailable}")
            appendLine("locationLab.activeTransport=${capabilities.activeTransport}")
            appendLine("locationLab.finePermission=${capabilities.fineLocationPermission}")
            appendLine("rememberedLocationRetention=until_replaced_cleared_or_reset")
            appendLine("backgroundLocationRequired=false")
            appendLine("discreetModeActive=${settings.isDiscreetModeActive()}")
            checks.forEachIndexed { index, value ->
                appendLine("check.$index=${value.state}:${value.title}")
            }
            append("Phone numbers, SMS bodies, trigger phrases, SSIDs/BSSIDs, Cell IDs, fallback mode and coordinates are excluded.")
        }
        return DiagnosticsSnapshot(checks, backend.name, report, capabilities)
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun check(ok: Boolean, title: String, okDetail: String, badDetail: String) =
        ReadinessCheck(title, if (ok) okDetail else badDetail, if (ok) CheckState.Ok else CheckState.Error)
}
