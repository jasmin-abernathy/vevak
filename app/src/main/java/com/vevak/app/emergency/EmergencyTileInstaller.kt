/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app.emergency

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.vevak.app.R

object EmergencyTileInstaller {
    fun request(context: Context, result: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            result("Ouvrez deux fois le volet Android, touchez le crayon ou Modifier, puis faites glisser Urgence VeVak parmi vos réglages rapides.")
            return
        }
        val manager = context.getSystemService(StatusBarManager::class.java)
        if (manager == null) {
            result("L'ajout automatique n'est pas disponible sur ce téléphone. Utilisez Modifier dans le volet Android.")
            return
        }
        try {
            manager.requestAddTileService(
                ComponentName(context, EmergencyQuickSettingsTileService::class.java),
                context.getString(R.string.emergency_tile_label),
                Icon.createWithResource(context, R.drawable.ic_emergency_tile),
                context.mainExecutor
            ) { code ->
                result(when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Tuile ajoutée aux réglages rapides Android."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "La tuile est déjà dans vos réglages rapides."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "Tuile non ajoutée. Vous pourrez réessayer plus tard."
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS -> "Une demande d'ajout est déjà ouverte dans Android."
                    StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND -> "Revenez dans VeVak puis réessayez d'ajouter la tuile."
                    else -> "Android n'a pas ajouté la tuile. Vous pouvez utiliser Modifier dans le volet Android."
                })
            }
        } catch (_: IllegalArgumentException) {
            result("Android ne permet pas l'ajout automatique ici. Utilisez Modifier dans le volet Android.")
        } catch (_: SecurityException) {
            result("Ouvrez VeVak au premier plan pour proposer l'ajout de la tuile.")
        }
    }
}
