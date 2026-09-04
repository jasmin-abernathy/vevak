/*
 * Copyright (C) 2026 VeVak contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.vevak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vevak.app.data.VeVakSettingsRepository
import com.vevak.app.location.VeVakPositionResolver
import com.vevak.app.ui.VeVakAppRoot
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var positionPrimeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { VeVakAppRoot() }
    }

    /**
     * VeVak deliberately does not track in the background. When the owner actually opens the app,
     * we opportunistically refresh the local last-position memory if the configured sources are
     * available. A later authorised SMS can reuse that remembered point even if Android location is
     * subsequently switched off.
     */
    override fun onResume() {
        super.onResume()
        positionPrimeJob?.cancel()
        val app = application as VeVakApplication
        positionPrimeJob = app.applicationScope.launch {
            val settings = runCatching { VeVakSettingsRepository(this@MainActivity).current() }.getOrNull()
                ?: return@launch
            if (!settings.completedOnboarding) return@launch
            runCatching {
                VeVakPositionResolver(this@MainActivity).resolve(
                    settings = settings,
                    includeTrustedPlace = false
                )
            }
        }
    }

    override fun onPause() {
        positionPrimeJob?.cancel()
        positionPrimeJob = null
        super.onPause()
    }
}
