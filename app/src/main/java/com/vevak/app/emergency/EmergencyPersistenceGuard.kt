/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.vevak.app.emergency

import java.io.IOException

/** Used under the controller lock. Failed disk writes invalidate in-memory preferences. */
internal class EmergencyPersistenceGuard {
    private var failed = false

    fun checkHealthy() {
        if (failed) throw IOException("État de l'urgence non confirmé sur le stockage")
    }

    fun persist(write: () -> Boolean) {
        checkHealthy()
        try {
            if (!write()) throw IOException("Enregistrement de l'urgence impossible")
        } catch (failure: Exception) {
            failed = true
            throw failure
        }
    }
}
