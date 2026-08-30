/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vevak.app.BuildConfig
import com.vevak.app.data.ProtectionPromptRepository
import com.vevak.app.data.RequestAuditOutcome
import com.vevak.app.data.RequestAuditRepository
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.LocationSource
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.location.VeVakPositionResolution
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import com.vevak.app.system.BatteryReader
import com.vevak.app.system.RequestVisibilityNotifier

class SmsRequestHandler(private val context: Context) {
    private val settingsRepository = VeVakSettingsRepository(context)
    private val runtimeRepository = RuntimeStateRepository(context)
    private val auditRepository = RequestAuditRepository(context)
    private val protectionPromptRepository = ProtectionPromptRepository(context)
    private val phoneMatcher = PhoneNumberMatcher(context)
    private val positionResolver = VeVakPositionResolver(context)
    private val replySender = SmsReplySender(context)
    private val batteryReader = BatteryReader(context)
    private val notifier = RequestVisibilityNotifier(context)

    suspend fun handle(intent: Intent) {
        val incoming = SmsIntentReader.read(intent) ?: return
        val settings = settingsRepository.current()
        if (!settings.completedOnboarding) return

        val resolved = resolveContact(incoming.sender, incoming.body, settings) ?: return
        val contact = resolved.first
        val mode = resolved.second
        val now = System.currentTimeMillis()
        val discreet = settings.isDiscreetModeActive(now)

        if (!contact.hasActiveAuthorization(now)) {
            auditRepository.append(now, RequestAuditOutcome.BlockedAuthorization)
            if (notifier.notificationsAllowedForRequests(discreet)) notifier.showRequestReceived(discreet)
            notifier.syncActiveStatus(settings)
            return
        }

        if (!notifier.showRequestReceived(discreet)) {
            auditRepository.append(now, RequestAuditOutcome.BlockedVisibility)
            return
        }
        notifier.syncActiveStatus(settings)

        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            auditRepository.append(now, RequestAuditOutcome.SendFailed)
            return
        }

        // Keep the production anti-tracking limit, but make debug builds usable for repeated
        // real-device tests. This never changes the release behaviour.
        val configuredIntervalMillis = settings.minRequestIntervalSeconds * 1_000L
        val allowed = runtimeRepository.tryAcquire(
            nowMillis = now,
            minimumIntervalMillis = if (BuildConfig.DEBUG) {
                minOf(configuredIntervalMillis, DEBUG_REQUEST_INTERVAL_MILLIS)
            } else {
                configuredIntervalMillis
            }
        )
        if (!allowed) {
            auditRepository.append(now, RequestAuditOutcome.BlockedRate)
            return
        }

        // A protection/duress request never enters the normal resolver. It must not inspect current
        // Wi-Fi, call Android location providers or perform the optional network request.
        val resolution = when (mode) {
            IncomingRequestMode.Duress -> safetyFallback(settings)?.let {
                VeVakPositionResolution.Coordinates(it)
            } ?: VeVakPositionResolution.Unavailable
            IncomingRequestMode.Normal -> positionResolver.resolve(settings)
        }

        val batteryLabel = batteryReader.label()
        val reply = when (resolution) {
            is VeVakPositionResolution.KnownPlace -> SmsReplyFormatter.formatTrustedPlaceWithBattery(
                label = resolution.label,
                batteryLabel = batteryLabel,
                includeBattery = settings.includeBattery
            )
            is VeVakPositionResolution.Coordinates -> SmsReplyFormatter.formatWithBatteryLabel(
                settings,
                resolution.location,
                batteryLabel
            )
            VeVakPositionResolution.Unavailable -> SmsReplyFormatter.formatWithBatteryLabel(
                settings,
                null,
                batteryLabel
            )
        }

        val sent = runCatching {
            replySender.send(incoming.sender, reply, incoming.subscriptionId)
        }.isSuccess

        if (sent && mode == IncomingRequestMode.Normal) {
            // This counter is deliberately invisible. The UI only checks eligibility on a later
            // voluntary app launch, so the second request itself creates no prompt or notification.
            protectionPromptRepository.recordSuccessfulNormalReply(contact.id)
        }

        auditRepository.append(
            now,
            when {
                !sent -> RequestAuditOutcome.SendFailed
                resolution == VeVakPositionResolution.Unavailable -> RequestAuditOutcome.Unavailable
                else -> RequestAuditOutcome.Replied
            }
        )
    }

    private fun resolveContact(
        sender: String?,
        body: String,
        settings: VeVakSettings
    ): Pair<TrustedContact, IncomingRequestMode>? {
        return settings.trustedContacts().asSequence()
            .filter { phoneMatcher.matches(sender, it.phone) }
            .mapNotNull { contact ->
                RequestModeResolver.resolve(body, contact.triggerPhrase, settings)?.let { mode -> contact to mode }
            }
            .firstOrNull()
    }

    /**
     * This branch deliberately never calls VeVakPositionResolver. A protection request must not
     * acquire, refresh or inspect the real device location, even if the fallback is invalid.
     */
    private fun safetyFallback(settings: VeVakSettings): VeVakLocationSnapshot? {
        val latitude = settings.fallbackLatitude ?: return null
        val longitude = settings.fallbackLongitude ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return VeVakLocationSnapshot(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = settings.fallbackAccuracyMeters?.coerceAtLeast(1f),
            source = LocationSource.SafetyFallback,
            ageMillis = 0L,
            isMocked = false
        )
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val DEBUG_REQUEST_INTERVAL_MILLIS = 10_000L
    }
}
