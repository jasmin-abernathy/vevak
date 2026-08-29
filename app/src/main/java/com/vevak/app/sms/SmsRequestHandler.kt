/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.sms

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vevak.app.data.ProtectionPromptRepository
import com.vevak.app.data.RequestAuditOutcome
import com.vevak.app.data.RequestAuditRepository
import com.vevak.app.data.RuntimeStateRepository
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.LocationRequestPolicy
import com.vevak.app.location.LocationSource
import com.vevak.app.location.VeVakLocationRepository
import com.vevak.app.location.VeVakLocationSnapshot
import com.vevak.app.model.TrustedContact
import com.vevak.app.model.VeVakSettings
import com.vevak.app.security.IncomingRequestMode
import com.vevak.app.security.RequestModeResolver
import com.vevak.app.system.BatteryReader
import com.vevak.app.system.RequestVisibilityNotifier
import com.vevak.app.system.TrustedNetworkReader

class SmsRequestHandler(private val context: Context) {
    private val settingsRepository = VeVakSettingsRepository(context)
    private val runtimeRepository = RuntimeStateRepository(context)
    private val auditRepository = RequestAuditRepository(context)
    private val protectionPromptRepository = ProtectionPromptRepository(context)
    private val phoneMatcher = PhoneNumberMatcher(context)
    private val locationRepository = VeVakLocationRepository(context)
    private val replySender = SmsReplySender(context)
    private val batteryReader = BatteryReader(context)
    private val notifier = RequestVisibilityNotifier(context)
    private val trustedNetworkReader = TrustedNetworkReader(context)

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

        // Global limiter: adding contacts must never multiply tracking capacity.
        val allowed = runtimeRepository.tryAcquire(
            nowMillis = now,
            minimumIntervalMillis = settings.minRequestIntervalSeconds * 1_000L
        )
        if (!allowed) {
            auditRepository.append(now, RequestAuditOutcome.BlockedRate)
            return
        }

        // A protection/duress request never inspects current Wi-Fi or real location.
        val trustedPlaceLabel = when (mode) {
            IncomingRequestMode.Duress -> null
            IncomingRequestMode.Normal -> settings.trustedPlaceLabel.takeIf {
                trustedNetworkReader.matches(settings)
            }
        }

        val location = when {
            mode == IncomingRequestMode.Duress -> safetyFallback(settings)
            trustedPlaceLabel != null -> null
            else -> fetchRealLocation(settings)
        }

        val reply = if (mode == IncomingRequestMode.Normal && trustedPlaceLabel != null) {
            SmsReplyFormatter.formatTrustedPlace()
        } else {
            SmsReplyFormatter.format(settings, location, batteryReader.percentage())
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
                trustedPlaceLabel != null -> RequestAuditOutcome.Replied
                location == null -> RequestAuditOutcome.Unavailable
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
     * This branch deliberately never calls VeVakLocationRepository. A protection request must not
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

    private suspend fun fetchRealLocation(settings: VeVakSettings): VeVakLocationSnapshot? {
        if (!canReadLocationInBackground()) return null
        return locationRepository.fetchBestLocation(
            LocationRequestPolicy(
                maxAcceptedCacheAgeMillis = settings.maxCachedLocationAgeSeconds * 1_000L,
                currentLocationTimeoutMillis = settings.locationTimeoutSeconds * 1_000L,
                allowStaleFallback = settings.allowStaleFallback
            )
        )
    }

    private fun canReadLocationInBackground(): Boolean {
        val foreground = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!foreground) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
