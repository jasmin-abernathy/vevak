/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.vevak.app.R
import com.vevak.app.data.EmergencyRecipientStore
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.model.VeVakSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** System-bound only while the tile is visible; no foreground service or notification. */
class EmergencyQuickSettingsTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { EmergencyShortcutArmController(this) }
    private val repository by lazy { VeVakSettingsRepository(applicationContext) }
    private val recipients by lazy { EmergencyRecipientStore(this) }
    private var listeningJob: Job? = null
    private var actionJob: Job? = null
    private var settings: VeVakSettings? = null
    private var unlockRequest = 0L
    private var listening = false
    private var cancellationShown = false

    override fun onStartListening() {
        super.onStartListening()
        listening = true
        listeningJob?.cancel()
        listeningJob = scope.launch {
            settings = runCatching { repository.current() }.getOrNull()
            while (isActive) {
                renderIfListening()
                delay(if (controller.remainingMillis() > 0L) 250L else 1_000L)
            }
        }
    }

    override fun onStopListening() {
        listening = false
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        listening = false
        ++unlockRequest
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        // Repeated taps while preparation is running never cancel it.
        if (actionJob?.isActive == true) return
        if (cancellationShown) {
            actionJob = scope.launch {
                try {
                    val cancelled = controller.cancelIfArmed()
                    Toast.makeText(this@EmergencyQuickSettingsTileService, if (cancelled) "Envoi annulé" else "Aucune urgence en attente à annuler", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this@EmergencyQuickSettingsTileService, "Annulation non confirmée. L'envoi peut encore être pris en charge.", Toast.LENGTH_LONG).show()
                }
                renderIfListening()
            }
            return
        }
        if (isLocked) {
            val request = ++unlockRequest
            unlockAndRun {
                if (request == unlockRequest) {
                    ++unlockRequest
                    armAfterChecks()
                }
            }
        } else {
            ++unlockRequest
            armAfterChecks()
        }
    }

    private fun armAfterChecks() {
        if (isLocked || actionJob?.isActive == true) return
        actionJob = scope.launch {
            settings = runCatching { repository.current() }.getOrNull()
            // runCatching also catches CancellationException: never continue to arm after cancel.
            ensureActive()
            val reason = unavailableReason()
            if (reason != null) {
                Toast.makeText(this@EmergencyQuickSettingsTileService, reason, Toast.LENGTH_LONG).show()
            } else if (!isLocked && !controller.state().isCancellable) {
                // Share the same recipient selection, deadline and single-consumption receiver.
                try {
                    controller.armIfIdle()
                } catch (_: Exception) {
                    Toast.makeText(this@EmergencyQuickSettingsTileService, "Préparation non confirmée. Vérifiez VeVak.", Toast.LENGTH_LONG).show()
                }
            }
            renderIfListening()
        }
    }

    private fun unavailableReason(): String? {
        val current = settings ?: return "Ouvrez VeVak pour vérifier la configuration"
        if (!current.completedOnboarding || recipients.recipients(current).isEmpty()) {
            return "Choisissez les destinataires dans Sécurité VeVak"
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return "Autorisez l'envoi SMS dans VeVak"
        }
        if (SubscriptionManager.getDefaultSmsSubscriptionId() < 0) {
            return "Choisissez une SIM pour les SMS dans Android"
        }
        return null
    }

    private suspend fun renderIfListening() {
        if (!listening) return
        val tile = qsTile ?: return
        val state = controller.state()
        if (!listening) return
        val remaining = state.remainingMillis
        val armed = state.phase == EmergencyArmPhase.CANCEL_WINDOW
        val pending = state.phase == EmergencyArmPhase.PENDING_SYSTEM
        cancellationShown = state.isCancellable
        val unavailable = if (state.isCancellable) null else unavailableReason()
        tile.state = when {
            state.isCancellable -> Tile.STATE_ACTIVE
            unavailable != null -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = when {
            armed -> "Annuler · ${(remaining + 999L) / 1_000L} s"
            pending -> "Urgence en attente"
            else -> getString(R.string.emergency_tile_label)
        }
        val detail = when {
            armed || pending -> "Touchez pour annuler"
            unavailable != null -> unavailable
            isLocked -> "Déverrouiller pour préparer"
            else -> "Préparer l'envoi"
        }
        tile.contentDescription = "${tile.label}. $detail"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = detail
        tile.updateTile()
    }
}
